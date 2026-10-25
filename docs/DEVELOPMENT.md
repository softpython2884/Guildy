# Guildy — notes de développement

Mod Fabric **100 % serveur** (clients vanilla) : guildes PvE, territoires par chunks avec drapeaux, hordes nocturnes,
constructions multiblocs, quêtes, boutique, carrefour commercial, événements, villageois à tâches, arbre d'évolution
et armes uniques.

Conventions : code, commentaires et identifiants en **anglais** ; textes affichés aux joueurs en **français**, tous
dans `config/guildy/lang.json` (codes couleur `&`, paramètres `{0}`), jamais en dur dans le code.

## Versions

| Élément | Version |
|---|---|
| Minecraft | **26.3** (jeu non obfusqué : noms officiels Mojang, plus de Yarn) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0+26.3 |
| Loom | 1.18-SNAPSHOT (plugin `net.fabricmc.fabric-loom`, pas de remap, `implementation` au lieu de `modImplementation`) |
| Gradle | 9.7.1 (wrapper) |
| Java | **25** (requis par Minecraft 26.x) |
| SGUI (Patbox) | 2.2.1+26.3 (maven.nucleoid.xyz), embarqué jar-in-jar |
| fabric-permissions-api (lucko) | 0.7.0 (Maven Central), embarqué jar-in-jar |
| Polymer | 0.18.4+26.3, `compileOnly` : facultatif à l'exécution (pack de ressources des armes uniques) |

## Construire et tester

```bash
./gradlew build            # jar : build/libs/guildy-<version>.jar (+ tests unitaires + GameTests)
./gradlew test             # tests unitaires JUnit 5 (logique pure)
./gradlew runGameTest      # GameTests serveur (src/gametest : scénarios en jeu avec joueurs simulés)
./gradlew runServer        # serveur de développement dans ./run
./gradlew resourcePackZip  # pack de ressources des armes uniques : build/libs/guildy-resourcepack-<version>.zip
./gradlew genSources       # sources Minecraft décompilées, pour vérifier les API
```

- GameTests : le monde `build/run/gameTest/world` est réutilisé entre deux exécutions, donc `GuildyTestInit` vide
  `guilds.json` / `events.json` au démarrage et coupe les tirages aléatoires (événements, visites de marchands).
  Chaque scénario travaille dans une région lointaine (`TestSupport.region` : plateforme + chunks forcés) ;
  `TestSupport.connected/survivor` créent des joueurs simulés connectés dont le chat est journalisé (`[GuildyTest]` dans
  `build/run/gameTest/logs/latest.log`). Les clics de construction sont « debounce » : espacer deux clics de quelques ticks.
- Les fichiers de configuration par défaut de `docs/default-config/` sont une copie de ceux que génère le serveur de
  test (`build/run/gameTest/config/guildy/`, à vider avant un `runGameTest` pour les régénérer).

## Architecture (package `dev.guildy`)

```
Guildy.java   entrypoint : config, langue, init des modules, lifecycle, sauvegarde automatique, commande racine
core/         GuildyServer, Scheduler (tâches réparties) + Safe, Lang/CoreLang, Msg, Hooks (liens entre modules),
              Lifecycle, GuildyItems (drapeaux, plans, cartes = custom_data), GoldUtil, ItemUtil, UniqueDrops, ScrollDrops
config/       ConfigManager/ConfigHandle/Json5 (@Comment, @Json5.Section, Validatable), GuildyConfig (config.json5)
data/         Guild, GuildManager (index id/nom/joueur/chunk, XP, trésor, invitations), GuildStorage (JSON atomique),
              GuildEvents (+ WaveOutcome), BannerDesign, ChunkKey, rôles et permissions
claim/        FlagHandler (planter les drapeaux), ClaimService (API territoire), ClaimRules (pur, testé),
              ProtectionHandler + WorldProtection + ClaimGuard + HeartIndex, HeartBanners, TerritoryNotifier,
              BorderVisualizer, HomeTeleports, ClaimCommands
mixin/        ServerExplosion, FireBlock, PistonStructureResolver, FlowingFluid, Projectile, BannerBlock (cœur),
              LivingEntityPortal (pas de portail pour les villageois de guilde), FarmlandBlock (terre piétinée)
command/      GuildCommand (racine /guild + /gld, admin sous guildy.admin), GuildCommands, GuildActions (toutes les
              actions de guilde, partagées entre commandes et menus), GuildAdminCommands, GuildRules (pur)
gui/          GuiUtil, GuildMenus + GuildGui (base) + menus NoGuild/Main/Members/Member/Invite/Territory/Treasury/Banner
wave/         WaveManager (nuit, démarrage), WaveSession (une horde), WaveSpawner, WavePlanner/WaveFormula/WeightedPicker
              (purs, testés), WaveTruce (trêve, pur), WaveApi + NightModifiers (événements), WaveLoot, MoveToHeartGoal,
              WaveMenu, WaveConfig
structure/    StructureManager (validation au plan, panneaux gérés, intégrité), PatternMatcher/StructurePattern (purs),
              StructureIndex, StructureMenus, StructureConfig, VillageSignBehavior (panneau du village) ;
              StructureBehavior = API pour les autres modules ; clé de motif `*` = n'importe quel bloc plein
quest/        QuestManager, QuestGenerator (pur), QuestHooks + quest/mixin (artisanat, cuisson, pêche, élevage,
              apprivoisement, commerce, enchantement), QuestBoardBehavior, QuestMenus, TreasureMaps, QuestConfig
shop/         ShopTransactions (achat/vente atomiques), ShopMenus, ShopValidator (pur), PlainItems, ShopConfig
trade/        TradeHub (carrefour commercial), Merchants (marchands de passage), TradeOffers/TradeRolls, menus, TradeConfig
event/        EventManager (nuit de sang, raid de pillards, caravane, érudit), EventRules (pur), EventState, EventConfig
villager/     VillagerManager (arrivées, candidats, départs), TaskEngine + *Task (Sorter, Miner, Lumberjack, Farmer,
              Guard, Expedition, Blacksmith, Alchemist, Cartographer), forge et alchimie (ForgeRules, AlchemyRules (purs),
              CraftJobs, CraftWorld, CraftStock, TerritoryScan), travaux des champs (ZoneOption, ZonePermissions,
              ZoneOptionsMenu, FieldPlanner (pur), FieldWorks, FieldMemory, FieldState), Movement (cibles de marche + délais + repli), VillagerCalls (« Appeler ici »),
              zones (WorkZone, ZoneFlags, WorkArea, WorkAnchor, PatrolRoute), mine réelle (MineLayout, MineRules,
              MineWorld, Mines, RealMiner), objectifs prioritaires (Wishlist, WishMatch, WorkWishes, WishlistMenu),
              gardes (Guards, GuardRules, GuardCombat, GuardArrow, GuardEquipment, GuardRetreat, FriendlyFire),
              Missions, Expeditions + ExpeditionDestinations, stockage (Storage, ItemClassifier, StorageCategories
              (pur), StorageDefaults, StorageLabels, StorageGui), DepthLoot (pur), VillagerGui, ZoneGui, VillagerConfig
upgrade/      UpgradeTree/UpgradeRules/UpgradeMath/UpgradeEffects (purs, testés), Upgrades (bonus lus par les autres
              modules), UpgradeService (déblocage atomique), UpgradeMenu, UpgradeBuffs, UpgradeConfig
alchemy/      AlchemyModule, AlchemyConfig (alchemy.json5), BrewBook (pur : étapes de brassage), Brews, Elixirs,
              ElixirDrinks, MagicBoosts + MagicBoostRules (pur) → core/MagicBoost (bonus des sorts), AlchemyCommands
fly/          FlyModule, GuildFly (vol de guilde), FlyRules (pur, testé), FlyLang ; core/FlightOwnership (pur) +
              FlightGrants : propriété partagée de `mayfly` (vol de guilde, double saut)
rune/         RuneModule, RuneConfig (runes.json5), RuneRules (pur, testé), RuneItems (gravure/retrait), RuneForge,
              RunePowers + RuneMythics, RuneFx, RuneGrinding + rune/mixin (meule, table de forgeron), RuneCommands ;
              villager/BlacksmithRunes
level/        LevelCurve, LevelScaling, LevelRewardRules (purs, testés), LevelUps (montées de niveau, récompenses,
              annonces), LevelLang
unique/       UniqueItems (fabrique), UniqueRolls (tables de butin), UniquePowers + Combat/Lightning/MiningPowers,
              DoubleJump, UniqueTracker (annonces), UniqueVisuals + PolymerBridge (modèles), UniqueConfig ; armes des
              ruines : Leaps, JaguarPowers, SolarPowers, DefensePowers, CursePowers, InfernalPowers, UniqueFx ;
              panoplies : UniqueSets (pur), SetBonuses, SetPowers, SetPower ; protection minimale : ArmorFloor (pur)
scroll/       ScrollModule (implémente ScrollDrops), ScrollConfig, objets (custom_data kind `scroll`), sorts animés,
              EffectRunner (effets par tick, budget de particules), TargetRules (pur), scroll/mixin (fin d'incantation)
ruin/         RuinModule, RuinIndex + RuinStore (ruins.json), MarkerCapture + ruin/mixin (marqueurs DATA à la génération),
              Encounters/Encounter (combats), BossFactory, BossAbilities + RuinFx (capacités animées), RuinLoot,
              RuinRules (pur), Cartography + CartographerBehavior + AtelierMenu (recherches), RuinItems, RuinConfig
```

Ressources : `data/guildy/enchantment` (10 enchantements data-driven), `data/guildy/damage_type`, tags
`data/guildy/tags`, ruines (`data/guildy/structure/ruin`, `worldgen/structure|template_pool|structure_set`, tags de
biomes et de structures, `loot_table/chests/ruin`), modèles et textures des armes uniques et des parchemins dans
`assets/guildy` (générés par `tools/gen_textures.py`). Outils des ruines (gabarits, aperçus, convertisseur de
schématiques) : `tools/ruins/`.

## Noyau partagé (API entre modules)

- `GuildyServer` : `server()`, `config()`, `claimLevel()`, `isClaimLevel(level)`, `player(uuid)`, `players()`.
- `GuildManager` : `of(player)`, `get(id)`, `byName`, `at(level, pos)`, `atChunk(key)`, `create/disband/addMember/
  removeMember/setRole`, `claim/unclaim/unclaimAll`, `fireTerritoryChanged`, `addXp`, `addTreasury/takeTreasury`,
  invitations, `markDirty()`.
- `Guild` : champs publics (level, xp, treasury, heart, home, banner, données des modules : waves, structures, quests,
  villagers, upgrades, events, trade) ; membres et claims en lecture seule (`memberIds()`, `role()`,
  `can(uuid, GuildPermission)`, `claims()`, `owns()`, `isBorderChunk()`…).
