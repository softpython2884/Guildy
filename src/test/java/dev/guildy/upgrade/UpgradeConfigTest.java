package dev.guildy.upgrade;

import dev.guildy.config.Json5;
import dev.guildy.quest.QuestConfig;
import dev.guildy.quest.QuestTemplate;
import dev.guildy.quest.QuestType;
import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopConfig;
import dev.guildy.shop.ShopEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeConfigTest {

	@Test
	void writeThenReadKeepsTheTree(@TempDir Path dir) throws Exception {
		UpgradeConfig original = new UpgradeConfig();
		assertTrue(original.rebuild(id -> true).isEmpty());
		Path file = dir.resolve("upgrades.json5");
		Json5.write(file, original);
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(text.startsWith("// Guildy - arbre d'evolution"), "header comment");
		assertTrue(text.contains("Frontières élargies"), "accents kept");

		UpgradeConfig loaded = Json5.read(file, UpgradeConfig.class);
		assertTrue(loaded.rebuild(id -> true).isEmpty());
		assertEquals(original.buffs.durationSeconds, loaded.buffs.durationSeconds);
		assertEquals(original.tree().branches(), loaded.tree().branches());
		assertEquals(List.copyOf(original.tree().nodes()), List.copyOf(loaded.tree().nodes()), "nodes (costs, effects, requirements) unchanged");
	}

	@Test
	void missingKeysKeepDefaultsAndValuesAreClamped() {
		UpgradeConfig loaded = Json5.fromString("{ // partial file\n buffs: { durationSeconds: 999 } }", UpgradeConfig.class);
		loaded.rebuild(id -> true);
		assertEquals(60, loaded.buffs.durationSeconds);
		assertEquals(12, loaded.tree().nodes().size());

		UpgradeConfig custom = Json5.fromString("{ nodes: [ { id: 'solo', branch: 'defense', tier: 1, effects: { extra_chunks: 3 } } ] }",
				UpgradeConfig.class);
		assertTrue(custom.rebuild(id -> true).isEmpty());
		UpgradeTree.Node solo = custom.tree().node("solo");
		assertTrue(solo != null);
		assertEquals(1, solo.minLevel());
		assertEquals(0, solo.costGold());
		assertTrue(solo.costItems().isEmpty() && solo.requires().isEmpty());
		assertEquals("solo", solo.name());
		assertEquals(3, custom.tree().value(Set.of("solo"), UpgradeEffects.EXTRA_CHUNKS));
		assertEquals(3, custom.tree().branches().size(), "default branches kept");
	}

	@Test
	void nullListsFallBackToDefaults() {
		UpgradeConfig config = Json5.fromString("{ buffs: null, branches: null, nodes: null }", UpgradeConfig.class);
		config.rebuild(id -> true);
		assertEquals(5, config.buffs.durationSeconds);
		assertEquals(3, config.tree().branches().size());
		assertEquals(12, config.tree().nodes().size());
	}

	/** The quest bonus never breaks the "no gold loop" rule: a delivery still pays less than buying its items. */
	@Test
	void questBonusKeepsDeliveriesBelowShopPrices() {
		UpgradeTree tree = UpgradeTree.build(UpgradeConfig.defaultBranches(), UpgradeConfig.defaultNodes(), id -> true, new java.util.ArrayList<>());
		double maxBonus = 0;
		for (UpgradeTree.Node node : tree.nodes()) {
			maxBonus += node.effect(UpgradeEffects.QUEST_REWARD_PCT);
		}
		assertTrue(maxBonus > 0, "the default tree has a quest bonus");
		Map<String, Double> unitPrice = new HashMap<>();
		for (ShopCategory category : ShopConfig.defaults()) {
			for (ShopEntry entry : category.entries) {
				if (entry.buy > 0) {
					unitPrice.merge(entry.item, (double) entry.buy / entry.amount, Math::min);
				}
			}
		}
		Map<String, String> tagToItem = Map.of("#minecraft:wool", "minecraft:white_wool");
		for (QuestTemplate template : QuestConfig.defaults()) {
			if (template.type != QuestType.DELIVER) {
				continue;
			}
			Double price = unitPrice.get(tagToItem.getOrDefault(template.target, template.target));
			if (price == null) {
				continue;
			}
			for (int level = 1; level <= 10; level++) {
				int minAmount = template.amountMin + (int) Math.floor(template.amountPerLevel * (level - 1));
				int maxGold = template.rewardGoldMax + (int) Math.floor(template.rewardGoldPerLevel * (level - 1));
				long paid = UpgradeMath.questGold(maxGold, maxBonus, true);
				assertTrue(paid < minAmount * price, template.id + " level " + level + ": " + paid + " gold >= cost " + minAmount * price);
			}
		}
	}
}
