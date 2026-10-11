package dev.guildy.quest;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure rules of the quest types added after the first five: targets, assortments (COLLECTION), fixed amounts, legendary
 * contracts, per-item progress, horde ranks, loading of older quests.
 */
class QuestTypesTest {
	private static final QuestGenerator.Legendary LEGENDARY = new QuestGenerator.Legendary(2.0, 2.0, 2.5, 3.0);

	private static QuestTemplate template(String id, QuestType type, String target) {
		QuestTemplate t = new QuestTemplate();
		t.id = id;
		t.type = type;
		t.name = id;
		t.target = target;
		t.amountMin = 10;
		t.amountMax = 20;
		t.amountPerLevel = 2;
		t.rewardGoldMin = 10;
		t.rewardGoldMax = 10;
		t.rewardXp = 40;
		t.durationDays = 3;
		return t;
	}

	private static QuestTemplate collection(int min, int max, int items) {
		QuestTemplate t = template("assortiment", QuestType.COLLECTION, "");
		List<QuestTemplate.Part> parts = new ArrayList<>();
		for (int i = 0; i < items; i++) {
			parts.add(new QuestTemplate.Part("minecraft:item_" + i, "", 4, 8, 1));
		}
		t.collection = new QuestTemplate.Assortment(min, max, parts);
		return t;
	}

	@Test
	void typeProperties() {
		assertTrue(QuestType.CRAFT.requiresTarget() && QuestType.SMELT.requiresTarget() && QuestType.BIOME.requiresTarget());
		for (QuestType type : List.of(QuestType.FISH, QuestType.BREED, QuestType.TAME, QuestType.TRADE, QuestType.ENCHANT, QuestType.NIGHT_HUNT)) {
			assertTrue(type.hasTarget() && !type.requiresTarget(), type + ": optional target");
		}
		for (QuestType type : List.of(QuestType.SURVIVE, QuestType.EXPLORE, QuestType.ELITE, QuestType.BOSS, QuestType.COLLECTION)) {
			assertFalse(type.hasTarget(), type + ": no target");
		}
		assertEquals(QuestType.TargetKind.ITEM, QuestType.SMELT.targetKind());
		assertEquals(QuestType.TargetKind.ENTITY, QuestType.TAME.targetKind());
		assertEquals(QuestType.TargetKind.BIOME, QuestType.BIOME.targetKind());
		assertTrue(QuestType.BIOME.fixedAmount() && QuestType.EXPLORE.fixedAmount() && !QuestType.BOSS.fixedAmount());
		assertTrue(QuestType.DELIVER.isDelivery() && QuestType.COLLECTION.isDelivery() && !QuestType.CRAFT.isDelivery());
		assertFalse(QuestType.BIOME.canBeLegendary() || QuestType.EXPLORE.canBeLegendary());
		assertEquals("night_hunt", QuestType.NIGHT_HUNT.key());
		assertTrue(QuestType.names().contains("COLLECTION"));
	}

