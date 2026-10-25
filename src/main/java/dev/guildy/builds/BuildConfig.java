package dev.guildy.builds;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;

import java.util.ArrayList;
import java.util.List;

/**
 * builds.json5 - construction sites ("chantiers") built by the guild's villagers from schematics. Must stay free of
 * Minecraft classes (unit tests).
 */
@Comment({
		"Guildy - chantiers : les villageois batisseurs construisent des schematiques (.schem, .litematic, .nbt).",
		"Fichiers : config/guildy/schematics/ (sous-dossiers permis) et structures sauvegardees avec un bloc de structure.",
		"Rechargement a chaud : /guild admin reload"
})
public final class BuildConfig implements Validatable {
	public static final int CURRENT_VERSION = 1;

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Lecture des fichiers de schematique")
	public Schematics schematics = new Schematics();

	@Comment("Chantiers d'une guilde")
	public Projects projects = new Projects();

	@Comment("Apercu avant validation (visible seulement par le joueur qui place le plan)")
	public Preview preview = new Preview();

	@Comment("Villageois batisseurs (tache 'Batisseur')")
	public Builder builder = new Builder();

	@Comment({
			"Terrassement intelligent : la ou un bloc doit etre pose, le bloc naturel present est retire (son butin va dans le sac",
			"du batisseur puis au stockage) ; les blocs poses par des joueurs, les coffres et les blocs a donnees ne sont jamais",
			"casses (sauf option du chantier 'remplacer les blocs construits' : jamais les coffres ni les blocs a donnees)."
	})
	public Terraform terraform = new Terraform();

	@Comment("Materiaux")
	public Materials materials = new Materials();

	@Comment("Fondations (option du chantier) : sous la couche du bas, jusqu'au sol")
	public Foundations foundations = new Foundations();

	@Comment("Barre de progression et fin de chantier")
	public Display display = new Display();

	@Json5.Section
	public static final class Schematics {
		@Comment("Taille maximum d'un cote (blocs, 1 a 1024)")
		public int maxAxis = 256;
		@Comment({
				"Volume maximum de la boite (blocs, air compris ; 1 a 64 000 000) : une schematique chargee occupe environ",
				"7 octets de memoire par bloc de sa boite (2 097 152 = 15 Mo, 20 000 000 = 140 Mo)"
		})
		public int maxVolume = 2_097_152;
		@Comment("Nombre maximum de blocs (hors air ; 1 a 16 000 000)")
		public int maxBlocks = 500_000;
		@Comment("Taille maximum du fichier (Mo, 1 a 1024)")
		public int maxFileMegabytes = 64;
		@Comment("Taille maximum des donnees decompressees (Mo, 1 a 4096) : protege contre les fichiers pieges")
		public int maxDataMegabytes = 256;
		@Comment("Mettre a jour les anciens fichiers (DataVersion plus ancienne) avec le DataFixer de Minecraft")
		public boolean upgradeOldFiles = true;
		@Comment("Lister aussi les structures sauvegardees en jeu avec un bloc de structure (<monde>/generated/.../structure/*.nbt)")
		public boolean listWorldStructures = true;
		@Comment("Schematiques analysees gardees en memoire (1 a 64 ; a baisser avec de tres grandes schematiques)")
		public int cacheSize = 8;
	}

	@Json5.Section
	public static final class Projects {
		@Comment("Chantiers actifs (non termines) par guilde au niveau 1")
		public int maxActive = 2;
		@Comment("Un chantier actif de plus tous les N niveaux de guilde (0 = jamais)")
		public int extraEveryLevels = 5;
		@Comment("Plafond de chantiers actifs par guilde")
		public int maxActiveCap = 8;
		@Comment("Chantiers termines gardes dans la liste (les plus anciens sont retires)")
		public int keepFinished = 10;
		@Comment("Le chantier doit etre entierement dans le territoire de la guilde")
		public boolean insideTerritory = true;
		@Comment("Mode de l'interieur par defaut : keep (garder le terrain), hollow (creuser l'interieur, defaut), box (vider toute la boite)")
		public String defaultInterior = "hollow";
		@Comment("Source des materiaux par defaut : storage (coffres tries des villageois) ou all (tous les coffres et tonneaux du territoire)")
		public String defaultSources = "storage";
		@Comment("Option 'remplacer les blocs construits' par defaut")
		public boolean defaultReplaceBuilt = false;
		@Comment("Option 'fondations' par defaut")
		public boolean defaultFoundations = false;
	}

