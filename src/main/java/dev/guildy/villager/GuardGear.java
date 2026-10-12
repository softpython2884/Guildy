package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lifecycle;
import dev.guildy.data.Guild;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Base equipment of the guards by guild level ({@link GuardGearConfig}): a sword for the swordsmen, a bow or a
 * crossbow for the archers, an armor. It is free and never exists as an item on the villager (nothing to drop, take
 * back or wear out): the weapon is the one used for the hits / shots and shown in the main hand when nothing better was
 * taken from the storage; the armor is a pair of attribute modifiers (armor, toughness) adding, slot by slot, what the
 * base piece protects more than the worn one. A worn piece or a storage weapon outclassed by the base equipment goes
 * back to the storage.
 */
final class GuardGear {
	static final Identifier ARMOR_ID = Identifier.fromNamespaceAndPath("guildy", "guard_base_armor");
	static final Identifier TOUGHNESS_ID = Identifier.fromNamespaceAndPath("guildy", "guard_base_toughness");
	private static final double EPSILON = 1e-6;
	/** Built items per table step content (cleared on reload: the enchantment registry and the table may change). */
	private static final Map<String, Base> CACHE = new HashMap<>();

	private GuardGear() {
	}

	/** The built items of one step of the table (never handed out: copies only). */
	record Base(int level, ItemStack sword, ItemStack ranged, Map<EquipmentSlot, ItemStack> armor) {
	}

	static void init() {
		Lifecycle.RELOADED.add(CACHE::clear);
		Lifecycle.STOPPING.add(server -> CACHE.clear());
	}

	// ================================================================ the table

	/** Base equipment of the guild's level, null when disabled (or no step reached). */
	static @Nullable Base base(RegistryAccess registries, Guild guild) {
		GuardGearConfig.GearLevel step = VillagerModule.config().guardGear.forLevel(guild.level);
		if (step == null) {
			return null;
		}
		Base base = CACHE.get(step.key());
		if (base == null) {
			base = build(registries, step);
			CACHE.put(step.key(), base);
		}
		return base;
	}

	private static Base build(RegistryAccess registries, GuardGearConfig.GearLevel step) {
		ItemStack sword = item(step.sword, stack -> stack.is(ItemTags.SWORDS), "sword");
		enchant(registries, sword, Enchantments.SHARPNESS, step.sharpness);
		ItemStack ranged = item(step.ranged, stack -> stack.is(Items.BOW) || stack.is(Items.CROSSBOW), "ranged");
		if (ranged.isEmpty()) {
			ranged = new ItemStack(Items.BOW);
		}
		if (ranged.is(Items.CROSSBOW)) {
			enchant(registries, ranged, Enchantments.QUICK_CHARGE, step.quickCharge);
			enchant(registries, ranged, Enchantments.PIERCING, step.piercing);
			enchant(registries, ranged, Enchantments.MULTISHOT, step.multishot);
		} else {
			enchant(registries, ranged, Enchantments.POWER, step.power);
		}
		Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
		if (!step.armor.isEmpty()) {
			for (EquipmentSlot slot : GuardEquipment.ARMOR) {
				ItemStack piece = item(step.armor + suffix(slot), stack -> fits(stack, slot), "armor");
				if (!piece.isEmpty()) {
					armor.put(slot, piece);
				}
			}
		}
		return new Base(step.level, sword, ranged, armor);
	}

	private static ItemStack item(String id, Predicate<ItemStack> valid, String what) {
		if (id.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Item item = ItemUtil.item(id);
		ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
		if (stack.isEmpty() || !valid.test(stack)) {
			Guildy.LOGGER.warn("villagers.json5 guardGear: '{}' is not a valid {} item (ignored)", id, what);
			return ItemStack.EMPTY;
		}
		return stack;
	}

	private static boolean fits(ItemStack stack, EquipmentSlot slot) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot;
	}

	private static void enchant(RegistryAccess registries, ItemStack stack, ResourceKey<Enchantment> key, int level) {
		if (stack.isEmpty() || level <= 0) {
			return;
		}
		registries.lookupOrThrow(Registries.ENCHANTMENT).get(key).ifPresent(holder -> stack.enchant(holder, level));
	}

