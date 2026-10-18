package dev.guildy.config;

import dev.guildy.data.GuildPermission;
import dev.guildy.data.GuildRole;
import dev.guildy.level.LevelCurve;
import dev.guildy.level.LevelRewardRules;
import dev.guildy.level.LevelScaling;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Comment({
		"Guildy - configuration principale (guildes, territoire, protection, economie, niveaux).",
		"Les autres fichiers de ce dossier configurent les vagues, quetes, boutique, constructions et villageois.",
		"Rechargement a chaud : /guild admin reload   (les commentaires sont regeneres automatiquement)"
})
public final class GuildyConfig implements Validatable {
	/** Current file format; older files get their untouched old defaults migrated in {@link #validate()}. */
	public static final int CURRENT_VERSION = 3;

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Regles des guildes")
	public Guilds guilds = new Guilds();

	@Comment("Territoire, drapeaux et claims (en chunks de 16x16)")
	public Territory territory = new Territory();

	@Comment("Protection des territoires")
	public Protection protection = new Protection();

	@Comment("Economie : l'or (lingot vanilla) est la monnaie. 1 or = 1 lingot d'or.")
	public Economy economy = new Economy();

	@Comment("Niveaux de guilde (XP gagnee via vagues et quetes)")
	public Levels levels = new Levels();

	@Comment("Banniere / drapeau de guilde et affichage sur les cartes")
	public Banners banners = new Banners();

	@Comment({
			"Role minimum (MEMBER, OFFICER, LEADER) requis pour chaque action de guilde.",
			"BUILD=casser/poser, INTERACT=portes/coffres/entites, INVITE, KICK, CLAIM=claims/drapeaux,",
			"TREASURY=retirer/depenser le tresor, VILLAGERS=gerer les villageois, STRUCTURES=constructions,",
			"BANNER=editer la banniere, SET_HOME, QUESTS=abandonner/relancer des quetes"
	})
	public Map<String, String> rolePermissions = defaultRolePermissions();

	@Comment("Divers")
	public General general = new General();

	@Json5.Section
	public static final class Guilds {
		@Comment("Longueur minimale / maximale du nom de guilde")
		public int nameMinLength = 3;
		public int nameMaxLength = 16;
		@Comment("Nombre maximum de membres par guilde (0 = illimite)")
		public int maxMembers = 20;
		@Comment("Duree de validite d'une invitation (secondes)")
		public int inviteExpireSeconds = 300;
		@Comment("Activer /guild home")
		public boolean homeEnabled = true;
		@Comment("Delai entre deux /guild home (secondes)")
		public int homeCooldownSeconds = 60;
		@Comment("Temps d'immobilite avant la teleportation (secondes, 0 = instantane)")
		public int homeWarmupSeconds = 3;
	}

	@Json5.Section
	public static final class Territory {
		@Comment("Dimension ou les territoires peuvent exister")
		public String dimension = "minecraft:overworld";
		@Comment("Rayon (en chunks) claim par le drapeau fondateur : 1 => carre 3x3 = 9 chunks")
		public int foundingRadius = 1;
		@Comment("Rayon claim par un drapeau d'extension : 0 => 1 chunk, 1 => 3x3")
		public int extensionRadius = 0;
		@Comment("Les nouveaux chunks doivent toucher (4 voisins) le territoire existant")
		public boolean requireConnected = true;
		@Comment("Ecart minimum (en chunks) entre les territoires de deux guildes differentes")
		public int minDistanceBetweenGuilds = 3;
		@Comment("Distance minimale (en chunks) entre un territoire et le spawn du monde")
		public int minDistanceFromSpawn = 4;
		@Comment("Nombre maximum de chunks au niveau 1")
		public int baseMaxChunks = 12;
		@Comment("Chunks supplementaires autorises par niveau de guilde, jusqu'au niveau 10")
		public int maxChunksPerLevel = 6;
		@Comment({
				"Au-dela du niveau 10 : chunks supplementaires par niveau, au plus maxChunksAbove10 en tout pour ces niveaux",
				"(0 = sans plafond). Par defaut : 66 chunks au niveau 10, 156 au niveau 100, 256 au plus"
		})
		public int maxChunksPerLevelAbove10 = 1;
		public int maxChunksAbove10 = 190;
		@Comment("Prix (or, paye par le tresor) du premier drapeau d'extension")
		public int extensionFlagBasePrice = 8;
		@Comment("Augmentation du prix pour chaque drapeau d'extension deja achete")
		public int extensionFlagPriceStep = 4;
		@Comment("Prix maximum d'un drapeau d'extension")
		public int extensionFlagMaxPrice = 64;
		@Comment("Afficher un titre (true) ou seulement la barre d'action (false) en entrant dans un territoire")
		public boolean enterTitle = true;
		@Comment("Duree d'affichage des limites du territoire avec /guild show (secondes)")
		public int borderDisplaySeconds = 45;
	}

