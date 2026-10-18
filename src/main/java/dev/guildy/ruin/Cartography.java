package dev.guildy.ruin;

import com.mojang.datafixers.util.Pair;
import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.ruin.GuildRuinData.Research;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.Orientation;
import dev.guildy.structure.StructureManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cartographer research: started from the atelier menu (cost paid by the player), advanced by the guild's cartographer
 * villagers working at the atelier (one second of work per cartographer and per second, capped, paused at night), then
 * a locate of the nearest ruin not yet referenced by a map (like vanilla explorer maps); "Récupérer" hands out a map
 * (Overworld) or a lodestone compass (Nether), with incantation scrolls to awaken its guardian. Nothing found: the cost
 * is refunded.
 */
public final class Cartography {
	/** Structure id of the "Atelier du cartographe". */
	public static final String STRUCTURE_ID = "cartographer";
	private static final long WORKING_WINDOW_TICKS = 60;

	/** Finds a ruin for a research (replaceable for tests): the vanilla structure search by default. */
	@FunctionalInterface
	public interface Locator {
		@Nullable RuinRecord locate(ServerLevel level, BlockPos origin, boolean nether, Guild guild);
	}

	public static Locator locator = Cartography::vanillaLocate;

	/** Finds the grand temple of a legend research or of the admin command (replaceable for tests). */
	@FunctionalInterface
	public interface TempleLocator {
		/**
		 * @param claim add a reference to the start (the next legend skips it, like the other searches); false (admin): the
		 *              nearest temple, the indexed ones as a fallback
		 */
		@Nullable RuinRecord locate(ServerLevel level, BlockPos origin, @Nullable Guild guild, boolean claim);
	}

	public static TempleLocator templeLocator = Cartography::vanillaTempleLocate;

	/** Guild id → villager id → last tick it worked at the atelier. */
	private static final Map<UUID, Map<UUID, Long>> WORKERS = new HashMap<>();
	/** Guild id → {second, work seconds added during that second}. */
	private static final Map<UUID, long[]> BUDGET = new HashMap<>();

	private Cartography() {
	}

	static void clear() {
		WORKERS.clear();
		BUDGET.clear();
	}

	static RuinConfig.CartographySettings settings() {
		return RuinModule.config().cartography;
	}

	// ================================================================== atelier

	public static @Nullable BuiltStructure atelier(Guild guild) {
		return guild.structures.find(STRUCTURE_ID);
	}

