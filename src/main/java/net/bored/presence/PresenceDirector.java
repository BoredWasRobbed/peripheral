package net.bored.presence;

import net.bored.Peripheral;
import net.bored.config.PeripheralConfig;
import net.bored.effect.PresenceEffect;
import net.bored.effect.PresenceEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks on every player once a second: moves their unease with their situation, and when their countdown
 * runs out, picks an effect that suits the moment.
 */
public final class PresenceDirector {
	public static final PresenceDirector INSTANCE = new PresenceDirector();

	private static final int CHECK_INTERVAL = 20;
	private static final int TICKS_PER_DAY = 24000;
	private static final int TICKS_PER_MINUTE = 20 * 60;
	/** When nothing could fire, look again this soon. */
	private static final int RETRY_TICKS = 30 * 20;

	// Unease per second. One in-game hour is 50 seconds, so the baseline is about +1 per in-game hour.
	private static final float BASE_RISE = 1.0f / 50.0f;
	private static final float NIGHT_RISE = 0.01f;
	private static final float COMPANY_FALL = 0.03f;
	private static final float DAYLIGHT_FALL = 0.01f;
	private static final float DAYLIGHT_FLOOR = 10.0f;
	private static final float SLEEP_FALL = 20.0f;
	private static final float AFTER_SCARE_FALL = 30.0f;

	private final PresenceScheduler scheduler = new PresenceScheduler();
	private final Map<UUID, Map<String, Long>> lastFired = new HashMap<>();
	private final Map<UUID, String> lastEffect = new HashMap<>();
	private final Set<UUID> debugging = new HashSet<>();
	private long ticks;

	private PresenceDirector() {
	}

	public void tick(MinecraftServer server) {
		ticks++;
		scheduler.tick();
		if (ticks % CHECK_INTERVAL != 0) {
			return;
		}
		boolean grace = inGracePeriod(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			check(player, grace);
		}
	}

	private void check(ServerPlayerEntity player, boolean grace) {
		PeripheralConfig config = PeripheralConfig.get();
		UneaseHolder holder = (UneaseHolder) player;
		PresenceContext context = PresenceContext.sample(player);
		boolean hauntable = PresenceContext.isHauntable(player, config.affectCreativePlayers);

		float seconds = CHECK_INTERVAL / 20.0f;
		if (hauntable) {
			holder.peripheral$setUnease(holder.peripheral$getUnease() + uneaseChange(holder.peripheral$getUnease(), context, config) * seconds);
		}

		int next = holder.peripheral$getNextEventTicks();
		if (next < 0) {
			next = intervalFor(holder.peripheral$getUnease(), player.getRandom());
		}
		next -= CHECK_INTERVAL;
		if (next <= 0) {
			if (grace || !hauntable) {
				next = RETRY_TICKS;
			} else {
				PresenceEffect fired = rollEffect(player, context);
				next = fired != null ? intervalFor(holder.peripheral$getUnease(), player.getRandom()) : RETRY_TICKS;
			}
		}
		holder.peripheral$setNextEventTicks(next);

		if (debugging.contains(player.getUuid())) {
			showDebug(player, context, grace, hauntable);
		}
	}

	private static float uneaseChange(float unease, PresenceContext context, PeripheralConfig config) {
		if (!context.alone() || context.nearVillage()) {
			return -COMPANY_FALL;
		}
		if (context.surfaceDaylight() && !context.dark()) {
			return unease > DAYLIGHT_FLOOR ? -DAYLIGHT_FALL : 0.0f;
		}
		float rise = BASE_RISE * 2.0f; // alone, which is the only way to get here
		if (context.dark() || context.underground()) {
			rise *= 2.0f;
		}
		if (context.night() && context.skyVisible()) {
			rise += NIGHT_RISE;
		}
		return rise * (float) config.uneaseRiseMultiplier;
	}

	/** The highest tier of event the player's unease allows. */
	public static int tierFor(float unease) {
		if (unease >= 75) return 4;
		if (unease >= 50) return 3;
		if (unease >= 25) return 2;
		return 1;
	}

	/** Ticks until the next event, drawn from the band for this unease level. */
	private static int intervalFor(float unease, Random random) {
		int minMinutes;
		int maxMinutes;
		switch (tierFor(unease)) {
			case 4 -> { minMinutes = 3; maxMinutes = 6; }
			case 3 -> { minMinutes = 5; maxMinutes = 10; }
			case 2 -> { minMinutes = 10; maxMinutes = 20; }
			default -> { minMinutes = 20; maxMinutes = 40; }
		}
		int ticks = random.nextBetween(minMinutes * TICKS_PER_MINUTE, maxMinutes * TICKS_PER_MINUTE);
		return Math.max(CHECK_INTERVAL, (int) (ticks * PeripheralConfig.get().eventIntervalMultiplier));
	}

