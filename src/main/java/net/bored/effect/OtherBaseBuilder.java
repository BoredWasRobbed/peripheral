package net.bored.effect;

import net.bored.config.PeripheralConfig;
import net.bored.presence.EffectDebug;
import net.bored.presence.Home;
import net.bored.presence.Observer;
import net.bored.presence.PresenceDirector;
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
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * The other base, built the way a player builds one: first a cleared spot with a crafting table and a campfire,
 * then walls going up, a roof, a furnace and a little farm, and finally a fence and torches. One stage goes up per
 * in-game day of play, only while nobody is near it or looking, so it changes between visits. Every stage only
 * places blocks where there is air, plants or natural ground, so nothing a player put there is ever replaced.
 */
public final class OtherBaseBuilder {
	public static final int CHECK_INTERVAL = 30 * 20;
	public static final int MAX_STAGE = 4;
	/** The cabin is 7x7 around its centre. In front of the door: a step, the campfire, and the farm. */
	static final int HALF = 3;
	private static final int FLAT_ROWS = HALF + 2;
	private static final int FARM_ROWS = HALF + 6;
	/** Nobody may be closer than this, horizontally, while a stage goes up. */
	private static final double PRIVACY = 40.0;
	/** Players this close hear it being built. */
	private static final double HEARING = 80.0;
	private static final String[] STAGE_NAMES = {
			"a cleared spot with a crafting table, chest and campfire",
			"walls going up and a bed",
			"walls, roof, door and torches",
			"a furnace with food in it and a small wheat farm",
			"a fenced farm, torch posts and a path"};

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

