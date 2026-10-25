package dev.guildy.villager;

import dev.guildy.builds.BuildModule;
import dev.guildy.builds.BuildProject;
import dev.guildy.builds.BuildSite;
import dev.guildy.builds.BuildSites;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The builders of a guild (not saved): the distribution of their roles ({@link BuilderRoles}, every
 * {@code builder.rolesSeconds}), and for the menus what each builder does (its site, the role it works at and why).
 */
public final class Builders {
	private static final Map<UUID, GuildBuilders> GUILDS = new HashMap<>();
	/** A note older than this is not shown as "working on that site". */
	private static final long NOTE_FRESH_TICKS = 10 * 20L;

	/** What a builder does: preferred role (manual or not), the role it works at now, its site (-1: none), when. */
	record Note(BuilderRoles.Role preferred, boolean manual, BuilderRoles.@Nullable Role working, int project, long at) {
	}

	private static final class GuildBuilders {
		final Map<UUID, BuilderRoles.Role> roles = new HashMap<>();
		long rolesAt = -1;
		Set<UUID> rolesFor = Set.of();
		final Map<UUID, Note> notes = new HashMap<>();
	}

	private Builders() {
	}

	/** Called by the build module: transient state dropped at server stop and with a disbanded guild. */
	public static void init() {
		dev.guildy.core.Lifecycle.STOPPING.add(server -> {
			clear();
			BuildStock.clear();
		});
		dev.guildy.data.GuildEvents.DISBANDED.add(guild -> forget(guild.id));
	}

	static void clear() {
		GUILDS.clear();
	}

	static void forget(UUID guild) {
		GUILDS.remove(guild);
	}

	private static GuildBuilders of(Guild guild) {
		return GUILDS.computeIfAbsent(guild.id, id -> new GuildBuilders());
	}

	static BuilderData data(VillagerRecord record) {
		if (record.builder == null) {
			record.builder = new BuilderData();
			GuildManager.markDirty();
		}
		return record.builder;
	}

