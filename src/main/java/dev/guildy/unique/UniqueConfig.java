package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * uniques.json5 - unique weapons and tools: definitions, drop tables per source, power balance, announcements and
 * resource pack options. Free of Minecraft classes except in {@link #validate()} (unit tests use {@link #rebuild}).
 */
@Comment({
		"Guildy - armes uniques : armes et outils legendaires (enchantements de fou, pouvoirs, modeles optionnels).",
		"Ils tombent des boss des hordes (bien plus souvent pendant une nuit de sang), des quetes, des expeditions des",
		"villageois et se vendent parfois au Carrefour commercial palier III (trade.json5).",
		"Commandes admin : /guild admin unique list | give <joueur> <id>."
})
public final class UniqueConfig implements Validatable {
	/** Current file format (no migration yet). */
	public static final int CURRENT_VERSION = 1;
	/** Drop sources used by the other modules (UniqueDrops.roll). */
	public static final List<String> SOURCES = List.of("boss", "blood_boss", "quest", "legendary_quest", "expedition", "trade");
	public static final Set<String> MODEL_MODES = Set.of("auto", "on", "off");
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
	private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> SLOTS = Set.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body",
			"saddle");
	private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Butin : les armes uniques tombent des boss, des quetes, des expeditions et se vendent au Carrefour (false = plus aucune)")
	public boolean dropsEnabled = true;

	@Comment("Pouvoirs des armes uniques (foudre, double saut, abattage...). false = elles ne gardent que leurs enchantements")
	public boolean powersEnabled = true;

	@Comment({
			"Modeles et textures personnalises (pack de ressources) :",
			"\"auto\" = seulement si le mod Polymer (polymer-resource-pack) est installe : Guildy ajoute ses textures a son pack ;",
			"\"on\" = toujours (l'admin heberge guildy-resourcepack-<version>.zip : resource-pack= dans server.properties) ;",
			"\"off\" = jamais : les armes gardent l'apparence de leur objet de base (nom, description et brillance)"
	})
	public String customModels = "auto";

	@Comment("Avec Polymer : pack de ressources obligatoire (sinon un joueur qui le refuse verrait des textures manquantes)")
	public boolean polymerRequired = true;

	@Comment("Annonce a tout le serveur quand un joueur obtient une arme unique")
	public Announce announce = new Announce();

	@Comment({
			"Tables de butin par source : chance (0 a 1) qu'une arme unique tombe, puis tirage pondere dans items {id: poids}.",
			"Sources : boss (boss des hordes), blood_boss (boss pendant une nuit de sang), quest (recompense de quete, rare),",
			"legendary_quest (quete legendaire), expedition (expeditions des villageois), trade (Carrefour commercial : l'offre",
			"\"arme unique\" de trade.json5 tire ici l'arme proposee). announce : annoncer l'obtention (raretes de \"announce\")."
	})
	public Map<String, DropTable> drops = defaultDrops();

	@Comment("Reglages des pouvoirs (recharges en secondes, degats en points de vie : 2 = 1 coeur, distances en blocs)")
	public Powers powers = new Powers();

	@Comment({
			"Armes et outils uniques : id (minuscules, chiffres, _), item (objet vanilla de base), name, rarity (RARE, EPIC,",
			"LEGENDARY, MYTHIC), lore (histoire, codes couleur &), enchantments {id: niveau} (vanilla ou guildy:foudroiement,",
			"guildy:vampirisme, guildy:givre, guildy:brasier, guildy:echo, guildy:seisme, guildy:apesanteur, guildy:salve,",
			"guildy:moisson, guildy:marque ; niveaux au-dela du maximum vanilla permis), attributes [{attribute, amount, operation",
			"(add_value, add_multiplied_base, add_multiplied_total), slot (mainhand, offhand, feet, head, armor...)}] ajoutes aux",
			"attributs de l'objet de base, glint (brillance), unbreakable (incassable), power (storm_call, frost_nova, blink_strike,",
			"tree_feller, excavate, vein_miner, double_jump, shockwave, phoenix_arrows, tide_call, soul_harvest ou vide), model",
			"(texture du pack de ressources, vide = apparence vanilla), color (couleur RGB du cuir teint, -1 = aucune).",
			"Les objets deja obtenus suivent le pouvoir et le modele de la config ; nom, description et enchantements restent",
			"ceux de leur creation."
	})
	public List<UniqueDef> items = defaultItems();

	private transient Map<String, UniqueDef> byId = new LinkedHashMap<>();
	private transient Set<UniqueRarity> announced = EnumSet.of(UniqueRarity.LEGENDARY, UniqueRarity.MYTHIC);

	// ================================================================== sections

	@Json5.Section
	public static final class Announce {
		@Comment("Raretes annoncees a tout le serveur (RARE, EPIC, LEGENDARY, MYTHIC)")
		public List<String> rarities = new ArrayList<>(List.of("LEGENDARY", "MYTHIC"));
		@Comment("Son joue a tous les joueurs lors de l'annonce")
		public boolean sound = true;
	}

	/** One drop source: chance of a unique, then a weighted pick among the items. */
	public static final class DropTable {
		public double chance;
		public boolean announce = true;
		public Map<String, Double> items = new LinkedHashMap<>();

		public DropTable() {
		}

		DropTable(double chance, boolean announce) {
			this.chance = chance;
			this.announce = announce;
		}

		DropTable add(String id, double weight) {
			items.put(id, weight);
			return this;
		}
	}

	@Json5.Section
	public static final class Powers {
		@Comment("Foudrelame (storm_call) - clic droit : la foudre frappe les monstres autour du point vise")
		public StormCall stormCall = new StormCall();
		@Comment("Croc-de-Givre (frost_nova) - clic droit : onde de givre autour du joueur")
		public FrostNova frostNova = new FrostNova();
		@Comment("Murmure du Neant (blink_strike) - clic droit : se teleporte derriere le monstre vise et le frappe")
		public BlinkStrike blinkStrike = new BlinkStrike();
		@Comment("Hache de l'Ancien Forestier (tree_feller) - abat tout l'arbre naturel (accroupi : une seule buche)")
		public TreeFeller treeFeller = new TreeFeller();
		@Comment("Pioche du Titan (excavate) - creuse en 3x3 dans la roche naturelle (accroupi : un seul bloc)")
		public Excavate excavate = new Excavate();
		@Comment("Pic du Filon (vein_miner) - extrait tout le filon de minerai (accroupi : un seul bloc)")
		public VeinMiner veinMiner = new VeinMiner();
		@Comment("Bottes de Zephyr (double_jump) - double saut (appuyer deux fois sur saut), pas de degats de chute")
		public DoubleJump doubleJump = new DoubleJump();
		@Comment("Marteau du Seisme (shockwave) - une attaque ecrasante (en tombant) frappe aussi les monstres autour")
		public Shockwave shockwave = new Shockwave();
		@Comment("Arc du Phenix (phoenix_arrows) - la fleche embrase les monstres autour de sa cible")
		public PhoenixArrows phoenixArrows = new PhoenixArrows();
		@Comment("Trident du Leviathan (tide_call) - lance, il appelle la foudre qui rebondit sur les monstres proches")
		public TideCall tideCall = new TideCall();
		@Comment("Faux des Ames (soul_harvest) - chaque monstre tue soigne le porteur")
		public SoulHarvest soulHarvest = new SoulHarvest();
	}

	@Json5.Section
	public static final class StormCall {
		public double cooldownSeconds = 12;
		@Comment("Portee de visee, rayon autour du point vise, nombre maximum de monstres foudroyes, degats par eclair")
		public double range = 32;
		public double radius = 4.5;
		public int maxTargets = 4;
		public double damage = 10;
	}

	@Json5.Section
	public static final class FrostNova {
		public double cooldownSeconds = 18;
		@Comment("Rayon, degats de froid, duree (secondes) et niveau de la lenteur")
		public double radius = 6;
		public double damage = 4;
		public int slowSeconds = 6;
		public int slowLevel = 3;
	}

	@Json5.Section
	public static final class BlinkStrike {
		public double cooldownSeconds = 8;
		@Comment("Portee de visee et degats du coup porte apres la teleportation")
		public double range = 20;
		public double damage = 10;
	}

	@Json5.Section
	public static final class TreeFeller {
		@Comment("Buches maximum par arbre, buches coupees par tick, feuilles naturelles minimum autour (sinon : construction)")
		public int maxLogs = 128;
		public int logsPerTick = 12;
		public int minLeaves = 4;
		@Comment("Accroupi : pouvoir desactive")
		public boolean sneakDisables = true;
	}

	@Json5.Section
	public static final class Excavate {
		@Comment("Accroupi : pouvoir desactive")
		public boolean sneakDisables = true;
		@Comment("Blocs creuses en 3x3 (ids ou #tags). Jamais chez une autre guilde, jamais le coeur.")
		public List<String> blocks = new ArrayList<>(List.of("#minecraft:base_stone_overworld", "#minecraft:base_stone_nether",
				"#minecraft:ores", "minecraft:calcite", "minecraft:dripstone_block", "minecraft:smooth_basalt", "minecraft:end_stone",
				"minecraft:magma_block"));
	}

	@Json5.Section
	public static final class VeinMiner {
		@Comment("Blocs maximum par filon")
		public int maxBlocks = 32;
		@Comment("Accroupi : pouvoir desactive")
		public boolean sneakDisables = true;
		@Comment("Minerais concernes (ids ou #tags). Jamais chez une autre guilde.")
		public List<String> blocks = new ArrayList<>(List.of("#minecraft:ores", "minecraft:ancient_debris"));
	}

	@Json5.Section
	public static final class DoubleJump {
		@Comment("Delai minimum entre deux doubles sauts (secondes), elan vertical et vers l'avant")
		public double cooldownSeconds = 0.5;
		public double upward = 0.75;
		public double forward = 0.45;
	}

	@Json5.Section
	public static final class Shockwave {
		public double cooldownSeconds = 4;
		@Comment("Rayon autour de la cible, degats aux autres monstres, elan vers le haut")
		public double radius = 5;
		public double damage = 8;
		public double lift = 0.45;
	}

	@Json5.Section
	public static final class PhoenixArrows {
		public double cooldownSeconds = 2;
		@Comment("Rayon autour de la cible, degats de feu, duree d'embrasement (secondes)")
		public double radius = 3.5;
		public double damage = 4;
		public int fireSeconds = 5;
	}

	@Json5.Section
	public static final class TideCall {
		public double cooldownSeconds = 6;
		@Comment("Degats de l'eclair sur la cible, nombre de rebonds, rayon et degats des rebonds")
		public double damage = 8;
		public int chainTargets = 3;
		public double chainRadius = 8;
		public double chainDamage = 5;
	}

	@Json5.Section
	public static final class SoulHarvest {
		@Comment("Points de vie rendus par monstre tue, duree (secondes) de la Vitesse I accordee")
		public double heal = 3;
		public int speedSeconds = 3;
	}

	/** A unique item definition. */
	public static final class UniqueDef {
		public String id = "";
		public String item = "minecraft:iron_sword";
		public String name = "";
		public String rarity = "RARE";
		public List<String> lore = new ArrayList<>();
		public Map<String, Integer> enchantments = new LinkedHashMap<>();
		public List<AttributeEntry> attributes = new ArrayList<>();
		public boolean glint = true;
		public boolean unbreakable;
		public String power = "";
		public String model = "";
		public int color = -1;

		private transient UniqueRarity parsedRarity = UniqueRarity.RARE;
		private transient @Nullable UniquePower parsedPower;

		public UniqueRarity rarity() {
			return parsedRarity == null ? UniqueRarity.RARE : parsedRarity;
		}

		public @Nullable UniquePower power() {
			return parsedPower;
		}

		UniqueDef ench(String enchantment, int level) {
			enchantments.put(enchantment, level);
			return this;
		}

		UniqueDef attr(String attribute, double amount, String operation, String slot) {
			attributes.add(new AttributeEntry(attribute, amount, operation, slot));
			return this;
		}

		UniqueDef story(String... lines) {
			lore.addAll(List.of(lines));
			return this;
		}
	}

	/** An attribute modifier added to the item's own modifiers. */
	public static final class AttributeEntry {
		public String attribute = "minecraft:attack_damage";
		public double amount;
		public String operation = "add_value";
		public String slot = "mainhand";

		public AttributeEntry() {
		}

		AttributeEntry(String attribute, double amount, String operation, String slot) {
			this.attribute = attribute;
			this.amount = amount;
			this.operation = operation;
			this.slot = slot;
		}
	}

	// ================================================================== queries

	/** Valid definition (after {@link #rebuild}), or null. */
	public @Nullable UniqueDef item(@Nullable String id) {
		return id == null ? null : byId.get(id.trim().toLowerCase(Locale.ROOT));
	}

	/** Valid definitions, in file order. */
	public Collection<UniqueDef> usable() {
		return byId.values();
	}

	/** Drop table of a source, or null. */
	public @Nullable DropTable table(@Nullable String source) {
		return source == null || drops == null ? null : drops.get(source.trim().toLowerCase(Locale.ROOT));
	}

	public boolean announces(UniqueRarity rarity) {
		return announced.contains(rarity);
	}

	/** "auto" / "on" / "off". */
	public String modelMode() {
		return customModels == null ? "auto" : customModels;
	}

	// ================================================================== validation

	@Override
	public void validate() {
		for (String warning : rebuild(id -> ItemUtil.item(id) != null)) {
			Guildy.LOGGER.warn("[uniques.json5] {}", warning);
		}
	}

	/** Clamps the settings, validates the definitions and drop tables (pure). @return the problems found */
	public List<String> rebuild(Predicate<String> itemExists) {
		List<String> warnings = new ArrayList<>();
		configVersion = CURRENT_VERSION;
		customModels = customModels == null ? "auto" : customModels.trim().toLowerCase(Locale.ROOT);
		if (!MODEL_MODES.contains(customModels)) {
			warnings.add("customModels '" + customModels + "' unknown (auto, on, off): auto used");
			customModels = "auto";
		}
		rebuildAnnounce(warnings);
		rebuildItems(itemExists, warnings);
		rebuildDrops(warnings);
		rebuildPowers();
		return warnings;
	}

	private void rebuildAnnounce(List<String> warnings) {
		if (announce == null) {
			announce = new Announce();
		}
		if (announce.rarities == null) {
			announce.rarities = new ArrayList<>();
		}
		Set<UniqueRarity> parsed = EnumSet.noneOf(UniqueRarity.class);
		for (String value : announce.rarities) {
			UniqueRarity rarity = UniqueRarity.parse(value);
			if (rarity == null) {
				warnings.add("announce: unknown rarity '" + value + "'");
			} else {
				parsed.add(rarity);
			}
		}
		announced = parsed;
	}

	private void rebuildItems(Predicate<String> itemExists, List<String> warnings) {
		if (items == null) {
			items = defaultItems();
		}
		Map<String, UniqueDef> valid = new LinkedHashMap<>();
		for (UniqueDef def : items) {
			if (def == null) {
				continue;
			}
			def.id = def.id == null ? "" : def.id.trim().toLowerCase(Locale.ROOT);
			if (!ID.matcher(def.id).matches()) {
				warnings.add("item '" + def.id + "': invalid id (a-z, 0-9, _): ignored");
				continue;
			}
			if (valid.containsKey(def.id)) {
				warnings.add("item '" + def.id + "': duplicate id: ignored");
				continue;
			}
			def.item = normalize(def.item);
			if (!itemExists.test(def.item)) {
				warnings.add("item '" + def.id + "': unknown base item '" + def.item + "': ignored");
				continue;
			}
			if (def.name == null || def.name.isBlank()) {
				def.name = def.id;
			}
			UniqueRarity rarity = UniqueRarity.parse(def.rarity);
			if (rarity == null) {
				warnings.add("item '" + def.id + "': unknown rarity '" + def.rarity + "': RARE used");
				rarity = UniqueRarity.RARE;
			}
			def.rarity = rarity.name();
			def.parsedRarity = rarity;
			if (def.lore == null) {
				def.lore = new ArrayList<>();
			}
			def.lore.removeIf(line -> line == null);
			Map<String, Integer> enchantments = new LinkedHashMap<>();
			if (def.enchantments != null) {
				for (Map.Entry<String, Integer> entry : def.enchantments.entrySet()) {
					String id = normalize(entry.getKey());
					if (!RESOURCE.matcher(id).matches() || entry.getValue() == null) {
						warnings.add("item '" + def.id + "': invalid enchantment '" + entry.getKey() + "': ignored");
						continue;
					}
					enchantments.put(id, Math.max(1, Math.min(255, entry.getValue())));
				}
			}
			def.enchantments = enchantments;
			List<AttributeEntry> attributes = new ArrayList<>();
			if (def.attributes != null) {
				for (AttributeEntry attribute : def.attributes) {
					String problem = checkAttribute(attribute);
					if (problem != null) {
						warnings.add("item '" + def.id + "': " + problem + ": ignored");
					} else {
						attributes.add(attribute);
					}
				}
			}
			def.attributes = attributes;
			def.power = def.power == null ? "" : def.power.trim().toLowerCase(Locale.ROOT);
			def.parsedPower = UniquePower.byId(def.power);
			if (!def.power.isEmpty() && def.parsedPower == null) {
				warnings.add("item '" + def.id + "': unknown power '" + def.power + "' (" + String.join(", ", UniquePower.ids()) + "): none");
				def.power = "";
			}
			def.model = def.model == null ? "" : def.model.trim().toLowerCase(Locale.ROOT);
			if (!def.model.isEmpty() && !ID.matcher(def.model).matches()) {
				warnings.add("item '" + def.id + "': invalid model '" + def.model + "': vanilla look");
				def.model = "";
			}
			def.color = def.color < 0 ? -1 : def.color & 0xFFFFFF;
			valid.put(def.id, def);
		}
		byId = valid;
	}

	private static @Nullable String checkAttribute(@Nullable AttributeEntry attribute) {
		if (attribute == null) {
			return "empty attribute";
		}
		attribute.attribute = normalize(attribute.attribute);
		if (!RESOURCE.matcher(attribute.attribute).matches()) {
			return "invalid attribute '" + attribute.attribute + "'";
		}
		if (!Double.isFinite(attribute.amount)) {
			return "attribute '" + attribute.attribute + "': invalid amount";
		}
		attribute.operation = attribute.operation == null ? "" : attribute.operation.trim().toLowerCase(Locale.ROOT);
		if (!OPERATIONS.contains(attribute.operation)) {
			return "attribute '" + attribute.attribute + "': unknown operation '" + attribute.operation + "'";
		}
		attribute.slot = attribute.slot == null ? "" : attribute.slot.trim().toLowerCase(Locale.ROOT);
		if (!SLOTS.contains(attribute.slot)) {
			return "attribute '" + attribute.attribute + "': unknown slot '" + attribute.slot + "'";
		}
		return null;
	}

	private void rebuildDrops(List<String> warnings) {
		if (drops == null) {
			drops = defaultDrops();
		}
		Map<String, DropTable> fixed = new LinkedHashMap<>();
		for (Map.Entry<String, DropTable> entry : drops.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			String source = entry.getKey().trim().toLowerCase(Locale.ROOT);
			DropTable table = entry.getValue();
			table.chance = clamp(table.chance, 0, 1);
			Map<String, Double> weights = new LinkedHashMap<>();
			if (table.items != null) {
				for (Map.Entry<String, Double> weight : table.items.entrySet()) {
					String id = weight.getKey() == null ? "" : weight.getKey().trim().toLowerCase(Locale.ROOT);
					if (!byId.containsKey(id)) {
						warnings.add("drops." + source + ": unknown item '" + weight.getKey() + "': ignored");
						continue;
					}
					double w = weight.getValue() == null ? 0 : weight.getValue();
					weights.put(id, Double.isFinite(w) && w > 0 ? w : 0);
				}
			}
			table.items = weights;
			if (!SOURCES.contains(source)) {
				warnings.add("drops: source '" + source + "' is never used (" + String.join(", ", SOURCES) + ")");
			}
			fixed.put(source, table);
		}
		drops = fixed;
	}

	private void rebuildPowers() {
		if (powers == null) {
			powers = new Powers();
		}
		Powers p = powers;
		if (p.stormCall == null) {
			p.stormCall = new StormCall();
		}
		p.stormCall.cooldownSeconds = clamp(p.stormCall.cooldownSeconds, 1, 600);
		p.stormCall.range = clamp(p.stormCall.range, 4, 64);
		p.stormCall.radius = clamp(p.stormCall.radius, 1, 12);
		p.stormCall.maxTargets = (int) clamp(p.stormCall.maxTargets, 1, 12);
		p.stormCall.damage = clamp(p.stormCall.damage, 0, 200);
		if (p.frostNova == null) {
			p.frostNova = new FrostNova();
		}
		p.frostNova.cooldownSeconds = clamp(p.frostNova.cooldownSeconds, 1, 600);
		p.frostNova.radius = clamp(p.frostNova.radius, 1, 16);
		p.frostNova.damage = clamp(p.frostNova.damage, 0, 200);
		p.frostNova.slowSeconds = (int) clamp(p.frostNova.slowSeconds, 1, 60);
		p.frostNova.slowLevel = (int) clamp(p.frostNova.slowLevel, 1, 6);
		if (p.blinkStrike == null) {
			p.blinkStrike = new BlinkStrike();
		}
		p.blinkStrike.cooldownSeconds = clamp(p.blinkStrike.cooldownSeconds, 1, 600);
		p.blinkStrike.range = clamp(p.blinkStrike.range, 3, 48);
		p.blinkStrike.damage = clamp(p.blinkStrike.damage, 0, 200);
		if (p.treeFeller == null) {
			p.treeFeller = new TreeFeller();
		}
		p.treeFeller.maxLogs = (int) clamp(p.treeFeller.maxLogs, 1, 512);
		p.treeFeller.logsPerTick = (int) clamp(p.treeFeller.logsPerTick, 1, 64);
		p.treeFeller.minLeaves = (int) clamp(p.treeFeller.minLeaves, 0, 64);
		if (p.excavate == null) {
			p.excavate = new Excavate();
		}
		p.excavate.blocks = cleanIds(p.excavate.blocks);
		if (p.veinMiner == null) {
			p.veinMiner = new VeinMiner();
		}
		p.veinMiner.maxBlocks = (int) clamp(p.veinMiner.maxBlocks, 1, 256);
		p.veinMiner.blocks = cleanIds(p.veinMiner.blocks);
		if (p.doubleJump == null) {
			p.doubleJump = new DoubleJump();
		}
		p.doubleJump.cooldownSeconds = clamp(p.doubleJump.cooldownSeconds, 0, 60);
		p.doubleJump.upward = clamp(p.doubleJump.upward, 0.1, 2);
		p.doubleJump.forward = clamp(p.doubleJump.forward, 0, 2);
		if (p.shockwave == null) {
			p.shockwave = new Shockwave();
		}
		p.shockwave.cooldownSeconds = clamp(p.shockwave.cooldownSeconds, 0, 600);
		p.shockwave.radius = clamp(p.shockwave.radius, 1, 12);
		p.shockwave.damage = clamp(p.shockwave.damage, 0, 200);
		p.shockwave.lift = clamp(p.shockwave.lift, 0, 2);
		if (p.phoenixArrows == null) {
			p.phoenixArrows = new PhoenixArrows();
		}
		p.phoenixArrows.cooldownSeconds = clamp(p.phoenixArrows.cooldownSeconds, 0, 600);
		p.phoenixArrows.radius = clamp(p.phoenixArrows.radius, 1, 12);
		p.phoenixArrows.damage = clamp(p.phoenixArrows.damage, 0, 200);
		p.phoenixArrows.fireSeconds = (int) clamp(p.phoenixArrows.fireSeconds, 0, 60);
		if (p.tideCall == null) {
			p.tideCall = new TideCall();
		}
		p.tideCall.cooldownSeconds = clamp(p.tideCall.cooldownSeconds, 0, 600);
		p.tideCall.damage = clamp(p.tideCall.damage, 0, 200);
		p.tideCall.chainTargets = (int) clamp(p.tideCall.chainTargets, 0, 12);
		p.tideCall.chainRadius = clamp(p.tideCall.chainRadius, 1, 16);
		p.tideCall.chainDamage = clamp(p.tideCall.chainDamage, 0, 200);
		if (p.soulHarvest == null) {
			p.soulHarvest = new SoulHarvest();
		}
		p.soulHarvest.heal = clamp(p.soulHarvest.heal, 0, 40);
		p.soulHarvest.speedSeconds = (int) clamp(p.soulHarvest.speedSeconds, 0, 60);
	}

	/** Cooldown of an active power (seconds). */
	public double cooldownSeconds(UniquePower power) {
		return switch (power) {
			case STORM_CALL -> powers.stormCall.cooldownSeconds;
			case FROST_NOVA -> powers.frostNova.cooldownSeconds;
			case BLINK_STRIKE -> powers.blinkStrike.cooldownSeconds;
			case DOUBLE_JUMP -> powers.doubleJump.cooldownSeconds;
			case SHOCKWAVE -> powers.shockwave.cooldownSeconds;
			case PHOENIX_ARROWS -> powers.phoenixArrows.cooldownSeconds;
			case TIDE_CALL -> powers.tideCall.cooldownSeconds;
			default -> 0;
		};
	}

	private static List<String> cleanIds(@Nullable List<String> ids) {
		List<String> out = new ArrayList<>();
		if (ids != null) {
			for (String id : ids) {
				if (id != null && !id.isBlank()) {
					String value = id.trim().toLowerCase(Locale.ROOT);
					boolean tag = value.startsWith("#");
					String bare = tag ? value.substring(1) : value;
					out.add((tag ? "#" : "") + (bare.contains(":") ? bare : "minecraft:" + bare));
				}
			}
		}
		return out;
	}

	/** "Netherite_Sword " -> "minecraft:netherite_sword". */
	static String normalize(@Nullable String id) {
		if (id == null) {
			return "";
		}
		String value = id.trim().toLowerCase(Locale.ROOT);
		return value.isEmpty() || value.contains(":") ? value : "minecraft:" + value;
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}

	// ================================================================== defaults

	public static Map<String, DropTable> defaultDrops() {
		Map<String, DropTable> map = new LinkedHashMap<>();
		map.put("boss", new DropTable(0.04, true)
				.add("coutelas_du_contrebandier", 10).add("bottes_du_coursier", 10).add("pic_du_filon", 8)
				.add("croc_de_givre", 6).add("hache_sylvestre", 6).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 4)
				.add("foudrelame", 2).add("pioche_du_titan", 2).add("bottes_de_zephyr", 2).add("arc_du_phenix", 2)
				.add("marteau_du_seisme", 2));
		map.put("blood_boss", new DropTable(0.2, true)
				.add("croc_de_givre", 6).add("hache_sylvestre", 5).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 5)
				.add("foudrelame", 5).add("pioche_du_titan", 4).add("bottes_de_zephyr", 4).add("arc_du_phenix", 4)
				.add("marteau_du_seisme", 4).add("trident_du_leviathan", 2).add("faux_des_ames", 2));
		map.put("quest", new DropTable(0.01, true)
				.add("coutelas_du_contrebandier", 10).add("bottes_du_coursier", 10).add("pic_du_filon", 8)
				.add("croc_de_givre", 4).add("hache_sylvestre", 4).add("arbalete_du_traqueur", 3));
		map.put("legendary_quest", new DropTable(0.35, true)
				.add("croc_de_givre", 5).add("hache_sylvestre", 5).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 5)
				.add("foudrelame", 4).add("pioche_du_titan", 4).add("bottes_de_zephyr", 4).add("arc_du_phenix", 4)
				.add("marteau_du_seisme", 4).add("trident_du_leviathan", 1).add("faux_des_ames", 1));
		map.put("expedition", new DropTable(0.005, true)
				.add("coutelas_du_contrebandier", 10).add("bottes_du_coursier", 10).add("pic_du_filon", 8)
				.add("hache_sylvestre", 4).add("croc_de_givre", 3).add("arbalete_du_traqueur", 3).add("bottes_de_zephyr", 1));
		map.put("trade", new DropTable(1.0, false)
				.add("croc_de_givre", 6).add("hache_sylvestre", 6).add("arbalete_du_traqueur", 6).add("murmure_du_neant", 5)
				.add("pic_du_filon", 5).add("foudrelame", 3).add("pioche_du_titan", 3).add("bottes_de_zephyr", 3)
				.add("arc_du_phenix", 3).add("marteau_du_seisme", 3));
		return map;
	}

	public static List<UniqueDef> defaultItems() {
		List<UniqueDef> list = new ArrayList<>();
		list.add(def("foudrelame", "netherite_sword", "Foudrelame", UniqueRarity.LEGENDARY, UniquePower.STORM_CALL, true)
				.story("Forgée sous l'orage au sommet du monde,", "elle gronde encore quand le ciel s'assombrit.")
				.ench("minecraft:sharpness", 6).ench("guildy:foudroiement", 3).ench("minecraft:looting", 3).ench("minecraft:unbreaking", 3)
				.attr("minecraft:attack_damage", 2, "add_value", "mainhand"));
		list.add(def("croc_de_givre", "diamond_sword", "Croc-de-Givre", UniqueRarity.EPIC, UniquePower.FROST_NOVA, false)
				.story("Taillée dans la glace d'un glacier", "qui n'a jamais connu le soleil.")
				.ench("minecraft:sharpness", 5).ench("guildy:givre", 2).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1)
				.attr("minecraft:attack_speed", 0.2, "add_value", "mainhand"));
		list.add(def("murmure_du_neant", "netherite_sword", "Murmure du Néant", UniqueRarity.LEGENDARY, UniquePower.BLINK_STRIKE, true)
				.story("Un poignard que nul n'entend venir.", "Celui qui le tient marche entre les ombres.")
				.ench("minecraft:sharpness", 5).ench("guildy:echo", 3).ench("minecraft:looting", 2)
				.attr("minecraft:attack_speed", 0.6, "add_value", "mainhand")
				.attr("minecraft:movement_speed", 0.1, "add_multiplied_base", "mainhand"));
		list.add(def("hache_sylvestre", "netherite_axe", "Hache de l'Ancien Forestier", UniqueRarity.EPIC, UniquePower.TREE_FELLER, false)
				.story("Les arbres eux-mêmes s'inclinent devant elle.", "Un coup, et la forêt cède.")
				.ench("minecraft:efficiency", 5).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1));
		list.add(def("pioche_du_titan", "netherite_pickaxe", "Pioche du Titan", UniqueRarity.LEGENDARY, UniquePower.EXCAVATE, false)
				.story("Forgée pour creuser le cœur des montagnes,", "elle ouvre des galeries entières d'un seul coup.")
				.ench("minecraft:efficiency", 6).ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:block_interaction_range", 1, "add_value", "mainhand"));
		list.add(def("pic_du_filon", "diamond_pickaxe", "Pic du Filon", UniqueRarity.RARE, UniquePower.VEIN_MINER, false)
				.story("Le vieux mineur jurait qu'il sentait le minerai.", "Il avait raison.")
				.ench("minecraft:efficiency", 4).ench("minecraft:fortune", 2).ench("minecraft:unbreaking", 3));
		list.add(def("bottes_de_zephyr", "netherite_boots", "Bottes de Zéphyr", UniqueRarity.LEGENDARY, UniquePower.DOUBLE_JUMP, true)
				.story("Cousues avec le vent du nord :", "qui les porte ne touche plus vraiment le sol.")
				.ench("minecraft:protection", 4).ench("minecraft:feather_falling", 4).ench("guildy:apesanteur", 1)
				.ench("minecraft:depth_strider", 3)
				.attr("minecraft:movement_speed", 0.1, "add_multiplied_base", "feet"));
		list.add(def("bottes_du_coursier", "leather_boots", "Bottes du Coursier", UniqueRarity.RARE, null, true)
				.story("Elles ont porté les messages de cent guildes", "sans jamais user leur cuir.")
				.ench("minecraft:feather_falling", 3).ench("minecraft:soul_speed", 2)
				.attr("minecraft:movement_speed", 0.2, "add_multiplied_base", "feet")
				.attr("minecraft:step_height", 0.5, "add_value", "feet"));
		list.getLast().color = 0x3A7D44;
		list.add(def("marteau_du_seisme", "mace", "Marteau du Séisme", UniqueRarity.LEGENDARY, UniquePower.SHOCKWAVE, false)
				.story("Quand il frappe le sol,", "la terre se souvient.")
				.ench("minecraft:density", 5).ench("minecraft:breach", 2).ench("guildy:seisme", 2).ench("minecraft:unbreaking", 3)
				.ench("minecraft:mending", 1));
		list.add(def("arc_du_phenix", "bow", "Arc du Phénix", UniqueRarity.LEGENDARY, UniquePower.PHOENIX_ARROWS, true)
				.story("Ses flèches renaissent en flammes", "là où elles frappent.")
				.ench("minecraft:power", 5).ench("minecraft:flame", 1).ench("minecraft:infinity", 1).ench("guildy:brasier", 2));
		list.add(def("arbalete_du_traqueur", "crossbow", "Arbalète du Traqueur", UniqueRarity.EPIC, null, false)
				.story("Ce qu'elle marque", "ne lui échappe jamais.")
				.ench("minecraft:quick_charge", 3).ench("minecraft:multishot", 1).ench("minecraft:piercing", 4)
				.ench("guildy:marque", 2).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1));
		list.getLast().model = "";
		list.add(def("trident_du_leviathan", "trident", "Trident du Léviathan", UniqueRarity.MYTHIC, UniquePower.TIDE_CALL, true)
				.story("Arraché aux abysses par un marin oublié,", "il commande encore aux tempêtes.")
				// No vanilla Channeling: its storm bolts are real ones (fire). Its lightning is the power's visual one.
				.ench("minecraft:impaling", 5).ench("minecraft:loyalty", 3).ench("guildy:foudroiement", 3).ench("guildy:echo", 2)
				.attr("minecraft:attack_damage", 3, "add_value", "mainhand"));
		list.add(def("faux_des_ames", "netherite_hoe", "Faux des Âmes", UniqueRarity.MYTHIC, UniquePower.SOUL_HARVEST, true)
				.story("Chaque âme fauchée nourrit son porteur.", "Nul ne sait qui l'a forgée, ni s'il vit encore.")
				.ench("minecraft:sharpness", 5).ench("guildy:vampirisme", 3).ench("guildy:moisson", 3).ench("minecraft:looting", 3)
				.attr("minecraft:attack_damage", 8, "add_value", "mainhand")
				.attr("minecraft:attack_speed", -2.6, "add_value", "mainhand")
				.attr("minecraft:entity_interaction_range", 1, "add_value", "mainhand"));
		list.add(def("coutelas_du_contrebandier", "iron_sword", "Coutelas du Contrebandier", UniqueRarity.RARE, null, false)
				.story("Trouvé dans la cale d'un navire échoué,", "il sent encore le sel et l'or volé.")
				.ench("minecraft:sharpness", 3).ench("minecraft:looting", 3).ench("guildy:echo", 1).ench("minecraft:unbreaking", 3)
				.attr("minecraft:attack_speed", 0.4, "add_value", "mainhand")
				.attr("minecraft:luck", 1, "add_value", "mainhand"));
		return list;
	}

	private static UniqueDef def(String id, String item, String name, UniqueRarity rarity, @Nullable UniquePower power, boolean unbreakable) {
		UniqueDef def = new UniqueDef();
		def.id = id;
		def.item = "minecraft:" + item;
		def.name = name;
		def.rarity = rarity.name();
		def.power = power == null ? "" : power.id;
		def.unbreakable = unbreakable;
		def.model = id;
		return def;
	}
}
