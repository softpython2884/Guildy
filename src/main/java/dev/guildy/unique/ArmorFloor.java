package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Protection floor of the unique armors (pure, unit-tested): a unique worn in an armor slot protects at least as well as
 * the netherite piece of that slot (armor points, toughness, knockback resistance, values of the vanilla netherite armor
 * material). The item factory adds the missing part as attribute modifiers of their own
 * ({@code guildy:unique_floor_<slot>_<stat>}, one per slot so that the pieces never replace each other's), any higher
 * value the item already has is kept. uniques.json5 {@code armorFloor}: "netherite" (default) or "none".
 */
public final class ArmorFloor {
	public static final String NETHERITE = "netherite";
	public static final String NONE = "none";
	public static final Set<String> MODES = Set.of(NETHERITE, NONE);
	/** Path prefix of the floor modifiers (namespace guildy; never a rune_ nor a set_ one). */
	public static final String MODIFIER_PREFIX = "unique_floor_";
	public static final String ARMOR = "minecraft:armor";
	public static final String TOUGHNESS = "minecraft:armor_toughness";
	public static final String KNOCKBACK = "minecraft:knockback_resistance";
	private static final double EPSILON = 1.0E-6;

	/** Protection of an armor piece for its slot. */
	public record Stats(double armor, double toughness, double knockback) {
		public static final Stats ZERO = new Stats(0, 0, 0);

		public Stats plus(Stats other) {
			return new Stats(armor + other.armor, toughness + other.toughness, knockback + other.knockback);
		}

		public boolean isZero() {
			return Math.abs(armor) < EPSILON && Math.abs(toughness) < EPSILON && Math.abs(knockback) < EPSILON;
		}

		/** Every value at least the other's. */
		public boolean atLeast(Stats other) {
			return armor + EPSILON >= other.armor && toughness + EPSILON >= other.toughness && knockback + EPSILON >= other.knockback;
		}
	}

	/** The netherite armor material (ArmorMaterials.NETHERITE: defense 3 / 8 / 6 / 3, toughness 3, knockback resistance 0.1). */
	private static final Map<String, Stats> NETHERITE_STATS = Map.of(
			"head", new Stats(3, 3, 0.1),
			"chest", new Stats(8, 3, 0.1),
			"legs", new Stats(6, 3, 0.1),
			"feet", new Stats(3, 3, 0.1));

	private ArmorFloor() {
	}

	/** "Netherite " -> "netherite"; null when unknown. */
	public static @Nullable String parse(@Nullable String mode) {
		if (mode == null) {
			return null;
		}
		String value = mode.trim().toLowerCase(Locale.ROOT);
		return MODES.contains(value) ? value : null;
	}

	/** The floor of an armor slot (head, chest, legs, feet) under a mode, or null (no floor, or not an armor slot). */
	public static @Nullable Stats floor(@Nullable String mode, @Nullable String slot) {
		return NETHERITE.equals(mode) && slot != null ? NETHERITE_STATS.get(slot) : null;
	}

	/** What the item lacks to reach the floor (zero for each value already at or above it). */
	public static Stats missing(@Nullable Stats floor, Stats current) {
		if (floor == null) {
			return Stats.ZERO;
		}
		return new Stats(gap(floor.armor, current.armor), gap(floor.toughness, current.toughness), gap(floor.knockback, current.knockback));
	}

	private static double gap(double floor, double current) {
		double missing = floor - current;
		return missing > EPSILON ? Math.round(missing * 1000) / 1000.0 : 0;
	}

	/**
	 * Protection of a piece: the base item's own, plus the additive attributes of a definition that apply to the slot
	 * (attribute slot "any", "armor" or the slot itself).
	 */
	public static Stats withAttributes(Stats base, List<UniqueConfig.AttributeEntry> attributes, String slot) {
		Stats total = base;
		for (UniqueConfig.AttributeEntry entry : attributes) {
			if (!"add_value".equals(entry.operation) || !appliesTo(entry.slot, slot)) {
				continue;
			}
			total = total.plus(switch (entry.attribute) {
				case ARMOR -> new Stats(entry.amount, 0, 0);
				case TOUGHNESS -> new Stats(0, entry.amount, 0);
				case KNOCKBACK -> new Stats(0, 0, entry.amount);
				default -> Stats.ZERO;
			});
		}
		return total;
	}

	/** An attribute slot group name (any, armor, head...) that counts for this armor slot. */
	public static boolean appliesTo(@Nullable String group, String slot) {
		return "any".equals(group) || "armor".equals(group) || slot.equals(group);
	}

	/** "unique_floor_feet_toughness": the floor modifier of a stat (armor, toughness, knockback) for a slot. */
	public static String modifierPath(String slot, String stat) {
		return MODIFIER_PREFIX + slot + "_" + stat;
	}

	public static boolean isFloorModifier(String path) {
		return path.startsWith(MODIFIER_PREFIX);
	}
}
