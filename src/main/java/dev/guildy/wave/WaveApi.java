package dev.guildy.wave;

import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Public entry points of the wave module for other modules (the event module): night modifiers hook (blood night),
 * extra status lines for the menus, and "event waves" (a horde with a fixed theme started at any time of day, ended by
 * its duration like the admin test waves, with normal victory / defeat).
 */
public final class WaveApi {
	/**
	 * Modifiers of a night or test wave about to start for this guild at this overworld clock. Called once per wave start
	 * (never for event waves, which carry their own). Set by the event module; default: none.
	 */
	public static BiFunction<Guild, Long, NightModifiers> nightModifiers = (guild, clock) -> NightModifiers.NONE;

	/** Extra lines (current / upcoming events) appended to the waves status lore and to the "next night" menu item. */
	public static Function<Guild, List<Component>> eventLore = guild -> List.of();

	/**
	 * A wave started by an event (pillager raid...): fixed theme, ends after {@code durationSeconds} (victory if the heart
	 * holds) or when every mob is dead; never changes the guild's night step nor blocks the night wave.
	 *
	 * @param eventId   id of the event (shown in admin status, {@link Info#eventId()})
	 * @param theme     composition of the horde (mobs, bosses, theme.forceBoss)
	 * @param modifiers balance changes (rewards multipliers...)
	 */
	public record EventWave(String eventId, WaveConfig.Theme theme, int durationSeconds, NightModifiers modifiers) {
	}

	public enum StartResult {
		STARTED,
		/** Waves disabled in waves.json5, peaceful difficulty or no server. */
		UNAVAILABLE,
		NOT_FOUNDED,
		ALREADY_ACTIVE,
		/** No valid mob in the theme. */
		NO_MOB
	}

	/** Read-only snapshot of a running wave. */
	public record Info(String themeId, String themeName, String eventId, int total, int kills, int alive, boolean test,
			NightModifiers modifiers) {
		public boolean isEvent() {
			return !eventId.isEmpty();
		}
	}

	private WaveApi() {
	}

	/** Starts an event wave now (the caller checks that defenders are present when it matters). */
	public static StartResult startEventWave(Guild guild, EventWave wave) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || !WaveModule.config().enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return StartResult.UNAVAILABLE;
		}
		if (!guild.isFounded()) {
			return StartResult.NOT_FOUNDED;
		}
		if (WaveManager.isActive(guild)) {
			return StartResult.ALREADY_ACTIVE;
		}
		int nearby = WaveManager.nearbyMembers(level, guild);
		WaveManager.Request request = WaveManager.Request.event(wave);
		return WaveManager.startWave(level, guild, Math.max(1, nearby), request, level.getOverworldClockTime()) != null
				? StartResult.STARTED : StartResult.NO_MOB;
	}

	public static boolean isActive(Guild guild) {
		return WaveManager.isActive(guild);
	}

	/** Online members near the heart or inside the territory (the rule that starts night hordes). */
	public static int nearbyMembers(Guild guild) {
		return WaveManager.nearbyMembers(GuildyServer.claimLevel(), guild);
	}

	/** Cancels the running wave (no reward, no penalty). @return false when none */
	public static boolean cancel(Guild guild, String reasonKey) {
		return WaveManager.stop(guild, reasonKey);
	}

	public static @Nullable Info info(Guild guild) {
		WaveSession session = WaveManager.session(guild);
		if (session == null) {
			return null;
		}
		return new Info(session.theme().id, session.theme().name, session.eventId(), session.total(), session.kills(), session.alive(),
				session.isTest(), session.modifiers());
	}
}
