package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * Work zones and patrol routes of a guild: lookups, placement rules, creation / deletion / changes (villagers working
 * there restart their search), and upkeep of the fanion banners (names kept in sync, fanions of deleted zones removed
 * without drop, later if their chunk was not loaded).
 */
final class Zones {
	private static final int MAX_STALE_FANIONS = 64;

	/** A zone and its guild. */
	record Located(Guild guild, WorkZone zone) {
	}

	enum FanionState {
		PRESENT,
		ABSENT,
		UNLOADED,
		/** Zone created without fanion. */
		NONE
	}

	private Zones() {
	}

	private static VillagerConfig config() {
		return VillagerModule.config();
	}

	// ================================================================ lookups

	/** Zone the villager works in: its assigned zone when it matches its task, else null (automatic mode). */
	static @Nullable WorkZone assignedZone(Guild guild, VillagerRecord record) {
		WorkZone zone = guild.villagers.zone(record.zoneId);
		return zone != null && zone.type.task() == record.task ? zone : null;
	}

	/** Patrol route of a guard, else null. */
	static @Nullable PatrolRoute assignedRoute(Guild guild, VillagerRecord record) {
		if (record.task != Task.GUARD) {
			return null;
		}
		PatrolRoute route = guild.villagers.route(record.routeId);
		return route != null && !route.points.isEmpty() ? route : null;
	}

