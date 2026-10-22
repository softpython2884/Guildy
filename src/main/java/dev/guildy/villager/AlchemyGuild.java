package dev.guildy.villager;

import dev.guildy.alchemy.AlchemyConfig;
import dev.guildy.alchemy.AlchemyModule;
import dev.guildy.alchemy.Brews;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the alchemists of a guild share (not persisted, rebuilt after a restart): the enchanting tables and brewing stands
 * of the territory (one scan for all of them), an inventory of the storage (potions held, supplies they may take),
 * the batches being brewed (so that two alchemists do not brew the same missing potion), who uses which stand, the
 * distribution of the roles and, for the menus, what each alchemist does and why.
 */
final class AlchemyGuild {
	private static final Map<UUID, AlchemyGuild> GUILDS = new HashMap<>();
	/** A scan not finished after this long is taken over by another alchemist. */
	private static final long SCAN_LEASE_TICKS = 60 * 20L;
	private static final long STAND_LEASE_TICKS = 30 * 20L;

	// workshops
	List<BlockPos> tables = List.of();
	List<BlockPos> stands = List.of();
	/** Scheduler tick of the last complete scan, -1 = never. */
	long workshopsAt = -1;
	/** The last scan read no chunk at all (territory out of reach). */
	boolean unloadedOnly;
	private @Nullable UUID scanner;
	private long scannerAt;
	private final Map<BlockPos, BlockPos> water = new HashMap<>();
	private final Set<BlockPos> dry = new HashSet<>();

	// storage inventory
	private @Nullable Stock stock;
	private long stockAt = -1;

	// batches and stands
	private final Map<UUID, Batch> batches = new HashMap<>();
	private final Map<BlockPos, UUID> standUsers = new HashMap<>();
	private final Map<BlockPos, Long> standUntil = new HashMap<>();

	// roles
	final Map<UUID, AlchemyJob> roles = new HashMap<>();
	long rolesAt = -1;
	private Set<UUID> rolesFor = Set.of();

	// menus
	final Map<UUID, Note> notes = new HashMap<>();

	/** A batch being brewed: its product and bottles. */
	record Batch(String product, int bottles) {
	}

	/**
	 * What an alchemist does: its preferred job (manual or distributed), the job it works at (null: nothing possible) and
	 * the reason of each blocked job before it (lang text, already formatted).
	 */
	record Note(AlchemyJob preferred, boolean manual, @Nullable AlchemyJob working, Map<AlchemyJob, String> reasons) {
	}

	/**
	 * The storage seen by the alchemists: products held in every registered container and in the alchemists' bags
	 * ({@link #products}), plain items and bottles they may take ({@code Storage.gives}).
	 */
	static final class Stock {
		final Map<String, Integer> products = new HashMap<>();
		final Map<Item, Integer> plain = new HashMap<>();
		final Map<String, Integer> bottles = new HashMap<>();

		int plain(@Nullable Item item) {
			return item == null ? 0 : plain.getOrDefault(item, 0);
		}

		int bottles(String key) {
			return bottles.getOrDefault(key, 0);
		}
	}

	private AlchemyGuild() {
	}

	static AlchemyGuild of(Guild guild) {
		return GUILDS.computeIfAbsent(guild.id, id -> new AlchemyGuild());
	}

	static @Nullable AlchemyGuild peek(Guild guild) {
		return GUILDS.get(guild.id);
	}

	static void clear() {
		GUILDS.clear();
	}

	static void forget(UUID guild) {
		GUILDS.remove(guild);
	}

	// ------------------------------------------------------------------ workshops

	boolean workshopsKnown() {
		return workshopsAt >= 0;
	}

	boolean scanDue(long now, long interval) {
		return workshopsAt < 0 || now - workshopsAt >= interval;
	}

	/** True when this alchemist may run the scan (nobody else is running one). */
	boolean claimScan(UUID alchemist, long now) {
		if (scanner != null && !scanner.equals(alchemist) && now - scannerAt < SCAN_LEASE_TICKS) {
			return false;
		}
		scanner = alchemist;
		scannerAt = now;
		return true;
	}

	void publish(List<BlockPos> foundTables, List<BlockPos> foundStands, boolean nothingRead, long now) {
		tables = List.copyOf(foundTables);
		stands = List.copyOf(foundStands);
		unloadedOnly = nothingRead;
		workshopsAt = now;
		scanner = null;
		water.clear();
		dry.clear();
	}

	void releaseScan(UUID alchemist) {
		if (alchemist.equals(scanner)) {
			scanner = null;
		}
	}

	/** A table or stand disappeared: the next decision rescans. */
	void invalidateWorkshops() {
		workshopsAt = -1;
	}

	/** Water near a stand to fill glass bottles (cached until the next scan), null when there is none. */
	@Nullable BlockPos waterNear(ServerLevel level, BlockPos stand, int radius) {
		BlockPos known = water.get(stand);
		if (known != null && CraftWorld.isWater(level, known)) {
			return known;
		}
		if (known == null && dry.contains(stand)) {
			return null;
		}
		BlockPos found = CraftWorld.water(level, stand, radius);
		if (found == null) {
			water.remove(stand);
			dry.add(stand);
		} else {
			water.put(stand, found);
		}
		return found;
	}

	/** Some stand of the territory has water near it. */
	boolean anyWater(ServerLevel level, int radius) {
		for (BlockPos stand : stands) {
			if (level.isLoaded(stand) && waterNear(level, stand, radius) != null) {
				return true;
			}
		}
		return false;
	}

