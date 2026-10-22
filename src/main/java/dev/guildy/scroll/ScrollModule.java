package dev.guildy.scroll;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.ScrollDrops;
import dev.guildy.stats.FightTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Spell scrolls ("parchemins de sort"): single-use vanilla items (custom data + components) defined in
 * config/guildy/scrolls.json5, cast with a vanilla channel animation, nineteen spells with long animated particle shows (PvE
 * only, no block ever changed; one of them is a real fireworks show), drop tables per source and the admin commands; the
 * grimoire binds up to four scrolls and
 * casts their spells for food, the cursed grimoire (dropped only by the cursed guardians of {@code cursedGrimoire.ruinDrops})
 * casts them much stronger for food and blood. Implements the core {@link ScrollDrops} hook used by the ruin bosses, the hordes, the quests,
 * the merchants, the expeditions and the alchemist villagers.
 */
public final class ScrollModule {
	/** Drop source of the trade hub merchant. */
	static final String TRADE_SOURCE = "trade";
	private static final ScrollConfig DEFAULTS = defaults();
	private static @Nullable ConfigHandle<ScrollConfig> config;

	private ScrollModule() {
	}

	public static void init() {
		ConfigHandle<ScrollConfig> handle = ConfigManager.register("scrolls.json5", ScrollConfig.class, ScrollConfig::new);
		config = handle;
		handle.onReload(ScrollItems::clearSignatures);
		ScrollLang.register();
		GrimoireLang.register();
		ScrollCommands.register();
		ScrollCasting.register();
		EffectRunner.init();
		ScrollTracker.init();
		Vulnerability.init();
		TailwindSpell.init();
		RallySpell.registerCommand();
		ShowLang.register();
		FireworksSpell.init();
		// Fight statistics: the hits of a summoned guardian count for its caster.
		FightTracker.addOwnerResolver(GuardiansSpell::summoner);
		ScrollDrops.roller = ScrollModule::roll;
		ScrollDrops.picker = ScrollModule::pick;
		ScrollDrops.factory = ScrollModule::create;
		ScrollDrops.lookup = ScrollModule::info;
	}

	/** Loaded config (validated defaults before the first load / in unit tests). */
	public static ScrollConfig config() {
		ConfigHandle<ScrollConfig> handle = config;
		return handle != null ? handle.get() : DEFAULTS;
	}

	private static ScrollConfig defaults() {
		ScrollConfig cfg = new ScrollConfig();
		cfg.rebuild(id -> true);
		return cfg;
	}

	/**
	 * {@link ScrollDrops.Roller}: a blank grimoire (grimoire.drops chance of the source, the trade hub excepted: see
	 * {@link #tradeRoll}), else scrolls of the source's table, or empty.
	 */
	static ItemStack roll(String source, RandomGenerator random) {
		ScrollConfig cfg = config();
		if (!cfg.dropsEnabled || GuildyServer.server() == null) {
			return ItemStack.EMPTY;
		}
		if (!TRADE_SOURCE.equals(source)) {
			ItemStack grimoire = rollGrimoire(cfg, source, random);
			if (!grimoire.isEmpty()) {
				return grimoire;
			}
		}
		ScrollRolls.Drop drop = ScrollRolls.roll(cfg.table(source), id -> cfg.scroll(id) != null, random);
		ScrollConfig.ScrollDef def = drop == null ? null : cfg.scroll(drop.id());
		return def == null ? ItemStack.EMPTY : ScrollItems.create(def, drop.count());
	}

	/** {@link ScrollDrops.Picker}: one scroll of the source's table, its chance ignored, or empty. */
	static ItemStack pick(String source, RandomGenerator random) {
		ScrollConfig cfg = config();
		ScrollConfig.DropTable table = cfg.table(source);
		if (!cfg.dropsEnabled || table == null || GuildyServer.server() == null) {
			return ItemStack.EMPTY;
		}
		String id = ScrollRolls.pick(table, key -> cfg.scroll(key) != null, random);
		ScrollConfig.ScrollDef def = id == null ? null : cfg.scroll(id);
		return def == null ? ItemStack.EMPTY : ScrollItems.create(def, 1);
	}

	/** A blank grimoire with the source's grimoire.drops chance, or empty. */
	private static ItemStack rollGrimoire(ScrollConfig cfg, String source, RandomGenerator random) {
		double chance = cfg.grimoire.dropChance(source);
		if (chance <= 0 || chance < 1 && random.nextDouble() >= chance) {
			return ItemStack.EMPTY;
		}
		return GrimoireItems.create(GrimoireRules.Pages.EMPTY);
	}

	/**
	 * What a trade hub merchant of this tier may sell besides its offers: a blank grimoire (grimoire.drops "trade" chance,
	 * from grimoire.tradeMinTier), else a scroll of the "trade" table, or empty.
	 */
	public static ItemStack tradeRoll(int tier) {
		ScrollConfig cfg = config();
		if (cfg.dropsEnabled && GuildyServer.server() != null && tier >= cfg.grimoire.tradeMinTier) {
			ItemStack grimoire = rollGrimoire(cfg, TRADE_SOURCE, ThreadLocalRandom.current());
			if (!grimoire.isEmpty()) {
				return grimoire;
			}
		}
		return ScrollDrops.roll(TRADE_SOURCE);
	}

