package dev.guildy.scroll;

/**
 * Who a spell may hurt and who it may help (pure Java, unit-tested). Spells are PvE only: damage and debuffs land on
 * monsters only, never on a player, a villager (of a guild or not), a merchant, a pet or a summoned guardian; buffs go to
 * the caster, the members of the caster's guild and the villagers of that guild only.
 */
public final class TargetRules {
	private TargetRules() {
	}

	/**
	 * What the spell knows about an entity.
	 *
	 * @param alive        alive and not a spectator
	 * @param monster      a hostile mob (vanilla {@code Enemy})
	 * @param player       any player
	 * @param caster       the caster
	 * @param guildMember  a player of the caster's guild
	 * @param guildVillager a villager of the caster's guild
	 * @param villager     any villager or merchant (guild villagers included)
	 * @param owned        a pet / tamed entity (has an owner)
	 * @param summoned     a guardian summoned by a scroll
	 */
	public record Facts(boolean alive, boolean monster, boolean player, boolean caster, boolean guildMember, boolean guildVillager,
			boolean villager, boolean owned, boolean summoned) {
	}

	/** Damage, debuffs, pull / push: monsters only. */
	public static boolean mayHarm(Facts facts) {
		return facts.alive() && facts.monster() && !facts.player() && !facts.caster() && !facts.villager() && !facts.guildVillager()
				&& !facts.owned() && !facts.summoned();
	}

	/** Heals and protections: the caster, the members of their guild, the villagers of their guild. */
	public static boolean mayHelp(Facts facts) {
		if (!facts.alive()) {
			return false;
		}
		return facts.caster() || facts.player() && facts.guildMember() || facts.guildVillager();
	}

	/** Teleported along by a recall: the members of the caster's guild (players only). */
	public static boolean mayRecall(Facts facts) {
		return facts.alive() && facts.player() && !facts.caster() && facts.guildMember();
	}

	/**
	 * Swapped with the caster by a permutation: a monster (pulled out of a corner), or an ally to rescue (a member or a villager
	 * of the caster's guild); never the caster, another player, another villager, a pet or a summoned guardian.
	 */
	public static boolean maySwap(Facts facts) {
		return mayHarm(facts) || mayHelp(facts) && !facts.caster();
	}
}
