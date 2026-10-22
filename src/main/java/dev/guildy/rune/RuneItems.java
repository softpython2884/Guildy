package dev.guildy.rune;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Runes on items: a runed armor piece is a vanilla item with custom_data {"guildy": {"kind": "rune", "id": ...}}, its
 * rune's attribute modifiers added to the piece's own ones (ids guildy:rune_{id}_{slot}[_n]), lore lines marked with the
 * insertion {@value #MARK} (tier, name, effects), and the enchantment glint (plus the glider component of the Dragon rune)
 * when the rune added them. {@link #strip} takes exactly that away again (grindstone), whatever the config says now.
 */
public final class RuneItems {
	public static final String KIND = "rune";
	/** Insertion text marking the lore lines written by a rune (invisible in tooltips). */
	public static final String MARK = "guildy:rune";
	private static final String ID = "id";
	/** The rune added the glint override (removed with the rune). */
	private static final String GLINT = "glint";
	/** The rune added the glider component (Dragon; removed with the rune). */
	private static final String GLIDER = "glider";
	/** Phoenix: game time when the rune is ready again. */
	private static final String READY = "ready";
	/** Phoenix: the wearer is told when the rune is ready again. */
	private static final String NOTIFY = "notify";
	private static final String MODIFIER_PREFIX = "rune_";
	/** Attributes shown in percent even when added as a value (0.1 knockback resistance = 10 %). */
	private static final Set<String> PERCENT = Set.of("minecraft:knockback_resistance", "minecraft:explosion_knockback_resistance",
			"minecraft:water_movement_efficiency", "minecraft:movement_efficiency", "minecraft:sweeping_damage_ratio",
			"minecraft:sneaking_speed");
	private static final Set<String> WARNED = new HashSet<>();

	private RuneItems() {
	}

	// ================================================================== queries

	/** Rune id of the stack, or null. */
	public static @Nullable String idOf(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		CompoundTag inner = data.copyTag().getCompoundOrEmpty(GuildyItems.ROOT);
		if (!KIND.equals(inner.getStringOr("kind", ""))) {
			return null;
		}
		String id = inner.getStringOr(ID, "");
		return id.isEmpty() ? null : id;
	}

	public static boolean has(ItemStack stack) {
		return idOf(stack) != null;
	}

	/** Current definition of the stack's rune (null for other items, unknown ids or when the runes are disabled). */
	public static RuneConfig.@Nullable RuneDef defOf(ItemStack stack) {
		String id = idOf(stack);
		RuneConfig config = RuneModule.config();
		return id == null || !config.enabled ? null : config.rune(id);
	}

	/** Power of the stack's rune, null when none (or the runes are disabled). */
	public static @Nullable RunePower powerOf(ItemStack stack) {
		RuneConfig.RuneDef def = defOf(stack);
		return def == null ? null : def.power();
	}

	/** Armor slot of a piece (its equippable slot: head, chest, legs, feet), null for anything else. */
	public static @Nullable EquipmentSlot armorSlot(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			return null;
		}
		return equippable.slot();
	}

	/**
	 * May this stack receive a rune? A single armor piece that can wear out (no pumpkin or head, no unbreakable unique), no
	 * Guildy item (unique weapons and armors, flags...) and no rune yet.
	 */
	public static boolean canReceive(ItemStack stack) {
		return !stack.isEmpty() && stack.getCount() == 1 && stack.isDamageableItem() && armorSlot(stack) != null
				&& GuildyItems.kind(stack) == null;
	}

	/** Registry id of the item ("minecraft:netherite_chestplate"). */
	public static String itemId(ItemStack stack) {
		return ItemUtil.id(stack.getItem());
	}

	// ================================================================== inscription

	/**
	 * Writes the rune on the piece (which must {@link #canReceive} it): custom data, attribute modifiers on top of the
	 * piece's own ones, lore lines, glint (and the glider of the Dragon rune) when the piece has none of its own.
	 */
	public static void inscribe(ItemStack stack, RuneConfig.RuneDef def) {
		EquipmentSlot slot = armorSlot(stack);
		if (slot == null) {
			return;
		}
		boolean glint = !stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		boolean glider = def.power() == RunePower.DRAGON && RuneModule.config().powers.dragon.glide && !stack.has(DataComponents.GLIDER);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			inner.putString("kind", KIND);
			inner.putString(ID, def.id);
			if (glint) {
				inner.putBoolean(GLINT, true);
			}
			if (glider) {
				inner.putBoolean(GLIDER, true);
			}
			tag.put(GuildyItems.ROOT, inner);
		});
		addModifiers(stack, def, slot);
		List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		lines.addAll(lore(def));
		stack.set(DataComponents.LORE, new ItemLore(lines));
		if (glint) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		if (glider) {
			stack.set(DataComponents.GLIDER, Unit.INSTANCE);
		}
	}

	private static void addModifiers(ItemStack stack, RuneConfig.RuneDef def, EquipmentSlot slot) {
		if (def.attributes.isEmpty()) {
			return;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		int index = 0;
		for (RuneConfig.AttributeDef entry : def.attributes) {
			Optional<Holder.Reference<Attribute>> attribute = attribute(entry.attributeId());
			if (attribute.isEmpty()) {
				warnOnce("attr:" + entry.attribute, "[runes] unknown attribute '" + entry.attribute + "' on '" + def.id + "' (skipped)");
				continue;
			}
			modifiers = modifiers.withModifierAdded(attribute.get(), new AttributeModifier(modifierId(def.id, slot, index++), entry.amount,
					operation(entry.operation)), group);
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
	}

	/** guildy:rune_{id}_{slot}, then _1, _2... for the next attributes of the rune (unique per worn slot). */
	public static Identifier modifierId(String runeId, EquipmentSlot slot, int index) {
		String path = MODIFIER_PREFIX + runeId + "_" + slot.getSerializedName() + (index > 0 ? "_" + index : "");
		return Identifier.fromNamespaceAndPath(Guildy.MOD_ID, path);
	}

	/** A modifier written by a rune (guildy:rune_...). */
	public static boolean isRuneModifier(Identifier id) {
		return Guildy.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(MODIFIER_PREFIX);
	}

	static Optional<Holder.Reference<Attribute>> attribute(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null ? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(parsed);
	}

	private static AttributeModifier.Operation operation(String name) {
		return switch (name) {
			case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
	}

	// ================================================================== removal

	/**
	 * Takes the rune away from the piece (in place): its custom data, its attribute modifiers, its lore lines, the glint and
	 * glider it added. Everything else (enchantments, name, durability, other lore) stays.
	 *
	 * @return the same stack
	 */
	public static ItemStack strip(ItemStack stack) {
		if (stack.isEmpty()) {
			return stack;
		}
		CompoundTag inner = GuildyItems.data(stack);
		boolean runed = KIND.equals(inner.getStringOr("kind", ""));
		if (runed) {
			boolean glint = inner.getBooleanOr(GLINT, false);
			boolean glider = inner.getBooleanOr(GLIDER, false);
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
				CompoundTag root = tag.getCompoundOrEmpty(GuildyItems.ROOT);
				for (String key : List.of("kind", ID, GLINT, GLIDER, READY, NOTIFY)) {
					root.remove(key);
				}
				if (root.isEmpty()) {
					tag.remove(GuildyItems.ROOT);
				} else {
					tag.put(GuildyItems.ROOT, root);
				}
			});
			if (glint) {
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, stack.getPrototype().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
			}
			if (glider) {
				stack.set(DataComponents.GLIDER, stack.getPrototype().get(DataComponents.GLIDER));
			}
		}
		ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers != null) {
			List<ItemAttributeModifiers.Entry> kept = new ArrayList<>();
			for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
				if (!isRuneModifier(entry.modifier().id())) {
					kept.add(entry);
				}
			}
			if (kept.size() != modifiers.modifiers().size()) {
				// A list equal to the item's own modifiers clears the patch.
				stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(kept));
			}
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			List<Component> kept = new ArrayList<>();
			for (Component line : lore.lines()) {
				if (!MARK.equals(line.getStyle().getInsertion())) {
					kept.add(line);
				}
			}
			if (kept.size() != lore.lines().size()) {
				stack.set(DataComponents.LORE, kept.isEmpty() ? stack.getPrototype().get(DataComponents.LORE) : new ItemLore(kept));
			}
		}
		return stack;
	}

	/** Anything a rune left on the stack (data, modifier or lore line)? */
	public static boolean hasRuneTraces(ItemStack stack) {
		if (has(stack)) {
			return true;
		}
		ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers != null) {
			for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
				if (isRuneModifier(entry.modifier().id())) {
					return true;
				}
			}
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (MARK.equals(line.getStyle().getInsertion())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * After a smithing upgrade (diamond → netherite): the copied attribute modifiers still hold the old item's own values;
	 * they become the new item's own values, plus whatever else the piece had (the rune's modifiers).
	 */
	public static void rebase(ItemStack result, ItemStack oldBase) {
		if (result.isEmpty() || oldBase.isEmpty() || result.getItem() == oldBase.getItem() || !hasRuneTraces(result)) {
			return;
		}
		ItemAttributeModifiers current = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (current == null) {
			return;
		}
		List<ItemAttributeModifiers.Entry> oldDefaults = oldBase.getPrototype()
				.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers();
		List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(result.getPrototype()
				.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers());
		for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
			if (!oldDefaults.contains(entry) && !entries.contains(entry)) {
				entries.add(entry);
			}
		}
		result.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
	}

	// ================================================================== lore

	/** Lore lines of a rune: tier and name, effects, story (all marked, non italic). */
	public static List<Component> lore(RuneConfig.RuneDef def) {
		RuneTier tier = def.tier();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.empty());
		String color = tier.color + (tier == RuneTier.MYTHIC ? "&l" : "");
		lines.add(Lang.get("rune.lore.header", color, def.glyph, def.name, tier.color + Lang.raw(tier.langKey())));
		for (RuneConfig.AttributeDef attribute : def.attributes) {
			lines.add(Lang.get("rune.lore.attribute", attributeText(attribute)));
		}
		RunePower power = def.power();
		if (power != null) {
			for (String line : Lang.raw(power.langKey() + ".lore", powerArgs(power)).split("\n")) {
				lines.add(Lang.get("rune.lore.power", line));
			}
		}
		for (String line : def.lore) {
			lines.add(Lang.get("rune.lore.story", line));
		}
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false).withInsertion(MARK)));
		}
		return out;
	}

	/** "+1 armure", "+5 % vitesse" (lang rune.attr.{path}, else the attribute path). */
	public static String attributeText(RuneConfig.AttributeDef attribute) {
		String id = attribute.attributeId();
		boolean percent = attribute.multiplied() || PERCENT.contains(id);
		String value = signed(percent ? attribute.amount * 100 : attribute.amount) + (percent ? " %" : "");
		Identifier parsed = Identifier.tryParse(id);
		String path = parsed == null ? id : parsed.getPath();
		String key = "rune.attr." + path;
		return Lang.has(key) ? Lang.raw(key, value) : Lang.raw("rune.attr.generic", value, path);
	}

	static Object[] powerArgs(RunePower power) {
		RuneConfig.Powers p = RuneModule.config().powers;
		return switch (power) {
			case SPARKS -> new Object[] {hearts(p.sparks.damage), number(p.sparks.cooldownSeconds)};
			case EMBER -> new Object[] {number(p.ember.chance * 100), p.ember.fireSeconds};
			case GLOW -> new Object[] {number(p.glow.radius), p.glow.seconds};
			case ECHO -> new Object[] {number(p.echo.sneakSeconds), number(p.echo.radius), p.echo.seconds};
			case FOX -> new Object[] {number(p.fox.radius)};
			case BOUNCE -> new Object[] {number(p.bounce.minFall)};
			case PHOENIX -> new Object[] {number(p.phoenix.healthRatio * 100), number(p.phoenix.radius), p.phoenix.cooldownMinutes};
			case DRAGON -> new Object[] {hearts(p.dragon.breathDamage), number(p.dragon.breathRange), number(p.dragon.breathCooldownSeconds)};
		};
	}

	/** "+1", "-0,5". */
	static String signed(double value) {
		return (value >= 0 ? "+" : "-") + number(Math.abs(value));
	}

	/** "5", "2,5" (hearts = health points / 2). */
	static String hearts(double health) {
		return number(health / 2.0);
	}

	static String number(double value) {
		return Math.abs(value - Math.rint(value)) < 1e-6 ? String.valueOf((long) Math.rint(value))
				: String.format(Locale.FRANCE, "%.1f", value);
	}

	// ================================================================== phoenix cooldown (on the item)

	/** Game time when the stack's Phoenix rune is ready again (0 = ready). */
	public static long readyAt(ItemStack stack) {
		return GuildyItems.data(stack).getLongOr(READY, 0L);
	}

	/** The rune burnt: ready again at {@code gameTime}; the wearer is told then. */
	public static void setReadyAt(ItemStack stack, long gameTime) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			inner.putLong(READY, gameTime);
			inner.putBoolean(NOTIFY, true);
			tag.put(GuildyItems.ROOT, inner);
		});
	}

	/** True once when the rune is ready again (the "ready" notice is then cleared). */
	public static boolean takeReadyNotice(ItemStack stack, long gameTime) {
		CompoundTag inner = GuildyItems.data(stack);
		if (!inner.getBooleanOr(NOTIFY, false) || inner.getLongOr(READY, 0L) > gameTime) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			root.remove(NOTIFY);
			tag.put(GuildyItems.ROOT, root);
		});
		return true;
	}

	private static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			Guildy.LOGGER.warn(message);
		}
	}
}
