package net.bored.effect;

import net.bored.config.PeripheralConfig;
import net.bored.presence.EffectDebug;
import net.bored.presence.Home;
import net.bored.presence.Observer;
import net.bored.presence.PresenceDirector;
import net.bored.world.OtherPlayerState;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * The other base, built the way a player builds one: first a cleared spot with a crafting table and a campfire,
 * then walls going up, a roof, a furnace and a little farm, and finally a fence and torches. One stage goes up per
 * in-game day of play, only while nobody is near it or looking, so it changes between visits. The first stage levels
 * the plot; after that blocks only go into air or plants, on solid ground, so nothing floats and nothing a player
 * put there is ever replaced.
 */
public final class OtherBaseBuilder {
	public static final int CHECK_INTERVAL = 30 * 20;
	public static final int MAX_STAGE = 12;
	/** The cabin is finished: walls, roof, door and windows. */
	public static final int STAGE_CABIN = 6;
	/** The farm or the pen is in. */
	public static final int STAGE_YARD = 8;
	/** The portal frame, which waits for iron tools. */
	static final int STAGE_PORTAL = 12;
	/** Height of the roof's ridge above the floor. */
	static final int ROOF_TOP = 7;
	/** The cabin is 7x7 around its centre. In front of the door: a step, the campfire, and the farm. */
	static final int HALF = 3;
	/** Last row of the plot in front of the cabin: step and campfire, then the fenced farm. */
	static final int PLOT_END = HALF + 6;
	/** Width of the ring around the plot that must not be a cliff or a terrace. */
	private static final int MARGIN = 3;
	/** How far around the plot no player-made block may be. */
	private static final int UNTOUCHED_RADIUS = 12;
	/** Chunks players have spent more than an in-game day near (vanilla's "inhabited time") are theirs. */
	private static final long MAX_INHABITED_TICKS = 24000L;
	/** Nobody may be closer than this, horizontally, while a stage goes up. */
	private static final double PRIVACY = 40.0;
	/** Players this close hear it being built. */
	static final double HEARING = 80.0;
	/** Chunks within this many blocks of the centre are loaded when the base is built or tidied from afar. */
	private static final int AREA = 28;
	private static final String[] STAGE_NAMES = {
			"a cleared spot with a crafting table, chest and campfire",
			"log corner posts and a course of stone",
			"walls going up and a bed",
			"a log beam along the top of the walls",
			"the gable ends and the first courses of roof",
			"the roof finished, with a log ridge",
			"the door, glass windows, shutters and torches",
			"a furnace with food in it, lanterns and a rug",
			"a wheat farm or an animal pen",
			"a fence round the farm or a young animal in the pen, torch posts and a path",
			"flowers by the door and a lamp post",
			"a lean-to on the side",
			"a nether portal frame, never lit"};

	private OtherBaseBuilder() {
	}

	/** Positions relative to the cabin's centre: {@code a} to the right, {@code b} toward the door, {@code y} above the floor. */
	record Layout(BlockPos centre, Direction front, Direction right) {
		Layout(BlockPos centre, Direction front) {
			this(centre, front, front.rotateYClockwise());
		}

		BlockPos at(int a, int b, int y) {
			return centre.offset(right, a).offset(front, b).up(y);
		}

		BlockPos door() {
			return at(0, HALF, 1);
		}
	}

	public static String stageName(int stage) {
		return STAGE_NAMES[Math.max(0, Math.min(MAX_STAGE, stage))];
	}

	static long stageTicks() {
		return Math.max(20L, (long) (PeripheralConfig.get().otherBaseDaysPerStage * 24000));
	}

	// ---- Choosing the spot ----

