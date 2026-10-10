package net.bored.effect;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

final class EffectSounds {
	private EffectSounds() {
	}

	/**
	 * Plays a sound at a position that only this player hears. A volume above 1 doesn't get louder,
	 * it only carries further (16 blocks per unit of volume).
	 */
	static void playTo(ServerPlayerEntity player, SoundEvent sound, Vec3d pos, float volume, float pitch) {
		if (player.isRemoved()) {
			return;
		}
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(
				Registries.SOUND_EVENT.getEntry(sound), SoundCategory.BLOCKS,
				pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
	}
}
