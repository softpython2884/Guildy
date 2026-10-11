package dev.guildy.quest;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure quest generation (unit-tested with a seeded {@link Random}): weighted choice among eligible templates,
 * amounts and rewards scaled by the guild level, item/flag/map rewards rolled up-front so they can be displayed,
 * assortments of COLLECTION quests, legendary contracts.
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
	 * @param parts         COLLECTION items (empty for the other types); {@code amount} is their sum
	 */
	public record Rolled(QuestTemplate template, int amount, int gold, int xp, List<RewardStack> items, int flags,
			boolean treasureMap, long durationTicks, List<QuestPart> parts, boolean legendary) {
	}

	/** Multipliers turning a quest into a legendary contract. */
	public record Legendary(double amount, double duration, double gold, double xp) {
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

	/** Templates allowed to become legendary contracts (not EXPLORE / BIOME, not opted out). */
	public static List<QuestTemplate> legendaryCandidates(Collection<QuestTemplate> templates) {
		List<QuestTemplate> list = new ArrayList<>();
		for (QuestTemplate template : templates) {
			if (template != null && template.legendaryAllowed()) {
				list.add(template);
			}
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
		if (template.type != null && template.type.fixedAmount()) {
			amount = 1;
		}
		List<QuestPart> parts = template.type == QuestType.COLLECTION ? rollParts(template.collection, level, random) : List.of();
		if (!parts.isEmpty()) {
			amount = 0;
			for (QuestPart part : parts) {
				amount += part.amount;
			}
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
		return new Rolled(template, amount, gold, xp, items, flags, map, durationTicks(template.durationDays), parts, false);
	}

	/**
	 * Assortment of a COLLECTION quest: between {@code min} and {@code max} different items of the pool (distinct
	 * entries), each with its own amount scaled by the level.
	 */
	public static List<QuestPart> rollParts(QuestTemplate.@Nullable Assortment collection, int level, Random random) {
		if (collection == null) {
			return List.of();
		}
		List<QuestTemplate.Part> pool = new ArrayList<>();
		for (QuestTemplate.Part part : collection.pool()) {
			if (part != null && part.item != null && !part.item.isBlank()) {
				pool.add(part);
			}
		}
		if (pool.isEmpty()) {
			return List.of();
		}
		int max = Math.max(1, Math.min(collection.max, pool.size()));
		int min = Math.max(1, Math.min(collection.min, max));
		int count = min + random.nextInt(max - min + 1);
		Collections.shuffle(pool, random);
		List<QuestPart> parts = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			QuestTemplate.Part part = pool.get(i);
			int amount = Math.max(1, scaled(part.amountMin, part.amountMax, part.amountPerLevel, level, random));
			parts.add(new QuestPart(part.item.trim(), part.name == null ? "" : part.name, amount));
		}
		return parts;
	}

	/** Picks and rolls a quest; null when no template is eligible. */
	public static @Nullable Rolled generate(Collection<QuestTemplate> templates, int level, Set<String> exclude, boolean allowExplore, Random random) {
		QuestTemplate template = pick(eligible(templates, level, exclude, allowExplore), random);
		return template == null ? null : roll(template, level, random);
	}

	/**
	 * True when the next quest of the board becomes a legendary contract: enabled, guild level high enough, no legendary
	 * contract on the board, cooldown since the previous one elapsed, then a {@code chance} roll.
	 */
	public static boolean legendaryTurn(boolean enabled, int level, int minLevel, boolean alreadyActive, long now, long lastLegendaryAt,
			long cooldownTicks, double chance, Random random) {
		if (!enabled || level < minLevel || alreadyActive || !(chance > 0)) {
			return false;
		}
		if (lastLegendaryAt > 0 && now >= lastLegendaryAt && now - lastLegendaryAt < cooldownTicks) {
			return false;
		}
		return random.nextDouble() < chance;
	}

	/**
	 * Legendary version of a rolled quest: amounts (and every item of an assortment) multiplied and rounded up, longer time
	 * limit, more gold and XP. Deliveries never pay more gold per item than the normal quest (their gold multiplier is
	 * capped by the amount multiplier), so the anti gold-loop rule of the shop still holds.
	 */
	public static Rolled legendary(Rolled rolled, Legendary multipliers) {
		QuestType type = rolled.template().type;
		double amountFactor = atLeastOne(multipliers.amount());
		boolean fixed = type != null && type.fixedAmount();
		List<QuestPart> parts = new ArrayList<>(rolled.parts().size());
		int amount = fixed ? rolled.amount() : ceil(rolled.amount(), amountFactor);
		if (!rolled.parts().isEmpty()) {
			amount = 0;
			for (QuestPart part : rolled.parts()) {
				QuestPart scaled = new QuestPart(part.target, part.targetName, ceil(part.amount, amountFactor));
				parts.add(scaled);
				amount += scaled.amount;
			}
		}
		double goldFactor = atLeastOne(multipliers.gold());
		if (type != null && type.isDelivery()) {
			goldFactor = Math.min(goldFactor, amountFactor);
		}
		int gold = (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(rolled.gold() * goldFactor + 1e-9));
		int xp = (int) Math.min(Integer.MAX_VALUE, Math.round(rolled.xp() * atLeastOne(multipliers.xp())));
		long duration = Math.max(rolled.durationTicks(), Math.round(rolled.durationTicks() * atLeastOne(multipliers.duration())));
		return new Rolled(rolled.template(), Math.max(1, amount), gold, xp, rolled.items(), rolled.flags(), rolled.treasureMap(),
				duration, parts, true);
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

	private static double atLeastOne(double factor) {
		return Double.isFinite(factor) ? Math.max(1.0, factor) : 1.0;
	}

	private static int ceil(int value, double factor) {
		return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (long) Math.ceil(value * factor - 1e-9)));
	}
}
