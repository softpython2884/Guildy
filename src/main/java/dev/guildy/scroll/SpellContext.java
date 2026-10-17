package dev.guildy.scroll;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * One cast: the caster, their level and guild (allies), the scroll definition and its resolved spell parameters, and the
 * power of the cast (1 for a scroll; a grimoire multiplies damage, heals and durations).
 */
final class SpellContext {
	final ServerPlayer caster;
	final ServerLevel level;
	final ScrollConfig.ScrollDef def;
	final Spell spell;
	final @Nullable Guild guild;
	final double power;

	SpellContext(ServerPlayer caster, ScrollConfig.ScrollDef def) {
		this(caster, def, 1.0);
	}

	SpellContext(ServerPlayer caster, ScrollConfig.ScrollDef def, double power) {
		this.caster = caster;
		this.level = caster.level();
		this.def = def;
		this.spell = def.spell();
		this.guild = GuildManager.of(caster);
		this.power = power;
	}

	double p(String name) {
		double value = def.param(name);
		Spell.Param param = spell.param(name);
		return param == null ? value : param.powered(value, power);
	}

	int i(String name) {
		return def.intParam(name);
	}

	/** A duration parameter (seconds) in ticks. */
	int ticks(String name) {
		return (int) Math.round(p(name) * 20);
	}
}
