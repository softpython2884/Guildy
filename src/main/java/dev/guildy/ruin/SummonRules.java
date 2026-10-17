package dev.guildy.ruin;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Pure rules of the incantation ritual (no Minecraft classes, unit-tested): awakening mode, where an incantation scroll
 * may be read, who takes part in the ritual, the blood price (never below the minimum health), the timeline of the
 * ritual and when it is cancelled.
 */
public final class SummonRules {
	/** Guardians are awakened by an incantation scroll only. */
	public static final String MODE_SCROLL = "scroll";
	/** Old behaviour: a survival player who comes close awakens the guardian. */
	public static final String MODE_PROXIMITY = "proximity";
	/** Part of the ritual during which the participants are drained (progress 0..1). */
	public static final double DRAIN_START = 0.12;
	public static final double DRAIN_END = 0.85;
	/** Part of the ritual during which the energy column rises above the boss marker. */
	public static final double COLUMN_START = 0.45;
	/** Part of the ritual during which the ground trembles. */
	public static final double TREMOR_START = 0.5;
	/** Ticks the ritual survives without any participant (someone stepping out for a moment). */
	public static final int GRACE_TICKS = 20;

	/** Why an incantation scroll cannot be read here (NONE: it can). */
	public enum Refusal {
		NONE,
		NOT_IN_RUIN,
		WRONG_DIMENSION,
		DISABLED,
		PEACEFUL,
		CLEARED,
		CLEARED_FOREVER,
		AWAKE,
		RITUAL,
		TOO_FAR,
		BUSY,
		NOT_LOADED,
		/** Room guards are still alive in the arena (see {@link RoomGuardRules#blocksRitual}). */
		GUARDED;

		public String langKey() {
			return "ruin.summon.fail." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** What the ritual loop must do this tick. */
	public enum Outcome {
		CONTINUE,
		/** Nobody feeds the ritual any more, or the guardian woke up by other means: refund, nothing spawns. */
		CANCEL,
		/** The ritual is complete: the guardian and its escort appear. */
		CLIMAX
	}

	/**
	 * Everything that decides whether an incantation can start (or complete) for one player.
	 *
	 * @param inRuin      the player stands inside a known ruin (its box inflated by the activation margin)
	 * @param sameDimension the ruin belongs to the player's dimension
	 * @param daysLeft    defeated ruin: days before its guardian is back (-1: never), from {@link RuinRules#daysLeft}
	 * @param fightActive a fight is running in this ruin
	 * @param ritualActive another ritual is running in this ruin
	 * @param distanceSq  squared distance between the player and the boss marker
	 * @param running     fights and rituals running on the server
	 * @param loaded      the boss marker is in an entity-ticking chunk
	 */
	public record Situation(boolean bossesEnabled, boolean peaceful, boolean inRuin, boolean sameDimension, RuinState state, long daysLeft,
			boolean fightActive, boolean ritualActive, double distanceSq, double arenaRadius, int running, int maxActive, boolean loaded) {
	}

	private SummonRules() {
	}

	/** Normalized mode ("scroll" / "proximity"), null when unknown (blank = scroll). */
	public static @Nullable String mode(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return MODE_SCROLL;
		}
		String mode = value.trim().toLowerCase(Locale.ROOT);
		return MODE_SCROLL.equals(mode) || MODE_PROXIMITY.equals(mode) ? mode : null;
	}

	/** True when approaching a dormant ruin awakens its guardian (old behaviour). */
	public static boolean proximityAwakens(@Nullable String mode) {
		return MODE_PROXIMITY.equals(mode(mode));
	}

