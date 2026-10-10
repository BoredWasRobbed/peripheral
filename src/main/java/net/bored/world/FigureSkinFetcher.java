package net.bored.world;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.properties.Property;
import net.bored.Peripheral;
import net.bored.network.PeripheralNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Borrows a random real player's skin for the figure. It makes up plausible usernames, asks Mojang whether
 * each one exists, and takes the first one that has a custom skin. Runs off the server thread; if Mojang
 * can't be reached, the figure keeps one of Minecraft's default skins.
 */
public final class FigureSkinFetcher {
	private static final int ATTEMPTS = 25;
	private static final String[] FIRST = {
			"quiet", "lost", "pale", "grey", "old", "cold", "late", "still", "lone", "dark", "dusty", "hollow",
			"faded", "silent", "little", "blue", "red", "green", "wild", "slow", "sleepy", "tiny", "happy", "lucky"};
	private static final String[] SECOND = {
			"fox", "crow", "miner", "wolf", "oak", "river", "stone", "moth", "owl", "finch", "pine", "ember",
			"frost", "rain", "hill", "cave", "moss", "bear", "deer", "hare", "builder", "panda", "cookie", "potato"};

	private FigureSkinFetcher() {
	}

	/** Starts a fetch if this world still needs a skin. Safe to call on every server start. */
	public static void fetchIfNeeded(MinecraftServer server) {
		PeripheralWorldState state = PeripheralWorldState.get(server);
		if (!state.needsSkin()) {
			return;
		}
		Thread thread = new Thread(() -> {
			Property textures = findSkin(server);
			if (textures == null) {
				Peripheral.LOGGER.info("Couldn't borrow a skin for the figure; using a default skin for now");
				return;
			}
			server.execute(() -> {
				PeripheralWorldState current = PeripheralWorldState.get(server);
				if (!current.needsSkin()) {
					return;
				}
				current.setSkin(textures.getValue(), textures.getSignature());
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					if (ServerPlayNetworking.canSend(player, PeripheralNetworking.FIGURE_SKIN)) {
						PeripheralNetworking.sendFigureSkin(player, current);
					}
				}
			});
		}, "Peripheral skin fetcher");
		thread.setDaemon(true);
		thread.start();
	}

	private static Property findSkin(MinecraftServer server) {
		Random random = new Random();
		for (int i = 0; i < ATTEMPTS; i++) {
			String name = randomName(random);
			try {
				GameProfile profile = lookup(server, name);
				if (profile == null) {
					continue;
				}
				GameProfile filled = server.getSessionService().fillProfileProperties(profile, false);
				for (Property property : filled.getProperties().get("textures")) {
					if (hasCustomSkin(property.getValue())) {
						Peripheral.LOGGER.debug("Borrowed the figure's skin from {}", filled.getName());
						return property;
					}
				}
			} catch (RuntimeException e) {
				Peripheral.LOGGER.debug("Skin lookup for {} failed", name, e);
			}
		}
		return null;
	}

	private static GameProfile lookup(MinecraftServer server, String name) {
		AtomicReference<GameProfile> found = new AtomicReference<>();
		server.getGameProfileRepo().findProfilesByNames(new String[] {name}, Agent.MINECRAFT, new ProfileLookupCallback() {
			@Override
			public void onProfileLookupSucceeded(GameProfile profile) {
				found.set(profile);
			}

			@Override
			public void onProfileLookupFailed(GameProfile profile, Exception exception) {
			}
		});
		return found.get();
	}

	private static boolean hasCustomSkin(String texturesBase64) {
		try {
			String json = new String(Base64.getDecoder().decode(texturesBase64), StandardCharsets.UTF_8);
			return json.contains("\"SKIN\"");
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Names like "quietfox", "Ember2041", "lone_miner" or "PalePine07". */
	private static String randomName(Random random) {
		String first = FIRST[random.nextInt(FIRST.length)];
		String second = SECOND[random.nextInt(SECOND.length)];
		return switch (random.nextInt(4)) {
			case 0 -> first + second;
			case 1 -> capitalize(second) + (random.nextInt(9000) + 100);
			case 2 -> first + "_" + second;
			default -> capitalize(first) + capitalize(second) + String.format("%02d", random.nextInt(100));
		};
	}

	private static String capitalize(String word) {
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
