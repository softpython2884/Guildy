package dev.guildy.villager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * A container assigned to the guild storage system (persisted). In guilds.json each container is one compact line
 * without its default values ({@link Json}).
 */
@JsonAdapter(StorageChest.Json.class)
public class StorageChest {
	public enum Role {
		/** Workers drop their loot here; the sorter empties it. */
		DEPOSIT,
		/** Receives the items of {@link #category} (the fallback category name = "misc" chest). */
		CATEGORY,
		/** Receives only the items its {@link #filter} accepts (family or exact items), before their category chests. */
		FILTER
	}

	public BlockPos pos;
	public Role role = Role.DEPOSIT;
	/** Category name when role = CATEGORY. */
	public String category = "";
	/**
	 * Custom name Guildy last gave to the container ("Dépôt", "Minerais et lingots"), "" if none: it is only replaced or
	 * removed while the container still bears it (see {@link StorageLabels}).
	 */
	public String label = "";
	/**
	 * Automatic chest of a chest room ({@link ChestRoom}): its category is chosen by the room planning
	 * ({@link ChestRooms}), "" = free (reserve, receives nothing). A role chosen by a player makes it manual.
	 */
	public boolean auto = false;
	/** Chest room holding this container (0 = none). */
	public int room = 0;
	/** What a FILTER chest accepts (null for the other roles). */
	public @Nullable ChestFilter filter;

	public StorageChest() {
	}

	public StorageChest(BlockPos pos, Role role, String category) {
		this.pos = pos;
		this.role = role;
		this.category = category == null ? "" : category;
	}

	public static StorageChest filtered(BlockPos pos, ChestFilter filter) {
		StorageChest chest = new StorageChest(pos, Role.FILTER, "");
		chest.filter = filter;
		return chest;
	}

	public boolean isDeposit() {
		return role == Role.DEPOSIT;
	}

	/** Filter chest with a usable filter. */
	public boolean isFilter() {
		return role == Role.FILTER && filter != null;
	}

	/** Automatic chest without category yet (reserve of a chest room): no group, receives nothing. */
	public boolean isFree() {
		return auto && role == Role.CATEGORY && (category == null || category.isBlank());
	}

	/**
	 * guilds.json form of a container: one compact line ({"pos":[x,y,z],"role":"CATEGORY","category":"Pierre"...})
	 * without the default values (no category, no label, not automatic, no room, no filter), so that thousands of
	 * containers stay a small file; read like any object (older files with every field included).
	 */
	static final class Json implements TypeAdapterFactory {
		@Override
		@SuppressWarnings("unchecked")
		public <T> @Nullable TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			if (type.getRawType() != StorageChest.class) {
				return null;
			}
			TypeAdapter<StorageChest> delegate = gson.getDelegateAdapter(this, TypeToken.get(StorageChest.class));
			TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
			return (TypeAdapter<T>) new TypeAdapter<StorageChest>() {
				@Override
				public void write(JsonWriter out, StorageChest value) throws IOException {
					if (value == null) {
						out.nullValue();
						return;
					}
					JsonElement tree = delegate.toJsonTree(value);
					if (tree.isJsonObject()) {
						JsonObject object = tree.getAsJsonObject();
						object.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isJsonNull());
						dropDefault(object, "category", new JsonPrimitive(""));
						dropDefault(object, "label", new JsonPrimitive(""));
						dropDefault(object, "auto", new JsonPrimitive(false));
						dropDefault(object, "room", new JsonPrimitive(0));
					}
					try {
						out.jsonValue(tree.toString());
					} catch (UnsupportedOperationException e) {
						// A tree writer (toJsonTree) takes no raw JSON: the same object, written normally.
						elements.write(out, tree);
					}
				}

				@Override
				public StorageChest read(JsonReader in) throws IOException {
					return delegate.read(in);
				}
			};
		}

		private static void dropDefault(JsonObject object, String name, JsonPrimitive value) {
			JsonElement current = object.get(name);
			if (current != null && current.isJsonPrimitive()) {
				JsonPrimitive primitive = current.getAsJsonPrimitive();
				boolean same = value.isNumber() ? primitive.isNumber() && primitive.getAsInt() == value.getAsInt() : primitive.equals(value);
				if (same) {
					object.remove(name);
				}
			}
		}
	}

	void sanitize() {
		if (role == null) {
			role = Role.DEPOSIT;
		}
		if (category == null) {
			category = "";
		}
		if (label == null) {
			label = "";
		}
		if (room < 0) {
			room = 0;
		}
		if (role == Role.FILTER && (filter == null || !filter.sanitize())) {
			// A filter that accepts nothing (edited data): the chest becomes a fallback chest.
			role = Role.CATEGORY;
			filter = null;
		}
		if (role != Role.FILTER) {
			filter = null;
		} else {
			auto = false;
		}
		if (auto) {
			role = Role.CATEGORY;
		}
	}
}
