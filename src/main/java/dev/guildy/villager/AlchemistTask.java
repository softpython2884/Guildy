package dev.guildy.villager;

import dev.guildy.alchemy.BrewBook;
import dev.guildy.alchemy.Brews;
import dev.guildy.alchemy.PotionKey;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * "Alchimiste": three jobs, tried in order from its preferred one (its role: chosen in its menu, or given by the guild's
 * distribution, see {@link Alchemists}), the first possible one wins and the reasons of the others are shown in its menu:
 * <ul>
 * <li><b>Livres</b>: at the enchanting table of the territory with the most bookshelves (ties: the nearest), enchants one
 * plain book at a time for {@code secondsPerLevel × level} seconds at the vanilla level of the table's third slot and rolls
 * the vanilla outcome for a book, restricted to {@code #minecraft:in_enchanting_table} (Guildy's enchantments are filtered
 * out); with chance {@code scrollChance} the book becomes a spell scroll when it has paper ({@link AlchemistScrolls}).</li>
 * <li><b>Parchemins</b>: the same work at the table, always a spell scroll (book + lapis + {@code scrollPaper} paper).</li>
 * <li><b>Potions</b>: at a brewing stand of the territory, brews the product furthest below the guild's stock target
 * (alchemy.json5, {@link PotionPlanner}): from water bottles (or glass bottles filled at water near the stand), one brew per
 * step of {@code secondsPerBrew} seconds with one reagent for up to three bottles, fuel from blaze powder.</li>
 * </ul>
 * Supplies come from its bag, else from the registered storage chests (really removed, see {@link CraftStock}); products
 * go to the chest of their storage category (else a fallback, else a deposit chest). Each job has its daily cap. The
 * tables and stands are found by one incremental scan of the loaded chunks of the territory shared by the guild's
 * alchemists ({@link AlchemyGuild}), repeated every {@code alchemist.scanIntervalSeconds}.
 */
final class AlchemistTask extends TaskHandler {
	private enum Phase {
		SCAN,
		READY,
		FETCH,
		GO,
		WORK,
		BREW_GO,
		BREW,
		DELIVER
	}

	/** Something to fetch from the storage. */
	private record Supply(String id, Predicate<ItemStack> match, @Nullable Item plain, String statusKey, Object[] args) {
	}

	private static final int CHUNKS_PER_STEP = 8;
	private static final int MAX_TABLES = 16;
	private static final long RETRY_TICKS = 30 * 20L;
	private static final long DEPOSIT_RETRY_TICKS = 60 * 20L;

	private Phase phase = Phase.SCAN;
	private @Nullable TerritoryScan scan;
	private final List<BlockPos> foundTables = new ArrayList<>();
	private final List<BlockPos> foundStands = new ArrayList<>();
	/** Job of the cycle in progress. */
	private @Nullable AlchemyJob job;
	// table
	private @Nullable BlockPos table;
	private int shelves;
	// supplies
	private @Nullable Supply fetching;
	private @Nullable BlockPos fetchFrom;
	private int fetchAmount;
	/** Chests without a supply / unreachable during this period (retried every 30 s), per supply. */
	private final Map<String, Set<BlockPos>> excluded = new HashMap<>();
	private final Set<BlockPos> full = new HashSet<>();
	private long excludedResetAt;
	/** Supplies found nowhere in the storage: not looked for again until then. */
	private final Map<String, Long> missingUntil = new HashMap<>();
	// table work
	private int enchantLevel;
	private long progress;
	private long required;
	// brewing
	private BrewBook.@Nullable Plan candidate;
	private BrewBook.@Nullable Plan plan;
	private int stepIndex;
	private int batch;
	private @Nullable BlockPos stand;
	// delivery
	private @Nullable BlockPos deliverTo;
	/** Storage category of the items taken to {@link #deliverTo}, and their kind for the status. */
	private String deliverCategory = "";
	private String deliverKind = "books";
	/** The trip takes surplus supplies back to the storage to make room in the bag. */
	private boolean stowing;
	private long deliverRetryAt;
	private final DepositFlow deposit = new DepositFlow();
	private long depositRetryAt;

	@Override
	void tick(WorkContext ctx) {
		if (!CraftJobs.checkLevel(ctx)) {
			if (phase != Phase.SCAN) {
				phase = Phase.READY;
			}
			enchantLevel = 0;
			progress = 0;
			abortBrew(ctx);
			return;
		}
		if (ctx.now >= excludedResetAt) {
			excluded.clear();
			full.clear();
			excludedResetAt = ctx.now + RETRY_TICKS;
		}
		switch (phase) {
			case SCAN -> scanStep(ctx);
			case READY -> ready(ctx);
			case FETCH -> fetchStep(ctx);
			case GO -> goStep(ctx);
			case WORK -> workStep(ctx);
			case BREW_GO -> brewGoStep(ctx);
			case BREW -> brewStep(ctx);
			case DELIVER -> deliverStep(ctx);
		}
	}

	@Override
	void stop(WorkContext ctx) {
		AlchemyGuild.of(ctx.guild).leave(ctx.record.uuid);
	}

	private AlchemyGuild shared(WorkContext ctx) {
		return AlchemyGuild.of(ctx.guild);
	}

	private static long today(WorkContext ctx) {
		return AlchemyRules.day(ctx.level.getOverworldClockTime());
	}

	// ------------------------------------------------------------------ workshops (shared scan)

	private void scanStep(WorkContext ctx) {
		AlchemyGuild shared = shared(ctx);
		long interval = WorkContext.ticks(ctx.config.alchemist.scanIntervalSeconds);
		TerritoryScan running = scan;
		if (running == null) {
			if (!shared.scanDue(ctx.now, interval)) {
				phase = Phase.READY;
				ready(ctx);
				return;
			}
			if (ctx.guild.claims().isEmpty()) {
				ctx.status("villager.status.no_territory");
				return;
			}
			if (!shared.claimScan(ctx.record.uuid, ctx.now)) {
				// Another alchemist of the guild is scanning: its results serve everyone.
				if (shared.workshopsKnown()) {
					phase = Phase.READY;
					ready(ctx);
				} else {
					ctx.status("villager.status.alchemy.scan_wait");
				}
				return;
			}
			running = new TerritoryScan(ctx.guild, ctx.villager.blockPosition());
			scan = running;
			foundTables.clear();
			foundStands.clear();
		}
		for (int i = 0; i < CHUNKS_PER_STEP; i++) {
			LevelChunk chunk = running.next(ctx);
			if (chunk == null) {
				break;
			}
			for (BlockPos pos : CraftWorld.enchantingTables(chunk)) {
				if (ctx.inTerritory(pos)) {
					foundTables.add(pos);
				}
			}
			for (BlockPos pos : CraftWorld.brewingStands(chunk)) {
				if (ctx.inTerritory(pos)) {
					foundStands.add(pos);
				}
			}
		}
		if (!running.done()) {
			shared.claimScan(ctx.record.uuid, ctx.now);
			ctx.status("villager.status.alchemy.scan", running.position(), running.total());
			return;
		}
		shared.publish(foundTables, foundStands, running.readChunks() == 0 && running.unloadedChunks() > 0, ctx.now);
		scan = null;
		foundTables.clear();
		foundStands.clear();
		phase = Phase.READY;
		ready(ctx);
	}

