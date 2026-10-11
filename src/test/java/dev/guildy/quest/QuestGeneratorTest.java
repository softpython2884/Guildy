package dev.guildy.quest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestGeneratorTest {

	private static QuestTemplate template(String id, QuestType type, int weight, int minLevel) {
		QuestTemplate t = new QuestTemplate();
		t.id = id;
		t.type = type;
		t.name = id;
		t.target = type.hasTarget() ? "minecraft:stone" : "";
		t.amountMin = 10;
		t.amountMax = 20;
		t.amountPerLevel = 5;
		t.rewardGoldMin = 4;
		t.rewardGoldMax = 8;
		t.rewardGoldPerLevel = 1.5;
		t.rewardXp = 30;
		t.rewardXpPerLevel = 10;
		t.weight = weight;
		t.minLevel = minLevel;
		t.durationDays = 2;
		return t;
	}

	@Test
	void sameSeedSameQuest() {
		List<QuestTemplate> templates = List.of(template("a", QuestType.GATHER, 10, 1), template("b", QuestType.KILL, 10, 1),
				template("c", QuestType.DELIVER, 10, 1));
		QuestGenerator.Rolled first = QuestGenerator.generate(templates, 3, Set.of(), true, new Random(42));
		QuestGenerator.Rolled second = QuestGenerator.generate(templates, 3, Set.of(), true, new Random(42));
		assertNotNull(first);
		assertNotNull(second);
		assertEquals(first.template().id, second.template().id);
		assertEquals(first.amount(), second.amount());
		assertEquals(first.gold(), second.gold());
		assertEquals(first.xp(), second.xp());
		assertEquals(first.flags(), second.flags());
		assertEquals(first.treasureMap(), second.treasureMap());
	}

	@Test
	void eligibilityFilters() {
		QuestTemplate low = template("low", QuestType.GATHER, 10, 1);
		QuestTemplate high = template("high", QuestType.GATHER, 10, 5);
		QuestTemplate capped = template("capped", QuestType.GATHER, 10, 1);
		capped.maxLevel = 2;
		QuestTemplate disabled = template("disabled", QuestType.GATHER, 0, 1);
		QuestTemplate explore = template("explore", QuestType.EXPLORE, 10, 1);
		List<QuestTemplate> all = List.of(low, high, capped, disabled, explore);

		assertEquals(List.of(low, capped, explore), QuestGenerator.eligible(all, 1, Set.of(), true));
		assertEquals(List.of(low, capped), QuestGenerator.eligible(all, 1, Set.of(), false));
		assertEquals(List.of(low, high, explore), QuestGenerator.eligible(all, 5, Set.of(), true));
		assertEquals(List.of(capped, explore), QuestGenerator.eligible(all, 1, Set.of("low"), true));
	}

	@Test
	void weightedChoice() {
		List<QuestTemplate> eligible = List.of(template("rare", QuestType.GATHER, 1, 1), template("common", QuestType.GATHER, 3, 1));
		Random random = new Random(7);
		int rare = 0;
		int draws = 20000;
		for (int i = 0; i < draws; i++) {
			if (QuestGenerator.pick(eligible, random).id.equals("rare")) {
				rare++;
			}
		}
		double ratio = (double) rare / draws;
		assertTrue(ratio > 0.22 && ratio < 0.28, "ratio " + ratio);
		assertNull(QuestGenerator.pick(List.of(), random));
	}

	@Test
	void valuesScaleWithLevel() {
		QuestTemplate t = template("a", QuestType.GATHER, 10, 1);
		for (int level = 1; level <= 10; level++) {
			int bonus = (int) Math.floor(5.0 * (level - 1));
			int goldBonus = (int) Math.floor(1.5 * (level - 1));
			for (long seed = 0; seed < 50; seed++) {
				QuestGenerator.Rolled rolled = QuestGenerator.roll(t, level, new Random(seed));
				assertTrue(rolled.amount() >= 10 + bonus && rolled.amount() <= 20 + bonus, "amount " + rolled.amount());
				assertTrue(rolled.gold() >= 4 + goldBonus && rolled.gold() <= 8 + goldBonus, "gold " + rolled.gold());
				assertEquals(30 + 10 * (level - 1), rolled.xp());
				assertEquals(2 * QuestGenerator.DAY_TICKS, rolled.durationTicks());
			}
		}
	}

	@Test
	void rewardChances() {
		QuestTemplate t = template("a", QuestType.GATHER, 10, 1);
		t.rewardItems = new ArrayList<>(List.of(new QuestTemplate.RewardItem("minecraft:bread", 4, 1.0),
				new QuestTemplate.RewardItem("minecraft:diamond", 1, 0.0)));
		t.flagChance = 1.0;
		t.treasureMapChance = 0.0;
		QuestGenerator.Rolled rolled = QuestGenerator.roll(t, 1, new Random(3));
		assertEquals(1, rolled.items().size());
		assertEquals("minecraft:bread", rolled.items().get(0).item);
		assertEquals(4, rolled.items().get(0).count);
		assertEquals(1, rolled.flags());
		assertFalse(rolled.treasureMap());
	}

	@Test
	void exploreAlwaysNeedsOneVisit() {
		QuestTemplate t = template("e", QuestType.EXPLORE, 10, 1);
		assertTrue(t.check().isEmpty());
		assertEquals(1, QuestGenerator.roll(t, 9, new Random(1)).amount());
	}

	@Test
	void noDuplicateTemplatesOnTheBoard() {
		List<QuestTemplate> templates = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			templates.add(template("t" + i, QuestType.GATHER, 1 + i, 1));
		}
		Random random = new Random(11);
		Set<String> active = new HashSet<>();
		for (int slot = 0; slot < 6; slot++) {
			QuestGenerator.Rolled rolled = QuestGenerator.generate(templates, 1, active, true, random);
			assertNotNull(rolled);
			assertTrue(active.add(rolled.template().id), "duplicate " + rolled.template().id);
		}
		// Only 2 templates left, then none.
		active.add(QuestGenerator.generate(templates, 1, active, true, random).template().id);
		active.add(QuestGenerator.generate(templates, 1, active, true, random).template().id);
		assertNull(QuestGenerator.generate(templates, 1, active, true, random));
	}

	@Test
	void templateCheckClampsValues() {
		QuestTemplate t = template("x", QuestType.KILL, -5, -1);
		t.amountMin = 0;
		t.amountMax = -3;
		t.flagChance = 7;
		t.treasureMapChance = Double.NaN;
		t.durationDays = -1;
		assertTrue(t.check().isEmpty());
		assertEquals(0, t.weight);
		assertEquals(1, t.minLevel);
		assertEquals(1, t.amountMin);
		assertEquals(1, t.amountMax);
		assertEquals(1.0, t.flagChance);
		assertEquals(0.0, t.treasureMapChance);
		assertEquals(3.0, t.durationDays);

		QuestTemplate noTarget = template("y", QuestType.DELIVER, 1, 1);
		noTarget.target = "";
		assertFalse(noTarget.check().isEmpty());
		QuestTemplate badId = template("Bad Id", QuestType.GATHER, 1, 1);
		assertFalse(badId.check().isEmpty());
	}

	@Test
	void mapTiers() {
		assertEquals(1, QuestGenerator.mapTier(1, 3));
		assertEquals(1, QuestGenerator.mapTier(3, 3));
		assertEquals(2, QuestGenerator.mapTier(4, 3));
		assertEquals(3, QuestGenerator.mapTier(7, 3));
		assertEquals(3, QuestGenerator.mapTier(10, 3));
		assertEquals(1, QuestGenerator.mapTier(10, 1));
		assertEquals(1, QuestGenerator.mapTier(10, 0));
	}

	@Test
	void defaultTemplates() {
		List<QuestTemplate> defaults = QuestConfig.defaults();
		assertTrue(defaults.size() >= 40 && defaults.size() <= 60, "count " + defaults.size());
		Set<String> ids = new HashSet<>();
		EnumSet<QuestType> types = EnumSet.noneOf(QuestType.class);
		for (QuestTemplate t : defaults) {
			assertTrue(t.check().isEmpty(), t.id + " " + t.check());
			assertTrue(ids.add(t.id), "duplicate id " + t.id);
			assertFalse(t.name.isBlank() || t.description.isBlank(), t.id + ": name and description");
			types.add(t.type);
		}
		assertEquals(EnumSet.allOf(QuestType.class), types);
		// A level 1 guild must always be able to fill the 6 slots, with variety, even without a base (no EXPLORE).
		assertTrue(QuestGenerator.eligible(defaults, 1, Set.of(), false).size() >= 12);
		// Content spread over levels 1 to 6: every level unlocks something new.
		for (int level = 1; level <= 6; level++) {
			int min = level;
			assertTrue(defaults.stream().anyMatch(t -> t.minLevel == min), "nothing unlocked at level " + level);
		}
		// Every level offers several types (no monotonous board).
		for (int level = 1; level <= 10; level++) {
			EnumSet<QuestType> offered = EnumSet.noneOf(QuestType.class);
			for (QuestTemplate t : QuestGenerator.eligible(defaults, level, Set.of(), true)) {
				offered.add(t.type);
			}
			assertTrue(offered.size() >= 12, "level " + level + " offers " + offered);
		}
	}
}