	@Json5.Section
	public static final class Protection {
		@Comment("Empecher les explosions de detruire des blocs dans les territoires")
		public boolean explosions = true;
		@Comment("Empecher le feu de detruire des blocs dans les territoires")
		public boolean fire = true;
		@Comment("Empecher les pistons de deplacer des blocs a travers une frontiere de territoire")
		public boolean pistons = true;
		@Comment("Empecher les liquides venant de l'exterieur de couler dans un territoire")
		public boolean fluids = true;
		@Comment("Proteger les entites (animaux, villageois, cadres, supports d'armure) contre les non-membres")
		public boolean entities = true;
		@Comment("Les non-membres peuvent ouvrir portes/portillons/trappes dans un territoire")
		public boolean visitorsCanUseDoors = false;
	}

	@Json5.Section
	public static final class Economy {
		@Comment("Prix (or du tresor) d'une banniere decorative aux couleurs de la guilde")
		public int decorativeBannerPrice = 2;
		@Comment("Or dans le tresor a la creation d'une guilde")
		public int startingTreasury = 0;
		@Comment("Accepter les pepites (9 = 1 or) lors des depots au tresor")
		public boolean depositNuggets = true;
	}

	@Json5.Section
	public static final class Levels {
		@Comment("Niveau maximum des guildes (10 a 500)")
		public int maxLevel = 100;
		@Comment({
				"XP totale requise pour atteindre chaque niveau de la liste (index 0 = niveau 2, etc.) ;",
				"les niveaux suivants, jusqu'a maxLevel, suivent la formule xpStepBase / xpStepGrowth"
		})
		public List<Integer> xpThresholds = new ArrayList<>(List.of(100, 250, 450, 700, 1000, 1400, 1900, 2500, 3200));
		@Comment({
				"Au-dela de la liste : XP pour passer du niveau N-1 au niveau N = xpStepBase + xpStepGrowth x (N - dernier niveau",
				"de la liste). Par defaut (liste jusqu'au niveau 10) : 1340 XP pour le niveau 11, 2600 pour le 20, 6800 pour le 50,",
				"13800 pour le 100 ; XP totale : 3200 au niveau 10, 22900 au 20, 166000 au 50, 684500 au 100"
		})
		public int xpStepBase = 1200;
		public int xpStepGrowth = 140;
		@Comment("Recompenses des niveaux gagnes au-dela du niveau 10")
		public LevelRewards rewards = new LevelRewards();
		@Comment({
				"Rangs de guilde : le rang dont 'level' est le plus haut niveau atteint s'affiche dans /guild info, le menu",
				"principal et l'annonce de niveau (name : nom affiche, codes couleur &)"
		})
		public List<Rank> ranks = defaultRanks();
	}

	@Json5.Section
	public static final class LevelRewards {
		@Comment("Activer les recompenses des niveaux au-dela du niveau 10 (or, paliers, drapeaux, places de villageois)")
		public boolean enabled = true;
		@Comment("Or verse au tresor a chaque niveau gagne au-dela du niveau 10 = min(goldMax, goldBase + goldPerLevel x (niveau - 10))")
		public int goldBase = 20;
		public double goldPerLevel = 1.0;
		public int goldMax = 120;
		@Comment("Paliers : tous les 'milestoneEvery' niveaux au-dela du niveau 10 (5 = niveaux 15, 20, 25...) ; 0 = aucun palier")
		public int milestoneEvery = 5;
		@Comment("Or verse au tresor en plus a chaque palier")
		public int milestoneGold = 100;
		@Comment("Drapeaux d'extension offerts a chaque palier (pris gratuitement dans le menu Territoire ou avec /guild flag buy)")
		public int milestoneFlags = 1;
		@Comment("Annoncer les paliers (et les nouveaux rangs) a tout le serveur")
		public boolean announceMilestones = true;
		@Comment({
				"Places de villageois en plus : une tous les 'villagerSlotEvery' niveaux au-dela du niveau 10 (10 = niveaux 20, 30...),",
				"au plus maxVillagerSlots en tout (0 = aucune)"
		})
		public int villagerSlotEvery = 10;
		public int maxVillagerSlots = 10;
	}

