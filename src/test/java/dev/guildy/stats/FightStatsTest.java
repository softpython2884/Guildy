package dev.guildy.stats;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fight statistics, pure parts: who a hit belongs to (projectiles, pets, summons), healing without overheal, the tallies,
 * the titles of the summary and their ties, the totems of undying, the participants listed, the settings.
 */
class FightStatsTest {
	private static final double EPS = 1.0E-9;

	/** A fake entity: a player or not, with an owner (shooter, tamer, summoner). */
	private static final class Node {
		final String name;
		final boolean player;
		@Nullable Node owner;

		Node(String name, boolean player, @Nullable Node owner) {
			this.name = name;
			this.player = player;
			this.owner = owner;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private static @Nullable Node owner(@Nullable Node start) {
		return FightRules.owner(start, n -> n.owner, n -> n.player, FightRules.MAX_OWNER_DEPTH);
	}

	// ================================================================== attribution

	@Test
	void hitsBelongToThePlayerBehindThem() {
		Node steve = new Node("steve", true, null);
		assertSame(steve, owner(steve), "a melee hit: the player himself");
		Node arrow = new Node("arrow", false, steve);
		assertSame(steve, owner(arrow), "a projectile: its shooter");
		Node wolf = new Node("wolf", false, steve);
		assertSame(steve, owner(wolf), "a pet: its owner");
		Node golem = new Node("golem", false, steve);
		Node fireball = new Node("fireball", false, golem);
		assertSame(steve, owner(fireball), "the projectile of a summon: the summoner");
		assertNull(owner(null), "no entity");
	}

	@Test
	void monstersAndLoopsBelongToNobody() {
		Node skeleton = new Node("skeleton", false, null);
		assertNull(owner(new Node("arrow", false, skeleton)), "a monster's arrow is nobody's");
		Node evoker = new Node("evoker", false, null);
		assertNull(owner(new Node("vex", false, evoker)), "a vex belongs to its evoker, not to a player");
		Node a = new Node("a", false, null);
		Node b = new Node("b", false, a);
		a.owner = b;
		assertNull(owner(a), "an ownership loop ends");
		Node self = new Node("self", false, null);
		self.owner = self;
		assertNull(owner(self), "owner of itself");
		// A chain longer than the bound is cut.
		Node player = new Node("p", true, null);
		Node current = player;
		for (int i = 0; i < 10; i++) {
			current = new Node("n" + i, false, current);
		}
		assertNull(owner(current), "too deep");
		assertSame(player, FightRules.owner(current, n -> n.owner, n -> n.player, 20), "found with a larger bound");
	}

	// ================================================================== healing, losses

	@Test
	void healingNeverCountsOverheal() {
		assertEquals(4, FightRules.effectiveHeal(10, 20, 4), EPS, "missing 10, healed 4");
		assertEquals(2, FightRules.effectiveHeal(18, 20, 6), EPS, "only the 2 missing points count");
		assertEquals(0, FightRules.effectiveHeal(20, 20, 6), EPS, "full health: nothing");
		assertEquals(0, FightRules.effectiveHeal(0, 20, 6), EPS, "a dead player is not healed");
		assertEquals(0, FightRules.effectiveHeal(10, 20, -3), EPS, "negative heal");
		assertEquals(0, FightRules.effectiveHeal(10, 20, Double.NaN), EPS);
		assertEquals(0, FightRules.effectiveHeal(10, 20, Double.POSITIVE_INFINITY), EPS);
		assertEquals(3, FightRules.gained(4, 7), EPS, "absorption 4 -> 7");
		assertEquals(0, FightRules.gained(8, 4), EPS, "nothing gained when it went down");
	}

	@Test
	void talliesIgnoreInvalidAmounts() {
		FightTally t = new FightTally();
		t.addDamage(FightKind.BOSS, 12.5);
		t.addDamage(FightKind.BOSS, -3);
		t.addDamage(FightKind.BOSS, Double.NaN);
		t.addDamage(FightKind.ESCORT, 1.0E9);
		t.addDamage(FightKind.GUARD, 7);
		assertEquals(12.5, t.damage(FightKind.BOSS), EPS);
		assertEquals(FightTally.MAX_EVENT, t.damage(FightKind.ESCORT), EPS, "one event is capped");
		assertEquals(12.5 + FightTally.MAX_EVENT + 7, t.damageDealt(), EPS);
		t.addKill(FightKind.ESCORT);
		t.addKill(FightKind.GUARD);
		t.addKill(FightKind.GUARD);
		assertEquals(3, t.kills());
		assertEquals(2, t.kills(FightKind.GUARD));
		FightTally copy = t.copy();
		t.addHealing(5);
		assertEquals(0, copy.healing(), EPS, "a copy is independent");
		assertEquals(3, copy.kills());
	}

	@Test
	void whoQualifiesForTheSummary() {
		FightTally idle = new FightTally();
		idle.addSecond();
		assertFalse(idle.qualifies(1), "only there: not listed");
		assertTrue(idle.qualifies(0), "threshold 0: everyone who came");
		FightTally healer = new FightTally();
		healer.addHealing(3);
		assertTrue(healer.qualifies(1), "a healer who dealt nothing is listed");
		FightTally tank = new FightTally();
		tank.addTaken(40);
		assertTrue(tank.qualifies(20));
		assertFalse(tank.qualifies(50));
		FightTally killer = new FightTally();
		killer.addKill(FightKind.ESCORT);
		assertTrue(killer.qualifies(1000), "a kill always counts");
		FightTally blow = new FightTally();
		blow.setFinalBlow();
		assertTrue(blow.qualifies(1000), "the final blow always counts");
	}

	// ================================================================== titles

	private static FightReport.Header header() {
		return new FightReport.Header("Crypte", "Seigneur", "Menace II", false, false, FightReport.Outcome.VICTORY, 2400, 3600, 4, 3, 7, -1);
	}

	@Test
	void titlesGoToTheBestPlayers() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		Map<UUID, FightTally> tallies = new LinkedHashMap<>();
		Map<UUID, String> names = new LinkedHashMap<>();
		FightTally ta = new FightTally();
		ta.addDamage(FightKind.BOSS, 1240);
		ta.addDamage(FightKind.ESCORT, 20);
		ta.addTaken(80);
		ta.setFinalBlow();
		FightTally tb = new FightTally();
		tb.addDamage(FightKind.BOSS, 300);
		tb.addDamage(FightKind.GUARD, 150);
		tb.addDamage(FightKind.ESCORT, 90);
		tb.addKill(FightKind.ESCORT);
		tb.addKill(FightKind.ESCORT);
		tb.addHealing(36);
		tb.addShield(16);
		FightTally tc = new FightTally();
		tc.addSecond();
		tallies.put(c, tc);
		tallies.put(b, tb);
		tallies.put(a, ta);
		names.put(a, "Alex");
		names.put(b, "Bea");
		names.put(c, "Cid");
		FightReport report = FightReport.of(header(), names, tallies, 1, Set.of(c));
		assertEquals(List.of(a, b), report.entries().stream().map(FightReport.Entry::player).toList(), "sorted by damage to the guardian");
		assertEquals(List.of(a, b, c), report.recipients(), "the listed players, then the others present");
		assertEquals(1, report.rank(a));
		assertEquals(0, report.rank(c), "not listed");
		assertNull(report.entry(c));

		List<FightReport.Title> titles = report.titles(List.of(FightCategory.values()));
		Map<FightCategory, FightReport.Title> byCategory = new LinkedHashMap<>();
		titles.forEach(t -> byCategory.put(t.category(), t));
		assertEquals(List.of(a), holders(byCategory.get(FightCategory.GUARDIAN)), "Fléau du gardien");
		assertEquals(1240, byCategory.get(FightCategory.GUARDIAN).value(), EPS);
		assertEquals(List.of(b), holders(byCategory.get(FightCategory.GUARDS)), "Briseur de gardes");
		assertEquals(List.of(b), holders(byCategory.get(FightCategory.ESCORT)), "Chasseur de sbires");
		assertEquals(List.of(b), holders(byCategory.get(FightCategory.HEALING)), "Ange gardien");
		assertEquals(List.of(b), holders(byCategory.get(FightCategory.SHIELD)), "Protecteur");
		assertEquals(List.of(a), holders(byCategory.get(FightCategory.TANK)), "Rempart");
		assertEquals(List.of(a), holders(byCategory.get(FightCategory.FINAL_BLOW)), "Coup fatal");
		assertEquals(List.of(b), holders(byCategory.get(FightCategory.KILLS)), "Moissonneur (2 kills)");
		assertFalse(byCategory.containsKey(FightCategory.SPELLS), "nobody cast a spell: no title");
		assertEquals(List.of(FightCategory.GUARDIAN, FightCategory.TANK, FightCategory.FINAL_BLOW),
				report.titlesOf(a, List.of(FightCategory.values())));

		// Only the configured categories, in their order.
		List<FightReport.Title> chosen = report.titles(List.of(FightCategory.HEALING, FightCategory.GUARDIAN));
		assertEquals(List.of(FightCategory.HEALING, FightCategory.GUARDIAN), chosen.stream().map(FightReport.Title::category).toList());
	}