	/** Picks and fires an effect that suits the player right now, favouring lower tiers. Returns null if none fit. */
	private PresenceEffect rollEffect(ServerPlayerEntity player, PresenceContext context) {
		PeripheralConfig config = PeripheralConfig.get();
		int maxTier = tierFor(((UneaseHolder) player).peripheral$getUnease());
		Map<String, Long> fired = lastFired.computeIfAbsent(player.getUuid(), id -> new HashMap<>());

		List<PresenceEffect> candidates = new ArrayList<>();
		for (PresenceEffect effect : PresenceEffects.all()) {
			Long last = fired.get(effect.name());
			boolean cooledDown = last == null || ticks - last >= effect.cooldownTicks();
			if (effect.tier() <= maxTier && cooledDown && config.isEffectEnabled(effect.name()) && effect.canFire(player, context)) {
				candidates.add(effect);
			}
		}

		Random random = player.getRandom();
		while (!candidates.isEmpty()) {
			PresenceEffect effect = pickWeighted(candidates, maxTier, random);
			candidates.remove(effect);
			if (start(player, effect, context)) {
				return effect;
			}
		}
		return null;
	}

	private static PresenceEffect pickWeighted(List<PresenceEffect> candidates, int maxTier, Random random) {
		int total = 0;
		for (PresenceEffect effect : candidates) {
			total += weightOf(effect, maxTier);
		}
		int roll = random.nextInt(total);
		for (PresenceEffect effect : candidates) {
			roll -= weightOf(effect, maxTier);
			if (roll < 0) {
				return effect;
			}
		}
		return candidates.get(candidates.size() - 1);
	}

	/** Lower tiers are favoured even when higher ones are open, so big moments stay rare. */
	private static int weightOf(PresenceEffect effect, int maxTier) {
		return Math.max(1, effect.weight()) * (maxTier - effect.tier() + 1);
	}

	private boolean start(ServerPlayerEntity player, PresenceEffect effect, PresenceContext context) {
		if (!effect.fire(player, context, scheduler)) {
			return false;
		}
		lastFired.computeIfAbsent(player.getUuid(), id -> new HashMap<>()).put(effect.name(), ticks);
		lastEffect.put(player.getUuid(), effect.name());
		if (effect.tier() >= 3) {
			UneaseHolder holder = (UneaseHolder) player;
			holder.peripheral$setUnease(holder.peripheral$getUnease() - AFTER_SCARE_FALL);
		}
		Peripheral.LOGGER.debug("Presence effect {} for {}", effect.name(), player.getName().getString());
		return true;
	}

	/** Fires an effect now, ignoring tier, cooldown, grace period and config. Used by the trigger command. */
	public boolean forceEffect(ServerPlayerEntity player, PresenceEffect effect) {
		return start(player, effect, PresenceContext.sample(player));
	}

	public void onJoin(ServerPlayerEntity player) {
		UneaseHolder holder = (UneaseHolder) player;
		if (holder.peripheral$getNextEventTicks() < 0) {
			holder.peripheral$setNextEventTicks(intervalFor(holder.peripheral$getUnease(), player.getRandom()));
		}
	}

	public void onLeave(ServerPlayerEntity player) {
		debugging.remove(player.getUuid());
		lastEffect.remove(player.getUuid());
	}

	public void onSlept(ServerPlayerEntity player) {
		UneaseHolder holder = (UneaseHolder) player;
		holder.peripheral$setUnease(holder.peripheral$getUnease() - SLEEP_FALL);
	}

	public boolean toggleDebug(ServerPlayerEntity player) {
		if (!debugging.remove(player.getUuid())) {
			debugging.add(player.getUuid());
			return true;
		}
		return false;
	}

	public static boolean inGracePeriod(MinecraftServer server) {
		return server.getOverworld().getTime() < PeripheralConfig.get().gracePeriodDays * TICKS_PER_DAY;
	}

	private void showDebug(ServerPlayerEntity player, PresenceContext context, boolean grace, boolean hauntable) {
		UneaseHolder holder = (UneaseHolder) player;
		float unease = holder.peripheral$getUnease();
		StringBuilder state = new StringBuilder();
		if (context.alone()) state.append("alone ");
		if (context.dark()) state.append("dark ");
		if (context.underground()) state.append("underground ");
		if (context.night()) state.append("night ");
		if (context.nearVillage()) state.append("village ");
		if (grace) state.append("GRACE ");
		if (!hauntable) state.append("IGNORED ");

		Text text = Text.literal(String.format("Unease %.1f (tier %d) ", unease, tierFor(unease))).formatted(Formatting.DARK_RED)
				.append(Text.literal(String.format("next %ds ", holder.peripheral$getNextEventTicks() / 20)).formatted(Formatting.GRAY))
				.append(Text.literal(state.toString().trim()).formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" last: " + lastEffect.getOrDefault(player.getUuid(), "none")).formatted(Formatting.GRAY));
		player.sendMessage(text, true);
	}
}
