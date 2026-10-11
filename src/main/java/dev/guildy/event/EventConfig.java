package dev.guildy.event;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Safe;
import dev.guildy.shop.ShopModule;
import dev.guildy.trade.TradeEntry;
import dev.guildy.trade.TradeValidator;
import dev.guildy.wave.NightModifiers;
import dev.guildy.wave.WaveConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * events.json5 - random events. Free of Minecraft classes except in {@link #validate()} (unit tests use {@link #rebuild}).
 */
@Comment({
		"Guildy - evenements aleatoires : nuit de sang (tous les serveurs), raid de pillards, caravane marchande et erudit",
		"itinerant (par guilde). Tirages une fois par jour : au crepuscule pour les evenements de nuit, le matin pour ceux",
		"de jour. Chance = probabilite a chaque tirage ; cooldownDays = jours minimum entre deux occurrences pour une guilde.",
		"Admin : /guild admin event list | start <evenement> [guilde] | stop <evenement> [guilde]",
		"(blood_night, pillager_raid, caravan, book_merchant, merchant = visite du Carrefour commercial).",
		"Echanges (trades / books) : meme format que trade.json5 ; les marchands vendent seulement, jamais moins cher que la boutique."
})
public final class EventConfig implements Validatable {

	@Comment("Activer les tirages aleatoires (les commandes admin fonctionnent toujours)")
	public boolean randomEvents = true;

	@Comment("Heures des tirages (heure du jour en ticks : 0 = lever du soleil, 12000 = coucher, 13000 = debut des hordes)")
	public Timing timing = new Timing();

	@Comment({
			"Nuit de sang : au crepuscule, si une guilde a atteint minNightStep, chance de declencher une nuit ou TOUTES les",
			"hordes sont plus nombreuses, plus fortes et paient davantage (barre de progression prefixee, annonce a tout le serveur)."
	})
	public BloodNight bloodNight = new BloodNight();

	@Comment({
			"Raid de pillards : le matin, une guilde peut etre visee. Les membres sont prevenus des qu'un membre est pres du coeur,",
			"puis une horde d'illageois attaque en plein jour (memes regles que les hordes : coeur, victoire, defaite).",
			"Sans defenseur, les pillards attendent ; ils renoncent au crepuscule. Le palier de nuit ne change pas."
	})
	public PillagerRaid pillagerRaid = new PillagerRaid();

	@Comment("Caravane marchande : un marchand ambulant et ses lamas s'installent au Carrefour commercial (ou pres du coeur)")
	public Caravan caravan = new Caravan();

	@Comment("Marchand de livres enchantes : l'erudit itinerant vend quelques livres tires dans 'books'")
	public BookMerchant bookMerchant = new BookMerchant();

	@Json5.Section
	public static final class Timing {
		@Comment("Tirage des evenements de nuit (nuit de sang) a partir de cette heure ; les raids en attente renoncent a cette heure")
		public int duskTime = 12000;
		@Comment("Tirage des evenements de jour (raid, caravane, erudit) a partir de cette heure")
		public int morningTime = 1000;
		@Comment("Duree (ticks) pendant laquelle un tirage peut encore avoir lieu apres l'heure prevue")
		public int rollWindowTicks = 1000;
	}

	@Json5.Section
	public static final class BloodNight {
		public boolean enabled = true;
		@Comment("Chance chaque soir")
		public double chance = 0.07;
		@Comment("Il faut au moins une guilde ayant atteint ce palier de nuit")
		public int minNightStep = 3;
		@Comment("Nombre minimum de jours entre deux nuits de sang")
		public int minDaysBetween = 4;
		@Comment("Multiplicateur du nombre de monstres (toujours borne par maxMobsPerWave de waves.json5)")
		public double mobCountMultiplier = 1.6;
		@Comment("Chance d'elite ajoutee, puis plafonnee a eliteCap (jamais en dessous de la chance normale)")
		public double eliteBonus = 0.15;
		public double eliteCap = 0.6;
		@Comment("Multiplicateurs de vie et de degats des monstres")
		public double healthMultiplier = 1.25;
		public double damageMultiplier = 1.25;
		@Comment("Chance qu'un boss accompagne chaque horde")
		public double bossChance = 0.6;
		@Comment("Multiplicateurs des recompenses de victoire : or, XP de guilde, tirages d'objets")
		public double goldMultiplier = 2.0;
		public double xpMultiplier = 2.0;
		public double itemRollMultiplier = 1.5;
	}

	@Json5.Section
	public static final class PillagerRaid {
		public boolean enabled = true;
		@Comment("Chance chaque matin, niveau de guilde minimum, jours minimum entre deux raids")
		public double chance = 0.05;
		public int minGuildLevel = 2;
		public int cooldownDays = 3;
		@Comment("Delai (secondes) entre l'alerte et l'attaque")
		public int warningSeconds = 120;
		@Comment("Duree maximale de l'assaut (secondes) : victoire si le coeur tient jusque-la (ou des que tous les pillards sont morts)")
		public int maxDurationSeconds = 600;
		@Comment("Multiplicateur des recompenses de victoire (or, XP de guilde, tirages d'objets)")
		public double rewardMultiplier = 1.2;
		@Comment({
				"Composition de l'assaut (meme format qu'un theme de waves.json5 : name, barColor, mobCountMultiplier, eliteBonus,",
				"forceBoss, mobs [{entity, weight, minDifficulty, maxCount, gear, alwaysHostile, spawnHeight}], bosses [...]).",
				"La difficulte suit la formule des hordes (palier de nuit, niveau, membres presents)."
		})
		public WaveConfig.Theme theme = defaultRaidTheme();
	}

	@Json5.Section
	public static final class Caravan {
		public boolean enabled = true;
		@Comment("Chance chaque matin, niveau de guilde minimum, jours minimum entre deux caravanes")
		public double chance = 0.15;
		public int minGuildLevel = 1;
		public int cooldownDays = 2;
		@Comment("Duree du sejour (secondes ; depart au plus tard a la tombee de la nuit) et nombre de lamas")
		public int staySeconds = 600;
		public int llamas = 2;
		@Comment("Nombre d'echanges proposes")
		public int offersMin = 4;
		public int offersMax = 6;
		@Comment("Echanges possibles de la caravane")
		public List<TradeEntry> trades = defaultCaravanTrades();

		private transient List<TradeEntry> usable = new ArrayList<>();

		public List<TradeEntry> usable() {
			return usable == null ? List.of() : usable;
		}
	}

	@Json5.Section
	public static final class BookMerchant {
		public boolean enabled = true;
		@Comment("Chance chaque matin, niveau de guilde minimum, jours minimum entre deux visites")
		public double chance = 0.06;
		public int minGuildLevel = 3;
		public int cooldownDays = 4;
		@Comment("Duree du sejour (secondes ; depart au plus tard a la tombee de la nuit)")
		public int staySeconds = 600;
		@Comment("Nombre de livres proposes")
		public int booksMin = 3;
		public int booksMax = 5;
		@Comment("Livres possibles (item minecraft:enchanted_book + enchantment + level), tires selon 'weight'")
		public List<TradeEntry> books = defaultBooks();

		private transient List<TradeEntry> usable = new ArrayList<>();

		public List<TradeEntry> usable() {
			return usable == null ? List.of() : usable;
		}
	}

	/** Modifiers applied to every horde of a blood night. */
	public NightModifiers bloodNightModifiers(String label) {
		BloodNight b = bloodNight;
		return new NightModifiers(label, b.mobCountMultiplier, b.eliteBonus, b.eliteCap, b.healthMultiplier, b.damageMultiplier,
				b.bossChance, b.goldMultiplier, b.xpMultiplier, b.itemRollMultiplier);
	}

	// ------------------------------------------------------------------ validation

	@Override
	@SuppressWarnings("unchecked")
	public void validate() {
		Map<String, Double>[] shop = Safe.get("event.shop_prices", () -> TradeValidator.shopPrices(ShopModule.config().categories),
				new Map[] {Map.of(), Map.of()});
		for (String warning : rebuild(id -> ItemUtil.item(id) != null, shop[0], shop[1])) {
			Guildy.LOGGER.warn("[events.json5] {}", warning);
		}
	}

	/** Clamps every value and validates the trade tables (pure). @return the problems found */
	public List<String> rebuild(Predicate<String> itemExists, Map<String, Double> shopSells, Map<String, Double> shopBuys) {
		List<String> warnings = new ArrayList<>();
		if (timing == null) {
			timing = new Timing();
		}
		timing.duskTime = clamp(timing.duskTime, 0, 23999);
		timing.morningTime = clamp(timing.morningTime, 0, 23999);
		timing.rollWindowTicks = clamp(timing.rollWindowTicks, 20, 12000);

		if (bloodNight == null) {
			bloodNight = new BloodNight();
		}
		BloodNight b = bloodNight;
		b.chance = clamp(b.chance, 0, 1);
		b.minNightStep = clamp(b.minNightStep, 0, 100_000);
		b.minDaysBetween = clamp(b.minDaysBetween, 0, 365);
		b.mobCountMultiplier = clamp(b.mobCountMultiplier, 0.1, 10);
		b.eliteBonus = clamp(b.eliteBonus, 0, 1);
		b.eliteCap = clamp(b.eliteCap, 0, 1);
		b.healthMultiplier = clamp(b.healthMultiplier, 0.1, 50);
		b.damageMultiplier = clamp(b.damageMultiplier, 0.1, 50);
		b.bossChance = clamp(b.bossChance, 0, 1);
		b.goldMultiplier = clamp(b.goldMultiplier, 0, 100);
		b.xpMultiplier = clamp(b.xpMultiplier, 0, 100);
		b.itemRollMultiplier = clamp(b.itemRollMultiplier, 0, 100);

		if (pillagerRaid == null) {
			pillagerRaid = new PillagerRaid();
		}
		PillagerRaid r = pillagerRaid;
		r.chance = clamp(r.chance, 0, 1);
		r.minGuildLevel = clamp(r.minGuildLevel, 1, 1000);
		r.cooldownDays = clamp(r.cooldownDays, 0, 365);
		r.warningSeconds = clamp(r.warningSeconds, 0, 3600);
		r.maxDurationSeconds = clamp(r.maxDurationSeconds, 30, 3600);
		r.rewardMultiplier = clamp(r.rewardMultiplier, 0, 100);
		r.theme = cleanTheme(r.theme, warnings);

		if (caravan == null) {
			caravan = new Caravan();
		}
		Caravan c = caravan;
		c.chance = clamp(c.chance, 0, 1);
		c.minGuildLevel = clamp(c.minGuildLevel, 1, 1000);
		c.cooldownDays = clamp(c.cooldownDays, 0, 365);
		c.staySeconds = clamp(c.staySeconds, 30, 3600);
		c.llamas = clamp(c.llamas, 0, 4);
		c.offersMin = clamp(c.offersMin, 1, 20);
		c.offersMax = clamp(c.offersMax, c.offersMin, 20);
		c.usable = TradeValidator.clean("caravan", c.trades, itemExists, shopSells, shopBuys, warnings);

		if (bookMerchant == null) {
			bookMerchant = new BookMerchant();
		}
		BookMerchant m = bookMerchant;
		m.chance = clamp(m.chance, 0, 1);
		m.minGuildLevel = clamp(m.minGuildLevel, 1, 1000);
		m.cooldownDays = clamp(m.cooldownDays, 0, 365);
		m.staySeconds = clamp(m.staySeconds, 30, 3600);
		m.booksMin = clamp(m.booksMin, 1, 20);
		m.booksMax = clamp(m.booksMax, m.booksMin, 20);
		m.usable = TradeValidator.clean("books", m.books, itemExists, shopSells, shopBuys, warnings);
		return warnings;
	}

	/** Same cleaning as the wave themes (lower-case ids, positive weights, sane multipliers); defaults when unusable. */
	static WaveConfig.Theme cleanTheme(WaveConfig.Theme theme, List<String> warnings) {
		if (theme == null || theme.mobs == null) {
			return defaultRaidTheme();
		}
		if (theme.id == null || theme.id.isBlank()) {
			theme.id = "pillager_raid";
		}
		if (theme.name == null || theme.name.isBlank()) {
			theme.name = "Raid de pillards";
		}
		theme.barColor = theme.barColor == null ? "YELLOW" : theme.barColor.trim().toUpperCase(Locale.ROOT);
		theme.weight = Math.max(1, theme.weight);
		theme.eliteBonus = clamp(theme.eliteBonus, 0, 1);
		theme.mobCountMultiplier = clamp(theme.mobCountMultiplier, 0.1, 10);
		List<WaveConfig.MobEntry> mobs = new ArrayList<>();
		for (WaveConfig.MobEntry entry : theme.mobs) {
			if (entry == null || entry.entity == null || entry.entity.isBlank()) {
				continue;
			}
			entry.entity = entry.entity.trim().toLowerCase(Locale.ROOT);
			entry.weight = Math.max(0, entry.weight);
			entry.minDifficulty = Math.max(0, entry.minDifficulty);
			entry.maxCount = Math.max(0, entry.maxCount);
			entry.spawnHeight = clamp(entry.spawnHeight, 0, 64);
			mobs.add(entry);
		}
		theme.mobs = mobs;
		List<WaveConfig.BossDef> bosses = new ArrayList<>();
		if (theme.bosses != null) {
			for (WaveConfig.BossDef boss : theme.bosses) {
				if (boss == null || boss.entity == null || boss.entity.isBlank()) {
					continue;
				}
				boss.entity = boss.entity.trim().toLowerCase(Locale.ROOT);
				if (boss.name == null || boss.name.isBlank()) {
					boss.name = "&4&lBoss";
				}
				boss.weight = Math.max(0, boss.weight);
				boss.healthMultiplier = clamp(boss.healthMultiplier, 0.1, 100);
				boss.damageMultiplier = clamp(boss.damageMultiplier, 0.1, 100);
				boss.spawnHeight = clamp(boss.spawnHeight, 0, 64);
				bosses.add(boss);
			}
		}
		theme.bosses = bosses;
		if (theme.mobs.isEmpty()) {
			warnings.add("pillagerRaid.theme has no mob: default composition used");
			return defaultRaidTheme();
		}
		return theme;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------------ defaults

	public static WaveConfig.Theme defaultRaidTheme() {
		WaveConfig.Theme theme = WaveConfig.Theme.of("pillager_raid", "&6Raid de pillards", 1, 0, "YELLOW");
		theme.mobCountMultiplier = 0.8;
		theme.forceBoss = true;
		theme.mobs.add(WaveConfig.MobEntry.of("minecraft:pillager", 45, 0, 0));
		theme.mobs.add(WaveConfig.MobEntry.of("minecraft:vindicator", 30, 0, 0));
		theme.mobs.add(WaveConfig.MobEntry.of("minecraft:evoker", 4, 3, 1));
		theme.bosses.add(WaveConfig.BossDef.of("minecraft:ravager", "&4&lRavageur des pillards", 10, 2.5, 1.0));
		return theme;
	}

	public static List<TradeEntry> defaultCaravanTrades() {
		List<TradeEntry> list = new ArrayList<>();
		list.add(e("cocoa_beans", 16, 5, 4, 8));
		list.add(e("sugar", 16, 4, 4, 8));
		list.add(e("honey_bottle", 4, 6, 4, 8));
		list.add(e("cookie", 16, 5, 4, 6));
		list.add(e("pumpkin_pie", 4, 5, 4, 6));
		list.add(e("cake", 1, 6, 2, 5));
		list.add(e("sweet_berries", 16, 4, 4, 6));
		list.add(e("glow_berries", 16, 5, 4, 6));
		list.add(e("red_wool", 8, 4, 4, 4));
		list.add(e("blue_wool", 8, 4, 4, 4));
		list.add(e("yellow_wool", 8, 4, 4, 4));
		list.add(e("candle", 4, 4, 4, 4));
		list.add(e("firework_rocket", 8, 6, 4, 6));
		list.add(e("spyglass", 1, 10, 1, 4));
		list.add(e("bundle", 1, 8, 1, 4));
		list.add(e("compass", 1, 9, 1, 4));
		list.add(e("map", 1, 7, 2, 4));
		list.add(e("lead", 2, 7, 2, 5));
		list.add(e("name_tag", 1, 18, 1, 4));
		list.add(e("saddle", 1, 22, 1, 4));
		list.add(e("emerald", 4, 14, 2, 4));
		list.add(e("dried_ghast", 1, 24, 1, 2));
		return list;
	}

	public static List<TradeEntry> defaultBooks() {
		List<TradeEntry> list = new ArrayList<>();
		list.add(TradeEntry.book("minecraft:mending", 1, 44, 4));
		list.add(TradeEntry.book("minecraft:unbreaking", 3, 26, 8));
		list.add(TradeEntry.book("minecraft:efficiency", 5, 34, 7));
		list.add(TradeEntry.book("minecraft:fortune", 3, 38, 6));
		list.add(TradeEntry.book("minecraft:silk_touch", 1, 30, 6));
		list.add(TradeEntry.book("minecraft:looting", 3, 36, 6));
		list.add(TradeEntry.book("minecraft:protection", 4, 28, 8));
		list.add(TradeEntry.book("minecraft:sharpness", 5, 32, 7));
		list.add(TradeEntry.book("minecraft:feather_falling", 4, 20, 8));
		list.add(TradeEntry.book("minecraft:power", 5, 28, 6));
		list.add(TradeEntry.book("minecraft:respiration", 3, 18, 6));
		list.add(TradeEntry.book("minecraft:aqua_affinity", 1, 12, 6));
		list.add(TradeEntry.book("minecraft:depth_strider", 3, 20, 5));
		list.add(TradeEntry.book("minecraft:infinity", 1, 28, 4));
		list.add(TradeEntry.book("minecraft:thorns", 3, 22, 4));
		list.add(TradeEntry.book("minecraft:fire_aspect", 2, 20, 5));
		list.add(TradeEntry.book("minecraft:sweeping_edge", 3, 16, 5));
		list.add(TradeEntry.book("minecraft:smite", 5, 18, 5));
		list.add(TradeEntry.book("minecraft:blast_protection", 4, 18, 4));
		list.add(TradeEntry.book("minecraft:projectile_protection", 4, 18, 4));
		list.add(TradeEntry.book("minecraft:fire_protection", 4, 16, 4));
		list.add(TradeEntry.book("minecraft:frost_walker", 2, 24, 3));
		list.add(TradeEntry.book("minecraft:swift_sneak", 3, 32, 2));
		list.add(TradeEntry.book("minecraft:soul_speed", 3, 24, 2));
		list.add(TradeEntry.book("minecraft:loyalty", 3, 22, 3));
		list.add(TradeEntry.book("minecraft:riptide", 3, 22, 3));
		list.add(TradeEntry.book("minecraft:channeling", 1, 26, 2));
		list.add(TradeEntry.book("minecraft:impaling", 5, 24, 3));
		list.add(TradeEntry.book("minecraft:multishot", 1, 18, 4));
		list.add(TradeEntry.book("minecraft:piercing", 4, 18, 3));
		list.add(TradeEntry.book("minecraft:quick_charge", 3, 20, 4));
		list.add(TradeEntry.book("minecraft:lure", 3, 14, 4));
		list.add(TradeEntry.book("minecraft:luck_of_the_sea", 3, 16, 4));
		list.add(TradeEntry.book("minecraft:density", 5, 26, 2));
		list.add(TradeEntry.book("minecraft:breach", 4, 30, 2));
		list.add(TradeEntry.book("minecraft:wind_burst", 3, 34, 2));
		return list;
	}

	private static TradeEntry e(String item, int count, int price, int maxUses, double weight) {
		return TradeEntry.of("minecraft:" + item, count, price, maxUses, weight);
	}
}
