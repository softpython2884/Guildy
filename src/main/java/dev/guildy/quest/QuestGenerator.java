package dev.guildy.quest;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure quest generation (unit-tested with a seeded {@link Random}): weighted choice among eligible templates,
 * amounts and rewards scaled by the guild level, item/flag/map rewards rolled up-front so they can be displayed.
 */
public final class QuestGenerator {
	/** In-game day length in ticks. */
	public static final long DAY_TICKS = 24000L;

	private QuestGenerator() {
	}

	/**
	 * A rolled quest.
	 *
	 * @param durationTicks time limit in game ticks
	 */
	public record Rolled(QuestTemplate template, int amount, int gold, int xp, List<RewardStack> items, int flags,
			boolean treasureMap, long durationTicks) {
	}

	/** Templates that may be offered: level range, positive weight, not already active, EXPLORE only if allowed. */
	public static List<QuestTemplate> eligible(Collection<QuestTemplate> templates, int level, Set<String> exclude, boolean allowExplore) {
		List<QuestTemplate> list = new ArrayList<>();
		for (QuestTemplate template : templates) {
			if (template == null || template.type == null || template.weight <= 0 || !template.levelAllows(level)
					|| exclude.contains(template.id) || (template.type == QuestType.EXPLORE && !allowExplore)) {
				continue;
			}
			list.add(template);
		}
		return list;
	}

	/** Weighted random choice (null when the list is empty). */
	public static @Nullable QuestTemplate pick(List<QuestTemplate> eligible, Random random) {
		long total = 0;
		for (QuestTemplate template : eligible) {
			total += Math.max(0, template.weight);
		}
		if (total <= 0) {
			return null;
		}
		long roll = (long) (random.nextDouble() * total);
		for (QuestTemplate template : eligible) {
			roll -= Math.max(0, template.weight);
			if (roll < 0) {
				return template;
			}
		}
		return eligible.get(eligible.size() - 1);
	}

	/** Rolls amount and rewards of a template for a guild level. */
	public static Rolled roll(QuestTemplate template, int level, Random random) {
		int amount = Math.max(1, scaled(template.amountMin, template.amountMax, template.amountPerLevel, level, random));
		if (template.type == QuestType.EXPLORE) {
			amount = 1;
		}
		int gold = scaled(template.rewardGoldMin, template.rewardGoldMax, template.rewardGoldPerLevel, level, random);
		int xp = scaled(template.rewardXp, template.rewardXp, template.rewardXpPerLevel, level, random);
		List<RewardStack> items = new ArrayList<>();
		if (template.rewardItems != null) {
			for (QuestTemplate.RewardItem reward : template.rewardItems) {
				if (reward != null && reward.item != null && !reward.item.isBlank() && random.nextDouble() < reward.chance) {
					items.add(new RewardStack(reward.item, Math.max(1, reward.count)));
				}
			}
		}
		int flags = random.nextDouble() < template.flagChance ? 1 : 0;
		boolean map = random.nextDouble() < template.treasureMapChance;
		return new Rolled(template, amount, gold, xp, items, flags, map, durationTicks(template.durationDays));
	}

	/** Picks and rolls a quest; null when no template is eligible. */
	public static @Nullable Rolled generate(Collection<QuestTemplate> templates, int level, Set<String> exclude, boolean allowExplore, Random random) {
		QuestTemplate template = pick(eligible(templates, level, exclude, allowExplore), random);
		return template == null ? null : roll(template, level, random);
	}

	/** {@code random(min..max) + floor(perLevel * (level - 1))}, never negative. */
	public static int scaled(int min, int max, double perLevel, int level, Random random) {
		int lo = Math.min(min, max);
		int hi = Math.max(min, max);
		int base = hi > lo ? lo + random.nextInt(hi - lo + 1) : lo;
		double bonus = Double.isFinite(perLevel) ? perLevel * Math.max(0, level - 1) : 0;
		long value = (long) base + (long) Math.floor(bonus);
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
	}

	/** Treasure map tier for a guild level: 1 at levels 1-3, 2 at 4-6, 3 at 7-9... capped by the tier count. */
	public static int mapTier(int level, int tiers) {
		int tier = 1 + Math.max(0, level - 1) / 3;
		return Math.max(1, Math.min(Math.max(1, tiers), tier));
	}

	public static long durationTicks(double days) {
		double safe = Double.isFinite(days) && days > 0 ? days : 3;
		return Math.max(1200L, Math.round(safe * DAY_TICKS));
	}
}
