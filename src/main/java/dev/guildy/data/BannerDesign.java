package dev.guildy.data;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guild banner: base color + up to {@link #MAX_LAYERS} vanilla pattern layers (stored by id, resolved at use time).
 */
public final class BannerDesign {
	public static final int MAX_LAYERS = 6;

	public String base = "white";
	public List<Layer> layers = new ArrayList<>();

	public static final class Layer {
		/** Banner pattern id, e.g. "minecraft:stripe_center". */
		public String pattern;
		/** Dye color name, e.g. "red". */
		public String color;

		public Layer() {
		}

		public Layer(String pattern, String color) {
			this.pattern = pattern;
			this.color = color;
		}
	}

	public DyeColor baseColor() {
		DyeColor color = DyeColor.byName(base, DyeColor.WHITE);
		return color != null ? color : DyeColor.WHITE;
	}

	public BannerPatternLayers toLayers(RegistryAccess access) {
		HolderLookup.RegistryLookup<BannerPattern> lookup = access.lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
		for (Layer layer : layers) {
			if (layer == null || layer.pattern == null) {
				continue;
			}
			try {
				ResourceKey<BannerPattern> key = ResourceKey.create(Registries.BANNER_PATTERN, Identifier.parse(layer.pattern));
				Optional<? extends Holder<BannerPattern>> holder = lookup.get(key);
				DyeColor color = DyeColor.byName(layer.color, DyeColor.BLACK);
				if (holder.isPresent() && color != null) {
					builder.add(holder.get(), color);
				}
			} catch (Exception ignored) {
				// unknown pattern id: skipped
			}
		}
		return builder.build();
	}

	/** A banner item stack carrying this design (no custom name). */
	public ItemStack createBanner(RegistryAccess access) {
		ItemStack stack = new ItemStack(Items.BANNER.pick(baseColor()));
		stack.set(DataComponents.BANNER_PATTERNS, toLayers(access));
		return stack;
	}

	public BannerDesign copy() {
		BannerDesign copy = new BannerDesign();
		copy.base = base;
		for (Layer layer : layers) {
			copy.layers.add(new Layer(layer.pattern, layer.color));
		}
		return copy;
	}
}
