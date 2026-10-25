package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

/**
 * State machine of one task for one villager. {@link #tick} is called at most once per second, only while the
 * villager is loaded and not paused (night, trading...). A new instance is created when the task changes (or when
 * its zone / route assignment changes, see {@link TaskEngine#resetTask}).
 */
abstract class TaskHandler {
	abstract void tick(WorkContext ctx);

	/** Called when the task changes or the villager leaves (release targets if any). */
	void stop(WorkContext ctx) {
	}

	static TaskHandler create(Task task) {
		return switch (task) {
			case SORTER -> new SorterTask();
			case MINER -> new MinerTask();
			case LUMBERJACK -> new LumberjackTask();
			case FARMER -> new FarmerTask();
			case GUARD -> new GuardTask();
			case EXPLORER, NETHER -> new ExpeditionTask();
			case BLACKSMITH -> new BlacksmithTask();
			case ALCHEMIST -> new AlchemistTask();
			case CARTOGRAPHER -> new CartographerTask();
			case BUILDER -> new BuilderTask();
			case IDLE -> new TaskHandler() {
				@Override
				void tick(WorkContext ctx) {
					ctx.status("villager.status.idle");
				}
			};
		};
	}

	/** Identity of the work area (zone settings, or "auto"): a change restarts the search. */
	static String areaKey(@Nullable WorkZone zone) {
		return zone == null ? "auto" : zone.settingsKey();
	}
}
