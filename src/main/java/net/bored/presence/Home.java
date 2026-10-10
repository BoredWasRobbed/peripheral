package net.bored.presence;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Where the player lives, as far as the mod can tell: their bed or respawn anchor in the dimension they're in. */
public final class Home {
	private Home() {
	}

	/** The player's spawn point if it's in their current dimension, otherwise null. */
	public static BlockPos of(ServerPlayerEntity player) {
		BlockPos spawn = player.getSpawnPointPosition();
		if (spawn == null || player.getSpawnPointDimension() != player.getWorld().getRegistryKey()) {
			return null;
		}
		return spawn;
	}

	/** Squared distance from the player to their home, or {@link Double#MAX_VALUE} if they have none here. */
	public static double distanceSq(ServerPlayerEntity player) {
		BlockPos home = of(player);
		return home == null ? Double.MAX_VALUE : player.getBlockPos().getSquaredDistance(home);
	}
}
