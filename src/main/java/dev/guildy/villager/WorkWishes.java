package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The "objectifs prioritaires" of one villager at work (its own list merged with its zone's), with cached answers to
 * "does this tree / crop give a wanted item?" (rules in {@link WishMatch}).
 */
final class WorkWishes {
	static final WorkWishes NONE = new WorkWishes(List.of(), "");

	private final List<String> ids;
	private final String key;
	private final Map<Block, Boolean> logs = new HashMap<>();
	private final Map<Block, Boolean> leafMaybe = new HashMap<>();
	private final Map<Block, Boolean> leaves = new HashMap<>();
	private final Map<Block, Boolean> crops = new HashMap<>();

	private WorkWishes(List<String> ids, String key) {
		this.ids = ids;
		this.key = key;
	}

	/** Identity of the wishes ("" = none): a change restarts the search of the villager. */
	static String keyOf(VillagerRecord record, @Nullable WorkZone zone) {
		if (record.wishlist.isEmpty() && (zone == null || zone.wishlist.isEmpty())) {
			return "";
		}
		return String.join(",", record.wishlist) + "/" + (zone == null ? "" : String.join(",", zone.wishlist));
	}

	static WorkWishes of(VillagerRecord record, @Nullable WorkZone zone) {
		String key = keyOf(record, zone);
		if (key.isEmpty()) {
			return NONE;
		}
		List<String> ids = new ArrayList<>();
		for (Item item : Wishlist.items(record, zone)) {
			ids.add(ItemUtil.id(item));
		}
		return ids.isEmpty() ? NONE : new WorkWishes(ids, key);
	}

	boolean isEmpty() {
		return ids.isEmpty();
	}

	String key() {
		return key;
	}

	/** Cutting these logs gives a wanted item (log, wood, planks of this wood). */
	boolean logWanted(Block log) {
		return !ids.isEmpty() && logs.computeIfAbsent(log, block -> any(id -> WishMatch.fromLogs(blockId(block), id)));
	}

	/** The leaves of a tree made of these logs may drop a wanted item (sapling, apple, stick). */
	boolean leavesMayGive(Block log) {
		return !ids.isEmpty() && leafMaybe.computeIfAbsent(log, block -> any(id -> WishMatch.leavesMayGive(blockId(block), id)));
	}

	/** Breaking these leaves may drop a wanted item. */
	boolean leavesWanted(Block leavesBlock) {
		return !ids.isEmpty() && leaves.computeIfAbsent(leavesBlock, block -> any(id -> WishMatch.fromLeaves(blockId(block), id)));
	}

	/** Harvesting this ripe plant gives a wanted item (its seed or its harvest). */
	boolean cropWanted(Block crop) {
		return !ids.isEmpty() && crops.computeIfAbsent(crop, block -> {
			Item seed = block.asItem();
			String seedId = seed == Items.AIR ? null : ItemUtil.id(seed);
			return any(id -> WishMatch.fromCrop(blockId(block), seedId, id));
		});
	}

	private boolean any(Predicate<String> test) {
		for (String id : ids) {
			if (test.test(id)) {
				return true;
			}
		}
		return false;
	}

	private static String blockId(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	// ------------------------------------------------------------------ menu hints

	/** A lumberjack can bring it back: logs and wood, planks (crafted from its logs), saplings, apples, sticks. */
	static boolean lumberjackCanGet(Item item) {
		ItemStack stack = new ItemStack(item);
		return stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.SAPLINGS) || item == Items.APPLE
				|| item == Items.STICK;
	}

	/** A farmer can bring it back (vanilla crops, melons, pumpkins, sugar cane). */
	static boolean farmerCanGet(Item item) {
		return WishMatch.FARM_ITEMS.contains(ItemUtil.id(item));
	}
}