	@Json5.Section
	public static final class Preview {
		@Comment("Duree (secondes) de l'apercu sans action avant qu'il disparaisse")
		public int seconds = 300;
		@Comment({
				"Blocs fantomes affiches au plus (entites d'affichage envoyees au seul joueur) ; au-dela, seulement la coque",
				"(blocs visibles de l'exterieur), echantillonnee (0 = aucun bloc fantome, seulement le contour)"
		})
		public int maxGhostBlocks = 1024;
		@Comment("Distance (blocs) jusqu'a laquelle le contour et les blocs fantomes sont envoyes")
		public int range = 96;
		@Comment("Duree (secondes) de 'Afficher le contour' depuis le menu d'un chantier")
		public int outlineSeconds = 30;
	}

	@Json5.Section
	public static final class Builder {
		@Comment("Blocs poses (ou retires) par seconde et par batisseur (le bonus de vitesse de travail s'applique ; 1 a 10)")
		public double blocksPerSecond = 2.0;
		@Comment("Piles d'objets transportees (materiaux + deblais)")
		public int carryStacks = 9;
		@Comment("Portee (blocs) depuis laquelle un batisseur pose un bloc")
		public double reach = 4.5;
		@Comment("Couches ouvertes en meme temps sur le front du gros oeuvre et du terrassement (1 a 8)")
		public int layerWindow = 2;
		@Comment("Cote (blocs) d'une parcelle reservee par un batisseur sur une couche (2 a 16)")
		public int tileSize = 6;
		@Comment({
				"Repartition des roles en mode Auto : poids du role 'Construire' (cellules faisables) et du role 'Recolter'",
				"(objets manquants) ; au plus maxGatherShare des batisseurs recoltent (0 a 1)"
		})
		public double buildWeight = 1.0;
		public double gatherWeight = 2.0;
		public double maxGatherShare = 0.5;
		@Comment("Intervalle (secondes) entre deux repartitions des roles")
		public int rolesSeconds = 10;
		@Comment("Intervalle (secondes) entre deux inventaires des sources de materiaux")
		public int stockSeconds = 10;
		@Comment("Intervalle (secondes) entre deux demandes des materiaux manquants aux recolteurs")
		public int procurementSeconds = 30;
		@Comment("Cellules verifiees par tick pour tous les chantiers (suivi de l'avancement ; 256 a 65536)")
		public int scanCellsPerTick = 4096;
		@Comment("Mode 'tous les coffres et tonneaux' : chunks du territoire lus par seconde pour trouver les conteneurs")
		public int containerScanChunksPerSecond = 4;
	}