	/**
	 * The floor height if this is a spot a player would actually pick for a first base, else null:
	 * <ul>
	 * <li>the whole plot (cabin, porch and farm) is grass, dirt or sand, within a block of one height, and mostly
	 * exactly level, with nothing but air or plants above it;</li>
	 * <li>the ground around it is gentle too, so it isn't on a cliff edge or a terraced hillside;</li>
	 * <li>no player has built anything nearby or spent much time there, and it isn't near a village.</li>
	 * </ul>
	 */
	static Integer findFloor(ServerWorld world, Layout layout) {
		BlockPos centre = layout.at(0, 0, 0);
		if (!world.isChunkLoaded(centre)) {
			return null;
		}
		int floor = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()) - 1;
		if (floor <= world.getBottomY() + 4 || floor + 10 >= world.getTopY()) {
			return null;
		}
		int level = 0;
		int total = 0;
		for (int a = -HALF - MARGIN; a <= HALF + MARGIN; a++) {
			for (int b = -HALF - MARGIN; b <= PLOT_END + MARGIN; b++) {
				BlockPos column = layout.at(a, b, 0);
				if (!world.isChunkLoaded(column)) {
					return null;
				}
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
				BlockPos ground = new BlockPos(column.getX(), top, column.getZ());
				if (!inPlot(a, b)) {
					// Around the plot only steep drops and rises matter; tree trunks are fine.
					if (Math.abs(top - floor) > 2 && !world.getBlockState(ground).isIn(BlockTags.LOGS)) {
						return null;
					}
					// Except under the eaves, which need room to hang over.
					if (Math.abs(a) <= HALF + 1 && Math.abs(b) <= HALF + 1) {
						for (int y = Math.max(top + 1, floor + 3); y <= floor + ROOF_TOP + 1; y++) {
							if (!isClear(world, new BlockPos(column.getX(), y, column.getZ()))) {
								return null;
							}
						}
					}
					continue;
				}
				if (Math.abs(top - floor) > 1) {
					return null;
				}
				total++;
				if (top == floor) {
					level++;
				}
				BlockState groundState = world.getBlockState(ground);
				if (!world.getFluidState(ground).isEmpty() || !(groundState.isIn(BlockTags.DIRT) || groundState.isIn(BlockTags.SAND))) {
					return null;
				}
				for (int y = top + 1; y <= floor + ROOF_TOP + 1; y++) {
					if (!isClear(world, new BlockPos(column.getX(), y, column.getZ()))) {
						return null;
					}
				}
			}
		}
		if (level * 4 < total * 3) {
			return null;
		}
		return isUntouched(world, layout, floor) ? floor : null;
	}

	private static boolean inPlot(int a, int b) {
		return a >= -HALF && a <= HALF && b >= -HALF && b <= PLOT_END;
	}

	/** Nothing built by a player in or around the plot, little time spent there, and no village close by. */
	private static boolean isUntouched(ServerWorld world, Layout layout, int floor) {
		// The layout may not know the floor height yet, so every position here is put at it.
		BlockPos centre = layout.at(0, 0, 0);
		if (world.isNearOccupiedPointOfInterest(new BlockPos(centre.getX(), floor, centre.getZ()), 4)) {
			return false;
		}
		for (int a = -HALF; a <= HALF; a += HALF) {
			for (int b = -HALF; b <= PLOT_END; b += HALF) {
				if (world.getWorldChunk(layout.at(a, b, 0)).getInhabitedTime() > MAX_INHABITED_TICKS) {
					return false;
				}
			}
		}
		for (int a = -HALF - UNTOUCHED_RADIUS; a <= HALF + UNTOUCHED_RADIUS; a++) {
			for (int b = -HALF - UNTOUCHED_RADIUS; b <= PLOT_END + UNTOUCHED_RADIUS; b++) {
				BlockPos plotColumn = layout.at(a, b, 0);
				BlockPos column = new BlockPos(plotColumn.getX(), floor, plotColumn.getZ());
				if (!world.isChunkLoaded(column)) {
					return false;
				}
				for (int y = -4; y <= 8; y++) {
					if (isPlayerMade(world.getBlockState(column.up(y)))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Blocks that only show up on the surface because someone placed them. */
	private static boolean isPlayerMade(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		return state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.SLABS) || state.isIn(BlockTags.STAIRS) || state.isIn(BlockTags.WALLS)
				|| state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.DOORS)
				|| state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.BEDS) || state.isIn(BlockTags.SIGNS)
				|| state.isIn(BlockTags.BANNERS) || state.isIn(BlockTags.RAILS) || state.isIn(BlockTags.WOOL)
				|| state.isIn(BlockTags.IMPERMEABLE) || state.isIn(BlockTags.CAMPFIRES) || state.isIn(BlockTags.SHULKER_BOXES)
				|| state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH)
				|| state.isOf(Blocks.LANTERN) || state.isOf(Blocks.SOUL_LANTERN) || state.isOf(Blocks.CRAFTING_TABLE)
				|| state.isOf(Blocks.FURNACE) || state.isOf(Blocks.BLAST_FURNACE) || state.isOf(Blocks.SMOKER)
				|| state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL)
				|| state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.BRICKS)
				|| state.isOf(Blocks.GLASS_PANE) || state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.DIRT_PATH)
				|| state.isOf(Blocks.LADDER) || state.isOf(Blocks.BOOKSHELF) || state.isOf(Blocks.ANVIL);
	}

	/** Whether this player could see any part of the plot right now. */
	static boolean canSee(ServerPlayerEntity player, Layout layout) {
		for (int a = -HALF; a <= HALF; a += HALF) {
			for (int b = -HALF; b <= PLOT_END; b += HALF) {
				for (int y = 1; y <= 4; y += 3) {
					if (Observer.canSee(player, Vec3d.ofCenter(layout.at(a, b, y)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ---- Growing ----

	/** Called every {@link #CHECK_INTERVAL} ticks: builds the next stage if it's due and nobody is around to see it. */
	public static void tick(MinecraftServer server) {
		PeripheralConfig config = PeripheralConfig.get();
		if (!config.allowWorldChanges || !config.isEffectEnabled(OtherBaseEffect.NAME)) {
			return;
		}
		PeripheralWorldState state = PeripheralWorldState.get(server);
		if (state.otherBase() == null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		// Only the other you does any of this, and only while it's in the game.
		boolean online = OtherPlayerState.get(server).online;
		boolean tidy = online && !state.placedNearBase().isEmpty();
		boolean due = online && state.otherBaseStage() < MAX_STAGE && world.getTime() >= state.otherBaseNextStage();
		boolean snapshot = !state.hasBaseSnapshot();
		Layout known = state.otherBaseCentre() != null && state.otherBaseFront() != null
				? new Layout(state.otherBaseCentre(), state.otherBaseFront()) : null;
		boolean repair = online && known != null && OtherBaseRepair.needsRepair(world, state, known);
		if (!tidy && !due && !snapshot && !repair) {
			return;
		}
		Layout layout = layoutOf(world, state);
		if (layout == null) {
			return;
		}
		if (snapshot) {
			loadArea(world, layout);
			OtherBaseTidy.snapshot(world, state, layout);
		}
		if ((!tidy && !due && !repair) || isWatched(world, layout)) {
			return;
		}
		loadArea(world, layout);
		if (tidy) {
			OtherBaseTidy.tidy(world, state, layout);
		}
		// Whatever is broken (including anything the tidying just cleared away) is put back.
		OtherBaseRepair.repair(world, state, layout);
		if (due) {
			// Late stages catch up one at a time, so a base left alone for days is further along when the player is back.
			grow(world, state, layout, state.otherBaseNextStage() + stageTicks());
		}
	}

	/** Builds the next stage right now, whoever is watching. Returns the new stage, or -1 if it couldn't. */
	public static int forceGrow(MinecraftServer server) {
		PeripheralWorldState state = PeripheralWorldState.get(server);
		if (state.otherBase() == null || state.otherBaseStage() >= MAX_STAGE) {
			return -1;
		}
		ServerWorld world = server.getOverworld();
		Layout layout = layoutOf(world, state);
		if (layout == null) {
			return -1;
		}
		loadArea(world, layout);
		grow(world, state, layout, world.getTime() + stageTicks());
		return state.otherBaseStage();
	}

	private static void grow(ServerWorld world, PeripheralWorldState state, Layout layout, long nextStage) {
		int stage = state.otherBaseStage() + 1;
		if (stage == STAGE_PORTAL && OtherPlayerState.get(world.getServer()).toolTier < 2) {
			// The portal frame waits until it has iron tools.
			state.setOtherBaseStage(state.otherBaseStage(), nextStage);
			return;
		}
		Random random = world.getRandom();
		build(world, layout, OtherBaseStyle.of(state), stage, random);
		state.setOtherBaseStage(stage, nextStage);
		OtherBaseTidy.snapshot(world, state, layout);

		BlockPos door = layout.door();
		for (ServerPlayerEntity player : world.getPlayers()) {
			double distance = Math.sqrt(player.getBlockPos().getSquaredDistance(door));
			if (distance < HEARING) {
				playBuildingSounds(player, layout, random);
			}
			if (EffectDebug.isReporting(player)) {
				EffectDebug.report(player, OtherBaseEffect.NAME, door, "grew to stage " + stage + ": " + stageName(stage));
			}
		}
	}

	/** Reads the layout from the saved state; a base built before it could grow is worked out from its door. */
	static Layout layoutOf(ServerWorld world, PeripheralWorldState state) {
		if (state.otherBaseCentre() != null && state.otherBaseFront() != null) {
			return new Layout(state.otherBaseCentre(), state.otherBaseFront());
		}
		BlockPos door = state.otherBase();
		BlockState doorState = world.getBlockState(door);
		if (!(doorState.getBlock() instanceof DoorBlock)) {
			// Someone took the door out; leave the place as it is.
			state.setOtherBaseStage(MAX_STAGE, 0L);
			return null;
		}
		Direction front = doorState.get(DoorBlock.FACING).getOpposite();
		BlockPos centre = door.offset(front.getOpposite(), HALF).down();
		state.setOtherBase(door, centre, front, STAGE_CABIN, world.getTime());
		return new Layout(centre, front);
	}

	/**
	 * Loads the chunks around the base, so it can be built and tidied while everyone is far away. The server lets them
	 * unload again on its own once nobody needs them.
	 */
	static void loadArea(ServerWorld world, Layout layout) {
		BlockPos centre = layout.centre();
		for (int cx = (centre.getX() - AREA) >> 4; cx <= (centre.getX() + AREA) >> 4; cx++) {
			for (int cz = (centre.getZ() - AREA) >> 4; cz <= (centre.getZ() + AREA) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
	}

	/** Someone is close to the base, or near enough to have it on screen and looking its way. */
	static boolean isWatched(ServerWorld world, Layout layout) {
		BlockPos centre = layout.centre();
		double viewDistance = world.getServer().getPlayerManager().getViewDistance() * 16.0 + 16.0;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - centre.getX();
			double dz = player.getZ() - centre.getZ();
			double distanceSq = dx * dx + dz * dz;
			if (distanceSq < PRIVACY * PRIVACY) {
				return true;
			}
			// Beyond their view distance the base isn't even drawn, and looking would load chunks just to check.
			if (distanceSq < viewDistance * viewDistance && canSee(player, layout)) {
				return true;
			}
		}
		return false;
	}

	/** A few seconds of someone at work: wood going down, a few hits. Only heard, from wherever the player is. */
	static void playBuildingSounds(ServerPlayerEntity player, Layout layout, Random random) {
		int count = 4 + random.nextInt(4);
		int delay = 0;
		for (int i = 0; i < count; i++) {
			Vec3d at = Vec3d.ofCenter(layout.at(random.nextBetween(-HALF, HALF), random.nextBetween(-HALF, HALF), 1 + random.nextInt(3)));
			BlockSoundGroup group = random.nextInt(3) == 0 ? BlockSoundGroup.STONE : BlockSoundGroup.WOOD;
			SoundEvent sound = random.nextBoolean() ? group.getPlaceSound() : group.getHitSound();
			PresenceDirector.INSTANCE.scheduler().schedule(delay, () -> EffectSounds.playTo(player, sound, at, 3.0f, group.getPitch() * 0.8f));
			delay += 10 + random.nextInt(25);
		}
	}

	// ---- The stages ----

	static void build(ServerWorld world, Layout layout, OtherBaseStyle style, int stage, Random random) {
		switch (stage) {
			case 0 -> buildSite(world, layout, style, random);
			case 1 -> buildFooting(world, layout, style);
			case 2 -> buildWalls(world, layout, style);
			case 3 -> buildTopBeam(world, layout, style);
			case 4 -> buildRoof(world, layout, style, 0, 1);
			case 5 -> buildRoof(world, layout, style, 2, 4);
			case 6 -> buildFinishing(world, layout, style);
			case 7 -> buildInterior(world, layout, style, random);
			case 8 -> buildYard(world, layout, style, random);
			case 9 -> buildSettled(world, layout, style, random);
			case 10 -> buildGarden(world, layout, style, random);
			case 11 -> buildAnnex(world, layout, style);
			default -> buildPortalFrame(world, layout);
		}
	}

	private static boolean isWall(int a, int b) {
		return Math.abs(a) <= HALF && Math.abs(b) <= HALF && (Math.abs(a) == HALF || Math.abs(b) == HALF);
	}

	private static boolean isCorner(int a, int b) {
		return Math.abs(a) == HALF && Math.abs(b) == HALF;
	}

	private static boolean isDoorway(int a, int b, int y) {
		return a == 0 && b == HALF && y <= 2;
	}

	/** One window in each side wall and the back, one either side of the door, and one high in each gable. */
	private static boolean isWindow(int a, int b, int y) {
		if (y == 5) {
			return a == 0 && Math.abs(b) == HALF;
		}
		return y == 2 && ((Math.abs(a) == HALF && b == 0) || (a == 0 && b == -HALF) || (Math.abs(a) == 2 && b == HALF));
	}

	/**
	 * Stage 0: the whole plot levelled to one height (the ground is already within a block of it), the cabin's floor
	 * laid, a crafting table, a chest of what they had on them, and a campfire.
	 */
	private static void buildSite(ServerWorld world, Layout layout, OtherBaseStyle style, Random random) {
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= PLOT_END; b++) {
				for (int y = 1; y <= ROOF_TOP + 1; y++) {
					clear(world, layout.at(a, b, y));
				}
				BlockPos ground = layout.at(a, b, 0);
				fillBelow(world, ground, Blocks.DIRT.getDefaultState());
				boolean cabin = Math.abs(a) <= HALF && Math.abs(b) <= HALF;
				if (cabin) {
					setGround(world, ground, (isWall(a, b) ? style.stone() : style.wood().planks).getDefaultState());
				} else if (isClear(world, ground)) {
					world.setBlockState(ground, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		// Room for the eaves that will hang over the sides and back.
		for (int a = -HALF - 1; a <= HALF + 1; a++) {
			for (int b = -HALF - 1; b <= HALF + 1; b++) {
				if (!inPlot(a, b)) {
					for (int y = 3; y <= ROOF_TOP + 1; y++) {
						clear(world, layout.at(a, b, y));
					}
				}
			}
		}
		Direction front = layout.front();
		placeSupported(world, layout.at(1, -2, 1), Blocks.CRAFTING_TABLE.getDefaultState());
		placeSupported(world, layout.at(2, -2, 1), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, front));
		place(world, layout.at(-2, 2, 1), Blocks.TORCH.getDefaultState());
		placeSupported(world, layout.at(-2, HALF + 2, 1),
				Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, layout.right()).with(CampfireBlock.LIT, true));

		ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
		pickaxe.setDamage(10 + random.nextInt(40));
		stock(world, layout.at(2, -2, 1), random,
				new ItemStack(style.wood().log, 4 + random.nextInt(7)),
				new ItemStack(Items.STICK, 2 + random.nextInt(5)),
				new ItemStack(Items.APPLE, 1 + random.nextInt(2)),
				pickaxe);
	}

	/** Stage 1: a log post at each corner, full height, and a course of stone between them. */
	private static void buildFooting(ServerWorld world, Layout layout, OtherBaseStyle style) {
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				if (isCorner(a, b)) {
					for (int y = 1; y <= 3; y++) {
						placeSupported(world, layout.at(a, b, y), style.wood().log.getDefaultState());
					}
				} else if (isWall(a, b) && !isDoorway(a, b, 1)) {
					placeSupported(world, layout.at(a, b, 1), style.stone().getDefaultState());
				}
			}
		}
	}

	/** Stage 2: a row of planks on the stone, with gaps left for the windows, and a bed in the corner. */
	private static void buildWalls(ServerWorld world, Layout layout, OtherBaseStyle style) {
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				if (isWall(a, b) && !isCorner(a, b) && !isDoorway(a, b, 2) && !isWindow(a, b, 2)) {
					placeSupported(world, layout.at(a, b, 2), style.wood().planks.getDefaultState());
				}
			}
		}
		placeBed(world, layout);
	}

	/** Stage 3: logs laid along the top of every wall, over the door and the window gaps, tying the corners together. */
	private static void buildTopBeam(ServerWorld world, Layout layout, OtherBaseStyle style) {
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				if (isWall(a, b) && !isCorner(a, b)) {
					Direction.Axis axis = Math.abs(a) == HALF ? layout.front().getAxis() : layout.right().getAxis();
					place(world, layout.at(a, b, 3), style.wood().log.getDefaultState().with(PillarBlock.AXIS, axis));
				}
			}
		}
	}

	/**
	 * Stages 4 and 5: a pitched roof of stairs, ridge running front to back, hanging one block over every wall. The
	 * first half fills in the gable ends and lays the lowest two courses; the second lays the rest and a log ridge.
	 * Courses are counted from the eaves; course 4 is the ridge. Nothing goes up until all four corners stand.
	 */
	private static void buildRoof(ServerWorld world, Layout layout, OtherBaseStyle style, int firstCourse, int lastCourse) {
		for (int a = -HALF; a <= HALF; a += 2 * HALF) {
			for (int b = -HALF; b <= HALF; b += 2 * HALF) {
				if (!world.getBlockState(layout.at(a, b, 3)).isIn(BlockTags.LOGS)) {
					return;
				}
			}
		}
		if (firstCourse == 0) {
			// The gable ends: a triangle of planks over the front and back walls.
			for (int b = -HALF; b <= HALF; b += 2 * HALF) {
				for (int y = 4; y <= ROOF_TOP - 1; y++) {
					int reach = ROOF_TOP - 1 - y;
					for (int a = -reach; a <= reach; a++) {
						if (!isWindow(a, b, y)) {
							place(world, layout.at(a, b, y), style.wood().planks.getDefaultState());
						}
					}
				}
			}
		}
		for (int course = firstCourse; course <= lastCourse; course++) {
			for (int b = -HALF - 1; b <= HALF + 1; b++) {
				if (course == 4) {
					place(world, layout.at(0, b, ROOF_TOP),
							style.wood().log.getDefaultState().with(PillarBlock.AXIS, layout.front().getAxis()));
					continue;
				}
				int out = HALF + 1 - course;
				place(world, layout.at(-out, b, 3 + course), roofStair(style, layout.right()));
				place(world, layout.at(out, b, 3 + course), roofStair(style, layout.right().getOpposite()));
			}
		}
	}

	/** A roof stair rising toward {@code up}. */
	private static BlockState roofStair(OtherBaseStyle style, Direction up) {
		return style.wood().stairs.getDefaultState().with(StairsBlock.FACING, up).with(StairsBlock.HALF, BlockHalf.BOTTOM);
	}

	/** Stage 6: the door hung, glass in the windows, shutters, and torches inside and either side of the door. */
	private static void buildFinishing(ServerWorld world, Layout layout, OtherBaseStyle style) {
		Direction front = layout.front();
		BlockPos lower = layout.door();
		if (isClear(world, lower) && isClear(world, lower.up()) && isSolidBelow(world, lower)) {
			BlockState door = style.wood().door.getDefaultState().with(DoorBlock.FACING, front.getOpposite());
			world.setBlockState(lower, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.NOTIFY_LISTENERS);
			world.setBlockState(lower.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_LISTENERS);
		}
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				for (int y = 2; y <= 5; y += 3) {
					if (isWall(a, b) && isWindow(a, b, y)) {
						glaze(world, layout.at(a, b, y));
					}
				}
			}
		}
		// Open shutters either side of the windows that look out, flat against the wall.
		BlockState shutter = style.wood().trapdoor.getDefaultState().with(TrapdoorBlock.OPEN, true);
		for (int side = -1; side <= 1; side += 2) {
			shutter(world, layout.at(-HALF - 1, side, 2), shutter.with(TrapdoorBlock.FACING, layout.right().getOpposite()));
			shutter(world, layout.at(side, -HALF - 1, 2), shutter.with(TrapdoorBlock.FACING, front.getOpposite()));
		}
		place(world, layout.at(2, 1, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, layout.right().getOpposite()));
		for (int side = -1; side <= 1; side += 2) {
			place(world, layout.at(side, HALF + 1, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, front));
		}
	}

	/** A shutter only goes up beside a wall that's there to hold it. */
	private static void shutter(ServerWorld world, BlockPos pos, BlockState state) {
		BlockPos wall = pos.offset(state.get(TrapdoorBlock.FACING).getOpposite());
		if (!world.getBlockState(wall).isAir()) {
			place(world, pos, state);
		}
	}

	/** A glass pane in a gap, joined up to the wall either side. */
	private static void glaze(ServerWorld world, BlockPos pos) {
		BlockState pane = Blocks.GLASS_PANE.getDefaultState();
		if (place(world, pos, pane)) {
			world.setBlockState(pos, Block.postProcessState(pane, world, pos), Block.NOTIFY_LISTENERS);
		}
	}

	/** Stage 7: a furnace with cooked food left in it, a lantern hung from the ridge, a rug, and more in the chest. */
	private static void buildInterior(ServerWorld world, Layout layout, OtherBaseStyle style, Random random) {
		BlockPos furnacePos = layout.at(0, -2, 1);
		placeSupported(world, furnacePos, Blocks.FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, layout.front()));
		if (world.getBlockEntity(furnacePos) instanceof FurnaceBlockEntity furnace && furnace.getStack(2).isEmpty()) {
			furnace.setStack(2, new ItemStack(style.cookedFood(random.nextBoolean()), 2 + random.nextInt(3)));
			furnace.markDirty();
		}
		place(world, layout.at(0, 0, ROOF_TOP - 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		place(world, layout.at(0, -HALF + 1, ROOF_TOP - 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		// A rug the colour of the bed.
		DyeColor colour = world.getBlockState(layout.at(-2, -1, 1)).getBlock() instanceof BedBlock bed ? bed.getColor() : DyeColor.RED;
		Block carpet = Registries.BLOCK.get(new Identifier(colour.getName() + "_carpet"));
		if (carpet != Blocks.AIR) {
			for (int a = -1; a <= 0; a++) {
				for (int b = 0; b <= 1; b++) {
					place(world, layout.at(a, b, 1), carpet.getDefaultState());
				}
			}
		}

		stock(world, layout.at(2, -2, 1), random,
				new ItemStack(Items.BREAD, 2 + random.nextInt(4)),
				new ItemStack(Items.COAL, 4 + random.nextInt(9)),
				new ItemStack(style.stone(), 20 + random.nextInt(40)),
				new ItemStack(Items.RAW_IRON, 2 + random.nextInt(4)));
	}

	/** Stage 8: out front, a 3x3 wheat farm or, where animals graze nearby, a fenced pen with two of them in it. */
	private static void buildYard(ServerWorld world, Layout layout, OtherBaseStyle style, Random random) {
		if (style.yard() == null) {
			buildFarm(world, layout, random);
		} else {
			buildFence(world, layout, style);
			addAnimals(world, layout, style.yard(), 2, false, random);
		}
		stock(world, layout.at(2, -2, 1), random,
				style.yard() == null ? new ItemStack(Items.WHEAT_SEEDS, 3 + random.nextInt(8)) : new ItemStack(Items.WHEAT, 4 + random.nextInt(8)));
	}

	/**
	 * Stage 9: settled in. The farm gets its fence and ripens, or the pen has a young one; torches go on the corner
	 * posts and a path is worn in front of the door.
	 */
	private static void buildSettled(ServerWorld world, Layout layout, OtherBaseStyle style, Random random) {
		if (style.yard() == null) {
			buildFence(world, layout, style);
			for (int a = 0; a <= 2; a++) {
				for (int b = HALF + 3; b <= HALF + 5; b++) {
					BlockPos crop = layout.at(a, b, 1);
					if (world.getBlockState(crop).isOf(Blocks.WHEAT)) {
						world.setBlockState(crop, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, CropBlock.MAX_AGE), Block.NOTIFY_LISTENERS);
					}
				}
			}
		} else {
			addAnimals(world, layout, style.yard(), 1, true, random);
		}

		for (int a = -1; a <= 3; a += 4) {
			for (int b = HALF + 2; b <= PLOT_END; b += PLOT_END - HALF - 2) {
				BlockPos post = layout.at(a, b, 1);
				if (world.getBlockState(post).isIn(BlockTags.FENCES)) {
					place(world, post.up(), Blocks.TORCH.getDefaultState());
				}
			}
		}
		for (int a = -2; a <= 1; a++) {
			BlockPos path = layout.at(a, HALF + 1, 0);
			if (world.getBlockState(path).isOf(Blocks.GRASS_BLOCK) || world.getBlockState(path).isOf(Blocks.DIRT)) {
				if (isClear(world, path.up())) {
					world.setBlockState(path, Blocks.DIRT_PATH.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}

		stock(world, layout.at(2, -2, 1), random,
				new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(5)),
				new ItemStack(Items.TORCH, 8 + random.nextInt(12)),
				new ItemStack(style.animalProduct(), 3 + random.nextInt(6)),
				new ItemStack(Items.BREAD, 2 + random.nextInt(3)));
	}

	/** Stage 10: flowers planted along the front of the cabin, and a lamp post by the campfire. */
	private static void buildGarden(ServerWorld world, Layout layout, OtherBaseStyle style, Random random) {
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET};
		Block flower = flowers[random.nextInt(flowers.length)];
		for (int a : new int[] {-HALF, 2, HALF}) {
			place(world, layout.at(a, HALF + 1, 1), flower.getDefaultState());
		}
		BlockPos post = layout.at(-HALF, HALF + 4, 1);
		if (placeSupported(world, post, style.wood().fence.getDefaultState())
				&& place(world, post.up(), style.wood().fence.getDefaultState())) {
			place(world, post.up(2), Blocks.LANTERN.getDefaultState());
		}
	}

	/**
	 * Stage 11: a lean-to on the right of the cabin, under the eaves, through a doorway knocked in the wall, with
	 * barrels and a smoker. Skipped if the ground there won't take it.
	 */
	private static void buildAnnex(ServerWorld world, Layout layout, OtherBaseStyle style) {
		int a0 = HALF + 1;
		int a1 = HALF + 5;
		int b0 = -1;
		int b1 = HALF;
		// Right against the cabin only the walls' height has to be clear; the eaves are already overhead.
		if (!isBuildable(world, layout, a0 + 1, a1, b0, b1, 5) || !isBuildable(world, layout, a0, a0, b0, b1, 2)) {
			return;
		}
		level(world, layout, a0, a1, b0, b1, Blocks.DIRT);
		for (int a = a0; a <= a1; a++) {
			for (int b = b0; b <= b1; b++) {
				boolean edge = a == a1 || b == b0 || b == b1;
				boolean corner = a == a1 && (b == b0 || b == b1);
				setGround(world, layout.at(a, b, 0), (edge ? style.stone() : style.wood().planks).getDefaultState());
				if (corner) {
					for (int y = 1; y <= 2; y++) {
						placeSupported(world, layout.at(a, b, y), style.wood().log.getDefaultState());
					}
				} else if (edge) {
					placeSupported(world, layout.at(a, b, 1), style.stone().getDefaultState());
					placeSupported(world, layout.at(a, b, 2), style.wood().planks.getDefaultState());
				}
			}
		}
		for (int b = b0; b <= b1; b++) {
			place(world, layout.at(a0, b, 3), roofStair(style, layout.right().getOpposite()));
			for (int a = a0 + 1; a <= a1; a++) {
				place(world, layout.at(a, b, 3), style.wood().slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		BlockPos window = layout.at(a1, 1, 2);
		if (world.getBlockState(window).isOf(style.wood().planks)) {
			world.setBlockState(window, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			glaze(world, window);
		}
		// The doorway from the cabin.
		for (int y = 1; y <= 2; y++) {
			replaceWith(world, layout.at(HALF, 2, y), style.wood().planks, Blocks.AIR.getDefaultState());
			replaceWith(world, layout.at(HALF, 2, y), style.stone(), Blocks.AIR.getDefaultState());
		}
		placeSupported(world, layout.at(a1 - 1, 0, 1), Blocks.BARREL.getDefaultState());
		placeSupported(world, layout.at(a1 - 1, 1, 1), Blocks.BARREL.getDefaultState());
		placeSupported(world, layout.at(a1 - 1, 2, 1), Blocks.SMOKER.getDefaultState().with(AbstractFurnaceBlock.FACING, layout.right().getOpposite()));
		place(world, layout.at(a0 + 1, 0, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, layout.front()));
	}

	/** Stage 12: an obsidian portal frame out front, never lit. Skipped if the ground there won't take it. */
	private static void buildPortalFrame(ServerWorld world, Layout layout) {
		int a = HALF + 3;
		int b0 = HALF + 2;
		int b1 = HALF + 5;
		if (!isBuildable(world, layout, a, a, b0, b1, 5)) {
			return;
		}
		level(world, layout, a, a, b0, b1, Blocks.DIRT);
		BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
		for (int b = b0 + 1; b <= b1 - 1; b++) {
			place(world, layout.at(a, b, 1), obsidian);
			place(world, layout.at(a, b, 5), obsidian);
		}
		for (int y = 2; y <= 4; y++) {
			place(world, layout.at(a, b0, y), obsidian);
			place(world, layout.at(a, b1, y), obsidian);
		}
	}

	/** A 3x3 wheat farm around a water block, in front of the cabin. */
	private static void buildFarm(ServerWorld world, Layout layout, Random random) {
		BlockPos water = layout.at(1, HALF + 4, 0);
		for (int a = 0; a <= 2; a++) {
			for (int b = HALF + 3; b <= HALF + 5; b++) {
				BlockPos soil = layout.at(a, b, 0);
				if (soil.equals(water)) {
					continue;
				}
				if (isClear(world, soil.up()) && setGround(world, soil,
						Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE))) {
					place(world, soil.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, random.nextInt(6)));
				}
			}
		}
		if (isNaturalGround(world, water) && isClear(world, water.up()) && holdsWater(world, water)) {
			world.setBlockState(water, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** A fence around the yard in front of the cabin, with a gate facing the door. */
	private static void buildFence(ServerWorld world, Layout layout, OtherBaseStyle style) {
		int nearRow = HALF + 2;
		for (int a = -1; a <= 3; a++) {
			for (int b = nearRow; b <= PLOT_END; b++) {
				boolean ring = a == -1 || a == 3 || b == nearRow || b == PLOT_END;
				if (!ring) {
					continue;
				}
				BlockPos post = layout.at(a, b, 1);
				if (a == 1 && b == nearRow) {
					placeSupported(world, post, style.wood().gate.getDefaultState().with(FenceGateBlock.FACING, layout.front()));
				} else {
					placeSupported(world, post, style.wood().fence.getDefaultState());
				}
			}
		}
		// Fences and the gate join up with their neighbours, as they would if placed by hand.
		for (int a = -1; a <= 3; a++) {
			for (int b = nearRow; b <= PLOT_END; b++) {
				BlockPos pos = layout.at(a, b, 1);
				BlockState state = world.getBlockState(pos);
				if (state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.FENCE_GATES)) {
					world.setBlockState(pos, Block.postProcessState(state, world, pos), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	/** Animals inside the fence, kept for good: they never despawn. */
	private static void addAnimals(ServerWorld world, Layout layout, EntityType<?> type, int count, boolean baby, Random random) {
		for (int i = 0; i < count; i++) {
			BlockPos spot = layout.at(random.nextBetween(0, 2), random.nextBetween(HALF + 3, HALF + 5), 1);
			if (!isClear(world, spot) || !(type.create(world) instanceof AnimalEntity animal)) {
				continue;
			}
			animal.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
			animal.setPersistent();
			if (baby) {
				animal.setBreedingAge(-24000);
			}
			world.spawnEntity(animal);
		}
	}

	/** The bed goes in the back corner: like a nearby player's own bed if one is loaded, otherwise red. */
	private static void placeBed(ServerWorld world, Layout layout) {
		BlockPos foot = layout.at(-2, -1, 1);
		BlockPos head = layout.at(-2, -2, 1);
		if (!isClear(world, foot) || !isClear(world, head) || !isSolidBelow(world, foot) || !isSolidBelow(world, head)) {
			return;
		}
		BlockState bed = bedLike(world).with(BedBlock.FACING, layout.front().getOpposite());
		world.setBlockState(foot, bed.with(BedBlock.PART, BedPart.FOOT), Block.NOTIFY_LISTENERS);
		world.setBlockState(head, bed.with(BedBlock.PART, BedPart.HEAD), Block.NOTIFY_LISTENERS);
	}

	private static BlockState bedLike(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			BlockPos theirs = Home.of(player);
			if (theirs != null && world.isChunkLoaded(theirs) && world.getBlockState(theirs).getBlock() instanceof BedBlock bed) {
				return bed.getDefaultState();
			}
		}
		return Blocks.RED_BED.getDefaultState();
	}

	// ---- Building beyond the plot ----

	/** Whether the ground over this rectangle is natural, within a block of the cabin floor, and open {@code height} blocks above. */
	static boolean isBuildable(ServerWorld world, Layout layout, int a0, int a1, int b0, int b1, int height) {
		int floor = layout.centre().getY();
		for (int a = a0; a <= a1; a++) {
			for (int b = b0; b <= b1; b++) {
				BlockPos column = layout.at(a, b, 0);
				if (!world.isChunkLoaded(column)) {
					return false;
				}
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
				if (Math.abs(top - floor) > 1 || !isNaturalGround(world, new BlockPos(column.getX(), top, column.getZ()))) {
					return false;
				}
				for (int y = top + 1; y <= floor + height; y++) {
					if (!isClear(world, new BlockPos(column.getX(), y, column.getZ()))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Levels a rectangle to the cabin floor, filling gaps with {@code fill}. */
	static void level(ServerWorld world, Layout layout, int a0, int a1, int b0, int b1, Block fill) {
		for (int a = a0; a <= a1; a++) {
			for (int b = b0; b <= b1; b++) {
				for (int y = 1; y <= 5; y++) {
					clear(world, layout.at(a, b, y));
				}
				BlockPos ground = layout.at(a, b, 0);
				fillBelow(world, ground, fill.getDefaultState());
				if (isClear(world, ground)) {
					world.setBlockState(ground, fill.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	// ---- Block helpers ----

	/** Air, plants, snow layers: things a block can simply go in place of. */
	static boolean isClear(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return world.getFluidState(pos).isEmpty() && (state.isAir() || state.isReplaceable());
	}

	/** Ground nobody built: dirt, grass, sand, gravel, stone. */
	static boolean isNaturalGround(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return world.getFluidState(pos).isEmpty() && world.getBlockEntity(pos) == null
				&& (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.BASE_STONE_OVERWORLD)
				|| state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK));
	}

	static boolean isSolidBelow(ServerWorld world, BlockPos pos) {
		BlockPos below = pos.down();
		return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
	}

	/** Places a block into air or plants if it can stand there. Only listeners are told, so nothing pops off mid-build. */
	static boolean place(ServerWorld world, BlockPos pos, BlockState state) {
		if (!isClear(world, pos) || !state.canPlaceAt(world, pos)) {
			return false;
		}
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		return true;
	}

	/** Swaps one block for another, if the expected block is still there. */
	private static void replaceWith(ServerWorld world, BlockPos pos, Block expected, BlockState state) {
		if (world.getBlockState(pos).isOf(expected)) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		}
	}

	/** Same, but only on top of something solid, so nothing is ever left floating. */
	static boolean placeSupported(ServerWorld world, BlockPos pos, BlockState state) {
		return isSolidBelow(world, pos) && place(world, pos, state);
	}

	/** Swaps a ground block (or fills a gap in the ground) at the plot's level. */
	static boolean setGround(ServerWorld world, BlockPos pos, BlockState state) {
		if (!isNaturalGround(world, pos) && !isClear(world, pos)) {
			return false;
		}
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		return true;
	}

	/** Clears plants and natural ground above the plot's level; anything a player built stays. */
	static void clear(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.isAir() && (isClear(world, pos) || isNaturalGround(world, pos))) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** Fills any gap under the plot's level down to the ground, up to three blocks. */
	static void fillBelow(ServerWorld world, BlockPos pos, BlockState fill) {
		for (int y = 1; y <= 3; y++) {
			BlockPos below = pos.down(y);
			if (!isClear(world, below)) {
				return;
			}
			world.setBlockState(below, fill, Block.NOTIFY_LISTENERS);
		}
	}

	static boolean holdsWater(ServerWorld world, BlockPos pos) {
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			BlockState state = world.getBlockState(pos.offset(direction));
			if (state.isAir() || state.isReplaceable()) {
				return false;
			}
		}
		return true;
	}

	/** Puts items into empty slots of the chest, if it's still there. */
	private static void stock(ServerWorld world, BlockPos pos, Random random, ItemStack... items) {
		BlockEntity entity = world.getBlockEntity(pos);
		if (!(entity instanceof ChestBlockEntity chest)) {
			return;
		}
		for (ItemStack item : items) {
			int start = random.nextInt(chest.size());
			for (int i = 0; i < chest.size(); i++) {
				int slot = (start + i) % chest.size();
				if (chest.getStack(slot).isEmpty()) {
					chest.setStack(slot, item);
					break;
				}
			}
		}
		chest.markDirty();
	}
}
