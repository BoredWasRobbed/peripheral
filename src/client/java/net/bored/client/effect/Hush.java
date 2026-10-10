package net.bored.client.effect;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumSet;
import java.util.Set;

/**
 * The quiet before something happens: music, ambience and weather stop and don't come back for a while.
 * Everything the player and the world around them do can still be heard, which is the point.
 */
public final class Hush {
	private static final Set<SoundCategory> MUTED = EnumSet.of(SoundCategory.MUSIC, SoundCategory.AMBIENT, SoundCategory.WEATHER);

	private static int ticksLeft;
	private static boolean debug;

	private Hush() {
	}

	/** Goes quiet for this many ticks, or brings the sound back if {@code ticks} is 0. */
	public static void start(MinecraftClient client, int ticks, boolean debug) {
		Hush.debug = debug;
		if (ticks <= 0) {
			end(client, "the effect couldn't happen");
			return;
		}
		ticksLeft = ticks;
		SoundManager sounds = client.getSoundManager();
		for (SoundCategory category : MUTED) {
			sounds.stopSounds(null, category);
		}
		client.getMusicTracker().stop();
		message(client, String.format("hush: music, ambience and weather off for %ds", ticks / 20));
	}

	public static void tick(MinecraftClient client) {
		if (ticksLeft > 0 && --ticksLeft == 0) {
			message(client, "hush over, sound is back");
		}
	}

	public static void reset() {
		ticksLeft = 0;
	}

	/** Whether this sound should be kept from playing right now. */
	public static boolean mutes(SoundInstance sound) {
		return ticksLeft > 0 && MUTED.contains(sound.getCategory());
	}

	private static void end(MinecraftClient client, String why) {
		if (ticksLeft > 0) {
			ticksLeft = 0;
			message(client, "hush over early: " + why);
		}
	}

	private static void message(MinecraftClient client, String text) {
		if (debug && client.player != null) {
			client.player.sendMessage(Text.literal("[Peripheral] " + text).formatted(Formatting.GRAY), false);
		}
	}
}
