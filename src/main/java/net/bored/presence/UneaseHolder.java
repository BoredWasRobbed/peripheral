package net.bored.presence;

/**
 * Implemented on {@link net.minecraft.server.network.ServerPlayerEntity} by a mixin so each player
 * carries their own unease, saved with the player's data.
 */
public interface UneaseHolder {
	float peripheral$getUnease();

	void peripheral$setUnease(float unease);

	/** Ticks until the director next rolls for an event for this player. */
	int peripheral$getNextEventTicks();

	void peripheral$setNextEventTicks(int ticks);
}