	/** The table with the most bookshelves (ties: the nearest), usable now. */
	private @Nullable BlockPos bestTable(WorkContext ctx, AlchemyGuild shared) {
		BlockPos best = null;
		int bestShelves = 0;
		double bestDist = 0;
		int seen = 0;
		for (BlockPos pos : shared.tables) {
			if (seen++ >= MAX_TABLES) {
				break;
			}
			if (!CraftWorld.isTable(ctx.level, pos) || !ctx.inTerritory(pos) || !ctx.ticking(pos)) {
				continue;
			}
			int count = CraftWorld.bookshelves(ctx.level, pos);
			double dist = ctx.distSq(pos);
			if (best == null || AlchemyRules.compareTables(count, dist, bestShelves, bestDist) < 0) {
				best = pos;
				bestShelves = count;
				bestDist = dist;
			}
		}
		return best;
	}

	private boolean anyStand(WorkContext ctx, AlchemyGuild shared) {
		for (BlockPos pos : shared.stands) {
			if (CraftWorld.isStand(ctx.level, pos) && ctx.inTerritory(pos) && ctx.ticking(pos)) {
				return true;
			}
		}
		return false;
	}

	private boolean tableUsable(WorkContext ctx) {
		BlockPos at = table;
		return at != null && CraftWorld.isTable(ctx.level, at) && ctx.inTerritory(at) && ctx.ticking(at);
	}

	private boolean standUsable(WorkContext ctx) {
		BlockPos at = stand;
		return at != null && CraftWorld.isStand(ctx.level, at) && ctx.inTerritory(at) && ctx.ticking(at);
	}

	// ------------------------------------------------------------------ decisions

	private void ready(WorkContext ctx) {
		AlchemistConfig cfg = ctx.config.alchemist;
		AlchemyGuild shared = shared(ctx);
		if (productsInBag(ctx) > 0 && ctx.now >= deliverRetryAt) {
			stowing = false;
			phase = Phase.DELIVER;
			deliverStep(ctx);
			return;
		}
		if (hasForeign(ctx) && ctx.now >= depositRetryAt) {
			// Items of a former task go to a deposit chest; supplies and products of the alchemist stay in the bag.
			DepositFlow.State state = deposit.tick(ctx, AlchemistTask::isAlchemyItem);
			if (state == DepositFlow.State.WORKING) {
				return;
			}
			if (state != DepositFlow.State.DONE) {
				depositRetryAt = ctx.now + DEPOSIT_RETRY_TICKS;
			}
		}
		long interval = WorkContext.ticks(cfg.scanIntervalSeconds);
		if (shared.scanDue(ctx.now, interval) && (shared.claimScan(ctx.record.uuid, ctx.now) || !shared.workshopsKnown())) {
			shared.releaseScan(ctx.record.uuid);
			phase = Phase.SCAN;
			scanStep(ctx);
			return;
		}
		if (plan != null) {
			// A batch in progress (back from fetching a supply): it goes on.
			job = AlchemyJob.POTIONS;
			brewReady(ctx);
			return;
		}
		AlchemyJob chosen = decide(ctx, shared);
		if (chosen == null) {
			return;
		}
		job = chosen;
		if (chosen.atTable()) {
			tableReady(ctx, chosen);
		} else {
			brewReady(ctx);
		}
	}

	/** Preferred job, then the others in order: the first possible one, the reasons of the jobs before it noted. */
	private @Nullable AlchemyJob decide(WorkContext ctx, AlchemyGuild shared) {
		AlchemistConfig cfg = ctx.config.alchemist;
		CraftData data = CraftJobs.data(ctx.record);
		AlchemyJob manual = AlchemyJob.parse(data.role);
		AlchemyJob preferred = manual != null ? manual : Alchemists.assigned(ctx, shared);
		List<AlchemyJob> order = manual != null && !cfg.manualRoleFallback ? List.of(manual) : AlchemyRoles.tryOrder(preferred, cfg.order());
		Map<AlchemyJob, String> reasons = new LinkedHashMap<>();
		candidate = null;
		AlchemyJob chosen = AlchemyRoles.firstPossible(order, candidateJob -> {
			String reason = blocked(ctx, shared, candidateJob);
			if (reason != null) {
				reasons.put(candidateJob, reason);
			}
			return reason;
		});
		shared.notes.put(ctx.record.uuid, new AlchemyGuild.Note(preferred, manual != null, chosen, reasons));
		if (chosen == null) {
			List<String> parts = new ArrayList<>();
			for (Map.Entry<AlchemyJob, String> entry : reasons.entrySet()) {
				parts.add(AlchemistLang.reason(entry.getKey(), entry.getValue()));
			}
			ctx.status("villager.status.alchemy.nothing", String.join(AlchemistLang.separator(), parts));
		}
		return chosen;
	}