	/** One guild rank (plain JSON object in the file). */
	public static final class Rank {
		public int level = 1;
		public String name = "";

		public Rank() {
		}

		public Rank(int level, String name) {
			this.level = level;
			this.name = name;
		}
	}

	public static List<Rank> defaultRanks() {
		return new ArrayList<>(List.of(
				new Rank(1, "&7Novice"),
				new Rank(10, "&6Bronze"),
				new Rank(20, "&fArgent"),
				new Rank(30, "&eOr"),
				new Rank(40, "&3Platine"),
				new Rank(50, "&bDiamant"),
				new Rank(75, "&aÉmeraude"),
				new Rank(100, "&d&lLégendaire"),
				new Rank(150, "&5&lMythique"),
				new Rank(250, "&c&lImmortelle"),
				new Rank(400, "&6&lDivine")));
	}

	@Json5.Section
	public static final class Banners {
		@Comment({
				"false : la banniere se cree au METIER A TISSER avec de vrais colorants (on tisse le drapeau fondateur,",
				"ou n'importe quelle banniere puis 'Adopter la banniere en main' dans le menu Banniere).",
				"true : editeur gratuit dans le menu en plus (couleurs et motifs sans ressources)."
		})
		public boolean freeEditor = false;
		@Comment("Afficher le drapeau-coeur des guildes sur les cartes (marqueur de banniere avec le nom de la guilde)")
		public boolean heartOnMaps = true;
		@Comment("true : toutes les guildes apparaissent sur les cartes ; false : seulement sa propre guilde")
		public boolean allGuildsOnMaps = true;
	}

	@Json5.Section
	public static final class General {
		@Comment("Sauvegarde automatique des donnees de guildes (minutes, si modifiees)")
		public int autosaveMinutes = 5;
		@Comment("Niveau d'operateur vanilla requis pour les commandes admin si aucun gestionnaire de permissions n'est installe")
		public int adminOpLevel = 2;
	}

