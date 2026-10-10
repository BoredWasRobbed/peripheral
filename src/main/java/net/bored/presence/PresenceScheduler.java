package net.bored.presence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Runs small actions later, for effects that play out over time: a chest closing a moment after it opened,
 * a run of pickaxe hits, an animal that keeps staring.
 */
public final class PresenceScheduler {
	private final List<Task> tasks = new ArrayList<>();
	private final List<Task> pending = new ArrayList<>();
	private final List<BooleanSupplier> repeating = new ArrayList<>();
	private final List<BooleanSupplier> pendingRepeating = new ArrayList<>();

	public void schedule(int delayTicks, Runnable action) {
		pending.add(new Task(Math.max(1, delayTicks), action));
	}

	/** Runs {@code action} every tick, starting next tick, until it returns false. */
	public void repeat(BooleanSupplier action) {
		pendingRepeating.add(action);
	}

	void tick() {
		tasks.addAll(pending);
		pending.clear();
		Iterator<Task> it = tasks.iterator();
		while (it.hasNext()) {
			Task task = it.next();
			if (--task.ticksLeft <= 0) {
				it.remove();
				task.action.run();
			}
		}

		repeating.addAll(pendingRepeating);
		pendingRepeating.clear();
		repeating.removeIf(action -> !action.getAsBoolean());
	}

	void clear() {
		tasks.clear();
		pending.clear();
		repeating.clear();
		pendingRepeating.clear();
	}

	private static final class Task {
		int ticksLeft;
		final Runnable action;

		Task(int ticksLeft, Runnable action) {
			this.ticksLeft = ticksLeft;
			this.action = action;
		}
	}
}
