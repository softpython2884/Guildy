package dev.guildy.quest;

import dev.guildy.core.GuildyServer;
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
 * Quest menus: the 6 slots of the board, and a detail page per quest (deliver, claim, abandon; one button per item of an
 * assortment). Legendary contracts get a golden frame.
 */
public final class QuestMenus {
	/** Menu slots mirroring the 3 x 2 quest board. */
	private static final int[] SLOT_POSITIONS = {11, 13, 15, 29, 31, 33};
	private static final Item LEGENDARY_PANE = Items.STAINED_GLASS_PANE.pick(DyeColor.YELLOW);

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
				gui.setSlot(SLOT_POSITIONS[slot], questIcon(player, guild, quest, extra).setCallback((index, type, action, g) -> Safe.run("quest.gui", () -> {
					GuiUtil.clickSound(player);
					openDetail(player, guild, id);
				})));
			} else {
				gui.setSlot(SLOT_POSITIONS[slot], emptyIcon(guild, slot, board));
			}
		}
		gui.setSlot(4, GuiUtil.item(Items.BOOK, Lang.get("quest.gui.info.name"),
				GuiUtil.lines("quest.gui.info.lore", guild.quests.completedTotal, guild.quests.active.size(), guild.quests.legendaryCompleted)));
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
		// Golden frame around legendary contracts (only on free cells).
		for (int slot = 0; slot < GuildQuestData.SLOTS; slot++) {
			QuestInstance quest = guild.quests.bySlot(slot);
			if (quest != null && quest.legendary) {
				frame(gui, SLOT_POSITIONS[slot], 5);
			}
		}
		GuiUtil.fill(gui);
		gui.open();
	}

	/** Golden panes on the free cells around a menu slot. */
	private static void frame(SimpleGui gui, int center, int rows) {
		int row = center / 9;
		int column = center % 9;
		for (int dr = -1; dr <= 1; dr++) {
			for (int dc = -1; dc <= 1; dc++) {
				int r = row + dr;
				int c = column + dc;
				if ((dr == 0 && dc == 0) || r < 0 || r >= rows || c < 0 || c > 8) {
					continue;
				}
				int index = r * 9 + c;
				if (gui.getGuiElement(index) == null) {
					gui.setSlot(index, GuiUtil.item(LEGENDARY_PANE, Lang.get("quest.gui.legendary.border"), List.of()));
				}
			}
		}
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
		boolean assortment = quest.hasParts();
		int rows = assortment ? 5 : 4;
		SimpleGui gui = GuiUtil.chest(player, rows, Lang.get("quest.gui.detail.title", Lang.strip(Lang.colorize(quest.name))));
		gui.setSlot(4, questIcon(player, guild, quest, List.of()));
		if (assortment) {
			int count = quest.parts.size();
			for (int i = 0; i < count; i++) {
				gui.setSlot(13 - (count - 1) + 2 * i, partIcon(player, guild, quest, i));
			}
			gui.setSlot(31, contributionsIcon(quest));
		} else {
			gui.setSlot(13, contributionsIcon(quest));
		}

		if (quest.isActive() && quest.type == QuestType.DELIVER) {
			int have = QuestManager.deliverable(player, quest);
			List<Component> lore = new ArrayList<>();
			lore.add(TextUtil.lang("quest.gui.deliver.have", have, targetName(quest)));
			lore.add(Lang.get("quest.gui.deliver.missing", quest.remaining()));
			lore.addAll(GuiUtil.lines("quest.gui.deliver.lore"));
			gui.setSlot(20, GuiUtil.item(Items.CHEST, Lang.get("quest.gui.deliver.name"), lore).setCallback((i, t, a, g) ->
					deliverClick(player, guild, quest, -1)));
		} else if (quest.isActive() && assortment) {
			List<Component> lore = new ArrayList<>();
			for (int i = 0; i < quest.parts.size(); i++) {
				lore.add(partLine(quest.parts.get(i)));
			}
			lore.add(Component.empty());
			lore.addAll(GuiUtil.lines("quest.gui.deliver.all.lore"));
			gui.setSlot(20, GuiUtil.item(Items.BUNDLE, Lang.get("quest.gui.deliver.all.name"), lore).setCallback((i, t, a, g) ->
					deliverClick(player, guild, quest, -1)));
		}
		if (quest.isCompleted()) {
			gui.setSlot(22, GuiUtil.item(Items.EMERALD, Lang.get("quest.gui.claim.name"), rewardLines(guild, quest)).glow().setCallback((i, t, a, g) ->
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
		gui.setSlot(rows * 9 - 9, GuiUtil.back(() -> openList(player, guild)));
		gui.setSlot(rows * 9 - 1, GuiUtil.close());
		if (quest.legendary) {
			// Golden background for legendary contracts.
			for (int i = 0; i < gui.getSize(); i++) {
				if (gui.getGuiElement(i) == null) {
					gui.setSlot(i, new GuiElementBuilder(LEGENDARY_PANE).setName(Component.literal(" ")).hideTooltip());
				}
			}
		}
		GuiUtil.fill(gui);
		gui.open();
	}

	/** Deliver button (part = -1: whole DELIVER quest or every missing item of an assortment). */
	private static void deliverClick(ServerPlayer player, Guild guild, QuestInstance quest, int part) {
		Safe.run("quest.gui.deliver", () -> {
			Component error = QuestManager.deliver(player, guild, quest, part);
			if (error != null) {
				Msg.sendRaw(player, error);
				GuiUtil.errorSound(player);
			} else {
				GuiUtil.successSound(player);
			}
			reopenDetail(player, guild, quest.id);
		});
	}

	/** One item of an assortment: progress, what the player has, click = deliver only this item. */
	private static GuiElementBuilder partIcon(ServerPlayer player, Guild guild, QuestInstance quest, int index) {
		QuestPart part = quest.parts.get(index);
		QuestTarget target = QuestTarget.get(QuestType.TargetKind.ITEM, part.target);
		Item item = target == null ? null : target.icon();
		Component name = partName(part);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("quest.gui.part.progress", QuestFormat.bar(part.progress, part.amount, 20), part.progress, part.amount));
		if (part.isDone()) {
			lore.add(Lang.get("quest.gui.part.complete"));
		} else if (quest.isActive()) {
			lore.add(Lang.get("quest.gui.part.have", QuestManager.deliverable(player, quest, index)));
			lore.add(Component.empty());
			lore.add(Lang.get("quest.gui.part.click"));
		}
		Item shown = item == null ? Items.PAPER : item;
		GuiElementBuilder builder = GuiUtil.item(shown, TextUtil.lang(part.isDone() ? "quest.gui.part.name.done" : "quest.gui.part.name", name), lore);
		// Stack size = what is still missing (within the item's stack size).
		builder.setCount(part.isDone() ? 1 : Math.max(1, Math.min(shown.getDefaultMaxStackSize(), part.remaining())));
		if (part.isDone()) {
			builder.glow();
		} else if (quest.isActive()) {
			builder.setCallback((i, t, a, g) -> deliverClick(player, guild, quest, index));
		}
		return builder;
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

	/** Target display name (translated client-side for ids, configured name for tags, a label for targetless quests). */
	public static Component targetName(QuestInstance quest) {
		return switch (quest.type) {
			case SURVIVE -> Lang.get("quest.target.survive");
			case EXPLORE -> Lang.get("quest.target.explore", quest.targetX, quest.targetZ);
			case ELITE -> Lang.get("quest.target.elite");
			case BOSS -> Lang.get("quest.target.boss");
			case COLLECTION -> Lang.get("quest.target.collection", quest.parts.size());
			default -> {
				if (quest.target.isBlank()) {
					String key = "quest.target." + quest.type.key();
					yield Lang.has(key) ? Lang.get(key) : Lang.get("quest.target.unknown");
				}
				QuestTarget target = QuestTarget.get(quest.type, quest.target);
				yield target != null ? target.name(quest.targetName)
						: Component.literal(quest.targetName.isBlank() ? quest.target : Lang.colorize(quest.targetName));
			}
		};
	}

	/** Name of one item of an assortment. */
	static Component partName(QuestPart part) {
		QuestTarget target = QuestTarget.get(QuestType.TargetKind.ITEM, part.target);
		return target != null ? target.name(part.targetName)
				: Component.literal(part.targetName.isBlank() ? part.target : Lang.colorize(part.targetName));
	}

	/** "- 12/16 Bread" or "✔ 16/16 Bread". */
	static Component partLine(QuestPart part) {
		return TextUtil.lang(part.isDone() ? "quest.gui.part.done" : "quest.gui.part.line", partName(part), part.progress, part.amount);
	}

	/** Default icon of each quest type (targetless quests, unresolved targets). */
	static Item typeIcon(QuestType type) {
		return switch (type) {
			case GATHER -> Items.IRON_PICKAXE;
			case KILL -> Items.IRON_SWORD;
			case DELIVER -> Items.CHEST;
			case SURVIVE -> Items.SHIELD;
			case EXPLORE -> Items.COMPASS;
			case CRAFT -> Items.CRAFTING_TABLE;
			case SMELT -> Items.FURNACE;
			case FISH -> Items.FISHING_ROD;
			case BREED -> Items.WHEAT;
			case TAME -> Items.LEAD;
			case TRADE -> Items.EMERALD;
			case ENCHANT -> Items.ENCHANTING_TABLE;
			case ELITE -> Items.GOLDEN_SWORD;
			case BOSS -> Items.WITHER_SKELETON_SKULL;
			case NIGHT_HUNT -> Items.CLOCK;
			case BIOME -> Items.MAP;
			case COLLECTION -> Items.BUNDLE;
		};
	}

	/** Configured icon, else the target's icon, else the type's icon. */
	static Item icon(QuestInstance quest) {
		Item configured = quest.icon == null || quest.icon.isBlank() ? null : ItemUtil.item(quest.icon);
		if (configured != null) {
			return configured;
		}
		if (quest.type.hasTarget() && !quest.target.isBlank()) {
			QuestTarget target = QuestTarget.get(quest.type, quest.target);
			Item item = target == null ? null : target.icon();
			if (item != null) {
				return item;
			}
		}
		return typeIcon(quest.type);
	}

	static GuiElementBuilder questIcon(ServerPlayer player, Guild guild, QuestInstance quest, List<Component> extra) {
		List<Component> lore = new ArrayList<>();
		if (quest.legendary) {
			lore.add(Lang.get("quest.gui.legendary"));
		}
		lore.add(Lang.get("quest.gui.type." + quest.type.key()));
		if (!quest.description.isBlank()) {
			for (String line : quest.description.split("\n")) {
				lore.add(Component.literal(Lang.colorize("&7" + line)));
			}
		}
		if (quest.legendary) {
			lore.addAll(GuiUtil.lines("quest.gui.legendary.hint"));
		}
		lore.add(Component.empty());
		lore.add(TextUtil.lang(objectiveKey(quest), targetName(quest), quest.amount));
		for (QuestPart part : quest.parts) {
			lore.add(partLine(part));
		}
		lore.add(Lang.get("quest.gui.progress", QuestFormat.bar(quest.progress, quest.amount, 20), quest.progress, quest.amount));
		if (quest.type == QuestType.EXPLORE && quest.isActive() && player.level() == GuildyServer.claimLevel()) {
			double dx = player.getX() - quest.targetX;
			double dz = player.getZ() - quest.targetZ;
			lore.add(Lang.get("quest.gui.explore.distance", (int) Math.sqrt(dx * dx + dz * dz)));
		}
		String hint = "quest.gui.hint." + quest.type.key();
		if (quest.isActive() && Lang.has(hint)) {
			lore.addAll(GuiUtil.lines(hint));
		}
		lore.add(Component.empty());
		lore.addAll(rewardLines(guild, quest));
		lore.add(Component.empty());
		if (quest.isCompleted()) {
			lore.add(Lang.get("quest.gui.completed"));
		} else if (quest.expiresAt > 0) {
			lore.add(Lang.get("quest.gui.expires", QuestFormat.duration(quest.expiresAt - QuestManager.now())));
		}
		lore.addAll(extra);
		String nameKey = quest.legendary
				? (quest.isCompleted() ? "quest.gui.quest.legendary_done" : "quest.gui.quest.legendary")
				: (quest.isCompleted() ? "quest.gui.quest.done" : "quest.gui.quest.name");
		GuiElementBuilder builder = GuiUtil.item(icon(quest), TextUtil.lang(nameKey, TextUtil.colored(quest.name)), lore);
		if (quest.isCompleted() || quest.legendary) {
			builder.glow();
		}
		return builder;
	}

	/** Objective line: "quest.gui.objective.<type>", or its ".any" variant for an optional target left blank. */
	static String objectiveKey(QuestInstance quest) {
		String key = "quest.gui.objective." + quest.type.key();
		if (quest.target.isBlank() && Lang.has(key + ".any")) {
			return key + ".any";
		}
		return key;
	}

	static List<Component> rewardLines(Guild guild, QuestInstance quest) {
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("quest.gui.rewards"));
		if (quest.rewardGold > 0) {
			lines.add(Lang.get("quest.gui.reward.gold", QuestManager.rewardGold(guild, quest)));
		}
		if (quest.rewardXp > 0) {
			lines.add(Lang.get("quest.gui.reward.xp", QuestManager.rewardXp(guild, quest)));
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
		if (quest.legendary) {
			lines.add(Lang.get("quest.gui.reward.unique.legendary"));
		} else if (QuestManager.config().uniqueChance > 0) {
			lines.add(Lang.get("quest.gui.reward.unique.chance", QuestFormat.percent(QuestManager.config().uniqueChance)));
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
