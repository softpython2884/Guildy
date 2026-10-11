package dev.guildy.unique;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

/**
 * The only class touching Polymer's API (compileOnly dependency): it is loaded only when the polymer-resource-pack mod is
 * present ({@link UniqueVisuals}).
 */
final class PolymerBridge {
	private PolymerBridge() {
	}

	/** Adds the mod's assets/ (unique weapon models and textures) to Polymer's generated resource pack. */
	static boolean addAssets(String modId) {
		return PolymerResourcePackUtils.addModAssets(modId);
	}

	/** Players must accept the pack (otherwise they would see missing textures on the uniques). */
	static void markRequired() {
		PolymerResourcePackUtils.markAsRequired();
	}
}
