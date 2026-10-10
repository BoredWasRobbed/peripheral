package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * A nearby farm animal stops what it's doing and turns its head to follow the player, and doesn't move again
 * until they leave. Nothing about it is changed for good.
 */
public class StaringAnimalEffect implements PresenceEffect {
	public static final String NAME = "staring_animal";
	/** Marks a frozen animal so it can be released if the world is closed mid-stare. */
	public static final String TAG = "peripheral_staring";
	private static final double SEARCH_RADIUS = 20.0;
	private static final double MIN_DISTANCE_SQ = 6.0 * 6.0;
	private static final double RELEASE_DISTANCE_SQ = 32.0 * 32.0;
	private static final int MAX_TICKS = 20 * 120;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 2;
	}

	@Override
	public int cooldownTicks() {
		return 20 * 60 * 20;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone();
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		List<AnimalEntity> animals = player.getWorld().getEntitiesByClass(AnimalEntity.class,
				player.getBoundingBox().expand(SEARCH_RADIUS),
				animal -> animal.isAlive() && !animal.isAiDisabled() && !animal.isLeashed()
						&& animal.squaredDistanceTo(player) >= MIN_DISTANCE_SQ
						&& Observer.hasLineOfSight(player, animal.getEyePos()));
		if (animals.isEmpty()) {
			return false;
		}

		Random random = player.getRandom();
		AnimalEntity animal = animals.get(random.nextInt(animals.size()));
		animal.getNavigation().stop();
		animal.setAiDisabled(true);
		animal.addCommandTag(TAG);
		EffectDebug.report(player, NAME, animal.getBlockPos(), animal.getName().getString() + " is staring at you");

		int[] ticks = {0};
		scheduler.repeat(() -> {
			if (++ticks[0] > MAX_TICKS || shouldRelease(animal, player)) {
				release(animal);
				return false;
			}
			lookAt(animal, player);
			return true;
		});
		return true;
	}

	private static boolean shouldRelease(AnimalEntity animal, ServerPlayerEntity player) {
		return animal.isRemoved() || !animal.isAlive() || animal.hurtTime > 0
				|| player.isRemoved() || player.getWorld() != animal.getWorld()
				|| animal.squaredDistanceTo(player) > RELEASE_DISTANCE_SQ;
	}

	private static void lookAt(MobEntity mob, Entity target) {
		Vec3d delta = target.getEyePos().subtract(mob.getEyePos());
		float yaw = (float) Math.toDegrees(MathHelper.atan2(delta.z, delta.x)) - 90.0f;
		float pitch = (float) -Math.toDegrees(MathHelper.atan2(delta.y, delta.horizontalLength()));
		mob.setHeadYaw(yaw);
		mob.setPitch(pitch);
		// The body only turns once the head can't twist any further, which is what makes it look deliberate.
		float twist = MathHelper.wrapDegrees(yaw - mob.getBodyYaw());
		if (Math.abs(twist) > 60.0f) {
			mob.setBodyYaw(yaw - Math.signum(twist) * 60.0f);
		}
	}

	public static void release(MobEntity mob) {
		mob.setAiDisabled(false);
		mob.getCommandTags().remove(TAG);
	}
}
