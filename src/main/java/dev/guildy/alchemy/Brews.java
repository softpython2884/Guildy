package dev.guildy.alchemy;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The products of alchemy.json5 as items (Minecraft side of {@link PotionKey} and {@link BrewBook}): a product's stack (a
 * vanilla potion in its bottle, exactly like a brewing stand makes it, or an elixir), the product of a stack, French
 * names for the villager statuses, the brewing book of the current config and the registry check of its ids.
 */
public final class Brews {
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");
	private static @Nullable BrewBook book;
	private static @Nullable AlchemyConfig bookFor;

	private Brews() {
	}

	/** Brewing book of the current alchemy.json5 (rebuilt after a reload). */
	public static BrewBook book() {
		AlchemyConfig config = AlchemyModule.config();
		if (book == null || bookFor != config) {
			book = config.book();
			bookFor = config;
		}
		return book;
	}

	// ------------------------------------------------------------------ items

	/** One bottle of a product (empty for an unknown potion, elixir or a disabled elixir). */
	public static ItemStack create(String product) {
		PotionKey key = PotionKey.parse(product);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		if (key.isElixir()) {
			AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(key.id());
			return def == null || !def.enabled ? ItemStack.EMPTY : Elixirs.create(def);
		}
		Holder<Potion> potion = potion(key.potionId());
		return potion == null ? ItemStack.EMPTY : PotionContents.createItemStack(bottle(key.form()), potion);
	}

	/** Water bottle (the start of every brew). */
	public static ItemStack water() {
		return create(PotionKey.WATER.text());
	}

	/**
	 * Product of a stack: an elixir ("elixir:id"), else a potion, splash or lingering potion holding a vanilla potion and no
	 * custom effect ("healing", "splash:healing"); null for anything else.
	 */
	public static @Nullable String keyOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String elixir = Elixirs.idOf(stack);
		if (elixir != null) {
			return PotionKey.Form.ELIXIR.prefix() + elixir;
		}
		PotionKey.Form form = stack.is(Items.POTION) ? PotionKey.Form.POTION : stack.is(Items.SPLASH_POTION) ? PotionKey.Form.SPLASH
				: stack.is(Items.LINGERING_POTION) ? PotionKey.Form.LINGERING : null;
		PotionContents contents = form == null ? null : stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || !contents.isPotionWithoutCustomEffects() || contents.customName().isPresent()) {
			return null;
		}
		String id = contents.potion().flatMap(Holder::unwrapKey).map(k -> k.identifier().toString()).orElse(null);
		return id == null ? null : PotionKey.canonical(form.prefix() + id);
	}

	/** A plain water bottle. */
	public static boolean isWater(ItemStack stack) {
		return PotionKey.WATER.text().equals(keyOf(stack)) && stack.getCustomName() == null;
	}

	/** Any bottle the alchemist handles: a potion of any kind or an elixir. */
	public static boolean isBrew(ItemStack stack) {
		return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
	}

	static Item bottle(PotionKey.Form form) {
		return switch (form) {
			case SPLASH -> Items.SPLASH_POTION;
			case LINGERING -> Items.LINGERING_POTION;
			default -> Items.POTION;
		};
	}

	static @Nullable Holder<Potion> potion(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.POTION.get(identifier).map(h -> (Holder<Potion>) h).orElse(null);
	}

	/** Item of a reagent id, null when unknown. */
	public static @Nullable Item item(String id) {
		return ItemUtil.item(id);
	}

	/** Colour of a product's bottle (status particles). */
	public static int color(String product) {
		ItemStack stack = create(product);
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? 0x385DC6 : contents.getColor() & 0xFFFFFF;
	}

	// ------------------------------------------------------------------ names

	/**
	 * French name of a product for the statuses and messages ("Potion de soin II", "Potion jetable de force",
	 * "Élixir arcanique"); the item's own name stays the vanilla one, translated by the client.
	 */
	public static String name(String product) {
		PotionKey key = PotionKey.parse(product);
		if (key == null) {
			return product;
		}
		if (key.isElixir()) {
			AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(key.id());
			return def == null ? key.id() : Lang.strip(Lang.colorize(def.name));
		}
		String id = key.id().contains(":") ? key.id().substring(key.id().indexOf(':') + 1) : key.id();
		String suffix = "";
		String base = id;
		if (id.startsWith("strong_")) {
			base = id.substring("strong_".length());
			suffix = Lang.raw("alchemy.potion.strong");
		} else if (id.startsWith("long_")) {
			base = id.substring("long_".length());
			suffix = Lang.raw("alchemy.potion.long");
		}
		String langKey = "alchemy.potion." + base;
		String effect = Lang.has(langKey) ? Lang.raw(langKey) : base.replace('_', ' ');
		String form = Lang.raw("alchemy.form." + key.form().name().toLowerCase(java.util.Locale.ROOT), effect);
		return Lang.strip(form + suffix);
	}

	// ------------------------------------------------------------------ registry check

	/** Warnings for the ids of alchemy.json5 the game does not know (checked once the server has started). */
	public static void warnUnknown() {
		AlchemyConfig config = AlchemyModule.config();
		Set<String> warned = new LinkedHashSet<>();
		List<String> warnings = new ArrayList<>();
		for (BrewBook.Step step : config.book().steps()) {
			for (String product : List.of(step.from(), step.to())) {
				if (!PotionKey.parse(product).isElixir() && create(product).isEmpty() && warned.add(product)) {
					warnings.add("unknown potion '" + product + "' (recipes): its steps are never brewed");
				}
			}
			if (item(step.reagent()) == null && warned.add(step.reagent())) {
				warnings.add("unknown reagent item '" + step.reagent() + "'");
			}
		}
		for (String product : config.stock.keySet()) {
			if (create(product).isEmpty() && warned.add(product)) {
				warnings.add("stock: unknown or disabled product '" + product + "'");
			}
		}
		for (AlchemyConfig.ElixirDef def : config.elixirs) {
			for (AlchemyConfig.EffectDef effect : def.effects) {
				if (Elixirs.effect(effect.effectId()) == null) {
					warnings.add("elixirs." + def.id + ": unknown effect '" + effect.effectId() + "': ignored");
				}
			}
		}
		for (String warning : warnings) {
			LOGGER.warn("[alchemy.json5] {}", warning);
		}
	}
}