	@Test
	void tiesShareTheTitle() {
		List<FightReport.Entry> entries = new ArrayList<>();
		FightTally one = new FightTally();
		one.addDamage(FightKind.BOSS, 100.2);
		FightTally two = new FightTally();
		two.addDamage(FightKind.BOSS, 99.7);
		FightTally three = new FightTally();
		three.addDamage(FightKind.BOSS, 60);
		entries.add(new FightReport.Entry(UUID.randomUUID(), "One", one));
		entries.add(new FightReport.Entry(UUID.randomUUID(), "Two", two));
		entries.add(new FightReport.Entry(UUID.randomUUID(), "Three", three));
		List<FightReport.Title> titles = FightReport.titles(entries, List.of(FightCategory.GUARDIAN, FightCategory.HEALING));
		assertEquals(1, titles.size(), "nobody healed: no Ange gardien");
		FightReport.Title title = titles.getFirst();
		assertEquals(List.of("One", "Two"), title.holders().stream().map(FightReport.Entry::name).toList(), "100 and 100 as shown: shared");
		assertEquals("100", FightRules.number(title.value()));

		FightTally a = new FightTally();
		a.addKill(FightKind.GUARD);
		FightTally b = new FightTally();
		b.addKill(FightKind.GUARD);
		List<FightReport.Entry> killers = List.of(new FightReport.Entry(UUID.randomUUID(), "A", a), new FightReport.Entry(UUID.randomUUID(), "B", b));
		FightReport.Title kills = FightReport.titles(killers, List.of(FightCategory.KILLS)).getFirst();
		assertEquals(2, kills.holders().size(), "one kill each: both");
		assertEquals(1, kills.value(), EPS);

		FightTally tiny = new FightTally();
		tiny.addHealing(0.3);
		assertTrue(FightReport.titles(List.of(new FightReport.Entry(UUID.randomUUID(), "T", tiny)), List.of(FightCategory.HEALING)).isEmpty(),
				"0.3 shows as 0: no title");
	}

