package dev.guildy.structure;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-guild structures (persisted inside the guild).
 */
public class GuildStructureData {
	/** Structure ids unlocked (bought) by the guild. */
	public Set<String> unlocked = new LinkedHashSet<>();
	/** Validated structures currently standing. */
	public List<BuiltStructure> built = new ArrayList<>();
	/** Structure ids whose first-use explanation was already shown. */
	public Set<String> tutorialsSeen = new LinkedHashSet<>();

	public @Nullable BuiltStructure find(String type) {
		for (BuiltStructure structure : built) {
			if (type.equals(structure.type)) {
				return structure;
			}
		}
		return null;
	}
}
