package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * One running incantation ritual at a dormant ruin: who takes part, the blood price paid so far, the ritual's boss bar
 * (darkens the sky) and its choreography, drawn every tick within the particle budget of a {@link RitualFx}:
 * <ol>
 *     <li>a runic summoning circle draws itself on the floor at the boss marker (rotating rings, octagram, glyphs in the
 *     ruin's colour), a smaller rune circle under every participant;</li>
 *     <li>curving streams of blood (red dust fading into the ruin's colour) flow from each participant to the circle,
 *     pulsing with a heartbeat that races towards the end; each beat drains a little health and slows the players;</li>
 *     <li>the energy gathers into a column of soul fire, end rods and portal sparks rising above the marker while the floor
 *     trembles (block crack particles of the floor, no block is changed);</li>
 *     <li>climax: flash and sonic boom, then the guardian and its escort materialise ({@link Incantations} starts the fight).</li>
 * </ol>
 * Runtime only: a ritual never survives a restart (the scroll is refunded at shutdown).
 */
final class Ritual {
	static final int BLOOD = 0x8E0012;
	static final int BLOOD_BRIGHT = 0xFF1A2B;
	private static final double CIRCLE_RADIUS = 4.5;
	private static final int BAR_SYNC_TICKS = 10;

	final RuinRecord ruin;
	final RuinDef def;
	final ResourceKey<Level> dimension;
	/** Boss marker (spawn cell of the guardian). */
	final BlockPos marker;
	/** Center of the circle, on the floor under the marker. */
	final Vec3 ground;
	final UUID caster;
	final String casterName;
	/** The scroll given back if the ritual is cancelled (empty when none was consumed: creative caster). */
	final ItemStack refund;
	final int duration;
	final int color;
	final int accent;
	final ServerBossEvent bar;
	private final ParticleOptions floor;
	private final ParticleOptions pillar;
	private final SplittableRandom random = new SplittableRandom();
	/** Health already drained per participant (players joining late start at the current target: they pay the rest). */
	private final Map<UUID, Double> drained = new HashMap<>();
	private final Set<UUID> warned = new HashSet<>();
	private final long seed;
	List<ServerPlayer> participants = List.of();
	int age;
	int emptyTicks;
	private int nextBeat;
	private int beats;
	private boolean fog;

	Ritual(ServerLevel level, RuinRecord ruin, RuinDef def, BlockPos marker, ServerPlayer caster, ItemStack refund, RuinConfig cfg) {
		this.ruin = ruin;
		this.def = def;
		this.dimension = level.dimension();
		this.marker = marker;
		this.caster = caster.getUUID();
		this.casterName = caster.getName().getString();
		this.refund = refund;
		this.duration = SummonRules.ticks(cfg.summon.ritualSeconds);
		this.color = SummonRules.themeColor(def.bossBarColor);
		this.accent = RitualFx.mix(color, 0xFFFFFF, 0.45);
		this.seed = random.nextLong();
		BlockPos floorPos = floor(level, marker);
		BlockState state = floorPos != null ? level.getBlockState(floorPos) : (ruin.isNether() ? Blocks.BLACKSTONE : Blocks.DEEPSLATE).defaultBlockState();
		this.ground = new Vec3(marker.getX() + 0.5, floorPos != null ? floorPos.getY() + 1.0 : marker.getY(), marker.getZ() + 0.5);
		this.floor = new BlockParticleOption(ParticleTypes.BLOCK, state);
		this.pillar = new BlockParticleOption(ParticleTypes.DUST_PILLAR, state);
		this.bar = new ServerBossEvent(UUID.randomUUID(), Lang.get("ruin.summon.bar", Lang.colorize(def.boss.name)), barColor(def.bossBarColor),
				BossEvent.BossBarOverlay.PROGRESS);
		bar.setDarkenScreen(cfg.encounter.darkenSky);
		bar.setProgress(0.0F);
	}

	/** First solid block at or under the marker's floor (the marker cell itself is air). */
	private static BlockPos floor(ServerLevel level, BlockPos marker) {
		for (int dy = 1; dy <= 6; dy++) {
			BlockPos pos = marker.below(dy);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	private int at(double fraction) {
		return (int) Math.round(duration * fraction);
	}

	double progress() {
		return Math.min(1.0, (double) age / Math.max(1, duration));
	}

	// ================================================================== participants

	/** Players taking part right now (alive, not spectators, inside the arena). */
	List<ServerPlayer> gather(ServerLevel level, double arenaRadius) {
		List<ServerPlayer> list = new ArrayList<>();
		Vec3 center = Vec3.atBottomCenterOf(marker);
		for (ServerPlayer player : level.players()) {
			if (SummonRules.participant(player.isAlive(), player.isSpectator(), true, player.position().distanceToSqr(center), arenaRadius)) {
				list.add(player);
				// A late participant pays only the rest of the price.
				drained.putIfAbsent(player.getUUID(), SummonRules.drainTarget(progress(), RuinModule.config().summon.drainHealth));
			}
		}
		participants = list;
		return list;
	}

	// ================================================================== start

	void begin(ServerLevel level, RuinConfig cfg) {
		sound(level, ground, SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.6F);
		sound(level, ground, SoundEvents.SCULK_CATALYST_BLOOM, 3.0F, 0.5F);
		sound(level, ground, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2.0F, 0.7F);
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.AMBIENT_CAVE, SoundSource.HOSTILE, 3.0F, 0.6F);
		Component title = Lang.get("ruin.summon.start.title");
		Component subtitle = Lang.get("ruin.summon.start.subtitle", Lang.colorize(def.boss.name));
		for (ServerPlayer player : Encounters.present(level, ruin, cfg.encounter.presenceMargin)) {
			Msg.title(player, title, subtitle, 10, 50, 15);
			Msg.send(player, "ruin.summon.start.chat", casterName, Lang.colorize(def.boss.name), Lang.colorize(def.name));
		}
		nextBeat = at(SummonRules.DRAIN_START);
		syncBar(level, cfg);
	}

	// ================================================================== tick

	/** One tick of the ritual (participants already gathered): bar, drain heartbeats, sounds, particles. */
	void tick(ServerLevel level, RitualFx fx, RuinConfig cfg) {
		double p = progress();
		bar.setProgress((float) p);
		if (age % BAR_SYNC_TICKS == 0) {
			syncBar(level, cfg);
		}
		if (!fog && p >= SummonRules.COLUMN_START) {
			fog = true;
			bar.setCreateWorldFog(true);
		}
		if (age >= nextBeat && p >= SummonRules.DRAIN_START && p <= SummonRules.DRAIN_END + 0.05) {
			beat(level, fx, cfg, p);
			nextBeat = age + SummonRules.heartbeatTicks(p);
		}
		cues(level, p);
		drawCircle(fx, p);
		for (int i = 0; i < participants.size(); i++) {
			ServerPlayer player = participants.get(i);
			if ((age + i) % 3 == 0) {
				drawPlayerCircle(fx, player);
			}
			if (p >= SummonRules.DRAIN_START - 0.04 && p <= SummonRules.DRAIN_END + 0.06) {
				drawStream(fx, player, i, p);
			}
		}
		if (p >= SummonRules.COLUMN_START) {
			drawColumn(fx, SummonRules.phase(p, SummonRules.COLUMN_START, 1.0));
		}
		if (p >= SummonRules.TREMOR_START) {
			drawTremor(fx, SummonRules.phase(p, SummonRules.TREMOR_START, 1.0));
		}
		if (p >= 0.85) {
			drawConvergence(fx, SummonRules.phase(p, 0.85, 1.0));
		}
	}

	/** Sounds at fixed points of the ritual. */
	private void cues(ServerLevel level, double p) {
		if (age == at(SummonRules.COLUMN_START)) {
			sound(level, ground, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
			sound(level, ground, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2.5F, 0.5F);
		}
		if (age == at(0.55)) {
			sound(level, ground, SoundEvents.WARDEN_EMERGE, 3.0F, 0.8F);
		}
		if (age == at(0.75)) {
			sound(level, ground, SoundEvents.SCULK_SHRIEKER_SHRIEK, 3.5F, 0.6F);
		}
		if (age == at(0.9)) {
			sound(level, ground, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3.0F, 0.5F);
		}
		if (p >= SummonRules.TREMOR_START && (age - at(SummonRules.TREMOR_START)) % 30 == 15) {
			sound(level, ground, SoundEvents.WARDEN_DIG, 2.0F, 0.6F);
		}
	}

	/** A heartbeat: sound, a share of the blood price, slowness, blood bursting from the chests, the circle pulses. */
	private void beat(ServerLevel level, RitualFx fx, RuinConfig cfg, double p) {
		beats++;
		double target = SummonRules.drainTarget(p, cfg.summon.drainHealth);
		float pitch = 0.85F + 0.4F * (float) p;
		for (ServerPlayer player : participants) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6F, pitch);
			boolean paid = drainTo(player, target, cfg);
			if (cfg.summon.slowParticipants && !player.isCreative()) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, true, false, true));
			}
			Vec3 chest = player.position().add(0, 1.0, 0);
			fx.burst(RitualFx.dust(BLOOD, 1.1F), chest.x, chest.y, chest.z, paid ? 12 : 5, 0.3, 0.4, 0.3, 0.0);
			if (paid) {
				fx.burst(ParticleTypes.DAMAGE_INDICATOR, chest.x, chest.y + 0.3, chest.z, 2, 0.2, 0.1, 0.2, 0.05);
			}
			if (warned.add(player.getUUID()) || beats % 3 == 0) {
				Msg.actionBar(player, cfg.summon.drainHealth > 0 ? "ruin.summon.drain" : "ruin.summon.drain.none");
			}
		}
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.5F, pitch - 0.15F);
		fx.ring(RitualFx.fade(BLOOD_BRIGHT, color, 1.5F), ground.add(0, 0.15, 0), CIRCLE_RADIUS * 1.03, 44, age * 0.035);
	}

	/**
	 * Drains the participant down to {@code target} health lost in total (never below the minimum health, nothing in
	 * creative). @return true when health was taken
	 */
	private boolean drainTo(ServerPlayer player, double target, RuinConfig cfg) {
		UUID uuid = player.getUUID();
		double already = drained.getOrDefault(uuid, target);
		drained.put(uuid, Math.max(already, target));
		double amount = target - already;
		if (amount <= 1.0E-6 || player.isCreative() || player.isSpectator()) {
			return false;
		}
		float health = player.getHealth();
		float after = SummonRules.drain(health, cfg.summon.minHealth, amount);
		if (after >= health) {
			return false;
		}
		player.setHealth(after);
		ClientboundHurtAnimationPacket hurt = new ClientboundHurtAnimationPacket(player);
		player.connection.send(hurt);
		player.level().getChunkSource().sendToTrackingPlayers(player, hurt);
		return true;
	}

	/** Climax: whoever is still there pays the whole price. */
	void settle(ServerLevel level, RuinConfig cfg) {
		for (ServerPlayer player : participants) {
			drainTo(player, Math.max(0, cfg.summon.drainHealth), cfg);
		}
	}

	private void syncBar(ServerLevel level, RuinConfig cfg) {
		Set<ServerPlayer> viewers = new HashSet<>(Encounters.present(level, ruin, cfg.encounter.presenceMargin));
		viewers.addAll(participants);
		for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
			if (!viewers.contains(player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : viewers) {
			bar.addPlayer(player);
		}
	}

	void close() {
		bar.removeAllPlayers();
	}

	// ================================================================== drawing

	/** The summoning circle at the marker: rings drawn clockwise with the progress, octagram, glyphs. */
	private void drawCircle(RitualFx fx, double p) {
		Vec3 c = ground.add(0, 0.06, 0);
		double spin = age * 0.035;
		double draw = SummonRules.phase(p, 0.0, 0.3);
		if (age % 2 == 0) {
			double r = CIRCLE_RADIUS * (1 + 0.02 * Math.sin(age * 0.5));
			fx.arc(RitualFx.dust(color, 1.3F), c, r, spin, spin + Math.PI * 2 * draw, (int) Math.ceil(64 * draw));
			fx.arc(RitualFx.fade(BLOOD, color, 1.0F), c, CIRCLE_RADIUS * 0.86, -spin * 1.3, -spin * 1.3 - Math.PI * 2 * draw, (int) Math.ceil(52 * draw));
			fx.arc(RitualFx.dust(accent, 0.9F), c, CIRCLE_RADIUS * 0.42, spin * 2, spin * 2 + Math.PI * 2 * draw, (int) Math.ceil(26 * draw));
			if (draw > 0.2) {
				fx.polygram(RitualFx.dust(color, 1.0F), c, CIRCLE_RADIUS * 0.84, 8, 3, spin * 0.6, (draw - 0.2) / 0.8, 2.2);
				fx.polygram(RitualFx.dust(accent, 0.8F), c, CIRCLE_RADIUS * 0.42, 3, 1, -spin * 2, (draw - 0.2) / 0.8, 3.0);
			}
			if (draw > 0.5) {
				// Glyphs between the two outer rings: radial strokes, every other one crossed.
				for (int i = 0; i < 12; i++) {
					double angle = -spin * 1.3 + Math.PI * 2 * i / 12;
					double cos = Math.cos(angle);
					double sin = Math.sin(angle);
					Vec3 in = new Vec3(c.x + cos * CIRCLE_RADIUS * 0.88, c.y, c.z + sin * CIRCLE_RADIUS * 0.88);
					Vec3 out = new Vec3(c.x + cos * CIRCLE_RADIUS * 0.98, c.y, c.z + sin * CIRCLE_RADIUS * 0.98);
					fx.line(RitualFx.dust(BLOOD_BRIGHT, 0.8F), in, out, 5);
					if (i % 2 == 0) {
						Vec3 mid = in.add(out).scale(0.5);
						Vec3 side = new Vec3(-sin * 0.22, 0, cos * 0.22);
						fx.line(RitualFx.dust(BLOOD_BRIGHT, 0.7F), mid.subtract(side), mid.add(side), 6);
					}
				}
			}
		}
		if (draw > 0.3) {
			// Glyph energy flowing from the rim to the center.
			for (int i = 0; i < 3; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double dist = CIRCLE_RADIUS * (0.5 + random.nextDouble() * 0.5);
				fx.move(ParticleTypes.ENCHANT, c.x, c.y + 1.0, c.z, Math.cos(angle) * dist, -0.9, Math.sin(angle) * dist);
			}
		}
	}

	/** A small rune circle under a participant. */
	private void drawPlayerCircle(RitualFx fx, ServerPlayer player) {
		Vec3 feet = player.position().add(0, 0.08, 0);
		double spin = age * 0.09;
		fx.ring(RitualFx.dust(color, 0.8F), feet, 1.2, 16, spin);
		fx.polygram(RitualFx.fade(BLOOD_BRIGHT, color, 0.7F), feet, 1.05, 3, 1, -spin, 1.0, 3.0);
	}

	/** Curving stream of blood from the participant's chest to the circle, with pulses running along it. */
	private void drawStream(RitualFx fx, ServerPlayer player, int index, double p) {
		Vec3 from = player.position().add(0, 1.0, 0);
		Vec3 to = ground.add(0, 1.2, 0);
		Vec3 delta = to.subtract(from);
		double dist = delta.length();
		if (dist < 0.8) {
			return;
		}
		Vec3 side = new Vec3(-delta.z, 0, delta.x);
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
		double sway = Math.sin(age * 0.08 + index * 1.7 + (seed & 0xFF)) * Math.min(2.0, 0.25 * dist);
		Vec3 control = from.add(to).scale(0.5).add(0, 1.2 + dist * 0.18, 0).add(side.scale(sway));
		if ((age + index) % 3 == 0) {
			int n = (int) Math.max(8, Math.min(40, dist * 1.6));
			ParticleOptions path = RitualFx.fade(BLOOD, color, 0.7F);
			for (int k = 0; k <= n; k++) {
				if (!fx.dot(path, RitualFx.bezier(from, control, to, (double) k / n))) {
					return;
				}
			}
		}
		double speed = 1.0 / 26.0 + p / 40.0;
		ParticleOptions head = RitualFx.dust(BLOOD_BRIGHT, 1.4F);
		ParticleOptions tail = RitualFx.fade(BLOOD_BRIGHT, color, 1.0F);
		for (int k = 0; k < 3; k++) {
			double t = frac(age * speed + k / 3.0 + index * 0.13);
			fx.dot(head, RitualFx.bezier(from, control, to, t));
			fx.dot(tail, RitualFx.bezier(from, control, to, Math.max(0, t - 0.025)));
		}
	}

	/** Column of energy rising above the marker. */
	private void drawColumn(RitualFx fx, double k) {
		Vec3 base = ground.add(0, 0.2, 0);
		double height = 2 + 14 * k;
		for (int i = 0; i < 3; i++) {
			double y = random.nextDouble() * height;
			fx.move(ParticleTypes.END_ROD, base.x + random.nextGaussian() * 0.12, base.y + y, base.z + random.nextGaussian() * 0.12, 0,
					0.08 + 0.1 * k, 0);
		}
		double spin = age * 0.35;
		double radius = 1.0 - 0.4 * k;
		for (int strand = 0; strand < 2; strand++) {
			for (int s = 0; s < 3; s++) {
				double y = (age * 0.25 + s * height / 3) % height;
				double angle = spin + strand * Math.PI + y * 0.6;
				fx.dot(ParticleTypes.SOUL_FIRE_FLAME, base.x + Math.cos(angle) * radius, base.y + y, base.z + Math.sin(angle) * radius);
			}
		}
		fx.burst(ParticleTypes.REVERSE_PORTAL, base.x, base.y + 0.3, base.z, 4, 0.5, 0.1, 0.5, 0.02);
		if (age % 2 == 0) {
			ParticleOptions shell = RitualFx.fade(color, 0xFFFFFF, 1.2F);
			for (int i = 0; i < 6; i++) {
				double y = random.nextDouble() * height;
				double angle = random.nextDouble() * Math.PI * 2;
				fx.dot(shell, base.x + Math.cos(angle) * 0.5, base.y + y, base.z + Math.sin(angle) * 0.5);
			}
		}
		if (age % 4 == 0) {
			fx.ring(RitualFx.fade(color, 0xFFFFFF, 1.1F), base.add(0, height, 0), 1.3, 18, spin);
		}
	}

	/** The floor trembles: block crack particles bursting from the floor around the circle (no block is changed). */
	private void drawTremor(RitualFx fx, double k) {
		if (age % 2 != 0) {
			return;
		}
		int n = 2 + (int) (6 * k);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 1 + random.nextDouble() * (2 + 4.5 * k);
			fx.burst(floor, ground.x + Math.cos(angle) * dist, ground.y + 0.1, ground.z + Math.sin(angle) * dist, 4, 0.25, 0.05, 0.25, 0.2);
		}
		if (random.nextDouble() < 0.3 * k) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 1.5 + random.nextDouble() * 4;
			fx.burst(pillar, ground.x + Math.cos(angle) * dist, ground.y + 0.1, ground.z + Math.sin(angle) * dist, 6, 0.3, 0.1, 0.3, 0.3);
		}
	}

	/** Last moments: sparks rush from the circle into the column. */
	private void drawConvergence(RitualFx fx, double k) {
		Vec3 core = ground.add(0, 1.6, 0);
		int n = 3 + (int) (4 * k);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = CIRCLE_RADIUS * (0.6 + random.nextDouble() * 0.4);
			fx.dot(RitualFx.trail(core, i % 2 == 0 ? color : BLOOD_BRIGHT, 10 + random.nextInt(6)), ground.x + Math.cos(angle) * dist, ground.y + 0.1,
					ground.z + Math.sin(angle) * dist);
		}
		if (age % 3 == 0) {
			fx.burst(ParticleTypes.SCULK_SOUL, core.x, core.y - 1.0, core.z, 2, 0.4, 0.2, 0.4, 0.03);
		}
	}

	/** Flash, sonic boom, a sphere of blood and light, the floor bursting around the circle. */
	void climaxFx(ServerLevel level, RitualFx fx) {
		Vec3 core = ground.add(0, 1.6, 0);
		fx.dot(RitualFx.flash(color), core);
		fx.dot(RitualFx.flash(0xFFFFFF), core.add(0, 1.5, 0));
		fx.burst(ParticleTypes.EXPLOSION_EMITTER, core, 1, 0, 0);
		fx.dot(ParticleTypes.SONIC_BOOM, core);
		for (int i = 0; i < 10; i++) {
			double angle = Math.PI * 2 * i / 10;
			fx.dot(ParticleTypes.SONIC_BOOM, ground.x + Math.cos(angle) * 3, ground.y + 1.0, ground.z + Math.sin(angle) * 3);
		}
		fx.sphereBurst(RitualFx.fade(BLOOD_BRIGHT, color, 1.6F), core, 0.4, 90, 0.45);
		fx.burst(ParticleTypes.END_ROD, core, 40, 0.4, 0.35);
		fx.burst(ParticleTypes.SOUL_FIRE_FLAME, core, 40, 0.6, 0.15);
		fx.burst(floor, ground.x, ground.y + 0.2, ground.z, 60, 2.5, 0.1, 2.5, 0.35);
		fx.ring(RitualFx.fade(color, BLOOD, 1.8F), ground.add(0, 0.15, 0), CIRCLE_RADIUS, 48, 0);
		sound(level, ground, SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.9F);
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.7F);
		sound(level, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.6F);
		// Shockwave of floor debris running outwards.
		ParticleOptions debris = floor;
		Vec3 at = ground;
		RuinFx.play(level, (lvl, tick) -> {
			double radius = 1.5 + tick * 0.7;
			RuinFx.ring(lvl, debris, at.add(0, 0.1, 0), radius, (int) (12 + radius * 4), 0.1);
			if (tick % 2 == 0) {
				RuinFx.ring(lvl, ParticleTypes.CLOUD, at.add(0, 0.2, 0), radius, (int) (8 + radius * 2), 0.05);
			}
			return tick < 10;
		});
	}

	/** The guardian and its escort appear in a puff, sparks running from the column to each escort mob. */
	void materialize(ServerLevel level, RitualFx fx, Mob boss, List<Mob> minions) {
		Vec3 core = ground.add(0, 1.6, 0);
		Vec3 at = boss.position().add(0, boss.getBbHeight() / 2, 0);
		fx.burst(ParticleTypes.POOF, at, 40, 0.6, 0.08);
		fx.burst(ParticleTypes.LARGE_SMOKE, at, 20, 0.5, 0.03);
		fx.burst(RitualFx.dust(color, 1.5F), at, 30, 0.7, 0.0);
		for (Mob minion : minions) {
			Vec3 m = minion.position().add(0, minion.getBbHeight() / 2, 0);
			fx.burst(ParticleTypes.POOF, m, 12, 0.35, 0.05);
			for (int i = 0; i < 8; i++) {
				fx.dot(RitualFx.trail(m, i % 2 == 0 ? color : BLOOD_BRIGHT, 8 + i), core.x + random.nextGaussian() * 0.2, core.y + random.nextGaussian() * 0.3,
						core.z + random.nextGaussian() * 0.2);
			}
		}
	}

	/** A broken ritual: the circle fades in smoke. */
	void cancelFx(ServerLevel level, RitualFx fx) {
		fx.burst(ParticleTypes.LARGE_SMOKE, ground.x, ground.y + 0.5, ground.z, 30, 1.5, 0.3, 1.5, 0.02);
		fx.burst(ParticleTypes.SOUL, ground.x, ground.y + 0.5, ground.z, 12, 1.0, 0.3, 1.0, 0.02);
		fx.ring(ParticleTypes.SMOKE, ground.add(0, 0.1, 0), CIRCLE_RADIUS, 40, 0);
		sound(level, ground, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
		sound(level, ground, SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.5F, 0.6F);
	}

	// ================================================================== helpers

	private static double frac(double value) {
		return value - Math.floor(value);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	private static BossEvent.BossBarColor barColor(String name) {
		try {
			return BossEvent.BossBarColor.valueOf(name);
		} catch (IllegalArgumentException e) {
			return BossEvent.BossBarColor.RED;
		}
	}
}
