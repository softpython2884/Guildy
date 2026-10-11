package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A guild villager (persisted). Runtime state (task phase, status...) lives in {@link VillagerRuntime}.
 */
public class VillagerRecord {
	/** Entity UUID. */
	public UUID uuid;
	public String name = "";
	public Task task = Task.IDLE;
	/** Work position set from the menu (miner depth, lumberjack / farmer area centre, guard post). */
	public @Nullable BlockPos workPos;
	/** Items collected and not yet deposited (dropped if the villager dies). */
	public List<ItemStack> carried = new ArrayList<>();
	public long joinedAt;
	/** Last known block position (menu display when unloaded). */
	public @Nullable BlockPos lastPos;
	public Stats stats = new Stats();

	public static class Stats {
		/** Items moved by the sorter. */
		public long sorted;
		/** Items brought back by the miner. */
		public long mined;
		public long logs;
		public long crops;
		public long kills;
	}

	public VillagerRecord() {
	}

	public VillagerRecord(UUID uuid, String name) {
		this.uuid = uuid;
		this.name = name;
		this.joinedAt = System.currentTimeMillis();
	}

	public int carriedCount() {
		int total = 0;
		for (ItemStack stack : carried) {
			total += stack.getCount();
		}
		return total;
	}

	/** Fixes nulls after deserialization. */
	void sanitize(UUID key) {
		uuid = key;
		if (name == null || name.isBlank()) {
			name = "Villageois";
		}
		if (task == null) {
			task = Task.IDLE;
		}
		if (carried == null) {
			carried = new ArrayList<>();
		}
		carried.removeIf(stack -> stack == null || stack.isEmpty());
		if (stats == null) {
			stats = new Stats();
		}
	}
}
