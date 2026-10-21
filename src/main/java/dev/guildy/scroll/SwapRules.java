package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

/**
 * Safety of Permutation (pure Java, unit-tested): where the caster lands (the target's spot) and where the target lands
 * (the caster's spot). The caster never lands in a block, in lava, fire or on a harmful block, nor above the void: a target
 * floating in the air sends the caster to the ground under it when that ground is at most {@link #MAX_DROP} blocks below,
 * otherwise the swap is refused. The target only needs room at the caster's spot (the caster stood there).
 */
public final class SwapRules {
	/** Largest fall (blocks) under a floating target: the caster lands on that ground (no fall damage below 4 blocks). */
	public static final int MAX_DROP = 3;

	/** Why a destination is refused (OK = safe). */
	public enum Verdict {
		OK,
		/** The chunk is not loaded. */
		UNLOADED,
		/** Not enough room for the hitbox (a wall, a ceiling, a big monster at a small spot). */
		BLOCKED,
		LAVA,
		/** Fire, magma, lit campfire, cactus, berry bush, wither rose, powder snow, dripstone. */
		HAZARD,
		/** No ground close enough under the spot (void, a deep pit, a flying monster high in the air). */
		FALL
	}

	/**
	 * What a destination looks like for the entity that moves there.
	 *
	 * The caster's spot is the ground under the target (the target's feet lowered by {@code drop}): room, lava and hazards
	 * are read there.
	 *
	 * @param loaded loaded chunk
	 * @param free   the hitbox fits (no collision)
	 * @param lava   lava at the feet or the head
	 * @param hazard a harmful block at the feet, the head or under the feet
	 * @param drop   blocks of fall under the target's feet down to solid ground or water (0 = standing on it, -1 = none
	 *               found within the scan)
	 */
	public record Spot(boolean loaded, boolean free, boolean lava, boolean hazard, int drop) {
	}

	private SwapRules() {
	}

	/** Where the caster lands: every rule (room, lava, hazards, ground at most {@link #MAX_DROP} blocks below). */
	public static Verdict forCaster(Spot spot) {
		if (!spot.loaded()) {
			return Verdict.UNLOADED;
		}
		if (!spot.free()) {
			return Verdict.BLOCKED;
		}
		if (spot.lava()) {
			return Verdict.LAVA;
		}
		if (spot.hazard()) {
			return Verdict.HAZARD;
		}
		return spot.drop() < 0 || spot.drop() > MAX_DROP ? Verdict.FALL : Verdict.OK;
	}

	/** Where the target lands (the caster's own spot): only room for its hitbox. */
	public static Verdict forTarget(Spot spot) {
		if (!spot.loaded()) {
			return Verdict.UNLOADED;
		}
		return spot.free() ? Verdict.OK : Verdict.BLOCKED;
	}

	/** Both destinations: the first problem (the caster's first), OK when both are safe. */
	public static Verdict check(Spot caster, Spot target) {
		Verdict first = forCaster(caster);
		return first != Verdict.OK ? first : forTarget(target);
	}

	/** Lang key of a refusal (scroll kept), or null for OK. */
	public static @Nullable String failureKey(Verdict verdict) {
		return switch (verdict) {
			case OK -> null;
			case UNLOADED, BLOCKED -> "scroll.fail.swap_blocked";
			case LAVA, HAZARD -> "scroll.fail.swap_hazard";
			case FALL -> "scroll.fail.swap_fall";
		};
	}
}
