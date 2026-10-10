package net.bored.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bored.Peripheral;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Plain JSON config at {@code config/peripheral.json}. Presets and an in-game screen come in later milestones;
 * for now this holds the handful of values worth changing while playtesting.
 */
public class PeripheralConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PeripheralConfig instance = new PeripheralConfig();

	/** In-game days (24000 ticks of world age) before anything happens in a new world. */
	public double gracePeriodDays = 2.0;

	/** Scales how fast unease rises. 1.0 is the designed pace. */
	public double uneaseRiseMultiplier = 1.0;

	/** Scales the time between events. Lower means events happen more often. */
	public double eventIntervalMultiplier = 1.0;

	/** Whether players in creative mode are haunted. Spectators never are. */
	public boolean affectCreativePlayers = false;

	/** Per-effect switches, keyed by effect name (for example "footstep_echo"). Missing means enabled. */
	public Map<String, Boolean> effects = new HashMap<>();

	public static PeripheralConfig get() {
		return instance;
	}

	public boolean isEffectEnabled(String name) {
		return effects.getOrDefault(name, true);
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				PeripheralConfig loaded = GSON.fromJson(reader, PeripheralConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | RuntimeException e) {
				Peripheral.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			Peripheral.LOGGER.error("Could not write {}", path, e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(Peripheral.MOD_ID + ".json");
	}
}
