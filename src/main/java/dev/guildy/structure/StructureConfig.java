package dev.guildy.structure;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Comment({
		"Guildy - constructions multiblocs (murs verticaux, profondeur 1 bloc).",
		"Une construction = id, name, description, tutorial (texte montre a la premiere construction), icon,",
		"minLevel (niveau de guilde requis), unlockCost (or du tresor pour la debloquer, 0 = debloquee d'office),",
		"maxPerGuild, rows (lignes de HAUT en BAS, caracteres de GAUCHE a DROITE vus de face, '.' = n'importe quel bloc),",
		"key (caractere -> bloc 'minecraft:bookshelf' ou tag de blocs '#minecraft:logs'), keyLabels (noms affiches),",
		"signCells : cases [colonne, ligne] (0 = gauche / haut) qui recoivent un panneau gere sur leur face avant ;",
		"l'ordre de la liste = numero du panneau (tableau de quetes : panneau n = quete n).",
		"Les blocs portant un panneau doivent etre des blocs pleins. Taille maximale : 9 de large, 4 de haut.",
		"Les ids 'quest_board', 'shop' et 'reception' sont utilises par les quetes, la boutique et les villageois.",
		"Validation en jeu : clic droit sur un bloc de la construction avec le Plan (menu /guild build)."
})
public final class StructureConfig implements Validatable {

	@Comment("Intervalle (secondes) entre deux verifications de l'integrite des constructions chargees")
	public int checkIntervalSeconds = 30;

	@Comment("Intervalle (secondes) de mise a jour du texte des panneaux geres")
	public int signRefreshSeconds = 5;

	@Comment("Panneau mural pose devant les constructions (id d'un panneau mural vanilla, ex. minecraft:spruce_wall_sign)")
	public String signBlock = "minecraft:oak_wall_sign";

	@Comment("Texte lumineux sur les panneaux geres")
	public boolean glowingSignText = false;

	@Comment("Liste des constructions (voir l'explication en haut du fichier)")
	public List<StructureDefinition> definitions = defaults();

	/** Usable structure types by id (built by validate()). */
	private transient Map<String, StructureType> types = new LinkedHashMap<>();
	private transient Block resolvedSignBlock = Blocks.OAK_WALL_SIGN;

	public Map<String, StructureType> types() {
		return types;
	}

	public Block signBlock() {
		return resolvedSignBlock;
	}

	@Override
	public void validate() {
		checkIntervalSeconds = Math.max(5, Math.min(600, checkIntervalSeconds));
		signRefreshSeconds = Math.max(1, Math.min(60, signRefreshSeconds));
		resolvedSignBlock = Blocks.OAK_WALL_SIGN;
		Identifier signId = signBlock == null ? null : Identifier.tryParse(signBlock);
		if (signId != null && BuiltInRegistries.BLOCK.containsKey(signId)
				&& BuiltInRegistries.BLOCK.getValue(signId) instanceof WallSignBlock wallSign) {
			resolvedSignBlock = wallSign;
		} else {
			Guildy.LOGGER.warn("[structures.json5] signBlock '{}' is not a wall sign, using minecraft:oak_wall_sign", signBlock);
			signBlock = "minecraft:oak_wall_sign";
		}
		if (definitions == null) {
			definitions = defaults();
		}
		definitions.removeIf(d -> d == null);
		Map<String, StructureType> result = new LinkedHashMap<>();
		for (StructureDefinition def : definitions) {
			List<String> errors = def.check();
			Map<Character, BlockKey> keys = new HashMap<>();
			StructurePattern pattern = def.pattern();
			if (errors.isEmpty() && pattern != null) {
				for (char c : pattern.keys()) {
					BlockKey key = BlockKey.parse(def.keyFor(c));
					if (key == null) {
						errors.add("unknown block or invalid tag '" + def.keyFor(c) + "' for '" + c + "'");
					} else {
						keys.put(c, key);
					}
				}
				for (int[] cell : def.signCells) {
					BlockKey key = keys.get(pattern.at(cell[0], cell[1]));
					if (key != null && !key.canHoldSign()) {
						errors.add("sign cell [" + cell[0] + "," + cell[1] + "] uses '" + key.raw() + "' which is not a full solid block");
					}
				}
			}
			if (ItemUtil.item(def.icon) == null) {
				def.icon = "minecraft:paper";
			}
			if (result.containsKey(def.id)) {
				errors.add("duplicate id");
			}
			if (!errors.isEmpty() || pattern == null) {
				// Kept in the file (so the admin can fix it) but not usable in game.
				Guildy.LOGGER.error("[structures.json5] structure '{}' ignored: {}", def.id, String.join("; ", errors));
				continue;
			}
			result.put(def.id, new StructureType(def, pattern, keys));
		}
		types = result;
	}

