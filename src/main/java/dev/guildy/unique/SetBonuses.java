package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The armor set bonuses on the players ({@link UniqueSets} for the pure rules): the pieces in the four armor slots are
 * looked at when the equipment changes (Fabric EQUIPMENT_CHANGE, handled at the end of the tick), at login and respawn,
 * after a reload and every half second (cheap: four slots per player, nothing done when nothing changed). The attribute
 * modifiers of the tiers reached are transient ones with stable ids ({@code guildy:set_<set>_<pieces>_<n>}: never stacked,
 * gone with the entity, given back at the next login), removed as soon as a piece comes off; the powers of the tiers are
 * read by {@link SetPowers}. A tier gained or lost is shown in the action bar, and the set section of the pieces' lore
 * ticks the pieces worn ({@link UniqueItems#syncSetLore}).
 */
final class SetBonuses {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** What a player wears, for the entity it was computed on (a respawn creates a new entity). */
	private static final class Worn {
		final ServerPlayer entity;
		String key = "";
		Set<String> ids = Set.of();
		Map<String, Integer> counts = Map.of();
		Map<String, Integer> tiers = Map.of();
		Set<SetPower> powers = EnumSet.noneOf(SetPower.class);
		/** Power -> set that gives it (cooldown group of its pieces). */
		Map<SetPower, String> powerSets = new EnumMap<>(SetPower.class);
		/** Modifiers applied on the entity: attribute -> modifier ids. */
		final Map<Holder<Attribute>, Set<Identifier>> applied = new LinkedHashMap<>();

		Worn(ServerPlayer entity) {
			this.entity = entity;
		}
	}

	private static final Map<UUID, Worn> STATE = new HashMap<>();
	/** Players whose equipment changed during the tick (refreshed at its end, outside the vanilla equipment loop). */
	private static final Set<UUID> DIRTY = new LinkedHashSet<>();
	private static final Set<String> WARNED = new HashSet<>();

	private SetBonuses() {
	}

	static void init() {
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
			if (entity instanceof ServerPlayer player && slot.isArmor()) {
				DIRTY.add(player.getUUID());
			}
		});
		Scheduler.every("unique.sets", 10, 7, SetBonuses::checkAll);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("unique.sets.join", () -> refresh(handler.getPlayer(), false)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("unique.sets.leave", () -> forget(handler.getPlayer().getUUID())));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Safe.run("unique.sets.respawn", () -> refresh(newPlayer, false)));
		Lifecycle.RELOADED.add(() -> Safe.run("unique.sets.reload", () -> {
			for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
				refresh(player, true);
			}
		}));
		Lifecycle.STOPPING.add(server -> clear());
	}

	/** End of every server tick: the players whose armor changed. */
	static void tick() {
		if (DIRTY.isEmpty()) {
			return;
		}
		List<UUID> dirty = new ArrayList<>(DIRTY);
		DIRTY.clear();
		for (UUID uuid : dirty) {
			ServerPlayer player = GuildyServer.player(uuid);
			if (player != null) {
				Safe.run("unique.sets", () -> update(player));
			}
		}
	}

	/** Every half second: anything worn differently (or a new entity) is refreshed. */
	static void checkAll() {
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			Safe.run("unique.sets", () -> update(player));
		}
	}

	/** Refreshes the player when his armor differs from the last look (announced), or when the entity is new (silent). */
	static void update(ServerPlayer player) {
		Worn state = STATE.get(player.getUUID());
		if (state == null || state.entity != player) {
			refresh(player, false);
		} else if (!state.key.equals(key(wornIds(player)))) {
			refresh(player, true);
		}
	}

	// ================================================================== queries

	/** True when the player's worn pieces give this power (powers enabled). */
	static boolean has(ServerPlayer player, SetPower power) {
		Worn state = STATE.get(player.getUUID());
		return state != null && state.entity == player && state.powers.contains(power);
	}

	/** Set whose bonus gives the player this power (cooldown group of its pieces), or null. */
	static @Nullable String setOf(ServerPlayer player, SetPower power) {
		Worn state = STATE.get(player.getUUID());
		return state == null || state.entity != player ? null : state.powerSets.get(power);
	}

	/** Online players with at least one set power (for the power ticks). */
	static List<ServerPlayer> wearers(SetPower power) {
		List<ServerPlayer> out = new ArrayList<>();
		for (Worn state : STATE.values()) {
			if (state.powers.contains(power) && !state.entity.isRemoved()) {
				out.add(state.entity);
			}
		}
		return out;
	}

	/** Unique ids in the four armor slots (null for other items), head to feet. */
	static List<@Nullable String> wornIds(ServerPlayer player) {
		List<String> ids = new ArrayList<>(ARMOR.length);
		for (EquipmentSlot slot : ARMOR) {
			ids.add(UniqueItems.idOf(player.getItemBySlot(slot)));
		}
		return ids;
	}

	/** The worn unique ids of the player (for the lore of the pieces he carries). */
	static Set<String> worn(ServerPlayer player) {
		Set<String> ids = new HashSet<>();
		for (String id : wornIds(player)) {
			if (id != null) {
				ids.add(id);
			}
		}
		return ids;
	}

	private static String key(List<@Nullable String> ids) {
		StringBuilder key = new StringBuilder();
		for (String id : ids) {
			key.append(id == null ? "" : id).append('|');
		}
		return key.toString();
	}

	// ================================================================== refresh

	/**
	 * Recomputes the sets the player wears: attribute modifiers added / removed, powers, lore of the pieces he carries;
	 * {@code announce}: the tiers gained or lost are shown in the action bar.
	 */
	static void refresh(ServerPlayer player, boolean announce) {
		UniqueConfig cfg = UniqueModule.config();
		List<String> ids = wornIds(player);
		Map<String, Integer> counts = UniqueSets.count(ids, cfg);
		Worn previous = STATE.get(player.getUUID());
		if (previous != null && previous.entity != player) {
			// A new entity (respawn): the old one's modifiers and power states are gone with it.
			SetPowers.forget(player.getUUID());
		}
		Worn state = previous != null && previous.entity == player ? previous : new Worn(player);
		Map<String, Integer> oldTiers = state.tiers;
		Map<String, Integer> oldCounts = state.counts;
		Set<SetPower> oldPowers = state.powers;
		state.key = key(ids);
		state.ids = worn(player);
		state.counts = counts;
		state.tiers = UniqueSets.tiers(counts, cfg);
		state.powers = cfg.powersEnabled ? UniqueSets.powers(counts, cfg) : EnumSet.noneOf(SetPower.class);
		state.powerSets = new EnumMap<>(SetPower.class);
		counts.forEach((set, worn) -> {
			for (UniqueConfig.SetBonus bonus : UniqueSets.active(cfg.set(set), worn)) {
				if (bonus.power() != null) {
					state.powerSets.putIfAbsent(bonus.power(), set);
				}
			}
		});
		applyModifiers(player, state, UniqueSets.modifiers(counts, cfg));
		// One entry per online player, sets or not (the half-second check then does nothing until the armor changes).
		STATE.put(player.getUUID(), state);
		SetPowers.onPowersChanged(player, oldPowers, state.powers);
		syncLore(player, state.ids);
		if (announce) {
			announce(player, cfg, oldCounts, oldTiers, counts, state.tiers);
		}
	}

	/** Adds the wanted modifiers (unchanged ones untouched), removes the applied ones no longer wanted. */
	private static void applyModifiers(ServerPlayer player, Worn state, List<UniqueSets.Modifier> wanted) {
		Map<Holder<Attribute>, Map<Identifier, AttributeModifier>> next = new LinkedHashMap<>();
		for (UniqueSets.Modifier modifier : wanted) {
			Identifier attributeId = Identifier.tryParse(modifier.attribute());
			Optional<Holder.Reference<Attribute>> attribute = attributeId == null ? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(attributeId);
			if (attribute.isEmpty()) {
				if (WARNED.add(modifier.attribute())) {
					Guildy.LOGGER.warn("[uniques] unknown attribute '{}' in a set bonus (skipped)", modifier.attribute());
				}
				continue;
			}
			Identifier id = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, modifier.path());
			next.computeIfAbsent(attribute.get(), key -> new LinkedHashMap<>())
					.put(id, new AttributeModifier(id, modifier.amount(), UniqueItems.operation(modifier.operation())));
		}
		for (Map.Entry<Holder<Attribute>, Set<Identifier>> entry : state.applied.entrySet()) {
			AttributeInstance instance = player.getAttribute(entry.getKey());
			Map<Identifier, AttributeModifier> keep = next.getOrDefault(entry.getKey(), Map.of());
			for (Identifier id : entry.getValue()) {
				if (instance != null && !keep.containsKey(id)) {
					instance.removeModifier(id);
				}
			}
		}
		state.applied.clear();
		for (Map.Entry<Holder<Attribute>, Map<Identifier, AttributeModifier>> entry : next.entrySet()) {
			AttributeInstance instance = player.getAttribute(entry.getKey());
			if (instance == null) {
				// Not an attribute of the players (a mob-only one).
				continue;
			}
			for (AttributeModifier modifier : entry.getValue().values()) {
				if (!modifier.equals(instance.getModifier(modifier.id()))) {
					instance.addOrUpdateTransientModifier(modifier);
				}
				state.applied.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).add(modifier.id());
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** The set section of the lore of every set piece the player carries: the pieces he wears ticked. */
	static void syncLore(ServerPlayer player, Set<String> worn) {
		Inventory inventory = player.getInventory();
		boolean changed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
				continue;
			}
			UniqueConfig.UniqueDef def = UniqueItems.defOf(stack);
			if (def != null && !def.set.isEmpty()) {
				changed |= UniqueItems.syncSetLore(stack, def, worn);
			}
		}
		if (changed) {
			inventory.setChanged();
		}
	}

	/** Action bar: the highest tier gained (or lost) of each set whose tier changed. */
	private static void announce(ServerPlayer player, UniqueConfig cfg, Map<String, Integer> oldCounts, Map<String, Integer> oldTiers,
			Map<String, Integer> counts, Map<String, Integer> tiers) {
		Set<String> sets = new LinkedHashSet<>(oldTiers.keySet());
		sets.addAll(tiers.keySet());
		for (String setId : sets) {
			int before = oldTiers.getOrDefault(setId, 0);
			int after = tiers.getOrDefault(setId, 0);
			UniqueConfig.SetDef set = cfg.set(setId);
			if (before == after || set == null) {
				continue;
			}
			int pieces = cfg.piecesOf(setId).size();
			String name = Lang.colorize(set.name);
			if (after > before) {
				Msg.actionBar(player, "unique.set.tier_up", name, counts.getOrDefault(setId, 0), pieces, bonusName(set, after));
				Msg.sound(player, SoundEvents.BEACON_ACTIVATE, 0.6F, after >= pieces ? 1.4F : 1.8F);
			} else {
				Msg.actionBar(player, "unique.set.tier_down", name, counts.getOrDefault(setId, 0), pieces, bonusName(set, before));
				Msg.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.6F);
			}
		}
	}

	private static String bonusName(UniqueConfig.SetDef set, int pieces) {
		for (UniqueConfig.SetBonus bonus : set.bonuses()) {
			if (bonus.pieces == pieces) {
				return bonus.name;
			}
		}
		return "";
	}

	// ================================================================== cleanup

	static void forget(UUID player) {
		STATE.remove(player);
		DIRTY.remove(player);
		SetPowers.forget(player);
	}

	static void clear() {
		STATE.clear();
		DIRTY.clear();
		SetPowers.clear();
	}
}