	@Test
	void templateChecksOfTheNewTypes() {
		assertFalse(template("c", QuestType.CRAFT, "").check().isEmpty(), "CRAFT needs a target");
		assertFalse(template("s", QuestType.SMELT, " ").check().isEmpty(), "SMELT needs a target");
		assertFalse(template("b", QuestType.BIOME, "").check().isEmpty(), "BIOME needs a target");
		for (QuestType type : List.of(QuestType.FISH, QuestType.BREED, QuestType.TAME, QuestType.TRADE, QuestType.ENCHANT,
				QuestType.NIGHT_HUNT, QuestType.ELITE, QuestType.BOSS)) {
			assertTrue(template("t", type, "").check().isEmpty(), type + " works without target");
		}
		QuestTemplate biome = template("b", QuestType.BIOME, "minecraft:desert");
		assertTrue(biome.check().isEmpty());
		assertEquals(1, biome.amountMin);
		assertEquals(1, biome.amountMax);
		assertEquals(0, biome.amountPerLevel);

		QuestTemplate noAssortment = template("x", QuestType.COLLECTION, "");
		assertFalse(noAssortment.check().isEmpty(), "COLLECTION needs a collection");
		QuestTemplate tooFew = collection(3, 4, 2);
		assertFalse(tooFew.check().isEmpty(), "fewer items than min");
		QuestTemplate clamped = collection(0, 99, 8);
		clamped.collection.items.add(null);
		clamped.collection.items.add(new QuestTemplate.Part("", "", 1, 1, 0));
		clamped.collection.items.get(0).amountMin = -4;
		clamped.collection.items.get(0).amountMax = -9;
		assertTrue(clamped.check().isEmpty(), clamped.check().toString());
		assertEquals(1, clamped.collection.min);
		assertEquals(QuestTemplate.MAX_COLLECTION_ITEMS, clamped.collection.max);
		assertEquals(8, clamped.collection.items.size(), "null / blank items removed");
		assertEquals(1, clamped.collection.items.get(0).amountMin);
		assertEquals(1, clamped.collection.items.get(0).amountMax);
		// Another type never keeps a stray assortment.
		QuestTemplate stray = template("y", QuestType.DELIVER, "minecraft:bread");
		stray.collection = new QuestTemplate.Assortment();
		assertTrue(stray.check().isEmpty());
		assertNull(stray.collection);
	}

	@Test
	void assortmentsDrawDistinctItemsScaledByLevel() {
		QuestTemplate t = collection(2, 4, 6);
		assertTrue(t.check().isEmpty());
		for (long seed = 0; seed < 200; seed++) {
			for (int level : new int[] {1, 5}) {
				QuestGenerator.Rolled rolled = QuestGenerator.roll(t, level, new Random(seed));
				List<QuestPart> parts = rolled.parts();
				assertTrue(parts.size() >= 2 && parts.size() <= 4, "count " + parts.size());
				Set<String> items = new HashSet<>();
				int sum = 0;
				for (QuestPart part : parts) {
					assertTrue(items.add(part.target), "duplicate item " + part.target);
					int bonus = level - 1;
					assertTrue(part.amount >= 4 + bonus && part.amount <= 8 + bonus, "amount " + part.amount);
					assertEquals(0, part.progress);
					sum += part.amount;
				}
				assertEquals(sum, rolled.amount(), "amount = sum of the items");
			}
		}
		// Same seed, same assortment.
		QuestGenerator.Rolled a = QuestGenerator.roll(t, 3, new Random(99));
		QuestGenerator.Rolled b = QuestGenerator.roll(t, 3, new Random(99));
		assertEquals(a.parts().stream().map(p -> p.target + p.amount).toList(), b.parts().stream().map(p -> p.target + p.amount).toList());
		// Never more items than the pool offers.
		QuestTemplate small = collection(2, 5, 3);
		assertTrue(small.check().isEmpty());
		for (long seed = 0; seed < 50; seed++) {
			int count = QuestGenerator.roll(small, 1, new Random(seed)).parts().size();
			assertTrue(count >= 2 && count <= 3, "count " + count);
		}
		// Other types have no parts.
		assertTrue(QuestGenerator.roll(template("c", QuestType.CRAFT, "minecraft:torch"), 1, new Random(1)).parts().isEmpty());
	}

	@Test
	void oneVisitTypes() {
		QuestTemplate biome = template("b", QuestType.BIOME, "#minecraft:is_jungle");
		biome.amountMin = 5;
		biome.amountMax = 9;
		biome.amountPerLevel = 3;
		assertEquals(1, QuestGenerator.roll(biome, 7, new Random(2)).amount(), "rolled without check()");
		assertTrue(biome.check().isEmpty());
		assertEquals(1, QuestGenerator.roll(biome, 7, new Random(2)).amount());
	}

