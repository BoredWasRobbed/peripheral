package net.bored.client;

import net.bored.Peripheral;
import net.bored.client.effect.Breathing;
import net.bored.client.effect.Figure;
import net.bored.client.effect.FigureSkin;
import net.bored.client.effect.FootstepEcho;
import net.bored.effect.FigureEffect;
import net.bored.effect.FootstepEchoEffect;
import net.bored.network.PeripheralNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

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
		ClientPlayNetworking.registerGlobalReceiver(PeripheralNetworking.FIGURE_SKIN, (client, handler, buf, responseSender) -> {
			String textures = buf.readString(Short.MAX_VALUE);
			String signature = buf.readString(Short.MAX_VALUE);
			UUID fallback = buf.readUuid();
			client.execute(() -> FigureSkin.apply(client, textures, signature, fallback));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			FootstepEcho.INSTANCE.tick(client);
			Figure.INSTANCE.tick(client);
		});
		WorldRenderEvents.AFTER_ENTITIES.register(Figure.INSTANCE::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FootstepEcho.INSTANCE.reset();
			Figure.INSTANCE.hide();
			FigureSkin.reset();
		});
	}

	private static void handleEffect(MinecraftClient client, String effect, BlockPos pos, int param, boolean debug) {
		switch (effect) {
			case FootstepEchoEffect.NAME -> FootstepEcho.INSTANCE.start(param, debug);
			case FigureEffect.Watcher.NAME -> Figure.INSTANCE.showWatcher(client, debug);
			case FigureEffect.BehindYou.NAME -> Figure.INSTANCE.showBehind(client, debug);
			case FigureEffect.Breathing.NAME -> Breathing.play(client, debug);
			default -> Peripheral.LOGGER.warn("Unknown presence effect from server: {}", effect);
		}
	}
}
