# Guildy

Mod **Fabric 100 % serveur** pour Minecraft **26.3** : survie coopérative en guildes (**PvE uniquement**).
Les joueurs se connectent avec un **client vanilla**, sans mod : tout passe par des menus (inventaires), des titres,
la barre d'action, des bossbars et du chat cliquable. Un pack de ressources **facultatif** donne leurs textures aux
armes uniques.

Guide du joueur (HTML) : [`docs/guide-standalone.html`](docs/guide-standalone.html) — à ouvrir dans un navigateur
ou à héberger pour les joueurs.

> Fonder une base avec un drapeau → survivre aux hordes de la nuit → gagner de l'or et du butin →
> agrandir le territoire avec des drapeaux → recruter des villageois et leur confier des tâches →
> quêtes, boutique et marchands de passage → se renforcer → des nuits toujours plus dures (et parfois une nuit de sang)
> → explorer les ruines de l'Overworld et du Nether et vaincre leurs boss.

## Fonctionnalités

- **Guildes** : chef, officiers et membres, invitations, trésor en lingots d'or, point de retour.
- **Territoire** : drapeau fondateur (le cœur de la base) et drapeaux d'extension par chunk, protections (explosions,
  feu, pistons, fluides), limites affichées en particules, bannière tissée au métier à tisser et visible sur les cartes.
- **Hordes nocturnes** : 7 thèmes (morts-vivants, arachnides, illageois, Nether, End, boss, élites), difficulté
  progressive, élites et boss avec bossbar ; il faut défendre le cœur jusqu'à l'aube.
- **Événements** : nuit de sang, raid de pillards en plein jour, caravane marchande, marchand de livres enchantés.
- **Constructions multiblocs** : tableau de quêtes, comptoir de commerce, bureau d'accueil, carrefour commercial,
  atelier du cartographe.
- **Quêtes** : 17 types (chasse, minage, livraison, artisanat, pêche, élevage, exploration, boss…), contrats légendaires,
  cartes au trésor.
- **Économie** : boutique de guilde, carrefour commercial à 3 paliers avec marchands de passage.
- **Villageois** : rangement des coffres, **mine réelle** (escalier en colimaçon, galeries, filons), bûcheron, fermier
  (qui peut transformer toute une zone en champs), garde avec parcours de ronde, **forgeron**, **alchimiste**,
  **cartographe**, expéditions (exploration, Nether), zones de travail par fanions et leurs autorisations, missions.
- **Arbre d'évolution** : 3 branches, 12 améliorations permanentes.
- **Armes uniques** : 14 armes et outils à pouvoirs (foudre, givre, téléportation, double saut, minage 3×3…),
  10 enchantements inédits, textures animées facultatives.
