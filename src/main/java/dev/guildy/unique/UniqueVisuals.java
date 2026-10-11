package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.core.Safe;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional custom models. The item_model component is only set when a resource pack is guaranteed: the Polymer resource
 * pack mod is installed (Guildy's assets are added to its generated pack) or the admin forces it ("on": the zip built by
 * {@code gradlew resourcePackZip} is served with {@code resource-pack=} in server.properties). Otherwise the uniques keep
 * the look of their vanilla base item.
 */
public final class UniqueVisuals {
	/** Mod id of Polymer's resource pack module (optional dependency). */
	public static final String POLYMER_MOD = "polymer-resource-pack";
	private static final boolean POLYMER = FabricLoader.getInstance().isModLoaded(POLYMER_MOD);
	private static boolean markedRequired;

	private UniqueVisuals() {
	}

	/** Mod init: registers Guildy's assets in Polymer's pack when Polymer is there. */
	static void init() {
		if (POLYMER) {
			Safe.run("unique.polymer", () -> {
				if (PolymerBridge.addAssets(Guildy.MOD_ID)) {
					Guildy.LOGGER.info("[uniques] Polymer detected: unique weapon models added to its resource pack");
				}
			});
		}
	}

	/** After each config load: makes Polymer's pack required when the custom models rely on it. */
	static void onConfigLoaded(UniqueConfig config) {
		if (POLYMER && !markedRequired && config.polymerRequired && enabled(config)) {
			markedRequired = true;
			Safe.run("unique.polymer", PolymerBridge::markRequired);
		}
	}

	public static boolean polymerPresent() {
		return POLYMER;
	}

	/** Custom models active (item_model set on the uniques). */
	public static boolean enabled() {
		return enabled(UniqueModule.config());
	}

	static boolean enabled(UniqueConfig config) {
		String mode = config.modelMode();
		return "on".equals(mode) || "auto".equals(mode) && POLYMER;
	}
}
