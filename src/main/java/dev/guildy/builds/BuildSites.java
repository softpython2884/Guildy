package dev.guildy.builds;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.Procurement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The construction sites of every guild at runtime: loading their copies after a restart, the scan budget shared by all
 * sites (round robin), every second the inventories of the sources, the requests of the missing materials
 * ({@link Procurement}, requester {@code "build:<id>"}, every {@code builder.procurementSeconds}), the boss bars of the
 * members near a site, and the end of a site (title, sound, firework, guild message).
 */
public final class BuildSites {
	private static final Map<String, BuildSite> SITES = new LinkedHashMap<>();
	private static final Map<String, ServerBossEvent> BARS = new HashMap<>();
	/** Firework tag shared with the spell show: such a rocket never hurts anyone and never comes back from the disk. */
	private static final String FIREWORK_TAG = "guildy_firework";
	private static boolean ready;
	private static int roundRobin;

	private BuildSites() {
	}

	static void init() {
		Lifecycle.STARTED.add(BuildSites::onStarted);
		Lifecycle.STOPPING.add(server -> stop());
		Lifecycle.RELOADED.add(BuildSites::onReload);
		GuildEvents.DISBANDED.add(BuildSites::onDisband);
		Scheduler.every("build.scan", 1, 0, () -> {
			if (ready) {
				scanTick();
			}
		});
		Scheduler.every("build.second", 20, 7, () -> {
			if (ready) {
				everySecond();
			}
		});
	}

	static String key(Guild guild, BuildProject project) {
		return guild.id + ":" + project.id;
	}

	// ================================================================ lifecycle

	private static void onStarted(MinecraftServer server) {
		stop();
		int keep = BuildModule.config().projects.keepFinished;
		for (Guild guild : GuildManager.all()) {
			guild.builds.sanitize();
			for (BuildProject removed : guild.builds.trimFinished(keep)) {
				SchematicLibrary.deleteCopy(server, removed.file);
			}
			for (BuildProject project : guild.builds.projects) {
				if (!project.finished) {
					site(guild, project);
				}
			}
		}
		ready = true;
	}

	private static void stop() {
		ready = false;
		for (ServerBossEvent bar : BARS.values()) {
			bar.removeAllPlayers();
		}
		BARS.clear();
		for (BuildSite site : SITES.values()) {
			site.close();
		}
		SITES.clear();
		SchematicLibrary.clear();
	}

	/** builds.json5 reloaded: the plans are applied again (foundation depth, lists, ignored properties). */
	private static void onReload() {
		for (BuildSite site : SITES.values()) {
			BuildPlan plan = site.plan();
			if (plan != null) {
				site.setPlan(plan);
			}
		}
	}

	private static void onDisband(Guild guild) {
		MinecraftServer server = GuildyServer.server();
		for (BuildProject project : new ArrayList<>(guild.builds.projects)) {
			forget(guild, project);
			if (server != null) {
				SchematicLibrary.deleteCopy(server, project.file);
			}
		}
	}

	// ================================================================ sites

	/** Runtime of a site (created and loaded on first use; null for a finished one). */
	public static @Nullable BuildSite site(Guild guild, BuildProject project) {
		if (project.finished) {
			return null;
		}
		String key = key(guild, project);
		BuildSite site = SITES.get(key);
		if (site != null && site.project == project) {
			return site;
		}
		BuildSite created = new BuildSite(guild, project);
		SITES.put(key, created);
		MinecraftServer server = GuildyServer.server();
		if (server != null) {
			created.setLoading();
			SchematicLibrary.loadCopy(server, project.file, result -> {
				if (SITES.get(key) != created) {
					return;
				}
				if (result.ok()) {
					created.setPlan(result.plan());
				} else {
					created.fail(result.error(), result.args());
					Guildy.LOGGER.warn("Guild {}: site {} cannot load {}: {}", guild.name, project.name, project.file, result.error());
				}
			});
		}
		return created;
	}

	/** A new site whose plan is already loaded (from the preview). */
	static BuildSite start(Guild guild, BuildProject project, BuildPlan plan) {
		BuildSite site = new BuildSite(guild, project);
		site.setPlan(plan);
		SITES.put(key(guild, project), site);
		return site;
	}

	/** The guild's unfinished sites with a runtime. */
	public static List<BuildSite> sites(Guild guild) {
		List<BuildSite> list = new ArrayList<>();
		for (BuildProject project : guild.builds.ordered()) {
			BuildSite site = site(guild, project);
			if (site != null) {
				list.add(site);
			}
		}
		return list;
	}

	public static @Nullable BuildSite peek(Guild guild, BuildProject project) {
		return SITES.get(key(guild, project));
	}

	/** Drops the runtime of a site (removed, finished, guild disbanded). */
	static void forget(Guild guild, BuildProject project) {
		BuildSite site = SITES.remove(key(guild, project));
		if (site != null) {
			site.close();
		}
		ServerBossEvent bar = BARS.remove(key(guild, project));
		if (bar != null) {
			bar.removeAllPlayers();
		}
		Procurement.clear(guild, requester(project));
	}

	public static String requester(BuildProject project) {
		return "build:" + project.id;
	}

	/** The site must be planned again (interior mode, foundations changed). */
	static void replan(Guild guild, BuildProject project) {
		BuildSite site = peek(guild, project);
		if (site != null && site.plan() != null) {
			site.setPlan(site.plan());
		}
	}

	// ================================================================ ticking

