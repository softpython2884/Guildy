package dev.guildy.unique;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Worn look of the unique armors. The optional resource pack ships one equipment asset per unique armor piece
 * ({@code assets/guildy/equipment/<model>.json}, drawn with {@code textures/entity/equipment/<layer>/<model>.png});
 * when the pack is guaranteed (same rule as the item models, {@link UniqueVisuals}) the piece's {@code equippable}
 * component points to it ({@code asset_id guildy:<model>}). Pure logic: which uniques get an asset, and where it lives.
 */
public final class UniqueEquipment {
	/** Equipment assets of the pack: asset name (= the model of the unique) -> armor slot it is drawn for. */
	public static final Map<String, String> ASSETS = Map.of(
			"couronne_du_roi_de_givre", "head",
			"plastron_du_serpent", "chest",
			"bottes_de_zephyr", "feet",
			"bottes_du_coursier", "feet");

	private UniqueEquipment() {
	}

	/**
	 * Equipment asset of a unique (path in the guildy namespace), or null for the look of its base item: the model of the
	 * unique has a worn texture in the pack, drawn for the armor slot of its base item (a crown asset on boots would draw
	 * nothing, so it is not used).
	 */
	public static @Nullable String assetFor(@Nullable String model, @Nullable String slot) {
		if (model == null || slot == null) {
			return null;
		}
		String asset = model.trim().toLowerCase(Locale.ROOT);
		return slot.equals(ASSETS.get(asset)) ? asset : null;
	}

	/** Model layer an armor slot is drawn with: the leggings use the inner layer, the other pieces the outer one. */
	public static String layer(String slot) {
		return "legs".equals(slot) ? "humanoid_leggings" : "humanoid";
	}

	/** Path of the equipment asset in the pack (from the pack root). */
	public static String definitionPath(String asset) {
		return "assets/guildy/equipment/" + asset + ".json";
	}

	/** Path of the texture of the asset's layer in the pack (from the pack root). */
	public static String texturePath(String asset, String slot) {
		return "assets/guildy/textures/entity/equipment/" + layer(slot) + "/" + asset + ".png";
	}
}
