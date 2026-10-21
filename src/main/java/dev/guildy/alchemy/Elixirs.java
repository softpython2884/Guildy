package dev.guildy.alchemy;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guildy elixirs are vanilla potions (drunk like any potion): {@code potion_contents} with the elixir's vanilla effects
 * and colour, a coloured non-italic custom name, lore (story, magic bonuses and their duration), a glint for the magical
 * ones and custom_data {"guildy": {"kind": "elixir", "id": ...}}. The vanilla effects apply by themselves when drunk; the
 * magic bonus is added at the end of the drink ({@link MagicBoosts#drink}, mixin on {@code Consumable.onConsume}).
 */
public final class Elixirs {
	public static final String KIND = "elixir";

	private Elixirs() {
	}

	/** One elixir of this id (empty for an unknown id). */
	public static ItemStack create(String id) {
		AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(id);
		return def == null ? ItemStack.EMPTY : create(def);
	}

	public static ItemStack create(AlchemyConfig.ElixirDef def) {
		ItemStack stack = new ItemStack(Items.POTION);
		List<MobEffectInstance> effects = new ArrayList<>();
		for (AlchemyConfig.EffectDef effect : def.effects) {
			Holder<MobEffect> holder = effect(effect.effectId());
			if (holder != null) {
				effects.add(new MobEffectInstance(holder, effect.seconds * 20, effect.level - 1));
			}
		}
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(def.rgb()), effects, Optional.empty()));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(Lang.colorize(def.name)).withStyle(style -> style.withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(lore(def)));
		if (effects.isEmpty()) {
			// No vanilla effect: no "No effects" line, the lore tells what it does.
			stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true));
		}
		if (def.magical()) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		CompoundTag extra = new CompoundTag();
		extra.putString("id", def.id);
		GuildyItems.tag(stack, KIND, extra);
		return stack;
	}

	/** Story, magic bonuses and their duration, origin. */
	static List<Component> lore(AlchemyConfig.ElixirDef def) {
		List<Component> lines = new ArrayList<>();
		for (String line : def.lore) {
			lines.add(italicOff(Lang.get("alchemy.elixir.lore.story", line)));
		}
		MagicBoostRules.Boost boost = def.boost();
		if (boost.any()) {
			lines.add(Component.empty());
			if (boost.power() > 0) {
				lines.add(italicOff(Lang.get("alchemy.elixir.lore.power", MagicBoostRules.percent(boost.power()))));
			}
			if (boost.cooldown() > 0) {
				lines.add(italicOff(Lang.get("alchemy.elixir.lore.cooldown", MagicBoostRules.percent(boost.cooldown()))));
			}
			if (boost.channel() > 0) {
				lines.add(italicOff(Lang.get("alchemy.elixir.lore.channel", MagicBoostRules.percent(boost.channel()))));
			}
			if (boost.food() > 0) {
				lines.add(italicOff(Lang.get("alchemy.elixir.lore.food", MagicBoostRules.percent(boost.food()))));
			}
			lines.add(italicOff(Lang.get("alchemy.elixir.lore.duration", MagicBoostRules.clock(def.magicSeconds * 20))));
		}
		lines.add(Component.empty());
		lines.add(italicOff(Lang.get("alchemy.elixir.lore.footer")));
		return lines;
	}

	private static Component italicOff(Component line) {
		return Component.empty().append(line).withStyle(style -> style.withItalic(false));
	}

	/** Elixir id of a stack, or null for anything else. */
	public static @Nullable String idOf(ItemStack stack) {
		if (!GuildyItems.is(stack, KIND)) {
			return null;
		}
		String id = GuildyItems.data(stack).getStringOr("id", "");
		return id.isEmpty() ? null : id;
	}

	public static boolean isElixir(ItemStack stack) {
		return idOf(stack) != null;
	}

	static @Nullable Holder<MobEffect> effect(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.MOB_EFFECT.get(identifier).map(h -> (Holder<MobEffect>) h).orElse(null);
	}
}
