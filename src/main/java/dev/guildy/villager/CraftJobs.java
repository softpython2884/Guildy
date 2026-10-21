package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Shared bits of the blacksmith and alchemist tasks: guild level requirement (menu lore, refusal, task step), hand
 * items, persisted data and the statistics lines of the villager menu.
 */
final class CraftJobs {
	private CraftJobs() {
	}

	static boolean isCraft(Task task) {
		return task == Task.BLACKSMITH || task == Task.ALCHEMIST;
	}

	/** Visual tool while working: a mace for the smith's hammer, a book for the alchemist. */
	static @Nullable Item handItem(Task task) {
		return switch (task) {
			case BLACKSMITH -> Items.MACE;
			case ALCHEMIST -> Items.BOOK;
			default -> null;
		};
	}

	/** Guild level needed for the task (0 = none). */
	static int minGuildLevel(Task task) {
		VillagerConfig config = VillagerModule.config();
		return switch (task) {
			case BLACKSMITH -> config.blacksmith.minGuildLevel;
			case ALCHEMIST -> config.alchemist.minGuildLevel;
			default -> 0;
		};
	}

	static boolean levelOk(Guild guild, Task task) {
		return guild.level >= minGuildLevel(task);
	}

	/** Requirement line of a task button in the villager menus (nothing for tasks without requirement). */
	static void addRequirementLore(List<Component> lore, Guild guild, Task task) {
		int min = minGuildLevel(task);
		if (min <= 0) {
			return;
		}
		lore.add(levelOk(guild, task) ? Lang.get("villager.craft.level.ok", min) : Lang.get("villager.craft.level.required", min, guild.level));
	}

	/** True (message + error sound) when the guild level does not allow the task yet. */
	static boolean refuse(ServerPlayer player, Guild guild, Task task) {
		if (levelOk(guild, task)) {
			return false;
		}
		Msg.send(player, "villager.craft.level.refused", Lang.raw(task.langKey()), minGuildLevel(task), guild.level);
		GuiUtil.errorSound(player);
		return true;
	}

	/** Task step: false (status shown, villager stopped) while the guild level is too low (requirement raised later). */
	static boolean checkLevel(WorkContext ctx) {
		Task task = ctx.record.task;
		if (levelOk(ctx.guild, task)) {
			return true;
		}
		if (ctx.runtime.moveTarget != null) {
			Movement.stop(ctx);
		}
		ctx.status("villager.status.craft.level", minGuildLevel(task));
		return false;
	}

	static CraftData data(VillagerRecord record) {
		if (record.craft == null) {
			record.craft = new CraftData();
			GuildManager.markDirty();
		}
		return record.craft;
	}

	/** Statistics lines of the villager menu (only what was done at least once). */
	static void statLore(List<Component> lore, VillagerRecord record, long today) {
		CraftData data = record.craft;
		if (data == null) {
			return;
		}
		if (data.repairSteps > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.repaired", data.repaired, data.durability));
		}
		if (data.anvilsWorn > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.anvils", data.anvilsWorn));
		}
		AlchemistConfig alchemist = VillagerModule.config().alchemist;
		int scrollsToday = data.scrollsToday(today);
		if (data.books > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.books", data.books, data.booksToday(today), alchemist.maxBooksPerDay));
			if (!data.bestEnchant.isEmpty()) {
				lore.add(Lang.get("villager.gui.lore.stat.best_enchant", CraftNames.enchantment(data.bestEnchant, data.bestLevel)));
			}
			if (!data.lastBook.isEmpty()) {
				lore.add(Lang.get("villager.gui.lore.stat.last_book", CraftNames.saved(data.lastBook)));
			}
		}
		if (data.scrolls > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.scrolls", data.scrolls, scrollsToday, alchemist.maxScrollsPerDay));
			if (!data.lastScroll.isEmpty()) {
				lore.add(Lang.get("villager.gui.lore.stat.last_scroll", AlchemistScrolls.name(data.lastScroll)));
			}
		}
		AlchemistGui.statLore(lore, data, today);
	}
}