	// ------------------------------------------------------------------ defaults

	public static List<StructureDefinition> defaults() {
		List<StructureDefinition> list = new ArrayList<>();

		StructureDefinition board = new StructureDefinition();
		board.id = "quest_board";
		board.name = "Tableau de quêtes";
		board.icon = "minecraft:writable_book";
		board.description = new ArrayList<>(List.of(
				"&7Un panneau d'affichage où la guilde reçoit",
				"&7ses quêtes : une quête par panneau (6 au total)."));
		board.tutorial = new ArrayList<>(List.of(
				"&6&lTableau de quêtes construit !",
				"&7Chaque panneau affiche une quête commune à toute la guilde.",
				"&7Clic droit sur un panneau : détails, livraison, récompense.",
				"&7Une nouvelle quête apparaît peu après qu'un panneau se libère.",
				"&7Raccourci : &e/guild quests"));
		board.minLevel = 1;
		board.unlockCost = 0;
		board.rows = new ArrayList<>(List.of("LBL", "LPL"));
		board.key = map("L", "#minecraft:logs", "B", "minecraft:bookshelf", "P", "#minecraft:planks");
		board.keyLabels = map("L", "Bûche (tout bois)", "P", "Planches (tout bois)");
		board.signCells = cells(0, 0, 1, 0, 2, 0, 0, 1, 1, 1, 2, 1);
		list.add(board);

		StructureDefinition shop = new StructureDefinition();
		shop.id = "shop";
		shop.name = "Comptoir de commerce";
		shop.icon = "minecraft:emerald";
		shop.description = new ArrayList<>(List.of(
				"&7Un comptoir où les membres achètent et",
				"&7vendent des marchandises contre de l'or."));
		shop.tutorial = new ArrayList<>(List.of(
				"&6&lComptoir de commerce construit !",
				"&7Clic droit sur son panneau pour ouvrir la boutique.",
				"&7Clic gauche : acheter, clic droit : vendre (maj : par lots).",
				"&7On paie avec les lingots d'or de son inventaire."));
		shop.minLevel = 1;
		shop.unlockCost = 20;
		shop.rows = new ArrayList<>(List.of("PGP", "BBB"));
		shop.key = map("P", "#minecraft:planks", "G", "minecraft:gold_block", "B", "minecraft:barrel");
		shop.keyLabels = map("P", "Planches (tout bois)");
		shop.signCells = cells(1, 0);
		list.add(shop);

		StructureDefinition reception = new StructureDefinition();
		reception.id = "reception";
		reception.name = "Bureau d'accueil";
		reception.icon = "minecraft:bell";
		reception.description = new ArrayList<>(List.of(
				"&7Un bureau où les villageois de passage",
				"&7peuvent proposer leurs services à la guilde."));
		reception.tutorial = new ArrayList<>(List.of(
				"&6&lBureau d'accueil construit !",
				"&7Des villageois viendront proposer leurs services.",
				"&7Clic droit sur le panneau pour les gérer."));
		reception.minLevel = 2;
		reception.unlockCost = 40;
		reception.rows = new ArrayList<>(List.of("SHS", "SCS"));
		reception.key = map("S", "minecraft:stone_bricks", "H", "minecraft:hay_block", "C", "minecraft:crafting_table");
		reception.signCells = cells(1, 0);
		list.add(reception);
		return list;
	}

	private static Map<String, String> map(String... kv) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static List<int[]> cells(int... colRow) {
		List<int[]> list = new ArrayList<>();
		for (int i = 0; i + 1 < colRow.length; i += 2) {
			list.add(new int[] {colRow[i], colRow[i + 1]});
		}
		return list;
	}
}
