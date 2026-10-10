package net.bored.network;

import net.bored.Peripheral;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * One server-to-client packet: "play this effect". Effects that need logic on the client
 * (reacting to the player's own movement, drawing things) are sent through here.
 */
public final class PeripheralNetworking {
	public static final Identifier EFFECT = Peripheral.id("effect");

	private PeripheralNetworking() {
	}

	public static void sendEffect(ServerPlayerEntity player, String effect, BlockPos pos, int param) {
		if (!ServerPlayNetworking.canSend(player, EFFECT)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(effect);
		buf.writeBlockPos(pos);
		buf.writeVarInt(param);
		ServerPlayNetworking.send(player, EFFECT, buf);
	}
}
