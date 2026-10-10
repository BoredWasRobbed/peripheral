package net.bored.mixin;

import net.bored.presence.UneaseHolder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements UneaseHolder {
	@Unique
	private static final String NBT_KEY = "peripheral";

	@Unique
	private float peripheral$unease;

	@Unique
	private int peripheral$nextEventTicks = -1;

	@Override
	public float peripheral$getUnease() {
		return peripheral$unease;
	}

	@Override
	public void peripheral$setUnease(float unease) {
		peripheral$unease = MathHelper.clamp(unease, 0.0f, 100.0f);
	}

	@Override
	public int peripheral$getNextEventTicks() {
		return peripheral$nextEventTicks;
	}

	@Override
	public void peripheral$setNextEventTicks(int ticks) {
		peripheral$nextEventTicks = ticks;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void peripheral$write(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound data = new NbtCompound();
		data.putFloat("unease", peripheral$unease);
		data.putInt("nextEventTicks", peripheral$nextEventTicks);
		nbt.put(NBT_KEY, data);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void peripheral$read(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
			NbtCompound data = nbt.getCompound(NBT_KEY);
			peripheral$setUnease(data.getFloat("unease"));
			peripheral$nextEventTicks = data.getInt("nextEventTicks");
		}
	}
}
