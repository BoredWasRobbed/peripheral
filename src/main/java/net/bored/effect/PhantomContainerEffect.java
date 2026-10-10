package net.bored.effect;

import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * A nearby chest, barrel, door, trapdoor or gate that the player can't see makes the sound of someone
 * opening it, then closing it a moment later. Nothing actually moves.
 */
public class PhantomContainerEffect implements PresenceEffect {
	public static final String NAME = "phantom_container";
	private static final int RADIUS = 12;
	private static final double MIN_DISTANCE_SQ = 4.0 * 4.0;

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
		return context.alone();
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		Random random = player.getRandom();
		BlockPos center = player.getBlockPos();

		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -RADIUS / 2, -RADIUS), center.add(RADIUS, RADIUS / 2, RADIUS))) {
			if (pos.getSquaredDistance(center) >= MIN_DISTANCE_SQ && sounds(world.getBlockState(pos)) != null) {
				candidates.add(pos.toImmutable());
			}
		}

		// Shuffle-pick until one is out of the player's sight.
		while (!candidates.isEmpty()) {
			BlockPos pos = candidates.remove(random.nextInt(candidates.size()));
			Vec3d at = Vec3d.ofCenter(pos);
			if (Observer.canSee(player, at)) {
				continue;
			}

			BlockState state = world.getBlockState(pos);
			Sounds sounds = sounds(state);
			boolean currentlyOpen = state.contains(Properties.OPEN) && state.get(Properties.OPEN);
			SoundEvent first = currentlyOpen ? sounds.close : sounds.open;
			SoundEvent second = currentlyOpen ? sounds.open : sounds.close;
			float pitch = 0.9f + random.nextFloat() * 0.1f;

			EffectSounds.playTo(player, first, at, sounds.volume, pitch);
			scheduler.schedule(sounds.holdTicks + random.nextInt(sounds.holdTicks), () ->
					EffectSounds.playTo(player, second, at, sounds.volume, pitch));
			return true;
		}
		return false;
	}

	private static Sounds sounds(BlockState state) {
		if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)) {
			return Sounds.CHEST;
		}
		if (state.isOf(Blocks.BARREL)) {
			return Sounds.BARREL;
		}
		if (state.isIn(BlockTags.WOODEN_DOORS)) {
			return Sounds.DOOR;
		}
		if (state.isIn(BlockTags.WOODEN_TRAPDOORS)) {
			return Sounds.TRAPDOOR;
		}
		if (state.isIn(BlockTags.FENCE_GATES)) {
			return Sounds.GATE;
		}
		return null;
	}

	private record Sounds(SoundEvent open, SoundEvent close, float volume, int holdTicks) {
		static final Sounds CHEST = new Sounds(SoundEvents.BLOCK_CHEST_OPEN, SoundEvents.BLOCK_CHEST_CLOSE, 0.5f, 25);
		static final Sounds BARREL = new Sounds(SoundEvents.BLOCK_BARREL_OPEN, SoundEvents.BLOCK_BARREL_CLOSE, 0.5f, 25);
		static final Sounds DOOR = new Sounds(SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 20);
		static final Sounds TRAPDOOR = new Sounds(SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.0f, 15);
		static final Sounds GATE = new Sounds(SoundEvents.BLOCK_FENCE_GATE_OPEN, SoundEvents.BLOCK_FENCE_GATE_CLOSE, 1.0f, 20);
	}
}
