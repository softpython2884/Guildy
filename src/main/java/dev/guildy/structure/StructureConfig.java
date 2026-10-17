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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Comment({
		"Guildy - constructions multiblocs (murs verticaux, profondeur 1 bloc).",
		"Une construction = id, name, description, tutorial (texte montre a la premiere construction), icon,",
		"minLevel (niveau de guilde requis), unlockCost (or du tresor pour la debloquer, 0 = debloquee d'office),",
		"maxPerGuild, rows (lignes de HAUT en BAS, caracteres de GAUCHE a DROITE vus de face, '.' = n'importe quel bloc),",
		"key (caractere -> bloc 'minecraft:bookshelf', tag de blocs '#minecraft:logs' ou '*' = n'importe quel bloc plein),",
		"keyLabels (noms affiches),",
		"signCells : cases [colonne, ligne] (0 = gauche / haut) qui recoivent un panneau gere sur leur face avant ;",
		"l'ordre de la liste = numero du panneau (tableau de quetes : panneau n = quete n).",
		"Les blocs portant un panneau doivent etre des blocs pleins. Taille maximale : 9 de large, 4 de haut.",
		"Les ids 'quest_board', 'shop', 'reception', 'trade_hub' et 'village_sign' sont utilises par les quetes, la boutique,",
		"les villageois, le commerce (Carrefour commercial) et le panneau du village (raccourci vers les menus).",
		"Validation en jeu : clic droit sur un bloc de la construction avec le Plan (menu /guild build)."
})
public final class StructureConfig implements Validatable {
	/** Defaults that existed before {@link #knownDefaults}: never re-added (an admin may have removed them on purpose). */
	private static final Set<String> ORIGINAL_DEFAULTS = Set.of("quest_board", "shop", "reception");

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

	@Comment({
			"Constructions par defaut deja proposees. Une construction ajoutee par une mise a jour du mod est ajoutee",
			"automatiquement a 'definitions' une seule fois ; pour ne pas l'utiliser, retirez-la de 'definitions' (pas d'ici)."
	})
	public List<String> knownDefaults = new ArrayList<>();

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
		if (knownDefaults == null) {
			knownDefaults = new ArrayList<>();
		}
		for (String added : mergeNewDefaults(definitions, knownDefaults)) {
			Guildy.LOGGER.info("[structures.json5] new default structure '{}' added", added);
		}
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

	/**
	 * Existing files keep their own 'definitions' list: default structures added by a mod update (not yet in
	 * {@code known}) are appended once, then remembered in {@code known} (pure, unit-tested).
	 *
	 * @return ids appended to {@code definitions}
	 */
	static List<String> mergeNewDefaults(List<StructureDefinition> definitions, List<String> known) {
		List<String> added = new ArrayList<>();
		Set<String> present = new HashSet<>();
		for (StructureDefinition def : definitions) {
			present.add(def.id);
		}
		for (StructureDefinition def : defaults()) {
			if (known.contains(def.id)) {
				continue;
			}
			known.add(def.id);
			if (!present.contains(def.id) && !ORIGINAL_DEFAULTS.contains(def.id)) {
				definitions.add(def);
				added.add(def.id);
			}
		}
		return added;
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

		StructureDefinition hub = new StructureDefinition();
		hub.id = "trade_hub";
		hub.name = "Carrefour commercial";
		hub.icon = "minecraft:emerald_block";
		hub.description = new ArrayList<>(List.of(
				"&7Un carrefour où des marchands de passage",
				"&7viennent vendre des marchandises rares.",
				"&7Plus il est développé, plus les échanges sont fous."));
		hub.tutorial = new ArrayList<>(List.of(
				"&6&lCarrefour commercial construit !",
				"&7Certains matins, un &eMarchand de passage&7 s'y arrête quelques minutes.",
				"&7Clic droit sur son panneau : palier, marchands présents, échanges possibles.",
				"&7Améliorez le Carrefour pour attirer de meilleurs marchands.",
				"&7Raccourci : &e/guild trade"));
		hub.minLevel = 2;
		hub.unlockCost = 60;
		hub.rows = new ArrayList<>(List.of("WEW", "BTB"));
		hub.key = map("W", "#minecraft:wool", "E", "minecraft:emerald_block", "B", "minecraft:barrel", "T", "minecraft:cartography_table");
		hub.keyLabels = map("W", "Laine (toute couleur)");
		hub.signCells = cells(1, 0);
		list.add(hub);

		StructureDefinition sign = new StructureDefinition();
		sign.id = "village_sign";
		sign.name = "Panneau du village";
		sign.icon = "minecraft:oak_sign";
		sign.description = new ArrayList<>(List.of(
				"&7Un simple panneau au nom de la guilde,",
				"&7posé sur n'importe quel bloc plein.",
				"&7Clic droit : constructions ; accroupi : menu de guilde."));
		sign.tutorial = new ArrayList<>(List.of(
				"&6&lPanneau du village posé !",
				"&7Il affiche le nom et le niveau de la guilde.",
				"&7Clic droit dessus : menu des constructions.",
				"&7Accroupi + clic droit : menu principal de la guilde (comme &e/guild&7)."));
		sign.minLevel = 1;
		sign.unlockCost = 0;
		sign.maxPerGuild = 4;
		sign.rows = new ArrayList<>(List.of("X"));
		sign.key = map("X", BlockKey.ANY_FULL_BLOCK);
		sign.keyLabels = map("X", "Bloc plein (n'importe lequel)");
		sign.signCells = cells(0, 0);
		list.add(sign);

		StructureDefinition atelier = new StructureDefinition();
		atelier.id = "cartographer";
		atelier.name = "Atelier du cartographe";
		atelier.icon = "minecraft:cartography_table";
		atelier.description = new ArrayList<>(List.of(
				"&7Un atelier où les cartographes de la guilde",
				"&7localisent les ruines gardées par des boss,",
				"&7à la surface comme dans le Nether."));
		atelier.tutorial = new ArrayList<>(List.of(
				"&6&lAtelier du cartographe construit !",
				"&7Donnez la tâche &eCartographe&7 à un villageois (menu des villageois).",
				"&7Clic droit sur le panneau : lancer une recherche (papier + boussole).",
				"&7Le cartographe y travaille le jour ; ensuite, récupérez une &ecarte",
				"&7(surface) ou une &eboussole&7 (Nether) qui mène à une ruine.",
				"&7Un boss y dort : vainquez-le pour son trésor !"));
		atelier.minLevel = 5;
		atelier.unlockCost = 80;
		atelier.rows = new ArrayList<>(List.of("BLB", "PCP"));
		atelier.key = map("B", "minecraft:bookshelf", "L", "minecraft:lectern", "P", "#minecraft:planks", "C", "minecraft:cartography_table");
		atelier.keyLabels = map("P", "Planches (tout bois)");
		atelier.signCells = cells(0, 0);
		list.add(atelier);
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
