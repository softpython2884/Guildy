package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps working guild villagers active when no player is around (villagers.json5 → chunkLoading). Every few seconds
 * the anchors of the working villagers (around themselves, where they are going, their work zone, the storage) are
 * planned by {@link ChunkLoadPlanner} within the caps and held as chunk tickets; tickets no longer planned are removed.
 * Idle villagers, villagers away on an expedition (until their return is due) and sleeping villagers hold nothing; at
 * dusk a villager keeps its chunks for {@code bedTravelSeconds} to reach its bed. Nothing is held in mode "never".
 * <p>
 * Tickets, from the 26.3 sources: a ticket of level L gives its chunk level L and the chunks at Chebyshev distance d
 * level L + d (ChunkTracker); a chunk is entity-ticking at level &lt;= 31 (ChunkLevel), on both the loading and the
 * simulation trackers (DistanceManager). {@code addTicketWithRadius(type, pos, r)} adds a ticket of level 33 - r, so an
 * entity-ticking square of radius R needs a vanilla radius R + 2 (a ring of block-ticking chunks and a ring of loaded
 * chunks surround it). The ticket type is a dedicated instance, not registered: loading + simulation + keeps the
 * dimension ticking (ServerLevel stops ticking entities 15 s after the last player left without such a ticket), not
 * persistent (never written to the chunk_tickets data), expiring 10 s after its last refresh. Being its own instance,
 * it never merges with vanilla ender pearl tickets nor with those of "Appeler ici" (same type and level = one ticket).
 * <p>
 * In these chunks crops and saplings grow (ServerChunkCache.tickChunks random-ticks every entity-ticking chunk) but no
 * mob spawns naturally (only chunks within 8 chunks / 128 blocks of a player spawn). A dedicated server pauses after
 * {@code pause-when-empty-seconds} without players (MinecraftServer.tickServer): then nothing runs at all.
 */