	@Test
	void legendaryContracts() {
		QuestTemplate hunt = template("hunt", QuestType.NIGHT_HUNT, "");
		QuestGenerator.Rolled normal = new QuestGenerator.Rolled(hunt, 15, 12, 40, List.of(), 0, false, 3 * QuestGenerator.DAY_TICKS,
				List.of(), false);
		QuestGenerator.Rolled legendary = QuestGenerator.legendary(normal, LEGENDARY);
		assertTrue(legendary.legendary());
		assertEquals(30, legendary.amount());
		assertEquals(30, legendary.gold());
		assertEquals(120, legendary.xp());
		assertEquals(6 * QuestGenerator.DAY_TICKS, legendary.durationTicks());

		// Amounts are rounded up, a boss quest asks for 2 bosses.
		QuestTemplate boss = template("boss", QuestType.BOSS, "");
		QuestGenerator.Rolled oneBoss = new QuestGenerator.Rolled(boss, 1, 40, 150, List.of(), 1, true, QuestGenerator.DAY_TICKS, List.of(), false);
		QuestGenerator.Rolled twoBosses = QuestGenerator.legendary(oneBoss, new QuestGenerator.Legendary(1.5, 1, 1, 1));
		assertEquals(2, twoBosses.amount());
		assertEquals(1, twoBosses.flags());
		assertTrue(twoBosses.treasureMap());

		// Deliveries: the gold multiplier never exceeds the amount multiplier (anti gold loop).
		QuestTemplate deliver = template("deliver", QuestType.DELIVER, "minecraft:bread");
		QuestGenerator.Rolled bread = new QuestGenerator.Rolled(deliver, 16, 7, 45, List.of(), 0, false, QuestGenerator.DAY_TICKS, List.of(), false);
		QuestGenerator.Rolled moreBread = QuestGenerator.legendary(bread, LEGENDARY);
		assertEquals(32, moreBread.amount());
		assertEquals(14, moreBread.gold(), "x2 like the amount, not x2.5");

		// Assortments: every item is scaled, the total follows.
		QuestTemplate assortment = collection(2, 2, 2);
		List<QuestPart> parts = List.of(new QuestPart("minecraft:a", "", 5), new QuestPart("minecraft:b", "", 3));
		QuestGenerator.Rolled pack = new QuestGenerator.Rolled(assortment, 8, 4, 70, List.of(), 0, false, QuestGenerator.DAY_TICKS, parts, false);
		QuestGenerator.Rolled bigPack = QuestGenerator.legendary(pack, new QuestGenerator.Legendary(1.5, 2, 3, 2));
		assertEquals(List.of(8, 5), bigPack.parts().stream().map(p -> p.amount).toList());
		assertEquals(13, bigPack.amount());
		assertEquals(6, bigPack.gold(), "x1.5 (amount) instead of x3");
		assertEquals(5, parts.get(0).amount, "the rolled quest is left untouched");

		// Fixed-amount quests keep their single visit; multipliers below 1 are ignored.
		QuestTemplate biome = template("biome", QuestType.BIOME, "minecraft:desert");
		QuestGenerator.Rolled visit = new QuestGenerator.Rolled(biome, 1, 10, 50, List.of(), 0, false, QuestGenerator.DAY_TICKS, List.of(), false);
		assertEquals(1, QuestGenerator.legendary(visit, LEGENDARY).amount());
		QuestGenerator.Rolled same = QuestGenerator.legendary(normal, new QuestGenerator.Legendary(0.5, Double.NaN, -2, 0));
		assertEquals(15, same.amount());
		assertEquals(12, same.gold());
		assertEquals(40, same.xp());
		assertEquals(normal.durationTicks(), same.durationTicks());
	}

