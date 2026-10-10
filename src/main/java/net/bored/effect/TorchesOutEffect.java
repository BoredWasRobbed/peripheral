package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * After the game has gone quiet: the torches around the player are broken one after another, starting with the
 * furthest and working closer, as if someone were walking toward them putting out the light. Only torches the
 * player can't see at that moment go, and each one drops like any broken torch.
 */
public class TorchesOutEffect implements PresenceEffect {
	public static final String NAME = "torches_out";
	private static final int RADIUS = 20;
	private static final double MIN_DISTANCE_SQ = 3.0 * 3.0;
	private static final int MIN_TORCHES = 4;
	private static final int MAX_TORCHES = 8;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	public boolean changesWorld() {
		return true;
	}

	@Override
	public int cooldownTicks() {
		return 20 * 60 * 60;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone() && (context.night() || context.underground());
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		BlockPos center = player.getBlockPos();
		List<BlockPos> torches = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -RADIUS / 2, -RADIUS), center.add(RADIUS, RADIUS / 2, RADIUS))) {
			if (pos.getSquaredDistance(center) >= MIN_DISTANCE_SQ && world.isChunkLoaded(pos) && isTorch(world.getBlockState(pos))
					&& !Observer.canSee(player, Vec3d.ofCenter(pos))) {
				torches.add(pos.toImmutable());
			}
		}
		if (torches.size() < MIN_TORCHES) {
			return false;
		}
		// Furthest first, so it sounds like something coming closer.
		torches.sort(Comparator.comparingDouble(pos -> -pos.getSquaredDistance(center)));
		List<BlockPos> chosen = torches.subList(0, Math.min(MAX_TORCHES, torches.size()));

		int delay = 0;
		for (BlockPos pos : chosen) {
			scheduler.schedule(delay, () -> {
				if (player.isRemoved() || player.getServerWorld() != world || !isTorch(world.getBlockState(pos))
						|| Observer.canSee(player, Vec3d.ofCenter(pos))) {
					return;
				}
				world.breakBlock(pos, true);
			});
			delay += 25 + player.getRandom().nextInt(15);
		}
		EffectDebug.report(player, NAME, chosen.get(0), chosen.size() + " torches going out, this one first, then closer");
		return true;
	}

	private static boolean isTorch(BlockState state) {
		return state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH);
	}
}
