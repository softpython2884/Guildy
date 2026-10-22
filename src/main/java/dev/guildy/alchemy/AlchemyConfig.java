package dev.guildy.alchemy;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * alchemy.json5 - what the alchemist villagers brew: the brewing steps of the vanilla potions, the stock each guild keeps
 * by default, the Guildy elixirs (vanilla potions with their own effects, colour and name, some of them boosting the spells)
 * and the bounds of the magic boosts. Free of Minecraft classes (unit tests); ids are checked against the game's
 * registries once the server has started ({@link PotionCatalog}).
 */
@Comment({
		"Guildy - alchimie : potions et elixirs prepares par les villageois alchimistes a un alambic du territoire.",
		"Deux modes (brewing.mode) : 'simple' (defaut) = chaque fournee ne coute que de la poudre de blaze et du temps",
		"(voir la section brewing) ; 'recipes' = brassage vanilla : chaque brassage transforme jusqu'a 3 fioles avec 1",
		"ingredient (duree, poudre de blaze : villagers.json5 -> alchemist), l'alchimiste part de fioles d'eau (ou de fioles",
		"vides remplies a une source d'eau ou a un chaudron pres de l'alambic) et enchaine les etapes de 'recipes'.",
		"Produits : 'healing' (potion), 'splash:healing' (jetable), 'lingering:healing' (persistante), 'elixir:arcanique'.",
		"Stocks : chaque guilde choisit combien en garder (menu de l'alchimiste -> Stocks de potions) ; ici les valeurs par",
		"defaut. Commandes admin : /guild admin elixir list | give <joueur> <id> [nombre] | boosts <joueur>."
})
public final class AlchemyConfig implements Validatable {
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");
	/** Current file format (new default entries are added to older files once, see {@link #validate()}). */
	public static final int CURRENT_FORMAT = 1;
	private static final Pattern ELIXIR_ID = Pattern.compile("[a-z0-9_]{1,48}");
	private static final Pattern COLOR = Pattern.compile("#?[0-9a-fA-F]{6}");
	public static final int MAX_EFFECT_LEVEL = 5;
	public static final int MAX_EFFECT_SECONDS = 3600;

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int formatVersion = 0;

	@Comment("Mode de brassage et couts du mode simple (poudre de blaze et temps par fournee)")
	public Brewing brewing = new Brewing();

	@Comment({
			"Mode 'recipes' : etapes de brassage connues : \"entree + ingredient -> sortie\" (produits comme ci-dessus, ingredient",
			"= id d'objet ; 'minecraft:' facultatif). Par defaut, les recettes vanilla des potions (verrue du Nether, puis",
			"l'ingredient de l'effet, redstone = plus long, poudre lumineuse = plus fort, oeil d'araignee fermente = effet",
			"inverse). Une ligne invalide est ignoree avec un avertissement dans les journaux du serveur."
	})
	public List<String> recipes = defaultRecipes();

	@Comment("Mode 'recipes' : ingredient qui rend une potion jetable (toute potion buvable + cet ingredient ; \"\" = jamais)")
	public String splashReagent = "minecraft:gunpowder";

	@Comment("Mode 'recipes' : ingredient qui rend une potion jetable persistante (\"\" = jamais de potion persistante)")
	public String lingeringReagent = "minecraft:dragon_breath";

	@Comment({
			"Stocks gardes par defaut (produit -> nombre de fioles, 0 = pas de stock mais visible dans le menu) : l'alchimiste",
			"prepare en priorite le produit le plus en dessous de son stock. Cette liste donne aussi l'ordre du menu des stocks ;",
			"les elixirs absents de la liste y sont ajoutes a 0. Les stocks se comptent dans tous les coffres du stockage."
	})
	public Map<String, Integer> stock = defaultStock();

	@Comment("Stock maximum qu'une guilde peut demander par produit (1 a 256)")
	public int maxStockTarget = 64;

