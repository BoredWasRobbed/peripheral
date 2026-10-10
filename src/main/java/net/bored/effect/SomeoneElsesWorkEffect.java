package net.bored.effect;

import net.bored.presence.Home;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Out in the wild, away from the player's home, a trace of another player appears where nobody built it:
 * a lone torch, a dirt pillar someone climbed, or underground a short 1x2 tunnel dug into the rock.
 * Always placed somewhere the player can't see at that moment.
 */
public class SomeoneElsesWorkEffect implements PresenceEffect {
	public static final String NAME = "someone_elses_work";
	private static final double MIN_HOME_DISTANCE_SQ = 48.0 * 48.0;
	private static final int ATTEMPTS = 24;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 2;
	}

	@Override
	public boolean changesWorld() {
		return true;
	}

	@Override
	public int cooldownTicks() {
		return 20 * 60 * 20;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone() && Home.distanceSq(player) >= MIN_HOME_DISTANCE_SQ;
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		Random random = player.getRandom();
		if (context.underground()) {
			return random.nextBoolean() ? digTunnel(player, world, random) || placeCaveTorch(player, world, random)
					: placeCaveTorch(player, world, random);
		}
		return random.nextBoolean() ? buildPillar(player, world, random) : placeSurfaceTorch(player, world, random);
	}

	// Surface

	private boolean placeSurfaceTorch(ServerPlayerEntity player, ServerWorld world, Random random) {
		for (int i = 0; i < ATTEMPTS; i++) {
			BlockPos pos = surfaceSpot(player, world, random, 20, 40);
			if (pos != null && Blocks.TORCH.getDefaultState().canPlaceAt(world, pos)) {
				world.setBlockState(pos, Blocks.TORCH.getDefaultState(), Block.NOTIFY_ALL);
				return true;
			}
		}
		return false;
	}

	/** A pillar of dirt someone jumped up on to look around, sometimes with a torch left on top. */
	private boolean buildPillar(ServerPlayerEntity player, ServerWorld world, Random random) {
		int height = 3 + random.nextInt(3);
		for (int i = 0; i < ATTEMPTS; i++) {
			BlockPos base = surfaceSpot(player, world, random, 20, 40);
			if (base == null || !world.getBlockState(base.down()).isIn(BlockTags.DIRT) || !allAir(world, base, height + 1)) {
				continue;
			}
			for (int y = 0; y < height; y++) {
				world.setBlockState(base.up(y), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
			}
			if (random.nextBoolean()) {
				world.setBlockState(base.up(height), Blocks.TORCH.getDefaultState(), Block.NOTIFY_ALL);
			}
			return true;
		}
		return false;
	}

	/** The first air block above solid, natural ground, at a random spot in the ring, out of sight and away from home. */
	private static BlockPos surfaceSpot(ServerPlayerEntity player, ServerWorld world, Random random, int minDistance, int maxDistance) {
		BlockPos column = ringSpot(player.getBlockPos(), random, minDistance, maxDistance);
		if (!world.isChunkLoaded(column)) {
			return null;
		}
		BlockPos pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
		BlockState ground = world.getBlockState(pos.down());
		if (!world.getBlockState(pos).isAir() || !ground.isSideSolidFullSquare(world, pos.down(), Direction.UP)
				|| ground.isIn(BlockTags.LEAVES) || !world.getFluidState(pos.down()).isEmpty()) {
			return null;
		}
		return isHidden(player, pos) && farFromHome(player, pos) ? pos : null;
	}

	// Underground

	private boolean placeCaveTorch(ServerPlayerEntity player, ServerWorld world, Random random) {
		for (int i = 0; i < ATTEMPTS; i++) {
			BlockPos floor = caveFloor(player, world, random);
			if (floor == null) {
				continue;
			}
			// On a wall if there is one next to this spot, otherwise on the floor.
			for (Direction side : Direction.Type.HORIZONTAL) {
				BlockState wallTorch = Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, side.getOpposite());
				if (wallTorch.canPlaceAt(world, floor.up())) {
					world.setBlockState(floor.up(), wallTorch, Block.NOTIFY_ALL);
					return true;
				}
			}
			if (Blocks.TORCH.getDefaultState().canPlaceAt(world, floor)) {
				world.setBlockState(floor, Blocks.TORCH.getDefaultState(), Block.NOTIFY_ALL);
				return true;
			}
		}
		return false;
	}

	/** A short 1x2 tunnel dug straight into the cave wall, the way a player strip-mines. Only natural stone is removed. */
	private boolean digTunnel(ServerPlayerEntity player, ServerWorld world, Random random) {
		for (int i = 0; i < ATTEMPTS; i++) {
			BlockPos floor = caveFloor(player, world, random);
			if (floor == null) {
				continue;
			}
			Direction heading = Direction.Type.HORIZONTAL.random(random);
			int length = 4 + random.nextInt(4);
			List<BlockPos> dug = new ArrayList<>();
			BlockPos pos = floor.offset(heading);
			for (int step = 0; step < length; step++) {
				if (!diggable(world, pos) || !diggable(world, pos.up())) {
					break;
				}
				dug.add(pos);
				dug.add(pos.up());
				pos = pos.offset(heading);
			}
			if (dug.size() < 6) {
				continue;
			}
			for (BlockPos block : dug) {
				world.setBlockState(block, Blocks.CAVE_AIR.getDefaultState(), Block.NOTIFY_ALL);
			}
			return true;
		}
		return false;
	}

	/** Natural stone with no water or lava touching it, so digging it never floods anything. */
	private static boolean diggable(ServerWorld world, BlockPos pos) {
		if (!world.getBlockState(pos).isIn(BlockTags.BASE_STONE_OVERWORLD)) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (!world.getFluidState(pos.offset(direction)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** An air block with solid ground under it, in a cave near the player's depth, out of sight and away from home. */
	private static BlockPos caveFloor(ServerPlayerEntity player, ServerWorld world, Random random) {
		BlockPos start = ringSpot(player.getBlockPos(), random, 12, 28).up(random.nextBetween(-6, 6));
		if (!world.isChunkLoaded(start)) {
			return null;
		}
		BlockPos.Mutable pos = start.mutableCopy();
		for (int down = 0; down < 8; down++) {
			BlockState here = world.getBlockState(pos);
			BlockState below = world.getBlockState(pos.down());
			if (here.isAir() && world.getBlockState(pos.up()).isAir()
					&& below.isSideSolidFullSquare(world, pos.down(), Direction.UP) && !world.isSkyVisible(pos)) {
				BlockPos found = pos.toImmutable();
				return isHidden(player, found) && farFromHome(player, found) ? found : null;
			}
			pos.move(Direction.DOWN);
		}
		return null;
	}

	// Shared

	private static BlockPos ringSpot(BlockPos origin, Random random, int minDistance, int maxDistance) {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
		return origin.add(MathHelper.floor(Math.cos(angle) * distance), 0, MathHelper.floor(Math.sin(angle) * distance));
	}

	private static boolean isHidden(ServerPlayerEntity player, BlockPos pos) {
		return !Observer.canSee(player, Vec3d.ofCenter(pos)) && !Observer.canSee(player, Vec3d.ofCenter(pos.up()));
	}

	private static boolean farFromHome(ServerPlayerEntity player, BlockPos pos) {
		BlockPos home = Home.of(player);
		return home == null || pos.getSquaredDistance(home) >= MIN_HOME_DISTANCE_SQ;
	}

	private static boolean allAir(ServerWorld world, BlockPos base, int height) {
		for (int y = 0; y < height; y++) {
			if (!world.getBlockState(base.up(y)).isAir()) {
				return false;
			}
		}
		return true;
	}
}
