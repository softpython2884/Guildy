package dev.guildy.villager;

/**
 * State machine of one task for one villager. {@link #tick} is called at most once per second, only while the
 * villager is loaded and not paused (night, trading...). A new instance is created when the task changes.
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
			case IDLE -> new TaskHandler() {
				@Override
				void tick(WorkContext ctx) {
					ctx.status("villager.status.idle");
				}
			};
		};
	}

	/**
	 * Common checks for tasks working around {@code record.workPos}.
	 *
	 * @return false (status set) when the villager cannot work
	 */
	static boolean checkWorkPos(WorkContext ctx) {
		if (ctx.record.workPos == null) {
			ctx.status("villager.status.no_workpos");
			return false;
		}
		if (!ctx.inTerritory(ctx.record.workPos)) {
			ctx.status("villager.status.workpos_outside");
			return false;
		}
		return true;
	}
}
