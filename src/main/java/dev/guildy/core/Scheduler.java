package dev.guildy.core;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny tick scheduler. All tasks run on the server thread at the end of the server tick, inside {@link Safe}.
 * Use {@link #every} with an offset to spread work over ticks.
 */
public final class Scheduler {
	private static final List<Repeating> REPEATING = new ArrayList<>();
	private static final List<Delayed> DELAYED = new ArrayList<>();
	private static final List<Delayed> PENDING = new ArrayList<>();
	private static long ticks;

	private Scheduler() {
	}

	/** Runs {@code task} every {@code interval} ticks (offset spreads tasks sharing an interval). */
	public static void every(String name, int interval, int offset, Runnable task) {
		REPEATING.add(new Repeating(name, Math.max(1, interval), Math.floorMod(offset, Math.max(1, interval)), task));
	}

	public static void every(String name, int interval, Runnable task) {
		every(name, interval, REPEATING.size() * 7, task);
	}

	/** Runs once after {@code delay} ticks (cleared when the server stops). */
	public static void later(String name, int delay, Runnable task) {
		PENDING.add(new Delayed(name, ticks + Math.max(1, delay), task));
	}

	public static long ticks() {
		return ticks;
	}

	public static void onServerTick(MinecraftServer server) {
		ticks++;
		for (Repeating repeating : REPEATING) {
			if (ticks % repeating.interval == repeating.offset) {
				Safe.run(repeating.name, repeating.task);
			}
		}
		if (!PENDING.isEmpty()) {
			DELAYED.addAll(PENDING);
			PENDING.clear();
		}
		if (!DELAYED.isEmpty()) {
			Iterator<Delayed> it = DELAYED.iterator();
			while (it.hasNext()) {
				Delayed delayed = it.next();
				if (delayed.at <= ticks) {
					it.remove();
					Safe.run(delayed.name, delayed.task);
				}
			}
		}
	}

	public static void clearDelayed() {
		DELAYED.clear();
		PENDING.clear();
	}

	private record Repeating(String name, int interval, int offset, Runnable task) {
	}

	private record Delayed(String name, long at, Runnable task) {
	}
}