- `GuildEvents` : CREATED, DISBANDED (avant suppression), MEMBER_JOINED/LEFT, TERRITORY_CHANGED, LEVEL_UP,
  WAVE_STARTED, WAVE_ENDED (WaveOutcome).
- `Lifecycle` : STARTED (après chargement des données), STOPPING (avant la sauvegarde finale), RELOADED.
- `Scheduler.every(name, interval, offset, task)` / `later(...)` : tout passe par `Safe` (aucune exception ne remonte).
- `Hooks` : `openMainMenu`, `openWaves`, `openStructures`, `openQuests`, `openShop`, `openVillagers`, `openUpgrades`,
  `isWaveActive`, `waveStatusLore`.
- `Upgrades.value(guild, UpgradeEffects.X)` : bonus de l'arbre d'évolution + helpers `boost/reduce/discount/count/workSeconds`.
- `StructureBehavior.register(id, behavior)` : comportement d'une construction (onBuilt, onUse, onRemoved, signText).
- `UniqueDrops.roll(source)` / `create(id)` : armes uniques pour les autres modules (sources `boss`, `blood_boss`,
  `quest`, `legendary_quest`, `expedition`, `trade`) ; no-op sûr si le module est absent.
- `Msg`, `Lang` (`addDefaults`, `get`, `raw`), `GuiUtil` (chest, item, back, confirm, textInput, fill), `GuildyItems`,
  `GoldUtil`, `ItemUtil`.
- Config par module : `ConfigManager.register("xxx.json5", XxxConfig.class, XxxConfig::new)` dans `XxxModule.init()` ;
  classes annotées `@Comment`, sous-objets `@Json5.Section`, `Validatable` pour borner les valeurs ; un champ
  `configVersion` permet de migrer les anciennes valeurs par défaut (seulement si elles n'ont pas été modifiées).

## Décisions de conception

(voir aussi le README, section « Choix de conception »)

1. **Persistance** : JSON (Gson) dans `<monde>/guildy/guilds.json`, `dataVersion` versionné, écriture atomique
   (fichier temporaire + remplacement) + `.bak`. Sauvegarde toutes les N minutes si modifié, à chaque sauvegarde du
   monde et à l'arrêt. Indépendant des API SavedData, qui changent souvent.
2. **Config** : `config/guildy/*.json5` générés depuis les classes Java (commentaires via `@Comment`), relus en Gson
   lenient (commentaires `//` acceptés), valeurs manquantes = défauts, fichiers réécrits à jour. `/guild admin reload`
   recharge tout. Les nouveaux modèles par défaut (constructions, quêtes, offres) sont ajoutés une seule fois aux
   fichiers existants (`knownDefaults`, `knownTemplates`, `configVersion`).
3. **Monnaie** : lingot d'or vanilla = 1 « or ». Trésor de guilde = compteur entier. Aucun objet moddé : drapeaux,
   plans, cartes et armes uniques sont des objets vanilla avec des composants (`custom_data`).
4. **Drapeaux** : bannières vanilla marquées. Le drapeau fondateur planté crée le **cœur** de la base et revendique un
   carré (2r+1)² de chunks ; les drapeaux d'extension se plantent dans un chunk **adjacent** au territoire. Le cœur est
   indestructible ; abandonner le territoire (chef, avec confirmation) rend le drapeau fondateur.
5. **Territoire** : dans l'Overworld (configurable), chunks connexes (4 voisins), distance minimale entre guildes,
   nombre maximum de chunks par niveau.
6. **Hordes** : à la tombée de la nuit pour chaque guilde qui a un cœur **et** un membre près du cœur (sinon nouvel
   essai pendant la première moitié de la nuit). Pas de horde pour une base vide (pas de raid hors ligne).
7. **Condition de défaite** : le cœur a des points d'intégrité (bossbar) ; les monstres de la horde à moins de 3 blocs
   l'abîment chaque seconde. Intégrité 0 = défaite ; tenir jusqu'à l'aube ou tuer toute la horde = victoire
   (récompense proportionnelle aux monstres tués).
8. **Conséquence de défaite** (configurable) : −25 % du trésor, 20 % de départ par villageois, palier de nuit −2
   (anti-spirale), horde dispersée. L'équipement des joueurs n'est jamais touché.
9. **Sommeil** interdit pendant une horde ; nuit sautée avant le début = pas de horde, ni récompense ni pénalité.
10. **Redémarrage pendant une horde** : horde annulée, monstres tagués `guildy_wave` refusés au chargement
    (`ServerEntityEvents.ALLOW_LOAD`).
11. **Multiblocs** : murs verticaux (largeur × hauteur, profondeur 1) définis en données (lignes + clé bloc/`#tag`),
    validés par un clic droit avec le **plan** ; panneaux gérés posés devant (cirés, non éditables, incassables) ;
    intégrité vérifiée périodiquement.
12. **Tableau de quêtes** : 3 × 2 × 1 → 6 panneaux = 6 quêtes, progression commune à la guilde.
13. **Villageois** : activités vanilla WORK/MEET désactivées pendant une tâche (REST/PANIC aussi pour les gardes) ;
    chaque déplacement a un délai maximum et une solution de repli (jamais bloqué) ; bûcheron = arbres naturels
    seulement (feuilles non posées par un joueur) ; candidats non persistés.
14. **Formules des hordes** : D = 1 + 0,5·palier + 0,5·(niveau−1) + 0,75·(présents−1) ≤ 20 ; monstres = 6 + 3D
    (≤ 80, 30 vivants par base, 150 sur le serveur) ; cœur 150 + 30·(niveau−1).
15. **Territoire** : les drapeaux plantés ne gardent que les motifs (casser une bannière ne rend jamais un drapeau) ;
    le cœur et son support sont toujours protégés ; abandon impossible pendant une horde ; dissolution → la
    bannière-cœur disparaît.
16. **Constructions, quêtes, boutique** : plans non consommés ; en créatif, pas de progression de quête, de livraison
    ni de vente ; récompenses de livraison toujours sous le prix boutique (test `EconomyBalanceTest`) ; la boutique ne
    reprend que des objets « simples » (ni enchantés, ni renommés, ni abîmés).
17. **Menus et commandes** : `GuildActions` centralise toutes les actions et revérifie les droits à chaque clic ;
    confirmations par menu (ou `... confirm`).
18. **Arbre d'évolution** : nœuds data-driven, payés avec l'or du trésor et des objets simples de l'inventaire
    (paiement atomique) ; bonus = somme des effets des nœuds débloqués, lus aux points d'accroche (chunks max, cœur,
    récompenses, gardes, durées de travail, prix des drapeaux…). L'or des quêtes de livraison n'est jamais augmenté.
19. **Drapeau au métier à tisser** : le métier à tisser vanilla conserve `custom_data`, le drapeau fondateur se tisse
    donc avec de vrais colorants ; planté, son dessin devient celui de la guilde. Le cœur porte le nom de la guilde et
    apparaît sur les cartes tenues en main (`MapItemSavedData.toggleBanner`).
