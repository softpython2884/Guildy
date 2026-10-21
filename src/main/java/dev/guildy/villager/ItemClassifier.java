package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the storage categories know about an item ({@link StorageCategories.Facts}): its id, its tags (with their size),
 * its properties read from the item components (food, armor, tool, weapon, potion, stored enchantments...) and the
 * creative inventory tabs that show it (built once on the server, like a client builds them). Facts are cached per item
 * for stacks without component changes; resolutions (routing chain and category, {@link StorageCategories#resolve}) per
 * item and per distinct stack (enchanted books, potions...; bounded); {@link #invalidate()} after a data pack reload.
 */
public final class ItemClassifier {
	private static final Map<Item, StorageCategories.Facts> FACTS = new HashMap<>();
	private static final Map<Item, StorageCategories.Resolution> RESOLUTIONS = new HashMap<>();
	/** Resolutions of stacks with components (same item, same components), cleared when it grows too big. */
	private static final Object2ObjectOpenCustomHashMap<ItemStack, StorageCategories.Resolution> VARIANTS =
			new Object2ObjectOpenCustomHashMap<>(StorageSort.SAME_ITEM);
	private static final Map<String, StorageCategories.Resolution> MAGIC = new HashMap<>();
	private static final int VARIANTS_LIMIT = 4096;
	private static @Nullable List<ItemStack> equipment;
	private static @Nullable StorageCategories cachedFor;
	private static @Nullable Map<String, Integer> tagSizes;
	private static @Nullable Map<Item, Set<String>> tabs;
	/** Item → its entries in the creative tabs (stack shown, position in the whole creative inventory), in order. */
	private static @Nullable Map<Item, List<CreativeEntry>> creative;

	/** Guildy magic items (custom_data kind) sorted like the enchanted books. */
	private static final Set<String> MAGIC_KINDS = Set.of("scroll", "grimoire", "ruin_summon", "ruin_summon_cursed");

	private record CreativeEntry(ItemStack stack, int index) {
	}

	private ItemClassifier() {
	}

	/**
	 * Storage category of a stack (with the current villagers.json5 categories): its deepest category of the tree, the
	 * fallback when none (transversal categories aside: see {@link #chain}).
	 */
	public static String categoryOf(ItemStack stack) {
		return resolve(stack).category();
	}

	/**
	 * Routing chain of a stack, most specific first: the transversal categories it matches, then its deepest
	 * category, its parent... its top-level category (empty: only the fallback).
	 */
	public static List<String> chain(ItemStack stack) {
		return resolve(stack).chain();
	}

	/** Category of a stack and the rule that chose it. */
	public static StorageCategories.Match match(ItemStack stack) {
		return resolve(stack).home();
	}

	/** Where the stack belongs (cached). */
	public static StorageCategories.Resolution resolve(ItemStack stack) {
		StorageCategories categories = categories();
		if (stack.getComponentsPatch().isEmpty()) {
			StorageCategories.Resolution cached = RESOLUTIONS.get(stack.getItem());
			if (cached == null) {
				cached = categories.resolve(facts(stack));
				RESOLUTIONS.put(stack.getItem(), cached);
			}
			return cached;
		}
		String kind = GuildyItems.kind(stack);
		if (kind != null && MAGIC_KINDS.contains(kind)) {
			// Spell scrolls, grimoires and incantation scrolls are banner patterns or books underneath: an enchanted book
			// chooses their top-level category, the magic item itself (id "guildy:<kind>", property @magic) the rest.
			StorageCategories.Resolution magic = MAGIC.get(kind);
			if (magic == null) {
				StorageCategories.Facts detail = new StorageCategories.Facts("guildy:" + kind, Map.of(), Set.of("magic"));
				magic = categories.resolve(facts(new ItemStack(Items.ENCHANTED_BOOK)), detail);
				MAGIC.put(kind, magic);
			}
			return magic;
		}
		StorageCategories.Resolution known = VARIANTS.get(stack);
		if (known == null) {
			known = categories.resolve(facts(stack));
			if (VARIANTS.size() >= VARIANTS_LIMIT) {
				VARIANTS.clear();
			}
			VARIANTS.put(stack.copyWithCount(1), known);
		}
		return known;
	}

	private static StorageCategories categories() {
		StorageCategories categories = Storage.categories();
		if (categories != cachedFor) {
			RESOLUTIONS.clear();
			VARIANTS.clear();
			MAGIC.clear();
			equipment = null;
			cachedFor = categories;
			warnUnknownRules();
		}
		return categories;
	}

	/**
	 * One plain stack of every item a guard may wear or wield (armor pieces, shields, elytra, weapons), for the chests
	 * worth searching ({@link StorageIndex#equipmentGivers}).
	 */
	static List<ItemStack> equipmentSamples() {
		categories();
		if (equipment == null) {
			List<ItemStack> samples = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack stack = item.getDefaultInstance();
				if (!stack.isEmpty() && (isArmor(stack) || isWeapon(stack))) {
					samples.add(stack);
				}
			}
			equipment = List.copyOf(samples);
		}
		return equipment;
	}

	/** Forgets the cached facts, tags and tabs (data pack reload, server start). */
	public static void invalidate() {
		FACTS.clear();
		RESOLUTIONS.clear();
		VARIANTS.clear();
		MAGIC.clear();
		equipment = null;
		cachedFor = null;
		tagSizes = null;
		tabs = null;
		creative = null;
	}

	// ------------------------------------------------------------------ facts

	public static StorageCategories.Facts facts(ItemStack stack) {
		Item item = stack.getItem();
		boolean plain = stack.getComponentsPatch().isEmpty();
		if (plain) {
			StorageCategories.Facts cached = FACTS.get(item);
			if (cached != null) {
				return cached;
			}
		}
		Map<String, Integer> sizes = tagSizes();
		Map<String, Integer> tags = new LinkedHashMap<>();
		stack.tags().forEach(tag -> {
			String id = tag.location().toString();
			tags.put(id, sizes.getOrDefault(id, 0));
		});
		Set<String> properties = properties(stack);
		properties.addAll(tabs().getOrDefault(item, Set.of()));
		StorageCategories.Facts facts = new StorageCategories.Facts(BuiltInRegistries.ITEM.getKey(item).toString(), tags, Set.copyOf(properties));
		if (plain) {
			FACTS.put(item, facts);
		}
		return facts;
	}

	/** Properties of {@link StorageCategories#PROPERTIES} read from the components of the stack. */
	static Set<String> properties(ItemStack stack) {
		Set<String> properties = new HashSet<>();
		Item item = stack.getItem();
		if (stack.has(DataComponents.FOOD)) {
			properties.add("food");
		}
		if (stack.has(DataComponents.POTION_CONTENTS)) {
			properties.add("potion");
		}
		if (stack.has(DataComponents.STORED_ENCHANTMENTS)) {
			properties.add("enchanted_book");
		}
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments != null && !enchantments.isEmpty()) {
			properties.add("enchanted");
		}
		if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) {
			properties.add("music_disc");
		}
		if (stack.has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
			properties.add("banner_pattern");
		}
		if (item instanceof SmithingTemplateItem) {
			properties.add("smithing_template");
		}
		if (item instanceof SpawnEggItem) {
			properties.add("spawn_egg");
		}
		if (stack.has(DataComponents.DYE)) {
			properties.add("dye");
		}
		if (item instanceof BlockItem) {
			properties.add("block");
		}
		boolean weapon = isWeapon(stack);
		if (weapon) {
			properties.add("weapon");
		} else if (stack.has(DataComponents.TOOL)) {
			properties.add("tool");
		}
		if (isArmor(stack)) {
			properties.add("armor");
		}
		return properties;
	}

	/**
	 * Swords, spears, maces, tridents, bows, crossbows: a weapon component that costs 1 durability per hit (tools cost
	 * 2), a spear component, a projectile weapon, or attack damage without being a tool.
	 */
	private static boolean isWeapon(ItemStack stack) {
		if (stack.has(DataComponents.KINETIC_WEAPON) || stack.has(DataComponents.PIERCING_WEAPON) || stack.getItem() instanceof ProjectileWeaponItem) {
			return true;
		}
		Weapon weapon = stack.get(DataComponents.WEAPON);
		if (weapon != null) {
			return weapon.itemDamagePerAttack() <= 1;
		}
		return !stack.has(DataComponents.TOOL) && bonus(stack, true) > 0;
	}

	/** Armor pieces (worn in an armor slot and giving armor), elytra (glider) and shields (blocking). */
	private static boolean isArmor(ItemStack stack) {
		if (stack.has(DataComponents.GLIDER) || stack.has(DataComponents.BLOCKS_ATTACKS)) {
			return true;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot slot = equippable == null ? null : equippable.slot();
		return slot != null && slot.isArmor() && bonus(stack, false) > 0;
	}

	/** Sum of the positive attack damage (weapon) or armor / toughness (armor) modifiers of the stack. */
	private static double bonus(ItemStack stack, boolean attack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double total = 0;
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			Object attribute = entry.attribute().value();
			boolean counted = attack ? attribute == Attributes.ATTACK_DAMAGE.value()
					: attribute == Attributes.ARMOR.value() || attribute == Attributes.ARMOR_TOUGHNESS.value();
			if (counted && entry.modifier().amount() > 0) {
				total += entry.modifier().amount();
			}
		}
		return total;
	}

	// ------------------------------------------------------------------ registries

	private static Map<String, Integer> tagSizes() {
		if (tagSizes == null) {
			Map<String, Integer> sizes = new HashMap<>();
			BuiltInRegistries.ITEM.getTags().forEach(named -> sizes.put(named.key().location().toString(), named.size()));
			tagSizes = sizes;
		}
		return tagSizes;
	}

	/** Item → "tab:minecraft:combat"... for every creative tab of type CATEGORY (empty when no server runs). */
	static Map<Item, Set<String>> tabs() {
		if (tabs == null) {
			buildTabs();
		}
		return tabs;
	}

	private static Map<Item, List<CreativeEntry>> creative() {
		if (creative == null) {
			buildTabs();
		}
		return creative;
	}

	private static void buildTabs() {
		Map<Item, Set<String>> map = new HashMap<>();
		Map<Item, List<CreativeEntry>> order = new HashMap<>();
		tabs = map;
		creative = order;
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		try {
			// Dedicated server only (Guildy is server-side): no client shares these tab contents.
			CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), false, server.registryAccess());
			int index = 0;
			for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
				Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
				if (key == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
					continue;
				}
				String property = StorageCategories.TAB + key;
				addTab(map, tab.getDisplayItems(), property);
				addTab(map, tab.getSearchTabDisplayItems(), property);
				for (ItemStack stack : tab.getDisplayItems()) {
					List<CreativeEntry> entries = order.computeIfAbsent(stack.getItem(), item -> new ArrayList<>());
					boolean known = false;
					for (CreativeEntry entry : entries) {
						known |= ItemStack.isSameItemSameComponents(entry.stack(), stack);
					}
					if (!known) {
						entries.add(new CreativeEntry(stack.copyWithCount(1), index));
					}
					index++;
				}
			}
		} catch (RuntimeException e) {
			Guildy.LOGGER.warn("[villagers.json5] creative tabs unavailable, @tab: storage rules ignored", e);
		}
	}

	/**
	 * Position of a stack in the creative inventory (tabs in order, then their items): the entry with the very same
	 * components (potions, enchanted books, paintings...), else the item's first entry; {@link Integer#MAX_VALUE} for
	 * items in no tab.
	 */
	static int creativeIndex(ItemStack stack) {
		List<CreativeEntry> entries = creative().get(stack.getItem());
		if (entries == null || entries.isEmpty()) {
			return Integer.MAX_VALUE;
		}
		if (!stack.getComponentsPatch().isEmpty()) {
			for (CreativeEntry entry : entries) {
				if (ItemStack.isSameItemSameComponents(entry.stack(), stack)) {
					return entry.index();
				}
			}
		}
		return entries.getFirst().index();
	}

	/** True when the stack is shown as such in the creative inventory (a plain item, or a potion / book variant). */
	static boolean isCreativeVariant(ItemStack stack) {
		if (stack.getComponentsPatch().isEmpty()) {
			return true;
		}
		List<CreativeEntry> entries = creative().get(stack.getItem());
		if (entries != null) {
			for (CreativeEntry entry : entries) {
				if (ItemStack.isSameItemSameComponents(entry.stack(), stack)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void addTab(Map<Item, Set<String>> map, Collection<ItemStack> stacks, String property) {
		for (ItemStack stack : stacks) {
			map.computeIfAbsent(stack.getItem(), item -> new HashSet<>()).add(property);
		}
	}

	/** Creative tab ids ("minecraft:combat") of type CATEGORY. */
	static Set<String> tabIds() {
		Set<String> ids = new HashSet<>();
		for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
			Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
			if (key != null && tab.getType() == CreativeModeTab.Type.CATEGORY) {
				ids.add(key.toString());
			}
		}
		return ids;
	}

	/** Logs, once per config load, the rules that designate nothing (unknown item, tag, property or tab). */
	private static void warnUnknownRules() {
		VillagerConfig config = VillagerModule.config();
		Set<String> tabIds = tabIds();
		Map<String, Integer> sizes = tagSizes();
		for (Map.Entry<String, StorageCategory> entry : config.storage.categories.entrySet()) {
			List<String> unknown = new ArrayList<>(StorageCategories.problems(entry.getValue().rules));
			for (String rule : entry.getValue().rules) {
				if (rule.startsWith("#")) {
					String tag = StorageCategories.normalize(rule);
					// Common "c:" tags are optional (conventions of Fabric and other mods).
					if (!tag.startsWith("c:") && !sizes.containsKey(tag)) {
						unknown.add("unknown tag '" + rule + "'");
					}
				} else if (rule.startsWith("@")) {
					String property = StorageCategories.property(rule);
					if (property.startsWith(StorageCategories.TAB) && !tabIds.contains(property.substring(StorageCategories.TAB.length()))) {
						unknown.add("unknown creative tab '" + rule + "'");
					}
				} else if (rule.indexOf('*') < 0) {
					Identifier id = Identifier.tryParse(StorageCategories.normalize(rule));
					if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
						unknown.add("unknown item '" + rule + "'");
					}
				}
			}
			String icon = entry.getValue().icon;
			if (icon != null && !icon.isBlank()) {
				Identifier id = Identifier.tryParse(StorageCategories.normalize(icon));
				if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
					unknown.add("unknown icon '" + icon + "'");
				}
			}
			if (!unknown.isEmpty()) {
				Guildy.LOGGER.warn("[villagers.json5] storage category '{}': {}", entry.getKey(), String.join(", ", unknown));
			}
		}
	}
}
