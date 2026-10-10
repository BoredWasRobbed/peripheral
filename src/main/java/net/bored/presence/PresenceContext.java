package net.bored.presence;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * A snapshot of the player's situation, taken each time the director checks on them.
 *
 * @param alone       no other (non-spectator) player within {@link #COMPANY_RADIUS} blocks
 * @param light       combined block and sky light at the player's feet
 * @param skyVisible  the player can see the sky from where they stand
 * @param underground below {@link #UNDERGROUND_Y} with no sky overhead
 * @param night       the world's clock says night
 * @param nearVillage near an occupied point of interest such as a villager's bed or workstation
 */
public record PresenceContext(boolean alone, int light, boolean skyVisible, boolean underground, boolean night, boolean nearVillage) {
	public static final double COMPANY_RADIUS = 64.0;
	public static final int UNDERGROUND_Y = 50;
	public static final int DARK_LIGHT = 7;

	public static PresenceContext sample(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		BlockPos pos = player.getBlockPos();

		boolean alone = true;
		for (ServerPlayerEntity other : world.getPlayers()) {
			if (other != player && !other.isSpectator() && other.squaredDistanceTo(player) < COMPANY_RADIUS * COMPANY_RADIUS) {
				alone = false;
				break;
			}
		}

		boolean skyVisible = world.isSkyVisible(pos);
		return new PresenceContext(
				alone,
				world.getLightLevel(pos),
				skyVisible,
				pos.getY() < UNDERGROUND_Y && !skyVisible,
				world.isNight(),
				world.isNearOccupiedPointOfInterest(pos)
		);
	}

	public boolean dark() {
		return light < DARK_LIGHT;
	}

	/** Out on the surface in daylight, the safest a player gets. */
	public boolean surfaceDaylight() {
		return skyVisible && !night;
	}

	public static boolean isHauntable(PlayerEntity player, boolean affectCreative) {
		return !player.isSpectator() && (affectCreative || !player.isCreative());
	}
}
