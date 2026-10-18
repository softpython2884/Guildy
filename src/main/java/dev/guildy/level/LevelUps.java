package dev.guildy.level;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Level-ups of a guild: a single announcement for all the levels gained at once (title, chat, sound), the one-shot
 * rewards of the levels past 10 (treasury gold, free extension flags), the rank and milestone announcements, then
 * {@link GuildEvents#LEVEL_UP} once per level gained.
 */
public final class LevelUps {
	private LevelUps() {
	}

	/** Called by {@link GuildManager#addXp} after the level went from {@code from} to {@code to}. */
	public static void levelsGained(Guild guild, int from, int to) {
		GuildyConfig config = GuildyServer.config();
		GuildyConfig.LevelRewards rewards = config.levels.rewards;
		int previous = guild.rewardedLevel;
		LevelRewardRules.Gains gains = LevelRewardRules.between(rewards, from, to, previous);
		guild.rewardedLevel = Math.max(previous, to);
		if (gains.gold() > 0) {
			GuildManager.addTreasury(guild, gains.gold());
		}
		if (gains.flags() > 0) {
			guild.freeFlags += gains.flags();
		}
		// Levels regained after an admin lowered the guild: no "new" villager place, rank or server announcement.
		int firstNew = Math.max(from, Math.min(previous, to));
		int slots = config.levelVillagerSlots(to) - config.levelVillagerSlots(firstNew);
		GuildyConfig.Rank before = config.rank(from);
		GuildyConfig.Rank after = config.rank(to);
		boolean newRank = after != null && (before == null || after.level != before.level) && after.level > previous;

		if (to - from == 1) {
			Msg.guild(guild, "guild.levelup", to);
		} else {
			Msg.guild(guild, "guild.levelup.multi", to - from, to);
		}
		if (!gains.milestones().isEmpty()) {
			Msg.guild(guild, "guild.levelup.milestone", gains.milestones().getLast());
		}
		String summary = rewardSummary(gains.gold(), gains.flags(), slots);
		if (!summary.isEmpty()) {
			Msg.guild(guild, "guild.levelup.rewards", summary);
		}
		if (gains.flags() > 0) {
			Msg.guild(guild, "guild.levelup.flags_hint", guild.freeFlags);
		}
		if (newRank) {
			Msg.guild(guild, "guild.levelup.rank", after.name);
		}
		if (to >= config.maxLevel()) {
			Msg.guild(guild, "guild.levelup.max", to);
		} else if (rewards.enabled) {
			int next = LevelRewardRules.nextMilestone(rewards, to, config.maxLevel());
			if (next > 0 && to >= LevelScaling.CLASSIC_LEVELS) {
				Msg.guild(guild, "guild.levelup.next", next);
			}
		}
		Component subtitle = newRank ? Lang.get("guild.levelup.subtitle.rank", to, after.name) : Lang.get("guild.levelup.subtitle", to);
		Msg.guildTitle(guild, Lang.get("guild.levelup.title"), subtitle);
		Msg.guildSound(guild, gains.milestones().isEmpty() && !newRank ? SoundEvents.PLAYER_LEVELUP : SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
		if (rewards.announceMilestones && (!gains.milestones().isEmpty() || (newRank && to > LevelScaling.CLASSIC_LEVELS))) {
			broadcast(guild, newRank ? Lang.raw("guild.milestone.broadcast.rank", guild.name, to, after.name)
					: Lang.raw("guild.milestone.broadcast", guild.name, to));
		}
		for (int level = from + 1; level <= to; level++) {
			GuildEvents.fire2(GuildEvents.LEVEL_UP, guild, level);
		}
	}

	/** "+120 or, +1 drapeau d'extension offert, +1 place de villageois" (empty when nothing). */
	public static String rewardSummary(long gold, int flags, int villagerSlots) {
		List<String> parts = new ArrayList<>();
		if (gold > 0) {
			parts.add(Lang.raw("level.reward.gold", gold));
		}
		if (flags > 0) {
			parts.add(Lang.raw(flags == 1 ? "level.reward.flag" : "level.reward.flags", flags));
		}
		if (villagerSlots > 0) {
			parts.add(Lang.raw(villagerSlots == 1 ? "level.reward.villager" : "level.reward.villagers", villagerSlots));
		}
		return String.join(Lang.raw("level.reward.separator"), parts);
	}

	/** Rewards of the next milestone, for the menus ("+100 or, +1 drapeau offert..."), or "" when none. */
	public static String milestonePreview(GuildyConfig config, int milestone) {
		GuildyConfig.LevelRewards rewards = config.levels.rewards;
		LevelRewardRules.Gains gains = LevelRewardRules.between(rewards, milestone - 1, milestone, 0);
		int slots = config.levelVillagerSlots(milestone) - config.levelVillagerSlots(milestone - 1);
		return rewardSummary(gains.gold(), gains.flags(), slots);
	}

	/** Colored rank name of a level, or null. */
	public static @Nullable String rankName(int level) {
		GuildyConfig.Rank rank = GuildyServer.config().rank(level);
		return rank == null ? null : rank.name;
	}

	/** Sent to every online player who is not a member of the guild (members get the detailed messages). */
	private static void broadcast(Guild guild, String text) {
		if (Lang.strip(text).isBlank()) {
			return;
		}
		Component message = Msg.prefixed(Component.literal(text));
		for (ServerPlayer player : GuildyServer.players()) {
			if (!guild.isMember(player.getUUID())) {
				player.sendSystemMessage(message);
			}
		}
	}
}
