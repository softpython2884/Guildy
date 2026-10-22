package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Unique items are vanilla items with components: custom_data {"guildy": {"kind": "unique", "id": ...}}, colored item
 * name, lore (rarity, story, power), enchantments (vanilla + guildy), extra attribute modifiers, glint, unbreakable,
 * dyed color, a cooldown group for right-click powers and, only when a resource pack is guaranteed, an item_model (and,
 * for the armors, an equippable drawn with their own equipment asset).
 */
public final class UniqueItems {
	public static final String KIND = "unique";
	/** custom_data key of the pending server announcement (value = drop source). */
	private static final String ANNOUNCE = "announce";
	/** custom_data key of the registry serial of the copy. */
	private static final String SERIAL = "serial";
	/** custom_data key of the source of an offered (not yet registered) stack. */
	private static final String PENDING = "pending";
	/** custom_data key of a set piece: number of lore lines before its set section (rewritten for what the holder wears). */
	private static final String LORE_BASE = "lore_base";
	private static final Set<String> WARNED = new HashSet<>();

	private UniqueItems() {
	}

	// ================================================================== creation

	/** Builds the item of a definition (empty stack when the base item is unknown). */
	public static ItemStack create(UniqueConfig.UniqueDef def, HolderLookup.Provider registries) {
		Item item = ItemUtil.item(def.item);
		if (item == null) {
			warnOnce("item:" + def.item, "[uniques] unknown base item '" + def.item + "' for '" + def.id + "'");
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		CompoundTag extra = new CompoundTag();
		extra.putString("id", def.id);
		GuildyItems.tag(stack, KIND, extra);
		stack.set(DataComponents.ITEM_NAME, name(def));
		stack.set(DataComponents.RARITY, def.rarity() == UniqueRarity.RARE ? Rarity.RARE : Rarity.EPIC);
		List<Component> lines = lore(def);
		UniqueConfig.SetDef set = setOf(def);
		if (set != null) {
			// The set section follows the item's own lore: it is rewritten for what the holder wears (see syncSetLore).
			int base = lines.size();
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
				CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
				inner.putInt(LORE_BASE, base);
				tag.put(GuildyItems.ROOT, inner);
			});
			lines.addAll(setLore(def, set, null));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
		applyEnchantments(stack, def, registries);
		applyAttributes(stack, def);
		if (def.glint) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		if (def.unbreakable) {
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		if (def.color >= 0) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(def.color));
		}
		if (def.banner != null) {
			applyBanner(stack, def, registries);
		}
		UniquePower power = def.power();
		if (power != null && power.active) {
			// Own cooldown group: the vanilla cooldown overlay shows the power recharge on this item only.
			float seconds = (float) Math.max(0.05, UniqueModule.config().cooldownSeconds(power));
			stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(seconds, Optional.of(cooldownGroup(def.id))));
		} else if (set != null) {
			// The set's cooldown group: the recharge of its power shows on every piece (equipping by right click: one tick).
			stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(0.05F, Optional.of(setCooldownGroup(set.id))));
		}
		syncVisuals(stack, def);
		return stack;
	}

	/** Set of a definition (null when none or unknown). */
	static UniqueConfig.@Nullable SetDef setOf(UniqueConfig.UniqueDef def) {
		return def.set == null || def.set.isEmpty() ? null : UniqueModule.config().set(def.set);
	}

	/** Cooldown group of a set's power: guildy:set/{id} (shown on the pieces that carry it). */
	public static Identifier setCooldownGroup(String setId) {
		return Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "set/" + setId);
	}

	/**
	 * Set section of a piece's lore: the set and its pieces (worn ones ticked), then each bonus with its attributes, power
	 * and recharge (reached ones lit). {@code worn} = unique ids worn by the holder, null when the piece is not carried
	 * (neutral look).
	 */
	public static List<Component> setLore(UniqueConfig.UniqueDef def, UniqueConfig.SetDef set, @Nullable Set<String> worn) {
		UniqueConfig cfg = UniqueModule.config();
		List<String> pieces = cfg.piecesOf(set.id);
		int count = 0;
		if (worn != null) {
			for (String piece : pieces) {
				if (worn.contains(piece)) {
					count++;
				}
			}
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.empty());
		String setName = Lang.colorize(set.name);
		lines.add(worn == null ? Lang.get("unique.set.header", setName, pieces.size()) : Lang.get("unique.set.header_worn", setName, count, pieces.size()));
		for (String piece : pieces) {
			UniqueConfig.UniqueDef other = cfg.item(piece);
			String name = other == null ? piece : Lang.strip(Lang.colorize(other.name));
			String key = worn == null ? "unique.set.piece" : worn.contains(piece) ? "unique.set.piece.worn" : "unique.set.piece.missing";
			lines.add(Lang.get(key, name));
		}
		for (UniqueConfig.SetBonus bonus : set.bonuses()) {
			String state = worn == null ? "neutral" : count >= bonus.pieces ? "on" : "off";
			lines.add(Lang.get("unique.set.bonus." + state, bonus.pieces, bonus.name));
			for (String line : bonusLines(bonus)) {
				lines.add(Lang.get("unique.set.line." + state, line));
			}
		}
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false)));
		}
		return out;
	}

	/** Description of a set bonus: one line per attribute ("+2 Robustesse"), its power, its recharge. */
	static List<String> bonusLines(UniqueConfig.SetBonus bonus) {
		List<String> lines = new ArrayList<>();
		for (UniqueConfig.AttributeEntry attribute : bonus.attributes) {
			lines.add(attributeText(attribute));
		}
		SetPower power = bonus.power();
		if (power != null) {
			lines.addAll(List.of(Lang.raw(power.langKey() + ".lore", setPowerArgs(power)).split("\n")));
			double cooldown = UniqueModule.config().cooldownSeconds(power);
			if (cooldown >= 1 && power != SetPower.SOUL_PYRE) {
				lines.add(Lang.raw("unique.set.cooldown", number(cooldown)));
			}
		}
		return lines;
	}

	/** "+2 Robustesse", "+10 % Vitesse", "-50 % Durée de combustion" (French attribute names of lang.json). */
	static String attributeText(UniqueConfig.AttributeEntry attribute) {
		String path = attribute.attribute.contains(":") ? attribute.attribute.substring(attribute.attribute.indexOf(':') + 1) : attribute.attribute;
		String key = "unique.attribute." + path;
		String name = Lang.has(key) ? Lang.raw(key) : path;
		boolean percent = !"add_value".equals(attribute.operation);
		double shown = percent ? attribute.amount * 100 : "knockback_resistance".equals(path) ? attribute.amount * 10 : attribute.amount;
		String sign = shown < 0 ? "-" : "+";
		return Lang.raw(percent ? "unique.attribute.percent" : "unique.attribute.value", sign + number(Math.abs(shown)), name);
	}

	private static Object[] setPowerArgs(SetPower power) {
		UniqueConfig.Powers p = UniqueModule.config().powers;
		return switch (power) {
			case WINTER_HEART -> new Object[] {number(p.winterHeart.healthThreshold * 100), number(p.winterHeart.radius),
					hearts(p.winterHeart.damage), roman(p.winterHeart.slowLevel), p.winterHeart.freezeSeconds, roman(p.winterHeart.resistanceLevel),
					p.winterHeart.resistanceSeconds};
			case JAGUAR_AMBUSH -> new Object[] {number(p.jaguarAmbush.camouflageSeconds), hearts(p.jaguarAmbush.damage),
					number(p.jaguarAmbush.radius), hearts(p.jaguarAmbush.clawDamage)};
			case SOUL_PYRE -> new Object[] {number(p.soulPyre.pulseSeconds), number(p.soulPyre.radius), hearts(p.soulPyre.damage),
					number(p.soulPyre.healRatio * 100)};
			default -> new Object[0];
		};
	}

	/**
	 * Rewrites the set section of a piece's lore for what its holder wears ({@code worn} = unique ids in his armor slots, null
	 * when the piece is not carried: neutral look). The lines before it (the item's own lore) are kept.
	 *
	 * @return true when the stack changed
	 */
	public static boolean syncSetLore(ItemStack stack, UniqueConfig.@Nullable UniqueDef def, @Nullable Set<String> worn) {
		UniqueConfig.SetDef set = def == null ? null : setOf(def);
		int base = GuildyItems.data(stack).getIntOr(LORE_BASE, -1);
		ItemLore current = stack.get(DataComponents.LORE);
		if (set == null || base < 0 || current == null || base > current.lines().size()) {
			return false;
		}
		List<Component> lines = new ArrayList<>(current.lines().subList(0, base));
		lines.addAll(setLore(def, set, worn));
		if (lines.equals(current.lines())) {
			return false;
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return true;
	}

	/** Colored, non-italic item name ("&6&lFoudrelame"); names starting with '&' keep their own colors. */
	public static Component name(UniqueConfig.UniqueDef def) {
		String text = def.name.startsWith("&") ? def.name : def.rarity().namePrefix() + def.name;
		return Component.literal(Lang.colorize(text)).withStyle(style -> style.withItalic(false));
	}

	/** Rarity line, story, power description. */
	public static List<Component> lore(UniqueConfig.UniqueDef def) {
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("unique.item.header", rarityName(def.rarity())));
		if (!def.lore.isEmpty()) {
			lines.add(Component.empty());
			for (String line : def.lore) {
				lines.add(Lang.get("unique.item.story", line));
			}
		}
		UniquePower power = def.power();
		if (power != null) {
			lines.add(Component.empty());
			lines.add(Lang.get("unique.item.power", Lang.raw(power.langKey() + ".name")));
			for (String line : Lang.raw(power.langKey() + ".lore", powerArgs(power)).split("\n")) {
				lines.add(Lang.get("unique.item.power_line", line));
			}
			double cooldown = UniqueModule.config().cooldownSeconds(power);
			if (cooldown >= 1 && power != UniquePower.DOUBLE_JUMP) {
				lines.add(Lang.get("unique.item.cooldown", number(cooldown)));
			}
		}
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false)));
		}
		return out;
	}

	/** "&6Légendaire" (colored French rarity name). */
	public static String rarityName(UniqueRarity rarity) {
		return rarity.color + Lang.raw(rarity.langKey());
	}

	private static Object[] powerArgs(UniquePower power) {
		UniqueConfig.Powers p = UniqueModule.config().powers;
		return switch (power) {
			case STORM_CALL -> new Object[] {p.stormCall.maxTargets, hearts(p.stormCall.damage)};
			case FROST_NOVA -> new Object[] {number(p.frostNova.radius), roman(p.frostNova.slowLevel), p.frostNova.slowSeconds,
					hearts(p.frostNova.damage)};
			case BLINK_STRIKE -> new Object[] {number(p.blinkStrike.range), hearts(p.blinkStrike.damage)};
			case TREE_FELLER -> new Object[] {p.treeFeller.maxLogs};
			case VEIN_MINER -> new Object[] {p.veinMiner.maxBlocks};
			case SHOCKWAVE -> new Object[] {number(p.shockwave.radius), hearts(p.shockwave.damage)};
			case PHOENIX_ARROWS -> new Object[] {number(p.phoenixArrows.radius), hearts(p.phoenixArrows.damage)};
			case TIDE_CALL -> new Object[] {hearts(p.tideCall.damage), p.tideCall.chainTargets};
			case SOUL_HARVEST -> new Object[] {hearts(p.soulHarvest.heal)};
			case JAGUAR_POUNCE -> new Object[] {number(p.jaguarPounce.radius), hearts(p.jaguarPounce.damage), roman(p.jaguarPounce.slowLevel),
					p.jaguarPounce.slowSeconds};
			case FROST_CROWN -> new Object[] {roman(p.frostCrown.slowLevel), p.frostCrown.freezeSeconds, hearts(p.frostCrown.damage)};
			case SOUL_AEGIS -> new Object[] {number(p.soulAegis.reflectRatio * 100), hearts(p.soulAegis.reflectMin), hearts(p.soulAegis.heal)};
			case SOLAR_LEAP -> new Object[] {number(p.solarLeap.radius), hearts(p.solarLeap.damage), number(p.solarLeap.undeadMultiplier)};
			case PACT_SEAL -> new Object[] {p.pactSeal.seals, hearts(p.pactSeal.damage), number(p.pactSeal.spreadRadius)};
			case INFERNAL_RIFT -> new Object[] {p.infernalRift.length, hearts(p.infernalRift.damage)};
			case JADE_GALE -> new Object[] {number(p.jadeGale.healthThreshold * 100), number(p.jadeGale.radius)};
			default -> new Object[0];
		};
	}

	private static void applyEnchantments(ItemStack stack, UniqueConfig.UniqueDef def, HolderLookup.Provider registries) {
		if (def.enchantments.isEmpty()) {
			return;
		}
		HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		EnchantmentHelper.updateEnchantments(stack, mutable -> {
			for (Map.Entry<String, Integer> entry : def.enchantments.entrySet()) {
				Identifier id = Identifier.tryParse(entry.getKey());
				Optional<Holder.Reference<Enchantment>> holder = id == null ? Optional.empty()
						: lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id));
				if (holder.isEmpty()) {
					warnOnce("ench:" + entry.getKey(), "[uniques] unknown enchantment '" + entry.getKey() + "' on '" + def.id + "' (skipped)");
					continue;
				}
				mutable.set(holder.get(), entry.getValue());
			}
		});
	}

	/** Shield look: base color + vanilla banner patterns (drawn by vanilla clients, with or without the resource pack). */
	private static void applyBanner(ItemStack stack, UniqueConfig.UniqueDef def, HolderLookup.Provider registries) {
		UniqueConfig.Banner banner = def.banner;
		DyeColor base = banner == null ? null : DyeColor.byName(banner.base, null);
		if (base == null) {
			return;
		}
		stack.set(DataComponents.BASE_COLOR, base);
		HolderLookup.RegistryLookup<BannerPattern> patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
		for (UniqueConfig.BannerLayer layer : banner.patterns) {
			Identifier id = Identifier.tryParse(layer.pattern);
			Optional<Holder.Reference<BannerPattern>> pattern = id == null ? Optional.empty()
					: patterns.get(ResourceKey.create(Registries.BANNER_PATTERN, id));
			DyeColor color = DyeColor.byName(layer.color, null);
			if (pattern.isEmpty() || color == null) {
				warnOnce("banner:" + layer.pattern, "[uniques] unknown banner pattern '" + layer.pattern + "' on '" + def.id + "' (skipped)");
				continue;
			}
			layers.add(pattern.get(), color);
		}
		stack.set(DataComponents.BANNER_PATTERNS, layers.build());
	}

	private static void applyAttributes(ItemStack stack, UniqueConfig.UniqueDef def) {
		if (def.attributes.isEmpty()) {
			return;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		int index = 0;
		for (UniqueConfig.AttributeEntry entry : def.attributes) {
			Identifier id = Identifier.tryParse(entry.attribute);
			Optional<Holder.Reference<Attribute>> attribute = id == null ? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(id);
			if (attribute.isEmpty()) {
				warnOnce("attr:" + entry.attribute, "[uniques] unknown attribute '" + entry.attribute + "' on '" + def.id + "' (skipped)");
				continue;
			}
			Identifier modifierId = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "unique/" + def.id + "/" + index++);
			modifiers = modifiers.withModifierAdded(attribute.get(), new AttributeModifier(modifierId, entry.amount, operation(entry.operation)),
					slot(entry.slot));
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
	}

	static AttributeModifier.Operation operation(String name) {
		return switch (name) {
			case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
	}

	private static EquipmentSlotGroup slot(String name) {
		for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
			if (group.getSerializedName().equals(name)) {
				return group;
			}
		}
		return EquipmentSlotGroup.MAINHAND;
	}

	/** Cooldown group of the right-click power of an item: guildy:unique/{id}. */
	public static Identifier cooldownGroup(String id) {
		return Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "unique/" + id);
	}

	// ================================================================== queries

	/** Unique id of the stack, or null for other items. */
	public static @Nullable String idOf(ItemStack stack) {
		if (stack.isEmpty() || !GuildyItems.is(stack, KIND)) {
			return null;
		}
		String id = GuildyItems.data(stack).getStringOr("id", "");
		return id.isEmpty() ? null : id;
	}

	public static boolean isUnique(ItemStack stack) {
		return idOf(stack) != null;
	}

	/** Current definition of the stack's unique id (null for other items or ids removed from the config). */
	public static UniqueConfig.@Nullable UniqueDef defOf(ItemStack stack) {
		String id = idOf(stack);
		return id == null ? null : UniqueModule.config().item(id);
	}

	/** Power of the stack (null when none, or when powers are disabled). */
	public static @Nullable UniquePower powerOf(ItemStack stack) {
		if (!UniqueModule.config().powersEnabled) {
			return null;
		}
		UniqueConfig.UniqueDef def = defOf(stack);
		return def == null ? null : def.power();
	}

	// ================================================================== announcement marker

	/** Marks a freshly rolled item: the first player who gets it is announced to the server. */
	public static void markAnnounce(ItemStack stack, String source) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			inner.putString(ANNOUNCE, source);
			tag.put(GuildyItems.ROOT, inner);
		});
	}

	/** Removes and returns the pending announcement source of the stack (null when none). */
	public static @Nullable String takeAnnounce(ItemStack stack) {
		String source = GuildyItems.data(stack).getStringOr(ANNOUNCE, "");
		if (source.isEmpty()) {
			return null;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			inner.remove(ANNOUNCE);
			tag.put(GuildyItems.ROOT, inner);
		});
		return source;
	}

	// ================================================================== registry markers

	/** Serial of the copy (custom_data guildy.serial), 0 for an unregistered stack. */
	public static long serialOf(ItemStack stack) {
		return stack.isEmpty() ? 0 : GuildyItems.data(stack).getLongOr(SERIAL, 0L);
	}

	/** Writes the serial of the registered copy into the stack (removes a pending merchant source). */
	public static void setSerial(ItemStack stack, long serial) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			inner.putLong(SERIAL, serial);
			inner.remove(PENDING);
			tag.put(GuildyItems.ROOT, inner);
		});
	}

	/** Marks an unregistered stack (merchant offer): registered with this source once someone carries it. */
	public static void markPending(ItemStack stack, String source) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag inner = tag.getCompoundOrEmpty(GuildyItems.ROOT);
			inner.putString(PENDING, source);
			tag.put(GuildyItems.ROOT, inner);
		});
	}

	/** Source waiting on an unregistered stack, or "" (legacy item). */
	public static String pendingSource(ItemStack stack) {
		return GuildyItems.data(stack).getStringOr(PENDING, "");
	}

	/**
	 * Display copy for menus: the weapon's look (item, name, enchantment glint, model) without its Guildy data, so that it
	 * never counts as a copy, and without its lore (the menu writes its own).
	 */
	public static ItemStack display(UniqueConfig.UniqueDef def, HolderLookup.Provider registries) {
		ItemStack stack = create(def, registries);
		if (!stack.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
			stack.remove(DataComponents.LORE);
			stack.remove(DataComponents.USE_COOLDOWN);
		}
		return stack;
	}

	// ================================================================== visuals

	/**
	 * Custom model (and worn look of the armors) only when a resource pack is guaranteed (Polymer or forced in the config);
	 * otherwise the vanilla model and look of the base item. Also reverts items created while models were enabled.
	 *
	 * @return true when the stack changed
	 */
	public static boolean syncVisuals(ItemStack stack, UniqueConfig.@Nullable UniqueDef def) {
		return syncModel(stack, def) | syncEquipment(stack, def);
	}

	private static boolean syncModel(ItemStack stack, UniqueConfig.@Nullable UniqueDef def) {
		Identifier wanted = def != null && !def.model.isEmpty() && UniqueVisuals.enabled()
				? Identifier.fromNamespaceAndPath(Guildy.MOD_ID, def.model) : null;
		Identifier current = stack.get(DataComponents.ITEM_MODEL);
		if (wanted != null) {
			if (!wanted.equals(current)) {
				stack.set(DataComponents.ITEM_MODEL, wanted);
				return true;
			}
			return false;
		}
		if (current != null && Guildy.MOD_ID.equals(current.getNamespace())) {
			// Back to the base item's own model (a value equal to the prototype clears the patch).
			stack.set(DataComponents.ITEM_MODEL, stack.getPrototype().get(DataComponents.ITEM_MODEL));
			return true;
		}
		return false;
	}

	/**
	 * Worn look of the unique armors: the base item's equippable with asset_id guildy:{model} when the pack ships that
	 * equipment asset for the base item's slot (slot, sounds and every other field stay the base item's); otherwise the
	 * base item's own equippable (vanilla armor texture, dyed leather).
	 */
	private static boolean syncEquipment(ItemStack stack, UniqueConfig.@Nullable UniqueDef def) {
		Equippable base = stack.getPrototype().get(DataComponents.EQUIPPABLE);
		if (base == null) {
			return false;
		}
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		String asset = def != null && UniqueVisuals.enabled() ? UniqueEquipment.assetFor(def.model, base.slot().getSerializedName()) : null;
		if (asset != null) {
			ResourceKey<EquipmentAsset> key = equipmentAsset(asset);
			if (current != null && current.assetId().equals(Optional.of(key))) {
				return false;
			}
			stack.set(DataComponents.EQUIPPABLE, withAsset(base, key));
			return true;
		}
		if (current != null && current.assetId().map(id -> Guildy.MOD_ID.equals(id.identifier().getNamespace())).orElse(false)) {
			// Back to the base item's own look (a value equal to the prototype clears the patch).
			stack.set(DataComponents.EQUIPPABLE, base);
			return true;
		}
		return false;
	}

	/** Key of a Guildy equipment asset (assets/guildy/equipment/{name}.json in the resource pack). */
	public static ResourceKey<EquipmentAsset> equipmentAsset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Guildy.MOD_ID, name));
	}

	/** A copy of an equippable component drawn with another equipment asset. */
	public static Equippable withAsset(Equippable base, ResourceKey<EquipmentAsset> asset) {
		return new Equippable(base.slot(), base.equipSound(), Optional.of(asset), base.cameraOverlay(), base.allowedEntities(),
				base.dispensable(), base.swappable(), base.damageOnHurt(), base.equipOnInteract(), base.canBeSheared(),
				base.shearingSound());
	}

	// ================================================================== helpers

	/** "5", "2,5" (hearts = health points / 2). */
	static String hearts(double health) {
		return number(health / 2.0);
	}

	static String number(double value) {
		return Math.abs(value - Math.rint(value)) < 1e-6 ? String.valueOf((long) Math.rint(value))
				: String.format(Locale.FRANCE, "%.1f", value);
	}

	static String roman(int n) {
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= numerals.length ? numerals[n - 1] : String.valueOf(n);
	}

	private static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			Guildy.LOGGER.warn(message);
		}
	}
}
