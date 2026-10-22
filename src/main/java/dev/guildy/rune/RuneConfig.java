package dev.guildy.rune;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * runes.json5 - the runes the blacksmith villagers inscribe on the armor pieces of the guild's armor stands: when and how
 * often ({@link Inscription}), the tier weights, the rune definitions (stat buffs as attribute modifiers, powers) and the
 * settings of the powers. Free of Minecraft classes (unit tests); attribute ids are checked once the server has started.
 */
@Comment({
		"Guildy - runes gravees par les forgerons : quand un forgeron travaille sur une armure posee sur un porte-armure",
		"(reparation terminee, ou examen periodique d'une piece intacte), il a une chance d'y graver une rune (une seule par",
		"piece, jamais sur un objet unique). Commune : petit bonus de caracteristique ; rare : petit pouvoir amusant ;",
		"mythique (plastron en netherite seulement) : pouvoir legendaire. Une meule retire la rune (les enchantements restent).",
		"Commandes admin : /guild admin rune list | inscribe <joueur> <id> | remove <joueur> (objet en main)."
})
public final class RuneConfig implements Validatable {
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");
	/** Current file format (new default entries are added to older files once, see {@link #clean()}). */
	public static final int CURRENT_FORMAT = 1;
	public static final Pattern RUNE_ID = Pattern.compile("[a-z0-9_]{1,32}");
	private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	/** Armor slots a rune can go on (equippable slot of the piece). */
	public static final List<String> SLOTS = List.of("head", "chest", "legs", "feet");
	public static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int formatVersion = 0;

	@Comment({
			"Runes activees. false : plus aucune rune gravee et les pouvoirs des runes deja gravees sont coupes (leurs bonus",
			"de caracteristiques restent sur les objets ; une meule les retire toujours)"
	})
	public boolean enabled = true;

	@Comment("Gravure par les forgerons : niveau de guilde, chance, plafond par jour, examens, cout en materiau")
	public Inscription inscription = new Inscription();

	@Comment({
			"Poids des rangs quand une rune est tiree (le rang est tire d'abord, puis une rune de ce rang parmi celles qui vont",
			"sur la piece). Un rang sans rune possible pour la piece est ignore : les runes mythiques ne vont que sur les objets",
			"de mythicItems, donc les autres pieces ne tirent qu'entre commune et rare."
	})
	public Tiers tiers = new Tiers();

	@Comment({
			"Objets qui peuvent recevoir une rune mythique (plastrons seulement : les runes mythiques vont sur le torse)."
	})
	public List<String> mythicItems = new ArrayList<>(List.of("minecraft:netherite_chestplate"));

	@Comment({
			"Les runes. id : identifiant (a-z, 0-9, _) ; name : nom affiche ; glyph : caractere runique du nom ; tier : common,",
			"rare ou mythic ; slots : pieces possibles (head, chest, legs, feet ; les runes mythiques vont toujours sur chest) ;",
			"weight : poids parmi les runes du meme rang ; attributes : bonus de caracteristiques (attribut vanilla, amount,",
			"operation add_value | add_multiplied_base | add_multiplied_total), ajoutes a ceux de la piece avec les identifiants",
			"guildy:rune_<id>_<emplacement> ; power : pouvoir (sparks, ember, glow, echo, fox, bounce ; phoenix et dragon",
			"seulement sur une rune mythique ; \"\" = aucun) ; lore : lignes d'ambiance ; enabled : false = plus jamais tiree",
			"(les objets deja graves la gardent, sans pouvoir). Changer les valeurs ne touche que les nouvelles gravures ; les",
			"reglages des pouvoirs (section powers) s'appliquent tout de suite."
	})
	public List<RuneDef> runes = defaultRunes();

	@Comment("Reglages des pouvoirs des runes (jamais contre les joueurs, villageois ou animaux : monstres seulement)")
	public Powers powers = new Powers();

	// ------------------------------------------------------------------ sections