	private static void scanTick() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || SITES.isEmpty()) {
			return;
		}
		List<BuildSite> active = new ArrayList<>();
		for (BuildSite site : SITES.values()) {
			if (site.ready() && !site.project.finished) {
				active.add(site);
			}
		}
		if (active.isEmpty()) {
			return;
		}
		int budget = BuildModule.config().builder.scanCellsPerTick;
		int share = Math.max(64, budget / active.size());
		int start = Math.floorMod(roundRobin++, active.size());
		for (int i = 0; i < active.size() && budget > 0; i++) {
			BuildSite site = active.get((start + i) % active.size());
			int allowed = Math.min(share, budget);
			int read = Safe.get("build.scan", () -> site.scan(level, allowed), 0);
			budget -= Math.max(1, read);
		}
	}

	private static void everySecond() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		long now = Scheduler.ticks();
		long post = BuildModule.config().builder.procurementSeconds * 20L;
		for (BuildSite site : new ArrayList<>(SITES.values())) {
			Safe.run("build.site", () -> {
				BuildProject project = site.project;
				Guild guild = site.guild;
				if (GuildManager.get(guild.id) != guild || !guild.builds.projects.contains(project)) {
					forget(guild, project);
					return;
				}
				if (!site.ready()) {
					return;
				}
				site.refreshStock(level, now, false);
				if (site.scanned() && site.complete()) {
					finish(level, site);
					return;
				}
				if (project.active() && site.scanned()) {
					if (site.lastPostAt < 0 || now - site.lastPostAt >= post) {
						site.lastPostAt = now;
						Procurement.want(guild, requester(project), site.missing(now));
					}
				} else {
					Procurement.clear(guild, requester(project));
					site.lastPostAt = -1;
				}
				bossbar(level, site);
			});
		}
	}

	/** Progress bar for the members near the site. */
	private static void bossbar(ServerLevel level, BuildSite site) {
		String key = key(site.guild, site.project);
		int radius = BuildModule.config().display.bossbarRadius;
		ServerBossEvent bar = BARS.get(key);
		List<ServerPlayer> near = new ArrayList<>();
		if (radius > 0 && site.scanned()) {
			int[] box = site.box();
			for (ServerPlayer player : site.guild.onlineMembers()) {
				if (player.level() == level && near(player, box, radius)) {
					near.add(player);
				}
			}
		}
		if (near.isEmpty()) {
			if (bar != null) {
				bar.removeAllPlayers();
				BARS.remove(key);
			}
			return;
		}
		if (bar == null) {
			bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
			BARS.put(key, bar);
		}
		BuildSite.Counts counts = site.counts();
		int percent = counts.percent();
		String status = Lang.raw(BuildMenus.statusKey(site), BuildMenus.statusArgs(site));
		bar.setName(Lang.get("build.bossbar", site.project.name, percent, status));
		bar.setProgress(Math.max(0F, Math.min(1F, percent / 100F)));
		bar.setColor(site.project.paused ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.YELLOW);
		for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
			if (!near.contains(player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : near) {
			bar.addPlayer(player);
		}
	}

	private static boolean near(ServerPlayer player, int[] box, int radius) {
		double dx = Math.max(0, Math.max(box[0] - player.getX(), player.getX() - (box[3] + 1)));
		double dz = Math.max(0, Math.max(box[2] - player.getZ(), player.getZ() - (box[5] + 1)));
		double dy = Math.max(0, Math.max(box[1] - player.getY(), player.getY() - (box[4] + 1)));
		return dx * dx + dz * dz + dy * dy <= (double) radius * radius;
	}

	// ================================================================ end of a site

	private static void finish(ServerLevel level, BuildSite site) {
		BuildProject project = site.project;
		Guild guild = site.guild;
		project.finished = true;
		project.finishedAt = System.currentTimeMillis();
		project.paused = false;
		BuildSite.Counts counts = site.counts();
		project.done = counts.done();
		project.total = counts.total();
		GuildManager.markDirty();
		forget(guild, project);
		Msg.guild(guild, "build.finished.message", project.name, project.placed, project.cleared);
		int[] box = site.box();
		int radius = Math.max(32, BuildModule.config().display.bossbarRadius);
		for (ServerPlayer player : guild.onlineMembers()) {
			if (player.level() == level && near(player, box, radius)) {
				Msg.title(player, Lang.get("build.finished.title"), Lang.get("build.finished.subtitle", project.name));
				Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
			}
		}
		if (BuildModule.config().display.firework) {
			firework(level, guild, box);
		}
		int keep = BuildModule.config().projects.keepFinished;
		MinecraftServer server = level.getServer();
		for (BuildProject removed : guild.builds.trimFinished(keep)) {
			SchematicLibrary.deleteCopy(server, removed.file);
		}
	}

	/** One rocket in the guild's colours over the middle of the site (tagged: harmless, never saved). */
	private static void firework(ServerLevel level, Guild guild, int[] box) {
		double x = (box[0] + box[3] + 1) / 2.0;
		double y = box[4] + 1.5;
		double z = (box[2] + box[5] + 1) / 2.0;
		if (!level.isLoaded(BlockPos.containing(x, y, z))) {
			return;
		}
		IntArrayList colors = new IntArrayList();
		colors.add(guild.banner.baseColor().getFireworkColor());
		colors.add(0xFFFFFF);
		IntArrayList fades = new IntArrayList();
		fades.add(0xFFD700);
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(
				new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, colors, fades, true, true),
				new FireworkExplosion(FireworkExplosion.Shape.STAR, fades, colors, false, true))));
		FireworkRocketEntity rocket = new FireworkRocketEntity(level, x, y, z, stack);
		rocket.addTag(FIREWORK_TAG);
		level.addFreshEntity(rocket);
	}

}