	/** Why a job is impossible right now (French text), null when it is possible. */
	private @Nullable String blocked(WorkContext ctx, AlchemyGuild shared, AlchemyJob candidateJob) {
		AlchemistConfig cfg = ctx.config.alchemist;
		CraftData data = CraftJobs.data(ctx.record);
		long today = today(ctx);
		AlchemyGuild.Stock stock = shared.stock(ctx.level, ctx.guild);
		switch (candidateJob) {
			case BOOKS, SCROLLS -> {
				boolean scrolls = candidateJob == AlchemyJob.SCROLLS;
				if (scrolls && !scrollJobEnabled(cfg)) {
					return AlchemistLang.block("disabled");
				}
				BlockPos best = bestTable(ctx, shared);
				if (best == null) {
					return AlchemistLang.block(shared.unloadedOnly ? "unloaded" : "no_table");
				}
				int made = scrolls ? data.scrollsToday(today) : data.booksToday(today);
				int max = scrolls ? cfg.maxScrollsPerDay : cfg.maxBooksPerDay;
				if (made >= max) {
					return AlchemistLang.block("cap", made, max);
				}
				if (scrolls && !AlchemyRules.anyScroll(cfg.scrolls, AlchemistScrolls::rarity,
						AlchemyRules.maxLevel(CraftWorld.bookshelves(ctx.level, best)), cfg.epicScrollLevel)) {
					return AlchemistLang.block("no_scroll");
				}
				if (CraftStock.countInBag(ctx, Items.BOOK) + stock.plain(Items.BOOK) < 1) {
					return AlchemistLang.block("no_books");
				}
				if (cfg.lapisPerBook > 0 && CraftStock.countInBag(ctx, Items.LAPIS_LAZULI) + stock.plain(Items.LAPIS_LAZULI) < cfg.lapisPerBook) {
					return AlchemistLang.block("no_lapis", cfg.lapisPerBook);
				}
				if (scrolls && CraftStock.countInBag(ctx, Items.PAPER) + stock.plain(Items.PAPER) < cfg.scrollPaper) {
					return AlchemistLang.block("no_paper", cfg.scrollPaper);
				}
				return null;
			}
			case POTIONS -> {
				if (cfg.maxPotionsPerDay <= 0) {
					return AlchemistLang.block("disabled");
				}
				if (!anyStand(ctx, shared)) {
					return AlchemistLang.block(shared.unloadedOnly ? "unloaded" : "no_stand");
				}
				int made = data.potionsToday(today);
				if (made >= cfg.maxPotionsPerDay) {
					return AlchemistLang.block("cap", made, cfg.maxPotionsPerDay);
				}
				PotionPlanner.View view = view(ctx, shared, stock);
				BrewBook.Choice choice = PotionPlanner.choose(ctx.level, ctx.guild, shared, view, ctx.record.uuid);
				if (choice.verdict() == BrewBook.Verdict.STOCK_FULL) {
					return AlchemistLang.block("stock_full");
				}
				if (choice.verdict() == BrewBook.Verdict.MISSING) {
					String[] missing = PotionPlanner.missing(view, choice.wanted());
					return AlchemistLang.block(missing[0], missing[1]);
				}
				candidate = choice.plan();
				return null;
			}
		}
		return null;
	}

	/** Supplies this alchemist can use for potions: its bag, the storage, its fuel. */
	private PotionPlanner.View view(WorkContext ctx, AlchemyGuild shared, AlchemyGuild.Stock stock) {
		boolean fillable = ctx.config.alchemist.fillBottles && shared.anyWater(ctx.level, ctx.config.alchemist.waterRadius);
		return PotionPlanner.view(ctx.guild, stock, List.of(ctx.record.carried), CraftJobs.data(ctx.record).brewFuel, fillable);
	}

	/** Scrolls may be written at all (a daily cap above 0 and a table of scrolls). */
	private static boolean scrollJobEnabled(AlchemistConfig cfg) {
		return cfg.maxScrollsPerDay > 0 && cfg.scrolls != null && !cfg.scrolls.isEmpty();
	}

	/** The books job sometimes writes a scroll (a chance and a daily cap above 0). */
	private static boolean scrollsEnabled(AlchemistConfig cfg) {
		return cfg.scrollChance > 0 && cfg.maxScrollsPerDay > 0;
	}

	// ------------------------------------------------------------------ bag

	/** Plain books, lapis and paper: supplies of the enchanting table. */
	private static boolean isTableSupply(ItemStack stack) {
		return CraftStock.plain(stack, Items.BOOK) || CraftStock.plain(stack, Items.LAPIS_LAZULI) || CraftStock.plain(stack, Items.PAPER);
	}