	@Test
	void reportCopiesTheTallies() {
		UUID a = UUID.randomUUID();
		FightTally t = new FightTally();
		t.addDamage(FightKind.BOSS, 10);
		FightReport report = FightReport.of(header(), Map.of(a, "A"), Map.of(a, t), 1, List.of());
		t.addDamage(FightKind.BOSS, 50);
		assertEquals(10, report.entry(a).tally().damage(FightKind.BOSS), EPS, "a summary never changes after it is built");
		assertTrue(FightReport.of(header(), Map.of(), Map.of(), 1, List.of()).recipients().isEmpty());
	}

	// ================================================================== formatting, settings

	@Test
	void numbersAndClocks() {
		assertEquals("0", FightRules.number(0.2));
		assertEquals("1 240", FightRules.number(1239.6));
		assertEquals("12 345 678", FightRules.number(12_345_678));
		assertEquals("999", FightRules.number(999));
		assertEquals("0:00", FightRules.clock(0));
		assertEquals("3:07", FightRules.clock((3 * 60 + 7) * 20));
		assertEquals("1:02:45", FightRules.clock((3600 + 2 * 60 + 45) * 20L));
		assertEquals(35, FightRules.percent(0.351));
		assertEquals(100, FightRules.percent(3));
		assertEquals(0, FightRules.percent(Double.NaN));
	}

