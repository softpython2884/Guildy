package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the unique weapon registry ({@link UniqueLedger}, {@code <world>/guildy/uniques.json}): serials written
 * into the items, sightings from the inventory scan, loss detection (destroyed item entities and thrown tridents,
 * broken items), the uniqueness check of the rolls, collection announcements, persistence.
 */
public final class UniqueRegistry {
	/** A destroyed entity's copy is lost if not seen within this delay (a pickup also removes the entity). */
	static final int LOSS_DELAY_TICKS = 60;
	private static final int AUTOSAVE_TICKS = 5 * 60 * 20;

	private static UniqueLedger ledger = fresh();
	private static long lastSave;

	private UniqueRegistry() {
	}

	private static UniqueLedger fresh() {
		UniqueLedger ledger = new UniqueLedger();
		ledger.fixNulls();
		return ledger;
	}

	static void init() {
		Lifecycle.STARTED.add(server -> {
			ledger = UniqueStore.load(server);
			lastSave = Scheduler.ticks();
			Guildy.LOGGER.info("[uniques] {} unique weapon copies in the registry", ledger.copies().size());
		});
		Lifecycle.STOPPING.add(server -> {
			// Last look at the inventories, then the suspected losses are settled before the final save.
			Safe.run("unique.scan", UniqueTracker::scanAll);
			afterScan(true);
			save(server, true);
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> Safe.run("unique.save", () -> save(server, false)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ledger = fresh());
		Scheduler.every("unique.autosave", 200, 23, () -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null && Scheduler.ticks() - lastSave >= AUTOSAVE_TICKS) {
				lastSave = Scheduler.ticks();
				save(server, false);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity || entity instanceof AbstractArrow) {
				Safe.run("unique.unload", () -> onRemoved(entity));
			}
		});
		GuildEvents.DISBANDED.add(guild -> Safe.run("unique.disband", () -> ledger.forgetGuild(guild.id)));
	}

	/** The registry (empty before the server started). */
	public static UniqueLedger ledger() {
		return ledger;
	}

	static void save(MinecraftServer server, boolean force) {
		if (!force && !ledger.isDirty()) {
			return;
		}
		try {
			UniqueStore.save(server, ledger);
			ledger.markSaved();
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not save uniques.json", e);
		}
	}

	// ================================================================== rolls

	/** Ids a roll must skip for this recipient under the given mode. */
	public static Set<String> excluded(UniquenessMode mode, UniqueDrops.Recipient recipient) {
		return ledger.excluded(mode, recipient.guild(), recipient.player());
	}

	/** Registers a freshly created copy (rolled or given): new serial written into the stack, held by the recipient. */
	public static UniqueLedger.Copy issue(ItemStack stack, String id, String source, UniqueDrops.Recipient recipient) {
		ServerPlayer player = recipient.player() == null ? null : GuildyServer.player(recipient.player());
		UniqueLedger.Copy copy = ledger.issue(id, source, recipient.guild(), recipient.player(),
				player == null ? "" : player.getName().getString(), System.currentTimeMillis(), Scheduler.ticks());
		UniqueItems.setSerial(stack, copy.serial);
		checkCollections();
		return copy;
	}

	// ================================================================== sightings

	/** Sightings of one scan pass (all players in the same tick): the same serial in two stacks at once is a duplicate. */
	static final class Pass {
		private record Seen(ItemStack stack, String where) {
		}

		private final Map<Long, Seen> seen = new HashMap<>();
	}

	/**
	 * A unique stack seen by the scan. Unregistered stacks (legacy items, bought offers) get a serial now.
	 *
	 * @param carried in the player's inventory or ender chest (he becomes the holder), else in a container he opened
	 * @return true when the stack was modified
	 */
	static boolean see(ItemStack stack, String id, ServerPlayer viewer, boolean carried, Pass pass, String where) {
		long now = System.currentTimeMillis();
		long tick = Scheduler.ticks();
		Guild guild = GuildManager.of(viewer);
		UUID guildId = guild == null ? null : guild.id;
		String name = viewer.getName().getString();
		long serial = UniqueItems.serialOf(stack);
		boolean changed = false;
		if (serial <= 0) {
			String pending = UniqueItems.pendingSource(stack);
			String source = pending.isEmpty() ? UniqueLedger.LEGACY : pending;
			UniqueLedger.Copy copy = ledger.issue(id, source, guildId, viewer.getUUID(), name, now, tick);
			UniqueItems.setSerial(stack, copy.serial);
			Guildy.LOGGER.info("[uniques] {} registered as #{} (source {}, seen with {})", id, copy.serial, source, name);
			serial = copy.serial;
			changed = true;
		} else {
			UniqueLedger.Sighting sighting = ledger.see(serial, id, guildId, viewer.getUUID(), name, carried, UniqueLedger.LEGACY, now, tick);
			if (sighting == UniqueLedger.Sighting.CONFLICT) {
				// Serial of another weapon (item from another world): this copy gets its own serial.
				UniqueLedger.Copy copy = ledger.issue(id, UniqueLedger.LEGACY, guildId, viewer.getUUID(), name, now, tick);
				Guildy.LOGGER.warn("[uniques] serial #{} of {} belongs to another weapon: re-registered as #{}", serial, id, copy.serial);
				UniqueItems.setSerial(stack, copy.serial);
				serial = copy.serial;
				changed = true;
			} else if (sighting == UniqueLedger.Sighting.FOUND) {
				Guildy.LOGGER.info("[uniques] lost copy #{} of {} found again with {}", serial, id, name);
			} else if (sighting == UniqueLedger.Sighting.NEW) {
				Guildy.LOGGER.info("[uniques] unknown copy #{} of {} registered (seen with {})", serial, id, name);
			}
		}
		String place = name + " (" + where + ")";
		Pass.Seen previous = pass.seen.putIfAbsent(serial, new Pass.Seen(stack, place));
		if (previous != null && previous.stack() != stack && ledger.duplicate(serial, id, previous.where(), place, now)) {
			Guildy.LOGGER.warn("[uniques] duplicate unique weapon: copy #{} of {} seen at {} and at {} (items left untouched)", serial, id,
					previous.where(), place);
		}
		return changed;
	}

