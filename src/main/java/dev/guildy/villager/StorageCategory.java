package dev.guildy.villager;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One storage category of villagers.json5 ({@code storage.categories}: name → category): its menu icon, a short text
 * listing examples (lore of the menus) and its sorting rules (see {@link StorageCategories}). Pure data, free of
 * Minecraft classes.
 * <p>
 * The older format (a plain list of rules, before configVersion 3) is still accepted: it becomes a category without
 * icon nor description (the icon is then the first item id of its rules).
 */
@JsonAdapter(StorageCategory.Adapter.class)
public final class StorageCategory {
	/** Item shown in the menus ("minecraft:oak_log"); empty = the first item id of the rules, else a chest. */
	public String icon = "";
	/** Examples shown in the menus ("Bûches, planches..."); '&' colour codes allowed. */
	public String description = "";
	public List<String> rules = new ArrayList<>();

	public StorageCategory() {
	}

	public StorageCategory(String icon, String description, List<String> rules) {
		this.icon = icon == null ? "" : icon;
		this.description = description == null ? "" : description;
		this.rules = new ArrayList<>(rules);
	}

	public StorageCategory copy() {
		return new StorageCategory(icon, description, rules);
	}

	/** The icon, or the first plain item id of the rules, or "" (the menus then show a chest). */
	public String iconOrGuess() {
		if (icon != null && !icon.isBlank()) {
			return icon.trim();
		}
		for (String rule : rules) {
			if (rule != null && !rule.isBlank() && !rule.startsWith("#") && !rule.startsWith("@") && rule.indexOf('*') < 0) {
				return rule.trim();
			}
		}
		return "";
	}

	void sanitize() {
		icon = icon == null ? "" : icon.trim();
		description = description == null ? "" : description.trim();
		List<String> clean = new ArrayList<>();
		if (rules != null) {
			for (String rule : rules) {
				if (rule != null && !rule.isBlank() && !clean.contains(rule.trim())) {
					clean.add(rule.trim());
				}
			}
		}
		rules = clean;
	}

	/** Writes {icon, description, rules}; reads that object or a plain list of rules (older files). */
	static final class Adapter extends TypeAdapter<StorageCategory> {
		@Override
		public void write(JsonWriter out, StorageCategory value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name("icon").value(value.icon == null ? "" : value.icon);
			out.name("description").value(value.description == null ? "" : value.description);
			out.name("rules").beginArray();
			if (value.rules != null) {
				for (String rule : value.rules) {
					if (rule != null) {
						out.value(rule);
					}
				}
			}
			out.endArray();
			out.endObject();
		}

		@Override
		public StorageCategory read(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			if (token == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			StorageCategory category = new StorageCategory();
			if (token == JsonToken.BEGIN_ARRAY) {
				category.rules = readRules(in);
				return category;
			}
			if (token != JsonToken.BEGIN_OBJECT) {
				in.skipValue();
				return null;
			}
			in.beginObject();
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
					case "icon" -> category.icon = readString(in);
					case "description" -> category.description = readString(in);
					case "rules" -> category.rules = in.peek() == JsonToken.BEGIN_ARRAY ? readRules(in) : skip(in);
					default -> in.skipValue();
				}
			}
			in.endObject();
			return category;
		}

		private static List<String> readRules(JsonReader in) throws IOException {
			List<String> rules = new ArrayList<>();
			in.beginArray();
			while (in.hasNext()) {
				String rule = readString(in);
				if (!rule.isBlank()) {
					rules.add(rule);
				}
			}
			in.endArray();
			return rules;
		}

		private static String readString(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
				return in.nextString();
			}
			in.skipValue();
			return "";
		}

		private static List<String> skip(JsonReader in) throws IOException {
			in.skipValue();
			return new ArrayList<>();
		}
	}
}
