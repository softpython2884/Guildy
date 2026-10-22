package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The cursed incantation ritual: the same rules as the normal one (participants, grace, refund when it breaks before its
 * climax) with a heavier blood price ({@code cursed.ritual.drainHealth}, never below {@code cursed.ritual.minHealth}) and a
 * darker choreography, drawn every tick within the particle budget of a {@link RitualFx}:
 * <ol>
 *     <li>an inverted pentagram in a double rune circle of void, purple and blood draws itself on the floor, wider than
 *     the normal circle, pulsing with a racing heartbeat; smaller inverted pentagrams under every participant;</li>
 *     <li>blood streams flow from each participant into the pentagram, souls rise from its five points and scream;</li>
 *     <li>a column of black smoke, soul fire and reverse portal sparks rises while the floor trembles;</li>
 *     <li>climax: a black flash, the curse of the elder guardian on every participant's screen, a sonic boom, then the
 *     guardian appears cursed ({@link Incantations} starts the cursed fight).</li>
 * </ol>
 */
final class CursedRitual extends Ritual {
	private static final double CIRCLE_RADIUS = 5.5;
	private final SplittableRandom random = new SplittableRandom();
	private final Set<UUID> warned = new HashSet<>();
	/** Nether stars seen in the circle (the Avatar): held there (nobody picks them up) and wrapped in embers. */
	private final Set<UUID> stars = new HashSet<>();
	private final double facing;
	private int nextBeat;
	private int beats;
	private boolean fog;

	CursedRitual(ServerLevel level, RuinRecord ruin, RuinDef def, BlockPos marker, ServerPlayer caster, ItemStack refund, RuinConfig cfg) {
		// The ritual already names the cursed form (the celestial jaguar's: Le Jaguar d'obsidienne).
		super(level, ruin, def.cursedForm(), marker, caster, refund, cfg, cfg.cursed.ritual.ritualSeconds);
		bar.setName(Lang.get("ruin.cursed.ritual.bar", Lang.colorize(def.boss.name)));
		bar.setColor(BossEvent.BossBarColor.PURPLE);
		bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
		bar.setDarkenScreen(true);
		// The pentagram's horns face the ruin's entrance (where the players come from).
		BlockPos entrance = ruin.entrance != null ? ruin.entrance : ruin.center();
		double dx = entrance.getX() + 0.5 - ground.x;
		double dz = entrance.getZ() + 0.5 - ground.z;
		this.facing = dx * dx + dz * dz < 1.0E-4 ? 0 : Math.atan2(dz, dx);
	}

	@Override
	double drainTotal(RuinConfig cfg) {
		return cfg.cursed.ritual.drainHealth;
	}

	@Override
	double minHealth(RuinConfig cfg) {
		return cfg.cursed.ritual.minHealth;
	}

	@Override
	boolean cursed() {
		return true;
	}

	// ================================================================== start

