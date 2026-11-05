package dev.guildy.core;

import dev.guildy.data.BannerDesign;
import dev.guildy.data.Guild;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Special Guildy items are plain vanilla items carrying a {@code custom_data} tag {"guildy": {"kind": ..., ...}}.
 * Survival players cannot forge them, and vanilla clients render them normally.
 */
public final class GuildyItems {
	public static final String ROOT = "guildy";
	public static final String KIND_FOUNDING_FLAG = "founding_flag";
	public static final String KIND_EXTENSION_FLAG = "extension_flag";
	public static final String KIND_PLAN = "plan";
	public static final String KIND_TREASURE_MAP = "treasure_map";
	/** Work zone fanion of the villager module (banner): {"type": zone type, "guild": id, "zone": id when moving one}. */
	public static final String KIND_ZONE_FLAG = "zone_flag";
	/** Anchor flag (banner) keeping the chunks around it active: {"guild": id}. */
	public static final String KIND_ANCHOR_FLAG = "anchor_flag";

	private GuildyItems() {
	}

	/** Kind of a Guildy item, or null for normal items. */
	public static @Nullable String kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		CompoundTag root = data.copyTag().getCompoundOrEmpty(ROOT);
		String kind = root.getStringOr("kind", "");
		return kind.isEmpty() ? null : kind;
	}

	public static boolean isSpecial(ItemStack stack) {
		return kind(stack) != null;
	}

	public static boolean is(ItemStack stack, String kind) {
		return kind.equals(kind(stack));
	}

	/** The {"guildy": {...}} compound (copy), empty for normal items. */
	public static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty(ROOT);
	}

	public static ItemStack tag(ItemStack stack, String kind, CompoundTag extra) {
		CompoundTag inner = extra.copy();
		inner.putString("kind", kind);
		CompoundTag root = new CompoundTag();
		root.put(ROOT, inner);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
		return stack;
	}

	/** Founding flag bound to a guild: planting it creates the heart of the base. */
	public static ItemStack foundingFlag(Guild guild) {
		ItemStack stack = guild.banner.createBanner(GuildyServer.registryAccess());
		CompoundTag extra = new CompoundTag();
		extra.putString("guild", guild.id.toString());
		tag(stack, KIND_FOUNDING_FLAG, extra);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("item.founding_flag.lore1", guild.name));
		lore.add(Lang.get("item.founding_flag.lore2"));
		lore.add(Lang.get("item.founding_flag.lore3"));
		return ItemUtil.named(stack, Lang.get("item.founding_flag.name", guild.name), lore);
	}

	/** Extension flag: claims the chunk where it is planted (must touch the territory). Usable by any guild. */
	public static ItemStack extensionFlag(@Nullable Guild guild) {
		BannerDesign design = guild != null ? guild.banner : new BannerDesign();
		ItemStack stack = design.createBanner(GuildyServer.registryAccess());
		tag(stack, KIND_EXTENSION_FLAG, new CompoundTag());
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("item.extension_flag.lore1"));
		lore.add(Lang.get("item.extension_flag.lore2"));
		return ItemUtil.named(stack, Lang.get("item.extension_flag.name"), lore);
	}

	/** Construction plan: right-click a block of the built structure to validate it. */
	public static ItemStack plan(String structureId, Component structureName, List<Component> lore) {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag extra = new CompoundTag();
		extra.putString("structure", structureId);
		tag(stack, KIND_PLAN, extra);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return ItemUtil.named(stack, Lang.get("item.plan.name", structureName), lore);
	}

	/** Treasure map: go to (x, z) holding it to unearth a reward chest. */
	public static ItemStack treasureMap(int x, int z, int tier, UUID guildId) {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		CompoundTag extra = new CompoundTag();
		extra.putInt("x", x);
		extra.putInt("z", z);
		extra.putInt("tier", tier);
		extra.putString("guild", guildId.toString());
		tag(stack, KIND_TREASURE_MAP, extra);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("item.treasure_map.lore1", x, z));
		lore.add(Lang.get("item.treasure_map.lore2"));
		return ItemUtil.named(stack, Lang.get("item.treasure_map.name", tier), lore);
	}

	/** Decorative banner in the guild colors (normal banner, no Guildy tag). */
	public static ItemStack decorativeBanner(Guild guild) {
		ItemStack stack = guild.banner.createBanner(GuildyServer.registryAccess());
		return ItemUtil.named(stack, Lang.get("item.decor_banner.name", guild.name), List.of());
	}

	public static @Nullable UUID guildOf(ItemStack stack) {
		String id = data(stack).getStringOr("guild", "");
		if (id.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
