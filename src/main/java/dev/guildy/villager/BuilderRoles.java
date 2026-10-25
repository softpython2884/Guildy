package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Who builds and who fetches the materials among the builders of a guild (pure, unit tested), like the roles of the
 * alchemists ({@link AlchemyRoles}). A builder has a preferred role: a manual one chosen in its menu, or one given by
 * this distribution. Each step it works at its role when that role is possible, else at the other one ("plus possible
 * ou pas besoin : il passe à l'autre").
 * <p>
 * Distribution: when only one role is possible, every automatic builder takes it. When both are, the places follow the
 * needs (highest averages on {@code buildNeed} — cells that can be worked now — and {@code gatherNeed} — items still
 * missing — each times its weight), with at least one place per role from two builders, at most {@code maxGatherShare}
 * of all the builders gathering, manual roles counted first; a builder keeps its role while that role still has a place
 * (no reshuffle when nothing changed).
 */
public final class BuilderRoles {
	public enum Role {
		BUILD,
		GATHER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Role other() {
			return this == BUILD ? GATHER : BUILD;
		}

		/** Role of a saved text, null for "" / unknown (automatic). */
		public static @Nullable Role parse(@Nullable String text) {
			if (text == null) {
				return null;
			}
			for (Role role : values()) {
				if (role.name().equalsIgnoreCase(text.trim())) {
					return role;
				}
			}
			return null;
		}
	}

	private BuilderRoles() {
	}

	/**
	 * Preferred role of each builder ({@code ids} in a stable order).
	 *
	 * @param manual   roles chosen in the menus
	 * @param previous the last distribution
	 */
	public static Map<String, Role> assign(List<String> ids, Map<String, Role> manual, Map<String, Role> previous,
			boolean buildPossible, boolean gatherPossible, double buildNeed, double gatherNeed, double maxGatherShare) {
		Map<String, Role> result = new LinkedHashMap<>();
		List<String> auto = new ArrayList<>();
		Map<Role, Integer> counts = new EnumMap<>(Role.class);
		for (String id : ids) {
			Role chosen = manual.get(id);
			if (chosen != null) {
				result.put(id, chosen);
				counts.merge(chosen, 1, Integer::sum);
			} else {
				auto.add(id);
			}
		}
		if (auto.isEmpty()) {
			return result;
		}
		if (buildPossible != gatherPossible || !buildPossible) {
			Role only = gatherPossible && !buildPossible ? Role.GATHER : Role.BUILD;
			for (String id : auto) {
				result.put(id, only);
			}
			return result;
		}
		Map<Role, Integer> slots = slots(ids.size(), auto.size(), counts, Math.max(0, buildNeed), Math.max(0, gatherNeed),
				Math.max(0, Math.min(1, maxGatherShare)));
		List<String> waiting = new ArrayList<>();
		for (String id : auto) {
			Role before = previous.get(id);
			if (before != null && slots.getOrDefault(before, 0) > 0) {
				result.put(id, before);
				slots.merge(before, -1, Integer::sum);
			} else {
				waiting.add(id);
			}
		}
		for (String id : waiting) {
			Role role = slots.getOrDefault(Role.BUILD, 0) > 0 ? Role.BUILD : slots.getOrDefault(Role.GATHER, 0) > 0 ? Role.GATHER : Role.BUILD;
			result.put(id, role);
			slots.merge(role, -1, Integer::sum);
		}
		return result;
	}

	/** Places of each role for the automatic builders (both roles possible). */
	static Map<Role, Integer> slots(int total, int autoCount, Map<Role, Integer> counts, double buildNeed, double gatherNeed,
			double maxGatherShare) {
		int maxGather = (int) Math.floor(total * maxGatherShare + 1e-9);
		int taken = counts.getOrDefault(Role.GATHER, 0);
		int build = counts.getOrDefault(Role.BUILD, 0);
		Map<Role, Integer> slots = new EnumMap<>(Role.class);
		double wb = buildNeed > 0 ? buildNeed : 1e-9;
		double wg = gatherNeed > 0 ? gatherNeed : 1e-9;
		for (int i = 0; i < autoCount; i++) {
			Role next;
			if (build == 0) {
				// Someone always builds with what is there (a lone builder too: it gathers once nothing can be built).
				next = Role.BUILD;
			} else if (taken == 0 && maxGather >= 1) {
				// Then someone fetches what is missing.
				next = Role.GATHER;
			} else if (taken >= maxGather) {
				next = Role.BUILD;
			} else {
				next = wg / (taken + 1) > wb / (build + 1) + 1e-12 ? Role.GATHER : Role.BUILD;
			}
			if (next == Role.GATHER) {
				taken++;
			} else {
				build++;
			}
			slots.merge(next, 1, Integer::sum);
		}
		return slots;
	}
}
