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
 * Plain JSON config at {@code config/peripheral.json}. An in-game screen comes in a later milestone.
 */
public class PeripheralConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PeripheralConfig instance = new PeripheralConfig();

	/** "subtle" (sounds and small signs only, slower), "default", or "relentless" (faster, shorter quiet spells). */
	public String preset = Preset.DEFAULT.id;

	/** In-game days (24000 ticks of world age) before anything happens in a new world. */
	public double gracePeriodDays = 2.0;

	/** Whether effects may change blocks: opening doors, taking torches, building and digging. */
	public boolean allowWorldChanges = true;

	/** Scales how fast unease rises, on top of the preset. 1.0 is the designed pace. */
	public double uneaseRiseMultiplier = 1.0;

	/** Scales the time between events, on top of the preset. Lower means events happen more often. */
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

	public Preset preset() {
		return Preset.byId(preset);
	}

	public double riseMultiplier() {
		return preset().rise * uneaseRiseMultiplier;
	}

	public double intervalMultiplier() {
		return preset().interval * eventIntervalMultiplier;
	}

	public enum Preset {
		SUBTLE("subtle", 0.75, 1.5, 2),
		DEFAULT("default", 1.0, 1.0, 4),
		RELENTLESS("relentless", 1.5, 0.6, 4);

		public final String id;
		/** Multiplies how fast unease rises. */
		public final double rise;
		/** Multiplies the time between events. */
		public final double interval;
		/** The highest tier of event this preset ever allows. */
		public final int maxTier;

		Preset(String id, double rise, double interval, int maxTier) {
			this.id = id;
			this.rise = rise;
			this.interval = interval;
			this.maxTier = maxTier;
		}

		public static Preset byId(String id) {
			for (Preset preset : values()) {
				if (preset.id.equalsIgnoreCase(id)) {
					return preset;
				}
			}
			return DEFAULT;
		}
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
