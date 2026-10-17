package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.ResurrectionSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The resurrection of a guardian killed too fast: about five seconds during which it is invulnerable and frozen (no AI),
 * its body sinks into the ground then lifts and spins while its health bar fills up again, choreographed every tick
 * within the particle budget of a {@link RitualFx} (long-distance particles: visible from afar):
 * <ul>
 *     <li>a spiral of soul fire and totem-of-undying sparks rising around it, tightening;</li>
 *     <li>a column of light falling from the sky (end rods, glow, golden rings, a flash at its top);</li>
 *     <li>rings of red and gold dust collapsing into it, sparks rushing to its heart;</li>
 *     <li>souls drawn from the spots where its escort died (or from the ruin around it);</li>
 *     <li>lightning flashes around the arena (visual only: never on copper or a lightning rod) and sounds building up
 *     (totem, beacon, wither, ender dragon, a racing heartbeat);</li>
 *     <li>its return: flashes, sonic booms, an explosion of light and a shockwave pushing the players back (no damage).</li>
 * </ul>
 * The fight logic (statistics of the risen guardian, boss bar, escort, messages) is in {@link Encounters}.
 */
final class Resurrection {
	static final int RED = 0xFF1A2B;
	static final int GOLD = 0xFFC233;
	static final int SOUL = 0x7FE8FF;
	static final int CRIMSON = 0x8E0012;
	private static final double COLUMN_HEIGHT = 30;
	private static final double RING_RADIUS = 6.5;
	private static final int RING_LIFE = 18;
	private static final int RING_EVERY = 7;
	private static final int MAX_SOULS = 8;
	private static final double[] BOLTS = {0.3, 0.55, 0.8};
	/** Extra ticks after which a resurrection that never ended is finished anyway (errors). */
	static final int GRACE_TICKS = 40;

	/** Feet position at the death (on the floor below when it died in the air). */
	final Vec3 anchor;
	final int duration;
	/** How deep the body sinks, how high it lifts (limited by the free space above it). */
	final double sink;
	final double lift;
	/** Scale multiplier of the risen guardian (limited by the free space above it). */
	final double scale;
	final List<Vec3> souls;
	final int theme;
	private final double height;
	private final ParticleOptions floor;
	private final SplittableRandom random = new SplittableRandom();
	int age;
	private float yaw;
	private int nextBeat;

