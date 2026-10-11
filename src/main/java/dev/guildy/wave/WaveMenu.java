package dev.guildy.wave;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Waves info menu (/guild waves, or the main menu button): live progress, next-night estimate, stats, last result,
 * themes and rules. Refreshed every second while open.
 */
final class WaveMenu {
	private static final int SLOT_CURRENT = 10;
	private static final int SLOT_NEXT = 12;
	private static final int SLOT_STATS = 14;
	private static final int SLOT_LAST = 16;
	private static final int SLOT_THEMES = 30;
	private static final int SLOT_RULES = 32;
	private static final int SLOT_BACK = 36;
	private static final int SLOT_CLOSE = 44;

	private WaveMenu() {
	}

	static void open(ServerPlayer player, Guild guild) {
		Menu menu = new Menu(player, guild);
		menu.build();
		GuiUtil.fill(menu);
		menu.open();
	}

	private static final class Menu extends SimpleGui {
		private final Guild guild;
		private int ticks;

		Menu(ServerPlayer player, Guild guild) {
			super(GuiUtil.rows(5), player, false);
			this.guild = guild;
			setTitle(Lang.get("wave.gui.title"));
		}

		@Override
		public void onTick() {
			if (++ticks % 20 == 0) {
				Safe.run("wave.gui", this::build);
			}
		}

		void build() {
			setSlot(SLOT_CURRENT, current());
			setSlot(SLOT_NEXT, next());
			setSlot(SLOT_STATS, stats());
			setSlot(SLOT_LAST, last());
			setSlot(SLOT_THEMES, themes());
			setSlot(SLOT_RULES, rules());
			setSlot(SLOT_BACK, GuiUtil.back(() -> Hooks.openMainMenu.accept(getPlayer())));
			setSlot(SLOT_CLOSE, GuiUtil.close());
		}

		private GuiElementBuilder current() {
			WaveConfig cfg = WaveModule.config();
			ServerLevel level = GuildyServer.claimLevel();
			long clock = WaveManager.clock();
			WaveSession session = WaveManager.session(guild);
			if (session != null) {
				List<Component> lore = GuiUtil.lines("wave.gui.current.lore", Lang.colorize(session.theme().name),
						WaveSession.formatDifficulty(session.difficulty()), session.kills(), session.total(),
						Math.max(0, session.total() - session.kills()), session.eliteKills(), session.integrity(), session.maxIntegrity(),
						duration(session.remainingSeconds(clock)));
				if (session.hasBoss()) {
					float[] health = level == null ? null : session.bossHealth(level);
					if (session.bossKilled()) {
						lore.add(Lang.get("wave.gui.current.boss.killed"));
					} else if (health != null) {
						lore.add(Lang.get("wave.gui.current.boss.alive", Lang.colorize(session.bossName()),
								(int) Math.ceil(health[0]), (int) Math.ceil(health[1])));
					} else {
						lore.add(Lang.get("wave.gui.current.boss.pending"));
					}
				}
				if (session.isTest()) {
					lore.add(Lang.get("wave.gui.current.test"));
				}
				return GuiUtil.item(Items.IRON_SWORD, Lang.get("wave.gui.current.name"), lore).glow();
			}

			List<Component> lore;
			if (!cfg.enabled) {
				lore = GuiUtil.lines("wave.gui.idle.disabled");
			} else if (!guild.isFounded()) {
				lore = GuiUtil.lines("wave.gui.idle.notfounded");
			} else if (!WaveManager.advanceTime()) {
				lore = GuiUtil.lines("wave.gui.idle.frozen");
			} else {
				long timeOfDay = WaveFormula.timeOfDay(clock);
				boolean doneTonight = guild.waves.lastWaveDay == WaveFormula.dayIndex(clock);
				if (WaveFormula.inStartWindow(cfg.timing, timeOfDay)) {
					lore = GuiUtil.lines(doneTonight ? "wave.gui.idle.done" : "wave.gui.idle.night");
				} else if (WaveFormula.inNight(cfg.timing, timeOfDay)) {
					lore = GuiUtil.lines(doneTonight ? "wave.gui.idle.done" : "wave.gui.idle.late");
				} else {
					lore = GuiUtil.lines("wave.gui.idle.day", duration(WaveFormula.ticksUntilNight(cfg.timing, timeOfDay) / 20));
				}
			}
			return GuiUtil.item(Items.SHIELD, Lang.get("wave.gui.idle.name"), lore);
		}