	@Comment({
			"Elixirs de Guildy : potions buvables (fiole vanilla) avec leurs propres effets, couleur et nom. Mode 'recipes' : la",
			"potion 'base' (preparee d'abord par l'alchimiste) + l'ingredient 'reagent' (un brassage). effects : effets vanilla",
			"(level 1 = niveau I, seconds = duree). Bonus magiques (pendant magicSeconds, a la fin de la gorgee) : spellPower",
			"(0.25 = sorts +25 % : degats, soins, durees), cooldownReduction (0.3 = recharges des parchemins et grimoires -30 %),",
			"channelReduction (0.3 = incantations -30 %), foodReduction (0.5 = faim du grimoire -50 %). enabled=false : plus",
			"prepare ni bu (les fioles deja faites restent des potions sans bonus magique)."
	})
	public List<ElixirDef> elixirs = defaultElixirs();

	@Comment("Bonus magiques des elixirs : plafonds quand plusieurs elixirs sont bus, affichage")
	public Magic magic = new Magic();

	/** One elixir of the config. */
	public static final class ElixirDef {
		public String id = "";
		public String name = "";
		public String color = "#A64DFF";
		public String base = "awkward";
		public String reagent = "minecraft:amethyst_shard";
		public List<EffectDef> effects = new ArrayList<>();
		public double spellPower;
		public double cooldownReduction;
		public double channelReduction;
		public double foodReduction;
		public int magicSeconds;
		public List<String> lore = new ArrayList<>();
		public boolean enabled = true;

		public ElixirDef() {
		}

		ElixirDef(String id, String name, String color, String base, String reagent) {
			this.id = id;
			this.name = name;
			this.color = color;
			this.base = base;
			this.reagent = reagent;
		}

		ElixirDef effect(String effect, int level, int seconds) {
			effects.add(new EffectDef(effect, level, seconds));
			return this;
		}

		ElixirDef magic(double power, double cooldown, double channel, double food, int seconds) {
			spellPower = power;
			cooldownReduction = cooldown;
			channelReduction = channel;
			foodReduction = food;
			magicSeconds = seconds;
			return this;
		}

		ElixirDef lore(String... lines) {
			lore.addAll(List.of(lines));
			return this;
		}

		/** Product key of the elixir ("elixir:arcanique"). */
		public String product() {
			return PotionKey.Form.ELIXIR.prefix() + id;
		}

		/** Its magic boost (none when it boosts nothing or for no time). */
		public MagicBoostRules.Boost boost() {
			if (magicSeconds <= 0) {
				return MagicBoostRules.Boost.NONE;
			}
			return new MagicBoostRules.Boost(spellPower, cooldownReduction, channelReduction, foodReduction);
		}

		public boolean magical() {
			return boost().any();
		}

		/** RGB colour of the bottle. */
		public int rgb() {
			String value = color == null ? "" : color.trim();
			if (!COLOR.matcher(value).matches()) {
				return 0xA64DFF;
			}
			return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
		}

		/** The brewing step of the elixir (its base + its reagent), null when they are invalid. */
		public BrewBook.@Nullable Step step() {
			String from = PotionKey.canonical(base);
			String item = BrewBook.itemId(reagent);
			return from == null || item == null ? null : new BrewBook.Step(from, item, product());
		}
	}

	/** A vanilla effect of an elixir: id, level (1 = I) and duration in seconds. */
	public static final class EffectDef {
		public String effect = "";
		public int level = 1;
		public int seconds = 180;

		public EffectDef() {
		}

		EffectDef(String effect, int level, int seconds) {
			this.effect = effect;
			this.level = level;
			this.seconds = seconds;
		}

		/** Namespaced effect id. */
		public String effectId() {
			String value = effect == null ? "" : effect.trim().toLowerCase(Locale.ROOT);
			return value.contains(":") ? value : "minecraft:" + value;
		}
	}

	@Json5.Section
	public static final class Magic {
		@Comment("Bonus de puissance des sorts au plus, tous elixirs confondus (1.0 = +100 %)")
		public double maxPowerBonus = 1.0;
		@Comment("Reduction des recharges au plus (0.75 = -75 %)")
		public double maxCooldownReduction = 0.75;
		@Comment("Reduction du temps d'incantation au plus (0.75 = -75 %)")
		public double maxChannelReduction = 0.75;
		@Comment("Reduction de la faim du grimoire au plus (1.0 = gratuit)")
		public double maxFoodReduction = 1.0;
		@Comment("Affichage des bonus en cours : bossbar (barre violette en haut de l'ecran), actionbar ou none")
		public String display = "bossbar";

		public MagicBoostRules.Caps caps() {
			return new MagicBoostRules.Caps(maxPowerBonus, maxCooldownReduction, maxChannelReduction, maxFoodReduction);
		}
	}

