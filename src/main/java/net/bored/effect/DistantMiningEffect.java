package net.bored.effect;

import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Somewhere in the rock nearby, someone is mining: a few hits, a block breaking, a pause, the next block.
 * The sounds come from real stone, so they sound like the rock around the player.
 */
public class DistantMiningEffect implements PresenceEffect {
	public static final String NAME = "distant_mining";
	private static final int TICKS_PER_HIT = 4;
	// Hit and break sounds only carry 16 blocks per unit of volume; these reach the 10 to 16 block range faintly.
	private static final float HIT_VOLUME = 1.2f;
	private static final float BREAK_VOLUME = 1.5f;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 1;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.underground() && context.alone();
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		Random random = player.getRandom();

		BlockPos start = findRock(world, player.getBlockPos(), random);
		if (start == null) {
			return false;
		}

		// Dig a short 1x2 tunnel in one direction: the lower block, then the one above it, then step forward.
		Direction heading = Direction.Type.HORIZONTAL.random(random);
		int blocks = 3 + random.nextInt(4);
		int delay = 0;
		BlockPos pos = start;
		for (int i = 0; i < blocks; i++) {
			BlockPos target = (i % 2 == 0) ? pos : pos.up();
			BlockSoundGroup group = soundGroup(world.getBlockState(target));
			Vec3d at = Vec3d.ofCenter(target);

			int hits = 3 + random.nextInt(3);
			for (int h = 0; h < hits; h++) {
				scheduler.schedule(delay, () -> EffectSounds.playTo(player, group.getHitSound(), at, HIT_VOLUME, group.getPitch() * 0.5f));
				delay += TICKS_PER_HIT;
			}
			scheduler.schedule(delay, () -> EffectSounds.playTo(player, group.getBreakSound(), at, BREAK_VOLUME, group.getPitch() * 0.8f));

			// A short gap between the two blocks of a column, a longer one before moving forward.
			delay += (i % 2 == 0) ? 6 + random.nextInt(6) : 15 + random.nextInt(25);
			if (i % 2 == 1) {
				pos = pos.offset(heading);
			}
		}
		return true;
	}

	private static BlockPos findRock(ServerWorld world, BlockPos origin, Random random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 10 + random.nextDouble() * 6;
			BlockPos pos = origin.add(
					MathHelper.floor(Math.cos(angle) * distance),
					random.nextBetween(-3, 3),
					MathHelper.floor(Math.sin(angle) * distance));
			if (world.isChunkLoaded(pos) && world.getBlockState(pos).isIn(BlockTags.BASE_STONE_OVERWORLD)) {
				return pos;
			}
		}
		return null;
	}

	private static BlockSoundGroup soundGroup(BlockState state) {
		return state.isAir() ? BlockSoundGroup.STONE : state.getSoundGroup();
	}
}
