package dev.guildy.stats;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The titles of a fight summary: one statistic each, the best player (ties share the title) gets it. Ids are the values of
 * {@code stats.categories} in ruins.json5.
 */
public enum FightCategory {
	/** "Fléau du gardien": damage to the guardian. */
	GUARDIAN("guardian", t -> t.damage(FightKind.BOSS)),
	/** "Briseur de gardes": damage to the room guards. */
	GUARDS("guards", t -> t.damage(FightKind.GUARD)),
	/** "Chasseur de sbires": damage to the escort (minions, shadows). */
	ESCORT("escort", t -> t.damage(FightKind.ESCORT)),
	/** "Ange gardien": health given back to the other players. */
	HEALING("healing", FightTally::healing),
	/** "Protecteur": absorption hearts given to the other players. */
	SHIELD("shield", FightTally::shielding),
	/** "Rempart": damage taken. */
	TANK("tank", FightTally::taken),
	/** "Coup fatal": the blow that killed the guardian. */
	FINAL_BLOW("final_blow", t -> t.finalBlow() ? 1 : 0),
	/** "Moissonneur": monsters of the fight killed. */
	KILLS("kills", FightTally::kills),
	/** "Arcaniste": spells cast. */
	SPELLS("spells", FightTally::spells);

	public final String id;
	private final ToDoubleFunction<FightTally> metric;

	FightCategory(String id, ToDoubleFunction<FightTally> metric) {
		this.id = id;
		this.metric = metric;
	}

	public double value(FightTally tally) {
		return metric.applyAsDouble(tally);
	}

	/** Counted in whole units (no rounding of a health amount). */
	public boolean count() {
		return this == FINAL_BLOW || this == KILLS || this == SPELLS;
	}

	public static @Nullable FightCategory byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		String clean = id.trim().toLowerCase(Locale.ROOT);
		for (FightCategory category : values()) {
			if (category.id.equals(clean)) {
				return category;
			}
		}
		return null;
	}

	/** The known categories of {@code ids}, in order, without duplicates (unknown ids are skipped). */
	public static List<FightCategory> parse(@Nullable List<String> ids) {
		List<FightCategory> list = new ArrayList<>();
		if (ids == null) {
			return list;
		}
		for (String id : ids) {
			FightCategory category = byId(id);
			if (category != null && !list.contains(category)) {
				list.add(category);
			}
		}
		return list;
	}
}