	@Json5.Section
	public static final class Terraform {
		@Comment({
				"Blocs naturels retires la ou un bloc doit etre pose ou la ou l'interieur est creuse (id ou #tag) ; les feuilles ne",
				"comptent que si elles sont naturelles (non persistantes), les buches seulement si elles portent des feuilles naturelles"
		})
		public List<String> naturalBlocks = new ArrayList<>(List.of(
				"#minecraft:base_stone_overworld", "#minecraft:substrate_overworld", "#minecraft:sand", "minecraft:gravel",
				"minecraft:clay", "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:calcite", "minecraft:dripstone_block",
				"minecraft:pointed_dripstone", "minecraft:smooth_basalt", "minecraft:magma_block", "minecraft:snow_block",
				"minecraft:powder_snow", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:obsidian",
				"#minecraft:terracotta", "minecraft:mud", "minecraft:moss_block", "minecraft:amethyst_block", "minecraft:tuff",
				"#minecraft:leaves", "#minecraft:logs", "minecraft:cactus", "minecraft:bamboo", "minecraft:pumpkin", "minecraft:melon",
				"minecraft:mushroom_stem", "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block", "#minecraft:ores",
				"#minecraft:coal_ores", "#minecraft:iron_ores", "#minecraft:copper_ores", "#minecraft:gold_ores",
				"#minecraft:redstone_ores", "#minecraft:lapis_ores", "#minecraft:diamond_ores", "#minecraft:emerald_ores",
				"minecraft:infested_stone", "minecraft:infested_deepslate"));
		@Comment("Garder le butin du terrassement (au sac puis au stockage) ; false = jete")
		public boolean keepDrops = true;
		@Comment("Sac plein et aucun coffre de depot libre : jeter les deblais pour continuer (true) ou attendre (false)")
		public boolean discardWhenStorageFull = true;
		@Comment({
				"Colmater l'eau et la lave hors du chantier qui couleraient dans le terrassement (avec un bloc du sac, sinon un bloc",
				"gratuit si freePlugBlocks) ; sinon la cellule reste en place"
		})
		public boolean plugOutsideFluids = true;
		public boolean freePlugBlocks = true;
		@Comment("Bloc gratuit utilise pour colmater")
		public String plugBlock = "minecraft:cobblestone";
		@Comment("Objets du sac utilisables pour colmater (blocs pleins)")
		public List<String> plugItems = new ArrayList<>(List.of(
				"minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:dirt", "minecraft:stone", "minecraft:deepslate",
				"minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:netherrack"));
	}

	@Json5.Section
	public static final class Materials {
		@Comment("L'eau (sources et blocs immerges) est gratuite ; sinon un seau d'eau par source (le seau vide revient au stockage)")
		public boolean waterFree = true;
		@Comment({
				"Proprietes ignorees pour dire qu'un bloc est deja bien place (elles changent toutes seules : age des cultures,",
				"distance des feuilles, alimentation en redstone...)"
		})
		public List<String> ignoredProperties = new ArrayList<>(List.of(
				"age", "distance", "persistent", "stage", "moisture", "power", "powered", "lit", "triggered", "enabled", "occupied",
				"honey_level", "has_book", "has_record", "bites", "extended", "note", "locked", "signal_fire", "hatch", "berries",
				"level", "attached", "disarmed", "in_wall", "open", "short", "unstable", "bloom", "can_summon", "shrieking", "tip",
				"thickness", "hanging", "charges", "dusted", "cracked", "ominous", "vault_state", "trial_spawner_state"));
	}

	@Json5.Section
	public static final class Foundations {
		@Comment("Bloc des fondations sous un bloc qui n'est pas un cube plein (sinon le meme bloc que celui du dessus)")
		public String block = "minecraft:cobblestone";
		@Comment("Profondeur maximum (blocs) sous la couche du bas")
		public int maxDepth = 16;
	}

	@Json5.Section
	public static final class Display {
		@Comment("Barre de progression pour les membres de la guilde pres d'un chantier (rayon en blocs, 0 = jamais)")
		public int bossbarRadius = 48;
		@Comment("Feu d'artifice a la fin d'un chantier")
		public boolean firework = true;
	}

	/** Active (unfinished) sites allowed for a guild level. */
	public int maxActive(int guildLevel) {
		int extra = projects.extraEveryLevels <= 0 ? 0 : Math.max(0, guildLevel - 1) / projects.extraEveryLevels;
		return Math.max(0, Math.min(projects.maxActiveCap, projects.maxActive + extra));
	}

	public SchematicReader.Limits limits() {
		return new SchematicReader.Limits(schematics.maxAxis, schematics.maxVolume, schematics.maxBlocks,
				(long) schematics.maxDataMegabytes * 1024 * 1024);
	}