public final class WorkChunkLoader {
	/** Ticks without refresh before a ticket expires by itself (refreshSeconds is at most 5). */
	private static final long EXPIRY_TICKS = 200L;
	static final TicketType TICKET = new TicketType(EXPIRY_TICKS,
			TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
	/** Entity-ticking radius → vanilla ticket radius (ticket level 31 - radius). */
	private static final int VANILLA_RADIUS_OFFSET = 2;
	/** Walk target not refreshed for this long (ticks): the walk is over. */
	private static final long WALK_FRESH_TICKS = 100L;
	/** A player keeps min(view distance, simulation distance) chunks around it active; minus this safety margin. */
	private static final int PLAYER_MARGIN = 2;

	/** Ticket centre (packed chunk) → entity-ticking radius held in {@link #heldLevel}. */
	private static final Map<Long, Integer> HELD = new HashMap<>();
	/** Villager → scheduler tick when it was first seen resting (dusk), cleared once it works again. */
	private static final Map<UUID, Long> RESTING_SINCE = new HashMap<>();
	private static @Nullable ServerLevel heldLevel;
	private static ChunkLoadPlanner.Plan plan = ChunkLoadPlanner.Plan.EMPTY;
	private static int secondsToRefresh;

	private WorkChunkLoader() {
	}

	static void init() {
		ChunkLoadLang.register();
		Scheduler.every("villager.chunks", 20, 13, WorkChunkLoader::tick);
		Lifecycle.STARTED.add(server -> {
			forget();
			warnPause(server);
		});
		Lifecycle.STOPPING.add(server -> releaseAll());
		Lifecycle.RELOADED.add(() -> {
			// Mode, radius or caps changed: the next plan replaces the tickets at once ("never" releases everything).
			refresh();
			MinecraftServer server = GuildyServer.server();
			if (server != null) {
				warnPause(server);
			}
		});
		// The guild is removed right after this event: plan again without it.
		GuildEvents.DISBANDED.add(guild -> Scheduler.later("villager.chunks.disband", 1, WorkChunkLoader::refresh));
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("villager")
				.then(Commands.literal("chunks").executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					return Safe.get("villager.command.chunks", () -> report(source), 0);
				}))));
	}

	// ================================================================ queries

	/** Chunks kept active for the guild's villagers by the last refresh (0 when none). */
	public static int chunks(Guild guild) {
		return plan.chunks(guild.id);
	}

	/** Distinct chunks kept active on the server by the last refresh. */
	public static int totalChunks() {
		return plan.totalChunks();
	}

	/** Tickets held right now (all guilds). */
	public static int tickets() {
		return HELD.size();
	}

	/** At least one ticket of the last refresh was planned for this villager. */
	public static boolean kept(UUID villager) {
		return plan.kept().contains(villager);
	}

	/** Part of what this villager needs was refused by the caps at the last refresh. */
	public static boolean capped(UUID villager) {
		return plan.capped().contains(villager);
	}

	// ================================================================ refresh

	private static void tick() {
		if (--secondsToRefresh > 0) {
			return;
		}
		secondsToRefresh = VillagerModule.config().chunkLoading.refreshSeconds;
		refreshNow();
	}

	/** Plans and applies the tickets now (also after a reload or a disband). */
	public static void refresh() {
		Safe.run("villager.chunks.refresh", WorkChunkLoader::refreshNow);
	}

	private static void refreshNow() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			releaseAll();
			return;
		}
		ChunkLoadingConfig config = VillagerModule.config().chunkLoading;
		ChunkLoadPlanner.Mode mode = config.mode();
		Map<UUID, Long> resting = new HashMap<>();
		ChunkLoadPlanner.Plan next = ChunkLoadPlanner.Plan.EMPTY;
		if (mode != ChunkLoadPlanner.Mode.NEVER) {
			Set<UUID> online = new HashSet<>();
			for (ServerPlayer player : GuildyServer.players()) {
				online.add(player.getUUID());
			}
			List<ChunkLoadPlanner.GuildRequest> requests = new ArrayList<>();
			for (Guild guild : GuildManager.all()) {
				if (guild.villagers.members.isEmpty()) {
					continue;
				}
				boolean memberOnline = false;
				for (UUID member : guild.memberIds()) {
					if (online.contains(member)) {
						memberOnline = true;
						break;
					}
				}
				if (ChunkLoadPlanner.active(mode, memberOnline)) {
					requests.add(new ChunkLoadPlanner.GuildRequest(guild.id, memberOnline, anchors(level, guild, config, resting)));
				}
			}
			next = ChunkLoadPlanner.plan(new ChunkLoadPlanner.Limits(mode, config.maxChunksPerGuild, config.maxChunksTotal), requests,
					players(level));
		}
		RESTING_SINCE.clear();
		RESTING_SINCE.putAll(resting);
		apply(level, next);
	}

	/** What the working villagers of the guild need (see {@link ChunkLoadPlanner.Kind}). */
	private static List<ChunkLoadPlanner.Anchor> anchors(ServerLevel level, Guild guild, ChunkLoadingConfig config, Map<UUID, Long> resting) {
		List<ChunkLoadPlanner.Anchor> anchors = new ArrayList<>();
		BlockPos heart = guild.heart;
		boolean dark = level.isDarkOutside();
		long now = Scheduler.ticks();
		for (VillagerRecord record : guild.villagers.members.values()) {
			UUID id = record.uuid;
			if (record.isAway()) {
				// Away: nothing, but its return point once the return is due (it waits for an entity-ticking chunk).
				BlockPos home = record.returnPos != null ? record.returnPos : heart;
				if (home != null && level.getGameTime() >= record.returnAt) {
					add(anchors, heart, id, ChunkLoadPlanner.Kind.DESTINATION, home, config.radius);
				}
				continue;
			}
			Task task = record.task;
			if (task == Task.IDLE || (task == Task.GUARD && !config.guards)) {
				continue;
			}
			Villager villager = VillagerManager.entity(level, record.uuid);
			BlockPos pos = villager != null ? villager.blockPosition() : record.lastPos;
			if (pos == null) {
				continue;
			}
			if (task != Task.GUARD) {
				if (villager != null && villager.isSleeping()) {
					continue;
				}
				boolean rest = dark || (villager != null && villager.getBrain().isActive(Activity.REST));
				if (rest) {
					// An unloaded villager stays unloaded until morning; a loaded one may walk to its bed for a while.
					if (villager != null) {
						long since = RESTING_SINCE.getOrDefault(id, now);
						resting.put(id, since);
						if (now - since < config.bedTravelSeconds * 20L) {
							add(anchors, heart, id, ChunkLoadPlanner.Kind.SELF, pos, config.radius);
							BlockPos bed = bed(level, villager);
							if (bed != null) {
								add(anchors, heart, id, ChunkLoadPlanner.Kind.DESTINATION, bed, config.radius);
							}
						}
					}
					continue;
				}
			}
			add(anchors, heart, id, ChunkLoadPlanner.Kind.SELF, pos, config.radius);
			VillagerRuntime runtime = villager == null ? null : TaskEngine.peek(id);
			if (runtime != null && runtime.moveTarget != null && now - runtime.lastMoveCall <= WALK_FRESH_TICKS) {
				add(anchors, heart, id, ChunkLoadPlanner.Kind.DESTINATION, runtime.moveTarget, config.radius);
			}
			if (!record.carried.isEmpty() && (task.isGatherer() || task.isExpedition())) {
				// Deposit trips: the deposit chests must be loaded to be chosen at all.
				for (StorageChest chest : Storage.index(guild).deposits()) {
					add(anchors, heart, id, ChunkLoadPlanner.Kind.DESTINATION, chest.pos, 0);
				}
			}
			zoneAnchor(anchors, guild, record, pos, config);
			if (task == Task.BUILDER) {
				// The site it works on (its parcels move around the box).
				int[] site = BuilderTask.siteAnchor(guild, record, config.zoneRadius);
				if (site != null) {
					add(anchors, heart, id, ChunkLoadPlanner.Kind.ZONE, new BlockPos(site[0], site[1], site[2]), site[3]);
				}
			}
			if (task == Task.SORTER || task == Task.ALCHEMIST || task == Task.BUILDER) {
				// One anchor per chunk of storage (a chest room holds many chests in a few chunks).
				for (BlockPos anchor : Storage.index(guild).chunkAnchors()) {
					add(anchors, heart, id, ChunkLoadPlanner.Kind.STORAGE, anchor, 0);
				}
			}
		}
		return anchors;
	}

	/**
	 * Work area: the assigned zone (an automatic miner: the nearest mine of the guild), the automatic search area of a
	 * lumberjack or a farmer (around the point of the territory nearest to it), the heart for a guard without zone.
	 */
	private static void zoneAnchor(List<ChunkLoadPlanner.Anchor> anchors, Guild guild, VillagerRecord record, BlockPos pos, ChunkLoadingConfig config) {
		Task task = record.task;
		WorkZone zone = Zones.assignedZone(guild, record);
		if (zone == null && task == Task.MINER) {
			zone = nearestMine(guild, pos);
		}
		if (zone != null) {
			add(anchors, guild.heart, record.uuid, ChunkLoadPlanner.Kind.ZONE, zone.pos, Math.min(zone.effectiveRadius(), config.zoneRadius));
			return;
		}
		if (task == Task.LUMBERJACK || task == Task.FARMER) {
			int[] center = WorkAnchor.anchor(guild.claims(), pos.getX(), pos.getZ(), pos.getX(), pos.getZ());
			int radius = Math.min((VillagerModule.config().zones.autoSearchRadius + 15) / 16, config.zoneRadius);
			add(anchors, guild.heart, record.uuid, ChunkLoadPlanner.Kind.ZONE, new BlockPos(center[0], pos.getY(), center[1]), radius);
		} else if (task == Task.GUARD && Zones.assignedRoute(guild, record) == null && guild.heart != null) {
			add(anchors, guild.heart, record.uuid, ChunkLoadPlanner.Kind.ZONE, guild.heart, Math.min(1, config.zoneRadius));
		}
	}

	/** Nearest mine of the guild that still has work (automatic miners go there). */
	private static @Nullable WorkZone nearestMine(Guild guild, BlockPos from) {
		WorkZone best = null;
		double bestDist = Double.MAX_VALUE;
		for (WorkZone zone : guild.villagers.zones) {
			if (zone.type != ZoneType.MINE || zone.pos == null || (zone.isRealMine() && zone.mine().exhausted)) {
				continue;
			}
			double dist = zone.pos.distSqr(from);
			if (dist < bestDist) {
				bestDist = dist;
				best = zone;
			}
		}
		return best;
	}

	/** Bed of the villager (vanilla HOME memory) in this level, or null. */
	private static @Nullable BlockPos bed(ServerLevel level, Villager villager) {
		Brain<Villager> brain = villager.getBrain();
		if (!brain.hasMemoryValue(MemoryModuleType.HOME)) {
			return null;
		}
		Optional<GlobalPos> home = brain.getMemory(MemoryModuleType.HOME);
		return home.filter(global -> global.dimension().equals(level.dimension())).map(GlobalPos::pos).orElse(null);
	}

	private static void add(List<ChunkLoadPlanner.Anchor> anchors, @Nullable BlockPos heart, UUID villager, ChunkLoadPlanner.Kind kind,
			BlockPos pos, int radius) {
		long distance = 0;
		if (heart != null) {
			long dx = (long) pos.getX() - heart.getX();
			long dz = (long) pos.getZ() - heart.getZ();
			distance = dx * dx + dz * dz;
		}
		anchors.add(new ChunkLoadPlanner.Anchor(villager, kind, pos.getX() >> 4, pos.getZ() >> 4, radius, distance));
	}

	/** Chunks of the players of the level (spectators aside) and how far around them chunks tick anyway. */
	private static ChunkLoadPlanner.Players players(ServerLevel level) {
		MinecraftServer server = level.getServer();
		int reach = Math.min(server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance()) - PLAYER_MARGIN;
		if (reach < 0) {
			return ChunkLoadPlanner.Players.NONE;
		}
		List<Long> chunks = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				chunks.add(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()));
			}
		}
		long[] packed = new long[chunks.size()];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = chunks.get(i);
		}
		return new ChunkLoadPlanner.Players(packed, reach);
	}

	// ================================================================ tickets

	/** Removes the tickets no longer planned (or planned with another radius), adds / refreshes the planned ones. */
	private static void apply(ServerLevel level, ChunkLoadPlanner.Plan next) {
		if (heldLevel != null && heldLevel != level) {
			// Claim dimension changed by a reload: the old tickets go first.
			removeAll(heldLevel);
		}
		ServerChunkCache chunks = level.getChunkSource();
		Iterator<Map.Entry<Long, Integer>> it = HELD.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Integer> held = it.next();
			Integer wanted = next.tickets().get(held.getKey());
			if (wanted == null || !wanted.equals(held.getValue())) {
				chunks.removeTicketWithRadius(TICKET, chunk(held.getKey()), held.getValue() + VANILLA_RADIUS_OFFSET);
				it.remove();
			}
		}
		for (Map.Entry<Long, Integer> wanted : next.tickets().entrySet()) {
			// Adding an existing ticket only resets its expiry.
			chunks.addTicketWithRadius(TICKET, chunk(wanted.getKey()), wanted.getValue() + VANILLA_RADIUS_OFFSET);
			HELD.put(wanted.getKey(), wanted.getValue());
		}
		heldLevel = HELD.isEmpty() ? null : level;
		plan = next;
	}

	private static void removeAll(ServerLevel level) {
		ServerChunkCache chunks = level.getChunkSource();
		for (Map.Entry<Long, Integer> held : HELD.entrySet()) {
			chunks.removeTicketWithRadius(TICKET, chunk(held.getKey()), held.getValue() + VANILLA_RADIUS_OFFSET);
		}
		HELD.clear();
		heldLevel = null;
	}

	/** Server stopping: every ticket goes (they are not saved anyway). */
	static void releaseAll() {
		if (heldLevel != null) {
			removeAll(heldLevel);
		}
		HELD.clear();
		RESTING_SINCE.clear();
		plan = ChunkLoadPlanner.Plan.EMPTY;
	}

	/** Server started: nothing is held yet (tickets of a previous server instance are gone with it). */
	private static void forget() {
		HELD.clear();
		RESTING_SINCE.clear();
		heldLevel = null;
		plan = ChunkLoadPlanner.Plan.EMPTY;
		secondsToRefresh = 0;
	}

	private static ChunkPos chunk(long key) {
		return new ChunkPos(ChunkKey.x(key), ChunkKey.z(key));
	}

	// ================================================================ server pause

	/** pause-when-empty-seconds of a dedicated server (0 = never pauses). */
	private static int pauseSeconds(@Nullable MinecraftServer server) {
		return server instanceof DedicatedServer dedicated ? dedicated.pauseWhenEmptySeconds() : 0;
	}

	private static void warnPause(MinecraftServer server) {
		int pause = pauseSeconds(server);
		if (pause > 0 && VillagerModule.config().chunkLoading.mode() == ChunkLoadPlanner.Mode.ALWAYS) {
			Guildy.LOGGER.warn("villagers.json5: chunkLoading.mode is \"always\" but pause-when-empty-seconds is {}: the server pauses when "
					+ "nobody is online and guild villagers stop too. Set pause-when-empty-seconds=0 in server.properties.", pause);
		}
	}

	// ================================================================ display

	/** Villagers menu: mode and chunks kept active for the guild. */
	static GuiElementBuilder menuItem(Guild guild) {
		ChunkLoadingConfig config = VillagerModule.config().chunkLoading;
		ChunkLoadPlanner.Mode mode = config.mode();
		String modeText = Lang.raw("villager.chunks.mode." + mode.id());
		List<Component> lore = new ArrayList<>();
		if (mode == ChunkLoadPlanner.Mode.NEVER) {
			lore.addAll(GuiUtil.lines("villager.chunks.menu.lore.never", modeText));
		} else {
			lore.addAll(GuiUtil.lines("villager.chunks.menu.lore.active", modeText, chunks(guild), config.maxChunksPerGuild));
			int capped = cappedCount(guild);
			if (capped > 0) {
				lore.addAll(GuiUtil.lines("villager.chunks.menu.lore.capped", capped));
			}
		}
		return GuiUtil.item(Items.MAP, Lang.get("villager.chunks.menu.name"), lore);
	}

	/** Villager icon: one line when its chunks are kept active, or when the caps refused part of them. */
	static void addLore(List<Component> lore, VillagerRecord record) {
		if (capped(record.uuid)) {
			lore.add(Lang.get("villager.chunks.lore.capped"));
		} else if (kept(record.uuid)) {
			lore.add(Lang.get("villager.chunks.lore.kept"));
		}
	}

	private static int cappedCount(Guild guild) {
		int count = 0;
		for (UUID id : guild.villagers.members.keySet()) {
			if (capped(id)) {
				count++;
			}
		}
		return count;
	}

	/** /guild admin villager chunks */
	private static int report(CommandSourceStack source) {
		ChunkLoadingConfig config = VillagerModule.config().chunkLoading;
		ChunkLoadPlanner.Mode mode = config.mode();
		Component header = Msg.prefixed(Lang.get("villager.chunks.admin.header", Lang.raw("villager.chunks.mode." + mode.id()),
				plan.totalChunks(), config.maxChunksTotal, HELD.size(), config.maxChunksPerGuild));
		source.sendSuccess(() -> header, false);
		int listed = 0;
		for (Guild guild : GuildManager.all()) {
			int chunks = plan.chunks(guild.id);
			int kept = 0;
			for (UUID id : guild.villagers.members.keySet()) {
				if (kept(id)) {
					kept++;
				}
			}
			int capped = cappedCount(guild);
			if (chunks == 0 && capped == 0) {
				continue;
			}
			listed++;
			Component line = Lang.get("villager.chunks.admin.guild", guild.name, chunks, kept, capped);
			source.sendSuccess(() -> line, false);
		}
		if (listed == 0) {
			source.sendSuccess(() -> Lang.get("villager.chunks.admin.none"), false);
		}
		int pause = pauseSeconds(source.getServer());
		if (pause > 0 && mode == ChunkLoadPlanner.Mode.ALWAYS) {
			source.sendSuccess(() -> Lang.get("villager.chunks.admin.pause", pause), false);
		}
		return Math.max(1, plan.totalChunks());
	}
}
