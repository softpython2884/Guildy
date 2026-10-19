package dev.guildy.fly;

import dev.guildy.command.GuildActions.Denial;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.FlightGrants;
import dev.guildy.core.FlightOwnership.Owner;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.fly.FlyRules.Reason;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /guild fly: vanilla flight ({@code abilities.mayfly}, owner {@link Owner#GUILD_FLY} of {@link FlightGrants}) for the
 * members who switched it on, while they stand in a chunk of their own guild and no horde attacks it (rules:
 * {@link FlyRules}). The preference is an entity tag ({@link #PREFERENCE_TAG}, saved with the player). Only the players
 * with the preference are evaluated, every {@link #INTERVAL} ticks (spread by entity id), plus right away on the guild
 * events (horde started, member left, guild disbanded, territory changed) and on a config reload.
 *
 * <p>Flight taken back in the air arms a fall guard: the next fall damage within {@code fly.fallProtectionSeconds} is
 * cancelled (ALLOW_DAMAGE), and the guard ends once the player stood on the ground. A player logging in mid-air with the
 * preference on (logout or crash while flying: the grant was taken back) gets the same guard.
 */
public final class GuildFly {
	/** Entity tag of the preference (saved with the player, kept on respawn). */
	public static final String PREFERENCE_TAG = "guildy_fly";
	/** Permission node (granted by default when no permission mod says otherwise). */
	public static final String PERMISSION = "guildy.fly";
	/** Each player with the preference is evaluated every INTERVAL ticks. */
	static final int INTERVAL = 5;
	private static final int PERMISSION_CACHE_TICKS = 40;
	private static final int NOTICE_COOLDOWN = 40;
	private static final int BORDER_WARNING_COOLDOWN = 60;
	private static final int STOP_MESSAGE_COOLDOWN = 20;
	/** A move longer than this between two checks is a teleport, not a flight (no border look-ahead). */
	private static final double TELEPORT_BLOCKS = 20;

	/** Online players with the preference on. */
	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Players protected from their next fall. */
	private static final Map<UUID, FallGuard> FALL_GUARDS = new HashMap<>();

	private static final class State {
		long lastHurt = Long.MIN_VALUE;
		long permissionAt = Long.MIN_VALUE;
		boolean permission;
		long lastNotice = Long.MIN_VALUE;
		long lastWarning = Long.MIN_VALUE;
		long lastStopMessage = Long.MIN_VALUE;
		boolean hasLast;
		double lastX;
		double lastZ;
	}

	private record FallGuard(long armedAt, long expiresAt) {
	}

	private GuildFly() {
	}

	static void register() {
		Scheduler.every("fly.tick", 1, 0, GuildFly::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				Safe.get("fly.fall", () -> allowDamage(entity, source), true));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && !STATES.isEmpty()) {
				Safe.run("fly.hurt", () -> onHurt(player, source));
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("fly.join", () -> onJoin(handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("fly.leave", () -> forget(handler.getPlayer().getUUID())));
		GuildEvents.WAVE_STARTED.add(GuildFly::evaluateMembers);
		GuildEvents.TERRITORY_CHANGED.add(GuildFly::evaluateMembers);
		GuildEvents.MEMBER_LEFT.add((guild, id) -> {
			ServerPlayer player = GuildyServer.player(id);
			if (player != null) {
				evaluateNow(player);
			}
		});
		// Fired before the guild is removed (still indexed): stop the members' flight explicitly.
		GuildEvents.DISBANDED.add(guild -> {
			for (ServerPlayer player : guild.onlineMembers()) {
				Safe.run("fly.disband", () -> stop(player, STATES.get(player.getUUID()), Reason.NO_GUILD, Scheduler.ticks()));
			}
		});
		Lifecycle.RELOADED.add(() -> {
			for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
				evaluateNow(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			FALL_GUARDS.clear();
		});
	}

	// ================================================================== queries (menus, commands, tests)

	public static boolean preference(ServerPlayer player) {
		return player.entityTags().contains(PREFERENCE_TAG);
	}

	/** Guild flight currently holds the player's mayfly. */
	public static boolean granted(ServerPlayer player) {
		return FlightGrants.holds(player, Owner.GUILD_FLY);
	}

	/** The next fall of the player will not hurt (flight taken back in the air). */
	public static boolean fallProtected(ServerPlayer player) {
		return FALL_GUARDS.containsKey(player.getUUID());
	}

	/** Why flight is unavailable for this player right now (NONE = available). No side effect. */
	public static Reason status(ServerPlayer player) {
		return reason(player, GuildManager.of(player), STATES.get(player.getUUID()), config(), Scheduler.ticks());
	}

	/** Text of a reason with its arguments (role, level, seconds left). */
	public static Component reasonText(ServerPlayer player, Reason reason) {
		return Lang.get("fly.reason." + reason.id(), args(player, reason));
	}

	/** Territory menu: why the preference cannot be switched on (null when it can, or when it is on). */
	public static @Nullable Denial switchOnDenial(ServerPlayer player) {
		if (preference(player)) {
			return null;
		}
		Reason reason = status(player);
		return FlyRules.canSwitchOn(reason) ? null : new Denial("fly.refused", reasonText(player, reason));
	}

	/** Territory menu lore: what it does, the current state and the click hint. */
	public static List<Component> menuLore(ServerPlayer player) {
		List<Component> lore = new ArrayList<>();
		for (String line : Lang.raw("menu.territory.fly.lore").split("\n")) {
			lore.add(Component.literal(line));
		}
		lore.add(Component.empty());
		Reason reason = status(player);
		lore.add(reason == Reason.NONE ? Lang.get("menu.territory.fly.state.ok")
				: Lang.get("menu.territory.fly.state.blocked", reasonText(player, reason)));
		lore.add(Lang.get(preference(player) ? "menu.territory.fly.click.off" : "menu.territory.fly.click.on"));
		return lore;
	}

	// ================================================================== preference

	public static boolean toggle(ServerPlayer player) {
		return setPreference(player, !preference(player));
	}

	/**
	 * Switches the preference. Switching on is refused for lasting reasons (disabled, no guild, role, level, permission);
	 * otherwise flight starts now if possible, or as soon as the situation allows it. Switching off takes the flight
	 * back (fall guard when in the air).
	 *
	 * @return true when the preference is as asked afterwards
	 */
	public static boolean setPreference(ServerPlayer player, boolean on) {
		long now = Scheduler.ticks();
		if (!on) {
			player.removeTag(PREFERENCE_TAG);
			STATES.remove(player.getUUID());
			takeBack(player, now);
			Msg.send(player, "fly.off");
			return true;
		}
		State state = STATES.get(player.getUUID());
		Reason reason = reason(player, GuildManager.of(player), state, config(), now);
		if (!FlyRules.canSwitchOn(reason)) {
			Msg.send(player, "fly.refused", reasonText(player, reason));
			return false;
		}
		player.addTag(PREFERENCE_TAG);
		if (state == null) {
			state = new State();
			STATES.put(player.getUUID(), state);
		}
		reason = evaluate(player, state, false);
		if (reason == Reason.NONE) {
			Msg.send(player, "fly.on");
		} else {
			Msg.send(player, "fly.on.waiting", reasonText(player, reason));
		}
		return true;
	}

	/** Re-evaluates a player with the preference right away (no-op without it). */
	public static void evaluateNow(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state != null) {
			Safe.run("fly.evaluate", () -> evaluate(player, state, true));
		}
	}

	// ================================================================== evaluation

	private static void tick() {
		long now = Scheduler.ticks();
		if (!FALL_GUARDS.isEmpty() && now % INTERVAL == 0) {
			tickFallGuards(now);
		}
		if (STATES.isEmpty()) {
			return;
		}
		int phase = (int) (now % INTERVAL);
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		List<ServerPlayer> due = null;
		while (it.hasNext()) {
			Map.Entry<UUID, State> entry = it.next();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			if (player == null) {
				it.remove();
			} else if (Math.floorMod(player.getId(), INTERVAL) == phase) {
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(player);
			}
		}
		if (due != null) {
			for (ServerPlayer player : due) {
				evaluateNow(player);
			}
		}
	}

	private static Reason evaluate(ServerPlayer player, State state, boolean notify) {
		GuildyConfig.Fly cfg = config();
		long now = Scheduler.ticks();
		Guild guild = GuildManager.of(player);
		Reason reason = reason(player, guild, state, cfg, now);
		boolean held = FlightGrants.holds(player, Owner.GUILD_FLY);
		if (reason == Reason.NONE) {
			// Also gives mayfly back to a holder that lost it (respawn, another mod); refused over a foreign flight.
			boolean holds = FlightGrants.grant(player, Owner.GUILD_FLY);
			if (holds && !held && notify && FlyRules.ready(now, state.lastNotice, NOTICE_COOLDOWN)) {
				state.lastNotice = now;
				Msg.actionBar(player, "fly.available");
			}
			if (holds && guild != null && player.getAbilities().flying) {
				warnBorder(player, guild, state, cfg, now);
			}
		} else if (reason == Reason.GAME_MODE) {
			if (held) {
				FlightGrants.forget(player, Owner.GUILD_FLY); // creative / spectator: the game mode owns the flight now
			}
		} else if (held) {
			stop(player, state, reason, now);
		}
		state.lastX = player.getX();
		state.lastZ = player.getZ();
		state.hasLast = true;
		return reason;
	}

	private static Reason reason(ServerPlayer player, @Nullable Guild guild, @Nullable State state, GuildyConfig.Fly cfg, long now) {
		GuildRole role = guild == null ? null : guild.role(player.getUUID());
		boolean horde = cfg.disableDuringWaves && guild != null && Hooks.isWaveActive.test(guild);
		boolean own = guild != null && GuildyServer.isClaimLevel(player.level())
				&& guild.owns(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()));
		boolean combat = state != null && FlyRules.inCombat(now, state.lastHurt, cfg.combatCooldownSeconds);
		return FlyRules.check(cfg.enabled, FlightGrants.manageable(player), role, cfg.role(), guild == null ? 0 : guild.level,
				cfg.minGuildLevel, () -> permission(player, state, now), horde, own, combat);
	}

	private static boolean permission(ServerPlayer player, @Nullable State state, long now) {
		if (state != null && state.permissionAt != Long.MIN_VALUE && now - state.permissionAt < PERMISSION_CACHE_TICKS) {
			return state.permission;
		}
		boolean allowed = Permissions.check(player, PERMISSION, true);
		if (state != null) {
			state.permission = allowed;
			state.permissionAt = now;
		}
		return allowed;
	}

	private static void warnBorder(ServerPlayer player, Guild guild, State state, GuildyConfig.Fly cfg, long now) {
		if (cfg.borderWarningBlocks <= 0 || !FlyRules.ready(now, state.lastWarning, BORDER_WARNING_COOLDOWN)) {
			return;
		}
		double dx = state.hasLast ? player.getX() - state.lastX : 0;
		double dz = state.hasLast ? player.getZ() - state.lastZ : 0;
		if (dx * dx + dz * dz > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
			dx = 0;
			dz = 0;
		}
		if (FlyRules.nearBorder(player.getX(), player.getZ(), dx, dz, cfg.borderWarningBlocks, guild::owns)) {
			state.lastWarning = now;
			Msg.actionBar(player, "fly.border");
		}
	}

	/** Every online member with the preference (horde started, territory changed). */
	private static void evaluateMembers(Guild guild) {
		if (STATES.isEmpty()) {
			return;
		}
		for (ServerPlayer player : guild.onlineMembers()) {
			evaluateNow(player);
		}
	}

	// ================================================================== taking the flight back

	/** Takes guild flight back for {@code reason}: fall guard when in the air, short message (cooldown). */
	private static void stop(ServerPlayer player, @Nullable State state, Reason reason, long now) {
		if (!takeBack(player, now)) {
			return;
		}
		if (state == null || FlyRules.ready(now, state.lastStopMessage, STOP_MESSAGE_COOLDOWN)) {
			if (state != null) {
				state.lastStopMessage = now;
			}
			Msg.actionBar(player, "fly.stopped." + reason.id(), args(player, reason));
		}
		if (reason == Reason.HORDE) {
			Msg.send(player, "fly.horde.chat");
		}
	}

	/** @return true when guild flight held the grant (now released) */
	private static boolean takeBack(ServerPlayer player, long now) {
		boolean airborne = FlyRules.needsFallProtection(player.onGround(), player.getAbilities().flying);
		if (!FlightGrants.release(player, Owner.GUILD_FLY)) {
			return false;
		}
		if (airborne && FlightGrants.manageable(player)) {
			armFallGuard(player, now);
		}
		return true;
	}

	private static void armFallGuard(ServerPlayer player, long now) {
		FALL_GUARDS.put(player.getUUID(), new FallGuard(now, now + config().fallProtectionSeconds * 20L));
	}

	private static void tickFallGuards(long now) {
		Iterator<Map.Entry<UUID, FallGuard>> it = FALL_GUARDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, FallGuard> entry = it.next();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			FallGuard guard = entry.getValue();
			if (player == null || FlyRules.fallProtectionOver(now, guard.armedAt(), guard.expiresAt(),
					player.onGround() || player.isInWater() || player.onClimbable(), INTERVAL)) {
				it.remove();
			}
		}
	}

	/** The guarded fall: cancelled once (the guard is used up), within the protection time. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (FALL_GUARDS.isEmpty() || !(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
			return true;
		}
		FallGuard guard = FALL_GUARDS.remove(player.getUUID());
		return guard == null || Scheduler.ticks() > guard.expiresAt();
	}

	private static void onHurt(ServerPlayer player, DamageSource source) {
		State state = STATES.get(player.getUUID());
		if (state != null && config().combatCooldownSeconds > 0
				&& (source.getEntity() instanceof Enemy || source.getDirectEntity() instanceof Enemy)) {
			state.lastHurt = Scheduler.ticks();
		}
	}

	// ================================================================== login / logout

	/**
	 * Login: the preference is read from the tag; a player saved in the air with it (logout or crash while flying: the
	 * grant was taken back, see {@link FlightGrants}) is protected from the fall. The next check grants flight again if
	 * allowed.
	 */
	private static void onJoin(ServerPlayer player) {
		if (!preference(player)) {
			return;
		}
		STATES.put(player.getUUID(), new State());
		if (FlightGrants.manageable(player) && !player.onGround()) {
			armFallGuard(player, Scheduler.ticks());
		}
	}

	private static void forget(UUID player) {
		STATES.remove(player);
		FALL_GUARDS.remove(player);
	}

	// ================================================================== helpers

	private static Object[] args(ServerPlayer player, Reason reason) {
		GuildyConfig.Fly cfg = config();
		return switch (reason) {
			case ROLE -> new Object[]{Lang.get(cfg.role().langKey())};
			case LEVEL -> new Object[]{cfg.minGuildLevel};
			case COMBAT -> {
				State state = STATES.get(player.getUUID());
				yield new Object[]{state == null ? 0
						: FlyRules.combatSecondsLeft(Scheduler.ticks(), state.lastHurt, cfg.combatCooldownSeconds)};
			}
			default -> new Object[0];
		};
	}

	private static GuildyConfig.Fly config() {
		return GuildyServer.config().fly;
	}
}
