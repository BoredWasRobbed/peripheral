package net.bored.effect;

import net.bored.presence.Home;
import net.bored.world.OtherPlayerState;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * What the other you leaves behind while it goes about its day: a path worn out to where it cuts wood, stumps where
 * trees were, a staircase mine going deeper every afternoon, the odd pillar it climbed down from, and a wheat field
 * once it has seen the player farming. Nothing is ever done where a player could see it happen.
 */
final class OtherTraces {
	private static final int TREE_SEARCH = 48;
	private static final int MAX_PILLARS = 4;
	private static final int MAX_MINE_DEPTH = 64;
	private static final int PLANTINGS_FOR_FIELD = 4;

	private OtherTraces() {
	}

	private static long day(ServerWorld world) {
		return world.getTimeOfDay() / 24000L;
	}

	private static int surface(ServerWorld world, int x, int z) {
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	// ---- Mornings ----

	static void gather(ServerWorld world, PeripheralWorldState base, OtherPlayerState other, OtherBaseBuilder.Layout layout,
			OtherBaseStyle style, Random random) {
		if (!other.treeSiteChosen && world.isChunkLoaded(layout.centre())) {
			other.treeSite = findTree(world, layout.centre(), 12, TREE_SEARCH);
			other.treeSiteChosen = true;
			other.markDirty();
		}
		wearPath(world, other, layout);
		if (random.nextInt(4) == 0) {
			chopTree(world, other, layout);
		}
		if (random.nextInt(10) == 0) {
			buildPillar(world, other, layout, style, random);
		}
		if (!other.wheatFieldDone && other.plantingsSeen >= PLANTINGS_FOR_FIELD && base.otherBaseStage() >= 3) {
			OtherBaseBuilder.loadArea(world, layout);
			if (buildWheatField(world, layout, random)) {
				OtherPlayer.report(world, layout.at(-8, 1, 1), "planted a wheat field, like yours");
			}
			other.wheatFieldDone = true;
			other.markDirty();
		}
	}

	/** The nearest tree trunk standing on the surface, between {@code min} and {@code max} blocks away. */
	private static BlockPos findTree(ServerWorld world, BlockPos centre, int min, int max) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -max; dx <= max; dx += 2) {
			for (int dz = -max; dz <= max; dz += 2) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance < min || distance > max || distance >= bestDistance) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (!world.isChunkLoaded(new BlockPos(x, centre.getY(), z))) {
					continue;
				}
				BlockPos top = new BlockPos(x, surface(world, x, z), z);
				if (world.getBlockState(top).isIn(BlockTags.LOGS)) {
					best = top;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private static BlockPos mineEntrance(ServerWorld world, OtherBaseBuilder.Layout layout) {
		BlockPos column = layout.at(-OtherBaseBuilder.HALF - 2, -OtherBaseBuilder.HALF, 0);
		return new BlockPos(column.getX(), surface(world, column.getX(), column.getZ()), column.getZ());
	}

	/** A couple more blocks of dirt path from the door toward where it cuts wood (or its mine). */
	private static void wearPath(ServerWorld world, OtherPlayerState other, OtherBaseBuilder.Layout layout) {
		if (other.pathDone) {
			return;
		}
		BlockPos start = layout.at(0, OtherBaseBuilder.HALF + 1, 0);
		BlockPos target = other.treeSite != null ? other.treeSite : mineEntrance(world, layout);
		double dx = target.getX() - start.getX();
		double dz = target.getZ() - start.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0) {
			other.pathDone = true;
			other.markDirty();
			return;
		}
		for (int step = 0; step < 2; step++) {
			int i = other.pathLength;
			if (i > length - 2) {
				other.pathDone = true;
				break;
			}
			int x = (int) Math.round(start.getX() + dx / length * i);
			int z = (int) Math.round(start.getZ() + dz / length * i);
			if (!world.isChunkLoaded(new BlockPos(x, start.getY(), z))) {
				return;
			}
			BlockPos ground = new BlockPos(x, surface(world, x, z), z);
			if (!OtherPlayer.unseen(world, ground, 16)) {
				return;
			}
			BlockState state = world.getBlockState(ground);
			if ((state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT)) && OtherBaseBuilder.isClear(world, ground.up())) {
				world.setBlockState(ground, Blocks.DIRT_PATH.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			other.pathLength++;
		}
		other.markDirty();
	}

	/** Once a morning, one tree near its wood spot is cut down to a stump, and the logs go in its chests. */
	private static void chopTree(ServerWorld world, OtherPlayerState other, OtherBaseBuilder.Layout layout) {
		long today = day(world);
		if (other.treeSite == null || other.lastChopDay == today || !world.isChunkLoaded(other.treeSite)) {
			return;
		}
		BlockPos top = findTree(world, other.treeSite, 0, 10);
		if (top == null) {
			// This stand of trees is used up; find another next time.
			other.treeSiteChosen = false;
			other.markDirty();
			return;
		}
		BlockPos bottom = top;
		while (world.getBlockState(bottom.down()).isIn(BlockTags.LOGS) && top.getY() - bottom.getY() < 24) {
			bottom = bottom.down();
		}
		if (!OtherPlayer.unseen(world, bottom, 24)) {
			return;
		}
		Item log = world.getBlockState(bottom).getBlock().asItem();
		int count = 0;
		for (BlockPos pos = bottom.up(); world.getBlockState(pos).isIn(BlockTags.LOGS) && count < 24; pos = pos.up()) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			count++;
		}
		other.lastChopDay = today;
		other.markDirty();
		if (count == 0) {
			return;
		}
		OtherBaseBuilder.loadArea(world, layout);
		OtherBaseTidy.store(world, layout, List.of(new ItemStack(log, count)));
		playNear(world, bottom.up(), BlockSoundGroup.WOOD, 64, 4);
		OtherPlayer.report(world, bottom, "cut down a tree, leaving the stump");
	}

