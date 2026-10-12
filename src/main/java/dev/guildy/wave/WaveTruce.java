package dev.guildy.wave;

/**
 * Truce after repeated defeats (pure, tested): {@code defeatsInARow} defeats in a row give the guild {@code nights}
 * nights without horde (and without pillager raid when configured). A night counts once, the first time a horde would
 * have started; a victory resets the defeat count.
 */
final class WaveTruce {
	/** Result of {@link #spareTonight}. */
	enum Night {
		/** No truce tonight: the horde may start. */
		NONE,
		/** First check of a spared night (announce it). */
		SPARED_NOW,
		/** Night already spared (later checks of the same night). */
		SPARED
	}

	private WaveTruce() {
	}

	/** A defeat: counts it and starts a truce when the threshold is reached. @return true when a truce starts */
	static boolean onDefeat(WaveConfig.Truce cfg, GuildWaveData data) {
		data.defeatsInRow++;
		if (!cfg.enabled || cfg.nights <= 0 || data.defeatsInRow < cfg.defeatsInARow) {
			return false;
		}
		data.defeatsInRow = 0;
		data.truceNights = cfg.nights;
		return true;
	}

	static void onVictory(GuildWaveData data) {
		data.defeatsInRow = 0;
	}

	/** Called when a night horde could start on this overworld day: consumes one truce night per night. */
	static Night spareTonight(WaveConfig.Truce cfg, GuildWaveData data, long day) {
		if (!cfg.enabled) {
			return Night.NONE;
		}
		if (data.truceDay == day) {
			return Night.SPARED;
		}
		if (data.truceNights <= 0) {
			return Night.NONE;
		}
		data.truceNights--;
		data.truceDay = day;
		return Night.SPARED_NOW;
	}

	/** A truce is running: nights left, or tonight already spared. */
	static boolean active(WaveConfig.Truce cfg, GuildWaveData data, long day) {
		return cfg.enabled && (data.truceNights > 0 || data.truceDay == day);
	}

	/** Nights without horde left, tonight included when it is being spared. */
	static int nightsLeft(GuildWaveData data, long day) {
		return data.truceNights + (data.truceDay == day ? 1 : 0);
	}
}
