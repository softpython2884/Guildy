package dev.guildy.config;

import dev.guildy.data.GuildPermission;
import dev.guildy.data.GuildRole;

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
		@Comment("Chunks supplementaires autorises par niveau de guilde")
		public int maxChunksPerLevel = 6;
		@Comment("Prix (or, paye par le tresor) du premier drapeau d'extension")
		public int extensionFlagBasePrice = 8;
		@Comment("Augmentation du prix pour chaque drapeau d'extension deja achete")
		public int extensionFlagPriceStep = 4;
		@Comment("Prix maximum d'un drapeau d'extension")
		public int extensionFlagMaxPrice = 64;
		@Comment("Afficher un titre (true) ou seulement la barre d'action (false) en entrant dans un territoire")
		public boolean enterTitle = true;
		@Comment("Duree d'affichage des limites du territoire avec /guild show (secondes)")
		public int borderDisplaySeconds = 15;
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
		@Comment("XP totale requise pour atteindre chaque niveau (index 0 = niveau 2, etc.). Niveau max = taille + 1")
		public List<Integer> xpThresholds = new ArrayList<>(List.of(100, 250, 450, 700, 1000, 1400, 1900, 2500, 3200));
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

	public int maxChunks(int level) {
		return territory.baseMaxChunks + Math.max(0, level - 1) * territory.maxChunksPerLevel;
	}

	public int maxLevel() {
		return levels.xpThresholds.size() + 1;
	}

	/** Total XP needed to reach {@code level} (level 1 = 0). */
	public long xpForLevel(int level) {
		if (level <= 1) {
			return 0;
		}
		int index = Math.min(level - 2, levels.xpThresholds.size() - 1);
		return levels.xpThresholds.get(index);
	}

	public int extensionFlagPrice(int alreadyBought) {
		long price = (long) territory.extensionFlagBasePrice + (long) territory.extensionFlagPriceStep * alreadyBought;
		return (int) Math.min(price, territory.extensionFlagMaxPrice);
	}

	@Override
	public void validate() {
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
		territory.extensionFlagBasePrice = Math.max(0, territory.extensionFlagBasePrice);
		territory.extensionFlagPriceStep = Math.max(0, territory.extensionFlagPriceStep);
		territory.extensionFlagMaxPrice = Math.max(territory.extensionFlagBasePrice, territory.extensionFlagMaxPrice);
		territory.borderDisplaySeconds = Math.max(1, Math.min(120, territory.borderDisplaySeconds));
		economy.decorativeBannerPrice = Math.max(0, economy.decorativeBannerPrice);
		economy.startingTreasury = Math.max(0, economy.startingTreasury);
		if (levels.xpThresholds == null || levels.xpThresholds.isEmpty()) {
			levels.xpThresholds = new ArrayList<>(new Levels().xpThresholds);
		}
		levels.xpThresholds.removeIf(v -> v == null || v < 0);
		levels.xpThresholds.sort(Integer::compareTo);
		if (rolePermissions == null) {
			rolePermissions = defaultRolePermissions();
		}
		for (var entry : defaultRolePermissions().entrySet()) {
			rolePermissions.putIfAbsent(entry.getKey(), entry.getValue());
		}
		general.autosaveMinutes = Math.max(1, general.autosaveMinutes);
		general.adminOpLevel = Math.max(0, Math.min(4, general.adminOpLevel));
	}
}