	/** First reason why the incantation cannot be read here, or {@link Refusal#NONE}. */
	public static Refusal check(Situation s) {
		if (!s.inRuin()) {
			return Refusal.NOT_IN_RUIN;
		}
		if (!s.sameDimension()) {
			return Refusal.WRONG_DIMENSION;
		}
		if (!s.bossesEnabled()) {
			return Refusal.DISABLED;
		}
		if (s.peaceful()) {
			return Refusal.PEACEFUL;
		}
		if (s.state() == RuinState.CLEARED) {
			return s.daysLeft() < 0 ? Refusal.CLEARED_FOREVER : Refusal.CLEARED;
		}
		if (s.fightActive() || s.state() == RuinState.AWAKE) {
			return Refusal.AWAKE;
		}
		if (s.ritualActive()) {
			return Refusal.RITUAL;
		}
		if (!inArena(s.distanceSq(), s.arenaRadius())) {
			return Refusal.TOO_FAR;
		}
		if (s.running() >= Math.max(1, s.maxActive())) {
			return Refusal.BUSY;
		}
		if (!s.loaded()) {
			return Refusal.NOT_LOADED;
		}
		return Refusal.NONE;
	}

	/** Within {@code radius} blocks of the boss marker. */
	public static boolean inArena(double distanceSq, double radius) {
		return distanceSq >= 0 && distanceSq <= radius * radius;
	}

	/** A player takes part in the ritual: alive, not a spectator, in the ruin's level and inside the arena. */
	public static boolean participant(boolean alive, boolean spectator, boolean sameLevel, double distanceSq, double arenaRadius) {
		return alive && !spectator && sameLevel && inArena(distanceSq, arenaRadius);
	}

	/**
	 * One step of the ritual.
	 *
	 * @param age          ticks since the ritual started
	 * @param participants players taking part right now
	 * @param emptyTicks   consecutive ticks without any participant
	 * @param dormant      the ruin is still dormant (no fight started by an admin meanwhile)
	 */
	public static Outcome step(int age, int duration, int participants, int emptyTicks, int graceTicks, boolean dormant) {
		if (!dormant) {
			return Outcome.CANCEL;
		}
		if (participants <= 0) {
			return emptyTicks >= Math.max(0, graceTicks) ? Outcome.CANCEL : Outcome.CONTINUE;
		}
		return age >= Math.max(1, duration) ? Outcome.CLIMAX : Outcome.CONTINUE;
	}

	/** Ritual length in ticks (at least one). */
	public static int ticks(double seconds) {
		return (int) Math.max(1, Math.round(seconds * 20));
	}

	/** Progress of a sub-phase: 0 before {@code start}, 1 after {@code end}, linear in between. */
	public static double phase(double progress, double start, double end) {
		if (end <= start) {
			return progress >= end ? 1 : 0;
		}
		return Math.max(0, Math.min(1, (progress - start) / (end - start)));
	}

	/** Health a participant should have lost at this point of the ritual ({@code total} once the drain phase is over). */
	public static double drainTarget(double progress, double total) {
		return Math.max(0, total) * phase(progress, DRAIN_START, DRAIN_END);
	}

	/**
	 * Health after draining {@code amount}: never below {@code minHealth}, and a player already below it loses nothing.
	 */
	public static float drain(float health, double minHealth, double amount) {
		if (amount <= 0 || health <= minHealth) {
			return health;
		}
		return (float) Math.max(minHealth, health - amount);
	}

	/** Ticks between two heartbeats: slow at first, racing near the climax. */
	public static int heartbeatTicks(double progress) {
		double p = Math.max(0, Math.min(1, progress));
		return (int) Math.max(6, Math.round(20 - 13 * p));
	}

	/** RGB colour of the ritual from the ruin's boss bar colour (crimson for unknown names). */
	public static int themeColor(@Nullable String barColor) {
		if (barColor == null) {
			return 0xC0102A;
		}
		return switch (barColor.trim().toUpperCase(Locale.ROOT)) {
			case "PINK" -> 0xFF5FD2;
			case "BLUE" -> 0x3D8BFF;
			case "GREEN" -> 0x46E86A;
			case "YELLOW" -> 0xFFC233;
			case "PURPLE" -> 0x9B3DFF;
			case "WHITE" -> 0xDDEEFF;
			default -> 0xC0102A;
		};
	}
}