	/** The cartography table of the atelier (else the middle of its bottom row). */
	public static BlockPos table(ServerLevel level, BuiltStructure atelier) {
		for (BlockPos pos : atelier.blocks) {
			if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)) {
				return pos;
			}
		}
		int[] offset = Orientation.offset(atelier.width / 2, 0, atelier.facing.getStepX(), atelier.facing.getStepZ());
		return atelier.origin.offset(offset[0], offset[1], offset[2]);
	}

	/** Where a cartographer stands to work: in front of the table. */
	public static BlockPos workSpot(ServerLevel level, BuiltStructure atelier) {
		return table(level, atelier).relative(atelier.facing);
	}

	// ================================================================== research

	public static @Nullable Research research(Guild guild) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		return data == null ? null : data.research;
	}

	/** Starts a research (cost taken from the player and the treasury). @return the error, or null on success */
	public static @Nullable Component start(ServerPlayer player, Guild guild, boolean nether) {
		return begin(player, guild, nether, false);
	}

	/**
	 * Starts "La legende du Jaguar celeste": a statue trail to the nearest grand temple (guild level, its own cost and
	 * duration, see {@code cartography.statues}). @return the error, or null on success
	 */
	public static @Nullable Component startLegend(ServerPlayer player, Guild guild) {
		return begin(player, guild, false, true);
	}

	private static @Nullable Component begin(ServerPlayer player, Guild guild, boolean nether, boolean legend) {
		if (!guild.isMember(player.getUUID())) {
			return Lang.get("error.no_guild");
		}
		if (atelier(guild) == null) {
			return Lang.get("ruin.research.no_atelier");
		}
		StatueSettings statues = settings().statues;
		if (legend) {
			String refusal = StatueRules.legendRefusal(statues.enabled, guild.level, statues.minGuildLevel);
			if (refusal != null) {
				return Lang.get(refusal, statues.minGuildLevel, guild.level);
			}
		}
		GuildRuinData data = RuinIndex.guild(guild.id);
		if (data.research != null) {
			return Lang.get("ruin.research.busy");
		}
		RuinConfig.CartographySettings cfg = settings();
		Map<String, Integer> cost = legend ? statues.cost : nether ? cfg.netherCost : cfg.overworldCost;
		int goldCost = legend ? statues.goldCost : cfg.goldCost;
		if (!cost.isEmpty() && player.isCreative()) {
			return Lang.get("ruin.research.creative");
		}
		if (goldCost > 0 && !guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			return Lang.get("error.no_permission");
		}
		Map.Entry<String, Integer> missing = StatueRules.missing(cost, id -> {
			Item item = ItemUtil.item(id);
			return item == null ? Integer.MAX_VALUE : ItemUtil.count(player, item);
		});
		if (missing != null) {
			Item item = ItemUtil.item(missing.getKey());
			return Lang.get("ruin.research.missing", missing.getValue(), item == null ? missing.getKey() : ItemUtil.name(item).getString());
		}
		if (goldCost > 0 && guild.treasury < goldCost) {
			return Lang.get("error.not_enough_treasury", goldCost);
		}
		// Pay: items first (all checked above), then the treasury (items given back if it fails).
		List<ItemStack> paid = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			Item item = ItemUtil.item(entry.getKey());
			if (item == null) {
				continue;
			}
			if (!ItemUtil.remove(player, item, entry.getValue())) {
				refund(player, paid);
				return Lang.get("ruin.research.missing", entry.getValue(), ItemUtil.name(item).getString());
			}
			paid.add(new ItemStack(item, entry.getValue()));
		}
		if (goldCost > 0 && !GuildManager.takeTreasury(guild, goldCost)) {
			refund(player, paid);
			return Lang.get("error.not_enough_treasury", goldCost);
		}
		Research research = new Research();
		research.nether = nether;
		research.legend = legend;
		double minutes = legend ? statues.researchMinutes : cfg.researchMinutes;
		research.required = Math.max(1, Upgrades.workSeconds(guild, (int) Math.round(minutes * 60)));
		research.startedBy = player.getUUID();
		research.startedByName = player.getName().getString();
		research.startedAt = System.currentTimeMillis();
		research.paidItems = paid;
		research.paidGold = goldCost;
		data.research = research;
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		Msg.guild(guild, legend ? "ruin.research.started.legend" : nether ? "ruin.research.started.nether" : "ruin.research.started",
				player.getName().getString());
		if (cartographers(guild) == 0) {
			Msg.send(player, "ruin.research.no_cartographer");
		}
		return null;
	}

	/** Cancels a running research (cost refunded to the player who clicks). */
	public static @Nullable Component cancel(ServerPlayer player, Guild guild) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		Research research = data == null ? null : data.research;
		if (research == null || research.status != Research.Status.RUNNING) {
			return Lang.get("ruin.research.none");
		}
		data.research = null;
		refund(player, research.paidItems);
		if (research.paidGold > 0) {
			GuildManager.addTreasury(guild, research.paidGold);
		}
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		Msg.guild(guild, "ruin.research.cancelled", player.getName().getString());
		return null;
	}

	/** "Récupérer": the map / compass (READY) or the refund (FAILED). @return the error, or null on success */
	public static @Nullable Component claim(ServerPlayer player, Guild guild) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		Research research = data == null ? null : data.research;
		if (research == null || research.status == Research.Status.RUNNING) {
			return Lang.get("ruin.research.not_ready");
		}
		MinecraftServer server = player.level().getServer();
		if (research.status == Research.Status.FAILED) {
			data.research = null;
			refund(player, research.paidItems);
			if (research.paidGold > 0) {
				GuildManager.addTreasury(guild, research.paidGold);
			}
			RuinIndex.markDirty();
			StructureManager.requestSignRefresh(guild);
			Msg.send(player, "ruin.research.refunded");
			return null;
		}
		RuinRecord ruin = RuinIndex.get(research.ruinKey);
		if (ruin == null) {
			research.status = Research.Status.FAILED;
			RuinIndex.markDirty();
			return Lang.get("ruin.research.lost");
		}
		if (research.legend) {
			return claimLegend(player, guild, data, research, ruin);
		}
		ItemStack item = RuinItems.forRuin(server, ruin);
		if (item.isEmpty()) {
			return Lang.get("ruin.research.lost");
		}
		data.research = null;
		data.addKnown(ruin.key);
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		ItemUtil.giveOrDrop(player, item);
		int incantations = settings().incantationsPerResearch;
		if (incantations > 0) {
			// The way to awaken the guardian the map leads to.
			IncantationItems.give(player, incantations);
			Msg.send(player, "ruin.research.incantation", incantations);
		}
		Msg.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 1.0F);
		BlockPos target = ruin.target();
		Msg.guild(guild, ruin.isNether() ? "ruin.research.claimed.nether" : "ruin.research.claimed", player.getName().getString(),
				Lang.colorize(Encounters.def(ruin).name), target.getX(), target.getZ());
		return null;
	}

	/**
	 * "Récupérer" of a legend research: the statue map of its trail (planned again if it was forgotten), the incantation
	 * scrolls; the temple stays unknown to the guild (no coordinates anywhere) until its members defeat its guardian.
	 */
	private static @Nullable Component claimLegend(ServerPlayer player, Guild guild, GuildRuinData data, Research research, RuinRecord temple) {
		var dimension = RuinItems.dimension(temple);
		ServerLevel level = dimension == null ? null : player.level().getServer().getLevel(dimension);
		if (level == null) {
			return Lang.get("ruin.research.lost");
		}
		StatueTrail trail = Statues.trail(research.trailId);
		if (trail == null) {
			trail = Statues.createTrail(level, legendOrigin(guild), temple, guild);
			if (trail == null) {
				return Lang.get("ruin.research.lost");
			}
			research.trailId = trail.id;
		}
		ItemStack item = RuinItems.statueMap(level, trail);
		data.research = null;
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		ItemUtil.giveOrDrop(player, item);
		int incantations = settings().incantationsPerResearch;
		if (incantations > 0) {
			IncantationItems.give(player, incantations);
			Msg.send(player, "ruin.research.incantation", incantations);
		}
		Msg.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 1.0F);
		Msg.guild(guild, "ruin.research.claimed.legend", player.getName().getString(), trail.statues.size());
		return null;
	}

	/** Where a legend trail of the guild starts: its atelier, else its heart. */
	static BlockPos legendOrigin(Guild guild) {
		BuiltStructure atelier = atelier(guild);
		return atelier != null ? atelier.origin : guild.heart != null ? guild.heart : BlockPos.ZERO;
	}

	/**
	 * One second of work by a cartographer standing at the atelier (called by its task). Adds progress (capped by the
	 * number of cartographers that count at once) and completes the research when it is done.
	 *
	 * @return false when there is nothing to work on
	 */
	public static boolean work(ServerLevel level, Guild guild, UUID villager) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		Research research = data == null ? null : data.research;
		if (research == null || research.status != Research.Status.RUNNING) {
			return false;
		}
		long now = Scheduler.ticks();
		WORKERS.computeIfAbsent(guild.id, k -> new HashMap<>()).put(villager, now);
		long second = now / 20;
		long[] budget = BUDGET.computeIfAbsent(guild.id, k -> new long[] {-1, 0});
		if (budget[0] != second) {
			budget[0] = second;
			budget[1] = 0;
		}
		if (budget[1] >= RuinRules.researchWorkers(Integer.MAX_VALUE, settings().maxCartographers)) {
			return true;
		}
		budget[1]++;
		research.progress = Math.min(research.required, research.progress + 1.0);
		RuinIndex.markDirty();
		if (research.progress >= research.required) {
			complete(level.getServer(), guild, research);
		}
		return true;
	}

	/** Adds work seconds directly (admin command / tests). */
	public static void addProgress(MinecraftServer server, Guild guild, double seconds) {
		Research research = research(guild);
		if (research == null || research.status != Research.Status.RUNNING) {
			return;
		}
		research.progress = Math.min(research.required, research.progress + Math.max(0, seconds));
		RuinIndex.markDirty();
		if (research.progress >= research.required) {
			complete(server, guild, research);
		}
	}

	/** Cartographers that worked at the atelier during the last few seconds. */
	public static int working(Guild guild) {
		Map<UUID, Long> workers = WORKERS.get(guild.id);
		if (workers == null) {
			return 0;
		}
		long now = Scheduler.ticks();
		workers.values().removeIf(t -> now - t > WORKING_WINDOW_TICKS || t > now);
		return workers.size();
	}

	/** Villagers of the guild with the cartographer task. */
	public static int cartographers(Guild guild) {
		int count = 0;
		for (var record : guild.villagers.members.values()) {
			if (record.task == dev.guildy.villager.Task.CARTOGRAPHER && !record.isAway()) {
				count++;
			}
		}
		return count;
	}

	/** Research done: locate now (server thread), READY with the ruin or FAILED (refund at "Récupérer"). */
	static void complete(MinecraftServer server, Guild guild, Research research) {
		research.progress = research.required;
		RuinRecord ruin = null;
		ServerLevel claimLevel = GuildyServer.claimLevel();
		BlockPos origin = legendOrigin(guild);
		ServerLevel level = research.nether ? server.getLevel(Level.NETHER) : claimLevel != null ? claimLevel : server.overworld();
		if (research.legend) {
			completeLegend(level, guild, research, origin);
			return;
		}
		if (level != null) {
			BlockPos from = research.nether ? new BlockPos(origin.getX() / 8, 64, origin.getZ() / 8) : origin;
			try {
				ruin = locator.locate(level, from, research.nether, guild);
			} catch (Exception e) {
				Guildy.LOGGER.error("[ruins] ruin search failed for guild {}", guild.name, e);
			}
		}
		if (ruin == null) {
			research.status = Research.Status.FAILED;
			Msg.guild(guild, "ruin.research.failed");
		} else {
			research.status = Research.Status.READY;
			research.ruinKey = ruin.key;
			Msg.guild(guild, research.nether ? "ruin.research.ready.nether" : "ruin.research.ready");
			Msg.guildSound(guild, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);
		}
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
	}

	/** Legend research done: the nearest grand temple not yet referenced, then its statue trail from the atelier. */
	private static void completeLegend(@Nullable ServerLevel level, Guild guild, Research research, BlockPos origin) {
		RuinRecord temple = null;
		StatueTrail trail = null;
		if (level != null) {
			try {
				temple = templeLocator.locate(level, origin, guild, true);
				if (temple != null) {
					trail = Statues.createTrail(level, origin, temple, guild);
				}
			} catch (Exception e) {
				Guildy.LOGGER.error("[ruins] legend research failed for guild {}", guild.name, e);
			}
		}
		if (temple == null || trail == null) {
			research.status = Research.Status.FAILED;
			Msg.guild(guild, "ruin.research.failed.legend");
		} else {
			research.status = Research.Status.READY;
			research.ruinKey = temple.key;
			research.trailId = trail.id;
			Msg.guild(guild, "ruin.research.ready.legend");
			Msg.guildSound(guild, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);
		}
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
	}

	/** Nearest ruin of the dimension's tag not yet referenced by a map (vanilla explorer-map search). */
	static @Nullable RuinRecord vanillaLocate(ServerLevel level, BlockPos origin, boolean nether, Guild guild) {
		HolderSet<Structure> set = nether ? tagSet(level, RuinIndex.TAG_NETHER) : surfaceStructures(level);
		if (set == null || set.size() == 0) {
			return null;
		}
		return locate(level, set, origin, settings().searchRadius, true);
	}

	private static @Nullable HolderSet<Structure> tagSet(ServerLevel level, TagKey<Structure> tag) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<HolderSet.Named<Structure>> set = registry.get(tag);
		return set.isEmpty() ? null : set.get();
	}

	/**
	 * The ruins the surface research looks for: the overworld ruin tag, without the grand temple while the legend is enabled
	 * (only the statues lead to it). Public for the GameTests.
	 */
	public static @Nullable HolderSet<Structure> surfaceStructures(ServerLevel level) {
		HolderSet<Structure> set = tagSet(level, RuinIndex.TAG_OVERWORLD);
		StatueSettings statues = settings().statues;
		if (set == null || !statues.enabled) {
			return set;
		}
		List<Holder<Structure>> kept = new ArrayList<>();
		boolean removed = false;
		for (Holder<Structure> holder : set) {
			boolean temple = holder.unwrapKey().map(key -> key.identifier().toString().equals(statues.templeStructure)).orElse(false);
			if (temple) {
				removed = true;
			} else {
				kept.add(holder);
			}
		}
		return removed ? HolderSet.direct(kept) : set;
	}

	/**
	 * The grand temple of the structure {@code cartography.statues.templeStructure} nearest to the origin (vanilla structure
	 * search, a referenced start skipped when {@code claim}); without {@code claim}, the nearest indexed one as a fallback.
	 */
	static @Nullable RuinRecord vanillaTempleLocate(ServerLevel level, BlockPos origin, @Nullable Guild guild, boolean claim) {
		StatueSettings statues = settings().statues;
		Identifier id = Identifier.tryParse(statues.templeStructure);
		if (id == null) {
			return null;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<Holder.Reference<Structure>> holder = registry.get(id);
		RuinRecord found = null;
		if (holder.isPresent() && !level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
			found = locate(level, HolderSet.direct(holder.get()), origin, statues.searchRadius, claim);
		}
		if (found == null && !claim) {
			found = RuinIndex.nearest(level.dimension(), origin.getX(), origin.getZ(), RuinRules.ruinIdOf(id.getPath()));
		}
		return found;
	}

	/**
	 * Structure search + registration of the found start in the ruin index.
	 *
	 * @param claim add a reference to the start (the next search skips it, like vanilla explorer maps)
	 */
	static @Nullable RuinRecord locate(ServerLevel level, HolderSet<Structure> structures, BlockPos origin, int radius, boolean claim) {
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, structures, origin, radius, claim);
		if (found == null) {
			return null;
		}
		ChunkPos chunk = ChunkPos.containing(found.getFirst());
		ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS);
		StructureStart start = access.getStartForStructure(found.getSecond().value());
		if (start == null || !start.isValid()) {
			return null;
		}
		return RuinIndex.register(level, start);
	}

	private static void refund(ServerPlayer player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			ItemUtil.giveOrDrop(player, stack.copy());
		}
	}

	static void onGuildRemoved(UUID guildId) {
		WORKERS.remove(guildId);
		BUDGET.remove(guildId);
		RuinIndex.removeGuild(guildId);
	}
}
