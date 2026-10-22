package dev.guildy.unique;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * uniques.json5 - unique weapons and tools: definitions, drop tables per source, power balance, announcements and
 * resource pack options. Free of Minecraft classes except in {@link #validate()} (unit tests use {@link #rebuild}).
 */
@Comment({
		"Guildy - armes uniques : armes et outils legendaires (enchantements de fou, pouvoirs, modeles optionnels).",
		"Ils tombent des boss des hordes (bien plus souvent pendant une nuit de sang), des quetes, des expeditions des",
		"villageois, des gardiens des ruines et se vendent parfois au Carrefour commercial palier III (trade.json5).",
		"Chaque exemplaire porte un numero de serie (registre : <monde>/guildy/uniques.json).",
		"Commandes admin : /guild admin unique list | give <joueur> <id> | registry [id] | free <id> [guilde]."
})
public final class UniqueConfig implements Validatable {
	/**
	 * Current file format: 2 = drop tables of the ruin bosses added to older files, 3 = crossbow model, 4 = the seven uniques
	 * of the ruins (definitions and drop table entries added to older files), 5 = the three armor sets (twelve pieces, set
	 * definitions, drop table and favourite entries added to older files).
	 */
	public static final int CURRENT_VERSION = 5;
	/** Drop sources used by the other modules (UniqueDrops.roll). */
	public static final List<String> SOURCES = List.of("boss", "blood_boss", "quest", "legendary_quest", "expedition", "trade",
			"ruin_boss", "nether_ruin_boss");
	/** Drop sources added by format 2 (ruin bosses). */
	static final List<String> RUIN_SOURCES = List.of("ruin_boss", "nether_ruin_boss");
	/** Uniques added by format 4 (ruin guardians, cursed guardians, legendary contracts). */
	public static final List<String> RUIN_UNIQUES = List.of("lance_du_jaguar_celeste", "couronne_du_roi_de_givre", "egide_des_ames",
			"masse_du_zenith", "lame_du_pacte", "hache_du_seigneur_de_guerre", "plastron_du_serpent");
	/** The armor sets added by format 5 (helmet, chestplate, leggings, boots of each). */
	public static final List<String> SET_IDS = List.of("hiver_eternel", "guerrier_jaguar", "brasier_des_ames");
	/** The twelve set pieces added by format 5, set by set. */
	public static final List<String> SET_UNIQUES = List.of(
			"heaume_hiver_eternel", "cuirasse_hiver_eternel", "jambieres_hiver_eternel", "solerets_hiver_eternel",
			"masque_guerrier_jaguar", "pectoral_guerrier_jaguar", "jambieres_guerrier_jaguar", "sandales_guerrier_jaguar",
			"heaume_brasier_des_ames", "cuirasse_brasier_des_ames", "greves_brasier_des_ames", "bottes_brasier_des_ames");
	/** Separator of the drop context in a source ("ruin_boss@grand_temple_maya,cursed"), see {@link #effectiveTable}. */
	public static final char CONTEXT = '@';
	private static final Set<String> DYES = Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
			"cyan", "purple", "blue", "brown", "green", "red", "black");
	public static final Set<String> MODEL_MODES = Set.of("auto", "on", "off");
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
	private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> SLOTS = Set.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body",
			"saddle");
	private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Butin : les armes uniques tombent des boss, des quetes, des expeditions et se vendent au Carrefour (false = plus aucune)")
	public boolean dropsEnabled = true;

	@Comment("Pouvoirs des armes uniques (foudre, double saut, abattage...). false = elles ne gardent que leurs enchantements")
	public boolean powersEnabled = true;

	@Comment({
			"Unicite des armes uniques :",
			"\"guild\" = une guilde ne recoit jamais une arme qu'elle detient deja (une autre guilde peut l'obtenir) ;",
			"\"server\" = un seul exemplaire de chaque arme sur tout le serveur (elle redevient disponible si elle est detruite) ;",
			"\"none\" = aucune limite (ancien comportement). Les dons des admins ignorent cette regle (avertissement)."
	})
	public String uniqueness = "guild";

	@Comment("Quand toutes les armes d'une table sont deja detenues : un parchemin de sort de la meme source a la place, garanti (false = rien)")
	public boolean fallbackToScroll = true;

	@Comment("Annonce a tout le serveur quand une guilde a reuni toutes les armes uniques (collection complete)")
	public boolean announceCollection = true;

	@Comment({
			"Modeles et textures personnalises (pack de ressources) :",
			"\"auto\" = seulement si le mod Polymer (polymer-resource-pack) est installe : Guildy ajoute ses textures a son pack ;",
			"\"on\" = toujours (l'admin heberge guildy-resourcepack-<version>.zip : resource-pack= dans server.properties) ;",
			"\"off\" = jamais : les armes gardent l'apparence de leur objet de base (nom, description et brillance)"
	})
	public String customModels = "auto";

	@Comment("Avec Polymer : pack de ressources obligatoire (sinon un joueur qui le refuse verrait des textures manquantes)")
	public boolean polymerRequired = true;

	@Comment("Annonce a tout le serveur quand un joueur obtient une arme unique")
	public Announce announce = new Announce();

	@Comment({
			"Tables de butin par source : chance (0 a 1) qu'une arme unique tombe, puis tirage pondere dans items {id: poids}.",
			"Sources : boss (boss des hordes), blood_boss (boss pendant une nuit de sang), quest (recompense de quete, rare),",
			"legendary_quest (quete legendaire), expedition (expeditions des villageois), trade (Carrefour commercial : l'offre",
			"\"arme unique\" de trade.json5 tire ici l'arme proposee), ruin_boss / nether_ruin_boss (boss des ruines de l'Overworld",
			"/ du Nether, ruins.json5). announce : annoncer l'obtention (raretes de \"announce\")."
	})
	public Map<String, DropTable> drops = defaultDrops();

	@Comment({
			"Armes favorites de certains gardiens : poids AJOUTES a la table de la source quand le butin vient de ce contexte.",
			"Cle \"source@contexte\" : contexte = id d'une ruine de ruins.json5 (son gardien) ou \"cursed\" (tout gardien maudit).",
			"Exemple : \"ruin_boss@grand_temple_maya\": {\"lance_du_jaguar_celeste\": 25} = le gardien du Grand Temple tire bien",
			"plus souvent la Lance du Jaguar celeste (la chance de la table ne change pas, seul le choix de l'arme change)."
	})
	public Map<String, Map<String, Double>> favorites = defaultFavorites();

	@Comment("Reglages des pouvoirs (recharges en secondes, degats en points de vie : 2 = 1 coeur, distances en blocs)")
	public Powers powers = new Powers();

	@Comment({
			"Armes et outils uniques : id (minuscules, chiffres, _), item (objet vanilla de base), name, rarity (RARE, EPIC,",
			"LEGENDARY, MYTHIC), lore (histoire, codes couleur &), enchantments {id: niveau} (vanilla ou guildy:foudroiement,",
			"guildy:vampirisme, guildy:givre, guildy:brasier, guildy:echo, guildy:seisme, guildy:apesanteur, guildy:salve,",
			"guildy:moisson, guildy:marque ; niveaux au-dela du maximum vanilla permis), attributes [{attribute, amount, operation",
			"(add_value, add_multiplied_base, add_multiplied_total), slot (mainhand, offhand, feet, head, armor...)}] ajoutes aux",
			"attributs de l'objet de base, glint (brillance), unbreakable (incassable), power (storm_call, frost_nova, blink_strike,",
			"tree_feller, excavate, vein_miner, double_jump, shockwave, phoenix_arrows, tide_call, soul_harvest, jaguar_pounce,",
			"frost_crown, soul_aegis, solar_leap, pact_seal, infernal_rift, jade_gale ou vide), model (texture du pack de",
			"ressources, vide = apparence vanilla), color (couleur RGB du cuir teint, -1 = aucune), banner (bouclier : base = couleur",
			"de fond, patterns [{pattern: minecraft:skull..., color}] = motifs de banniere vanilla, visibles sans pack),",
			"set (id d'une panoplie de \"sets\" : la piece compte pour ses bonus, vide = aucune).",
			"Les objets deja obtenus suivent le pouvoir et le modele de la config ; nom, description et enchantements restent",
			"ceux de leur creation."
	})
	public List<UniqueDef> items = defaultItems();

	@Comment({
			"Panoplies : les armures uniques dont le champ \"set\" porte cet id. Portees ensemble (pieces differentes), elles",
			"donnent des bonus par palier : bonuses [{pieces (nombre de pieces portees), name, attributes [{attribute, amount,",
			"operation}] (ajoutes au joueur tant que le palier est atteint), power (frost_blood, winter_heart, jaguar_ambush,",
			"soul_pyre ou vide ; reglages dans powers)}]. Les paliers se cumulent (4 pieces = bonus 2 + bonus 4)."
	})
	public List<SetDef> sets = defaultSets();

	private transient Map<String, UniqueDef> byId = new LinkedHashMap<>();
	private transient Map<String, SetDef> setsById = new LinkedHashMap<>();
	private transient Map<String, List<String>> piecesBySet = new LinkedHashMap<>();
	private transient UniquenessMode mode = UniquenessMode.GUILD;
	private transient Set<UniqueRarity> announced = EnumSet.of(UniqueRarity.LEGENDARY, UniqueRarity.MYTHIC);

	// ================================================================== sections

	@Json5.Section
	public static final class Announce {
		@Comment("Raretes annoncees a tout le serveur (RARE, EPIC, LEGENDARY, MYTHIC)")
		public List<String> rarities = new ArrayList<>(List.of("LEGENDARY", "MYTHIC"));
		@Comment("Son joue a tous les joueurs lors de l'annonce")
		public boolean sound = true;
	}

	/** One drop source: chance of a unique, then a weighted pick among the items. */
	public static final class DropTable {
		public double chance;
		public boolean announce = true;
		public Map<String, Double> items = new LinkedHashMap<>();

		public DropTable() {
		}

		DropTable(double chance, boolean announce) {
			this.chance = chance;
			this.announce = announce;
		}

		DropTable add(String id, double weight) {
			items.put(id, weight);
			return this;
		}
	}

	@Json5.Section
	public static final class Powers {
		@Comment("Foudrelame (storm_call) - clic droit : la foudre frappe les monstres autour du point vise")
		public StormCall stormCall = new StormCall();
		@Comment("Croc-de-Givre (frost_nova) - clic droit : onde de givre autour du joueur")
		public FrostNova frostNova = new FrostNova();
		@Comment("Murmure du Neant (blink_strike) - clic droit : se teleporte derriere le monstre vise et le frappe")
		public BlinkStrike blinkStrike = new BlinkStrike();
		@Comment("Hache de l'Ancien Forestier (tree_feller) - abat tout l'arbre naturel (accroupi : une seule buche)")
		public TreeFeller treeFeller = new TreeFeller();
		@Comment("Pioche du Titan (excavate) - creuse en 3x3 dans la roche naturelle (accroupi : un seul bloc)")
		public Excavate excavate = new Excavate();
		@Comment("Pic du Filon (vein_miner) - extrait tout le filon de minerai (accroupi : un seul bloc)")
		public VeinMiner veinMiner = new VeinMiner();
		@Comment("Bottes de Zephyr (double_jump) - double saut (appuyer deux fois sur saut), pas de degats de chute")
		public DoubleJump doubleJump = new DoubleJump();
		@Comment("Marteau du Seisme (shockwave) - une attaque ecrasante (en tombant) frappe aussi les monstres autour")
		public Shockwave shockwave = new Shockwave();
		@Comment("Arc du Phenix (phoenix_arrows) - la fleche embrase les monstres autour de sa cible")
		public PhoenixArrows phoenixArrows = new PhoenixArrows();
		@Comment("Trident du Leviathan (tide_call) - lance, il appelle la foudre qui rebondit sur les monstres proches")
		public TideCall tideCall = new TideCall();
		@Comment("Faux des Ames (soul_harvest) - chaque monstre tue soigne le porteur")
		public SoulHarvest soulHarvest = new SoulHarvest();
		@Comment("Lance du Jaguar celeste (jaguar_pounce) - clic droit (charge) : bond du jaguar, griffes a l'atterrissage")
		public JaguarPounce jaguarPounce = new JaguarPounce();
		@Comment("Couronne du Roi de givre (frost_crown) - gele les monstres qui frappent le porteur, qui ne gele jamais")
		public FrostCrown frostCrown = new FrostCrown();
		@Comment("Egide des Ames (soul_aegis) - un coup de monstre pare au bouclier lui revient et soigne le porteur")
		public SoulAegis soulAegis = new SoulAegis();
		@Comment("Masse du Zenith (solar_leap) - clic droit : saut vers le ciel, un soleil eclate a l'impact")
		public SolarLeap solarLeap = new SolarLeap();
		@Comment("Lame du Pacte (pact_seal) - chaque coup scelle le monstre, le dernier sceau ouvre un pentagramme qui eclate")
		public PactSeal pactSeal = new PactSeal();
		@Comment("Hache du Seigneur de guerre (infernal_rift) - clic droit : une faille de magma file droit devant")
		public InfernalRift infernalRift = new InfernalRift();
		@Comment("Plastron du Serpent a plumes (jade_gale) - sous un seuil de vie, souffle de jade qui repousse les monstres")
		public JadeGale jadeGale = new JadeGale();
		@Comment("Panoplie de l'Hiver eternel, 4 pieces (winter_heart) - sous un seuil de vie, une nova de givre gele les monstres")
		public WinterHeart winterHeart = new WinterHeart();
		@Comment("Panoplie du Guerrier jaguar, 4 pieces (jaguar_ambush) - accroupi, camouflage ; l'attaque suivante est une embuscade")
		public JaguarAmbush jaguarAmbush = new JaguarAmbush();
		@Comment("Panoplie du Brasier des ames, 4 pieces (soul_pyre) - aura qui draine les ames des monstres, trainee de feu d'ame")
		public SoulPyre soulPyre = new SoulPyre();
	}

	@Json5.Section
	public static final class WinterHeart {
		public double cooldownSeconds = 60;
		@Comment("Seuil de vie apres un coup (0.3 = 30 %), rayon de la nova, degats de froid, duree (secondes) et niveau de la lenteur")
		public double healthThreshold = 0.3;
		public double radius = 6;
		public double damage = 6;
		public int freezeSeconds = 5;
		public int slowLevel = 4;
		@Comment("Projection des monstres, puis Resistance (duree en secondes, niveau) et Absorption (niveau) du porteur")
		public double push = 0.9;
		public int resistanceSeconds = 6;
		public int resistanceLevel = 2;
		public int absorptionLevel = 2;
	}

	@Json5.Section
	public static final class JaguarAmbush {
		public double cooldownSeconds = 12;
		@Comment("Secondes accroupi et sans attaquer avant le camouflage (dans le feuillage : foliageSeconds), portee ou les monstres perdent votre trace")
		public double camouflageSeconds = 1.5;
		public double foliageSeconds = 0.5;
		public double loseTrackRange = 16;
		@Comment("Delai (secondes) pour frapper apres la fin du camouflage, degats ajoutes au coup d'embuscade")
		public double strikeWindowSeconds = 2;
		public double damage = 10;
		@Comment("Griffes autour de la cible : rayon, degats, nombre maximum de monstres, duree (secondes) et niveau de la lenteur")
		public double radius = 3.5;
		public double clawDamage = 6;
		public int maxTargets = 6;
		public int slowSeconds = 3;
		public int slowLevel = 2;
	}

	@Json5.Section
	public static final class SoulPyre {
		@Comment("Une pulsation toutes les pulseSeconds : degats aux monstres a radius blocs (maxTargets au plus), part rendue au porteur, soin maximum")
		public double pulseSeconds = 2;
		public double radius = 5;
		public double damage = 2;
		public int maxTargets = 4;
		public double healRatio = 0.5;
		public double maxHeal = 3;
		@Comment("Trainee de feu d'ame en courant : duree d'une flamme (secondes), degats de feu et embrasement (secondes) des monstres qui la touchent")
		public double trailSeconds = 3;
		public double trailDamage = 3;
		public int fireSeconds = 3;
	}

	@Json5.Section
	public static final class StormCall {
		public double cooldownSeconds = 12;
		@Comment("Portee de visee, rayon autour du point vise, nombre maximum de monstres foudroyes, degats par eclair")
		public double range = 32;
		public double radius = 4.5;
		public int maxTargets = 4;
		public double damage = 10;
	}

	@Json5.Section
	public static final class FrostNova {
		public double cooldownSeconds = 18;
		@Comment("Rayon, degats de froid, duree (secondes) et niveau de la lenteur")
		public double radius = 6;
		public double damage = 4;
		public int slowSeconds = 6;
		public int slowLevel = 3;
	}

	@Json5.Section
	public static final class BlinkStrike {
		public double cooldownSeconds = 8;
		@Comment("Portee de visee et degats du coup porte apres la teleportation")
		public double range = 20;
		public double damage = 10;
	}

	@Json5.Section
	public static final class TreeFeller {
		@Comment("Buches maximum par arbre, buches coupees par tick, feuilles naturelles minimum autour (sinon : construction)")
		public int maxLogs = 128;
		public int logsPerTick = 12;
		public int minLeaves = 4;
		@Comment("Accroupi : pouvoir desactive")
		public boolean sneakDisables = true;
	}

	@Json5.Section
	public static final class Excavate {
		@Comment("Accroupi : pouvoir desactive")
		public boolean sneakDisables = true;
		@Comment("Blocs creuses en 3x3 (ids ou #tags). Jamais chez une autre guilde, jamais le coeur.")
		public List<String> blocks = new ArrayList<>(List.of("#minecraft:base_stone_overworld", "#minecraft:base_stone_nether",
				"#minecraft:ores", "minecraft:calcite", "minecraft:dripstone_block", "minecraft:smooth_basalt", "minecraft:end_stone",
				"minecraft:magma_block"));
	}

	@Json5.Section
	public static final class VeinMiner {
		@Comment("Blocs maximum par filon")
		public int maxBlocks = 32;
		@Comment("Accroupi : pouvoir desactive")
		public boolean sneakDisables = true;
		@Comment("Minerais concernes (ids ou #tags). Jamais chez une autre guilde.")
		public List<String> blocks = new ArrayList<>(List.of("#minecraft:ores", "minecraft:ancient_debris"));
	}

	@Json5.Section
	public static final class DoubleJump {
		@Comment("Delai minimum entre deux doubles sauts (secondes), elan vertical et vers l'avant")
		public double cooldownSeconds = 0.5;
		public double upward = 0.75;
		public double forward = 0.45;
	}

	@Json5.Section
	public static final class Shockwave {
		public double cooldownSeconds = 4;
		@Comment("Rayon autour de la cible, degats aux autres monstres, elan vers le haut")
		public double radius = 5;
		public double damage = 8;
		public double lift = 0.45;
	}

	@Json5.Section
	public static final class PhoenixArrows {
		public double cooldownSeconds = 2;
		@Comment("Rayon autour de la cible, degats de feu, duree d'embrasement (secondes)")
		public double radius = 3.5;
		public double damage = 4;
		public int fireSeconds = 5;
	}

	@Json5.Section
	public static final class TideCall {
		public double cooldownSeconds = 6;
		@Comment("Degats de l'eclair sur la cible, nombre de rebonds, rayon et degats des rebonds")
		public double damage = 8;
		public int chainTargets = 3;
		public double chainRadius = 8;
		public double chainDamage = 5;
	}

	@Json5.Section
	public static final class SoulHarvest {
		@Comment("Points de vie rendus par monstre tue, duree (secondes) de la Vitesse I accordee")
		public double heal = 3;
		public int speedSeconds = 3;
	}

	@Json5.Section
	public static final class JaguarPounce {
		public double cooldownSeconds = 10;
		@Comment("Elan du bond vers l'avant et vers le haut (blocs par tick)")
		public double leap = 1.1;
		public double lift = 0.5;
		@Comment("Rayon des griffes a l'atterrissage, degats, nombre maximum de monstres, duree (secondes) et niveau de la lenteur")
		public double radius = 3.5;
		public double damage = 8;
		public int maxTargets = 8;
		public int slowSeconds = 3;
		public int slowLevel = 2;
	}

	@Json5.Section
	public static final class FrostCrown {
		public double cooldownSeconds = 3;
		@Comment("Degats de froid a l'attaquant, duree (secondes) et niveau de la lenteur, distance maximum d'un tireur")
		public double damage = 3;
		public int freezeSeconds = 4;
		public int slowLevel = 4;
		public double range = 24;
		@Comment("Rayon de la vague de froid autour du porteur (lenteur I pour les autres monstres)")
		public double chillRadius = 3;
	}

	@Json5.Section
	public static final class SoulAegis {
		public double cooldownSeconds = 1.5;
		@Comment("Part des degats bloques renvoyee (1 = 100 %), minimum et maximum renvoyes, soin du porteur, distance d'un tireur")
		public double reflectRatio = 1.0;
		public double reflectMin = 3;
		public double reflectMax = 30;
		public double heal = 3;
		public double range = 24;
	}

	@Json5.Section
	public static final class SolarLeap {
		public double cooldownSeconds = 14;
		@Comment("Elan vertical et vers l'avant du saut (blocs par tick)")
		public double upward = 1.2;
		public double forward = 0.35;
		@Comment("Rayon du soleil, degats de feu, multiplicateur contre les morts-vivants, duree d'embrasement (secondes)")
		public double radius = 5;
		public double damage = 8;
		public double undeadMultiplier = 2;
		public int fireSeconds = 5;
	}

	@Json5.Section
	public static final class PactSeal {
		@Comment("Delai minimum entre deux pactes (secondes)")
		public double cooldownSeconds = 2;
		@Comment("Sceaux necessaires, duree d'un sceau sans nouveau coup (secondes), degats et duree du fletrissement (secondes)")
		public int seals = 3;
		public int sealSeconds = 8;
		public double damage = 12;
		public int witherSeconds = 4;
		@Comment("Rayon de propagation (un sceau pour chaque monstre proche)")
		public double spreadRadius = 4;
	}

	@Json5.Section
	public static final class InfernalRift {
		public double cooldownSeconds = 12;
		@Comment("Longueur de la faille (blocs), largeur touchee, degats de feu, embrasement (secondes), projection vers le haut")
		public int length = 12;
		public double width = 1.4;
		public double damage = 7;
		public int fireSeconds = 4;
		public double lift = 0.5;
	}

	@Json5.Section
	public static final class JadeGale {
		public double cooldownSeconds = 30;
		@Comment("Seuil de vie qui declenche le souffle (0.4 = 40 %), rayon, force de la poussee")
		public double healthThreshold = 0.4;
		public double radius = 5;
		public double push = 1.4;
		@Comment("Poison des monstres et regeneration du porteur : durees (secondes) et niveaux ; chute lente du porteur (secondes)")
		public int poisonSeconds = 5;
		public int poisonLevel = 2;
		public int regenSeconds = 5;
		public int regenLevel = 2;
		public int slowFallingSeconds = 4;
	}

	/** Banner look of a shield unique: vanilla clients draw it without any resource pack. */
	public static final class Banner {
		public String base = "white";
		public List<BannerLayer> patterns = new ArrayList<>();

		public Banner() {
		}

		Banner(String base) {
			this.base = base;
		}

		Banner layer(String pattern, String color) {
			patterns.add(new BannerLayer(pattern, color));
			return this;
		}
	}

	/** One banner pattern layer (vanilla pattern id, dye color name). */
	public static final class BannerLayer {
		public String pattern = "minecraft:base";
		public String color = "white";

		public BannerLayer() {
		}

		BannerLayer(String pattern, String color) {
			this.pattern = pattern;
			this.color = color;
		}
	}

	/** A unique item definition. */
	public static final class UniqueDef {
		public String id = "";
		public String item = "minecraft:iron_sword";
		public String name = "";
		public String rarity = "RARE";
		public List<String> lore = new ArrayList<>();
		public Map<String, Integer> enchantments = new LinkedHashMap<>();
		public List<AttributeEntry> attributes = new ArrayList<>();
		public boolean glint = true;
		public boolean unbreakable;
		public String power = "";
		public String model = "";
		public int color = -1;
		public @Nullable Banner banner;
		public String set = "";

		private transient UniqueRarity parsedRarity = UniqueRarity.RARE;
		private transient @Nullable UniquePower parsedPower;

		public UniqueRarity rarity() {
			return parsedRarity == null ? UniqueRarity.RARE : parsedRarity;
		}

		public @Nullable UniquePower power() {
			return parsedPower;
		}

		UniqueDef ench(String enchantment, int level) {
			enchantments.put(enchantment, level);
			return this;
		}

		UniqueDef attr(String attribute, double amount, String operation, String slot) {
			attributes.add(new AttributeEntry(attribute, amount, operation, slot));
			return this;
		}

		UniqueDef story(String... lines) {
			lore.addAll(List.of(lines));
			return this;
		}
	}

	/** An attribute modifier added to the item's own modifiers. */
	public static final class AttributeEntry {
		public String attribute = "minecraft:attack_damage";
		public double amount;
		public String operation = "add_value";
		public String slot = "mainhand";

		public AttributeEntry() {
		}

		AttributeEntry(String attribute, double amount, String operation, String slot) {
			this.attribute = attribute;
			this.amount = amount;
			this.operation = operation;
			this.slot = slot;
		}
	}

	/** An armor set: the uniques whose {@code set} is this id, and its bonuses per number of pieces worn. */
	public static final class SetDef {
		public String id = "";
		public String name = "";
		public List<SetBonus> bonuses = new ArrayList<>();

		/** Bonuses by increasing number of pieces (after {@link #rebuild}). */
		public List<SetBonus> bonuses() {
			return bonuses;
		}

		SetDef bonus(int pieces, String name, @Nullable SetPower power, AttributeEntry... attributes) {
			SetBonus bonus = new SetBonus();
			bonus.pieces = pieces;
			bonus.name = name;
			bonus.power = power == null ? "" : power.id;
			bonus.attributes.addAll(List.of(attributes));
			bonuses.add(bonus);
			return this;
		}
	}

	/** One tier of a set: reached with {@code pieces} different pieces worn; attribute modifiers on the player and a power. */
	public static final class SetBonus {
		public int pieces = 2;
		public String name = "";
		public List<AttributeEntry> attributes = new ArrayList<>();
		public String power = "";

		private transient @Nullable SetPower parsedPower;

		public @Nullable SetPower power() {
			return parsedPower;
		}
	}

	/** An attribute of a set bonus (on the player: no equipment slot). */
	static AttributeEntry bonusAttr(String attribute, double amount, String operation) {
		return new AttributeEntry(attribute, amount, operation, "armor");
	}

	// ================================================================== queries

	/** Valid definition (after {@link #rebuild}), or null. */
	public @Nullable UniqueDef item(@Nullable String id) {
		return id == null ? null : byId.get(id.trim().toLowerCase(Locale.ROOT));
	}

	/** Valid definitions, in file order. */
	public Collection<UniqueDef> usable() {
		return byId.values();
	}

	/** Valid set (after {@link #rebuild}), or null. */
	public @Nullable SetDef set(@Nullable String id) {
		return id == null ? null : setsById.get(id.trim().toLowerCase(Locale.ROOT));
	}

	/** Valid sets that have pieces, in file order. */
	public Collection<SetDef> usableSets() {
		return setsById.values();
	}

	/** Ids of the valid pieces of a set, in file order (empty for an unknown set). */
	public List<String> piecesOf(@Nullable String setId) {
		List<String> pieces = setId == null ? null : piecesBySet.get(setId.trim().toLowerCase(Locale.ROOT));
		return pieces == null ? List.of() : pieces;
	}

	/** Drop table of a source, or null. */
	public @Nullable DropTable table(@Nullable String source) {
		return source == null || drops == null ? null : drops.get(source.trim().toLowerCase(Locale.ROOT));
	}

	/** "ruin_boss@grand_temple_maya,cursed" -> "ruin_boss" (lower case, trimmed). */
	public static String baseSource(@Nullable String source) {
		if (source == null) {
			return "";
		}
		int at = source.indexOf(CONTEXT);
		return (at < 0 ? source : source.substring(0, at)).trim().toLowerCase(Locale.ROOT);
	}

	/** "ruin_boss@grand_temple_maya,cursed" -> [grand_temple_maya, cursed] (empty without context). */
	public static List<String> contexts(@Nullable String source) {
		List<String> out = new ArrayList<>();
		int at = source == null ? -1 : source.indexOf(CONTEXT);
		if (at >= 0) {
			for (String part : source.substring(at + 1).split(",")) {
				String key = part.trim().toLowerCase(Locale.ROOT);
				if (!key.isEmpty() && !out.contains(key)) {
					out.add(key);
				}
			}
		}
		return out;
	}

	/**
	 * The drop table of a source with its context ({@code base@ctx1,ctx2}): the base table (same chance) whose weights get the
	 * {@link #favorites} of every "base@ctx" key added. The base table itself when there is no matching favorite; null
	 * without a base table.
	 */
	public @Nullable DropTable effectiveTable(@Nullable String source) {
		String base = baseSource(source);
		DropTable table = table(base);
		if (table == null) {
			return null;
		}
		List<Map<String, Double>> extras = new ArrayList<>();
		for (String context : contexts(source)) {
			Map<String, Double> extra = favorites == null ? null : favorites.get(base + CONTEXT + context);
			if (extra != null && !extra.isEmpty()) {
				extras.add(extra);
			}
		}
		if (extras.isEmpty()) {
			return table;
		}
		DropTable merged = new DropTable(table.chance, table.announce);
		if (table.items != null) {
			merged.items.putAll(table.items);
		}
		for (Map<String, Double> extra : extras) {
			for (Map.Entry<String, Double> entry : extra.entrySet()) {
				if (entry.getValue() != null && entry.getValue() > 0) {
					merged.items.merge(entry.getKey(), entry.getValue(), Double::sum);
				}
			}
		}
		return merged;
	}

	/** Parsed {@link #uniqueness} (GUILD before validation). */
	public UniquenessMode mode() {
		return mode == null ? UniquenessMode.GUILD : mode;
	}

	public boolean announces(UniqueRarity rarity) {
		return announced.contains(rarity);
	}

	/** "auto" / "on" / "off". */
	public String modelMode() {
		return customModels == null ? "auto" : customModels;
	}

	// ================================================================== validation

	@Override
	public void validate() {
		for (String warning : rebuild(id -> ItemUtil.item(id) != null)) {
			Guildy.LOGGER.warn("[uniques.json5] {}", warning);
		}
	}

	/** Clamps the settings, validates the definitions and drop tables (pure). @return the problems found */
	public List<String> rebuild(Predicate<String> itemExists) {
		List<String> warnings = new ArrayList<>();
		migrate();
		configVersion = CURRENT_VERSION;
		customModels = customModels == null ? "auto" : customModels.trim().toLowerCase(Locale.ROOT);
		if (!MODEL_MODES.contains(customModels)) {
			warnings.add("customModels '" + customModels + "' unknown (auto, on, off): auto used");
			customModels = "auto";
		}
		UniquenessMode parsed = UniquenessMode.parse(uniqueness);
		if (parsed == null) {
			warnings.add("uniqueness '" + uniqueness + "' unknown (guild, server, none): guild used");
			parsed = UniquenessMode.GUILD;
		}
		mode = parsed;
		uniqueness = parsed.id();
		rebuildAnnounce(warnings);
		rebuildSets(warnings);
		rebuildItems(itemExists, warnings);
		linkSets(warnings);
		rebuildDrops(warnings);
		rebuildFavorites(warnings);
		rebuildPowers();
		return warnings;
	}

	/** Set definitions: ids, names, bonuses (tiers sorted by pieces, one per number of pieces), attributes and powers. */
	private void rebuildSets(List<String> warnings) {
		if (sets == null) {
			sets = defaultSets();
		}
		Map<String, SetDef> valid = new LinkedHashMap<>();
		for (SetDef set : sets) {
			if (set == null) {
				continue;
			}
			set.id = set.id == null ? "" : set.id.trim().toLowerCase(Locale.ROOT);
			if (!ID.matcher(set.id).matches()) {
				warnings.add("set '" + set.id + "': invalid id (a-z, 0-9, _): ignored");
				continue;
			}
			if (valid.containsKey(set.id)) {
				warnings.add("set '" + set.id + "': duplicate id: ignored");
				continue;
			}
			if (set.name == null || set.name.isBlank()) {
				set.name = set.id;
			}
			Map<Integer, SetBonus> tiers = new java.util.TreeMap<>();
			if (set.bonuses != null) {
				for (SetBonus bonus : set.bonuses) {
					if (bonus == null) {
						continue;
					}
					if (bonus.pieces < 1 || bonus.pieces > 16) {
						warnings.add("set '" + set.id + "': bonus for " + bonus.pieces + " pieces (1 to 16): ignored");
						continue;
					}
					if (tiers.containsKey(bonus.pieces)) {
						warnings.add("set '" + set.id + "': two bonuses for " + bonus.pieces + " pieces: the second one is ignored");
						continue;
					}
					bonus.name = bonus.name == null ? "" : bonus.name;
					List<AttributeEntry> attributes = new ArrayList<>();
					if (bonus.attributes != null) {
						for (AttributeEntry attribute : bonus.attributes) {
							if (attribute != null && (attribute.slot == null || attribute.slot.isBlank())) {
								attribute.slot = "armor";
							}
							String problem = checkAttribute(attribute);
							if (problem != null) {
								warnings.add("set '" + set.id + "' (" + bonus.pieces + " pieces): " + problem + ": ignored");
							} else {
								attributes.add(attribute);
							}
						}
					}
					bonus.attributes = attributes;
					bonus.power = bonus.power == null ? "" : bonus.power.trim().toLowerCase(Locale.ROOT);
					bonus.parsedPower = SetPower.byId(bonus.power);
					if (!bonus.power.isEmpty() && bonus.parsedPower == null) {
						warnings.add("set '" + set.id + "' (" + bonus.pieces + " pieces): unknown power '" + bonus.power + "' ("
								+ String.join(", ", SetPower.ids()) + "): none");
						bonus.power = "";
					}
					tiers.put(bonus.pieces, bonus);
				}
			}
			set.bonuses = new ArrayList<>(tiers.values());
			valid.put(set.id, set);
		}
		setsById = valid;
	}

	/** Pieces of each set (after the items): an unknown set id on an item is cleared, a set without pieces is unused. */
	private void linkSets(List<String> warnings) {
		Map<String, List<String>> pieces = new LinkedHashMap<>();
		for (UniqueDef def : byId.values()) {
			def.set = def.set == null ? "" : def.set.trim().toLowerCase(Locale.ROOT);
			if (def.set.isEmpty()) {
				continue;
			}
			if (!setsById.containsKey(def.set)) {
				warnings.add("item '" + def.id + "': unknown set '" + def.set + "': none");
				def.set = "";
				continue;
			}
			pieces.computeIfAbsent(def.set, key -> new ArrayList<>()).add(def.id);
		}
		Map<String, SetDef> used = new LinkedHashMap<>();
		for (SetDef set : setsById.values()) {
			List<String> own = pieces.get(set.id);
			if (own == null || own.isEmpty()) {
				warnings.add("set '" + set.id + "': no item has \"set\": \"" + set.id + "\" (unused)");
				continue;
			}
			for (SetBonus bonus : set.bonuses) {
				if (bonus.pieces > own.size()) {
					warnings.add("set '" + set.id + "': bonus for " + bonus.pieces + " pieces, but the set has " + own.size() + " (never reached)");
				}
			}
			used.put(set.id, set);
		}
		setsById = used;
		piecesBySet = pieces;
	}

	private void rebuildFavorites(List<String> warnings) {
		if (favorites == null) {
			favorites = new LinkedHashMap<>();
		}
		Map<String, Map<String, Double>> fixed = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Double>> entry : favorites.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
			String base = baseSource(key);
			List<String> contexts = contexts(key);
			if (base.isEmpty() || contexts.size() != 1 || !ID.matcher(contexts.getFirst()).matches()) {
				warnings.add("favorites: invalid key '" + entry.getKey() + "' (source@context, e.g. ruin_boss@grand_temple_maya): ignored");
				continue;
			}
			if (!SOURCES.contains(base)) {
				warnings.add("favorites." + key + ": source '" + base + "' is never used (" + String.join(", ", SOURCES) + ")");
			}
			Map<String, Double> weights = new LinkedHashMap<>();
			for (Map.Entry<String, Double> weight : entry.getValue().entrySet()) {
				String id = weight.getKey() == null ? "" : weight.getKey().trim().toLowerCase(Locale.ROOT);
				if (!byId.containsKey(id)) {
					warnings.add("favorites." + key + ": unknown item '" + weight.getKey() + "': ignored");
					continue;
				}
				double w = weight.getValue() == null ? 0 : weight.getValue();
				weights.put(id, clamp(Double.isFinite(w) ? w : 0, 0, 10000));
			}
			fixed.put(base + CONTEXT + contexts.getFirst(), weights);
		}
		favorites = fixed;
	}

	/** Problem of a banner look (null when fine): known dye names, pattern ids in resource form, 16 layers at most. */
	private static @Nullable String checkBanner(Banner banner) {
		banner.base = banner.base == null ? "" : banner.base.trim().toLowerCase(Locale.ROOT);
		if (!DYES.contains(banner.base)) {
			return "banner: unknown base color '" + banner.base + "'";
		}
		if (banner.patterns == null) {
			banner.patterns = new ArrayList<>();
		}
		List<BannerLayer> layers = new ArrayList<>();
		for (BannerLayer layer : banner.patterns) {
			if (layer == null) {
				continue;
			}
			layer.pattern = normalize(layer.pattern);
			layer.color = layer.color == null ? "" : layer.color.trim().toLowerCase(Locale.ROOT);
			if (!RESOURCE.matcher(layer.pattern).matches() || !DYES.contains(layer.color)) {
				return "banner: invalid layer '" + layer.pattern + "' / '" + layer.color + "'";
			}
			layers.add(layer);
		}
		if (layers.size() > 16) {
			return "banner: more than 16 layers";
		}
		banner.patterns = layers;
		return null;
	}

	private void rebuildAnnounce(List<String> warnings) {
		if (announce == null) {
			announce = new Announce();
		}
		if (announce.rarities == null) {
			announce.rarities = new ArrayList<>();
		}
		Set<UniqueRarity> parsed = EnumSet.noneOf(UniqueRarity.class);
		for (String value : announce.rarities) {
			UniqueRarity rarity = UniqueRarity.parse(value);
			if (rarity == null) {
				warnings.add("announce: unknown rarity '" + value + "'");
			} else {
				parsed.add(rarity);
			}
		}
		announced = parsed;
	}

	private void rebuildItems(Predicate<String> itemExists, List<String> warnings) {
		if (items == null) {
			items = defaultItems();
		}
		Map<String, UniqueDef> valid = new LinkedHashMap<>();
		for (UniqueDef def : items) {
			if (def == null) {
				continue;
			}
			def.id = def.id == null ? "" : def.id.trim().toLowerCase(Locale.ROOT);
			if (!ID.matcher(def.id).matches()) {
				warnings.add("item '" + def.id + "': invalid id (a-z, 0-9, _): ignored");
				continue;
			}
			if (valid.containsKey(def.id)) {
				warnings.add("item '" + def.id + "': duplicate id: ignored");
				continue;
			}
			def.item = normalize(def.item);
			if (!itemExists.test(def.item)) {
				warnings.add("item '" + def.id + "': unknown base item '" + def.item + "': ignored");
				continue;
			}
			if (def.name == null || def.name.isBlank()) {
				def.name = def.id;
			}
			UniqueRarity rarity = UniqueRarity.parse(def.rarity);
			if (rarity == null) {
				warnings.add("item '" + def.id + "': unknown rarity '" + def.rarity + "': RARE used");
				rarity = UniqueRarity.RARE;
			}
			def.rarity = rarity.name();
			def.parsedRarity = rarity;
			if (def.lore == null) {
				def.lore = new ArrayList<>();
			}
			def.lore.removeIf(line -> line == null);
			Map<String, Integer> enchantments = new LinkedHashMap<>();
			if (def.enchantments != null) {
				for (Map.Entry<String, Integer> entry : def.enchantments.entrySet()) {
					String id = normalize(entry.getKey());
					if (!RESOURCE.matcher(id).matches() || entry.getValue() == null) {
						warnings.add("item '" + def.id + "': invalid enchantment '" + entry.getKey() + "': ignored");
						continue;
					}
					enchantments.put(id, Math.max(1, Math.min(255, entry.getValue())));
				}
			}
			def.enchantments = enchantments;
			List<AttributeEntry> attributes = new ArrayList<>();
			if (def.attributes != null) {
				for (AttributeEntry attribute : def.attributes) {
					String problem = checkAttribute(attribute);
					if (problem != null) {
						warnings.add("item '" + def.id + "': " + problem + ": ignored");
					} else {
						attributes.add(attribute);
					}
				}
			}
			def.attributes = attributes;
			def.power = def.power == null ? "" : def.power.trim().toLowerCase(Locale.ROOT);
			def.parsedPower = UniquePower.byId(def.power);
			if (!def.power.isEmpty() && def.parsedPower == null) {
				warnings.add("item '" + def.id + "': unknown power '" + def.power + "' (" + String.join(", ", UniquePower.ids()) + "): none");
				def.power = "";
			}
			def.model = def.model == null ? "" : def.model.trim().toLowerCase(Locale.ROOT);
			if (!def.model.isEmpty() && !ID.matcher(def.model).matches()) {
				warnings.add("item '" + def.id + "': invalid model '" + def.model + "': vanilla look");
				def.model = "";
			}
			def.color = def.color < 0 ? -1 : def.color & 0xFFFFFF;
			if (def.banner != null) {
				String problem = checkBanner(def.banner);
				if (problem != null) {
					warnings.add("item '" + def.id + "': " + problem + ": vanilla look");
					def.banner = null;
				}
			}
			valid.put(def.id, def);
		}
		byId = valid;
	}

	private static @Nullable String checkAttribute(@Nullable AttributeEntry attribute) {
		if (attribute == null) {
			return "empty attribute";
		}
		attribute.attribute = normalize(attribute.attribute);
		if (!RESOURCE.matcher(attribute.attribute).matches()) {
			return "invalid attribute '" + attribute.attribute + "'";
		}
		if (!Double.isFinite(attribute.amount)) {
			return "attribute '" + attribute.attribute + "': invalid amount";
		}
		attribute.operation = attribute.operation == null ? "" : attribute.operation.trim().toLowerCase(Locale.ROOT);
		if (!OPERATIONS.contains(attribute.operation)) {
			return "attribute '" + attribute.attribute + "': unknown operation '" + attribute.operation + "'";
		}
		attribute.slot = attribute.slot == null ? "" : attribute.slot.trim().toLowerCase(Locale.ROOT);
		if (!SLOTS.contains(attribute.slot)) {
			return "attribute '" + attribute.attribute + "': unknown slot '" + attribute.slot + "'";
		}
		return null;
	}

	private void rebuildDrops(List<String> warnings) {
		if (drops == null) {
			drops = defaultDrops();
		}
		Map<String, DropTable> fixed = new LinkedHashMap<>();
		for (Map.Entry<String, DropTable> entry : drops.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			String source = entry.getKey().trim().toLowerCase(Locale.ROOT);
			DropTable table = entry.getValue();
			table.chance = clamp(table.chance, 0, 1);
			Map<String, Double> weights = new LinkedHashMap<>();
			if (table.items != null) {
				for (Map.Entry<String, Double> weight : table.items.entrySet()) {
					String id = weight.getKey() == null ? "" : weight.getKey().trim().toLowerCase(Locale.ROOT);
					if (!byId.containsKey(id)) {
						warnings.add("drops." + source + ": unknown item '" + weight.getKey() + "': ignored");
						continue;
					}
					double w = weight.getValue() == null ? 0 : weight.getValue();
					weights.put(id, Double.isFinite(w) && w > 0 ? w : 0);
				}
			}
			table.items = weights;
			if (!SOURCES.contains(source)) {
				warnings.add("drops: source '" + source + "' is never used (" + String.join(", ", SOURCES) + ")");
			}
			fixed.put(source, table);
		}
		drops = fixed;
	}

	private void rebuildPowers() {
		if (powers == null) {
			powers = new Powers();
		}
		Powers p = powers;
		if (p.stormCall == null) {
			p.stormCall = new StormCall();
		}
		p.stormCall.cooldownSeconds = clamp(p.stormCall.cooldownSeconds, 1, 600);
		p.stormCall.range = clamp(p.stormCall.range, 4, 64);
		p.stormCall.radius = clamp(p.stormCall.radius, 1, 12);
		p.stormCall.maxTargets = (int) clamp(p.stormCall.maxTargets, 1, 12);
		p.stormCall.damage = clamp(p.stormCall.damage, 0, 200);
		if (p.frostNova == null) {
			p.frostNova = new FrostNova();
		}
		p.frostNova.cooldownSeconds = clamp(p.frostNova.cooldownSeconds, 1, 600);
		p.frostNova.radius = clamp(p.frostNova.radius, 1, 16);
		p.frostNova.damage = clamp(p.frostNova.damage, 0, 200);
		p.frostNova.slowSeconds = (int) clamp(p.frostNova.slowSeconds, 1, 60);
		p.frostNova.slowLevel = (int) clamp(p.frostNova.slowLevel, 1, 6);
		if (p.blinkStrike == null) {
			p.blinkStrike = new BlinkStrike();
		}
		p.blinkStrike.cooldownSeconds = clamp(p.blinkStrike.cooldownSeconds, 1, 600);
		p.blinkStrike.range = clamp(p.blinkStrike.range, 3, 48);
		p.blinkStrike.damage = clamp(p.blinkStrike.damage, 0, 200);
		if (p.treeFeller == null) {
			p.treeFeller = new TreeFeller();
		}
		p.treeFeller.maxLogs = (int) clamp(p.treeFeller.maxLogs, 1, 512);
		p.treeFeller.logsPerTick = (int) clamp(p.treeFeller.logsPerTick, 1, 64);
		p.treeFeller.minLeaves = (int) clamp(p.treeFeller.minLeaves, 0, 64);
		if (p.excavate == null) {
			p.excavate = new Excavate();
		}
		p.excavate.blocks = cleanIds(p.excavate.blocks);
		if (p.veinMiner == null) {
			p.veinMiner = new VeinMiner();
		}
		p.veinMiner.maxBlocks = (int) clamp(p.veinMiner.maxBlocks, 1, 256);
		p.veinMiner.blocks = cleanIds(p.veinMiner.blocks);
		if (p.doubleJump == null) {
			p.doubleJump = new DoubleJump();
		}
		p.doubleJump.cooldownSeconds = clamp(p.doubleJump.cooldownSeconds, 0, 60);
		p.doubleJump.upward = clamp(p.doubleJump.upward, 0.1, 2);
		p.doubleJump.forward = clamp(p.doubleJump.forward, 0, 2);
		if (p.shockwave == null) {
			p.shockwave = new Shockwave();
		}
		p.shockwave.cooldownSeconds = clamp(p.shockwave.cooldownSeconds, 0, 600);
		p.shockwave.radius = clamp(p.shockwave.radius, 1, 12);
		p.shockwave.damage = clamp(p.shockwave.damage, 0, 200);
		p.shockwave.lift = clamp(p.shockwave.lift, 0, 2);
		if (p.phoenixArrows == null) {
			p.phoenixArrows = new PhoenixArrows();
		}
		p.phoenixArrows.cooldownSeconds = clamp(p.phoenixArrows.cooldownSeconds, 0, 600);
		p.phoenixArrows.radius = clamp(p.phoenixArrows.radius, 1, 12);
		p.phoenixArrows.damage = clamp(p.phoenixArrows.damage, 0, 200);
		p.phoenixArrows.fireSeconds = (int) clamp(p.phoenixArrows.fireSeconds, 0, 60);
		if (p.tideCall == null) {
			p.tideCall = new TideCall();
		}
		p.tideCall.cooldownSeconds = clamp(p.tideCall.cooldownSeconds, 0, 600);
		p.tideCall.damage = clamp(p.tideCall.damage, 0, 200);
		p.tideCall.chainTargets = (int) clamp(p.tideCall.chainTargets, 0, 12);
		p.tideCall.chainRadius = clamp(p.tideCall.chainRadius, 1, 16);
		p.tideCall.chainDamage = clamp(p.tideCall.chainDamage, 0, 200);
		if (p.soulHarvest == null) {
			p.soulHarvest = new SoulHarvest();
		}
		p.soulHarvest.heal = clamp(p.soulHarvest.heal, 0, 40);
		p.soulHarvest.speedSeconds = (int) clamp(p.soulHarvest.speedSeconds, 0, 60);
		rebuildRuinPowers(p);
		rebuildSetPowers(p);
	}

	/** Bounds of the set powers added by format 5. */
	private static void rebuildSetPowers(Powers p) {
		if (p.winterHeart == null) {
			p.winterHeart = new WinterHeart();
		}
		WinterHeart wh = p.winterHeart;
		wh.cooldownSeconds = clamp(wh.cooldownSeconds, 1, 3600);
		wh.healthThreshold = clamp(wh.healthThreshold, 0.05, 0.95);
		wh.radius = clamp(wh.radius, 1, 16);
		wh.damage = clamp(wh.damage, 0, 200);
		wh.freezeSeconds = (int) clamp(wh.freezeSeconds, 0, 60);
		wh.slowLevel = (int) clamp(wh.slowLevel, 1, 6);
		wh.push = clamp(wh.push, 0, 4);
		wh.resistanceSeconds = (int) clamp(wh.resistanceSeconds, 0, 60);
		wh.resistanceLevel = (int) clamp(wh.resistanceLevel, 1, 4);
		wh.absorptionLevel = (int) clamp(wh.absorptionLevel, 0, 5);
		if (p.jaguarAmbush == null) {
			p.jaguarAmbush = new JaguarAmbush();
		}
		JaguarAmbush ja = p.jaguarAmbush;
		ja.cooldownSeconds = clamp(ja.cooldownSeconds, 1, 600);
		ja.camouflageSeconds = clamp(ja.camouflageSeconds, 0.25, 30);
		ja.foliageSeconds = clamp(ja.foliageSeconds, 0.25, ja.camouflageSeconds);
		ja.loseTrackRange = clamp(ja.loseTrackRange, 0, 48);
		ja.strikeWindowSeconds = clamp(ja.strikeWindowSeconds, 0, 30);
		ja.damage = clamp(ja.damage, 0, 200);
		ja.radius = clamp(ja.radius, 0, 10);
		ja.clawDamage = clamp(ja.clawDamage, 0, 200);
		ja.maxTargets = (int) clamp(ja.maxTargets, 0, 24);
		ja.slowSeconds = (int) clamp(ja.slowSeconds, 0, 60);
		ja.slowLevel = (int) clamp(ja.slowLevel, 1, 6);
		if (p.soulPyre == null) {
			p.soulPyre = new SoulPyre();
		}
		SoulPyre sp = p.soulPyre;
		sp.pulseSeconds = clamp(sp.pulseSeconds, 0.5, 60);
		sp.radius = clamp(sp.radius, 1, 12);
		sp.damage = clamp(sp.damage, 0, 200);
		sp.maxTargets = (int) clamp(sp.maxTargets, 1, 24);
		sp.healRatio = clamp(sp.healRatio, 0, 2);
		sp.maxHeal = clamp(sp.maxHeal, 0, 40);
		sp.trailSeconds = clamp(sp.trailSeconds, 0, 10);
		sp.trailDamage = clamp(sp.trailDamage, 0, 200);
		sp.fireSeconds = (int) clamp(sp.fireSeconds, 0, 60);
	}

	/** Recharge of a set power (seconds; the pulse of the soul pyre aura), 0 for a passive one. */
	public double cooldownSeconds(SetPower power) {
		return switch (power) {
			case WINTER_HEART -> powers.winterHeart.cooldownSeconds;
			case JAGUAR_AMBUSH -> powers.jaguarAmbush.cooldownSeconds;
			case SOUL_PYRE -> powers.soulPyre.pulseSeconds;
			default -> 0;
		};
	}

	/** Bounds of the powers added by format 4. */
	private static void rebuildRuinPowers(Powers p) {
		if (p.jaguarPounce == null) {
			p.jaguarPounce = new JaguarPounce();
		}
		JaguarPounce jp = p.jaguarPounce;
		jp.cooldownSeconds = clamp(jp.cooldownSeconds, 1, 600);
		jp.leap = clamp(jp.leap, 0, 2.5);
		jp.lift = clamp(jp.lift, 0, 1.5);
		jp.radius = clamp(jp.radius, 1, 10);
		jp.damage = clamp(jp.damage, 0, 200);
		jp.maxTargets = (int) clamp(jp.maxTargets, 1, 24);
		jp.slowSeconds = (int) clamp(jp.slowSeconds, 0, 60);
		jp.slowLevel = (int) clamp(jp.slowLevel, 1, 6);
		if (p.frostCrown == null) {
			p.frostCrown = new FrostCrown();
		}
		FrostCrown fc = p.frostCrown;
		fc.cooldownSeconds = clamp(fc.cooldownSeconds, 0, 600);
		fc.damage = clamp(fc.damage, 0, 200);
		fc.freezeSeconds = (int) clamp(fc.freezeSeconds, 1, 60);
		fc.slowLevel = (int) clamp(fc.slowLevel, 1, 6);
		fc.range = clamp(fc.range, 2, 64);
		fc.chillRadius = clamp(fc.chillRadius, 0, 12);
		if (p.soulAegis == null) {
			p.soulAegis = new SoulAegis();
		}
		SoulAegis sa = p.soulAegis;
		sa.cooldownSeconds = clamp(sa.cooldownSeconds, 0, 600);
		sa.reflectRatio = clamp(sa.reflectRatio, 0, 5);
		sa.reflectMax = clamp(sa.reflectMax, 0, 200);
		sa.reflectMin = clamp(sa.reflectMin, 0, sa.reflectMax);
		sa.heal = clamp(sa.heal, 0, 40);
		sa.range = clamp(sa.range, 2, 64);
		if (p.solarLeap == null) {
			p.solarLeap = new SolarLeap();
		}
		SolarLeap sl = p.solarLeap;
		sl.cooldownSeconds = clamp(sl.cooldownSeconds, 1, 600);
		sl.upward = clamp(sl.upward, 0.2, 2.5);
		sl.forward = clamp(sl.forward, 0, 2);
		sl.radius = clamp(sl.radius, 1, 12);
		sl.damage = clamp(sl.damage, 0, 200);
		sl.undeadMultiplier = clamp(sl.undeadMultiplier, 1, 5);
		sl.fireSeconds = (int) clamp(sl.fireSeconds, 0, 60);
		if (p.pactSeal == null) {
			p.pactSeal = new PactSeal();
		}
		PactSeal ps = p.pactSeal;
		ps.cooldownSeconds = clamp(ps.cooldownSeconds, 0, 600);
		ps.seals = (int) clamp(ps.seals, 1, 10);
		ps.sealSeconds = (int) clamp(ps.sealSeconds, 1, 120);
		ps.damage = clamp(ps.damage, 0, 200);
		ps.witherSeconds = (int) clamp(ps.witherSeconds, 0, 60);
		ps.spreadRadius = clamp(ps.spreadRadius, 0, 12);
		if (p.infernalRift == null) {
			p.infernalRift = new InfernalRift();
		}
		InfernalRift ir = p.infernalRift;
		ir.cooldownSeconds = clamp(ir.cooldownSeconds, 1, 600);
		ir.length = (int) clamp(ir.length, 2, 32);
		ir.width = clamp(ir.width, 0.5, 4);
		ir.damage = clamp(ir.damage, 0, 200);
		ir.fireSeconds = (int) clamp(ir.fireSeconds, 0, 60);
		ir.lift = clamp(ir.lift, 0, 1.5);
		if (p.jadeGale == null) {
			p.jadeGale = new JadeGale();
		}
		JadeGale jg = p.jadeGale;
		jg.cooldownSeconds = clamp(jg.cooldownSeconds, 1, 3600);
		jg.healthThreshold = clamp(jg.healthThreshold, 0.05, 0.95);
		jg.radius = clamp(jg.radius, 1, 12);
		jg.push = clamp(jg.push, 0, 4);
		jg.poisonSeconds = (int) clamp(jg.poisonSeconds, 0, 60);
		jg.poisonLevel = (int) clamp(jg.poisonLevel, 1, 6);
		jg.regenSeconds = (int) clamp(jg.regenSeconds, 0, 60);
		jg.regenLevel = (int) clamp(jg.regenLevel, 1, 6);
		jg.slowFallingSeconds = (int) clamp(jg.slowFallingSeconds, 0, 60);
	}

	/** Cooldown of an active power (seconds). */
	public double cooldownSeconds(UniquePower power) {
		return switch (power) {
			case STORM_CALL -> powers.stormCall.cooldownSeconds;
			case FROST_NOVA -> powers.frostNova.cooldownSeconds;
			case BLINK_STRIKE -> powers.blinkStrike.cooldownSeconds;
			case DOUBLE_JUMP -> powers.doubleJump.cooldownSeconds;
			case SHOCKWAVE -> powers.shockwave.cooldownSeconds;
			case PHOENIX_ARROWS -> powers.phoenixArrows.cooldownSeconds;
			case TIDE_CALL -> powers.tideCall.cooldownSeconds;
			case JAGUAR_POUNCE -> powers.jaguarPounce.cooldownSeconds;
			case FROST_CROWN -> powers.frostCrown.cooldownSeconds;
			case SOUL_AEGIS -> powers.soulAegis.cooldownSeconds;
			case SOLAR_LEAP -> powers.solarLeap.cooldownSeconds;
			case PACT_SEAL -> powers.pactSeal.cooldownSeconds;
			case INFERNAL_RIFT -> powers.infernalRift.cooldownSeconds;
			case JADE_GALE -> powers.jadeGale.cooldownSeconds;
			default -> 0;
		};
	}

	private static List<String> cleanIds(@Nullable List<String> ids) {
		List<String> out = new ArrayList<>();
		if (ids != null) {
			for (String id : ids) {
				if (id != null && !id.isBlank()) {
					String value = id.trim().toLowerCase(Locale.ROOT);
					boolean tag = value.startsWith("#");
					String bare = tag ? value.substring(1) : value;
					out.add((tag ? "#" : "") + (bare.contains(":") ? bare : "minecraft:" + bare));
				}
			}
		}
		return out;
	}

	/** "Netherite_Sword " -> "minecraft:netherite_sword". */
	static String normalize(@Nullable String id) {
		if (id == null) {
			return "";
		}
		String value = id.trim().toLowerCase(Locale.ROOT);
		return value.isEmpty() || value.contains(":") ? value : "minecraft:" + value;
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}

	/**
	 * Format 2: the ruin boss drop tables are added to files written before them (only when absent: a table removed or
	 * edited by the admin afterwards is never touched again, the version is saved). Format 3: the crossbow got its
	 * texture, its model is set when still the old default (empty). Format 4: the seven uniques of the ruins are added to
	 * the item list and to the existing ruin_boss / nether_ruin_boss / legendary_quest tables, only where absent (a
	 * definition or an entry removed after the migration stays removed; the favorites are a new section, defaults when
	 * absent).
	 */
	void migrate() {
		if (configVersion < 2) {
			if (drops == null) {
				drops = defaultDrops();
			}
			Map<String, DropTable> defaults = defaultDrops();
			for (String source : RUIN_SOURCES) {
				drops.putIfAbsent(source, defaults.get(source));
			}
		}
		if (configVersion < 3 && items != null) {
			for (UniqueDef def : items) {
				if (def != null && "arbalete_du_traqueur".equals(def.id) && (def.model == null || def.model.isBlank())) {
					def.model = "arbalete_du_traqueur";
				}
			}
		}
		if (configVersion < 4) {
			addRuinUniques();
		}
		if (configVersion < 5) {
			addSetUniques();
		}
	}

	/**
	 * Format 5: the twelve set pieces (definitions absent from the file), the three set definitions (absent ids), their
	 * entries in the legendary_quest table and in the favourites of their guardians (only where absent: a definition, a set
	 * or an entry removed after the migration stays removed; a table removed by the admin is never added back; a favourite
	 * key of a guardian missing from the file gets only the set pieces).
	 */
	private void addSetUniques() {
		if (items != null) {
			Set<String> present = new java.util.HashSet<>();
			for (UniqueDef def : items) {
				if (def != null && def.id != null) {
					present.add(def.id.trim().toLowerCase(Locale.ROOT));
				}
			}
			for (UniqueDef def : defaultItems()) {
				if (SET_UNIQUES.contains(def.id) && !present.contains(def.id)) {
					items.add(def);
				}
			}
		}
		if (sets != null) {
			Set<String> present = new java.util.HashSet<>();
			for (SetDef set : sets) {
				if (set != null && set.id != null) {
					present.add(set.id.trim().toLowerCase(Locale.ROOT));
				}
			}
			for (SetDef set : defaultSets()) {
				if (!present.contains(set.id)) {
					sets.add(set);
				}
			}
		}
		if (drops != null) {
			Map<String, DropTable> defaults = defaultDrops();
			for (Map.Entry<String, DropTable> entry : drops.entrySet()) {
				DropTable table = entry.getValue();
				DropTable reference = entry.getKey() == null ? null : defaults.get(entry.getKey().trim().toLowerCase(Locale.ROOT));
				if (table == null || reference == null) {
					continue;
				}
				if (table.items == null) {
					table.items = new LinkedHashMap<>();
				}
				for (String id : SET_UNIQUES) {
					Double weight = reference.items.get(id);
					if (weight != null) {
						table.items.putIfAbsent(id, weight);
					}
				}
			}
		}
		if (favorites != null) {
			for (Map.Entry<String, Map<String, Double>> entry : defaultFavorites().entrySet()) {
				Map<String, Double> pieces = new LinkedHashMap<>();
				for (Map.Entry<String, Double> weight : entry.getValue().entrySet()) {
					if (SET_UNIQUES.contains(weight.getKey())) {
						pieces.put(weight.getKey(), weight.getValue());
					}
				}
				if (pieces.isEmpty()) {
					continue;
				}
				Map<String, Double> existing = favorites.get(entry.getKey());
				if (existing == null) {
					favorites.put(entry.getKey(), pieces);
				} else {
					pieces.forEach(existing::putIfAbsent);
				}
			}
		}
	}

	/** Format 4: the new definitions and their drop table entries, only where absent. */
	private void addRuinUniques() {
		if (items != null) {
			Set<String> present = new java.util.HashSet<>();
			for (UniqueDef def : items) {
				if (def != null && def.id != null) {
					present.add(def.id.trim().toLowerCase(Locale.ROOT));
				}
			}
			for (UniqueDef def : defaultItems()) {
				if (RUIN_UNIQUES.contains(def.id) && !present.contains(def.id)) {
					items.add(def);
				}
			}
		}
		if (drops != null) {
			Map<String, DropTable> defaults = defaultDrops();
			for (String source : List.of("ruin_boss", "nether_ruin_boss", "legendary_quest")) {
				DropTable table = drops.get(source);
				DropTable reference = defaults.get(source);
				if (table == null || reference == null) {
					continue;
				}
				if (table.items == null) {
					table.items = new LinkedHashMap<>();
				}
				for (String id : RUIN_UNIQUES) {
					Double weight = reference.items.get(id);
					if (weight != null) {
						table.items.putIfAbsent(id, weight);
					}
				}
			}
		}
	}

	// ================================================================== defaults

	public static Map<String, DropTable> defaultDrops() {
		Map<String, DropTable> map = new LinkedHashMap<>();
		map.put("boss", new DropTable(0.04, true)
				.add("coutelas_du_contrebandier", 10).add("bottes_du_coursier", 10).add("pic_du_filon", 8)
				.add("croc_de_givre", 6).add("hache_sylvestre", 6).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 4)
				.add("foudrelame", 2).add("pioche_du_titan", 2).add("bottes_de_zephyr", 2).add("arc_du_phenix", 2)
				.add("marteau_du_seisme", 2));
		map.put("blood_boss", new DropTable(0.2, true)
				.add("croc_de_givre", 6).add("hache_sylvestre", 5).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 5)
				.add("foudrelame", 5).add("pioche_du_titan", 4).add("bottes_de_zephyr", 4).add("arc_du_phenix", 4)
				.add("marteau_du_seisme", 4).add("trident_du_leviathan", 2).add("faux_des_ames", 2));
		map.put("quest", new DropTable(0.01, true)
				.add("coutelas_du_contrebandier", 10).add("bottes_du_coursier", 10).add("pic_du_filon", 8)
				.add("croc_de_givre", 4).add("hache_sylvestre", 4).add("arbalete_du_traqueur", 3));
		map.put("legendary_quest", new DropTable(0.35, true)
				.add("croc_de_givre", 5).add("hache_sylvestre", 5).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 5)
				.add("foudrelame", 4).add("pioche_du_titan", 4).add("bottes_de_zephyr", 4).add("arc_du_phenix", 4)
				.add("marteau_du_seisme", 4).add("trident_du_leviathan", 1).add("faux_des_ames", 1)
				// The uniques of the ruins: rarely in a legendary contract.
				.add("lance_du_jaguar_celeste", 1).add("couronne_du_roi_de_givre", 1).add("egide_des_ames", 1).add("masse_du_zenith", 1)
				.add("hache_du_seigneur_de_guerre", 1).add("plastron_du_serpent", 1).add("lame_du_pacte", 0.5));
		// The armor set pieces: rarely in a legendary contract (their guardians favour them, see defaultFavorites).
		for (String id : SET_UNIQUES) {
			map.get("legendary_quest").add(id, 0.5);
		}
		map.put("expedition", new DropTable(0.02, true)
				.add("coutelas_du_contrebandier", 10).add("bottes_du_coursier", 10).add("pic_du_filon", 8)
				.add("hache_sylvestre", 4).add("croc_de_givre", 3).add("arbalete_du_traqueur", 3).add("bottes_de_zephyr", 1));
		map.put("ruin_boss", new DropTable(0.12, true)
				.add("croc_de_givre", 6).add("hache_sylvestre", 5).add("arbalete_du_traqueur", 5).add("murmure_du_neant", 5)
				.add("foudrelame", 4).add("pioche_du_titan", 4).add("bottes_de_zephyr", 4).add("arc_du_phenix", 4)
				.add("marteau_du_seisme", 4).add("trident_du_leviathan", 2).add("faux_des_ames", 1)
				// The uniques of the ruins (each one favoured by its own guardian, see defaultFavorites).
				.add("couronne_du_roi_de_givre", 3).add("plastron_du_serpent", 3).add("masse_du_zenith", 2).add("lance_du_jaguar_celeste", 2)
				.add("lame_du_pacte", 0.5));
		map.put("nether_ruin_boss", new DropTable(0.25, true)
				.add("murmure_du_neant", 5).add("foudrelame", 5).add("arc_du_phenix", 5).add("faux_des_ames", 4)
				.add("pioche_du_titan", 4).add("bottes_de_zephyr", 4).add("marteau_du_seisme", 4).add("trident_du_leviathan", 2)
				.add("hache_du_seigneur_de_guerre", 4).add("egide_des_ames", 3).add("lame_du_pacte", 1));
		map.put("trade", new DropTable(1.0, false)
				.add("croc_de_givre", 6).add("hache_sylvestre", 6).add("arbalete_du_traqueur", 6).add("murmure_du_neant", 5)
				.add("pic_du_filon", 5).add("foudrelame", 3).add("pioche_du_titan", 3).add("bottes_de_zephyr", 3)
				.add("arc_du_phenix", 3).add("marteau_du_seisme", 3));
		return map;
	}

	/**
	 * Each new unique favoured by the guardian of its ruin; the cursed blade by every cursed guardian; the pieces of each
	 * armor set by the guardians of its ruins (they never drop from the plain tables of the ruins: only there, and rarely
	 * from a legendary contract).
	 */
	public static Map<String, Map<String, Double>> defaultFavorites() {
		Map<String, Map<String, Double>> map = new LinkedHashMap<>();
		map.put("ruin_boss@grand_temple_maya", new LinkedHashMap<>(Map.of("lance_du_jaguar_celeste", 25.0)));
		map.put("ruin_boss@bastion_de_givre", new LinkedHashMap<>(Map.of("couronne_du_roi_de_givre", 25.0)));
		map.put("ruin_boss@temple_maya", new LinkedHashMap<>(Map.of("plastron_du_serpent", 25.0)));
		map.put("ruin_boss@ziggourat_des_sables", new LinkedHashMap<>(Map.of("masse_du_zenith", 25.0)));
		map.put("nether_ruin_boss@citadelle_des_ames", new LinkedHashMap<>(Map.of("egide_des_ames", 25.0)));
		map.put("nether_ruin_boss@autel_infernal", new LinkedHashMap<>(Map.of("hache_du_seigneur_de_guerre", 25.0)));
		map.put("ruin_boss@cursed", new LinkedHashMap<>(Map.of("lame_du_pacte", 30.0)));
		map.put("nether_ruin_boss@cursed", new LinkedHashMap<>(Map.of("lame_du_pacte", 30.0)));
		addSetFavorites(map, "ruin_boss@bastion_de_givre", "hiver_eternel", 20);
		addSetFavorites(map, "ruin_boss@grand_temple_maya", "guerrier_jaguar", 20);
		addSetFavorites(map, "ruin_boss@temple_maya", "guerrier_jaguar", 10);
		addSetFavorites(map, "nether_ruin_boss@citadelle_des_ames", "brasier_des_ames", 20);
		addSetFavorites(map, "nether_ruin_boss@autel_infernal", "brasier_des_ames", 10);
		return map;
	}

	/** The four pieces of a default set (SET_UNIQUES, in order) favoured by a guardian with this weight each. */
	private static void addSetFavorites(Map<String, Map<String, Double>> map, String key, String set, double weight) {
		int index = SET_IDS.indexOf(set);
		for (String id : SET_UNIQUES.subList(index * 4, index * 4 + 4)) {
			map.get(key).put(id, weight);
		}
	}

	/** The three armor sets of the ruins (format 5): a passive bonus with two pieces, a power with four. */
	public static List<SetDef> defaultSets() {
		List<SetDef> list = new ArrayList<>();
		list.add(setDef("hiver_eternel", "&bPanoplie de l'Hiver éternel")
				.bonus(2, "Sang-froid", SetPower.FROST_BLOOD, bonusAttr("minecraft:armor_toughness", 2, "add_value"))
				.bonus(4, "Cœur de l'hiver", SetPower.WINTER_HEART, bonusAttr("minecraft:max_health", 4, "add_value")));
		list.add(setDef("guerrier_jaguar", "&6Panoplie du Guerrier jaguar")
				.bonus(2, "Agilité du jaguar", null, bonusAttr("minecraft:movement_speed", 0.1, "add_multiplied_base"),
						bonusAttr("minecraft:safe_fall_distance", 3, "add_value"))
				.bonus(4, "Embuscade du jaguar", SetPower.JAGUAR_AMBUSH, bonusAttr("minecraft:attack_damage", 1, "add_value")));
		list.add(setDef("brasier_des_ames", "&3Panoplie du Brasier des âmes")
				.bonus(2, "Flamme d'âme", null, bonusAttr("minecraft:max_health", 4, "add_value"),
						bonusAttr("minecraft:burning_time", -0.5, "add_multiplied_base"))
				.bonus(4, "Bûcher des âmes", SetPower.SOUL_PYRE, bonusAttr("minecraft:armor_toughness", 2, "add_value")));
		return list;
	}

	private static SetDef setDef(String id, String name) {
		SetDef set = new SetDef();
		set.id = id;
		set.name = name;
		return set;
	}

	public static List<UniqueDef> defaultItems() {
		List<UniqueDef> list = new ArrayList<>();
		list.add(def("foudrelame", "netherite_sword", "Foudrelame", UniqueRarity.LEGENDARY, UniquePower.STORM_CALL, true)
				.story("Forgée sous l'orage au sommet du monde,", "elle gronde encore quand le ciel s'assombrit.")
				.ench("minecraft:sharpness", 6).ench("guildy:foudroiement", 3).ench("minecraft:looting", 3).ench("minecraft:unbreaking", 3)
				.attr("minecraft:attack_damage", 2, "add_value", "mainhand"));
		list.add(def("croc_de_givre", "diamond_sword", "Croc-de-Givre", UniqueRarity.EPIC, UniquePower.FROST_NOVA, false)
				.story("Taillée dans la glace d'un glacier", "qui n'a jamais connu le soleil.")
				.ench("minecraft:sharpness", 5).ench("guildy:givre", 2).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1)
				.attr("minecraft:attack_speed", 0.2, "add_value", "mainhand"));
		list.add(def("murmure_du_neant", "netherite_sword", "Murmure du Néant", UniqueRarity.LEGENDARY, UniquePower.BLINK_STRIKE, true)
				.story("Un poignard que nul n'entend venir.", "Celui qui le tient marche entre les ombres.")
				.ench("minecraft:sharpness", 5).ench("guildy:echo", 3).ench("minecraft:looting", 2)
				.attr("minecraft:attack_speed", 0.6, "add_value", "mainhand")
				.attr("minecraft:movement_speed", 0.1, "add_multiplied_base", "mainhand"));
		list.add(def("hache_sylvestre", "netherite_axe", "Hache de l'Ancien Forestier", UniqueRarity.EPIC, UniquePower.TREE_FELLER, false)
				.story("Les arbres eux-mêmes s'inclinent devant elle.", "Un coup, et la forêt cède.")
				.ench("minecraft:efficiency", 5).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1));
		list.add(def("pioche_du_titan", "netherite_pickaxe", "Pioche du Titan", UniqueRarity.LEGENDARY, UniquePower.EXCAVATE, false)
				.story("Forgée pour creuser le cœur des montagnes,", "elle ouvre des galeries entières d'un seul coup.")
				.ench("minecraft:efficiency", 6).ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:block_interaction_range", 1, "add_value", "mainhand"));
		list.add(def("pic_du_filon", "diamond_pickaxe", "Pic du Filon", UniqueRarity.RARE, UniquePower.VEIN_MINER, false)
				.story("Le vieux mineur jurait qu'il sentait le minerai.", "Il avait raison.")
				.ench("minecraft:efficiency", 4).ench("minecraft:fortune", 2).ench("minecraft:unbreaking", 3));
		list.add(def("bottes_de_zephyr", "netherite_boots", "Bottes de Zéphyr", UniqueRarity.LEGENDARY, UniquePower.DOUBLE_JUMP, true)
				.story("Cousues avec le vent du nord :", "qui les porte ne touche plus vraiment le sol.")
				.ench("minecraft:protection", 4).ench("minecraft:feather_falling", 4).ench("guildy:apesanteur", 1)
				.ench("minecraft:depth_strider", 3)
				.attr("minecraft:movement_speed", 0.1, "add_multiplied_base", "feet"));
		list.add(def("bottes_du_coursier", "leather_boots", "Bottes du Coursier", UniqueRarity.RARE, null, true)
				.story("Elles ont porté les messages de cent guildes", "sans jamais user leur cuir.")
				.ench("minecraft:feather_falling", 3).ench("minecraft:soul_speed", 2)
				.attr("minecraft:movement_speed", 0.2, "add_multiplied_base", "feet")
				.attr("minecraft:step_height", 0.5, "add_value", "feet"));
		list.getLast().color = 0x3A7D44;
		list.add(def("marteau_du_seisme", "mace", "Marteau du Séisme", UniqueRarity.LEGENDARY, UniquePower.SHOCKWAVE, false)
				.story("Quand il frappe le sol,", "la terre se souvient.")
				.ench("minecraft:density", 5).ench("minecraft:breach", 2).ench("guildy:seisme", 2).ench("minecraft:unbreaking", 3)
				.ench("minecraft:mending", 1));
		list.add(def("arc_du_phenix", "bow", "Arc du Phénix", UniqueRarity.LEGENDARY, UniquePower.PHOENIX_ARROWS, true)
				.story("Ses flèches renaissent en flammes", "là où elles frappent.")
				.ench("minecraft:power", 5).ench("minecraft:flame", 1).ench("minecraft:infinity", 1).ench("guildy:brasier", 2));
		list.add(def("arbalete_du_traqueur", "crossbow", "Arbalète du Traqueur", UniqueRarity.EPIC, null, false)
				.story("Ce qu'elle marque", "ne lui échappe jamais.")
				.ench("minecraft:quick_charge", 3).ench("minecraft:multishot", 1).ench("minecraft:piercing", 4)
				.ench("guildy:marque", 2).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1));
		list.add(def("trident_du_leviathan", "trident", "Trident du Léviathan", UniqueRarity.MYTHIC, UniquePower.TIDE_CALL, true)
				.story("Arraché aux abysses par un marin oublié,", "il commande encore aux tempêtes.")
				// No vanilla Channeling: its storm bolts are real ones (fire). Its lightning is the power's visual one.
				.ench("minecraft:impaling", 5).ench("minecraft:loyalty", 3).ench("guildy:foudroiement", 3).ench("guildy:echo", 2)
				.attr("minecraft:attack_damage", 3, "add_value", "mainhand"));
		list.add(def("faux_des_ames", "netherite_hoe", "Faux des Âmes", UniqueRarity.MYTHIC, UniquePower.SOUL_HARVEST, true)
				.story("Chaque âme fauchée nourrit son porteur.", "Nul ne sait qui l'a forgée, ni s'il vit encore.")
				.ench("minecraft:sharpness", 5).ench("guildy:vampirisme", 3).ench("guildy:moisson", 3).ench("minecraft:looting", 3)
				.attr("minecraft:attack_damage", 8, "add_value", "mainhand")
				.attr("minecraft:attack_speed", -2.6, "add_value", "mainhand")
				.attr("minecraft:entity_interaction_range", 1, "add_value", "mainhand"));
		list.add(def("coutelas_du_contrebandier", "iron_sword", "Coutelas du Contrebandier", UniqueRarity.RARE, null, false)
				.story("Trouvé dans la cale d'un navire échoué,", "il sent encore le sel et l'or volé.")
				.ench("minecraft:sharpness", 3).ench("minecraft:looting", 3).ench("guildy:echo", 1).ench("minecraft:unbreaking", 3)
				.attr("minecraft:attack_speed", 0.4, "add_value", "mainhand")
				.attr("minecraft:luck", 1, "add_value", "mainhand"));
		list.addAll(ruinItems());
		list.addAll(setItems());
		return list;
	}

	/**
	 * The twelve pieces of the three armor sets (format 5): good armors on their own (enchantments, attributes), no power of
	 * their own (the power comes from the set, see {@link #defaultSets}). The jaguar warrior's gold is unbreakable (golden
	 * armor wears out too fast for a unique).
	 */
	private static List<UniqueDef> setItems() {
		List<UniqueDef> list = new ArrayList<>();
		// Hiver éternel: frost-steel plates of the Bastion de givre (diamond).
		list.add(piece("heaume_hiver_eternel", "diamond_helmet", "Heaume de l'Hiver éternel", UniqueRarity.EPIC, "hiver_eternel", false)
				.story("Sa visière n'a jamais dégelé :", "le regard qui s'y cache non plus.")
				.ench("minecraft:protection", 4).ench("minecraft:respiration", 3).ench("minecraft:aqua_affinity", 1)
				.ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1)
				.attr("minecraft:armor_toughness", 1, "add_value", "head"));
		list.add(piece("cuirasse_hiver_eternel", "diamond_chestplate", "Cuirasse de l'Hiver éternel", UniqueRarity.EPIC, "hiver_eternel", false)
				.story("Forgée dans la glace du Bastion,", "elle garde au cœur un hiver qui ne finit pas.")
				.ench("minecraft:protection", 4).ench("minecraft:thorns", 2).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1)
				.attr("minecraft:max_health", 2, "add_value", "chest"));
		list.add(piece("jambieres_hiver_eternel", "diamond_leggings", "Jambières de l'Hiver éternel", UniqueRarity.EPIC, "hiver_eternel", false)
				.story("Le givre qui les couvre", "éteint les flammes qui s'en approchent.")
				.ench("minecraft:protection", 4).ench("minecraft:fire_protection", 3).ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1)
				.attr("minecraft:knockback_resistance", 0.1, "add_value", "legs"));
		list.add(piece("solerets_hiver_eternel", "diamond_boots", "Solerets de l'Hiver éternel", UniqueRarity.EPIC, "hiver_eternel", false)
				.story("Chaque pas fige l'eau", "comme le souffle du Roi de givre.")
				.ench("minecraft:protection", 4).ench("minecraft:frost_walker", 2).ench("minecraft:feather_falling", 4)
				.ench("minecraft:unbreaking", 3).ench("minecraft:mending", 1)
				.attr("minecraft:movement_speed", 0.05, "add_multiplied_base", "feet"));
		// Guerrier jaguar: gold, spotted pelt and jade of the Mayan temples (gold, unbreakable).
		list.add(piece("masque_guerrier_jaguar", "golden_helmet", "Masque du Guerrier jaguar", UniqueRarity.EPIC, "guerrier_jaguar", true)
				.story("Le guerrier regarde le monde", "par la gueule ouverte du jaguar.")
				.ench("minecraft:protection", 4).ench("minecraft:projectile_protection", 3)
				.attr("minecraft:armor", 2, "add_value", "head")
				.attr("minecraft:armor_toughness", 1, "add_value", "head"));
		list.add(piece("pectoral_guerrier_jaguar", "golden_chestplate", "Pectoral du Guerrier jaguar", UniqueRarity.EPIC, "guerrier_jaguar", true)
				.story("Or des prêtres, jade du fleuve,", "peau du jaguar céleste.")
				.ench("minecraft:protection", 4).ench("minecraft:thorns", 1)
				.attr("minecraft:armor", 3, "add_value", "chest")
				.attr("minecraft:armor_toughness", 1, "add_value", "chest")
				.attr("minecraft:attack_damage", 1, "add_value", "chest"));
		list.add(piece("jambieres_guerrier_jaguar", "golden_leggings", "Jambières du Guerrier jaguar", UniqueRarity.EPIC, "guerrier_jaguar", true)
				.story("Le guerrier jaguar ne fait aucun bruit", "quand il se glisse sous les feuilles.")
				.ench("minecraft:protection", 4).ench("minecraft:swift_sneak", 3)
				.attr("minecraft:armor", 3, "add_value", "legs")
				.attr("minecraft:armor_toughness", 1, "add_value", "legs"));
		list.add(piece("sandales_guerrier_jaguar", "golden_boots", "Sandales du Guerrier jaguar", UniqueRarity.EPIC, "guerrier_jaguar", true)
				.story("Lacées d'or et de jade,", "elles bondissent de branche en branche.")
				.ench("minecraft:protection", 4).ench("minecraft:feather_falling", 4)
				.attr("minecraft:armor", 2, "add_value", "feet")
				.attr("minecraft:armor_toughness", 1, "add_value", "feet")
				.attr("minecraft:jump_strength", 0.08, "add_value", "feet"));
		// Brasier des âmes: blackened soul-steel of the Citadelle des âmes (netherite).
		list.add(piece("heaume_brasier_des_ames", "netherite_helmet", "Heaume du Brasier des âmes", UniqueRarity.LEGENDARY, "brasier_des_ames", false)
				.story("Ses yeux brûlent d'un feu bleu", "qui ne réchauffe personne.")
				.ench("minecraft:protection", 4).ench("minecraft:respiration", 3).ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:max_health", 2, "add_value", "head"));
		list.add(piece("cuirasse_brasier_des_ames", "netherite_chestplate", "Cuirasse du Brasier des âmes", UniqueRarity.LEGENDARY, "brasier_des_ames",
				false)
				.story("Le Champion des âmes y enfermait", "les esprits des guerriers vaincus.")
				.ench("minecraft:protection", 4).ench("minecraft:thorns", 3).ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:attack_damage", 1, "add_value", "chest"));
		list.add(piece("greves_brasier_des_ames", "netherite_leggings", "Grèves du Brasier des âmes", UniqueRarity.LEGENDARY, "brasier_des_ames", false)
				.story("Des flammes d'âme courent", "dans les fissures du métal noir.")
				.ench("minecraft:protection", 4).ench("minecraft:fire_protection", 4).ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:knockback_resistance", 0.1, "add_value", "legs"));
		list.add(piece("bottes_brasier_des_ames", "netherite_boots", "Bottes du Brasier des âmes", UniqueRarity.LEGENDARY, "brasier_des_ames", false)
				.story("Elles laissent derrière elles", "une traînée de flammes bleues.")
				.ench("minecraft:protection", 4).ench("minecraft:soul_speed", 3).ench("minecraft:feather_falling", 4)
				.ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:movement_speed", 0.05, "add_multiplied_base", "feet"));
		return list;
	}

	private static UniqueDef piece(String id, String item, String name, UniqueRarity rarity, String set, boolean unbreakable) {
		UniqueDef def = def(id, item, name, rarity, null, unbreakable);
		def.set = set;
		return def;
	}

	/** The seven uniques of the ruins (format 4): one per themed guardian, the cursed blade of the cursed guardians. */
	private static List<UniqueDef> ruinItems() {
		List<UniqueDef> list = new ArrayList<>();
		list.add(def("lance_du_jaguar_celeste", "netherite_spear", "Lance du Jaguar céleste", UniqueRarity.LEGENDARY, UniquePower.JAGUAR_POUNCE,
				true)
				.story("Taillée dans un croc du Jaguar céleste,", "elle bondit avant même que la main ne frappe.")
				.ench("minecraft:sharpness", 6).ench("guildy:marque", 2).ench("minecraft:looting", 3).ench("minecraft:unbreaking", 3)
				.attr("minecraft:attack_damage", 2, "add_value", "mainhand")
				.attr("minecraft:movement_speed", 0.1, "add_multiplied_base", "mainhand"));
		list.add(def("couronne_du_roi_de_givre", "diamond_helmet", "Couronne du Roi de givre", UniqueRarity.EPIC, UniquePower.FROST_CROWN, false)
				.story("Le Roi de givre la portait sur son trône de glace ;", "le froid lui obéit encore.")
				.ench("minecraft:protection", 5).ench("minecraft:respiration", 3).ench("minecraft:aqua_affinity", 1)
				.ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:max_health", 4, "add_value", "head")
				.attr("minecraft:burning_time", -0.5, "add_multiplied_base", "head"));
		UniqueDef aegis = def("egide_des_ames", "shield", "Égide des Âmes", UniqueRarity.LEGENDARY, UniquePower.SOUL_AEGIS, true)
				.story("Forgée dans la Citadelle des âmes,", "elle rend chaque coup à celui qui l'a porté.")
				.ench("minecraft:thorns", 3).ench("minecraft:unbreaking", 3)
				.attr("minecraft:armor", 2, "add_value", "hand")
				.attr("minecraft:knockback_resistance", 0.25, "add_value", "hand")
				.attr("minecraft:max_health", 4, "add_value", "hand");
		aegis.banner = new Banner("black").layer("minecraft:flow", "cyan").layer("minecraft:skull", "light_blue")
				.layer("minecraft:gradient", "black").layer("minecraft:border", "cyan");
		list.add(aegis);
		list.add(def("masse_du_zenith", "mace", "Masse du Zénith", UniqueRarity.LEGENDARY, UniquePower.SOLAR_LEAP, true)
				.story("Les prêtres de la ziggourat y ont enfermé", "le dernier rayon d'un soleil mourant.")
				.ench("minecraft:density", 4).ench("minecraft:smite", 6).ench("minecraft:fire_aspect", 3).ench("guildy:brasier", 2)
				.ench("minecraft:unbreaking", 3)
				.attr("minecraft:attack_damage", 2, "add_value", "mainhand"));
		list.add(def("lame_du_pacte", "netherite_sword", "Lame du Pacte", UniqueRarity.MYTHIC, UniquePower.PACT_SEAL, true)
				.story("Scellée dans le sang d'un rituel maudit,", "elle réclame les âmes qu'on lui a promises.")
				.ench("minecraft:sharpness", 7).ench("guildy:vampirisme", 2).ench("guildy:echo", 2).ench("minecraft:sweeping_edge", 4)
				.ench("minecraft:looting", 3)
				.attr("minecraft:attack_damage", 3, "add_value", "mainhand")
				.attr("minecraft:attack_speed", 0.2, "add_value", "mainhand"));
		list.add(def("hache_du_seigneur_de_guerre", "netherite_axe", "Hache du Seigneur de guerre", UniqueRarity.EPIC, UniquePower.INFERNAL_RIFT,
				false)
				.story("Arrachée au Seigneur de guerre de l'Autel infernal,", "elle fend la roche jusqu'au magma.")
				.ench("minecraft:sharpness", 5).ench("guildy:brasier", 2).ench("minecraft:fire_aspect", 2).ench("minecraft:efficiency", 5)
				.ench("minecraft:unbreaking", 4).ench("minecraft:mending", 1)
				.attr("minecraft:attack_damage", 1, "add_value", "mainhand")
				.attr("minecraft:attack_knockback", 0.5, "add_value", "mainhand"));
		list.add(def("plastron_du_serpent", "leather_chestplate", "Plastron du Serpent à plumes", UniqueRarity.EPIC, UniquePower.JADE_GALE,
				false)
				.story("Tressé de plumes de jade par les prêtres du temple,", "le souffle du Serpent veille sur qui le porte.")
				.ench("minecraft:protection", 5).ench("minecraft:projectile_protection", 4).ench("minecraft:unbreaking", 4)
				.ench("minecraft:mending", 1)
				.attr("minecraft:armor", 5, "add_value", "chest")
				.attr("minecraft:armor_toughness", 3, "add_value", "chest")
				.attr("minecraft:knockback_resistance", 0.1, "add_value", "chest"));
		list.getLast().color = 0x2F9E6A;
		return list;
	}

	private static UniqueDef def(String id, String item, String name, UniqueRarity rarity, @Nullable UniquePower power, boolean unbreakable) {
		UniqueDef def = new UniqueDef();
		def.id = id;
		def.item = "minecraft:" + item;
		def.name = name;
		def.rarity = rarity.name();
		def.power = power == null ? "" : power.id;
		def.unbreakable = unbreakable;
		def.model = id;
		return def;
	}
}