	@Json5.Section
	public static final class Inscription {
		@Comment("Niveau de guilde minimum pour que les forgerons gravent des runes (1 a 100)")
		public int minGuildLevel = 5;
		@Comment({
				"Chance (0 a 1) de graver une rune a chaque occasion : une piece d'armure reparee jusqu'au bout, ou un examen",
				"termine (le forgeron examine les pieces intactes des porte-armures quand il n'a rien a reparer)"
		})
		public double chance = 0.15;
		@Comment("Runes gravees au plus par forgeron et par jour de jeu (0 = jamais de rune)")
		public int maxPerDay = 2;
		@Comment({
				"Intervalle minimum (minutes) entre deux examens d'un meme porte-armure (une reparation terminee compte comme un",
				"examen) (1 a 1440)"
		})
		public int inspectionMinutes = 15;
		@Comment("Temps de travail (secondes) d'un examen a l'enclume ; le bonus de vitesse de travail s'applique (1 a 600)")
		public int secondsPerInspection = 20;
		@Comment({
				"Materiau use pour chaque rune gravee, pris dans le stockage de la guilde (coffres de depot, de sa categorie ou",
				"Divers) : le forgeron va le chercher avant un examen. \"\" ou materialCount 0 = gratuit"
		})
		public String material = "minecraft:amethyst_shard";
		@Comment("Nombre de materiaux par rune (0 a 64)")
		public int materialCount = 1;
		@Comment("Annonce a tout le serveur quand une rune mythique est gravee")
		public boolean announceMythic = true;

		/** Material id, or "" when the runes are free. */
		public String materialId() {
			return materialCount <= 0 || material == null ? "" : material;
		}
	}

	@Json5.Section
	public static final class Tiers {
		@Comment("Poids du rang commun (petits bonus de caracteristiques)")
		public int common = 80;
		@Comment("Poids du rang rare (petits pouvoirs amusants)")
		public int rare = 18;
		@Comment("Poids du rang mythique (plastron en netherite seulement)")
		public int mythic = 2;

		public int weight(RuneTier tier) {
			return switch (tier) {
				case COMMON -> common;
				case RARE -> rare;
				case MYTHIC -> mythic;
			};
		}
	}

	/** One rune of the config. */
	public static final class RuneDef {
		public String id = "";
		public String name = "";
		public String glyph = "ᛟ";
		public String tier = "common";
		public List<String> slots = new ArrayList<>(SLOTS);
		public int weight = 10;
		public List<AttributeDef> attributes = new ArrayList<>();
		public String power = "";
		public List<String> lore = new ArrayList<>();
		public boolean enabled = true;

		public RuneDef() {
		}

		RuneDef(String id, String name, String glyph, RuneTier tier, int weight, String... slots) {
			this.id = id;
			this.name = name;
			this.glyph = glyph;
			this.tier = tier.id;
			this.weight = weight;
			this.slots = new ArrayList<>(List.of(slots));
		}

		RuneDef attribute(String attribute, double amount, String operation) {
			attributes.add(new AttributeDef(attribute, amount, operation));
			return this;
		}

		RuneDef power(RunePower value) {
			power = value.id;
			return this;
		}

		RuneDef lore(String... lines) {
			lore.addAll(List.of(lines));
			return this;
		}

		/** Tier of the rune (common when the value is unknown). */
		public RuneTier tier() {
			RuneTier parsed = RuneTier.parse(tier);
			return parsed == null ? RuneTier.COMMON : parsed;
		}

		/** Power of the rune, null when it has none. */
		public @Nullable RunePower power() {
			return RunePower.parse(power);
		}

		public boolean allows(String slot) {
			return slots != null && slots.contains(slot);
		}
	}

	/** A vanilla attribute bonus of a rune. */
	public static final class AttributeDef {
		public String attribute = "";
		public double amount;
		public String operation = "add_value";

		public AttributeDef() {
		}

		AttributeDef(String attribute, double amount, String operation) {
			this.attribute = attribute;
			this.amount = amount;
			this.operation = operation;
		}

		/** Namespaced attribute id. */
		public String attributeId() {
			String value = attribute == null ? "" : attribute.trim().toLowerCase(Locale.ROOT);
			return value.contains(":") ? value : "minecraft:" + value;
		}

