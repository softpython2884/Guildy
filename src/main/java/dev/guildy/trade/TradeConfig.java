package dev.guildy.trade;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Safe;
import dev.guildy.shop.ShopModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * trade.json5 - the trade hub ("Carrefour commercial") and its visiting merchants. Free of Minecraft classes except in
 * {@link #validate()} (unit tests use {@link #rebuild}).
 */
@Comment({
		"Guildy - Carrefour commercial : des marchands de passage (marchands ambulants vanilla) viennent certains matins",
		"vendre des marchandises contre de l'or (lingots ; au-dela de 64 or : blocs d'or + lingots). Ils ne rachetent rien.",
		"tiers : paliers du Carrefour (index 0 = palier 1) : name, minLevel (niveau de guilde), costGold (or du tresor),",
		"costItems [{item, count}] (objets simples pris dans l'inventaire du joueur qui ameliore), offersMin / offersMax",
		"(nombre d'echanges par visite), trades (table du palier). Un Carrefour de palier N tire ses echanges dans sa table",
		"(poids visits.ownTierWeight) et dans celles des paliers inferieurs (poids visits.lowerTierWeight chacune).",
		"Echange : {item, count (lot), price (or), maxUses (echanges possibles pendant la visite), weight (chance relative),",
		"enchantment (optionnel, ex. minecraft:mending : livre enchante ou objet enchante), level}.",
		"Regles appliquees au chargement : objets en or et objets inconnus refuses ; un marchand ne vend jamais moins cher",
		"(a l'unite) que la boutique ni que le prix de rachat de la boutique (pas de boucle d'or ; prix releve sinon)."
})
public final class TradeConfig implements Validatable {

	@Comment("Visites des marchands au Carrefour commercial")
	public Visits visits = new Visits();

	@Comment("Paliers du Carrefour (voir l'explication en haut du fichier)")
	public List<Tier> tiers = defaultTiers();

	@Json5.Section
	public static final class Visits {
		@Comment("Activer les visites naturelles (les commandes admin fonctionnent toujours)")
		public boolean enabled = true;
		@Comment("Chance de visite chaque matin = chanceBase + chancePerTier * (palier - 1)")
		public double chanceBase = 0.5;
		public double chancePerTier = 0.15;
		@Comment("Heure du jour (ticks) du tirage du matin ; tirage possible pendant rollWindowTicks ticks (une fois par jour)")
		public int morningTime = 1000;
		public int rollWindowTicks = 2000;
		@Comment("Duree du sejour d'un marchand (secondes)")
		public int staySeconds = 600;
		@Comment("Les marchands arrives de jour repartent au plus tard a la tombee de la nuit")
		public boolean leaveAtNight = true;
		@Comment("Poids du palier du Carrefour et de chaque palier inferieur dans le tirage des echanges")
		public double ownTierWeight = 3;
		public double lowerTierWeight = 1;
		@Comment("Rayon (blocs) autour de son point d'arrivee dans lequel le marchand reste")
		public int homeRadius = 6;
	}

	public static final class Tier {
		public String name = "";
		public int minLevel = 1;
		public int costGold;
		public List<Cost> costItems = new ArrayList<>();
		public int offersMin = 5;
		public int offersMax = 6;
		public List<TradeEntry> trades = new ArrayList<>();

		private transient List<TradeEntry> usable = new ArrayList<>();

		/** Validated trades of this tier. */
		public List<TradeEntry> usable() {
			return usable == null ? List.of() : usable;
		}
	}

	public static final class Cost {
		public String item = "minecraft:emerald";
		public int count = 1;

		public Cost() {
		}

		public Cost(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}

	/** Number of tiers (at least 1). */
	public int maxTier() {
		return Math.max(1, tiers.size());
	}

	/** Tier definition (1-based, clamped). */
	public Tier tier(int tier) {
		return tiers.get(Math.max(1, Math.min(tier, tiers.size())) - 1);
	}

	/** Validated trade tables, index 0 = tier 1. */
	public List<List<TradeEntry>> tables() {
		List<List<TradeEntry>> tables = new ArrayList<>();
		for (Tier tier : tiers) {
			tables.add(tier.usable());
		}
		return tables;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void validate() {
		Map<String, Double>[] shop = Safe.get("trade.shop_prices", () -> TradeValidator.shopPrices(ShopModule.config().categories),
				new Map[] {Map.of(), Map.of()});
		for (String warning : rebuild(id -> ItemUtil.item(id) != null, shop[0], shop[1])) {
			Guildy.LOGGER.warn("[trade.json5] {}", warning);
		}
	}

	/** Clamps the settings and validates the trade tables (pure). @return the problems found */
	public List<String> rebuild(Predicate<String> itemExists, Map<String, Double> shopSells, Map<String, Double> shopBuys) {
		if (visits == null) {
			visits = new Visits();
		}
		Visits v = visits;
		v.chanceBase = clamp(v.chanceBase, 0, 1);
		v.chancePerTier = clamp(v.chancePerTier, 0, 1);
		v.morningTime = (int) clamp(v.morningTime, 0, 23999);
		v.rollWindowTicks = (int) clamp(v.rollWindowTicks, 20, 12000);
		v.staySeconds = (int) clamp(v.staySeconds, 30, 3600);
		v.ownTierWeight = clamp(v.ownTierWeight, 0, 1000);
		v.lowerTierWeight = clamp(v.lowerTierWeight, 0, 1000);
		v.homeRadius = (int) clamp(v.homeRadius, 2, 32);
		List<String> warnings = new ArrayList<>();
		if (tiers == null || tiers.isEmpty()) {
			tiers = defaultTiers();
		}
		tiers.removeIf(t -> t == null);
		if (tiers.isEmpty()) {
			tiers = defaultTiers();
		}
		int minLevel = 1;
		for (int i = 0; i < tiers.size(); i++) {
			Tier tier = tiers.get(i);
			if (tier.name == null || tier.name.isBlank()) {
				tier.name = "Palier " + (i + 1);
			}
			tier.minLevel = Math.max(minLevel, Math.min(1000, tier.minLevel));
			minLevel = tier.minLevel;
			tier.costGold = (int) clamp(tier.costGold, 0, 1_000_000);
			if (tier.costItems == null) {
				tier.costItems = new ArrayList<>();
			}
			tier.costItems.removeIf(c -> c == null || c.item == null || c.item.isBlank());
			for (Cost cost : tier.costItems) {
				cost.item = TradeValidator.normalizeId(cost.item);
				cost.count = (int) clamp(cost.count, 1, 64 * 36);
				if (!itemExists.test(cost.item)) {
					warnings.add("tier " + (i + 1) + ": unknown cost item '" + cost.item + "' (ignored)");
				}
			}
			tier.costItems.removeIf(c -> !itemExists.test(c.item));
			tier.offersMin = (int) clamp(tier.offersMin, 1, 20);
			tier.offersMax = (int) clamp(tier.offersMax, tier.offersMin, 20);
			tier.usable = TradeValidator.clean("tier " + (i + 1), tier.trades, itemExists, shopSells, shopBuys, warnings);
		}
		return warnings;
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------------ defaults

	public static List<Tier> defaultTiers() {
		List<Tier> list = new ArrayList<>();
		list.add(tier("Halte marchande", 2, 0, List.of(), 5, 7, tier1()));
		list.add(tier("Comptoir prospère", 3, 150, List.of(new Cost("minecraft:emerald", 16)), 6, 8, tier2()));
		list.add(tier("Grand carrefour", 5, 400, List.of(new Cost("minecraft:diamond_block", 2)), 7, 9, tier3()));
		return list;
	}

	private static Tier tier(String name, int minLevel, int gold, List<Cost> costs, int offersMin, int offersMax, List<TradeEntry> trades) {
		Tier tier = new Tier();
		tier.name = name;
		tier.minLevel = minLevel;
		tier.costGold = gold;
		tier.costItems = new ArrayList<>(costs);
		tier.offersMin = offersMin;
		tier.offersMax = offersMax;
		tier.trades = trades;
		return tier;
	}

	/** Tier 1: saplings, seeds, dyes, flowers and plants. */
	static List<TradeEntry> tier1() {
		List<TradeEntry> list = new ArrayList<>();
		for (String sapling : new String[] {"oak_sapling", "spruce_sapling", "birch_sapling"}) {
			list.add(e(sapling, 4, 2, 8, 10));
		}
		for (String sapling : new String[] {"jungle_sapling", "acacia_sapling", "dark_oak_sapling"}) {
			list.add(e(sapling, 4, 3, 8, 10));
		}
		list.add(e("cherry_sapling", 2, 3, 6, 10));
		list.add(e("mangrove_propagule", 2, 3, 6, 8));
		list.add(e("pale_oak_sapling", 2, 4, 6, 8));
		list.add(e("azalea", 2, 3, 6, 6));
		list.add(e("flowering_azalea", 2, 3, 6, 6));
		list.add(e("wheat_seeds", 16, 3, 8, 8));
		list.add(e("beetroot_seeds", 16, 3, 8, 8));
		list.add(e("melon_seeds", 8, 3, 8, 8));
		list.add(e("pumpkin_seeds", 8, 3, 8, 8));
		list.add(e("torchflower_seeds", 4, 4, 6, 5));
		list.add(e("pitcher_pod", 2, 4, 6, 5));
		for (String color : new String[] {"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan",
				"light_blue", "blue", "purple", "magenta", "pink"}) {
			list.add(e(color + "_dye", 8, 2, 8, 2));
		}
		for (String flower : new String[] {"dandelion", "poppy", "pink_petals", "wildflowers"}) {
			list.add(e(flower, 8, 2, 8, 3));
		}
		for (String flower : new String[] {"blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
				"oxeye_daisy", "cornflower", "lily_of_the_valley"}) {
			list.add(e(flower, 4, 2, 8, 3));
		}
		for (String flower : new String[] {"sunflower", "lilac", "rose_bush", "peony"}) {
			list.add(e(flower, 2, 2, 6, 3));
		}
		list.add(e("torchflower", 2, 3, 4, 3));
		list.add(e("open_eyeblossom", 2, 3, 4, 3));
		list.add(e("cactus_flower", 2, 3, 4, 3));
		list.add(e("spore_blossom", 1, 4, 4, 3));
		list.add(e("sugar_cane", 8, 2, 8, 6));
		list.add(e("cactus", 4, 2, 8, 6));
		list.add(e("bamboo", 8, 2, 8, 6));
		list.add(e("cocoa_beans", 8, 3, 8, 6));
		list.add(e("vine", 8, 2, 8, 6));
		list.add(e("lily_pad", 4, 2, 8, 6));
		list.add(e("glow_berries", 8, 3, 8, 6));
		list.add(e("sweet_berries", 8, 2, 8, 6));
		list.add(e("kelp", 8, 2, 8, 5));
		list.add(e("moss_block", 4, 3, 8, 5));
		list.add(e("brown_mushroom", 4, 2, 8, 5));
		list.add(e("red_mushroom", 4, 2, 8, 5));
		list.add(e("big_dripleaf", 2, 3, 6, 4));
		list.add(e("small_dripleaf", 2, 3, 6, 4));
		list.add(e("glow_lichen", 8, 3, 6, 4));
		list.add(e("firefly_bush", 2, 3, 6, 4));
		return list;
	}

	/** Tier 2: mob drops, ocean goods, ice, music discs, saddles and name tags... */
	static List<TradeEntry> tier2() {
		List<TradeEntry> list = new ArrayList<>();
		list.add(e("slime_ball", 4, 5, 4, 8));
		list.add(e("honeycomb", 6, 6, 4, 8));
		list.add(e("honey_bottle", 4, 6, 4, 6));
		list.add(e("nautilus_shell", 1, 8, 4, 6));
		list.add(e("amethyst_shard", 8, 8, 4, 6));
		list.add(e("blue_ice", 4, 10, 4, 6));
		list.add(e("packed_ice", 8, 8, 4, 6));
		list.add(e("sea_pickle", 4, 5, 4, 6));
		list.add(e("prismarine_shard", 8, 6, 4, 5));
		list.add(e("prismarine_crystals", 8, 6, 4, 5));
		list.add(e("turtle_scute", 2, 12, 2, 4));
		list.add(e("armadillo_scute", 2, 10, 2, 4));
		list.add(e("ink_sac", 8, 4, 4, 5));
		list.add(e("glow_ink_sac", 4, 6, 4, 5));
		list.add(e("rabbit_foot", 1, 8, 2, 4));
		list.add(e("phantom_membrane", 2, 8, 2, 4));
		list.add(e("ghast_tear", 1, 12, 2, 3));
		list.add(e("ender_pearl", 2, 10, 2, 5));
		list.add(e("experience_bottle", 4, 14, 2, 5));
		list.add(e("name_tag", 1, 20, 1, 5));
		list.add(e("saddle", 1, 24, 1, 5));
		list.add(e("lead", 2, 7, 2, 5));
		list.add(e("bell", 1, 16, 1, 3));
		list.add(e("iron_horse_armor", 1, 12, 1, 3));
		list.add(e("turtle_egg", 1, 12, 2, 2));
		for (String disc : new String[] {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait"}) {
			list.add(e("music_disc_" + disc, 1, 16, 1, 2));
		}
		return list;
	}

	/** Tier 3 ("trades de fou"): enchanted books, totem, enchanted golden apple, rare treasures. */
	static List<TradeEntry> tier3() {
		List<TradeEntry> list = new ArrayList<>();
		list.add(TradeEntry.book("minecraft:mending", 1, 48, 5));
		list.add(TradeEntry.book("minecraft:unbreaking", 3, 30, 6));
		list.add(TradeEntry.book("minecraft:efficiency", 5, 36, 6));
		list.add(TradeEntry.book("minecraft:fortune", 3, 40, 5));
		list.add(TradeEntry.book("minecraft:silk_touch", 1, 32, 5));
		list.add(TradeEntry.book("minecraft:looting", 3, 40, 5));
		list.add(TradeEntry.book("minecraft:protection", 4, 30, 6));
		list.add(e("totem_of_undying", 1, 99, 1, 5));
		list.add(e("enchanted_golden_apple", 1, 64, 1, 4));
		list.add(e("netherite_upgrade_smithing_template", 1, 48, 1, 4));
		list.add(e("heart_of_the_sea", 1, 40, 1, 4));
		list.add(e("trident", 1, 64, 1, 3));
		list.add(e("shulker_shell", 2, 40, 2, 4));
		list.add(e("diamond_horse_armor", 1, 24, 1, 3));
		list.add(e("echo_shard", 2, 16, 2, 3));
		list.add(e("sniffer_egg", 1, 32, 1, 2));
		list.add(e("wither_skeleton_skull", 1, 48, 1, 2));
		list.add(e("elytra", 1, 270, 1, 1));
		return list;
	}

	private static TradeEntry e(String item, int count, int price, int maxUses, double weight) {
		return TradeEntry.of("minecraft:" + item, count, price, maxUses, weight);
	}
}
