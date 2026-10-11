package dev.guildy.quest;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
			assertTrue(b.check().isEmpty(), b.id);
		}
	}
}
