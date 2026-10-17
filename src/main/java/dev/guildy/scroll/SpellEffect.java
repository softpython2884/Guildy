package dev.guildy.scroll;

import dev.guildy.core.GuildyServer;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * A running spell (animation + gameplay over several ticks), ticked by the {@link EffectRunner}. It belongs to the player
 * who cast it: it ends when that player logs out or changes dimension (unless {@link #bound()} is false), and when the
 * server stops.
 */
abstract class SpellEffect {
	final UUID owner;
	final ServerLevel level;
	final SpellContext ctx;
	final RandomGenerator random = ThreadLocalRandom.current();
	/** Ticks since the start (0 during the first tick). */
	int age;
	private boolean done;

	SpellEffect(SpellContext ctx) {
		this(ctx, ctx.level);
	}

	SpellEffect(SpellContext ctx, ServerLevel level) {
		this.ctx = ctx;
		this.owner = ctx.caster.getUUID();
		this.level = level;
	}

	/** Where the effect is (players within the view distance see its particles). */
	abstract Vec3 anchor();

	/** One tick of animation and gameplay; call {@link #finish()} when over. */
	abstract void tick(Fx fx);

	/** Called once when the effect ends: naturally ({@code cancelled} false), or owner gone / server stopping. */
	void end(boolean cancelled) {
	}

	/** Ends with its owner (logout, dimension change). */
	boolean bound() {
		return true;
	}

	final void finish() {
		done = true;
	}

	final boolean done() {
		return done;
	}

	/** The caster if online (in any level), else null. */
	@Nullable ServerPlayer caster() {
		ServerPlayer player = GuildyServer.player(owner);
		return player == null || player.isRemoved() ? null : player;
	}

	void sound(Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	void sound(Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Smooth 0..1 easing (fast start, slow end). */
	static double easeOut(double t) {
		double k = Math.max(0, Math.min(1, t));
		return 1 - (1 - k) * (1 - k);
	}
}
