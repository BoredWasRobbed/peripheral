package net.bored.effect;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PresenceEffects {
	private static final Map<String, PresenceEffect> EFFECTS = new LinkedHashMap<>();

	private PresenceEffects() {
	}

	public static void register() {
		// Tier 1: Whispers
		add(new FootstepEchoEffect());
		add(new PhantomContainerEffect());
		add(new DistantMiningEffect());
		// Tier 2: Signs
		add(new LeftOpenEffect());
		add(new TorchOutEffect());
		add(new SomeoneElsesWorkEffect());
		add(new StaringAnimalEffect());
		// Tier 3: Presence
		add(new FigureEffect.Watcher());
		add(new FigureEffect.BehindYou());
		add(new FigureEffect.Breathing());
		// Tier 4: Encounter
		add(new TorchesOutEffect());
		add(new RunningFeetEffect());
		add(new OtherBaseEffect());
	}

	private static void add(PresenceEffect effect) {
		EFFECTS.put(effect.name(), effect);
	}

	public static PresenceEffect get(String name) {
		return EFFECTS.get(name);
	}

	public static Collection<PresenceEffect> all() {
		return Collections.unmodifiableCollection(EFFECTS.values());
	}
}
