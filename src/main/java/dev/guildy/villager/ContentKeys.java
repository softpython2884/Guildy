package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.TreeSet;

/**
 * What counts as "the same item" for the chests of a filtered room (pure, no Minecraft classes: unit tested). A chest of
 * a filtered room accepts the kinds of items it already holds; the kind of a stack is its key:
 * <ul>
 * <li>by default its item id ("minecraft:diorite"): components never matter (renamed, damaged or enchanted items are the
 * same kind as the plain item);</li>
 * <li>items holding a potion (potions, splash and lingering potions, tipped arrows): the item id and the potion
 * ({@link PotionMatch}: by default its type, the long and strong versions included: "minecraft:potion[minecraft:healing]"
 * for healing and healing II); a potion without a base potion is "custom" (or the id of a Guildy elixir);</li>
 * <li>enchanted books ({@link BookMatch}): by default every enchanted book together ("minecraft:enchanted_book"), or by
 * the set of their enchantments, levels ignored.</li>
 * </ul>
 */
public final class ContentKeys {
	/** Item of the enchanted books. */
	public static final String ENCHANTED_BOOK = "minecraft:enchanted_book";
	/** Potion part of a potion item without a base potion (custom effects only). */
	public static final String CUSTOM_POTION = "custom";

	/** Potions: same type (long / strong versions together), same exact potion, or every potion of the item together. */
	public enum PotionMatch {
		TYPE("type"),
		EXACT("exact"),
		ITEM("item");

		private final String id;

		PotionMatch(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** The setting of villagers.json5 ("type", "exact", "item"; French words accepted), or null when unknown. */
		public static @Nullable PotionMatch parse(@Nullable String text) {
			if (text == null) {
				return null;
			}
			return switch (text.trim().toLowerCase(Locale.ROOT)) {
				case "type", "effect", "effet" -> TYPE;
				case "exact", "exacte", "variant", "variante" -> EXACT;
				case "item", "objet", "all", "toutes" -> ITEM;
				default -> null;
			};
		}
	}

	/** Enchanted books: every book together, or the same set of enchantments (levels ignored). */
	public enum BookMatch {
		ITEM("item"),
		ENCHANTMENTS("enchantments");

		private final String id;

		BookMatch(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** The setting of villagers.json5 ("item", "enchantments"; French words accepted), or null when unknown. */
		public static @Nullable BookMatch parse(@Nullable String text) {
			if (text == null) {
				return null;
			}
			return switch (text.trim().toLowerCase(Locale.ROOT)) {
				case "item", "objet", "all", "tous" -> ITEM;
				case "enchantments", "enchantment", "enchantements", "enchantement" -> ENCHANTMENTS;
				default -> null;
			};
		}
	}

	/** The identity rules in force. */
	public record Rules(PotionMatch potions, BookMatch books) {
		public static final Rules DEFAULT = new Rules(PotionMatch.TYPE, BookMatch.ITEM);
	}

	private ContentKeys() {
	}

	/**
	 * Key of a stack.
	 *
	 * @param itemId       namespaced item id
	 * @param potion       for an item holding a potion: the potion id ("minecraft:strong_healing"), {@link #CUSTOM_POTION}
	 *                     or a Guildy elixir ("elixir:force"); null for every other item
	 * @param enchantments for an enchanted book: the ids of its stored enchantments (null for every other item)
	 */
	public static String key(String itemId, @Nullable String potion, @Nullable Collection<String> enchantments, Rules rules) {
		if (potion != null && !potion.isEmpty()) {
			return switch (rules.potions()) {
				case ITEM -> itemId;
				case EXACT -> itemId + "[" + potion + "]";
				case TYPE -> itemId + "[" + potionType(potion) + "]";
			};
		}
		if (enchantments != null && rules.books() == BookMatch.ENCHANTMENTS && ENCHANTED_BOOK.equals(itemId) && !enchantments.isEmpty()) {
			return itemId + "[" + String.join(",", new TreeSet<>(enchantments)) + "]";
		}
		return itemId;
	}

	/** Type of a potion: its id without the "long_" / "strong_" prefix of its path ("minecraft:strong_healing" → "minecraft:healing"). */
	public static String potionType(String potion) {
		int colon = potion.indexOf(':');
		String namespace = colon < 0 ? "" : potion.substring(0, colon + 1);
		String path = colon < 0 ? potion : potion.substring(colon + 1);
		for (String prefix : new String[]{"long_", "strong_"}) {
			if (path.startsWith(prefix) && path.length() > prefix.length()) {
				return namespace + path.substring(prefix.length());
			}
		}
		return potion;
	}

	/** Item id of a key ("minecraft:potion[minecraft:healing]" → "minecraft:potion"). */
	public static String itemOf(String key) {
		int bracket = key.indexOf('[');
		return bracket < 0 ? key : key.substring(0, bracket);
	}
}
