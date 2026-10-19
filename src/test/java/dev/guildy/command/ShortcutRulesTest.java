package dev.guildy.command;

import dev.guildy.command.ShortcutRules.Decision;
import dev.guildy.command.ShortcutRules.Outcome;
import dev.guildy.config.GuildyConfig;
import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutRulesTest {
	/** Fake /guild tree: the paths that exist. */
	private static final Set<List<String>> TREE = Set.of(List.of(), List.of("home"), List.of("sethome"), List.of("quests"),
			List.of("villagers"), List.of("villagers", "storage"), List.of("bilan"), List.of("deposit"), List.of("admin"),
			List.of("admin", "save"));
	/** Fake root commands of Minecraft and other mods. */
	private static final Set<String> ROOT = Set.of("guild", "gld", "tp", "give", "spawn");

	private static List<Decision> plan(Map<String, String> aliases) {
		return ShortcutRules.plan(aliases, TREE::contains, ROOT::contains);
	}

	private static Map<String, String> map(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@Test
	void aliasesAreNormalizedAndValidated() {
		assertEquals("home", ShortcutRules.normalizeAlias("  /Home "));
		assertEquals("", ShortcutRules.normalizeAlias(null));
		assertTrue(ShortcutRules.validAlias("home"));
		assertTrue(ShortcutRules.validAlias("g"));
		assertTrue(ShortcutRules.validAlias("my_home-2"));
		assertFalse(ShortcutRules.validAlias(""));
		assertFalse(ShortcutRules.validAlias("ho me"));
		assertFalse(ShortcutRules.validAlias("quêtes"));
		assertFalse(ShortcutRules.validAlias("guildy:home"));
		assertFalse(ShortcutRules.validAlias("a".repeat(ShortcutRules.MAX_ALIAS_LENGTH + 1)));
		assertTrue(ShortcutRules.validAlias("a".repeat(ShortcutRules.MAX_ALIAS_LENGTH)));
		assertTrue(ShortcutRules.reserved("guild"));
		assertTrue(ShortcutRules.reserved("gld"));
		assertFalse(ShortcutRules.reserved("g"));
	}

	@Test
	void targetsAreSubcommandPathsUnderGuild() {
		assertEquals(List.of("home"), ShortcutRules.parseTarget("home"));
		assertEquals(List.of("home"), ShortcutRules.parseTarget("/guild home"));
		assertEquals(List.of("quests"), ShortcutRules.parseTarget(" gld   Quests "));
		assertEquals(List.of("villagers", "storage"), ShortcutRules.parseTarget("villagers storage"));
		assertEquals(List.of(), ShortcutRules.parseTarget("guild"));
		assertEquals(List.of(), ShortcutRules.parseTarget("/gld"));
		assertEquals(List.of(), ShortcutRules.parseTarget(""));
		assertNull(ShortcutRules.parseTarget("deposit <amount>"));
		assertNull(ShortcutRules.parseTarget("info \"My guild\""));
		assertNull(ShortcutRules.parseTarget(null));
		assertEquals("/guild villagers storage", ShortcutRules.command(List.of("villagers", "storage")));
		assertEquals("/guild", ShortcutRules.command(List.of()));
	}

	@Test
	void planDecidesEveryEntryInOrder() {
		List<Decision> decisions = plan(map(
				"home", "home",
				"/Quetes", "quests",
				"g", "guild",
				"stock", "villagers storage",
				"tp", "home",
				"fly", "fly",
				"guild", "info",
				"HOME", "sethome",
				"ho me", "home",
				"depot", "deposit 5",
				"save", "admin save"));
		assertEquals(11, decisions.size());
		assertDecision(decisions.get(0), "home", Outcome.ACTIVE, "/guild home");
		assertDecision(decisions.get(1), "quetes", Outcome.ACTIVE, "/guild quests");
		assertDecision(decisions.get(2), "g", Outcome.ACTIVE, "/guild");
		assertDecision(decisions.get(3), "stock", Outcome.ACTIVE, "/guild villagers storage");
		// Vanilla /tp is never replaced.
		assertDecision(decisions.get(4), "tp", Outcome.COLLISION, "/guild home");
		// /guild fly does not exist (feature absent): skipped.
		assertDecision(decisions.get(5), "fly", Outcome.MISSING_TARGET, "/guild fly");
		assertDecision(decisions.get(6), "guild", Outcome.RESERVED, "/guild info");
		// Same name as the first entry once lowercased: the first one wins.
		assertDecision(decisions.get(7), "home", Outcome.DUPLICATE, "/guild sethome");
		assertEquals(Outcome.INVALID_ALIAS, decisions.get(8).outcome());
		// An argument value is not a subcommand.
		assertDecision(decisions.get(9), "depot", Outcome.MISSING_TARGET, "/guild deposit 5");
		// Admin targets are allowed: the copied requirements keep them gated.
		assertDecision(decisions.get(10), "save", Outcome.ACTIVE, "/guild admin save");
	}

	@Test
	void invalidTargetIsReportedAsWritten() {
		Decision decision = plan(map("pay", "withdraw <amount>")).getFirst();
		assertEquals(Outcome.INVALID_TARGET, decision.outcome());
		assertNull(decision.path());
		assertEquals("withdraw <amount>", decision.targetCommand());
	}

	@Test
	void collisionIsCheckedOnlyForExistingTargets() {
		// Missing target first: the "spawn" of another mod is irrelevant while /guild spawn does not exist.
		assertEquals(Outcome.MISSING_TARGET, plan(map("spawn", "spawn")).getFirst().outcome());
		assertEquals(Outcome.COLLISION, plan(map("spawn", "home")).getFirst().outcome());
	}

	@Test
	void missingDefaultTargetsAreNotWarnings() {
		Map<String, String> defaults = GuildyConfig.defaultShortcuts();
		Decision fly = plan(map("fly", "fly")).getFirst();
		assertEquals(Outcome.MISSING_TARGET, fly.outcome());
		assertFalse(ShortcutRules.worthWarning(fly, defaults), "default shortcut of an absent feature: debug only");
		Decision custom = plan(map("fly", "flight")).getFirst();
		assertTrue(ShortcutRules.worthWarning(custom, defaults), "a custom target that does not exist is a configuration error");
		assertTrue(ShortcutRules.worthWarning(plan(map("tp", "home")).getFirst(), defaults), "collisions are warned");
		assertFalse(ShortcutRules.worthWarning(plan(map("home", "home")).getFirst(), defaults));
	}

	@Test
	void defaultShortcutsAreValid() {
		Map<String, String> defaults = GuildyConfig.defaultShortcuts();
		assertEquals(List.of("g", "home", "sethome", "fly", "quetes", "villageois", "bilan"), List.copyOf(defaults.keySet()));
		for (Decision decision : ShortcutRules.plan(defaults, path -> true, name -> false)) {
			assertEquals(Outcome.ACTIVE, decision.outcome(), decision.alias());
		}
	}

	@Test
	void configSectionDefaultsAndOverrides() {
		GuildyConfig missing = Json5.fromString("{ \"general\": { \"autosaveMinutes\": 5 } }", GuildyConfig.class);
		missing.validate();
		assertTrue(missing.shortcuts.enabled);
		assertEquals(GuildyConfig.defaultShortcuts(), missing.shortcuts.aliases);

		GuildyConfig custom = Json5.fromString("{ \"shortcuts\": { \"enabled\": false, \"aliases\": { \"maison\": \"home\", "
				+ "\"zz\": null, \"depot\": \"deposit\" } } }", GuildyConfig.class);
		custom.validate();
		assertFalse(custom.shortcuts.enabled);
		assertEquals(List.of("maison", "depot"), List.copyOf(custom.shortcuts.aliases.keySet()), "the file replaces the defaults, in order");

		GuildyConfig nulls = Json5.fromString("{ \"shortcuts\": { \"aliases\": null } }", GuildyConfig.class);
		nulls.validate();
		assertEquals(GuildyConfig.defaultShortcuts(), nulls.shortcuts.aliases);
	}

	private static void assertDecision(Decision decision, String alias, Outcome outcome, String target) {
		assertEquals(alias, decision.alias());
		assertEquals(outcome, decision.outcome(), alias);
		assertEquals(target, decision.targetCommand(), alias);
	}
}
