package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.ScrollDrops;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.CursedRules.Attack;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollConfig;
import dev.guildy.scroll.ScrollModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The curse of one running fight ({@link Encounter#cursed}): the guardian's multipliers (permanent attribute modifiers
 * {@code guildy:ruin_cursed_*}, on top of the threat level), its three phases (a hit never skips a phase: the guardian keeps
 * the health of the next threshold and the {@link CursedTransition} starts, invulnerable; a death outside the last phase is
 * refused the same way), its attacks ({@link Pentagrams}, {@link CursedAttacks}) scheduled by phase, its aura, and the
 * texts of its awakening, failure and victory. Runtime only, like the fight itself. Ticked by {@link CursedFights}.
 */
final class CursedEncounter {
	private static final Identifier HEALTH = id("ruin_cursed_health");
	private static final Identifier DAMAGE = id("ruin_cursed_damage");
	private static final Identifier ARMOR = id("ruin_cursed_armor");
	private static final Identifier TOUGHNESS = id("ruin_cursed_toughness");
	private static final Identifier SCALE = id("ruin_cursed_scale");
	private static final Identifier SPEED = id("ruin_cursed_speed");
	private static final Identifier PHASE_DAMAGE = id("ruin_cursed_phase_damage");
	private static final Identifier PHASE_SPEED = id("ruin_cursed_phase_speed");
	private static final Identifier MINION_HEALTH = id("ruin_cursed_minion_health");
	private static final Identifier MINION_DAMAGE = id("ruin_cursed_minion_damage");
	/** Cursed attacks running at once at most. */
	private static final int MAX_RUNNING = 4;
	/** First uses of the attacks unlocked by a phase (ticks after its transition), in rotation order. */
	private static final int[] FIRST_USE = {40, 120, 200, 260};

	final CursedRules.Stats stats;
	/** 1, 2 or 3. */
	int phase = CursedRules.FIRST_PHASE;
	@Nullable CursedTransition transition;
	final List<CursedAttacks.Running> running = new ArrayList<>();
	private final Map<Attack, Long> readyAt = new EnumMap<>(Attack.class);
	private long nextAttackTick;
	private int rotation;
	/** Scale multiplier applied (limited by the headroom of the arena). */
	private double scale = 1.0;
	/** Shadow clones alive and their end (game time). */
	final Map<UUID, Long> clones = new LinkedHashMap<>();
	/** Health drained from the players and regained by the guardian (statistics, tests). */
	double drained;
	double healed;
	int transitions;
	private final SplittableRandom random = new SplittableRandom();

	CursedEncounter(CursedSettings settings) {
		this.stats = CursedRules.stats(settings.guardian);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("guildy", path);
	}

	private static CursedSettings settings() {
		return RuinModule.config().cursed;
	}

	// ================================================================== spawn

	/** The guardian appears cursed (called before it enters the level): multipliers, size, name, full health. */
	void apply(ServerLevel level, Mob boss, String bossName) {
		double height = Math.max(0.5, boss.getBbHeight());
		double room = Resurrection.headroom(level, BlockPos.containing(boss.position()), 12);
		scale = CursedRules.scale(stats.scale(), room, height);
		modifiers(boss);
		AttributeInstance knockback = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		double kb = settings().guardian.knockbackResistance;
		if (knockback != null && knockback.getBaseValue() < kb) {
			knockback.setBaseValue(kb);
		}
		boss.setCustomName(Lang.get("ruin.cursed.name", bossName));
		boss.setCustomNameVisible(true);
		boss.setHealth(boss.getMaxHealth());
	}

	/** After a conversion (the attributes are rebuilt): the curse and the current phase again. */
	void reapply(Mob boss, String bossName) {
		modifiers(boss);
		boss.setCustomName(Lang.get("ruin.cursed.name", bossName));
	}

	private void modifiers(Mob boss) {
		multiply(boss, Attributes.MAX_HEALTH, HEALTH, stats.health());
		multiply(boss, Attributes.ATTACK_DAMAGE, DAMAGE, stats.damage());
		add(boss, Attributes.ARMOR, ARMOR, stats.armor());
		add(boss, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, stats.toughness());
		multiply(boss, Attributes.SCALE, SCALE, scale);
		multiply(boss, Attributes.MOVEMENT_SPEED, SPEED, 1.0 + stats.speed());
		phaseModifiers(boss);
	}

	private void phaseModifiers(Mob boss) {
		CursedSettings.PhaseSettings p = settings().phases;
		multiply(boss, Attributes.ATTACK_DAMAGE, PHASE_DAMAGE, CursedRules.phaseDamage(phase, p.damageBonus));
		multiply(boss, Attributes.MOVEMENT_SPEED, PHASE_SPEED, CursedRules.phaseSpeed(phase, p.speedBonus));
	}

	/** A cursed escort mob: more health and damage. */
	void curseMinion(Mob minion) {
		CursedSettings.GuardianSettings g = settings().guardian;
		multiply(minion, Attributes.MAX_HEALTH, MINION_HEALTH, g.minionHealthMultiplier);
		multiply(minion, Attributes.ATTACK_DAMAGE, MINION_DAMAGE, g.minionDamageMultiplier);
		minion.setHealth(minion.getMaxHealth());
	}

	private static void multiply(Mob mob, Holder<Attribute> attribute, Identifier id, double multiplier) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (Math.abs(multiplier - 1.0) < 1.0E-4) {
			instance.removeModifier(id);
			return;
		}
		instance.addOrReplacePermanentModifier(new AttributeModifier(id, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void add(Mob mob, Holder<Attribute> attribute, Identifier id, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && value > 1.0E-4) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ================================================================== queries

	/** Nothing hurts the guardian during a phase transition. */
	boolean invulnerable() {
		return transition != null;
	}

	/** Factor of the cooldowns of its normal abilities (on top of the threat level, the enrage...). */
	double cooldownFactor() {
		return CursedRules.abilityCooldown(settings(), phase);
	}

	/** Factor of the damage of its normal abilities. */
	double damageFactor() {
		return CursedRules.abilityDamage(settings(), phase);
	}

	/** Extra living escort mobs allowed. */
	int extraAlive() {
		return settings().guardian.extraAlive;
	}

	/** Centers of the pentagrams on the floor (telegraphed or burning). */
	List<Vec3> pentagrams() {
		List<Vec3> list = new ArrayList<>();
		for (CursedAttacks.Running attack : running) {
			if (attack instanceof Pentagrams set) {
				for (Pentagrams.Circle circle : set.circles) {
					list.add(circle.center());
				}
			}
		}
		return list;
	}

	/** Boss bar title: "Gardien Maudit — Menace II — Phase II — 3 joueurs — 540/720 ❤" (or the transition). */
	Component barName(Encounter encounter, float health, float max) {
		String name = Lang.colorize(encounter.def.boss.name);
		if (transition != null) {
			return Lang.get("ruin.cursed.bar.transition", name, CursedRules.roman(transition.toPhase));
		}
		String title = Lang.raw("ruin.cursed.bar", name, Encounters.threatLabel(encounter.threat), CursedRules.roman(phase));
		if (encounter.party.group()) {
			title = Lang.raw("ruin.bar.group", title, encounter.party.players());
		}
		return Lang.get("ruin.bar", title, (int) Math.ceil(health), (int) Math.ceil(max));
	}

	// ================================================================== phases

	/**
	 * ALLOW_DEATH of the guardian: refused outside the last phase (it keeps the next threshold's health and the transition
	 * starts), and during a transition.
	 */
	boolean allowDeath(ServerLevel level, Encounter encounter, Mob boss) {
		CursedSettings.PhaseSettings p = settings().phases;
		if (transition != null) {
			boss.setHealth((float) Math.max(1, CursedRules.threshold(transition.toPhase, p.phase2Below, p.phase3Below) * boss.getMaxHealth()));
			return false;
		}
		if (CursedRules.diesIn(phase)) {
			return true;
		}
		int next = phase + 1;
		boss.setHealth((float) Math.max(1, CursedRules.threshold(next, p.phase2Below, p.phase3Below) * boss.getMaxHealth()));
		startTransition(level, encounter, boss, next);
		return false;
	}

	/** After a hit: under the next threshold, the guardian keeps that health and the transition starts. */
	void afterDamage(ServerLevel level, Encounter encounter, Mob boss) {
		if (transition != null || phase >= CursedRules.LAST_PHASE || !boss.isAlive()) {
			return;
		}
		CursedSettings.PhaseSettings p = settings().phases;
		float max = Math.max(1.0F, boss.getMaxHealth());
		double ratio = boss.getHealth() / max;
		int next = CursedRules.next(phase, ratio, p.phase2Below, p.phase3Below);
		if (next > phase) {
			boss.setHealth((float) Math.max(1, CursedRules.floorRatio(phase, ratio, p.phase2Below, p.phase3Below) * max));
			startTransition(level, encounter, boss, next);
		}
	}

	/** Starts the transition to {@code toPhase}. @return false when one runs already or the phase is not the next one */
	boolean startTransition(ServerLevel level, Encounter encounter, Mob boss, int toPhase) {
		if (transition != null || toPhase != phase + 1 || toPhase > CursedRules.LAST_PHASE) {
			return false;
		}
		RuinConfig cfg = RuinModule.config();
		CursedTransition next = new CursedTransition(level, encounter, boss, toPhase, cfg.cursed);
		transition = next;
		transitions++;
		// The attacks of the previous phase stop (a transition is never a trap).
		running.clear();
		List<ServerPlayer> present = Encounters.present(level, encounter.ruin, cfg.encounter.presenceMargin);
		RitualFx fx = new RitualFx(level, next.anchor, Math.max(400, cfg.cursed.attacks.particleBudget / 2), cfg.summon.viewDistance);
		Safe.run("ruin.cursed.transition.begin", () -> next.begin(level, encounter, boss, present, cfg.cursed, fx));
		fx.flush();
		encounter.bar.setName(barName(encounter, boss.getHealth(), boss.getMaxHealth()));
		return true;
	}

	/** The transition is over: the guardian fights again, stronger, with the attacks of its new phase. */
	private void finishTransition(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, RuinConfig cfg) {
		CursedTransition done = transition;
		transition = null;
		if (done == null) {
			return;
		}
		phase = done.toPhase;
		done.release(boss, encounter, cfg);
		phaseModifiers(boss);
		boss.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1, false, true));
		if (phase >= CursedRules.LAST_PHASE) {
			boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, false, true));
		}
		long now = Scheduler.ticks();
		int k = 0;
		for (Attack attack : CursedRules.unlocked(phase)) {
			if (attack.phase == phase) {
				readyAt.put(attack, now + FIRST_USE[Math.min(k++, FIRST_USE.length - 1)]);
			}
		}
		nextAttackTick = now + 30;
		for (int i = 0; i < encounter.readyAt.length; i++) {
			encounter.readyAt[i] = Math.min(encounter.readyAt[i], now + 50 + i * 20L);
		}
		encounter.nextAbilityTick = now + 40;
		String bossName = Lang.colorize(encounter.def.boss.name);
		ServerPlayer nearest = null;
		for (ServerPlayer player : present) {
			Msg.send(player, "ruin.cursed.phase.chat." + phase);
			Msg.actionBar(player, "ruin.cursed.phase.back", bossName);
			if (BossAbilities.valid(player, level) && (nearest == null || player.distanceToSqr(boss) < nearest.distanceToSqr(boss))) {
				nearest = player;
			}
		}
		if (nearest != null) {
			boss.setTarget(nearest);
		}
		encounter.bar.setName(barName(encounter, boss.getHealth(), boss.getMaxHealth()));
	}

	// ================================================================== tick

	/** One tick of the fight: transition, attacks, clones, aura, the next attack. */
	void tick(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, RitualFx fx, RuinConfig cfg) {
		CursedSettings settings = cfg.cursed;
		CursedAttacks.Context ctx = new CursedAttacks.Context(level, encounter, this, boss, targets(level, boss, present, cfg), fx, settings, random);
		CursedClones.tick(ctx);
		CursedTransition current = transition;
		if (current != null) {
			boolean done = Safe.get("ruin.cursed.transition", () -> current.tick(level, encounter, boss, present, fx, settings), true);
			if (done || current.age > current.duration + 40) {
				finishTransition(level, encounter, boss, present, cfg);
			}
			return;
		}
		long now = Scheduler.ticks();
		if (phase > CursedRules.FIRST_PHASE && now >= nextAttackTick && running.size() < MAX_RUNNING && !ctx.targets.isEmpty()) {
			choose(ctx, now);
		}
		Iterator<CursedAttacks.Running> it = running.iterator();
		while (it.hasNext()) {
			CursedAttacks.Running attack = it.next();
			boolean keep = Safe.get("ruin.cursed.attack", () -> attack.tick(ctx), false);
			attack.age++;
			if (!keep) {
				it.remove();
			}
		}
		aura(ctx, now);
	}

	/** Survival / adventure players in reach of the guardian. */
	private static List<ServerPlayer> targets(ServerLevel level, Mob boss, List<ServerPlayer> present, RuinConfig cfg) {
		double range = cfg.encounter.abilityRange;
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : present) {
			if (BossAbilities.valid(player, level) && player.distanceToSqr(boss) <= range * range) {
				list.add(player);
			}
		}
		return list;
	}

	/** The next ready attack of the phase, in rotation. */
	private void choose(CursedAttacks.Context ctx, long now) {
		List<Attack> attacks = CursedRules.unlocked(phase);
		for (int i = 0; i < attacks.size(); i++) {
			Attack attack = attacks.get((rotation + i) % attacks.size());
			long ready = readyAt.getOrDefault(attack, now + 40L);
			readyAt.putIfAbsent(attack, ready);
			if (now < ready) {
				continue;
			}
			CursedAttacks.Running started = cast(attack, ctx);
			if (started == null) {
				readyAt.put(attack, now + 40);
				continue;
			}
			running.add(started);
			readyAt.put(attack, now + CursedRules.cooldownTicks(cooldownSeconds(attack, ctx.cfg()), CursedRules.phaseCooldown(phase,
					ctx.settings.phases.cooldownFactor)));
			nextAttackTick = now + Math.round(ctx.cfg().globalCooldownSeconds * 20);
			rotation = (rotation + i + 1) % Math.max(1, attacks.size());
			return;
		}
	}

	private static CursedAttacks.@Nullable Running cast(Attack attack, CursedAttacks.Context ctx) {
		return switch (attack) {
			case PENTAGRAMS -> Pentagrams.cast(ctx);
			case CHAINS -> CursedAttacks.Chains.cast(ctx);
			case METEORS -> CursedAttacks.Meteors.cast(ctx);
			case DARKNESS -> CursedAttacks.Darkness.cast(ctx);
			case CAGE -> CursedAttacks.Cage.cast(ctx);
			case NOVA -> CursedAttacks.Nova.cast(ctx);
			case CLONES -> CursedAttacks.Clones.cast(ctx);
		};
	}

	private static double cooldownSeconds(Attack attack, CursedSettings.AttackSettings c) {
		return switch (attack) {
			case PENTAGRAMS -> c.pentagramCooldownSeconds;
			case CHAINS -> c.chainCooldownSeconds;
			case METEORS -> c.meteorCooldownSeconds;
			case DARKNESS -> c.darknessCooldownSeconds;
			case CAGE -> c.cageCooldownSeconds;
			case NOVA -> c.novaCooldownSeconds;
			case CLONES -> c.cloneCooldownSeconds;
		};
	}

	/** Admin / tests: uses an attack now (cooldowns ignored). @return false during a transition or without a target */
	boolean force(Attack attack, ServerLevel level, Encounter encounter, Mob boss, RuinConfig cfg) {
		if (transition != null) {
			return false;
		}
		List<ServerPlayer> present = Encounters.present(level, encounter.ruin, cfg.encounter.presenceMargin);
		RitualFx fx = new RitualFx(level, boss.position(), cfg.cursed.attacks.particleBudget, cfg.summon.viewDistance);
		CursedAttacks.Context ctx = new CursedAttacks.Context(level, encounter, this, boss, targets(level, boss, present, cfg), fx, cfg.cursed, random);
		if (ctx.targets.isEmpty()) {
			return false;
		}
		CursedAttacks.Running started = cast(attack, ctx);
		fx.flush();
		if (started == null) {
			return false;
		}
		running.add(started);
		return true;
	}

	/** The cursed aura: heavy in the last phase (a spinning sigil, void and soul fire, wither close to it), faint before. */
	private void aura(CursedAttacks.Context ctx, long now) {
		Mob boss = ctx.boss;
		Vec3 feet = boss.position();
		double width = Math.max(0.6, boss.getBbWidth());
		double height = Math.max(0.6, boss.getBbHeight());
		if (phase < CursedRules.LAST_PHASE) {
			if (now % 4 == 0) {
				ctx.fx.burst(RitualFx.dust(CursedFx.VOID, 1.1F), feet.x, feet.y + height * 0.5, feet.z, 2, width * 0.5, height * 0.3, width * 0.5, 0.0);
				ctx.fx.burst(ParticleTypes.WITCH, feet.x, feet.y + height * 0.8, feet.z, 1, width * 0.4, 0.2, width * 0.4, 0.0);
			}
			return;
		}
		if (now % 2 == 0) {
			int age = (int) (now / 2 % 1000);
			CursedFx.helix(ctx.fx, ParticleTypes.SOUL_FIRE_FLAME, feet, width * 0.9 + 0.4, age, height + 0.6, 16);
			CursedFx.helix(ctx.fx, RitualFx.dust(CursedFx.PURPLE, 1.2F), feet, width * 0.9 + 0.4, age + 8, height + 0.6, 16);
			ctx.fx.burst(RitualFx.fade(CursedFx.VOID, CursedFx.BLOOD, 1.3F), feet.x, feet.y + height * 0.5, feet.z, 2, width * 0.5, height * 0.3, width * 0.5, 0.0);
		}
		if (now % 4 == 0) {
			double spin = now * 0.05;
			ctx.fx.polygram(RitualFx.dust(CursedFx.BLOOD_BRIGHT, 0.9F), feet.add(0, 0.08, 0), width + 1.0, 5, 2, spin, 1.0, 2.2);
			ctx.fx.ring(RitualFx.dust(CursedFx.PURPLE, 0.9F), feet.add(0, 0.08, 0), width + 1.1, 18, -spin);
		}
		CursedSettings.AttackSettings cfg = ctx.cfg();
		if (now % 20 == 0 && cfg.auraWitherSeconds > 0 && cfg.auraRadius > 0) {
			int ticks = (int) Math.round(cfg.auraWitherSeconds * 20);
			for (ServerPlayer player : ctx.targets) {
				if (player.distanceToSqr(boss) <= cfg.auraRadius * cfg.auraRadius) {
					player.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, 0, false, true, true), boss);
				}
			}
		}
		if (now % 100 == 0) {
			ctx.level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5F, 0.4F + random.nextFloat() * 0.2F);
		}
	}

	// ================================================================== awakening, failure, victory

	/**
	 * The cursed guardian appears: titles, the curse in the chat, a burst of void ({@code threatLine}: the threat label, shown
	 * on the boss bar only).
	 */
	void announce(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, String threatLine) {
		String ruinName = Lang.colorize(encounter.def.name);
		String bossName = Lang.colorize(encounter.def.boss.name);
		Component title = Lang.get("ruin.cursed.awaken.title", ruinName);
		Component subtitle = Lang.get("ruin.cursed.awaken.subtitle", bossName);
		for (ServerPlayer player : present) {
			Msg.title(player, title, subtitle, 10, 70, 20);
			Msg.send(player, "ruin.cursed.awaken.chat", bossName, ruinName, IncantationItems.number(stats.health()), IncantationItems.number(stats.damage()));
			GroupScaling.Party party = encounter.party;
			if (party.group()) {
				Msg.send(player, "ruin.awaken.group", party.players(), GroupScaling.percent(party.health()), GroupScaling.percent(party.damage()));
			}
			Msg.sound(player, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 0.7F);
		}
		Vec3 at = boss.position().add(0, boss.getBbHeight() / 2, 0);
		RuinFx.burst(level, RitualFx.dust(CursedFx.PURPLE, 1.6F), at, 40, 0.8, 0.0);
		RuinFx.burst(level, ParticleTypes.SOUL, at, 30, 0.7, 0.05);
		RuinFx.sound(level, at, SoundEvents.WITHER_SPAWN, 0.9F, 0.5F);
	}

	/** Everyone left or died: the cursed guardian vanishes in a burst, the ruin sleeps again (the scroll is lost). */
	void vanish(ServerLevel level, Encounter encounter, @Nullable Mob boss, boolean announce) {
		if (boss != null) {
			Vec3 at = boss.position().add(0, boss.getBbHeight() / 2, 0);
			RitualFx fx = new RitualFx(level, at, 600, RuinModule.config().summon.viewDistance);
			fx.dot(RitualFx.flash(CursedFx.PURPLE), at);
			fx.sphereBurst(RitualFx.fade(CursedFx.PURPLE, CursedFx.VOID, 1.8F), at, 0.4, 80, 0.5);
			fx.burst(ParticleTypes.LARGE_SMOKE, at, 50, 0.8, 0.06);
			fx.burst(ParticleTypes.SOUL, at, 40, 0.8, 0.1);
			fx.burst(ParticleTypes.REVERSE_PORTAL, at, 60, 0.6, 0.5);
			fx.flush();
			RuinFx.sound(level, at, SoundEvents.GHAST_SCREAM, 1.5F, 0.4F);
			RuinFx.sound(level, at, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
		}
		if (announce) {
			String bossName = Lang.colorize(encounter.def.boss.name);
			for (ServerPlayer player : Encounters.present(level, encounter.ruin, RuinModule.config().encounter.presenceMargin * 2)) {
				Msg.send(player, "ruin.cursed.vanish", bossName);
			}
		}
		running.clear();
		clones.clear();
	}

	/**
	 * The curse is broken: titles, sounds, a purifying burst of light and the server announcement (no note on the threat:
	 * {@code threat} / {@code raised} show on the boss bar, the maps and the atelier).
	 */
	void victory(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, int threat, boolean raised) {
		RuinConfig cfg = RuinModule.config();
		String bossName = Lang.colorize(encounter.def.boss.name);
		String ruinName = Lang.colorize(encounter.def.name);
		for (ServerPlayer player : present) {
			Msg.title(player, Lang.get("ruin.cursed.victory.title"), Lang.get("ruin.cursed.victory.subtitle", bossName), 10, 90, 25);
			Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.7F);
			Msg.sound(player, SoundEvents.ENDER_DRAGON_DEATH, 0.35F, 1.2F);
		}
		Vec3 at = boss.position();
		RitualFx fx = new RitualFx(level, at, 900, cfg.summon.viewDistance);
		Vec3 core = at.add(0, boss.getBbHeight() / 2, 0);
		fx.dot(RitualFx.flash(0xFFFFFF), core);
		fx.dot(RitualFx.flash(CursedFx.VIOLET), core.add(0, 2, 0));
		fx.sphereBurst(RitualFx.fade(CursedFx.VIOLET, 0xFFE9A0, 1.8F), core, 0.5, 140, 0.6);
		fx.burst(ParticleTypes.TOTEM_OF_UNDYING, core, 120, 0.8, 0.7);
		fx.burst(ParticleTypes.END_ROD, core, 60, 0.5, 0.4);
		fx.burst(ParticleTypes.SOUL, core, 60, 1.2, 0.15);
		CursedFx.pentagram(fx, CursedAttacks.floor(level, at.x, at.y, at.z).add(0, 0.1, 0), 4.5, 0, 1.0, 0xFFE9A0, CursedFx.VIOLET, 1.6F);
		fx.flush();
		RuinFx.play(level, (lvl, age) -> {
			// The freed souls rise into the sky.
			RuinFx.helix(lvl, ParticleTypes.SOUL, at, 1.2 + age * 0.05, age, 6.0, 30);
			RuinFx.helix(lvl, ParticleTypes.END_ROD, at, 0.8 + age * 0.04, age + 15, 8.0, 30);
			return age < 50;
		});
		RuinFx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 3.0F, 1.2F);
		RuinFx.sound(level, at, SoundEvents.TOTEM_USE, 2.0F, 0.8F);
		if (cfg.cursed.rewards.announce) {
			String names = encounter.names.isEmpty() ? Lang.raw("ruin.victory.someone") : String.join(", ", encounter.names.values());
			level.getServer().getPlayerList().broadcastSystemMessage(Msg.prefixed(Lang.get("ruin.cursed.victory.announce", names, bossName, ruinName)), false);
		}
		running.clear();
	}

	// ================================================================== rewards

	/**
	 * Drops added to a victory's loot: a cursed scroll sometimes on a guardian defeated in its risen form, and on a cursed
	 * guardian a blank grimoire ({@code grimoireChance}) and a guaranteed spell scroll when no roll gave one.
	 *
	 * @return the stacks dropped
	 */
	static int bonusDrops(ServerLevel level, Encounter encounter, Mob boss, RandomGenerator random, String scrollSource, boolean scrollDropped) {
		int dropped = 0;
		CursedSettings settings = settings();
		if (encounter.risen && encounter.cursed == null) {
			dropped += RuinLoot.protectedDrop(level, boss, CursedItems.rollRisen(random));
		}
		if (encounter.cursed == null) {
			return dropped;
		}
		ScrollConfig scrolls = ScrollModule.config();
		double chance = settings.rewards.grimoireChance;
		if (scrolls.dropsEnabled && scrolls.grimoire.enabled && chance > 0 && (chance >= 1 || random.nextDouble() < chance)) {
			dropped += RuinLoot.protectedDrop(level, boss, GrimoireItems.create(GrimoireRules.Pages.EMPTY));
		}
		if (settings.rewards.guaranteedScroll && !scrollDropped) {
			ItemStack scroll = ScrollDrops.pick(scrollSource, random);
			dropped += RuinLoot.protectedDrop(level, boss, scroll);
		}
		return dropped;
	}
}
