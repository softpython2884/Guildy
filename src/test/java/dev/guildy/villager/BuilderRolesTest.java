package dev.guildy.villager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Distribution of the builders' roles (Construire / Récolter). */
class BuilderRolesTest {
	private static final List<String> FOUR = List.of("a", "b", "c", "d");

	private static long count(Map<String, BuilderRoles.Role> roles, BuilderRoles.Role role) {
		return roles.values().stream().filter(r -> r == role).count();
	}

	@Test
	void onlyOnePossibleRole() {
		Map<String, BuilderRoles.Role> build = BuilderRoles.assign(FOUR, Map.of(), Map.of(), true, false, 10, 0, 0.5);
		assertEquals(4, count(build, BuilderRoles.Role.BUILD));
		// Nothing to build but materials wanted: everyone gathers (no site at all, or all waiting).
		Map<String, BuilderRoles.Role> gather = BuilderRoles.assign(FOUR, Map.of(), Map.of(), false, true, 0, 40, 0.5);
		assertEquals(4, count(gather, BuilderRoles.Role.GATHER));
		Map<String, BuilderRoles.Role> none = BuilderRoles.assign(FOUR, Map.of(), Map.of(), false, false, 0, 0, 0.5);
		assertEquals(4, count(none, BuilderRoles.Role.BUILD));
	}

	@Test
	void bothRolesShareByNeedWithinTheGatherShare() {
		Map<String, BuilderRoles.Role> roles = BuilderRoles.assign(FOUR, Map.of(), Map.of(), true, true, 10, 1000, 0.5);
		assertEquals(2, count(roles, BuilderRoles.Role.GATHER), "at most half gather");
		Map<String, BuilderRoles.Role> little = BuilderRoles.assign(FOUR, Map.of(), Map.of(), true, true, 1000, 1, 0.5);
		assertEquals(1, count(little, BuilderRoles.Role.GATHER), "one gatherer from two builders");
		Map<String, BuilderRoles.Role> alone = BuilderRoles.assign(List.of("a"), Map.of(), Map.of(), true, true, 1, 1000, 0.5);
		assertEquals(BuilderRoles.Role.BUILD, alone.get("a"), "a lone builder builds what it can first");
		Map<String, BuilderRoles.Role> noShare = BuilderRoles.assign(FOUR, Map.of(), Map.of(), true, true, 10, 1000, 0.0);
		assertEquals(0, count(noShare, BuilderRoles.Role.GATHER));
	}

	@Test
	void manualRolesCountAndRolesStayStable() {
		Map<String, BuilderRoles.Role> manual = Map.of("a", BuilderRoles.Role.GATHER, "b", BuilderRoles.Role.GATHER);
		Map<String, BuilderRoles.Role> roles = BuilderRoles.assign(FOUR, manual, Map.of(), true, true, 10, 1000, 0.5);
		assertEquals(BuilderRoles.Role.GATHER, roles.get("a"));
		assertEquals(BuilderRoles.Role.BUILD, roles.get("c"), "the gather share is full with the manual gatherers");
		assertEquals(BuilderRoles.Role.BUILD, roles.get("d"));
		Map<String, BuilderRoles.Role> first = BuilderRoles.assign(FOUR, Map.of(), Map.of(), true, true, 10, 30, 0.5);
		Map<String, BuilderRoles.Role> again = BuilderRoles.assign(FOUR, Map.of(), first, true, true, 10, 30, 0.5);
		assertEquals(first, again);
		// The previous gatherer keeps its role when the places do not change.
		Map<String, BuilderRoles.Role> swapped = BuilderRoles.assign(FOUR, Map.of(), Map.of("d", BuilderRoles.Role.GATHER), true, true, 1000, 1, 0.5);
		assertEquals(BuilderRoles.Role.GATHER, swapped.get("d"));
		assertEquals(1, count(swapped, BuilderRoles.Role.GATHER));
	}

	@Test
	void parsing() {
		assertEquals(BuilderRoles.Role.GATHER, BuilderRoles.Role.parse(" gather "));
		assertNull(BuilderRoles.Role.parse(""));
		assertNull(BuilderRoles.Role.parse(null));
		assertEquals(BuilderRoles.Role.BUILD, BuilderRoles.Role.GATHER.other());
		assertEquals("build", BuilderRoles.Role.BUILD.id());
	}
}
