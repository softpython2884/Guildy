package dev.guildy.trade;

import dev.guildy.Guildy;
import dev.guildy.core.ItemUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds vanilla merchant offers from {@link TradeEntry trade entries}: paid in gold ingots (gold blocks + ingots above
 * 64), fixed prices (no demand, no reputation discount).
 */
public final class TradeOffers {
	private static final Set<String> WARNED = new HashSet<>();

	private TradeOffers() {
	}

	public static MerchantOffers build(HolderLookup.Provider registries, List<TradeEntry> entries) {
		MerchantOffers offers = new MerchantOffers();
		for (TradeEntry entry : entries) {
			MerchantOffer offer = offer(registries, entry);
			if (offer != null) {
				offers.add(offer);
			}
		}
		return offers;
	}

	public static @Nullable MerchantOffer offer(HolderLookup.Provider registries, TradeEntry entry) {
		ItemStack result = result(registries, entry);
		if (result == null) {
			return null;
		}
		int[] split = TradeRolls.priceSplit(entry.price);
		ItemCost costA;
		Optional<ItemCost> costB = Optional.empty();
		if (split[0] > 0) {
			costA = new ItemCost(Items.GOLD_BLOCK, split[0]);
			if (split[1] > 0) {
				costB = Optional.of(new ItemCost(Items.GOLD_INGOT, split[1]));
			}
		} else {
			costA = new ItemCost(Items.GOLD_INGOT, split[1]);
		}
		// priceMultiplier 0: the price never changes with demand.
		return new MerchantOffer(costA, costB, result, Math.max(1, entry.maxUses), 1, 0.0F);
	}

	/** The sold stack (enchanted when the entry has an enchantment), or null when the item / enchantment is unknown. */
	public static @Nullable ItemStack result(HolderLookup.Provider registries, TradeEntry entry) {
		Item item = ItemUtil.item(entry.item);
		if (item == null) {
			warnOnce(entry.item, "unknown item");
			return null;
		}
		ItemStack stack = new ItemStack(item);
		stack.setCount(Math.max(1, Math.min(entry.count, stack.getMaxStackSize())));
		if (entry.enchanted()) {
			Holder<Enchantment> enchantment = enchantment(registries, entry.enchantment);
			if (enchantment == null) {
				warnOnce(entry.enchantment, "unknown enchantment");
				return null;
			}
			stack.enchant(enchantment, Math.max(1, entry.level));
		}
		return stack;
	}

	public static @Nullable Holder<Enchantment> enchantment(HolderLookup.Provider registries, String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		return registries.lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, identifier)).orElse(null);
	}

	/** "Mending", "Efficiency V"... (translated client-side), or null for plain entries / unknown enchantments. */
	public static @Nullable Component enchantmentName(HolderLookup.Provider registries, TradeEntry entry) {
		if (!entry.enchanted()) {
			return null;
		}
		Holder<Enchantment> enchantment = enchantment(registries, entry.enchantment);
		return enchantment == null ? null : Enchantment.getFullname(enchantment, Math.max(1, entry.level));
	}

	private static void warnOnce(String id, String problem) {
		if (WARNED.add(id)) {
			Guildy.LOGGER.warn("[trade] {} '{}': merchant offer skipped", problem, id);
		}
	}
}
