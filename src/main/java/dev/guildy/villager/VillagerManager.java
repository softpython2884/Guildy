package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.Upgrades;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Guild villagers lifecycle: morning arrivals at the reception, candidates (transient), acceptance, deaths and
 * removals, disband / defeat consequences, and the staggered once-per-second processing loop.
 */
public final class VillagerManager {
	public static final String TAG_VILLAGER = "guildy_villager";
	public static final String TAG_CANDIDATE = "guildy_candidate";
	public static final String RECEPTION = "reception";

	private static final List<ResourceKey<VillagerProfession>> PROFESSIONS = List.of(
			VillagerProfession.ARMORER, VillagerProfession.BUTCHER, VillagerProfession.CARTOGRAPHER, VillagerProfession.CLERIC,
			VillagerProfession.FARMER, VillagerProfession.FISHERMAN, VillagerProfession.FLETCHER, VillagerProfession.LEATHERWORKER,
			VillagerProfession.LIBRARIAN, VillagerProfession.MASON, VillagerProfession.SHEPHERD, VillagerProfession.TOOLSMITH,
			VillagerProfession.WEAPONSMITH);

	/** A villager waiting at the reception for an answer (not persisted). */
	record Candidate(UUID entity, String name, long expiresAt) {
	}

	record Owner(Guild guild, VillagerRecord record) {
	}

	enum ArrivalMode {
		/** Daily roll. */
		MORNING,
		/** First candidate after the reception is built (no roll). */
		FIRST,
		/** Admin command: no roll, no limit, no bed needed. */
		FORCED
	}

	/** Guild id → candidates waiting at the reception, in arrival order. */
	private static final Map<UUID, List<Candidate>> CANDIDATES = new HashMap<>();
	private static boolean ready;
	private static long lastDay = -1;

	private VillagerManager() {
	}

