package net.bored.presence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs small actions a number of ticks later, for effects that play out over time (a chest closing, a run of pickaxe hits). */
public final class PresenceScheduler {
	private final List<Task> tasks = new ArrayList<>();
	private final List<Task> pending = new ArrayList<>();

	public void schedule(int delayTicks, Runnable action) {
		pending.add(new Task(Math.max(1, delayTicks), action));
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
	}

	void clear() {
		tasks.clear();
		pending.clear();
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