		private GuiElementBuilder next() {
			WaveConfig cfg = WaveModule.config();
			int night = guild.waves.night;
			int players = Math.max(1, WaveManager.nearbyMembers(GuildyServer.claimLevel(), guild));
			double difficulty = WaveFormula.difficulty(cfg.difficulty, night, guild.level, players);
			int expected = WaveFormula.mobCount(cfg.difficulty, difficulty, 1.0);
			long elite = Math.round(WaveFormula.eliteChance(cfg.difficulty, difficulty, 0) * 100);
			List<Component> lore = GuiUtil.lines("wave.gui.next.lore", night, WaveSession.formatDifficulty(difficulty), expected, elite,
					WaveFormula.heartIntegrity(cfg.heart, guild.level));
			lore.addAll(GuiUtil.lines("wave.gui.next.players", players, WaveSession.formatDifficulty(cfg.difficulty.perExtraPlayer)));
			int untilBoss = WaveFormula.nightsUntilBoss(cfg.difficulty, night);
			if (untilBoss == 0) {
				lore.add(Lang.get("wave.gui.next.boss"));
			} else if (untilBoss > 0) {
				lore.add(Lang.get("wave.gui.next.bossin", untilBoss));
			}
			lore.add(Lang.get("wave.gui.next.themes"));
			for (WaveConfig.Theme theme : WavePlanner.availableThemes(cfg.themes, night)) {
				lore.add(Lang.get("wave.gui.next.theme", Lang.colorize(theme.name)));
			}
			return GuiUtil.item(Items.CLOCK, Lang.get("wave.gui.next.name"), lore);
		}

		private GuiElementBuilder stats() {
			GuildWaveData data = guild.waves;
			List<Component> lore = GuiUtil.lines("wave.gui.stats.lore", data.night, data.victories, data.defeats, data.streak, data.bestStreak,
					data.totalKills, data.bossesKilled);
			return GuiUtil.item(Items.BOOK, Lang.get("wave.gui.stats.name"), lore);
		}

		private GuiElementBuilder last() {
			GuildWaveData data = guild.waves;
			if (data.lastResult == null || data.lastResult.isEmpty()) {
				return GuiUtil.item(Items.PAPER, Lang.get("wave.gui.last.name"), List.of(Lang.get("wave.gui.last.none")));
			}
			long ago = data.lastAt <= 0 ? 0 : Math.max(0, (System.currentTimeMillis() - data.lastAt) / 1000);
			List<Component> lore = GuiUtil.lines("wave.gui.last.lore", Lang.raw(data.lastResult), Lang.colorize(data.lastTheme),
					data.lastKills, data.lastTotal, duration(ago));
			if ("wave.result.victory".equals(data.lastResult)) {
				lore.add(Lang.get("wave.gui.last.gain", data.lastGold, data.lastXp));
			} else if ("wave.result.defeat".equals(data.lastResult)) {
				lore.add(Lang.get("wave.gui.last.loss", data.lastGold));
			}
			return GuiUtil.item(Items.PAPER, Lang.get("wave.gui.last.name"), lore);
		}

		private GuiElementBuilder themes() {
			WaveConfig cfg = WaveModule.config();
			List<Component> lore = new ArrayList<>();
			for (WaveConfig.Theme theme : cfg.themes) {
				boolean unlocked = guild.waves.night >= theme.minNight && (theme.maxNight < 0 || guild.waves.night <= theme.maxNight);
				lore.add(Lang.get(unlocked ? "wave.gui.themes.unlocked" : "wave.gui.themes.locked", Lang.colorize(theme.name), theme.minNight));
			}
			return GuiUtil.item(Items.SPAWNER, Lang.get("wave.gui.themes.name"), lore);
		}

		private GuiElementBuilder rules() {
			WaveConfig cfg = WaveModule.config();
			List<Component> lore = GuiUtil.lines("wave.gui.rules.lore", cfg.timing.activationDistance, cfg.defeat.treasuryLossPercent,
					cfg.defeat.nightRollback);
			return GuiUtil.item(Items.LECTERN, Lang.get("wave.gui.rules.name"), lore);
		}
	}

	/** Human readable duration (French). */
	static String duration(long seconds) {
		long s = Math.max(0, seconds);
		if (s < 60) {
			return Lang.raw("wave.time.seconds", s);
		}
		if (s < 3600) {
			return Lang.raw("wave.time.minutes", s / 60, s % 60);
		}
		if (s < 86400) {
			return Lang.raw("wave.time.hours", s / 3600, (s % 3600) / 60);
		}
		return Lang.raw("wave.time.days", s / 86400, (s % 86400) / 3600);
	}
}