	/** Cost of a batch in simple mode: blaze powder used up and seconds of work. */
	public static final class CostDef {
		public int blaze;
		public int seconds;

		public CostDef() {
		}

		public CostDef(int blaze, int seconds) {
			this.blaze = blaze;
			this.seconds = seconds;
		}

		public SimpleBrewing.Cost cost() {
			return new SimpleBrewing.Cost(blaze, seconds);
		}
	}

	@Json5.Section
	public static final class Brewing {
		/** Bounds of the costs. */
		public static final int MAX_BLAZE = 64;
		public static final int MAX_SECONDS = 600;

		@Comment({
				"simple (defaut) = chaque fournee (jusqu'a 3 fioles, villagers.json5 -> alchemist.bottlesPerBrew) ne coute que de la",
				"poudre de blaze (prise au stockage, puis dans son sac) et du temps a un alambic du territoire : ni fiole, ni eau, ni",
				"verrue du Nether, ni ingredient. recipes = brassage vanilla avec les etapes de 'recipes' (fioles d'eau, ingredients,",
				"combustible ; reglages dans villagers.json5 -> alchemist)"
		})
		public String mode = SimpleBrewing.Mode.SIMPLE.id();
		@Comment("Mode simple : objet consomme (poudre de blaze par defaut)")
		public String item = "minecraft:blaze_powder";
		@Comment({
				"Mode simple, cout d'une fournee : {blaze: poudre de blaze, seconds: duree}. Potion buvable simple (soin, force,",
				"rapidite...) ; le bonus de vitesse de travail s'applique a la duree ; une fournee plus petite paie sa part (arrondie",
				"au-dessus), la duree ne change pas"
		})
		public CostDef plainPotion = new CostDef(1, 20);
		@Comment("Potion longue (long_..., la redstone en vanilla)")
		public CostDef longPotion = new CostDef(1, 30);
		@Comment("Potion forte (strong_..., niveau II, la poudre lumineuse en vanilla)")
		public CostDef strongPotion = new CostDef(1, 30);
		@Comment("Ajoute au cout de la potion buvable pour une potion jetable (splash:...)")
		public CostDef splashExtra = new CostDef(0, 10);
		@Comment("Ajoute au cout de la potion buvable pour une potion persistante (lingering:...) : 2 blaze et 40 s pour une simple")
		public CostDef lingeringExtra = new CostDef(1, 20);
		@Comment("Elixir de Guildy sans bonus magique (colosse, bete, ombre, titan, phenix, abysses)")
		public CostDef elixir = new CostDef(2, 60);
		@Comment("Elixir magique (bonus des sorts : arcanique, clairvoyance, satiete)")
		public CostDef magicElixir = new CostDef(4, 90);
		@Comment({
				"Mode simple : fioles d'elixirs au plus par alchimiste et par jour de jeu, comptees aussi dans",
				"alchemist.maxPotionsPerDay (0 a 1000 ; 0 = jamais d'elixir en mode simple)"
		})
		public int maxElixirsPerDay = 6;
		@Comment({
				"Mode simple : cout propre a un produit, a la place de celui de sa classe. Exemple :",
				"{\"elixir:titan\": {blaze: 3, seconds: 75}, \"splash:strong_harming\": {blaze: 2, seconds: 45}}"
		})
		public Map<String, CostDef> products = new LinkedHashMap<>();

		public SimpleBrewing.Mode parsedMode() {
			SimpleBrewing.Mode parsed = SimpleBrewing.Mode.parse(mode);
			return parsed == null ? SimpleBrewing.Mode.SIMPLE : parsed;
		}

		/** True in simple mode (blaze powder and time only). */
		public boolean simple() {
			return parsedMode() == SimpleBrewing.Mode.SIMPLE;
		}

