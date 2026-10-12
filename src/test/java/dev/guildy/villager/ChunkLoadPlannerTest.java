package dev.guildy.villager;

import dev.guildy.data.ChunkKey;
import dev.guildy.villager.ChunkLoadPlanner.Anchor;
import dev.guildy.villager.ChunkLoadPlanner.GuildRequest;
import dev.guildy.villager.ChunkLoadPlanner.Kind;
import dev.guildy.villager.ChunkLoadPlanner.Limits;
import dev.guildy.villager.ChunkLoadPlanner.Mode;
import dev.guildy.villager.ChunkLoadPlanner.Plan;
import dev.guildy.villager.ChunkLoadPlanner.Players;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkLoadPlannerTest {
	private static final UUID GUILD_A = new UUID(1, 1);
	private static final UUID GUILD_B = new UUID(2, 2);
	private static final UUID V1 = new UUID(10, 1);
	private static final UUID V2 = new UUID(10, 2);
	private static final UUID V3 = new UUID(10, 3);

	private static Limits limits(Mode mode, int perGuild, int total) {
		return new Limits(mode, perGuild, total);
	}

	private static Anchor anchor(UUID villager, Kind kind, int cx, int cz, int radius, long distance) {
		return new Anchor(villager, kind, cx, cz, radius, distance);
	}

	private static Plan plan(Limits limits, GuildRequest... guilds) {
		return ChunkLoadPlanner.plan(limits, List.of(guilds), Players.NONE);
	}

	@Test
	void modesDecideWhichGuildsAreServed() {
		GuildRequest offline = new GuildRequest(GUILD_A, false, List.of(anchor(V1, Kind.SELF, 0, 0, 1, 0)));
		GuildRequest online = new GuildRequest(GUILD_B, true, List.of(anchor(V2, Kind.SELF, 50, 50, 1, 0)));

		Plan never = plan(limits(Mode.NEVER, 100, 100), offline, online);
		assertTrue(never.tickets().isEmpty());
		assertEquals(0, never.totalChunks());

		Plan onlineOnly = plan(limits(Mode.ONLINE, 100, 100), offline, online);
		assertEquals(Map.of(ChunkKey.of(50, 50), 1), onlineOnly.tickets());
		assertEquals(0, onlineOnly.chunks(GUILD_A));
		assertEquals(9, onlineOnly.chunks(GUILD_B));

		Plan always = plan(limits(Mode.ALWAYS, 100, 100), offline, online);
		assertEquals(2, always.tickets().size());
		assertEquals(9, always.chunks(GUILD_A));
		assertEquals(18, always.totalChunks());

		assertTrue(ChunkLoadPlanner.active(Mode.ALWAYS, false));
		assertTrue(ChunkLoadPlanner.active(Mode.ONLINE, true));
		assertFalse(ChunkLoadPlanner.active(Mode.ONLINE, false));
		assertFalse(ChunkLoadPlanner.active(Mode.NEVER, true));
	}

	@Test
	void radiusGivesASquareAndSharedChunksCountOnce() {
		// Two villagers in the same chunk and a third one chunk further: one ticket per centre, overlap counted once.
		GuildRequest request = new GuildRequest(GUILD_A, true, List.of(
				anchor(V1, Kind.SELF, 4, -3, 1, 0),
				anchor(V2, Kind.SELF, 4, -3, 1, 0),
				anchor(V3, Kind.SELF, 5, -3, 1, 10)));
		Plan result = plan(limits(Mode.ONLINE, 100, 100), request);
		assertEquals(Map.of(ChunkKey.of(4, -3), 1, ChunkKey.of(5, -3), 1), result.tickets());
		// 3x3 + 3x3 shifted by one chunk = 4 x 3.
		assertEquals(12, result.chunks(GUILD_A));
		assertTrue(result.kept().containsAll(List.of(V1, V2, V3)));
		assertTrue(result.capped().isEmpty());

		Plan single = plan(limits(Mode.ONLINE, 100, 100), new GuildRequest(GUILD_A, true, List.of(anchor(V1, Kind.SELF, 0, 0, 0, 0))));
		assertEquals(Map.of(ChunkKey.of(0, 0), 0), single.tickets());
		assertEquals(1, single.chunks(GUILD_A));

		Plan wide = plan(limits(Mode.ONLINE, 100, 100), new GuildRequest(GUILD_A, true, List.of(anchor(V1, Kind.ZONE, 0, 0, 2, 0))));
		assertEquals(25, wide.chunks(GUILD_A));
	}

	@Test
	void guildCapShrinksThenRefuses() {
		GuildRequest request = new GuildRequest(GUILD_A, true, List.of(
				anchor(V1, Kind.SELF, 0, 0, 1, 0),
				anchor(V2, Kind.SELF, 10, 0, 1, 100),
				anchor(V3, Kind.SELF, 20, 0, 1, 400)));
		// 9 for the first villager, 1 left: the second one gets its own chunk only, the third nothing.
		Plan result = plan(limits(Mode.ALWAYS, 10, 1000), request);
		assertEquals(Map.of(ChunkKey.of(0, 0), 1, ChunkKey.of(10, 0), 0), result.tickets());
		assertEquals(10, result.chunks(GUILD_A));
		assertTrue(result.kept().contains(V1) && result.kept().contains(V2));
		assertFalse(result.kept().contains(V3));
		assertTrue(result.capped().contains(V3));
		assertFalse(result.capped().contains(V1));

		Plan none = plan(limits(Mode.ALWAYS, 0, 1000), request);
		assertTrue(none.tickets().isEmpty());
		assertEquals(3, none.capped().size());
	}

	@Test
	void serverCapIsSharedInTurns() {
		// Guild A has two villagers, guild B one: A0, then B0, then A1 (refused: the server cap is reached).
		GuildRequest a = new GuildRequest(GUILD_A, true, List.of(
				anchor(V1, Kind.SELF, 0, 0, 1, 0),
				anchor(V2, Kind.SELF, 10, 0, 1, 50)));
		GuildRequest b = new GuildRequest(GUILD_B, true, List.of(anchor(V3, Kind.SELF, 100, 100, 1, 0)));
		Plan result = plan(limits(Mode.ALWAYS, 64, 18), a, b);
		assertEquals(18, result.totalChunks());
		assertEquals(9, result.chunks(GUILD_A));
		assertEquals(9, result.chunks(GUILD_B));
		assertTrue(result.kept().contains(V3), "the second guild is served before the first one's second villager");
		assertTrue(result.capped().contains(V2));
		assertNull(result.tickets().get(ChunkKey.of(10, 0)));
	}

	@Test
	void villagersFirstThenDestinationsThenZonesNearestFirst() {
		// Listed in the wrong order on purpose: the zone first, the far villager before the near one.
		GuildRequest request = new GuildRequest(GUILD_A, true, List.of(
				anchor(V1, Kind.ZONE, 30, 30, 1, 0),
				anchor(V2, Kind.STORAGE, 40, 40, 0, 0),
				anchor(V1, Kind.SELF, 20, 0, 1, 400),
				anchor(V2, Kind.SELF, 0, 20, 1, 100),
				anchor(V1, Kind.DESTINATION, -20, 0, 0, 900)));
		// Room for both villagers (18) and the destination (1): the zone and the storage chest are left out.
		Plan result = plan(limits(Mode.ALWAYS, 19, 1000), request);
		assertEquals(Map.of(ChunkKey.of(0, 20), 1, ChunkKey.of(20, 0), 1, ChunkKey.of(-20, 0), 0), result.tickets());
		assertTrue(result.capped().contains(V1) && result.capped().contains(V2));

		// Room for one villager only: the one nearest to the heart wins.
		Plan tight = plan(limits(Mode.ALWAYS, 9, 1000), request);
		assertEquals(Map.of(ChunkKey.of(0, 20), 1), tight.tickets());
		assertTrue(tight.kept().contains(V2));
		assertFalse(tight.kept().contains(V1));
	}

	@Test
	void anchorsAlreadyKeptActiveByAPlayerCostNothing() {
		Players player = new Players(new long[]{ChunkKey.of(0, 0)}, 8);
		assertTrue(player.covers(5, -3, 1));
		assertTrue(player.covers(7, 0, 1));
		assertFalse(player.covers(8, 0, 1), "the square reaches one chunk beyond the player's reach");
		assertFalse(Players.NONE.covers(0, 0, 0));

		GuildRequest request = new GuildRequest(GUILD_A, true, List.of(
				anchor(V1, Kind.SELF, 2, 2, 1, 0),
				anchor(V2, Kind.SELF, 30, 0, 1, 0)));
		Plan result = ChunkLoadPlanner.plan(limits(Mode.ONLINE, 9, 1000), List.of(request), player);
		// The villager near the player needs no ticket, so the far one fits in the cap.
		assertEquals(Map.of(ChunkKey.of(30, 0), 1), result.tickets());
		assertFalse(result.kept().contains(V1));
		assertFalse(result.capped().contains(V1));
		assertTrue(result.kept().contains(V2));
	}

	@Test
	void sameCentreKeepsTheLargestRadius() {
		GuildRequest request = new GuildRequest(GUILD_A, true, List.of(
				anchor(V1, Kind.DESTINATION, 3, 3, 0, 0),
				anchor(V2, Kind.ZONE, 3, 3, 2, 0)));
		Plan result = plan(limits(Mode.ALWAYS, 100, 100), request);
		assertEquals(Map.of(ChunkKey.of(3, 3), 2), result.tickets());
		assertEquals(25, result.chunks(GUILD_A));
	}

	@Test
	void emptyInputsPlanNothing() {
		assertSame(Plan.EMPTY, ChunkLoadPlanner.plan(limits(Mode.ALWAYS, 100, 100), List.of(), Players.NONE));
		Plan noAnchor = plan(limits(Mode.ALWAYS, 100, 100), new GuildRequest(GUILD_A, true, List.of()));
		assertTrue(noAnchor.tickets().isEmpty());
		assertEquals(0, noAnchor.chunks(GUILD_A));
	}

	@Test
	void modeParsingAndConfigBounds() {
		assertEquals(Mode.ALWAYS, Mode.parse(" Always "));
		assertEquals(Mode.NEVER, Mode.parse("never"));
		assertEquals(Mode.ONLINE, Mode.parse("ONLINE"));
		assertNull(Mode.parse("sometimes"));
		assertNull(Mode.parse(null));
		assertEquals("always", Mode.ALWAYS.id());

		ChunkLoadingConfig defaults = new ChunkLoadingConfig();
		assertFalse(defaults.validate());
		assertEquals("online", defaults.mode);
		assertEquals(Mode.ONLINE, defaults.mode());
		assertEquals(1, defaults.radius);

		ChunkLoadingConfig config = new ChunkLoadingConfig();
		config.mode = "Toujours";
		config.radius = 9;
		config.zoneRadius = -1;
		config.maxChunksPerGuild = -5;
		config.maxChunksTotal = 1_000_000;
		config.refreshSeconds = 60;
		config.bedTravelSeconds = -3;
		assertTrue(config.validate(), "an unknown mode is replaced");
		assertEquals("online", config.mode);
		assertEquals(3, config.radius);
		assertEquals(0, config.zoneRadius);
		assertEquals(0, config.maxChunksPerGuild);
		assertEquals(8192, config.maxChunksTotal);
		assertEquals(5, config.refreshSeconds);
		assertEquals(0, config.bedTravelSeconds);

		ChunkLoadingConfig upper = new ChunkLoadingConfig();
		upper.mode = " ALWAYS";
		assertFalse(upper.validate());
		assertEquals("always", upper.mode);

		// A villagers.json5 written before this section: the defaults are used.
		VillagerConfig old = new VillagerConfig();
		old.chunkLoading = null;
		old.validate();
		assertEquals(Mode.ONLINE, old.chunkLoading.mode());
	}
}
