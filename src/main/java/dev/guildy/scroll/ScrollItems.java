package dev.guildy.scroll;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.ScrollDrops;
import dev.guildy.unique.UniqueRarity;
import dev.guildy.unique.UniqueVisuals;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Scrolls are vanilla items with components: custom_data {"guildy": {"kind": "scroll", "id": ..., "sig": ...}}, colored
 * item name, lore (rarity, story, spell with its values, channel time and cooldown), glint, max stack size, a consumable
 * component (the vanilla channel animation of the right click, silent, never eaten: the cast is decided by the mod), use
 * effects (movement while channeling), a use_cooldown with the scroll's shared group (the client shows the cooldown) and,
 * only when a resource pack is guaranteed, an item_model.
 *
 * <p>A stack carrying only the custom data (a loot table entry, an old scroll after a config change) is rebuilt as soon as
 * it is seen in a player's inventory: {@code sig} records what the stack was built from.
 */
public final class ScrollItems {
	public static final String KIND = ScrollDrops.KIND;
	private static final String SIGNATURE = "sig";
	private static final Holder<SoundEvent> SILENT = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
	private static final Map<String, Integer> SIGNATURES = new HashMap<>();

	private ScrollItems() {
	}

	// ================================================================== creation

	/** {@code count} scrolls of a definition (empty when the base item is unknown). */
	public static ItemStack create(ScrollConfig.ScrollDef def, int count) {
		Item item = ItemUtil.item(def.item);
		if (item == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		CompoundTag extra = new CompoundTag();
		extra.putString("id", def.id);
		extra.putInt(SIGNATURE, signature(def));
		GuildyItems.tag(stack, KIND, extra);
		stack.set(DataComponents.MAX_STACK_SIZE, def.maxStack);
		stack.setCount(Math.max(1, Math.min(def.maxStack, count)));
		stack.set(DataComponents.ITEM_NAME, name(def));
		stack.set(DataComponents.RARITY, def.rarity() == UniqueRarity.RARE ? Rarity.RARE : Rarity.EPIC);
		stack.set(DataComponents.LORE, new ItemLore(lore(def)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, def.glint);
		stack.set(DataComponents.CONSUMABLE, Consumable.builder()
				.consumeSeconds((float) def.channelSeconds)
				.animation(animation(def.animation))
				.sound(SILENT)
				.hasConsumeParticles(false)
				.build());
		stack.set(DataComponents.USE_EFFECTS, new UseEffects(false, false, (float) ScrollModule.config().casting.moveSpeed));
		stack.set(DataComponents.USE_COOLDOWN, new UseCooldown((float) def.cooldownSeconds, Optional.of(cooldownGroup(def))));
		if (stack.getPrototype().has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
			// Base banner patterns: no pattern line in the tooltip, not usable in a loom.
			stack.remove(DataComponents.PROVIDES_BANNER_PATTERNS);
		}
		if (!def.model.isEmpty() && UniqueVisuals.enabled()) {
			stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Guildy.MOD_ID, def.model));
		}
		return stack;
	}

	private static ItemUseAnimation animation(String name) {
		try {
			return ItemUseAnimation.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ItemUseAnimation.TOOT_HORN;
		}
	}

	/** Colored, non-italic item name ("&6&lPluie de météores"); names starting with '&' keep their own colors. */
	public static Component name(ScrollConfig.ScrollDef def) {
		String text = def.name.startsWith("&") ? def.name : def.rarity().namePrefix() + def.name;
		return Component.literal(Lang.colorize(text)).withStyle(style -> style.withItalic(false));
	}

