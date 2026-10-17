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
	/** Assigned work zone ({@link GuildVillagerData#zones} id), -1 = automatic. Ignored when incompatible with the task. */
	public int zoneId = -1;
	/** Assigned patrol route ({@link GuildVillagerData#routes} id) of a guard, -1 = none. */
	public int routeId = -1;
	/**
	 * Legacy work position (before work zones): read from old data, turned into a zone when the guild data loads
	 * ({@link GuildVillagerData#migrateLegacyWorkPositions}), then always null (no longer saved).
	 */
	public @Nullable BlockPos workPos;
	/** Items collected and not yet deposited (dropped if the villager dies). */
	public List<ItemStack> carried = new ArrayList<>();
	public long joinedAt;
	/** Last known block position (menu display when unloaded). */
	public @Nullable BlockPos lastPos;
	public Stats stats = new Stats();

	/** Mission goal (gathering tasks and expeditions). */
	public Goal goal = Goal.CONTINUOUS;
	/** Item counted by a QUOTA goal ("" = any item brought back). */
	public String goalItem = "";
	public int goalAmount = 64;
	/** Items counted toward the current mission. */
	public int goalProgress;
	/** "Objectifs prioritaires" of this villager (item ids, see {@link Wishlist}). */
	public List<String> wishlist = new ArrayList<>();
	/** Goal reached: everything is brought back, then the mission ends (task becomes IDLE). */
	public boolean goalReached;

	/** While away on an expedition: the saved villager entity (SNBT), restored on its return. Null at home. */
	public @Nullable String awayEntity;
	/** Expedition kind (EXPLORER / NETHER) while away. */
	public @Nullable Task awayTask;
	/**
	 * Expedition destination chosen in the menu (id, see {@link ExpeditionDestinations}); "" or an id unknown for the
	 * task's kind = the default destination of the kind. Kept when the task changes.
	 */
	public String expeditionDestination = "";
	/** Destination of the trip in progress (set at the departure, used by the return), null at home. */
	public @Nullable String awayDestination;
	/** Claim-level game time of the return. */
	public long returnAt;
	/** Where it left and comes back (portal, reception or heart). */
	public @Nullable BlockPos returnPos;

	/** Guard role setting (created the first time the villager becomes a guard, see {@link Guards}). */
	public @Nullable GuardData guard;
	/** Blacksmith / alchemist statistics and daily count (created when one of these tasks first works, see {@link CraftJobs}). */
	public @Nullable CraftData craft;

	public boolean isAway() {
		return awayEntity != null;
	}

	public static class Stats {
		/** Items moved by the sorter. */
		public long sorted;
		/** Items brought back by the miner. */
		public long mined;
		public long logs;
		public long crops;
		public long kills;
		/** Expeditions completed and items they brought back. */
		public long expeditions;
		public long found;
		/** Saplings planted by a lumberjack (replanting at a cut trunk not included) and crops sown by a farmer. */
		public long planted;
		public long sown;
		/** Field works of a farmer: blocks tilled (retilled ones included) and water holes dug or refilled. */
		public long tilled;
		public long waterHoles;
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
		if (goal == null) {
			goal = Goal.CONTINUOUS;
		}
		if (goalItem == null) {
			goalItem = "";
		}
		goalAmount = Math.max(1, Math.min(10_000, goalAmount));
		goalProgress = Math.max(0, goalProgress);
		wishlist = Wishlist.sanitize(wishlist);
		if (awayEntity != null && awayEntity.isBlank()) {
			awayEntity = null;
		}
		if (awayEntity != null && awayTask == null) {
			awayTask = Task.EXPLORER;
		}
		if (expeditionDestination == null) {
			expeditionDestination = "";
		}
		if (awayEntity == null) {
			awayDestination = null;
		}
		zoneId = Math.max(-1, zoneId);
		routeId = Math.max(-1, routeId);
		if (guard != null) {
			guard.sanitize();
		}
		if (craft != null) {
			craft.sanitize();
		}
	}
}
