package net.bored;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Custom sounds. Each one plays only once its .ogg file is added under assets/peripheral/sounds/.
 */
public final class PeripheralSounds {
	public static final Identifier BREATHING_ID = Peripheral.id("breathing");
	public static final SoundEvent BREATHING = SoundEvent.of(BREATHING_ID);

	private PeripheralSounds() {
	}

	public static void register() {
		Registry.register(Registries.SOUND_EVENT, BREATHING_ID, BREATHING);
	}
}
