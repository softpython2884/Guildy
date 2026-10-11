package dev.guildy.wave;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Pure planning of a wave (theme draw + ordered spawn list). No Minecraft access: entity ids are validated through
 * the given predicate so the planner can be unit-tested with a seeded random.
 */
public final class WavePlanner {
	private WavePlanner() {
	}

	public enum Kind {
		NORMAL,
		ELITE,
		BOSS
	}

	/** One mob of the spawn list. Exactly one of {@code entry} / {@code boss} is set. */
	public record PlannedMob(String entity, Kind kind, WaveConfig.@Nullable MobEntry entry, WaveConfig.@Nullable BossDef boss) {
		public boolean isBoss() {
			return kind == Kind.BOSS;
		}

		public boolean isElite() {
			return kind == Kind.ELITE;
		}

		public boolean gear() {
			return entry != null ? entry.gear : boss != null && boss.gear;
		}

		public boolean alwaysHostile() {
			return entry != null ? entry.alwaysHostile : boss != null && boss.alwaysHostile;
		}

		public int spawnHeight() {
			return entry != null ? entry.spawnHeight : boss != null ? boss.spawnHeight : 0;
		}
	}

	/** Spawn order of the wave (the boss is inserted according to spawning.bossSpawnProgress). */
	public record Plan(WaveConfig.Theme theme, boolean hasBoss, List<PlannedMob> mobs) {
		public int eliteCount() {
			int n = 0;
			for (PlannedMob mob : mobs) {
				if (mob.isElite()) {
					n++;
				}
			}
			return n;
		}
	}

	/** Themes that can be drawn at this night step. */
	public static List<WaveConfig.Theme> availableThemes(List<WaveConfig.Theme> themes, int night) {
		List<WaveConfig.Theme> list = new ArrayList<>();
		for (WaveConfig.Theme theme : themes) {
			if (theme != null && theme.availableAt(night)) {
				list.add(theme);
			}
		}
		return list;
	}

	/** Weighted theme draw; on scheduled boss nights, themes having bosses are preferred. */
	public static WaveConfig.@Nullable Theme pickTheme(List<WaveConfig.Theme> themes, int night, boolean scheduledBossNight, RandomGenerator random) {
		List<WaveConfig.Theme> available = availableThemes(themes, night);
		if (scheduledBossNight) {
			List<WaveConfig.Theme> withBoss = new ArrayList<>();
			for (WaveConfig.Theme theme : available) {
				if (theme.hasBosses()) {
					withBoss.add(theme);
				}
			}
			WaveConfig.Theme picked = WeightedPicker.pick(withBoss, t -> t.weight, random);
			if (picked != null) {
				return picked;
			}
		}
		return WeightedPicker.pick(available, t -> t.weight, random);
	}

	/** Entries usable at this difficulty (falls back to the lowest-difficulty entries when none qualifies). */
	public static List<WaveConfig.MobEntry> entryPool(WaveConfig.Theme theme, double difficulty, Predicate<String> validEntity) {
		List<WaveConfig.MobEntry> valid = new ArrayList<>();
		for (WaveConfig.MobEntry entry : theme.mobs) {
			if (entry != null && entry.weight > 0 && validEntity.test(entry.entity)) {
				valid.add(entry);
			}
		}
		List<WaveConfig.MobEntry> pool = new ArrayList<>();
		double lowest = Double.MAX_VALUE;
		for (WaveConfig.MobEntry entry : valid) {
			if (entry.minDifficulty <= difficulty) {
				pool.add(entry);
			}
			lowest = Math.min(lowest, entry.minDifficulty);
		}
		if (pool.isEmpty()) {
			for (WaveConfig.MobEntry entry : valid) {
				if (entry.minDifficulty == lowest) {
					pool.add(entry);
				}
			}
		}
		return pool;
	}

	public static Plan plan(WaveConfig config, WaveConfig.Theme theme, double difficulty, boolean scheduledBossNight,
			Predicate<String> validEntity, RandomGenerator random) {
		List<WaveConfig.MobEntry> pool = entryPool(theme, difficulty, validEntity);
		int count = WaveFormula.mobCount(config.difficulty, difficulty, theme.mobCountMultiplier);
		double eliteChance = WaveFormula.eliteChance(config.difficulty, difficulty, theme.eliteBonus);
		Map<WaveConfig.MobEntry, Integer> counts = new IdentityHashMap<>();
		List<PlannedMob> mobs = new ArrayList<>(count + 1);
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			WaveConfig.MobEntry entry = WeightedPicker.pick(pool,
					e -> e.maxCount > 0 && counts.getOrDefault(e, 0) >= e.maxCount ? 0 : e.weight, random);
			if (entry == null) {
				break; // every entry reached its maxCount
			}
			counts.merge(entry, 1, Integer::sum);
			boolean elite = random.nextDouble() < eliteChance;
			mobs.add(new PlannedMob(entry.entity, elite ? Kind.ELITE : Kind.NORMAL, entry, null));
		}

		boolean hasBoss = false;
		if ((scheduledBossNight || theme.forceBoss) && theme.hasBosses()) {
			List<WaveConfig.BossDef> bosses = new ArrayList<>();
			for (WaveConfig.BossDef boss : theme.bosses) {
				if (boss != null && validEntity.test(boss.entity)) {
					bosses.add(boss);
				}
			}
			WaveConfig.BossDef boss = WeightedPicker.pick(bosses, b -> b.weight, random);
			if (boss != null) {
				int index = (int) Math.round(config.spawning.bossSpawnProgress * mobs.size());
				index = Math.max(0, Math.min(mobs.size(), index));
				mobs.add(index, new PlannedMob(boss.entity, Kind.BOSS, null, boss));
				hasBoss = true;
			}
		}
		return new Plan(theme, hasBoss, mobs);
	}
}