	/** End of a scan pass (or the server stop): suspected losses confirmed, completed collections announced. */
	static void afterScan(boolean force) {
		for (UniqueLedger.Copy copy : ledger.confirmLosses(Scheduler.ticks(), LOSS_DELAY_TICKS, System.currentTimeMillis(), force)) {
			Guildy.LOGGER.info("[uniques] copy #{} of {} lost ({}): available again", copy.serial, copy.id, copy.lostReason);
		}
		checkCollections();
	}

	static boolean hasPendingLosses() {
		return ledger.hasPendingLosses();
	}

	// ================================================================== losses

	/** An item entity or a thrown trident left the world: a destroyed copy is lost unless it is seen again soon. */
	private static void onRemoved(Entity entity) {
		Entity.RemovalReason reason = entity.getRemovalReason();
		// Chunk unload, dimension change, server stop: never a loss.
		if (reason == null || !reason.shouldDestroy()) {
			return;
		}
		ItemStack stack = entity instanceof ItemEntity item ? item.getItem()
				: entity instanceof AbstractArrow arrow ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
		// Picked up (player, hopper, mob): the entity's stack was emptied first.
		if (stack.isEmpty() || UniqueItems.idOf(stack) == null) {
			return;
		}
		long serial = UniqueItems.serialOf(stack);
		if (serial > 0) {
			ledger.suspectLoss(serial, Scheduler.ticks(), reason == Entity.RemovalReason.KILLED ? "killed" : "destroyed");
		}
	}

	/** A damageable unique broke (ItemStack.hurtAndBreak, mixin): lost at once. */
	public static void onBroken(ItemStack stack) {
		if (UniqueItems.idOf(stack) == null) {
			return;
		}
		long serial = UniqueItems.serialOf(stack);
		if (serial > 0 && ledger.markLost(serial, "broken", System.currentTimeMillis())) {
			Guildy.LOGGER.info("[uniques] copy #{} of {} broke: available again", serial, UniqueItems.idOf(stack));
		}
	}

	// ================================================================== collections

	/** Announces the guilds whose collection just became complete (every usable unique ever obtained). */
	private static void checkCollections() {
		Set<UUID> grown = ledger.drainGrown();
		if (grown.isEmpty()) {
			return;
		}
		UniqueConfig cfg = UniqueModule.config();
		List<String> all = new ArrayList<>();
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			all.add(def.id);
		}
		for (UUID id : grown) {
			Guild guild = GuildManager.get(id);
			if (guild != null && ledger.completes(id, all) && cfg.announceCollection) {
				announceCollection(guild, all.size());
			}
		}
	}

	private static void announceCollection(Guild guild, int total) {
		for (ServerPlayer player : GuildyServer.players()) {
			Msg.send(player, "unique.collection.complete", guild.name, total);
			Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
		}
		Msg.guildTitle(guild, Lang.get("unique.collection.complete.title"), Lang.get("unique.collection.complete.subtitle", total));
	}

	/** Holder text of a copy: "Steve (Dragons)", "Dragons", "Steve" or "-". */
	static String holder(UniqueLedger.Copy copy) {
		Guild guild = copy.guild == null ? null : GuildManager.get(copy.guild);
		String player = copy.playerName == null ? "" : copy.playerName;
		if (guild != null && !player.isEmpty()) {
			return player + " (" + guild.name + ")";
		}
		if (guild != null) {
			return guild.name;
		}
		return player.isEmpty() ? "-" : player;
	}
}
