package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

/**
 * Role "fetch the materials" of the builders: crafts, smelts or gathers what {@link Procurement} lists for the guild and
 * brings it to the storage. One instance per builder while it has this role.
 */
final class GatherWork {
	/** Null when this villager can fetch something useful now, otherwise the lang key of the reason (shown in its menu). */
	static @Nullable String blocked(WorkContext ctx) {
		return "villager.gather.unavailable";
	}

	/** One step, at most once per second. */
	void tick(WorkContext ctx) {
	}

	/** True when the current trip is over (nothing more to fetch, or everything delivered): the villager may switch role. */
	boolean finished() {
		return true;
	}

	/** Releases what it holds (reservations...) when the villager changes role or task. */
	void stop(WorkContext ctx) {
	}
}
