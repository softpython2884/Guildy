package dev.guildy.quest;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Quest kinds. Progress is only counted for guild members, never in creative mode.
 * <ul>
 * <li>GATHER breaks blocks (target = block or #tag), KILL kills entities (entity or #tag), DELIVER hands items in from the
 * menu (item or #tag); SURVIVE counts night wave victories and EXPLORE a destination point (no target).</li>
 * <li>CRAFT / SMELT: items taken out of a crafting result slot / of a furnace, smoker or blast furnace (item or #tag).</li>
 * <li>FISH (fished items), BREED (animals born), TAME (animals tamed), TRADE (trades with villagers or merchants, target =
 * item obtained), ENCHANT (items enchanted at the table), NIGHT_HUNT (monsters killed at night): optional target, blank =
 * anything.</li>
 * <li>ELITE / BOSS: horde elites / bosses killed (no target); BIOME: enter a biome (biome or #tag, checked every few
 * seconds); COLLECTION: deliver an assortment of different items, each with its own progress
 * ({@link QuestTemplate#collection}).</li>
 * </ul>
 */
public enum QuestType {
	GATHER(TargetKind.BLOCK, true),
	KILL(TargetKind.ENTITY, true),
	DELIVER(TargetKind.ITEM, true),
	SURVIVE(TargetKind.NONE, false),
	EXPLORE(TargetKind.NONE, false),
	CRAFT(TargetKind.ITEM, true),
	SMELT(TargetKind.ITEM, true),
	FISH(TargetKind.ITEM, false),
	BREED(TargetKind.ENTITY, false),
	TAME(TargetKind.ENTITY, false),
	TRADE(TargetKind.ITEM, false),
	ENCHANT(TargetKind.ITEM, false),
	ELITE(TargetKind.NONE, false),
	BOSS(TargetKind.NONE, false),
	NIGHT_HUNT(TargetKind.ENTITY, false),
	BIOME(TargetKind.BIOME, true),
	COLLECTION(TargetKind.NONE, false);

	/** What the {@code target} of a quest designates. */
	public enum TargetKind {
		NONE,
		BLOCK,
		ENTITY,
		ITEM,
		BIOME
	}

	private final TargetKind targetKind;
	private final boolean requiresTarget;

	QuestType(TargetKind targetKind, boolean requiresTarget) {
		this.targetKind = targetKind;
		this.requiresTarget = requiresTarget;
	}

	public TargetKind targetKind() {
		return targetKind;
	}

	/** True when a target may be set (required or optional). */
	public boolean hasTarget() {
		return targetKind != TargetKind.NONE;
	}

	/** True when the quest cannot work without a target. */
	public boolean requiresTarget() {
		return requiresTarget;
	}

	/** EXPLORE and BIOME are done in one visit: their amount is always 1 (and they never become legendary). */
	public boolean fixedAmount() {
		return this == EXPLORE || this == BIOME;
	}

	/** Items handed in from the menu: their gold stays below the shop price and is never boosted (no gold loop). */
	public boolean isDelivery() {
		return this == DELIVER || this == COLLECTION;
	}

	/** Can be drawn as a legendary contract (needs an amount to scale). */
	public boolean canBeLegendary() {
		return !fixedAmount();
	}

	/** Lang key suffix ("quest.type.gather"...). */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** "GATHER, KILL, ..." (config error messages). */
	public static String names() {
		return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
	}
}
