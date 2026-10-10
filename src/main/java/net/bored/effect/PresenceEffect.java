package net.bored.effect;

import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * One thing the presence can do to a player. The director picks effects by tier and context;
 * adding an effect never touches the director.
 */
public interface PresenceEffect {
	/** Short name used in the config and in commands, for example "footstep_echo". */
	String name();

	/** 1 Whispers, 2 Signs, 3 Presence, 4 Encounter. */
	int tier();

	/** Relative chance of being picked against other effects of the same tier. */
	default int weight() {
		return 10;
	}

	/** Whether this effect actually changes blocks in the world. These can all be turned off with one config switch. */
	default boolean changesWorld() {
		return false;
	}

	/** Whether this effect shows the figure. These can all be turned off with one config switch. */
	default boolean showsFigure() {
		return false;
	}

	/** Minimum ticks before this effect can happen to the same player again. */
	default int cooldownTicks() {
		return 20 * 60 * 15;
	}

	/** Cheap check of whether the situation suits this effect. */
	default boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return true;
	}

	/**
	 * Starts the effect. Returns false if it couldn't find what it needed (no container nearby, no rock to mine),
	 * in which case the director tries something else.
	 */
	boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler);
}
