package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Java powers of the unique items (what data-driven enchantments cannot express). Pure Java: the behaviour lives in the
 * power classes, the balance in uniques.json5 ({@link UniqueConfig.Powers}).
 */
public enum UniquePower {
	/** Right click: lightning on the monsters around the aimed point. */
	STORM_CALL("storm_call", true),
	/** Right click: frost nova around the player (slowness + freeze). */
	FROST_NOVA("frost_nova", true),
	/** Right click: teleport behind the aimed monster and strike it. */
	BLINK_STRIKE("blink_strike", true),
	/** Breaking a log fells the whole natural tree. */
	TREE_FELLER("tree_feller", false),
	/** Mines 3x3 in natural stone. */
	EXCAVATE("excavate", false),
	/** Breaking an ore mines the whole vein. */
	VEIN_MINER("vein_miner", false),
	/** Boots: double jump (no fall damage while they are worn). */
	DOUBLE_JUMP("double_jump", false),
	/** Mace smash attacks send a shockwave through the nearby monsters. */
	SHOCKWAVE("shockwave", false),
	/** Arrows burst into flames on the monsters around the one they hit. */
	PHOENIX_ARROWS("phoenix_arrows", false),
	/** Thrown trident hits call lightning, chained to the nearby monsters. */
	TIDE_CALL("tide_call", false),
	/** Each kill heals the wielder. */
	SOUL_HARVEST("soul_harvest", false),
	/** Spear, right click (charge): the wielder pounces forward; the landing claws the monsters around. */
	JAGUAR_POUNCE("jaguar_pounce", true),
	/** Helmet: the monsters that hit the wearer are frozen; the wearer never freezes. */
	FROST_CROWN("frost_crown", false),
	/** Shield: a blocked monster attack is sent back to its author and drains its life to the wielder. */
	SOUL_AEGIS("soul_aegis", false),
	/** Mace, right click: leap into the sky; the landing or the next smash attack bursts into a sun. */
	SOLAR_LEAP("solar_leap", true),
	/** Sword: each hit seals the monster; the last seal opens a cursed pentagram that bursts and spreads. */
	PACT_SEAL("pact_seal", false),
	/** Axe, right click: a magma rift runs along the ground, erupting under the monsters. */
	INFERNAL_RIFT("infernal_rift", true),
	/** Chestplate: below a health threshold, a jade gale repels and poisons the monsters around and heals the wearer. */
	JADE_GALE("jade_gale", false);

	public final String id;
	/** Triggered by a right click (cooldown shown on the item by the vanilla cooldown overlay). */
	public final boolean active;

	UniquePower(String id, boolean active) {
		this.id = id;
		this.active = active;
	}

	/** Lang key prefix: unique.power.{id}.name / .lore */
	public String langKey() {
		return "unique.power." + id;
	}

	public static @Nullable UniquePower byId(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		for (UniquePower power : values()) {
			if (power.id.equals(key)) {
				return power;
			}
		}
		return null;
	}

	public static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (UniquePower power : values()) {
			ids.add(power.id);
		}
		return ids;
	}
}
