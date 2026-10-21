package dev.guildy.villager;

import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Index of a guild's registered containers (runtime only, never saved), so that the storage work costs what it has to
 * do rather than the number of chests:
 * <ul>
 * <li>position → chest (both lookups of {@link GuildVillagerData#chestAt} and {@link Storage#registered});</li>
 * <li>deposits, sort sources (deposits and free chests of the rooms), one container per chunk (chunk tickets), the
 * counters of the limits;</li>
 * <li>the routing index ({@link StorageRouting.Index}: chests by category, filter, fallback, deposit) built with the
 * current categories;</li>
 * <li>the chest groups ({@link StorageGroups}) in reading order, built on demand.</li>
 * </ul>
 * Rebuilt when the chest list changes (its modification counter), when a chest's role changes ({@link Storage#changed},
 * called by every code that edits a registered chest), when the categories are reloaded, and at most every
 * {@link #MAX_AGE} ticks: a change made without notice is seen within 5 seconds. A rebuild reads no block and no
 * container: a few hundred microseconds for thousands of chests. The groups (they read the facing of every container)
 * survive such a periodic rebuild when nothing changed, and are computed again at most every {@link #GROUPS_MAX_AGE}
 * ticks (a double chest formed by a player, which changes the reading order, is seen within 30 seconds).
 */
final class StorageIndex {
	/** Ticks after which the index is rebuilt anyway. */
	static final long MAX_AGE = 100;
	/** Ticks after which the groups are computed again even when nothing changed. */
	static final long GROUPS_MAX_AGE = 600;

	private final GuildVillagerData.ChestList list;
	private final int modifications;
	private final int version;
	private final long builtAt;

	/** Chests with a position, in list order. */
	private final List<StorageChest> chests;
	private final Map<BlockPos, StorageChest> byPos;
	private final List<StorageChest> deposits = new ArrayList<>();
	private final List<StorageChest> sources = new ArrayList<>();
	private final List<BlockPos> chunkAnchors = new ArrayList<>();
	private int manual;
	private int filters;
	private int roomChests;
	private int autos;

	private @Nullable StorageCategories routingFor;
	private StorageRouting.@Nullable Index routing;
	private @Nullable StorageCategories groupsFor;
	private ChestOrder.@Nullable Mode groupsMode;
	private @Nullable List<StorageGroups.Group> groups;
	private @Nullable Map<String, Integer> groupPositions;
	private long groupsBuiltAt;
	private @Nullable List<StorageChest> equipmentGivers;

	private StorageIndex(GuildVillagerData.ChestList list, int version, long now) {
		this.list = list;
		this.modifications = list.modifications();
		this.version = version;
		this.builtAt = now;
		this.chests = new ArrayList<>(list.size());
		this.byPos = new HashMap<>(Math.max(16, list.size() * 2));
		Set<Long> chunks = new HashSet<>();
		for (StorageChest chest : list) {
			if (chest == null || chest.pos == null) {
				continue;
			}
			chests.add(chest);
			byPos.putIfAbsent(chest.pos, chest);
			if (chest.isDeposit()) {
				deposits.add(chest);
			}
			if (Storage.isSortSource(chest)) {
				sources.add(chest);
			}
			if (chest.role == StorageChest.Role.FILTER) {
				filters++;
			} else if (chest.room > 0) {
				roomChests++;
			} else {
				manual++;
			}
			if (chest.auto) {
				autos++;
			}
			if (chunks.add(ChunkKey.ofBlock(chest.pos.getX(), chest.pos.getZ()))) {
				chunkAnchors.add(chest.pos);
			}
		}
	}

	/** The current index of the guild's containers (rebuilt when stale). */
	static StorageIndex of(GuildVillagerData data) {
		GuildVillagerData.ChestList list = data.chestList();
		StorageIndex index = data.storageIndex;
		long now = Scheduler.ticks();
		boolean same = index != null && index.list == list && index.modifications == list.modifications() && index.version == data.storageVersion
				&& now >= index.builtAt;
		if (!same || now - index.builtAt >= MAX_AGE) {
			StorageIndex previous = index;
			index = new StorageIndex(list, data.storageVersion, now);
			if (same && previous.groups != null && now - previous.groupsBuiltAt < GROUPS_MAX_AGE) {
				// Periodic rebuild, nothing changed: the same chests in the same groups (they hold the chest objects).
				index.groups = previous.groups;
				index.groupPositions = previous.groupPositions;
				index.groupsFor = previous.groupsFor;
				index.groupsMode = previous.groupsMode;
				index.groupsBuiltAt = previous.groupsBuiltAt;
			}
			data.storageIndex = index;
		}
		return index;
	}

	// ================================================================ positions, kinds, counters

	/** Registered chest at exactly this position (the first one registered there), or null. */
	@Nullable StorageChest at(BlockPos pos) {
		return byPos.get(pos);
	}

	/** Chests with a position, in registration order. */
	List<StorageChest> chests() {
		return chests;
	}

	List<StorageChest> deposits() {
		return deposits;
	}

	/** Deposits and free chests of the rooms: what the sorters empty. */
	List<StorageChest> sources() {
		return sources;
	}

	/** One registered position per chunk holding registered containers. */
	List<BlockPos> chunkAnchors() {
		return chunkAnchors;
	}

	/** Containers outside the rooms, filter chests aside (budget: storage.maxChests). */
	int manualCount() {
		return manual;
	}

	/** Filter chests (budget: storage.maxFilterChests). */
	int filterCount() {
		return filters;
	}

	/** Containers of the rooms, filter chests aside (budget: storage.maxRoomChests). */
	int roomChestCount() {
		return roomChests;
	}

	int autoCount() {
		return autos;
	}

	// ================================================================ routing

	/** Routing index with the current categories (targets in the order of {@link #chests()}). */
	StorageRouting.Index routing() {
		StorageCategories categories = Storage.categories();
		if (routing == null || routingFor != categories) {
			List<StorageRouting.Target> targets = new ArrayList<>(chests.size());
			int[] xs = new int[chests.size()];
			int[] ys = new int[chests.size()];
			int[] zs = new int[chests.size()];
			for (int i = 0; i < chests.size(); i++) {
				StorageChest chest = chests.get(i);
				targets.add(Storage.target(chest));
				xs[i] = chest.pos.getX();
				ys[i] = chest.pos.getY();
				zs[i] = chest.pos.getZ();
			}
			routing = new StorageRouting.Index(targets, xs, ys, zs);
			routingFor = categories;
			equipmentGivers = null;
		}
		return routing;
	}

	/** Chest of a routing target index. */
	StorageChest chest(int target) {
		return chests.get(target);
	}

	/**
	 * Every chest that may give this stack to a worker or receive it from a sorter ({@link Storage#gives}): deposits,
	 * fallback chests, filter chests accepting it and the chests of the categories of its routing chain, best tier first.
	 */
	List<StorageChest> givers(ItemStack stack) {
		StorageRouting.Index index = routing();
		List<StorageChest> givers = new ArrayList<>();
		for (StorageRouting.Bucket bucket : index.buckets(Storage.itemId(stack), Storage.chain(stack), StorageRouting.DEPOSIT_TIER)) {
			for (int member : bucket.members()) {
				givers.add(chests.get(member));
			}
		}
		return givers;
	}

	/** Chests that may give one of these items (each once). */
	List<StorageChest> givers(Collection<ItemStack> stacks) {
		Set<StorageChest> set = new LinkedHashSet<>();
		for (ItemStack stack : stacks) {
			set.addAll(givers(stack));
		}
		return new ArrayList<>(set);
	}

	/**
	 * Chests a guard may find equipment in: the givers of every armor piece and weapon, and the chests of the transversal
	 * categories (enchanted equipment).
	 */
	List<StorageChest> equipmentGivers() {
		StorageRouting.Index index = routing();
		if (equipmentGivers == null) {
			Set<StorageChest> set = new LinkedHashSet<>(givers(ItemClassifier.equipmentSamples()));
			for (String transversal : Storage.categories().transversals()) {
				for (int member : index.categoryMembers(transversal)) {
					set.add(chests.get(member));
				}
			}
			equipmentGivers = new ArrayList<>(set);
		}
		return equipmentGivers;
	}

	// ================================================================ groups

	/** Chest groups in menu order (see {@link StorageGroups#groups}), built once per index. */
	List<StorageGroups.Group> groups(ServerLevel level, GuildVillagerData data, ChestOrder.Mode mode) {
		StorageCategories categories = Storage.categories();
		if (groups == null || groupsFor != categories || groupsMode != mode) {
			groups = List.copyOf(StorageGroups.build(level, chests, mode));
			Map<String, Integer> positions = new HashMap<>();
			for (int i = 0; i < groups.size(); i++) {
				positions.put(groups.get(i).key(), i);
			}
			groupPositions = positions;
			groupsFor = categories;
			groupsMode = mode;
			groupsBuiltAt = Scheduler.ticks();
		}
		return groups;
	}

	/** Position of a group in {@link #groups} (-1 if unknown); call after {@link #groups}. */
	int groupPosition(String key) {
		Map<String, Integer> positions = groupPositions;
		Integer position = positions == null ? null : positions.get(key);
		return position == null ? -1 : position;
	}

	/** Forgets the groups (the order setting of the guild changed). */
	void forgetGroups() {
		groups = null;
		groupPositions = null;
	}
}
