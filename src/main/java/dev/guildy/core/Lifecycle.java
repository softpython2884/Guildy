package dev.guildy.core;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ordered server lifecycle hooks for modules (Guildy core guarantees: STARTED runs after guild data is loaded,
 * STOPPING runs before the final save).
 */
public final class Lifecycle {
	/** After configs, lang and guild data are loaded. */
	public static final List<Consumer<MinecraftServer>> STARTED = new ArrayList<>();
	/** Before the final guild data save (clean up mobs, signs, etc.). */
	public static final List<Consumer<MinecraftServer>> STOPPING = new ArrayList<>();
	/** After /guild admin reload reloaded the config files. */
	public static final List<Runnable> RELOADED = new ArrayList<>();

	private Lifecycle() {
	}

	public static void fire(List<Consumer<MinecraftServer>> hooks, MinecraftServer server, String name) {
		for (Consumer<MinecraftServer> hook : hooks) {
			Safe.run("lifecycle." + name, () -> hook.accept(server));
		}
	}
}
