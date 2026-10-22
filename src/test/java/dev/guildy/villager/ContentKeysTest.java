package dev.guildy.villager;

import dev.guildy.villager.ContentKeys.BookMatch;
import dev.guildy.villager.ContentKeys.PotionMatch;
import dev.guildy.villager.ContentKeys.Rules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What counts as "the same item" in a filtered room: item id, potion type, enchanted books. */
class ContentKeysTest {
	private static final Rules DEFAULT = Rules.DEFAULT;

	@Test
	void plainItemsAreTheirIdWhateverTheirComponents() {
		assertEquals("minecraft:diorite", ContentKeys.key("minecraft:diorite", null, null, DEFAULT));
		// An enchanted sword (enchantments are not stored ones) is a diamond sword like any other.
		assertEquals("minecraft:diamond_sword", ContentKeys.key("minecraft:diamond_sword", null, null, DEFAULT));
		assertEquals("minecraft:diamond_sword", ContentKeys.key("minecraft:diamond_sword", null, List.of("minecraft:sharpness"),
				new Rules(PotionMatch.TYPE, BookMatch.ENCHANTMENTS)), "only enchanted books are split by enchantment");
	}

	@Test
	void potionsOfTheSameTypeAreTheSameKindByDefault() {
		String healing = ContentKeys.key("minecraft:potion", "minecraft:healing", null, DEFAULT);
		assertEquals("minecraft:potion[minecraft:healing]", healing);
		assertEquals(healing, ContentKeys.key("minecraft:potion", "minecraft:strong_healing", null, DEFAULT), "healing II with healing");
		assertEquals("minecraft:potion[minecraft:swiftness]", ContentKeys.key("minecraft:potion", "minecraft:long_swiftness", null, DEFAULT));
		assertNotEquals(healing, ContentKeys.key("minecraft:potion", "minecraft:poison", null, DEFAULT));
		assertNotEquals(healing, ContentKeys.key("minecraft:splash_potion", "minecraft:healing", null, DEFAULT), "a splash potion is another item");
		assertEquals("minecraft:tipped_arrow[minecraft:poison]", ContentKeys.key("minecraft:tipped_arrow", "minecraft:strong_poison", null, DEFAULT));
		assertEquals("minecraft:potion[custom]", ContentKeys.key("minecraft:potion", ContentKeys.CUSTOM_POTION, null, DEFAULT));
		assertEquals("minecraft:potion[elixir:force]", ContentKeys.key("minecraft:potion", "elixir:force", null, DEFAULT));
	}

	@Test
	void potionRulesExactAndItem() {
		Rules exact = new Rules(PotionMatch.EXACT, BookMatch.ITEM);
		assertEquals("minecraft:potion[minecraft:strong_healing]", ContentKeys.key("minecraft:potion", "minecraft:strong_healing", null, exact));
		assertNotEquals(ContentKeys.key("minecraft:potion", "minecraft:healing", null, exact),
				ContentKeys.key("minecraft:potion", "minecraft:strong_healing", null, exact));
		Rules item = new Rules(PotionMatch.ITEM, BookMatch.ITEM);
		assertEquals("minecraft:potion", ContentKeys.key("minecraft:potion", "minecraft:poison", null, item));
		assertEquals("minecraft:lingering_potion", ContentKeys.key("minecraft:lingering_potion", "minecraft:healing", null, item));
	}

	@Test
	void enchantedBooksTogetherByDefaultOrByTheirEnchantments() {
		assertEquals(ContentKeys.ENCHANTED_BOOK, ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of("minecraft:mending"), DEFAULT));
		assertEquals(ContentKeys.ENCHANTED_BOOK, ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of("minecraft:sharpness"), DEFAULT));
		Rules books = new Rules(PotionMatch.TYPE, BookMatch.ENCHANTMENTS);
		assertEquals("minecraft:enchanted_book[minecraft:mending]", ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of("minecraft:mending"), books));
		// Sorted, each once: the same set in any order is the same kind (levels are not part of it).
		assertEquals("minecraft:enchanted_book[minecraft:mending,minecraft:unbreaking]",
				ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of("minecraft:unbreaking", "minecraft:mending"), books));
		assertEquals(ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of("minecraft:mending", "minecraft:unbreaking"), books),
				ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of("minecraft:unbreaking", "minecraft:mending"), books));
		assertEquals(ContentKeys.ENCHANTED_BOOK, ContentKeys.key(ContentKeys.ENCHANTED_BOOK, null, List.of(), books), "a book without enchantment");
	}

	@Test
	void potionTypesAndItemOfAKey() {
		assertEquals("minecraft:healing", ContentKeys.potionType("minecraft:strong_healing"));
		assertEquals("minecraft:night_vision", ContentKeys.potionType("minecraft:long_night_vision"));
		assertEquals("minecraft:turtle_master", ContentKeys.potionType("minecraft:strong_turtle_master"));
		assertEquals("healing", ContentKeys.potionType("strong_healing"));
		assertEquals("minecraft:water", ContentKeys.potionType("minecraft:water"));
		assertEquals("minecraft:strong_", ContentKeys.potionType("minecraft:strong_"), "nothing left after the prefix: unchanged");
		assertEquals("minecraft:potion", ContentKeys.itemOf("minecraft:potion[minecraft:healing]"));
		assertEquals("minecraft:diorite", ContentKeys.itemOf("minecraft:diorite"));
	}

	@Test
	void settingsAreParsed() {
		assertEquals(PotionMatch.TYPE, PotionMatch.parse(" Type "));
		assertEquals(PotionMatch.TYPE, PotionMatch.parse("effet"));
		assertEquals(PotionMatch.EXACT, PotionMatch.parse("exact"));
		assertEquals(PotionMatch.EXACT, PotionMatch.parse("variante"));
		assertEquals(PotionMatch.ITEM, PotionMatch.parse("item"));
		assertNull(PotionMatch.parse("whatever"));
		assertNull(PotionMatch.parse(null));
		assertEquals(BookMatch.ITEM, BookMatch.parse("item"));
		assertEquals(BookMatch.ENCHANTMENTS, BookMatch.parse("enchantements"));
		assertEquals(BookMatch.ENCHANTMENTS, BookMatch.parse("enchantments"));
		assertNull(BookMatch.parse(""));
	}
}
