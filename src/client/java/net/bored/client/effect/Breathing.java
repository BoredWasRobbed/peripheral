package net.bored.client.effect;

import net.bored.Peripheral;
import net.bored.PeripheralSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Slow breathing just behind the player's head. Played as a player sound, so the hush never mutes it. */
public final class Breathing {
	private static final Identifier FILE = Peripheral.id("sounds/breathing.ogg");

	private Breathing() {
	}

	public static void play(MinecraftClient client, boolean debug) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		if (client.getResourceManager().getResource(FILE).isEmpty()) {
			if (debug) {
				player.sendMessage(Text.literal("[Peripheral] breathing skipped: add assets/peripheral/sounds/breathing.ogg to the mod")
						.formatted(Formatting.GRAY), false);
			}
			return;
		}
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d at = player.getEyePos().subtract(look.x * 1.2, 0.1, look.z * 1.2);
		client.world.playSound(at.x, at.y, at.z, PeripheralSounds.BREATHING, SoundCategory.PLAYERS, 0.6f, 1.0f, false);
		if (debug) {
			player.sendMessage(Text.literal("[Peripheral] breathing: played just behind you").formatted(Formatting.GRAY), false);
		}
	}
}