	/** Builders of the guild at home, stable order. */
	static List<VillagerRecord> builders(Guild guild) {
		List<VillagerRecord> list = new ArrayList<>();
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (record.task == Task.BUILDER && !record.isAway()) {
				list.add(record);
			}
		}
		list.sort((a, b) -> a.uuid.toString().compareTo(b.uuid.toString()));
		return list;
	}

	/** Preferred role of a builder: its manual role, else the guild's distribution (computed again when due). */
	static BuilderRoles.Role role(Guild guild, VillagerRecord record) {
		BuilderRoles.Role manual = record.builder == null ? null : BuilderRoles.Role.parse(record.builder.role);
		if (manual != null) {
			return manual;
		}
		GuildBuilders state = of(guild);
		List<VillagerRecord> builders = builders(guild);
		Set<UUID> ids = new java.util.LinkedHashSet<>();
		for (VillagerRecord builder : builders) {
			ids.add(builder.uuid);
		}
		long now = Scheduler.ticks();
		long interval = BuildModule.config().builder.rolesSeconds * 20L;
		if (state.rolesAt < 0 || now - state.rolesAt >= interval || !state.rolesFor.equals(ids)) {
			distribute(guild, state, builders, ids, now);
		}
		return state.roles.getOrDefault(record.uuid, BuilderRoles.Role.BUILD);
	}

	private static void distribute(Guild guild, GuildBuilders state, List<VillagerRecord> builders, Set<UUID> ids, long now) {
		List<String> order = new ArrayList<>();
		Map<String, BuilderRoles.Role> manual = new LinkedHashMap<>();
		Map<String, BuilderRoles.Role> previous = new LinkedHashMap<>();
		for (VillagerRecord builder : builders) {
			String id = builder.uuid.toString();
			order.add(id);
			BuilderRoles.Role chosen = builder.builder == null ? null : BuilderRoles.Role.parse(builder.builder.role);
			if (chosen != null) {
				manual.put(id, chosen);
			}
			BuilderRoles.Role before = state.roles.get(builder.uuid);
			if (before != null) {
				previous.put(id, before);
			}
		}
		double buildNeed = 0;
		for (BuildSite site : BuildSites.sites(guild)) {
			if (site.project.active() && site.scanned()) {
				buildNeed += site.actionable();
			}
		}
		double gatherNeed = 0;
		for (int count : Procurement.wanted(guild).values()) {
			gatherNeed += count;
		}
		var cfg = BuildModule.config().builder;
		Map<String, BuilderRoles.Role> assigned = BuilderRoles.assign(order, manual, previous, buildNeed > 0, gatherNeed > 0,
				buildNeed * cfg.buildWeight, gatherNeed * cfg.gatherWeight, cfg.maxGatherShare);
		state.roles.clear();
		assigned.forEach((id, role) -> state.roles.put(UUID.fromString(id), role));
		state.rolesFor = Set.copyOf(ids);
		state.rolesAt = now;
	}

	static void note(Guild guild, VillagerRecord record, BuilderRoles.Role preferred, boolean manual, BuilderRoles.@Nullable Role working, int project) {
		of(guild).notes.put(record.uuid, new Note(preferred, manual, working, project, Scheduler.ticks()));
	}

	static void leave(Guild guild, UUID builder) {
		GuildBuilders state = GUILDS.get(guild.id);
		if (state != null) {
			state.notes.remove(builder);
			state.roles.remove(builder);
			state.rolesAt = -1;
		}
	}

	static @Nullable Note noteOf(Guild guild, UUID builder) {
		GuildBuilders state = GUILDS.get(guild.id);
		return state == null ? null : state.notes.get(builder);
	}

	// ================================================================ menus

	/** Builders working on that site now. */
	public static int assigned(Guild guild, BuildProject project) {
		GuildBuilders state = GUILDS.get(guild.id);
		if (state == null) {
			return 0;
		}
		long now = Scheduler.ticks();
		int count = 0;
		for (Map.Entry<UUID, Note> entry : state.notes.entrySet()) {
			VillagerRecord record = guild.villagers.members.get(entry.getKey());
			Note note = entry.getValue();
			if (record != null && record.task == Task.BUILDER && note.project() == project.id && now - note.at() <= NOTE_FRESH_TICKS) {
				count++;
			}
		}
		return count;
	}

	/** Lore of the "builders" button of a site: who works there, at which role, and what it does. */
	public static List<Component> describe(Guild guild, BuildProject project) {
		List<Component> lore = new ArrayList<>();
		List<VillagerRecord> builders = builders(guild);
		if (builders.isEmpty()) {
			lore.addAll(dev.guildy.gui.GuiUtil.lines("build.gui.builders.none"));
			return lore;
		}
		ServerLevel level = GuildyServer.claimLevel();
		long now = Scheduler.ticks();
		int shown = 0;
		for (VillagerRecord record : builders) {
			Note note = noteOf(guild, record.uuid);
			boolean here = note != null && note.project() == project.id && now - note.at() <= NOTE_FRESH_TICKS;
			BuilderData data = record.builder;
			boolean bound = data != null && data.project == project.id;
			if (!here && !bound) {
				continue;
			}
			if (shown++ >= 8) {
				lore.add(Lang.get("build.gui.builders.more"));
				break;
			}
			BuilderRoles.Role working = note == null ? null : note.working();
			String role = Lang.raw(working == null ? "villager.builder.role.none" : "villager.builder.role." + working.id());
			Villager villager = level == null ? null : VillagerManager.entity(level, record.uuid);
			lore.add(Lang.get("build.gui.builders.line", record.name, role));
			lore.add(Lang.get("build.gui.builders.status", VillagerGui.statusText(record, villager)));
		}
		if (shown == 0) {
			lore.addAll(dev.guildy.gui.GuiUtil.lines("build.gui.builders.elsewhere", builders.size()));
		}
		return lore;
	}
}
