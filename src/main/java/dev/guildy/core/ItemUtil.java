package dev.guildy.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {
	private ItemUtil() {
	}

	/** Gives the stack to the player; whatever doesn't fit is dropped at their feet. */
	public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack copy = stack.copy();
		boolean added = player.getInventory().add(copy);
		if (!added || !copy.isEmpty()) {
			player.drop(copy, false, Prediction.SERVER_ONLY);
		}
		player.containerMenu.broadcastChanges();
	}

	/** Gives {@code count} items, split into max-size stacks. */
	public static void giveOrDrop(ServerPlayer player, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		while (count > 0) {
			int n = Math.min(max, count);
			giveOrDrop(player, new ItemStack(item, n));
			count -= n;
		}
	}

	/** Parses "minecraft:iron_ingot" (namespace optional). Returns null for unknown ids / air. */
	public static @Nullable Item item(String id) {
		try {
			Identifier identifier = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
			Item item = BuiltInRegistries.ITEM.getValue(identifier);
			return item == Items.AIR ? null : item;
		} catch (Exception e) {
			return null;
		}
	}

	public static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Display name (translatable component rendered client-side in the player's language). */
	public static Component name(Item item) {
		return new ItemStack(item).getHoverName();
	}

	/** Sets a non-italic custom name and lore lines (legacy '&' codes already converted by Lang). */
	public static ItemStack named(ItemStack stack, Component name, List<? extends Component> lore) {
		stack.set(DataComponents.CUSTOM_NAME, Component.empty().append(name).withStyle(s -> s.withItalic(false)));
		if (lore != null && !lore.isEmpty()) {
			List<Component> lines = new ArrayList<>(lore.size());
			for (Component line : lore) {
				lines.add(Component.empty().append(line).withStyle(s -> s.withItalic(false)));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}

	/** Counts matching items in the player's inventory. */
	public static int count(ServerPlayer player, Item item) {
		int total = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.is(item) && !GuildyItems.isSpecial(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Removes exactly {@code amount} items (never special Guildy items). Checks first: returns false and changes
	 * nothing if the player doesn't have enough.
	 */
	public static boolean remove(ServerPlayer player, Item item, int amount) {
		if (amount <= 0) {
			return true;
		}
		if (count(player, item) < amount) {
			return false;
		}
		var inventory = player.getInventory();
		int remaining = amount;
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.is(item) && !GuildyItems.isSpecial(stack)) {
				int n = Math.min(remaining, stack.getCount());
				stack.shrink(n);
				if (stack.isEmpty()) {
					inventory.setItem(i, ItemStack.EMPTY);
				}
				remaining -= n;
			}
		}
		inventory.setChanged();
		player.containerMenu.broadcastChanges();
		return true;
	}
}
