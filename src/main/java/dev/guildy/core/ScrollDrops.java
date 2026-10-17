package dev.guildy.core;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Entry point for spell scroll drops, implemented by the scroll module. Other modules (ruin bosses, hordes, quests,
 * merchants, the alchemist) call {@link #roll} with their source name, or {@link #create} with a scroll id; the default
 * is a safe no-op.
 */
public final class ScrollDrops {
	/** Kind of a scroll in its custom data ({@link GuildyItems}). */
	public static final String KIND = "scroll";

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

	/** Picks one scroll of a source's table without its drop chance (a guaranteed scroll, e.g. a replacement). */
	@FunctionalInterface
	public interface Picker {
		ItemStack pick(String source, RandomGenerator random);
	}

	/** What other modules may know about a scroll id, or null for an id the scroll config does not define. */
	@FunctionalInterface
	public interface Lookup {
		@Nullable Info info(String id);
	}

	/**
	 * A defined scroll: its rarity name (RARE, EPIC, LEGENDARY, MYTHIC), its plain name (no color codes) and whether new
	 * scrolls may be handed out right now (false while scrolls or their drops are disabled in scrolls.json5).
	 */
	public record Info(String rarity, String name, boolean obtainable) {
	}

	public static Roller roller = (source, random) -> ItemStack.EMPTY;
	public static Picker picker = (source, random) -> ItemStack.EMPTY;
	public static Factory factory = (id, count) -> ItemStack.EMPTY;
	public static Lookup lookup = id -> null;

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

	/** One scroll of the source's table, chance ignored, or {@link ItemStack#EMPTY} (empty table, module absent). */
	public static ItemStack pick(String source, RandomGenerator random) {
		ItemStack stack = Safe.get("scroll.pick", () -> picker.pick(source, random), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	public static ItemStack create(String id, int count) {
		ItemStack stack = Safe.get("scroll.create", () -> factory.create(id, count), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	/** Rarity and name of a scroll id, or null (unknown id, module absent). */
	public static @Nullable Info info(String id) {
		return Safe.get("scroll.info", () -> lookup.info(id), null);
	}

	/** A spell scroll (any id, even one removed from the config). */
	public static boolean isScroll(ItemStack stack) {
		return GuildyItems.is(stack, KIND);
	}
}