		/** Bounds the values, normalizes the item and the products (pure). */
		void clean(List<String> warnings) {
			SimpleBrewing.Mode parsed = SimpleBrewing.Mode.parse(mode);
			if (parsed == null) {
				warnings.add("brewing.mode: unknown mode '" + mode + "' (simple, recipes): simple");
				parsed = SimpleBrewing.Mode.SIMPLE;
			}
			mode = parsed.id();
			String id = item == null ? null : BrewBook.itemId(item);
			if (id == null) {
				warnings.add("brewing.item: invalid item id '" + item + "': minecraft:blaze_powder");
				id = "minecraft:blaze_powder";
			}
			item = id;
			plainPotion = bound("plainPotion", plainPotion, new CostDef(1, 20), 1, warnings);
			longPotion = bound("longPotion", longPotion, new CostDef(1, 30), 1, warnings);
			strongPotion = bound("strongPotion", strongPotion, new CostDef(1, 30), 1, warnings);
			splashExtra = bound("splashExtra", splashExtra, new CostDef(0, 10), 0, warnings);
			lingeringExtra = bound("lingeringExtra", lingeringExtra, new CostDef(1, 20), 0, warnings);
			elixir = bound("elixir", elixir, new CostDef(2, 60), 1, warnings);
			magicElixir = bound("magicElixir", magicElixir, new CostDef(4, 90), 1, warnings);
			maxElixirsPerDay = Math.max(0, Math.min(1000, maxElixirsPerDay));
			Map<String, CostDef> own = new LinkedHashMap<>();
			if (products != null) {
				for (Map.Entry<String, CostDef> entry : products.entrySet()) {
					String key = PotionKey.canonical(entry.getKey());
					if (key == null || key.equals(PotionKey.WATER.text()) || entry.getValue() == null) {
						warnings.add("brewing.products: invalid product or cost '" + entry.getKey() + "': ignored");
						continue;
					}
					own.put(key, bound("products." + key, entry.getValue(), null, 1, warnings));
				}
			}
			products = own;
		}

		/** Blaze powder 0 to 64, seconds {@code minSeconds} to 600; a missing cost takes its default. */
		private static CostDef bound(String name, @Nullable CostDef cost, @Nullable CostDef fallback, int minSeconds, List<String> warnings) {
			if (cost == null) {
				warnings.add("brewing." + name + ": missing cost: default");
				return fallback == null ? new CostDef(1, 20) : fallback;
			}
			int blaze = Math.max(0, Math.min(MAX_BLAZE, cost.blaze));
			int seconds = Math.max(minSeconds, Math.min(MAX_SECONDS, cost.seconds));
			if (blaze != cost.blaze || seconds != cost.seconds) {
				warnings.add("brewing." + name + ": out of bounds (blaze 0-" + MAX_BLAZE + ", seconds " + minSeconds + "-" + MAX_SECONDS + ")");
			}
			return new CostDef(blaze, seconds);
		}
	}

	// ------------------------------------------------------------------ validation

	@Override
	public void validate() {
		for (String warning : clean()) {
			LOGGER.warn("[alchemy.json5] {}", warning);
		}
	}

