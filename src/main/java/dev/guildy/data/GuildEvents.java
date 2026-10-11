package dev.guildy.data;

import dev.guildy.core.Safe;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Simple cross-module events (all fired on the server thread, each listener isolated by {@link Safe}).
 */
public final class GuildEvents {
	public static final List<Consumer<Guild>> CREATED = new ArrayList<>();
	/** Fired BEFORE the guild is removed from the manager (data still readable). */
	public static final List<Consumer<Guild>> DISBANDED = new ArrayList<>();
	public static final List<BiConsumer<Guild, UUID>> MEMBER_JOINED = new ArrayList<>();
	public static final List<BiConsumer<Guild, UUID>> MEMBER_LEFT = new ArrayList<>();
	/** Claims or heart changed (planted, extended, unclaimed, abandoned). */
	public static final List<Consumer<Guild>> TERRITORY_CHANGED = new ArrayList<>();
	/** New level reached. */
	public static final List<BiConsumer<Guild, Integer>> LEVEL_UP = new ArrayList<>();
	public static final List<Consumer<Guild>> WAVE_STARTED = new ArrayList<>();
	public static final List<Consumer<WaveOutcome>> WAVE_ENDED = new ArrayList<>();

	private GuildEvents() {
	}

	public static void fire(List<Consumer<Guild>> listeners, Guild guild) {
		for (Consumer<Guild> listener : listeners) {
			Safe.run("event", () -> listener.accept(guild));
		}
	}

	public static <T> void fire2(List<BiConsumer<Guild, T>> listeners, Guild guild, T value) {
		for (BiConsumer<Guild, T> listener : listeners) {
			Safe.run("event", () -> listener.accept(guild, value));
		}
	}

	public static void fireWaveEnded(WaveOutcome outcome) {
		for (Consumer<WaveOutcome> listener : WAVE_ENDED) {
			Safe.run("event.wave_ended", () -> listener.accept(outcome));
		}
	}

	/**
	 * Result of a night wave.
	 *
	 * @param night      night number (difficulty step) of this wave
	 * @param difficulty computed difficulty value
	 * @param kills      wave mobs killed
	 * @param total      wave mobs planned
	 */
	public record WaveOutcome(Guild guild, Result result, int night, double difficulty, int kills, int total) {
		public enum Result {
			VICTORY,
			DEFEAT,
			/** Night skipped, server restart, nobody present... : no reward, no penalty. */
			CANCELLED
		}
	}
}