20. **Missions et expéditions** : objectifs CONTINUOUS / FILL / QUOTA comptés à la collecte ; une expédition sauvegarde
    l'entité du villageois en SNBT, la retire du monde, puis la recrée à l'identique (même UUID) au retour — le record
    est marqué « présent » avant `addFreshEntity` (sinon l'entité serait prise pour une copie périmée).
21. **Événements et commerce** : tirage au crépuscule (nuit de sang, globale) et tirage du matin par guilde (raid,
    caravane, érudit) ; aucune logique de horde dupliquée : `WaveApi` (multiplicateurs `NightModifiers`, vague
    d'événement chronométrée qui ne touche pas au palier de nuit). Marchands = marchands ambulants vanilla tagués
    `guildy_merchant`, payés en or, qui ne rachètent rien et ne vendent jamais sous le prix boutique.
22. **Zones de travail et parcours de garde** : fanions plantés (bannières marquées), rayon en chunks autour du chunk
    du fanion, objectif par type ; zones hors territoire permises mais travaillées seulement en chunks chargés
    (aucun chargement forcé), jamais chez une autre guilde ; recherches incrémentales (quelques chunks par seconde).
    En 26.x `#dirt` ne contient plus l'herbe : le sol des arbres est `#substrate_overworld`.
23. **Quêtes variées** : 17 types ; les actions sans événement Fabric sont captées par `quest/mixin`
    (`ResultSlot`/`FurnaceResultSlot.checkTakeAchievements` avec `removeCount` > 0, car le shift-clic l'appelle deux
    fois ; `trigger` des critères d'advancement, appelés même sans advancement) ; contrats légendaires (un à la fois) ;
    l'arme unique d'une quête est tirée à la remise, jamais à la génération (abandonner ne permet pas de la « pêcher »).
24. **Mine réelle** (le mode simulé reste disponible) : le plan de la mine n'est jamais stocké, il est recalculé
    (`MineLayout`, pur et testé : escalier en colimaçon 1 × 3 autour d'un pilier, 8 orientations choisies en sondant
    le terrain, galerie principale + latérales tous les 3 blocs ou salles de carrière, tout dans le carré de la zone) ;
    `MineRules` (pur, testé) : seuls les blocs naturels configurés, jamais un bloc à données, chez une autre guilde ni
    sous un bloc non naturel ; fluides colmatés. Le pathfinding vanilla ne grimpe pas aux échelles (pas de nœud
    d'escalade dans `WalkNodeEvaluator`) : les mineurs marchent par points de passage dans leurs propres tunnels.
    Segments réservés par mineur (`Mines`), remontée avant la nuit, niveaux suivants automatiques.
25. **Armes uniques** : objets vanilla + composants (`custom_data` kind `unique` + id, nom, lore, enchantements,
    attributs, `unbreakable`, `use_cooldown` à groupe propre pour afficher la recharge) ; 10 enchantements data-driven
    qui n'utilisent que des types d'effets vanilla (registre synchronisé avec les clients vanilla) et ne sont dans
    aucun tag de table, d'échange ou de butin ; dégâts de procs en `guildy:foudre|brasier|givre` (le temps
    d'invulnérabilité est remis à zéro pour ces types) ; pouvoirs bornés et rechargés, sur monstres uniquement ; foudre
    purement visuelle ; minage via `gameMode.destroyBlock` + protections ; double saut = autorisation de vol accordée
    au sol (nettoyée à la connexion). `item_model` posé seulement si un pack est garanti (Polymer présent, ou
    `customModels: "on"`).
26. **Trêve** : `WaveTruce` (pur) compte les défaites d'affilée (hordes de nuit et vagues d'événement, une victoire
    remet à zéro) ; au seuil, `truceNights` nuits sans horde. Une nuit n'est décomptée qu'une fois (`truceDay` = jour
    de la nuit épargnée, les tentatives de démarrage suivantes de la même nuit restent épargnées) ; `WaveApi.inTruce`
    / `truceBlocksRaids` pour le module des événements (pas de raid de pillards pendant la trêve).
27. **Panneau du village** : construction d'un seul bloc dont la clé `*` accepte n'importe quel bloc plein ; son
    panneau affiche nom, niveau, membres en ligne (ou horde en cours) ; clic = constructions, accroupi = menu principal
    (via `Hooks`). Ajouté aux anciens `structures.json5` par `knownDefaults`.
28. **Objectifs prioritaires** : listes d'au plus 9 ids d'objets sur `VillagerRecord.wishlist` et `WorkZone.wishlist`
    (fusionnées par `Wishlist.items`) ; éditeur « fantôme » (l'objet du joueur n'est jamais pris) ; bûcheron / fermier :
    cibles voulues d'abord, jusqu'à `zones.wishlistMargin` blocs hors zone, jamais chez une autre guilde ; expéditions :
    poids des objets voulus multipliés.
29. **Bûcheron et fermier** : le scan d'une zone reprend un chunk à la colonne où il s'était arrêté (sinon une forêt
    dense bouclait sur le même chunk) et un arbre rejeté est ignoré 5 minutes ; le mode automatique est ancré sur le
    point du territoire le plus proche (`WorkAnchor`), jamais recentré sur la position du villageois (plus de dérive).
    « Appeler ici » (`VillagerCalls`) charge 3×3 chunks autour de la dernière position connue avec un ticket vanilla
    non sauvegardé, attend l'entité (10 s max) puis la téléporte.
30. **Destinations d'expédition** : `expeditions.destinations` (données) + les deux sections historiques comme
    destinations par défaut ; la destination du voyage est enregistrée (`awayDestination`) pour un retour correct après
    un redémarrage ; un id inconnu ou verrouillé retombe sur la destination par défaut ; les livres enchantés du butin
    tirent dans `#minecraft:on_random_loot`.
31. **Gardes** : rôle épéiste / archer (équilibré en mode Auto, stable), flèches vanilla taguées qui ne blessent que
    des monstres (`ALLOW_DAMAGE` + tag, aussi après rechargement) ; l'arme réelle prise au stockage est gardée en main
    secondaire (la main principale est effacée par le comportement vanilla des villageois, une copie d'affichage y est
    posée) ; meilleure armure par emplacement prise dans les coffres enregistrés, l'ancienne y retourne ; repli sous
    `retreatHealthRatio` vers le lit (mémoire HOME, sinon lit libre) avec soins, debout (l'activité REST reste verrouillée
    pour les gardes). Pas de tir ami : `AttackEntityCallback` + `ALLOW_DAMAGE` annulent coups, projectiles et animaux
    des membres sur les villageois de leur guilde.
32. **Mine réelle v2** : escalier de 2 voies (la voie intérieure sert de chemin, chaque virage forme un palier 2×2),
    niveaux tous les `levelSpacing` (6) blocs alignés sur la cible et ouverts dès que l'escalier les atteint ; le travail
    est découpé en chantiers réservés (`MineJobs`, pur : le plus proche le long de l'arbre des tunnels, avec priorités),
    20 mineurs au plus par zone ; minerais exposés pris partout (sols rebouchés), filons suivis hors zone
    (`veinMaxOutside`) ou mis en attente (repris par un tunnel d'approche depuis un niveau) ; objectifs prioritaires
    minés comme des filons et jamais utilisés pour colmater. Données : `MineState.format` (0 = mine d'avant les
    niveaux, gardée avec son escalier d'une voie). Migration n° 2 (`levelSpacing` 8 → 6, `maxVeinBlocks` 16 → 64).
33. **Rangement par thème** : `ItemClassifier` applique des règles par catégorie (`storage.categories` : id, `#tag`,
    motif `*_ore`, propriétés `@food/@armor/@tool/…`, onglets créatifs `@tab:` construits côté serveur avec
    `CreativeModeTabs.tryRebuildTabContents`) ; la règle la plus précise gagne (id > plus petit tag > plus long motif >
    propriété > onglet, puis ordre du fichier) ; résultat mis en cache par objet. Les coffres enregistrés prennent le nom
    de leur rôle (jamais par-dessus un nom donné par un joueur). Migration n° 3 : anciennes catégories par défaut
    remplacées seulement si non modifiées, anciens noms de catégorie des coffres convertis.
34. **Rythme des hordes et candidats** : `WaveSchedule` (pur) tire la prochaine nuit de horde 1 à 3 jours après chaque
    horde de nuit (`GuildWaveData.nextWaveDay`) ; une horde manquée (personne au cœur) reste due ; une nuit d'événement
    (`NightModifiers.hasLabel`) en amène toujours une ; la trêve passe avant. Les candidats villageois sont une liste par
    guilde (groupes de `arrivals.groupMin..groupMax`, bornés par les places, les lits libres et `maxCandidates`),
    acceptés ou refusés par nom.
35. **Chunks des villageois** : `ChunkLoadPlanner` (pur) choisit les chunks à garder actifs (villageois au travail, leur
    destination, leur zone, les coffres utiles ; plafonds par guilde et serveur, tour de rôle) et `WorkChunkLoader` pose
    des tickets non sauvegardés qui expirent seuls (niveau ≤ 31 = entités actives, `addTicketWithRadius(r + 2)`), en
    mode `online` (un membre connecté), `always` ou `never`. En 26.3 les ticks aléatoires ont lieu dans ces chunks, pas
    les apparitions de monstres ; `pause-when-empty-seconds` met le serveur en pause sans joueur.
36. **Survie des villageois** : `VillagerVitality` (modificateur `guildy:guild_vitality` : PV par niveau de guilde, plus
    pour les gardes, régénération hors combat) ; `NightShelter` (retour au lit à `night.homeTime` avec Vitesse, mise à
    l'abri par téléportation à `shelterTime` et au début d'une horde ; le repos vanilla abandonnait un lit inaccessible
    en 1200 ticks) ; `HordeTargets` + mixin `NearestAttackableTargetGoal.findTarget` (`guildy.wave.mixins.json`) : les
    monstres tagués `guildy_wave` ignorent les villageois sauf riposte ; `GuardGear` : équipement de base par niveau
    (table configurable, armure = modificateur d'attributs, jamais un objet), arbalète pour les archers à partir du
    niveau 6.
37. **Plantation** : `PlantingRules` (pur) + `Planter` / `SaplingPlanter` / `CropSower` : tournées de plantation entre
    deux récoltes, emplacements naturels espacés et plafonnés par chunk, jamais près d'un bloc non naturel ou d'une autre
    guilde ; pousses / graines prises au sac puis au stockage (`PlantStock`, retrait réel) ; labour seulement en zone
    Champs, près d'une eau.
38. **Carrière** (objectif Pierre, `realMining.stoneQuarry`) : `Quarry` (pur) découpe le carré de la zone en gradins
    de `quarryBenchHeight` blocs et en bandes, garde la tour de l'escalier (son carré + un mur d'un bloc, une ouverture
    par gradin) et une chaussée jusqu'au bord ; avancement par colonne (bits) ; `QuarryJobs` (pur) n'ouvre une bande que
    sous une partie entièrement creusée du gradin du dessus (deux gradins à la fois au plus). Colonnes de soutien sous
    tout bloc non naturel, jamais de creusage sous les pieds d'une entité. GameTests de mine en environnement
    `guildy-test:day` (heure figée : le refuge de nuit arrêterait les mineurs).

39. **Forgeron et alchimiste** : tâches de territoire sans zone (`TerritoryScan` : chunks revendiqués chargés, du plus
    proche au plus lointain). Le forgeron modifie la durabilité **sur place** dans l'équipement du porte-armure (rien ne
    quitte le support, aucune duplication) ; chaque étape est une utilisation vanilla de l'enclume (`AnvilBlock.damage`).
    L'alchimiste suit la table vanilla (bibliothèques `BOOKSHELF_OFFSETS`, coût de la 3e ligne, tirage limité à
    `#minecraft:in_enchanting_table`, enchantements `guildy:` filtrés) ; plafond de livres par jour sauvegardé dans
    `VillagerRecord.craft`. Niveau de guilde requis par tâche (`CraftJobs`), vérifié aussi dans « Assigner les inactifs ».
40. **Autorisations du fanion** : `WorkZone.options` (par nom ; absent = défauts du type de zone, qui reproduisent
    l'ancien comportement), préréglages « Entretenir un champ » / « Créer des champs ». `FieldPlanner` (pur) place les
    points d'eau sur un quadrillage de 9 (rayon d'hydratation vanilla 4, même niveau ou un au-dessus) avec décalage si la
    case n'est pas sûre ; `FarmlandBlockMixin` (`FarmlandBlock.turnToBaseBlock`) mémorise la terre piétinée et sa culture
    dans les zones Champs (mémoire bornée) ; ordre : récolte, relabour, semis, puis travaux du sol bornés.
41. **Ruines** : structures jigsaw data-driven (une pièce par ruine, `structure_set` Overworld et Nether, tags
    `#guildy:ruins`, `ruins/overworld`, `ruins/nether`), donc seulement dans les nouveaux chunks. En 26.3,
    `SinglePoolElement.place` retire les blocs de structure (`BlockIgnoreProcessor.STRUCTURE_BLOCK`) et le crochet
    `handleDataMarker` n'est jamais appelé : `ruin/mixin` lit les marqueurs DATA (`guildy:boss|minion|entrance`) au RETURN
    de `SinglePoolElement.place`, pour le chunk en cours seulement, met leur case à l'air et passe les données au thread
    serveur par une file ; le start courant est connu par un ThreadLocal posé dans `StructureStart.placeInChunk`. Les
    ruines sont aussi indexées au chargement des chunks (starts du tag). Combats : réveil près du marqueur boss, entités
    taguées `guildy_ruin` refusées au chargement hors combat actif (redémarrage = ruine dormante), laisse, barre de boss,
    réinitialisation sans joueur, retour après `respawnDays`. Sources `ruin_boss` / `nether_ruin_boss` pour les armes
    uniques (`UniqueConfig` v2) et les parchemins.
42. **Cartographe** : construction `cartographer` + tâche CARTOGRAPHER ; la recherche (coût en objets de l'inventaire)
    avance pendant que des cartographes travaillent à l'atelier, puis `ChunkGenerator.findNearestMapStructure` avec
    référence (comme les cartes d'explorateur vanilla : une ruine déjà indiquée n'est plus proposée) ; carte
    d'explorateur (Overworld) ou boussole `LodestoneTracker` non suivie (Nether, recherche depuis les coordonnées / 8).
    Le monde des GameTests a `generateStructures=false` : la localisation y est remplacée par `Cartography.locator`.
43. **Parchemins de sort** : objets vanilla + composants (`custom_data` kind `scroll`, `consumable` pour l'animation
    d'incantation vanilla, `use_cooldown` par groupe) ; une pile qui n'a que `custom_data` (tables de butin) est
    reconstruite dans l'inventaire. Le sort part à la fin de l'utilisation (mixin `ItemStack.finishUsingItem`), un seul
    parchemin consommé et seulement en cas de réussite. Effets PvE (`TargetRules`), aucun bloc modifié, `EffectRunner`
    borné (effets par joueur et serveur, budget de particules par tick, paquets longue distance groupés).

44. **Coffres rangés et salles des coffres** : `StorageGroups` = coffres d'une catégorie dans une même salle
    (`StorageChest.room`, 0 = hors salle), en ordre de lecture (`ChestOrder`, pur : mur = même FACING et même plan, droite
    du lecteur = (dz, −dx) de la face, colonnes ou rangées selon `GuildVillagerData.storageOrder`, coffre double = une
    unité ; `sections` = coffres à portée d'un même point). `Tidier` : piles triées par `StorageSort` (index créatif
    `ItemClassifier.creativeIndex`, id, puis simple < composants < enchanté < renommé < abîmé), `StorageLayout` (pur :
    disposition parfaite, `arrange` idempotent par section, `transfers` entre sections) ; travaux TAKE_OUT > ARRANGE >
    CARRY, baux par groupe, budget `auditChestsPerStep`. Chaque écriture compare le multiensemble d'objets avant/après
    (sinon restauration) ; un conteneur ouvert (`Storage.inUse` : menus ouverts + `getEntitiesWithContainerOpen`) n'est
    jamais touché. Salles : `ChestRoom` (boîte), coffres `auto` (catégorie "" = réserve, vidée comme un dépôt),
    `RoomPlanner.planRoom` (pur) : blocs de lignes entières dans l'ordre des catégories avec marge, hystérésis
    (`roomGrowFreeSlots`, `roomNewCategoryStacks`, `roomIdleMinutes`, décalage ≤ `roomMaxShift`) ; un rôle choisi par un
    joueur rend le coffre manuel. Une ancre de chunk STORAGE par chunk de coffres.

45. **Unicité des armes uniques** (`UniqueLedger` pur, `UniqueRegistry`, `UniqueStore`) : numéro de série dans
    `custom_data` (guildy.serial) ; registre `<monde>/guildy/uniques.json` (id, source, date, détenteur guilde/joueur,
    dernière vue, ACTIVE/LOST, collections, doublons). `uniqueness` guild / server / none ; la chance d'abord, puis le
    tirage parmi les ids non exclus ; tout exclu → `ScrollDrops.pick` (parchemin garanti de la même source).
    `UniqueDrops.roll(source, [random,] Recipient)` (`killerOrNearest` pour les boss de horde, `topDamager` pour les
    ruines via `UniqueDamage`), anciennes signatures conservées ; `UniqueDrops.offer` (offres numérotées quand un joueur
    les porte). Scan 1 s : inventaire + coffre de l'Ender, menus de stockage vanilla une fois par ouverture ; doublon
    journalisé, jamais supprimé. Perte : `ENTITY_UNLOAD` d'une `ItemEntity` / `AbstractArrow` avec une raison destructrice
    et une pile non vide, confirmée après 60 ticks sans être revue ; cassure par mixin `ItemStack.applyDamage`.
    `Hooks.openUniques` / `uniqueCollectionLore`, `/guild uniques`. `uniques.json5` format 3 : modèle de l'arbalète.
46. **Parchemin d'incantation** : `summon.mode` (`scroll` par défaut, `proximity` = ancien réveil) ; objet `custom_data`
    kind `ruin_summon` (+ `consumable`, complété depuis les tables de butin) ; lecture = `UseItemCallback` + mixin
    `ItemStack.finishUsingItem` (ruin/mixin), vérifications pures `SummonRules.check` ; un parchemin consommé au début du
    rituel, rendu à l'annulation (arène vide 1 s, arrêt du serveur, admin awaken/reset). `Ritual` avance chaque tick avec
    `RitualFx` (particules longue distance, budget `summon.particleBudget`), draine via `setHealth` (jamais sous
    `minHealth` ≥ 1), puis `Encounters.awaken`. Les rituels comptent dans `encounter.maxActive`.
47. **Grimoire** (`scroll/Grimoire*`, section `grimoire` de `scrolls.json5`) : livre enchanté + composants (`custom_data`
    kind `grimoire`, `spells` [4 ids, "" = vierge], `selected` ; `custom_model_data` strings[0] = id choisi ou `empty` ;
    `item_model` `guildy:grimoire` si pack garanti ; `use_cooldown` groupe `guildy:grimoire`). Fusion côté serveur par
    mixins `ItemStack.overrideOtherStackedOnMe` / `overrideStackedOnOther` (clic secondaire, comme un bundle ; la synchro
    du menu corrige la prédiction du client). Lancement par `ScrollCasting.begin` (`SpellContext.power`), coût en faim
    (`GrimoireRules.spend` : saturation puis faim, fraction en épuisement ; refus si faim < coût ; créatif gratuit). Quand
    rien n'est consommé, l'inventaire est renvoyé et la recharge prédite effacée. Accroupi + échange de main par mixin
    HEAD de `handlePlayerAction`. Butin : `grimoire.drops` tiré avant la table de parchemins de la source.

48. **Menace et résurrection** (`ThreatRules` pur, `Resurrection`) : `RuinRecord.threat` (0 dans les anciennes sauvegardes
    = 1 + `timesCleared`), +1 à chaque victoire, plafond `escalation.maxLevel` ; bonus par modificateurs
    `guildy:ruin_threat_*` (ré-appliqués à la conversion), dégâts des capacités via `AbilityDef.scaled`, récompenses
    (`Rewards`). Fenêtre (`fastKillSeconds` par ruine ou formule) : `ALLOW_DEATH` met la vie à 1, refuse la mort (sauf
    `BYPASSES_INVULNERABILITY`) et lance la résurrection : NoAI + `setPermanentlyInvulnerable` + `ALLOW_DAMAGE`, corps
    déplacé par `setPos`, `RitualFx` avec un budget partagé, éclairs visuels ; puis modificateurs `guildy:ruin_risen_*`
    (échelle limitée par la hauteur libre), nom « Enragé », escorte, aura. Styles `jade` / `wind`. 7e ruine `temple_maya`
    (brise à l'échelle 2 ; ≥ 5 blocs libres au-dessus du marqueur boss), poids 2 dans `ruins_overworld` (jungles).
49. **Parchemins de l'alchimiste** : uniquement par `ScrollDrops` (`create`, `info` : rareté, nom, disponibilité ;
    `isScroll`) ; `AlchemyRules.outcome` / `pickScroll` purs (chance, niveau de table pour les épiques, papier, plafonds
    `booksToday` commun + `scrollsToday`). Les objets magiques Guildy (`custom_data` kind scroll / grimoire / ruin_summon)
    sont rangés dans la catégorie du livre enchanté (`ItemClassifier`).

50. **Gardiens de salle, renfort de groupe, sanctuaires, 8e ruine** : marqueur DATA `guildy:guard` (`RuinRules.Marker.GUARD`,
    `RuinRecord.guards`) ; `RoomGuards` poste les gardiens quand un joueur survie/aventure approche une ruine DORMANTE sans
    combat ni rituel, une fois par période dormante (`RoomGuardRules` : nombre par palier + joueurs + menace, postes =
    marqueurs sinon postes de secours, laisse) ; tag `guildy_ruin_guard`, refusés au chargement sans `Watch` ;
    `RoomGuards.blocking` → `Refusal.GUARDED`. `GroupScaling` (pur) : joueurs figés dans `Encounter.party`, modificateurs
    `guildy:ruin_group_*` en ADD_MULTIPLIED_TOTAL (multipliés avec la menace et la malédiction), or réparti par
    `ItemEntity.setTarget`. Sanctuaires : `Sanctuaries` + `BeaconRules` (niveaux complets comme
    `BeaconBlockEntity.updateBase`), cassure annulée (`AttackBlockCallback`, `PlayerBlockBreakEvents.BEFORE`), mixins RETURN
    de `ServerExplosion.calculateExplodedPositions` et `PistonStructureResolver.resolve`. 8e ruine `grand_temple_maya`
    (112 × 104 × 116, deux pièces, balise sur base de fer à ~83 blocs, structure set `ruins_landmark` espacement 56 avec
    exclusion de 10 chunks autour de `ruins_overworld` ; emprise max 116 = 8 chunks de référence + marge d'adaptation de 12)
    : ocelot ×5 (chat noir `all_black` en maudit, décision 54), styles `solar` / `gold`. Raiders des ruines
    `setCanJoinRaid(false)` ; `RuinFx.play` pendant `tick` mis en file.
51. **Gardiens maudits** : `CursedItems` (kind `ruin_summon_cursed`), même chemin que le parchemin normal (`Incantations` +
    mixin), refus `CURSED_DISABLED` / `GUARDED` ; `CursedRitual` étend `Ritual`. `Encounters.awaken(..., cursed)` crée
    `Encounter.cursed` (`CursedEncounter`) : modificateurs `guildy:ruin_cursed_*`, barre PURPLE NOTCHED_6, pas de
    résurrection. Phases `CursedRules` (pur) : `ALLOW_DEATH` et `AFTER_DAMAGE` ramènent la vie au seuil et lancent
    `CursedTransition` ; `CursedFights` avance chaque tick avec un `RitualFx` partagé. Attaques (`Pentagrams`,
    `CursedAttacks`) télégraphiées, joueurs seulement, dégâts × menace × groupe × phase ; ombres via `BossFactory`, suivies
    comme escorte. Récompenses `CursedRules.rewards` (`maxUniques`), grimoire / parchemin garanti ; menace +`threatGain`.
    GameTests : `GuildyTestInit` monte `encounter.maxActive` à 64. Les armes uniques ne sont plus vendues au Carrefour
    (`trade.json5` format 3 retire l'ancienne offre une fois).

52. **Incantation du grimoire et indices des ruines** : `ChannelRules` (pur) : grimoire = max(`grimoire.channelSeconds` 1 s,
    `channelFactor` 0,6 × durée du parchemin), jamais plus long que le parchemin ; le cercle runique suit la vraie durée de
    l'utilisation (complet à 85 %, halo, embrasé au lancement) ; `scrolls.json5` format 2 (0,4 → 1 s si inchangé). Ruines :
    plus de note de menace (présence, réveil, victoire), sentinelles sans annonce à l'écran (`guards.announceOnScreen`) ni
    nombre ; refus GUARDED = `Seals` (cercle `summon.sealSeconds`, fissures, éclatement, `summon.sealCooldownSeconds`,
    parchemin jamais consommé).
53. **Bilan du combat** (`stats/`, pont `ruin/RuinFightStats`, section `stats` de ruins.json5) : une `FightSession` par
    ruine (ouverte quand les sentinelles sont postées ou que le gardien apparaît, arène = boîte + `presenceMargin`). Dégâts =
    PV + absorption perdus autour de `actuallyHurt` dans `hurtServer` (mixin), attribués par `FightRules.owner`
    (`OwnableEntity` / `TraceableEntity` / golems de sort) ; classement boss / escorte / garde au premier coup, coup fatal
    via `Encounters.victory`. Soins : pile de contextes `FightHealing` (tick d'un sort = lanceur,
    `HealOrHarmMobEffect.applyInstantaneousEffect` = propriétaire de la potion, tick de régénération = donneur), surplus et
    soi-même exclus. Chat 30 ticks après la victoire, forme courte en cas d'échec, `/guild bilan [detail]` ; rien n'est
    sauvegardé. Hordes non couvertes (pont `WaveFightStats` à faire).

54. **Donjons à thème, Jaguar céleste, forme finale maudite, eau des ruines** : ruins.json5 v2 (`RuinMigration` pur : ne
    remplace que les valeurs encore aux défauts 1.8) ; morts-vivants dans les temples mayas (bogged, parched, zombies) ;
    `BossDef.variant` / `cursedForm` ; `Felines` (IA de chasse remplaçant celle de l'ocelot / du chat, accesseur
    `MobTargetSelectorAccessor`, interactions refusées, comptés comme monstres pour les sorts, pouvoirs et protections) ;
    `JaguarAbilities` (bond, griffes, traque, rugissement, esprits) ; `cursed.finalForm` (`CursedRules.risesAgain` /
    `diesForGood`, récompenses maudites à la mort définitive seulement, sans le bonus « ressuscité »), `Resurrection` aux
    couleurs maudites. Structures des ruines en `liquid_settings: ignore_waterlogging` (sinon l'eau du monde noie les blocs
    noyables posés sous la nappe) ; audit `tools/ruins/ruinlib/water.py` dans check.py ; pots avec `LootTable`
    `guildy:pots/ruin`. `/guild admin scroll give` accepte `incantation` / `incantation_maudit`.

55. **Colonnes et coffres filtrés** : `StorageLayout.Fill` (COLUMNS par défaut, ROWS = ancien ordre ;
    `GuildVillagerData.chestLayout`, `storage.defaultChestLayout`) : emplacements en ordre de colonnes, un nouveau genre saute
    en haut de la colonne suivante seulement si toutes les piles restantes tiennent après. Rôle `FILTER` + `ChestFilter`
    (ITEMS / MATERIAL / TYPE) ; `ChestFilters` (pur, testé sur les 1 658 ids de 26.3 : mots modificateurs / de forme / de
    type). `StorageRouting` (pur) : filtres (exacte, matière, type), catégorie, Divers, dépôt ; `Tidier.markOut` sort une clé
    si une meilleure destination a de la place ; clé de groupe `room:filter:<clé>`. `ChestScan` (chunks revendiqués chargés,
    4 chunks / 32 conteneurs par tick, aperçu non sauvegardé, `apply` revérifie chaque conteneur, budget
    `maxFilterChests`) ; commandes `/guild stockage …` (`StorageCommands`).

56. **Grimoire maudit** (section `cursedGrimoire` de scrolls.json5) : grimoire `custom_data` `cursed: true` (même kind,
    pages, mixins, menu, rangement), recharge `guildy:grimoire_maudit`, `item_model` `guildy:grimoire_maudit` si pack
    garanti. `SpellPower` (pur) : lectures via `SpellContext.p` ; cœurs/secondes × `powerMultiplier`, blocs ×
    `radiusMultiplier`, nombres × `countMultiplier`, niveaux + `levelBonus`, surcharges par sort ; bornes (nombres ≤ max,
    portées ≤ 1,5 × max, cœurs/secondes ≤ 5 × max) ; `SpellPower.of(p)` = ancien `powerMultiplier`.
    `CursedGrimoireRules` : sang par rareté (`setHealth`), refus sous `minHealth` ; `ruinDrops` seulement sur le gardien
    maudit (`CursedEncounter.bonusDrops`). `CursedSpellFx` : couche de particules (incantation, lancement, effets,
    `SpellEffect.curse`) dans le budget partagé.
57. **Légende du Jaguar céleste et statues** : `Cartography.surfaceStructures` retire `cartography.statues.templeStructure` ;
    `Research.legend` (trailId) via `Cartography.templeLocator` puis `Statues.createTrail` ; `StatueRules.waypoints` (pur ;
    terre ferme via `ChunkGenerator.getBaseHeight` OCEAN_FLOOR_WG contre WORLD_SURFACE_WG) ; réutilisation (même temple =
    rejoint la chaîne, sinon nouvelle route) ; `StatueRecord` / `StatueTrail` dans ruins.json ; pose au CHUNK_LOAD +
    balayage toutes les 2 s, une par tick, zone chargée (`getChunkNow`), MOTION_BLOCKING_NO_LEAVES, `groundBlocks`, jamais
    dans un claim (+1 chunk), seconde recherche à 2 × `siteRadius` puis FAILED ; `placeInWorld` pivot au centre,
    IGNORE_WATERLOGGING, fondations ; `StatueTemplate` (yeux = lumière ≥ 12) ; carte `statue_map` (`StatueRules.mapView`) ;
    `StatueGuide` tous les 10 ticks ; protection par `Sanctuaries` ; outil : classe `Monument` + `check_monument`.
58. **Armes des ruines** (uniques.json5 format 4) : 7 uniques (21 en tout), ajoutées une fois (définitions + entrées
    `ruin_boss` / `nether_ruin_boss` / `legendary_quest` si absentes). `favorites` (`source@contexte` → poids ajoutés) ;
    `RuinLoot` passe `UniqueDrops.withContext(source, ruinId, "cursed")` ; `UniqueConfig.effectiveTable`. `Leaps` (envol
    puis atterrissage, 10 ticks au sol, pas de dégâts de chute via ALLOW_DAMAGE). Lance 26.3 : le pouvoir démarre la charge
    (`startUsingItem` + son KINETIC_WEAPON). `DefensePowers` sur AFTER_DAMAGE du joueur (Égide si `blocked`, plastron
    sous le seuil). Sceaux en mémoire bornée (256). Faille 1 bloc/tick suivant le sol. Dégâts `indirectMagic` +
    invulnérabilité remise à zéro. `UniqueDef.banner` → BASE_COLOR + BANNER_PATTERNS. Pack : lance = définition des lances
    vanilla (`select display_context`, `spear_in_hand` 32×32), bouclier : icône des menus, modèle 3D en main (décision 67). `/guild version`
    (`FabricLoader` + `SharedConstants.getCurrentVersion()`).

59. **Niveaux au-delà de 10** (`level/`, section `levels` de config.json5 v3) : `LevelCurve` (pur) = seuils explicites
    (index 0 = niveau 2) puis XP par niveau `xpStepBase + xpStepGrowth × (N − dernier niveau listé)`, en `long`,
    `maxLevel` 10 à 500 (migration : une liste plus longue relève `maxLevel`). `LevelScaling` (pur) : jusqu'au niveau
    10 chaque formule garde son `perLevel`, au-delà un taux `...Above10` plafonné (`max...Above10`, 0 = sans plafond) :
    difficulté des hordes (+0,05, ≤ 4,5), cœur (+5, ≤ 600), chunks (+1, ≤ 190), dégâts des gardes (+0,05, ≤ 4,5), vie
    des villageois (+0,5, `maxBonus`), places de villageois (0 : paliers) ; quêtes : niveau de calcul
    `LevelScaling.softLevel` (10 + 0,2 × (N − 10), ≤ 30) pour quantités et récompenses, éligibilité au vrai niveau
    (`EconomyBalanceTest` jusqu'au niveau 30). `GuildManager.addXp` → `LevelUps` : une annonce pour tous les niveaux
    gagnés, puis `LEVEL_UP` par niveau ; `LevelRewardRules` (pur) : or par niveau, paliers (or + `Guild.freeFlags`) payés
    une fois (`Guild.rewardedLevel`, `setlevel` ne paie rien), places de villageois calculées depuis le niveau, rangs
    (`levels.ranks`), annonce serveur aux non-membres. Drapeau offert = prix 0 dans `ClaimService.buyExtensionFlag`
    (CLAIM, `extensionFlagsBought` inchangé). Équipement des gardes : paliers 20 / 30 / 50 (villagers.json5 v7).
60. **Équilibrage 1.10 : or des gardiens, sorts offensifs** : ruins.json5 v3 (`RuinMigration` : plage d'or migrée par
    paire si les deux bornes sont aux défauts 1.9, `OLD_GOLD`) ; or ×3 environ par palier (`RuinDefaults.GOLD_MIN/MAX`
    15-25 … 50-75, Jaguar 60-90), `rewards.goldPerEscort` / `escortGoldMax` (1 par sbire tué, 12 au plus, avant les
    multiplicateurs : `RuinRules.baseGold`) ; pas d'or des sentinelles (reviennent après `resetSeconds` sans joueur :
    boucle) ; une part de plus d'une pile tombe en blocs d'or + le reste en lingots (`RuinLoot.goldStacks`). Sorts :
    dégâts `guildy:arcane` + `guildy:sort_brasier|sort_foudre|sort_givre` dans `#minecraft:bypasses_armor` (procs des
    uniques à part) ; `SpellTargets.hurt` remet les frames d'invulnérabilité et `lastHurt` (accesseur
    `LivingEntityAccessor`) à leur valeur d'avant le coup ; `hostiles` à la distance de la hitbox ; météores visés
    (`MeteorRules` pur : 2 sur 3 sur un monstre, le moins visé puis le plus proche, sauf ceux déjà condamnés par les
    météores en vol ; suivi jusqu'à 2 ticks de l'impact). Dégâts ×1,6 à ×2,3 ; scrolls.json5 format 3
    (`ScrollConfig.migrateDamage` : dégâts encore à l'ancien défaut du sort, `OLD_DAMAGE`).
61. **Totems dans le bilan** : mixin HEAD de `UsedTotemTrigger.trigger` (`stats.mixin.UsedTotemTriggerFightMixin`),
    appelé par `LivingEntity.checkTotemDeathProtection` seulement quand un objet `DEATH_PROTECTION` est consommé (main ou
    seconde main, après les `ALLOW_DEATH` de Fabric : une mort annulée ne consomme rien) → `FightTracker.totemUsed`
    (session de l'arène, créatif / spectateur ignorés) → `FightTally.totems` (suffit pour figurer au bilan). Titre
    `FightCategory.TOTEMS` (*Increvable*, compté en unités) après `tank` dans `stats.categories` ; `stats.version` 2 : une
    liste encore égale aux défauts 1.8.1 (`FightStatsSettings.V1_CATEGORIES`) reçoit le titre. Chat : total de l'équipe sur
    la ligne d'info, « (avec N totems) » après *Fléau du gardien*, ligne du lecteur, survol / lore et en-tête du menu (si
    > 0) ; forme courte = premier titre + *Increvable* (`FightReport.chatTitles`) ; singulier / pluriel : clé `….one`.

62. **Vol de guilde** (`fly/`, section `fly` de config.json5) : `/guild fly [on|off]` (nœud `guildy.fly`, accordé par
    défaut ; enfant ordinaire de `/guild`), préférence = tag `guildy_fly` (sauvé, gardé à la réapparition). `FlyRules`
    (pur) : DISABLED > GAME_MODE > NO_GUILD > ROLE > LEVEL > PERMISSION (lue en dernier, cache 2 s) > HORDE
    (`Hooks.isWaveActive` = toute session de `WaveManager` : nuit, test, événement) > OUTSIDE (chunk de sa guilde, dimension
    des claims) > COMBAT ; activation refusée seulement pour les raisons durables. `GuildFly` : joueurs avec la préférence
    seulement, tous les 5 ticks (répartis par id), plus tout de suite sur WAVE_STARTED, TERRITORY_CHANGED, MEMBER_LEFT,
    DISBANDED (forcé : la guilde est encore indexée) et RELOADED. Vol retiré en l'air → `FallGuard` : la prochaine chute
    (`#is_fall`, ALLOW_DAMAGE) est annulée une fois dans `fallProtectionSeconds`, garde levée après un atterrissage
    (sol, eau, échelle) ; connexion en l'air avec la préférence = même garde. Avertissement de bord : 8 directions à
    `borderWarningBlocks` + trajectoire extrapolée. Propriété partagée de `mayfly` (`core/FlightOwnership` pur +
    `FlightGrants`) : un propriétaire ne prend la main que si `mayfly` est faux ou déjà donné par Guildy (jamais sur
    créatif, opérateur ou autre mod, donc jamais repris), rangs GUILD_FLY > DOUBLE_JUMP (le double saut s'efface : ni
    octroi, ni bond, ni retrait), `mayfly` retiré au départ du dernier propriétaire, `flying` à celui du dernier qui permet
    le vrai vol ; tag par propriétaire (`guildy_guild_fly`, `guildy_double_jump` inchangé) nettoyé à la connexion, tout
    rendu à la déconnexion et à l'arrêt.
63. **Raccourcis de commandes** (`command/ShortcutCommands`, `ShortcutRules` pur et testé, section `shortcuts` de
    config.json5 : `enabled`, `aliases` raccourci → chemin sous `/guild`) : enregistrés dans une phase tardive
    `guildy:shortcuts` de `CommandRegistrationCallback` (après `Event.DEFAULT_PHASE` : vanilla et les autres mods ont déjà
    leurs commandes) ; chaque raccourci est un nouveau littéral racine qui **copie** la cible (commande, enfants partagés,
    redirection éventuelle, ET des `requires` de tout le chemin) — pas de `redirect` nu (un nœud redirigé sans argument ne
    s'exécute pas). Refus : nom invalide, réservé (`guild`, `gld`), doublon, cible mal écrite, cible absente (un défaut
    absent = log debug), nom déjà pris à la racine (jamais remplacé) ; un message par cause et par JVM. État par
    dispatcher (`WeakHashMap`, `/reload` reconstruit) ; nœud fusionné ensuite par un autre mod = `OVERRIDDEN`.
    `/guild admin reload` reconstruit en place (retrait par réflexion sur `children/literals/arguments` de `CommandNode`,
    échec → warn) et renvoie l'arbre aux joueurs (`Commands.sendCommands`, aussi après `END_DATA_PACK_RELOAD`).

64. **Stockage à grande échelle et catégories fines** : limites `storage.max*` à 0 = illimité (`filterChests` /
    `chestRooms` remplacent l'ancien 0 = désactivé ; étape de migration 8 pour les valeurs restées par défaut).
    `StorageIndex` (par guilde, jamais sauvegardé) : position → coffre, dépôts, sources, compteurs des limites, une ancre
    par chunk, groupes en ordre de lecture, `StorageRouting.Index` (pur : coffres par catégorie / clé de filtre / Divers /
    dépôt, réponses des filtres mémorisées, `Cursor` qui ne trie que le palier atteint) ; reconstruit quand la liste change
    (`ChestList`, compteur de modifications), sur `Storage.changed` et au plus tard toutes les 100 ticks. Groupe de plus de
    `maxGroupChests` (128) coffres coupé en parties consécutives. `route`, `findSortSource`, `markOut`, `CraftStock`,
    `PlantStock`, `GuardEquipment` passent par l'index ; salles planifiées une par appel ; noms des coffres vérifiés 1/100
    par tick ; guilds.json : un conteneur = une ligne compacte. Catégories : `StorageCategory.parent` ("" / parent / "*") ;
    `StorageCategories.resolve` → chaîne (transversales, puis la plus profonde... la principale) ; palier = 3 + position
    dans la chaîne (Divers 11, dépôt 12) ; 12 catégories par défaut ajoutées une fois (`knownCategories`) ; salles
    réservées (`ChestRoom.categories`) ; filtre d'un coffre vide = l'objet en main ; avertissement sur « Divers ».
65. **Alchimiste : potions, élixirs, repli et rôles** (`villager/AlchemistTask`, `AlchemyJob`, `AlchemyRoles` pur,
    `AlchemyGuild`, `PotionPlanner` ; module `alchemy/`, `alchemy.json5`) : trois travaux (`jobOrder` books > scrolls >
    potions), le rôle puis l'ordre, le premier possible gagne (raisons dans le menu), réévalué après chaque cycle ; rôle
    manuel (`CraftData.role`) sinon répartition `balanced` (`roleWeights` parmi les travaux possibles) ou `priority` ;
    plafonds séparés (livres, parchemins, `maxPotionsPerDay`). `AlchemyGuild` (runtime) : un scan des tables et alambics
    pour tous, inventaire du stockage rafraîchi, fournées réservées. Brassage simulé : `BrewBook` (recettes de
    `alchemy.json5`, fioles jetables / persistantes, une étape par élixir), produit le plus en dessous de son stock
    (`GuildVillagerData.potionTargets`), fioles vides remplies près de l'alambic. Élixirs = potions vanilla (effets,
    couleur, nom, `custom_data` kind `elixir`). `core/MagicBoost` lu aux seuls points de calcul de `scroll/`
    (`SpellPower.boosted`, recharge, coût en faim, `shortenChannel`) ; état = pièce jointe Fabric persistante
    `guildy:magic_boosts`, barre violette, début à la fin de la gorgée (mixin `Consumable.onConsume`) ; `MagicBoostRules`
    (pur) : même élixir = durée relancée, élixirs différents additionnés et plafonnés. Nouveaux parchemins proposés une
    fois aux tables existantes de l'alchimiste (`alchemist.knownScrolls`).
66. **Sorts tactiques** (scrolls.json5 format 4) : `banniere_de_guerre`, `fleau`, `permutation`, `ralliement`,
    `chaines_spectrales`, `hate_du_vent`, ajoutés une fois (`ScrollConfig.NEW_SCROLLS` : définitions, poids des tables
    existantes, réglages maudits). `Spell.Unit.PERCENT` (borné au max, seulement par surcharge maudite). Fléau :
    `Vulnerability` (marques en mémoire) lue par `LivingEntityVulnerabilityMixin` (HEAD de `actuallyHurt`) ; `DebuffRules`
    (pur) : +100 % max, jamais cumulé, boss = `bossShare`. Permutation : `TargetRules.maySwap`, `SwapRules` (pur : place,
    lave, blocs dangereux, sol sous une cible volante) ; refus = parchemin gardé. Ralliement : `RallyRules` (pur) +
    `RallySpell` (appels en mémoire, `/guild ralliement <id>` derrière [Rejoindre], fermé si le lanceur part). Chaînes :
    prisonniers remis à leur ancre, `guildy:arcane` à la rupture. Hâte du vent : chutes annulées par ALLOW_DAMAGE.
    Textures dans `tools/gen_textures.py` (`SCROLL_RUNES` / `GRIMOIRE_SIGILS`).

67. **Apparence portée des armures uniques** (`unique/UniqueEquipment` pur et testé, `UniqueItems.syncVisuals`) : en 26.3
    l'apparence portée vient du composant `equippable` (`asset_id` → `assets/<ns>/equipment/<id>.json`, couches
    `humanoid` / `humanoid_leggings` / `humanoid_baby`, textures `textures/entity/equipment/<couche>/<texture>.png` 64×32 ;
    un asset absent côté client = armure invisible). Même règle que `item_model` (Polymer ou `customModels: "on"`) : copie
    de l'`equippable` de l'objet de base (emplacement, sons, autres champs) avec `asset_id guildy:<model>` si
    `UniqueEquipment.ASSETS` livre ce modèle pour l'emplacement de l'objet de base ; sinon l'`equippable` vanilla ;
    exemplaires en circulation mis à jour par le même scan d'inventaire (emplacements d'armure compris). Couches non
    teintables, `humanoid_baby` → texture vanilla du matériau de base, mêmes `trim_overrides` que le matériau de base. Pas
    d'animation (textures d'équipement hors atlas). Égide en main : modèle à éléments au lieu du rendu spécial
    `minecraft:shield`, dans les bornes de la boîte vanilla (plaque et poignée), transformations copiées de `item/shield`
    et `item/shield_blocking` (`condition using_item`), texture 32×32 animée ; GUI = icône 2D. Textures dans
    `tools/gen_textures.py` (`WORN_ARMORS`), aperçu `tools/previews/armor_worn.png` ; `resourcePackZip` vérifie que le zip
    contient tous les assets.

68. **Panoplies** (uniques.json5 format 5 ; `UniqueSets` pur et testé, `SetBonuses`, `SetPowers`) : 3 panoplies de 4
    pièces (12 uniques, 33 en tout) liées par `UniqueDef.set`, section `sets` (paliers cumulés {pieces, name, attributes,
    power}) ; ajoutées une fois (`addSetUniques` : définitions, panoplies, `legendary_quest`, favoris des gardiens).
    `UniqueSets` : pièces différentes comptées par panoplie, modificateurs à id stable `guildy:set_<panoplie>_<pièces>_<n>`.
    `SetBonuses` : emplacements d'armure relus sur `ServerEntityEvents.EQUIPMENT_CHANGE` (fin de tick), à la connexion, à la
    réapparition, au rechargement et toutes les 10 ticks ; modificateurs **transitoires** ; lore réécrite (✔ des pièces
    portées). Pouvoirs (monstres seulement, recharge via le groupe `use_cooldown` `guildy:set/<panoplie>`) : `winter_heart`
    (nova `guildy:givre` sous le seuil), `jaguar_ambush` (camouflage + perte de trace sauf boss, embuscade), `soul_pyre`
    (aura de drain, traînée de feu d'âme). Couche `humanoid_leggings` pour les jambières. **Protection minimale**
    (`ArmorFloor` pur, `armorFloor` netherite / none) : une unique portée dans un emplacement d'armure atteint au moins la
    pièce netherite (3/8/6/3, robustesse 3, recul 0,1) via des modificateurs `guildy:unique_floor_<emplacement>_*`
    (`UniqueItems.syncArmorFloor`, dans `syncVisuals` : création et scan d'inventaire).
69. **Runes du forgeron** (`rune/`, `runes.json5`, `villager/BlacksmithRunes`) : occasion = pièce d'armure réparée jusqu'au
    bout ou **examen** (porte-armure dont une pièce peut recevoir une rune, au plus une fois par `inspectionMinutes`) ;
    chance, plafond par forgeron et par jour, niveau de guilde, matériau pris au stockage. `RuneRules` (pur) : rang tiré
    parmi ceux qui ont une rune pour la pièce (mythiques : `mythicItems` + emplacement plastron), puis la rune par poids.
    Objet : `custom_data` kind `rune` + id, modificateurs `guildy:rune_<id>_<slot>[_n]`, lignes de lore marquées ;
    `RuneItems.strip` les retire exactement. Meule : HEAD de `GrindstoneMenu.computeResult` (pièce seule gravée → copie sans
    rune, enchantements gardés, pas d'XP), RETURN (fusion → jamais de rune) ; `SmithingTransformRecipe.assemble` RETURN :
    modificateurs recalculés. Pouvoirs (`RunePowers`, joueurs seulement) : AFTER_DAMAGE (étincelles, braise, lueur), cache
    des runes portées (rebond, renard, écho, Dragon) ; Phénix sur ALLOW_DEATH (avant le totem, jamais
    `BYPASSES_INVULNERABILITY`, recharge sur le plastron) ; Dragon = composant `glider` + souffle sur saut + accroupi.
70. **Ralliement pour toute la guilde, limites par sort** (scrolls.json5 format 5) : `RallyRules.reason` (pur) classe chaque
    membre (lanceur, hors ligne, indisponible, autre dimension, déjà à 8 blocs, trop loin) ; défauts `range` 0 = illimitée
    et `crossDimension` 1 (migrés s'ils valaient encore 512 / 0) ; refus détaillé avec les nombres ; arrivée par
    `teleportTo` vers la dimension du ralliement. Dégâts : `CastDamage` (pur, un par `SpellContext`) traversé par
    `SpellTargets.hurt` (seul chemin des dégâts de sort) : coup n° k × `repeatHitDecay`^k (plancher `repeatHitFloor`), un
    tic de sort continu pèse 1 coup / 5 s ; boss (`SpellTargets.isBoss`) plafonné à `bossCastCap` × vie max par sort
    (`cursedGrimoire.bossCastCap` pour le grimoire maudit) ; météores visés sur ce que le sort peut encore retirer.
71. **Salles filtrées** (`ChestRoom.mode` FILTERED ; rôle `StorageChest.Role.CONTENT`) : un coffre ne reçoit que les sortes
    d'objets qu'il contient déjà, rien s'il est vide. Sorte = `ContentKeys` (pur : id, composants ignorés ; potions = id +
    potion, `filteredRoomPotions` ; livres enchantés ensemble ou par enchantements, `filteredRoomBooks`). `ContentIndex`
    (pur, par guilde, jamais sauvegardé) tenu à jour par `RoomContents` (`filteredRoomReadsPerTick` coffres relus par tick,
    relecture avant chaque dépôt, salle entière à la conversion). `StorageRouting` : `CONTENT_TIER` = 0 (palier de la liste
    exacte). `Tidier` : groupe `room:content`, rien à sortir, chaque coffre rangé avec ses seuls objets. `Storage.Spare` :
    forge, alchimie, plantations et gardes laissent le dernier objet de chaque sorte. `ChestRooms.setFiltered` (menu Mode).

72. **Forme redoutable et Avatar** (ruins.json5 format 4) : `DreadRules` (pur) : à partir de `escalation.dreadThreshold`
    (VII), multiplicateurs par niveau (vie ×2,5 → ×4 à X, dégâts +50 % → +125 %, recharges ×0,8 → ×0,65, or et XP),
    plafonds. **Vie au-delà de 1 024** (plafond de `max_health`) pour tous les gardiens : vie prévue = configurée × menace ×
    groupe × malédiction × forme redoutable (× Avatar, bornée par joueur) ; `Encounter.vitality` = prévue / réelle ; mixins
    `LivingEntityDreadMixin` / `PlayerDreadMixin` (après `getDamageAfterMagicAbsorb` dans `actuallyHurt` ; `Player` le
    redéfinit sans `super`) : coup sur le gardien ÷ vitalité, soin ÷ vitalité ; coup sur un joueur venant d'un combat
    redoutable × facteur, bonus borné par `maxHitHealthRatio`. Barre et bilan (`FightTracker.Hit.scale`) en vie prévue ; le
    plafond par sort des boss (`CastDamage`) se calcule sur `Encounters.effectiveMaxHealth`. Capacités : `DreadEncounter`
    (rotation, phases), `DreadAttacks` + `DreadZones` / `DreadStrikes` / `DreadSummons` (lignes, cônes, anneaux à brèches,
    puits, racines, zones, missiles, plongeons, tempêtes à œil, marques), `DreadFx` (alertes au sol), `Roots`,
    `DreadFights`. Avatar : `AvatarRules` (pur), `AvatarEncounter` (ciel rouge = distance d'alerte de la bordure envoyée aux
    joueurs de l'arène, musique, aura, annonces, trophée « Cœur de l'Avatar »), étoile détectée dans le cercle à l'apogée du
    rituel maudit (`Incantations.climax`), consommée seulement si l'Avatar apparaît, retenue pendant le rituel.
73. **Grand spectacle** (scrolls.json5 format 6 ; `scroll/FireworksSpell`, `ShowPlan`, `ShowStage`, `ShowFigures` purs et
    testés) : parchemin épique `grand_spectacle` (sort `fireworks`, groupe `spectacle`), ajouté une fois (`addScrolls`) et
    proposé une fois à l'alchimiste. `ShowStage` : ligne perpendiculaire au regard du lanceur, 3 blocs derrière lui,
    extrémités ramenées vers le public, sol au niveau du lanceur, ciel libre, ni fluide ni claim étranger ; refus sous 3
    points. `ShowPlan` (même graine = même plan) : actes par tick d'éclatement (ouverture, cycles salves / figures / tirs
    croisés / accalmie, crescendo, final avec silence puis dernière salve), 20 s à 2 min, ≤ 40 fusées en vol (48 maudit).
    Fusées vanilla à vitesse calculée, éclatement au tick prévu par l'événement d'entité 17 puis `discard` (jamais
    `explode`), `noPhysics`, tag `guildy_firework` (ALLOW_DAMAGE annule tout dégât, ALLOW_LOAD refuse au rechargement) ;
    rien n'est tiré sans joueur à `viewDistance`, `limits.maxFireworks` (120) pour tout le serveur.

74. **Brassage simple** (`alchemy/SimpleBrewing` pur et testé, section `brewing` d'alchemy.json5) : `mode` simple (défaut,
    ajouté aux anciens fichiers comme toute clé manquante) ou recipes (décision 65, inchangé). Classe d'un produit
    (`classify` : PLAIN / LONG / STRONG d'après `long_` / `strong_`, forme potion / jetable / persistante, ELIXIR /
    MAGIC_ELIXIR selon `ElixirDef.magical()`) → coût d'une fournée {poudre, secondes} (+ suppléments), sauf entrée
    `brewing.products` ; une fournée plus petite paie sa part arrondie au-dessus. `SimpleBrewing.choose` : le produit le
    plus en manque que la poudre paie ; verdicts STOCK_FULL / NO_BLAZE / ELIXIR_CAP = raisons du repli. `AlchemistTask` :
    même cycle, poudre au sac, une place par fiole, fioles créées à la fin (`Brews.create`), progression gardée si
    l'alchimiste revient au même alambic (`brewingAt`). `CraftData.elixirsToday`, plafond `brewing.maxElixirsPerDay` (mode
    simple). GameTests du mode recipes dans leur propre environnement `guildy-test:brewing_recipes`.

75. **Chantiers et bâtisseurs** (`builds/`, `builds.json5`, tâche BUILDER : `villager/BuilderTask`, `Builders`,
    `BuilderRoles` pur, `BuildStock`) : schématiques listées (jamais un chemin libre) dans `config/guildy/schematics/`
    et `<monde>/generated/<ns>/structure/` ; `SchematicReader` (pur, testé : Sponge 1/2/3 en var-ints, Litematica
    fusionné — tailles négatives, tableaux de longs serrés, une entrée sur deux longs —, structure vanilla où les cases
    non listées sont « vides ») vérifie toutes les tailles avant d'allouer ; lecture et `BuildPlan` sur un fil à part
    (DataFixer `block_state` / `block_entity` depuis la DataVersion, blocs inconnus ignorés et signalés), cache par
    chemin + taille + date (les refus sont oubliés à `/guild admin reload`, car les limites ont pu changer — 1.15.1 ;
    environ 7 octets par case de la boîte) ; copie dans `<monde>/guildy/builds/<guilde8>-<id>-<fichier>`. `PlacementTransform` (pur) =
    formules de `StructureTemplate.transform` (pivot 0), état `mirror` puis `rotate`. Le **monde fait foi** : `BuildSite`
    (non sauvegardé) tient un état par cellule (fait, à poser, gratuit, attend des matériaux / un support, retenu,
    bloquant, laissé…) mis à jour par un balayage incrémental (`scanCellsPerTick` partagés, 10 ticks au moins entre deux
    passages, chunks chargés seulement) et par les actions des bâtisseurs ; fin décidée sur un balayage complet.
    `CellRules` (pur) : naturel → remplacé dans le même `setBlock` (jamais d'air intermédiaire), même bloc → état
    corrigé gratuitement, bâti → obstacle sauf option, conteneurs / cœur / fanions / constructions / autre guilde →
    jamais. `InteriorRules` (pur) : `hollow` = colonne entre bloc bas et haut + air coupé des bords de sa couche + devant
    / derrière des portes. `BuildPasses` (pur) : terrassement du haut vers le bas, gros œuvre du bas vers le haut,
    finitions sous le front (`SupportProbe` : `canSurvive` dans un monde d'air simulé), fluides en dernier ; les fronts ne
    comptent que les cellules faisables (un manquant ne bloque pas le reste). Pose : `UPDATE_CLIENTS |
    UPDATE_KNOWN_SHAPE` (état exact, voisins intacts), portes / lits posés entiers, textes de panneaux et motifs de
    bannières seulement ; creusage en mises à jour normales après les contrôles (bloc qui tombe au-dessus : retenu ;
    fluide voisin : colmaté hors du chantier ou cellule mouillée remplie de terre puis creusée). `MaterialRules` (pur) :
    coût d'un état (dalles doubles, bougies, cornichons, couches de neige, moitiés gratuites, cultures → graines, murs →
    objet debout, pots garnis, eau gratuite, lave = seau rendu, non constructibles). Bâtisseurs : parcelle louée
    (`tileSize`), cible de marche gardée jusqu'à l'arrivée, point d'appui (case creusée, dessus d'une colline) pour une
    case enterrée, matériaux de la parcelle + une pile d'avance par objet, déblais au dépôt (jetés si le stockage est
    plein), s'écarte d'une case à remplir. Rôles BUILD / GATHER (`BuilderRoles`, comme `AlchemyRoles` : besoins pondérés,
    au moins un de chaque à partir de deux, `maxGatherShare`, rôles stables) ; repli sur l'autre rôle ; sans chantier
    mais avec des manquants : GATHER. `Procurement.want(guild, "build:<id>", manquants)` toutes les
    `procurementSeconds` (restant − sources − sacs) avec `Procurement.keep` (le restant déjà au stockage : jamais pris
    comme ingrédient), effacé en pause / fin / annulation. Commande `/guild chantier` (alias `chantiers` ; `/guild build`
    reste le menu des constructions). Aperçu (`BuildPreview`, 1.15.2) : entités d'affichage envoyées au seul joueur,
    choisies par `GhostLod` (pur, testé) : tous les blocs s'ils tiennent dans `maxGhostBlocks` (4096), sinon la coque,
    sinon la schématique en cubes de côté croissant (2, 3, 4… ; un cube compte s'il contient une demi-tranche de blocs,
    affiché à l'échelle 1:1 sur la boîte englobante de ses blocs (1.15.3 : un mur d'un bloc reste à sa place), dans la
    matière la plus fréquente, blocs pleins comptés double, seuls les cubes visibles de l'extérieur) : la forme reste
    lisible à toute taille et les faces extérieures sont exactes (avant : échantillon dispersé de la coque). Calculé une fois par
    plan (`BuildPlan.ghosts`, préparé sur le fil de lecture) ; contour et fantômes visibles jusqu'à la diagonale du
    chantier + 32 (512 au plus, `view_range` en conséquence).
76. **Approvisionnement des chantiers** (`villager/GatherWork`, `Supply*`, `Pit*`, supply.json5) : rôle « Chercher les
    matériaux » des bâtisseurs, un voyage à la fois (sac vidé au stockage, choix, travail, livraison, `finished()`).
    `Procurement` (transitoire) : `want/wanted/delivered`, plus `fetching` (réservation par villageois, renouvelée à
    chaque pas, oubliée après 30 s) et `keep/kept` (ce que les chantiers comptent prendre au stockage). `SupplyPlanner`
    (pur, testé) : objet → stock du stockage (jamais la part réservée : objets demandés, `kept`, entrées prévues par les
    autres), sinon la recette la moins chère (CRAFT main/établi, SMELT four + combustible en « objets cuits » =
    durée vanilla / 200, CUT tailleur de pierre, TRANSFORM main ou eau), sinon récolte, sinon manquant ; coût = unités
    manquantes, à récolter, prises, puis étapes ; profondeur `crafting.maxDepth` (5), budget de 6 000 essais par plan ;
    la cible n'est jamais prise au stockage ; restes = surplus déposés. `bestFromStock` : plus grande quantité faisable
    tout de suite qui tient dans le sac (recherche dichotomique). `SupplyBook` : recettes `ShapedRecipe`/`ShapelessRecipe`
    (2×2 = main), `SmeltingRecipe`, `StonecutterRecipe` du `RecipeManager` (spéciales, résultats à composants,
    ingrédients à reste — seaux — ignorés) ; transformations dérivées des ids (`stripped_*`, `#turns_into_dirt_path`,
    `*_concrete_powder` → `*_concrete`, terre stérile, boue) ; combustible par `ResolvableInt` du composant
    `COOKING_FUEL` avec le contexte de butin d'un four détaché ; sources (`SupplySources`, pur) = butin des blocs naturels
    de realMining tiré 12 fois avec un outil en fer de leur sorte (`mineable/*`), bûches de `#overworld_natural_logs`
    avec pousse, surchargées par `sources` / `neverFetch`. `SupplyGuild` (par guilde, transitoire) : scan incrémental
    des établis, fours, tailleurs et sources d'eau (`TerritoryScan`, palettes), instantané du stockage, analyse des
    manques (route CRAFT > GATHER > GATHER_FOR, puis STATION / MISSING avec la raison), baux des fosses et ateliers,
    gisements introuvables (`noSiteRetrySeconds`). `GatherWork.blocked` rend une clé ou un texte déjà formaté.
    **Fosses** : `PitPlan` (pur) = carré `pitSide`, niveau k = y `top + 1 − k`, rampe = anneau des colonnes du bord à
    partir de l'entrée (la marche i n'est creusée que jusqu'au niveau i, ciel ouvert au-dessus : la navigation vanilla
    la monte et la descend), profondeur ≤ 4 (côté − 1) − 1 ; `PitRules` (pur) = colonnes autorisées + bord, sol plat
    (`pitMaxUneven`), rien que du naturel sur le bord et rien au-dessus (arbre, torche, eau), profondeur arrêtée avant une
    grotte / un fluide / une marche non creusable, entrée la plus proche du cœur hors des coins, score = blocs voulus
    réellement creusés ; `SupplyPits` : grille alignée `pitSide + pitGap`, fosses commencées d'abord, puis zones de mine
    réelles (hors carrière, escalier planifié ; exclusion = copie privée du `MineLayout` : colonnes de l'escalier, du
    fanion et des galeries des niveaux de `Mines.levels` + grille plus profonde, dilatées de `mineMargin`), puis hors
    territoire (aucun claim, `minDistanceFromSpawn`, protection du spawn d'un serveur dédié, chunks entity-ticking) ;
    sondage de 5 colonnes avant l'examen complet ; `SupplyPit` persisté (`GuildVillagerData.pits`, niveaux faits,
    colonnes abandonnées). `SupplyDigger` : terrain au-dessus du sol puis niveau par niveau, `MineRules.dig` (fluides
    colmatés, jamais sous un fluide ni un bloc non naturel), une colonne bloquée reste en pilier, une marche bloquée
    arrête la fosse à ce niveau, jamais sous les pieds d'une entité, plantes retirées sans butin, objets gardés = voulus
    + `fillerReserve` (+ `keepByproducts`). `SupplyTrees` : zones de coupe (CUT / TREE_REPLANT) puis arbres naturels hors
    claims, arbre toujours coupé entier, pousse replantée. `SupplyCrafter` : entrées prises au stockage (`CraftStock`),
    nouveau plan depuis le sac, étapes dans l'ordre (atelier loué, effets : son du crafteur, du tailleur, crépitement et
    four allumé seulement s'il est vide et éteint), combustible du plan d'abord. Livraison : chaque pile au coffre de sa
    catégorie (`Storage.route`), `Procurement.delivered` pour les objets demandés. Aucun voyage avant la fin du scan des
    ateliers (instantané du stockage refait à sa fin). `Supply.allContainers` (prédicat par guilde, défaut
    `crafting.allContainers`) : coffres et tonneaux non enregistrés du territoire repérés par le même scan (entités de
    bloc des chunks), comptés dans l'instantané et vidés au besoin (`Storage.inUse` respecté) ; `BuildModule` y ajoute
    l'option « tous les coffres et tonneaux » des chantiers actifs.

## Notes API Minecraft 26.3

Vérifier les API dans les sources décompilées (`./gradlew genSources`).

- `ResourceLocation` n'existe plus → `net.minecraft.resources.Identifier` (`parse`, `withDefaultNamespace`,
  `fromNamespaceAndPath`).
- Blocs et objets colorés = `ColorCollection` : `Items.BANNER.pick(DyeColor.WHITE)`, `Items.WOOL`, `Items.DYE`,
  `Items.BED`… ; `Blocks.BANNER/WALL_BANNER.pick(color)`. Plus de `Items.WHITE_BANNER`.
- Villageois : `net.minecraft.world.entity.npc.villager.Villager` (+ `VillagerData`, `VillagerProfession`).
- Types d'entités : `EntityTypes` ; `ClickType` s'appelle `ContainerInput`.
- Heure : `level.getOverworldClockTime()` (`% 24000` = heure du jour), `level.getGameTime()`.
- Chat : `new ClickEvent.RunCommand(cmd)`, `ClickEvent.SuggestCommand`, `new HoverEvent.ShowText(c)` (records).
- NBT : `CompoundTag.getStringOr/getIntOr/getLongOr/getBooleanOr/getCompoundOrEmpty` ; `getString` renvoie `Optional`.
- Objets : `stack.is(Items.X)`, `stack.get/set(DataComponents.X, ...)`, `CustomData.set(DataComponents.CUSTOM_DATA, stack, tag)`.
- Panneaux : `SignBlockEntity.setText(SignText, SignTextSlot)`, `setWaxed(boolean)`.
- Téléportation : `ServerPlayer.teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot, resetCamera)`.
- Permissions : `Permissions.require(node, PermissionLevel.GAMEMASTERS)` / `Permissions.check(entity, node, PermissionLevel)`.
- Nullable : `org.jspecify.annotations.Nullable`.
- `SoundEvents.X` est soit un `SoundEvent`, soit un `Holder.Reference<SoundEvent>` : `Msg.sound(...)` accepte les deux.
- Spawn du monde : `server.getRespawnData()` → `LevelData.RespawnData`.
- Bannières : debout `BannerBlock.ROTATION = RotationSegment.convertToSegment(yaw + 180)` ; murale
  `WallBannerBlock.FACING` ; données d'objet → `BlockEntity.applyComponentsFromItemStack`.
- Plus de `AxeItem`/`HoeItem`/`ShovelItem` : utiliser `DataComponents.TOOL` / `BLOCK_TRANSFORMER`.
- Particules pour un seul joueur : `ClientboundLevelParticlesPacket` (regroupables dans un `ClientboundBundlePacket`).
- Tags d'entité : `entity.entityTags()` / `addTag` ; chargement refusable via `ServerEntityEvents.ALLOW_LOAD`.
- Entités sauvegardées : `TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries)` + `entity.save(out)` ;
  restauration : `EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(EntitySpawnReason.LOAD, false), ...)`.
- GameTests : `helper.makeMockServerPlayerInLevel()` force le mode créatif → voir `TestSupport.connected`.

## Pack de ressources des armes uniques

Sprites dessinés à la main dans `gen_textures.py` (grilles de caractères + palettes, animations par images avec
ordre `.mcmeta` et interpolation) ; le script supprime les sorties obsolètes et écrit les aperçus dans `tools/previews/` ;
`ResourcePackTest` vérifie la cohérence du pack. Grimoire : `select` sur `custom_model_data` (chaîne 0 = id du
parchemin), défaut = livre endormi.

`tools/gen_textures.py` (Python 3, sans dépendance) génère les textures 16×16 originales (dont les animations), les
définitions d'objets et les modèles au format 26.3 dans `src/main/resources/assets/guildy`. La tâche Gradle
`resourcePackZip` assemble ces assets avec `tools/resourcepack/pack.mcmeta` et `pack.png`. Avec Polymer installé,
les assets du jar sont ajoutés automatiquement au pack généré par Polymer.
