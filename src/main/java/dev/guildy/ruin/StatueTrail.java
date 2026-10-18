package dev.guildy.ruin;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A trail of jaguar statues toward a grand temple (persisted in {@code <world>/guildy/ruins.json}): created by a legend
 * research (or the admin command), shown by its statue map. Its statues may be shared with other trails.
 */
public class StatueTrail {
	public int id;
	/** Ruin key of the temple. */
	public String temple = "";
	public String dimension = "";
	/** Guild of the research (null: admin command). */
	public @Nullable UUID guild;
	public String guildName = "";
	/** Where the trail starts (the atelier). */
	public int originX;
	public int originZ;
	/** Statue ids from the origin to the temple. */
	public List<Integer> statues = new ArrayList<>();
	public long createdAt;

	public StatueTrail() {
	}

	void fixNulls() {
		if (temple == null) {
			temple = "";
		}
		if (dimension == null) {
			dimension = "";
		}
		if (guildName == null) {
			guildName = "";
		}
		if (statues == null) {
			statues = new ArrayList<>();
		}
		statues.removeIf(id -> id == null);
	}
}
