package net.bored.network;

import net.bored.Peripheral;
import net.bored.world.PeripheralWorldState;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server-to-client packets. "effect" plays an effect that needs logic on the client (reacting to the player's
 * own movement, drawing things); "figure_skin" tells the client which skin the figure wears in this world.
 */
public final class PeripheralNetworking {
	public static final Identifier EFFECT = Peripheral.id("effect");
	public static final Identifier FIGURE_SKIN = Peripheral.id("figure_skin");

	private PeripheralNetworking() {
	}

	public static void sendEffect(ServerPlayerEntity player, String effect, BlockPos pos, int param, boolean debug) {
		if (!ServerPlayNetworking.canSend(player, EFFECT)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(effect);
		buf.writeBlockPos(pos);
		buf.writeVarInt(param);
		buf.writeBoolean(debug);
		ServerPlayNetworking.send(player, EFFECT, buf);
	}

	/** Callers check {@link ServerPlayNetworking#canSend} first, except while the client is registering the channel. */
	public static void sendFigureSkin(ServerPlayerEntity player, PeripheralWorldState state) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(state.skinTextures(), Short.MAX_VALUE);
		buf.writeString(state.skinSignature(), Short.MAX_VALUE);
		buf.writeUuid(state.fallbackId());
		ServerPlayNetworking.send(player, FIGURE_SKIN, buf);
	}
}
