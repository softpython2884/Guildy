package dev.guildy.ruin;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ruins.json keeps the statues, their routes and the trails; older saves without them load. */
class StatueStoreTest {
	@Test
	void statuesAndTrailsRoundTrip() {
		RuinStore.Root root = new RuinStore.Root();
		StatueRecord placed = new StatueRecord();
		placed.id = 3;
		placed.dimension = "minecraft:overworld";
		placed.status = StatueRecord.Status.PLACED;
		placed.plannedX = 100;
		placed.plannedY = 70;
		placed.plannedZ = -40;
		placed.template = "guildy:ruin/statue_jaguar";
		placed.x = 104;
		placed.y = 71;
		placed.z = -38;
		placed.rotation = 1;
		placed.minX = 97;
		placed.maxX = 111;
		placed.minY = 70;
		placed.maxY = 84;
		placed.minZ = -45;
		placed.maxZ = -31;
		placed.eyes = List.of(new BlockPos(108, 82, -40), new BlockPos(108, 82, -36));
		StatueRecord.Route route = new StatueRecord.Route("minecraft:overworld@10,20@guildy:ruin/grand_temple_maya", 5);
		route.next = 4;
		route.toX = 600;
		route.toY = 75;
		route.toZ = -60;
		placed.routes.add(route);
		placed.timesGuided = 2;
		StatueRecord pending = new StatueRecord();
		pending.id = 4;
		pending.dimension = "minecraft:overworld";
		pending.plannedX = 600;
		pending.plannedZ = -60;
		pending.routes.add(new StatueRecord.Route(route.temple, 5));
		root.statues.add(placed);
		root.statues.add(pending);
		StatueTrail trail = new StatueTrail();
		trail.id = 5;
		trail.temple = route.temple;
		trail.dimension = "minecraft:overworld";
		trail.guild = UUID.randomUUID();
		trail.guildName = "Les Jaguars";
		trail.originX = 10;
		trail.originZ = 12;
		trail.statues.addAll(List.of(3, 4));
		root.trails.add(trail);

		RuinStore.Root back = RuinStore.parse(RuinStore.toJson(root));
		assertEquals(2, back.statues.size());
		StatueRecord s = back.statues.getFirst();
		assertEquals(StatueRecord.Status.PLACED, s.status);
		assertEquals(104, s.x);
		assertEquals(71, s.y);
		assertEquals(1, s.rotation);
		assertEquals(placed.eyes, s.eyes);
		assertTrue(s.contains(108, 82, -40, 0));
		StatueRecord.Route r = s.route(route.temple);
		assertNotNull(r, "route kept");
		assertEquals(4, r.next);
		assertEquals(600, r.toX);
		assertEquals(5, r.trail);
		assertEquals(2, s.timesGuided);
		StatueRecord p = back.statues.get(1);
		assertEquals(StatueRecord.Status.PENDING, p.status);
		assertEquals(-1, p.routes.getFirst().next, "the last statue leads to the temple");
		assertFalse(p.isPlaced());
		assertEquals(1, back.trails.size());
		assertEquals(List.of(3, 4), back.trails.getFirst().statues);
		assertEquals(trail.guild, back.trails.getFirst().guild);
		assertEquals("Les Jaguars", back.trails.getFirst().guildName);
		assertEquals(5, back.nextStatueId, "ids continue after the highest one");
		assertEquals(6, back.nextTrailId);
	}

	@Test
	void olderSavesLoadWithoutStatues() {
		RuinStore.Root root = RuinStore.parse("{\"dataVersion\": 1, \"ruins\": [], \"guilds\": {}}");
		assertTrue(root.statues.isEmpty());
		assertTrue(root.trails.isEmpty());
		assertEquals(1, root.nextStatueId);
		RuinStore.Root broken = RuinStore.parse("{\"statues\": [null, {\"id\": 0}, {\"id\": 7, \"routes\": [null, {\"temple\": \"\"}]}],"
				+ " \"trails\": [{\"id\": 2, \"statues\": null}]}");
		assertEquals(1, broken.statues.size(), "statues without an id dropped");
		StatueRecord s = broken.statues.getFirst();
		assertEquals(StatueRecord.Status.PENDING, s.status);
		assertTrue(s.routes.isEmpty(), "broken routes dropped");
		assertNull(s.route("x"));
		assertEquals(8, broken.nextStatueId);
		assertTrue(broken.trails.getFirst().statues.isEmpty());
	}

	@Test
	void researchOfOlderSavesIsNoLegend() {
		RuinStore.Root root = RuinStore.parse("{\"guilds\": {\"" + UUID.randomUUID() + "\": {\"research\": {\"nether\": false, \"progress\": 3}}}}");
		GuildRuinData data = root.guilds.values().iterator().next();
		assertNotNull(data.research);
		assertFalse(data.research.legend);
		assertEquals(0, data.research.trailId);
	}
}
