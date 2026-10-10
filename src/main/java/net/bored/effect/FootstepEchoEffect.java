package net.bored.effect;

import net.bored.network.PeripheralNetworking;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Footsteps a few blocks behind the player that keep pace with theirs and stop when they stop.
 * The client does the work, since only it knows exactly when the player takes a step.
 */
public class FootstepEchoEffect implements PresenceEffect {
	public static final String NAME = "footstep_echo";

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
		// Footsteps with a friend nearby are just the friend.
		return context.alone();
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		int steps = 8 + player.getRandom().nextInt(9);
		PeripheralNetworking.sendEffect(player, NAME, player.getBlockPos(), steps);
		return true;
	}
}
