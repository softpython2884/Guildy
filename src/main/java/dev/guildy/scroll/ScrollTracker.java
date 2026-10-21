package dev.guildy.scroll;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Light inventory scan (every second, players only, items with custom data only): scrolls and grimoires carrying only their
 * custom data (data pack loot tables) or built from an older definition are rebuilt in place. Also the lifecycle of the running
 * spells: logout and dimension change end the caster's effects, server stop ends everything, summoned guardians never
 * survive a reload of their chunk, spell damage ignores invulnerability frames, and the wind of Hâte du vent cushions
 * the falls of the allies it blessed.
 */
final class ScrollTracker {
	private ScrollTracker() {
	}

	static void init() {
		Scheduler.every("scroll.scan", 20, 11, ScrollTracker::scanAll);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("scroll.join", () -> scan(handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("scroll.leave", () -> {
			ScrollCasting.forget(handler.getPlayer().getUUID());
			EffectRunner.endFor(handler.getPlayer().getUUID());
		}));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> Safe.run("scroll.level_change", () -> {
			ScrollCasting.forget(player.getUUID());
			EffectRunner.endFor(player.getUUID());
		}));
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) ->
				Safe.get("scroll.guardian.load", () -> GuardiansSpell.allowLoad(entity), true));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				Safe.get("scroll.guardian.death", () -> GuardiansSpell.allowDeath(entity), true));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				Safe.get("scroll.allow_damage", () -> SpellTargets.allowDamage(entity, source), true));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				Safe.get("scroll.tailwind.fall", () -> TailwindSpell.allowDamage(entity, source), true));
		Lifecycle.STARTED.add(server -> GuardiansSpell.clearAll());
		Lifecycle.STOPPING.add(server -> {
			EffectRunner.clear();
			ScrollCasting.clear();
			GuardiansSpell.clearAll();
			MeteorShowerSpell.clearImpacts();
			Vulnerability.clear();
			RallySpell.clearAll();
			TailwindSpell.clearAll();
		});
		Lifecycle.RELOADED.add(ScrollItems::clearSignatures);
	}

	private static void scanAll() {
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			Safe.run("scroll.scan", () -> scan(player));
		}
	}

	/** Rebuilds the incomplete / outdated scrolls of a player's inventory (never the one being channeled). */
	static void scan(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
		boolean changed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack == using || !stack.has(DataComponents.CUSTOM_DATA)) {
				continue;
			}
			if (GrimoireItems.isGrimoire(stack)) {
				changed |= refreshGrimoire(player, inventory, slot, stack);
				continue;
			}
			ItemStack fresh = ScrollItems.refreshed(stack);
			if (fresh == null) {
				continue;
			}
			inventory.setItem(slot, fresh);
			int extra = stack.getCount() - fresh.getCount();
			ScrollConfig.ScrollDef def = ScrollItems.defOf(fresh);
			while (extra > 0 && def != null) {
				// More than the scroll's max stack size: the rest goes back to the player (or at their feet).
				ItemStack rest = ScrollItems.create(def, extra);
				if (rest.isEmpty()) {
					break;
				}
				extra -= rest.getCount();
				ItemUtil.giveOrDrop(player, rest);
			}
			changed = true;
		}
		if (changed) {
			inventory.setChanged();
		}
	}

	/** Completes a grimoire in place (or rebuilds it on its base item); extra copies of an oversized stack go to the player. */
	private static boolean refreshGrimoire(ServerPlayer player, Inventory inventory, int slot, ItemStack stack) {
		ItemStack fresh = GrimoireItems.refreshed(stack);
		if (fresh == null) {
			return false;
		}
		int extra = stack.getCount() - 1;
		ItemStack kept = fresh.copyWithCount(1);
		inventory.setItem(slot, fresh == stack && extra <= 0 ? stack : kept);
		for (int i = 0; i < Math.min(extra, 64); i++) {
			ItemUtil.giveOrDrop(player, kept.copy());
		}
		return true;
	}
}