	@Override
	public void validate() {
		if (schematics == null) {
			schematics = new Schematics();
		}
		if (projects == null) {
			projects = new Projects();
		}
		if (preview == null) {
			preview = new Preview();
		}
		if (builder == null) {
			builder = new Builder();
		}
		if (terraform == null) {
			terraform = new Terraform();
		}
		if (materials == null) {
			materials = new Materials();
		}
		if (foundations == null) {
			foundations = new Foundations();
		}
		if (display == null) {
			display = new Display();
		}
		schematics.maxAxis = clamp(schematics.maxAxis, 1, 1024);
		schematics.maxVolume = clamp(schematics.maxVolume, 1, 64_000_000);
		schematics.maxBlocks = clamp(schematics.maxBlocks, 1, 16_000_000);
		schematics.maxFileMegabytes = clamp(schematics.maxFileMegabytes, 1, 1024);
		schematics.maxDataMegabytes = clamp(schematics.maxDataMegabytes, 1, 4096);
		schematics.cacheSize = clamp(schematics.cacheSize, 1, 64);
		projects.maxActive = clamp(projects.maxActive, 0, 64);
		projects.extraEveryLevels = clamp(projects.extraEveryLevels, 0, 100);
		projects.maxActiveCap = clamp(projects.maxActiveCap, 0, 64);
		projects.keepFinished = clamp(projects.keepFinished, 0, 100);
		projects.defaultInterior = InteriorRules.Mode.parse(projects.defaultInterior, InteriorRules.Mode.HOLLOW).id();
		projects.defaultSources = BuildProject.Sources.parse(projects.defaultSources).id();
		preview.seconds = clamp(preview.seconds, 10, 3600);
		preview.maxGhostBlocks = clamp(preview.maxGhostBlocks, 0, 8192);
		preview.range = clamp(preview.range, 16, 256);
		preview.outlineSeconds = clamp(preview.outlineSeconds, 5, 600);
		builder.blocksPerSecond = clamp(builder.blocksPerSecond, 0.2, 10.0);
		builder.carryStacks = clamp(builder.carryStacks, 1, 36);
		builder.reach = clamp(builder.reach, 2.0, 8.0);
		builder.layerWindow = clamp(builder.layerWindow, 1, 8);
		builder.tileSize = clamp(builder.tileSize, 2, 16);
		builder.buildWeight = clamp(builder.buildWeight, 0.0, 100.0);
		builder.gatherWeight = clamp(builder.gatherWeight, 0.0, 100.0);
		builder.maxGatherShare = clamp(builder.maxGatherShare, 0.0, 1.0);
		builder.rolesSeconds = clamp(builder.rolesSeconds, 2, 300);
		builder.stockSeconds = clamp(builder.stockSeconds, 2, 300);
		builder.procurementSeconds = clamp(builder.procurementSeconds, 5, 55);
		builder.scanCellsPerTick = clamp(builder.scanCellsPerTick, 256, 65536);
		builder.containerScanChunksPerSecond = clamp(builder.containerScanChunksPerSecond, 1, 64);
		terraform.naturalBlocks = cleanIds(terraform.naturalBlocks, new Terraform().naturalBlocks);
		terraform.plugItems = cleanIds(terraform.plugItems, new Terraform().plugItems);
		if (terraform.plugBlock == null || terraform.plugBlock.isBlank()) {
			terraform.plugBlock = "minecraft:cobblestone";
		}
		materials.ignoredProperties = cleanIds(materials.ignoredProperties, new Materials().ignoredProperties);
		if (foundations.block == null || foundations.block.isBlank()) {
			foundations.block = "minecraft:cobblestone";
		}
		foundations.maxDepth = clamp(foundations.maxDepth, 1, 64);
		display.bossbarRadius = clamp(display.bossbarRadius, 0, 256);
		configVersion = CURRENT_VERSION;
	}

	private static List<String> cleanIds(List<String> ids, List<String> defaults) {
		if (ids == null) {
			return defaults;
		}
		List<String> cleaned = new ArrayList<>();
		for (String id : ids) {
			if (id != null && !id.isBlank()) {
				cleaned.add(id.trim());
			}
		}
		return cleaned;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
	}
}
