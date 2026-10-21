package dev.guildy.alchemy;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A brewable product as written in alchemy.json5 (pure, unit tested): a vanilla potion in one of its three bottles, or a
 * Guildy elixir. Text form: {@code healing}, {@code splash:healing}, {@code lingering:strong_healing},
 * {@code mymod:frost} (another namespace), {@code elixir:arcanique}; {@code water} is the water bottle every brew starts
 * from. The {@code minecraft:} namespace is optional and dropped in the canonical text.
 */
public record PotionKey(Form form, String id) {
	/** Water bottle: the start of every chain. */
	public static final PotionKey WATER = new PotionKey(Form.POTION, "water");
	private static final Pattern POTION_ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
	private static final Pattern ELIXIR_ID = Pattern.compile("[a-z0-9_]{1,48}");

	/** Bottle of the product. */
	public enum Form {
		/** Drinkable potion. */
		POTION(""),
		/** Splash potion (gunpowder). */
		SPLASH("splash:"),
		/** Lingering potion (dragon's breath, from a splash potion). */
		LINGERING("lingering:"),
		/** Guildy elixir (a drinkable potion with its own effects). */
		ELIXIR("elixir:");

		private final String prefix;

		Form(String prefix) {
			this.prefix = prefix;
		}

		public String prefix() {
			return prefix;
		}
	}

	public PotionKey {
		form = form == null ? Form.POTION : form;
		id = id == null ? "" : id;
	}

	/** Parses a product text, or null when it is not one. */
	public static @Nullable PotionKey parse(@Nullable String text) {
		if (text == null) {
			return null;
		}
		String value = text.trim().toLowerCase(Locale.ROOT);
		Form form = Form.POTION;
		for (Form candidate : new Form[]{Form.SPLASH, Form.LINGERING, Form.ELIXIR}) {
			if (value.startsWith(candidate.prefix)) {
				form = candidate;
				value = value.substring(candidate.prefix.length());
				break;
			}
		}
		if (form == Form.ELIXIR) {
			return ELIXIR_ID.matcher(value).matches() ? new PotionKey(form, value) : null;
		}
		if (!POTION_ID.matcher(value).matches()) {
			return null;
		}
		if (value.startsWith("minecraft:")) {
			value = value.substring("minecraft:".length());
		}
		return value.isEmpty() ? null : new PotionKey(form, value);
	}

	/** Canonical text of a product, or null when the text is not one. */
	public static @Nullable String canonical(@Nullable String text) {
		PotionKey key = parse(text);
		return key == null ? null : key.text();
	}

	/** Canonical text ("splash:healing", "elixir:arcanique"). */
	public String text() {
		return form.prefix + id;
	}

	/** Namespaced potion id of a vanilla product ("minecraft:healing"); the elixir id for an elixir. */
	public String potionId() {
		return form == Form.ELIXIR || id.contains(":") ? id : "minecraft:" + id;
	}

	public boolean isElixir() {
		return form == Form.ELIXIR;
	}

	/** The same potion in another bottle (never for an elixir). */
	public PotionKey in(Form other) {
		return new PotionKey(other, id);
	}

	@Override
	public String toString() {
		return text();
	}
}
