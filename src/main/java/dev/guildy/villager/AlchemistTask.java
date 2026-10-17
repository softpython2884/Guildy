package dev.guildy.villager;

import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * "Alchimiste": works at the enchanting table of the territory with the most bookshelves (ties: the nearest), found by
 * an incremental search of the loaded chunks of the territory ({@link TerritoryScan}) repeated every
 * {@code alchemist.scanIntervalSeconds}. Takes plain books (and lapis when {@code alchemist.lapisPerBook} > 0) from its
 * bag, else from the registered storage chests (really removed, see {@link CraftStock}), enchants one book at a time
 * for {@code secondsPerLevel × level} seconds at the vanilla level of the table's third slot, then rolls the vanilla
 * enchanting table outcome for a book, restricted to {@code #minecraft:in_enchanting_table} (Guildy's enchantments are
 * never part of it, and are filtered out anyway). The enchanted book goes to the chest of its storage category (else a
 * fallback, else a deposit chest). At most {@code maxBooksPerDay} books per in-game day.
 *
 * <p>Spell scrolls ({@link AlchemistScrolls}): with chance {@code scrollChance} a finished cycle writes a scroll of the
 * {@code scrolls} table instead (the rarer ones only from {@code epicScrollLevel}), using the same book and lapis plus
 * {@code scrollPaper} sheets of paper, fetched from the storage like the books but optional (without paper: a book).
 * Scrolls count in the daily cap, with their own {@code maxScrollsPerDay}, and are stored like the books.
 */
final class AlchemistTask extends TaskHandler {
	private enum Phase {
		TABLE,
		READY,
		FETCH,
		GO,
		WORK,
		DELIVER
	}

	private static final int CHUNKS_PER_STEP = 8;
	private static final long RETRY_TICKS = 30 * 20L;
	private static final long DEPOSIT_RETRY_TICKS = 60 * 20L;

	private Phase phase = Phase.TABLE;
	private @Nullable TerritoryScan scan;
	private final List<BlockPos> found = new ArrayList<>();
	private @Nullable BlockPos table;
	private int shelves;
	private long nextTableScan;
	private String noTableKey = "villager.status.alchemy.no_table";
	// supplies
	private @Nullable BlockPos fetchFrom;
	private Item fetchItem = Items.BOOK;
	private int fetchAmount;
	private long supplyRetryAt;
	private String supplyKey = "villager.status.alchemy.no_books";
	/** Chests without supplies / full or unreachable chests during this period (retried every 30 s). */
	private final Set<BlockPos> excluded = new HashSet<>();
	private final Set<BlockPos> paperExcluded = new HashSet<>();
	private final Set<BlockPos> full = new HashSet<>();
	private long excludedResetAt;
	/** No paper found in the storage: books only until then. */
	private long paperRetryAt;
	// work
	private int enchantLevel;
	private long progress;
	private long required;
	// delivery
	private @Nullable BlockPos deliverTo;
	/** Storage category of the products taken to {@link #deliverTo}, and whether they are scrolls (status). */
	private String deliverCategory = "";
	private boolean deliverScrolls;
	private long deliverRetryAt;
	private final DepositFlow deposit = new DepositFlow();
	private long depositRetryAt;

	@Override
	void tick(WorkContext ctx) {
		if (!CraftJobs.checkLevel(ctx)) {
			if (phase != Phase.TABLE) {
				phase = Phase.READY;
			}
			enchantLevel = 0;
			progress = 0;
			return;
		}
		if (ctx.now >= excludedResetAt) {
			excluded.clear();
			paperExcluded.clear();
			full.clear();
			excludedResetAt = ctx.now + RETRY_TICKS;
		}
		switch (phase) {
			case TABLE -> tableStep(ctx);
			case READY -> ready(ctx);
			case FETCH -> fetchStep(ctx);
			case GO -> goStep(ctx);
			case WORK -> workStep(ctx);
			case DELIVER -> deliverStep(ctx);
		}
	}

	// ------------------------------------------------------------------ table

