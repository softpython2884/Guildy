package dev.guildy.stats;

import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Fight statistics ("Bilan du combat"): live tallies of the tracked fights ({@link FightTracker}, fed by {@code stats.mixin}
 * and Fabric events), the summaries sent at their end ({@link FightReports}, {@link FightStatsMenu}) and
 * {@code /guild bilan [detail]}. The module that runs the fights (ruins) opens and closes the sessions and gives the settings
 * (section {@code stats} of ruins.json5). Runtime only: nothing is saved, a restart forgets everything.
 */
public final class FightModule {
	private static final FightStatsSettings DEFAULTS = new FightStatsSettings();
	private static Supplier<FightStatsSettings> settings = () -> DEFAULTS;
	private static boolean initialized;

	private FightModule() {
	}

	/** Once (later calls only replace the settings supplier). */
	public static void init(Supplier<FightStatsSettings> source) {
		settings = source;
		if (initialized) {
			return;
		}
		initialized = true;
		FightStatsLang.register();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source2) -> {
			if (FightTracker.tracking()) {
				Safe.run("fight.death", () -> FightTracker.death(entity, source2));
			}
		});
		Scheduler.every("fight.context", 1, 0, FightHealing::reset);
		Scheduler.every("fight.presence", 20, 5, FightTracker::tickSecond);
		Scheduler.every("fight.prune", 1200, 41, () -> {
			FightReports.prune();
			FightHealing.prune();
		});
		Lifecycle.STOPPING.add(server -> Safe.run("fight.stop", () -> {
			FightTracker.clear();
			FightReports.clear();
		}));
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("bilan")
				.executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					Safe.run("fight.command", () -> show(player, false));
					return 1;
				})
				.then(Commands.literal("detail").executes(ctx -> {
					ServerPlayer player = GuildCommand.player(ctx);
					Safe.run("fight.command", () -> show(player, true));
					return 1;
				}))));
	}

	public static FightStatsSettings settings() {
		FightStatsSettings cfg = settings.get();
		return cfg != null ? cfg : DEFAULTS;
	}

	/** {@code /guild bilan}: the last summary again in chat; {@code detail}: its menu. */
	static void show(ServerPlayer player, boolean menu) {
		FightStatsSettings cfg = settings();
		FightReport report = FightReports.last(player.getUUID());
		if (report == null) {
			Msg.send(player, cfg.enabled ? "fight.none" : "fight.disabled", cfg.keepMinutes);
			return;
		}
		if (menu) {
			FightStatsMenu.open(player, report);
		} else {
			FightReports.send(player, report, cfg);
		}
	}
}