	Resurrection(ServerLevel level, Mob boss, Encounter encounter, ResurrectionSettings cfg) {
		this.duration = SummonRules.ticks(cfg.durationSeconds);
		this.height = Math.max(0.5, boss.getBbHeight());
		BlockPos floorPos = floor(level, boss.blockPosition());
		this.anchor = floorPos != null ? new Vec3(boss.getX(), floorPos.getY() + 1.0, boss.getZ()) : boss.position();
		BlockState state = floorPos != null ? level.getBlockState(floorPos) : Blocks.STONE.defaultBlockState();
		this.floor = new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() ? Blocks.STONE.defaultBlockState() : state);
		double room = headroom(level, BlockPos.containing(anchor), 12);
		this.sink = Math.min(0.9, height * 0.35);
		this.lift = Math.max(0, Math.min(1.6, room - height - 0.2));
		this.scale = Math.max(1.0, Math.min(cfg.scale, (room - 0.3) / height));
		this.theme = SummonRules.themeColor(encounter.def.bossBarColor);
		this.yaw = boss.getYRot();
		List<Vec3> spots = new ArrayList<>(encounter.fallen);
		if (spots.size() > MAX_SOULS) {
			spots = spots.subList(spots.size() - MAX_SOULS, spots.size());
		}
		if (spots.isEmpty()) {
			// No fallen escort: the souls rise from the ruin all around.
			for (int i = 0; i < 6; i++) {
				double angle = Math.PI * 2 * i / 6 + random.nextDouble() * 0.4;
				double dist = 5 + random.nextDouble() * 2.5;
				spots.add(new Vec3(anchor.x + Math.cos(angle) * dist, anchor.y, anchor.z + Math.sin(angle) * dist));
			}
		}
		this.souls = List.copyOf(spots);
	}

	double progress() {
		return Math.min(1.0, (double) age / Math.max(1, duration));
	}

	/** First solid block at or under {@code pos} (within 4 blocks), null when it floats higher. */
	private static BlockPos floor(ServerLevel level, BlockPos pos) {
		for (int dy = 0; dy <= 4; dy++) {
			BlockPos below = pos.below(dy + 1);
			BlockState state = level.getBlockState(below);
			if (!state.isAir() && state.getFluidState().isEmpty() && state.isFaceSturdy(level, below, Direction.UP)) {
				return below;
			}
		}
		return null;
	}

	/** Free blocks above {@code pos} (up to {@code max}). */
	static int headroom(ServerLevel level, BlockPos pos, int max) {
		for (int dy = 0; dy < max; dy++) {
			BlockPos cell = pos.above(dy);
			if (!level.isLoaded(cell) || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				return dy;
			}
		}
		return max;
	}

	// ================================================================== lifecycle

	/** Death refused: the guardian is frozen and invulnerable, a first burst of souls and totem sparks. */
	void begin(ServerLevel level, Mob boss, RitualFx fx) {
		boss.setNoAi(true);
		boss.setPermanentlyInvulnerable(true);
		boss.stopUsingItem();
		boss.getNavigation().stop();
		boss.setTarget(null);
		boss.clearFire();
		boss.setDeltaMovement(Vec3.ZERO);
		Vec3 core = boss.position().add(0, height / 2, 0);
		fx.burst(ParticleTypes.TOTEM_OF_UNDYING, core, 80, 0.6, 0.5);
		fx.burst(ParticleTypes.SOUL, core, 30, 0.5, 0.05);
		fx.burst(ParticleTypes.LARGE_SMOKE, core, 20, 0.5, 0.03);
		fx.dot(RitualFx.flash(RED), core);
		sound(level, SoundEvents.TOTEM_USE, 2.0F, 0.5F);
		sound(level, SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.5F);
		sound(level, SoundEvents.AMBIENT_CAVE.value(), 3.0F, 0.6F);
		nextBeat = 10;
	}

	/** One tick: body, health, sounds, lightning and particles. @return true when the sequence is over */
	boolean tick(ServerLevel level, Mob boss, RitualFx fx, ResurrectionSettings cfg) {
		age++;
		double p = progress();
		double y = anchor.y + ThreatRules.bodyOffset(p, sink, lift);
		yaw += (float) (3 + 33 * p);
		boss.setPos(anchor.x, y, anchor.z);
		boss.setDeltaMovement(Vec3.ZERO);
		boss.setYRot(yaw);
		boss.setYBodyRot(yaw);
		boss.setYHeadRot(yaw);
		boss.setHealth(ThreatRules.risingHealth(p, boss.getMaxHealth(), cfg.healthRatio));
		Vec3 core = new Vec3(anchor.x, y + height * 0.5, anchor.z);
		cues(level, p);
		lightning(level);
		if (p <= ThreatRules.SINK_END + 0.05) {
			drawGround(fx);
		}
		drawSpiral(fx, y, p);
		if (p >= 0.15) {
			drawColumn(fx, core, SummonRules.phase(p, 0.15, 1.0));
		}
		if (p >= 0.05 && p <= 0.92) {
			drawRings(fx, core);
		}
		if (p >= 0.12 && p <= 0.88) {
			drawSouls(fx, core);
		}
		if (p >= ThreatRules.SINK_END) {
			fx.burst(RitualFx.fade(RED, CRIMSON, 1.2F), core.x, core.y, core.z, 3, 0.5, height * 0.3, 0.5, 0.0);
		}
		return age >= duration;
	}

	/** The guardian may move and be hurt again, back on the floor. */
	void release(Mob boss) {
		boss.setPos(anchor.x, anchor.y, anchor.z);
		boss.setDeltaMovement(Vec3.ZERO);
		boss.setNoAi(false);
		boss.setPermanentlyInvulnerable(false);
	}

	/** Its return: flashes, sonic booms, an explosion of light and the shockwave (players pushed back, no damage). */
	void land(ServerLevel level, Mob boss, RitualFx fx, ResurrectionSettings cfg) {
		double h = Math.max(0.5, boss.getBbHeight());
		Vec3 core = anchor.add(0, h * 0.5, 0);
		fx.dot(RitualFx.flash(RED), core);
		fx.dot(RitualFx.flash(0xFFFFFF), core.add(0, 1.5, 0));
		fx.dot(RitualFx.flash(GOLD), core.add(0, 3.0, 0));
		fx.burst(ParticleTypes.EXPLOSION_EMITTER, core, 1, 0, 0);
		fx.dot(ParticleTypes.SONIC_BOOM, core);
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2 * i / 12;
			fx.dot(ParticleTypes.SONIC_BOOM, anchor.x + Math.cos(angle) * 3.5, anchor.y + 1.0, anchor.z + Math.sin(angle) * 3.5);
		}
		fx.sphereBurst(RitualFx.fade(GOLD, RED, 1.8F), core, 0.5, 120, 0.5);
		fx.burst(ParticleTypes.TOTEM_OF_UNDYING, core, 120, 0.8, 0.7);
		fx.burst(ParticleTypes.SOUL_FIRE_FLAME, core, 60, 0.7, 0.2);
		fx.burst(ParticleTypes.END_ROD, core, 40, 0.5, 0.4);
		fx.burst(floor, anchor.x, anchor.y + 0.2, anchor.z, 60, 2.5, 0.1, 2.5, 0.35);
		fx.ring(RitualFx.fade(RED, GOLD, 1.8F), anchor.add(0, 0.15, 0), 3.0, 40, 0);
		sound(level, SoundEvents.TOTEM_USE, 3.0F, 0.7F);
		sound(level, SoundEvents.WITHER_SPAWN, 1.5F, 0.9F);
		level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
		sound(level, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.8F);
		sound(level, SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
		sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 5.0F, 0.7F);
		Vec3 at = anchor;
		ParticleOptions debris = floor;
		ParticleOptions blood = RitualFx.fade(RED, GOLD, 1.5F);
		double radius = cfg.shockwaveRadius;
		RuinFx.play(level, (lvl, tick) -> {
			double r = 1.0 + tick * Math.max(0.6, radius / 10.0);
			RuinFx.ring(lvl, debris, at.add(0, 0.1, 0), r, (int) (12 + r * 4), 0.1);
			RuinFx.ring(lvl, blood, at.add(0, 0.4, 0), r, (int) (8 + r * 3), 0.05);
			if (tick % 2 == 0) {
				RuinFx.ring(lvl, ParticleTypes.CLOUD, at.add(0, 0.2, 0), r, (int) (8 + r * 2), 0.05);
			}
			return tick < 12;
		});
		if (radius > 0 && cfg.shockwaveStrength > 0) {
			for (ServerPlayer player : level.players()) {
				if (!BossAbilities.valid(player, level)) {
					continue;
				}
				double dx = player.getX() - anchor.x;
				double dz = player.getZ() - anchor.z;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > radius || Math.abs(player.getY() - anchor.y) > 4) {
					continue;
				}
				Vec3 away = dist < 1.0E-3 ? new Vec3(1, 0, 0) : new Vec3(dx / dist, 0, dz / dist);
				double strength = cfg.shockwaveStrength * (1.0 - 0.6 * dist / radius);
				player.push(away.x * strength, 0.3 + 0.15 * strength, away.z * strength);
				player.syncVelocity = true;
			}
		}
	}

	// ================================================================== sounds and lightning

	private void cues(ServerLevel level, double p) {
		if (age == at(0.2)) {
			sound(level, SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.5F);
		}
		if (age == at(0.35)) {
			sound(level, SoundEvents.WITHER_AMBIENT, 2.5F, 0.5F);
		}
		if (age == at(0.5)) {
			sound(level, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
		}
		if (age == at(0.62)) {
			sound(level, SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.6F);
		}
		if (age == at(0.8)) {
			sound(level, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3.0F, 0.5F);
			sound(level, SoundEvents.TOTEM_USE, 1.5F, 0.8F);
		}
		if (age >= nextBeat && p < 0.97) {
			// A heartbeat racing towards the return.
			sound(level, SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.7F + 0.5F * (float) p);
			nextBeat = age + (int) Math.max(4, Math.round(16 - 12 * p));
		}
	}

	private void lightning(ServerLevel level) {
		for (double moment : BOLTS) {
			if (age == at(moment)) {
				double angle = random.nextDouble() * Math.PI * 2;
				double dist = 3 + random.nextDouble() * 3;
				bolt(level, new Vec3(anchor.x + Math.cos(angle) * dist, anchor.y, anchor.z + Math.sin(angle) * dist));
			}
		}
	}

	/** A visual-only lightning bolt (sparks instead above copper or a lightning rod, which even a visual strike alters). */
	private static void bolt(ServerLevel level, Vec3 pos) {
		BlockPos strike = BlockPos.containing(pos.x, pos.y - 1.0E-6, pos.z);
		if (level.isLoaded(strike) && safeStrike(level.getBlockState(strike))) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setVisualOnly(true);
				bolt.snapTo(pos.x, pos.y, pos.z);
				if (level.addFreshEntity(bolt)) {
					return;
				}
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 30, 0.2, 1.2, 0.2, 0.3);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 1.0F);
	}

	static boolean safeStrike(BlockState state) {
		Block block = state.getBlock();
		return !(block instanceof WeatheringCopper) && !(block instanceof LightningRodBlock)
				&& HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block) == null;
	}

	// ================================================================== drawing

	/** The floor cracks while the body sinks. */
	private void drawGround(RitualFx fx) {
		if (age % 2 != 0) {
			return;
		}
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 0.5 + random.nextDouble() * 2.0;
			fx.burst(floor, anchor.x + Math.cos(angle) * dist, anchor.y + 0.1, anchor.z + Math.sin(angle) * dist, 3, 0.2, 0.05, 0.2, 0.15);
		}
		fx.burst(ParticleTypes.LARGE_SMOKE, anchor.x, anchor.y + 0.2, anchor.z, 2, 0.4, 0.05, 0.4, 0.01);
	}

	/** Four strands (soul fire, totem sparks) spiralling up around the body, tightening as it comes back. */
	private void drawSpiral(RitualFx fx, double bodyY, double p) {
		double radius = 2.6 - 1.6 * p;
		double top = height + 2.5;
		double base = Math.min(anchor.y, bodyY);
		for (int strand = 0; strand < 4; strand++) {
			ParticleOptions particle = strand % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.TOTEM_OF_UNDYING;
			for (int s = 0; s < 3; s++) {
				double yy = (age * 0.2 + s * top / 3.0 + strand * 0.4) % top;
				double angle = age * 0.3 + strand * Math.PI / 2 + yy * 1.1;
				if (!fx.move(particle, anchor.x + Math.cos(angle) * radius, base + yy, anchor.z + Math.sin(angle) * radius, 0, 0.04, 0)) {
					return;
				}
			}
		}
	}

	/** A column of light falling from the sky onto the guardian. */
	private void drawColumn(RitualFx fx, Vec3 core, double k) {
		double top = anchor.y + COLUMN_HEIGHT;
		int rods = 3 + (int) Math.round(5 * k);
		for (int i = 0; i < rods; i++) {
			double y = core.y + random.nextDouble() * (top - core.y);
			fx.move(ParticleTypes.END_ROD, anchor.x + random.nextGaussian() * 0.15, y, anchor.z + random.nextGaussian() * 0.15, 0,
					-(0.8 + 0.8 * k), 0);
		}
		for (int i = 0; i < 2 + (int) Math.round(3 * k); i++) {
			double y = core.y + random.nextDouble() * (top - core.y) * 0.6;
			fx.dot(ParticleTypes.GLOW, anchor.x + random.nextGaussian() * 0.3, y, anchor.z + random.nextGaussian() * 0.3);
		}
		if (age % 3 == 0) {
			double y = core.y + random.nextDouble() * (top - core.y) * 0.7;
			fx.ring(RitualFx.fade(GOLD, 0xFFFFFF, 1.1F), new Vec3(anchor.x, y, anchor.z), 0.7, 12, age * 0.2);
		}
		if (age % 10 == 0) {
			fx.dot(RitualFx.flash(0xFFF2B0), new Vec3(anchor.x, top, anchor.z));
		}
	}

	/** Rings of red and gold dust born wide every few ticks and collapsing into the guardian. */
	private void drawRings(RitualFx fx, Vec3 core) {
		for (int born = Math.max(0, age - RING_LIFE + 1); born <= age; born++) {
			if (born % RING_EVERY != 0) {
				continue;
			}
			int index = born / RING_EVERY;
			int color = index % 2 == 0 ? RED : GOLD;
			double t = (double) (age - born) / RING_LIFE;
			double radius = 0.4 + RING_RADIUS * Math.pow(1 - t, 1.2);
			Vec3 center = new Vec3(anchor.x, anchor.y + 0.2 + (index % 3) * 0.9, anchor.z);
			double phase = age * 0.05 * (index % 2 == 0 ? 1 : -1);
			fx.ring(RitualFx.dust(color, 1.6F), center, radius, (int) (14 + radius * 5), phase);
			if (born == age) {
				// Sparks rushing from the new ring into the guardian's heart.
				for (int i = 0; i < 12; i++) {
					double angle = phase + Math.PI * 2 * i / 12;
					fx.dot(RitualFx.trail(core, color, RING_LIFE), center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius);
				}
			}
		}
	}

	/** Souls rising from where the escort died, drawn into the guardian. */
	private void drawSouls(RitualFx fx, Vec3 core) {
		if (age % 2 != 0) {
			return;
		}
		for (int i = 0; i < souls.size(); i++) {
			Vec3 spot = souls.get(i);
			fx.move(ParticleTypes.SOUL, spot.x + random.nextGaussian() * 0.2, spot.y + 0.3, spot.z + random.nextGaussian() * 0.2, 0, 0.06, 0);
			if ((age / 2 + i) % 2 == 0) {
				fx.dot(RitualFx.trail(core, SOUL, 12 + random.nextInt(7)), spot.x, spot.y + 1.0, spot.z);
			}
			if ((age + i * 3) % 12 == 0) {
				fx.burst(ParticleTypes.SCULK_SOUL, spot.x, spot.y + 0.4, spot.z, 2, 0.2, 0.1, 0.2, 0.02);
			}
		}
	}

	// ================================================================== helpers

	private int at(double fraction) {
		return Math.max(1, (int) Math.round(duration * fraction));
	}

	private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, anchor.x, anchor.y, anchor.z, sound, SoundSource.HOSTILE, volume, pitch);
	}
}