	/** Bounds every value, normalizes ids, drops the invalid entries (pure). @return the problems found */
	public List<String> clean() {
		List<String> warnings = new ArrayList<>();
		if (formatVersion < CURRENT_FORMAT) {
			// Format 1 is the first one: nothing to migrate yet (later formats add their new default entries here).
			formatVersion = CURRENT_FORMAT;
		}
		if (brewing == null) {
			brewing = new Brewing();
		}
		brewing.clean(warnings);
		if (recipes == null) {
			recipes = defaultRecipes();
		}
		List<String> lines = new ArrayList<>();
		for (String line : recipes) {
			BrewBook.Step step = BrewBook.parse(line);
			if (step == null) {
				warnings.add("recipes: invalid line '" + line + "' (\"input + reagent -> output\"): ignored");
				continue;
			}
			lines.add(step.text());
		}
		recipes = lines;
		String splash = splashReagent == null || splashReagent.isBlank() ? "" : BrewBook.itemId(splashReagent);
		if (splash == null) {
			warnings.add("splashReagent: invalid item id '" + splashReagent + "': no splash potions");
			splash = "";
		}
		splashReagent = splash;
		String lingering = lingeringReagent == null || lingeringReagent.isBlank() ? "" : BrewBook.itemId(lingeringReagent);
		if (lingering == null) {
			warnings.add("lingeringReagent: invalid item id '" + lingeringReagent + "': no lingering potions");
			lingering = "";
		}
		lingeringReagent = lingering;
		maxStockTarget = Math.max(1, Math.min(256, maxStockTarget));
		if (elixirs == null) {
			elixirs = defaultElixirs();
		}
		List<ElixirDef> kept = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (ElixirDef def : elixirs) {
			if (def == null) {
				continue;
			}
			def.id = def.id == null ? "" : def.id.trim().toLowerCase(Locale.ROOT);
			if (!ELIXIR_ID.matcher(def.id).matches() || !ids.add(def.id)) {
				warnings.add("elixirs: invalid or duplicate id '" + def.id + "' (a-z, 0-9, _): ignored");
				continue;
			}
			cleanElixir(def, warnings);
			kept.add(def);
		}
		elixirs = kept;
		if (stock == null) {
			stock = defaultStock();
		}
		Map<String, Integer> targets = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : stock.entrySet()) {
			String key = PotionKey.canonical(entry.getKey());
			if (key == null || key.equals(PotionKey.WATER.text())) {
				warnings.add("stock: invalid product '" + entry.getKey() + "': ignored");
				continue;
			}
			int value = entry.getValue() == null ? 0 : entry.getValue();
			targets.put(key, Math.max(0, Math.min(maxStockTarget, value)));
		}
		stock = targets;
		if (magic == null) {
			magic = new Magic();
		}
		magic.maxPowerBonus = bound(magic.maxPowerBonus, 0, 10);
		magic.maxCooldownReduction = bound(magic.maxCooldownReduction, 0, 0.95);
		magic.maxChannelReduction = bound(magic.maxChannelReduction, 0, 0.95);
		magic.maxFoodReduction = bound(magic.maxFoodReduction, 0, 1);
		String display = magic.display == null ? "" : magic.display.trim().toLowerCase(Locale.ROOT);
		magic.display = Set.of("bossbar", "actionbar", "none").contains(display) ? display : "bossbar";
		return warnings;
	}

	private static void cleanElixir(ElixirDef def, List<String> warnings) {
		if (def.name == null || def.name.isBlank()) {
			def.name = def.id;
		}
		if (def.color == null || !COLOR.matcher(def.color.trim()).matches()) {
			warnings.add("elixirs." + def.id + ": invalid color '" + def.color + "' (#RRGGBB)");
			def.color = "#A64DFF";
		}
		String base = PotionKey.canonical(def.base);
		if (base == null || base.equals(def.product())) {
			warnings.add("elixirs." + def.id + ": invalid base '" + def.base + "': awkward");
			base = "awkward";
		}
		def.base = base;
		String reagent = BrewBook.itemId(def.reagent);
		if (reagent == null) {
			warnings.add("elixirs." + def.id + ": invalid reagent '" + def.reagent + "': disabled");
			def.enabled = false;
			reagent = "minecraft:air";
		}
		def.reagent = reagent;
		List<EffectDef> effects = new ArrayList<>();
		for (EffectDef effect : def.effects == null ? List.<EffectDef>of() : def.effects) {
			if (effect == null || effect.effect == null || !effect.effectId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
				warnings.add("elixirs." + def.id + ": invalid effect: ignored");
				continue;
			}
			effect.effect = effect.effectId();
			effect.level = Math.max(1, Math.min(MAX_EFFECT_LEVEL, effect.level));
			effect.seconds = Math.max(1, Math.min(MAX_EFFECT_SECONDS, effect.seconds));
			effects.add(effect);
		}
		def.effects = effects;
		def.spellPower = bound(def.spellPower, 0, 2);
		def.cooldownReduction = bound(def.cooldownReduction, 0, 0.9);
		def.channelReduction = bound(def.channelReduction, 0, 0.9);
		def.foodReduction = bound(def.foodReduction, 0, 1);
		def.magicSeconds = Math.max(0, Math.min(MAX_EFFECT_SECONDS, def.magicSeconds));
		def.lore = def.lore == null ? new ArrayList<>() : new ArrayList<>(def.lore);
		def.lore.removeIf(line -> line == null);
		if (def.effects.isEmpty() && !def.magical()) {
			warnings.add("elixirs." + def.id + ": no effect and no magic boost");
		}
	}

	private static double bound(double value, double min, double max) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
	}

	// ------------------------------------------------------------------ lookups

	public @Nullable ElixirDef elixir(@Nullable String id) {
		if (id == null || elixirs == null) {
			return null;
		}
		for (ElixirDef def : elixirs) {
			if (def.id.equals(id)) {
				return def;
			}
		}
		return null;
	}

	/** Recipe steps of the config (valid lines). */
	public List<BrewBook.Step> recipeSteps() {
		List<BrewBook.Step> steps = new ArrayList<>();
		for (String line : recipes == null ? List.<String>of() : recipes) {
			BrewBook.Step step = BrewBook.parse(line);
			if (step != null) {
				steps.add(step);
			}
		}
		return steps;
	}

	/** Brewing book of the config: recipes, bottle changes, enabled elixirs. */
	public BrewBook book() {
		List<BrewBook.Step> elixirSteps = new ArrayList<>();
		for (ElixirDef def : elixirs == null ? List.<ElixirDef>of() : elixirs) {
			BrewBook.Step step = def.enabled ? def.step() : null;
			if (step != null) {
				elixirSteps.add(step);
			}
		}
		return BrewBook.build(recipeSteps(), splashReagent, lingeringReagent, elixirSteps);
	}

	/** Products of the stock menu, in order: the stock list, then the enabled elixirs it does not name. */
	public List<String> menuProducts() {
		List<String> products = new ArrayList<>(stock == null ? List.of() : stock.keySet());
		for (ElixirDef def : elixirs == null ? List.<ElixirDef>of() : elixirs) {
			if (def.enabled && !products.contains(def.product())) {
				products.add(def.product());
			}
		}
		return products;
	}

	/** Default stock of a product (0 when the list does not name it). */
	public int defaultTarget(String product) {
		Integer value = stock == null ? null : stock.get(product);
		return value == null ? 0 : value;
	}

	// ------------------------------------------------------------------ defaults

	/** The vanilla brewing recipes of the drinkable potions (Minecraft 26.3, data/minecraft/recipe/brewing). */
	public static List<String> defaultRecipes() {
		return new ArrayList<>(List.of(
				"water + nether_wart -> awkward",
				"water + fermented_spider_eye -> weakness",
				"weakness + redstone -> long_weakness",
				"awkward + glistering_melon_slice -> healing",
				"healing + glowstone_dust -> strong_healing",
				"awkward + spider_eye -> poison",
				"poison + redstone -> long_poison",
				"poison + glowstone_dust -> strong_poison",
				"healing + fermented_spider_eye -> harming",
				"strong_healing + fermented_spider_eye -> strong_harming",
				"poison + fermented_spider_eye -> harming",
				"long_poison + fermented_spider_eye -> harming",
				"strong_poison + fermented_spider_eye -> strong_harming",
				"harming + glowstone_dust -> strong_harming",
				"awkward + ghast_tear -> regeneration",
				"regeneration + redstone -> long_regeneration",
				"regeneration + glowstone_dust -> strong_regeneration",
				"awkward + blaze_powder -> strength",
				"strength + redstone -> long_strength",
				"strength + glowstone_dust -> strong_strength",
				"awkward + sugar -> swiftness",
				"swiftness + redstone -> long_swiftness",
				"swiftness + glowstone_dust -> strong_swiftness",
				"swiftness + fermented_spider_eye -> slowness",
				"long_swiftness + fermented_spider_eye -> long_slowness",
				"awkward + rabbit_foot -> leaping",
				"leaping + redstone -> long_leaping",
				"leaping + glowstone_dust -> strong_leaping",
				"leaping + fermented_spider_eye -> slowness",
				"long_leaping + fermented_spider_eye -> long_slowness",
				"slowness + redstone -> long_slowness",
				"slowness + glowstone_dust -> strong_slowness",
				"awkward + magma_cream -> fire_resistance",
				"fire_resistance + redstone -> long_fire_resistance",
				"awkward + golden_carrot -> night_vision",
				"night_vision + redstone -> long_night_vision",
				"night_vision + fermented_spider_eye -> invisibility",
				"long_night_vision + fermented_spider_eye -> long_invisibility",
				"invisibility + redstone -> long_invisibility",
				"awkward + pufferfish -> water_breathing",
				"water_breathing + redstone -> long_water_breathing",
				"awkward + turtle_helmet -> turtle_master",
				"turtle_master + redstone -> long_turtle_master",
				"turtle_master + glowstone_dust -> strong_turtle_master",
				"awkward + phantom_membrane -> slow_falling",
				"slow_falling + redstone -> long_slow_falling",
				"awkward + breeze_rod -> wind_charged",
				"awkward + cobweb -> weaving",
				"awkward + slime_block -> oozing",
				"awkward + stone -> infested"
		));
	}

	/** A few healing and combat potions, the common elixirs (the very rare reagents only on demand). */
	public static Map<String, Integer> defaultStock() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("healing", 6);
		map.put("strong_healing", 3);
		map.put("splash:healing", 3);
		map.put("regeneration", 2);
		map.put("strength", 4);
		map.put("strong_strength", 2);
		map.put("swiftness", 2);
		map.put("fire_resistance", 4);
		map.put("night_vision", 2);
		map.put("water_breathing", 2);
		map.put("slow_falling", 2);
		map.put("invisibility", 0);
		map.put("leaping", 0);
		map.put("turtle_master", 0);
		map.put("long_fire_resistance", 0);
		map.put("splash:harming", 0);
		map.put("splash:poison", 0);
		map.put("splash:weakness", 0);
		map.put("splash:slowness", 0);
		map.put("lingering:healing", 0);
		map.put("elixir:arcanique", 2);
		map.put("elixir:satiete", 2);
		map.put("elixir:colosse", 2);
		map.put("elixir:bete", 1);
		map.put("elixir:ombre", 1);
		map.put("elixir:phenix", 1);
		map.put("elixir:titan", 0);
		map.put("elixir:clairvoyance", 0);
		map.put("elixir:abysses", 0);
		return map;
	}

	public static List<ElixirDef> defaultElixirs() {
		List<ElixirDef> list = new ArrayList<>();
		list.add(new ElixirDef("colosse", "&6Élixir du colosse", "#8E3B1F", "strong_strength", "minecraft:armadillo_scute")
				.effect("strength", 2, 180).effect("resistance", 1, 180)
				.lore("Une potion de force II épaissie d'écailles", "de tatou : la peau devient de la pierre."));
		list.add(new ElixirDef("bete", "&2Élixir de la bête", "#6B8E23", "strength", "minecraft:rabbit_foot")
				.effect("strength", 1, 180).effect("speed", 1, 180).effect("jump_boost", 1, 180)
				.lore("Force, vitesse et bonds du fauve."));
		list.add(new ElixirDef("ombre", "&8Élixir de l'ombre", "#3B3355", "invisibility", "minecraft:phantom_membrane")
				.effect("invisibility", 1, 240).effect("night_vision", 1, 240).effect("speed", 1, 240)
				.lore("Voir sans être vu, et filer avant l'aube."));
		list.add(new ElixirDef("titan", "&eÉlixir du titan", "#E8B730", "strong_healing", "minecraft:golden_apple")
				.effect("health_boost", 2, 240).effect("absorption", 2, 240)
				.lore("Quatre cœurs de plus, et un bouclier doré."));
		list.add(new ElixirDef("phenix", "&cÉlixir du phénix", "#FF6A1A", "fire_resistance", "minecraft:ghast_tear")
				.effect("fire_resistance", 1, 240).effect("regeneration", 2, 45)
				.lore("Renaître des flammes, encore et encore."));
		list.add(new ElixirDef("abysses", "&3Élixir des abysses", "#1E6F8F", "water_breathing", "minecraft:heart_of_the_sea")
				.effect("water_breathing", 1, 480).effect("conduit_power", 1, 480).effect("dolphins_grace", 1, 480)
				.lore("Le cœur de la mer bat dans vos veines."));
		list.add(new ElixirDef("arcanique", "&dÉlixir arcanique", "#A64DFF", "awkward", "minecraft:amethyst_shard")
				.magic(0.25, 0, 0, 0, 300)
				.lore("L'améthyste chante : vos sorts frappent", "plus fort, soignent mieux et durent plus longtemps."));
		list.add(new ElixirDef("clairvoyance", "&bÉlixir de clairvoyance", "#6FE3E8", "night_vision", "minecraft:echo_shard")
				.effect("night_vision", 1, 300).magic(0, 0.3, 0.3, 0, 300)
				.lore("L'écho du vide guide la main : incantations", "plus rapides, recharges plus courtes."));
		list.add(new ElixirDef("satiete", "&aÉlixir de satiété mystique", "#8FD14F", "awkward", "minecraft:glow_berries")
				.magic(0, 0, 0, 0.5, 360)
				.lore("Les baies lumineuses nourrissent l'esprit :", "le grimoire a moins faim."));
		return list;
	}
}
