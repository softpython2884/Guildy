package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

/**
 * villagers.json5, section "farmer.irrigation": the water holes dug by the farmers (autorisation "Créer des points
 * d'eau" of a field zone). Free of Minecraft classes.
 */
@Json5.Section
public final class IrrigationConfig {
	@Comment({
			"Seaux d'eau consommes par point d'eau creuse ou rempli (0 a 4 ; 0 = eau gratuite). Plus de 0 : le fermier prend les",
			"seaux d'eau dans les coffres du stockage (depot, categorie, Divers) et rapporte les seaux vides au coffre de depot"
	})
	public int waterBucketCost = 0;
	@Comment("Seaux d'eau pris au stockage en un voyage (1 a 16)")
	public int bucketFetchAmount = 4;
	@Comment("Points d'eau retenus (et remplis s'ils disparaissent) par zone au plus (1 a 4096)")
	public int maxHolesPerZone = 512;

	void validate() {
		waterBucketCost = FieldWorkConfig.clamp(waterBucketCost, 0, 4);
		bucketFetchAmount = FieldWorkConfig.clamp(bucketFetchAmount, 1, 16);
		maxHolesPerZone = FieldWorkConfig.clamp(maxHolesPerZone, 1, 4096);
	}
}
