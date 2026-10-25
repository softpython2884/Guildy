package dev.guildy.villager;

import dev.guildy.builds.Bags;
import dev.guildy.builds.BuildModule;
import dev.guildy.builds.BuildProject;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where the builders take their materials (a site's option, {@link BuildProject.Sources}):
 * <ul>
 * <li>STORAGE: the guild's registered chests that may give the item (deposit chests, the chests of its category,
 * "Divers", filtered rooms keeping one of each kind: {@link Storage#gives}, {@link Storage.Spare});</li>
 * <li>ALL: every chest, trapped chest and barrel of the territory, registered or not, found by an incremental scan of
 * the claimed loaded chunks ({@code builder.containerScanChunksPerSecond}).</li>
 * </ul>
 * Plain stacks only (no renamed / enchanted block is ever built with), never a container open by a player. What is
 * taken really leaves the container (counted, then removed exactly).
 */
public final class BuildStock {
	private static final long RESCAN_TICKS = 60 * 20L;
	private static final Map<UUID, Containers> CONTAINERS = new HashMap<>();

	/** Containers of a guild's territory (scan in progress + the last complete list). */
	private static final class Containers {
		List<BlockPos> known = List.of();
		final Set<BlockPos> scanning = new LinkedHashSet<>();
		long[] chunks = new long[0];
		int next;
		long doneAt = -1;
		long lastStep = -1;
	}

	private BuildStock() {
	}

	static void clear() {
		CONTAINERS.clear();
	}

	// ================================================================ counting

	/** Plain items the builders may take from the sources (item → count). */
	public static Map<Item, Integer> available(ServerLevel level, Guild guild, BuildProject.Sources sources) {
		Map<Item, Integer> stock = new HashMap<>();
		Set<BlockPos> seen = new HashSet<>();
		List<Container> shown = Storage.shownContainers(level);
		for (BlockPos pos : sourcePositions(level, guild, sources)) {
			if (seen.contains(pos) || !level.isLoaded(pos)) {
				continue;
			}
			seen.addAll(Storage.halves(level, pos));
			if (Storage.inUse(level, pos, shown)) {
				continue;
			}
			StorageChest chest = sources == BuildProject.Sources.STORAGE ? Storage.registered(level, guild, pos) : null;
			Container container = Storage.container(level, pos);
			if (container == null || (sources == BuildProject.Sources.STORAGE && chest == null)) {
				continue;
			}
			Storage.Spare spare = Storage.Spare.of(chest, container);
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (!Bags.plain(stack) || (chest != null && !Storage.gives(chest, stack))) {
					continue;
				}
				int givable = spare.take(stack, stack.getCount());
				if (givable > 0) {
					stock.merge(stack.getItem(), givable, Integer::sum);
				}
			}
		}
		return stock;
	}

	/** Positions of the source containers (registered chests usable now, or every container found in the territory). */
	private static List<BlockPos> sourcePositions(ServerLevel level, Guild guild, BuildProject.Sources sources) {
		List<BlockPos> list = new ArrayList<>();
		if (sources == BuildProject.Sources.STORAGE) {
			for (StorageChest chest : guild.villagers.chests) {
				if (Storage.usable(level, guild, chest)) {
					list.add(chest.pos);
				}
			}
			return list;
		}
		Containers containers = scan(level, guild);
		Set<BlockPos> all = new LinkedHashSet<>(containers.known);
		all.addAll(containers.scanning);
		for (BlockPos pos : all) {
			if (level.isLoaded(pos) && GuildManager.at(level, pos) == guild && isContainer(level.getBlockEntity(pos))) {
				list.add(pos);
			}
		}
		return list;
	}

	/** Chests, trapped chests and barrels (shulker boxes stay their owner's business). */
	private static boolean isContainer(@Nullable BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
	}

	/** Advances the territory scan of the guild (a few chunks per call, at most once per tick). */
	private static Containers scan(ServerLevel level, Guild guild) {
		Containers containers = CONTAINERS.computeIfAbsent(guild.id, id -> new Containers());
		long now = Scheduler.ticks();
		if (containers.lastStep == now) {
			return containers;
		}
		long elapsed = containers.lastStep < 0 ? 20 : Math.min(20 * 30, now - containers.lastStep);
		containers.lastStep = now;
		if (containers.next >= containers.chunks.length) {
			if (containers.doneAt >= 0 && now - containers.doneAt < RESCAN_TICKS && containers.chunks.length == guild.claimCount()) {
				return containers;
			}
			List<Long> claims = new ArrayList<>(guild.claims());
			BlockPos heart = guild.heart;
			if (heart != null) {
				int hx = heart.getX() >> 4;
				int hz = heart.getZ() >> 4;
				claims.sort(Comparator.comparingLong(key -> {
					long dx = ChunkKey.x(key) - hx;
					long dz = ChunkKey.z(key) - hz;
					return dx * dx + dz * dz;
				}));
			}
			containers.chunks = claims.stream().mapToLong(Long::longValue).toArray();
			containers.next = 0;
			containers.scanning.clear();
		}
		int budget = (int) Math.max(1, BuildModule.config().builder.containerScanChunksPerSecond * Math.max(1, elapsed / 20));
		while (budget-- > 0 && containers.next < containers.chunks.length) {
			long key = containers.chunks[containers.next++];
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkKey.x(key), ChunkKey.z(key));
			if (chunk == null) {
				continue;
			}
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				if (isContainer(blockEntity)) {
					containers.scanning.add(blockEntity.getBlockPos().immutable());
				}
			}
		}
		if (containers.next >= containers.chunks.length) {
			containers.known = List.copyOf(containers.scanning);
			containers.doneAt = now;
		}
		return containers;
	}

	// ================================================================ fetching

	/** Nearest source container (not excluded, not in use) holding at least one wanted plain item. */
	static @Nullable BlockPos nearestWith(WorkContext ctx, BuildProject.Sources sources, Set<Item> wanted, Set<BlockPos> excluded) {
		if (wanted.isEmpty()) {
			return null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		Set<BlockPos> seen = new HashSet<>();
		List<Container> shown = Storage.shownContainers(ctx.level);
		for (BlockPos pos : sourcePositions(ctx.level, ctx.guild, sources)) {
			if (excluded.contains(pos) || seen.contains(pos)) {
				continue;
			}
			seen.addAll(Storage.halves(ctx.level, pos));
			double dist = ctx.distSq(pos);
			if (dist >= bestDist || Storage.inUse(ctx.level, pos, shown)) {
				continue;
			}
			StorageChest chest = sources == BuildProject.Sources.STORAGE ? Storage.registered(ctx.level, ctx.guild, pos) : null;
			Container container = Storage.container(ctx.level, pos);
			if (container == null || (sources == BuildProject.Sources.STORAGE && chest == null)) {
				continue;
			}
			Storage.Spare spare = Storage.Spare.of(chest, container);
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (Bags.plain(stack) && wanted.contains(stack.getItem()) && (chest == null || Storage.gives(chest, stack)) && spare.mayTake(stack)) {
					best = pos;
					bestDist = dist;
					break;
				}
			}
		}
		return best;
	}

	/**
	 * Moves wanted plain items from the container at {@code pos} into the bag (as much as fits in {@code capacity} stacks).
	 *
	 * @return items moved
	 */
	static int fetch(WorkContext ctx, BlockPos pos, BuildProject.Sources sources, Map<Item, Integer> wanted, int capacity) {
		if (Storage.inUse(ctx.level, pos)) {
			return 0;
		}
		StorageChest chest = sources == BuildProject.Sources.STORAGE ? Storage.registered(ctx.level, ctx.guild, pos) : null;
		if (sources == BuildProject.Sources.STORAGE && (chest == null || !Storage.usable(ctx.level, ctx.guild, chest))) {
			return 0;
		}
		if (sources == BuildProject.Sources.ALL && GuildManager.at(ctx.level, pos) != ctx.guild) {
			return 0;
		}
		Container container = Storage.container(ctx.level, pos);
		if (container == null) {
			return 0;
		}
		Map<Item, Integer> left = new HashMap<>(wanted);
		Storage.Spare spare = Storage.Spare.of(chest, container);
		int moved = 0;
		List<ItemStack> bag = ctx.record.carried;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!Bags.plain(stack) || (chest != null && !Storage.gives(chest, stack))) {
				continue;
			}
			int want = left.getOrDefault(stack.getItem(), 0);
			if (want <= 0) {
				continue;
			}
			int take = Math.min(Math.min(want, stack.getCount()), Bags.room(bag, stack.getItem(), capacity));
			take = spare.take(stack, take);
			if (take <= 0) {
				continue;
			}
			ItemStack taken = container.removeItem(i, take);
			ItemStack rest = Bags.add(bag, taken, capacity);
			if (!rest.isEmpty()) {
				// Never lost: what does not fit after all goes back where it was.
				ItemStack back = Storage.insert(container, rest);
				if (!back.isEmpty()) {
					bag.add(back);
				}
			}
			int count = taken.getCount() - rest.getCount();
			moved += count;
			left.merge(stack.getItem(), -count, Integer::sum);
		}
		if (moved > 0) {
			container.setChanged();
			GuildManager.markDirty();
		}
		return moved;
	}
}
