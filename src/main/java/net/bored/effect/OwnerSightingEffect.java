package net.bored.effect;

import net.bored.network.PeripheralNetworking;
import net.bored.presence.EffectDebug;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.bored.world.OtherPlayerState;
import net.bored.world.PeripheralWorldState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Catching the other you at home: from a distance, at the edge of view, the figure stands where its routine has it
 * right now. Outside the door at dusk and night, by its trees in the morning, at the mouth of its mine in the
 * afternoon. Only while it's in the game, and gone like any other sighting the moment it's looked at.
 */
public class OwnerSightingEffect implements PresenceEffect {
	public static final String NAME = "the_owner";
	private static final double MIN_DISTANCE = 24.0;
	private static final double MAX_DISTANCE = 96.0;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 3;
	}

	@Override
	public boolean showsFigure() {
		return true;
	}

	@Override
	public int cooldownTicks() {
		return 20 * 60 * 20;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		if (!context.alone() || player.getWorld().getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		PeripheralWorldState base = PeripheralWorldState.get(player.getServer());
		if (base.otherBase() == null || !OtherPlayerState.get(player.getServer()).online) {
			return false;
		}
		double distance = Math.sqrt(player.getBlockPos().getSquaredDistance(base.otherBase()));
		return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE;
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		PeripheralWorldState base = PeripheralWorldState.get(player.getServer());
		OtherBaseBuilder.Layout layout = OtherBaseBuilder.layoutOf(world, base);
		if (layout == null) {
			return false;
		}
		OtherPlayerState other = OtherPlayerState.get(player.getServer());
		BlockPos spot = switch (OtherPlayer.activity(player.getServer())) {
			case GATHERING -> other.treeSite != null ? standingNear(world, other.treeSite.add(2, 0, 0)) : layout.at(0, OtherBaseBuilder.HALF + 1, 1);
			case MINING -> standingNear(world, layout.at(-OtherBaseBuilder.HALF - 1, -OtherBaseBuilder.HALF, 0));
			default -> layout.at(0, OtherBaseBuilder.HALF + 1, 1);
		};
		Vec3d head = Vec3d.ofBottomCenter(spot).add(0, 1.62, 0);
		double angle = Observer.angleTo(player, head);
		// At the edge of their view, never straight ahead, and actually visible from where they are.
		if (angle < 25.0 || angle > 65.0 || !Observer.hasLineOfSight(player, head)) {
			return false;
		}
		EffectDebug.report(player, NAME, spot, "the other you stands at its " + OtherPlayer.activity(player.getServer()).describe() + " spot", false);
		PeripheralNetworking.sendEffect(player, NAME, spot, 0, EffectDebug.isReporting(player));
		return true;
	}

	/** Feet position on the surface at this column. */
	private static BlockPos standingNear(ServerWorld world, BlockPos column) {
		return new BlockPos(column.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
	}
}
