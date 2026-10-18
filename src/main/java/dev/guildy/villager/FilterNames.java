package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Names of the filter chests ({@link ChestFilters#label}): as a text component (container titles, menus, chat: item
 * names are translated by the client, in French for French players) and as plain text (statuses: the French family
 * names of lang.json, else the names the server knows).
 */
final class FilterNames {
	private FilterNames() {
	}

	/** Name with the item names translated client-side ("Diorite", "Blé et graines", "Granite et Andésite"). */
	static MutableComponent component(ChestFilter filter) {
		MutableComponent name = Component.empty();
		for (ChestFilters.LabelPart part : ChestFilters.label(filter, Lang::has, FilterNames::isItem)) {
			switch (part.kind()) {
				case LANG -> name.append(Component.literal(Lang.raw(part.value(), part.number())));
				case ITEM -> {
					Item item = ItemUtil.item(part.value());
					name.append(item == null ? Component.literal(part.value()) : ItemUtil.name(item));
				}
				case TEXT -> name.append(Component.literal(part.value()));
			}
		}
		return name;
	}

	/**
	 * The name in a colour (legacy '&' codes of a text before a component do not colour the component: the style is
	 * set on it).
	 */
	static MutableComponent styled(ChestFilter filter, ChatFormatting... formats) {
		return component(filter).withStyle(formats);
	}

	/** Plain-text name (statuses and other texts that cannot be translated by the client). */
	static String text(ChestFilter filter) {
		StringBuilder name = new StringBuilder();
		for (ChestFilters.LabelPart part : ChestFilters.label(filter, Lang::has, FilterNames::isItem)) {
			switch (part.kind()) {
				case LANG -> name.append(Lang.raw(part.value(), part.number()));
				case ITEM -> {
					Item item = ItemUtil.item(part.value());
					name.append(item == null ? part.value() : CraftNames.item(new ItemStack(item)));
				}
				case TEXT -> name.append(part.value());
			}
		}
		return name.toString();
	}

	/** Menu icon of a filter: its icon item, else its first item, else a hopper. */
	static Item icon(ChestFilter filter) {
		Item item = filter.icon.isEmpty() ? null : ItemUtil.item(filter.icon);
		if (item == null && !filter.items.isEmpty()) {
			item = ItemUtil.item(filter.items.getFirst());
		}
		if (item == null && filter.mode != ChestFilter.Mode.ITEMS) {
			item = ItemUtil.item("minecraft:" + filter.family);
		}
		return item == null ? Items.HOPPER : item;
	}

	/** "Famille (matière) : diorite", "Famille (type) : wool", "Objets : 3". */
	static String description(ChestFilter filter) {
		return switch (filter.mode) {
			case ITEMS -> Lang.raw("villager.storage.filter.mode.items", filter.items.size());
			case MATERIAL -> Lang.raw("villager.storage.filter.mode.material", filter.family);
			case TYPE -> Lang.raw("villager.storage.filter.mode.type", filter.family);
		};
	}

	private static boolean isItem(String id) {
		return ItemUtil.item(id) != null;
	}
}