		public boolean multiplied() {
			return !"add_value".equals(operation);
		}
	}

	@Json5.Section
	public static final class Powers {
		@Comment("Etincelles : un monstre qui frappe au corps a corps prend une gerbe d'etincelles")
		public Sparks sparks = new Sparks();
		@Comment("Braise : un monstre qui vous frappe peut prendre feu")
		public Ember ember = new Ember();
		@Comment("Lueur : quand vous etes frappe, les monstres autour brillent quelques secondes")
		public Glow glow = new Glow();
		@Comment("Echo : rester accroupi immobile envoie une onde qui revele les monstres autour")
		public Echo echo = new Echo();
		@Comment("Renard : les objets au sol tout proches volent vers vous (pas accroupi)")
		public Fox fox = new Fox();
		@Comment("Rebond : apres une grande chute, vous rebondissez (la rune reduit aussi les degats de chute)")
		public Bounce bounce = new Bounce();
		@Comment("Phenix (mythique) : un coup mortel vous releve de vos cendres dans une explosion de flammes")
		public Phoenix phoenix = new Phoenix();
		@Comment("Dragon (mythique) : le plastron plane comme des elytres ; accroupi + saut = souffle du dragon")
		public Dragon dragon = new Dragon();
	}

	@Json5.Section
	public static final class Sparks {
		@Comment("Degats des etincelles (points de vie, 2 = 1 coeur)")
		public double damage = 2.0;
		@Comment("Recharge (secondes)")
		public double cooldownSeconds = 3;
	}

	@Json5.Section
	public static final class Ember {
		@Comment("Chance (0 a 1) d'enflammer le monstre")
		public double chance = 0.35;
		@Comment("Duree du feu (secondes)")
		public int fireSeconds = 4;
		@Comment("Recharge (secondes)")
		public double cooldownSeconds = 4;
	}

	@Json5.Section
	public static final class Glow {
		@Comment("Rayon (blocs) autour du porteur")
		public double radius = 12;
		@Comment("Duree de la lueur (secondes)")
		public int seconds = 6;
		@Comment("Recharge (secondes)")
		public double cooldownSeconds = 15;
	}

	@Json5.Section
	public static final class Echo {
		@Comment("Temps accroupi immobile avant l'onde (secondes)")
		public double sneakSeconds = 2;
		@Comment("Rayon de l'onde (blocs)")
		public double radius = 24;
		@Comment("Duree de la lueur des monstres reveles (secondes)")
		public int seconds = 4;
		@Comment("Recharge (secondes)")
		public double cooldownSeconds = 20;
	}

	@Json5.Section
	public static final class Fox {
		@Comment("Rayon d'attraction des objets (blocs)")
		public double radius = 5;
		@Comment("Objets attires au plus a chaque impulsion (deux par seconde)")
		public int maxItems = 12;
	}

	@Json5.Section
	public static final class Bounce {
		@Comment("Hauteur de chute minimum (blocs) pour rebondir")
		public double minFall = 4;
		@Comment("Force du rebond (vitesse verticale ; 0.42 = un saut)")
		public double hop = 0.5;
		@Comment("Recharge (secondes)")
		public double cooldownSeconds = 1;
	}

	@Json5.Section
	public static final class Phoenix {
		@Comment("Recharge (minutes de jeu ; elle suit le plastron et survit aux redemarrages)")
		public int cooldownMinutes = 20;
		@Comment("Vie rendue (part de la vie maximum, 0.5 = la moitie)")
		public double healthRatio = 0.5;
		@Comment("Rayon de l'explosion de flammes (blocs)")
		public double radius = 6;
		@Comment("Degats de feu aux monstres du rayon")
		public double damage = 6;
		@Comment("Les monstres du rayon brulent (secondes)")
		public int fireSeconds = 6;
		@Comment("Force de projection des monstres")
		public double push = 1.2;
		@Comment("Resistance au feu donnee au porteur (secondes)")
		public int fireResistanceSeconds = 15;
		@Comment("Regeneration donnee au porteur (secondes, niveau)")
		public int regenSeconds = 8;
		public int regenLevel = 2;
	}