	/**
	 * The cursed grimoire of a defeated ruin guardian: a blank cursed grimoire with the chance of {@code cursedGrimoire.ruinDrops}
	 * for this ruin, only when the guardian was cursed (and drops are enabled), else empty. Its only source.
	 */
	public static ItemStack cursedGrimoireDrop(String ruinId, boolean cursed, RandomGenerator random) {
		ScrollConfig cfg = config();
		double chance = cfg.cursedGrimoire.dropChance(ruinId, cursed);
		if (!cfg.dropsEnabled || chance <= 0 || !CursedGrimoireRules.drops(chance, chance >= 1 ? 0 : random.nextDouble())) {
			return ItemStack.EMPTY;
		}
		return GrimoireItems.create(GrimoireRules.Pages.EMPTY, true);
	}

	/**
	 * The command of the newest open rally call addressed to this member (what its [Rejoindre] runs, without the slash), or
	 * null when none is waiting for them (tests).
	 */
	public static @Nullable String pendingRally(UUID member) {
		return RallySpell.pendingCommand(member);
	}

	/**
	 * Why {@code caster} could not sound the rally of the default Ralliement scroll right now (the message they would read,
	 * colors stripped), or null when they could (tests).
	 */
	public static @Nullable String rallyRefusal(ServerPlayer caster) {
		ScrollConfig.ScrollDef def = null;
		for (ScrollConfig.ScrollDef candidate : config().usable()) {
			if (candidate.spell() == Spell.RALLY && (def == null || candidate.id.equals("ralliement"))) {
				def = candidate;
			}
		}
		String failure = def == null ? "scroll.fail.unknown" : RallySpell.check(new SpellContext(caster, def));
		return failure == null ? null : Lang.strip(Lang.raw(SpellFailure.key(failure), SpellFailure.args(failure)));
	}

	/**
	 * A running Grand spectacle: launch points, length and age (ticks), rockets planned, launched, burst, in flight and
	 * skipped (nobody watching, server limit), cursed show, launch points (left to right as the audience sees them).
	 */
	public record ShowStatus(int points, int ticks, int age, int planned, int launched, int exploded, int flying, int skipped, boolean cursed,
			List<Vec3> spots) {
	}

	/** The fireworks show this player is running, or null (tests). */
	public static @Nullable ShowStatus show(UUID caster) {
		FireworksSpell show = FireworksSpell.of(caster);
		return show == null ? null : show.status();
	}

	/** Launch points the default Grand spectacle scroll would get if {@code caster} read it now (tests). */
	public static List<Vec3> showStage(ServerPlayer caster) {
		ScrollConfig.ScrollDef def = null;
		for (ScrollConfig.ScrollDef candidate : config().usable()) {
			if (candidate.spell() == Spell.FIREWORKS && (def == null || candidate.id.equals("grand_spectacle"))) {
				def = candidate;
			}
		}
		return def == null ? List.of() : FireworksSpell.stage(new SpellContext(caster, def)).spots();
	}

	/** Ends the fireworks show of this player at once, its rockets removed (tests). */
	public static void endShow(UUID caster) {
		FireworksSpell show = FireworksSpell.of(caster);
		if (show != null) {
			show.stopNow();
		}
	}

	/** Rockets of the fireworks shows in flight on the server (tests). */
	public static int fireworksInFlight() {
		return FireworksSpell.inFlight();
	}

	/** Meteors of the meteor showers cast by this player that landed since the server started (statistics, tests). */
	public static int meteorImpacts(UUID caster) {
		return MeteorShowerSpell.impacts(caster);
	}

	/** {@link ScrollDrops.Factory}: scrolls of this id, or empty. */
	static ItemStack create(String id, int count) {
		ScrollConfig.ScrollDef def = config().scroll(id);
		return def == null ? ItemStack.EMPTY : ScrollItems.create(def, Math.max(1, count));
	}

	/**
	 * {@link ScrollDrops.Lookup}: rarity and plain name of a defined scroll; obtainable while scrolls and their drops are
	 * enabled (the alchemist writes none otherwise).
	 */
	static ScrollDrops.@Nullable Info info(String id) {
		ScrollConfig cfg = config();
		ScrollConfig.ScrollDef def = cfg.scroll(id);
		return def == null ? null : new ScrollDrops.Info(def.rarity().name(), Lang.strip(Lang.colorize(def.name)), cfg.enabled && cfg.dropsEnabled);
	}

	/**
	 * Gold price of a scroll stack sold by a trade hub merchant of this tier (scrolls.json5: price of its rarity, minimum hub
	 * tier); 0 when it must not be sold there (or the stack is no scroll).
	 */
	public static int tradePrice(ItemStack stack, int tier) {
		if (GrimoireItems.isCursed(stack)) {
			// Never sold: only the cursed guardians drop it.
			return 0;
		}
		if (GrimoireItems.isGrimoire(stack)) {
			ScrollConfig.Grimoire grimoire = config().grimoire;
			return tier >= grimoire.tradeMinTier ? grimoire.tradePrice : 0;
		}
		ScrollConfig.ScrollDef def = ScrollItems.defOf(stack);
		return def == null ? 0 : config().tradePrice(def.rarity(), tier) * Math.max(1, stack.getCount());
	}
}