	static void init() {
		Lifecycle.STARTED.add(VillagerManager::onStarted);
		Lifecycle.STOPPING.add(VillagerManager::onStopping);
		Lifecycle.RELOADED.add(VillagerManager::onReload);
		GuildEvents.DISBANDED.add(VillagerManager::onDisband);
		GuildEvents.WAVE_ENDED.add(VillagerManager::onWaveEnded);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Safe.run("villager.load", () -> onEntityLoad(entity, level)));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Safe.run("villager.unload", () -> onEntityUnload(entity)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Safe.run("villager.death", () -> onDeath(entity)));
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) ->
				Safe.run("villager.conversion", () -> onConversion(previous, converted)));
		Scheduler.every("villager.tick", 1, 0, VillagerManager::tick);
		Scheduler.every("villager.second", 20, 11, VillagerManager::everySecond);
		Scheduler.every("villager.zones.upkeep", 100, 53, () -> {
			if (ready) {
				Zones.upkeep();
			}
		});
	}

	// ================================================================ queries

	public static int maxVillagers(Guild guild) {
		return VillagerModule.config().maxVillagers(guild.level) + Upgrades.count(guild, UpgradeEffects.EXTRA_VILLAGERS);
	}

	/** Oldest waiting candidate (null when none). */
	static @Nullable Candidate candidate(Guild guild) {
		List<Candidate> list = CANDIDATES.get(guild.id);
		return list == null || list.isEmpty() ? null : list.getFirst();
	}

	/** Every waiting candidate of the guild, in arrival order. */
	static List<Candidate> candidates(Guild guild) {
		List<Candidate> list = CANDIDATES.get(guild.id);
		return list == null ? List.of() : List.copyOf(list);
	}

	/** Latest candidate (admin feedback). */
	static @Nullable Candidate lastCandidate(Guild guild) {
		List<Candidate> list = CANDIDATES.get(guild.id);
		return list == null || list.isEmpty() ? null : list.getLast();
	}

	/** Candidate of this guild with this name (case-insensitive), the oldest one when {@code name} is null or blank. */
	static @Nullable Candidate findCandidate(Guild guild, @Nullable String name) {
		List<Candidate> list = CANDIDATES.get(guild.id);
		if (list == null || list.isEmpty()) {
			return null;
		}
		if (name == null || name.isBlank()) {
			return list.getFirst();
		}
		for (Candidate candidate : list) {
			if (candidate.name().equalsIgnoreCase(name.trim())) {
				return candidate;
			}
		}
		return null;
	}

	/** Removes this candidate from its guild's list. @return true when it was waiting */
	private static boolean removeCandidate(Guild guild, Candidate candidate) {
		List<Candidate> list = CANDIDATES.get(guild.id);
		if (list == null || !list.remove(candidate)) {
			return false;
		}
		if (list.isEmpty()) {
			CANDIDATES.remove(guild.id);
		}
		return true;
	}

	/** Removes the candidate whose entity is {@code entity} (death, conversion, unload...). @return it, or null */
	private static @Nullable Candidate removeCandidateEntity(Guild guild, UUID entity) {
		for (Candidate candidate : candidates(guild)) {
			if (candidate.entity().equals(entity)) {
				removeCandidate(guild, candidate);
				return candidate;
			}
		}
		return null;
	}

	static @Nullable Owner owner(UUID entity) {
		for (Guild guild : GuildManager.all()) {
			VillagerRecord record = guild.villagers.members.get(entity);
			if (record != null) {
				return new Owner(guild, record);
			}
		}
		return null;
	}

	private static @Nullable Guild candidateGuild(UUID entity) {
		for (Map.Entry<UUID, List<Candidate>> entry : CANDIDATES.entrySet()) {
			for (Candidate candidate : entry.getValue()) {
				if (candidate.entity().equals(entity)) {
					return GuildManager.get(entry.getKey());
				}
			}
		}
		return null;
	}

	static @Nullable Villager entity(ServerLevel level, UUID uuid) {
		Entity entity = level.getEntity(uuid);
		return entity instanceof Villager villager && !villager.isRemoved() ? villager : null;
	}

	// ================================================================ lifecycle

	private static void onStarted(MinecraftServer server) {
		CANDIDATES.clear();
		TaskEngine.clear();
		VillagerInteractions.clear();
		RouteSelection.clear();
		ZoneVisualizer.clear();
		Mines.clear();
		lastDay = -1;
		int maxRadius = VillagerModule.config().zones.maxRadiusChunks;
		Function<ZoneType, String> zoneNames = type -> Lang.strip(Lang.raw(type.langKey()));
		String routeName = Lang.strip(Lang.raw("villager.route.base"));
		for (Guild guild : GuildManager.all()) {
			guild.villagers.sanitize(maxRadius, zoneNames, routeName);
			// Data saved before work zones: per-villager work positions become zones.
			int migrated = guild.villagers.migrateLegacyWorkPositions(zoneNames, System.currentTimeMillis());
			if (migrated > 0) {
				Guildy.LOGGER.info("Guild {}: {} work position(s) migrated to work zones", guild.name, migrated);
				GuildManager.markDirty();
			}
			for (WorkZone zone : guild.villagers.zones) {
				if (zone.type == ZoneType.MINE && zone.mine == null) {
					// Mines saved before the real / simulated modes existed.
					zone.mine = new MineState(Zones.defaultMineMode());
				}
			}
		}
		ready = true;
		// Entities of the spawn chunks were loaded before the guild data: check them now.
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			List<Villager> villagers = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Villager villager) {
					villagers.add(villager);
				}
			}
			for (Villager villager : villagers) {
				Safe.run("villager.load", () -> onEntityLoad(villager, level));
			}
		}
	}

	/** /guild admin reload: zones larger than a lowered maximum radius shrink to it. */
	private static void onReload() {
		int max = VillagerModule.config().zones.maxRadiusChunks;
		for (Guild guild : GuildManager.all()) {
			for (WorkZone zone : guild.villagers.zones) {
				if (zone.radius > max) {
					Zones.setRadius(guild, zone, max);
				}
			}
		}
	}

	private static void onStopping(MinecraftServer server) {
		ready = false;
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			// Candidates are transient: remove them before the final save.
			for (List<Candidate> list : CANDIDATES.values()) {
				for (Candidate candidate : list) {
					Villager villager = entity(level, candidate.entity());
					if (villager != null) {
						villager.discard();
					}
				}
			}
		}
		CANDIDATES.clear();
		TaskEngine.clear();
		VillagerInteractions.clear();
		RouteSelection.clear();
		ZoneVisualizer.clear();
		Mines.clear();
	}

	// ================================================================ ticking

	/** Every tick: processes the villagers of bucket (tick % 20), so each one runs once per second. */
	private static void tick() {
		if (!ready) {
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		int bucket = (int) (Scheduler.ticks() % 20);
		for (Guild guild : GuildManager.all()) {
			if (guild.villagers.members.isEmpty()) {
				continue;
			}
			List<VillagerRecord> batch = null;
			for (VillagerRecord record : guild.villagers.members.values()) {
				if (Math.floorMod(record.uuid.hashCode(), 20) == bucket) {
					if (batch == null) {
						batch = new ArrayList<>();
					}
					batch.add(record);
				}
			}
			if (batch == null) {
				continue;
			}
			for (VillagerRecord record : batch) {
				if (guild.villagers.members.get(record.uuid) == record) {
					if (record.isAway()) {
						Safe.run("villager.expedition", () -> Expeditions.tickAway(level, guild, record));
					} else {
						Safe.run("villager.task", () -> TaskEngine.process(level, guild, record));
					}
				}
			}
		}
	}

	private static void everySecond() {
		if (!ready) {
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (lastDay >= 0 && day > lastDay) {
			for (Guild guild : List.copyOf(GuildManager.all())) {
				Safe.run("villager.morning", () -> {
					Storage.cleanup(level, guild);
					tryArrival(level, guild, ArrivalMode.MORNING);
				});
			}
		}
		lastDay = day;

		Iterator<Map.Entry<UUID, List<Candidate>>> it = CANDIDATES.entrySet().iterator();
		List<Runnable> departures = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, List<Candidate>> entry = it.next();
			Guild guild = GuildManager.get(entry.getKey());
			if (guild == null) {
				it.remove();
				continue;
			}
			Iterator<Candidate> waiting = entry.getValue().iterator();
			while (waiting.hasNext()) {
				Candidate candidate = waiting.next();
				if (Scheduler.ticks() >= candidate.expiresAt()) {
					waiting.remove();
					departures.add(() -> candidateLeaves(level, guild, candidate, "villager.candidate.timeout"));
				}
			}
			if (entry.getValue().isEmpty()) {
				it.remove();
			}
		}
		for (Runnable departure : departures) {
			Safe.run("villager.candidate.timeout", departure);
		}
		VillagerInteractions.tickSelections();
		RouteSelection.tick();
	}

	// ================================================================ arrivals

	static void scheduleFirstArrival(Guild guild) {
		UUID id = guild.id;
		int delay = VillagerModule.config().arrivals.firstArrivalDelaySeconds;
		Scheduler.later("villager.first_arrival", delay * 20, () -> {
			Guild current = GuildManager.get(id);
			ServerLevel level = GuildyServer.claimLevel();
			if (current != null && level != null && ready) {
				tryArrival(level, current, ArrivalMode.FIRST);
			}
		});
	}

	/**
	 * Tries to bring candidates to the guild: a group of {@code arrivals.groupMin..groupMax} (one for the admin command),
	 * within the free places, free beds and {@code arrivals.maxCandidates}.
	 *
	 * @return null on success (at least one candidate came), otherwise a lang key explaining why not
	 */
	static @Nullable String tryArrival(ServerLevel level, Guild guild, ArrivalMode mode) {
		VillagerConfig config = VillagerModule.config();
		if (!guild.isFounded()) {
			return "villager.error.not_founded";
		}
		int waiting = candidates(guild).size();
		if (mode != ArrivalMode.FORCED && waiting >= config.arrivals.maxCandidates) {
			return "villager.error.already_candidate";
		}
		BuiltStructure reception = guild.structures.find(RECEPTION);
		if (reception == null && mode != ArrivalMode.FORCED) {
			return "villager.error.no_reception";
		}
		BlockPos anchor = receptionAnchor(guild);
		if (anchor == null) {
			return "villager.error.no_reception";
		}
		if (!level.isLoaded(anchor)) {
			return "villager.error.not_loaded";
		}
		int room = maxVillagers(guild) - guild.villagers.members.size() - waiting;
		if (mode != ArrivalMode.FORCED && room <= 0) {
			return waiting > 0 ? "villager.error.already_candidate" : "villager.error.full";
		}
		if (mode == ArrivalMode.MORNING && ThreadLocalRandom.current().nextDouble() >= config.arrivals.arrivalChance) {
			return "villager.error.no_luck";
		}
		int count = 1;
		if (mode != ArrivalMode.FORCED) {
			VillagerConfig.Arrivals arrivals = config.arrivals;
			count = arrivals.groupMin + ThreadLocalRandom.current().nextInt(arrivals.groupMax - arrivals.groupMin + 1);
			count = Math.min(count, Math.min(room, arrivals.maxCandidates - waiting));
			if (arrivals.requireFreeBed) {
				int beds = freeBeds(level, guild) - waiting;
				if (beds <= 0) {
					Msg.guild(guild, "villager.arrival.no_bed");
					return "villager.arrival.no_bed";
				}
				count = Math.min(count, beds);
			}
		}
		if (count > 1) {
			Msg.guild(guild, "villager.arrival.group", count);
		}
		String error = null;
		int arrived = 0;
		for (int i = 0; i < count; i++) {
			String result = spawnCandidate(level, guild, anchor);
			if (result == null) {
				arrived++;
			} else if (error == null) {
				error = result;
			}
		}
		return arrived > 0 ? null : error;
	}

	/** Free beds in the loaded claimed chunks, minus loaded guild villagers that have no bed yet. */
	static int freeBeds(ServerLevel level, Guild guild) {
		PoiManager poi = level.getPoiManager();
		int free = 0;
		for (long key : guild.claims()) {
			int cx = ChunkKey.x(key);
			int cz = ChunkKey.z(key);
			if (!level.hasChunk(cx, cz)) {
				continue;
			}
			free += (int) poi.getInChunk(type -> type.is(PoiTypes.HOME), new ChunkPos(cx, cz), PoiManager.Occupancy.HAS_SPACE).count();
		}
		int homeless = 0;
		for (VillagerRecord record : guild.villagers.members.values()) {
			Villager villager = entity(level, record.uuid);
			if (villager != null && !villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
				homeless++;
			}
		}
		return free - homeless;
	}

	/** Reception sign (else structure origin, else the heart). */
	private static @Nullable BlockPos receptionAnchor(Guild guild) {
		BuiltStructure reception = guild.structures.find(RECEPTION);
		if (reception != null) {
			if (reception.signs != null && !reception.signs.isEmpty() && reception.signs.getFirst() != null) {
				return reception.signs.getFirst();
			}
			if (reception.origin != null) {
				return reception.origin;
			}
		}
		return guild.heart;
	}

	private static @Nullable String spawnCandidate(ServerLevel level, Guild guild, BlockPos anchor) {
		BlockPos spot = WorldUtil.findSafeSpot(level, anchor, 4, true, pos -> GuildManager.at(level, pos) == guild);
		if (spot == null) {
			spot = WorldUtil.findSafeSpot(level, anchor, 4, true, pos -> true);
		}
		if (spot == null) {
			return "villager.error.no_spot";
		}
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (villager == null) {
			return "villager.error.spawn_failed";
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		villager.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
		villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		villager.setAge(0);
		ResourceKey<VillagerProfession> profession = PROFESSIONS.get(random.nextInt(PROFESSIONS.size()));
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), profession));
		villager.setVillagerXp(1);
		villager.refreshBrain(level);
		villager.setPersistenceRequired();
		villager.addTag(TAG_CANDIDATE);
		String name = pickName(guild);
		villager.setCustomName(Component.literal(name).withStyle(ChatFormatting.YELLOW));
		villager.setCustomNameVisible(true);
		long timeout = VillagerModule.config().arrivals.candidateTimeoutSeconds * 20L;
		Candidate candidate = new Candidate(villager.getUUID(), name, Scheduler.ticks() + timeout);
		// Tracked before being added: the load event must not discard it as an orphan candidate.
		CANDIDATES.computeIfAbsent(guild.id, id -> new ArrayList<>()).add(candidate);
		if (!level.addFreshEntity(villager)) {
			removeCandidate(guild, candidate);
			return "villager.error.spawn_failed";
		}
		WorldUtil.poof(level, villager);
		WorldUtil.happy(level, villager);
		announce(guild, candidate);
		return null;
	}

	private static String pickName(Guild guild) {
		List<String> names = VillagerModule.config().names;
		Set<String> used = new HashSet<>();
		for (VillagerRecord record : guild.villagers.members.values()) {
			used.add(record.name);
		}
		for (Candidate candidate : candidates(guild)) {
			used.add(candidate.name());
		}
		List<String> free = new ArrayList<>();
		for (String name : names) {
			if (!used.contains(name)) {
				free.add(name);
			}
		}
		List<String> pool = free.isEmpty() ? names : free;
		return pool.isEmpty() ? "Villageois" : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	private static void announce(Guild guild, Candidate candidate) {
		MutableComponent message = Msg.prefixed(Lang.get("villager.candidate.arrived", candidate.name()))
				.append(Msg.button("villager.candidate.button.accept", "/guild villagers accept " + candidate.name(),
						"villager.candidate.button.accept.hover"))
				.append(Component.literal(" "))
				.append(Msg.button("villager.candidate.button.refuse", "/guild villagers refuse " + candidate.name(),
						"villager.candidate.button.refuse.hover"));
		for (ServerPlayer player : guild.onlineMembers()) {
			player.sendSystemMessage(message);
			Msg.sound(player, SoundEvents.VILLAGER_AMBIENT, 0.8f, 1.0f);
		}
	}

	// ================================================================ accept / refuse

	static void accept(ServerPlayer player) {
		accept(player, null);
	}

	/** Accepts the candidate with this name (the oldest one when null). */
	static void accept(ServerPlayer player, @Nullable String name) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return;
		}
		Candidate candidate = findCandidate(guild, name);
		if (candidate == null) {
			Msg.send(player, name == null || name.isBlank() ? "villager.candidate.none" : "villager.candidate.unknown", name == null ? "" : name);
			return;
		}
		if (guild.villagers.members.size() >= maxVillagers(guild)) {
			Msg.send(player, "villager.error.full_count", maxVillagers(guild));
			return;
		}
		removeCandidate(guild, candidate);
		VillagerRecord record = new VillagerRecord(candidate.entity(), candidate.name());
		guild.villagers.members.put(record.uuid, record);
		guild.villagers.leaving.remove(record.uuid);
		GuildManager.markDirty();
		ServerLevel level = GuildyServer.claimLevel();
		Villager villager = level == null ? null : entity(level, candidate.entity());
		if (villager != null) {
			setupMember(villager, record);
			WorldUtil.happy(level, villager);
			WorldUtil.playSound(level, villager, SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
		}
		// If not loaded, the entity is converted when its chunk loads (record exists).
		Msg.guild(guild, "villager.candidate.accepted", candidate.name());
	}

	static void refuse(ServerPlayer player) {
		refuse(player, null);
	}

	/** Sends the candidate with this name away (the oldest one when null). */
	static void refuse(ServerPlayer player, @Nullable String name) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return;
		}
		Candidate candidate = findCandidate(guild, name);
		if (candidate == null) {
			Msg.send(player, name == null || name.isBlank() ? "villager.candidate.none" : "villager.candidate.unknown", name == null ? "" : name);
			return;
		}
		removeCandidate(guild, candidate);
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			candidateLeaves(level, guild, candidate, "villager.candidate.refused");
		}
	}

	/** Candidate (already removed from the map) leaves: despawn with particles if loaded (else discarded on load). */
	private static void candidateLeaves(ServerLevel level, Guild guild, Candidate candidate, @Nullable String messageKey) {
		Villager villager = entity(level, candidate.entity());
		if (villager != null) {
			WorldUtil.playSound(level, villager, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
			WorldUtil.poof(level, villager);
			villager.discard();
		}
		if (messageKey != null) {
			Msg.guild(guild, messageKey, candidate.name());
		}
	}

	/** Admin: candidate right now (no roll, no limit, no bed). */
	static @Nullable String forceCandidate(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return "error.internal";
		}
		return tryArrival(level, guild, ArrivalMode.FORCED);
	}

	// ================================================================ members

	/** Cheap per-step check that a member entity carries our markers (tags, persistence, profession lock, name). */
	static void ensureMemberSetup(Villager villager, VillagerRecord record) {
		if (!villager.entityTags().contains(TAG_VILLAGER) || villager.entityTags().contains(TAG_CANDIDATE)) {
			setupMember(villager, record);
			return;
		}
		if (!villager.isPersistenceRequired()) {
			villager.setPersistenceRequired();
		}
		lockProfession(villager);
		Component customName = villager.getCustomName();
		if (customName == null) {
			villager.setCustomName(Component.literal(record.name));
		} else {
			// Renamed with a name tag: follow the new name.
			String name = customName.getString().trim();
			if (!name.isEmpty() && !name.equals(record.name) && name.length() <= 32) {
				record.name = name;
				GuildManager.markDirty();
			}
		}
	}

	private static void setupMember(Villager villager, VillagerRecord record) {
		villager.removeTag(TAG_CANDIDATE);
		villager.addTag(TAG_VILLAGER);
		villager.setPersistenceRequired();
		villager.setCustomName(Component.literal(record.name));
		villager.setCustomNameVisible(false);
		lockProfession(villager);
	}

	/**
	 * Vanilla ResetProfession fires the profession of a level-1 villager with 0 XP and no job site: one XP point keeps
	 * the profession forever (same as a single trade), without unlocking the next trade level (needs 10).
	 */
	static void lockProfession(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		if (!profession.is(VillagerProfession.NONE) && !profession.is(VillagerProfession.NITWIT) && villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1);
		}
	}

	/** Back to a normal villager (guild disbanded / record lost). */
	static void release(ServerLevel level, Villager villager) {
		villager.removeTag(TAG_VILLAGER);
		villager.removeTag(TAG_CANDIDATE);
		Guards.onRelease(level, villager);
		if (TaskEngine.isTaskTool(villager.getMainHandItem())) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		villager.setCustomNameVisible(false);
		TaskEngine.forget(villager.getUUID());
		TaskEngine.unlockBrain(level, villager);
	}

	/**
	 * Changes the task. Idle villagers and guards never deposit: items they carry are handed to {@code actor}
	 * (the player changing the task).
	 */
	static void setTask(Guild guild, VillagerRecord record, Task task, ServerPlayer actor) {
		if (record.task == task) {
			return;
		}
		Task previous = record.task;
		record.task = task;
		Missions.reset(record);
		Guards.onTaskChange(guild, record, previous, task, actor);
		// The zone / route assignment is kept: ignored while it does not match the task, back when it does again.
		if ((task == Task.IDLE || task == Task.GUARD) && !record.carried.isEmpty()) {
			for (ItemStack stack : record.carried) {
				ItemUtil.giveOrDrop(actor, stack);
			}
			record.carried.clear();
			Msg.send(actor, "villager.task.items_returned", record.name);
		}
		GuildManager.markDirty();
	}

	/** The villager leaves the guild (despawn with particles; if unloaded, despawned when its chunk loads). */
	static void leave(Guild guild, VillagerRecord record, String messageKey) {
		ServerLevel level = GuildyServer.claimLevel();
		Villager villager = level == null ? null : entity(level, record.uuid);
		removeRecord(guild, record);
		if (villager != null) {
			dropCarried(level, record, villager.position());
			Guards.onLeave(level, guild, villager);
			WorldUtil.playSound(level, villager, SoundEvents.VILLAGER_NO, 1.0f, 0.9f);
			WorldUtil.poof(level, villager);
			villager.discard();
		} else {
			guild.villagers.leaving.add(record.uuid);
			if (level != null) {
				stashCarried(level, guild, record);
			}
		}
		Msg.guild(guild, messageKey, record.name);
	}

	/** "Renvoyer" from the menu: carried items go to the player, then the villager leaves. */
	static void dismiss(ServerPlayer player, Guild guild, VillagerRecord record) {
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return;
		}
		if (guild.villagers.members.get(record.uuid) != record) {
			Msg.send(player, "villager.error.unknown");
			return;
		}
		for (ItemStack stack : record.carried) {
			ItemUtil.giveOrDrop(player, stack);
		}
		record.carried.clear();
		Guards.onDismiss(player, record);
		leave(guild, record, "villager.dismissed");
	}

	/**
	 * "Appeler ici": teleports the villager next to the player (who stands in the territory or in one of the guild's
	 * zones), wherever it is: an unloaded villager is fetched by loading its last known chunk for a moment, see
	 * {@link VillagerCalls}. Messages are sent either way.
	 */
	static VillagerCalls.Result summon(ServerPlayer player, Guild guild, VillagerRecord record) {
		return VillagerCalls.call(player, guild, record);
	}

	static void removeRecord(Guild guild, VillagerRecord record) {
		guild.villagers.members.remove(record.uuid);
		TaskEngine.forget(record.uuid);
		GuildManager.markDirty();
	}

	private static void dropCarried(ServerLevel level, VillagerRecord record, Vec3 pos) {
		for (ItemStack stack : record.carried) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.5, pos.z, stack.copy());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		record.carried.clear();
	}

	/** Unloaded villager leaving: its items go to a loaded deposit chest if possible (otherwise they are lost). */
	private static void stashCarried(ServerLevel level, Guild guild, VillagerRecord record) {
		for (StorageChest chest : guild.villagers.chests) {
			if (record.carried.isEmpty()) {
				break;
			}
			if (!chest.isDeposit() || !Storage.usable(level, guild, chest)) {
				continue;
			}
			Container container = Storage.container(level, chest.pos);
			if (container == null) {
				continue;
			}
			List<ItemStack> leftovers = new ArrayList<>();
			for (ItemStack stack : record.carried) {
				ItemStack leftover = Storage.insert(container, stack);
				if (!leftover.isEmpty()) {
					leftovers.add(leftover);
				}
			}
			record.carried.clear();
			record.carried.addAll(leftovers);
		}
		record.carried.clear();
	}

	// ================================================================ events

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!ready || !(entity instanceof Villager villager)) {
			return;
		}
		Set<String> tags = villager.entityTags();
		boolean candidateTag = tags.contains(TAG_CANDIDATE);
		if (!candidateTag && !tags.contains(TAG_VILLAGER)) {
			return;
		}
		UUID id = villager.getUUID();
		Owner owner = owner(id);
		if (owner != null) {
			if (owner.record().isAway()) {
				// Stale copy (chunk saved before an expedition departure): the villager is away, its saved copy returns.
				defer(villager, villager::discard);
				return;
			}
			// Member (possibly accepted while unloaded): make sure markers are set.
			defer(villager, () -> ensureMemberSetup(villager, owner.record()));
			return;
		}
		if (candidateTag && candidateGuild(id) != null) {
			return;
		}
		for (Guild guild : GuildManager.all()) {
			if (guild.villagers.leaving.remove(id)) {
				GuildManager.markDirty();
				defer(villager, () -> {
					Guards.onLeave(level, guild, villager);
					WorldUtil.poof(level, villager);
					villager.discard();
				});
				return;
			}
		}
		if (candidateTag) {
			// Candidates are transient: an untracked one (server restarted, refused while unloaded...) leaves.
			defer(villager, villager::discard);
		} else {
			// Its guild is gone: becomes a normal villager.
			defer(villager, () -> release(level, villager));
		}
	}

	/** Runs next tick (never modify / remove entities inside the load callback). */
	private static void defer(Villager villager, Runnable action) {
		Scheduler.later("villager.load.fix", 1, () -> {
			if (!villager.isRemoved()) {
				action.run();
			}
		});
	}

	private static void onEntityUnload(Entity entity) {
		if (!ready || !(entity instanceof Villager villager)) {
			return;
		}
		Entity.RemovalReason reason = villager.getRemovalReason();
		if (reason == null || !reason.shouldDestroy()) {
			return;
		}
		UUID id = villager.getUUID();
		Guild candidateGuild = candidateGuild(id);
		if (candidateGuild != null) {
			Candidate candidate = removeCandidateEntity(candidateGuild, id);
			if (candidate != null) {
				Msg.guild(candidateGuild, "villager.candidate.lost", candidate.name());
			}
			return;
		}
		Owner owner = owner(id);
		if (owner == null || owner.record().isAway() || !(villager.level() instanceof ServerLevel level)) {
			// Away = removed on purpose for an expedition.
			return;
		}
		dropCarried(level, owner.record(), villager.position());
		removeRecord(owner.guild(), owner.record());
		Msg.guild(owner.guild(), "villager.lost.vanished", owner.record().name);
	}

	private static void onDeath(LivingEntity entity) {
		if (!ready || !(entity instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		UUID id = villager.getUUID();
		Guild candidateGuild = candidateGuild(id);
		if (candidateGuild != null) {
			Candidate candidate = removeCandidateEntity(candidateGuild, id);
			if (candidate != null) {
				Msg.guild(candidateGuild, "villager.candidate.died", candidate.name());
			}
			return;
		}
		Owner owner = owner(id);
		if (owner == null) {
			return;
		}
		dropCarried(level, owner.record(), villager.position());
		removeRecord(owner.guild(), owner.record());
		Msg.guild(owner.guild(), level.isDarkOutside() ? "villager.death.night" : "villager.death", owner.record().name);
	}

	private static void onConversion(Mob previous, Mob converted) {
		if (!ready || !(previous instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		// Conversion copies entity tags: the zombie villager / witch must not look like a guild villager.
		converted.removeTag(TAG_VILLAGER);
		converted.removeTag(TAG_CANDIDATE);
		UUID id = villager.getUUID();
		Guild candidateGuild = candidateGuild(id);
		if (candidateGuild != null) {
			Candidate candidate = removeCandidateEntity(candidateGuild, id);
			if (candidate != null) {
				Msg.guild(candidateGuild, "villager.candidate.lost", candidate.name());
			}
			return;
		}
		Owner owner = owner(id);
		if (owner == null) {
			return;
		}
		dropCarried(level, owner.record(), villager.position());
		removeRecord(owner.guild(), owner.record());
		Msg.guild(owner.guild(), "villager.lost.converted", owner.record().name);
	}

	private static void onDisband(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		List<Candidate> waiting = CANDIDATES.remove(guild.id);
		if (waiting != null && level != null) {
			for (Candidate candidate : waiting) {
				candidateLeaves(level, guild, candidate, null);
			}
		}
		for (VillagerRecord record : List.copyOf(guild.villagers.members.values())) {
			Villager villager = level == null ? null : entity(level, record.uuid);
			if (villager != null) {
				dropCarried(level, record, villager.position());
				release(level, villager);
			}
			TaskEngine.forget(record.uuid);
		}
		// Unloaded ones are released when their chunk loads (no record anymore).
		guild.villagers.members.clear();
		guild.villagers.leaving.clear();
		Zones.onDisband(guild);
		VillagerInteractions.cancelGuild(guild);
		RouteSelection.cancelGuild(guild);
		GuildManager.markDirty();
	}

	private static void onWaveEnded(GuildEvents.WaveOutcome outcome) {
		if (outcome.result() != GuildEvents.WaveOutcome.Result.DEFEAT) {
			return;
		}
		Guild guild = outcome.guild();
		double chance = Upgrades.reduce(guild, UpgradeEffects.VILLAGER_LOYALTY_PCT, VillagerModule.config().defeatLeaveChance);
		for (VillagerRecord record : List.copyOf(guild.villagers.members.values())) {
			// Villagers away on an expedition were not there for the defeat.
			if (!record.isAway() && ThreadLocalRandom.current().nextDouble() < chance) {
				leave(guild, record, "villager.left.defeat");
			}
		}
	}

	/** Remaining seconds before the candidate leaves. */
	static long remainingSeconds(Candidate candidate) {
		return Math.max(0, (candidate.expiresAt() - Scheduler.ticks()) / 20);
	}
}
