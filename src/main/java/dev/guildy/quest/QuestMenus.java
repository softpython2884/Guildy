package dev.guildy.quest;

import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import dev.guildy.structure.TextUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Quest menus: the 6 slots of the board, and a detail page per quest (deliver, claim, abandon).
 */
public final class QuestMenus {
	/** Menu slots mirroring the 3 x 2 quest board. */
	private static final int[] SLOT_POSITIONS = {11, 13, 15, 29, 31, 33};

	private QuestMenus() {
	}

	/** Quest board sign click: detail of that quest, or the list when the slot is empty. */
	public static void openSlot(ServerPlayer player, Guild guild, int slot) {
		QuestInstance quest = guild.quests.bySlot(slot);
		if (quest != null) {
			openDetail(player, guild, quest.id);
		} else {
			openList(player, guild);
		}
	}

	public static void openList(ServerPlayer player, Guild guild) {
		SimpleGui gui = GuiUtil.chest(player, 5, Lang.get("quest.gui.title"));
		boolean board = QuestManager.hasBoard(guild);
		for (int slot = 0; slot < GuildQuestData.SLOTS; slot++) {
			QuestInstance quest = guild.quests.bySlot(slot);
			if (quest != null) {
				String id = quest.id;
				List<Component> extra = List.of(Component.empty(), Lang.get("quest.gui.click"));
				gui.setSlot(SLOT_POSITIONS[slot], questIcon(player, quest, extra).setCallback((index, type, action, g) -> Safe.run("quest.gui", () -> {
					GuiUtil.clickSound(player);
					openDetail(player, guild, id);
				})));
			} else {
				gui.setSlot(SLOT_POSITIONS[slot], emptyIcon(guild, slot, board));
			}
		}
		gui.setSlot(4, GuiUtil.item(Items.BOOK, Lang.get("quest.gui.info.name"),
				GuiUtil.lines("quest.gui.info.lore", guild.quests.completedTotal, guild.quests.active.size())));
		if (!board) {
			gui.setSlot(22, GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("quest.gui.noboard.name"), GuiUtil.lines("quest.gui.noboard.lore"))
					.glow()
					.setCallback((index, type, action, g) -> Safe.run("quest.gui.structures", () -> {
						GuiUtil.clickSound(player);
						Hooks.openStructures.accept(player, guild);
					})));
		}
		gui.setSlot(36, GuiUtil.back(() -> Hooks.openMainMenu.accept(player)));
		gui.setSlot(44, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder emptyIcon(Guild guild, int slot, boolean board) {
		List<Component> lore = new ArrayList<>();
		if (board) {
			long wait = guild.quests.readyAt(slot) - QuestManager.now();
			lore.add(wait > 0 ? Lang.get("quest.gui.empty.wait", QuestFormat.duration(wait)) : Lang.get("quest.gui.empty.soon"));
		} else {
			lore.add(Lang.get("quest.gui.empty.noboard"));
		}
		return GuiUtil.item(Items.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_GRAY), Lang.get("quest.gui.empty.name", slot + 1), lore);
	}