	@Test
	void legendaryCandidatesAndTurns() {
		QuestTemplate gather = template("g", QuestType.GATHER, "minecraft:stone");
		QuestTemplate explore = template("e", QuestType.EXPLORE, "");
		QuestTemplate biome = template("b", QuestType.BIOME, "minecraft:desert");
		QuestTemplate optedOut = template("o", QuestType.KILL, "minecraft:zombie");
		optedOut.legendary = false;
		assertEquals(List.of(gather), QuestGenerator.legendaryCandidates(List.of(gather, explore, biome, optedOut)));

		long day = QuestGenerator.DAY_TICKS;
		Random random = new Random(5);
		assertTrue(QuestGenerator.legendaryTurn(true, 3, 3, false, 10 * day, 0, 2 * day, 1.0, random));
		assertFalse(QuestGenerator.legendaryTurn(false, 3, 3, false, 10 * day, 0, 2 * day, 1.0, random), "disabled");
		assertFalse(QuestGenerator.legendaryTurn(true, 2, 3, false, 10 * day, 0, 2 * day, 1.0, random), "level too low");
		assertFalse(QuestGenerator.legendaryTurn(true, 3, 3, true, 10 * day, 0, 2 * day, 1.0, random), "one at a time");
		assertFalse(QuestGenerator.legendaryTurn(true, 3, 3, false, 10 * day, 9 * day, 2 * day, 1.0, random), "cooldown");
		assertTrue(QuestGenerator.legendaryTurn(true, 3, 3, false, 11 * day, 9 * day, 2 * day, 1.0, random), "cooldown over");
		assertTrue(QuestGenerator.legendaryTurn(true, 3, 3, false, 5 * day, 9 * day, 2 * day, 1.0, random), "clock went back");
		assertFalse(QuestGenerator.legendaryTurn(true, 9, 3, false, 10 * day, 0, 0, 0.0, random), "no chance");
		int hits = 0;
		for (int i = 0; i < 10000; i++) {
			if (QuestGenerator.legendaryTurn(true, 5, 3, false, 10 * day, 0, 0, 0.1, random)) {
				hits++;
			}
		}
		assertTrue(hits > 800 && hits < 1200, "hits " + hits);
	}

	@Test
	void progressIsCapped() {
		QuestInstance quest = new QuestInstance();
		quest.type = QuestType.CRAFT;
		quest.amount = 10;
		assertEquals(4, quest.addProgress(4));
		assertEquals(0, quest.addProgress(-3));
		assertEquals(6, quest.addProgress(50));
		assertTrue(quest.isGoalReached());
		assertEquals(0, quest.addProgress(1));
	}

	@Test
	void assortmentProgressPerItem() {
		QuestInstance quest = new QuestInstance();
		quest.type = QuestType.COLLECTION;
		quest.parts = new ArrayList<>(List.of(new QuestPart("minecraft:bread", "", 8), new QuestPart("minecraft:apple", "", 4)));
		quest.syncParts();
		assertEquals(12, quest.amount);
		assertEquals(0, quest.addProgress(5), "assortments only progress item by item");
		assertEquals(8, quest.addPartProgress(0, 20), "capped at the item's amount");
		assertEquals(0, quest.addPartProgress(0, 1));
		assertEquals(0, quest.addPartProgress(7, 1), "unknown item");
		assertEquals(8, quest.progress);
		assertEquals(1, quest.partsDone());
		assertFalse(quest.isGoalReached(), "one item still missing");
		assertEquals(3, quest.addPartProgress(1, 3));
		assertFalse(quest.isGoalReached());
		assertEquals(1, quest.addPartProgress(1, 9));
		assertTrue(quest.isGoalReached());
		assertEquals(2, quest.partsDone());

		QuestInstance forced = new QuestInstance();
		forced.type = QuestType.COLLECTION;
		forced.parts = new ArrayList<>(List.of(new QuestPart("minecraft:bread", "", 8), new QuestPart("minecraft:apple", "", 4)));
		forced.syncParts();
		forced.markCompleted(100);
		assertTrue(forced.isCompleted());
		assertEquals(2, forced.partsDone(), "admin completion fills every item");
		assertEquals(forced.amount, forced.progress);
	}

