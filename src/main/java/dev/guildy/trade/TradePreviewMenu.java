package dev.guildy.trade;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.gui.GuiUtil;
import dev.guildy.gui.GuildGui;
import dev.guildy.structure.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every possible offer of one hub tier (6 rows, 45 per page): item, price, stock per visit, chance to be offered.
 */
final class TradePreviewMenu extends GuildGui {
	private static final int PER_PAGE = 45;

	private final int tier;
	private int page;

	TradePreviewMenu(ServerPlayer player, Guild guild, int tier, int page) {
		super(player, 6, guild);
		this.tier = tier;
		this.page = page;
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		TradeConfig cfg = TradeModule.config();
		return Lang.get("trade.gui.preview.title", TradeHub.roman(tier), Lang.colorize(cfg.tier(tier).name));
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		TradeConfig cfg = TradeModule.config();
		List<TradeEntry> entries = cfg.tier(tier).usable();
		int pages = GuiUtil.pages(entries.size(), PER_PAGE);
		page = Math.max(0, Math.min(page, pages - 1));
		double totalWeight = 0;
		for (TradeEntry entry : entries) {
			totalWeight += Math.max(0, entry.weight);
		}
		HolderLookup.Provider registries = TradeHubMenu.registries();
		for (int i = 0; i < PER_PAGE; i++) {
			int index = page * PER_PAGE + i;
			if (index >= entries.size()) {
				break;
			}
			TradeEntry entry = entries.get(index);
			ItemStack stack = TradeHubMenu.icon(registries, entry);
			List<Component> lore = new ArrayList<>();
			Component enchantment = TradeOffers.enchantmentName(registries, entry);
			if (enchantment != null) {
				lore.add(TextUtil.lang("trade.gui.entry.enchantment", enchantment));
			}
			boolean randomUnique = entry.isUnique() && TradeEntry.RANDOM_UNIQUE.equals(entry.unique);
			if (randomUnique) {
				lore.addAll(GuiUtil.lines("unique.trade.random.lore"));
			}
			lore.add(TextUtil.lang("trade.gui.entry.price", TradeHubMenu.price(entry)));
			lore.add(Lang.get("trade.gui.entry.uses", entry.maxUses));
			lore.add(Lang.get("trade.gui.entry.chance", totalWeight <= 0 ? "0" : TradeHubMenu.percent(entry.weight / totalWeight)));
			Component name;
			if (stack.is(Items.BARRIER)) {
				name = Component.literal(entry.isUnique() ? entry.unique : entry.item);
			} else if (randomUnique) {
				name = Lang.get("unique.trade.random.name");
			} else if (entry.isUnique()) {
				name = stack.getHoverName();
			} else {
				name = TextUtil.lang("trade.gui.entry.name", entry.count, ItemUtil.name(stack.getItem()));
			}
			setSlot(i, GuiUtil.item(stack, name, lore));
		}
		if (entries.isEmpty()) {
			setSlot(22, GuiUtil.item(Items.BARRIER, Lang.get("trade.gui.preview.empty"), List.of()));
		}
		bottomBar(() -> TradeHubMenu.open(player, guild));
		pager(page, pages, target -> {
			page = target;
			render();
		});
	}
}