	@Test
	void settingsAreBounded() {
		FightStatsSettings s = new FightStatsSettings();
		List<String> warnings = new ArrayList<>();
		s.fix(warnings);
		assertTrue(warnings.isEmpty(), "defaults are valid: " + warnings);
		assertTrue(s.enabled && s.onFailure);
		assertEquals(FightStatsSettings.DEFAULT_CATEGORIES, s.categories);
		assertEquals(List.of(FightCategory.GUARDIAN, FightCategory.GUARDS, FightCategory.ESCORT, FightCategory.HEALING, FightCategory.SHIELD,
				FightCategory.TANK, FightCategory.TOTEMS, FightCategory.FINAL_BLOW), s.categoryList());
		assertEquals(FightStatsSettings.CURRENT_VERSION, s.version);

		s.categories = new ArrayList<>(List.of("TANK", "nope", "tank", " spells "));
		s.minDamage = -4;
		s.keepMinutes = 0;
		s.maxPlayers = 1000;
		s.fix(warnings);
		assertEquals(List.of("tank", "spells"), s.categories, "unknown dropped, duplicates removed, ids normalized");
		assertEquals(1, warnings.size(), "one warning for the unknown id: " + warnings);
		assertEquals(0, s.minDamage, EPS);
		assertEquals(1, s.keepMinutes);
		assertEquals(64, s.maxPlayers);
		s.categories = null;
		s.minDamage = Double.NaN;
		s.fix(warnings);
		assertEquals(FightStatsSettings.DEFAULT_CATEGORIES, s.categories);
		assertEquals(1, s.minDamage, EPS);
		assertNull(FightCategory.byId("unknown"));
		assertSame(FightCategory.FINAL_BLOW, FightCategory.byId("Final_Blow"));
	}

	// ================================================================== totems of undying

	@Test
	void totemsAreCountedAndEarnTheirTitle() {
		FightTally t = new FightTally();
		assertEquals(0, t.totems());
		assertFalse(t.qualifies(1), "nothing done");
		t.addTotem();
		assertTrue(t.qualifies(1000), "a totem used: listed whatever the threshold");
		t.addTotem();
		FightTally copy = t.copy();
		t.addTotem();
		assertEquals(2, copy.totems(), "copied, then independent");
		assertEquals(3, t.totems());
		assertEquals(3, FightCategory.TOTEMS.value(t), EPS);
		assertTrue(FightCategory.TOTEMS.count(), "counted in whole totems");
		assertSame(FightCategory.TOTEMS, FightCategory.byId("totems"));

		// The best damage dealer went through 40 totems, the healer through 2, the third none.
		FightTally hero = new FightTally();
		hero.addDamage(FightKind.BOSS, 2000);
		for (int i = 0; i < 40; i++) {
			hero.addTotem();
		}
		FightTally healer = new FightTally();
		healer.addHealing(50);
		healer.addTotem();
		healer.addTotem();
		FightTally safe = new FightTally();
		safe.addDamage(FightKind.BOSS, 100);
		UUID h = UUID.randomUUID();
		UUID m = UUID.randomUUID();
		UUID s = UUID.randomUUID();
		Map<UUID, FightTally> tallies = new LinkedHashMap<>();
		tallies.put(s, safe);
		tallies.put(m, healer);
		tallies.put(h, hero);
		FightReport report = FightReport.of(header(), Map.of(h, "Hero", m, "Medic", s, "Safe"), tallies, 1, List.of());
		assertEquals(42, report.totems(), "the team total");
		FightReport.Title increvable = FightReport.titles(report.entries(), List.of(FightCategory.TOTEMS)).getFirst();
		assertEquals(List.of(h), holders(increvable), "Increvable: the most totems");
		assertEquals(40, increvable.value(), EPS);
		FightReport.Title guardian = FightReport.titles(report.entries(), List.of(FightCategory.GUARDIAN)).getFirst();
		assertEquals(40, FightReport.totems(guardian.holders()), "the totems behind the best damage on the guardian");
		assertEquals(List.of(FightCategory.GUARDIAN, FightCategory.TOTEMS), report.titlesOf(h, List.of(FightCategory.values())));

		// Nobody used one: no title; a tie shares it.
		assertTrue(FightReport.titles(List.of(new FightReport.Entry(s, "Safe", safe)), List.of(FightCategory.TOTEMS)).isEmpty());
		FightTally one = new FightTally();
		one.addTotem();
		FightTally other = new FightTally();
		other.addTotem();
		List<FightReport.Entry> tie = List.of(new FightReport.Entry(UUID.randomUUID(), "One", one), new FightReport.Entry(UUID.randomUUID(), "Other", other));
		FightReport.Title shared = FightReport.titles(tie, List.of(FightCategory.TOTEMS)).getFirst();
		assertEquals(2, shared.holders().size(), "one totem each: both");
		assertEquals(2, FightReport.totems(shared.holders()));
	}

