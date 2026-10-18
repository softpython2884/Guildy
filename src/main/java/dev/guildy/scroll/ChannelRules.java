package dev.guildy.scroll;

/**
 * Pure rules of an incantation's channel (unit-tested): how long a grimoire reads a spell (a share of the scroll's own
 * channel, never shorter than the grimoire's minimum, never longer than the scroll), ticks like the vanilla consumable
 * component, and how far the runic circle under the caster is drawn: the drawing speed follows the channel so that the
 * circle is complete a little before the spell fires, whatever the channel's length.
 */
public final class ChannelRules {
	/** Share of the channel spent drawing the circle; the rest shows it complete, glowing, until the cast. */
	public static final double DRAW_SHARE = 0.85;

	private ChannelRules() {
	}

	/**
	 * Channel (seconds) of a spell read from a grimoire: {@code factor} times the scroll's channel, at least
	 * {@code minSeconds}, but never longer than the scroll itself (the grimoire is never slower than its scroll).
	 */
	public static double grimoireSeconds(double scrollSeconds, double minSeconds, double factor) {
		double scroll = Double.isFinite(scrollSeconds) && scrollSeconds > 0 ? scrollSeconds : minSeconds;
		double share = Double.isFinite(factor) && factor > 0 ? scroll * factor : 0;
		double seconds = Math.min(scroll, Math.max(minSeconds, share));
		return Double.isFinite(seconds) && seconds > 0 ? seconds : 0.05;
	}

	/** Channel time in ticks, computed like the vanilla consumable component does (at least one). */
	public static int ticks(double seconds) {
		return Math.max(1, (int) ((float) seconds * 20.0F));
	}

	/** Ticks spent drawing the circle of a channel lasting {@code total} ticks (at least one). */
	public static int drawTicks(int total) {
		return Math.max(1, (int) Math.round(Math.max(1, total) * DRAW_SHARE));
	}

	/**
	 * Part of the runic circle drawn {@code elapsed} ticks into a channel of {@code total} ticks (0..1): linear, complete
	 * at {@link #drawTicks} (so always before the spell fires, the last frames show the whole circle).
	 */
	public static double drawProgress(long elapsed, int total) {
		if (elapsed < 0) {
			return 0;
		}
		return Math.min(1.0, (elapsed + 1) / (double) drawTicks(total));
	}
}
