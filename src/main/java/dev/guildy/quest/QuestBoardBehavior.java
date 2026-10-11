package dev.guildy.quest;

import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import dev.guildy.structure.StructureManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The quest board structure: sign n shows quest slot n, clicking it opens that quest. Lines: type, target, progress,
 * reward; legendary contracts are written in gold; an assortment shows its items in turn (one per sign refresh).
 */
final class QuestBoardBehavior implements StructureBehavior {

	@Override
	public void onBuilt(ServerLevel level, Guild guild, BuiltStructure structure, ServerPlayer builder) {
		// Free slots are filled right away (a rebuilt board still respects the delay of recently freed slots).
		QuestManager.fillSlots(guild, false, false);
	}

	@Override
	public void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
		QuestMenus.openSlot(player, guild, signIndex);
	}

	@Override
	public @Nullable List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
		if (signIndex < 0 || signIndex >= GuildQuestData.SLOTS) {
			return null;
		}
		QuestInstance quest = guild.quests.bySlot(signIndex);
		if (quest == null) {
			long wait = guild.quests.readyAt(signIndex) - QuestManager.now();
			Component when = wait > 0
					? Lang.get("quest.sign.empty.wait", QuestFormat.minutesCeil(wait))
					: Lang.get("quest.sign.empty.soon");
			return List.of(Component.empty(), Lang.get("quest.sign.empty"), when, Component.empty());
		}
		return lines(guild, quest, QuestManager.now());
	}

	/** The 4 sign lines of a quest at game time {@code now}. */
	static List<Component> lines(Guild guild, QuestInstance quest, long now) {
		Component target = QuestMenus.targetName(quest);
		Component progress = Lang.get("quest.sign.progress", quest.progress, quest.amount);
		if (quest.hasParts() && quest.isActive()) {
			// One item of the assortment at a time (the next one at each periodic sign refresh): name, progress, position.
			long period = Math.max(1, StructureManager.config().signRefreshSeconds) * 20L;
			int index = (int) Math.floorMod(now / period, (long) quest.parts.size());
			QuestPart part = quest.parts.get(index);
			target = QuestMenus.partName(part);
			progress = Lang.get("quest.sign.part", part.progress, part.amount, index + 1, quest.parts.size());
		}
		if (quest.legendary) {
			target = Component.empty().withStyle(ChatFormatting.GOLD).append(target);
		}
		if (quest.isCompleted()) {
			return List.of(Lang.get(quest.legendary ? "quest.sign.done.legendary" : "quest.sign.done"), target,
					Lang.get("quest.sign.done.reward"), Lang.get("quest.sign.done.claim"));
		}
		Component type = Lang.get("quest.sign.type." + quest.type.key());
		if (quest.legendary) {
			// Gold type between stars when it fits on the sign (~15 characters, bold is wider).
			String plain = Lang.strip(Lang.raw("quest.sign.type." + quest.type.key())).trim();
			type = Lang.get(plain.length() <= 9 ? "quest.sign.legendary" : "quest.sign.legendary.long", plain);
		}
		long gold = QuestManager.rewardGold(guild, quest);
		Component reward = Lang.get(quest.legendary ? "quest.sign.reward.legendary" : "quest.sign.reward", gold);
		return List.of(type, target, progress, reward);
	}
}
