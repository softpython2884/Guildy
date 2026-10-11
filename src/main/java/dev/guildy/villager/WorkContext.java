package dev.guildy.villager;

import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Everything a task handler needs for one processing step of one villager.
 */
final class WorkContext {
	final ServerLevel level;
	final Guild guild;
	final VillagerRecord record;
	final Villager villager;
	final VillagerRuntime runtime;
	final VillagerConfig config;
	/** Scheduler tick of this step. */
	final long now;

	WorkContext(ServerLevel level, Guild guild, VillagerRecord record, Villager villager, VillagerRuntime runtime, VillagerConfig config) {
		this.level = level;
		this.guild = guild;
		this.record = record;
		this.villager = villager;
		this.runtime = runtime;
		this.config = config;
		this.now = Scheduler.ticks();
	}

	void status(String key, Object... args) {
		runtime.status(key, args);
	}

	boolean inTerritory(BlockPos pos) {
		return GuildManager.at(level, pos) == guild;
	}

	boolean loaded(BlockPos pos) {
		return level.isLoaded(pos);
	}

	double distSq(BlockPos pos) {
		return villager.position().distanceToSqr(Vec3.atBottomCenterOf(pos));
	}

	double horizontalDistSq(BlockPos pos) {
		double dx = villager.getX() - (pos.getX() + 0.5);
		double dz = villager.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	boolean carriedFull() {
		return record.carried.size() >= config.work.carryStacks;
	}

	/** Bag full or mission goal reached: time to bring everything back to a deposit chest. */
	boolean mustReturn() {
		return carriedFull() || record.goalReached;
	}

	/**
	 * Adds items to the carried list (merging into existing stacks, max {@code work.carryStacks} stacks).
	 *
	 * @return what did not fit (empty when everything was taken)
	 */
	ItemStack addCarried(ItemStack input) {
		ItemStack stack = input.copy();
		for (ItemStack carried : record.carried) {
			if (stack.isEmpty()) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(carried, stack) && carried.getCount() < carried.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), carried.getMaxStackSize() - carried.getCount());
				carried.grow(move);
				stack.shrink(move);
			}
		}
		while (!stack.isEmpty() && record.carried.size() < config.work.carryStacks) {
			record.carried.add(stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
		}
		Missions.onCollected(record, input, input.getCount() - stack.getCount(), carriedFull());
		GuildManager.markDirty();
		return stack;
	}

	/** Seconds → ticks. */
	static long ticks(int seconds) {
		return seconds * 20L;
	}

	/** Work duration (seconds → ticks) shortened by the guild's work speed upgrade. */
	long workTicks(int seconds) {
		return ticks(Upgrades.workSeconds(guild, seconds));
	}
}
