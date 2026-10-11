package dev.guildy.data;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
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
	/** Layers available in the free editor (same limit as the vanilla loom). */
	public static final int MAX_LAYERS = 6;
	/** Upper bound when adopting an existing banner (commands can create more than 6 layers). */
	public static final int MAX_ADOPTED_LAYERS = 16;

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

	/** Design of a banner item (base color = item color, layers = its patterns), or null if not a banner. */
	public static @Nullable BannerDesign fromStack(ItemStack stack) {
		if (!(stack.getItem() instanceof BannerItem bannerItem)) {
			return null;
		}
		BannerDesign design = new BannerDesign();
		design.base = bannerItem.getColor().getName();
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		for (BannerPatternLayers.Layer layer : patterns.layers()) {
			layer.pattern().unwrapKey().ifPresent(key ->
					design.layers.add(new Layer(key.identifier().toString(), layer.color().getName())));
			if (design.layers.size() >= MAX_ADOPTED_LAYERS) {
				break;
			}
		}
		return design;
	}

	/** Same base color and same layers. */
	public boolean sameAs(BannerDesign other) {
		if (baseColor() != other.baseColor() || layers.size() != other.layers.size()) {
			return false;
		}
		for (int i = 0; i < layers.size(); i++) {
			Layer a = layers.get(i);
			Layer b = other.layers.get(i);
			if (!java.util.Objects.equals(a.pattern, b.pattern) || !java.util.Objects.equals(a.color, b.color)) {
				return false;
			}
		}
		return true;
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
