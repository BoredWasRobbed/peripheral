package net.bored.effect;

import net.bored.network.PeripheralNetworking;
import net.bored.presence.EffectDebug;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * After the game has gone quiet: someone sprinting at the player from behind, getting closer with every step,
 * until the steps stop right behind them. The client plays it, since only it knows where the player is facing.
 */
public class RunningFeetEffect implements PresenceEffect {
	public static final String NAME = "running_feet";

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	public int cooldownTicks() {
		return 20 * 60 * 40;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone() && (context.dark() || context.night());
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		EffectDebug.report(player, NAME, player.getBlockPos(), "someone runs at you from behind; each step is marked with smoke", false);
		PeripheralNetworking.sendEffect(player, NAME, player.getBlockPos(), 0, EffectDebug.isReporting(player));
		return true;
	}
}
