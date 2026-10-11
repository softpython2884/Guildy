package dev.guildy.trade;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-guild trade state (persisted inside the guild): trade hub tier, visiting merchants, visits waiting to arrive.
 */
public class GuildTradeData {
	/** Trade hub tier (1 = just built). Kept when the hub is broken and rebuilt. */
	public int tier = 1;
	/** Overworld day index of the last morning visit roll of the trade hub. */
	public long lastVisitRollDay = -1;
	/** Visits won for a day but not arrived yet (visitor kind -> day index): they arrive once the area is loaded. */
	public Map<String, Long> pending = new LinkedHashMap<>();
	/** Merchants currently at the base. */
	public List<Visitor> visitors = new ArrayList<>();
	/** Merchants that came to the base (statistics). */
	public int visits;

	/** A merchant at the base: a tagged wandering trader (+ its trader llamas), gone at {@link #until}. */
	public static class Visitor {
		/** Visitor kind ("merchant", "caravan", "book_merchant"...). */
		public String kind = "";
		public UUID trader;
		public List<UUID> llamas = new ArrayList<>();
		/** Arrival spot (center of its home area). */
		public BlockPos pos;
		/** Game time of the arrival / of the departure. */
		public long arrivedAt;
		public long until;
		/** Display name ('&' codes) at its arrival. */
		public String name = "";
	}

	public @Nullable Visitor visitor(String kind) {
		for (Visitor visitor : visitors) {
			if (kind.equals(visitor.kind)) {
				return visitor;
			}
		}
		return null;
	}

	/** Called after deserialization. */
	public void fixNulls() {
		tier = Math.max(1, tier);
		if (pending == null) {
			pending = new LinkedHashMap<>();
		}
		pending.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		if (visitors == null) {
			visitors = new ArrayList<>();
		}
		visitors.removeIf(v -> v == null || v.trader == null || v.pos == null || v.kind == null);
		for (Visitor visitor : visitors) {
			if (visitor.llamas == null) {
				visitor.llamas = new ArrayList<>();
			}
			visitor.llamas.removeIf(id -> id == null);
			if (visitor.name == null) {
				visitor.name = "";
			}
		}
	}
}
