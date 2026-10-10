package net.bored.client.mixin;

import net.bored.client.effect.Hush;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps music, ambience and weather silent while a hush is on. Every sound, delayed or not, starts here. */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
	private void peripheral$hush(SoundInstance sound, CallbackInfo ci) {
		if (Hush.mutes(sound)) {
			ci.cancel();
		}
	}
}
