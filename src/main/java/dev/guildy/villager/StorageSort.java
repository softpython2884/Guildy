package dev.guildy.villager;

import it.unimi.dsi.fastutil.Hash;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Canonical order of the items in a chest group: the creative inventory order (similar things together: every wood
 * type, then stone..., helmet / chestplate / leggings / boots of each material), then the item id, then the components:
 * plain items (and creative variants such as potions) first, then other changes, enchanted, named and damaged items.
 * Stacks with different components never compare as equal unless they are interchangeable.
 */
final class StorageSort {
	/** Hash strategy: same item and same components (interchangeable stacks). */
	static final Hash.Strategy<ItemStack> SAME_ITEM = new Hash.Strategy<>() {
		@Override
		public int hashCode(@Nullable ItemStack stack) {
			return ItemStack.hashItemAndComponents(stack);
		}

		@Override
		public boolean equals(@Nullable ItemStack a, @Nullable ItemStack b) {
			return a == b || (a != null && b != null && ItemStack.isSameItemSameComponents(a, b));
		}
	};

	/** What the order compares, computed once per stack. */
	private record SortKey(int creative, String id, int rank, int damage, String name, String components) {
	}

	private static final Comparator<SortKey> KEY_ORDER = Comparator.comparingInt(SortKey::creative)
			.thenComparing(SortKey::id)
			.thenComparingInt(SortKey::rank)
			.thenComparingInt(SortKey::damage)
			.thenComparing(SortKey::name)
			.thenComparing(SortKey::components);

	private StorageSort() {
	}

	/** Sorts distinct stacks in the canonical order (each sort key computed once). */
	static void sort(List<ItemStack> stacks) {
		record Keyed(SortKey key, ItemStack stack) {
		}
		List<Keyed> keyed = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			keyed.add(new Keyed(key(stack), stack));
		}
		keyed.sort((a, b) -> KEY_ORDER.compare(a.key(), b.key()));
		stacks.clear();
		for (Keyed entry : keyed) {
			stacks.add(entry.stack());
		}
	}

	private static SortKey key(ItemStack stack) {
		boolean plain = stack.getComponentsPatch().isEmpty();
		return new SortKey(ItemClassifier.creativeIndex(stack), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), rank(stack),
				stack.getDamageValue(), name(stack), plain ? "" : components(stack));
	}

	/** 0 plain (or creative variant), 1 other components, 2 enchanted, 3 named, 4 damaged. */
	static int rank(ItemStack stack) {
		if (stack.getComponentsPatch().isEmpty() || ItemClassifier.isCreativeVariant(stack)) {
			return 0;
		}
		if (stack.isDamaged()) {
			return 4;
		}
		if (stack.has(DataComponents.CUSTOM_NAME)) {
			return 3;
		}
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments != null && !enchantments.isEmpty()) {
			return 2;
		}
		return 1;
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	/** Deterministic text of the component changes (sorted by component type). */
	private static String components(ItemStack stack) {
		List<String> parts = new ArrayList<>();
		DataComponentPatch.SplitResult split = stack.getComponentsPatch().split();
		for (TypedDataComponent<?> component : split.added()) {
			parts.add(component.type() + "=" + component.value());
		}
		for (DataComponentType<?> removed : split.removed()) {
			parts.add("!" + removed);
		}
		parts.sort(null);
		return String.join(";", parts);
	}
}
