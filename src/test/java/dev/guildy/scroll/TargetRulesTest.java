package dev.guildy.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PvE only: spells hurt monsters and nothing else, buffs reach the caster, their guild members and their guild's villagers.
 */
class TargetRulesTest {
	/** Builder of facts (everything false, alive). */
	private static final class F {
		boolean alive = true;
		boolean monster;
		boolean player;
		boolean caster;
		boolean member;
		boolean guildVillager;
		boolean villager;
		boolean owned;
		boolean summoned;

		TargetRules.Facts build() {
			return new TargetRules.Facts(alive, monster, player, caster, member, guildVillager, villager, owned, summoned);
		}
	}

	private static TargetRules.Facts monster() {
		F f = new F();
		f.monster = true;
		return f.build();
	}

	@Test
	void onlyMonstersAreHurt() {
		assertTrue(TargetRules.mayHarm(monster()));
		F dead = new F();
		dead.monster = true;
		dead.alive = false;
		assertFalse(TargetRules.mayHarm(dead.build()), "dead monster");
		F player = new F();
		player.player = true;
		assertFalse(TargetRules.mayHarm(player.build()), "another player");
		F self = new F();
		self.player = true;
		self.caster = true;
		assertFalse(TargetRules.mayHarm(self.build()), "the caster");
		F villager = new F();
		villager.villager = true;
		assertFalse(TargetRules.mayHarm(villager.build()), "a villager or merchant");
		F guildVillager = new F();
		guildVillager.villager = true;
		guildVillager.guildVillager = true;
		assertFalse(TargetRules.mayHarm(guildVillager.build()), "a guild villager");
		F animal = new F();
		assertFalse(TargetRules.mayHarm(animal.build()), "an animal");
		F pet = new F();
		pet.owned = true;
		assertFalse(TargetRules.mayHarm(pet.build()), "a pet");
		F tamedMonster = new F();
		tamedMonster.monster = true;
		tamedMonster.owned = true;
		assertFalse(TargetRules.mayHarm(tamedMonster.build()), "a monster with an owner");
		F guardian = new F();
		guardian.summoned = true;
		assertFalse(TargetRules.mayHarm(guardian.build()), "a summoned guardian");
		F weird = new F();
		weird.monster = true;
		weird.player = true;
		assertFalse(TargetRules.mayHarm(weird.build()), "never a player, whatever else it is");
	}

	@Test
	void buffsGoToTheCasterAndTheirGuild() {
		F self = new F();
		self.player = true;
		self.caster = true;
		assertTrue(TargetRules.mayHelp(self.build()));
		F member = new F();
		member.player = true;
		member.member = true;
		assertTrue(TargetRules.mayHelp(member.build()));
		F guildVillager = new F();
		guildVillager.villager = true;
		guildVillager.guildVillager = true;
		assertTrue(TargetRules.mayHelp(guildVillager.build()));
		F stranger = new F();
		stranger.player = true;
		assertFalse(TargetRules.mayHelp(stranger.build()), "a player of another guild (or none)");
		F villager = new F();
		villager.villager = true;
		assertFalse(TargetRules.mayHelp(villager.build()), "a villager of no guild / a merchant");
		assertFalse(TargetRules.mayHelp(monster()));
		F deadMember = new F();
		deadMember.player = true;
		deadMember.member = true;
		deadMember.alive = false;
		assertFalse(TargetRules.mayHelp(deadMember.build()));
	}

	@Test
	void recallTakesGuildMembersOnly() {
		F member = new F();
		member.player = true;
		member.member = true;
		assertTrue(TargetRules.mayRecall(member.build()));
		F self = new F();
		self.player = true;
		self.caster = true;
		self.member = true;
		assertFalse(TargetRules.mayRecall(self.build()), "the caster is handled apart");
		F stranger = new F();
		stranger.player = true;
		assertFalse(TargetRules.mayRecall(stranger.build()));
		F guildVillager = new F();
		guildVillager.guildVillager = true;
		assertFalse(TargetRules.mayRecall(guildVillager.build()), "villagers stay home");
	}
}
