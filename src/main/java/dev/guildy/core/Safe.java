package dev.guildy.core;

import dev.guildy.Guildy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Error isolation: a failure in one guild / feature is logged (throttled) and never propagates into the server tick.
 */
public final class Safe {
	private static final long LOG_INTERVAL_MS = 60_000;
	private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();

	private Safe() {
	}

	public static void run(String context, Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			log(context, t);
		}
	}

	public static <T> T get(String context, Supplier<T> action, T fallback) {
		try {
			return action.get();
		} catch (Throwable t) {
			log(context, t);
			return fallback;
		}
	}

	public static void log(String context, Throwable t) {
		if (t instanceof VirtualMachineError vmError) {
			throw vmError;
		}
		long now = System.currentTimeMillis();
		Long last = LAST_LOG.get(context);
		if (last == null || now - last > LOG_INTERVAL_MS) {
			LAST_LOG.put(context, now);
			Guildy.LOGGER.error("[{}] unexpected error (further errors from this context are throttled for 60s)", context, t);
		}
	}
}
