package dev.guildy.unique;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry of the unique weapon copies: serials, uniqueness per mode, holder changes, loss / found transitions, legacy
 * registration, duplicates, collections and the JSON round trip.
 */
class UniqueLedgerTest {
	private static final UUID DRAGONS = UUID.fromString("00000000-0000-0000-0000-00000000000d");
	private static final UUID WOLVES = UUID.fromString("00000000-0000-0000-0000-00000000000f");
	private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID LONER = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private static UniqueLedger ledger() {
		UniqueLedger ledger = new UniqueLedger();
		ledger.fixNulls();
		return ledger;
	}

	@Test
	void issuedCopiesGetIncreasingSerials() {
		UniqueLedger ledger = ledger();
		UniqueLedger.Copy first = ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 1000, 1);
		UniqueLedger.Copy second = ledger.issue("croc_de_givre", "quest", WOLVES, null, null, 2000, 2);
		assertEquals(1, first.serial);
		assertEquals(2, second.serial);
		assertEquals(3, ledger.nextSerial());
		assertTrue(first.active());
		assertEquals(DRAGONS, first.guild);
		assertEquals("Steve", first.playerName);
		assertEquals("", second.playerName);
		assertEquals(Set.of("foudrelame"), ledger.obtainedBy(DRAGONS), "the collection remembers it");
		assertEquals(Set.of(DRAGONS, WOLVES), ledger.drainGrown());
		assertTrue(ledger.drainGrown().isEmpty(), "drained once");
		assertTrue(ledger.isDirty());
	}

	@Test
	void exclusionDependsOnTheMode() {
		UniqueLedger ledger = ledger();
		ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		ledger.issue("croc_de_givre", "boss", WOLVES, ALEX, "Alex", 0, 0);
		UniqueLedger.Copy lost = ledger.issue("arc_du_phenix", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		ledger.markLost(lost.serial, "broken", 10);
		ledger.issue("pic_du_filon", "quest", null, LONER, "Loner", 0, 0);

		assertEquals(Set.of("foudrelame"), ledger.excluded(UniquenessMode.GUILD, DRAGONS, STEVE), "only the guild's active copies");
		assertEquals(Set.of("croc_de_givre"), ledger.excluded(UniquenessMode.GUILD, WOLVES, null));
		assertEquals(Set.of("pic_du_filon"), ledger.excluded(UniquenessMode.GUILD, null, LONER), "a guildless player: his own copies");
		assertTrue(ledger.excluded(UniquenessMode.GUILD, null, null).isEmpty(), "no recipient: nothing excluded");
		assertEquals(Set.of("foudrelame", "croc_de_givre", "pic_du_filon"), ledger.excluded(UniquenessMode.SERVER, WOLVES, null),
				"server: every active copy, a lost one is available again");
		assertTrue(ledger.excluded(UniquenessMode.NONE, DRAGONS, STEVE).isEmpty());
	}

	@Test
	void sightingsMoveTheHolder() {
		UniqueLedger ledger = ledger();
		UniqueLedger.Copy copy = ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		assertEquals(UniqueLedger.Sighting.SAME, ledger.see(copy.serial, "foudrelame", DRAGONS, STEVE, "Steve", true, "x", 5000, 5));
		assertEquals(5000, copy.lastSeen);

		// Given to a member of another guild: the holder (and the uniqueness check) follows.
		assertEquals(UniqueLedger.Sighting.MOVED, ledger.see(copy.serial, "foudrelame", WOLVES, ALEX, "Alex", true, "x", 6000, 6));
		assertEquals(WOLVES, copy.guild);
		assertEquals(ALEX, copy.player);
		assertTrue(ledger.excluded(UniquenessMode.GUILD, DRAGONS, STEVE).isEmpty(), "the Dragons may roll it again");
		assertEquals(Set.of("foudrelame"), ledger.excluded(UniquenessMode.GUILD, WOLVES, ALEX));
		assertEquals(Set.of("foudrelame"), ledger.obtainedBy(DRAGONS), "it stays in the Dragons' collection");
		assertEquals(Set.of("foudrelame"), ledger.obtainedBy(WOLVES));

		// Seen in a chest a Dragon opened: only a sighting, the holder stays.
		assertEquals(UniqueLedger.Sighting.SAME, ledger.see(copy.serial, "foudrelame", DRAGONS, STEVE, "Steve", false, "x", 7000, 7));
		assertEquals(WOLVES, copy.guild);
		assertEquals(7000, copy.lastSeen);

		// A copy that never had a holder (rolled without recipient) takes the one who opens its chest.
		UniqueLedger.Copy unheld = ledger.issue("arc_du_phenix", "boss", null, null, null, 0, 0);
		assertEquals(UniqueLedger.Sighting.MOVED, ledger.see(unheld.serial, "arc_du_phenix", DRAGONS, STEVE, "Steve", false, "x", 7500, 7));
		assertEquals(DRAGONS, unheld.guild);
		assertTrue(ledger.obtainedBy(DRAGONS).contains("arc_du_phenix"));

		// The same serial on another weapon (item from another world): conflict, the stack needs a new serial.
		assertEquals(UniqueLedger.Sighting.CONFLICT, ledger.see(copy.serial, "croc_de_givre", DRAGONS, STEVE, "Steve", true, "x", 8000, 8));
	}

	@Test
	void legacyAndUnknownCopiesAreRegistered() {
		UniqueLedger ledger = ledger();
		// A legacy stack without serial: registered with the next serial, held by the player who carries it.
		UniqueLedger.Copy legacy = ledger.issue("pic_du_filon", UniqueLedger.LEGACY, DRAGONS, STEVE, "Steve", 100, 1);
		assertEquals(1, legacy.serial);
		assertEquals(UniqueLedger.LEGACY, legacy.source);
		// A stack with a serial the registry does not know (registry file lost): registered as is, counter moved past it.
		assertEquals(UniqueLedger.Sighting.NEW, ledger.see(41, "foudrelame", WOLVES, ALEX, "Alex", true, UniqueLedger.LEGACY, 200, 2));
		UniqueLedger.Copy unknown = ledger.copy(41);
		assertNotNull(unknown);
		assertEquals(WOLVES, unknown.guild);
		assertEquals(42, ledger.nextSerial());
		assertEquals(42, ledger.issue("croc_de_givre", "boss", null, null, null, 300, 3).serial);
	}

	@Test
	void destroyedCopiesAreLostUnlessSeenAgain() {
		UniqueLedger ledger = ledger();
		UniqueLedger.Copy burnt = ledger.issue("croc_de_givre", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		UniqueLedger.Copy picked = ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		ledger.suspectLoss(burnt.serial, 100, "destroyed");
		ledger.suspectLoss(picked.serial, 100, "destroyed");
		assertTrue(ledger.hasPendingLosses());
		assertTrue(ledger.confirmLosses(130, 60, 5000, false).isEmpty(), "too early");
		// The picked up copy is seen in an inventory right after its entity was removed.
		ledger.see(picked.serial, "foudrelame", DRAGONS, STEVE, "Steve", true, "x", 4000, 110);
		List<UniqueLedger.Copy> lost = ledger.confirmLosses(160, 60, 6000, false);
		assertEquals(List.of(burnt), lost);
		assertEquals(UniqueLedger.State.LOST, burnt.state);
		assertEquals("destroyed", burnt.lostReason);
		assertEquals(6000, burnt.lostAt);
		assertTrue(picked.active(), "picked up, not lost");
		assertFalse(ledger.hasPendingLosses());
		assertTrue(ledger.excluded(UniquenessMode.SERVER, null, null).equals(Set.of("foudrelame")), "the burnt id is free again");

		// Seen again (it was not really destroyed): active again, with its new holder.
		assertEquals(UniqueLedger.Sighting.FOUND, ledger.see(burnt.serial, "croc_de_givre", WOLVES, ALEX, "Alex", false, "x", 9000, 300));
		assertTrue(burnt.active());
		assertEquals(WOLVES, burnt.guild);
		assertEquals("", burnt.lostReason);

		// Forced confirmation (server stop) does not wait; a lost copy is never suspected.
		ledger.suspectLoss(picked.serial, 400, "killed");
		assertEquals(1, ledger.confirmLosses(401, 60, 9500, true).size());
		ledger.suspectLoss(picked.serial, 500, "killed");
		assertFalse(ledger.hasPendingLosses(), "already lost");
	}

	@Test
	void brokenAndFreedCopies() {
		UniqueLedger ledger = ledger();
		UniqueLedger.Copy a = ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		UniqueLedger.Copy b = ledger.issue("foudrelame", "admin", WOLVES, ALEX, "Alex", 0, 0);
		UniqueLedger.Copy c = ledger.issue("croc_de_givre", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		assertTrue(ledger.markLost(c.serial, "broken", 50));
		assertFalse(ledger.markLost(c.serial, "broken", 60), "already lost");
		assertFalse(ledger.markLost(999, "broken", 60), "unknown serial");

		assertEquals(List.of(a), ledger.free("foudrelame", DRAGONS, "admin", 70), "only the Dragons' copy");
		assertTrue(b.active());
		assertEquals(List.of(b), ledger.free("foudrelame", null, "admin", 80));
		assertTrue(ledger.free("foudrelame", null, "admin", 90).isEmpty(), "nothing left to free");
		assertEquals("admin", a.lostReason);
		assertTrue(ledger.activeIds().isEmpty());
	}

	@Test
	void duplicatesAreCountedAndBounded() {
		UniqueLedger ledger = ledger();
		assertTrue(ledger.duplicate(7, "foudrelame", "Steve (inventaire)", "Alex (inventaire)", 1000), "logged the first time");
		assertFalse(ledger.duplicate(7, "foudrelame", "Steve (inventaire)", "Alex (inventaire)", 2000), "not every second");
		assertTrue(ledger.duplicate(7, "foudrelame", "Steve (inventaire)", "Alex (coffre)", 2000 + UniqueLedger.DUPLICATE_LOG_MS));
		assertEquals(1, ledger.duplicates().size());
		UniqueLedger.Duplicate duplicate = ledger.duplicates().getFirst();
		assertEquals(3, duplicate.count);
		assertEquals("Alex (coffre)", duplicate.second);
		for (int i = 0; i < UniqueLedger.MAX_DUPLICATES + 5; i++) {
			ledger.duplicate(100 + i, "croc_de_givre", "a", "b", 5000);
		}
		assertEquals(UniqueLedger.MAX_DUPLICATES, ledger.duplicates().size(), "bounded");
		assertEquals(1, ledger.clearDuplicates(110));
		assertEquals(UniqueLedger.MAX_DUPLICATES - 1, ledger.clearDuplicates(0));
	}

	@Test
	void collectionsCompleteOnceAndDisbandedGuildsAreForgotten() {
		UniqueLedger ledger = ledger();
		List<String> all = List.of("foudrelame", "croc_de_givre");
		ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		assertFalse(ledger.completes(DRAGONS, all));
		UniqueLedger.Copy croc = ledger.issue("croc_de_givre", "boss", DRAGONS, STEVE, "Steve", 0, 0);
		ledger.markLost(croc.serial, "broken", 1);
		assertTrue(ledger.completes(DRAGONS, all), "obtained once is enough, even if lost since");
		assertFalse(ledger.completes(DRAGONS, all), "announced once");
		assertFalse(ledger.completes(WOLVES, List.of()), "no weapon configured: never complete");

		ledger.forgetGuild(DRAGONS);
		assertTrue(ledger.obtainedBy(DRAGONS).isEmpty());
		assertNull(ledger.copiesOf("foudrelame").getFirst().guild, "the copy keeps only its player holder");
		assertEquals(STEVE, ledger.copiesOf("foudrelame").getFirst().player);
	}

	@Test
	void survivesTheJsonRoundTrip() {
		UniqueLedger ledger = ledger();
		UniqueLedger.Copy copy = ledger.issue("foudrelame", "boss", DRAGONS, STEVE, "Steve", 1234, 5);
		ledger.issue("croc_de_givre", "quest", null, null, null, 0, 0);
		ledger.markLost(2, "destroyed", 99);
		ledger.duplicate(1, "foudrelame", "a", "b", 10);
		ledger.completes(DRAGONS, List.of("foudrelame"));
		Gson gson = new Gson();
		String json = gson.toJson(ledger);
		assertFalse(json.contains("seenTick"), "runtime fields are not saved");
		UniqueLedger read = gson.fromJson(json, UniqueLedger.class);
		read.fixNulls();
		assertEquals(2, read.copies().size());
		UniqueLedger.Copy back = read.copy(copy.serial);
		assertNotNull(back);
		assertEquals(DRAGONS, back.guild);
		assertEquals(STEVE, back.player);
		assertEquals("Steve", back.playerName);
		assertEquals(1234, back.issuedAt);
		assertEquals(UniqueLedger.State.LOST, read.copy(2).state);
		assertEquals(3, read.nextSerial());
		assertEquals(Set.of("foudrelame"), read.obtainedBy(DRAGONS));
		assertEquals(1, read.duplicates().size());
		assertFalse(read.completes(DRAGONS, List.of("foudrelame")), "completion remembered");

		// Broken files: missing lists, repeated or invalid serials, stale counter.
		UniqueLedger broken = gson.fromJson("{\"nextSerial\": 1, \"copies\": [{\"serial\": 5, \"id\": \"a\"}, {\"serial\": 5, \"id\": \"b\"},"
				+ " {\"serial\": 0, \"id\": \"c\"}, {\"serial\": 6}, null], \"obtained\": null}", UniqueLedger.class);
		broken.fixNulls();
		assertEquals(1, broken.copies().size());
		assertEquals("a", broken.copy(5).id);
		assertTrue(broken.copy(5).active(), "state defaults to active");
		assertEquals(6, broken.nextSerial());
		assertTrue(broken.obtainedBy(DRAGONS).isEmpty());
	}
}
