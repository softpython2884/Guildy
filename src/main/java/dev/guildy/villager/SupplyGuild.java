package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What the gatherers of a guild share (not persisted, rebuilt after a restart): the crafting tables and furnaces of the
 * territory (one incremental scan for all of them), a snapshot of the storage (plain items the workers may take), the
 * analysis of the wants ({@link Procurement#wanted}: for each wanted item, the best route right now and, when there is
 * none, why), the items without any site in reach (looked for again later), the storage inputs planned by each gatherer
 * (never counted twice) and the leases of the pits and furnaces.
 */
final class SupplyGuild {
	private static final Map<UUID, SupplyGuild> GUILDS = new HashMap<>();
	private static final int SCAN_CHUNKS_PER_STEP = 8;
	private static final long LEASE_TICKS = 30 * 20L;
	private static final Predicate<BlockState> TABLE = state -> state.is(Blocks.CRAFTING_TABLE);
	private static final Predicate<BlockState> FURNACE = state -> state.is(Blocks.FURNACE);
	private static final Predicate<BlockState> CUTTER = state -> state.is(Blocks.STONECUTTER);
	private static final Predicate<BlockState> WATER = state -> state.is(Blocks.WATER) && state.getFluidState().isSource();
	/** Water sources remembered at most (one per chunk). */
	private static final int MAX_WATER = 16;

	/** How a wanted item can be obtained right now. */
	enum Route {
		/** Crafted / smelted from the storage. */
		CRAFT,
		/** Gathered itself (pit or tree). */
		GATHER,
		/** An ingredient is gathered first (logs for the stairs...). */
		GATHER_FOR,
		/** A crafting table or a furnace is missing. */
		STATION,
		/** Nothing makes or gives it: players must deposit it. */
		MISSING
	}

	/** One wanted item and its route. */
	record Option(Item item, int want, Route route, @Nullable Item gather, int gatherAmount, int planned, SupplySources.@Nullable Method method,
			String reason) {
		boolean doable() {
			return route == Route.CRAFT || route == Route.GATHER || route == Route.GATHER_FOR;
		}

		int rank() {
			return switch (route) {
				case CRAFT -> 0;
				case GATHER -> 1;
				case GATHER_FOR -> 2;
				default -> 9;
			};
		}
	}

	/** The analysis of the wants: options in work order, and the reason shown when none can be done. */
	record Analysis(List<Option> options, String reason, boolean empty) {
	}

	/** What a gatherer goes to do. */
	record Job(Route route, Item target, int amount, SupplyPlanner.@Nullable Plan plan, @Nullable Item gather, int gatherAmount,
			SupplySources.@Nullable Method method) {
		boolean crafts() {
			return route == Route.CRAFT;
		}
	}

	private record Lease(UUID owner, long until) {
	}

	// stations
	List<BlockPos> tables = List.of();
	List<BlockPos> furnaces = List.of();
	List<BlockPos> cutters = List.of();
	List<BlockPos> waters = List.of();
	private long stationsAt = -1;
	private @Nullable TerritoryScan scan;
	private final List<BlockPos> foundTables = new ArrayList<>();
	private final List<BlockPos> foundFurnaces = new ArrayList<>();
	private final List<BlockPos> foundCutters = new ArrayList<>();
	private final List<BlockPos> foundWaters = new ArrayList<>();
	// storage
	private @Nullable Map<Item, Integer> stock;
	private long stockAt = -1;
	/** Storage inputs planned by each gatherer and not fetched yet. */
	private final Map<UUID, Map<Item, Integer>> takes = new HashMap<>();
	// analysis
	private @Nullable Analysis analysis;
	private long analysisAt = -1;
	/** Items whose last site search found nothing: not gathered until then. */
	private final Map<Item, Long> noSiteUntil = new HashMap<>();
	private final Map<Item, SupplySources.Method> noSiteMethod = new HashMap<>();
	// leases
	private final Map<Integer, Lease> pitLeases = new HashMap<>();
	private final Map<BlockPos, Lease> stationLeases = new HashMap<>();

	private SupplyGuild() {
	}

	static SupplyGuild of(Guild guild) {
		return GUILDS.computeIfAbsent(guild.id, id -> new SupplyGuild());
	}

	static void clear() {
		GUILDS.clear();
	}

	/** Configs reloaded: every analysis is done again. */
	static void invalidateAll() {
		for (SupplyGuild shared : GUILDS.values()) {
			shared.analysis = null;
			shared.stationsAt = -1;
		}
	}

	// ================================================================ stations

	boolean stationsKnown() {
		return stationsAt >= 0;
	}

	/** Progress of the running scan, {chunks read, chunks}, or null. */
	int @Nullable [] scanProgress() {
		TerritoryScan running = scan;
		return running == null ? null : new int[]{running.position(), running.total()};
	}

	/** Runs a few chunks of the workstation scan when one is due. */
	void scanStations(WorkContext ctx) {
		long interval = WorkContext.ticks(Supply.config().crafting.stationScanSeconds);
		TerritoryScan running = scan;
		if (running == null) {
			if (stationsAt >= 0 && ctx.now - stationsAt < interval) {
				return;
			}
			BlockPos from = ctx.guild.heart != null ? ctx.guild.heart : ctx.villager.blockPosition();
			running = new TerritoryScan(ctx.guild, from);
			scan = running;
			foundTables.clear();
			foundFurnaces.clear();
			foundCutters.clear();
			foundWaters.clear();
		}
		for (int i = 0; i < SCAN_CHUNKS_PER_STEP; i++) {
			LevelChunk chunk = running.next(ctx);
			if (chunk == null) {
				break;
			}
			collect(chunk, TABLE, foundTables, ctx, 64);
			collect(chunk, FURNACE, foundFurnaces, ctx, 64);
			collect(chunk, CUTTER, foundCutters, ctx, 64);
			if (foundWaters.size() < MAX_WATER) {
				collect(chunk, WATER, foundWaters, ctx, foundWaters.size() + 1);
			}
		}
		if (running.done()) {
			tables = List.copyOf(foundTables);
			furnaces = List.copyOf(foundFurnaces);
			cutters = List.copyOf(foundCutters);
			waters = List.copyOf(foundWaters);
			stationsAt = ctx.now;
			scan = null;
			analysis = null;
		}
	}

	/** A table or furnace disappeared: the next decision scans again. */
	void invalidateStations() {
		stationsAt = -1;
		analysis = null;
	}

	/** Blocks of a chunk matching {@code test} (sections without one skipped through their palette), up to {@code max} in all. */
	private static void collect(LevelChunk chunk, Predicate<BlockState> test, List<BlockPos> into, WorkContext ctx, int max) {
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length && into.size() < max; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(test)) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16 && into.size() < max; y++) {
				for (int x = 0; x < 16 && into.size() < max; x++) {
					for (int z = 0; z < 16 && into.size() < max; z++) {
						if (test.test(section.getBlockState(x, y, z))) {
							BlockPos pos = new BlockPos(x0 + x, baseY + y, z0 + z);
							if (ctx.inTerritory(pos)) {
								into.add(pos);
							}
						}
					}
				}
			}
		}
	}

	/** Positions known for a workstation. */
	private List<BlockPos> list(SupplyPlanner.Station station) {
		return switch (station) {
			case TABLE -> tables;
			case FURNACE -> furnaces;
			case STONECUTTER -> cutters;
			case WATER -> waters;
			case HAND -> List.of();
		};
	}

	/** Block test of a workstation. */
	static Predicate<BlockState> test(SupplyPlanner.Station station) {
		return switch (station) {
			case TABLE -> TABLE;
			case FURNACE -> FURNACE;
			case STONECUTTER -> CUTTER;
			case WATER -> WATER;
			case HAND -> state -> true;
		};
	}

	/**
	 * A workstation for this gatherer: a free one nearest to it, else the nearest (shared), leased for a while; null when
	 * the territory has none usable now.
	 */
	@Nullable BlockPos station(WorkContext ctx, SupplyPlanner.Station station) {
		Predicate<BlockState> test = test(station);
		BlockPos free = null;
		BlockPos any = null;
		double freeDist = Double.MAX_VALUE;
		double anyDist = Double.MAX_VALUE;
		for (BlockPos pos : list(station)) {
			if (!usable(ctx, pos, test)) {
				continue;
			}
			double dist = ctx.distSq(pos);
			if (dist < anyDist) {
				any = pos;
				anyDist = dist;
			}
			Lease lease = stationLeases.get(pos);
			boolean taken = lease != null && !lease.owner().equals(ctx.record.uuid) && ctx.now < lease.until();
			if (!taken && dist < freeDist) {
				free = pos;
				freeDist = dist;
			}
		}
		BlockPos chosen = free != null ? free : any;
		if (chosen != null) {
			stationLeases.put(chosen, new Lease(ctx.record.uuid, ctx.now + LEASE_TICKS));
		}
		return chosen;
	}

	static boolean usable(WorkContext ctx, BlockPos pos, Predicate<BlockState> test) {
		return ctx.loaded(pos) && ctx.ticking(pos) && ctx.inTerritory(pos) && test.test(ctx.level.getBlockState(pos));
	}

	/** True when a workstation of that kind still stands in the territory (or the scan has not run yet). */
	boolean has(WorkContext ctx, SupplyPlanner.Station station) {
		if (station == SupplyPlanner.Station.HAND || !stationsKnown()) {
			return true;
		}
		Predicate<BlockState> test = test(station);
		for (BlockPos pos : list(station)) {
			if (usable(ctx, pos, test)) {
				return true;
			}
		}
		return false;
	}

	/** Workstations usable by the plans now: the known ones, water also when it may be simulated. */
	java.util.EnumSet<SupplyPlanner.Station> stations(WorkContext ctx) {
		java.util.EnumSet<SupplyPlanner.Station> stations = java.util.EnumSet.of(SupplyPlanner.Station.HAND);
		for (SupplyPlanner.Station station : SupplyPlanner.Station.values()) {
			if (has(ctx, station)) {
				stations.add(station);
			}
		}
		if (Supply.config().transforms.simulateWater) {
			stations.add(SupplyPlanner.Station.WATER);
		}
		return stations;
	}

	/** Kinds of recipes allowed by supply.json5. */
	static java.util.EnumSet<SupplyPlanner.Kind> kinds() {
		SupplyConfig cfg = Supply.config();
		java.util.EnumSet<SupplyPlanner.Kind> kinds = java.util.EnumSet.noneOf(SupplyPlanner.Kind.class);
		if (cfg.crafting.enabled) {
			kinds.add(SupplyPlanner.Kind.CRAFT);
			if (cfg.crafting.stonecutter) {
				kinds.add(SupplyPlanner.Kind.CUT);
			}
		}
		if (cfg.smelting.enabled) {
			kinds.add(SupplyPlanner.Kind.SMELT);
		}
		if (cfg.transforms.enabled) {
			kinds.add(SupplyPlanner.Kind.TRANSFORM);
		}
		return kinds;
	}

	// ================================================================ pits

	/** True when another gatherer works in this pit. */
	boolean pitTaken(int pit, UUID gatherer, long now) {
		Lease lease = pitLeases.get(pit);
		return lease != null && !lease.owner().equals(gatherer) && now < lease.until();
	}

	void leasePit(int pit, UUID gatherer, long now) {
		pitLeases.put(pit, new Lease(gatherer, now + LEASE_TICKS));
	}

	// ================================================================ storage

	/** Plain items the workers may take from the registered storage (at most {@code decisionSeconds} old). */
	Map<Item, Integer> stock(ServerLevel level, Guild guild) {
		long now = Scheduler.ticks();
		if (stock == null || stockAt < 0 || now - stockAt >= WorkContext.ticks(Supply.config().decisionSeconds)) {
			stock = inventory(level, guild);
			stockAt = now;
		}
		return stock;
	}

	void invalidateStock() {
		stockAt = -1;
		analysis = null;
	}

	private static Map<Item, Integer> inventory(ServerLevel level, Guild guild) {
		Map<Item, Integer> found = new HashMap<>();
		Set<BlockPos> seen = new HashSet<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.pos == null || seen.contains(chest.pos) || !Storage.usable(level, guild, chest)) {
				continue;
			}
			seen.addAll(Storage.halves(level, chest.pos));
			Container container = Storage.container(level, chest.pos);
			if (container == null) {
				continue;
			}
			Storage.Spare spare = Storage.Spare.of(chest, container);
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty() || !Storage.gives(chest, stack)) {
					continue;
				}
				int givable = spare.take(stack, stack.getCount());
				if (givable > 0) {
					found.merge(stack.getItem(), givable, Integer::sum);
				}
			}
		}
		return found;
	}

	/** Storage inputs a gatherer plans to fetch (null / empty: none). */
	void planTakes(UUID gatherer, @Nullable Map<Item, Integer> items) {
		if (items == null || items.isEmpty()) {
			takes.remove(gatherer);
		} else {
			takes.put(gatherer, new HashMap<>(items));
		}
	}

	// ================================================================ sites

	/** No site was found for this item: not gathered for a while. */
	void noSite(Item item, SupplySources.Method method, long now) {
		noSiteUntil.put(item, now + WorkContext.ticks(Supply.config().gathering.noSiteRetrySeconds));
		noSiteMethod.put(item, method);
		analysis = null;
	}

	boolean siteKnownMissing(Item item, long now) {
		Long until = noSiteUntil.get(item);
		if (until == null) {
			return false;
		}
		if (now >= until) {
			noSiteUntil.remove(item);
			noSiteMethod.remove(item);
			return false;
		}
		return true;
	}

	// ================================================================ analysis

	/** The analysis of the wants, at most {@code decisionSeconds} old (also runs the workstation scan). */
	Analysis analysis(WorkContext ctx) {
		scanStations(ctx);
		long now = ctx.now;
		if (analysis == null || analysisAt < 0 || now - analysisAt >= WorkContext.ticks(Supply.config().decisionSeconds)) {
			analysis = analyse(ctx);
			analysisAt = now;
		}
		return analysis;
	}

	/** Wants changed (an item delivered...): analysed again at the next decision. */
	void invalidateAnalysis() {
		analysis = null;
	}

	private Analysis analyse(WorkContext ctx) {
		SupplyConfig cfg = Supply.config();
		Map<Item, Integer> wants = Procurement.wanted(ctx.guild);
		wants.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
		if (wants.isEmpty()) {
			return new Analysis(List.of(), Lang.raw("villager.gather.nothing"), true);
		}
		List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(wants.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		if (sorted.size() > cfg.maxWantedItems) {
			sorted = sorted.subList(0, cfg.maxWantedItems);
		}
		Planning planning = planning(ctx, wants, null, true);
		Set<String> never = SupplyBook.expandItems(cfg.neverFetch);
		List<Option> options = new ArrayList<>();
		for (Map.Entry<Item, Integer> entry : sorted) {
			options.add(option(ctx, planning, entry.getKey(), entry.getValue(), never));
		}
		options.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank()) : Integer.compare(b.want(), a.want()));
		return new Analysis(List.copyOf(options), reason(options), false);
	}

	/** Everything the planner needs for this guild now. */
	record Planning(SupplyBook book, SupplySources sources, SupplyPlanner.Context context, SupplyPlanner.Context allStations, Set<String> gatherable) {
	}

	/**
	 * Planning context: storage snapshot, reserved part (wanted items, items kept by the requesters, inputs planned by
	 * the other gatherers), gatherable items, fuels, workstations.
	 */
	Planning planning(WorkContext ctx, Map<Item, Integer> wants, @Nullable UUID self, boolean stationsAsKnown) {
		SupplyConfig cfg = Supply.config();
		SupplyBook book = SupplyBook.of(ctx.level);
		SupplySources sources = SupplyBook.sources(ctx.level);
		Map<String, Integer> stockIds = new HashMap<>();
		stock(ctx.level, ctx.guild).forEach((item, count) -> stockIds.put(ItemUtil.id(item), count));
		Map<String, Integer> reserved = new HashMap<>();
		wants.keySet().forEach(item -> reserved.put(ItemUtil.id(item), Integer.MAX_VALUE));
		Procurement.kept(ctx.guild).forEach((item, count) -> reserved.merge(ItemUtil.id(item), count, SupplyGuild::saturatedAdd));
		takes.forEach((gatherer, items) -> {
			if (!gatherer.equals(self)) {
				items.forEach((item, count) -> reserved.merge(ItemUtil.id(item), count, SupplyGuild::saturatedAdd));
			}
		});
		Set<String> gatherable = new HashSet<>();
		Set<String> never = SupplyBook.expandItems(cfg.neverFetch);
		for (String item : sources.items()) {
			Item value = ItemUtil.item(item);
			if (value != null && !never.contains(item) && gatherableNow(ctx, sources, value)) {
				gatherable.add(item);
			}
		}
		List<String> fuels = new ArrayList<>(SupplyBook.expandItems(cfg.smelting.fuels));
		java.util.EnumSet<SupplyPlanner.Kind> kinds = kinds();
		SupplyPlanner.Settings settings = new SupplyPlanner.Settings(cfg.crafting.maxDepth, kinds,
				stationsAsKnown ? stations(ctx) : java.util.EnumSet.allOf(SupplyPlanner.Station.class));
		SupplyPlanner.Settings all = new SupplyPlanner.Settings(cfg.crafting.maxDepth, kinds, java.util.EnumSet.allOf(SupplyPlanner.Station.class));
		Map<String, Integer> neverReserved = new HashMap<>(reserved);
		never.forEach(item -> neverReserved.put(item, Integer.MAX_VALUE));
		SupplyPlanner.Context context = new SupplyPlanner.Context(book, stockIds, neverReserved, gatherable, fuels, settings);
		SupplyPlanner.Context allContext = new SupplyPlanner.Context(book, stockIds, neverReserved, gatherable, fuels, all);
		return new Planning(book, sources, context, allContext, gatherable);
	}

	private static int saturatedAdd(int a, int b) {
		long sum = (long) a + b;
		return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}

	/** True when a natural source of the item can be worked now (a gathering way enabled, no failed search pending). */
	boolean gatherableNow(WorkContext ctx, SupplySources sources, Item item) {
		SupplyConfig.Gathering cfg = Supply.config().gathering;
		if (!cfg.enabled || siteKnownMissing(item, ctx.now)) {
			return false;
		}
		String id = ItemUtil.id(item);
		boolean dig = !sources.blocks(id, SupplySources.Method.DIG).isEmpty() && (cfg.outside || (cfg.mineZones && SupplyPits.hasMineZone(ctx)));
		boolean tree = !sources.blocks(id, SupplySources.Method.TREE).isEmpty()
				&& ((cfg.outside && cfg.outsideTrees) || (cfg.forestZones && SupplyTrees.hasForestZone(ctx)));
		return dig || tree;
	}

	private Option option(WorkContext ctx, Planning planning, Item item, int want, Set<String> never) {
		String id = ItemUtil.id(item);
		SupplyConfig cfg = Supply.config();
		int bag = ctx.config.work.carryStacks;
		if (never.contains(id)) {
			return new Option(item, want, Route.MISSING, null, 0, 0, null, missingText(item, want, Map.of()));
		}
		int cap = Math.min(want, capacity(planning.book(), id, bag));
		SupplyPlanner.Plan fromStock = SupplyPlanner.bestFromStock(planning.context(), id, cap, bag);
		if (fromStock != null) {
			return new Option(item, want, Route.CRAFT, null, 0, fromStock.made(), null, "");
		}
		if (planning.gatherable().contains(id)) {
			int amount = Math.min(want, capacity(planning.book(), id, bag));
			return new Option(item, want, Route.GATHER, item, amount, amount, bestMethod(ctx, planning.sources(), id), "");
		}
		SupplyPlanner.Plan plan = SupplyPlanner.plan(planning.context(), id, Math.max(1, cap));
		String leaf = plan.possible() ? plan.firstGather() : null;
		Item leafItem = leaf == null ? null : ItemUtil.item(leaf);
		if (leafItem != null) {
			int needed = plan.gather().get(leaf);
			int amount = Math.min(needed, capacity(planning.book(), leaf, bag));
			// The target units this gathering pays for (a full bag of logs may pay for only part of the stairs wanted).
			int planned = needed <= amount ? cap : Math.max(1, (int) ((long) cap * amount / needed));
			return new Option(item, want, Route.GATHER_FOR, leafItem, amount, planned, bestMethod(ctx, planning.sources(), leaf), "");
		}
		SupplyPlanner.Plan anyStation = SupplyPlanner.plan(planning.allStations(), id, Math.max(1, cap));
		if (anyStation.possible()) {
			for (SupplyPlanner.Station station : anyStation.stations()) {
				if (!planning.context().settings().stations().contains(station)) {
					String key = "villager.gather.no_" + station.name().toLowerCase(java.util.Locale.ROOT);
					return new Option(item, want, Route.STATION, null, 0, 0, null, Lang.raw(key, SupplyLang.name(item)));
				}
			}
		}
		return new Option(item, want, Route.MISSING, null, 0, 0, null, missingText(item, want, plan.missing()));
	}

	/** Units of an item that fit in an empty bag. */
	static int capacity(SupplyPlanner.Book book, String item, int bagStacks) {
		return Math.max(1, book.maxStack(item) * Math.max(1, bagStacks));
	}

	private SupplySources.Method bestMethod(WorkContext ctx, SupplySources sources, String id) {
		SupplyConfig.Gathering cfg = Supply.config().gathering;
		boolean tree = !sources.blocks(id, SupplySources.Method.TREE).isEmpty()
				&& ((cfg.outside && cfg.outsideTrees) || (cfg.forestZones && SupplyTrees.hasForestZone(ctx)));
		return tree ? SupplySources.Method.TREE : SupplySources.Method.DIG;
	}

	private String missingText(Item item, int want, Map<String, Integer> leaves) {
		String because = "";
		for (Map.Entry<String, Integer> leaf : leaves.entrySet()) {
			if (leaf.getKey().equals(ItemUtil.id(item))) {
				continue;
			}
			String name;
			if (leaf.getKey().equals(SupplyPlanner.FUEL)) {
				name = Lang.raw("villager.gather.missing.fuel");
			} else {
				Item leafItem = ItemUtil.item(leaf.getKey());
				name = leafItem == null ? leaf.getKey() : SupplyLang.amount(leaf.getValue(), leafItem);
			}
			because = Lang.raw("villager.gather.missing.because", name);
			break;
		}
		return Lang.raw("villager.gather.missing", SupplyLang.amount(want, item), "") + because;
	}

	/** Reason shown when no option can be done: a missing workstation first, then the first missing item. */
	private String reason(List<Option> options) {
		Option station = null;
		Option missing = null;
		int missingCount = 0;
		Option noSite = null;
		for (Option option : options) {
			if (option.route() == Route.STATION && station == null) {
				station = option;
			} else if (option.route() == Route.MISSING) {
				missingCount++;
				if (missing == null) {
					missing = option;
				}
				if (noSite == null && noSiteUntil.containsKey(option.item())) {
					noSite = option;
				}
			}
		}
		if (station != null) {
			return station.reason();
		}
		if (noSite != null && noSite == missing) {
			return noSiteText(noSite.item());
		}
		if (missing != null) {
			String more = missingCount > 1 ? Lang.raw("villager.gather.missing.more", missingCount - 1) : "";
			return missing.reason() + more;
		}
		return Lang.raw("villager.gather.taken");
	}

	/** "Aucun gisement de Sable trouvé..." / "Aucun arbre naturel de Bûche de chêne...". */
	String noSiteText(Item item) {
		SupplySources.Method method = noSiteMethod.getOrDefault(item, SupplySources.Method.DIG);
		String key = method == SupplySources.Method.TREE ? "villager.gather.no_tree" : "villager.gather.no_site";
		return Lang.raw(key, SupplyLang.name(item), Supply.config().gathering.radius);
	}

	// ================================================================ choice

	/**
	 * What this gatherer should do now (the first doable option not fully handled by the others), or null with the reason
	 * in {@code reason[0]}. With {@code reserve}, the job is reserved ({@link Procurement#fetching}).
	 */
	@Nullable Job choose(WorkContext ctx, boolean reserve, String[] reason) {
		Analysis current = analysis(ctx);
		if (current.empty()) {
			reason[0] = current.reason();
			return null;
		}
		boolean taken = false;
		int bag = Math.max(0, ctx.config.work.carryStacks - ctx.record.carried.size());
		Planning planning = null;
		for (Option option : current.options()) {
			if (!option.doable()) {
				continue;
			}
			int remaining = option.want() - Procurement.fetchingByOthers(ctx.guild, option.item(), ctx.record.uuid);
			if (remaining <= 0) {
				taken = true;
				continue;
			}
			Job job = null;
			if (option.route() == Route.CRAFT) {
				if (planning == null) {
					planning = planning(ctx, Procurement.wanted(ctx.guild), ctx.record.uuid, true);
				}
				String id = ItemUtil.id(option.item());
				SupplyPlanner.Plan plan = SupplyPlanner.bestFromStock(planning.context(), id, Math.min(remaining, capacity(planning.book(), id, bag)), bag);
				if (plan != null && plan.steps().size() <= 64) {
					plan = trimCrafts(planning.context(), plan, bag);
					if (plan != null) {
						job = new Job(Route.CRAFT, option.item(), Math.min(remaining, plan.made()), plan, null, 0, null);
					}
				}
			} else if (option.gather() != null && bag > 0) {
				int amount = option.route() == Route.GATHER ? Math.min(remaining, option.gatherAmount()) : option.gatherAmount();
				SupplyBook book = SupplyBook.of(ctx.level);
				amount = Math.min(amount, book.maxStack(ItemUtil.id(option.gather())) * bag);
				if (amount > 0) {
					job = new Job(option.route(), option.item(), option.route() == Route.GATHER ? amount : Math.min(remaining, option.planned()), null,
							option.gather(), amount, option.method());
				}
			}
			if (job != null) {
				if (reserve) {
					Procurement.fetching(ctx.guild, ctx.record.uuid, job.target(), job.amount());
					planTakes(ctx.record.uuid, job.plan() == null ? null : items(job.plan()));
				}
				return job;
			}
		}
		reason[0] = taken && noneMissing(current) ? Lang.raw("villager.gather.taken") : current.reason();
		return null;
	}

	private static boolean noneMissing(Analysis analysis) {
		for (Option option : analysis.options()) {
			if (!option.doable()) {
				return false;
			}
		}
		return true;
	}

	/** At most {@code crafting.maxCraftsPerTrip} crafts in one trip (a smaller amount when needed). */
	private static SupplyPlanner.@Nullable Plan trimCrafts(SupplyPlanner.Context context, SupplyPlanner.Plan plan, int bag) {
		int max = Supply.config().crafting.maxCraftsPerTrip;
		SupplyPlanner.Plan current = plan;
		int amount = plan.amount();
		while (crafts(current) > max && amount > 1) {
			amount = Math.max(1, amount / 2);
			SupplyPlanner.Plan smaller = SupplyPlanner.bestFromStock(context, plan.target(), amount, bag);
			if (smaller == null) {
				return null;
			}
			current = smaller;
		}
		return current;
	}

	static int crafts(SupplyPlanner.Plan plan) {
		int total = 0;
		for (SupplyPlanner.Step step : plan.steps()) {
			total += step.times();
		}
		return total;
	}

	/** Storage inputs of a plan (fuel included), by item. */
	static Map<Item, Integer> items(SupplyPlanner.Plan plan) {
		Map<Item, Integer> items = new LinkedHashMap<>();
		plan.take().forEach((id, count) -> {
			Item item = ItemUtil.item(id);
			if (item != null) {
				items.merge(item, count, Integer::sum);
			}
		});
		plan.fuel().forEach((id, count) -> {
			Item item = ItemUtil.item(id);
			if (item != null) {
				items.merge(item, count, Integer::sum);
			}
		});
		return items;
	}

	/** The gatherer is done or stops: its reservation, planned inputs and leases are freed. */
	void leave(WorkContext ctx) {
		UUID me = ctx.record.uuid;
		Procurement.release(ctx.guild, me);
		takes.remove(me);
		pitLeases.values().removeIf(lease -> lease.owner().equals(me));
		stationLeases.values().removeIf(lease -> lease.owner().equals(me));
	}
}
