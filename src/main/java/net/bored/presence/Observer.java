package net.bored.presence;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Answers "can the player see this spot, and is it in the centre or the edge of their view?".
 * Works on either side, so the client can use it for its own effects.
 */
public final class Observer {
	/** Within this many degrees of the look direction, something is being looked at. */
	public static final double FOCUS_DEGREES = 30.0;
	/** Beyond this many degrees, something is out of view at a normal field of view. */
	public static final double VIEW_DEGREES = 60.0;

	private Observer() {
	}

	/** Angle in degrees between where the player is looking and the direction to {@code target}. */
	public static double angleTo(PlayerEntity player, Vec3d target) {
		Vec3d toTarget = target.subtract(player.getEyePos());
		if (toTarget.lengthSquared() < 1.0E-6) {
			return 0.0;
		}
		double dot = player.getRotationVec(1.0f).dotProduct(toTarget.normalize());
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
	}

	public static boolean isInView(PlayerEntity player, Vec3d target) {
		return angleTo(player, target) < VIEW_DEGREES;
	}

	public static boolean isFocusedOn(PlayerEntity player, Vec3d target) {
		return angleTo(player, target) < FOCUS_DEGREES;
	}

	/** Nothing solid between the player's eyes and the target. The target block itself doesn't count as blocking. */
	public static boolean hasLineOfSight(PlayerEntity player, Vec3d target) {
		BlockHitResult hit = player.getWorld().raycast(new RaycastContext(
				player.getEyePos(), target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(BlockPos.ofFloored(target));
	}

	/** The player could actually see the spot right now: it's in view and nothing is in the way. */
	public static boolean canSee(PlayerEntity player, Vec3d target) {
		return isInView(player, target) && hasLineOfSight(player, target);
	}
}
