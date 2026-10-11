package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Nouveau parcours": while active, each right-click on a block adds a waypoint (the spot above it) to the player's
 * route draft, with action bar feedback and a particle preview; sneak + right-click (or [Terminer] in the chat)
 * saves it (2 points at least). Cancelled after {@code routes.selectionTimeoutSeconds} without a new point.
 */
final class RouteSelection {
	/** Ignore repeated clicks (held button) closer than this. */
	private static final long CLICK_DEBOUNCE_TICKS = 6;

	private static final class Draft {
		final UUID guildId;
		/** Route redrawn by this draft, -1 for a new route. */
		final int routeId;
		final List<BlockPos> points = new ArrayList<>();
		long lastAction;
		long lastClick = -100;

		Draft(UUID guildId, int routeId, long now) {
			this.guildId = guildId;
			this.routeId = routeId;
			this.lastAction = now;
		}
	}

	/** Player → route draft (not persisted). */
	private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

	private RouteSelection() {
	}

	static boolean active(UUID player) {
		return DRAFTS.containsKey(player);
	}

	/** Waypoints placed so far (preview), or null. */
	static @Nullable List<BlockPos> points(UUID player) {
		Draft draft = DRAFTS.get(player);
		return draft == null ? null : draft.points;
	}

	/** Starts a new route ({@code route} null) or redraws one. */
	static void start(ServerPlayer player, Guild guild, @Nullable PatrolRoute route) {
		VillagerInteractions.cancelStorageSelection(player);
		DRAFTS.put(player.getUUID(), new Draft(guild.id, route == null ? -1 : route.id, Scheduler.ticks()));
		VillagerConfig.Routes config = VillagerModule.config().routes;
		MutableComponent message = Msg.prefixed(Lang.get(route == null ? "villager.route.select.start" : "villager.route.select.redraw",
						config.maxWaypoints, route == null ? "" : route.name))
				.append(Component.literal("\n"))
				.append(Msg.button("villager.route.button.finish", "/guild villagers route finish", "villager.route.button.finish.hover"))
				.append(Component.literal(" "))
				.append(Msg.button("villager.route.button.undo", "/guild villagers route undo", "villager.route.button.undo.hover"))
				.append(Component.literal(" "))
				.append(Msg.button("villager.route.button.cancel", "/guild villagers route cancel", "villager.route.button.cancel.hover"));
		player.sendSystemMessage(message);
		Msg.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8f, 1.0f);
	}

	/** Right-click on a block while drawing: adds a waypoint; sneaking finishes. */
	static InteractionResult onUseBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		Draft draft = DRAFTS.get(player.getUUID());
		if (draft == null) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		// The client may have predicted a placement or an interaction: undo it.
		player.connection.send(new ClientboundBlockUpdatePacket(level, clicked));
		player.connection.send(new ClientboundBlockUpdatePacket(level, clicked.relative(hit.getDirection())));
		player.containerMenu.sendAllDataToRemote();
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		long now = Scheduler.ticks();
		if (now - draft.lastClick < CLICK_DEBOUNCE_TICKS) {
			return InteractionResult.FAIL;
		}
		draft.lastClick = now;
		if (player.isShiftKeyDown()) {
			finish(player);
			return InteractionResult.SUCCESS;
		}
		Guild guild = guildOf(player, draft);
		if (guild == null) {
			return InteractionResult.FAIL;
		}
		VillagerConfig config = VillagerModule.config();
		if (draft.points.size() >= config.routes.maxWaypoints) {
			Msg.actionBar(player, "villager.route.select.full", config.routes.maxWaypoints);
			GuiUtil.errorSound(player);
			return InteractionResult.FAIL;
		}
		BlockPos point = clicked.above();
		String error = Zones.checkPlacement(level, guild, point);
		if (error != null) {
			Msg.actionBar(player, Lang.get(error, config.zones.maxZoneDistance));
			GuiUtil.errorSound(player);
			return InteractionResult.FAIL;
		}
		if (!draft.points.isEmpty() && draft.points.getLast().equals(point)) {
			return InteractionResult.FAIL;
		}
		draft.points.add(point.immutable());
		draft.lastAction = now;
		int count = draft.points.size();
		Msg.actionBar(player, count >= 2 ? "villager.route.select.added_more" : "villager.route.select.added",
				count, config.routes.maxWaypoints, point.getX(), point.getY(), point.getZ());
		Msg.sound(player, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 0.8f + Math.min(1.2f, count * 0.08f));
		ZoneVisualizer.previewNow(player);
		return InteractionResult.SUCCESS;
	}

	/** Saves the draft (2 points at least) and opens the route menu. */
	static void finish(ServerPlayer player) {
		Draft draft = DRAFTS.get(player.getUUID());
		if (draft == null) {
			Msg.send(player, "villager.route.select.none");
			return;
		}
		if (draft.points.size() < 2) {
			Msg.send(player, "villager.route.select.too_short");
			GuiUtil.errorSound(player);
			return;
		}
		Guild guild = guildOf(player, draft);
		if (guild == null) {
			return;
		}
		PatrolRoute route = guild.villagers.route(draft.routeId);
		if (route == null) {
			String limit = Zones.canCreateRoute(guild);
			if (limit != null) {
				// The draft is kept: free a slot (menu) and click [Terminer] again.
				Msg.send(player, limit, VillagerModule.config().routes.maxRoutes);
				GuiUtil.errorSound(player);
				return;
			}
		}
		DRAFTS.remove(player.getUUID());
		if (route != null) {
			Zones.redraw(guild, route, draft.points);
			Msg.send(player, "villager.route.redrawn", route.name, draft.points.size());
		} else {
			route = Zones.createRoute(guild, draft.points);
			Msg.guild(guild, "villager.route.created", player.getName().getString(), route.name, draft.points.size(), route.length());
		}
		Msg.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8f, 1.2f);
		ZoneVisualizer.showRoute(player, guild, route);
		ZoneGui.openRoute(player, guild, route.id);
	}

	static void undo(ServerPlayer player) {
		Draft draft = DRAFTS.get(player.getUUID());
		if (draft == null) {
			Msg.send(player, "villager.route.select.none");
			return;
		}
		if (!draft.points.isEmpty()) {
			draft.points.removeLast();
		}
		draft.lastAction = Scheduler.ticks();
		Msg.actionBar(player, "villager.route.select.undone", draft.points.size(), VillagerModule.config().routes.maxWaypoints);
		ZoneVisualizer.previewNow(player);
	}

	static void cancel(ServerPlayer player) {
		if (DRAFTS.remove(player.getUUID()) != null) {
			Msg.send(player, "villager.route.select.cancelled");
		} else {
			Msg.send(player, "villager.route.select.none");
		}
	}

	/** The player's guild if it is still the draft's one and the player may still manage villagers. */
	private static @Nullable Guild guildOf(ServerPlayer player, Draft draft) {
		Guild guild = GuildManager.get(draft.guildId);
		if (guild == null || GuildManager.of(player) != guild || !guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			DRAFTS.remove(player.getUUID());
			Msg.send(player, "error.no_permission");
			return null;
		}
		return guild;
	}

	/** Once per second: drafts left idle too long are cancelled. */
	static void tick() {
		if (DRAFTS.isEmpty()) {
			return;
		}
		long timeout = WorkContext.ticks(VillagerModule.config().routes.selectionTimeoutSeconds);
		Iterator<Map.Entry<UUID, Draft>> it = DRAFTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Draft> entry = it.next();
			if (Scheduler.ticks() - entry.getValue().lastAction > timeout) {
				it.remove();
				ServerPlayer player = GuildyServer.player(entry.getKey());
				if (player != null) {
					Msg.send(player, "villager.route.select.expired");
				}
			}
		}
	}

	/** Drafts of online players (preview particles). */
	static Map<UUID, List<BlockPos>> drafts() {
		Map<UUID, List<BlockPos>> map = new HashMap<>();
		for (Map.Entry<UUID, Draft> entry : DRAFTS.entrySet()) {
			map.put(entry.getKey(), entry.getValue().points);
		}
		return map;
	}

	static void cancelGuild(Guild guild) {
		DRAFTS.values().removeIf(draft -> draft.guildId.equals(guild.id));
	}

	static void forget(UUID player) {
		DRAFTS.remove(player);
	}

	static void clear() {
		DRAFTS.clear();
	}
}
