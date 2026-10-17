package dev.guildy.scroll;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * One cast: the caster, their level and guild (allies), the scroll definition and its resolved spell parameters.
 */
final class SpellContext {
	final ServerPlayer caster;
	final ServerLevel level;
	final ScrollConfig.ScrollDef def;
	final Spell spell;
	final @Nullable Guild guild;

	SpellContext(ServerPlayer caster, ScrollConfig.ScrollDef def) {
		this.caster = caster;
		this.level = caster.level();
		this.def = def;
		this.spell = def.spell();
		this.guild = GuildManager.of(caster);
	}

	double p(String name) {
		return def.param(name);
	}

	int i(String name) {
		return def.intParam(name);
	}

	/** A duration parameter (seconds) in ticks. */
	int ticks(String name) {
		return (int) Math.round(def.param(name) * 20);
	}
}
