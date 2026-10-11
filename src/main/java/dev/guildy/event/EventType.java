package dev.guildy.event;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Random events. {@link #global} events concern the whole server (no guild argument for the admin commands).
 */
public enum EventType {
	/** "Nuit de sang": every horde of the night is much harder and pays more. */
	BLOOD_NIGHT("blood_night", true),
	/** "Raid de pillards": a day horde of illagers against one guild. */
	PILLAGER_RAID("pillager_raid", false),
	/** "Caravane marchande": a wandering trader + llamas with caravan trades. */
	CARAVAN("caravan", false),
	/** "Marchand de livres enchantés": the "Érudit itinérant" sells enchanted books. */
	BOOK_MERCHANT("book_merchant", false),
	/** Visit of the trade hub's "Marchand de passage" (trade module). */
	MERCHANT("merchant", false);

	public final String id;
	public final boolean global;

	EventType(String id, boolean global) {
		this.id = id;
		this.global = global;
	}

	/** Lang key of the display name. */
	public String nameKey() {
		return "event." + id + ".name";
	}

	public static @Nullable EventType byId(String id) {
		for (EventType type : values()) {
			if (type.id.equalsIgnoreCase(id == null ? "" : id.trim())) {
				return type;
			}
		}
		return null;
	}

	public static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (EventType type : values()) {
			ids.add(type.id);
		}
		return ids;
	}
}
