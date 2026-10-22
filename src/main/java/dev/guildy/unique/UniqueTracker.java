package dev.guildy.unique;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.structure.TextUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Light inventory scan (every second, players only, items with custom data only): announces the first owner of a freshly
 * dropped unique to the whole server (configured rarities), keeps the custom model in line with the config (models
 * switched on / off, Polymer added or removed) and feeds the registry: holder of every unique in the inventories and
 * ender chests, unregistered stacks given a serial, storage containers opened by players scanned once per opening, the
 * same serial seen twice in one pass reported as a duplicate. Also the login / logout cleanup of the powers.
 */
final class UniqueTracker {
	/** Container id of the last opened menu scanned, per player (one scan per opening). */
	private static final Map<UUID, Integer> SCANNED_MENUS = new HashMap<>();

	private UniqueTracker() {
	}

	static void init() {
		Scheduler.every("unique.scan", 20, 3, UniqueTracker::scanAll);
		// (A double jump grant left by a crash is taken back at login by FlightGrants.)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("unique.join", () -> scan(handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("unique.leave", () -> {
			// Last look at what leaves with the player (a pickup just before logging out).
			scan(handler.getPlayer());
			DoubleJump.onLeave(handler.getPlayer());
			PowerUtil.forget(handler.getPlayer().getUUID());
			Leaps.forget(handler.getPlayer().getUUID());
			SCANNED_MENUS.remove(handler.getPlayer().getUUID());
		}));
		Lifecycle.STOPPING.add(server -> {
			DoubleJump.clear();
			PowerUtil.clear();
			MiningPowers.clear();
			Leaps.clear();
			CursePowers.clear();
			UniqueFx.clear();
			UniqueDamage.clear();
			SCANNED_MENUS.clear();
		});
	}

	/** Every online player in one pass, then the end-of-pass work of the registry (losses, collections). */
	static void scanAll() {
		UniqueRegistry.Pass pass = new UniqueRegistry.Pass();
		// While a destroyed copy waits for its confirmation, open containers are looked at again (it may have been put there).
		boolean rescanMenus = UniqueRegistry.hasPendingLosses();
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			Safe.run("unique.scan", () -> scan(player, pass, rescanMenus));
		}
		UniqueRegistry.afterScan(false);
	}

	/** Scans one player (inventory, ender chest, opened container). */
	static void scan(ServerPlayer player) {
		scan(player, new UniqueRegistry.Pass(), false);
	}

	static void scan(ServerPlayer player, UniqueRegistry.Pass pass, boolean rescanMenu) {
		Inventory inventory = player.getInventory();
		boolean changed = false;
		Set<String> worn = null;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
				continue;
			}
			String id = UniqueItems.idOf(stack);
			if (id == null) {
				continue;
			}
			UniqueConfig.UniqueDef def = UniqueModule.config().item(id);
			changed |= UniqueItems.syncVisuals(stack, def);
			if (def != null && !def.set.isEmpty()) {
				// Set pieces carried: their lore ticks the pieces worn.
				if (worn == null) {
					worn = SetBonuses.worn(player);
				}
				changed |= UniqueItems.syncSetLore(stack, def, worn);
			}
			String source = UniqueItems.takeAnnounce(stack);
			if (source != null) {
				changed = true;
				if (def != null) {
					announce(player, stack, def, source);
				}
			}
			changed |= UniqueRegistry.see(stack, id, player, true, pass, Lang.raw("unique.where.inventory"));
		}
		if (changed) {
			inventory.setChanged();
		}
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		if (scanContainer(player, ender, true, pass, Lang.raw("unique.where.ender_chest"))) {
			ender.setChanged();
		}
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != null && menu != player.inventoryMenu && storage(menu)) {
			Integer previous = SCANNED_MENUS.put(player.getUUID(), menu.containerId);
			if (rescanMenu || previous == null || previous != menu.containerId) {
				scanMenu(player, menu, inventory, ender, pass);
			}
		}
	}

	/** Storage menus only (never the Guildy / SGUI menus, merchants, crafting results...). */
	private static boolean storage(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}

	private static boolean scanContainer(ServerPlayer player, Container container, boolean carried, UniqueRegistry.Pass pass, String where) {
		boolean changed = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
				continue;
			}
			String id = UniqueItems.idOf(stack);
			if (id != null) {
				changed |= neutralSetLore(stack, id);
				changed |= UniqueRegistry.see(stack, id, player, carried, pass, where);
			}
		}
		return changed;
	}

	/** A set piece stored away (ender chest, chest): its lore no longer ticks the pieces of a wearer. */
	private static boolean neutralSetLore(ItemStack stack, String id) {
		UniqueConfig.UniqueDef def = UniqueModule.config().item(id);
		return def != null && !def.set.isEmpty() && UniqueItems.syncSetLore(stack, def, null);
	}

	/** The container a player opened (its own inventory and ender chest excluded: already scanned). */
	private static void scanMenu(ServerPlayer player, AbstractContainerMenu menu, Inventory inventory, Container ender, UniqueRegistry.Pass pass) {
		for (Slot slot : menu.slots) {
			if (slot.container == inventory || slot.container == ender) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
				continue;
			}
			String id = UniqueItems.idOf(stack);
			if (id != null && (neutralSetLore(stack, id) | UniqueRegistry.see(stack, id, player, false, pass, Lang.raw("unique.where.container")))) {
				slot.setChanged();
			}
		}
	}

	/** "Steve a obtenu [Foudrelame] (butin d'un boss de horde) !" for everyone, a title for the finder. */
	static void announce(ServerPlayer finder, ItemStack stack, UniqueConfig.UniqueDef def, String source) {
		UniqueConfig cfg = UniqueModule.config();
		Component name = stack.getDisplayName();
		Msg.title(finder, Lang.get("unique.announce.title"), TextUtil.lang("unique.announce.subtitle", name), 10, 60, 20);
		Msg.sound(finder, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
		if (!cfg.announces(def.rarity())) {
			return;
		}
		String sourceKey = "unique.source." + source;
		Component message = Msg.prefixed(TextUtil.lang("unique.announce", finder.getName().getString(), name,
				Lang.raw(Lang.has(sourceKey) ? sourceKey : "unique.source.unknown")));
		for (ServerPlayer player : GuildyServer.players()) {
			player.sendSystemMessage(message);
			if (cfg.announce.sound && player != finder) {
				Msg.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.4F);
			}
		}
	}
}
