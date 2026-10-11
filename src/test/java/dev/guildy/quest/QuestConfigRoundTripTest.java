package dev.guildy.quest;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** quests.json5 survives the Json5 writer (comments, sections) + lenient reader unchanged. */
class QuestConfigRoundTripTest {

	@Test
	void roundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("quests.json5");
		QuestConfig written = new QuestConfig();
		Json5.write(file, written);
		String text = Files.readString(file);
		assertTrue(text.startsWith("// Guildy - quetes"), "header comment");
		QuestConfig read = Json5.read(file, QuestConfig.class);
		assertEquals(written.refreshDelaySeconds, read.refreshDelaySeconds);
		assertEquals(written.exploreMinDistance, read.exploreMinDistance);
		assertEquals(written.uniqueChance, read.uniqueChance);
		assertEquals(written.legendary.minLevel, read.legendary.minLevel);
		assertEquals(written.legendary.chance, read.legendary.chance);
		assertEquals(written.legendary.goldMultiplier, read.legendary.goldMultiplier);
		assertTrue(text.contains("\"collection\""), "assortments written");
		assertFalse(text.contains("\"usable\""), "transient validation data never written");
		assertEquals(written.treasure.tiers.size(), read.treasure.tiers.size());
		assertEquals(written.treasure.tiers.get(1).lootTable, read.treasure.tiers.get(1).lootTable);
		assertEquals(written.templates.size(), read.templates.size());
		for (int i = 0; i < written.templates.size(); i++) {
			QuestTemplate a = written.templates.get(i);
			QuestTemplate b = read.templates.get(i);
			assertEquals(a.id, b.id);
			assertEquals(a.type, b.type);
			assertEquals(a.name, b.name);
			assertEquals(a.description, b.description);
			assertEquals(a.target, b.target);
			assertEquals(a.targetName, b.targetName);
			assertEquals(a.icon, b.icon);
			assertEquals(a.amountMin, b.amountMin);
			assertEquals(a.amountPerLevel, b.amountPerLevel);
			assertEquals(a.rewardGoldMax, b.rewardGoldMax);
			assertEquals(a.flagChance, b.flagChance);
			assertEquals(a.durationDays, b.durationDays);
			assertEquals(a.rewardItems.size(), b.rewardItems.size());
			assertEquals(a.maxLevel, b.maxLevel);
			assertEquals(a.legendary, b.legendary);
			assertEquals(a.collection == null, b.collection == null, a.id + ": collection only where defined");
			if (a.collection != null) {
				assertEquals(a.collection.min, b.collection.min);
				assertEquals(a.collection.max, b.collection.max);
				assertEquals(a.collection.items.size(), b.collection.items.size());
				assertEquals(a.collection.items.get(0).item, b.collection.items.get(0).item);
				assertEquals(a.collection.items.get(0).amountPerLevel, b.collection.items.get(0).amountPerLevel);
			}
			assertTrue(b.check().isEmpty(), b.id);
		}
	}
}
