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
              Lifecycle, GuildyItems (drapeaux, plans, cartes = custom_data), GoldUtil, ItemUtil, UniqueDrops
config/       ConfigManager/ConfigHandle/Json5 (@Comment, @Json5.Section, Validatable), GuildyConfig (config.json5)
data/         Guild, GuildManager (index id/nom/joueur/chunk, XP, trésor, invitations), GuildStorage (JSON atomique),
              GuildEvents (+ WaveOutcome), BannerDesign, ChunkKey, rôles et permissions
claim/        FlagHandler (planter les drapeaux), ClaimService (API territoire), ClaimRules (pur, testé),
              ProtectionHandler + WorldProtection + ClaimGuard + HeartIndex, HeartBanners, TerritoryNotifier,
              BorderVisualizer, HomeTeleports, ClaimCommands
mixin/        ServerExplosion, FireBlock, PistonStructureResolver, FlowingFluid, Projectile, BannerBlock (cœur)
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
              Guard, Expedition), Movement (cibles de marche + délais + repli), VillagerCalls (« Appeler ici »),
              zones (WorkZone, ZoneFlags, WorkArea, WorkAnchor, PatrolRoute), mine réelle (MineLayout, MineRules,
              MineWorld, Mines, RealMiner), objectifs prioritaires (Wishlist, WishMatch, WorkWishes, WishlistMenu),
              gardes (Guards, GuardRules, GuardCombat, GuardArrow, GuardEquipment, GuardRetreat, FriendlyFire),
              Missions, Expeditions + ExpeditionDestinations, stockage (Storage, ItemClassifier, StorageCategories
              (pur), StorageDefaults, StorageLabels, StorageGui), DepthLoot (pur), VillagerGui, ZoneGui, VillagerConfig
upgrade/      UpgradeTree/UpgradeRules/UpgradeMath/UpgradeEffects (purs, testés), Upgrades (bonus lus par les autres
              modules), UpgradeService (déblocage atomique), UpgradeMenu, UpgradeBuffs, UpgradeConfig
unique/       UniqueItems (fabrique), UniqueRolls (tables de butin), UniquePowers + Combat/Lightning/MiningPowers,
              DoubleJump, UniqueTracker (annonces), UniqueVisuals + PolymerBridge (modèles), UniqueConfig
```

Ressources : `data/guildy/enchantment` (10 enchantements data-driven), `data/guildy/damage_type`, tags
`data/guildy/tags`, modèles et textures des armes uniques dans `assets/guildy` (générés par `tools/gen_textures.py`).

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

`tools/gen_textures.py` (Python 3, sans dépendance) génère les textures 16×16 originales (dont les animations), les
définitions d'objets et les modèles au format 26.3 dans `src/main/resources/assets/guildy`. La tâche Gradle
`resourcePackZip` assemble ces assets avec `tools/resourcepack/pack.mcmeta` et `pack.png`. Avec Polymer installé,
les assets du jar sont ajoutés automatiquement au pack généré par Polymer.
