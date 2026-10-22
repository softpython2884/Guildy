package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Tidying of the chest groups ({@link StorageGroups}) by the sorters, when the deposits are empty:
 * <ul>
 * <li>an audit reads a whole group (loaded chests of the territory), gives every distinct stack a key in the canonical
 * order ({@link StorageSort}) and computes the perfect layout ({@link StorageLayout#layout}, by columns or rows inside
 * each chest: {@link StorageGroups#fill}); items with a better destination elsewhere ({@link StorageRouting}: another
 * category, a filter chest accepting them, items a filter chest refuses) are to be taken out (items without any other
 * destination stay in the group);</li>
 * <li>the work found: take misplaced items out (carried, then routed like deposits), reorganise a section within reach of
 * one standing spot in one atomic step ({@link StorageLayout#arrange}), or carry stacks between two sections
 * ({@link StorageLayout#transfers}, a full destination swaps its own extra items into the bag);</li>
 * <li>bounded: at most {@code auditChestsPerStep} chests read per step (a bigger group alone), groups in turn
 * (round robin) with a "Réorganiser maintenant" group first; a group leased by a sorter is left to it.</li>
 * </ul>
 * The chests of a filtered room (a group whose {@link StorageGroups.Group#content} is set) are only merged and laid out
 * inside each chest: their items define what they accept, so nothing is ever taken out of them nor carried from one to
 * another (each chest keeps exactly the items it holds, hence every kind it holds).
 * Every change is checked: the items of the containers are counted before and after, a mismatch restores the previous
 * contents. A container someone has open ({@link Storage#inUse}) is never touched.
 */
final class Tidier {
	enum Kind {
		/** Take the items of other categories out of one chest. */
		TAKE_OUT,
		/** Reorganise a section from one standing spot. */
		ARRANGE,
		/** Carry stacks from a section to another. */
		CARRY
	}

	/**
	 * Work found by an audit. {@code walkTo}: where to stand; {@code chests}: the chest (TAKE_OUT), the section (ARRANGE)
	 * or the source section (CARRY); {@code destination}: the destination section of a CARRY; {@code index}/{@code total}:
	 * 1-based number of the (first) chest in the group and the group size, for the statuses.
	 */
	record Job(Kind kind, String groupKey, String category, BlockPos walkTo, List<BlockPos> chests, List<BlockPos> destination,
			int index, int total) {
	}

	/** Result of {@link #next}: a job, or nothing (everything tidy, a busy chest, or audits to continue next step). */
	record Next(@Nullable Job job, boolean allTidy, @Nullable String busyCategory) {
		static final Next CONTINUE = new Next(null, false, null);
		static final Next ALL_TIDY = new Next(null, true, null);
	}

	/** Last audit of a group, for the menus. */
	record Status(boolean tidy, boolean busy, long checkedAt) {
	}

	/** Runtime state of a guild's tidying (not saved). */
	private static final class GuildState {
		int cursor;
		@Nullable String priority;
		long priorityUntil;
		final Map<String, Status> status = new HashMap<>();
	}

	private record Lease(UUID owner, long until) {
	}

	private static final Map<UUID, GuildState> STATES = new HashMap<>();
	private static final Map<String, Lease> LEASES = new HashMap<>();
	private static final long PRIORITY_TICKS = 5 * 60 * 20L;

	private Tidier() {
	}

	static void clear() {
		STATES.clear();
		LEASES.clear();
	}

	static void forget(Guild guild) {
		STATES.remove(guild.id);
		String prefix = guild.id + "|";
		LEASES.keySet().removeIf(key -> key.startsWith(prefix));
	}

	private static GuildState state(Guild guild) {
		return STATES.computeIfAbsent(guild.id, id -> new GuildState());
	}

	// ================================================================ menus

	/** Last audit of a group (null: never checked since the start). */
	static @Nullable Status status(Guild guild, String groupKey) {
		GuildState state = STATES.get(guild.id);
		return state == null ? null : state.status.get(groupKey);
	}

	/** "Réorganiser maintenant": the group is audited first by the next sorter. */
	static void prioritize(Guild guild, String groupKey, long now) {
		GuildState state = state(guild);
		state.priority = groupKey;
		state.priorityUntil = now + PRIORITY_TICKS;
		state.status.remove(groupKey);
	}

	/** Items were put into this chest (deposit sorting): its group is checked again at the next audit. */
	static void touched(Guild guild, StorageChest chest) {
		GuildState state = STATES.get(guild.id);
		if (state != null && !chest.isDeposit() && !chest.isFree()) {
			// The group, or every part of a big group.
			String key = StorageGroups.keyOf(chest);
			state.status.keySet().removeIf(k -> StorageGroups.baseKey(k).equals(key));
		}
	}

	/**
	 * Forgets the audits of a guild (categories reassigned, order changed): everything is checked again. The storage
	 * index is rebuilt too (every caller just changed a registered container or a setting of the groups).
	 */
	static void invalidate(Guild guild) {
		Storage.changed(guild);
		GuildState state = STATES.get(guild.id);
		if (state != null) {
			state.status.clear();
		}
	}

	/** Villager currently tidying the group (lease), or null. */
	static @Nullable UUID worker(Guild guild, String groupKey, long now) {
		Lease lease = LEASES.get(leaseKey(guild, groupKey));
		return lease == null || lease.until() < now ? null : lease.owner();
	}

	// ================================================================ leases

	private static String leaseKey(Guild guild, String groupKey) {
		return guild.id + "|" + groupKey;
	}

	/** Takes or renews the lease of a group; false when another sorter holds it. */
	static boolean lease(Guild guild, String groupKey, UUID owner, long now, int seconds) {
		String key = leaseKey(guild, groupKey);
		Lease lease = LEASES.get(key);
		if (lease != null && lease.until() >= now && !lease.owner().equals(owner)) {
			return false;
		}
		LEASES.put(key, new Lease(owner, now + seconds * 20L));
		return true;
	}

	static void release(UUID owner) {
		LEASES.values().removeIf(lease -> lease.owner().equals(owner));
	}

	private static boolean leasedByOther(Guild guild, String groupKey, UUID me, long now) {
		Lease lease = LEASES.get(leaseKey(guild, groupKey));
		return lease != null && lease.until() >= now && !lease.owner().equals(me);
	}

	// ================================================================ audits

	/** Everything known about a group at one moment (one tick). */
	static final class Audit {
		final StorageGroups.Group group;
		final List<StorageChest> chests = new ArrayList<>();
		final List<ChestOrder.Unit> units = new ArrayList<>();
		final List<Container> containers = new ArrayList<>();
		final List<ItemStack> keys = new ArrayList<>();
		final Object2IntOpenCustomHashMap<ItemStack> index = new Object2IntOpenCustomHashMap<>(StorageSort.SAME_ITEM);
		final Set<Integer> out = new HashSet<>();
		boolean[] busy = new boolean[0];
		StorageLayout.Stack[][] current = new StorageLayout.Stack[0][];
		StorageLayout.Stack @Nullable [][] target;
		List<List<Integer>> sections = List.of();
		StorageLayout.Rules rules = key -> 64;
		boolean tidy;

		Audit(StorageGroups.Group group) {
			this.group = group;
			index.defaultReturnValue(-1);
		}

		int indexOf(BlockPos pos) {
			for (int i = 0; i < chests.size(); i++) {
				if (chests.get(i).pos.equals(pos)) {
					return i;
				}
			}
			return -1;
		}

		boolean anyBusy() {
			for (boolean b : busy) {
				if (b) {
					return true;
				}
			}
			return false;
		}

		/** Key of a stack in this group, -1 if the group holds none. */
		int key(ItemStack stack) {
			return stack.isEmpty() ? -1 : index.getInt(stack);
		}
	}

	static Audit audit(ServerLevel level, Guild guild, StorageGroups.Group group) {
		VillagerConfig.Sorter config = VillagerModule.config().sorter;
		Audit a = new Audit(group);
		Set<BlockPos> covered = new HashSet<>();
		for (StorageChest chest : group.chests()) {
			if (!Storage.usable(level, guild, chest) || covered.contains(chest.pos)) {
				continue;
			}
			Container container = Storage.container(level, chest.pos);
			if (container == null) {
				continue;
			}
			// A double chest registered twice (one entry per half) is read once: never count the same slots twice.
			covered.addAll(Storage.halves(level, chest.pos));
			a.units.add(StorageGroups.unit(level, a.chests.size(), chest.pos));
			a.chests.add(chest);
			a.containers.add(container);
		}
		int n = a.chests.size();
		a.busy = new boolean[n];
		List<Container> shown = Storage.shownContainers(level);
		for (int i = 0; i < n; i++) {
			a.busy[i] = Storage.inUse(level, a.chests.get(i).pos, shown);
		}
		// Keys in canonical order.
		for (Container container : a.containers) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty() && a.index.getInt(stack) < 0) {
					ItemStack prototype = stack.copyWithCount(1);
					a.index.put(prototype, a.keys.size());
					a.keys.add(prototype);
				}
			}
		}
		StorageSort.sort(a.keys);
		a.index.clear();
		for (int k = 0; k < a.keys.size(); k++) {
			a.index.put(a.keys.get(k), k);
		}
		a.current = new StorageLayout.Stack[n][];
		int[] sizes = new int[n];
		for (int c = 0; c < n; c++) {
			Container container = a.containers.get(c);
			sizes[c] = container.getContainerSize();
			a.current[c] = new StorageLayout.Stack[sizes[c]];
			for (int slot = 0; slot < sizes[c]; slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty()) {
					a.current[c][slot] = new StorageLayout.Stack(a.index.getInt(stack), stack.getCount());
				}
			}
		}
		a.rules = rules(a);
		if (group.content()) {
			// Filtered room: nothing leaves a chest, each chest is laid out with its own items.
			a.target = ownLayouts(a, sizes, StorageGroups.fill(guild));
		} else {
			markOut(level, guild, a);
			SortedMap<Integer, Long> totals = StorageLayout.totals(a.current, null);
			totals.keySet().removeAll(a.out);
			a.target = StorageLayout.layout(sizes, totals, a.rules, StorageGroups.fill(guild));
		}
		a.sections = ChestOrder.sections(a.units, config.reach, config.maxChestsPerStep, a.busy);
		boolean hasOut = false;
		for (StorageLayout.Stack[] container : a.current) {
			for (StorageLayout.Stack stack : container) {
				hasOut |= stack != null && a.out.contains(stack.key());
			}
		}
		a.tidy = a.target == null ? !hasOut : StorageLayout.sameLayout(a.current, a.target);
		return a;
	}

	/**
	 * Target of a filtered room's chests: each chest laid out with exactly its own items (a chest whose items do not fit
	 * a layout keeps its current contents).
	 */
	private static StorageLayout.Stack[][] ownLayouts(Audit a, int[] sizes, StorageLayout.Fill fill) {
		StorageLayout.Stack[][] target = new StorageLayout.Stack[sizes.length][];
		for (int c = 0; c < sizes.length; c++) {
			int container = c;
			StorageLayout.Rules local = new StorageLayout.Rules() {
				@Override
				public int maxStack(int key) {
					return a.rules.maxStack(key);
				}

				@Override
				public int kind(int key) {
					return a.rules.kind(key);
				}

				@Override
				public boolean accepts(int ignored, int key) {
					return a.rules.accepts(container, key);
				}
			};
			StorageLayout.Stack[][] own = StorageLayout.layout(new int[]{sizes[c]}, StorageLayout.totals(a.current, new int[]{c}), local, fill);
			target[c] = own == null ? a.current[c].clone() : own[0];
		}
		return target;
	}

	/**
	 * New contents of a section: {@link StorageLayout#arrange}, except in a filtered room where each chest is arranged
	 * with its own items only (null when nothing fits).
	 */
	private static StorageLayout.Stack @Nullable [][] arrangeSection(Audit a, int[] containers) {
		if (!a.group.content()) {
			return StorageLayout.arrange(containers, a.target, a.current, a.rules);
		}
		StorageLayout.Stack[][] result = new StorageLayout.Stack[containers.length][];
		for (int i = 0; i < containers.length; i++) {
			StorageLayout.Stack[][] own = StorageLayout.arrange(new int[]{containers[i]}, a.target, a.current, a.rules);
			result[i] = own == null ? a.current[containers[i]].clone() : own[0];
		}
		return result;
	}

	/**
	 * Marks the keys to take out of the group: items with a better destination with room elsewhere
	 * ({@link StorageRouting}: a filter chest accepting them before the chests of their categories, a transversal or
	 * sub-category before its parent, a category before the fallback). Items a filter group refuses may go back to a
	 * deposit when no other chest takes them; items of another category in a category group stay when they have no other
	 * destination. Uses the routing index: only the chests of each item's better tiers are looked at.
	 */
	private static void markOut(ServerLevel level, Guild guild, Audit a) {
		StorageGroups.Group group = a.group;
		StorageRouting.Target home;
		if (group.filter() != null) {
			home = StorageRouting.Target.filter(group.filter());
		} else if (group.category().equalsIgnoreCase(Storage.categories().fallback())) {
			home = StorageRouting.Target.fallback();
		} else {
			home = StorageRouting.Target.category(group.category());
		}
		int maxTier = group.isFilter() ? StorageRouting.DEPOSIT_TIER : StorageRouting.FALLBACK_TIER;
		Set<BlockPos> members = new HashSet<>();
		for (StorageChest chest : group.chests()) {
			members.add(chest.pos);
		}
		StorageIndex index = Storage.index(guild);
		StorageRouting.Index routing = index.routing();
		Vec3 from = a.chests.isEmpty() ? Vec3.ZERO : Vec3.atCenterOf(a.chests.getFirst().pos);
		for (int k = 0; k < a.keys.size(); k++) {
			ItemStack stack = a.keys.get(k);
			String id = Storage.itemId(stack);
			String kind = RoomContents.key(stack);
			List<String> chain = Storage.chain(stack);
			int homeTier = StorageRouting.tier(home, id, kind, chain);
			if (homeTier == 0) {
				continue;
			}
			// Without reading any chest: could a better destination exist at all (a chest of a better tier outside the group)?
			int possible = routing.bestTier(id, kind, chain, Math.min(maxTier, homeTier - 1), target -> !members.contains(index.chest(target).pos));
			if (possible >= homeTier || possible > maxTier) {
				continue;
			}
			StorageChest destination = Storage.route(level, guild, from, stack, Set.of(), maxTier);
			if (destination != null && Storage.tier(guild, destination, stack) < homeTier) {
				a.out.add(k);
			}
		}
	}

	private static StorageLayout.Rules rules(Audit a) {
		// Largest stack of each key in every container of the group, computed once.
		int[] maxStacks = new int[a.keys.size()];
		// Kind of each key: the same item (with other components or not) = the same kind.
		int[] kinds = new int[a.keys.size()];
		Map<Item, Integer> kindOf = new HashMap<>();
		for (int key = 0; key < maxStacks.length; key++) {
			ItemStack stack = a.keys.get(key);
			int max = stack.getMaxStackSize();
			for (Container container : a.containers) {
				max = Math.min(max, container.getMaxStackSize(stack));
			}
			maxStacks[key] = Math.max(1, max);
			kinds[key] = kindOf.computeIfAbsent(stack.getItem(), item -> kindOf.size());
		}
		return new StorageLayout.Rules() {
			@Override
			public int maxStack(int key) {
				return maxStacks[key];
			}

			@Override
			public int kind(int key) {
				return kinds[key];
			}

			@Override
			public boolean accepts(int container, int key) {
				ItemStack stack = a.keys.get(key);
				Container target = a.containers.get(container);
				if (target instanceof ShulkerBoxBlockEntity && !stack.getItem().canFitInsideContainerItems()) {
					return false;
				}
				return Storage.canPlace(target, 0, stack);
			}
		};
	}

	// ================================================================ choosing the work

	/**
	 * Audits the next groups (bounded) and returns the first piece of work, leased to this sorter. Groups found tidy
	 * are not audited again before {@code tidyIntervalSeconds}.
	 */
	static Next next(WorkContext ctx) {
		VillagerConfig.Sorter config = ctx.config.sorter;
		List<StorageGroups.Group> groups = StorageGroups.groups(ctx.level, ctx.guild);
		StorageIndex index = Storage.index(ctx.guild);
		GuildState state = state(ctx.guild);
		Set<String> keys = new HashSet<>();
		for (StorageGroups.Group group : groups) {
			keys.add(group.key());
		}
		state.status.keySet().retainAll(keys);
		if (groups.isEmpty()) {
			return Next.ALL_TIDY;
		}
		List<StorageGroups.Group> queue = new ArrayList<>();
		StorageGroups.Group priority = null;
		if (state.priority != null && ctx.now < state.priorityUntil) {
			for (StorageGroups.Group group : groups) {
				if (group.key().equals(state.priority)) {
					priority = group;
				}
			}
		}
		if (priority == null) {
			state.priority = null;
		} else {
			queue.add(priority);
		}
		int start = Math.floorMod(state.cursor, groups.size());
		for (int i = 0; i < groups.size(); i++) {
			StorageGroups.Group group = groups.get((start + i) % groups.size());
			if (group != priority) {
				queue.add(group);
			}
		}
		UUID me = ctx.villager.getUUID();
		long recheck = config.tidyIntervalSeconds * 20L;
		int audited = 0;
		String busy = null;
		boolean pending = false;
		for (StorageGroups.Group group : queue) {
			if (leasedByOther(ctx.guild, group.key(), me, ctx.now)) {
				pending = true;
				continue;
			}
			Status known = state.status.get(group.key());
			if (group != priority && known != null && known.tidy() && ctx.now - known.checkedAt() < recheck) {
				continue;
			}
			if (audited > 0 && audited + group.chests().size() > config.auditChestsPerStep) {
				// Budget spent: the rest next second.
				return Next.CONTINUE;
			}
			Audit audit = audit(ctx.level, ctx.guild, group);
			audited += Math.max(1, audit.chests.size());
			state.status.put(group.key(), new Status(audit.tidy, audit.anyBusy(), ctx.now));
			if (group != priority) {
				state.cursor = index.groupPosition(group.key()) + 1;
			} else if (audit.tidy) {
				state.priority = null;
			}
			if (audit.tidy) {
				continue;
			}
			Job job = plan(ctx, audit);
			if (job != null && lease(ctx.guild, group.key(), me, ctx.now, config.leaseSeconds)) {
				return new Next(job, false, null);
			}
			if (audit.anyBusy()) {
				busy = group.category();
			}
		}
		if (busy != null) {
			return new Next(null, false, busy);
		}
		return pending ? Next.CONTINUE : Next.ALL_TIDY;
	}

	/** First piece of work of an untidy group: misplaced items out, then sections to reorganise, then carrying. */
	static @Nullable Job plan(WorkContext ctx, Audit a) {
		int total = a.chests.size();
		// 1. Items of another category (nearest chest first).
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int c = 0; c < total; c++) {
			if (a.busy[c] || !holdsOut(a, c)) {
				continue;
			}
			double dist = ctx.distSq(a.chests.get(c).pos);
			if (dist < bestDist) {
				bestDist = dist;
				best = c;
			}
		}
		if (best >= 0) {
			BlockPos pos = a.chests.get(best).pos;
			return new Job(Kind.TAKE_OUT, a.group.key(), a.group.category(), pos, List.of(pos), List.of(), best + 1, total);
		}
		if (a.target == null) {
			return null;
		}
		// 2. Sections whose own items are not in place (nearest first).
		List<Integer> section = null;
		bestDist = Double.MAX_VALUE;
		for (List<Integer> candidate : a.sections) {
			int[] containers = toArray(candidate);
			StorageLayout.Stack[][] arranged = arrangeSection(a, containers);
			if (arranged == null || !changes(a, containers, arranged)) {
				continue;
			}
			double dist = ctx.distSq(a.chests.get(candidate.getFirst()).pos);
			if (dist < bestDist) {
				bestDist = dist;
				section = candidate;
			}
		}
		if (section != null) {
			return new Job(Kind.ARRANGE, a.group.key(), a.group.category(), standingSpot(ctx.level, a, section), positions(a, section),
					List.of(), section.getFirst() + 1, total);
		}
		if (a.group.content()) {
			// A filtered room: nothing is carried from one chest to another.
			return null;
		}
		// 3. Items to carry between sections (source nearest first).
		List<int[]> sections = new ArrayList<>();
		for (List<Integer> candidate : a.sections) {
			sections.add(toArray(candidate));
		}
		StorageLayout.Transfer chosen = null;
		bestDist = Double.MAX_VALUE;
		for (StorageLayout.Transfer transfer : StorageLayout.transfers(sections, a.target, a.current)) {
			double dist = ctx.distSq(a.chests.get(sections.get(transfer.from())[0]).pos);
			if (dist < bestDist) {
				bestDist = dist;
				chosen = transfer;
			}
		}
		if (chosen != null) {
			List<Integer> from = a.sections.get(chosen.from());
			List<Integer> to = a.sections.get(chosen.to());
			return new Job(Kind.CARRY, a.group.key(), a.group.category(), sourceChest(a, from, chosen.key()), positions(a, from),
					positions(a, to), from.getFirst() + 1, total);
		}
		return null;
	}

	private static boolean holdsOut(Audit a, int container) {
		for (StorageLayout.Stack stack : a.current[container]) {
			if (stack != null && a.out.contains(stack.key())) {
				return true;
			}
		}
		return false;
	}

	private static boolean changes(Audit a, int[] containers, StorageLayout.Stack[][] arranged) {
		for (int i = 0; i < containers.length; i++) {
			if (!StorageLayout.sameContainer(arranged[i], a.current[containers[i]])) {
				return true;
			}
		}
		return false;
	}

	/** Chest of the section holding the most of {@code key} (where the sorter walks to). */
	private static BlockPos sourceChest(Audit a, List<Integer> section, int key) {
		int best = section.getFirst();
		long bestCount = -1;
		for (int c : section) {
			long count = 0;
			for (StorageLayout.Stack stack : a.current[c]) {
				count += stack != null && stack.key() == key ? stack.count() : 0;
			}
			if (count > bestCount) {
				bestCount = count;
				best = c;
			}
		}
		return a.chests.get(best).pos;
	}

	/**
	 * Where to stand to reach a section: in front of the middle of the section (facing of most of its containers) at
	 * the height of its lowest container; the first container itself when no safe spot is found.
	 */
	private static BlockPos standingSpot(ServerLevel level, Audit a, List<Integer> section) {
		List<ChestOrder.Unit> units = new ArrayList<>();
		Map<ChestOrder.Facing, Integer> votes = new HashMap<>();
		int minY = Integer.MAX_VALUE;
		for (int c : section) {
			ChestOrder.Unit unit = a.units.get(c);
			units.add(unit);
			minY = Math.min(minY, unit.y());
			if (unit.facing() != null) {
				votes.merge(unit.facing(), 1, Integer::sum);
			}
		}
		double[] center = ChestOrder.center(units);
		ChestOrder.Facing facing = null;
		int bestVotes = 0;
		for (Map.Entry<ChestOrder.Facing, Integer> entry : votes.entrySet()) {
			if (entry.getValue() > bestVotes) {
				bestVotes = entry.getValue();
				facing = entry.getKey();
			}
		}
		double x = center[0] + (facing == null ? 0 : facing.dx * 1.5);
		double z = center[2] + (facing == null ? 0 : facing.dz * 1.5);
		BlockPos front = BlockPos.containing(x, minY, z);
		BlockPos spot = WorldUtil.findSafeSpot(level, front, 2, false, pos -> true);
		return spot != null ? spot : a.chests.get(section.getFirst()).pos;
	}

	private static List<BlockPos> positions(Audit a, List<Integer> section) {
		List<BlockPos> positions = new ArrayList<>(section.size());
		for (int c : section) {
			positions.add(a.chests.get(c).pos);
		}
		return positions;
	}

	private static int[] toArray(List<Integer> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = list.get(i);
		}
		return array;
	}

	// ================================================================ doing the work

	/** Fresh audit of the job's group (the world may have changed while walking), or null if the group is gone. */
	static @Nullable Audit reaudit(ServerLevel level, Guild guild, Job job) {
		StorageGroups.Group group = StorageGroups.group(level, guild, job.groupKey());
		return group == null ? null : audit(level, guild, group);
	}

	/** Indices of the positions in the audit; null when one is missing (unloaded, removed) or busy. */
	private static int @Nullable [] indices(Audit a, List<BlockPos> positions) {
		int[] indices = new int[positions.size()];
		for (int i = 0; i < indices.length; i++) {
			int index = a.indexOf(positions.get(i));
			if (index < 0 || a.busy[index]) {
				return null;
			}
			indices[i] = index;
		}
		return indices;
	}

	/** True when one of the containers is in use (the step waits). */
	static boolean anyInUse(ServerLevel level, List<BlockPos> positions) {
		List<Container> shown = Storage.shownContainers(level);
		for (BlockPos pos : positions) {
			if (Storage.inUse(level, pos, shown)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ARRANGE: reorganises the section in one atomic step.
	 *
	 * @return number of items that changed place (0: nothing done)
	 */
	static int arrange(ServerLevel level, Guild guild, Job job) {
		Audit a = reaudit(level, guild, job);
		if (a == null || a.target == null) {
			return 0;
		}
		int[] containers = indices(a, job.chests());
		if (containers == null) {
			return 0;
		}
		StorageLayout.Stack[][] arranged = arrangeSection(a, containers);
		if (arranged == null) {
			return 0;
		}
		if (a.group.content()) {
			// Each chest of a filtered room keeps exactly its own items (so every kind it holds).
			for (int i = 0; i < containers.length; i++) {
				if (!StorageLayout.totals(new StorageLayout.Stack[][]{arranged[i]}, null)
						.equals(StorageLayout.totals(a.current, new int[]{containers[i]}))) {
					return 0;
				}
			}
		}
		return apply(a, containers, arranged);
	}

	/**
	 * Writes new contents into some containers of an audit, only if they hold exactly the same items; the containers are
	 * counted again afterwards and restored on any mismatch.
	 *
	 * @return number of items written to a slot that held something else
	 */
	static int apply(Audit a, int[] containers, StorageLayout.Stack[][] contents) {
		if (!StorageLayout.totals(contents, null).equals(StorageLayout.totals(a.current, containers))) {
			return 0;
		}
		List<ItemStack[]> snapshot = new ArrayList<>();
		for (int c : containers) {
			Container container = a.containers.get(c);
			ItemStack[] copy = new ItemStack[container.getContainerSize()];
			for (int slot = 0; slot < copy.length; slot++) {
				copy[slot] = container.getItem(slot).copy();
			}
			snapshot.add(copy);
		}
		Object2LongOpenCustomHashMap<ItemStack> before = count(snapshot);
		int moved = 0;
		for (int i = 0; i < containers.length; i++) {
			Container container = a.containers.get(containers[i]);
			boolean changed = false;
			for (int slot = 0; slot < container.getContainerSize() && slot < contents[i].length; slot++) {
				StorageLayout.Stack want = contents[i][slot];
				ItemStack now = container.getItem(slot);
				boolean same = want == null ? now.isEmpty()
						: !now.isEmpty() && now.getCount() == want.count() && ItemStack.isSameItemSameComponents(now, a.keys.get(want.key()));
				if (!same) {
					container.setItem(slot, want == null ? ItemStack.EMPTY : a.keys.get(want.key()).copyWithCount(want.count()));
					moved += want == null ? 0 : want.count();
					changed = true;
				}
			}
			if (changed) {
				container.setChanged();
			}
		}
		List<ItemStack[]> after = new ArrayList<>();
		for (int c : containers) {
			Container container = a.containers.get(c);
			ItemStack[] copy = new ItemStack[container.getContainerSize()];
			for (int slot = 0; slot < copy.length; slot++) {
				copy[slot] = container.getItem(slot);
			}
			after.add(copy);
		}
		if (!before.equals(count(after))) {
			// Never lose nor duplicate anything: put everything back.
			for (int i = 0; i < containers.length; i++) {
				Container container = a.containers.get(containers[i]);
				ItemStack[] copy = snapshot.get(i);
				for (int slot = 0; slot < copy.length; slot++) {
					container.setItem(slot, copy[slot]);
				}
				container.setChanged();
			}
			Guildy.LOGGER.warn("Storage: tidying of '{}' cancelled, the item counts did not match", a.group.category());
			return 0;
		}
		return moved;
	}

	/** Items per distinct stack (same item, same components). */
	private static Object2LongOpenCustomHashMap<ItemStack> count(List<ItemStack[]> containers) {
		Object2LongOpenCustomHashMap<ItemStack> counts = new Object2LongOpenCustomHashMap<>(StorageSort.SAME_ITEM);
		for (ItemStack[] container : containers) {
			for (ItemStack stack : container) {
				if (!stack.isEmpty()) {
					counts.addTo(stack.copyWithCount(1), stack.getCount());
				}
			}
		}
		return counts;
	}

	/**
	 * TAKE_OUT: moves up to {@code maxStacks} stacks of other categories from the chest into the bag (last slots
	 * first).
	 *
	 * @return number of stacks taken
	 */
	static int takeOut(WorkContext ctx, Job job, int maxStacks) {
		Audit a = reaudit(ctx.level, ctx.guild, job);
		if (a == null || a.group.content()) {
			return 0;
		}
		int[] containers = indices(a, job.chests());
		if (containers == null) {
			return 0;
		}
		Container container = a.containers.get(containers[0]);
		int taken = 0;
		for (int slot = container.getContainerSize() - 1; slot >= 0 && taken < maxStacks; slot--) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && a.out.contains(a.key(stack))) {
				ctx.record.carried.add(stack.copy());
				container.setItem(slot, ItemStack.EMPTY);
				taken++;
			}
		}
		if (taken > 0) {
			container.setChanged();
			GuildManager.markDirty();
		}
		return taken;
	}

	/**
	 * CARRY, at the source section: takes the items the destination section misses (at most {@code maxStacks} new
	 * stacks in the bag, last slots first).
	 *
	 * @return number of items taken
	 */
	static int carryPickup(WorkContext ctx, Job job, int maxStacks) {
		Audit a = reaudit(ctx.level, ctx.guild, job);
		if (a == null || a.target == null || a.group.content()) {
			return 0;
		}
		int[] from = indices(a, job.chests());
		int[] to = indices(a, job.destination());
		if (from == null || to == null) {
			return 0;
		}
		int bagBefore = ctx.record.carried.size();
		int taken = 0;
		for (StorageLayout.Transfer transfer : StorageLayout.transfers(List.of(from, to), a.target, a.current)) {
			if (transfer.from() != 0) {
				continue;
			}
			ItemStack prototype = a.keys.get(transfer.key());
			int max = a.rules.maxStack(transfer.key());
			long left = transfer.count();
			for (int i = from.length - 1; i >= 0 && left > 0; i--) {
				Container container = a.containers.get(from[i]);
				boolean changed = false;
				for (int slot = container.getContainerSize() - 1; slot >= 0 && left > 0; slot--) {
					ItemStack stack = container.getItem(slot);
					if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
						continue;
					}
					int amount = (int) Math.min(left, stack.getCount());
					amount = Math.min(amount, bagRoom(ctx.record.carried, prototype, max, bagBefore + maxStacks));
					if (amount <= 0) {
						break;
					}
					ItemStack moved = stack.split(amount);
					container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
					addToBag(ctx.record.carried, moved, max);
					left -= amount;
					taken += amount;
					changed = true;
				}
				if (changed) {
					container.setChanged();
				}
			}
		}
		if (taken > 0) {
			GuildManager.markDirty();
		}
		return taken;
	}

	/** How many items of this kind the bag can still take without going over {@code maxStacks} stacks. */
	private static int bagRoom(List<ItemStack> bag, ItemStack prototype, int max, int maxStacks) {
		int room = 0;
		for (ItemStack carried : bag) {
			if (ItemStack.isSameItemSameComponents(carried, prototype)) {
				room += Math.max(0, max - carried.getCount());
			}
		}
		return room + Math.max(0, maxStacks - bag.size()) * max;
	}

	private static void addToBag(List<ItemStack> bag, ItemStack stack, int max) {
		for (ItemStack carried : bag) {
			if (stack.isEmpty()) {
				return;
			}
			if (ItemStack.isSameItemSameComponents(carried, stack) && carried.getCount() < max) {
				int move = Math.min(stack.getCount(), max - carried.getCount());
				carried.grow(move);
				stack.shrink(move);
			}
		}
		while (!stack.isEmpty()) {
			bag.add(stack.split(Math.min(stack.getCount(), max)));
		}
	}

	/**
	 * CARRY, at the destination section: puts the carried items of the group into its chests (stacks merged first); when
	 * there is no room, each carried stack takes the slot of an item the section must give away, which goes into the
	 * bag. Containers in use are skipped. Carried items of other groups stay in the bag.
	 *
	 * @return number of items put in
	 */
	static int deliver(WorkContext ctx, Job job) {
		Audit a = reaudit(ctx.level, ctx.guild, job);
		if (a == null) {
			return 0;
		}
		List<Integer> targets = new ArrayList<>();
		for (BlockPos pos : job.destination()) {
			int index = a.indexOf(pos);
			if (index >= 0 && !a.busy[index]) {
				targets.add(index);
			}
		}
		if (targets.isEmpty()) {
			return 0;
		}
		int put = 0;
		List<ItemStack> leftovers = new ArrayList<>();
		Iterator<ItemStack> it = ctx.record.carried.iterator();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			if (!belongs(a, stack)) {
				continue;
			}
			it.remove();
			ItemStack left = stack;
			for (int c : targets) {
				if (left.isEmpty()) {
					break;
				}
				int before = left.getCount();
				left = Storage.insert(a.containers.get(c), left);
				put += before - left.getCount();
			}
			if (!left.isEmpty()) {
				leftovers.add(left);
			}
		}
		if (!leftovers.isEmpty() && a.target != null) {
			put += swap(ctx, a, toArray(targets), leftovers);
		}
		ctx.record.carried.addAll(leftovers);
		if (put > 0) {
			GuildManager.markDirty();
		}
		return put;
	}

	/** True when the stack belongs in this group (one of its keys that stays, an item its filter accepts or of its category). */
	private static boolean belongs(Audit a, ItemStack stack) {
		if (a.group.content()) {
			// Nothing is ever carried into the chests of a filtered room by the tidying.
			return false;
		}
		int key = a.key(stack);
		if (key >= 0) {
			return !a.out.contains(key);
		}
		if (a.group.filter() != null) {
			return a.group.filter().accepts(Storage.itemId(stack));
		}
		for (String category : Storage.chain(stack)) {
			if (category.equalsIgnoreCase(a.group.category())) {
				return true;
			}
		}
		return false;
	}

	/** Leftover carried stacks take the slots of the section's extra items (into the bag in exchange). */
	private static int swap(WorkContext ctx, Audit a, int[] section, List<ItemStack> leftovers) {
		// The section's extra items: keys it holds beyond its part of the target, and items to take out.
		SortedMap<Integer, Long> held = StorageLayout.totals(a.current, section);
		SortedMap<Integer, Long> wanted = StorageLayout.totals(a.target, section);
		Map<Integer, Long> extra = new HashMap<>();
		for (Map.Entry<Integer, Long> entry : held.entrySet()) {
			long more = a.out.contains(entry.getKey()) ? entry.getValue() : entry.getValue() - wanted.getOrDefault(entry.getKey(), 0L);
			if (more > 0) {
				extra.put(entry.getKey(), more);
			}
		}
		int put = 0;
		Iterator<ItemStack> it = leftovers.iterator();
		while (it.hasNext()) {
			ItemStack leftover = it.next();
			search:
			for (int i = section.length - 1; i >= 0; i--) {
				Container container = a.containers.get(section[i]);
				for (int slot = container.getContainerSize() - 1; slot >= 0; slot--) {
					if (leftover.isEmpty()) {
						break search;
					}
					ItemStack inside = container.getItem(slot);
					int key = a.key(inside);
					if (key < 0 || extra.getOrDefault(key, 0L) < inside.getCount() || !Storage.canPlace(container, slot, leftover)) {
						continue;
					}
					int fits = Math.min(leftover.getCount(), container.getMaxStackSize(leftover));
					extra.merge(key, (long) -inside.getCount(), Long::sum);
					ctx.record.carried.add(inside.copy());
					container.setItem(slot, leftover.split(fits));
					container.setChanged();
					put += fits;
				}
			}
			if (leftover.isEmpty()) {
				it.remove();
			}
		}
		return put;
	}
}
