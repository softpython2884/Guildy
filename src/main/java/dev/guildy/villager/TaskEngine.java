package dev.guildy.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.guildy.core.Hooks;
import dev.guildy.data.Guild;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-villager processing (once per second, staggered by the manager): pause conditions, hand item, brain locks and
 * the task state machine.
 * <p>
 * Brain locks: a task villager gets impossible requirements on some vanilla activities (a memory type no villager brain
 * registers), so the vanilla brain never switches to them: workers skip WORK/MEET (no competing walk targets during
 * the day; REST and PANIC stay vanilla so they sleep and flee), guards also skip REST/PANIC/HIDE/RAID (they never sleep
 * or flee). Going back to IDLE rebuilds a vanilla brain ({@link Villager#refreshBrain}).
 */
final class TaskEngine {
	private static final Map<UUID, VillagerRuntime> RUNTIME = new HashMap<>();
	/** DUMMY is registered by no brain: an activity requiring it can never start. */
	private static final Set<Pair<MemoryModuleType<?>, MemoryStatus>> NEVER =
			ImmutableSet.of(Pair.<MemoryModuleType<?>, MemoryStatus>of(MemoryModuleType.DUMMY, MemoryStatus.VALUE_PRESENT));
	private static final List<Activity> WORKER_LOCKS = List.of(Activity.WORK, Activity.MEET);
	private static final List<Activity> GUARD_LOCKS = List.of(Activity.WORK, Activity.MEET, Activity.REST, Activity.PANIC,
			Activity.HIDE, Activity.PRE_RAID, Activity.RAID);

	private TaskEngine() {
	}

	static VillagerRuntime runtime(UUID uuid) {
		return RUNTIME.computeIfAbsent(uuid, VillagerRuntime::new);
	}

	static @Nullable VillagerRuntime peek(UUID uuid) {
		return RUNTIME.get(uuid);
	}

	static void forget(UUID uuid) {
		RUNTIME.remove(uuid);
	}

	/** Restarts the task state machine from scratch at the next step (work zone changed...). */
	static void resetTask(UUID uuid) {
		VillagerRuntime rt = RUNTIME.get(uuid);
		if (rt != null) {
			rt.handler = null;
			rt.handlerTask = null;
		}
	}

	static void clear() {
		RUNTIME.clear();
	}

	static void process(ServerLevel level, Guild guild, VillagerRecord record) {
		VillagerRuntime rt = runtime(record.uuid);
		Entity entity = level.getEntity(record.uuid);
		if (!(entity instanceof Villager villager) || !villager.isAlive() || !level.isPositionEntityTicking(villager.blockPosition())) {
			rt.status("villager.status.unloaded");
			return;
		}
		record.lastPos = villager.blockPosition();
		VillagerManager.ensureMemberSetup(villager, record);
		WorkContext ctx = new WorkContext(level, guild, record, villager, rt, VillagerModule.config());
		Task task = record.task;
		if (rt.handler == null || rt.handlerTask != task) {
			if (rt.handler != null) {
				rt.handler.stop(ctx);
				Movement.stop(ctx);
			}
			rt.handler = TaskHandler.create(task);
			rt.handlerTask = task;
			rt.paused = false;
			rt.resetAllMovement();
			if (task == Task.IDLE && isTaskTool(villager.getMainHandItem())) {
				// Only when becoming idle: vanilla trade displays may legitimately show the same items later.
				villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}
		if (task != Task.IDLE) {
			applyHandItem(villager, task);
		}
		applyBrainLock(level, villager, rt, task);
		if (task == Task.IDLE) {
			rt.status("villager.status.idle");
			return;
		}
		String pause = pauseReason(level, guild, villager, task);
		if (pause != null) {
			if (!rt.paused) {
				rt.paused = true;
				Movement.stop(ctx);
			}
			rt.status(pause);
			return;
		}
		if (rt.paused) {
			rt.paused = false;
			rt.resetMovement();
		}
		rt.handler.tick(ctx);
	}

	/** Why the task is paused right now (lang key), or null to work. Guards only pause while trading / riding. */
	static @Nullable String pauseReason(ServerLevel level, Guild guild, Villager villager, Task task) {
		if (villager.isTrading()) {
			return "villager.status.trading";
		}
		if (villager.isPassenger()) {
			return "villager.status.riding";
		}
		if (task == Task.GUARD) {
			return null;
		}
		if (Hooks.isWaveActive.test(guild)) {
			return "villager.status.wave";
		}
		if (villager.isSleeping()) {
			return "villager.status.sleeping";
		}
		Brain<Villager> brain = villager.getBrain();
		if (brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE) || brain.isActive(Activity.RAID) || brain.isActive(Activity.PRE_RAID)) {
			return "villager.status.scared";
		}
		if (brain.isActive(Activity.REST) || level.isDarkOutside()) {
			return "villager.status.resting";
		}
		return null;
	}

	/** Visual tool of a working task (never dropped on death). Re-applied every step (trading displays may replace it). */
	static void applyHandItem(Villager villager, Task task) {
		Item wanted = task.handItem();
		if (wanted == null) {
			return;
		}
		if (!villager.getMainHandItem().is(wanted)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wanted));
			villager.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
	}

	static boolean isTaskTool(ItemStack stack) {
		for (Task task : Task.values()) {
			Item item = task.handItem();
			if (item != null && stack.is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Applies (idempotent) the activity locks of the task group; rebuilds a vanilla brain when the group changes. */
	static void applyBrainLock(ServerLevel level, Villager villager, VillagerRuntime rt, Task task) {
		Task group = task == Task.IDLE ? null : task == Task.GUARD ? Task.GUARD : Task.SORTER;
		if (!Objects.equals(rt.brainLock, group)) {
			if (rt.brainLock != null) {
				villager.refreshBrain(level);
			}
			rt.brainLock = group;
		}
		if (group == null) {
			return;
		}
		List<Activity> locks = group == Task.GUARD ? GUARD_LOCKS : WORKER_LOCKS;
		Brain<Villager> brain = villager.getBrain();
		for (Activity activity : locks) {
			brain.addActivity(activity, ImmutableList.of(), NEVER, ImmutableSet.of());
		}
		Optional<Activity> active = brain.getActiveNonCoreActivity();
		if (active.isPresent() && locks.contains(active.get())) {
			brain.useDefaultActivity();
		}
	}

	/** Restores a vanilla brain (task removed, villager released). */
	static void unlockBrain(ServerLevel level, Villager villager) {
		VillagerRuntime rt = RUNTIME.get(villager.getUUID());
		if (rt != null && rt.brainLock != null) {
			rt.brainLock = null;
		}
		villager.refreshBrain(level);
	}
}
