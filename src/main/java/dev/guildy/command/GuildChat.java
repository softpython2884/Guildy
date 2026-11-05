package dev.guildy.command;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.level.LevelRewardRules;
import dev.guildy.level.LevelUps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chat outputs: guild summary, ranking list and the help text.
 */
public final class GuildChat {
	public static final int LIST_PAGE_SIZE = 8;
	public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());
	/** Guild ranking: level desc, XP desc, name. */
	public static final Comparator<Guild> RANKING = (a, b) -> GuildRules.compareRanking(a.level, a.xp, a.name, b.level, b.xp, b.name);

	private GuildChat() {
	}

	/** "120/250 XP" (or "1234 XP, niveau max"). */
	public static String xpText(Guild guild) {
		GuildyConfig config = GuildyServer.config();
		if (guild.level >= config.maxLevel()) {
			return Lang.raw("guild.info.xp_max", guild.xp);
		}
		return Lang.raw("guild.info.xp", guild.xp, config.xpForLevel(guild.level + 1));
	}

	/**
	 * Guild summary.
	 *
	 * @param full also show private data (treasury, heart coordinates): members and admins
	 */
	public static void info(Consumer<Component> out, Guild guild, boolean full) {
		out.accept(Lang.get("guild.info.header", guild.name));
		levelLines(out, guild, full);
		UUID leader = guild.leaderId();
		if (leader != null) {
			out.accept(Lang.get("guild.info.leader", GuildActions.memberName(guild, leader)));
		}
		out.accept(Lang.get("guild.info.members", guild.size(), memberList(guild)));
		out.accept(Lang.get("guild.info.territory", guild.claimCount(), guild.maxChunks()));
		if (guild.heart == null) {
			out.accept(Lang.get("guild.info.heart_none"));
		} else if (full) {
			out.accept(Lang.get("guild.info.heart", guild.heart.getX(), guild.heart.getY(), guild.heart.getZ()));
		} else {
			out.accept(Lang.get("guild.info.heart_founded"));
		}
		if (full) {
			out.accept(Lang.get("guild.info.treasury", guild.treasury));
		}
		out.accept(Lang.get("guild.info.waves", guild.waves.night, guild.waves.victories, guild.waves.defeats));
		if (guild.createdAt > 0) {
			out.accept(Lang.get("guild.info.created", DATE.format(Instant.ofEpochMilli(guild.createdAt))));
		}
	}

	/** Level N / max with the XP, rank, progress bar inside the level, next milestone and (members) free flags. */
	private static void levelLines(Consumer<Component> out, Guild guild, boolean full) {
		GuildyConfig config = GuildyServer.config();
		int max = config.maxLevel();
		out.accept(Lang.get("guild.info.level_of", guild.level, max, xpText(guild)));
		String rank = LevelUps.rankName(guild.level);
		if (rank != null) {
			out.accept(Lang.get("guild.info.rank", rank));
		}
		if (guild.level < max) {
			long[] progress = GuildRules.levelProgress(guild.xp, config.xpForLevel(guild.level), config.xpForLevel(guild.level + 1), false);
			out.accept(Lang.get("guild.info.bar", GuildRules.progressBar(progress[0], progress[1], 20, "&a", "&8", '|'),
					GuildRules.percent(progress[0], progress[1]), progress[0], progress[1]));
			if (config.levels.rewards.enabled) {
				int next = LevelRewardRules.nextMilestone(config.levels.rewards, guild.level, max);
				if (next > 0) {
					String preview = LevelUps.milestonePreview(config, next);
					out.accept(Lang.get("guild.info.milestone", next, preview.isEmpty() ? "-" : preview));
				}
			}
		}
		if (full && guild.freeFlags > 0) {
			out.accept(Lang.get("guild.info.free_flags", guild.freeFlags));
		}
	}

	/** Comma separated member names: leader/officers first, online in green, offline in gray. */
	private static String memberList(Guild guild) {
		List<UUID> ids = new ArrayList<>(guild.memberIds());
		ids.sort(Comparator.<UUID, Integer>comparing(id -> {
			GuildRole role = guild.role(id);
			return role == null ? 0 : -role.ordinal();
		}).thenComparing(id -> GuildActions.memberName(guild, id), String.CASE_INSENSITIVE_ORDER));
		StringBuilder sb = new StringBuilder();
		for (UUID id : ids) {
			if (!sb.isEmpty()) {
				sb.append("&7, ");
			}
			sb.append(GuildyServer.player(id) != null ? "&a" : "&8").append(GuildActions.memberName(guild, id));
		}
		return sb.toString();
	}

	/** Paginated ranking (page is 1-based). */
	public static void list(Consumer<Component> out, int page) {
		List<Guild> guilds = new ArrayList<>(GuildManager.all());
		if (guilds.isEmpty()) {
			out.accept(Msg.prefixed(Lang.get("guild.list.empty")));
			return;
		}
		guilds.sort(RANKING);
		int pages = GuildRules.pageCount(guilds.size(), LIST_PAGE_SIZE);
		int index = GuildRules.clampPage(page - 1, pages);
		out.accept(Lang.get("guild.list.header", guilds.size(), index + 1, pages));
		int rank = index * LIST_PAGE_SIZE;
		for (Guild guild : GuildRules.page(guilds, index, LIST_PAGE_SIZE)) {
			rank++;
			out.accept(GuildActions.clickable(Lang.get("guild.list.entry", rank, guild.name, guild.level, guild.size()),
					"/guild info " + guild.name, Lang.get("guild.list.entry.hover")));
		}
		if (pages > 1) {
			MutableComponent nav = Component.empty();
			if (index > 0) {
				nav.append(GuildActions.clickable(Lang.get("guild.list.prev"), "/guild list " + index, Lang.get("guild.list.page.hover")));
				nav.append(Component.literal("  "));
			}
			if (index < pages - 1) {
				nav.append(GuildActions.clickable(Lang.get("guild.list.next"), "/guild list " + (index + 2), Lang.get("guild.list.page.hover")));
			}
			out.accept(nav);
		}
	}

	/** Game loop explanation with clickable commands. */
	public static void help(ServerPlayer player) {
		player.sendSystemMessage(Lang.get("guild.help.header"));
		for (String line : Lang.raw("guild.help.lines").split("\n")) {
			player.sendSystemMessage(Component.literal(line));
		}
		if (GuildyServer.config().fly.enabled) {
			player.sendSystemMessage(Msg.suggest(Lang.get("guild.help.fly"), "/guild fly", Lang.get("guild.help.fly.hover")));
		}
		if (GuildyServer.config().anchors.enabled) {
			player.sendSystemMessage(Msg.suggest(Lang.get("guild.help.anchor"), "/guild ancrage", Lang.get("guild.help.anchor.hover")));
		}
		MutableComponent buttons = Lang.get("guild.help.commands");
		boolean inGuild = GuildManager.of(player) != null;
		buttons.append(button("menu", "/guild", false));
		if (inGuild) {
			buttons.append(button("info", "/guild info", false));
			buttons.append(button("invite", "/guild invite ", true));
			buttons.append(button("deposit", "/guild deposit", false));
		} else {
			buttons.append(button("create", "/guild create ", true));
		}
		buttons.append(button("list", "/guild list", false));
		player.sendSystemMessage(buttons);
		Component shortcuts = ShortcutCommands.helpLine(player);
		if (shortcuts != null) {
			player.sendSystemMessage(shortcuts);
		}
	}

	private static Component button(String id, String command, boolean suggest) {
		Component label = Lang.get("guild.help.button." + id);
		Component hover = Lang.get("guild.help.button." + id + ".hover");
		MutableComponent button = suggest ? Msg.suggest(label, command, hover) : GuildActions.clickable(label, command, hover);
		return Component.empty().append(button).append(Component.literal(" "));
	}
}