	public static void openDetail(ServerPlayer player, Guild guild, String questId) {
		QuestInstance quest = guild.quests.byId(questId);
		if (quest == null) {
			openList(player, guild);
			return;
		}
		SimpleGui gui = GuiUtil.chest(player, 4, Lang.get("quest.gui.detail.title", Lang.strip(Lang.colorize(quest.name))));
		gui.setSlot(4, questIcon(player, quest, List.of()));
		gui.setSlot(13, contributionsIcon(quest));

		if (quest.isActive() && quest.type == QuestType.DELIVER) {
			int have = QuestManager.deliverable(player, quest);
			List<Component> lore = new ArrayList<>();
			lore.add(TextUtil.lang("quest.gui.deliver.have", have, targetName(quest)));
			lore.add(Lang.get("quest.gui.deliver.missing", quest.remaining()));
			lore.addAll(GuiUtil.lines("quest.gui.deliver.lore"));
			gui.setSlot(20, GuiUtil.item(Items.CHEST, Lang.get("quest.gui.deliver.name"), lore).setCallback((i, t, a, g) ->
					Safe.run("quest.gui.deliver", () -> {
						Component error = QuestManager.deliver(player, guild, quest);
						if (error != null) {
							Msg.sendRaw(player, error);
							GuiUtil.errorSound(player);
						} else {
							GuiUtil.successSound(player);
						}
						reopenDetail(player, guild, questId);
					})));
		}
		if (quest.isCompleted()) {
			gui.setSlot(22, GuiUtil.item(Items.EMERALD, Lang.get("quest.gui.claim.name"), rewardLines(quest)).glow().setCallback((i, t, a, g) ->
					Safe.run("quest.gui.claim", () -> {
						Component error = QuestManager.claim(player, guild, quest);
						if (error != null) {
							Msg.sendRaw(player, error);
							GuiUtil.errorSound(player);
						}
						reopenList(player, guild);
					})));
		}
		if (quest.isActive()) {
			List<Component> lore = new ArrayList<>(GuiUtil.lines("quest.gui.abandon.lore"));
			if (!guild.can(player.getUUID(), GuildPermission.QUESTS)) {
				lore.add(Lang.get("quest.gui.need_permission"));
			}
			gui.setSlot(24, GuiUtil.item(Items.BARRIER, Lang.get("quest.gui.abandon.name"), lore)
					.setCallback((i, t, a, g) -> Safe.run("quest.gui.abandon", () -> {
						GuiUtil.clickSound(player);
						confirmAbandon(player, guild, questId);
					})));
		}
		gui.setSlot(27, GuiUtil.back(() -> openList(player, guild)));
		gui.setSlot(35, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static void confirmAbandon(ServerPlayer player, Guild guild, String questId) {
		QuestInstance quest = guild.quests.byId(questId);
		if (quest == null) {
			reopenList(player, guild);
			return;
		}
		GuiUtil.confirm(player, Lang.get("quest.gui.abandon.confirm_title"),
				List.of(TextUtil.lang("quest.gui.abandon.confirm", quest.name)),
				() -> {
					QuestInstance current = guild.quests.byId(questId);
					Component error = current == null ? Lang.get("quest.claim.gone") : QuestManager.abandon(player, guild, current);
					if (error != null) {
						Msg.sendRaw(player, error);
						GuiUtil.errorSound(player);
					}
					reopenList(player, guild);
				},
				() -> reopenDetail(player, guild, questId));
	}

	private static void reopenList(ServerPlayer player, Guild guild) {
		if (GuildManager.of(player) == guild) {
			openList(player, guild);
		}
	}

	private static void reopenDetail(ServerPlayer player, Guild guild, String questId) {
		if (GuildManager.of(player) == guild) {
			openDetail(player, guild, questId);
		}
	}

	// ------------------------------------------------------------------ display helpers

	/** Target display name (translated client-side for ids, configured name for tags). */
	public static Component targetName(QuestInstance quest) {
		return switch (quest.type) {
			case SURVIVE -> Lang.get("quest.target.survive");
			case EXPLORE -> Lang.get("quest.target.explore", quest.targetX, quest.targetZ);
			default -> {
				QuestTarget target = QuestTarget.get(quest.type, quest.target);
				yield target != null ? target.name(quest.targetName)
						: Component.literal(quest.targetName.isBlank() ? quest.target : Lang.colorize(quest.targetName));
			}
		};
	}

	static Item icon(QuestInstance quest) {
		Item configured = quest.icon == null || quest.icon.isBlank() ? null : ItemUtil.item(quest.icon);
		if (configured != null) {
			return configured;
		}
		return switch (quest.type) {
			case SURVIVE -> Items.SHIELD;
			case EXPLORE -> Items.COMPASS;
			default -> {
				QuestTarget target = QuestTarget.get(quest.type, quest.target);
				yield target != null ? target.icon() : Items.PAPER;
			}
		};
	}

	static GuiElementBuilder questIcon(ServerPlayer player, QuestInstance quest, List<Component> extra) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("quest.gui.type." + quest.type.key()));
		if (!quest.description.isBlank()) {
			for (String line : quest.description.split("\n")) {
				lore.add(Component.literal(Lang.colorize("&7" + line)));
			}
		}
		lore.add(Component.empty());
		lore.add(TextUtil.lang("quest.gui.objective." + quest.type.key(), targetName(quest), quest.amount));
		lore.add(Lang.get("quest.gui.progress", QuestFormat.bar(quest.progress, quest.amount, 20), quest.progress, quest.amount));
		if (quest.type == QuestType.EXPLORE && quest.isActive() && player.level() == dev.guildy.core.GuildyServer.claimLevel()) {
			double dx = player.getX() - quest.targetX;
			double dz = player.getZ() - quest.targetZ;
			lore.add(Lang.get("quest.gui.explore.distance", (int) Math.sqrt(dx * dx + dz * dz)));
		}
		lore.add(Component.empty());
		lore.addAll(rewardLines(quest));
		lore.add(Component.empty());
		if (quest.isCompleted()) {
			lore.add(Lang.get("quest.gui.completed"));
		} else if (quest.expiresAt > 0) {
			lore.add(Lang.get("quest.gui.expires", QuestFormat.duration(quest.expiresAt - QuestManager.now())));
		}
		lore.addAll(extra);
		GuiElementBuilder builder = GuiUtil.item(icon(quest),
				TextUtil.lang(quest.isCompleted() ? "quest.gui.quest.done" : "quest.gui.quest.name", TextUtil.colored(quest.name)), lore);
		if (quest.isCompleted()) {
			builder.glow();
		}
		return builder;
	}

	static List<Component> rewardLines(QuestInstance quest) {
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("quest.gui.rewards"));
		if (quest.rewardGold > 0) {
			lines.add(Lang.get("quest.gui.reward.gold", quest.rewardGold));
		}
		if (quest.rewardXp > 0) {
			lines.add(Lang.get("quest.gui.reward.xp", quest.rewardXp));
		}
		for (RewardStack stack : quest.rewardItems) {
			Item item = ItemUtil.item(stack.item);
			if (item != null) {
				lines.add(TextUtil.lang("quest.gui.reward.item", stack.count, ItemUtil.name(item)));
			}
		}
		if (quest.rewardFlags > 0) {
			lines.add(Lang.get("quest.gui.reward.flag", quest.rewardFlags));
		}
		if (quest.rewardMap) {
			lines.add(Lang.get("quest.gui.reward.map"));
		}
		return lines;
	}

	private static GuiElementBuilder contributionsIcon(QuestInstance quest) {
		List<Component> lore = new ArrayList<>();
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(quest.contributions.entrySet());
		entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
		for (int i = 0; i < entries.size() && i < 8; i++) {
			lore.add(Lang.get("quest.gui.contribution", entries.get(i).getKey(), entries.get(i).getValue()));
		}
		if (lore.isEmpty()) {
			lore.add(Lang.get("quest.gui.contributions.none"));
		}
		return GuiUtil.item(Items.PLAYER_HEAD, Lang.get("quest.gui.contributions"), lore);
	}
}
