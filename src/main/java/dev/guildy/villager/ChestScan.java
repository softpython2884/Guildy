package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chat scan of the containers around a player ({@code /guild villagers storage scan <chunks> [remplacer]}): the loaded
 * chunks of the guild territory within the radius are read a few per tick (nearest first, at most
 * {@code storage.scanMaxContainers} containers), each non-empty container gets a proposal ({@link ChestFilters#propose}:
 * a filter, else a category chest, else nothing), registered containers are skipped (unless {@code replace}; deposits
 * always). The preview lists the proposals in the chat (hover: position, filter, items seen; click: particles) with
 * [Appliquer] / [Annuler] valid {@code storage.scanPreviewSeconds}; applying registers everything at once (names that
 * never replace a player's own, groups, tidying). Nothing is saved: a restart forgets the previews.
 */
final class ChestScan {
	/** Entries listed per chat line, and in all. */
	private static final int PER_LINE = 6;
	private static final int MAX_LISTED = 90;
	/** Items listed in the hover of an entry. */
	private static final int HOVER_ITEMS = 8;
	/** Containers read per tick at most (a room of a hundred chests takes a few ticks). */
	private static final int CONTAINERS_PER_TICK = 32;

	/** One item kind seen in a container: a prototype stack, its total count and its number of stacks. */
	record ItemSeen(ItemStack prototype, int count, int stacks) {
	}

	/** One container read by the scan and what is proposed for it. */
	record Found(BlockPos pos, boolean doubleChest, ChestFilters.Proposal proposal, List<ItemSeen> items, @Nullable String previous) {
	}

	/** A scan of a player: chunks still to read, then the preview. */
	static final class Session {
		final int id;
		final UUID player;
		final UUID guild;
		final boolean replace;
		final int radius;
		final ArrayDeque<Long> chunks = new ArrayDeque<>();
		/** Containers of the chunk being read. */
		final ArrayDeque<BlockPos> positions = new ArrayDeque<>();
		final Set<BlockPos> seen = new HashSet<>();
		final List<Found> found = new ArrayList<>();
		int containers;
		int registered;
		int unloaded;
		boolean truncated;
		boolean done;
		long expiresAt;

		Session(int id, UUID player, UUID guild, boolean replace, int radius) {
			this.id = id;
			this.player = player;
			this.guild = guild;
			this.replace = replace;
			this.radius = radius;
		}

		int count(ChestFilters.Outcome outcome, ChestFilter.@Nullable Mode mode) {
			int count = 0;
			for (Found f : found) {
				if (f.proposal().outcome() == outcome && (mode == null || (f.proposal().filter() != null && familyKind(f.proposal().filter()) == familyKind(mode)))) {
					count++;
				}
			}
			return count;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static int nextId = 1;

	private ChestScan() {
	}

	static void init() {
		Scheduler.every("villager.storage.scan", 1, 0, ChestScan::tick);
	}

	static void clear() {
		SESSIONS.clear();
	}

	static void forget(UUID player) {
		SESSIONS.remove(player);
	}

	/** True when the scan of this player is finished and its preview waits for [Appliquer] (GameTests). */
	static boolean ready(UUID player) {
		Session session = SESSIONS.get(player);
		return session != null && session.done;
	}

	/** True for family filters (material or type), false for exact items. */
	private static boolean familyKind(ChestFilter filter) {
		return familyKind(filter.mode);
	}

	private static boolean familyKind(ChestFilter.Mode mode) {
		return mode != ChestFilter.Mode.ITEMS;
	}

	// ================================================================ start

	/** Starts a scan of the containers within {@code radius} chunks (replaces the player's previous preview). */
	static void start(ServerPlayer player, int radius, boolean replace) {
		Guild guild = permitted(player);
		if (guild == null) {
			return;
		}
		VillagerConfig.Storage config = VillagerModule.config().storage;
		if (radius < 1 || radius > config.scanMaxChunks) {
			Msg.send(player, "villager.storage.scan.usage", config.scanMaxChunks);
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || player.level() != level) {
			Msg.send(player, "villager.storage.scan.wrong_level");
			return;
		}
		Session session = new Session(nextId++, player.getUUID(), guild.id, replace, radius);
		int cx = player.blockPosition().getX() >> 4;
		int cz = player.blockPosition().getZ() >> 4;
		List<long[]> chunks = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				long key = ChunkKey.of(cx + dx, cz + dz);
				if (GuildManager.atChunk(key) == guild) {
					chunks.add(new long[]{key, (long) dx * dx + (long) dz * dz});
				}
			}
		}
		if (chunks.isEmpty()) {
			Msg.send(player, "villager.storage.scan.no_chunk");
			return;
		}
		chunks.sort(Comparator.comparingLong(c -> c[1]));
		for (long[] chunk : chunks) {
			session.chunks.add(chunk[0]);
		}
		SESSIONS.put(player.getUUID(), session);
		Msg.send(player, "villager.storage.scan.started", radius, chunks.size());
	}

	/** The player's guild when the player may manage its villagers (messages otherwise). */
	private static @Nullable Guild permitted(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return null;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return null;
		}
		return guild;
	}

	// ================================================================ reading, spread over ticks

	private static void tick() {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = Scheduler.ticks();
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Session session = it.next().getValue();
			ServerPlayer player = GuildyServer.player(session.player);
			Guild guild = GuildManager.get(session.guild);
			if (player == null || guild == null || GuildManager.of(player) != guild || (session.done && now > session.expiresAt)) {
				it.remove();
				continue;
			}
			if (!session.done) {
				step(player, guild, session, now);
			}
		}
	}

	/**
	 * Reads the next containers of a scan (at most {@code scanChunksPerTick} chunks opened and
	 * {@link #CONTAINERS_PER_TICK} containers read per tick); the preview once everything is read.
	 */
	private static void step(ServerPlayer player, Guild guild, Session session, long now) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			session.chunks.clear();
			session.positions.clear();
		}
		int chunks = config.scanChunksPerTick;
		int containers = CONTAINERS_PER_TICK;
		while (level != null && containers > 0) {
			if (session.positions.isEmpty()) {
				if (session.chunks.isEmpty() || chunks == 0) {
					break;
				}
				chunks--;
				long key = session.chunks.poll();
				LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkKey.x(key), ChunkKey.z(key));
				if (chunk == null) {
					session.unloaded++;
					continue;
				}
				List<BlockPos> positions = new ArrayList<>();
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					if (Storage.isStorageBlock(entry.getValue())) {
						positions.add(entry.getKey().immutable());
					}
				}
				positions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
				session.positions.addAll(positions);
				continue;
			}
			if (session.containers >= config.scanMaxContainers) {
				session.truncated = true;
				session.chunks.clear();
				session.positions.clear();
				break;
			}
			if (read(level, guild, session, session.positions.poll())) {
				containers--;
			}
		}
		if (session.chunks.isEmpty() && session.positions.isEmpty()) {
			session.done = true;
			session.expiresAt = now + config.scanPreviewSeconds * 20L;
			preview(player, session);
		}
	}

	/**
	 * Reads one container (both halves of a double chest once) and records its proposal.
	 *
	 * @return true when a container was counted
	 */
	private static boolean read(ServerLevel level, Guild guild, Session session, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (session.seen.contains(pos) || !Storage.isStorageBlock(blockEntity) || GuildManager.at(level, pos) != guild) {
			return false;
		}
		List<BlockPos> halves = Storage.halves(level, pos);
		session.seen.addAll(halves);
		session.containers++;
		StorageChest existing = Storage.registered(level, guild, pos);
		if (existing != null && (!session.replace || existing.isDeposit())) {
			session.registered++;
			return true;
		}
		Container container = Storage.container(level, pos);
		if (container == null) {
			return true;
		}
		VillagerConfig.Storage config = VillagerModule.config().storage;
		List<ItemSeen> items = contents(container);
		List<ChestFilters.Seen> seen = new ArrayList<>(items.size());
		for (ItemSeen item : items) {
			seen.add(new ChestFilters.Seen(Storage.itemId(item.prototype()), Storage.categoryOf(item.prototype()), item.stacks()));
		}
		ChestFilters.Proposal proposal = ChestFilters.propose(seen, config.filterMaxItems, config.scanCategoryPercent, Storage.categories().fallback());
		// The registration stays on the half that was registered (a double chest registered on its other half).
		BlockPos at = existing != null ? existing.pos : pos;
		session.found.add(new Found(at, halves.size() > 1, proposal, items, existing == null ? null : Storage.roleName(existing)));
		return true;
	}

	/** Item kinds of a container in slot order (first seen first), with counts and stacks. */
	static List<ItemSeen> contents(Container container) {
		Map<String, ItemStack> prototypes = new LinkedHashMap<>();
		Map<String, int[]> counts = new HashMap<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String id = Storage.itemId(stack);
			prototypes.putIfAbsent(id, stack.copyWithCount(1));
			int[] count = counts.computeIfAbsent(id, k -> new int[2]);
			count[0] += stack.getCount();
			count[1]++;
		}
		List<ItemSeen> items = new ArrayList<>(prototypes.size());
		for (Map.Entry<String, ItemStack> entry : prototypes.entrySet()) {
			int[] count = counts.get(entry.getKey());
			items.add(new ItemSeen(entry.getValue(), count[0], count[1]));
		}
		return items;
	}

	// ================================================================ preview

	private static void preview(ServerPlayer player, Session session) {
		int families = session.count(ChestFilters.Outcome.FILTER, ChestFilter.Mode.MATERIAL);
		int lists = session.count(ChestFilters.Outcome.FILTER, ChestFilter.Mode.ITEMS);
		int categories = session.count(ChestFilters.Outcome.CATEGORY, null);
		int mixed = session.count(ChestFilters.Outcome.MIXED, null);
		int empty = session.count(ChestFilters.Outcome.EMPTY, null);
		Msg.send(player, "villager.storage.scan.done", session.containers);
		player.sendSystemMessage(Lang.get("villager.storage.scan.counts", families, lists, categories, mixed, empty, session.registered));
		if (session.unloaded > 0) {
			player.sendSystemMessage(Lang.get("villager.storage.scan.unloaded", session.unloaded));
		}
		if (session.truncated) {
			player.sendSystemMessage(Lang.get("villager.storage.scan.truncated", VillagerModule.config().storage.scanMaxContainers));
		}
		int[] listed = {0};
		list(player, session, "villager.storage.scan.list.family", f -> f.proposal().outcome() == ChestFilters.Outcome.FILTER
				&& familyKind(f.proposal().filter()), listed);
		list(player, session, "villager.storage.scan.list.items", f -> f.proposal().outcome() == ChestFilters.Outcome.FILTER
				&& !familyKind(f.proposal().filter()), listed);
		list(player, session, "villager.storage.scan.list.category", f -> f.proposal().outcome() == ChestFilters.Outcome.CATEGORY, listed);
		list(player, session, "villager.storage.scan.list.mixed", f -> f.proposal().outcome() == ChestFilters.Outcome.MIXED, listed);
		int applicable = families + lists + categories;
		if (applicable == 0) {
			Msg.send(player, "villager.storage.scan.nothing");
			if (empty + mixed > 0) {
				player.sendSystemMessage(button("villager.storage.scan.button.show", "/guild villagers storage scan show " + session.id,
						Lang.get("villager.storage.scan.button.show.hover")));
			}
			return;
		}
		MutableComponent buttons = Component.empty()
				.append(button("villager.storage.scan.button.apply", "/guild villagers storage scan apply " + session.id,
						Lang.get("villager.storage.scan.button.apply.hover", applicable)))
				.append(Component.literal("  "))
				.append(button("villager.storage.scan.button.cancel", "/guild villagers storage scan cancel " + session.id,
						Lang.get("villager.storage.scan.button.cancel.hover")))
				.append(Component.literal("  "))
				.append(button("villager.storage.scan.button.show", "/guild villagers storage scan show " + session.id,
						Lang.get("villager.storage.scan.button.show.hover")))
				.append(Lang.get("villager.storage.scan.valid", VillagerModule.config().storage.scanPreviewSeconds));
		player.sendSystemMessage(buttons);
	}

	private interface Selector {
		boolean test(Found found);
	}

	/** One title line then the matching entries, {@link #PER_LINE} per line (at most {@link #MAX_LISTED} in all). */
	private static void list(ServerPlayer player, Session session, String titleKey, Selector selector, int[] listed) {
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < session.found.size(); i++) {
			if (selector.test(session.found.get(i))) {
				indices.add(i);
			}
		}
		if (indices.isEmpty()) {
			return;
		}
		MutableComponent line = Lang.get(titleKey);
		int onLine = 0;
		int shown = 0;
		for (int index : indices) {
			if (listed[0] >= MAX_LISTED) {
				break;
			}
			if (onLine == PER_LINE) {
				player.sendSystemMessage(line);
				line = Component.literal("  ");
				onLine = 0;
			}
			line.append(entry(session, index)).append(Component.literal(" "));
			onLine++;
			shown++;
			listed[0]++;
		}
		if (shown < indices.size()) {
			line.append(Lang.get("villager.storage.scan.list.more", indices.size() - shown));
		}
		player.sendSystemMessage(line);
	}

	/** "[Diorite]": colour of the outcome, hover with the details, click = particles above the container. */
	private static MutableComponent entry(Session session, int index) {
		Found found = session.found.get(index);
		ChestFilters.Proposal proposal = found.proposal();
		MutableComponent name = switch (proposal.outcome()) {
			case FILTER -> FilterNames.component(proposal.filter());
			case CATEGORY -> Component.literal(proposal.category());
			default -> Component.literal(found.pos().getX() + " " + found.pos().getY() + " " + found.pos().getZ());
		};
		ChatFormatting colour = switch (proposal.outcome()) {
			case FILTER -> ChatFormatting.LIGHT_PURPLE;
			case CATEGORY -> ChatFormatting.YELLOW;
			case MIXED -> ChatFormatting.RED;
			case EMPTY -> ChatFormatting.DARK_GRAY;
		};
		MutableComponent label = Component.literal("[").append(name).append(Component.literal("]")).withStyle(colour);
		MutableComponent hover = Component.empty();
		hover.append(Lang.get("villager.storage.scan.hover.pos", found.pos().getX(), found.pos().getY(), found.pos().getZ()));
		if (found.doubleChest()) {
			hover.append(Component.literal(" ")).append(Lang.get("villager.storage.scan.hover.double"));
		}
		hover.append(Component.literal("\n"));
		switch (proposal.outcome()) {
			case FILTER -> {
				ChestFilter filter = proposal.filter();
				hover.append(Lang.get("villager.storage.scan.hover.filter")).append(FilterNames.styled(filter, ChatFormatting.LIGHT_PURPLE)).append(Component.literal("\n"));
				hover.append(switch (filter.mode) {
					case ITEMS -> Lang.get("villager.storage.scan.hover.accepts.items", filter.items.size());
					case MATERIAL -> Lang.get("villager.storage.scan.hover.accepts.material", filter.family);
					case TYPE -> Lang.get("villager.storage.scan.hover.accepts.type", filter.family);
				});
			}
			case CATEGORY -> hover.append(Lang.get("villager.storage.scan.hover.category", proposal.category()));
			case MIXED -> hover.append(Lang.get("villager.storage.scan.hover.mixed"));
			case EMPTY -> hover.append(Lang.get("villager.storage.scan.hover.empty"));
		}
		if (!found.items().isEmpty()) {
			hover.append(Component.literal("\n")).append(Lang.get("villager.storage.scan.hover.items"));
			int shown = 0;
			for (ItemSeen item : found.items()) {
				if (shown == HOVER_ITEMS) {
					hover.append(Component.literal("\n")).append(Lang.get("villager.storage.scan.hover.more_items", found.items().size() - shown));
					break;
				}
				hover.append(Component.literal("\n")).append(Lang.get("villager.storage.scan.hover.item"))
						.append(item.prototype().getHoverName()).append(Lang.get("villager.storage.scan.hover.item.count", item.count()));
				shown++;
			}
		}
		if (found.previous() != null) {
			hover.append(Component.literal("\n")).append(Lang.get("villager.storage.scan.hover.replaces", found.previous()));
		}
		hover.append(Component.literal("\n")).append(Lang.get("villager.storage.scan.hover.click"));
		String command = "/guild villagers storage scan show " + session.id + " " + index;
		return label.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static MutableComponent button(String labelKey, String command, Component hover) {
		return Lang.get(labelKey).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	// ================================================================ buttons

	/** The finished preview of the player ({@code id} -1 = the latest), or null (messages). */
	private static @Nullable Session pending(ServerPlayer player, int id) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || (id >= 0 && session.id != id)) {
			Msg.send(player, id >= 0 ? "villager.storage.scan.expired" : "villager.storage.scan.none");
			return null;
		}
		if (!session.done) {
			Msg.send(player, "villager.storage.scan.running");
			return null;
		}
		if (Scheduler.ticks() > session.expiresAt) {
			SESSIONS.remove(player.getUUID());
			Msg.send(player, "villager.storage.scan.expired");
			return null;
		}
		return session;
	}

	static void cancel(ServerPlayer player, int id) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || (id >= 0 && session.id != id)) {
			Msg.send(player, "villager.storage.scan.none");
			return;
		}
		SESSIONS.remove(player.getUUID());
		Msg.send(player, "villager.storage.scan.cancelled");
	}

	/** Particles above one analysed container ({@code index} &gt;= 0) or all of them. */
	static void show(ServerPlayer player, int id, int index) {
		Session session = pending(player, id);
		if (session == null) {
			return;
		}
		List<BlockPos> positions = new ArrayList<>();
		List<Character> codes = new ArrayList<>();
		for (int i = 0; i < session.found.size(); i++) {
			if (index >= 0 && i != index) {
				continue;
			}
			Found found = session.found.get(i);
			positions.add(found.pos());
			codes.add(switch (found.proposal().outcome()) {
				case FILTER -> 'd';
				case CATEGORY -> 'e';
				case MIXED -> 'c';
				case EMPTY -> '8';
			});
		}
		if (positions.isEmpty()) {
			return;
		}
		StorageVisualizer.showSpots(player, positions, codes);
		Msg.send(player, "villager.storage.scan.shown", VillagerModule.config().storage.showSeconds);
	}

	/** [Appliquer]: registers every proposed container (filters and categories) at once. */
	static void apply(ServerPlayer player, int id) {
		Session session = pending(player, id);
		if (session == null) {
			return;
		}
		Guild guild = permitted(player);
		ServerLevel level = GuildyServer.claimLevel();
		if (guild == null || level == null || !guild.id.equals(session.guild)) {
			return;
		}
		SESSIONS.remove(player.getUUID());
		int filters = 0;
		int categories = 0;
		int skipped = 0;
		int limited = 0;
		List<StorageChest> touched = new ArrayList<>();
		for (Found found : session.found) {
			ChestFilters.Proposal proposal = found.proposal();
			if (proposal.outcome() != ChestFilters.Outcome.FILTER && proposal.outcome() != ChestFilters.Outcome.CATEGORY) {
				continue;
			}
			BlockPos pos = found.pos();
			if (!level.isLoaded(pos) || !Storage.isStorageBlock(level.getBlockEntity(pos)) || GuildManager.at(level, pos) != guild) {
				skipped++;
				continue;
			}
			StorageChest chest = Storage.registered(level, guild, pos);
			if (chest != null && (!session.replace || chest.isDeposit())) {
				skipped++;
				continue;
			}
			boolean filter = proposal.outcome() == ChestFilters.Outcome.FILTER;
			ChestRoom room = roomAt(guild, pos);
			if (chest == null) {
				if (!room(guild, filter, room)) {
					limited++;
					continue;
				}
				chest = new StorageChest(pos, StorageChest.Role.CATEGORY, "");
				chest.room = room == null ? 0 : room.id;
				guild.villagers.chests.add(chest);
			}
			// A role chosen through the scan is manual (never reassigned by a chest room).
			chest.auto = false;
			if (filter) {
				chest.role = StorageChest.Role.FILTER;
				chest.filter = copy(proposal.filter());
				chest.category = "";
				filters++;
			} else {
				chest.role = StorageChest.Role.CATEGORY;
				chest.filter = null;
				chest.category = proposal.category();
				categories++;
			}
			touched.add(chest);
		}
		for (StorageChest chest : touched) {
			// Never over a name given by a player.
			StorageLabels.sync(level, guild, chest);
		}
		if (!touched.isEmpty()) {
			Tidier.invalidate(guild);
			GuildManager.markDirty();
		}
		Msg.send(player, "villager.storage.scan.applied", filters, categories);
		if (skipped > 0) {
			Msg.send(player, "villager.storage.scan.applied.skipped", skipped);
		}
		if (limited > 0) {
			Msg.send(player, "villager.storage.scan.applied.limit", limited);
		}
	}

	/** A fresh copy of a proposed filter (the preview keeps its own). */
	private static ChestFilter copy(ChestFilter filter) {
		ChestFilter copy = filter.mode == ChestFilter.Mode.ITEMS ? ChestFilter.items(filter.items)
				: ChestFilter.family(filter.mode, filter.family, filter.icon);
		copy.icon = filter.icon;
		return copy;
	}

	/** Chest room whose box holds the position, or null. */
	static @Nullable ChestRoom roomAt(Guild guild, BlockPos pos) {
		for (ChestRoom room : guild.villagers.rooms) {
			if (room.contains(pos)) {
				return room;
			}
		}
		return null;
	}

	/** True when the guild may register one more container of this kind (filter / category, in a room or not). */
	static boolean room(Guild guild, boolean filter, @Nullable ChestRoom room) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		if (filter) {
			return filterCount(guild) < config.maxFilterChests;
		}
		return room != null ? ChestRooms.roomChestCount(guild) < config.maxRoomChests : manualCount(guild) < config.maxChests;
	}

	/** Filter chests of the guild (their budget: storage.maxFilterChests). */
	static int filterCount(Guild guild) {
		int count = 0;
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.role == StorageChest.Role.FILTER) {
				count++;
			}
		}
		return count;
	}

	/** Containers outside the chest rooms, filter chests aside (their budget: storage.maxChests). */
	static int manualCount(Guild guild) {
		int count = 0;
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.room <= 0 && chest.role != StorageChest.Role.FILTER) {
				count++;
			}
		}
		return count;
	}
}
