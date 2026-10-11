package dev.guildy.shop;

import dev.guildy.core.GoldUtil;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.structure.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Atomic buy / sell operations (server thread): gold is taken before items are given, items are taken before gold
 * is paid; nothing changes when a check fails.
 */
final class ShopTransactions {
	/** Result message; {@code success} drives the sound. */
	record Result(boolean success, Component message) {
		static Result fail(Component message) {
			return new Result(false, message);
		}
	}

	private ShopTransactions() {
	}

	static Predicate<ItemStack> matcher(Item item) {
		return stack -> stack.is(item);
	}

	static Result buy(ServerPlayer player, Guild guild, ShopEntry entry, int lots) {
		Item item = ItemUtil.item(entry.item);
		if (item == null || entry.buy <= 0) {
			return Result.fail(Lang.get("shop.buy.unavailable"));
		}
		if (guild.level < entry.minLevel) {
			return Result.fail(Lang.get("shop.level", entry.minLevel));
		}
		long total = ShopValidator.buyTotal(entry, lots);
		if (total <= 0 || total > Integer.MAX_VALUE) {
			return Result.fail(Lang.get("shop.buy.unavailable"));
		}
		if (!GoldUtil.take(player, (int) total)) {
			return Result.fail(Lang.get("shop.buy.no_gold", total, GoldUtil.count(player)));
		}
		ItemUtil.giveOrDrop(player, item, entry.amount * lots);
		return new Result(true, TextUtil.lang("shop.buy.done", entry.amount * lots, ItemUtil.name(item), total));
	}

	/** Sells one lot, or every complete lot when {@code all}. */
	static Result sell(ServerPlayer player, Guild guild, ShopEntry entry, boolean all) {
		Item item = ItemUtil.item(entry.item);
		if (item == null || entry.sell <= 0) {
			return Result.fail(Lang.get("shop.sell.unavailable"));
		}
		if (player.isCreative()) {
			return Result.fail(Lang.get("shop.sell.creative"));
		}
		if (guild.level < entry.minLevel) {
			return Result.fail(Lang.get("shop.level", entry.minLevel));
		}
		Predicate<ItemStack> filter = matcher(item);
		int owned = PlainItems.count(player, filter);
		int lots = ShopValidator.sellableLots(entry, owned, all);
		if (lots <= 0) {
			return Result.fail(TextUtil.lang("shop.sell.not_enough", entry.amount, ItemUtil.name(item)));
		}
		// Items first, then gold: a failed removal never pays.
		if (!PlainItems.remove(player, filter, lots * entry.amount)) {
			return Result.fail(TextUtil.lang("shop.sell.not_enough", entry.amount, ItemUtil.name(item)));
		}
		long gold = (long) entry.sell * lots;
		GoldUtil.give(player, gold);
		return new Result(true, TextUtil.lang("shop.sell.done", lots * entry.amount, ItemUtil.name(item), gold));
	}
}