	private void tableStep(WorkContext ctx) {
		TerritoryScan running = scan;
		if (running == null) {
			if (ctx.now < nextTableScan) {
				ctx.status(noTableKey);
				return;
			}
			if (ctx.guild.claims().isEmpty()) {
				noTableKey = "villager.status.no_territory";
				nextTableScan = ctx.now + WorkContext.ticks(ctx.config.alchemist.scanIntervalSeconds);
				ctx.status(noTableKey);
				return;
			}
			running = new TerritoryScan(ctx.guild, ctx.villager.blockPosition());
			scan = running;
			found.clear();
		}
		for (int i = 0; i < CHUNKS_PER_STEP; i++) {
			LevelChunk chunk = running.next(ctx);
			if (chunk == null) {
				break;
			}
			for (BlockPos pos : CraftWorld.enchantingTables(chunk)) {
				if (ctx.inTerritory(pos)) {
					found.add(pos);
				}
			}
		}
		if (!running.done()) {
			ctx.status("villager.status.alchemy.scanning", running.position(), running.total());
			return;
		}
		scan = null;
		nextTableScan = ctx.now + WorkContext.ticks(ctx.config.alchemist.scanIntervalSeconds);
		BlockPos best = null;
		int bestShelves = 0;
		double bestDist = 0;
		for (BlockPos pos : found) {
			int count = CraftWorld.bookshelves(ctx.level, pos);
			double dist = ctx.distSq(pos);
			if (best == null || AlchemyRules.compareTables(count, dist, bestShelves, bestDist) < 0) {
				best = pos;
				bestShelves = count;
				bestDist = dist;
			}
		}
		found.clear();
		if (best == null) {
			table = null;
			noTableKey = running.readChunks() == 0 && running.unloadedChunks() > 0
					? "villager.status.craft.unloaded" : "villager.status.alchemy.no_table";
			ctx.status(noTableKey);
			return;
		}
		if (!best.equals(table)) {
			// Another table: a book in progress starts over there.
			enchantLevel = 0;
			progress = 0;
		}
		table = best;
		shelves = bestShelves;
		phase = Phase.READY;
		ready(ctx);
	}

	private boolean tableUsable(WorkContext ctx) {
		BlockPos at = table;
		return at != null && CraftWorld.isTable(ctx.level, at) && ctx.inTerritory(at) && ctx.ticking(at);
	}

	// ------------------------------------------------------------------ decisions

	private void ready(WorkContext ctx) {
		AlchemistConfig cfg = ctx.config.alchemist;
		if (productsInBag(ctx) > 0 && ctx.now >= deliverRetryAt) {
			phase = Phase.DELIVER;
			deliverStep(ctx);
			return;
		}
		if (hasForeign(ctx) && ctx.now >= depositRetryAt) {
			// Items of a former task go to a deposit chest; supplies, enchanted books and scrolls stay in the bag.
			DepositFlow.State state = deposit.tick(ctx, stack -> isSupply(stack, cfg) || isProduct(stack));
			if (state == DepositFlow.State.WORKING) {
				return;
			}
			if (state != DepositFlow.State.DONE) {
				depositRetryAt = ctx.now + DEPOSIT_RETRY_TICKS;
			}
		}
		if (!tableUsable(ctx) || ctx.now >= nextTableScan) {
			if (!tableUsable(ctx)) {
				table = null;
				nextTableScan = 0;
			}
			phase = Phase.TABLE;
			tableStep(ctx);
			return;
		}
		CraftData data = CraftJobs.data(ctx.record);
		long today = today(ctx);
		int made = data.worksToday(today);
		if (AlchemyRules.capReached(made, cfg.maxBooksPerDay)) {
			ctx.status("villager.status.alchemy.cap", made, cfg.maxBooksPerDay);
			return;
		}
		int left = Math.max(1, Math.min(cfg.booksPerTrip, cfg.maxBooksPerDay - made));
		int books = CraftStock.countInBag(ctx, Items.BOOK);
		if (books < 1) {
			supply(ctx, Items.BOOK, left, "villager.status.alchemy.no_books");
			return;
		}
		if (cfg.lapisPerBook > 0 && CraftStock.countInBag(ctx, Items.LAPIS_LAZULI) < cfg.lapisPerBook) {
			supply(ctx, Items.LAPIS_LAZULI, cfg.lapisPerBook * Math.min(left, books), "villager.status.alchemy.no_lapis");
			return;
		}
		if (paperSupply(ctx, cfg, data, today, Math.min(left, books))) {
			return;
		}
		if (!roomForResult(ctx)) {
			if (productsInBag(ctx) > 0) {
				ctx.status("villager.status.alchemy.no_room");
			} else {
				ctx.status("villager.status.alchemy.bag_full");
			}
			return;
		}
		phase = Phase.GO;
		goStep(ctx);
	}