	public static Map<String, String> defaultRolePermissions() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put(GuildPermission.BUILD.name(), GuildRole.MEMBER.name());
		map.put(GuildPermission.INTERACT.name(), GuildRole.MEMBER.name());
		map.put(GuildPermission.INVITE.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.KICK.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.CLAIM.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.TREASURY.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.VILLAGERS.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.STRUCTURES.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.BANNER.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.SET_HOME.name(), GuildRole.OFFICER.name());
		map.put(GuildPermission.QUESTS.name(), GuildRole.OFFICER.name());
		return map;
	}

	/** Minimum role for a permission (falls back to defaults on bad values). */
	public GuildRole minRole(GuildPermission permission) {
		String value = rolePermissions.get(permission.name());
		GuildRole fallback = GuildRole.parse(defaultRolePermissions().get(permission.name()), GuildRole.OFFICER);
		return GuildRole.parse(value, fallback);
	}

	/** Max chunks of a guild level (before the upgrade tree): unchanged up to level 10, slower and capped past it. */
	public int maxChunks(int level) {
		double bonus = LevelScaling.bonus(level, territory.maxChunksPerLevel, territory.maxChunksPerLevelAbove10, territory.maxChunksAbove10);
		return (int) Math.min(Integer.MAX_VALUE, territory.baseMaxChunks + (long) Math.floor(bonus + 1e-9));
	}

	public int maxLevel() {
		return levels.maxLevel;
	}

	/** Total XP needed to reach {@code level} (level 1 = 0). */
	public long xpForLevel(int level) {
		return LevelCurve.xpForLevel(level, levels.xpThresholds, levels.xpStepBase, levels.xpStepGrowth);
	}

	/** Rank of a guild level (null when no rank applies). */
	public @Nullable Rank rank(int level) {
		return LevelRewardRules.rank(levels.ranks, level);
	}

	/** Extra villager places earned with the guild level (level rewards). */
	public int levelVillagerSlots(int level) {
		return levels.rewards.enabled ? LevelRewardRules.villagerSlots(levels.rewards, level) : 0;
	}

	public int extensionFlagPrice(int alreadyBought) {
		long price = (long) territory.extensionFlagBasePrice + (long) territory.extensionFlagPriceStep * alreadyBought;
		return (int) Math.min(price, territory.extensionFlagMaxPrice);
	}

	@Override
	public void validate() {
		if (levels == null) {
			levels = new Levels();
		}
		if (levels.xpThresholds == null) {
			levels.xpThresholds = new ArrayList<>();
		}
		levels.xpThresholds.removeIf(v -> v == null || v < 0);
		if (levels.xpThresholds.isEmpty()) {
			levels.xpThresholds = new ArrayList<>(new Levels().xpThresholds);
		}
		levels.xpThresholds.sort(Integer::compareTo);
		// Migrations: only values still equal to the old default change (admin edits are kept).
		if (configVersion < 2 && territory != null && territory.borderDisplaySeconds == 15) {
			territory.borderDisplaySeconds = 45;
		}
		if (configVersion < 3 && levels.xpThresholds.size() + 1 > levels.maxLevel) {
			// v3: the maximum level is no longer the length of the list; a list edited past the new default keeps its levels.
			levels.maxLevel = levels.xpThresholds.size() + 1;
		}
		configVersion = CURRENT_VERSION;
		guilds.nameMinLength = Math.max(1, guilds.nameMinLength);
		guilds.nameMaxLength = Math.max(guilds.nameMinLength, Math.min(32, guilds.nameMaxLength));
		guilds.maxMembers = Math.max(0, guilds.maxMembers);
		guilds.inviteExpireSeconds = Math.max(10, guilds.inviteExpireSeconds);
		guilds.homeCooldownSeconds = Math.max(0, guilds.homeCooldownSeconds);
		guilds.homeWarmupSeconds = Math.max(0, Math.min(30, guilds.homeWarmupSeconds));
		territory.foundingRadius = Math.max(0, Math.min(5, territory.foundingRadius));
		territory.extensionRadius = Math.max(0, Math.min(3, territory.extensionRadius));
		territory.minDistanceBetweenGuilds = Math.max(0, Math.min(32, territory.minDistanceBetweenGuilds));
		territory.minDistanceFromSpawn = Math.max(0, territory.minDistanceFromSpawn);
		int founding = (2 * territory.foundingRadius + 1) * (2 * territory.foundingRadius + 1);
		territory.baseMaxChunks = Math.max(founding, territory.baseMaxChunks);
		territory.maxChunksPerLevel = Math.max(0, territory.maxChunksPerLevel);
		territory.maxChunksPerLevelAbove10 = Math.max(0, Math.min(1000, territory.maxChunksPerLevelAbove10));
		territory.maxChunksAbove10 = Math.max(0, Math.min(1_000_000, territory.maxChunksAbove10));
		territory.extensionFlagBasePrice = Math.max(0, territory.extensionFlagBasePrice);
		territory.extensionFlagPriceStep = Math.max(0, territory.extensionFlagPriceStep);
		territory.extensionFlagMaxPrice = Math.max(territory.extensionFlagBasePrice, territory.extensionFlagMaxPrice);
		territory.borderDisplaySeconds = Math.max(1, Math.min(120, territory.borderDisplaySeconds));
		economy.decorativeBannerPrice = Math.max(0, economy.decorativeBannerPrice);
		economy.startingTreasury = Math.max(0, economy.startingTreasury);
		levels.maxLevel = Math.max(LevelCurve.MIN_MAX_LEVEL, Math.min(LevelCurve.ABSOLUTE_MAX_LEVEL, levels.maxLevel));
		levels.xpStepBase = Math.max(1, Math.min(10_000_000, levels.xpStepBase));
		levels.xpStepGrowth = Math.max(0, Math.min(1_000_000, levels.xpStepGrowth));
		if (levels.rewards == null) {
			levels.rewards = new LevelRewards();
		}
		LevelRewardRules.validate(levels.rewards);
		levels.ranks = LevelRewardRules.cleanRanks(levels.ranks);
		if (rolePermissions == null) {
			rolePermissions = defaultRolePermissions();
		}
		for (var entry : defaultRolePermissions().entrySet()) {
			rolePermissions.putIfAbsent(entry.getKey(), entry.getValue());
		}
		if (banners == null) {
			banners = new Banners();
		}
		general.autosaveMinutes = Math.max(1, general.autosaveMinutes);
		general.adminOpLevel = Math.max(0, Math.min(4, general.adminOpLevel));
	}
}