	/** The floor height if the plot is flat, dry and open; else null. The cabin and porch must be flat to a block, the farm to two. */
	static Integer findFloor(ServerWorld world, Layout layout) {
		BlockPos centre = layout.at(0, 0, 0);
		if (!world.isChunkLoaded(centre)) {
			return null;
		}
		int floor = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()) - 1;
		if (floor <= world.getBottomY() + 4 || floor + 6 >= world.getTopY()) {
			return null;
		}
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= FARM_ROWS; b++) {
				boolean farm = b > FLAT_ROWS;
				if (farm && (a < -1 || a > 3)) {
					continue;
				}
				BlockPos column = layout.at(a, b, 0);
				if (!world.isChunkLoaded(column)) {
					return null;
				}
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
				if (Math.abs(top - floor) > (farm ? 2 : 1)) {
					return null;
				}
				BlockPos ground = new BlockPos(column.getX(), top, column.getZ());
				if (!world.getFluidState(ground).isEmpty() || !world.getBlockState(ground).isSideSolidFullSquare(world, ground, Direction.UP)) {
					return null;
				}
				for (int y = top + 1; y <= floor + (farm ? 3 : 5); y++) {
					if (!isClear(world, new BlockPos(column.getX(), y, column.getZ()))) {
						return null;
					}
				}
			}
		}
		return floor;
	}

	/** Whether this player could see any part of the plot right now. */
	static boolean canSee(ServerPlayerEntity player, Layout layout) {
		for (int a = -HALF; a <= HALF; a += HALF) {
			for (int b = -HALF; b <= FARM_ROWS; b += HALF) {
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
		if (state.otherBase() == null || state.otherBaseStage() >= MAX_STAGE) {
			return;
		}
		ServerWorld world = server.getOverworld();
		if (world.getTime() < state.otherBaseNextStage()) {
			return;
		}
		Layout layout = layoutOf(world, state);
		if (layout == null || !isLoaded(world, layout) || isWatched(world, layout)) {
			return;
		}
		// Late stages catch up one at a time, so a base left alone for days is further along when the player is back.
		grow(world, state, layout, state.otherBaseNextStage() + stageTicks());
	}

	/** Builds the next stage right now, whoever is watching. Returns the new stage, or -1 if it couldn't. */
	public static int forceGrow(MinecraftServer server) {
		PeripheralWorldState state = PeripheralWorldState.get(server);
		if (state.otherBase() == null || state.otherBaseStage() >= MAX_STAGE) {
			return -1;
		}
		ServerWorld world = server.getOverworld();
		Layout layout = layoutOf(world, state);
		if (layout == null || !isLoaded(world, layout)) {
			return -1;
		}
		grow(world, state, layout, world.getTime() + stageTicks());
		return state.otherBaseStage();
	}

	private static void grow(ServerWorld world, PeripheralWorldState state, Layout layout, long nextStage) {
		int stage = state.otherBaseStage() + 1;
		Random random = world.getRandom();
		build(world, layout, stage, random);
		state.setOtherBaseStage(stage, nextStage);

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
	private static Layout layoutOf(ServerWorld world, PeripheralWorldState state) {
		if (state.otherBaseCentre() != null && state.otherBaseFront() != null) {
			return new Layout(state.otherBaseCentre(), state.otherBaseFront());
		}
		BlockPos door = state.otherBase();
		if (!world.isChunkLoaded(door)) {
			return null;
		}
		BlockState doorState = world.getBlockState(door);
		if (!(doorState.getBlock() instanceof DoorBlock)) {
			// Someone took the door out; leave the place as it is.
			state.setOtherBaseStage(MAX_STAGE, 0L);
			return null;
		}
		Direction front = doorState.get(DoorBlock.FACING).getOpposite();
		BlockPos centre = door.offset(front.getOpposite(), HALF).down();
		state.setOtherBase(door, centre, front, 2, world.getTime());
		return new Layout(centre, front);
	}

	private static boolean isLoaded(ServerWorld world, Layout layout) {
		return world.isChunkLoaded(layout.at(-HALF, -HALF, 0)) && world.isChunkLoaded(layout.at(HALF, -HALF, 0))
				&& world.isChunkLoaded(layout.at(-HALF, FARM_ROWS, 0)) && world.isChunkLoaded(layout.at(HALF, FARM_ROWS, 0));
	}

	private static boolean isWatched(ServerWorld world, Layout layout) {
		BlockPos centre = layout.centre();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - centre.getX();
			double dz = player.getZ() - centre.getZ();
			if (dx * dx + dz * dz < PRIVACY * PRIVACY || canSee(player, layout)) {
				return true;
			}
		}
		return false;
	}

	/** A few seconds of someone at work: wood going down, a few hits. Only heard, from wherever the player is. */
	private static void playBuildingSounds(ServerPlayerEntity player, Layout layout, Random random) {
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

	static void build(ServerWorld world, Layout layout, int stage, Random random) {
		switch (stage) {
			case 0 -> buildSite(world, layout, random);
			case 1 -> buildLowWalls(world, layout);
			case 2 -> buildCabin(world, layout);
			case 3 -> buildLivedIn(world, layout, random);
			default -> buildSettled(world, layout, random);
		}
	}

	/** Stage 0: the spot levelled and floored, a crafting table, a chest of what they had on them, and a campfire. */
	private static void buildSite(ServerWorld world, Layout layout, Random random) {
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				for (int y = 1; y <= 4; y++) {
					clear(world, layout.at(a, b, y));
				}
				boolean edge = Math.abs(a) == HALF || Math.abs(b) == HALF;
				BlockPos floor = layout.at(a, b, 0);
				fillBelow(world, floor, Blocks.COBBLESTONE.getDefaultState());
				place(world, floor, (edge ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).getDefaultState());
			}
		}
		for (int y = 1; y <= 2; y++) {
			clear(world, layout.at(0, HALF + 1, y));
		}
		Direction front = layout.front();
		place(world, layout.at(1, -2, 1), Blocks.CRAFTING_TABLE.getDefaultState());
		place(world, layout.at(2, -2, 1), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, front));
		place(world, layout.at(-2, 2, 1), Blocks.TORCH.getDefaultState());
		BlockPos campfireColumn = layout.at(-2, HALF + 2, 0);
		int campfireY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, campfireColumn.getX(), campfireColumn.getZ());
		place(world, new BlockPos(campfireColumn.getX(), campfireY, campfireColumn.getZ()),
				Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, layout.right()).with(CampfireBlock.LIT, true));

		ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
		pickaxe.setDamage(10 + random.nextInt(40));
		stock(world, layout.at(2, -2, 1), random,
				new ItemStack(Items.OAK_LOG, 4 + random.nextInt(7)),
				new ItemStack(Items.STICK, 2 + random.nextInt(5)),
				new ItemStack(Items.APPLE, 1 + random.nextInt(2)),
				pickaxe);
	}

	/** Stage 1: log corners up to full height, the first row of planks, and a bed. */
	private static void buildLowWalls(ServerWorld world, Layout layout) {
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				boolean corner = Math.abs(a) == HALF && Math.abs(b) == HALF;
				boolean edge = Math.abs(a) == HALF || Math.abs(b) == HALF;
				if (corner) {
					for (int y = 1; y <= 3; y++) {
						place(world, layout.at(a, b, y), Blocks.OAK_LOG.getDefaultState());
					}
				} else if (edge && !(a == 0 && b == HALF)) {
					place(world, layout.at(a, b, 1), Blocks.OAK_PLANKS.getDefaultState());
				}
			}
		}
		placeBed(world, layout);
	}

	/** Stage 2: the rest of the walls with windows, the door, a roof, and torches inside and out. */
	private static void buildCabin(ServerWorld world, Layout layout) {
		Direction front = layout.front();
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				boolean edge = Math.abs(a) == HALF || Math.abs(b) == HALF;
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						boolean doorway = a == 0 && b == HALF && y <= 2;
						if (!doorway) {
							place(world, layout.at(a, b, y), Blocks.OAK_PLANKS.getDefaultState());
						}
					}
				}
				place(world, layout.at(a, b, 4), Blocks.OAK_PLANKS.getDefaultState());
			}
		}
		replaceWith(world, layout.at(-HALF, 0, 2), Blocks.OAK_PLANKS, Blocks.GLASS.getDefaultState());
		replaceWith(world, layout.at(HALF, 0, 2), Blocks.OAK_PLANKS, Blocks.GLASS.getDefaultState());
		replaceWith(world, layout.at(0, -HALF, 2), Blocks.OAK_PLANKS, Blocks.GLASS.getDefaultState());

		BlockPos lower = layout.at(0, HALF, 1);
		if (canReplace(world, lower) && canReplace(world, lower.up())) {
			BlockState door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, front.getOpposite());
			place(world, lower, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			place(world, lower.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}
		place(world, layout.at(2, 1, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, layout.right().getOpposite()));
		place(world, layout.at(1, HALF + 1, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, front));
	}

	/** Stage 3: a furnace with cooked food left in it, a 3x3 wheat farm out front, and more in the chest. */
	private static void buildLivedIn(ServerWorld world, Layout layout, Random random) {
		BlockPos furnacePos = layout.at(0, -2, 1);
		place(world, furnacePos, Blocks.FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, layout.front()));
		if (world.getBlockEntity(furnacePos) instanceof FurnaceBlockEntity furnace && furnace.getStack(2).isEmpty()) {
			furnace.setStack(2, new ItemStack(random.nextBoolean() ? Items.COOKED_BEEF : Items.COOKED_PORKCHOP, 2 + random.nextInt(3)));
			furnace.markDirty();
		}

		BlockPos water = layout.at(1, HALF + 4, 0);
		for (int a = 0; a <= 2; a++) {
			for (int b = HALF + 3; b <= HALF + 5; b++) {
				BlockPos pos = layout.at(a, b, 0);
				for (int y = 1; y <= 2; y++) {
					clear(world, pos.up(y));
				}
				if (pos.equals(water) || !canReplace(world, pos)) {
					continue;
				}
				fillBelow(world, pos, Blocks.DIRT.getDefaultState());
				world.setBlockState(pos, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.NOTIFY_LISTENERS);
				place(world, pos.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, random.nextInt(6)));
			}
		}
		if (canReplace(world, water) && holdsWater(world, water)) {
			world.setBlockState(water, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
		}

		stock(world, layout.at(2, -2, 1), random,
				new ItemStack(Items.BREAD, 2 + random.nextInt(4)),
				new ItemStack(Items.COAL, 4 + random.nextInt(9)),
				new ItemStack(Items.COBBLESTONE, 20 + random.nextInt(40)),
				new ItemStack(Items.RAW_IRON, 2 + random.nextInt(4)),
				new ItemStack(Items.WHEAT_SEEDS, 3 + random.nextInt(8)));
	}

	/** Stage 4: a fence round the farm with a gate facing the door, torches on the corner posts, a path, ripe wheat. */
	private static void buildSettled(ServerWorld world, Layout layout, Random random) {
		int nearRow = HALF + 2;
		int farRow = HALF + 6;
		for (int a = -1; a <= 3; a++) {
			for (int b = nearRow; b <= farRow; b++) {
				boolean ring = a == -1 || a == 3 || b == nearRow || b == farRow;
				if (!ring) {
					continue;
				}
				BlockPos post = layout.at(a, b, 1);
				fillBelow(world, post, Blocks.DIRT.getDefaultState());
				if (a == 1 && b == nearRow) {
					place(world, post, Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, layout.front()));
				} else {
					place(world, post, Blocks.OAK_FENCE.getDefaultState());
				}
				boolean corner = (a == -1 || a == 3) && (b == nearRow || b == farRow);
				if (corner) {
					place(world, post.up(), Blocks.TORCH.getDefaultState());
				}
			}
		}
		// Fences and the gate join up with their neighbours, as they would if placed by hand.
		for (int a = -1; a <= 3; a++) {
			for (int b = nearRow; b <= farRow; b++) {
				BlockPos pos = layout.at(a, b, 1);
				BlockState state = world.getBlockState(pos);
				if (state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.FENCE_GATES)) {
					world.setBlockState(pos, Block.postProcessState(state, world, pos), Block.NOTIFY_LISTENERS);
				}
			}
		}

		for (int a = -2; a <= 1; a++) {
			BlockPos path = layout.at(a, HALF + 1, 0);
			BlockState ground = world.getBlockState(path);
			if ((ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.DIRT)) && world.getBlockState(path.up()).isAir()) {
				world.setBlockState(path, Blocks.DIRT_PATH.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		for (int a = 0; a <= 2; a++) {
			for (int b = HALF + 3; b <= HALF + 5; b++) {
				BlockPos crop = layout.at(a, b, 1);
				if (world.getBlockState(crop).isOf(Blocks.WHEAT)) {
					world.setBlockState(crop, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, CropBlock.MAX_AGE), Block.NOTIFY_LISTENERS);
				}
			}
		}

		stock(world, layout.at(2, -2, 1), random,
				new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(5)),
				new ItemStack(Items.TORCH, 8 + random.nextInt(12)),
				new ItemStack(Items.WHEAT, 3 + random.nextInt(8)),
				new ItemStack(Items.BREAD, 2 + random.nextInt(3)));
	}

	/** The bed goes in the back corner: like a nearby player's own bed if one is loaded, otherwise red. */
	private static void placeBed(ServerWorld world, Layout layout) {
		BlockPos foot = layout.at(-2, -1, 1);
		BlockPos head = layout.at(-2, -2, 1);
		if (!canReplace(world, foot) || !canReplace(world, head)) {
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

	// ---- Block helpers ----

	private static boolean isClear(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return world.getFluidState(pos).isEmpty() && (state.isAir() || state.isReplaceable());
	}

	/** Ground nobody built: dirt, grass, stone, sand, gravel, snow. */
	private static boolean isNatural(BlockState state) {
		return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.SAND)
				|| state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK);
	}

	private static boolean canReplace(ServerWorld world, BlockPos pos) {
		return isClear(world, pos) || (isNatural(world.getBlockState(pos)) && world.getBlockEntity(pos) == null);
	}

	/** Places the block if the spot is free and it can stand there. Only listeners are told, so nothing pops off mid-build. */
	private static boolean place(ServerWorld world, BlockPos pos, BlockState state) {
		if (!canReplace(world, pos) || !state.canPlaceAt(world, pos)) {
			return false;
		}
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		return true;
	}

	private static void replaceWith(ServerWorld world, BlockPos pos, Block expected, BlockState state) {
		if (world.getBlockState(pos).isOf(expected)) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		}
	}

	/** Clears plants and natural ground out of the way; anything a player built stays. */
	private static void clear(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.isAir() && canReplace(world, pos) && world.getFluidState(pos).isEmpty()) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** Fills any gap under a floor or post down to the ground, up to three blocks. */
	private static void fillBelow(ServerWorld world, BlockPos pos, BlockState fill) {
		for (int y = 1; y <= 3; y++) {
			BlockPos below = pos.down(y);
			if (!isClear(world, below)) {
				return;
			}
			world.setBlockState(below, fill, Block.NOTIFY_LISTENERS);
		}
	}

	private static boolean holdsWater(ServerWorld world, BlockPos pos) {
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			BlockPos side = pos.offset(direction);
			BlockState state = world.getBlockState(side);
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
