package dev.guildy.ruin;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ruin data of one guild (persisted in ruins.json, keyed by guild id): the running cartographer research and the ruins
 * the guild knows (located by its cartographers or defeated by its members).
 */
public class GuildRuinData {
	public @Nullable Research research;
	/** Keys of the known ruins (see {@link RuinRecord#key}), oldest first. */
	public List<String> known = new ArrayList<>();

	public GuildRuinData() {
	}

	public void addKnown(String key) {
		if (key != null && !key.isEmpty() && !known.contains(key)) {
			known.add(key);
		}
	}

	void fixNulls() {
		if (known == null) {
			known = new ArrayList<>();
		}
		known.removeIf(k -> k == null || k.isEmpty());
		if (research != null) {
			research.fixNulls();
		}
	}

	/** A cartographer research: paid, worked on by the cartographers, then located and handed out by "Récupérer". */
	public static class Research {
		public enum Status {
			/** Cartographers are working on it. */
			RUNNING,
			/** A ruin was found: the map / compass is waiting for "Récupérer". */
			READY,
			/** Nothing found: "Récupérer" refunds the cost. */
			FAILED
		}

		public boolean nether;
		/** "La legende du Jaguar celeste": a statue trail to the grand temple instead of a map of the ruin (false in older saves). */
		public boolean legend;
		public Status status = Status.RUNNING;
		/** Seconds of work done / needed. */
		public double progress;
		public double required = 480;
		public @Nullable UUID startedBy;
		public String startedByName = "";
		public long startedAt;
		/** Items taken from the player (refunded if nothing is found or the research is cancelled). */
		public List<ItemStack> paidItems = new ArrayList<>();
		public int paidGold;
		/** Result (READY): ruin key. */
		public String ruinKey = "";
		/** Result of a legend research (READY): the statue trail (0 = none yet). */
		public int trailId;

		public Research() {
		}

		void fixNulls() {
			if (status == null) {
				status = Status.RUNNING;
			}
			if (startedByName == null) {
				startedByName = "";
			}
			if (paidItems == null) {
				paidItems = new ArrayList<>();
			}
			paidItems.removeIf(s -> s == null || s.isEmpty());
			if (ruinKey == null) {
				ruinKey = "";
			}
		}
	}
}
