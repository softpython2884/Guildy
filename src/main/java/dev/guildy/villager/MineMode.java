package dev.guildy.villager;

import java.util.Locale;

/**
 * How the miners of a MINE zone work. Persisted by name.
 */
public enum MineMode {
	/** Real excavation: a staircase and galleries really dug from the fanion (see {@link RealMiner}). */
	REAL,
	/** Simulated mining at the fanion: loot by depth, no block broken (design decision 13). */
	SIMULATED;

	public String langKey() {
		return "villager.mine.mode." + name().toLowerCase(Locale.ROOT);
	}

	public MineMode next() {
		return this == REAL ? SIMULATED : REAL;
	}
}
