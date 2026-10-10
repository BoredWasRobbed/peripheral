package net.bored;

import net.bored.command.PeripheralCommand;
import net.bored.config.PeripheralConfig;
import net.bored.effect.PresenceEffects;
import net.bored.effect.StaringAnimalEffect;
import net.bored.network.PeripheralNetworking;
import net.bored.presence.PresenceDirector;
import net.bored.presence.UneaseHolder;
import net.bored.world.FigureSkinFetcher;
import net.bored.world.PeripheralWorldState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Peripheral implements ModInitializer {
	public static final String MOD_ID = "peripheral";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PeripheralConfig.load();
		PresenceEffects.register();
		PeripheralSounds.register();

		ServerTickEvents.END_SERVER_TICK.register(PresenceDirector.INSTANCE::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PresenceDirector.INSTANCE.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PresenceDirector.INSTANCE.onLeave(handler.player));

		// Unease survives death; the thing doesn't forget you because you respawned.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			UneaseHolder from = (UneaseHolder) oldPlayer;
			UneaseHolder to = (UneaseHolder) newPlayer;
			to.peripheral$setUnease(from.peripheral$getUnease());
			to.peripheral$setNextEventTicks(from.peripheral$getNextEventTicks());
		});

		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayerEntity player && player.getWorld().isDay()) {
				PresenceDirector.INSTANCE.onSlept(player);
			}
		});

		// An animal still frozen from a stare when the world was last closed gets its AI back.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof MobEntity mob && mob.getCommandTags().contains(StaringAnimalEffect.TAG)) {
				StaringAnimalEffect.release(mob);
			}
		});

		// Borrow a skin for the figure when the world is first opened (retried on later starts until the figure is seen).
		ServerLifecycleEvents.SERVER_STARTED.register(FigureSkinFetcher::fetchIfNeeded);

		// Tell each client which skin the figure wears here, once it says it understands our packets.
		S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			if (channels.contains(PeripheralNetworking.FIGURE_SKIN)) {
				PeripheralNetworking.sendFigureSkin(handler.player, PeripheralWorldState.get(server));
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PeripheralCommand.register(dispatcher));

		LOGGER.info("Peripheral loaded with {} presence effects", PresenceEffects.all().size());
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