	private static String suffix(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "_helmet";
			case CHEST -> "_chestplate";
			case LEGS -> "_leggings";
			default -> "_boots";
		};
	}

	// ================================================================ weapon

	/** Base weapon of the role at the guild's level (a copy), empty when there is none. */
	static ItemStack baseWeapon(ServerLevel level, Guild guild, GuardRole role) {
		Base base = base(level.registryAccess(), guild);
		if (base == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = role == GuardRole.BOW ? base.ranged() : base.sword();
		return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}

	/** What the guard fights with: its storage weapon when strictly better than the base one, else the base weapon. */
	static ItemStack weapon(ServerLevel level, Guild guild, Villager villager, GuardRole role) {
		ItemStack own = GuardEquipment.weapon(villager, role);
		ItemStack base = baseWeapon(level, guild, role);
		if (base.isEmpty()) {
			return own;
		}
		if (own.isEmpty() || !ownWins(level, own, base, role)) {
			return base;
		}
		return own;
	}

	/** True when the guard fights with the base weapon of its guild level (menus: "(de base)"). */
	static boolean usesBaseWeapon(ServerLevel level, Guild guild, Villager villager, GuardRole role) {
		ItemStack base = baseWeapon(level, guild, role);
		if (base.isEmpty()) {
			return false;
		}
		ItemStack own = GuardEquipment.weapon(villager, role);
		return own.isEmpty() || !ownWins(level, own, base, role);
	}

	private static boolean ownWins(ServerLevel level, ItemStack own, ItemStack base, GuardRole role) {
		return GuardRules.betterWeapon(GuardEquipment.weaponScore(level, own, role), GuardEquipment.weaponScore(level, base, role));
	}

	/** Delay between two hits / shots with this weapon (a crossbow is faster with Quick Charge). */
	static int cooldownTicks(ServerLevel level, ItemStack weapon, GuardRole role, VillagerConfig config) {
		if (role != GuardRole.BOW) {
			return config.guard.attackCooldownTicks;
		}
		if (weapon.is(Items.CROSSBOW)) {
			return config.guardGear.crossbowCooldown(config.guard.bowCooldownTicks, GuardEquipment.enchantmentLevel(level, Enchantments.QUICK_CHARGE, weapon));
		}
		return config.guard.bowCooldownTicks;
	}

	// ================================================================ armor

	/** Protection of the base piece of a slot at the guild's level ({@link GuardRules.Piece#NONE} when none). */
	static GuardRules.Piece basePiece(ServerLevel level, Guild guild, EquipmentSlot slot) {
		Base base = base(level.registryAccess(), guild);
		ItemStack piece = base == null ? null : base.armor().get(slot);
		return piece == null ? GuardRules.Piece.NONE : GuardEquipment.piece(piece, slot);
	}

	/** Base piece of a slot (a copy for the menus), empty when none. */
	static ItemStack basePieceItem(ServerLevel level, Guild guild, EquipmentSlot slot) {
		Base base = base(level.registryAccess(), guild);
		ItemStack piece = base == null ? null : base.armor().get(slot);
		return piece == null ? ItemStack.EMPTY : piece.copy();
	}

	/** True when the base piece protects more than the worn one (the base piece is the one that counts). */
	static boolean baseWins(ServerLevel level, Guild guild, Villager villager, EquipmentSlot slot) {
		GuardRules.Piece base = basePiece(level, guild, slot);
		return base.useful() && GuardRules.compare(base, GuardEquipment.piece(villager.getItemBySlot(slot), slot)) > 0;
	}

	/** Protection that counts in a slot: the better of the worn piece and the base piece. */
	static GuardRules.Piece effectivePiece(ServerLevel level, Guild guild, Villager villager, EquipmentSlot slot) {
		GuardRules.Piece worn = GuardEquipment.piece(villager.getItemBySlot(slot), slot);
		GuardRules.Piece base = basePiece(level, guild, slot);
		return base.useful() && GuardRules.compare(base, worn) > 0 ? base : worn;
	}

	/** Re-applied each step: armor / toughness the base pieces add over the worn ones (0 = modifiers removed). */
	static void applyArmor(ServerLevel level, Guild guild, Villager villager) {
		double armor = 0.0;
		double toughness = 0.0;
		for (EquipmentSlot slot : GuardEquipment.ARMOR) {
			GuardRules.Piece base = basePiece(level, guild, slot);
			GuardRules.Piece worn = GuardEquipment.piece(villager.getItemBySlot(slot), slot);
			if (base.useful() && GuardRules.compare(base, worn) > 0) {
				armor += Math.max(0.0, base.armor() - worn.armor());
				toughness += Math.max(0.0, base.toughness() - worn.toughness());
			}
		}
		set(villager, Attributes.ARMOR, ARMOR_ID, armor);
		set(villager, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughness);
	}

	/** Not a guard anymore (or released): the base armor goes away. */
	static void clear(Villager villager) {
		set(villager, Attributes.ARMOR, ARMOR_ID, 0.0);
		set(villager, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, 0.0);
	}

	private static void set(Villager villager, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = villager.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount <= EPSILON) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || Math.abs(current.amount() - amount) > EPSILON || current.operation() != AttributeModifier.Operation.ADD_VALUE) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ================================================================ outclassed equipment

	/**
	 * Worn pieces and a storage weapon that the base equipment outclasses (the guild leveled up) are useless: they go
	 * back to the storage (kept when no chest has room).
	 *
	 * @return number of items brought back
	 */
	static int returnOutclassed(WorkContext ctx, GuardRole role) {
		if (base(ctx.level.registryAccess(), ctx.guild) == null) {
			return 0;
		}
		Villager villager = ctx.villager;
		int returned = 0;
		for (EquipmentSlot slot : GuardEquipment.ARMOR) {
			if (!villager.getItemBySlot(slot).isEmpty() && baseWins(ctx.level, ctx.guild, villager, slot) && store(ctx, slot)) {
				returned++;
			}
		}
		ItemStack own = GuardEquipment.weapon(villager, role);
		ItemStack base = baseWeapon(ctx.level, ctx.guild, role);
		if (!own.isEmpty() && !base.isEmpty() && !ownWins(ctx.level, own, base, role) && store(ctx, GuardEquipment.WEAPON)) {
			returned++;
		}
		return returned;
	}

	private static boolean store(WorkContext ctx, EquipmentSlot slot) {
		Villager villager = ctx.villager;
		ItemStack stack = villager.getItemBySlot(slot).copy();
		ItemStack leftover = GuardEquipment.storeInStorage(ctx.level, ctx.guild, stack, villager.position());
		if (leftover.getCount() == stack.getCount()) {
			return false;
		}
		villager.setItemSlot(slot, leftover);
		return true;
	}
}
