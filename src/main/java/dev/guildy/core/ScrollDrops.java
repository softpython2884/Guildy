package dev.guildy.core;

import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Entry point for spell scroll drops, implemented by the scroll module. Other modules (ruin bosses, hordes, quests,
 * merchants) call {@link #roll} with their source name; the default is a safe no-op.
 */
public final class ScrollDrops {
	/** Rolls a spell scroll for a source ("ruin_boss", "nether_ruin_boss", "boss", "legendary_quest", ...). */
	@FunctionalInterface
	public interface Roller {
		ItemStack roll(String source, RandomGenerator random);
	}

	/** Creates a spell scroll by id (empty stack for an unknown id). */
	@FunctionalInterface
	public interface Factory {
		ItemStack create(String id, int count);
	}

	public static Roller roller = (source, random) -> ItemStack.EMPTY;
	public static Factory factory = (id, count) -> ItemStack.EMPTY;

	private ScrollDrops() {
	}

	/** A scroll for this source, or {@link ItemStack#EMPTY} when the roll fails (or the module is absent). */
	public static ItemStack roll(String source) {
		return roll(source, ThreadLocalRandom.current());
	}

	public static ItemStack roll(String source, RandomGenerator random) {
		ItemStack stack = Safe.get("scroll.roll", () -> roller.roll(source, random), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	public static ItemStack create(String id, int count) {
		ItemStack stack = Safe.get("scroll.create", () -> factory.create(id, count), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}
}