	@Json5.Section
	public static final class Dragon {
		@Comment("Le plastron plane comme des elytres (sauter en l'air ; il s'use en planant, le forgeron le repare)")
		public boolean glide = true;
		@Comment("Recharge du souffle (secondes)")
		public double breathCooldownSeconds = 20;
		@Comment("Portee du souffle (blocs)")
		public double breathRange = 9;
		@Comment("Demi-angle du cone du souffle (degres)")
		public double breathAngle = 40;
		@Comment("Degats du souffle aux monstres du cone")
		public double breathDamage = 6;
		@Comment("Les monstres touches brulent (secondes)")
		public int breathFireSeconds = 4;
	}

	// ------------------------------------------------------------------ validation

	@Override
	public void validate() {
		for (String warning : clean()) {
			LOGGER.warn("[runes.json5] {}", warning);
		}
	}

	/** Bounds every value, normalizes ids, drops the invalid entries (pure). @return the problems found */
	public List<String> clean() {
		List<String> warnings = new ArrayList<>();
		if (formatVersion < CURRENT_FORMAT) {
			// Format 1 is the first one: later formats add their new default runes here, once.
			formatVersion = CURRENT_FORMAT;
		}
		if (inscription == null) {
			inscription = new Inscription();
		}
		cleanInscription(inscription, warnings);
		if (tiers == null) {
			tiers = new Tiers();
		}
		tiers.common = Math.max(0, Math.min(10000, tiers.common));
		tiers.rare = Math.max(0, Math.min(10000, tiers.rare));
		tiers.mythic = Math.max(0, Math.min(10000, tiers.mythic));
		if (tiers.common + tiers.rare + tiers.mythic <= 0) {
			warnings.add("tiers: every weight is 0: no rune can be inscribed");
		}
		Set<String> mythic = new LinkedHashSet<>();
		for (String item : mythicItems == null ? List.<String>of() : mythicItems) {
			String id = itemId(item);
			if (id == null) {
				warnings.add("mythicItems: invalid item id '" + item + "': ignored");
				continue;
			}
			mythic.add(id);
		}
		mythicItems = new ArrayList<>(mythic);
		if (runes == null) {
			runes = defaultRunes();
		}
		List<RuneDef> kept = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (RuneDef def : runes) {
			if (def == null) {
				continue;
			}
			def.id = def.id == null ? "" : def.id.trim().toLowerCase(Locale.ROOT);
			if (!RUNE_ID.matcher(def.id).matches() || !ids.add(def.id)) {
				warnings.add("runes: invalid or duplicate id '" + def.id + "' (a-z, 0-9, _): ignored");
				continue;
			}
			cleanRune(def, warnings);
			kept.add(def);
		}
		runes = kept;
		if (powers == null) {
			powers = new Powers();
		}
		cleanPowers(powers);
		return warnings;
	}

	private static void cleanInscription(Inscription in, List<String> warnings) {
		in.minGuildLevel = Math.max(1, Math.min(100, in.minGuildLevel));
		in.chance = bound(in.chance, 0, 1);
		in.maxPerDay = Math.max(0, Math.min(1000, in.maxPerDay));
		in.inspectionMinutes = Math.max(1, Math.min(1440, in.inspectionMinutes));
		in.secondsPerInspection = Math.max(1, Math.min(600, in.secondsPerInspection));
		in.materialCount = Math.max(0, Math.min(64, in.materialCount));
		if (in.material == null || in.material.isBlank()) {
			in.material = "";
		} else {
			String id = itemId(in.material);
			if (id == null) {
				warnings.add("inscription.material: invalid item id '" + in.material + "': runes are free");
				id = "";
			}
			in.material = id;
		}
	}