	/** Header, story, spell (name and description with the scroll's values), channel time and cooldown. */
	public static List<Component> lore(ScrollConfig.ScrollDef def) {
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("scroll.item.header", rarityName(def.rarity())));
		if (!def.lore.isEmpty()) {
			lines.add(Component.empty());
			for (String line : def.lore) {
				lines.add(Lang.get("scroll.item.story", line));
			}
		}
		Spell spell = def.spell();
		lines.add(Component.empty());
		lines.add(Lang.get("scroll.item.spell", Lang.raw(spell.langKey() + ".name")));
		for (String line : spellText(def).split("\n")) {
			lines.add(Lang.get("scroll.item.spell_line", line));
		}
		lines.add(Component.empty());
		lines.add(Lang.get("scroll.item.cast", Spell.number(def.channelSeconds), Spell.number(def.cooldownSeconds)));
		lines.add(Lang.get("scroll.item.group", groupName(def.cooldownGroup)));
		lines.add(Lang.get("scroll.item.single_use"));
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false)));
		}
		return out;
	}

	/** The spell description with its {param} placeholders replaced by the scroll's values. */
	static String spellText(ScrollConfig.ScrollDef def) {
		return spellText(def, 1.0);
	}

	/** The spell description with the values of a cast of this power (grimoire). */
	static String spellText(ScrollConfig.ScrollDef def, double power) {
		String text = Lang.raw(def.spell().langKey() + ".lore");
		for (Spell.Param param : def.spell().params()) {
			text = text.replace("{" + param.name() + "}", param.format(param.powered(def.param(param.name()), power)));
		}
		return text;
	}

	/** "&6Légendaire". */
	static String rarityName(UniqueRarity rarity) {
		return rarity.color + Lang.raw(rarity.langKey());
	}

	/** French name of a cooldown group ("Attaque"), the id when it has none. */
	static String groupName(String group) {
		String key = "scroll.group." + group;
		return Lang.has(key) ? Lang.raw(key) : group;
	}

	/** Cooldown group shared by the scrolls of a group: guildy:scroll/{group}. */
	public static Identifier cooldownGroup(ScrollConfig.ScrollDef def) {
		return Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "scroll/" + def.cooldownGroup);
	}

	// ================================================================== queries

	/** Scroll id of the stack, or null for other items. */
	public static @Nullable String idOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA) || !GuildyItems.is(stack, KIND)) {
			return null;
		}
		String id = GuildyItems.data(stack).getStringOr("id", "");
		return id.isEmpty() ? null : id;
	}

	public static boolean isScroll(ItemStack stack) {
		return idOf(stack) != null;
	}

	/** Current definition of the stack's scroll id (null for other items or ids removed from the config). */
	public static ScrollConfig.@Nullable ScrollDef defOf(ItemStack stack) {
		String id = idOf(stack);
		return id == null ? null : ScrollModule.config().scroll(id);
	}

	// ================================================================== refresh

	/**
	 * What a stack built from this definition records: the definition, the shared settings and texts it shows, the model
	 * mode (cached until the next reload).
	 */
	static int signature(ScrollConfig.ScrollDef def) {
		return SIGNATURES.computeIfAbsent(def.id, id -> Objects.hash(def.fingerprint(), UniqueVisuals.enabled(),
				ScrollModule.config().casting.moveSpeed, String.join("|", Lang.raw("scroll.item.header", ""), Lang.raw("scroll.item.cast", "", ""),
						Lang.raw("scroll.item.single_use"), Lang.raw(def.spell().langKey() + ".name"), Lang.raw(def.spell().langKey() + ".lore"),
						groupName(def.cooldownGroup))));
	}

	/** Config or lang reloaded: stacks are compared to fresh signatures. */
	static void clearSignatures() {
		SIGNATURES.clear();
		GrimoireItems.clearSignature();
	}

	/**
	 * The rebuilt stack (same count) when {@code stack} is a scroll built from another definition, signature or base item
	 * (custom_data only from a loot table, config changed), else null.
	 */
	static @Nullable ItemStack refreshed(ItemStack stack) {
		ScrollConfig.ScrollDef def = defOf(stack);
		if (def == null) {
			return null;
		}
		int sig = GuildyItems.data(stack).getIntOr(SIGNATURE, 0);
		Item item = ItemUtil.item(def.item);
		if (sig == signature(def) && (item == null || stack.is(item))) {
			return null;
		}
		ItemStack fresh = create(def, stack.getCount());
		return fresh.isEmpty() ? null : fresh;
	}
}
