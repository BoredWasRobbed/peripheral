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
		add(new FootstepEchoEffect());
		add(new PhantomContainerEffect());
		add(new DistantMiningEffect());
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
