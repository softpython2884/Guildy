package dev.guildy.villager;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guard equipment taken from the guild's registered storage chests: armor pieces in the four armor slots (vanilla
 * armor attributes then protect the villager; vanilla clients do not draw armor on villagers, the villager menu shows
 * it) and an optional better weapon, kept in the off hand. The main hand only shows a marked display copy (no
 * enchantment, no drop): vanilla villager behaviours overwrite the main hand (trade displays), never the off hand.
 * <p>
 * Items really move: taken out of the chest, the replaced piece goes back into it (or another storage chest), and on
 * task change / dismissal / departure everything returns to the storage, to the player or to the ground.
 */
final class GuardEquipment {
	/** custom_data kind of the main hand display item. */
	static final String KIND_DISPLAY = "guard_display";
	static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	/** Slot of the real weapon taken from a chest. */
	static final EquipmentSlot WEAPON = EquipmentSlot.OFFHAND;
	/** Slots holding items of the guild (returned / dropped when the villager stops being a guard). */
	static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.OFFHAND);
	/**
	 * Attack damage of a wooden sword (player formula: 1 + 3): the configured guard damage is that of a guard hitting with
	 * it (or with no sword at all); a better sword adds the difference.
	 */
	static final double BASELINE_SWORD_DAMAGE = 4.0;
	/** Drop chance above 1 = always dropped on death, never damaged (vanilla "preserved" equipment). */
	private static final float GUARANTEED_DROP = 2.0f;

	private GuardEquipment() {
	}

	/** What the equipment of a villager went to (for the messages). */
	record Returned(int stored, int given, int dropped) {
		int total() {
			return stored + given + dropped;
		}
	}

	// ================================================================ main hand display

	static boolean isDisplay(ItemStack stack) {
		return GuildyItems.is(stack, KIND_DISPLAY);
	}

	/** Display copy of the weapon: same item, glint if the real one is enchanted, no data (worth nothing if it leaks). */
	static ItemStack displayFor(GuardRole role, ItemStack weapon) {
		Item item = !weapon.isEmpty() ? weapon.getItem() : role == GuardRole.BOW ? Items.BOW : Items.IRON_SWORD;
		ItemStack display = new ItemStack(item);
		if (weapon.isEnchanted()) {
			display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return GuildyItems.tag(display, KIND_DISPLAY, new CompoundTag());
	}

	/**
	 * Shows the weapon the guard fights with (storage weapon or base weapon of the guild level) in the main hand
	 * (re-applied each step: trade displays may replace it).
	 */
	static void applyDisplay(ServerLevel level, Guild guild, Villager villager, GuardRole role) {
		ItemStack wanted = displayFor(role, GuardGear.weapon(level, guild, villager, role));
		if (!ItemStack.matches(villager.getMainHandItem(), wanted)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, wanted);
		}
		if (villager.getDropChances().byEquipment(EquipmentSlot.MAINHAND) != 0.0f) {
			villager.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
	}

	// ================================================================ weapon

	/** The real weapon of the guard when it fits its role (off hand), else empty. */
	static ItemStack weapon(Villager villager, GuardRole role) {
		ItemStack stack = villager.getItemBySlot(WEAPON);
		return fitsRole(stack, role) ? stack : ItemStack.EMPTY;
	}

	/** Swords for the swordsmen, bows and crossbows for the archers. */
	static boolean fitsRole(ItemStack stack, GuardRole role) {
		return !stack.isEmpty() && (role == GuardRole.BOW ? stack.is(Items.BOW) || stack.is(Items.CROSSBOW) : stack.is(ItemTags.SWORDS));
	}

	/** A weapon a guard may take from a chest: fits the role, and no Guildy item (unique weapons stay for players). */
	private static boolean weaponCandidate(ItemStack stack, GuardRole role) {
		return fitsRole(stack, role) && !GuildyItems.isSpecial(stack) && !stack.isBroken();
	}

	static double weaponScore(ServerLevel level, ItemStack stack, GuardRole role) {
		if (role == GuardRole.BOW) {
			if (stack.is(Items.CROSSBOW)) {
				return GuardRules.crossbowScore(enchantmentLevel(level, Enchantments.QUICK_CHARGE, stack),
						enchantmentLevel(level, Enchantments.PIERCING, stack), enchantmentLevel(level, Enchantments.MULTISHOT, stack));
			}
			return GuardRules.bowScore(enchantmentLevel(level, Enchantments.POWER, stack), enchantmentLevel(level, Enchantments.FLAME, stack),
					enchantmentLevel(level, Enchantments.PUNCH, stack));
		}
		return GuardRules.swordScore(attackDamage(stack), enchantmentLevel(level, Enchantments.SHARPNESS, stack));
	}

	/**
	 * Score of what the guard fights with now: its storage weapon or the base weapon of the guild level
	 * ({@link GuardGear}), at least a wooden sword / plain bow.
	 */
	static double currentWeaponScore(ServerLevel level, Guild guild, Villager villager, GuardRole role) {
		ItemStack weapon = GuardGear.weapon(level, guild, villager, role);
		double base = role == GuardRole.BOW ? GuardRules.bowScore(0, 0, 0) : GuardRules.swordScore(BASELINE_SWORD_DAMAGE, 0);
		return weapon.isEmpty() ? base : Math.max(base, weaponScore(level, weapon, role));
	}

	/** Main hand attack damage of a sword (player formula: base 1 + the item's modifiers). */
	static double attackDamage(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
	}

	static boolean hasFlame(ServerLevel level, ItemStack bow) {
		return enchantmentLevel(level, Enchantments.FLAME, bow) > 0;
	}

	static int enchantmentLevel(ServerLevel level, ResourceKey<Enchantment> key, ItemStack stack) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
	}

	// ================================================================ armor

	/** Protection of a piece worn in {@code slot} (empty / broken = nothing). */
	static GuardRules.Piece piece(ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty() || stack.isBroken()) {
			return GuardRules.Piece.NONE;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double armor = modifiers.compute(Attributes.ARMOR, 0.0, slot);
		double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
		double durability = stack.isDamageableItem() ? 1.0 - stack.getDamageValue() / (double) Math.max(1, stack.getMaxDamage()) : 1.0;
		return new GuardRules.Piece(armor, toughness, durability);
	}

	/** An armor piece for {@code slot} that a villager can wear (no Guildy item). */
	private static boolean armorCandidate(ItemStack stack, EquipmentSlot slot, Villager villager) {
		if (stack.isEmpty() || stack.isBroken() || GuildyItems.isSpecial(stack)) {
			return false;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot && equippable.canBeEquippedBy(villager.typeHolder());
	}

	/**
	 * Index of the best upgrade of {@code slot} in the container (better than what protects the guard: its worn piece or
	 * the base piece of the guild level), or -1.
	 */
	private static int bestArmor(WorkContext ctx, Container container, EquipmentSlot slot) {
		Villager villager = ctx.villager;
		GuardRules.Piece current = GuardGear.effectivePiece(ctx.level, ctx.guild, villager, slot);
		GuardRules.Piece best = null;
		int bestIndex = -1;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!armorCandidate(stack, slot, villager)) {
				continue;
			}
			GuardRules.Piece candidate = piece(stack, slot);
			if (GuardRules.better(candidate, current) && (best == null || GuardRules.compare(candidate, best) > 0)) {
				best = candidate;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	/** Index of the best weapon upgrade in the container (better than the storage or base weapon), or -1. */
	private static int bestWeapon(WorkContext ctx, Container container, GuardRole role) {
		ServerLevel level = ctx.level;
		double best = currentWeaponScore(level, ctx.guild, ctx.villager, role);
		int bestIndex = -1;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!weaponCandidate(stack, role)) {
				continue;
			}
			double score = weaponScore(level, stack, role);
			if (GuardRules.betterWeapon(score, best)) {
				best = score;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private static boolean hasUpgrade(WorkContext ctx, Container container, GuardRole role) {
		VillagerConfig.Guard cfg = ctx.config.guard;
		if (cfg.equipmentFromChests) {
			for (EquipmentSlot slot : ARMOR) {
				if (bestArmor(ctx, container, slot) >= 0) {
					return true;
				}
			}
		}
		return cfg.weaponsFromChests && bestWeapon(ctx, container, role) >= 0;
	}

	/**
	 * Nearest usable registered chest holding something better than what the guard wears, or null. Only the chests that
	 * may hold equipment are read ({@link StorageIndex#equipmentGivers}: deposits, fallback, filter and category chests
	 * of armor and weapons, transversal categories).
	 */
	static @Nullable BlockPos findUpgradeChest(WorkContext ctx, GuardRole role) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageChest chest : Storage.index(ctx.guild).equipmentGivers()) {
			if (!Storage.usable(ctx.level, ctx.guild, chest)) {
				continue;
			}
			Container container = Storage.container(ctx.level, chest.pos);
			if (container == null || container.isEmpty() || !hasUpgrade(ctx, container, role)) {
				continue;
			}
			double dist = ctx.distSq(chest.pos);
			if (dist < bestDist) {
				bestDist = dist;
				best = chest.pos;
			}
		}
		return best;
	}

	/**
	 * At the chest: takes every upgrade it holds (best piece per slot, then the weapon). The replaced piece goes back
	 * into the chest (another storage chest, else the ground).
	 *
	 * @return number of items equipped
	 */
	static int equipFrom(WorkContext ctx, Container container, GuardRole role) {
		VillagerConfig.Guard cfg = ctx.config.guard;
		Villager villager = ctx.villager;
		int equipped = 0;
		if (cfg.equipmentFromChests) {
			for (EquipmentSlot slot : ARMOR) {
				int index = bestArmor(ctx, container, slot);
				if (index >= 0 && swap(ctx, container, index, slot)) {
					equipped++;
				}
			}
		}
		if (cfg.weaponsFromChests) {
			int index = bestWeapon(ctx, container, role);
			if (index >= 0 && swap(ctx, container, index, WEAPON)) {
				equipped++;
			}
		}
		if (equipped > 0) {
			container.setChanged();
		}
		return equipped;
	}

	/** Moves one item from the container into the slot; the previous item of the slot goes back to the storage. */
	private static boolean swap(WorkContext ctx, Container container, int index, EquipmentSlot slot) {
		Villager villager = ctx.villager;
		ItemStack taken = container.removeItem(index, 1);
		if (taken.isEmpty()) {
			return false;
		}
		ItemStack previous = villager.getItemBySlot(slot);
		ItemStack old = previous.isEmpty() ? ItemStack.EMPTY : previous.copy();
		villager.setItemSlot(slot, taken);
		villager.setDropChance(slot, dropChance(ctx.config.guard));
		if (!old.isEmpty()) {
			ItemStack leftover = Storage.insert(container, old);
			leftover = storeInStorage(ctx.level, ctx.guild, leftover, villager.position());
			if (!leftover.isEmpty()) {
				drop(ctx.level, villager.position(), leftover);
			}
		}
		Equippable equippable = taken.get(DataComponents.EQUIPPABLE);
		Holder<SoundEvent> sound = equippable != null ? equippable.equipSound() : SoundEvents.ARMOR_EQUIP_GENERIC;
		ctx.level.playSound(null, villager, sound, SoundSource.NEUTRAL, 1.0f, 1.0f);
		return true;
	}

	/** Drop chance of the guild items on the entity, from the config (re-applied each step after a reload). */
	static void syncDropChances(Villager villager, VillagerConfig.Guard cfg) {
		float wanted = dropChance(cfg);
		for (EquipmentSlot slot : SLOTS) {
			if (!villager.getItemBySlot(slot).isEmpty() && villager.getDropChances().byEquipment(slot) != wanted) {
				villager.setDropChance(slot, wanted);
			}
		}
	}

	private static float dropChance(VillagerConfig.Guard cfg) {
		return cfg.dropEquipmentOnDeath ? GUARANTEED_DROP : 0.0f;
	}

	/** Armor wears out like a player's (pre-armor damage / 4 per piece, at least 1). Broken pieces get replaced later. */
	static void wear(Villager villager, DamageSource source, float damage) {
		if (damage <= 0.0f || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return;
		}
		int durability = (int) Math.max(1.0f, damage / 4.0f);
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = villager.getItemBySlot(slot);
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.damageOnHurt() && stack.isDamageableItem() && stack.canBeHurtBy(source)) {
				stack.hurtAndBreak(durability, villager, slot);
			}
		}
	}

	// ================================================================ giving back

	static boolean hasEquipment(Villager villager) {
		for (EquipmentSlot slot : SLOTS) {
			if (!villager.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Takes the guild items off the villager (and its display weapon). */
	private static List<ItemStack> strip(Villager villager) {
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = villager.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				items.add(stack.copy());
			}
			villager.setItemSlot(slot, ItemStack.EMPTY);
		}
		if (isDisplay(villager.getMainHandItem())) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		return items;
	}

	/**
	 * The equipment goes back to the guild storage; what does not fit goes to {@code actor} (if any), else to the
	 * ground at the villager's feet.
	 */
	static Returned returnAll(ServerLevel level, Guild guild, Villager villager, @Nullable ServerPlayer actor) {
		int stored = 0;
		int given = 0;
		int dropped = 0;
		for (ItemStack stack : strip(villager)) {
			ItemStack leftover = storeInStorage(level, guild, stack, villager.position());
			if (leftover.isEmpty()) {
				stored++;
			} else if (actor != null) {
				ItemUtil.giveOrDrop(actor, leftover);
				given++;
			} else {
				drop(level, villager.position(), leftover);
				dropped++;
			}
		}
		return new Returned(stored, given, dropped);
	}

	/** Dismissal: everything goes to the player. @return number of items given */
	static int giveAll(ServerPlayer player, Villager villager) {
		List<ItemStack> items = strip(villager);
		for (ItemStack stack : items) {
			ItemUtil.giveOrDrop(player, stack);
		}
		return items.size();
	}

	/** Guild gone / villager released: the equipment falls at its feet. */
	static void dropAll(ServerLevel level, Villager villager) {
		for (ItemStack stack : strip(villager)) {
			drop(level, villager.position(), stack);
		}
	}

	/**
	 * Puts a stack into the guild storage in the order of the sorters ({@link StorageRouting}: a filter chest accepting
	 * it, a chest of its category, the fallback, a deposit), then any registered chest (nearest first). @return what did
	 * not fit
	 */
	static ItemStack storeInStorage(ServerLevel level, Guild guild, ItemStack stack, Vec3 from) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack rest = stack.copy();
		// The sorters' destinations first (routing index), each chest at most once.
		Set<BlockPos> tried = new HashSet<>();
		while (!rest.isEmpty()) {
			StorageChest chest = Storage.route(level, guild, from, rest, tried, StorageRouting.DEPOSIT_TIER);
			if (chest == null) {
				break;
			}
			tried.add(chest.pos);
			Container container = Storage.container(level, chest.pos);
			if (container != null) {
				rest = Storage.insert(container, rest);
			}
		}
		if (rest.isEmpty()) {
			return rest;
		}
		// Every destination full: any registered chest with room, nearest first.
		List<StorageChest> order = new ArrayList<>(Storage.index(guild).chests());
		order.removeIf(chest -> tried.contains(chest.pos) || !Storage.usable(level, guild, chest));
		order.sort(Comparator.comparingDouble(chest -> from.distanceToSqr(Vec3.atCenterOf(chest.pos))));
		for (StorageChest chest : order) {
			if (rest.isEmpty()) {
				break;
			}
			Container container = Storage.container(level, chest.pos);
			if (container != null && Storage.hasSpaceFor(container, rest)) {
				rest = Storage.insert(container, rest);
			}
		}
		return rest;
	}

	private static void drop(ServerLevel level, Vec3 pos, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.5, pos.z, stack.copy());
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	/** A real weapon that no longer fits the role (role changed) goes back to the storage, else to the ground. */
	static void checkWeapon(WorkContext ctx, GuardRole role) {
		ItemStack stack = ctx.villager.getItemBySlot(WEAPON);
		if (stack.isEmpty() || fitsRole(stack, role)) {
			return;
		}
		ItemStack item = stack.copy();
		ctx.villager.setItemSlot(WEAPON, ItemStack.EMPTY);
		ItemStack leftover = storeInStorage(ctx.level, ctx.guild, item, ctx.villager.position());
		drop(ctx.level, ctx.villager.position(), leftover);
	}
}
