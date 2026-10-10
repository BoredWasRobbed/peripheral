package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.presence.Home;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.bored.world.PeripheralWorldState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Once per world, somewhere out of sight, someone else starts a base: a cleared spot with a crafting table, a chest
 * and a campfire whose smoke can be seen from a distance. From then on it is built up a stage at a time while nobody
 * is looking (see {@link OtherBaseBuilder}). Nobody is ever home.
 */
public class OtherBaseEffect implements PresenceEffect {
	public static final String NAME = "the_other_base";
	private static final double MIN_DISTANCE = 48.0;
	private static final double MAX_DISTANCE = 80.0;
	private static final double MIN_HOME_DISTANCE = 96.0;
	private static final int ATTEMPTS = 60;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	public int weight() {
		return 15;
	}

	@Override
	public boolean changesWorld() {
		return true;
	}

	/** It's found later, not heard now, so the game doesn't go quiet for it. */
	@Override
	public boolean hushes() {
		return false;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone() && context.skyVisible() && player.getWorld().getRegistryKey() == World.OVERWORLD
				&& PeripheralWorldState.get(player.getServer()).otherBase() == null;
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		PeripheralWorldState state = PeripheralWorldState.get(player.getServer());
		if (state.otherBase() != null || world.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		BlockPos home = Home.of(player);
		Random random = player.getRandom();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			// Somewhere behind the player, so it is never started in front of them.
			float yaw = player.getYaw() + 180.0f + (random.nextFloat() - 0.5f) * 140.0f;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			int x = (int) Math.floor(player.getX() - Math.sin(Math.toRadians(yaw)) * distance);
			int z = (int) Math.floor(player.getZ() + Math.cos(Math.toRadians(yaw)) * distance);
			if (home != null && Math.hypot(home.getX() - x, home.getZ() - z) < MIN_HOME_DISTANCE) {
				continue;
			}
			// The door faces back toward the player.
			Direction front = Direction.getFacing(player.getX() - x, 0, player.getZ() - z);
			if (front.getAxis() == Direction.Axis.Y) {
				continue;
			}
			Integer floor = OtherBaseBuilder.findFloor(world, new OtherBaseBuilder.Layout(new BlockPos(x, 0, z), front));
			if (floor == null) {
				continue;
			}
			OtherBaseBuilder.Layout layout = new OtherBaseBuilder.Layout(new BlockPos(x, floor, z), front);
			if (OtherBaseBuilder.canSee(player, layout)) {
				continue;
			}
			OtherBaseStyle style = OtherBaseStyle.choose(world, layout, floor);
			OtherBaseBuilder.build(world, layout, style, 0, random);
			BlockPos door = layout.door();
			state.setOtherBase(door, layout.centre(), front, 0, world.getTime() + OtherBaseBuilder.stageTicks());
			style.save(state);
			EffectDebug.report(player, NAME, door, "started (" + style.describe() + "): " + OtherBaseBuilder.stageName(0));
			return true;
		}
		return false;
	}
}
