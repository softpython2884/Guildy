package dev.guildy.quest;

import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The quest board structure: sign n shows quest slot n, clicking it opens that quest.
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
		Component target = QuestMenus.targetName(quest);
		if (quest.isCompleted()) {
			return List.of(Lang.get("quest.sign.done"), target, Lang.get("quest.sign.done.reward"), Lang.get("quest.sign.done.claim"));
		}
		return List.of(Lang.get("quest.sign.type." + quest.type.key()), target,
				Lang.get("quest.sign.progress", quest.progress, quest.amount),
				Lang.get("quest.sign.reward", quest.rewardGold));
	}
}
