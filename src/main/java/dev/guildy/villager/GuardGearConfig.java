package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * villagers.json5, section "guardGear": base equipment of the guards by guild level (free, never an item that can be
 * taken: see {@link GuardGear}). Free of Minecraft classes (unit tests).
 */
@Json5.Section
public final class GuardGearConfig {
	@Comment({
			"Equipement de base des gardes selon le niveau de la guilde (false = aucun : epee en fer et arc d'affichage, degats",
			"de base). Il est gratuit et n'existe pas en objet : il ne tombe jamais, ne se recupere pas et ne s'use pas. Une",
			"arme ou une piece d'armure prise dans les coffres du stockage la remplace quand elle est meilleure."
	})
	public boolean enabled = true;
	@Comment({
			"Paliers : la ligne du plus haut 'level' atteint par la guilde s'applique.",
			"sword : epee des epeistes (+ sharpness = Tranchant). ranged : 'minecraft:bow' (+ power = Puissance) ou",
			"'minecraft:crossbow' (+ quickCharge = Charge rapide, piercing = Perforation, multishot = Tir multiple 0/1) des archers.",
			"armor : armure complete 'minecraft:leather', 'minecraft:chainmail', 'minecraft:copper', 'minecraft:iron',",
			"'minecraft:golden', 'minecraft:diamond', 'minecraft:netherite' ('' = aucune). Degats : guard.damage (+ damagePerLevel)",
			"avec une epee en bois, + la difference de degats d'une meilleure epee (pierre +1, fer +2, diamant +3, netherite +4)."
	})
	public List<GearLevel> levels = defaultLevels();
	@Comment("Arbalete : degats d'un carreau = degats d'une fleche de garde x ce facteur")
	public double crossbowDamageFactor = 1.25;
	@Comment("Arbalete : chaque niveau de Charge rapide raccourcit de ce nombre de ticks le delai entre deux tirs (guard.bowCooldownTicks)")
	public int quickChargeTicks = 5;

	/** One step of the table (plain JSON object in the file). */
	public static final class GearLevel {
		public int level = 1;
		public String sword = "minecraft:wooden_sword";
		public int sharpness = 0;
		public String ranged = "minecraft:bow";
		public int power = 0;
		public int quickCharge = 0;
		public int piercing = 0;
		public int multishot = 0;
		public String armor = "";

		public GearLevel() {
		}

		GearLevel(int level, String sword, int sharpness, String ranged, int power, int quickCharge, int piercing, String armor) {
			this.level = level;
			this.sword = sword;
			this.sharpness = sharpness;
			this.ranged = ranged;
			this.power = power;
			this.quickCharge = quickCharge;
			this.piercing = piercing;
			this.armor = armor;
		}

		boolean crossbow() {
			return ranged.endsWith(":crossbow") || ranged.equals("crossbow");
		}

		/** Identity of the step's content (cache key of the built items). */
		String key() {
			return sword + "/" + sharpness + "/" + ranged + "/" + power + "/" + quickCharge + "/" + piercing + "/" + multishot + "/" + armor;
		}
	}

	/**
	 * Wooden / stone sword → iron → diamond → netherite; bow, then Power; crossbow from level 6 (Quick Charge, then
	 * Piercing); armor none → leather → chainmail → iron → diamond; past level 10: sharper swords, netherite armor at
	 * level 30, Multishot at level 50.
	 */
	public static List<GearLevel> defaultLevels() {
		List<GearLevel> levels = classicLevels();
		levels.addAll(highLevels());
		return levels;
	}

	/** Steps of the guild levels past 10 (added once to the tables written before, see {@link #addHighLevelSteps}). */
	static List<GearLevel> highLevels() {
		String crossbow = "minecraft:crossbow";
		GearLevel fifty = new GearLevel(50, "minecraft:netherite_sword", 5, crossbow, 0, 3, 4, "minecraft:netherite");
		fifty.multishot = 1;
		return new ArrayList<>(List.of(
				new GearLevel(20, "minecraft:netherite_sword", 3, crossbow, 0, 3, 3, "minecraft:diamond"),
				new GearLevel(30, "minecraft:netherite_sword", 4, crossbow, 0, 3, 4, "minecraft:netherite"),
				fifty));
	}

