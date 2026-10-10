package net.bored.effect;

import net.bored.presence.Home;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * A door, trapdoor or gate the player left closed is quietly opened while they're away from it, so they find it
 * open when they come back. Prefers the player's home. Daytime only, so it never lets a night's mobs in.
 */
public class LeftOpenEffect implements PresenceEffect {
	public static final String NAME = "left_open";
	private static final int RADIUS = 16;
	private static final double MIN_PLAYER_DISTANCE_SQ = 12.0 * 12.0;

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
		return 20 * 60 * 30;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone() && !context.night();
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		BlockPos home = Home.of(player);
		BlockPos center = home != null && world.isChunkLoaded(home) ? home : player.getBlockPos();

		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -RADIUS / 2, -RADIUS), center.add(RADIUS, RADIUS / 2, RADIUS))) {
			if (!world.isChunkLoaded(pos) || pos.getSquaredDistance(player.getBlockPos()) < MIN_PLAYER_DISTANCE_SQ) {
				continue;
			}
			if (isClosedOpenable(world.getBlockState(pos))) {
				candidates.add(pos.toImmutable());
			}
		}

		Random random = player.getRandom();
		while (!candidates.isEmpty()) {
			BlockPos pos = candidates.remove(random.nextInt(candidates.size()));
			if (Observer.canSee(player, Vec3d.ofCenter(pos)) || Observer.canSee(player, Vec3d.ofCenter(pos.up()))) {
				continue;
			}
			open(world, pos);
			return true;
		}
		return false;
	}

	private static boolean isClosedOpenable(BlockState state) {
		if (!state.contains(Properties.OPEN) || state.get(Properties.OPEN)) {
			return false;
		}
		if (state.isIn(BlockTags.WOODEN_DOORS)) {
			return state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
		}
		return state.isIn(BlockTags.WOODEN_TRAPDOORS) || state.isIn(BlockTags.FENCE_GATES);
	}

	/** Opens it without a sound: nobody was there to hear it. */
	private static void open(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		world.setBlockState(pos, state.with(Properties.OPEN, true), Block.NOTIFY_LISTENERS);
		if (state.getBlock() instanceof DoorBlock) {
			BlockPos upper = pos.up();
			BlockState upperState = world.getBlockState(upper);
			if (upperState.isOf(state.getBlock())) {
				world.setBlockState(upper, upperState.with(Properties.OPEN, true), Block.NOTIFY_LISTENERS);
			}
		}
	}
}