	@Test
	void shortFormKeepsTheFirstTitleAndIncrevable() {
		FightTally a = new FightTally();
		a.addDamage(FightKind.BOSS, 80);
		a.addTaken(30);
		a.addTotem();
		FightTally b = new FightTally();
		b.addHealing(12);
		List<FightReport.Entry> entries = List.of(new FightReport.Entry(UUID.randomUUID(), "A", a), new FightReport.Entry(UUID.randomUUID(), "B", b));
		List<FightCategory> categories = new FightStatsSettings().categoryList();
		List<FightReport.Title> titles = FightReport.titles(entries, categories);
		assertEquals(List.of(FightCategory.GUARDIAN, FightCategory.HEALING, FightCategory.TANK, FightCategory.TOTEMS), categories(titles));
		assertEquals(categories(titles), categories(FightReport.chatTitles(titles, FightReport.Outcome.VICTORY)), "a victory shows them all");
		assertEquals(List.of(FightCategory.GUARDIAN, FightCategory.TOTEMS), categories(FightReport.chatTitles(titles, FightReport.Outcome.FAILURE)),
				"a failure: the first title, and Increvable");
		FightTally noTotem = new FightTally();
		noTotem.addDamage(FightKind.BOSS, 5);
		noTotem.addTaken(4);
		List<FightReport.Title> plain = FightReport.titles(List.of(new FightReport.Entry(UUID.randomUUID(), "N", noTotem)), categories);
		assertEquals(List.of(FightCategory.GUARDIAN), categories(FightReport.chatTitles(plain, FightReport.Outcome.FAILURE)),
				"no totem: the first title only");
		List<FightReport.Title> onlyTotems = FightReport.titles(entries, List.of(FightCategory.TOTEMS, FightCategory.GUARDIAN));
		assertEquals(List.of(FightCategory.TOTEMS), categories(FightReport.chatTitles(onlyTotems, FightReport.Outcome.FAILURE)),
				"Increvable first: shown once");
		assertTrue(FightReport.chatTitles(List.of(), FightReport.Outcome.FAILURE).isEmpty());
	}

	@Test
	void oldTitleListsGetTheTotems() {
		// A section written by 1.8.1 - 1.9 (no version) with the default titles of that time: Increvable is added after Rempart.
		FightStatsSettings old = new FightStatsSettings();
		old.version = 0;
		old.categories = new ArrayList<>(FightStatsSettings.V1_CATEGORIES);
		List<String> warnings = new ArrayList<>();
		old.fix(warnings);
		assertEquals(FightStatsSettings.DEFAULT_CATEGORIES, old.categories);
		assertEquals(FightStatsSettings.CURRENT_VERSION, old.version);
		assertTrue(warnings.isEmpty(), "" + warnings);
		// Fixed again (next load): unchanged.
		old.fix(warnings);
		assertEquals(FightStatsSettings.DEFAULT_CATEGORIES, old.categories);
		// A customised list is left alone, and so is the old list chosen again on purpose after the update.
		FightStatsSettings custom = new FightStatsSettings();
		custom.version = 0;
		custom.categories = new ArrayList<>(List.of("guardian", "kills"));
		custom.fix(warnings);
		assertEquals(List.of("guardian", "kills"), custom.categories);
		FightStatsSettings chosen = new FightStatsSettings();
		chosen.version = FightStatsSettings.CURRENT_VERSION;
		chosen.categories = new ArrayList<>(FightStatsSettings.V1_CATEGORIES);
		chosen.fix(warnings);
		assertEquals(FightStatsSettings.V1_CATEGORIES, chosen.categories, "an up-to-date section is never migrated");
	}

	private static List<FightCategory> categories(List<FightReport.Title> titles) {
		return titles.stream().map(FightReport.Title::category).toList();
	}

	private static List<UUID> holders(FightReport.Title title) {
		return title.holders().stream().map(FightReport.Entry::player).toList();
	}
}