	private static void cleanRune(RuneDef def, List<String> warnings) {
		String where = "runes." + def.id;
		if (def.name == null || def.name.isBlank()) {
			def.name = def.id;
		}
		if (def.glyph == null || def.glyph.isBlank()) {
			def.glyph = "ᛟ";
		} else if (def.glyph.codePointCount(0, def.glyph.length()) > 2) {
			def.glyph = def.glyph.substring(0, def.glyph.offsetByCodePoints(0, 2));
		}
		RuneTier tier = RuneTier.parse(def.tier);
		if (tier == null) {
			warnings.add(where + ": unknown tier '" + def.tier + "' (common, rare, mythic): common");
			tier = RuneTier.COMMON;
		}
		def.tier = tier.id;
		Set<String> slots = new LinkedHashSet<>();
		for (String slot : def.slots == null ? List.<String>of() : def.slots) {
			String value = slot == null ? "" : slot.trim().toLowerCase(Locale.ROOT);
			if (SLOTS.contains(value)) {
				slots.add(value);
			} else {
				warnings.add(where + ": unknown slot '" + slot + "' (head, chest, legs, feet): ignored");
			}
		}
		if (tier == RuneTier.MYTHIC) {
			// Mythic runes always go on the chestplate (mythicItems).
			if (!slots.isEmpty() && !slots.equals(Set.of("chest"))) {
				warnings.add(where + ": a mythic rune only goes on a chestplate: slots = [chest]");
			}
			slots.clear();
			slots.add("chest");
		}
		if (slots.isEmpty()) {
			warnings.add(where + ": no slot: the rune can never be inscribed");
		}
		def.slots = new ArrayList<>(slots);
		def.weight = Math.max(0, Math.min(10000, def.weight));
		List<AttributeDef> attributes = new ArrayList<>();
		for (AttributeDef attribute : def.attributes == null ? List.<AttributeDef>of() : def.attributes) {
			if (attribute == null || !ITEM_ID.matcher(attribute.attributeId()).matches() || !Double.isFinite(attribute.amount)) {
				warnings.add(where + ": invalid attribute: ignored");
				continue;
			}
			attribute.attribute = attribute.attributeId();
			String op = attribute.operation == null ? "" : attribute.operation.trim().toLowerCase(Locale.ROOT);
			if (!OPERATIONS.contains(op)) {
				warnings.add(where + ": unknown operation '" + attribute.operation + "': add_value");
				op = "add_value";
			}
			attribute.operation = op;
			attribute.amount = Math.max(-1024, Math.min(1024, attribute.amount));
			attributes.add(attribute);
		}
		def.attributes = attributes;
		String powerText = def.power == null ? "" : def.power.trim().toLowerCase(Locale.ROOT);
		RunePower power = RunePower.parse(powerText);
		if (!powerText.isEmpty() && power == null) {
			warnings.add(where + ": unknown power '" + def.power + "': none");
		}
		if (power != null && power.mythic && tier != RuneTier.MYTHIC) {
			warnings.add(where + ": the power '" + power.id + "' only goes on a mythic rune: removed");
			power = null;
		}
		def.power = power == null ? "" : power.id;
		def.lore = def.lore == null ? new ArrayList<>() : new ArrayList<>(def.lore);
		def.lore.removeIf(line -> line == null);
		if (def.attributes.isEmpty() && power == null) {
			warnings.add(where + ": no attribute and no power: the rune does nothing");
		}
	}