	/** Every few days, a lone pillar it built to look around and climbed back down from. */
	private static void buildPillar(ServerWorld world, OtherPlayerState other, OtherBaseBuilder.Layout layout,
			OtherBaseStyle style, Random random) {
		long today = day(world);
		if (other.pillars >= MAX_PILLARS || today - other.lastPillarDay < 3) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 20.0 + random.nextDouble() * 30.0;
		int x = (int) Math.floor(layout.centre().getX() + Math.cos(angle) * distance);
		int z = (int) Math.floor(layout.centre().getZ() + Math.sin(angle) * distance);
		if (!world.isChunkLoaded(new BlockPos(x, layout.centre().getY(), z))) {
			return;
		}
		BlockPos ground = new BlockPos(x, surface(world, x, z), z);
		int height = 5 + random.nextInt(5);
		if (!OtherBaseBuilder.isNaturalGround(world, ground) || !OtherPlayer.unseen(world, ground.up(height / 2), 24)) {
			return;
		}
		for (int y = 1; y <= height; y++) {
			if (!OtherBaseBuilder.isClear(world, ground.up(y))) {
				return;
			}
		}
		Block block = random.nextBoolean() ? style.stone() : Blocks.DIRT;
		for (int y = 1; y <= height; y++) {
			world.setBlockState(ground.up(y), block.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		other.pillars++;
		other.lastPillarDay = today;
		other.markDirty();
		OtherPlayer.report(world, ground.up(height), "left a " + height + "-block pillar");
	}

	/** A 5x6 wheat field with a water channel, beside the cabin. Returns false if the ground there won't do. */
	private static boolean buildWheatField(ServerWorld world, OtherBaseBuilder.Layout layout, Random random) {
		int a0 = -10;
		int a1 = -6;
		int b0 = -1;
		int b1 = 4;
		if (!OtherBaseBuilder.isBuildable(world, layout, a0, a1, b0, b1) || !OtherPlayer.unseen(world, layout.at(-8, 1, 1), 24)) {
			return false;
		}
		OtherBaseBuilder.level(world, layout, a0, a1, b0, b1, Blocks.DIRT);
		for (int a = a0; a <= a1; a++) {
			for (int b = b0; b <= b1; b++) {
				BlockPos soil = layout.at(a, b, 0);
				if (a == -8) {
					continue;
				}
				world.setBlockState(soil, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.NOTIFY_LISTENERS);
				OtherBaseBuilder.place(world, soil.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, random.nextInt(8)));
			}
		}
		for (int b = b0; b <= b1; b++) {
			BlockPos channel = layout.at(-8, b, 0);
			boolean contained = true;
			for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
				if (world.getBlockState(channel.offset(side)).isAir()) {
					contained = false;
				}
			}
			if (contained) {
				world.setBlockState(channel, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		return true;
	}

	// ---- Afternoons ----

	/** One more step of a staircase mine beside the cabin, heading down and away from it. */
	static void mine(ServerWorld world, OtherPlayerState other, OtherBaseBuilder.Layout layout, OtherBaseStyle style, Random random) {
		if (other.mineDone || !world.isChunkLoaded(layout.centre())) {
			return;
		}
		Direction forward = layout.front().getOpposite();
		Direction side = forward.rotateYClockwise();
		BlockPos entranceColumn = layout.at(-OtherBaseBuilder.HALF - 2, -OtherBaseBuilder.HALF, 0);
		int top = other.mineDepth == 0 ? surface(world, entranceColumn.getX(), entranceColumn.getZ()) : layout.centre().getY() + other.mineTopOffset;
		int i = other.mineDepth;
		BlockPos column = entranceColumn.offset(forward, i);
		if (!world.isChunkLoaded(column)) {
			return;
		}
		int stepFloor = top - 1 - i;
		BlockPos floor = new BlockPos(column.getX(), stepFloor, column.getZ());
		if (!OtherPlayer.unseen(world, floor.up(), 16)) {
			return;
		}
		if (other.mineDepth == 0) {
			// Remember the entrance height relative to the floor of the cabin, so later steps line up.
			other.mineTopOffset = top - layout.centre().getY();
		}

		List<ItemStack> drops = new ArrayList<>();
		for (int y = 1; y <= 3; y++) {
			BlockPos pos = floor.up(y);
			BlockState state = world.getBlockState(pos);
			if (!OtherBaseBuilder.isClear(world, pos) && !isMineable(state)) {
				other.mineDone = true;
				other.markDirty();
				return;
			}
			for (Direction direction : Direction.values()) {
				if (!world.getFluidState(pos.offset(direction)).isEmpty()) {
					// Water or lava ahead: a careful player stops here.
					other.mineDone = true;
					other.markDirty();
					return;
				}
			}
		}
		for (int y = 1; y <= 3; y++) {
			BlockPos pos = floor.up(y);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir() && isMineable(state)) {
				drops.add(dropOf(state, random));
			}
			if (!state.isAir()) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
		if (OtherBaseBuilder.isClear(world, floor)) {
			world.setBlockState(floor, style.stone().getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		if (i % 6 == 3) {
			BlockPos torch = floor.up(2);
			if (world.getBlockState(torch.offset(side)).isSideSolidFullSquare(world, torch.offset(side), side.getOpposite())) {
				OtherBaseBuilder.place(world, torch, Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, side.getOpposite()));
			}
		}
		other.mineDepth++;
		if (other.mineDepth >= MAX_MINE_DEPTH || stepFloor <= world.getBottomY() + 8) {
			other.mineDone = true;
		}
		other.markDirty();

		if (!drops.isEmpty()) {
			OtherBaseBuilder.loadArea(world, layout);
			OtherBaseTidy.store(world, layout, drops);
		}
		playNear(world, floor.up(), BlockSoundGroup.STONE, 48, 3);
		if (other.mineDepth % 8 == 1) {
			OtherPlayer.report(world, floor.up(), "mined its staircase down to step " + other.mineDepth);
		}
	}

	private static boolean isMineable(BlockState state) {
		return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.SAND)
				|| state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.CLAY) || isOre(state);
	}

	private static boolean isOre(BlockState state) {
		return state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES) || state.isIn(BlockTags.COPPER_ORES)
				|| state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES)
				|| state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES);
	}

	/** What a stone pickaxe gets from the block, roughly. */
	private static ItemStack dropOf(BlockState state, Random random) {
		if (state.isIn(BlockTags.COAL_ORES)) return new ItemStack(Items.COAL);
		if (state.isIn(BlockTags.IRON_ORES)) return new ItemStack(Items.RAW_IRON);
		if (state.isIn(BlockTags.COPPER_ORES)) return new ItemStack(Items.RAW_COPPER, 2 + random.nextInt(3));
		if (state.isIn(BlockTags.GOLD_ORES)) return new ItemStack(Items.RAW_GOLD);
		if (state.isIn(BlockTags.REDSTONE_ORES)) return new ItemStack(Items.REDSTONE, 4);
		if (state.isIn(BlockTags.LAPIS_ORES)) return new ItemStack(Items.LAPIS_LAZULI, 4);
		if (state.isIn(BlockTags.DIAMOND_ORES)) return new ItemStack(Items.DIAMOND);
		if (state.isIn(BlockTags.EMERALD_ORES)) return new ItemStack(Items.EMERALD);
		if (state.isOf(Blocks.STONE)) return new ItemStack(Items.COBBLESTONE);
		if (state.isOf(Blocks.DEEPSLATE)) return new ItemStack(Items.COBBLED_DEEPSLATE);
		if (state.isOf(Blocks.GRASS_BLOCK)) return new ItemStack(Items.DIRT);
		return new ItemStack(state.getBlock().asItem());
	}

	// ---- Touching the player's things ----

	/**
	 * Something from a chest at the player's home goes missing, and something of the other's turns up in its place.
	 * Only while the player is away from home and nobody can see the chest.
	 */
	static boolean swap(MinecraftServer server, ServerWorld world, OtherBaseBuilder.Layout layout, Random random) {
		List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
		if (players.isEmpty()) {
			return false;
		}
		ServerPlayerEntity player = players.get(random.nextInt(players.size()));
		BlockPos home = player.getSpawnPointPosition();
		if (home == null || player.getSpawnPointDimension() != World.OVERWORLD || player.getBlockPos().getSquaredDistance(home) < 32 * 32) {
			return false;
		}
		if (!OtherPlayer.unseen(world, home, 24)) {
			return false;
		}
		world.getChunk(home);
		List<LootableContainerBlockEntity> containers = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(home.add(-12, -6, -12), home.add(12, 6, 12))) {
			if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container && !container.isEmpty()) {
				containers.add(container);
			}
		}
		if (containers.isEmpty()) {
			return false;
		}
		LootableContainerBlockEntity container = containers.get(random.nextInt(containers.size()));
		List<Integer> filled = new ArrayList<>();
		int empty = -1;
		for (int i = 0; i < container.size(); i++) {
			if (!container.getStack(i).isEmpty()) {
				filled.add(i);
			} else if (empty < 0) {
				empty = i;
			}
		}
		int slot = filled.get(random.nextInt(filled.size()));
		ItemStack stack = container.getStack(slot);
		ItemStack taken = stack.split(Math.max(1, stack.getCount() / 2));
		container.markDirty();

		OtherBaseBuilder.loadArea(world, layout);
		OtherBaseTidy.store(world, layout, List.of(taken));
		// Leaves one of its own things behind, if it has any.
		Item[] gifts = {Items.BREAD, Items.TORCH, Items.COOKED_BEEF, Items.COOKED_MUTTON, Items.COOKED_PORKCHOP, Items.COAL, Items.APPLE};
		for (Item gift : gifts) {
			int target = empty >= 0 ? empty : (stack.isEmpty() ? slot : -1);
			if (target < 0) {
				break;
			}
			if (OtherBaseTidy.take(world, layout, gift)) {
				container.setStack(target, new ItemStack(gift));
				container.markDirty();
				break;
			}
		}
		OtherPlayer.report(world, container.getPos(), "took " + taken.getCount() + " " + taken.getName().getString() + " from this chest");
		return true;
	}

	// ---- Shared ----

	/** A few block sounds at a spot, for any player close enough to hear them. */
	static void playNear(ServerWorld world, BlockPos pos, BlockSoundGroup group, double range, int count) {
		Vec3d at = Vec3d.ofCenter(pos);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(at) > range * range) {
				continue;
			}
			int delay = 0;
			for (int i = 0; i < count; i++) {
				boolean hit = i < count - 1;
				net.bored.presence.PresenceDirector.INSTANCE.scheduler().schedule(delay, () ->
						EffectSounds.playTo(player, hit ? group.getHitSound() : group.getBreakSound(), at, 2.0f, group.getPitch() * 0.8f));
				delay += 6 + world.getRandom().nextInt(6);
			}
		}
	}
}