	/** Water and glass bottles, fuel and reagents of the brewing steps. */
	private static boolean isBrewSupply(ItemStack stack) {
		if (Brews.isWater(stack) || CraftStock.plain(stack, Items.GLASS_BOTTLE)) {
			return true;
		}
		if (!stack.getComponentsPatch().isEmpty()) {
			return false;
		}
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (id.equals(VillagerModule.config().alchemist.brewFuel)) {
			return true;
		}
		for (BrewBook.Step step : Brews.book().steps()) {
			if (step.reagent().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** What the alchemist makes and stores: enchanted books, scrolls, potions and elixirs (not the bottles being brewed). */
	private boolean isProduct(ItemStack stack) {
		if (stack.is(Items.ENCHANTED_BOOK) || AlchemistScrolls.isScroll(stack)) {
			return true;
		}
		if (!Brews.isBrew(stack) || Brews.isWater(stack)) {
			return false;
		}
		BrewBook.Plan current = plan;
		if (current != null && stepIndex < current.steps().size()) {
			String key = Brews.keyOf(stack);
			return key == null || !key.equals(current.steps().get(stepIndex).from());
		}
		return true;
	}

	/** Everything the alchemist keeps in its bag (anything else goes to a deposit chest). */
	private static boolean isAlchemyItem(ItemStack stack) {
		return stack.is(Items.ENCHANTED_BOOK) || AlchemistScrolls.isScroll(stack) || Brews.isBrew(stack) || isTableSupply(stack)
				|| isBrewSupply(stack);
	}

	private static boolean hasForeign(WorkContext ctx) {
		for (ItemStack stack : ctx.record.carried) {
			if (!isAlchemyItem(stack)) {
				return true;
			}
		}
		return false;
	}

	private int productsInBag(WorkContext ctx) {
		int count = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (isProduct(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Supplies the current job does not use: taken back to the storage when the bag needs room. */
	private boolean isSurplus(WorkContext ctx, ItemStack stack) {
		if (isProduct(stack) || !isAlchemyItem(stack)) {
			return false;
		}
		if (job != null && job.atTable()) {
			return !isTableSupply(stack);
		}
		BrewBook.Plan current = plan;
		if (current == null || stepIndex >= current.steps().size()) {
			return isTableSupply(stack);
		}
		// The bottles of the next brew stay (water bottles, and glass bottles to fill, before the first one).
		String from = current.steps().get(stepIndex).from();
		boolean water = from.equals(PotionKey.WATER.text());
		String key = Brews.keyOf(stack);
		if (key != null) {
			return !key.equals(from) && !(water && Brews.isWater(stack));
		}
		if (CraftStock.plain(stack, Items.GLASS_BOTTLE)) {
			return !water;
		}
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (id.equals(ctx.config.alchemist.brewFuel)) {
			return false;
		}
		for (int i = stepIndex; i < current.steps().size(); i++) {
			if (current.steps().get(i).reagent().equals(id)) {
				return false;
			}
		}
		return true;
	}

	private boolean hasSurplus(WorkContext ctx) {
		for (ItemStack stack : ctx.record.carried) {
			if (isSurplus(ctx, stack)) {
				return true;
			}
		}
		return false;
	}

	/** The bag has no room for the next supply: surplus goes back to the storage, else the alchemist waits. */
	private void needRoom(WorkContext ctx) {
		if (hasSurplus(ctx) && ctx.now >= deliverRetryAt) {
			stowing = true;
			deliverTo = null;
			phase = Phase.DELIVER;
			deliverStep(ctx);
			return;
		}
		ctx.status(productsInBag(ctx) > 0 ? "villager.status.alchemy.no_room" : "villager.status.alchemy.bag_full");
	}

	// ------------------------------------------------------------------ supplies

	private static Supply plainSupply(String id, Item item, String statusKey, Object... args) {
		return new Supply(id, stack -> CraftStock.plain(stack, item), item, statusKey, args);
	}

	/**
	 * Goes to fetch a supply from the nearest chest that gives it.
	 *
	 * @return false (nothing started) when no chest of the storage has it now
	 */
	private boolean supply(WorkContext ctx, Supply supply, int amount) {
		if (ctx.now < missingUntil.getOrDefault(supply.id(), 0L)) {
			return false;
		}
		Set<BlockPos> skip = excluded.computeIfAbsent(supply.id(), k -> new HashSet<>());
		BlockPos from = supply.plain() != null ? CraftStock.nearestWith(ctx, supply.plain(), skip) : CraftStock.nearestWith(ctx, supply.match(), skip);
		if (from == null) {
			missingUntil.put(supply.id(), ctx.now + RETRY_TICKS);
			shared(ctx).invalidateStock();
			return false;
		}
		fetching = supply;
		fetchFrom = from;
		fetchAmount = Math.max(1, amount);
		phase = Phase.FETCH;
		fetchStep(ctx);
		return true;
	}

	private void fetchStep(WorkContext ctx) {
		BlockPos from = fetchFrom;
		Supply supply = fetching;
		if (from == null || supply == null) {
			phase = Phase.READY;
			return;
		}
		Set<BlockPos> skip = excluded.computeIfAbsent(supply.id(), k -> new HashSet<>());
		switch (Movement.moveTo(ctx, from, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				skip.add(from);
				fetchFrom = null;
				phase = Phase.READY;
			}
			case MOVING -> ctx.status(supply.statusKey(), supply.args());
			case ARRIVED -> {
				int moved = supply.plain() != null ? CraftStock.fetch(ctx, from, supply.plain(), fetchAmount)
						: CraftStock.fetch(ctx, from, supply.match(), fetchAmount);
				if (moved > 0) {
					Movement.hold(ctx, from);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
					shared(ctx).invalidateStock();
					if (supply.plain() == Items.BOOK) {
						// Each trip for books also looks for paper again.
						missingUntil.remove("paper");
					}
				} else {
					skip.add(from);
				}
				fetchFrom = null;
				fetching = null;
				phase = Phase.READY;
			}
		}
	}

	// ------------------------------------------------------------------ enchanting table (books, scrolls)

	private void tableReady(WorkContext ctx, AlchemyJob tableJob) {
		AlchemistConfig cfg = ctx.config.alchemist;
		AlchemyGuild shared = shared(ctx);
		BlockPos best = bestTable(ctx, shared);
		if (best == null) {
			shared.invalidateWorkshops();
			ctx.status("villager.status.alchemy.no_table");
			return;
		}
		if (!best.equals(table)) {
			// Another table: a work in progress starts over there.
			enchantLevel = 0;
			progress = 0;
		}
		table = best;
		shelves = CraftWorld.bookshelves(ctx.level, best);
		CraftData data = CraftJobs.data(ctx.record);
		long today = today(ctx);
		boolean scrolls = tableJob == AlchemyJob.SCROLLS;
		int made = scrolls ? data.scrollsToday(today) : data.booksToday(today);
		int max = scrolls ? cfg.maxScrollsPerDay : cfg.maxBooksPerDay;
		int left = Math.max(1, Math.min(cfg.booksPerTrip, max - made));
		int books = CraftStock.countInBag(ctx, Items.BOOK);
		if (books < 1) {
			startSupply(ctx, plainSupply("books", Items.BOOK, "villager.status.alchemy.fetch_books"), left, "no_books");
			return;
		}
		if (cfg.lapisPerBook > 0 && CraftStock.countInBag(ctx, Items.LAPIS_LAZULI) < cfg.lapisPerBook) {
			startSupply(ctx, plainSupply("lapis", Items.LAPIS_LAZULI, "villager.status.alchemy.fetch_lapis"), cfg.lapisPerBook * Math.min(left, books),
					"no_lapis");
			return;
		}
		if (scrolls && CraftStock.countInBag(ctx, Items.PAPER) < cfg.scrollPaper) {
			startSupply(ctx, plainSupply("paper", Items.PAPER, "villager.status.alchemy.fetch_paper"),
					cfg.scrollPaper * Math.min(left, books) - CraftStock.countInBag(ctx, Items.PAPER), "no_paper");
			return;
		}
		if (!scrolls && paperSupply(ctx, cfg, data, today, Math.min(left, books))) {
			return;
		}
		if (!roomForResult(ctx)) {
			needRoom(ctx);
			return;
		}
		phase = Phase.GO;
		goStep(ctx);
	}

	/** Fetches a mandatory supply; when the bag is full, makes room first; when no chest has it, back to deciding. */
	private void startSupply(WorkContext ctx, Supply supply, int amount, String missingReason) {
		ItemStack sample = supply.plain() != null ? new ItemStack(supply.plain()) : ItemStack.EMPTY;
		if (!sample.isEmpty() && CraftStock.roomFor(ctx, sample) <= 0) {
			needRoom(ctx);
			return;
		}
		if (!supply(ctx, supply, amount)) {
			// Gone from the storage since the inventory: the next decision knows it.
			ctx.status("villager.status.alchemy.missing", AlchemistLang.block(missingReason, ctx.config.alchemist.lapisPerBook));
		}
	}

	/**
	 * Paper for the scrolls the books job may still write today, fetched before going to the table when the bag lacks the
	 * paper of one scroll. Optional: without paper in the storage, books are made (looked for again 30 s later).
	 *
	 * @return true while fetching
	 */
	private boolean paperSupply(WorkContext ctx, AlchemistConfig cfg, CraftData data, long today, int worksLeft) {
		if (!scrollsEnabled(cfg) || cfg.scrollPaper <= 0) {
			return false;
		}
		int scrollsLeft = cfg.maxScrollsPerDay - data.scrollsToday(today);
		int inBag = CraftStock.countInBag(ctx, Items.PAPER);
		if (scrollsLeft <= 0 || inBag >= cfg.scrollPaper
				|| !AlchemyRules.anyScroll(cfg.scrolls, AlchemistScrolls::rarity, AlchemyRules.maxLevel(shelves), cfg.epicScrollLevel)) {
			return false;
		}
		// A new stack of paper must leave a bag slot for the result.
		if (inBag == 0 && ctx.record.carried.size() + 2 > ctx.config.work.carryStacks) {
			return false;
		}
		int wanted = AlchemyRules.paperWanted(cfg.scrollPaper, scrollsLeft, worksLeft, inBag);
		return wanted > 0 && supply(ctx, plainSupply("paper", Items.PAPER, "villager.status.alchemy.fetch_paper"), wanted);
	}

	/** The result needs a bag slot: a free one, or the slot of the last book / lapis it uses up. */
	private static boolean roomForResult(WorkContext ctx) {
		if (ctx.record.carried.size() < ctx.config.work.carryStacks) {
			return true;
		}
		int lapis = ctx.config.alchemist.lapisPerBook;
		for (ItemStack stack : ctx.record.carried) {
			if ((CraftStock.plain(stack, Items.BOOK) && stack.getCount() == 1)
					|| (lapis > 0 && CraftStock.plain(stack, Items.LAPIS_LAZULI) && stack.getCount() == lapis)) {
				return true;
			}
		}
		return false;
	}

	private void goStep(WorkContext ctx) {
		if (!tableUsable(ctx)) {
			phase = Phase.READY;
			return;
		}
		BlockPos at = table;
		switch (Movement.moveTo(ctx, at, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				table = null;
				shared(ctx).invalidateWorkshops();
				phase = Phase.READY;
			}
			case MOVING -> ctx.status("villager.status.alchemy.going", shelves, AlchemyRules.maxLevel(shelves));
			case ARRIVED -> {
				if (enchantLevel <= 0) {
					start(ctx, at);
				}
				phase = Phase.WORK;
				workStep(ctx);
			}
		}
	}

	/** Level of the work: the vanilla third slot for the table's bookshelves right now. */
	private void start(WorkContext ctx, BlockPos at) {
		shelves = CraftWorld.bookshelves(ctx.level, at);
		RandomSource random = ctx.level.getRandom();
		int power = AlchemyRules.power(shelves);
		enchantLevel = AlchemyRules.enchantLevel(random.nextInt(8), random.nextInt(power + 1), shelves);
		required = ctx.workTicks(AlchemyRules.workSeconds(enchantLevel, ctx.config.alchemist.secondsPerLevel));
		progress = 0;
	}

	private void workStep(WorkContext ctx) {
		AlchemistConfig cfg = ctx.config.alchemist;
		if (!tableUsable(ctx) || CraftStock.countInBag(ctx, Items.BOOK) < 1
				|| (cfg.lapisPerBook > 0 && CraftStock.countInBag(ctx, Items.LAPIS_LAZULI) < cfg.lapisPerBook)
				|| (job == AlchemyJob.SCROLLS && CraftStock.countInBag(ctx, Items.PAPER) < cfg.scrollPaper)) {
			enchantLevel = 0;
			progress = 0;
			phase = Phase.READY;
			return;
		}
		BlockPos at = table;
		if (!Movement.near(ctx, at, 3.5, 3.0)) {
			phase = Phase.GO;
			goStep(ctx);
			return;
		}
		Movement.hold(ctx, at);
		progress += 20;
		effects(ctx, at);
		if (progress < required) {
			ctx.status(job == AlchemyJob.SCROLLS ? "villager.status.alchemy.writing" : "villager.status.alchemy.working", enchantLevel,
					AlchemyRules.progressPercent(progress, required));
			return;
		}
		finish(ctx, at);
	}

	/** Vanilla look: ENCHANT particles flowing from the bookshelves to the table, page sounds, the arm moving. */
	private void effects(WorkContext ctx, BlockPos at) {
		ServerLevel level = ctx.level;
		RandomSource random = level.getRandom();
		double x = at.getX() + 0.5;
		double y = at.getY() + 2.0;
		double z = at.getZ() + 0.5;
		int sent = 0;
		for (BlockPos offset : CraftWorld.bookshelfOffsets(level, at)) {
			if (sent >= 8 || random.nextInt(3) != 0) {
				continue;
			}
			// Count 0: the "distances" are the particle's motion, as in EnchantingTableBlock.animateTick.
			level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 0, offset.getX() + random.nextFloat() - 0.5,
					offset.getY() - random.nextFloat() - 1.0, offset.getZ() + random.nextFloat() - 0.5, 1.0);
			sent++;
		}
		if (sent == 0) {
			level.sendParticles(ParticleTypes.ENCHANT, x, at.getY() + 1.4, z, 4, 0.4, 0.3, 0.4, 0.6);
		}
		long second = progress / 20;
		if (second % 4 == 1) {
			level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7f, 0.9f + random.nextFloat() * 0.2f);
		}
		if (second % 3 == 0) {
			WorldUtil.swing(ctx.villager);
		}
	}

	/**
	 * End of a cycle at the table. Scrolls job: a spell scroll. Books job: sometimes a spell scroll
	 * ({@link AlchemyRules#outcome}), else the vanilla enchanting table outcome for a book (EnchantmentMenu.getEnchantmentList
	 * with a book).
	 */
	private void finish(WorkContext ctx, BlockPos at) {
		AlchemistConfig cfg = ctx.config.alchemist;
		ServerLevel level = ctx.level;
		RandomSource random = level.getRandom();
		int workLevel = enchantLevel;
		if (job == AlchemyJob.SCROLLS) {
			if (!writeScroll(ctx, at, workLevel)) {
				enchantLevel = 0;
				progress = 0;
				phase = Phase.READY;
			}
			return;
		}
		AlchemyRules.Outcome outcome = AlchemyRules.Outcome.BOOK;
		if (scrollsEnabled(cfg)) {
			CraftData data = CraftJobs.data(ctx.record);
			boolean possible = AlchemyRules.anyScroll(cfg.scrolls, AlchemistScrolls::rarity, workLevel, cfg.epicScrollLevel);
			outcome = AlchemyRules.outcome(random.nextDouble(), cfg.scrollChance, possible, data.scrollsToday(today(ctx)), cfg.maxScrollsPerDay,
					CraftStock.countInBag(ctx, Items.PAPER), cfg.scrollPaper);
		}
		if (outcome == AlchemyRules.Outcome.SCROLL && writeScroll(ctx, at, workLevel)) {
			return;
		}
		Optional<HolderSet.Named<Enchantment>> tag = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.get(EnchantmentTags.IN_ENCHANTING_TABLE);
		List<EnchantmentInstance> list = tag.isEmpty() ? List.of()
				: EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.BOOK), workLevel,
				tag.get().stream().filter(holder -> !isGuildy(holder)));
		List<EnchantmentInstance> chosen = new ArrayList<>(list);
		if (chosen.size() > 1) {
			chosen.remove(random.nextInt(chosen.size()));
		}
		enchantLevel = 0;
		progress = 0;
		if (chosen.isEmpty()) {
			ctx.status("villager.status.alchemy.failed");
			phase = Phase.READY;
			return;
		}
		if (CraftStock.countInBag(ctx, Items.BOOK) < 1 || !CraftStock.takeFromBag(ctx, Items.LAPIS_LAZULI, cfg.lapisPerBook)
				|| !CraftStock.takeFromBag(ctx, Items.BOOK, 1)) {
			phase = Phase.READY;
			return;
		}
		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		for (EnchantmentInstance instance : chosen) {
			result.enchant(instance.enchantment(), instance.level());
		}
		ctx.record.carried.add(result);
		record(ctx, chosen);
		level.playSound(null, at, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, random.nextFloat() * 0.1f + 0.9f);
		level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 1.0);
		WorldUtil.happy(level, ctx.villager);
		String book = CraftNames.book(result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
		ctx.status(outcome == AlchemyRules.Outcome.BOOK_NO_PAPER ? "villager.status.alchemy.no_paper" : "villager.status.alchemy.done", book);
		deliverRetryAt = 0;
		phase = Phase.DELIVER;
	}

	/**
	 * A spell scroll of the table, allowed at the cycle's level: uses the book, the lapis and the paper (all or nothing).
	 *
	 * @return false (nothing used) when no scroll is possible or the scroll module creates none
	 */
	private boolean writeScroll(WorkContext ctx, BlockPos at, int workLevel) {
		AlchemistConfig cfg = ctx.config.alchemist;
		String id = AlchemyRules.pickScroll(cfg.scrolls, AlchemistScrolls::rarity, workLevel, cfg.epicScrollLevel, ctx.level.getRandom().nextDouble());
		ItemStack scroll = id == null ? ItemStack.EMPTY : AlchemistScrolls.create(id);
		if (scroll.isEmpty() || CraftStock.countInBag(ctx, Items.BOOK) < 1 || CraftStock.countInBag(ctx, Items.LAPIS_LAZULI) < cfg.lapisPerBook
				|| CraftStock.countInBag(ctx, Items.PAPER) < cfg.scrollPaper) {
			return false;
		}
		CraftStock.takeFromBag(ctx, Items.BOOK, 1);
		CraftStock.takeFromBag(ctx, Items.LAPIS_LAZULI, cfg.lapisPerBook);
		CraftStock.takeFromBag(ctx, Items.PAPER, cfg.scrollPaper);
		enchantLevel = 0;
		progress = 0;
		ctx.record.carried.add(scroll);
		CraftData data = CraftJobs.data(ctx.record);
		data.countWork(today(ctx), true);
		data.scrolls++;
		data.lastScroll = id;
		GuildManager.markDirty();
		AlchemistScrolls.effects(ctx.level, at);
		WorldUtil.happy(ctx.level, ctx.villager);
		ctx.status("villager.status.alchemy.scroll_done", AlchemistScrolls.name(id));
		deliverRetryAt = 0;
		phase = Phase.DELIVER;
		return true;
	}

	private static boolean isGuildy(Holder<Enchantment> holder) {
		return holder.unwrapKey().map(key -> "guildy".equals(key.identifier().getNamespace())).orElse(true);
	}

	/** Statistics and the daily count of a book. */
	private static void record(WorkContext ctx, List<EnchantmentInstance> chosen) {
		CraftData data = CraftJobs.data(ctx.record);
		data.countWork(today(ctx), false);
		data.books++;
		List<String> parts = new ArrayList<>();
		for (EnchantmentInstance instance : chosen) {
			String id = instance.enchantment().unwrapKey().map(key -> key.identifier().toString()).orElse("?");
			parts.add(id + "=" + instance.level());
			int score = instance.enchantment().value().getMinCost(instance.level());
			if (score > data.bestScore) {
				data.bestScore = score;
				data.bestEnchant = id;
				data.bestLevel = instance.level();
			}
		}
		data.lastBook = String.join(";", parts);
		GuildManager.markDirty();
	}

	// ------------------------------------------------------------------ brewing stand (potions, elixirs)

	/** Plans the batch (once), gathers its bottles, reagents and fuel, then goes to the stand. */
	private void brewReady(WorkContext ctx) {
		AlchemistConfig cfg = ctx.config.alchemist;
		AlchemyGuild shared = shared(ctx);
		CraftData data = CraftJobs.data(ctx.record);
		if (plan == null) {
			BrewBook.Plan chosen = candidate;
			if (chosen == null) {
				phase = Phase.READY;
				return;
			}
			int capLeft = cfg.maxPotionsPerDay - data.potionsToday(today(ctx));
			PotionPlanner.View view = view(ctx, shared, shared.stock(ctx.level, ctx.guild));
			int bottles = Math.min(Math.min(cfg.bottlesPerBrew, capLeft), PotionPlanner.startBottles(view, chosen.start()));
			if (bottles <= 0) {
				phase = Phase.READY;
				return;
			}
			plan = chosen;
			stepIndex = 0;
			batch = bottles;
			shared.reserve(ctx.record.uuid, chosen.product(), bottles);
		}
		BrewBook.Plan current = plan;
		if (!standUsable(ctx)) {
			stand = shared.stand(ctx);
			if (stand == null) {
				abortBrew(ctx);
				shared.invalidateWorkshops();
				ctx.status("villager.status.alchemy.missing", AlchemistLang.block("no_stand"));
				return;
			}
		} else {
			shared.lease(stand, ctx.record.uuid, ctx.now);
		}
		// 1. the bottles of the first step still to do
		String from = current.steps().get(stepIndex).from();
		int have = bottlesInBag(ctx, from);
		boolean water = from.equals(PotionKey.WATER.text());
		boolean fillable = water && cfg.fillBottles && shared.waterNear(ctx.level, stand, cfg.waterRadius) != null;
		int glass = fillable ? CraftStock.countInBag(ctx, Items.GLASS_BOTTLE) : 0;
		if (have + glass < batch) {
			int wanted = batch - have - glass;
			if (needsRoomFor(ctx, wanted)) {
				if (hasSurplus(ctx) && ctx.now >= deliverRetryAt) {
					needRoom(ctx);
					return;
				}
			} else {
				Supply bottles = water
						? new Supply("water", Brews::isWater, null, "villager.status.alchemy.fetch_water", new Object[0])
						: new Supply("bottle:" + from, stack -> from.equals(Brews.keyOf(stack)), null, "villager.status.alchemy.fetch_bottles",
						new Object[]{Brews.name(from)});
				if (supply(ctx, bottles, wanted)) {
					return;
				}
				if (fillable && supply(ctx, plainSupply("glass", Items.GLASS_BOTTLE, "villager.status.alchemy.fetch_glass"), wanted)) {
					return;
				}
			}
			if (have + glass <= 0) {
				abortBrew(ctx);
				ctx.status("villager.status.alchemy.missing", AlchemistLang.block("no_bottles"));
				return;
			}
			// Fewer bottles than planned: a smaller batch.
			batch = have + glass;
			shared.reserve(ctx.record.uuid, current.product(), batch);
		}
		// 2. the reagents of the remaining steps
		Map<String, Integer> reagents = BrewBook.reagents(current.steps().subList(stepIndex, current.steps().size()));
		for (Map.Entry<String, Integer> entry : reagents.entrySet()) {
			Item item = Brews.item(entry.getKey());
			if (item == null) {
				abortBrew(ctx);
				return;
			}
			int missing = entry.getValue() - CraftStock.countInBag(ctx, item);
			if (missing > 0) {
				if (CraftStock.roomFor(ctx, new ItemStack(item)) <= 0) {
					needRoom(ctx);
					return;
				}
				if (!supply(ctx, plainSupply("reagent:" + entry.getKey(), item, "villager.status.alchemy.fetch_reagent",
						AlchemistLang.itemName(entry.getKey())), missing)) {
					abortBrew(ctx);
					ctx.status("villager.status.alchemy.missing", AlchemistLang.block("no_reagents", Brews.name(current.product())));
				}
				return;
			}
		}
		// 3. fuel for the remaining brews
		int brews = current.steps().size() - stepIndex;
		Item fuel = Brews.item(cfg.brewFuel);
		if (fuel != null && data.brewFuel + CraftStock.countInBag(ctx, fuel) * cfg.brewsPerFuel < brews) {
			if (CraftStock.roomFor(ctx, new ItemStack(fuel)) <= 0) {
				needRoom(ctx);
				return;
			}
			int units = (brews - data.brewFuel + cfg.brewsPerFuel - 1) / cfg.brewsPerFuel - CraftStock.countInBag(ctx, fuel);
			if (!supply(ctx, plainSupply("fuel", fuel, "villager.status.alchemy.fetch_fuel", AlchemistLang.itemName(cfg.brewFuel)), Math.max(1, units))) {
				abortBrew(ctx);
				ctx.status("villager.status.alchemy.missing", AlchemistLang.block("no_fuel", AlchemistLang.itemName(cfg.brewFuel)));
			}
			return;
		}
		phase = Phase.BREW_GO;
		brewGoStep(ctx);
	}

	/** True when the bag has no free slot for {@code bottles} more bottles (one per slot). */
	private static boolean needsRoomFor(WorkContext ctx, int bottles) {
		return bottles > 0 && ctx.record.carried.size() >= ctx.config.work.carryStacks;
	}

	private static int bottlesInBag(WorkContext ctx, String key) {
		if (key.equals(PotionKey.WATER.text())) {
			return CraftStock.countInBag(ctx, Brews::isWater);
		}
		return CraftStock.countInBag(ctx, stack -> key.equals(Brews.keyOf(stack)));
	}

	private void brewGoStep(WorkContext ctx) {
		BrewBook.Plan current = plan;
		if (current == null || !standUsable(ctx)) {
			if (current != null) {
				stand = null;
			}
			phase = Phase.READY;
			return;
		}
		BlockPos at = stand;
		shared(ctx).lease(at, ctx.record.uuid, ctx.now);
		switch (Movement.moveTo(ctx, at, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				stand = null;
				shared(ctx).invalidateWorkshops();
				phase = Phase.READY;
			}
			case MOVING -> ctx.status("villager.status.alchemy.brew_going", Brews.name(current.product()), batch);
			case ARRIVED -> {
				if (stepIndex == 0 && current.start().equals(PotionKey.WATER.text()) && !fill(ctx, at)) {
					return;
				}
				required = ctx.workTicks(ctx.config.alchemist.secondsPerBrew);
				progress = 0;
				phase = Phase.BREW;
				brewStep(ctx);
			}
		}
	}

	/**
	 * Fills the glass bottles of the batch at the water near the stand (a cauldron loses a level per bottle).
	 *
	 * @return false when the batch cannot start (no bottle at all)
	 */
	private boolean fill(WorkContext ctx, BlockPos at) {
		AlchemistConfig cfg = ctx.config.alchemist;
		int water = bottlesInBag(ctx, PotionKey.WATER.text());
		int glass = CraftStock.countInBag(ctx, Items.GLASS_BOTTLE);
		if (water >= batch || glass <= 0) {
			if (water <= 0) {
				abortBrew(ctx);
				phase = Phase.READY;
				return false;
			}
			batch = Math.min(batch, water);
			return true;
		}
		BlockPos source = cfg.fillBottles ? shared(ctx).waterNear(ctx.level, at, cfg.waterRadius) : null;
		int filled = 0;
		while (source != null && water + filled < batch && filled < glass && CraftWorld.drawWater(ctx.level, source)) {
			if (!CraftStock.takeFromBag(ctx, Items.GLASS_BOTTLE, 1)) {
				break;
			}
			CraftStock.addStack(ctx, Brews.water());
			filled++;
		}
		if (filled > 0) {
			ctx.level.playSound(null, source, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
			ctx.level.sendParticles(ParticleTypes.SPLASH, source.getX() + 0.5, source.getY() + 1.0, source.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
			WorldUtil.swing(ctx.villager);
			ctx.status("villager.status.alchemy.filling", filled);
		}
		int ready = water + filled;
		if (ready <= 0) {
			abortBrew(ctx);
			ctx.status("villager.status.alchemy.no_water");
			phase = Phase.READY;
			return false;
		}
		batch = Math.min(batch, ready);
		shared(ctx).reserve(ctx.record.uuid, plan == null ? "" : plan.product(), batch);
		return true;
	}

	private void brewStep(WorkContext ctx) {
		BrewBook.Plan current = plan;
		AlchemistConfig cfg = ctx.config.alchemist;
		if (current == null || stepIndex >= current.steps().size()) {
			phase = Phase.READY;
			return;
		}
		if (!standUsable(ctx)) {
			stand = null;
			phase = Phase.READY;
			return;
		}
		BlockPos at = stand;
		if (!Movement.near(ctx, at, 3.5, 3.0)) {
			phase = Phase.BREW_GO;
			brewGoStep(ctx);
			return;
		}
		BrewBook.Step step = current.steps().get(stepIndex);
		Item reagent = Brews.item(step.reagent());
		Item fuel = Brews.item(cfg.brewFuel);
		CraftData data = CraftJobs.data(ctx.record);
		int bottles = Math.min(batch, bottlesInBag(ctx, step.from()));
		if (bottles <= 0) {
			abortBrew(ctx);
			phase = Phase.READY;
			return;
		}
		if (reagent == null || CraftStock.countInBag(ctx, reagent) < 1 || (data.brewFuel <= 0 && (fuel == null || CraftStock.countInBag(ctx, fuel) < 1))) {
			// Back to gathering what is missing (the batch goes on).
			phase = Phase.READY;
			return;
		}
		Movement.hold(ctx, at);
		shared(ctx).lease(at, ctx.record.uuid, ctx.now);
		progress += 20;
		brewEffects(ctx, at, step);
		if (progress < required) {
			ctx.status("villager.status.alchemy.brewing", Brews.name(current.product()), AlchemistLang.itemName(step.reagent()), stepIndex + 1,
					current.steps().size(), AlchemyRules.progressPercent(progress, required));
			return;
		}
		// One brew: the reagent and one fuel use, the bottles become the step's product.
		if (data.brewFuel <= 0) {
			CraftStock.takeFromBag(ctx, fuel, 1);
			data.brewFuel += cfg.brewsPerFuel;
		}
		data.brewFuel--;
		CraftStock.takeFromBag(ctx, reagent, 1);
		transform(ctx, step.from(), step.to(), bottles);
		GuildManager.markDirty();
		ctx.level.levelEvent(1035, at, 0);
		int color = 0xFF000000 | Brews.color(step.to());
		ctx.level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color), at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5,
				16, 0.25, 0.3, 0.25, 1.0);
		WorldUtil.swing(ctx.villager);
		stepIndex++;
		progress = 0;
		required = ctx.workTicks(cfg.secondsPerBrew);
		if (stepIndex >= current.steps().size()) {
			finishBatch(ctx, at);
		}
	}

	/** Replaces {@code count} bottles of {@code from} in the bag with bottles of {@code to}. */
	private static void transform(WorkContext ctx, String from, String to, int count) {
		int left = count;
		boolean water = from.equals(PotionKey.WATER.text());
		List<ItemStack> carried = ctx.record.carried;
		for (int i = 0; i < carried.size() && left > 0; i++) {
			ItemStack stack = carried.get(i);
			boolean match = water ? Brews.isWater(stack) : from.equals(Brews.keyOf(stack));
			if (!match) {
				continue;
			}
			int take = Math.min(left, stack.getCount());
			ItemStack made = Brews.create(to);
			if (made.isEmpty()) {
				return;
			}
			if (take == stack.getCount()) {
				made.setCount(take);
				carried.set(i, made);
			} else {
				stack.shrink(take);
				made.setCount(take);
				carried.add(made);
			}
			left -= take;
		}
	}

	/** Bubbles in the colour of the brew, the stand's smoke, the arm stirring. */
	private void brewEffects(WorkContext ctx, BlockPos at, BrewBook.Step step) {
		ServerLevel level = ctx.level;
		RandomSource random = level.getRandom();
		double x = at.getX() + 0.5;
		double y = at.getY() + 0.8;
		double z = at.getZ() + 0.5;
		int color = 0xFF000000 | Brews.color(step.to());
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color), x, y, z, 3, 0.2, 0.15, 0.2, 1.0);
		level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.1, z, 2, 0.15, 0.05, 0.15, 0.01);
		if (step.to().startsWith(PotionKey.Form.ELIXIR.prefix())) {
			level.sendParticles(ParticleTypes.WITCH, x, y + 0.3, z, 3, 0.25, 0.2, 0.25, 0.02);
		}
		long second = progress / 20;
		if (second % 3 == 0) {
			WorldUtil.swing(ctx.villager);
		}
		if (second % 5 == 2) {
			level.playSound(null, at, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.25f, 1.6f + random.nextFloat() * 0.3f);
		}
	}

	private void finishBatch(WorkContext ctx, BlockPos at) {
		BrewBook.Plan current = plan;
		if (current == null) {
			return;
		}
		int made = Math.min(batch, bottlesInBag(ctx, current.product()));
		CraftData data = CraftJobs.data(ctx.record);
		data.countPotions(today(ctx), made);
		data.lastPotion = current.product();
		GuildManager.markDirty();
		AlchemyGuild shared = shared(ctx);
		shared.release(ctx.record.uuid);
		shared.invalidateStock();
		ctx.level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
		WorldUtil.happy(ctx.level, ctx.villager);
		ctx.status("villager.status.alchemy.brewed", made, Brews.name(current.product()));
		plan = null;
		candidate = null;
		stepIndex = 0;
		batch = 0;
		deliverRetryAt = 0;
		phase = Phase.DELIVER;
	}

	/** Gives the batch up (its bottles stay in the bag, delivered as products or used by a later batch). */
	private void abortBrew(WorkContext ctx) {
		if (plan != null) {
			shared(ctx).release(ctx.record.uuid);
		}
		plan = null;
		candidate = null;
		stepIndex = 0;
		batch = 0;
	}

	// ------------------------------------------------------------------ delivery

	/**
	 * Takes the products (or, when making room, the surplus supplies) to the chest of their storage category (else a
	 * fallback, else a deposit chest), one category per trip.
	 */
	private void deliverStep(WorkContext ctx) {
		Predicate<ItemStack> carried = stowing ? stack -> isSurplus(ctx, stack) : this::isProduct;
		if (count(ctx, carried) == 0) {
			deliverTo = null;
			stowing = false;
			phase = Phase.READY;
			return;
		}
		if (deliverTo == null || ctx.guild.villagers.chestAt(deliverTo) == null) {
			StorageChest chest = null;
			String kind = "books";
			for (ItemStack stack : ctx.record.carried) {
				if (!carried.test(stack)) {
					continue;
				}
				kind = kind(stack);
				chest = Storage.route(ctx, stack, full, true);
				if (chest != null) {
					deliverCategory = Storage.categoryOf(stack);
					deliverKind = kind;
					break;
				}
			}
			if (chest == null) {
				deliverTo = null;
				deliverRetryAt = ctx.now + RETRY_TICKS;
				stowing = false;
				ctx.status(switch (kind) {
					case "scrolls" -> "villager.status.alchemy.no_room_scrolls";
					case "potions" -> "villager.status.alchemy.no_room_potions";
					default -> "villager.status.alchemy.no_room";
				});
				phase = Phase.READY;
				return;
			}
			deliverTo = chest.pos;
		}
		BlockPos target = deliverTo;
		String category = deliverCategory;
		Predicate<ItemStack> load = stack -> carried.test(stack) && Storage.categoryOf(stack).equalsIgnoreCase(category);
		switch (Movement.moveTo(ctx, target, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				full.add(target);
				deliverTo = null;
			}
			case MOVING -> ctx.status(stowing ? "villager.status.alchemy.stowing" : switch (deliverKind) {
				case "scrolls" -> "villager.status.alchemy.delivering_scrolls";
				case "potions" -> "villager.status.alchemy.delivering_potions";
				default -> "villager.status.alchemy.delivering";
			}, count(ctx, load));
			case ARRIVED -> {
				Container container = Storage.container(ctx.level, target);
				int moved = container == null ? 0 : Storage.insertCarried(ctx, container, load);
				if (moved > 0) {
					Movement.hold(ctx, target);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
					ctx.status("villager.status.deposited", moved);
					shared(ctx).invalidateStock();
				} else {
					full.add(target);
				}
				deliverTo = null;
			}
		}
	}

	private static String kind(ItemStack stack) {
		if (AlchemistScrolls.isScroll(stack)) {
			return "scrolls";
		}
		return Brews.isBrew(stack) ? "potions" : "books";
	}

	private static int count(WorkContext ctx, Predicate<ItemStack> filter) {
		int count = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (filter.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