	private static void cleanPowers(Powers p) {
		if (p.sparks == null) {
			p.sparks = new Sparks();
		}
		p.sparks.damage = bound(p.sparks.damage, 0, 100);
		p.sparks.cooldownSeconds = bound(p.sparks.cooldownSeconds, 0, 3600);
		if (p.ember == null) {
			p.ember = new Ember();
		}
		p.ember.chance = bound(p.ember.chance, 0, 1);
		p.ember.fireSeconds = Math.max(1, Math.min(60, p.ember.fireSeconds));
		p.ember.cooldownSeconds = bound(p.ember.cooldownSeconds, 0, 3600);
		if (p.glow == null) {
			p.glow = new Glow();
		}
		p.glow.radius = bound(p.glow.radius, 1, 48);
		p.glow.seconds = Math.max(1, Math.min(120, p.glow.seconds));
		p.glow.cooldownSeconds = bound(p.glow.cooldownSeconds, 0, 3600);
		if (p.echo == null) {
			p.echo = new Echo();
		}
		p.echo.sneakSeconds = bound(p.echo.sneakSeconds, 0.5, 30);
		p.echo.radius = bound(p.echo.radius, 1, 64);
		p.echo.seconds = Math.max(1, Math.min(120, p.echo.seconds));
		p.echo.cooldownSeconds = bound(p.echo.cooldownSeconds, 0, 3600);
		if (p.fox == null) {
			p.fox = new Fox();
		}
		p.fox.radius = bound(p.fox.radius, 1, 16);
		p.fox.maxItems = Math.max(1, Math.min(64, p.fox.maxItems));
		if (p.bounce == null) {
			p.bounce = new Bounce();
		}
		p.bounce.minFall = bound(p.bounce.minFall, 1, 256);
		p.bounce.hop = bound(p.bounce.hop, 0, 2);
		p.bounce.cooldownSeconds = bound(p.bounce.cooldownSeconds, 0, 3600);
		if (p.phoenix == null) {
			p.phoenix = new Phoenix();
		}
		p.phoenix.cooldownMinutes = Math.max(0, Math.min(10080, p.phoenix.cooldownMinutes));
		p.phoenix.healthRatio = bound(p.phoenix.healthRatio, 0.05, 1);
		p.phoenix.radius = bound(p.phoenix.radius, 1, 24);
		p.phoenix.damage = bound(p.phoenix.damage, 0, 100);
		p.phoenix.fireSeconds = Math.max(0, Math.min(60, p.phoenix.fireSeconds));
		p.phoenix.push = bound(p.phoenix.push, 0, 5);
		p.phoenix.fireResistanceSeconds = Math.max(0, Math.min(600, p.phoenix.fireResistanceSeconds));
		p.phoenix.regenSeconds = Math.max(0, Math.min(600, p.phoenix.regenSeconds));
		p.phoenix.regenLevel = Math.max(1, Math.min(5, p.phoenix.regenLevel));
		if (p.dragon == null) {
			p.dragon = new Dragon();
		}
		p.dragon.breathCooldownSeconds = bound(p.dragon.breathCooldownSeconds, 0, 3600);
		p.dragon.breathRange = bound(p.dragon.breathRange, 1, 32);
		p.dragon.breathAngle = bound(p.dragon.breathAngle, 5, 90);
		p.dragon.breathDamage = bound(p.dragon.breathDamage, 0, 100);
		p.dragon.breathFireSeconds = Math.max(0, Math.min(60, p.dragon.breathFireSeconds));
	}

