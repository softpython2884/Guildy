package dev.guildy.upgrade;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Territory buffs: every 2 seconds, online members standing in their own territory get Haste (territory_haste) and,
 * while their base is under attack, Regeneration (wave_regen). Short ambient effects without particles, renewed only
 * when about to expire; a stronger effect from another source is never replaced.
 */
final class UpgradeBuffs {
	static final int INTERVAL_TICKS = 40;
	/** Renew when at most this many ticks are left (>= the interval, so the effect never lapses). */
	private static final int RENEW_BELOW_TICKS = INTERVAL_TICKS;

	private UpgradeBuffs() {
	}

	static void init() {
		Scheduler.every("upgrade.buffs", INTERVAL_TICKS, 13, UpgradeBuffs::tick);
	}

	private static void tick() {
		UpgradeConfig config = UpgradeModule.config();
		if (config == null || config.tree().nodes().isEmpty()) {
			return;
		}
		int duration = config.buffs.durationSeconds * 20;
		for (ServerPlayer player : GuildyServer.players()) {
			Safe.run("upgrade.buffs", () -> apply(player, duration));
		}
	}

	private static void apply(ServerPlayer player, int duration) {
		if (player.isSpectator() || !player.isAlive() || !GuildyServer.isClaimLevel(player.level())) {
			return;
		}
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return;
		}
		int haste = Upgrades.count(guild, UpgradeEffects.TERRITORY_HASTE);
		int regen = Hooks.isWaveActive.test(guild) ? Upgrades.count(guild, UpgradeEffects.WAVE_REGEN) : 0;
		if ((haste <= 0 && regen <= 0) || !guild.owns(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()))) {
			return;
		}
		if (haste > 0) {
			renew(player, MobEffects.HASTE, haste - 1, duration);
		}
		if (regen > 0) {
			renew(player, MobEffects.REGENERATION, regen - 1, duration);
		}
	}

	private static void renew(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int duration) {
		MobEffectInstance current = player.getEffect(effect);
		if (current != null && (current.getAmplifier() > amplifier
				|| current.getAmplifier() == amplifier && !current.endsWithin(RENEW_BELOW_TICKS))) {
			return;
		}
		// ambient (beacon-like), no particles, icon shown
		player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
	}
}
