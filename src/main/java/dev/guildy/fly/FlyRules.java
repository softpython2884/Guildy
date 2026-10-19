package dev.guildy.fly;

import dev.guildy.data.ChunkKey;
import dev.guildy.data.GuildRole;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;

/**
 * Pure rules of the guild flight (tested): may this player fly right now, why not, border warnings, combat and message
 * cooldowns, fall protection.
 */
public final class FlyRules {
	/** Border warnings never look further than one chunk around the player. */
	public static final int MAX_WARNING_BLOCKS = 16;
	/** How far ahead of a moving flyer the border is looked for (blocks). */
	public static final int MAX_LOOKAHEAD_BLOCKS = 24;
	/** Movement extrapolated over this many check intervals. */
	public static final int LOOKAHEAD_STEPS = 6;

	/** Why guild flight is unavailable (NONE = available). Declaration order = order of the checks (the reason shown). */
	public enum Reason {
		NONE(true),
		/** fly.enabled is false. */
		DISABLED(false),
		/** Creative / spectator: the game mode owns the flight, Guildy never touches it. */
		GAME_MODE(true),
		NO_GUILD(false),
		ROLE(false),
		LEVEL(false),
		/** Missing permission node guildy.fly. */
		PERMISSION(false),
		/** A horde (night, test or event wave) attacks the guild. */
		HORDE(true),
		/** Not in a chunk of the own guild (another dimension included). */
		OUTSIDE(true),
		/** Hurt by a monster a short while ago (fly.combatCooldownSeconds). */
		COMBAT(true);

		/**
		 * The reason depends on where / when the player is: the preference may be switched on anyway (flight starts as
		 * soon as the reason goes away). The others are refused when switching it on.
		 */
		public final boolean situational;

		Reason(boolean situational) {
			this.situational = situational;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private FlyRules() {
	}

	/**
	 * The decision, cheapest checks first except the permission, read last of the lasting reasons (it may cost a lookup in
	 * a permission mod).
	 *
	 * @param role       role in the guild, null when the player has no guild
	 * @param horde      a horde attacks the guild and flight is disabled during hordes
	 * @param ownChunk   standing in a chunk of the own guild (claim dimension)
	 * @param combat     hurt by a monster within the combat cooldown
	 */
	public static Reason check(boolean enabled, boolean survival, @Nullable GuildRole role, GuildRole minRole, int guildLevel,
			int minGuildLevel, BooleanSupplier permission, boolean horde, boolean ownChunk, boolean combat) {
		if (!enabled) {
			return Reason.DISABLED;
		}
		if (!survival) {
			return Reason.GAME_MODE;
		}
		if (role == null) {
			return Reason.NO_GUILD;
		}
		if (!role.atLeast(minRole)) {
			return Reason.ROLE;
		}
		if (guildLevel < minGuildLevel) {
			return Reason.LEVEL;
		}
		if (!permission.getAsBoolean()) {
			return Reason.PERMISSION;
		}
		if (horde) {
			return Reason.HORDE;
		}
		if (!ownChunk) {
			return Reason.OUTSIDE;
		}
		return combat ? Reason.COMBAT : Reason.NONE;
	}

	/** Switching the preference on is refused only for lasting reasons (role, level, permission...). */
	public static boolean canSwitchOn(Reason reason) {
		return reason.situational;
	}

	/** Hurt by a monster less than {@code cooldownSeconds} ago ({@code lastHurt} = Long.MIN_VALUE when never). */
	public static boolean inCombat(long now, long lastHurt, int cooldownSeconds) {
		return cooldownSeconds > 0 && lastHurt != Long.MIN_VALUE && now - lastHurt < cooldownSeconds * 20L;
	}

	/** Whole seconds of combat cooldown left (at least 1 while in combat). */
	public static int combatSecondsLeft(long now, long lastHurt, int cooldownSeconds) {
		if (!inCombat(now, lastHurt, cooldownSeconds)) {
			return 0;
		}
		long left = cooldownSeconds * 20L - (now - lastHurt);
		return (int) Math.max(1, (left + 19) / 20);
	}

	/** Message cooldown: {@code last} = Long.MIN_VALUE when never sent. */
	public static boolean ready(long now, long last, int cooldownTicks) {
		return last == Long.MIN_VALUE || now - last >= cooldownTicks;
	}

	/** Flight taken back while flying or off the ground: the next landing must not hurt. */
	public static boolean needsFallProtection(boolean onGround, boolean flying) {
		return flying || !onGround;
	}

	/** The fall protection ends on its deadline, or once the player stood on the ground after it was armed. */
	public static boolean fallProtectionOver(long now, long armedAt, long expiresAt, boolean landed, int graceTicks) {
		return now > expiresAt || landed && now - armedAt >= graceTicks;
	}

	/**
	 * A flyer close to the border of the territory: a chunk not owned within {@code warnBlocks} blocks around (8
	 * directions), or on the path of the current movement ({@code dx, dz} = blocks moved during the last check interval,
	 * extrapolated over {@link #LOOKAHEAD_STEPS} intervals, at most {@link #MAX_LOOKAHEAD_BLOCKS}).
	 */
	public static boolean nearBorder(double x, double z, double dx, double dz, int warnBlocks, LongPredicate owned) {
		if (warnBlocks <= 0) {
			return false;
		}
		int w = Math.min(MAX_WARNING_BLOCKS, warnBlocks);
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				if ((ox != 0 || oz != 0) && !owned.test(chunkAt(x + ox * w, z + oz * w))) {
					return true;
				}
			}
		}
		double speed = Math.sqrt(dx * dx + dz * dz);
		double ahead = Math.min(MAX_LOOKAHEAD_BLOCKS, speed * LOOKAHEAD_STEPS);
		if (ahead <= w) {
			return false;
		}
		double ux = dx / speed;
		double uz = dz / speed;
		for (double step = w; ; step = Math.min(ahead, step + 4)) {
			if (!owned.test(chunkAt(x + ux * step, z + uz * step))) {
				return true;
			}
			if (step >= ahead) {
				return false;
			}
		}
	}

	private static long chunkAt(double x, double z) {
		return ChunkKey.ofBlock((int) Math.floor(x), (int) Math.floor(z));
	}
}