	@Test
	void olderQuestsStillLoad() {
		// A quest saved before legendary contracts and assortments existed (guilds.json).
		String json = "{\"id\":\"q1\",\"template\":\"bois\",\"slot\":2,\"type\":\"GATHER\",\"name\":\"Coupe de bois\",\"target\":\"#minecraft:logs\","
				+ "\"amount\":40,\"progress\":12,\"state\":\"ACTIVE\",\"rewardGold\":10,\"rewardXp\":25,\"rewardItems\":[],"
				+ "\"contributions\":{\"Alex\":12}}";
		QuestInstance quest = new Gson().fromJson(json, QuestInstance.class);
		quest.normalize();
		assertFalse(quest.legendary);
		assertNotNull(quest.parts);
		assertTrue(quest.parts.isEmpty());
		assertEquals(40, quest.amount);
		assertEquals(12, quest.progress);
		assertEquals(12, quest.addProgress(12));

		// Unknown (newer) type and broken values are repaired, assortment totals recomputed.
		String broken = "{\"id\":\"q2\",\"slot\":0,\"type\":\"FUTURE_TYPE\",\"parts\":[null,{\"target\":\"minecraft:bread\",\"amount\":0,\"progress\":9}],"
				+ "\"amount\":99,\"progress\":-5}";
		QuestInstance repaired = new Gson().fromJson(broken, QuestInstance.class);
		repaired.normalize();
		assertEquals(QuestType.GATHER, repaired.type);
		assertEquals(1, repaired.parts.size());
		assertEquals(1, repaired.parts.get(0).amount);
		assertEquals(1, repaired.parts.get(0).progress);
		assertEquals(1, repaired.amount);
		assertEquals(1, repaired.progress);

		GuildQuestData data = new Gson().fromJson("{\"active\":[],\"completedTotal\":4}", GuildQuestData.class);
		data.normalize();
		assertEquals(0, data.lastLegendaryAt);
		assertEquals(4, data.completedTotal);
		assertFalse(data.hasLegendary());
	}

	@Test
	void hordeRanks() {
		assertEquals(HordeMobs.Rank.NONE, HordeMobs.rank(false, true, true, true, true), "not a horde mob");
		assertEquals(HordeMobs.Rank.NORMAL, HordeMobs.rank(true, false, false, false, false));
		assertEquals(HordeMobs.Rank.BOSS, HordeMobs.rank(true, true, true, true, false), "visible name = boss");
		assertEquals(HordeMobs.Rank.ELITE, HordeMobs.rank(true, true, false, true, true));
		assertEquals(HordeMobs.Rank.ELITE, HordeMobs.rank(true, true, false, false, true), "elite without glowing (config)");
		assertEquals(HordeMobs.Rank.ELITE, HordeMobs.rank(true, true, false, true, false), "elite renamed by a name tag");
		assertEquals(HordeMobs.Rank.NORMAL, HordeMobs.rank(true, true, false, false, false), "horde mob renamed by a name tag");
	}

	@Test
	void newDefaultsReachOlderFilesOnce() {
		List<QuestTemplate> legacy = new ArrayList<>();
		for (QuestTemplate t : QuestConfig.defaults()) {
			if (QuestConfig.ORIGINAL_DEFAULTS.contains(t.id)) {
				legacy.add(t);
			}
		}
		assertEquals(20, legacy.size(), "the 20 templates of the first version are still defaults");
		legacy.removeIf(t -> t.id.equals("diamant")); // removed by the admin
		List<String> known = new ArrayList<>();
		List<String> added = QuestConfig.mergeNewDefaults(legacy, known);
		int defaults = QuestConfig.defaults().size();
		assertEquals(defaults - 20, added.size());
		assertEquals(defaults - 1, legacy.size(), "new templates added, removed original stays removed");
		assertEquals(defaults, known.size());
		assertTrue(added.contains("provisions") && added.contains("boss") && !added.contains("diamant"));
		// Removed afterwards: never added back.
		legacy.removeIf(t -> t.id.equals("boss"));
		assertTrue(QuestConfig.mergeNewDefaults(legacy, known).isEmpty());

		List<QuestTemplate> fresh = QuestConfig.defaults();
		List<String> freshKnown = new ArrayList<>();
		assertTrue(QuestConfig.mergeNewDefaults(fresh, freshKnown).isEmpty());
		assertEquals(defaults, fresh.size());
	}

	@Test
	void percentages() {
		assertEquals("3", QuestFormat.percent(0.03));
		assertEquals("2,5", QuestFormat.percent(0.025));
		assertEquals("0,1", QuestFormat.percent(0.001));
		assertEquals("100", QuestFormat.percent(7));
		assertEquals("0", QuestFormat.percent(Double.NaN));
	}
}