	/**
	 * Old tables (format 6 and before) that are still exactly the old default table get the steps of the levels past 10.
	 *
	 * @return true when steps were added
	 */
	static boolean addHighLevelSteps(GuardGearConfig gear) {
		if (gear.levels == null) {
			return false;
		}
		List<GearLevel> old = classicLevels();
		if (gear.levels.size() != old.size()) {
			return false;
		}
		for (int i = 0; i < old.size(); i++) {
			GearLevel step = gear.levels.get(i);
			if (step == null || step.level != old.get(i).level || !step.key().equals(old.get(i).key())) {
				return false;
			}
		}
		gear.levels = new ArrayList<>(gear.levels);
		gear.levels.addAll(highLevels());
		return true;
	}

	/** The table of the levels 1 to 10 (the whole default table before guild levels past 10). */
	static List<GearLevel> classicLevels() {
		String bow = "minecraft:bow";
		String crossbow = "minecraft:crossbow";
		return new ArrayList<>(List.of(
				new GearLevel(1, "minecraft:wooden_sword", 0, bow, 0, 0, 0, ""),
				new GearLevel(2, "minecraft:stone_sword", 0, bow, 0, 0, 0, "minecraft:leather"),
				new GearLevel(3, "minecraft:stone_sword", 0, bow, 1, 0, 0, "minecraft:leather"),
				new GearLevel(4, "minecraft:iron_sword", 0, bow, 1, 0, 0, "minecraft:chainmail"),
				new GearLevel(5, "minecraft:iron_sword", 0, bow, 2, 0, 0, "minecraft:chainmail"),
				new GearLevel(6, "minecraft:iron_sword", 1, crossbow, 0, 1, 0, "minecraft:iron"),
				new GearLevel(7, "minecraft:diamond_sword", 0, crossbow, 0, 1, 1, "minecraft:iron"),
				new GearLevel(8, "minecraft:diamond_sword", 1, crossbow, 0, 2, 1, "minecraft:iron"),
				new GearLevel(9, "minecraft:diamond_sword", 2, crossbow, 0, 2, 2, "minecraft:diamond"),
				new GearLevel(10, "minecraft:netherite_sword", 2, crossbow, 0, 3, 2, "minecraft:diamond")));
	}

	/** The step of a guild level: the highest {@code level} reached, null when disabled or below every step. */
	public @Nullable GearLevel forLevel(int guildLevel) {
		if (!enabled || levels == null) {
			return null;
		}
		GearLevel found = null;
		for (GearLevel step : levels) {
			if (step != null && step.level <= guildLevel && (found == null || step.level >= found.level)) {
				found = step;
			}
		}
		return found;
	}

	/** Shooting delay of a crossbow with Quick Charge (never below 5 ticks). */
	public int crossbowCooldown(int bowCooldownTicks, int quickCharge) {
		return Math.max(5, bowCooldownTicks - Math.max(0, quickCharge) * quickChargeTicks);
	}

	void validate() {
		if (levels == null) {
			levels = defaultLevels();
		}
		List<GearLevel> cleaned = new ArrayList<>();
		for (GearLevel step : levels) {
			if (step == null) {
				continue;
			}
			step.level = Math.max(1, Math.min(1000, step.level));
			step.sword = id(step.sword);
			step.ranged = step.ranged == null || step.ranged.isBlank() ? "minecraft:bow" : id(step.ranged);
			step.armor = id(step.armor);
			step.sharpness = enchant(step.sharpness);
			step.power = enchant(step.power);
			step.quickCharge = enchant(step.quickCharge);
			step.piercing = enchant(step.piercing);
			step.multishot = Math.max(0, Math.min(1, step.multishot));
			cleaned.add(step);
		}
		cleaned.sort(Comparator.comparingInt(step -> step.level));
		levels = cleaned;
		crossbowDamageFactor = Double.isNaN(crossbowDamageFactor) ? 1.0 : Math.max(0.0, Math.min(10.0, crossbowDamageFactor));
		quickChargeTicks = Math.max(0, Math.min(20, quickChargeTicks));
	}

	private static String id(@Nullable String value) {
		return value == null ? "" : value.trim();
	}

	private static int enchant(int level) {
		return Math.max(0, Math.min(5, level));
	}
}
