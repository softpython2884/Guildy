package dev.guildy.shop;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;

import java.util.ArrayList;
import java.util.List;

@Comment({
		"Guildy - boutique du comptoir de commerce (paiement en lingots d'or de l'inventaire : 1 or = 1 lingot).",
		"categories : [{id, name, icon, entries}] ; entries : [{item, amount (taille du lot), buy (prix d'achat du lot",
		"par le joueur, 0 = non vendu), sell (prix de rachat du lot au joueur, 0 = non rachete), minLevel (niveau de guilde)}].",
		"Regles appliquees au chargement : sell <= buy (sinon ramene a buy), un objet ne peut pas etre rachete plus cher",
		"qu'il n'est vendu ailleurs dans la boutique, les objets en or (lingot, pepite, bloc, or brut, minerais d'or) et",
		"les objets inconnus sont refuses. Attention aux recettes : ne rachetez pas un objet fabrique plus cher que ses",
		"ingredients vendus (ex. lingots de fer vendus / blocs de fer rachetes)."
})
public final class ShopConfig implements Validatable {

	@Comment("La boutique ne s'ouvre que pres d'un comptoir de commerce de la guilde (sinon : partout via le menu)")
	public boolean requireCounter = true;

	@Comment("Distance maximale (blocs) entre le joueur et le panneau du comptoir pour ouvrir la boutique depuis le menu")
	public int counterDistance = 8;

	@Comment("Categories et articles (voir l'explication en haut du fichier)")
	public List<ShopCategory> categories = defaults();

	private transient List<ShopCategory> usable = new ArrayList<>();

	/** Validated categories (only usable entries). */
	public List<ShopCategory> usable() {
		return usable;
	}

	@Override
	public void validate() {
		counterDistance = Math.max(2, Math.min(64, counterDistance));
		if (categories == null) {
			categories = defaults();
		}
		List<String> warnings = new ArrayList<>();
		usable = ShopValidator.validate(categories, id -> ItemUtil.item(id) != null, warnings);
		for (String warning : warnings) {
			Guildy.LOGGER.warn("[shop.json5] {}", warning);
		}
	}

	// ------------------------------------------------------------------ defaults

	public static List<ShopCategory> defaults() {
		List<ShopCategory> list = new ArrayList<>();
		list.add(new ShopCategory("blocs", "Blocs de construction", "minecraft:bricks", entries(
				e("minecraft:cobblestone", 64, 3, 1, 1),
				e("minecraft:stone", 32, 4, 1, 1),
				e("minecraft:stone_bricks", 32, 6, 1, 1),
				e("minecraft:oak_log", 16, 4, 1, 1),
				e("minecraft:spruce_log", 16, 4, 1, 1),
				e("minecraft:oak_planks", 64, 5, 0, 1),
				e("minecraft:dirt", 64, 2, 0, 1),
				e("minecraft:sand", 32, 3, 1, 1),
				e("minecraft:glass", 16, 4, 1, 1),
				e("minecraft:white_wool", 16, 6, 0, 1),
				e("minecraft:bricks", 16, 8, 2, 2),
				e("minecraft:quartz_block", 16, 16, 0, 3))));
		list.add(new ShopCategory("cultures", "Cultures et nourriture", "minecraft:wheat", entries(
				e("minecraft:wheat", 16, 3, 1, 1),
				e("minecraft:wheat_seeds", 16, 2, 0, 1),
				e("minecraft:carrot", 16, 3, 1, 1),
				e("minecraft:potato", 16, 3, 1, 1),
				e("minecraft:beetroot", 16, 3, 1, 1),
				e("minecraft:sugar_cane", 16, 3, 1, 1),
				e("minecraft:pumpkin", 8, 4, 1, 1),
				e("minecraft:melon_slice", 32, 3, 1, 1),
				e("minecraft:apple", 8, 4, 1, 1),
				e("minecraft:egg", 16, 3, 1, 1),
				e("minecraft:bread", 8, 4, 1, 1),
				e("minecraft:cooked_beef", 8, 6, 2, 1),
				e("minecraft:oak_sapling", 8, 3, 0, 1))));
		list.add(new ShopCategory("butin", "Butin de monstres", "minecraft:bone", entries(
				e("minecraft:rotten_flesh", 32, 0, 1, 1),
				e("minecraft:bone", 16, 4, 1, 1),
				e("minecraft:string", 16, 4, 1, 1),
				e("minecraft:spider_eye", 8, 4, 1, 1),
				e("minecraft:feather", 16, 3, 1, 1),
				e("minecraft:leather", 8, 6, 2, 1),
				e("minecraft:gunpowder", 8, 6, 2, 1),
				e("minecraft:arrow", 16, 3, 0, 1),
				e("minecraft:slime_ball", 8, 8, 2, 2),
				e("minecraft:ender_pearl", 4, 16, 4, 2),
				e("minecraft:blaze_rod", 4, 16, 4, 3))));
		list.add(new ShopCategory("minerais", "Minerais et lingots", "minecraft:iron_ingot", entries(
				e("minecraft:coal", 16, 4, 1, 1),
				e("minecraft:copper_ingot", 16, 6, 2, 1),
				e("minecraft:iron_ingot", 8, 10, 4, 1),
				e("minecraft:redstone", 16, 6, 2, 1),
				e("minecraft:lapis_lazuli", 16, 6, 2, 1),
				e("minecraft:quartz", 16, 6, 2, 2),
				e("minecraft:amethyst_shard", 8, 6, 1, 2),
				e("minecraft:emerald", 4, 12, 4, 2),
				e("minecraft:diamond", 1, 20, 8, 3))));
		list.add(new ShopCategory("utilitaires", "Utilitaires", "minecraft:torch", entries(
				e("minecraft:torch", 16, 3, 0, 1),
				e("minecraft:chest", 4, 4, 0, 1),
				e("minecraft:white_bed", 1, 6, 0, 1),
				e("minecraft:bucket", 1, 6, 0, 1),
				e("minecraft:shears", 1, 4, 0, 1),
				e("minecraft:shield", 1, 6, 0, 1),
				e("minecraft:compass", 1, 8, 0, 1),
				e("minecraft:map", 1, 6, 0, 1),
				e("minecraft:lead", 2, 6, 0, 2),
				e("minecraft:name_tag", 1, 16, 0, 3),
				e("minecraft:saddle", 1, 20, 0, 3),
				e("minecraft:experience_bottle", 4, 12, 0, 4),
				e("minecraft:totem_of_undying", 1, 96, 0, 6))));
		return list;
	}

	private static ShopEntry e(String item, int amount, int buy, int sell, int minLevel) {
		return new ShopEntry(item, amount, buy, sell, minLevel);
	}

	private static List<ShopEntry> entries(ShopEntry... entries) {
		return new ArrayList<>(List.of(entries));
	}
}
