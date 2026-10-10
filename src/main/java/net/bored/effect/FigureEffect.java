package net.bored.effect;

import net.bored.network.PeripheralNetworking;
import net.bored.presence.EffectDebug;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Tier 3 effects played out on the target's client: the figure, and things heard right next to them.
 * The client picks the exact spot, since only it knows the player's field of view, and reports it in debug mode.
 */
public abstract class FigureEffect implements PresenceEffect {
	private final String name;

	protected FigureEffect(String name) {
		this.name = name;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public int tier() {
		return 3;
	}

	@Override
	public int cooldownTicks() {
		return 20 * 60 * 20;
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		PeripheralNetworking.sendEffect(player, name, player.getBlockPos(), 0, EffectDebug.isReporting(player));
		return true;
	}

	/** A figure standing at the edge of view, gone as soon as the player looks straight at it or comes close. */
	public static class Watcher extends FigureEffect {
		public static final String NAME = "the_watcher";

		public Watcher() {
			super(NAME);
		}

		@Override
		public boolean showsFigure() {
			return true;
		}

		@Override
		public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
			return context.alone();
		}
	}

	/** The figure stands right behind the player for a moment; only a quick turn catches it. */
	public static class BehindYou extends FigureEffect {
		public static final String NAME = "behind_you";

		public BehindYou() {
			super(NAME);
		}

		@Override
		public boolean showsFigure() {
			return true;
		}

		@Override
		public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
			return context.alone();
		}
	}

	/** Slow breathing just behind the player, in tight dark places. Needs the custom breathing sound installed. */
	public static class Breathing extends FigureEffect {
		public static final String NAME = "breathing";

		public Breathing() {
			super(NAME);
		}

		@Override
		public int weight() {
			return 5;
		}

		@Override
		public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
			return context.alone() && context.dark() && !context.skyVisible();
		}
	}
}