	private static double bound(double value, double min, double max) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
	}

	/** Namespaced item id ("minecraft:" added when missing), null when invalid. */
	static @Nullable String itemId(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim().toLowerCase(Locale.ROOT);
		String id = text.contains(":") ? text : "minecraft:" + text;
		return ITEM_ID.matcher(id).matches() ? id : null;
	}

	// ------------------------------------------------------------------ lookups

	public @Nullable RuneDef rune(@Nullable String id) {
		if (id == null || runes == null) {
			return null;
		}
		for (RuneDef def : runes) {
			if (def.id.equals(id)) {
				return def;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ defaults

	/** The default runes: 16 common, 6 rare and the 2 mythic runes. */
	public static List<RuneDef> defaultRunes() {
		List<RuneDef> list = new ArrayList<>();
		// common: small stat buffs
		list.add(new RuneDef("rempart", "Rune du rempart", "ᛉ", RuneTier.COMMON, 12, "head", "chest", "legs", "feet")
				.attribute("armor", 1, "add_value"));
		list.add(new RuneDef("bastion", "Rune du bastion", "ᚦ", RuneTier.COMMON, 6, "chest", "legs")
				.attribute("armor", 2, "add_value"));
		list.add(new RuneDef("acier", "Rune d'acier", "ᛁ", RuneTier.COMMON, 8, "head", "chest", "legs", "feet")
				.attribute("armor_toughness", 1, "add_value"));
		list.add(new RuneDef("roc", "Rune du roc", "ᚢ", RuneTier.COMMON, 7, "chest", "legs")
				.attribute("knockback_resistance", 0.1, "add_value"));
		list.add(new RuneDef("vitalite", "Rune de vitalité", "ᛒ", RuneTier.COMMON, 6, "chest", "legs")
				.attribute("max_health", 2, "add_value"));
		list.add(new RuneDef("celerite", "Rune de célérité", "ᚱ", RuneTier.COMMON, 6, "feet")
				.attribute("movement_speed", 0.05, "add_multiplied_base"));
		list.add(new RuneDef("trefle", "Rune du trèfle", "ᚠ", RuneTier.COMMON, 5, "head")
				.attribute("luck", 1, "add_value"));
		list.add(new RuneDef("fureur", "Rune de fureur", "ᛏ", RuneTier.COMMON, 4, "chest")
				.attribute("attack_damage", 0.5, "add_value"));
		list.add(new RuneDef("abysses", "Rune des abysses", "ᚾ", RuneTier.COMMON, 5, "head")
				.attribute("oxygen_bonus", 1, "add_value"));
		list.add(new RuneDef("ondine", "Rune de l'ondine", "ᛚ", RuneTier.COMMON, 4, "feet")
				.attribute("water_movement_efficiency", 0.25, "add_value"));
		list.add(new RuneDef("plume", "Rune de la plume", "ᚹ", RuneTier.COMMON, 5, "feet")
				.attribute("safe_fall_distance", 2, "add_value"));
		list.add(new RuneDef("chamois", "Rune du chamois", "ᛖ", RuneTier.COMMON, 4, "legs")
				.attribute("jump_strength", 0.1, "add_multiplied_base"));
		list.add(new RuneDef("mineur", "Rune du mineur", "ᛈ", RuneTier.COMMON, 4, "head")
				.attribute("block_break_speed", 0.1, "add_multiplied_base"));
		list.add(new RuneDef("salamandre", "Rune de la salamandre", "ᚲ", RuneTier.COMMON, 4, "legs")
				.attribute("burning_time", -0.3, "add_multiplied_base"));
		list.add(new RuneDef("chat", "Rune du chat", "ᛇ", RuneTier.COMMON, 3, "legs")
				.attribute("sneaking_speed", 0.15, "add_value"));
		list.add(new RuneDef("allonge", "Rune de l'allonge", "ᚷ", RuneTier.COMMON, 3, "chest")
				.attribute("block_interaction_range", 1, "add_value"));
		// rare: small playful powers
		list.add(new RuneDef("etincelles", "Rune d'étincelles", "ᛊ", RuneTier.RARE, 10, "chest", "legs")
				.power(RunePower.SPARKS));
		list.add(new RuneDef("braise", "Rune de braise", "ᚳ", RuneTier.RARE, 10, "chest")
				.power(RunePower.EMBER));
		list.add(new RuneDef("lueur", "Rune de lueur", "ᛞ", RuneTier.RARE, 10, "head")
				.power(RunePower.GLOW));
		list.add(new RuneDef("echo", "Rune d'écho", "ᛗ", RuneTier.RARE, 8, "head", "feet")
				.power(RunePower.ECHO));
		list.add(new RuneDef("renard", "Rune du renard", "ᚻ", RuneTier.RARE, 8, "legs")
				.power(RunePower.FOX));
		list.add(new RuneDef("rebond", "Rune du rebond", "ᛃ", RuneTier.RARE, 8, "feet")
				.attribute("fall_damage_multiplier", -0.5, "add_multiplied_base")
				.attribute("safe_fall_distance", 1, "add_value")
				.power(RunePower.BOUNCE));
		// mythic: netherite chestplates only
		list.add(new RuneDef("phenix", "Rune du Phénix", "ᛟ", RuneTier.MYTHIC, 1, "chest")
				.power(RunePower.PHOENIX)
				.lore("Ce qui brûle ne meurt jamais tout à fait."));
		list.add(new RuneDef("dragon", "Rune du Dragon", "ᛥ", RuneTier.MYTHIC, 1, "chest")
				.power(RunePower.DRAGON)
				.lore("Les ailes d'un dragon oublié dorment dans ce métal."));
		return list;
	}
}
