package net.bored.client;

import net.bored.Peripheral;
import net.bored.client.effect.FootstepEcho;
import net.bored.effect.FootstepEchoEffect;
import net.bored.network.PeripheralNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class PeripheralClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(PeripheralNetworking.EFFECT, (client, handler, buf, responseSender) -> {
			String effect = buf.readString();
			BlockPos pos = buf.readBlockPos();
			int param = buf.readVarInt();
			boolean debug = buf.readBoolean();
			client.execute(() -> handleEffect(client, effect, pos, param, debug));
		});

		ClientTickEvents.END_CLIENT_TICK.register(FootstepEcho.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FootstepEcho.INSTANCE.reset());
	}

	private static void handleEffect(MinecraftClient client, String effect, BlockPos pos, int param, boolean debug) {
		switch (effect) {
			case FootstepEchoEffect.NAME -> FootstepEcho.INSTANCE.start(param, debug);
			default -> Peripheral.LOGGER.warn("Unknown presence effect from server: {}", effect);
		}
	}
}
