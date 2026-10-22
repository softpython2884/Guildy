package dev.guildy.scroll;

import dev.guildy.core.MagicBoost;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * One cast: the caster, their level and guild (allies), the scroll definition and its resolved spell parameters, and the
 * power of the cast ({@link SpellPower#NONE} for a scroll; a grimoire multiplies damage, heals and durations; the cursed
 * grimoire amplifies most parameters). Every spell reads its parameters here, so they all follow the power of the cast, and
 * every hit of the cast is measured by its {@link CastDamage} (see {@link SpellTargets#hurt}).
 */
final class SpellContext {
	final ServerPlayer caster;
	final ServerLevel level;
	final ScrollConfig.ScrollDef def;
	final Spell spell;
	final @Nullable Guild guild;
	final SpellPower power;
	/**
	 * What this cast has done to each target: diminishing returns of repeated hits, cap against bosses (the cursed grimoire
	 * has its own); a new one per cast.
	 */
	final CastDamage damage;

	SpellContext(ServerPlayer caster, ScrollConfig.ScrollDef def) {
		this(caster, def, SpellPower.NONE);
	}

	SpellContext(ServerPlayer caster, ScrollConfig.ScrollDef def, SpellPower power) {
		this.caster = caster;
		this.level = caster.level();
		this.def = def;
		this.spell = def.spell();
		this.guild = GuildManager.of(caster);
		// The caster's magic elixirs (core MagicBoost) add their power on top of the scroll's or the grimoire's.
		this.power = (power == null ? SpellPower.NONE : power).boosted(MagicBoost.powerMultiplier(caster), spell);
		ScrollConfig cfg = ScrollModule.config();
		this.damage = new CastDamage(cfg.limits.repeatHitDecay, cfg.limits.repeatHitFloor,
				this.power.cursed() ? cfg.cursedGrimoire.bossCastCap : cfg.limits.bossCastCap);
	}

	/** A parameter of the spell for this cast (the scroll's value through the power of the cast). */
	double p(String name) {
		return power.apply(spell.param(name), def.param(name));
	}

	/** A count, level or flag parameter for this cast (rounded). */
	int i(String name) {
		return (int) Math.round(p(name));
	}

	/** A duration parameter (seconds) in ticks. */
	int ticks(String name) {
		return (int) Math.round(p(name) * 20);
	}

	/** Cast from a cursed grimoire (its own visuals on top of the spell's). */
	boolean cursed() {
		return power.cursed();
	}
}
