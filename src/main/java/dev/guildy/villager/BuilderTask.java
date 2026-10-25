package dev.guildy.villager;

/**
 * PLACEHOLDER of the "Bâtisseur" task on the supply branch only: it does nothing but the role "fetch the materials"
 * ({@link GatherWork}), whenever {@link GatherWork#blocked} allows it. The construction sites provide the real task
 * (projects, roles BUILD / GATHER and their distribution), which replaces this class and the enum entry.
 */
final class BuilderTask extends TaskHandler {
	private GatherWork work = new GatherWork();
	private boolean running;

	@Override
	void tick(WorkContext ctx) {
		if (!running) {
			String reason = GatherWork.blocked(ctx);
			if (reason != null) {
				ctx.status(reason);
				return;
			}
			running = true;
		}
		work.tick(ctx);
		if (work.finished()) {
			running = false;
			work = new GatherWork();
		}
	}

	@Override
	void stop(WorkContext ctx) {
		work.stop(ctx);
	}
}
