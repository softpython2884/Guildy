package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Mission goals of gathering tasks and expeditions: progress counting, "bring everything back" trigger and mission end.
 * Pure counting rules are in {@link #count}.
 */
final class Missions {
	private Missions() {
	}

	/** Pure rule: items of {@code itemId} added to the bag count toward the goal? */
	static boolean counts(String goalItem, String itemId) {
		return goalItem == null || goalItem.isEmpty() || goalItem.equals(itemId);
	}

	/**
	 * Pure rule: new progress after collecting {@code taken} items.
	 *
	 * @return the updated progress (unchanged when the items don't count)
	 */
	static int count(Goal goal, String goalItem, int progress, String itemId, int taken) {
		if (goal != Goal.QUOTA || taken <= 0 || !counts(goalItem, itemId)) {
			return progress;
		}
		return progress + taken;
	}

	/** Called whenever items enter the villager's bag. */
	static void onCollected(VillagerRecord record, ItemStack stack, int taken, boolean bagFull) {
		if (record.goal == Goal.QUOTA) {
			record.goalProgress = count(record.goal, record.goalItem, record.goalProgress, ItemUtil.id(stack.getItem()), taken);
			if (record.goalProgress >= record.goalAmount) {
				record.goalReached = true;
			}
		} else if (record.goal == Goal.FILL && bagFull) {
			record.goalReached = true;
		}
	}

	/** New task or new goal settings: the mission starts over. */
	static void reset(VillagerRecord record) {
		record.goalProgress = 0;
		record.goalReached = false;
	}

	/**
	 * Called when the bag is empty again (everything deposited): ends the mission if its goal is reached.
	 *
	 * @return true when the mission ended (the task is now IDLE)
	 */
	static boolean complete(WorkContext ctx) {
		VillagerRecord record = ctx.record;
		if (!record.goalReached || !record.task.hasGoal()) {
			return false;
		}
		Guild guild = ctx.guild;
		Task done = record.task;
		if (record.goal == Goal.QUOTA) {
			Msg.guild(guild, "villager.mission.done.quota", record.name, Lang.raw(done.langKey()), record.goalProgress, goalItemName(record));
		} else {
			Msg.guild(guild, "villager.mission.done.fill", record.name, Lang.raw(done.langKey()));
		}
		record.task = Task.IDLE;
		reset(record);
		GuildManager.markDirty();
		WorldUtil.happy(ctx.level, ctx.villager);
		WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.VILLAGER_CELEBRATE, 1.0f, 1.0f);
		return true;
	}

	/** Display name of the goal item ("objets" when any item counts). */
	static String goalItemName(VillagerRecord record) {
		Item item = record.goalItem.isEmpty() ? null : ItemUtil.item(record.goalItem);
		return item == null ? Lang.raw("villager.goal.any_item") : Component.empty().append(ItemUtil.name(item)).getString();
	}
}