	/** Scrolls may be written at all (a chance and a daily cap above 0). */
	private static boolean scrollsEnabled(AlchemistConfig cfg) {
		return cfg.scrollChance > 0 && cfg.maxScrollsPerDay > 0;
	}

	/**
	 * Paper for the scrolls still possible today, fetched before going to the table when the bag lacks the paper of one
	 * scroll. Optional: without paper in the storage, books are made (looked for again 30 s later).
	 *
	 * @return true while fetching
	 */
	private boolean paperSupply(WorkContext ctx, AlchemistConfig cfg, CraftData data, long today, int worksLeft) {
		if (!scrollsEnabled(cfg) || cfg.scrollPaper <= 0 || ctx.now < paperRetryAt) {
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
		BlockPos from = wanted <= 0 ? null : CraftStock.nearestWith(ctx, Items.PAPER, paperExcluded);
		if (from == null) {
			paperRetryAt = ctx.now + RETRY_TICKS;
			return false;
		}
		fetchFrom = from;
		fetchItem = Items.PAPER;
		fetchAmount = wanted;
		phase = Phase.FETCH;
		fetchStep(ctx);
		return true;
	}

	/** Supplies stay in the bag: plain books, lapis when it is used, paper when scrolls are written. */
	private static boolean isSupply(ItemStack stack, AlchemistConfig cfg) {
		return CraftStock.plain(stack, Items.BOOK) || (cfg.lapisPerBook > 0 && CraftStock.plain(stack, Items.LAPIS_LAZULI))
				|| (scrollsEnabled(cfg) && cfg.scrollPaper > 0 && CraftStock.plain(stack, Items.PAPER));
	}

	/** What the alchemist makes and stores: enchanted books and spell scrolls. */
	private static boolean isProduct(ItemStack stack) {
		return stack.is(Items.ENCHANTED_BOOK) || AlchemistScrolls.isScroll(stack);
	}

	private static boolean hasForeign(WorkContext ctx) {
		AlchemistConfig cfg = ctx.config.alchemist;
		for (ItemStack stack : ctx.record.carried) {
			if (!isSupply(stack, cfg) && !isProduct(stack)) {
				return true;
			}
		}
		return false;
	}

	private static int productsInBag(WorkContext ctx) {
		int count = 0;
		for (ItemStack stack : ctx.record.carried) {
			if (isProduct(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** The enchanted book needs a bag slot: a free one, or the slot of the last book / lapis it uses up. */
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

	private static long today(WorkContext ctx) {
		return AlchemyRules.day(ctx.level.getOverworldClockTime());
	}

	// ------------------------------------------------------------------ supplies

	private void supply(WorkContext ctx, Item item, int amount, String missingKey) {
		if (ctx.now < supplyRetryAt) {
			status(ctx, supplyKey);
			return;
		}
		BlockPos from = CraftStock.nearestWith(ctx, item, excluded);
		if (from == null) {
			supplyKey = missingKey;
			supplyRetryAt = ctx.now + RETRY_TICKS;
			status(ctx, supplyKey);
			return;
		}
		fetchFrom = from;
		fetchItem = item;
		fetchAmount = Math.max(1, amount);
		phase = Phase.FETCH;
		fetchStep(ctx);
	}

	private void status(WorkContext ctx, String key) {
		if (key.equals("villager.status.alchemy.no_lapis")) {
			ctx.status(key, ctx.config.alchemist.lapisPerBook);
		} else {
			ctx.status(key);
		}
	}

	private void fetchStep(WorkContext ctx) {
		BlockPos from = fetchFrom;
		if (from == null) {
			phase = Phase.READY;
			return;
		}
		// Paper is optional: a chest without paper is not skipped for the books.
		Set<BlockPos> skip = fetchItem == Items.PAPER ? paperExcluded : excluded;
		switch (Movement.moveTo(ctx, from, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				skip.add(from);
				fetchFrom = null;
				phase = Phase.READY;
			}
			case MOVING -> ctx.status(fetchItem == Items.BOOK ? "villager.status.alchemy.fetch_books"
					: fetchItem == Items.PAPER ? "villager.status.alchemy.fetch_paper" : "villager.status.alchemy.fetch_lapis");
			case ARRIVED -> {
				int moved = CraftStock.fetch(ctx, from, fetchItem, fetchAmount);
				if (moved > 0) {
					Movement.hold(ctx, from);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
					if (fetchItem == Items.BOOK) {
						// Each trip for books also looks for paper again.
						paperRetryAt = 0;
					}
				} else {
					skip.add(from);
				}
				fetchFrom = null;
				phase = Phase.READY;
			}
		}
	}

	// ------------------------------------------------------------------ work

	private void goStep(WorkContext ctx) {
		if (!tableUsable(ctx)) {
			phase = Phase.READY;
			return;
		}
		BlockPos at = table;
		switch (Movement.moveTo(ctx, at, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				table = null;
				nextTableScan = 0;
				phase = Phase.TABLE;
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

	/** Level of the book: the vanilla third slot for the table's bookshelves right now. */
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
				|| (cfg.lapisPerBook > 0 && CraftStock.countInBag(ctx, Items.LAPIS_LAZULI) < cfg.lapisPerBook)) {
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
			ctx.status("villager.status.alchemy.working", enchantLevel, AlchemyRules.progressPercent(progress, required));
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
	 * End of a cycle: sometimes a spell scroll ({@link AlchemyRules#outcome}), else the vanilla enchanting table outcome for
	 * a book (EnchantmentMenu.getEnchantmentList with a book).
	 */
	private void finish(WorkContext ctx, BlockPos at) {
		AlchemistConfig cfg = ctx.config.alchemist;
		ServerLevel level = ctx.level;
		RandomSource random = level.getRandom();
		int workLevel = enchantLevel;
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
	 * @return false (nothing used) when no scroll is possible or the scroll module creates none: a book is made instead
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

	/** Statistics and the daily count. */
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

	// ------------------------------------------------------------------ delivery

	/**
	 * Takes the products to the chest of their storage category (else a fallback, else a deposit chest), one category per
	 * trip: enchanted books and scrolls usually belong to different categories.
	 */
	private void deliverStep(WorkContext ctx) {
		if (productsInBag(ctx) == 0) {
			deliverTo = null;
			phase = Phase.READY;
			return;
		}
		if (deliverTo == null || ctx.guild.villagers.chestAt(deliverTo) == null) {
			StorageChest chest = null;
			boolean scrolls = false;
			for (ItemStack stack : ctx.record.carried) {
				if (!isProduct(stack)) {
					continue;
				}
				scrolls |= AlchemistScrolls.isScroll(stack);
				chest = Storage.route(ctx, stack, full, true);
				if (chest != null) {
					deliverCategory = Storage.categoryOf(stack);
					deliverScrolls = AlchemistScrolls.isScroll(stack);
					break;
				}
			}
			if (chest == null) {
				deliverTo = null;
				deliverRetryAt = ctx.now + RETRY_TICKS;
				ctx.status(scrolls ? "villager.status.alchemy.no_room_scrolls" : "villager.status.alchemy.no_room");
				phase = Phase.READY;
				return;
			}
			deliverTo = chest.pos;
		}
		BlockPos target = deliverTo;
		String category = deliverCategory;
		Predicate<ItemStack> load = stack -> isProduct(stack) && Storage.categoryOf(stack).equalsIgnoreCase(category);
		switch (Movement.moveTo(ctx, target, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				full.add(target);
				deliverTo = null;
			}
			case MOVING -> ctx.status(deliverScrolls ? "villager.status.alchemy.delivering_scrolls" : "villager.status.alchemy.delivering",
					count(ctx, load));
			case ARRIVED -> {
				Container container = Storage.container(ctx.level, target);
				int moved = container == null ? 0 : Storage.insertCarried(ctx, container, load);
				if (moved > 0) {
					Movement.hold(ctx, target);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
					ctx.status("villager.status.deposited", moved);
				} else {
					full.add(target);
				}
				deliverTo = null;
			}
		}
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