	/** Villagers working in the zone (assigned and with the matching task). */
	static int assignedCount(Guild guild, WorkZone zone) {
		int count = 0;
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.zoneId == zone.id && record.task == zone.type.task()) {
				count++;
			}
		}
		return count;
	}

	/** Guards walking the route. */
	static int assignedCount(Guild guild, PatrolRoute route) {
		int count = 0;
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.routeId == route.id && record.task == Task.GUARD) {
				count++;
			}
		}
		return count;
	}

	/** "« Mine 1 » (Mine)", "Parcours « Ronde »", "Automatique" — null for tasks without zones. */
	static @Nullable String assignmentText(Guild guild, VillagerRecord record) {
		if (record.task.zoneType() == null) {
			return null;
		}
		PatrolRoute route = assignedRoute(guild, record);
		if (route != null) {
			return Lang.raw("villager.assign.route", route.name);
		}
		WorkZone zone = assignedZone(guild, record);
		if (zone != null) {
			return Lang.raw("villager.assign.zone", zone.name, Lang.raw(zone.type.langKey()));
		}
		return Lang.raw("villager.assign.auto");
	}

	/**
	 * Nearest zone of the type whose fanion is in an entity-ticking chunk (automatic miners), or null. Real mines with
	 * nothing left to dig are skipped.
	 */
	static @Nullable WorkZone nearestActive(ServerLevel level, Guild guild, ZoneType type, BlockPos from) {
		WorkZone best = null;
		double bestDist = Double.MAX_VALUE;
		for (WorkZone zone : guild.villagers.zones) {
			if (zone.type != type || !level.isPositionEntityTicking(zone.pos) || (zone.isRealMine() && zone.mine().exhausted)) {
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

	/** The zone (of any guild) whose planted fanion is at {@code pos}. */
	static @Nullable Located fanionAt(BlockPos pos) {
		for (Guild guild : GuildManager.all()) {
			WorkZone zone = guild.villagers.fanionZoneAt(pos);
			if (zone != null) {
				return new Located(guild, zone);
			}
		}
		return null;
	}

	static FanionState fanionState(@Nullable ServerLevel level, WorkZone zone) {
		if (!zone.fanion) {
			return FanionState.NONE;
		}
		if (level == null || !level.isLoaded(zone.pos)) {
			return FanionState.UNLOADED;
		}
		return level.getBlockState(zone.pos).getBlock() instanceof AbstractBannerBlock ? FanionState.PRESENT : FanionState.ABSENT;
	}

	/** True when the position is in the guild's territory or inside one of its zones. */
	static boolean inGuildArea(Level level, Guild guild, BlockPos pos) {
		if (!GuildyServer.isClaimLevel(level)) {
			return false;
		}
		if (GuildManager.at(level, pos) == guild) {
			return true;
		}
		for (WorkZone zone : guild.villagers.zones) {
			if (zone.contains(pos)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ rules

	/**
	 * Null when a fanion or a waypoint may be placed at {@code pos}: claim dimension, founded guild, never in another
	 * guild's territory, at most {@code zones.maxZoneDistance} blocks from the heart. Otherwise the lang key of the
	 * reason ({0} = max distance).
	 */
	static @Nullable String checkPlacement(Level level, Guild guild, BlockPos pos) {
		if (!GuildyServer.isClaimLevel(level)) {
			return "villager.zone.error.dimension";
		}
		BlockPos heart = guild.heart;
		if (heart == null) {
			return "villager.error.not_founded";
		}
		Guild owner = GuildManager.at(level, pos);
		if (owner != null && owner != guild) {
			return "villager.zone.error.other_guild";
		}
		if (!ZoneMath.within(pos.getX(), pos.getZ(), heart.getX(), heart.getZ(), config().zones.maxZoneDistance)) {
			return "villager.zone.error.too_far";
		}
		return null;
	}

	/** Null when the guild may create another zone, else the lang key ({0} = limit). */
	static @Nullable String canCreateZone(Guild guild) {
		return guild.villagers.zones.size() >= config().zones.maxZones ? "villager.zone.error.limit" : null;
	}

	static @Nullable String canCreateRoute(Guild guild) {
		int max = config().routes.maxRoutes;
		if (max <= 0) {
			return "villager.route.error.disabled";
		}
		return guild.villagers.routes.size() >= max ? "villager.route.error.limit" : null;
	}

	// ================================================================ zones

	static String defaultZoneName(Guild guild, ZoneType type) {
		return ZoneMath.nextName(Lang.strip(Lang.raw(type.langKey())), guild.villagers.zoneNames());
	}

	/** Registers a new zone at {@code pos} (rules already checked); {@code fanion}: a fanion was planted there. */
	static WorkZone createZone(Guild guild, ZoneType type, BlockPos pos, String name, boolean fanion) {
		GuildVillagerData data = guild.villagers;
		int radius = Math.min(config().zones.defaultRadiusChunks, config().zones.maxRadiusChunks);
		WorkZone zone = new WorkZone(data.nextZoneId++, type, name, pos.immutable(), radius, System.currentTimeMillis());
		zone.fanion = fanion;
		if (type == ZoneType.MINE) {
			zone.mine = new MineState(defaultMineMode());
		}
		data.zones.add(zone);
		GuildManager.markDirty();
		return zone;
	}

	/** Mode of a new mine (and of mines saved before the modes existed). */
	static MineMode defaultMineMode() {
		return config().realMining.defaultReal ? MineMode.REAL : MineMode.SIMULATED;
	}

	/** Deletes the zone: its fanion disappears (no drop), its villagers go back to the automatic mode. */
	static void deleteZone(@Nullable ServerLevel level, Guild guild, WorkZone zone) {
		guild.villagers.zones.remove(zone);
		Mines.forgetZone(guild, zone);
		if (zone.fanion) {
			removeFanion(level, guild, zone.pos, zone.name);
		}
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.zoneId == zone.id) {
				record.zoneId = -1;
				TaskEngine.resetTask(record.uuid);
			}
		}
		GuildManager.markDirty();
	}

	/** Its fanion was planted at {@code pos}: the old one disappears, the zone follows. */
	static void moveZone(@Nullable ServerLevel level, Guild guild, WorkZone zone, BlockPos pos) {
		if (zone.fanion && !pos.equals(zone.pos)) {
			removeFanion(level, guild, zone.pos, zone.name);
		}
		zone.pos = pos.immutable();
		zone.fanion = true;
		// A real mine plans a new staircase from the new fanion (its plan follows the fanion position).
		Mines.forgetZone(guild, zone);
		restart(guild, zone);
		GuildManager.markDirty();
	}

	static void rename(@Nullable ServerLevel level, WorkZone zone, String name) {
		zone.name = name;
		if (level != null && zone.fanion) {
			syncBannerName(level, zone.pos, name);
		}
		GuildManager.markDirty();
	}

	static void setRadius(Guild guild, WorkZone zone, int radius) {
		zone.radius = Math.max(0, Math.min(config().zones.maxRadiusChunks, radius));
		restart(guild, zone);
		GuildManager.markDirty();
	}

	static void setObjective(Guild guild, WorkZone zone, ZoneObjective objective) {
		zone.objective = ZoneObjective.validFor(zone.type, objective);
		if (zone.type == ZoneType.MINE) {
			// Other galleries (branches / quarry rooms) and maybe another default depth.
			zone.mine().resetLevel();
		}
		restart(guild, zone);
		GuildManager.markDirty();
	}

	// ================================================================ mines

	static void setMineMode(Guild guild, WorkZone zone, MineMode mode) {
		zone.mine().mode = mode;
		Mines.forgetZone(guild, zone);
		restart(guild, zone);
		GuildManager.markDirty();
	}

	/** New gallery depth: the level is chosen again (the staircase goes on or stops higher). */
	static void setMineTarget(Guild guild, WorkZone zone, int targetY) {
		MineState state = zone.mine();
		state.targetY = targetY;
		state.resetLevel();
		restart(guild, zone);
		GuildManager.markDirty();
	}

	/** "Réinitialiser l'exploitation": a new staircase is chosen, the galleries start over (dug tunnels stay). */
	static void resetMine(Guild guild, WorkZone zone) {
		MineState state = zone.mine();
		state.resetPlan();
		state.rejected.clear();
		state.issue("", null);
		state.epoch++;
		Mines.forgetZone(guild, zone);
		restart(guild, zone);
		GuildManager.markDirty();
	}

	/** Assigns the villager to a zone (null = automatic); a guard leaves its route. */
	static void assign(VillagerRecord record, @Nullable WorkZone zone) {
		record.zoneId = zone == null ? -1 : zone.id;
		record.routeId = -1;
		TaskEngine.resetTask(record.uuid);
		GuildManager.markDirty();
	}

	/** Assigns a guard to a route (null = none); it leaves its zone. */
	static void assignRoute(VillagerRecord record, @Nullable PatrolRoute route) {
		record.routeId = route == null ? -1 : route.id;
		record.zoneId = -1;
		TaskEngine.resetTask(record.uuid);
		GuildManager.markDirty();
	}

	/** Villagers working in the zone start their search over (settings changed). */
	private static void restart(Guild guild, WorkZone zone) {
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.zoneId == zone.id) {
				TaskEngine.resetTask(record.uuid);
			}
		}
	}

	// ================================================================ routes

	static PatrolRoute createRoute(Guild guild, List<BlockPos> points) {
		GuildVillagerData data = guild.villagers;
		String name = ZoneMath.nextName(Lang.strip(Lang.raw("villager.route.base")), data.routeNames());
		PatrolRoute route = new PatrolRoute(data.nextRouteId++, name, points, System.currentTimeMillis());
		data.routes.add(route);
		GuildManager.markDirty();
		return route;
	}

	/** New waypoints for an existing route (its guards keep it and restart at the nearest point). */
	static void redraw(Guild guild, PatrolRoute route, List<BlockPos> points) {
		route.points = new java.util.ArrayList<>(points);
		restart(guild, route);
		GuildManager.markDirty();
	}

	static void setPingPong(Guild guild, PatrolRoute route, boolean pingPong) {
		route.pingPong = pingPong;
		restart(guild, route);
		GuildManager.markDirty();
	}

	static void deleteRoute(Guild guild, PatrolRoute route) {
		guild.villagers.routes.remove(route);
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.routeId == route.id) {
				record.routeId = -1;
				TaskEngine.resetTask(record.uuid);
			}
		}
		GuildManager.markDirty();
	}

	private static void restart(Guild guild, PatrolRoute route) {
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.routeId == route.id) {
				TaskEngine.resetTask(record.uuid);
			}
		}
	}

	// ================================================================ fanion blocks

	/** Removes a fanion banner without drop; if its chunk is not loaded, it is removed once loaded. */
	static void removeFanion(@Nullable ServerLevel level, Guild guild, BlockPos pos, String name) {
		if (level != null && level.isLoaded(pos)) {
			if (level.getBlockState(pos).getBlock() instanceof AbstractBannerBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			return;
		}
		List<GuildVillagerData.StaleFanion> stale = guild.villagers.staleFanions;
		if (stale.size() < MAX_STALE_FANIONS) {
			stale.add(new GuildVillagerData.StaleFanion(pos.immutable(), name));
			GuildManager.markDirty();
		}
	}

	/** Sets the banner's name (shown on maps) if a banner stands there. */
	static void syncBannerName(ServerLevel level, BlockPos pos, String name) {
		if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof BannerBlockEntity banner)) {
			return;
		}
		Component wanted = Component.literal(name);
		if (wanted.equals(banner.getCustomName())) {
			return;
		}
		ZoneFlags.applyName(level, pos, banner, wanted);
	}

	/**
	 * Every few seconds: loaded fanions carry the name of their zone; stale fanions (deleted / moved zones) whose chunk
	 * is now loaded are removed if the banner there still carries the zone name (never a player's own banner).
	 */
	static void upkeep() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (Guild guild : GuildManager.all()) {
			GuildVillagerData data = guild.villagers;
			for (WorkZone zone : data.zones) {
				if (zone.fanion) {
					syncBannerName(level, zone.pos, zone.name);
				}
			}
			if (data.staleFanions.isEmpty()) {
				continue;
			}
			Iterator<GuildVillagerData.StaleFanion> it = data.staleFanions.iterator();
			while (it.hasNext()) {
				GuildVillagerData.StaleFanion stale = it.next();
				if (!level.isLoaded(stale.pos)) {
					continue;
				}
				it.remove();
				GuildManager.markDirty();
				if (data.fanionZoneAt(stale.pos) == null && level.getBlockEntity(stale.pos) instanceof BannerBlockEntity banner
						&& banner.getCustomName() != null && banner.getCustomName().getString().equals(stale.name)) {
					level.setBlock(stale.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	/** Disbanded guild: its loaded fanions disappear (like the heart). */
	static void onDisband(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (WorkZone zone : guild.villagers.zones) {
			if (zone.fanion && level.isLoaded(zone.pos) && level.getBlockState(zone.pos).getBlock() instanceof AbstractBannerBlock) {
				level.setBlock(zone.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}
}
