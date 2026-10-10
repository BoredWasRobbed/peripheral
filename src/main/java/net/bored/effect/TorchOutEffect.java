package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.presence.Home;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One torch near the edge of the player's home is taken down and left on the ground below where it was,
 * as if someone knocked it off. Only ever one torch, never anything else.
 */
public class TorchOutEffect implements PresenceEffect {
	public static final String NAME = "torch_out";
	private static final int RADIUS = 20;
	private static final double MIN_PLAYER_DISTANCE_SQ = 16.0 * 16.0;

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
		return context.alone();
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		BlockPos home = Home.of(player);
		BlockPos center = home != null && world.isChunkLoaded(home) ? home : player.getBlockPos();

		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -RADIUS / 2, -RADIUS), center.add(RADIUS, RADIUS / 2, RADIUS))) {
			if (world.isChunkLoaded(pos) && pos.getSquaredDistance(player.getBlockPos()) >= MIN_PLAYER_DISTANCE_SQ && isTorch(world.getBlockState(pos))) {
				candidates.add(pos.toImmutable());
			}
		}

		if (candidates.isEmpty()) {
			return false;
		}
		// The edge of the base: only the outer third of the torches, furthest from its centre.
		candidates.sort(Comparator.comparingDouble(pos -> -pos.getSquaredDistance(center)));
		List<BlockPos> outer = new ArrayList<>(candidates.subList(0, (candidates.size() + 2) / 3));

		Random random = player.getRandom();
		while (!outer.isEmpty()) {
			BlockPos pos = outer.remove(random.nextInt(outer.size()));
			if (Observer.canSee(player, Vec3d.ofCenter(pos))) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			world.removeBlock(pos, false);
			Block.dropStack(world, pos, new ItemStack(state.getBlock().asItem()));
			EffectDebug.report(player, NAME, pos, "knocked down " + state.getBlock().getName().getString());
			return true;
		}
		return false;
	}

	private static boolean isTorch(BlockState state) {
		return state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH);
	}
}