- **Parchemins de sort** : 12 sorts à usage unique (météores, foudre en chaîne, singularité, dôme d'Égide, rappel…)
  avec de grandes animations de particules.
- **Ruines et boss** : 6 ruines (4 dans l'Overworld, 2 dans le Nether), chacune gardée par un boss avec sa barre de vie,
  son escorte et ses attaques ; localisées par le cartographe de la guilde.
- **Tout est configurable** : fichiers JSON5 commentés en français, rechargeables à chaud ; permissions LuckPerms.

---

## Installation

| Requis | Version |
|---|---|
| Minecraft (serveur) | 26.3 |
| Java | 25 |
| Fabric Loader | ≥ 0.19.5 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.162.0+26.3 (ou plus récent pour 26.3) |

1. Télécharger `guildy-<version>.jar` depuis les [Releases](../../releases), le déposer **avec** Fabric API dans le
   dossier `mods/` du serveur (retirer l'ancienne version de Guildy s'il y en a une).
2. Démarrer le serveur : les fichiers de configuration sont créés dans `config/guildy/`.
3. Rien à installer côté client.
4. Facultatif : [Polymer](https://modrinth.com/mod/polymer) pour envoyer aux joueurs les textures des armes uniques
   (voir [§ 11](#11-armes-uniques)).

Bibliothèques **embarquées** dans le jar (rien à installer) : [SGUI](https://github.com/Patbox/sgui) (menus serveur)
et [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) (compatible LuckPerms).

---

## Guide du joueur

### 1. Créer sa guilde
`/guild` (ou `/gld`) ouvre le **menu principal** : tout se fait depuis là, sans connaître les commandes.
Sans guilde, le menu propose de **créer une guilde** (seul, c'est une guilde valide) ou d'accepter une invitation.
À la création, vous recevez le **drapeau fondateur** (une bannière aux couleurs de la guilde).

### 2. Fonder la base
**Plantez le drapeau fondateur** (clic droit sur un bloc) : il devient le **cœur** de la base et la guilde
revendique un carré de **3×3 chunks** autour.

**Le drapeau se crée au métier à tisser**, avec de vrais colorants : tissez-y le drapeau fondateur lui-même
(il reste un drapeau de guilde) ou n'importe quelle bannière, puis tenez-la en main et cliquez sur
« Adopter la bannière en main » (menu Bannière, ou `/guild banner adopt`). Le dessin adopté devient celui du cœur
(repeint automatiquement), des drapeaux d'extension, des bannières décoratives et des icônes des menus.
Le cœur porte le **nom de la guilde** et apparaît sur les **cartes** (marqueur de bannière avec le nom) dès qu'on
tient une carte qui couvre la base. L'éditeur gratuit (sans ressources) peut être activé en config
(`banners.freeEditor`). Le cœur est indestructible ; pour déménager, le chef utilise
« Abandonner le territoire » (le drapeau fondateur est rendu).

- Le territoire est protégé : seuls les membres (selon leur rôle) cassent, posent ou interagissent.
  Explosions, feu, pistons et liquides venant de l'extérieur ne l'abîment pas.
- Entrer/sortir d'un territoire affiche son nom (titre ou barre d'action). `/guild show` dessine ses limites en particules.
- **Drapeaux d'extension** (achetés avec le trésor dans le menu Territoire ou `/guild flag buy`, gagnés en quête ou sur un boss) :
  plantés dans un chunk **voisin** du territoire, ils l'ajoutent. `/guild claim` utilise un drapeau de l'inventaire
  sur le chunk où vous êtes, `/guild unclaim` rend le chunk (et le drapeau).
- Limite de chunks selon le niveau de guilde ; distance minimale entre deux guildes ; pas de base près du spawn.

### 3. Les nuits : la horde attaque
Tous les **1 à 3 jours** (tiré au hasard pour chaque guilde ; une nuit de sang en amène toujours une), si un membre est
près du cœur à la tombée de la nuit, une **horde** sort des **frontières** du territoire et marche vers le cœur. Le menu
des hordes indique la prochaine (« cette nuit » ou « dans N nuits ») ; un soir sans horde, un message prévient la guilde.
Les monstres de la horde **ignorent les villageois**, sauf ceux qui les frappent (les gardes).

- Thèmes variés : mort-vivants, arachnides, illageois, créatures du Nether, créatures de l'End, horde d'élite,
  nuit du boss. Monstres **élites** (brillants, plus forts) et **boss** réguliers.
- La difficulté monte avec les nuits survécues, le niveau de guilde et le nombre de membres présents.
- Alerte à la tombée de la nuit, bossbar de progression, bossbar rouge du **cœur**, récapitulatif à la fin.
- **Victoire** : le cœur tient jusqu'à l'aube (ou toute la horde est tuée) → or au trésor, XP de guilde et
  objets pour les membres présents (proportionnels aux monstres tués). Les monstres lâchent des pépites,
  les élites des lingots et objets, les boss du gros butin (drapeau d'extension, carte au trésor…).
- **Défaite** : les monstres au contact du cœur l'endommagent ; à 0 c'est la défaite → la horde se disperse,
  la guilde perd **25 % du trésor**, recule de **2 paliers** de difficulté, et chaque villageois a **20 %** de
  chance de partir. Le stuff des joueurs n'est jamais touché.
- **Trêve** : après **2 défaites d'affilée** (hordes ou raids), la base est épargnée pendant **3 nuits** : ni
  horde, ni raid de pillards. Une victoire remet le compte à zéro. La trêve s'affiche dans le menu des hordes
  (réglages : `truce` dans `waves.json5`).
- Impossible de dormir pendant une horde. Une nuit sautée (sommeil, `/time`) ou une base désertée annule la
  horde sans récompense ni pénalité.

### 4. Constructions exclusives
Menu « Constructions » (`/guild build`) : chaque construction s'y **débloque** (or du trésor), affiche son
**plan** couche par couche et donne un **Plan** (papier). Construisez le mur (3 de large × 2 de haut × 1 de
profondeur) dans votre territoire puis faites **clic droit avec le plan** sur sa face avant : le jeu vérifie
les blocs et pose des **panneaux gérés** devant.

| Construction | Plan (haut / bas, vu de face) | Déblocage | Rôle |
|---|---|---|---|
| **Tableau de quêtes** | bûche · bibliothèque · bûche / bûche · planches · bûche | gratuit, niv. 1 | 6 panneaux = 6 quêtes |
| **Comptoir de commerce** | planches · bloc d'or · planches / tonneau · tonneau · tonneau | 20 or, niv. 1 | la boutique |
| **Bureau d'accueil** | pierre taillée · botte de foin · pierre taillée / pierre taillée · établi · pierre taillée | 40 or, niv. 2 | arrivée des villageois |
| **Carrefour commercial** | laine (toute couleur) · bloc d'émeraude · laine / tonneau · table de cartographie · tonneau | 60 or, niv. 2 | marchands de passage (voir § 9) |
| **Atelier du cartographe** | bibliothèque · pupitre · bibliothèque / planches · table de cartographie · planches | 80 or, niv. 5 | recherches de ruines par le cartographe (voir § 13) |
| **Panneau du village** | **un seul bloc plein, n'importe lequel** | gratuit, niv. 1 (4 max) | panneau au nom de la guilde (niveau, membres en ligne, horde en cours) ; clic droit = menu des constructions, accroupi + clic droit = menu de la guilde |

Casser un bloc de la construction la désactive (les panneaux disparaissent) ; les plans se redemandent gratuitement.

### 5. Quêtes
Le tableau affiche **6 quêtes communes** à toute la guilde, mises à jour en direct sur les panneaux (type, objectif,
progression, récompense). Clic droit sur un panneau (ou `/guild quests`) : détail, progression, contributions,
**Livrer**, **Récupérer la récompense** (or au trésor, XP de guilde, objets, drapeaux, **cartes au trésor**, parfois une
**arme unique**). Une nouvelle quête remplace chaque quête terminée ou expirée. Seules les actions des **membres**
comptent, jamais en mode créatif.

| Type (panneau) | Objectif | Exemples |
|---|---|---|
| **Récolte** | casser des blocs (une même position ne compte qu'une fois) | Coupe de bois, Filon de fer, Éclats de diamant |
| **Chasse** | tuer des créatures | Chasse aux zombies, Désamorçage, Chasse aux pillards |
| **Livraison** | livrer des objets depuis le menu | Ravitaillement, Commande de la forge |
| **Défense** | remporter des hordes | Tenir la nuit, Rempart inébranlable |
| **Exploration** | passer près d'un lieu tiré au hasard, loin de la base | Expédition lointaine |
| **Fabrication** | fabriquer des objets (établi ou grille de l'inventaire) | Que la lumière soit (torches), Le chemin de fer, Le gâteau de la victoire |
| **Fourneau** | cuire ou fondre au fourneau, au fumoir ou au haut-fourneau (compté en retirant le résultat) | La fonderie, Le grand banquet, Le souffleur de verre |
| **Pêche** | pêcher des poissons, ou n'importe quelle prise | Jour de pêche, Pêche miraculeuse |
| **Élevage** | faire naître des animaux | Le petit élevage, Le troupeau |
| **Dressage** | apprivoiser ou dompter des animaux | Le meilleur ami (loups), La cavalerie (chevaux) |
| **Commerce** | échanger avec des villageois ou des marchands de passage | Jour de marché, La bourse aux émeraudes |
| **Enchantement** | enchanter des objets à la table d'enchantement | L'apprenti enchanteur, Les arcanes interdites |
| **Élites** / **Boss** | abattre des élites de horde (elles brillent, titre « Élite ») / vaincre des boss de horde | Chasseurs d'élite, La tête du chef de guerre |
| **☾ Chasse** | tuer des monstres de nuit | Nuit blanche, Les veilleurs de minuit |
| **Découverte** | entrer dans un biome (vérifié toutes les 5 s) | Mirage (désert), Au cœur de la jungle, L'île aux champignons, Les abysses |
| **Assortiment** | livrer 2 à 4 objets différents, chacun avec sa propre progression | Provisions pour l'hiver, Le cabinet de l'alchimiste, Le trésor de la couronne |

52 modèles par défaut, débloqués du niveau 1 au niveau 6 de la guilde (quantités et récompenses montent avec le niveau).
Un **assortiment** affiche ses objets à tour de rôle sur le panneau ; dans le menu, chaque objet a son bouton (clic =
livrer cet objet) et « Livrer l'assortiment » prend tout ce qui manque.

**Contrats légendaires** (guilde niveau 3+) : de temps en temps (8 % de chance par nouvelle quête, un seul à la fois, au
moins 2 jours de jeu entre deux), un panneau affiche un contrat **en or** (cadre doré dans le menu) : quantité ×2,
durée ×2, or ×2,5, XP ×3 et une **arme unique** à la remise (60 or au trésor si aucune n'est disponible). Les quêtes
ordinaires ont aussi 3 % de chance d'en donner une, tirée au moment de récupérer la récompense.

Carte au trésor : allez aux coordonnées indiquées, carte en main, pour déterrer un coffre.

### 6. Boutique et or
L'**or** est la monnaie : **1 lingot d'or = 1 or** (un bloc d'or = 9). Clic droit sur le panneau du comptoir :
clic gauche = acheter un lot (maj : ×8), clic droit = vendre un lot (maj : tout vendre). On paie avec l'or de
son inventaire. Le **trésor de guilde** (menu Trésor, `/guild deposit`, `/guild withdraw`) finance les drapeaux,
les constructions et les bannières décoratives. Les niveaux de guilde débloquent plus d'articles et de quêtes.

### 7. Villageois
Avec un **bureau d'accueil** et un **lit libre** dans le territoire, des villageois se présentent certains matins, parfois
**plusieurs à la fois** (1 à 3, dans la limite des places et des lits libres) : **[Accepter] / [Refuser]** dans le chat
pour chacun (ou dans le menu : clic gauche = accepter, clic droit = refuser ; `/guild villagers accept <nom>`).
Nombre maximum : 2 + 1 par niveau de guilde.
Menu « Villageois » (`/guild villagers`, ou accroupi + clic droit sur un villageois) : la liste montre la tâche, la zone,
le statut et ce que chacun transporte ; le menu d'un villageois règle sa tâche, sa **zone de travail** et sa mission,
permet de l'**appeler** à côté de soi (« Appeler ici », depuis le territoire ou une zone de la guilde, où que soit le
villageois : s'il est loin, sa dernière position est chargée un instant, « Appel en cours… ») ou de le renvoyer.

| Tâche | Ce qu'il fait |
|---|---|
| **Ranger la base** | vide les coffres de **dépôt** et range chaque objet dans le coffre de sa **catégorie** (19 thèmes, voir « Rangement par thème ») |
| **Mineur** | creuse vraiment sa **mine** (mode réel, voir plus bas) : escalier en colimaçon puis galeries, pierre et/ou minerais selon l'objectif ; ou minage simulé au fanion (mode simulé) |
| **Bûcheron** | coupe les arbres naturels de sa **coupe de bois** (ou autour de lui), replante, et **plante de nouvelles pousses** dans sa zone (voir « Plantation ») |
| **Fermier** | récolte les cultures mûres de ses **champs** (ou autour de lui), replante, **sème les terres vides** et laboure près de l'eau ; selon les **autorisations du fanion**, relaboure la terre piétinée ou transforme toute la zone en champs (voir « Autorisations du fanion ») |
| **Garde** | **épéiste** ou **archer** ; patrouille dans sa zone ou sur son **parcours de garde**, sinon défend le cœur ; toujours au cœur pendant les hordes ; s'équipe dans les coffres et se replie pour se soigner (voir « Gardes ») |
| **Exploration** | part vers la **destination** choisie (biome ou construction, voir « Destinations d'expédition ») : il quitte vraiment la base et revient quelques minutes plus tard avec le butin du lieu — risque de ne pas revenir |
| **Expédition au Nether** | passe par un **portail du Nether construit dans le territoire** vers la destination choisie (forteresse, bastion, forêts…) — risque plus élevé |
| **Forgeron** (guilde niv. 3) | répare peu à peu l'équipement abîmé posé sur les **porte-armures** du territoire, à l'**enclume** la plus proche, qu'il use (voir « Forgeron et alchimiste ») |
| **Alchimiste** (guilde niv. 5) | transforme les **livres** du stockage en **livres enchantés** à la **table d'enchantement** du territoire (voir « Forgeron et alchimiste ») |
| **Cartographe** | travaille à l'**Atelier du cartographe** sur les recherches de ruines de la guilde (voir § 13) |

#### Zones de travail (fanions)
Menu Villageois → **Zones et parcours** → « Créer une zone » : choisissez le type (**Mine**, **Coupe de bois**,
**Champs**, **Patrouille**) et recevez un **fanion** (bannière). **Plantez-le** (clic droit sur un bloc) : la zone est
créée là, le fanion prend son nom et ses réglages s'ouvrent. Clic droit sur un type = créer la zone à vos pieds, sans
fanion (pratique au fond d'une mine).
- Une zone peut **sortir du territoire** (à moins de 192 blocs du cœur), jamais entrer dans celui d'une autre guilde.
  Les villageois n'y travaillent que lorsqu'elle est chargée (un joueur dans les parages).
- **Rayon** en chunks autour du chunk du fanion : 0 = son chunk, 1 = 3×3 chunks (par défaut), jusqu'à 3 = 7×7.
  Une mine **réelle** ne creuse que dans ce carré ; une mine **simulée** n'utilise que son fanion : sa **hauteur fixe
  la profondeur** (et donc les minerais).
- **Objectif** : mine → *Tout venant*, *Minerais* (que des minerais, moins de tirages) ou *Pierre et variantes* (pierre,
  andésite, diorite, granite, tuf, calcite, gravier ; pierre des abîmes en profondeur) ; coupe de bois → tous les arbres ou
  une essence (chêne, sapin, bouleau, acajou, acacia, chêne noir, palétuvier, cerisier, chêne pâle, peuplier) ; champs →
  toutes les cultures (y compris melons, citrouilles et canne à sucre, la tige / la base restent) ou une seule (blé,
  carottes, pommes de terre, betteraves) ; patrouille → les gardes marchent au hasard dans la zone et combattent ce qui y
  entre (et un peu autour).
- Réglages : rayon −/+, objectif, renommer, **Afficher** (limites en particules pendant 20 s, pour vous seul), villageois
  affectés (un villageois d'une autre tâche affecté ici prend la tâche de la zone), **déplacer le fanion**, supprimer.
- Retirer le fanion (avec le droit de gérer les villageois) supprime la zone, sans rendre d'objet ; les autres joueurs ne
  peuvent pas le retirer. Si le fanion disparaît autrement, la zone continue de fonctionner (« fanion absent »).
- Menu d'un villageois → « Zone de travail » : une zone de son type, un parcours (gardes) ou **Automatique**.

**Mode automatique** (aucune zone choisie) : le bûcheron et le fermier travaillent **dans le territoire et jusqu'à 48 blocs
autour**, jamais plus loin (les arbres / cultures les plus proches du territoire d'abord, jamais chez une autre guilde) ;
le mineur va à la mine de la guilde la plus proche (sauf une mine réelle épuisée), sinon mine (en simulé) la pierre à
découvert la plus proche (sinon au cœur) ; le garde défend le cœur. Le statut indique ce qu'il a choisi
(« Coupe auto près de x y z »). Le bûcheron coupe les arbres jusqu'à 160 bûches (sapins et acajous géants compris).

#### Mine réelle
Une zone **Mine** est en mode **Réel** par défaut (bouton « Mode : Réel / Simulé » de son menu ; le mode simulé garde
l'ancien fonctionnement : aucun bloc cassé, butin tiré selon la hauteur du fanion, objectifs prioritaires 4 fois plus
fréquents). En mode réel, les mineurs creusent vraiment, dans le carré de la zone :
1. **Escalier** : depuis le fanion, un escalier en colimaçon de **2 blocs de large** (les mineurs s'y croisent sans se
   gêner, chaque virage forme un petit palier) descend vers la **profondeur cible**. Son sens est choisi en sondant le
   terrain : le plus loin possible des constructions, de l'eau et de la lave. Les joueurs l'empruntent aussi ; les
   villageois ne savent pas grimper aux échelles, ils marchent toujours par l'escalier. Une torche toutes les 8 marches
   (charbon du sac s'il y en a, sinon gratuite).
2. **Niveaux tous les 6 blocs** : du premier niveau sous la surface jusqu'à la profondeur cible, chaque niveau a un
   palier, une galerie principale (2 de haut) et des galeries latérales tous les 3 blocs (objectif *Pierre* : des
   **salles de carrière** 5×5). Un niveau s'ouvre dès que l'escalier l'atteint, pendant que l'escalier continue de
   descendre : les mineurs se répartissent sur plusieurs niveaux. Tout fini (ou des mineurs sans travail), l'escalier
   descend encore plus bas.
3. **Aucun minerai visible laissé** : murs, plafonds et sols (le sol est rebouché) de l'escalier, des galeries et des
   salles ; les **filons** sont suivis jusqu'à 64 blocs et **jusqu'à 16 blocs hors de la zone** (jamais chez une autre
   guilde ni sous une construction). Un filon hors de portée est repris plus tard par un mineur qui creuse un petit
   tunnel d'approche depuis le niveau.
4. **Retour** : sac plein, mission atteinte ou fin de journée (ils remontent avant la nuit) → ils remontent par leurs
   tunnels, vident le sac au coffre de dépôt, puis redescendent au front de taille.

**Carrière (objectif *Pierre et variantes*)** : au lieu de galeries, les mineurs creusent **tout le carré de la zone**,
par **gradins de 3 blocs**, de la surface jusqu'à la profondeur cible. Seul l'escalier reste debout : une **tour**
entourée d'un mur de roche, percée d'une ouverture vers chaque gradin, et une **chaussée** de roche relie le fanion au
bord de la carrière. Les mineurs (jusqu'à 20) se partagent des **bandes** ; un gradin ne se creuse que sous les parties
finies du gradin du dessus, jamais sous les pieds de quelqu'un. Herbes, fleurs, arbres naturels et neige de surface
sont dégagés ; une construction posée dans la zone (même une planche) garde la colonne de roche qui la porte. Torches au
sol tous les 8 blocs. Ils ne rapportent que la pierre et ses variantes, les minerais et les objectifs prioritaires (la
terre, le gravier et le sable sont jetés). `stoneQuarry: false` dans `villagers.json5` remet l'ancien fonctionnement.

**Jusqu'à 20 mineurs par mine** : le travail est partagé en petits chantiers (front de l'escalier, élargissement,
paliers, tronçons de galerie, galeries latérales, salles, filons) ; chaque mineur prend le chantier libre le plus proche,
jamais deux sur les mêmes blocs. Les blocs cassés donnent leur vrai butin (comme une pioche en fer) ; l'objectif
*Minerais* ne garde que les minerais (et les objectifs prioritaires) plus quelques blocs de pierre pour colmater.
**Objectifs prioritaires** (par exemple la diorite) : minés comme des filons, gardés en priorité dans le sac (jamais
utilisés pour colmater) et rapportés au coffre.

**Règles anti-casse** : le mineur ne casse que des **blocs naturels** (pierre, ardoise des abîmes, tuf, calcite, terre,
gravier, argile, sable, grès, minerais…, liste dans `villagers.json5`) ; jamais un bloc posé par un joueur (planches,
briques, pierre taillée, verre, laine…), un bloc à données (coffre, panneau…), un bloc chez une **autre guilde**, ni un
bloc naturel qui **porte** un bloc non naturel (pas de sape sous une construction) ou un cadre, un tableau, un support
d'armure. Il n'ouvre jamais sur l'eau ou la lave : il **colmate** d'abord avec un bloc de son sac (sinon il abandonne la
galerie ; de l'eau ou de la lave au-dessus : galerie abandonnée). Il **comble les trous** du sol. Seuls les chunks
chargés sont touchés.

Menu de la zone (mine réelle) : **profondeur cible** (−/+, entre le fond du monde + 5 et 4 blocs sous le fanion ; par
défaut : Y -54 pour *Minerais*, Y 16 pour *Tout venant*, 12 blocs sous le fanion pour *Pierre*), **rayon**, villageois
affectés (20 mineurs au plus) et **Exploitation** : escalier, chaque niveau (avancement, minerais, objectifs, mineurs
présents), les mineurs et leur statut, filons en attente, dernier incident et « Réinitialiser l'exploitation ». Le statut
de chaque mineur dit ce qu'il fait (« Galerie niveau Y 46 (3/8) », « Suit un filon hors zone… », « Élargit
l'escalier… », « Remonte déposer », « Bloqué : lave »…). Les mines creusées avant la version 1.2 gardent leur escalier
d'un bloc ; des niveaux sont ajoutés sur le chemin (« Réinitialiser l'exploitation » leur donne un escalier de 2 blocs).

#### Parcours de garde
« Nouveau parcours » (menu Zones et parcours, ou `/guild villagers route new`) : faites un clic droit sur les blocs où le
garde doit passer, dans l'ordre (2 à 16 points) ; la barre d'action et un aperçu en particules vous guident.
**Accroupi + clic droit** (ou [Terminer] dans le chat) enregistre le parcours ; [Retirer le dernier point], [Abandonner] ;
abandon automatique après 2 minutes sans nouveau point. Les gardes affectés le parcourent en **boucle** (ou en
**aller-retour**, au choix), marquent une courte pause à chaque point, combattent les monstres qui s'approchent (jamais à
plus de 16 blocs du parcours) puis reprennent au point le plus proche. Le parcours se renomme, s'affiche, se retrace.

**Missions** (menu du villageois, bouton cible) pour le mineur, le bûcheron, le fermier et les expéditions :
- **Travail continu** : il travaille sans s'arrêter (il vide son sac au coffre de dépôt dès qu'il est plein) ;
- **Remplir son sac et rentrer** : un sac plein (ou une expédition), vidé au coffre, puis il s'arrête ;
- **Rapporter une quantité** : par exemple « 64 fer brut » ou « 128 bûches de chêne » (prenez l'objet en main
  et cliquez sur « Objet recherché ») — il continue jusqu'à les avoir rapportés, puis s'arrête et prévient la guilde.

#### Objectifs prioritaires
Bouton « Objectifs prioritaires » du menu d'un **mineur**, d'un **bûcheron** ou d'un **fermier**, ou du menu d'une zone
(mine, coupe, champs) : jusqu'à **9 objets** que le villageois va chercher **en priorité**, même un peu hors de sa zone
(16 blocs, jamais chez une autre guilde). Cliquez sur une case libre avec l'objet sur le curseur, ou faites un shift-clic
dessus dans votre inventaire : seul son type est noté, votre objet n'est ni pris ni déplacé. Un clic sur un objectif le
retire, « Vider » efface la liste. Les objectifs d'une zone s'ajoutent à ceux de chacun de ses villageois.
- Bûcheron : bûches ou planches d'une essence, pousses, pommes, bâtons (il récolte alors aussi le feuillage).
- Fermier : blé, graines, carottes, pommes de terre, betteraves, melons, citrouilles, canne à sucre.
- Mineur : pierres et minerais (voir « Mine réelle »).
- Expéditions : les objets voulus présents dans le butin de la destination sont 3 fois plus fréquents.

#### Gardes : épéistes et archers
- Chaque garde est **épéiste** (corps à corps) ou **archer**. En mode **Auto** (par défaut), un nouveau garde prend le
  rôle qui équilibre les gardes de la guilde ; le bouton « Rôle » de son menu permet de l'imposer (Auto → Épéiste →
  Archer). Les archers tirent de vraies flèches jusqu'à 16 blocs et reculent quand un monstre s'approche ; leurs flèches
  traversent joueurs, villageois et animaux : elles ne touchent que les monstres.
- **Équipement** : hors combat, les gardes vont chercher les meilleures pièces d'armure (et une meilleure épée ou un arc
  enchanté) dans les coffres du **Stockage** de la guilde et y rapportent l'ancienne pièce. L'armure s'use puis est
  remplacée. Le menu du garde affiche sa vie, son armure et ses pièces (Maj + clic : récupérer son équipement).
  L'équipement retourne au stockage si le garde change de tâche, au joueur qui le renvoie, et tombe au sol s'il meurt.
  Les clients vanilla n'affichent pas d'armure sur un villageois : elle se voit dans le menu.
- **Repli** : sous 40 % de sa vie, un garde quitte le combat et court se soigner à son lit (« Se replie pour se
  soigner », puis « Se soigne (12/20 ❤) »), même pendant une horde, en frappant encore ce qui est à portée de bras. Il
  reprend son poste à 90 % ; hors combat, il se soigne lentement.
- **Pas de tir ami** : impossible de blesser les villageois de votre guilde (coups, balayage, flèches, tridents…). Pour
  vous en séparer, utilisez « Renvoyer » dans son menu.
- **Équipement de base selon le niveau de la guilde** (gratuit, il ne tombe pas et ne s'use pas ; un meilleur
  équipement pris dans les coffres reste prioritaire) :

| Niveau | Épée | Tir | Armure |
|---|---|---|---|
| 1 | bois | arc | aucune |
| 2-3 | pierre | arc (Puissance I au niv. 3) | cuir |
| 4-5 | fer | arc Puissance I / II | mailles |
| 6 | fer Tranchant I | **arbalète** Charge rapide I | fer |
| 7-8 | diamant (Tranchant I au niv. 8) | arbalète Charge rapide, Perforation I | fer |
| 9 | diamant Tranchant II | arbalète Charge rapide II, Perforation II | diamant |
| 10 | netherite Tranchant II | arbalète Charge rapide III, Perforation II | diamant |

#### Vie, nuit et refuge
- **Plus de vie avec le niveau** : chaque villageois de la guilde gagne +4 PV (2 cœurs) par niveau de guilde au-delà du
  premier, les gardes encore plus (×1,5 + 10 PV) ; hors combat, ils se soignent lentement. La vie s'affiche dans le menu.
- **Retour avant la nuit** : en fin de journée (heure 11000), les travailleurs posent leurs outils et rentrent **en
  courant** à leur lit (sans lit : un lit libre du territoire, sinon le bureau d'accueil ou le cœur). À la tombée de la
  nuit, ou dès qu'une horde arrive, un travailleur encore dehors est **mis à l'abri** près de son lit (téléporté).
  Les gardes restent dehors.

#### Plantation
- **Bûcheron** : quand il n'a plus d'arbre à couper dans sa coupe de bois, il plante de nouvelles pousses sur le sol
  naturel libre (herbe, terre, podzol, mousse, boue), espacées de 5 blocs, 8 arbres au plus par chunk ; jamais sur un
  chemin, une terre labourée, à côté de vos constructions ni près d'une autre guilde. Essence de la zone d'abord, puis
  celles de vos objectifs prioritaires ; chêne noir et chêne pâle par carrés de 4. Les pousses viennent de son sac (il
  ramasse celles qui tombent du feuillage), sinon des coffres « Graines et pousses », de dépôt ou « Divers ».
- **Fermier** : quand rien n'est mûr, il sème les terres labourées vides (blé, carottes, pommes de terre, betteraves),
  avec les graines de son sac ou du stockage ; dans une zone Champs, il **laboure** aussi l'herbe et la terre à moins de
  4 blocs d'une eau (jamais un chemin ni à côté de vos constructions). Pour aller plus loin (relabourer, creuser des
  points d'eau, transformer toute la zone en champs), voir « Autorisations du fanion ».
- Le statut dit ce qu'il fait (« Plante des pousses de chêne (3/12) », « Sème du blé », « Va chercher des graines au
  stockage ») et pourquoi il s'arrête quand il n'a plus rien à planter.

#### Autorisations du fanion (champs et coupes de bois)
Dans le menu d'une zone **Champs** ou **Coupe de bois**, le bouton **« Autorisations du fanion »** choisit ce que ses
villageois ont le droit d'y faire (droit de gérer les villageois requis) :

| Autorisation | Zone | Par défaut | Effet |
|---|---|---|---|
| Récolter · Replanter · Semer | Champs | oui | récolte les cultures mûres, replante, sème les terres vides avec les graines du sac ou des coffres |
| Relabourer | Champs | oui | relaboure la terre labourée devenue terre (piétinée ou desséchée) et ressème la même culture |
| Labourer | Champs | oui (sol déjà hydraté) | laboure l'herbe, la terre, la terre stérile et la terre racinée ; partout dans la zone avec « Points d'eau » |
| Créer des points d'eau | Champs | non | creuse un trou d'eau d'un bloc tous les 9 blocs, sur un sol sûr, pour que tout le champ soit hydraté ; rebouche ceux qui ont disparu |
| Dégager le sol | Champs | non | arrache herbes, fougères, fleurs et buissons sur le sol à labourer |
| Aplanir | Champs | non | comble les trous et arase les bosses d'un bloc |
| Poudre d'os | Champs | non | utilise la poudre d'os du stockage sur les cultures |
| Couper · Replanter · Planter | Coupe de bois | oui | coupe les arbres naturels, replante, plante de nouvelles pousses |

Deux préréglages : **« Entretenir un champ »** (un champ que vous avez déjà : récolte, semis, relabour, rien de nouveau
n'est labouré) et **« Créer des champs »** (toute la zone devient un champ : dégager, points d'eau, labour, semis).
Le fermier fait d'abord les récoltes, puis le sol entre deux récoltes, toujours dans la zone, jamais chez une autre
guilde ni contre vos constructions (barrières, torches, lanternes, chemins et composteurs sont acceptés). L'eau est
gratuite (`farmer.irrigation.waterBucketCost` pour la faire payer en seaux). Son statut dit ce qu'il fait
(« Laboure (12/80) », « Creuse un point d'eau », « Champ terminé ») et son menu compte les terres labourées et les points
d'eau creusés. Melons, citrouilles et canne à sucre sont récoltés mais pas semés.

#### Forgeron et alchimiste
- **Forgeron** (guilde niveau 3) : posez vos armures, armes ou outils abîmés sur des **porte-armures** du territoire, avec
  une **enclume** à moins de 12 blocs. Le forgeron va à l'enclume et rend peu à peu leur durabilité aux objets (10 % toutes
  les 8 s, le plus abîmé d'abord) **sans jamais les retirer du porte-armure**. Chaque réparation use l'enclume comme en
  vanilla (12 % de chance) : ébréchée, endommagée, puis brisée ; il passe alors à l'enclume suivante. Les objets
  incassables (armes uniques) ne sont pas concernés. Plusieurs forgerons se répartissent les porte-armures.
- **Alchimiste** (guilde niveau 5) : avec une **table d'enchantement** dans le territoire et des **livres** dans le
  stockage, il fabrique des **livres enchantés** qu'il range dans le coffre « Livres et enchantements ». Il choisit la
  table qui a le plus de bibliothèques (règle vanilla, 15 au plus) : avec 15 bibliothèques, chaque livre est enchanté au
  niveau 30 ; une table seule donne des enchantements faibles. Comptez 6 s par niveau, 8 livres par jour et par
  alchimiste au plus ; pas de lapis par défaut (`alchemist.lapisPerBook`). Comme à la table vanilla, Raccommodage et les
  autres enchantements « trésor » ne sortent jamais.
- Leur menu affiche leurs statistiques (objets réparés, enclumes usées ; livres faits, meilleur enchantement).

#### Travail à distance (chunks chargés)
Un villageois au travail garde chargés ses alentours, l'endroit où il se rend (coffre de dépôt, lit le soir, retour
d'expédition) et sa zone de travail : bûcherons, fermiers, mineurs et trieurs **continuent quand vous êtes loin**.
Par défaut, il suffit qu'un membre de la guilde soit connecté (`villagers.json5` → `chunkLoading`, mode `online`) ;
`always` les fait travailler même serveur vide (mettez alors `pause-when-empty-seconds=0` dans `server.properties`,
sinon le serveur se met en pause sans joueur), `never` revient à l'ancien fonctionnement. Rien n'est chargé pour un
villageois sans tâche, en expédition ou couché, ni pour les gardes. Les cultures et les pousses y grandissent, aucun
monstre n'y apparaît. Limites : 64 chunks par guilde, 512 pour le serveur (menu Villageois « Chunks des villageois »,
`/guild admin villager chunks`).

#### Destinations d'expédition
Pour un explorateur ou une expédition au Nether, le bouton « **Destination** » du menu du villageois choisit où il part.
Chaque destination indique sa durée, son risque de ne jamais revenir, son butin typique, ses trouvailles rares et le
niveau de guilde requis ; plus le lieu est dangereux, meilleur est le butin (et plus les armes uniques sont fréquentes).
Le choix vaut pour les départs suivants et s'affiche dans la liste, au départ et au retour.

| Exploration | Niveau | Durée | Risque | Butin |
|---|---|---|---|---|
| Forêt et plaines (par défaut) | 1 | 8 min | 5 % | graines, pousses, cuir, émeraudes, carte au trésor rare |
| Désert | 1 | 8 min | 6 % | sable, grès, cactus, os, peau de lapin, quelques pépites d'or |
| Jungle | 1 | ~9 min | 7 % | bambou, fèves de cacao, pastèque, pousses d'acajou, lianes |
| Taïga et neige | 1 | ~9 min | 6 % | pousses de sapin, baies sucrées, boules de neige, glace, lapin |
| Océan et plage | 1 | ~9 min | 7 % | varech, poissons, cornichons de mer, prismarine, coquille de nautile rare, cartes au trésor |
| Marais et mangrove | 1 | ~9 min | 7 % | argile, boue, propagules, nénuphars, slime |
| Cerisiers | 2 | ~9 min | 5 % | pétales roses, pousses de cerisier, rayons de miel |
| Montagnes | 2 | ~10 min | 8 % | pierre, charbon, fer et cuivre bruts, émeraudes et corne de chèvre rares |
| Village | 2 | 10 min | 6 % | pain, blé, carottes, pommes de terre, fer, émeraudes, selle rare |
| Puits de mine abandonné | 3 | 10 min | 10 % | rails, charbon, fer brut, baies lumineuses, étiquette rare |
| Épave et ruines | 3 | 10 min | 8 % | papier, charbon, émeraudes, carte au trésor 25 % |
| Temple du désert | 4 | 12 min | 12 % | os, poudre à canon, TNT, émeraudes, or, livre enchanté et diamant rares |
| Temple de la jungle | 4 | 12 min | 12 % | bambou, flèches, émeraudes, livre enchanté et diamant rares |
| Avant-poste de pillards | 5 | 12 min | 18 % | flèches, chêne noir, émeraudes, arbalète, fioles d'expérience |
| Monument océanique | 6 | 14 min | 20 % | éclats et cristaux de prismarine, lanternes aquatiques, éponge rare |
| Ville ancienne | 7 | 16 min | 25 % | sculk, bougies, améthyste, éclat d'écho et fragments de disque rares |

| Nether (portail dans le territoire) | Niveau | Durée | Risque | Butin |
|---|---|---|---|---|
| Terres du Nether (par défaut) | 1 | 12 min | 12 % | quartz, or, poudre lumineuse, bâtons de blaze… |
| Forêt carmin | 2 | 12 min | 12 % | tiges et champignons carmin, blocs de verrues, champilampes |
| Forêt biscornue | 2 | 12 min | 10 % | tiges et champignons biscornus, lianes, perles de l'Ender rares |
| Vallée des âmes | 3 | ~13 min | 15 % | sable et terre des âmes, os, larme de ghast rare |
| Deltas de basalte | 3 | ~13 min | 15 % | basalte, roche noire, crème et blocs de magma |
| Forteresse du Nether | 4 | 15 min | 16 % | verrues du Nether, bâtons de blaze, briques du Nether, crâne de wither squelette très rare |
| Bastion | 6 | 18 min | 22 % | pépites d'or, roche noire, débris antiques rares, fragment de netherite très rare |

Les destinations (durées, risques, tables de butin) se règlent dans `villagers.json5` (`expeditions.destinations`).

#### Rangement par thème
Le trieur (tâche « Ranger la base ») vide les coffres de **dépôt** et range chaque objet dans le coffre de sa
**catégorie** ; un objet sans coffre de sa catégorie va dans un coffre « **Divers** », sinon il reste au dépôt.
Quasiment tous les objets du jeu sont reconnus.

| Catégorie | Exemples |
|---|---|
| Bois | bûches, planches, dalles / escaliers / barrières / portes / trappes / panneaux en bois, bâtons, bols |
| Pierre | pierre, galets, pierre des abîmes, andésite, diorite, granite, tuf, calcite, grès, briques de pierre et dérivés |
| Terre, sable et gravier | terre, herbe, podzol, boue, argile, sable, gravier, neige, glace |
| Blocs de construction | briques, verre, vitres, béton, terre cuite, laine, tapis, cuivre, prismarine, barreaux |
| Mobilier et décoration | établi, fours, coffres, enclumes, lits, torches, lanternes, bougies, échelles, tableaux, cadres, pots, bannières, disques |
| Minerais et lingots | minerais, minerais bruts, lingots, pépites, gemmes, charbon, netherite, améthyste, blocs de minerai, silex |
| Redstone | redstone, répéteurs, pistons, leviers, boutons, plaques de pression, trémies, rails, wagonnets, TNT |
| Outils | pioches, haches, pelles, houes, cisailles, briquet, canne à pêche, seaux, boussoles, cartes, longue-vue, selle, bateaux |
| Armes | épées, arcs, arbalètes, trident, masse, flèches, charges de vent |
| Armures | casques, plastrons, jambières, bottes, bouclier, élytres, armures de cheval et de loup, modèles de forge |
| Livres et enchantements | livres enchantés, livres, papier, fioles d'expérience, bibliothèques, pupitre |
| Potions et alchimie | potions, fioles, alambic, chaudron, poudre de blaze, œil d'araignée fermenté, souffle de dragon |
| Nourriture | viandes, poissons, pain, fruits, légumes, gâteau, soupes, œufs, lait, sucre, blé |
| Graines et pousses | graines, pousses, propagule, azalées, fèves de cacao |
| Plantes et fleurs | fleurs, feuilles, herbes, lianes, mousses, champignons, coraux, algues, canne à sucre, bambou, cactus |
| Butin de monstres | os, ficelle, poudre à canon, chair putréfiée, perles de l'Ender, bâtons de blaze, larmes de ghast, cuir, plumes, peaux de lapin, poches d'encre, **têtes et crânes**, carapaces de shulker, coquilles de nautile, totems, œufs d'apparition |
| Nether | netherrack, briques du Nether, quartz, sable et terre des âmes, basalte, roche noire, verrues, pierre lumineuse, obsidienne, magma |
| End | pierre de l'End, purpur, chorus, barres de l'End, œil de l'Ender, cristal de l'End, boîtes de Shulker, œuf de dragon |
| Colorants | les 16 colorants |

Menu Villageois → **Stockage** → « Ajouter un coffre », puis clic droit sur un coffre du territoire et choisissez son
rôle (chaque catégorie montre son icône et des exemples) : le coffre **prend le nom de son rôle**, affiché quand on
l'ouvre. Dans le menu Stockage : clic gauche sur un coffre pour changer son rôle, clic droit pour le retirer ;
« **Afficher les coffres** » les signale par des particules colorées pendant 20 s ; « **Où ranger l'objet en main ?** »
indique où ira l'objet que vous tenez. Les catégories (règles : objets, tags, propriétés) se règlent dans
`villagers.json5`.

La nuit, les travailleurs dorment ; les gardes restent dehors. Les villageois peuvent encore **mourir** (gardes au
combat, flèches perdues) : posez des lits près du cœur et équipez vos gardes. Seuls les membres peuvent commercer avec eux.

### 8. Arbre d'évolution
Menu « Arbre d'évolution » (`/guild upgrades`, ou le bouton du menu principal) : la guilde dépense l'**or du
trésor** et des **matériaux** pour des **bonus permanents**. Les matériaux sont pris dans l'inventaire du joueur qui
débloque (objets simples : ni renommés, ni enchantés, ni abîmés ; impossible en créatif). Il faut le droit de dépenser
le trésor (officier par défaut). Chaque branche se lit de gauche à droite : un palier demande le précédent et un
niveau de guilde ; les liens deviennent verts une fois le palier précédent débloqué.

| Branche | Palier 1 | Palier 2 | Palier 3 | Palier 4 |
|---|---|---|---|---|
| **Défense** | Cœur renforcé (niv. 1, 30 or + 8 lingots de fer) : cœur +25 % | Gardes aguerris (niv. 2, 60 or + 2 blocs de fer) : dégâts des gardes +50 % | Bannière de ralliement (niv. 3, 100 or + 2 pommes dorées) : Régénération I dans le territoire pendant les hordes | Bastion (niv. 5, 200 or + 4 diamants) : cœur +50 %, or perdu en défaite −50 %, plus aucun départ de villageois après une défaite |
| **Économie** | Primes de quêtes (niv. 1, 25 or) : or et XP des quêtes +20 % | Butin de guerre (niv. 2, 50 or + 8 émeraudes) : or et XP des victoires +25 % | Cartographes (niv. 3, 80 or + 16 papiers) : drapeaux d'extension −30 % | Âge d'or (niv. 5, 200 or + 1 bloc de diamant) : quêtes +20 %, victoires +25 % |
| **Expansion** | Frontières élargies (niv. 1, 30 or) : +4 chunks | Logements (niv. 2, 60 or + 3 lits blancs) : +2 villageois | Artisans efficaces (niv. 3, 100 or + 16 lingots de fer) : tâches des villageois 30 % plus courtes | Grand domaine (niv. 5, 200 or + 4 diamants) : +8 chunks, +2 villageois, Célérité I dans le territoire |

Les bonus d'un même effet s'additionnent. L'or des quêtes de **livraison** n'est jamais augmenté (pas de boucle d'or
avec la boutique). Branches, paliers, coûts et effets se configurent dans `upgrades.json5`.

### 9. Carrefour commercial et marchands de passage
Une fois le **Carrefour commercial** construit (menu Constructions), un **Marchand de passage** (marchand ambulant
vanilla, nommé avec le palier du Carrefour) peut s'y arrêter **chaque matin** : chance 50 % + 15 % par palier. Il reste
environ **10 minutes** (il repart au plus tard à la tombée de la nuit) et arrive dès qu'un membre est à la base.
Clic droit sur lui pour commercer : on paie en **lingots d'or** (blocs d'or + lingots au-delà de 64 or). Les marchands
**ne rachètent rien** et ne vendent jamais moins cher que la boutique (pas de boucle d'or). Seuls les membres peuvent
commercer dans le territoire.

Clic droit sur le panneau du Carrefour (ou `/guild trade`) : palier, marchands présents (position, temps restant),
chance de la prochaine visite, **aperçu de tous les échanges** de chaque palier, et **amélioration** :

| Palier | Condition et coût (trésor + inventaire du joueur, objets simples) | Échanges par visite | Exemples |
|---|---|---|---|
| **I - Halte marchande** | construit (niv. 2) | 5 à 7 | toutes les pousses (cerisier, palétuvier, chêne pâle…), graines, colorants, fleurs, canne à sucre, cactus, bambou, cacao, lianes, nénuphars, baies lumineuses… |
| **II - Comptoir prospère** | niv. 3, 150 or + 16 émeraudes | 6 à 8 | boules de slime, rayons de miel, coquilles de nautile, éclats d'améthyste, glace bleue, disques, étiquettes, selles, cornichons de mer… |
| **III - Grand carrefour** | niv. 5, 400 or + 2 blocs de diamant | 7 à 9 | « trades de fou » : livres Raccommodage, Solidité III, Efficacité V, Fortune III, Toucher de soie, Butin III, Protection IV, totem d'immortalité, pomme dorée enchantée, modèle de forge (amélioration en netherite), cœur de la mer, trident, carapaces de shulker, parfois des **élytres** (chères) et une **arme unique** (360 or, voir § 11) |

Un Carrefour de palier III tire surtout dans sa table, mais aussi dans celles des paliers inférieurs. Il faut le droit
de dépenser le trésor (officier par défaut) pour l'améliorer ; impossible en créatif. Tout se règle dans `trade.json5`.

### 10. Événements
Des événements aléatoires pimentent la vie des guildes (tirage une fois par jour, configurable dans `events.json5`) :

| Événement | Quand | Effet |
|---|---|---|
| **Nuit de sang** | au crépuscule, 7 % de chance si une guilde a atteint le palier de nuit 3, au plus une tous les 4 jours | annonce à **tout le serveur** (titre rouge « La lune rougit… »). Cette nuit-là, **toutes les hordes** ont 60 % de monstres en plus, +15 % de chance d'élite (plafonnée), +25 % de vie et de dégâts, souvent un boss… mais **or et XP ×2** et plus d'objets. La barre de progression porte « ☾ Nuit de sang ». |
| **Raid de pillards** | le matin, 5 % (guilde niv. 2+, au plus un tous les 3 jours) | les pillards attendent qu'un membre soit près du cœur, préviennent (« ils attaquent dans 2 minutes »), puis une horde d'**illageois** (pillards, vindicateurs, évocateur rare, **ravageur** en boss) attaque **en plein jour**. Mêmes règles que les hordes (cœur, victoire, défaite), récompenses ×1,2, fin quand tous sont morts ou après 10 min (victoire si le cœur tient), sans changer le palier de nuit. Personne à la base : ils renoncent au crépuscule. |
| **Caravane marchande** | le matin, 15 % (au plus une tous les 2 jours) | un marchand et deux lamas s'installent au Carrefour commercial (ou près du cœur) ~10 min avec des marchandises de caravane. |
| **Marchand de livres enchantés** | le matin, 6 % (guilde niv. 3+, au plus un tous les 4 jours) | l'**Érudit itinérant** vend 3 à 5 livres enchantés tirés dans une liste pondérée (Raccommodage, Solidité III, Efficacité V, Fortune III, Toucher de soie, Butin III, Protection IV, Tranchant V, Chute amortie IV…). |

L'événement en cours ou à venir (nuit de sang, raid, marchands à la base) s'affiche dans le bouton « Hordes » du menu
principal et dans le menu des hordes (« Prochaine nuit »).

### 11. Armes uniques
Des **armes et outils légendaires**, chacun en un seul modèle : nom coloré selon la rareté (**Rare**, **Épique**,
**Légendaire**, **Mythique**), histoire dans la description, **enchantements de fou** (niveaux au-delà du maximum
vanilla, combinaisons impossibles autrement, enchantements Guildy), attributs bonus, brillance, souvent incassables, et
surtout un **pouvoir**. Ce sont des objets vanilla (clients vanilla, sans mod) : ils se rangent, s'échangent et se
réparent comme leur objet de base.

**Où les trouver** (chances par défaut, `uniques.json5`) :

| Source | Chance | Armes possibles |
|---|---|---|
| Boss d'une horde | 4 % | surtout Rares et Épiques, parfois une Légendaire |
| Boss pendant une **nuit de sang** | 20 % | Épiques, Légendaires et les deux **Mythiques** |
| Récompense de quête | 1 % | Rares et Épiques |
| Quête légendaire | 35 % | Épiques, Légendaires, rarement Mythiques |
| Expédition d'un villageois | 2 % | Rares et Épiques (très rarement les Bottes de Zéphyr) |
| Carrefour commercial palier III | une offre « Arme unique » de temps en temps, **360 or** (40 blocs d'or) | Épiques et Légendaires |

L'arme d'un boss tombe à sa mort, **brille** et ne disparaît jamais (insensible au feu, à la lave et aux explosions).
Le premier joueur qui obtient une arme **Légendaire** ou **Mythique** est **annoncé à tout le serveur** (« ✦ Steve a obtenu
[Foudrelame] (butin d'un boss de horde) ! », l'arme s'affiche au survol).

| Arme | Rareté | Base | Pouvoir |
|---|---|---|---|
| **Foudrelame** | Légendaire | épée en netherite (Tranchant VI, Foudroiement III) | **Appel de la foudre** : clic droit, la foudre s'abat sur 4 monstres autour du point visé (5 ❤ chacun), recharge 12 s |
| **Croc-de-Givre** | Épique | épée en diamant (Givre II) | **Onde de givre** : clic droit, gèle et ralentit les monstres à 6 blocs, recharge 18 s |
| **Murmure du Néant** | Légendaire | épée en netherite rapide (Écho III) | **Pas de l'ombre** : clic droit, téléportation derrière le monstre visé et coup de 5 ❤, recharge 8 s |
| **Hache de l'Ancien Forestier** | Épique | hache en netherite (Efficacité V) | **Abattage** : coupe tout l'arbre naturel d'un coup |
| **Pioche du Titan** | Légendaire | pioche en netherite (Efficacité VI, portée +1) | **Excavation** : creuse en 3×3 dans la roche naturelle |
| **Pic du Filon** | Rare | pioche en diamant (Fortune II) | **Filon** : extrait tout le filon de minerai (32 blocs au plus) |
| **Bottes de Zéphyr** | Légendaire | bottes en netherite (Apesanteur) | **Double saut** (appuyer deux fois sur Sauter), plus aucun dégât de chute |
| **Bottes du Coursier** | Rare | bottes en cuir teint | vitesse +20 %, monte les marches d'un bloc sans sauter |
| **Marteau du Séisme** | Légendaire | masse (Densité V **et** Brèche II, Séisme II) | **Onde de choc** : une attaque écrasante frappe aussi les monstres à 5 blocs |
| **Arc du Phénix** | Légendaire | arc (Puissance V, Infinité, Brasier II) | **Flèches du phénix** : la cible et les monstres autour s'embrasent |
| **Arbalète du Traqueur** | Épique | arbalète (Tir multiple **et** Perforation IV, Marque du chasseur II) | aucun (les monstres touchés brillent à travers les murs) |
| **Trident du Léviathan** | Mythique | trident (Empalement V, Loyauté III, Foudroiement III, Écho II) | **Colère des abysses** : lancé, il foudroie sa cible et l'éclair rebondit sur 3 monstres |
| **Faux des Âmes** | Mythique | houe en netherite devenue faux (+8 dégâts, Vampirisme III, Moisson III) | **Moisson des âmes** : chaque monstre tué rend 1,5 ❤ et donne de la vitesse |
| **Coutelas du Contrebandier** | Rare | épée en fer (Butin III, Écho I, chance +1) | aucun |

Les pouvoirs ne touchent **que les monstres** (jamais les joueurs, villageois, animaux ou familiers) et ont tous une
recharge (affichée sur l'objet comme celle d'une perle de l'Ender pour les pouvoirs au clic droit). La foudre est
**visuelle** : aucun feu, aucun bloc modifié, aucun villageois changé en sorcière (les dégâts viennent du pouvoir).
Les pouvoirs de minage passent par le minage normal du joueur (protections, usure, butin) : **jamais dans le territoire
d'une autre guilde**, jamais le cœur, seulement les arbres naturels (feuilles non posées par un joueur) et la roche ou
les minerais naturels ; **accroupi**, l'outil mine un seul bloc.

**Enchantements Guildy** (data pack intégré, compatibles clients vanilla ; absents de la table d'enchantement, des
échanges villageois et du butin aléatoire : on ne les trouve que sur les armes uniques) :

| Enchantement | Niv. max | Effet |
|---|---|---|
| **Foudroiement** | III | 15 à 35 % de chance par coup : décharge de foudre (dégâts, étincelles, tonnerre, cible lumineuse) |
| **Vampirisme** | III | 8 à 20 % de chance par coup au corps à corps : rend 2 ❤ |
| **Givre** | III | ralentit la cible à chaque coup, parfois des dégâts de froid |
| **Brasier** | III | embrase la cible, parfois une gerbe de flammes (dégâts de feu) |
| **Écho** | III | +1,5 à +3,5 dégâts, chaque coup résonne |
| **Séisme** | II | 20 à 30 % de chance : onde qui repousse tout autour du porteur (aucun dégât, aucun bloc touché) |
| **Apesanteur** | I | immunité aux dégâts de chute |
| **Salve** | II | l'arc tire 3 (ou 5) flèches |
| **Moisson** | III | ×1,5 à ×2,5 d'expérience des monstres tués |
| **Marque du chasseur** | II | la cible brille (visible à travers les murs) et est affaiblie |

Foudroiement, Givre et Brasier sont exclusifs entre eux (enclume, `/enchant`).

#### Apparence : pack de ressources optionnel (Polymer)
Sans pack de ressources, chaque arme garde **l'apparence de son objet de base** (nom, description, brillance) : tout
fonctionne. Guildy fournit aussi des **textures originales** (16×16, certaines **animées** : Foudrelame, Croc-de-Givre,
Murmure du Néant, Marteau du Séisme, Trident du Léviathan, Faux des Âmes, Arc du Phénix avec ses états de tension), qui ne
sont utilisées que lorsqu'un pack est garanti (réglage `customModels` d'`uniques.json5`) :

- **Avec Polymer** (`customModels: "auto"`, par défaut) : installez [Polymer](https://modrinth.com/mod/polymer)
  (`polymer-resource-pack`, et `polymer-autohost` pour l'envoyer automatiquement aux joueurs : `"enabled": true` dans
  `config/polymer/auto-host.json`). Guildy ajoute ses modèles au pack généré par Polymer et le marque obligatoire
  (`polymerRequired`).
- **Sans Polymer** : récupérez `guildy-resourcepack-<version>.zip` (produit par `./gradlew build` ou
  `./gradlew resourcePackZip` dans `build/libs/`), hébergez-le (lien de téléchargement direct) et déclarez-le dans
  `server.properties` : `resource-pack=<url>`, `resource-pack-sha1=<sha1 du zip>`, `require-resource-pack=true` ; puis
  `customModels: "on"` dans `uniques.json5`.
- `customModels: "off"` : jamais de modèle personnalisé. Les armes déjà en circulation se mettent à jour dans l'inventaire
  des joueurs au changement de réglage.

Les textures sont générées par `tools/gen_textures.py` (Python 3, sans dépendance) : modifiez-le puis relancez-le pour
retoucher l'art.

### 12. Parchemins de sort
De vieux rouleaux couverts de runes, **à usage unique**, qui libèrent des sorts bien plus puissants que les pouvoirs des
armes uniques. **Maintenez le clic droit** pour lire le parchemin : un cercle runique se dessine à vos pieds, puis le sort
part dans un grand spectacle de particules visible de loin. Le parchemin n'est consommé que si le sort réussit (aucun
monstre à portée, incantation interrompue ou recharge en cours : il reste intact ; jamais consommé en créatif).
Chaque parchemin a une **recharge partagée** avec son groupe (Attaque, Soutien, Rappel, Invocation).
Les sorts ne touchent que les **monstres** : jamais les joueurs, les villageois ni les familiers, et aucun bloc n'est
modifié. Les bienfaits vont au lanceur, aux membres de sa guilde et à ses villageois.

| Parchemin | Rareté | Effet (réglages par défaut, `scrolls.json5`) |
|---|---|---|
| **Pluie de météores** | Légendaire | 10 météores autour du point visé, 6 ❤ par impact, monstres embrasés |
| **Tempête de foudre** | Épique | foudre en chaîne sur 8 monstres, 7 ❤ chacun, étourdis 2 s |
| **Blizzard** | Épique | tempête de 8 s sur 7 blocs : Lenteur IV, gel, 2 ❤ de froid par seconde |
| **Nova solaire** | Légendaire | sphère de feu jusqu'à 10 blocs : 10 ❤ (×1,5 sur les morts-vivants), monstres repoussés |
| **Singularité** | Mythique | trou noir de 3 s qui aspire tout à 10 blocs, puis implose (13 ❤) |
| **Égide** | Épique | dôme de 6 blocs pendant 12 s : Absorption III et Résistance II aux alliés, monstres et projectiles repoussés |
| **Rappel** | Rare | 4 s d'incantation, puis retour au foyer de la guilde (ou au cœur) avec les membres à 6 blocs |
| **Gardiens** | Légendaire | 2 golems de fer vous escortent 60 s |
| **Bénédiction** | Rare | soin de 8 ❤, Régénération II, effets néfastes retirés (vous, vos membres, vos villageois) |
| **Révélation** | Rare | les monstres à 64 blocs brillent 30 s (visibles à travers les murs) |
| **Séisme** | Épique | 3 ondes de choc jusqu'à 10 blocs, 5 ❤ chacune, monstres projetés en l'air |
| **Rayon arcanique** | Légendaire | rayon de 32 blocs pendant 3 s qui suit votre visée |

**Où les trouver** : surtout sur les **boss des ruines** (60 %, 75 % dans le Nether, 1 ou 2 parchemins), plus rarement sur
les boss des hordes (8 %, 30 % pendant une nuit de sang), en récompense de quête (5 %) ou de contrat légendaire (50 %), au
retour des expéditions (3 %), et chez le marchand du Carrefour commercial (palier II ; palier III pour les plus rares).
Le pack de ressources facultatif leur donne des textures animées (comme aux armes uniques).

### 13. Ruines et boss
Des ruines oubliées parsèment l'**Overworld** et le **Nether**. Au cœur de chacune dort un **gardien** : approchez de sa
salle et il s'éveille avec son escorte (barre de boss, ciel assombri) : ondes de choc, anneaux de feu ou de givre,
téléportations, salves, vol de vie, puis une rage folle quand il faiblit. Vaincu, il laisse de l'or, des trésors, souvent
un **parchemin de sort**, parfois une **arme unique**, et rapporte de l'XP aux guildes des joueurs qui ont combattu ;
tout le serveur l'apprend. Le gardien revient **7 jours de jeu** plus tard. Si tout le monde s'éloigne, le combat
s'arrête et le gardien se rendort (avec toute sa vie). Les coffres des ruines ont leur propre butin.

| Ruine | Où | Palier | Gardien |
|---|---|---|---|
| **Crypte oubliée** | plaines, forêts, prairies, taïga | 1 | Seigneur des Cryptes (squelette wither, 220 PV) + squelettes et zombies |
| **Tour du Mage déchu** | forêts, jungles, forêts anciennes | 2 | Archimage déchu (évocateur, 260 PV) + vindicateurs et vex |
| **Ziggourat des sables** | désert, badlands | 2 | Pharaon des sables (zombie momifié en or, 300 PV) + momies |
| **Bastion de givre** | plaines et taïgas enneigées, pics de glace, bosquets | 3 | Roi de givre (vagabond, 380 PV) + vagabonds |
| **Autel infernal** | Nether : désolations, forêt carmin | 4 | Seigneur de guerre (piglin barbare, 480 PV) + piglins barbares et cubes de magma |
| **Citadelle des âmes** | Nether : vallée des âmes, deltas de basalte, forêt biscornue | 5 | Champion des âmes (squelette wither, 650 PV) + squelettes wither et blazes |

Les ruines n'apparaissent que dans les chunks **pas encore générés** : il faut explorer de nouvelles zones.
Aperçus des six ruines : [`tools/ruins/previews/`](tools/ruins/previews/). Pour tester : `/place structure guildy:ruin/<id>`
(dans le Nether pour les deux dernières), `/locate structure #guildy:ruins`.

**Ajouter vos propres ruines** : construisez-la en créatif, marquez la case du boss, celles des sbires et l'entrée par des
panneaux (`[boss]`, `[sbire]`, `[entree]`, `[coffre commun|rare|tresor]`), exportez-la (WorldEdit `.schem`, Litematica
`.litematic` ou bloc de structure `.nbt`) puis convertissez-la avec `python tools/ruins/import_schematic.py` : elle devient
une variante de la ruine choisie (procédure complète dans [`tools/ruins/README.md`](tools/ruins/README.md)).

**Les trouver : l'Atelier du cartographe** (construction, guilde niveau 5, 80 or). Donnez la tâche **Cartographe** à un
villageois, puis clic droit sur le panneau de l'atelier : **« Chercher une ruine »** à la surface (8 papiers + 1 boussole)
ou dans le Nether (+ 4 quartz), pris dans votre inventaire. Le cartographe y travaille le jour (environ 8 minutes de
travail, plus vite à plusieurs, 3 au plus). Ensuite, **« Récupérer »** donne une **carte d'explorateur** marquée d'une
croix (surface) ou une **boussole** qui pointe vers la ruine dans le Nether (coordonnées dans sa description). Chaque
recherche mène à une ruine différente. Le menu de l'atelier liste les ruines connues de la guilde et leur état
(dormante, réveillée, vaincue — de retour dans N jours).

### Rôles
**Chef**, **Officier**, **Membre**. Par défaut les membres construisent et interagissent ; les officiers
invitent, excluent, gèrent le territoire, le trésor, les villageois, les constructions, la bannière et les
quêtes ; le chef promeut, rétrograde, transfère la direction et dissout. Modifiable dans `config.json5`
(`rolePermissions`).

---

## Commandes

`/gld` est un alias de `/guild`. Auto-complétion partout ; les noms de guilde avec espaces sont acceptés
(entre guillemets quand un autre argument suit).

### Joueurs
| Commande | Effet |
|---|---|
| `/guild` · `/guild menu` | Menu principal |
| `/guild create <nom>` | Créer une guilde (donne le drapeau fondateur) |
| `/guild invite <joueur>` · `join [guilde]` (`accept`) · `deny [guilde]` | Invitations |
| `/guild leave` · `kick <membre>` · `promote` · `demote` · `transfer <membre>` | Membres |
| `/guild disband` | Dissoudre (chef, confirmation) |
| `/guild info [guilde]` · `list` · `help` | Informations |
| `/guild deposit [all\|montant]` · `withdraw <montant>` | Trésor |
| `/guild banner` · `banner adopt` | Menu Bannière / adopter la bannière tenue en main |
| `/guild flag` · `flag buy` | Récupérer le drapeau fondateur / acheter un drapeau d'extension |
| `/guild claim` · `unclaim` · `show` | Territoire |
| `/guild sethome` · `home` | Point de retour de la guilde |
| `/guild waves` | Hordes : état, prochaine nuit, statistiques, règles |
| `/guild build` | Constructions et plans |
| `/guild quests` | Quêtes |
| `/guild villagers [accept\|refuse\|zones]` · `villagers route new\|finish\|undo\|cancel` | Villageois, zones de travail / tracé d'un parcours de garde |
| `/guild upgrades` | Arbre d'évolution (améliorations permanentes) |
| `/guild trade` | Carrefour commercial : palier, marchands présents, échanges possibles, amélioration |

### Administration
Permission **`guildy.admin`** (LuckPerms…) ; sans gestionnaire de permissions : opérateurs niveau 2
(`general.adminOpLevel`).

| Commande | Effet |
|---|---|
| `/guild admin reload` · `save` | Recharger toute la config / sauvegarder les données |
| `/guild admin bypass` | Ignorer les protections de territoire |
| `/guild admin info\|disband <guilde>` · `forcejoin <joueur> <guilde>` | Gestion des guildes |
| `/guild admin setlevel\|addxp\|addgold <guilde> <valeur>` | Progression et trésor |
| `/guild admin giveflag <joueur> extension [n]` · `giveflag <joueur> founding <guilde>` · `tp <guilde>` | Drapeaux, téléport au cœur |
| `/guild admin wave start\|stop\|status <guilde>` · `wave setnight <guilde> <n>` · `wave truce <guilde> <nuits>` · `wave schedule <guilde> <nuits>` | Hordes (start = horde de test immédiate ; truce = nuits de trêve restantes, 0 = fin ; schedule = prochaine horde dans N nuits, 0 = cette nuit) |
| `/guild admin structure unlock <guilde> <id>` · `structure check <guilde>` | Constructions |
| `/guild admin quest reroll <guilde>` · `quest complete <guilde> <slot>` · `quest map <guilde>` · `quest legendary <guilde> <slot>` | Quêtes (`legendary` : remplace la quête du panneau par un contrat légendaire) |
| `/guild admin villager spawn <guilde>` · `villager chunks` | Faire venir un candidat villageois ; chunks gardés chargés pour les villageois (par guilde) |
| `/guild admin upgrade unlock\|lock <guilde> <id>` | Arbre d'évolution : `unlock` débloque gratuitement (avec les prérequis manquants), `lock` retire l'amélioration et celles qui en dépendent |
| `/guild admin event list` | Événements : réglages, nuit de sang prévue, raids en attente, marchands présents par guilde |
| `/guild admin event start\|stop <événement> [guilde]` | `blood_night` (tout le serveur : la nuit en cours ou la prochaine), `pillager_raid` (immédiat si un membre est au cœur, sinon dès qu'il y en a un), `caravan`, `book_merchant`, `merchant` (visite du Carrefour) ; `stop` annule / renvoie le marchand |
| `/guild admin trade tier <guilde> <palier>` | Fixe le palier du Carrefour commercial |
| `/guild admin unique list` · `unique give <joueur> <id>` | Armes uniques : liste (rareté, pouvoir, chances par source ; clic sur un nom = préparer le `give`), donner une arme |
| `/guild admin scroll list` · `scroll give <joueur> <id> [nombre]` | Parchemins de sort : liste (clic sur un nom = préparer le `give`), donner des parchemins |
| `/guild admin ruin list` · `info` · `awaken` · `reset` | Ruines : liste, ruine la plus proche, réveiller / rendormir son gardien |
| `/guild admin ruin locate <id>` · `map <joueur> <id>` · `research <guilde> finish` | Localiser une ruine, donner une carte / boussole vers une ruine, terminer la recherche en cours d'une guilde |
| `/guild admin ruin ability <type> [style]` | Jouer un effet de capacité de boss (test visuel) |

---

## Configuration

Tout l'équilibrage est dans `config/guildy/` (fichiers commentés en français, **rechargeables à chaud** avec
`/guild admin reload`, valeurs bornées automatiquement) :

| Fichier | Contenu |
|---|---|
| `config.json5` | Guildes (noms, membres, invitations, home), territoire (rayon fondateur, extensions, distances, chunks max par niveau, prix des drapeaux), protections, économie, seuils d'XP des niveaux, bannière (métier à tisser / éditeur gratuit, cœur sur les cartes), rôles |
| `waves.json5` | Horaires, jours entre deux hordes (1 à 3), formule de difficulté, taille des hordes, élites, boss, buffs, intégrité du cœur, récompenses, défaite, trêve après plusieurs défaites, **thèmes** (liste pondérée : monstres, difficulté minimale, boss) |
| `structures.json5` | Constructions : motifs (lignes + clé bloc/`#tag`/`*` = n'importe quel bloc plein), coûts, niveaux, panneaux |
| `quests.json5` | Modèles de quêtes (17 types : cible, quantités, assortiments, récompenses, durée, poids, niveaux), contrats légendaires (chance, niveau, délai, multiplicateurs), chance d'arme unique, cartes au trésor ; les nouveaux modèles par défaut sont ajoutés une fois aux fichiers existants (`knownTemplates`) |
| `shop.json5` | Catégories et articles (lot, prix d'achat, prix de vente, niveau requis) |
| `villagers.json5` | Arrivées, limites, réglages des tâches, zones de travail et parcours de garde (nombre, rayon, distance au cœur, recherche automatique, marge des objectifs prioritaires, pauses, laisse), gardes (rôles, arc, repli, soins, équipement pris dans les coffres, équipement de base par niveau), vie et refuge de nuit (`vitality`, `night`), plantation (bûcheron, fermier), chunks gardés chargés (`chunkLoading`), candidats en groupe, destinations d'expédition, catégories de rangement (règles), tables de minage simulé par objectif, minage réel (`realMining` : mode par défaut, vitesse, profondeurs par objectif, escalier, galeries, salles, filons, torches, remontée du soir, listes de blocs naturels / minerais / blocs de remplissage), catégories de rangement, départs après défaite, forgeron (`blacksmith`) et alchimiste (`alchemist`), travaux des champs
(`farmer.fieldWork`, `farmer.irrigation`) |
| `upgrades.json5` | Arbre d'évolution : branches, améliorations (palier, niveau, prérequis, coût en or et en objets, effets), durée des effets de territoire |
| `trade.json5` | Carrefour commercial : visites (chance, séjour, heure), paliers (niveau, coût, nombre d'échanges) et tables d'échanges (objet, lot, prix en or, stock, poids, enchantement) |
| `events.json5` | Événements : heures des tirages, nuit de sang (chance, conditions, multiplicateurs), raid de pillards (chance, alerte, durée, composition), caravane et érudit (chance, séjour, échanges) |
| `scrolls.json5` | Parchemins de sort : définitions (sort, paramètres, rareté, incantation, recharge et groupe, modèle), chances par source, prix au Carrefour, limites (effets simultanés, budget de particules) |
| `ruins.json5` | Ruines : gardien de chaque ruine (créature, nom, vie, dégâts, armure, taille, équipement, escorte, capacités et recharges), réveil, réinitialisation, retour (`respawnDays`), récompenses ; recherches du cartographe (durée, coûts, rayon de recherche, carte) |
| `uniques.json5` | Armes uniques : définitions (objet de base, nom, rareté, histoire, enchantements, attributs, incassable, pouvoir, modèle), tables de butin par source (chance + poids), réglages des pouvoirs (recharges, dégâts, rayons), annonces, modèles personnalisés (`customModels`, Polymer) |
| `lang.json` | **Tous les textes** affichés aux joueurs (français par défaut, codes couleur `&`, `{0}` = paramètres) |

Une copie des fichiers par défaut (commentés) se trouve dans [`docs/default-config/`](docs/default-config/).

Formules par défaut (modifiables) :
- **Difficulté** D = 1 + 0,5 × palier de nuit + 0,5 × (niveau − 1) + 0,75 × (membres présents − 1), plafonnée à 20.
- **Taille de la horde** = 6 + 3 × D monstres (5 à 80), au plus 30 en vie par base et 150 sur le serveur.
- **Cœur** : 150 + 30 × (niveau − 1) points ; −1/s par monstre au contact (élite −3, boss −8).
- **Victoire** : or = (4 + 2D) × facteur, XP = (20 + 10D) × facteur, facteur = 0,25 + 0,75 × (tués / total).
- **Défaite** : −25 % du trésor, −2 paliers de nuit, 20 % de départ par villageois ; 2 défaites d'affilée → 3 nuits de trêve.

## Données

- `<monde>/guildy/guilds.json` : toutes les guildes (format versionné `dataVersion`), écrit de façon atomique
  (fichier temporaire + remplacement) avec copie `.bak` ; sauvegarde à chaque sauvegarde du monde, toutes les
  5 minutes si nécessaire et à l'arrêt. En cas de fichier illisible, la sauvegarde `.bak` est utilisée.
- Les hordes ne sont pas sauvegardées : après un arrêt (même brutal), les monstres de horde restants sont
  supprimés au chargement et la horde est simplement annulée.
- `<monde>/guildy/ruins.json` : ruines connues (position, état, retour du gardien), écrit de façon atomique avec `.bak`.
  Un combat de boss n'est pas sauvegardé : après un redémarrage, le gardien et son escorte sont supprimés et la ruine
  redevient dormante.
- `<monde>/guildy/events.json` : état global des événements (nuit de sang prévue, dernier tirage). Les marchands de
  passage restent dans le monde avec leurs échanges et repartent à l'heure prévue ; un marchand tagué `guildy_merchant`
  sans fiche dans la guilde est supprimé à son chargement.

## Choix de conception

- **Condition de défaite = le cœur** (le drapeau fondateur) : objectif clair à défendre, visible en bossbar ;
  murs et gardes sont des stratégies valables, mais la récompense dépend des monstres tués.
- **Conséquence de défaite modérée** (trésor −25 %, difficulté qui recule, quelques villageois qui partent) :
  ça pique sans spirale infernale, et l'équipement des joueurs n'est jamais perdu.
- **Pas de horde sans défenseur** : il faut un membre près du cœur à la tombée de la nuit (pas de raid hors ligne).
- **Monnaie = lingot d'or vanilla**, trésor de guilde = compteur ; aucun objet custom, prix de vente toujours
  inférieurs aux prix d'achat et récompenses de livraison et d'assortiment sous les prix boutique, contrats légendaires
  compris (pas de boucle d'or). Les marchands de passage ne font que vendre, jamais moins cher que la boutique (prix
  relevé au chargement sinon).
- **Événements sans logique de horde dupliquée** : la nuit de sang et le raid de pillards passent par le moteur des
  hordes (multiplicateurs appliqués à une horde normale, ou « vague d'événement » chronométrée) ; un raid ne change
  pas le palier de nuit et se termine avant la nuit.
- **Drapeaux et plans = objets vanilla** marqués (bannière, papier) : impossible à fabriquer en survie.
- **Mineur réel** et prudent (retour utilisateur) : escalier en colimaçon que les villageois savent parcourir (le
  pathfinding vanilla ne grimpe pas aux échelles), galeries dans le carré de la zone, uniquement des blocs naturels qui
  ne portent rien de construit, fluides colmatés ; le mode simulé reste disponible. Bûcheron/fermier réels et bornés ; chaque
  déplacement de villageois a un délai maximum et une solution de repli (jamais bloqué).
- **Zones de travail = fanions** plantés (pas de coordonnées à taper) : elles peuvent sortir du territoire, mais les
  villageois n'y travaillent que si elles sont chargées (aucun chargement forcé de chunk) et ne touchent jamais au
  territoire d'une autre guilde ; sans zone, chaque villageois travaille autour de lui.
- **Performance** : index chunk → guilde en mémoire, tâches réparties sur les ticks (joueurs toutes les
  10 ticks, villageois et hordes une fois par seconde), plafonds de monstres, uniquement des chunks chargés,
  chaque erreur isolée et journalisée sans faire tomber le serveur.

## Limites connues

- Les dégâts à distance (flèches) des monstres ne sont pas multipliés ; ghasts/phantoms n'attaquent que les joueurs.
- Dormir **avant** le début d'une horde saute la nuit (sans récompense) ; pendant une horde, c'est interdit.
- Un monstre qui ne peut pas atteindre un cœur emmuré finit par être retiré puis réapparaît ailleurs.
- Les villageois occupés à une tâche ne réapprovisionnent plus leurs échanges (ils ne vont plus à leur poste).
- Un fanion de zone arraché par une explosion, un piston ou la casse de son support tombe en bannière ordinaire :
  la zone continue de fonctionner (« Déplacer le fanion » pour en replanter un).
- Mine réelle : déplacer son fanion fait creuser un nouvel escalier (l'ancien reste ouvert) ; les tunnels sont éclairés
  mais une mine reste un lieu où des monstres peuvent attaquer les mineurs ; un filon à plus de ~4 blocs au-dessus ou
  au-dessous d'un niveau n'est pas atteint ; dans une mine neuve, les mineurs attendent que l'escalier atteigne le
  premier niveau.
- Les projectiles de visiteurs peuvent encore abîmer cadres et tableaux (le corps-à-corps est bloqué) ;
  plaques de pression, fils et piétinement des cultures ne sont pas protégés.
- Les villageois de guilde ne franchissent pas les portails ; un villageois déjà parti dans une autre dimension est
  ramené au village dès que son chunk se charge (« Appeler ici » le cherche aussi côté Nether).
- Le cooldown de `/guild home` est remis à zéro au redémarrage.
- Les ruines ne se génèrent que dans de nouveaux chunks ; une ruine déjà indiquée par une carte n'est plus proposée par
  une autre recherche.
- Les livres enchantés de l'alchimiste ne comptent pas pour les quêtes « Enchanter ».
- Un panneau de construction arraché par une explosion/un piston peut tomber en objet (la construction est
  retirée à la vérification suivante, 30 s).
- Un `structures.json5` existant reçoit automatiquement une seule fois les nouvelles constructions par défaut (liste
  `knownDefaults`) ; pour ne pas utiliser le Carrefour commercial, retirez-le de `definitions` (pas de `knownDefaults`).
- Les vagues de test admin lancées pendant une nuit de sang prévue en reçoivent les multiplicateurs (pratique pour tester).
- Le double saut des Bottes de Zéphyr repose sur l'autorisation de vol vanilla (rendue à l'atterrissage) : un anticheat
  externe pourrait le signaler. Une arme unique déjà obtenue garde le nom, la description et les enchantements de sa
  création (son pouvoir et son modèle suivent la config).

---

## Développement

```bash
./gradlew build        # jar : build/libs/guildy-<version>.jar (+ tests unitaires et GameTests serveur)
./gradlew test         # tests unitaires (logique pure)
./gradlew runGameTest  # GameTests serveur (scénarios en jeu avec joueurs simulés)
./gradlew runServer    # serveur de développement dans ./run
./gradlew resourcePackZip  # pack de ressources optionnel des armes uniques : build/libs/guildy-resourcepack-<version>.zip
```

Java 25 requis (`JAVA_HOME`). Architecture, décisions de conception et notes techniques :
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

## Licence

[MIT](LICENSE).
