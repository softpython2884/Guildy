package dev.guildy.scroll;

import dev.guildy.stats.FightTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Spell dispatch: the conditions a spell needs (checked when the incantation starts and again when it completes, so a
 * scroll is never consumed for nothing) and the start of its effect.
 */
final class Spells {
	private Spells() {
	}

	/** Lang key of the reason the spell cannot be cast now (scroll kept), or null. */
	static @Nullable String check(SpellContext ctx) {
		return switch (ctx.spell) {
			case CHAIN_STORM -> ChainStormSpell.firstTarget(ctx) == null ? "scroll.fail.no_target" : null;
			case REVELATION -> RevelationSpell.anyMonster(ctx) ? null : "scroll.fail.no_monsters";
			case RECALL -> RecallSpell.check(ctx);
			case GUARDIANS -> GuardiansSpell.check(ctx);
			case SWAP -> SwapSpell.check(ctx);
			case RALLY -> RallySpell.check(ctx);
			case SPECTRAL_CHAINS -> SpectralChainsSpell.firstTarget(ctx) == null ? "scroll.fail.no_target" : null;
			case FIREWORKS -> FireworksSpell.check(ctx);
			default -> null;
		};
	}

	/** Casts the spell. @return lang key of a failure (nothing happened, scroll kept), or null on success */
	static @Nullable String cast(SpellContext ctx) {
		String problem = check(ctx);
		if (problem != null) {
			return problem;
		}
		switch (ctx.spell) {
			case METEOR_SHOWER -> EffectRunner.start(new MeteorShowerSpell(ctx, SpellTargets.aimPoint(ctx, ctx.p("range"))));
			case CHAIN_STORM -> {
				LivingEntity first = ChainStormSpell.firstTarget(ctx);
				if (first == null) {
					return "scroll.fail.no_target";
				}
				EffectRunner.start(new ChainStormSpell(ctx, first));
			}
			case BLIZZARD -> {
				double range = ctx.p("range");
				Vec3 center = range <= 0 ? ctx.caster.position() : SpellTargets.aimPoint(ctx, range);
				EffectRunner.start(new BlizzardSpell(ctx, center));
			}
			case SOLAR_NOVA -> EffectRunner.start(new SolarNovaSpell(ctx));
			case SINGULARITY -> EffectRunner.start(new SingularitySpell(ctx, SpellTargets.aimPoint(ctx, ctx.p("range"))));
			case AEGIS -> EffectRunner.start(new AegisSpell(ctx));
			case RECALL -> {
				String failure = RecallSpell.cast(ctx);
				if (failure == null) {
					FightTracker.spellCast(ctx.caster);
				}
				return failure;
			}
			case GUARDIANS -> {
				List<Vec3> spots = GuardiansSpell.spots(ctx);
				if (spots.isEmpty()) {
					return "scroll.fail.no_space";
				}
				EffectRunner.start(new GuardiansSpell(ctx, spots));
			}
			case BLESSING -> EffectRunner.start(new BlessingSpell(ctx));
			case REVELATION -> EffectRunner.start(new RevelationSpell(ctx));
			case EARTHQUAKE -> EffectRunner.start(new EarthquakeSpell(ctx));
			case ARCANE_RAY -> EffectRunner.start(new ArcaneRaySpell(ctx));
			case WAR_BANNER -> EffectRunner.start(new WarBannerSpell(ctx));
			case SCOURGE -> {
				double range = ctx.p("range");
				Vec3 center = range <= 0 ? ctx.caster.position() : SpellTargets.aimPoint(ctx, range);
				EffectRunner.start(new ScourgeSpell(ctx, center));
			}
			case SWAP, RALLY -> {
				String failure = ctx.spell == Spell.SWAP ? SwapSpell.cast(ctx) : RallySpell.cast(ctx);
				if (failure == null) {
					FightTracker.spellCast(ctx.caster);
				}
				return failure;
			}
			case SPECTRAL_CHAINS -> {
				LivingEntity first = SpectralChainsSpell.firstTarget(ctx);
				if (first == null) {
					return "scroll.fail.no_target";
				}
				EffectRunner.start(new SpectralChainsSpell(ctx, first));
			}
			case TAILWIND -> EffectRunner.start(new TailwindSpell(ctx));
			case FIREWORKS -> EffectRunner.start(new FireworksSpell(ctx, FireworksSpell.stage(ctx)));
		}
		FightTracker.spellCast(ctx.caster);
		return null;
	}
}
