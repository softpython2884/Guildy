package dev.guildy.core;

import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Entry point for unique (legendary) weapon drops, implemented by the unique-items module. Other modules (hordes,
 * quests, merchants, expeditions) call {@link #roll} with their source name; the default is a safe no-op.
 */
public final class UniqueDrops {
	/** Rolls a unique item for a source ("boss", "blood_boss", "quest", "legendary_quest", "expedition", "trade"). */
	@FunctionalInterface
	public interface Roller {
		ItemStack roll(String source, RandomGenerator random);
	}

	/** Creates a unique item by id (empty stack for an unknown id). */
	@FunctionalInterface
	public interface Factory {
		ItemStack create(String id);
	}

	public static Roller roller = (source, random) -> ItemStack.EMPTY;
	public static Factory factory = id -> ItemStack.EMPTY;

	private UniqueDrops() {
	}

	/** A unique item for this source, or {@link ItemStack#EMPTY} when the roll fails (or the module is absent). */
	public static ItemStack roll(String source) {
		return roll(source, ThreadLocalRandom.current());
	}

	public static ItemStack roll(String source, RandomGenerator random) {
		ItemStack stack = Safe.get("unique.roll", () -> roller.roll(source, random), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	public static ItemStack create(String id) {
		ItemStack stack = Safe.get("unique.create", () -> factory.create(id), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}
}