	@Override
	void begin(ServerLevel level, RuinConfig cfg) {
		sound(level, ground, SoundEvents.EVOKER_PREPARE_WOLOLO, 2.5F, 0.5F);
		sound(level, ground, SoundEvents.SCULK_CATALYST_BLOOM, 3.0F, 0.4F);
		sound(level, ground, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2.5F, 0.5F);
		sound(level, ground, SoundEvents.WITHER_AMBIENT, 1.5F, 0.5F);
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.AMBIENT_CAVE, SoundSource.HOSTILE, 3.0F, 0.5F);
		String boss = Lang.colorize(def.boss.name);
		Component title = Lang.get("ruin.cursed.ritual.title");
		Component subtitle = Lang.get("ruin.cursed.ritual.subtitle", boss);
		String threat = Encounters.threatLabel(Encounters.threatLevel(ruin));
		for (ServerPlayer player : Encounters.present(level, ruin, cfg.encounter.presenceMargin)) {
			Msg.title(player, title, subtitle, 10, 60, 15);
			Msg.send(player, "ruin.cursed.ritual.chat", casterName, boss, Lang.colorize(def.name));
			Msg.send(player, "ruin.summon.start.threat", threat);
		}
		nextBeat = (int) Math.round(duration * SummonRules.DRAIN_START);
		syncBar(level, cfg);
	}

	// ================================================================== tick

	@Override
	void tick(ServerLevel level, RitualFx fx, RuinConfig cfg) {
		double p = progress();
		bar.setProgress((float) p);
		if (age % 10 == 0) {
			syncBar(level, cfg);
		}
		if (!fog && p >= SummonRules.COLUMN_START) {
			fog = true;
			bar.setCreateWorldFog(true);
		}
		if (age >= nextBeat && p >= SummonRules.DRAIN_START && p <= SummonRules.DRAIN_END + 0.05) {
			beat(level, fx, cfg, p);
			nextBeat = age + Math.max(5, SummonRules.heartbeatTicks(p) - 2);
		}
		cues(level, p);
		drawPentagram(fx, p);
		for (int i = 0; i < participants.size(); i++) {
			ServerPlayer player = participants.get(i);
			if ((age + i) % 3 == 0) {
				drawPlayerSigil(fx, player);
			}
			if (p >= SummonRules.DRAIN_START - 0.04 && p <= SummonRules.DRAIN_END + 0.06) {
				CursedFx.stream(fx, player.position().add(0, 1.0, 0), ground.add(0, 1.0, 0), age, i);
			}
		}
		if (p >= 0.25) {
			drawSouls(fx, SummonRules.phase(p, 0.25, 1.0));
		}
		if (p >= SummonRules.COLUMN_START) {
			drawColumn(fx, SummonRules.phase(p, SummonRules.COLUMN_START, 1.0));
		}
		if (p >= SummonRules.TREMOR_START) {
			drawTremor(fx, SummonRules.phase(p, SummonRules.TREMOR_START, 1.0));
		}
		if (p >= 0.85) {
			CursedFx.implode(fx, ground.add(0, 1.8, 0), 3.5, SummonRules.phase(p, 0.85, 1.0), age % 2 == 0 ? CursedFx.PURPLE : CursedFx.BLOOD, 40, age * 0.2);
		}
		if (cfg.cursed.avatar.enabled && age % 2 == 0) {
			stars(level, fx, cfg);
		}
	}

	/**
	 * A Nether star lying in the circle: the ritual takes hold of it (nobody can pick it up while it lasts), embers spiral
	 * around it and the participants learn that it will be consumed.
	 */
	private void stars(ServerLevel level, RitualFx fx, RuinConfig cfg) {
		double radius = cfg.summon.arenaRadius;
		AABB box = new AABB(ground.x - radius, ground.y - 3, ground.z - radius, ground.x + radius, ground.y + 6, ground.z + radius);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && AvatarEncounter.isStar(e.getItem()))) {
			item.setPickUpDelay(20);
			Vec3 at = item.position();
			CursedFx.helix(fx, RitualFx.dust(AvatarEncounter.EMBER, 1.0F), at, 0.5, age, 1.4, 20);
			fx.dot(RitualFx.trail(ground.add(0, 1.8, 0), AvatarEncounter.CRIMSON, 18), at.x, at.y + 0.3, at.z);
			if (stars.add(item.getUUID())) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
				for (ServerPlayer player : participants) {
					Msg.send(player, "ruin.avatar.star.stirs");
				}
			}
		}
	}

	/** Screams, growls and chimes at fixed points of the ritual. */
	private void cues(ServerLevel level, double p) {
		if (age == at(0.2)) {
			sound(level, ground, SoundEvents.GHAST_SCREAM, 1.2F, 0.5F);
			for (ServerPlayer player : participants) {
				Msg.actionBar(player, "ruin.cursed.ritual.screams");
			}
		}
		if (age == at(SummonRules.COLUMN_START)) {
			sound(level, ground, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.4F);
			sound(level, ground, SoundEvents.WITHER_SPAWN, 0.6F, 0.4F);
		}
		if (age == at(0.55)) {
			sound(level, ground, SoundEvents.WARDEN_EMERGE, 3.0F, 0.6F);
		}
		if (age == at(0.65)) {
			sound(level, ground, SoundEvents.GHAST_SCREAM, 1.5F, 0.4F);
		}
		if (age == at(0.75)) {
			sound(level, ground, SoundEvents.SCULK_SHRIEKER_SHRIEK, 4.0F, 0.5F);
		}
		if (age == at(0.9)) {
			sound(level, ground, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3.0F, 0.4F);
			sound(level, ground, SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.5F);
		}
		if (p >= SummonRules.TREMOR_START && (age - at(SummonRules.TREMOR_START)) % 24 == 12) {
			sound(level, ground, SoundEvents.WARDEN_DIG, 2.0F, 0.5F);
		}
		if (p >= 0.3 && age % 37 == 0) {
			level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.4F + random.nextFloat() * 0.3F);
		}
	}

	/** A heartbeat: a share of the heavier blood price, slowness and a dash of darkness, blood bursting from the chests. */
	private void beat(ServerLevel level, RitualFx fx, RuinConfig cfg, double p) {
		beats++;
		double target = SummonRules.drainTarget(p, drainTotal(cfg));
		float pitch = 0.7F + 0.45F * (float) p;
		for (ServerPlayer player : participants) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.8F, pitch);
			boolean paid = drainTo(player, target, cfg);
			if (cfg.summon.slowParticipants && !player.isCreative()) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, true, false, true));
			}
			Vec3 chest = player.position().add(0, 1.0, 0);
			fx.burst(RitualFx.dust(CursedFx.BLOOD, 1.2F), chest.x, chest.y, chest.z, paid ? 14 : 5, 0.3, 0.4, 0.3, 0.0);
			fx.burst(RitualFx.dust(CursedFx.VOID, 1.0F), chest.x, chest.y, chest.z, 4, 0.4, 0.5, 0.4, 0.0);
			if (paid) {
				fx.burst(ParticleTypes.DAMAGE_INDICATOR, chest.x, chest.y + 0.3, chest.z, 3, 0.2, 0.1, 0.2, 0.05);
			}
			if (warned.add(player.getUUID()) || beats % 3 == 0) {
				Msg.actionBar(player, drainTotal(cfg) > 0 ? "ruin.cursed.ritual.drain" : "ruin.cursed.ritual.drain.none");
			}
		}
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0F, pitch - 0.2F);
		fx.ring(RitualFx.fade(CursedFx.BLOOD_BRIGHT, CursedFx.PURPLE, 1.6F), ground.add(0, 0.15, 0), CIRCLE_RADIUS * 1.05, 52, age * 0.03);
	}

	private int at(double fraction) {
		return (int) Math.round(duration * fraction);
	}

	// ================================================================== drawing

	/** The inverted pentagram drawing itself, then glowing and pulsing; flames along its lines. */
	private void drawPentagram(RitualFx fx, double p) {
		Vec3 c = ground.add(0, 0.06, 0);
		double spin = facing + age * 0.012;
		double draw = SummonRules.phase(p, 0.0, 0.35);
		if (age % 2 == 0) {
			double pulse = 1 + 0.025 * Math.sin(age * 0.6);
			CursedFx.pentagram(fx, c, CIRCLE_RADIUS * pulse, spin, draw, CursedFx.PURPLE, CursedFx.BLOOD_BRIGHT, 1.3F);
			if (draw > 0.5) {
				fx.ring(RitualFx.dust(CursedFx.VOID, 1.4F), c, CIRCLE_RADIUS * 1.28, 60, -spin);
			}
		}
		if (draw >= 1.0 && age % 3 == 0) {
			// Black flames licking up from the five points of the star.
			for (int i = 0; i < 5; i++) {
				double angle = spin + Math.PI / 2 + Math.PI + Math.PI * 2 * i * 2 / 5;
				double x = c.x + Math.cos(angle) * CIRCLE_RADIUS * 0.97;
				double z = c.z + Math.sin(angle) * CIRCLE_RADIUS * 0.97;
				fx.move(ParticleTypes.SOUL_FIRE_FLAME, x, c.y + 0.1, z, 0, 0.08, 0);
				fx.move(ParticleTypes.LARGE_SMOKE, x, c.y + 0.3, z, 0, 0.05, 0);
			}
		}
		if (draw > 0.3) {
			for (int i = 0; i < 3; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double dist = CIRCLE_RADIUS * (0.4 + random.nextDouble() * 0.6);
				fx.move(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(angle) * dist, c.y + 0.1, c.z + Math.sin(angle) * dist, 0, 0.04, 0);
			}
		}
	}

	/** A small inverted pentagram under a participant. */
	private void drawPlayerSigil(RitualFx fx, ServerPlayer player) {
		Vec3 feet = player.position().add(0, 0.08, 0);
		double spin = age * 0.08;
		fx.ring(RitualFx.dust(CursedFx.PURPLE, 0.8F), feet, 1.25, 16, spin);
		fx.polygram(RitualFx.fade(CursedFx.BLOOD_BRIGHT, CursedFx.VOID, 0.7F), feet, 1.1, 5, 2, -spin + Math.PI / 2, 1.0, 3.0);
	}

	/** Souls rising from the star's points, circling the column and drawn into it. */
	private void drawSouls(RitualFx fx, double k) {
		if (age % 2 != 0) {
			return;
		}
		Vec3 core = ground.add(0, 2.0 + 3.0 * k, 0);
		for (int i = 0; i < 5; i++) {
			double angle = facing + Math.PI * 1.5 + Math.PI * 2 * i * 2 / 5 + age * 0.01;
			double x = ground.x + Math.cos(angle) * CIRCLE_RADIUS * 0.97;
			double z = ground.z + Math.sin(angle) * CIRCLE_RADIUS * 0.97;
			fx.move(ParticleTypes.SOUL, x, ground.y + 0.3, z, 0, 0.07, 0);
			if ((age / 2 + i) % 3 == 0) {
				fx.dot(RitualFx.trail(core, i % 2 == 0 ? CursedFx.VIOLET : CursedFx.SOUL, 14 + random.nextInt(8)), x, ground.y + 1.2, z);
			}
		}
		if (age % 6 == 0) {
			fx.burst(ParticleTypes.SCULK_SOUL, ground.x, ground.y + 0.5, ground.z, 3, CIRCLE_RADIUS * 0.4, 0.2, CIRCLE_RADIUS * 0.4, 0.03);
		}
	}

	/** Column of black smoke, soul fire and void dust rising above the marker. */
	private void drawColumn(RitualFx fx, double k) {
		Vec3 base = ground.add(0, 0.2, 0);
		double height = 3 + 16 * k;
		for (int i = 0; i < 3; i++) {
			double y = random.nextDouble() * height;
			fx.move(ParticleTypes.LARGE_SMOKE, base.x + random.nextGaussian() * 0.2, base.y + y, base.z + random.nextGaussian() * 0.2, 0, 0.1 + 0.1 * k, 0);
		}
		double spin = age * 0.4;
		for (int strand = 0; strand < 3; strand++) {
			for (int s = 0; s < 3; s++) {
				double y = (age * 0.3 + s * height / 3) % height;
				double angle = spin + strand * Math.PI * 2 / 3 + y * 0.5;
				double radius = 1.2 - 0.5 * k;
				fx.dot(strand == 0 ? ParticleTypes.SOUL_FIRE_FLAME : RitualFx.dust(strand == 1 ? CursedFx.PURPLE : CursedFx.BLOOD, 1.2F),
						base.x + Math.cos(angle) * radius, base.y + y, base.z + Math.sin(angle) * radius);
			}
		}
		fx.burst(ParticleTypes.REVERSE_PORTAL, base.x, base.y + 0.3, base.z, 5, 0.6, 0.1, 0.6, 0.03);
		if (age % 4 == 0) {
			fx.ring(RitualFx.fade(CursedFx.VIOLET, CursedFx.VOID, 1.2F), base.add(0, height, 0), 1.6, 20, -spin);
		}
	}

	/** The floor trembles: debris of the floor around the pentagram (no block is changed). */
	private void drawTremor(RitualFx fx, double k) {
		if (age % 2 != 0) {
			return;
		}
		int n = 3 + (int) (7 * k);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 1 + random.nextDouble() * (2.5 + 5 * k);
			fx.burst(floor, ground.x + Math.cos(angle) * dist, ground.y + 0.1, ground.z + Math.sin(angle) * dist, 4, 0.25, 0.05, 0.25, 0.22);
		}
		if (random.nextDouble() < 0.35 * k) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 1.5 + random.nextDouble() * 4.5;
			fx.burst(pillar, ground.x + Math.cos(angle) * dist, ground.y + 0.1, ground.z + Math.sin(angle) * dist, 6, 0.3, 0.1, 0.3, 0.3);
		}
	}

	// ================================================================== climax and end

	/** A black flash, the elder guardian's curse on every participant, a sonic boom, a sphere of void and blood. */
	@Override
	void climaxFx(ServerLevel level, RitualFx fx) {
		Vec3 core = ground.add(0, 1.8, 0);
		fx.dot(RitualFx.flash(CursedFx.PURPLE), core);
		fx.dot(RitualFx.flash(CursedFx.VOID), core.add(0, 1.5, 0));
		fx.dot(RitualFx.flash(CursedFx.BLOOD_BRIGHT), core.add(0, 3.0, 0));
		fx.burst(ParticleTypes.EXPLOSION_EMITTER, core, 1, 0, 0);
		fx.dot(ParticleTypes.SONIC_BOOM, core);
		for (int i = 0; i < 10; i++) {
			double angle = Math.PI * 2 * i / 10;
			fx.dot(ParticleTypes.SONIC_BOOM, ground.x + Math.cos(angle) * 3.5, ground.y + 1.0, ground.z + Math.sin(angle) * 3.5);
		}
		fx.sphereBurst(RitualFx.fade(CursedFx.PURPLE, CursedFx.BLOOD, 1.8F), core, 0.4, 110, 0.5);
		fx.sphereBurst(RitualFx.dust(CursedFx.VOID, 2.0F), core, 0.3, 60, 0.3);
		fx.burst(ParticleTypes.SOUL, core, 50, 0.6, 0.2);
		fx.burst(ParticleTypes.SCULK_SOUL, core, 30, 0.8, 0.1);
		fx.burst(floor, ground.x, ground.y + 0.2, ground.z, 80, 3.0, 0.1, 3.0, 0.4);
		CursedFx.pentagram(fx, ground.add(0, 0.1, 0), CIRCLE_RADIUS, facing, 1.0, CursedFx.BLOOD_BRIGHT, CursedFx.VIOLET, 1.8F);
		sound(level, ground, SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.7F);
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.5F);
		sound(level, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.5F);
		sound(level, ground, SoundEvents.WITHER_SPAWN, 1.2F, 0.6F);
		for (ServerPlayer player : participants) {
			// The curse on the screen of every participant (the elder guardian's face, a vanilla client effect).
			player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
		}
		Vec3 at = ground;
		ParticleOptions debris = floor;
		ParticleOptions dark = RitualFx.dust(CursedFx.VOID, 1.6F);
		RuinFx.play(level, (lvl, tick) -> {
			double radius = 1.5 + tick * 0.8;
			RuinFx.ring(lvl, debris, at.add(0, 0.1, 0), radius, (int) (12 + radius * 4), 0.1);
			RuinFx.ring(lvl, dark, at.add(0, 0.3, 0), radius, (int) (10 + radius * 3), 0.05);
			return tick < 11;
		});
		for (int i = 0; i < 3; i++) {
			double angle = facing + Math.PI * 2 * i / 3;
			CursedFx.bolt(level, new Vec3(ground.x + Math.cos(angle) * (CIRCLE_RADIUS + 1), ground.y, ground.z + Math.sin(angle) * (CIRCLE_RADIUS + 1)));
		}
	}

	/** The cursed guardian and its escort appear in a burst of void, threads of blood running to each escort mob. */
	@Override
	void materialize(ServerLevel level, RitualFx fx, Mob boss, List<Mob> minions) {
		Vec3 core = ground.add(0, 1.8, 0);
		Vec3 at = boss.position().add(0, boss.getBbHeight() / 2, 0);
		fx.burst(ParticleTypes.LARGE_SMOKE, at, 40, 0.7, 0.05);
		fx.burst(ParticleTypes.SOUL_FIRE_FLAME, at, 40, 0.8, 0.08);
		fx.burst(RitualFx.dust(CursedFx.PURPLE, 1.6F), at, 40, 0.8, 0.0);
		fx.burst(ParticleTypes.REVERSE_PORTAL, at, 50, 0.6, 0.3);
		for (Mob minion : minions) {
			Vec3 m = minion.position().add(0, minion.getBbHeight() / 2, 0);
			fx.burst(ParticleTypes.LARGE_SMOKE, m, 12, 0.35, 0.03);
			for (int i = 0; i < 8; i++) {
				fx.dot(RitualFx.trail(m, i % 2 == 0 ? CursedFx.PURPLE : CursedFx.BLOOD_BRIGHT, 8 + i), core.x + random.nextGaussian() * 0.2,
						core.y + random.nextGaussian() * 0.3, core.z + random.nextGaussian() * 0.2);
			}
		}
	}

	/** A broken cursed ritual: the pentagram collapses into smoke and souls. */
	@Override
	void cancelFx(ServerLevel level, RitualFx fx) {
		fx.burst(ParticleTypes.LARGE_SMOKE, ground.x, ground.y + 0.5, ground.z, 40, 1.8, 0.3, 1.8, 0.02);
		fx.burst(ParticleTypes.SOUL, ground.x, ground.y + 0.5, ground.z, 20, 1.2, 0.3, 1.2, 0.03);
		fx.ring(ParticleTypes.SMOKE, ground.add(0, 0.1, 0), CIRCLE_RADIUS, 44, 0);
		sound(level, ground, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
		sound(level, ground, SoundEvents.GHAST_HURT, 1.2F, 0.4F);
		sound(level, ground, SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.5F, 0.5F);
	}
}
