package dev.guildy.scroll;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import dev.guildy.unique.UniqueRarity;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * scrolls.json5 - single-use spell scrolls: definitions (spell + parameters, channel time, cooldown group), drop tables per
 * source, merchant prices, limits of the effects. Free of Minecraft classes except in {@link #validate()} (unit tests use
 * {@link #rebuild}).
 */
@Comment({
		"Guildy - parchemins de sort : objets a usage unique qui lancent un sort tres puissant (clic droit maintenu).",
		"Ils tombent des boss des ruines (surtout), des boss des hordes, des quetes, des expeditions des villageois et se",
		"vendent parfois au Carrefour commercial. Les sorts ne blessent que les monstres et ne modifient aucun bloc.",
		"Le grimoire (section grimoire) relie jusqu'a 4 parchemins et relance leurs sorts contre de la nourriture ; le grimoire",
		"maudit (section cursedGrimoire) fait de meme avec des sorts bien plus puissants, contre plus de faim et un peu de sang.",
		"Commandes admin : /guild admin scroll list | give <joueur> <id> [nombre] | grimoire <joueur> [cursed] [ids]."
})
public final class ScrollConfig implements Validatable {
	/**
	 * Current file format. 2: the grimoire's channel follows each spell ({@code grimoire.channelFactor}), its minimum goes
	 * from 0.4 s to 1 s when still at the old default. 3 (Guildy 1.10): stronger damage spells (spell damage ignores armor
	 * and the default damage is raised): a scroll whose damage is still the old default of its spell gets the new one.
	 * 4 (Guildy 1.12): six new spell scrolls ({@link #NEW_SCROLLS}) added once to the definitions, to the drop tables and to
	 * the cursed grimoire's per-spell settings, only where absent. 5 (Guildy 1.13): the rally calls every online member by
	 * default ({@link #migrateRally}: range 512 -> 0 = unlimited, crossDimension 0 -> 1, where still at the old defaults);
	 * the per-cast limits of the damage spells ({@code limits.repeatHitDecay}, {@code limits.repeatHitFloor},
	 * {@code limits.bossCastCap}, {@code cursedGrimoire.bossCastCap}) appear with their defaults. 6 (Guildy 1.14): the Grand
	 * spectacle fireworks scroll ({@link #SHOW_SCROLLS}) added once to the definitions and to the drop tables, only where
	 * absent; {@code limits.maxFireworks} appears with its default.
	 */
	public static final int CURRENT_VERSION = 6;
	/** Ralliement before format 5: range (blocks) and crossDimension defaults. */
	static final double OLD_RALLY_RANGE = 512;
	static final double OLD_RALLY_CROSS = 0;
	/** The scrolls of format 4, added once to older files (a definition or an entry removed afterwards stays removed). */
	public static final List<String> NEW_SCROLLS = List.of("banniere_de_guerre", "fleau", "permutation", "ralliement", "chaines_spectrales",
			"hate_du_vent");
	/** The scroll of format 6 (the fireworks show), added once to older files like the scrolls of format 4. */
	public static final List<String> SHOW_SCROLLS = List.of("grand_spectacle");
	/** Channel of the grimoire before format 2 (too short to see the circle form). */
	static final double OLD_GRIMOIRE_CHANNEL = 0.4;
	/** Default damage (health points) of the damage spells before format 3. */
	public static final Map<Spell, Double> OLD_DAMAGE = oldDamage();
	/** Drop sources used by the other modules (ScrollDrops.roll). */
	public static final List<String> SOURCES = List.of("ruin_boss", "nether_ruin_boss", "boss", "blood_boss", "legendary_quest", "quest",
			"expedition", "trade");
	/** Vanilla use animations (ItemUseAnimation names). */
	public static final Set<String> ANIMATIONS = Set.of("none", "eat", "drink", "block", "bow", "trident", "crossbow", "spyglass", "toot_horn",
			"brush", "bundle", "spear");
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
	private static final Pattern GROUP = Pattern.compile("[a-z0-9_]{1,32}");
	private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Parchemins actifs (false = ils ne lancent plus rien, ils ne sont pas consommes)")
	public boolean enabled = true;

	@Comment("Butin : les parchemins tombent des boss, des quetes, des expeditions et se vendent au Carrefour (false = plus aucun)")
	public boolean dropsEnabled = true;

	@Comment("Incantation (clic droit maintenu)")
	public Casting casting = new Casting();

	@Comment("Limites des effets en cours (performances) et de ce qu'un seul sort peut faire a une meme cible (boss surtout)")
	public Limits limits = new Limits();

	@Comment("Vente au Carrefour commercial (table \"trade\" : chance qu'un marchand du Carrefour propose un parchemin, un seul achat)")
	public Trade trade = new Trade();

	@Comment({
			"Grimoire : un livre qui relie jusqu'a 4 parchemins (clic droit d'un parchemin sur le grimoire dans l'inventaire :",
			"le parchemin est consomme et devient une page). Clic droit = lancer le sort de la page choisie (meme visee, meme",
			"animation que le parchemin), accroupi + clic droit = page suivante, accroupi + touche d'echange de main (F) ou",
			"/guild grimoire = gerer les pages. Rien n'est consomme : chaque sort coute de la nourriture."
	})
	public Grimoire grimoire = new Grimoire();

	@Comment({
			"Grimoire maudit : memes regles que le grimoire (4 pages reliees depuis des parchemins, clic droit = lancer, accroupi +",
			"clic droit = page suivante, accroupi + F = les pages ; incantation de la section grimoire) mais ses sorts sont bien",
			"plus puissants : plus de degats, de duree, de portee, de cibles, de meteores... Prix : plus de faim, un peu de sang",
			"(points de vie, jamais en dessous de minHealth : sinon le sort est refuse) et une recharge plus longue. Creatif : gratuit.",
			"Il ne tombe que du gardien MAUDIT des ruines de ruinDrops (l'Archimage dechu de la Tour du Mage par defaut)."
	})
	public CursedGrimoire cursedGrimoire = new CursedGrimoire();

	@Comment({
			"Tables de butin par source : chance (0 a 1) qu'un parchemin tombe, min/max (nombre de parchemins), puis tirage",
			"pondere dans items {id: poids}. Sources : ruin_boss et nether_ruin_boss (boss des ruines : les meilleurs",
			"parchemins), boss (boss des hordes), blood_boss (boss d'une nuit de sang), legendary_quest (quete legendaire),",
			"quest (recompense de quete, rare), expedition (expeditions des villageois), trade (marchand du Carrefour commercial)."
	})
	public Map<String, DropTable> drops = defaultDrops();

	@Comment({
			"Parchemins : id (minuscules, chiffres, _), name, rarity (RARE, EPIC, LEGENDARY, MYTHIC), lore (histoire, codes &),",
			"spell (meteor_shower, chain_storm, blizzard, solar_nova, singularity, aegis, recall, guardians, blessing, revelation,",
			"earthquake, arcane_ray, war_banner, scourge, swap, rally, spectral_chains, tailwind, fireworks), params (reglages du sort ;",
			"degats et soins en points de vie : 2 = 1 coeur ; pourcentages : 25 = +25 % ; les parametres",
			"absents prennent leur valeur par defaut, tous sont bornes), item (objet vanilla simple de base : papier, motif de",
			"banniere...), glint (brillance), maxStack (1 a 64), channelSeconds (duree de l'incantation, 0.25 a 10), animation",
			"(toot_horn, spyglass, bow, trident, brush, block...), cooldownGroup (les parchemins d'un meme groupe partagent la",
			"recharge), cooldownSeconds, model (texture du pack de ressources, vide = apparence vanilla ; suit le reglage",
			"customModels d'uniques.json5). Les parchemins deja obtenus se mettent a jour dans l'inventaire des joueurs."
	})
	public List<ScrollDef> scrolls = defaultScrolls();

	private transient Map<String, ScrollDef> byId = new LinkedHashMap<>();
	private transient Set<String> disabledDimensions = Set.of();
	/** What the last {@link #rebuild} migrated: damage of a scroll ("id.damage"), a new scroll added ("+id"). */
	private transient List<String> migrations = new ArrayList<>();

	// ================================================================== sections

	@Json5.Section
	public static final class Casting {
		@Comment("Vitesse de deplacement pendant l'incantation (0 a 1 ; manger = 0.2)")
		public double moveSpeed = 0.35;
		@Comment("Dimensions ou les parchemins ne fonctionnent pas (ex. \"minecraft:the_end\")")
		public List<String> disabledDimensions = new ArrayList<>();
		@Comment("En creatif le parchemin n'est jamais consomme ; true = la recharge s'applique quand meme")
		public boolean creativeCooldown = true;
	}

	@Json5.Section
	public static final class Limits {
		@Comment("Sorts actifs en meme temps par joueur (au-dela, le parchemin n'est pas utilise)")
		public int maxEffectsPerPlayer = 3;
		@Comment("Sorts actifs en meme temps sur tout le serveur")
		public int maxEffectsServer = 40;
		@Comment("Particules envoyees par tick, tous sorts confondus (au-dela, l'animation est allegee ; les effets restent)")
		public int particleBudget = 2500;
		@Comment("Distance (blocs) a laquelle les joueurs voient les animations (particules longue distance)")
		public int viewDistance = 96;
		@Comment("Gardiens (golems invoques) vivants en meme temps par joueur")
		public int maxGuardiansPerPlayer = 4;
		@Comment({
				"Fusees de feu d'artifice (Grand spectacle) en vol en meme temps sur tout le serveur, tous spectacles confondus (8 a 512) :",
				"au-dela, des fusees du spectacle sont sautees (chaque spectacle en a deja 40 au plus, 48 pour le grimoire maudit)"
		})
		public int maxFireworks = 120;
		@Comment({
				"Rendement decroissant (0.05 a 1) : chaque nouveau coup d'un meme sort sur la meme cible fait moins mal, ses degats",
				"sont multiplies par cette valeur une fois de plus a chaque coup (0.75 : 100 %, 75 %, 56 %, 42 %... ; 1 = desactive).",
				"Un sort continu (impulsions du blizzard et de l'egide, rayon arcanique) compte un coup toutes les 5 s de degats"
		})
		public double repeatHitDecay = 0.75;
		@Comment("Rendement decroissant : part minimale des degats qu'un coup garde, meme apres beaucoup de coups (0 a 1)")
		public double repeatHitFloor = 0.2;
		@Comment({
				"Boss (gardiens des ruines, formes maudites comprises, boss des hordes, Wither, Ender Dragon, Warden, grand gardien) :",
				"part maximale de sa vie max qu'un seul sort d'un parchemin ou d'un grimoire peut lui retirer (0.001 a 1 ; 0.04 = 4 % ;",
				"1 = sans limite). Les monstres ordinaires n'ont pas de limite. Le grimoire maudit a la sienne : cursedGrimoire.bossCastCap"
		})
		public double bossCastCap = 0.04;
	}

	@Json5.Section
	public static final class Trade {
		@Comment("Prix (or) d'un parchemin au Carrefour selon sa rarete")
		public Map<String, Integer> prices = defaultPrices();
		@Comment("Palier minimum du Carrefour pour vendre un parchemin de cette rarete")
		public Map<String, Integer> minTiers = defaultMinTiers();
	}

	@Json5.Section
	public static final class Grimoire {
		@Comment("Grimoire actif (false = il ne lance plus rien et n'est plus obtenu ; ses pages sont conservees)")
		public boolean enabled = true;
		@Comment("Objet vanilla de base (simple, sans utilisation propre ; livre enchante par defaut)")
		public String item = "minecraft:enchanted_book";
		@Comment("Brillance")
		public boolean glint = true;
		@Comment({"Duree minimale de l'incantation (secondes, 0.05 a 5) : le cercle runique a le temps de se dessiner sous le",
				"lanceur. Jamais plus longue que celle du parchemin lui-meme (0.75 a 4 s par defaut)"})
		public double channelSeconds = 1.0;
		@Comment({"Part de la duree d'incantation du parchemin (0 a 1) : un sort long reste plus long a lire dans le grimoire",
				"(ex. 0.6 : Singularite 2 s -> 1.2 s, Rappel 4 s -> 2.4 s ; les sorts courts prennent la duree minimale)"})
		public double channelFactor = 0.6;
		@Comment("Animation de l'incantation (toot_horn, spyglass, bow, trident, brush, block...)")
		public String animation = "toot_horn";
		@Comment({"Recharge entre deux sorts (secondes, 0.05 a 600) : propre au grimoire (partagee par les grimoires d'un joueur),",
				"independante des recharges des parchemins"})
		public double cooldownSeconds = 3;
		@Comment("Multiplicateur des degats, soins et durees des sorts lances avec le grimoire (0.1 a 5 ; 1 = comme le parchemin)")
		public double powerMultiplier = 1.0;
		@Comment({"Cout d'un sort en points de faim (0 a 20 ; la barre en compte 20) selon la rarete du parchemin : la saturation",
				"part d'abord, puis la faim. Si la barre de faim est plus basse que le cout, le sort ne part pas. Creatif : gratuit"})
		public Map<String, Integer> foodCost = defaultFoodCost();
		@Comment({"Chance (0 a 1) qu'une source donne un grimoire vierge a la place de son tirage de parchemins (memes sources",
				"que drops ; trade = un marchand du Carrefour le propose, a partir du palier tradeMinTier)"})
		public Map<String, Double> drops = defaultGrimoireDrops();
		@Comment("Prix (or) du grimoire au Carrefour commercial")
		public int tradePrice = 400;
		@Comment("Palier minimum du Carrefour pour proposer le grimoire")
		public int tradeMinTier = 3;
		@Comment("Texture du pack de ressources (vide = apparence vanilla ; suit le reglage customModels d'uniques.json5)")
		public String model = "grimoire";

		/** Minimum channel time in ticks (a grimoire without a readable page), computed like the vanilla consumable component. */
		public int channelTicks() {
			return ChannelRules.ticks(channelSeconds);
		}

		/** Channel (seconds) of this spell read from the grimoire (the minimum without a spell). */
		public double channelSeconds(@Nullable ScrollDef def) {
			return def == null ? channelSeconds : ChannelRules.grimoireSeconds(def.channelSeconds, channelSeconds, channelFactor);
		}

		/** Channel time in ticks of this spell read from the grimoire, like the vanilla consumable component. */
		public int channelTicks(@Nullable ScrollDef def) {
			return ChannelRules.ticks(channelSeconds(def));
		}

		/** Chance (0..1) that a roll of this source gives a grimoire. */
		public double dropChance(@Nullable String source) {
			if (!enabled || source == null || drops == null) {
				return 0;
			}
			Double chance = drops.get(source.trim().toLowerCase(Locale.ROOT));
			return chance == null ? 0 : chance;
		}

		/** Food points a spell of this rarity costs. */
		public int foodCost(UniqueRarity rarity) {
			return GrimoireRules.foodCost(foodCost, rarity.name());
		}

		/** Stable fingerprint of what the item shows / does (grimoires built from another fingerprint are refreshed). */
		public int fingerprint() {
			return java.util.Objects.hash(enabled, item, glint, channelSeconds, channelFactor, animation, cooldownSeconds, powerMultiplier, foodCost,
					model);
		}
	}

	@Json5.Section
	public static final class CursedGrimoire {
		@Comment("Grimoire maudit actif (false = il ne lance plus rien et ne tombe plus ; ses pages sont conservees)")
		public boolean enabled = true;
		@Comment("Objet vanilla de base (simple, sans utilisation propre ; livre enchante par defaut)")
		public String item = "minecraft:enchanted_book";
		@Comment("Brillance")
		public boolean glint = true;
		@Comment("Multiplicateur des degats, soins et durees des sorts (0.1 a 5 ; 1 = comme le parchemin)")
		public double powerMultiplier = 1.75;
		@Comment("Multiplicateur des portees et des rayons (0.1 a 3)")
		public double radiusMultiplier = 1.35;
		@Comment("Multiplicateur des nombres : meteores, monstres foudroyes, ondes, golems, allies rappeles (0.1 a 5, arrondi)")
		public double countMultiplier = 1.5;
		@Comment("Niveaux d'effet en plus : Lenteur, Absorption, Resistance, Regeneration (0 a 3)")
		public int levelBonus = 1;
		@Comment({
				"Reglages par sort (id du sort, voir spell des parchemins) : {parametre: multiplicateur} remplace le multiplicateur",
				"general de ce parametre (niveaux : multiplies puis arrondis ; 0.1 a 5). Bornes : les nombres et les niveaux ne",
				"depassent jamais le maximum du parametre, les portees et forces 1.5 fois ce maximum, les degats et durees 5 fois"
		})
		public Map<String, Map<String, Double>> spells = defaultCursedSpells();
		@Comment("Recharge entre deux sorts (secondes, 0.05 a 600) : propre au grimoire maudit, un peu plus longue que celle du grimoire")
		public double cooldownSeconds = 5;
		@Comment("Cout d'un sort en points de faim (0 a 20) selon la rarete du parchemin (saturation d'abord, refus si la faim est plus basse)")
		public Map<String, Integer> foodCost = defaultCursedFoodCost();
		@Comment("Prix du sang : points de vie pris a chaque sort selon la rarete (0 a 20 ; 2 = 1 coeur)")
		public Map<String, Integer> bloodCost = defaultBloodCost();
		@Comment("Vie minimale apres le prix du sang (points de vie, 1 a 19) : en dessous, le sort est refuse")
		public double minHealth = 6;
		@Comment({
				"Boss : part maximale de sa vie max qu'un seul sort du grimoire maudit peut lui retirer (0.001 a 1 ; 0.07 = 7 % ;",
				"1 = sans limite ; voir limits.bossCastCap). Contre les groupes de monstres ordinaires, aucune limite"
		})
		public double bossCastCap = 0.07;
		@Comment({
				"Chance (0 a 1) que le gardien MAUDIT d'une ruine (id de ruins.json5) laisse un grimoire maudit vierge a sa mort",
				"definitive (forme finale). Aucune autre source : ni les gardiens normaux, ni les quetes, ni le Carrefour"
		})
		public Map<String, Double> ruinDrops = defaultCursedRuinDrops();
		@Comment("Texture du pack de ressources (vide = apparence vanilla ; suit le reglage customModels d'uniques.json5)")
		public String model = "grimoire_maudit";

		/** Food points a spell of this rarity costs. */
		public int foodCost(UniqueRarity rarity) {
			return CursedGrimoireRules.points(foodCost, rarity.name());
		}

		/** Health points a spell of this rarity costs. */
		public int bloodCost(UniqueRarity rarity) {
			return CursedGrimoireRules.points(bloodCost, rarity.name());
		}

		/** Amplification of a spell cast from the cursed grimoire (general multipliers + this spell's own). */
		public SpellPower power(Spell spell) {
			Map<String, Double> own = spells == null ? null : spells.get(spell.id);
			return new SpellPower(powerMultiplier, radiusMultiplier, countMultiplier, levelBonus, own == null ? Map.of() : own, true);
		}

		/** Chance (0..1) that the defeated guardian of this ruin drops a blank cursed grimoire. */
		public double dropChance(@Nullable String ruinId, boolean cursed) {
			return CursedGrimoireRules.dropChance(ruinDrops, ruinId, cursed, enabled);
		}

		/** Stable fingerprint of what the item shows / does (cursed grimoires built from another fingerprint are refreshed). */
		public int fingerprint() {
			return java.util.Objects.hash(enabled, item, glint, powerMultiplier, radiusMultiplier, countMultiplier, levelBonus, spells,
					cooldownSeconds, foodCost, bloodCost, minHealth, model);
		}
	}

	/** One drop source: chance of a drop, number of scrolls, then a weighted pick. */
	public static final class DropTable {
		public double chance;
		public int min = 1;
		public int max = 1;
		public Map<String, Double> items = new LinkedHashMap<>();

		public DropTable() {
		}

		DropTable(double chance, int min, int max) {
			this.chance = chance;
			this.min = min;
			this.max = max;
		}

		DropTable add(String id, double weight) {
			items.put(id, weight);
			return this;
		}
	}

	/** A scroll definition. */
	public static final class ScrollDef {
		public String id = "";
		public String name = "";
		public String rarity = "RARE";
		public List<String> lore = new ArrayList<>();
		public String spell = "";
		public Map<String, Double> params = new LinkedHashMap<>();
		public String item = "minecraft:paper";
		public boolean glint = true;
		public int maxStack = 16;
		public double channelSeconds = 1.0;
		public String animation = "toot_horn";
		public String cooldownGroup = "attaque";
		public double cooldownSeconds = 20;
		public String model = "";

		private transient UniqueRarity parsedRarity = UniqueRarity.RARE;
		private transient @Nullable Spell parsedSpell;

		public UniqueRarity rarity() {
			return parsedRarity == null ? UniqueRarity.RARE : parsedRarity;
		}

		/** The spell (never null for a usable definition). */
		public Spell spell() {
			return parsedSpell == null ? Spell.BLESSING : parsedSpell;
		}

		/** Resolved parameter (default of the spell when missing). */
		public double param(String name) {
			Double value = params.get(name);
			if (value != null) {
				return value;
			}
			Spell.Param param = spell().param(name);
			return param == null ? 0 : param.defaultValue();
		}

		public int intParam(String name) {
			return (int) Math.round(param(name));
		}

		/** Channel time in ticks, computed like the vanilla consumable component does. */
		public int channelTicks() {
			return Math.max(1, (int) ((float) channelSeconds * 20.0F));
		}

		/** Stable fingerprint of what the item shows / does (stacks built from another fingerprint are refreshed). */
		public int fingerprint() {
			return java.util.Objects.hash(id, name, rarity, lore, spell, params, item, glint, maxStack, channelSeconds, animation, cooldownGroup,
					cooldownSeconds, model);
		}

		ScrollDef story(String... lines) {
			lore.addAll(List.of(lines));
			return this;
		}

		ScrollDef param(String name, double value) {
			params.put(name, value);
			return this;
		}
	}

	// ================================================================== queries

	/** Valid definition (after {@link #rebuild}), or null. */
	public @Nullable ScrollDef scroll(@Nullable String id) {
		return id == null ? null : byId.get(id.trim().toLowerCase(Locale.ROOT));
	}

	/** Valid definitions, in file order. */
	public Collection<ScrollDef> usable() {
		return byId.values();
	}

	/** Drop table of a source, or null. */
	public @Nullable DropTable table(@Nullable String source) {
		return source == null || drops == null ? null : drops.get(source.trim().toLowerCase(Locale.ROOT));
	}

	public boolean dimensionDisabled(String dimension) {
		return disabledDimensions.contains(dimension);
	}

	/** Merchant price of a rarity at a trade hub tier (0 = not sold there). */
	public int tradePrice(UniqueRarity rarity, int tier) {
		Integer minTier = trade.minTiers.get(rarity.name());
		Integer price = trade.prices.get(rarity.name());
		if (price == null || price <= 0 || tier < (minTier == null ? 1 : minTier)) {
			return 0;
		}
		return price;
	}

	// ================================================================== validation

	@Override
	public void validate() {
		for (String warning : rebuild(id -> {
			Item item = ItemUtil.item(id);
			return item != null && item.getClass() == Item.class;
		})) {
			Guildy.LOGGER.warn("[scrolls.json5] {}", warning);
		}
		if (!migrations.isEmpty()) {
			Guildy.LOGGER.info("[scrolls.json5] migrated (values left on old defaults: damage, rally range and dimensions; +new scrolls): {}",
					String.join(", ", migrations));
		}
	}

	/**
	 * Clamps the settings, validates the definitions (spell parameters included) and drop tables (pure).
	 *
	 * @param plainItem true for a known vanilla item without its own right-click behaviour (paper, banner patterns...)
	 * @return the problems found
	 */
	public List<String> rebuild(Predicate<String> plainItem) {
		List<String> warnings = new ArrayList<>();
		migrate();
		configVersion = CURRENT_VERSION;
		rebuildSections(warnings);
		rebuildGrimoire(plainItem, warnings);
		rebuildCursedGrimoire(plainItem, warnings);
		rebuildScrolls(plainItem, warnings);
		rebuildDrops(warnings);
		return warnings;
	}

	/** Older files: only values still equal to their old default change (admin edits are kept). */
	private void migrate() {
		if (configVersion < 2 && grimoire != null && Math.abs(grimoire.channelSeconds - OLD_GRIMOIRE_CHANNEL) < 1.0E-9) {
			grimoire.channelSeconds = new Grimoire().channelSeconds;
		}
		migrations = configVersion < 3 ? migrateDamage(scrolls) : new ArrayList<>();
		if (configVersion < 4) {
			migrations = new ArrayList<>(migrations);
			migrations.addAll(addNewScrolls());
		}
		if (configVersion < 5) {
			migrations = new ArrayList<>(migrations);
			migrations.addAll(migrateRally(scrolls));
		}
		if (configVersion < 6) {
			migrations = new ArrayList<>(migrations);
			migrations.addAll(addScrolls(SHOW_SCROLLS, List.of()));
		}
	}

	/**
	 * Format 5 (pure): every rally scroll (default or added by the admin) whose range is still the old default (512 blocks)
	 * calls at any distance (0), and whose crossDimension is still the old default (0) calls the other dimensions too (1);
	 * any other value is kept. @return the values changed ("id.range", "id.crossDimension")
	 */
	static List<String> migrateRally(@Nullable List<ScrollDef> scrolls) {
		List<String> changed = new ArrayList<>();
		if (scrolls == null) {
			return changed;
		}
		for (ScrollDef def : scrolls) {
			if (def == null || def.params == null || Spell.byId(def.spell) != Spell.RALLY) {
				continue;
			}
			Double range = def.params.get("range");
			if (range != null && Math.abs(range - OLD_RALLY_RANGE) < 1.0E-9) {
				def.params.put("range", Spell.RALLY.param("range").defaultValue());
				changed.add(def.id + ".range");
			}
			Double cross = def.params.get("crossDimension");
			if (cross != null && Math.abs(cross - OLD_RALLY_CROSS) < 1.0E-9) {
				def.params.put("crossDimension", Spell.RALLY.param("crossDimension").defaultValue());
				changed.add(def.id + ".crossDimension");
			}
		}
		return changed;
	}

	/**
	 * Format 4 (pure): the definitions of {@link #NEW_SCROLLS}, their weights in the existing drop tables and the cursed
	 * grimoire's settings of their spells, each only where absent (admin edits are kept). @return what was added ("+id")
	 */
	List<String> addNewScrolls() {
		return addScrolls(NEW_SCROLLS, List.of(Spell.WAR_BANNER, Spell.SCOURGE, Spell.SWAP, Spell.RALLY, Spell.SPECTRAL_CHAINS, Spell.TAILWIND));
	}

	/**
	 * Adds once (pure) the default scrolls {@code ids}: their definitions, their weights in the existing drop tables and the
	 * cursed grimoire's default settings of {@code cursedSpells}, each only where absent (admin edits are kept; a table the
	 * admin removed is not brought back). @return what was added ("+id")
	 */
	List<String> addScrolls(List<String> ids, List<Spell> cursedSpells) {
		List<String> added = new ArrayList<>();
		if (scrolls != null) {
			Set<String> present = new java.util.HashSet<>();
			for (ScrollDef def : scrolls) {
				if (def != null && def.id != null) {
					present.add(def.id.trim().toLowerCase(Locale.ROOT));
				}
			}
			for (ScrollDef def : defaultScrolls()) {
				if (ids.contains(def.id) && !present.contains(def.id)) {
					scrolls.add(def);
					added.add("+" + def.id);
				}
			}
		}
		if (drops != null) {
			Map<String, DropTable> defaults = defaultDrops();
			for (Map.Entry<String, DropTable> entry : drops.entrySet()) {
				DropTable table = entry.getValue();
				DropTable reference = entry.getKey() == null ? null : defaults.get(entry.getKey().trim().toLowerCase(Locale.ROOT));
				if (table == null || reference == null) {
					continue;
				}
				if (table.items == null) {
					table.items = new LinkedHashMap<>();
				}
				for (String id : ids) {
					Double weight = reference.items.get(id);
					if (weight != null) {
						table.items.putIfAbsent(id, weight);
					}
				}
			}
		}
		if (cursedGrimoire != null && cursedGrimoire.spells != null) {
			Map<String, Map<String, Double>> defaults = defaultCursedSpells();
			for (Spell spell : cursedSpells) {
				Map<String, Double> own = defaults.get(spell.id);
				if (own != null && !cursedGrimoire.spells.containsKey(spell.id)) {
					cursedGrimoire.spells.put(spell.id, new LinkedHashMap<>(own));
				}
			}
		}
		return added;
	}

	/**
	 * Format 3 (pure): the damage of every scroll (default or added by the admin) still equal to the old default of its spell
	 * becomes the new default; any other value is kept. @return the scrolls changed ("id.damage")
	 */
	static List<String> migrateDamage(@Nullable List<ScrollDef> scrolls) {
		List<String> changed = new ArrayList<>();
		if (scrolls == null) {
			return changed;
		}
		for (ScrollDef def : scrolls) {
			Spell spell = def == null ? null : Spell.byId(def.spell);
			Double old = spell == null ? null : OLD_DAMAGE.get(spell);
			if (old == null || def.params == null) {
				continue;
			}
			Double value = def.params.get("damage");
			Spell.Param param = spell.param("damage");
			if (value != null && param != null && Math.abs(value - old) < 1.0E-9) {
				def.params.put("damage", param.defaultValue());
				changed.add(def.id + ".damage");
			}
		}
		return changed;
	}

	/** Values migrated by the last {@link #rebuild} (logged, tests). */
	public List<String> migrations() {
		return migrations == null ? List.of() : List.copyOf(migrations);
	}

	private static Map<Spell, Double> oldDamage() {
		Map<Spell, Double> map = new EnumMap<>(Spell.class);
		map.put(Spell.METEOR_SHOWER, 12.0);
		map.put(Spell.CHAIN_STORM, 14.0);
		map.put(Spell.BLIZZARD, 4.0);
		map.put(Spell.SOLAR_NOVA, 20.0);
		map.put(Spell.SINGULARITY, 26.0);
		map.put(Spell.AEGIS, 2.0);
		map.put(Spell.EARTHQUAKE, 10.0);
		map.put(Spell.ARCANE_RAY, 5.0);
		return Collections.unmodifiableMap(map);
	}

	private void rebuildGrimoire(Predicate<String> plainItem, List<String> warnings) {
		if (grimoire == null) {
			grimoire = new Grimoire();
		}
		Grimoire g = grimoire;
		g.item = normalize(g.item);
		if (!plainItem.test(g.item)) {
			warnings.add("grimoire: base item '" + g.item + "' unknown or with its own right-click use: minecraft:enchanted_book used");
			g.item = "minecraft:enchanted_book";
		}
		g.channelSeconds = clamp(g.channelSeconds, 0.05, 5);
		g.channelFactor = clamp(g.channelFactor, 0, 1);
		g.animation = g.animation == null ? "" : g.animation.trim().toLowerCase(Locale.ROOT);
		if (!ANIMATIONS.contains(g.animation)) {
			warnings.add("grimoire: unknown animation '" + g.animation + "': toot_horn used");
			g.animation = "toot_horn";
		}
		g.cooldownSeconds = clamp(g.cooldownSeconds, 0.05, 600);
		g.powerMultiplier = clamp(g.powerMultiplier, 0.1, 5);
		g.foodCost = rarityMap(g.foodCost, defaultFoodCost(), 0, 20, "grimoire.foodCost", warnings);
		Map<String, Double> chances = new LinkedHashMap<>();
		if (g.drops != null) {
			for (Map.Entry<String, Double> entry : g.drops.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				String source = entry.getKey().trim().toLowerCase(Locale.ROOT);
				if (!SOURCES.contains(source)) {
					warnings.add("grimoire.drops: source '" + source + "' is never used (" + String.join(", ", SOURCES) + ")");
				}
				chances.put(source, entry.getValue() == null ? 0 : clamp(entry.getValue(), 0, 1));
			}
		}
		g.drops = chances;
		g.tradePrice = (int) clamp(g.tradePrice, 1, 100000);
		g.tradeMinTier = (int) clamp(g.tradeMinTier, 1, 9);
		g.model = g.model == null ? "" : g.model.trim().toLowerCase(Locale.ROOT);
		if (!g.model.isEmpty() && !ID.matcher(g.model).matches()) {
			warnings.add("grimoire: invalid model '" + g.model + "': vanilla look");
			g.model = "";
		}
	}

	private void rebuildCursedGrimoire(Predicate<String> plainItem, List<String> warnings) {
		if (cursedGrimoire == null) {
			cursedGrimoire = new CursedGrimoire();
		}
		CursedGrimoire c = cursedGrimoire;
		c.item = normalize(c.item);
		if (!plainItem.test(c.item)) {
			warnings.add("cursedGrimoire: base item '" + c.item + "' unknown or with its own right-click use: minecraft:enchanted_book used");
			c.item = "minecraft:enchanted_book";
		}
		c.powerMultiplier = clamp(c.powerMultiplier, 0.1, 5);
		c.radiusMultiplier = clamp(c.radiusMultiplier, 0.1, 3);
		c.countMultiplier = clamp(c.countMultiplier, 0.1, 5);
		c.levelBonus = (int) clamp(c.levelBonus, 0, 3);
		Map<String, Map<String, Double>> spells = new LinkedHashMap<>();
		if (c.spells != null) {
			for (Map.Entry<String, Map<String, Double>> entry : c.spells.entrySet()) {
				Spell spell = Spell.byId(entry.getKey());
				if (spell == null) {
					warnings.add("cursedGrimoire.spells: unknown spell '" + entry.getKey() + "' (" + String.join(", ", Spell.ids()) + "): ignored");
					continue;
				}
				Map<String, Double> own = new LinkedHashMap<>();
				if (entry.getValue() != null) {
					for (Map.Entry<String, Double> param : entry.getValue().entrySet()) {
						Spell.Param known = param.getKey() == null ? null : spell.param(param.getKey().trim());
						if (known == null || param.getValue() == null) {
							warnings.add("cursedGrimoire.spells." + spell.id + ": unknown parameter '" + param.getKey() + "': ignored");
							continue;
						}
						own.put(known.name(), clamp(param.getValue(), 0.1, 5));
					}
				}
				spells.put(spell.id, own);
			}
		}
		c.spells = spells;
		c.cooldownSeconds = clamp(c.cooldownSeconds, 0.05, 600);
		c.foodCost = rarityMap(c.foodCost, defaultCursedFoodCost(), 0, 20, "cursedGrimoire.foodCost", warnings);
		c.bloodCost = rarityMap(c.bloodCost, defaultBloodCost(), 0, 20, "cursedGrimoire.bloodCost", warnings);
		c.minHealth = clamp(c.minHealth, 1, 19);
		c.bossCastCap = clamp(c.bossCastCap, 0.001, 1);
		Map<String, Double> chances = new LinkedHashMap<>();
		if (c.ruinDrops != null) {
			for (Map.Entry<String, Double> entry : c.ruinDrops.entrySet()) {
				if (entry.getKey() == null || entry.getKey().isBlank()) {
					continue;
				}
				chances.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue() == null ? 0 : clamp(entry.getValue(), 0, 1));
			}
		}
		c.ruinDrops = chances;
		c.model = c.model == null ? "" : c.model.trim().toLowerCase(Locale.ROOT);
		if (!c.model.isEmpty() && !ID.matcher(c.model).matches()) {
			warnings.add("cursedGrimoire: invalid model '" + c.model + "': vanilla look");
			c.model = "";
		}
	}

	private void rebuildSections(List<String> warnings) {
		if (casting == null) {
			casting = new Casting();
		}
		casting.moveSpeed = clamp(casting.moveSpeed, 0, 1);
		if (casting.disabledDimensions == null) {
			casting.disabledDimensions = new ArrayList<>();
		}
		List<String> dimensions = new ArrayList<>();
		for (String dimension : casting.disabledDimensions) {
			String value = dimension == null ? "" : normalize(dimension);
			if (DIMENSION.matcher(value).matches()) {
				dimensions.add(value);
			} else if (dimension != null && !dimension.isBlank()) {
				warnings.add("casting.disabledDimensions: invalid dimension '" + dimension + "': ignored");
			}
		}
		casting.disabledDimensions = dimensions;
		disabledDimensions = Set.copyOf(dimensions);
		if (limits == null) {
			limits = new Limits();
		}
		limits.maxEffectsPerPlayer = (int) clamp(limits.maxEffectsPerPlayer, 1, 16);
		limits.maxEffectsServer = (int) clamp(limits.maxEffectsServer, 1, 256);
		limits.particleBudget = (int) clamp(limits.particleBudget, 100, 20000);
		limits.viewDistance = (int) clamp(limits.viewDistance, 16, 256);
		limits.maxGuardiansPerPlayer = (int) clamp(limits.maxGuardiansPerPlayer, 1, 16);
		limits.maxFireworks = (int) clamp(limits.maxFireworks, 8, 512);
		limits.repeatHitDecay = clamp(limits.repeatHitDecay, 0.05, 1);
		limits.repeatHitFloor = clamp(limits.repeatHitFloor, 0, 1);
		limits.bossCastCap = clamp(limits.bossCastCap, 0.001, 1);
		if (trade == null) {
			trade = new Trade();
		}
		trade.prices = rarityMap(trade.prices, defaultPrices(), 1, 100000, "trade.prices", warnings);
		trade.minTiers = rarityMap(trade.minTiers, defaultMinTiers(), 1, 9, "trade.minTiers", warnings);
	}

	private static Map<String, Integer> rarityMap(@Nullable Map<String, Integer> raw, Map<String, Integer> defaults, int min, int max, String name,
			List<String> warnings) {
		Map<String, Integer> out = new LinkedHashMap<>();
		if (raw != null) {
			for (Map.Entry<String, Integer> entry : raw.entrySet()) {
				UniqueRarity rarity = UniqueRarity.parse(entry.getKey());
				if (rarity == null || entry.getValue() == null) {
					warnings.add(name + ": unknown rarity '" + entry.getKey() + "': ignored");
					continue;
				}
				out.put(rarity.name(), (int) clamp(entry.getValue(), min, max));
			}
		}
		for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
			out.putIfAbsent(entry.getKey(), entry.getValue());
		}
		return out;
	}

	private void rebuildScrolls(Predicate<String> plainItem, List<String> warnings) {
		if (scrolls == null) {
			scrolls = defaultScrolls();
		}
		Map<String, ScrollDef> valid = new LinkedHashMap<>();
		for (ScrollDef def : scrolls) {
			if (def == null) {
				continue;
			}
			def.id = def.id == null ? "" : def.id.trim().toLowerCase(Locale.ROOT);
			if (!ID.matcher(def.id).matches()) {
				warnings.add("scroll '" + def.id + "': invalid id (a-z, 0-9, _): ignored");
				continue;
			}
			if (valid.containsKey(def.id)) {
				warnings.add("scroll '" + def.id + "': duplicate id: ignored");
				continue;
			}
			Spell spell = Spell.byId(def.spell);
			if (spell == null) {
				warnings.add("scroll '" + def.id + "': unknown spell '" + def.spell + "' (" + String.join(", ", Spell.ids()) + "): ignored");
				continue;
			}
			def.spell = spell.id;
			def.parsedSpell = spell;
			def.params = spell.resolve(def.params, "scroll '" + def.id + "'", warnings);
			def.item = normalize(def.item);
			if (!plainItem.test(def.item)) {
				warnings.add("scroll '" + def.id + "': base item '" + def.item + "' unknown or with its own right-click use: minecraft:paper used");
				def.item = "minecraft:paper";
			}
			if (def.name == null || def.name.isBlank()) {
				def.name = def.id;
			}
			UniqueRarity rarity = UniqueRarity.parse(def.rarity);
			if (rarity == null) {
				warnings.add("scroll '" + def.id + "': unknown rarity '" + def.rarity + "': RARE used");
				rarity = UniqueRarity.RARE;
			}
			def.rarity = rarity.name();
			def.parsedRarity = rarity;
			if (def.lore == null) {
				def.lore = new ArrayList<>();
			}
			def.lore.removeIf(line -> line == null);
			def.maxStack = (int) clamp(def.maxStack, 1, 64);
			def.channelSeconds = clamp(def.channelSeconds, 0.25, 10);
			def.animation = def.animation == null ? "" : def.animation.trim().toLowerCase(Locale.ROOT);
			if (!ANIMATIONS.contains(def.animation)) {
				warnings.add("scroll '" + def.id + "': unknown animation '" + def.animation + "': toot_horn used");
				def.animation = "toot_horn";
			}
			def.cooldownGroup = def.cooldownGroup == null ? "" : def.cooldownGroup.trim().toLowerCase(Locale.ROOT);
			if (!GROUP.matcher(def.cooldownGroup).matches()) {
				warnings.add("scroll '" + def.id + "': invalid cooldownGroup '" + def.cooldownGroup + "': own group used");
				def.cooldownGroup = def.id.length() <= 32 ? def.id : def.id.substring(0, 32);
			}
			def.cooldownSeconds = clamp(def.cooldownSeconds, 0.05, 3600);
			def.model = def.model == null ? "" : def.model.trim().toLowerCase(Locale.ROOT);
			if (!def.model.isEmpty() && !ID.matcher(def.model).matches()) {
				warnings.add("scroll '" + def.id + "': invalid model '" + def.model + "': vanilla look");
				def.model = "";
			}
			valid.put(def.id, def);
		}
		byId = valid;
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
			table.min = (int) clamp(table.min, 1, 16);
			table.max = (int) clamp(table.max, table.min, 16);
			Map<String, Double> weights = new LinkedHashMap<>();
			if (table.items != null) {
				for (Map.Entry<String, Double> weight : table.items.entrySet()) {
					String id = weight.getKey() == null ? "" : weight.getKey().trim().toLowerCase(Locale.ROOT);
					if (!byId.containsKey(id)) {
						warnings.add("drops." + source + ": unknown scroll '" + weight.getKey() + "': ignored");
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

	/** "Paper " -> "minecraft:paper". */
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

	static Map<String, Integer> defaultPrices() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("RARE", 40);
		map.put("EPIC", 80);
		map.put("LEGENDARY", 150);
		map.put("MYTHIC", 250);
		return map;
	}

	static Map<String, Integer> defaultMinTiers() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("RARE", 2);
		map.put("EPIC", 2);
		map.put("LEGENDARY", 3);
		map.put("MYTHIC", 3);
		return map;
	}

	static Map<String, Integer> defaultFoodCost() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("RARE", 3);
		map.put("EPIC", 5);
		map.put("LEGENDARY", 7);
		map.put("MYTHIC", 10);
		return map;
	}

	static Map<String, Integer> defaultCursedFoodCost() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("RARE", 4);
		map.put("EPIC", 7);
		map.put("LEGENDARY", 9);
		map.put("MYTHIC", 12);
		return map;
	}

	static Map<String, Integer> defaultBloodCost() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("RARE", 1);
		map.put("EPIC", 1);
		map.put("LEGENDARY", 2);
		map.put("MYTHIC", 2);
		return map;
	}

	static Map<String, Double> defaultCursedRuinDrops() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put(CursedGrimoireRules.MAGE_TOWER, 0.1);
		return map;
	}

	/** Per-spell multipliers of the cursed grimoire (on top of its general ones): the showpieces get far more. */
	static Map<String, Map<String, Double>> defaultCursedSpells() {
		Map<String, Map<String, Double>> map = new LinkedHashMap<>();
		map.put(Spell.METEOR_SHOWER.id, params("count", 2.5, "radius", 1.6, "impactRadius", 1.3));
		map.put(Spell.CHAIN_STORM.id, params("count", 2.5, "chainRadius", 1.5));
		map.put(Spell.SOLAR_NOVA.id, params("knockback", 1.3));
		map.put(Spell.SINGULARITY.id, params("radius", 1.6, "pullStrength", 1.4));
		map.put(Spell.AEGIS.id, params("push", 1.3));
		map.put(Spell.REVELATION.id, params("radius", 1.25));
		map.put(Spell.EARTHQUAKE.id, params("waves", 1.67, "lift", 1.3));
		map.put(Spell.ARCANE_RAY.id, params("width", 1.5));
		map.put(Spell.SCOURGE.id, params("vulnerability", 1.4));
		map.put(Spell.SPECTRAL_CHAINS.id, params("bossShare", 1.3));
		return map;
	}

	private static Map<String, Double> params(Object... pairs) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
		}
		return map;
	}

	static Map<String, Double> defaultGrimoireDrops() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("ruin_boss", 0.08);
		map.put("nether_ruin_boss", 0.12);
		map.put("legendary_quest", 0.04);
		map.put("trade", 0.05);
		return map;
	}

	public static Map<String, DropTable> defaultDrops() {
		Map<String, DropTable> map = new LinkedHashMap<>();
		map.put("ruin_boss", new DropTable(0.6, 1, 2)
				.add("tempete_de_foudre", 6).add("blizzard", 6).add("seisme", 6).add("egide", 5).add("rappel", 5).add("benediction", 5)
				.add("revelation", 5).add("pluie_de_meteores", 4).add("nova_solaire", 4).add("gardiens", 3).add("rayon_arcanique", 3)
				.add("singularite", 1).add("banniere_de_guerre", 5).add("fleau", 5).add("permutation", 5).add("hate_du_vent", 5)
				.add("ralliement", 4).add("chaines_spectrales", 4));
		map.put("nether_ruin_boss", new DropTable(0.75, 1, 2)
				.add("pluie_de_meteores", 6).add("nova_solaire", 6).add("rayon_arcanique", 5).add("egide", 4).add("seisme", 4)
				.add("tempete_de_foudre", 4).add("rappel", 4).add("benediction", 4).add("gardiens", 3).add("singularite", 3)
				.add("revelation", 3).add("blizzard", 1).add("fleau", 5).add("banniere_de_guerre", 4).add("chaines_spectrales", 4)
				.add("permutation", 4).add("ralliement", 3).add("hate_du_vent", 3));
		map.put("boss", new DropTable(0.08, 1, 1)
				.add("benediction", 8).add("revelation", 8).add("rappel", 8).add("seisme", 6).add("tempete_de_foudre", 5).add("blizzard", 5)
				.add("egide", 4).add("nova_solaire", 2).add("pluie_de_meteores", 2).add("gardiens", 2).add("rayon_arcanique", 1)
				.add("hate_du_vent", 7).add("permutation", 6).add("ralliement", 5).add("banniere_de_guerre", 5).add("fleau", 4)
				.add("chaines_spectrales", 1));
		map.put("blood_boss", new DropTable(0.3, 1, 1)
				.add("tempete_de_foudre", 5).add("blizzard", 5).add("seisme", 5).add("egide", 5).add("nova_solaire", 4)
				.add("pluie_de_meteores", 4).add("benediction", 3).add("rayon_arcanique", 3).add("gardiens", 3).add("singularite", 1)
				.add("banniere_de_guerre", 5).add("fleau", 5).add("chaines_spectrales", 3).add("permutation", 3).add("hate_du_vent", 3)
				.add("ralliement", 2));
		map.put("legendary_quest", new DropTable(0.5, 1, 1)
				.add("egide", 4).add("nova_solaire", 4).add("pluie_de_meteores", 4).add("gardiens", 4).add("tempete_de_foudre", 4)
				.add("blizzard", 4).add("rayon_arcanique", 3).add("singularite", 1).add("chaines_spectrales", 4).add("banniere_de_guerre", 3)
				.add("fleau", 3));
		map.put("quest", new DropTable(0.05, 1, 1)
				.add("benediction", 10).add("revelation", 10).add("rappel", 10).add("seisme", 4).add("tempete_de_foudre", 3).add("blizzard", 3)
				.add("hate_du_vent", 8).add("permutation", 6).add("ralliement", 6).add("banniere_de_guerre", 3).add("fleau", 2));
		map.put("expedition", new DropTable(0.03, 1, 1)
				.add("rappel", 10).add("revelation", 8).add("benediction", 8).add("blizzard", 3).add("seisme", 3).add("tempete_de_foudre", 2)
				.add("hate_du_vent", 8).add("permutation", 6).add("ralliement", 6).add("banniere_de_guerre", 2));
		map.put("trade", new DropTable(0.35, 1, 1)
				.add("rappel", 8).add("benediction", 8).add("revelation", 8).add("seisme", 5).add("tempete_de_foudre", 5).add("blizzard", 5)
				.add("egide", 4).add("nova_solaire", 2).add("pluie_de_meteores", 2).add("gardiens", 2).add("hate_du_vent", 7)
				.add("permutation", 6).add("ralliement", 6).add("banniere_de_guerre", 4).add("fleau", 4).add("chaines_spectrales", 2));
		addShowDrops(map);
		return map;
	}

	/**
	 * Grand spectacle (Guildy 1.14): mostly from the trade hub, the expeditions and the quests, rarely from the ruins, never
	 * from the horde bosses.
	 */
	private static void addShowDrops(Map<String, DropTable> map) {
		map.get("trade").add("grand_spectacle", 4);
		map.get("expedition").add("grand_spectacle", 4);
		map.get("quest").add("grand_spectacle", 3);
		map.get("legendary_quest").add("grand_spectacle", 3);
		map.get("ruin_boss").add("grand_spectacle", 1);
		map.get("nether_ruin_boss").add("grand_spectacle", 1);
	}

	public static List<ScrollDef> defaultScrolls() {
		List<ScrollDef> list = new ArrayList<>();
		list.add(def("pluie_de_meteores", "Pluie de météores", UniqueRarity.LEGENDARY, Spell.METEOR_SHOWER, "piglin_banner_pattern", 1.5,
				"attaque", 25)
				.story("Recopié d'une tablette fondue", "retrouvée au cœur d'un cratère."));
		list.add(def("tempete_de_foudre", "Tempête de foudre", UniqueRarity.EPIC, Spell.CHAIN_STORM, "bordure_indented_banner_pattern", 1.0,
				"attaque", 20)
				.story("L'encre crépite encore", "quand l'orage approche."));
		list.add(def("blizzard", "Blizzard", UniqueRarity.EPIC, Spell.BLIZZARD, "guster_banner_pattern", 1.25, "attaque", 25)
				.story("Écrit avec du givre", "par un mage des cimes."));
		list.add(def("nova_solaire", "Nova solaire", UniqueRarity.LEGENDARY, Spell.SOLAR_NOVA, "flower_banner_pattern", 1.5, "attaque", 25)
				.story("Un fragment de soleil", "enfermé dans un rouleau de parchemin."));
		list.add(def("singularite", "Singularité", UniqueRarity.MYTHIC, Spell.SINGULARITY, "flow_banner_pattern", 2.0, "attaque", 40)
				.story("Ne pas lire à voix haute.", "Ne pas lire du tout, en fait."));
		list.add(def("egide", "Égide", UniqueRarity.EPIC, Spell.AEGIS, "field_masoned_banner_pattern", 1.0, "soutien", 30)
				.story("Le serment des gardiens de la première guilde,", "scellé dans la cire dorée."));
		list.add(def("rappel", "Rappel", UniqueRarity.RARE, Spell.RECALL, "globe_banner_pattern", 4.0, "rappel", 60)
				.story("Chaque guilde garde une copie", "de ce vieux chemin du retour."));
		list.add(def("gardiens", "Gardiens", UniqueRarity.LEGENDARY, Spell.GUARDIANS, "creeper_banner_pattern", 2.0, "invocation", 60)
				.story("Le fer se souvient des mains", "qui l'ont forgé pour défendre."));
		list.add(def("benediction", "Bénédiction", UniqueRarity.RARE, Spell.BLESSING, "mojang_banner_pattern", 1.25, "soutien", 20)
				.story("Une prière de guérisseuse", "que les villageois récitent encore."));
		list.add(def("revelation", "Révélation", UniqueRarity.RARE, Spell.REVELATION, "skull_banner_pattern", 0.75, "soutien", 15)
				.story("Ce qui rôde dans le noir", "ne s'y cachera plus."));
		list.add(def("seisme", "Séisme", UniqueRarity.EPIC, Spell.EARTHQUAKE, "field_masoned_banner_pattern", 1.25, "attaque", 20)
				.story("La terre gronde sous l'encre", "et se soulève à la dernière ligne."));
		list.add(def("rayon_arcanique", "Rayon arcanique", UniqueRarity.LEGENDARY, Spell.ARCANE_RAY, "flow_banner_pattern", 0.75, "attaque",
				25)
				.story("Une seule ligne de runes,", "si dense qu'elle brûle le papier."));
		list.add(def("banniere_de_guerre", "Bannière de guerre", UniqueRarity.EPIC, Spell.WAR_BANNER, "bordure_indented_banner_pattern", 1.25,
				"soutien", 40)
				.story("Cousue du fil des étendards", "qui n'ont jamais reculé."));
		list.add(def("fleau", "Fléau", UniqueRarity.EPIC, Spell.SCOURGE, "skull_banner_pattern", 1.25, "attaque", 30)
				.story("L'encre a la couleur du venin", "et l'odeur des marais maudits."));
		list.add(def("permutation", "Permutation", UniqueRarity.RARE, Spell.SWAP, "flow_banner_pattern", 0.75, "permutation", 20)
				.story("Deux sceaux jumeaux, un seul nom :", "celui qui lit prend la place de l'autre."));
		list.add(def("ralliement", "Ralliement", UniqueRarity.EPIC, Spell.RALLY, "globe_banner_pattern", 4.0, "rappel", 90)
				.story("Le vieux cor des guildes résonne", "jusqu'aux confins du monde."));
		list.add(def("chaines_spectrales", "Chaînes spectrales", UniqueRarity.LEGENDARY, Spell.SPECTRAL_CHAINS, "creeper_banner_pattern", 1.0,
				"attaque", 30)
				.story("Forgées dans les limbes", "pour les âmes qui refusaient de partir."));
		list.add(def("hate_du_vent", "Hâte du vent", UniqueRarity.RARE, Spell.TAILWIND, "guster_banner_pattern", 1.0, "soutien", 30)
				.story("Une plume de brise cousue", "dans la reliure du parchemin."));
		// Guildy 1.14: the fireworks show.
		list.add(def("grand_spectacle", "Grand spectacle", UniqueRarity.EPIC, Spell.FIREWORKS, "flower_banner_pattern", 3.0, "spectacle", 120)
				.story("Le carnet d'un artificier royal :", "chaque rune est une gerbe qui attend la nuit."));
		return list;
	}

	private static ScrollDef def(String id, String name, UniqueRarity rarity, Spell spell, String item, double channel, String group,
			double cooldown) {
		ScrollDef def = new ScrollDef();
		def.id = id;
		def.name = name;
		def.rarity = rarity.name();
		def.spell = spell.id;
		def.params = spell.defaults();
		def.item = "minecraft:" + item;
		def.channelSeconds = channel;
		def.cooldownGroup = group;
		def.cooldownSeconds = cooldown;
		def.maxStack = rarity == UniqueRarity.MYTHIC ? 4 : 16;
		def.model = "parchemin_" + id;
		return def;
	}
}