	/** Stand for an alchemist: a free one nearest to it, else the nearest (shared). Leased for a while. */
	@Nullable BlockPos stand(WorkContext ctx) {
		BlockPos free = null;
		BlockPos any = null;
		double freeDist = Double.MAX_VALUE;
		double anyDist = Double.MAX_VALUE;
		UUID me = ctx.record.uuid;
		for (BlockPos pos : stands) {
			if (!CraftWorld.isStand(ctx.level, pos) || !ctx.inTerritory(pos)) {
				continue;
			}
			double dist = ctx.distSq(pos);
			if (dist < anyDist) {
				any = pos;
				anyDist = dist;
			}
			UUID user = standUsers.get(pos);
			boolean taken = user != null && !user.equals(me) && ctx.now < standUntil.getOrDefault(pos, 0L);
			if (!taken && dist < freeDist) {
				free = pos;
				freeDist = dist;
			}
		}
		BlockPos chosen = free != null ? free : any;
		if (chosen != null) {
			lease(chosen, me, ctx.now);
		}
		return chosen;
	}

	void lease(BlockPos stand, UUID alchemist, long now) {
		standUsers.put(stand, alchemist);
		standUntil.put(stand, now + STAND_LEASE_TICKS);
	}

	void releaseStand(UUID alchemist) {
		standUsers.values().removeIf(alchemist::equals);
	}

	// ------------------------------------------------------------------ storage inventory

	/** Inventory of the storage, at most {@code alchemist.stockScanSeconds} old. */
	Stock stock(ServerLevel level, Guild guild) {
		long now = Scheduler.ticks();
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		if (stock == null || stockAt < 0 || now - stockAt >= WorkContext.ticks(cfg.stockScanSeconds)) {
			stock = inventory(level, guild);
			stockAt = now;
		}
		return stock;
	}

	void invalidateStock() {
		stockAt = -1;
	}

	private static Stock inventory(ServerLevel level, Guild guild) {
		Stock stock = new Stock();
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
			// A filtered-room chest keeps one item of each kind (its contents are its filter).
			Storage.Spare spare = Storage.Spare.of(chest, container);
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				String key = Brews.keyOf(stack);
				if (key != null) {
					stock.products.merge(key, stack.getCount(), Integer::sum);
				}
				if (!Storage.gives(chest, stack)) {
					continue;
				}
				int givable = spare.take(stack, stack.getCount());
				if (givable <= 0) {
					continue;
				}
				if (stack.getComponentsPatch().isEmpty()) {
					stock.plain.merge(stack.getItem(), givable, Integer::sum);
				}
				if (key != null) {
					stock.bottles.merge(key, givable, Integer::sum);
				}
			}
		}
		// Bottles on their way to the storage count as held.
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.task != Task.ALCHEMIST) {
				continue;
			}
			for (ItemStack stack : record.carried) {
				String key = Brews.keyOf(stack);
				if (key != null) {
					stock.products.merge(key, stack.getCount(), Integer::sum);
				}
			}
		}
		return stock;
	}

	// ------------------------------------------------------------------ batches

	void reserve(UUID alchemist, String product, int bottles) {
		batches.put(alchemist, new Batch(product, bottles));
	}

	void release(UUID alchemist) {
		batches.remove(alchemist);
	}

	/** Products held plus the batches of the other alchemists (what will soon be held). */
	Map<String, Integer> held(ServerLevel level, Guild guild, @Nullable UUID except) {
		Map<String, Integer> held = new HashMap<>(stock(level, guild).products);
		for (Map.Entry<UUID, Batch> entry : batches.entrySet()) {
			if (!entry.getKey().equals(except)) {
				held.merge(entry.getValue().product(), entry.getValue().bottles(), Integer::sum);
			}
		}
		return held;
	}

	// ------------------------------------------------------------------ stock targets

	/** Stock target of a product for this guild: its own choice, else the default of alchemy.json5. */
	static int target(Guild guild, String product) {
		Integer own = guild.villagers.potionTargets.get(product);
		AlchemyConfig config = AlchemyModule.config();
		int value = own != null ? own : config.defaultTarget(product);
		return Math.max(0, Math.min(config.maxStockTarget, value));
	}

	/** Targets of every product of the menu. */
	static Map<String, Integer> targets(Guild guild) {
		Map<String, Integer> targets = new LinkedHashMap<>();
		for (String product : AlchemyModule.config().menuProducts()) {
			targets.put(product, target(guild, product));
		}
		return targets;
	}

	// ------------------------------------------------------------------ roles

	/** Alchemists of the guild at home, in a stable order. */
	static List<VillagerRecord> alchemists(Guild guild) {
		List<VillagerRecord> list = new ArrayList<>();
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.task == Task.ALCHEMIST && !record.isAway()) {
				list.add(record);
			}
		}
		list.sort((a, b) -> a.uuid.toString().compareTo(b.uuid.toString()));
		return list;
	}

	/** True when the roles must be distributed again (time, or the alchemists changed). */
	boolean rolesDue(long now, long interval, Set<UUID> current) {
		return rolesAt < 0 || now - rolesAt >= interval || !rolesFor.equals(current);
	}

	void setRoles(Map<UUID, AlchemyJob> assigned, Set<UUID> current, long now) {
		roles.clear();
		roles.putAll(assigned);
		rolesFor = Set.copyOf(current);
		rolesAt = now;
	}

	/** An alchemist stopped (task changed, villager gone): its batch, stand and note are freed. */
	void leave(UUID alchemist) {
		release(alchemist);
		releaseStand(alchemist);
		releaseScan(alchemist);
		notes.remove(alchemist);
		rolesAt = -1;
	}
}
