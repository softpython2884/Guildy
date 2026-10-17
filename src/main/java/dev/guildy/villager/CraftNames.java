package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * French names of equipment and enchantments for the blacksmith / alchemist statuses (statuses are plain text and
 * the server only knows the English names): lang keys {@code villager.craft.item/piece/material/enchant.*}, then the
 * vanilla name as a fallback.
 */
final class CraftNames {
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private CraftNames() {
	}

	/** Name of an item: the name a player gave it, else its French name ("Plastron en fer"), else the vanilla name. */
	static String item(ItemStack stack) {
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		if (custom != null && !custom.getString().isBlank()) {
			return custom.getString();
		}
		String id = ItemUtil.id(stack.getItem());
		String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : null;
		if (path != null) {
			if (Lang.has("villager.craft.item." + path)) {
				return Lang.raw("villager.craft.item." + path);
			}
			int cut = path.lastIndexOf('_');
			if (cut > 0) {
				String material = path.substring(0, cut);
				String piece = path.substring(cut + 1);
				if (Lang.has("villager.craft.piece." + piece) && Lang.has("villager.craft.material." + material)) {
					return Lang.raw("villager.craft.piece." + piece) + " " + Lang.raw("villager.craft.material." + material);
				}
			}
		}
		return stack.getHoverName().getString();
	}

	/** "Tranchant IV" (no numeral for an enchantment with a single level, like vanilla). */
	static String enchantment(Holder<Enchantment> holder, int level) {
		Optional<ResourceKey<Enchantment>> key = holder.unwrapKey();
		String base = key.map(k -> baseName(k.identifier())).orElse(null);
		if (base == null) {
			return Enchantment.getFullname(holder, level).getString();
		}
		return holder.value().getMaxLevel() <= 1 && level <= 1 ? base : base + " " + roman(level);
	}

	/** Same from a saved id; the registry tells whether the enchantment has a single level. */
	static String enchantment(String id, int level) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return id;
		}
		Holder<Enchantment> holder = holder(identifier);
		if (holder != null) {
			return enchantment(holder, level);
		}
		String base = baseName(identifier);
		return (base == null ? id : base) + (level > 1 ? " " + roman(level) : "");
	}

	/** Enchantments of a book, comma separated. */
	static String book(ItemEnchantments enchantments) {
		List<String> parts = new ArrayList<>();
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			parts.add(enchantment(holder, enchantments.getLevel(holder)));
		}
		return String.join(", ", parts);
	}

	/** Saved "id=level;id=level" list, comma separated names. */
	static String saved(String list) {
		List<String> parts = new ArrayList<>();
		for (String entry : list.split(";")) {
			int eq = entry.lastIndexOf('=');
			if (eq <= 0) {
				continue;
			}
			int level;
			try {
				level = Integer.parseInt(entry.substring(eq + 1).trim());
			} catch (NumberFormatException e) {
				continue;
			}
			parts.add(enchantment(entry.substring(0, eq).trim(), level));
		}
		return String.join(", ", parts);
	}

	static String roman(int level) {
		return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
	}

	private static @Nullable String baseName(Identifier id) {
		String key = "villager.craft.enchant." + id.getPath();
		return "minecraft".equals(id.getNamespace()) && Lang.has(key) ? Lang.raw(key) : null;
	}

	private static @Nullable Holder<Enchantment> holder(Identifier id) {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return null;
		}
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, id))
				.map(reference -> (Holder<Enchantment>) reference).orElse(null);
	}
}
