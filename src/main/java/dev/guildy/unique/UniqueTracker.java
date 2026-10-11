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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Light inventory scan (every second, players only, items with custom data only): announces the first owner of a freshly
 * dropped unique to the whole server (configured rarities) and keeps the custom model in line with the config (models
 * switched on / off, Polymer added or removed). Also the login / logout cleanup of the powers.
 */
final class UniqueTracker {
	private UniqueTracker() {
	}

	static void init() {
		Scheduler.every("unique.scan", 20, 3, UniqueTracker::scanAll);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("unique.join", () -> {
			DoubleJump.onJoin(handler.getPlayer());
			scan(handler.getPlayer());
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("unique.leave", () -> {
			DoubleJump.onLeave(handler.getPlayer());
			PowerUtil.forget(handler.getPlayer().getUUID());
		}));
		Lifecycle.STOPPING.add(server -> {
			DoubleJump.clear();
			PowerUtil.clear();
			MiningPowers.clear();
		});
	}

	private static void scanAll() {
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			Safe.run("unique.scan", () -> scan(player));
		}
	}

	static void scan(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		boolean changed = false;
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
			String source = UniqueItems.takeAnnounce(stack);
			if (source != null) {
				changed = true;
				if (def != null) {
					announce(player, stack, def, source);
				}
			}
		}
		if (changed) {
			inventory.setChanged();
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
