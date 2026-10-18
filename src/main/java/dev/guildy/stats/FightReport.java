package dev.guildy.stats;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The "Bilan du combat" of a finished fight (pure, immutable once built): where and against whom, how it ended, how long
 * it took, and every participant's statistics (copies), sorted by damage to the guardian. {@link #titles} gives the best
 * player of each category, ties sharing the title.
 */
public final class FightReport {
	private static final AtomicLong IDS = new AtomicLong();

	public enum Outcome {
		VICTORY,
		FAILURE
	}

	/**
	 * What the fight was: names already coloured (section signs), the threat label, cursed / risen guardian, its duration
	 * (from the guardian's appearance) and the whole session (room guards included), the room guards posted and killed,
	 * the escort killed and, on a failure, the guardian's health left (ratio, -1 unknown).
	 */
	public record Header(String place, String boss, String threat, boolean cursed, boolean risen, Outcome outcome, long fightTicks,
			long totalTicks, int guardsPosted, int guardsKilled, int escortKilled, double bossHealthLeft) {
	}

	/** One participant: name at the time of the fight and a copy of their statistics. */
	public record Entry(UUID player, String name, FightTally tally) {
	}

	/** A title: its category, the players holding it (several on a tie) and the value. */
	public record Title(FightCategory category, List<Entry> holders, double value) {
	}

	private final long id;
	private final Header header;
	private final List<Entry> entries;
	private final List<UUID> recipients;

	private FightReport(Header header, List<Entry> entries, List<UUID> recipients) {
		this.id = IDS.incrementAndGet();
		this.header = header;
		this.entries = List.copyOf(entries);
		this.recipients = List.copyOf(recipients);
	}

	/**
	 * Builds the summary: the players whose statistics reach {@code minDamage} (see {@link FightTally#qualifies}) are listed;
	 * they and the players of {@code present} (there at the end) receive it.
	 */
	public static FightReport of(Header header, Map<UUID, String> names, Map<UUID, FightTally> tallies, double minDamage, Collection<UUID> present) {
		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<UUID, FightTally> e : tallies.entrySet()) {
			if (e.getValue().qualifies(minDamage)) {
				entries.add(new Entry(e.getKey(), names.getOrDefault(e.getKey(), "?"), e.getValue().copy()));
			}
		}
		entries.sort(ORDER);
		Set<UUID> recipients = new LinkedHashSet<>();
		for (Entry entry : entries) {
			recipients.add(entry.player());
		}
		recipients.addAll(present);
		return new FightReport(header, entries, new ArrayList<>(recipients));
	}

	/** Damage to the guardian first, then all damage dealt, healing, then the name. */
	static final Comparator<Entry> ORDER = Comparator.<Entry>comparingDouble(e -> -e.tally().damage(FightKind.BOSS))
			.thenComparingDouble(e -> -e.tally().damageDealt())
			.thenComparingDouble(e -> -e.tally().healing())
			.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);

	public long id() {
		return id;
	}

	public Header header() {
		return header;
	}

	/** The listed participants, best damage to the guardian first. */
	public List<Entry> entries() {
		return entries;
	}

	/** Who receives the summary (listed participants, then the other players there at the end). */
	public List<UUID> recipients() {
		return recipients;
	}

	public @Nullable Entry entry(UUID player) {
		for (Entry entry : entries) {
			if (entry.player().equals(player)) {
				return entry;
			}
		}
		return null;
	}

	/** 1-based rank of a player in the list, 0 when not listed. */
	public int rank(UUID player) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).player().equals(player)) {
				return i + 1;
			}
		}
		return 0;
	}

	public List<Title> titles(List<FightCategory> categories) {
		return titles(entries, categories);
	}

	/** Titles a player holds, in the order of {@code categories}. */
	public List<FightCategory> titlesOf(UUID player, List<FightCategory> categories) {
		List<FightCategory> held = new ArrayList<>();
		for (Title title : titles(categories)) {
			for (Entry holder : title.holders()) {
				if (holder.player().equals(player)) {
					held.add(title.category());
				}
			}
		}
		return held;
	}

	/**
	 * Best player(s) of each category, in the order given: compared on the value as shown (whole numbers), every player at
	 * the best value shares the title (list order kept); a category where nobody scored is left out.
	 */
	public static List<Title> titles(List<Entry> entries, List<FightCategory> categories) {
		List<Title> titles = new ArrayList<>();
		for (FightCategory category : categories) {
			long best = 0;
			double bestValue = 0;
			for (Entry entry : entries) {
				double value = category.value(entry.tally());
				long shown = FightRules.shown(value);
				if (shown > best) {
					best = shown;
					bestValue = value;
				}
			}
			if (best <= 0) {
				continue;
			}
			List<Entry> holders = new ArrayList<>();
			for (Entry entry : entries) {
				if (FightRules.shown(category.value(entry.tally())) == best) {
					holders.add(entry);
				}
			}
			titles.add(new Title(category, holders, category.count() ? best : bestValue));
		}
		return titles;
	}
}
