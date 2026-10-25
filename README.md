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

- **Guildes** : chef, officiers et membres, invitations, trésor en lingots d'or, point de retour, **niveaux jusqu'à
  100** (réglable jusqu'à 500) avec rangs, paliers et récompenses, **vol dans son territoire** (`/guild fly`) et
  raccourcis (`/home`, `/g`…).
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
- **Villageois** : rangement des coffres (**sans limite de coffres**, catégories et sous-catégories, piles fusionnées et
  triées, **salles des coffres** automatiques ou réservées), **mine réelle** (escalier en colimaçon, galeries, filons), bûcheron, fermier
  (qui peut transformer toute une zone en champs), garde avec parcours de ronde, **forgeron**, **alchimiste**,
  **cartographe**, expéditions (exploration, Nether), zones de travail par fanions et leurs autorisations, missions.
- **Chantiers** : donnez une schématique (`.schem`, `.litematic`, `.nbt` ou une structure enregistrée en jeu) et les
  **bâtisseurs** la construisent avec un terrassement intelligent, en prenant les matériaux dans vos coffres et en
  fabriquant, cuisant ou récoltant eux-mêmes ce qui manque.
- **Arbre d'évolution** : 3 branches, 12 améliorations permanentes.
- **Armes uniques** : 21 armes et outils à pouvoirs (foudre, givre, téléportation, double saut, minage 3×3, bond du
  jaguar, faille infernale…) et **3 panoplies** de 4 pièces (casque, plastron, jambières, bottes) dont les bonus
  d'ensemble s'éveillent à 2 puis 4 pièces portées ; 10 enchantements inédits, textures animées facultatives.
- **Runes du forgeron** : gravées sur les armures des porte-armures, petits bonus, petits pouvoirs, et deux runes
  mythiques (Phénix, Dragon) sur les plastrons en netherite.
- **Armes uniques vraiment uniques** : numéro de série, une guilde ne reçoit jamais une arme qu'elle a déjà, collection
  de guilde X / 33.
- **Parchemins de sort** : 19 sorts à usage unique (météores, foudre en chaîne, singularité, dôme d'Égide, rappel, grand feu
  d'artifice,
  bannière de guerre, fléau, permutation, ralliement de la guilde…)
  avec de grandes animations de particules, et le **grimoire** qui relie 4 sorts et les lance contre de la nourriture
  (et sa version **maudite**, bien plus puissante, sur l'Archimage déchu maudit).
- **Ruines et boss** : 8 ruines (6 dans l'Overworld, dont le Grand Temple du Jaguar céleste et sa balise visible de
  loin, 2 dans le Nether), gardées par des gardiens de salle et un boss avec sa barre de vie,
  son escorte et ses attaques, éveillé par un **rituel de sang** (parchemin d'incantation), plus dur à chaque retour
  (**menace**), plus fort à plusieurs, qui **ressuscite enragé** si on l'abat trop vite, et sa version **maudite** en 3
  phases ; localisées par le cartographe de la guilde, sauf le grand temple, qu'on rejoint en suivant une piste de
  **statues de jaguars**.
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

#### Niveaux de guilde : jusqu'au niveau 100
La guilde gagne de l'XP en **repoussant les hordes**, en **terminant des quêtes** et en **abattant les gardiens des
ruines**. Les 10 premiers niveaux vont vite ; ensuite chaque niveau demande un peu plus d'XP que le précédent
(+140 XP à chaque niveau) : le niveau 20 se gagne en une à deux semaines de jeu régulier, le niveau 50 est un objectif
de long terme et le niveau 100 (maximum par défaut, réglable jusqu'à 500) une affaire de plusieurs mois.

| Niveau | 10 | 15 | 20 | 30 | 50 | 75 | 100 |
|---|---|---|---|---|---|---|---|
| XP totale | 3 200 | 11 300 | 22 900 | 56 600 | 166 000 | 381 500 | 684 500 |
| XP pour ce niveau | 700 | 1 900 | 2 600 | 4 000 | 6 800 | 10 300 | 13 800 |

- **À chaque niveau au-delà du 10** : de l'or versé au trésor (21 or au niveau 11, +1 par niveau, 120 au plus).
- **Paliers** (niveaux 15, 20, 25, 30…) : **+100 or** et **un drapeau d'extension offert** (à prendre gratuitement dans
  le menu Territoire, bouton « Prendre un drapeau offert », ou avec `/guild flag buy` ; il ne fait pas monter le prix des
  drapeaux achetés). Tous les 10 niveaux (20, 30, 40…) : **+1 place de villageois** (10 au plus). Chaque palier est
  annoncé à tout le serveur.
- **Rangs** (dans `/guild info`, le menu principal et l'annonce de niveau) : Novice (1), **Bronze** (10), **Argent**
  (20), **Or** (30), **Platine** (40), **Diamant** (50), **Émeraude** (75), **Légendaire** (100), puis Mythique (150),
  Immortelle (250) et Divine (400) si le serveur monte plus haut.
- `/guild info` et le menu principal affichent « Niveau N/max », le rang, une barre de progression et le prochain palier.

**Ce qui continue de grandir après le niveau 10** (plus lentement, et avec un plafond, pour que le jeu reste équilibré) :
territoire +1 chunk par niveau (66 au niveau 10, 156 au niveau 100, 256 au plus) ; cœur +5 points d'intégrité par
niveau (420 au niveau 10, 870 au niveau 100, 1 020 au plus) ; hordes : +0,05 de difficulté par niveau (+4,5 au plus :
la nuit et les membres présents comptent toujours, une défaite fait encore baisser la horde) ; gardes : +0,05 dégât par
niveau et un meilleur équipement de base aux niveaux 20, 30 et 50 ; villageois : +0,5 PV par niveau ; quêtes :
quantités et récompenses montent plus lentement (niveau de calcul 12 au niveau 20, 18 au niveau 50, 28 au niveau 100).
Les déblocages (constructions, boutique, quêtes, carrefour, expéditions, forgeron, alchimiste…) restent aux niveaux 1 à 10.

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
- **Voler chez soi** : `/guild fly` (ou le bouton « Vol de guilde » du menu Territoire) active le vol de guilde. Dans
  les chunks de **votre** guilde, en survie ou en aventure, vous pouvez voler comme en créatif (sautez deux fois pour
  décoller). Le vol s'arrête dès que vous sortez du territoire (un avertissement s'affiche quand le bord approche) et
  **pendant toute horde** (nuit, raid de pillards, vague de test) ; il revient tout seul ensuite. Si le vol s'arrête en
  l'air, la chute qui suit ne fait **aucun dégât**. Le réglage est mémorisé (reconnexion, mort) ; `/guild fly off`
  l'éteint. Le serveur peut exiger un niveau de guilde ou un rôle minimum (section `fly` de `config.json5`). Avec les
  Bottes de Zéphyr, le double saut reprend dès l'atterrissage hors du territoire.
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
Nombre maximum : 2 + 1 par niveau de guilde jusqu'au niveau 10, puis +1 tous les 10 niveaux (paliers, 10 de plus au
maximum).
Menu « Villageois » (`/guild villagers`, ou accroupi + clic droit sur un villageois) : la liste montre la tâche, la zone,
le statut et ce que chacun transporte ; le menu d'un villageois règle sa tâche, sa **zone de travail** et sa mission,
permet de l'**appeler** à côté de soi (« Appeler ici », depuis le territoire ou une zone de la guilde, où que soit le
villageois : s'il est loin, sa dernière position est chargée un instant, « Appel en cours… ») ou de le renvoyer.

| Tâche | Ce qu'il fait |
|---|---|
| **Ranger la base** | vide les coffres de **dépôt** et range chaque objet dans le coffre de sa **catégorie** (19 thèmes, voir « Rangement par thème »), puis **re-range** sans cesse : piles fusionnées, triées et rangées dans l'ordre de lecture du mur de coffres (voir « Coffres bien rangés ») |
| **Mineur** | creuse vraiment sa **mine** (mode réel, voir plus bas) : escalier en colimaçon puis galeries, pierre et/ou minerais selon l'objectif ; ou minage simulé au fanion (mode simulé) |
| **Bûcheron** | coupe les arbres naturels de sa **coupe de bois** (ou autour de lui), replante, et **plante de nouvelles pousses** dans sa zone (voir « Plantation ») |
| **Fermier** | récolte les cultures mûres de ses **champs** (ou autour de lui), replante, **sème les terres vides** et laboure près de l'eau ; selon les **autorisations du fanion**, relaboure la terre piétinée ou transforme toute la zone en champs (voir « Autorisations du fanion ») |
| **Garde** | **épéiste** ou **archer** ; patrouille dans sa zone ou sur son **parcours de garde**, sinon défend le cœur ; toujours au cœur pendant les hordes ; s'équipe dans les coffres et se replie pour se soigner (voir « Gardes ») |
| **Exploration** | part vers la **destination** choisie (biome ou construction, voir « Destinations d'expédition ») : il quitte vraiment la base et revient quelques minutes plus tard avec le butin du lieu — risque de ne pas revenir |
| **Expédition au Nether** | passe par un **portail du Nether construit dans le territoire** vers la destination choisie (forteresse, bastion, forêts…) — risque plus élevé |
| **Forgeron** (guilde niv. 3) | répare peu à peu l'équipement abîmé posé sur les **porte-armures** du territoire, à l'**enclume** la plus proche, qu'il use ; à partir du niveau 5, grave parfois une **rune** sur les armures (voir « Forgeron et alchimiste ») |
| **Alchimiste** (guilde niv. 5) | livres enchantés et parchemins à la **table d'enchantement**, **potions** et **élixirs** à l'**alambic** du territoire, selon les stocks voulus ; passe au travail suivant quand le sien est impossible (voir « Forgeron et alchimiste ») |
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
| 20 | netherite Tranchant III | arbalète Charge rapide III, Perforation III | diamant |
| 30 | netherite Tranchant IV | arbalète Charge rapide III, Perforation IV | netherite |
| 50 | netherite Tranchant V | arbalète Charge rapide III, Perforation IV, Tir multiple | netherite |

#### Vie, nuit et refuge
- **Plus de vie avec le niveau** : chaque villageois de la guilde gagne +4 PV (2 cœurs) par niveau de guilde au-delà du
  premier jusqu'au niveau 10, puis +0,5 PV par niveau, les gardes encore plus (×1,5 + 10 PV) ; hors combat, ils se soignent lentement. La vie s'affiche dans le menu.
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
- **Runes du forgeron** (guilde niveau 5) : quand le forgeron travaille sur une **armure** posée sur un porte-armure, il
  peut y **graver une rune**. Chaque pièce d'armure qu'il finit de réparer est une occasion ; quand il n'a rien à
  réparer, il **examine** à son enclume les armures intactes des porte-armures (20 s, chaque porte-armure au plus une
  fois toutes les 15 min). À chaque occasion : **15 % de chances**. Une rune coûte **1 éclat d'améthyste** pris au
  stockage ; **2 runes par jour** au plus et par forgeron. Glyphes d'enchantement qui convergent vers la pièce, spirales,
  éclair de lumière, son d'enclume ; la guilde est prévenue. **Une seule rune par pièce**, jamais sur un objet unique.
- **Trois rangs** : surtout des runes **communes** (80 %), parfois des **rares** (18 %), et très rarement une **mythique**
  (2 %, **uniquement sur un plastron en netherite**, annoncée à tout le serveur). La rune apparaît dans la description de
  l'objet (vert, bleu, violet) avec un éclat d'enchantement ; ses bonus ne comptent que **portée**.

  | Rang | Rune | Pièces | Effet |
  |---|---|---|---|
  | Commune | Rempart | toutes | +1 armure |
  | Commune | Bastion | plastron, jambières | +2 armure |
  | Commune | Acier | toutes | +1 robustesse |
  | Commune | Roc | plastron, jambières | +10 % résistance au recul |
  | Commune | Vitalité | plastron, jambières | +2 points de vie (1 ❤) |
  | Commune | Célérité | bottes | +5 % vitesse |
  | Commune | Trèfle | casque | +1 chance (butin, pêche) |
  | Commune | Fureur | plastron | +0,5 dégâts d'attaque |
  | Commune | Abysses | casque | +1 souffle sous l'eau |
  | Commune | Ondine | bottes | +25 % aisance dans l'eau |
  | Commune | Plume | bottes | +2 blocs de chute sans dégâts |
  | Commune | Chamois | jambières | +10 % force de saut |
  | Commune | Mineur | casque | +10 % vitesse de minage |
  | Commune | Salamandre | jambières | −30 % durée des brûlures |
  | Commune | Chat | jambières | accroupi plus rapide |
  | Commune | Allonge | plastron | +1 bloc de portée |
  | Rare | Étincelles | plastron, jambières | un monstre qui vous frappe au corps à corps prend une gerbe d'étincelles (1 ❤, recharge 3 s) |
  | Rare | Braise | plastron | un monstre qui vous frappe a 35 % de chances de prendre feu |
  | Rare | Lueur | casque | quand un monstre vous frappe, les monstres à 12 blocs brillent 6 s |
  | Rare | Écho | casque, bottes | accroupi et immobile 2 s : une onde révèle les monstres à 24 blocs |
  | Rare | Renard | jambières | les objets au sol à 5 blocs volent vers vous |
  | Rare | Rebond | bottes | −50 % dégâts de chute, et un rebond après une grande chute |
  | **Mythique** | **Phénix** | plastron en netherite | **Renaissance** : un coup mortel vous relève de vos cendres à demi-vie, sans effets néfastes, avec Résistance au feu et Régénération II, dans une colonne de flammes ; l'explosion brûle (3 ❤) et repousse les monstres à 6 blocs. Recharge **20 min** ; passe avant un totem (épargné) ; jamais contre `/kill` ni le vide |
  | **Mythique** | **Dragon** | plastron en netherite | **Ailes du Dragon** : le plastron **plane comme des élytres**, avec des ailes spectrales violettes ; **accroupi + saut** : souffle du dragon en cône (9 blocs, 3 ❤, monstres embrasés et repoussés ; recharge 20 s) |

  Les pouvoirs ne touchent **que les monstres** et ne marchent que pour le **joueur** qui **porte** la pièce.
- **Retirer une rune** : posez la pièce **seule dans une meule** : elle ressort **sans sa rune** mais avec ses
  **enchantements**, son nom et sa durabilité (pas d'expérience). Repassez-la à la meule pour retirer ensuite les
  enchantements comme en vanilla. Une pièce sans rune peut en recevoir une nouvelle (pour retenter une mythique). La rune
  résiste à l'enclume, à la réparation par le forgeron et à l'amélioration en netherite. Une pièce gravée n'est pas un
  objet « simple » (ni vendue, ni livrée, ni prise par les gardes).
- **Alchimiste** (guilde niveau 5) : trois travaux, tous rangés au stockage.
  - **Livres** : avec une **table d'enchantement** dans le territoire et des **livres** dans le stockage, il fabrique des
    **livres enchantés** (coffre « Livres enchantés », sinon « Livres et enchantements »). Il choisit la table qui a le
    plus de bibliothèques (règle vanilla, 15 au plus) : avec 15 bibliothèques, chaque livre est enchanté au niveau 30 ; une
    table seule donne des enchantements faibles. 6 s par niveau, **8 livres par jour** et par alchimiste au plus ; pas de
    lapis par défaut (`alchemist.lapisPerBook`). Comme à la table vanilla, Raccommodage et les autres enchantements
    « trésor » ne sortent jamais. De temps en temps (15 % des livres), avec **2 feuilles de papier**, le livre devient un
    **parchemin de sort**.
  - **Parchemins** : le même travail à la table, mais chaque livre devient un **parchemin de sort** (livre + 2 papiers).
    Surtout des parchemins simples : **Rappel** le plus souvent, puis Bénédiction, Révélation, Hâte du vent et
    Permutation ; avec une table entourée de **15 bibliothèques**, rarement un épique (Blizzard, Séisme, Égide, Tempête de
    foudre, Ralliement, Bannière de guerre, Fléau, Grand spectacle) ; jamais de légendaire ni de mythique. **2 parchemins par jour** au plus,
    en plus des livres (`scrollChance`, `scrollPaper`, `maxScrollsPerDay`, `epicScrollLevel`, `scrolls`).
  - **Potions** : avec un **alambic** dans le territoire, il prépare les **potions** et **élixirs** qui manquent au
    stockage. Par défaut (**brassage simple**), chaque fournée de **3 fioles** ne coûte que de la **poudre de blaze**
    (prise au stockage) et du **temps** à l'alambic : ni fiole, ni eau, ni verrue du Nether, ni ingrédient.

    | Produit (fournée de 3 fioles) | Poudre de blaze | Durée |
    |---|---|---|
    | Potion simple (soin, force, rapidité, résistance au feu…) | 1 | 20 s |
    | Potion longue | 1 | 30 s |
    | Potion forte (II) | 1 | 30 s |
    | Potion jetable | + 0 | + 10 s |
    | Potion persistante | + 1 | + 20 s |
    | Élixir (colosse, bête, ombre, titan, phénix, abysses) | 2 | 60 s |
    | Élixir magique (arcanique, clairvoyance, satiété mystique) | 4 | 90 s |

    **18 fioles par jour** et par alchimiste au plus, dont **6 élixirs** au plus (`brewing.maxElixirsPerDay`), pour que
    les bonus magiques restent précieux. Sans poudre de blaze, il passe à un autre travail (« ↪ Potions : pas de poudre de
    blaze → Livres »). Les potions vont au coffre « Potions » (sinon « Potions et alchimie »). Le mode **`recipes`**
    (`alchemy.json5` → `brewing.mode`) remet le brassage vanilla d'avant (fioles, verrue du Nether, ingrédients).
- **Stocks de potions** (menu d'un alchimiste → « Stocks de potions ») : pour chaque potion et élixir, le nombre de fioles
  que la guilde veut garder (clic gauche +1, droit -1, Maj ±8 ; « Remettre par défaut »). Les alchimistes préparent
  d'abord le produit le plus en dessous de son stock. Par défaut : 6 potions de soin, 3 de soin II, 3 jetables de soin,
  4 de force, 4 de résistance au feu, 2 de régénération, de rapidité, de vision nocturne, de respiration aquatique, de
  chute lente, de force II… et quelques élixirs courants ; les élixirs aux ingrédients très rares restent à 0 (à
  demander). Les stocks se comptent dans tous les coffres du stockage, fioles en route comprises.
- **Élixirs de Guildy** : des potions à boire comme les autres, avec leurs propres effets, leur couleur et leur nom,
  préparées par l'alchimiste (mode simple : poudre de blaze et temps, voir le tableau ; mode `recipes` : une potion
  vanilla et un **ingrédient rare**, colonne « Recette ») :

  | Élixir | Recette (base + ingrédient) | Effets | Durée |
  |---|---|---|---|
  | Élixir du colosse | Potion de force II + écaille de tatou | Force II, Résistance I | 3:00 |
  | Élixir de la bête | Potion de force + patte de lapin | Force I, Vitesse I, Saut I | 3:00 |
  | Élixir de l'ombre | Potion d'invisibilité + membrane de Phantom | Invisibilité, Vision nocturne, Vitesse I | 4:00 |
  | Élixir du titan | Potion de soin II + pomme dorée | Boost de vie II (+4 cœurs), Absorption II | 4:00 |
  | Élixir du phénix | Potion de résistance au feu + larme de Ghast | Résistance au feu 4:00, Régénération II 0:45 | 4:00 |
  | Élixir des abysses | Potion de respiration aquatique + cœur de la mer | Respiration aquatique, Force des conduits, Grâce du dauphin | 8:00 |
  | **Élixir arcanique** | Potion étrange + éclat d'améthyste | **Sorts +25 %** (dégâts, soins, durées) | 5:00 |
  | **Élixir de clairvoyance** | Potion de vision nocturne + éclat d'écho | **Recharges -30 %**, **incantations -30 %**, Vision nocturne | 5:00 |
  | **Élixir de satiété mystique** | Potion étrange + baies lumineuses | **Faim du grimoire -50 %** | 6:00 |

  Les bonus magiques (parchemins, grimoire, grimoire maudit) commencent à la fin de la gorgée et s'affichent dans une
  **barre violette** en haut de l'écran avec leur temps restant ; ils se gardent à la déconnexion (le temps ne s'écoule
  qu'en ligne) mais disparaissent à la mort, comme les effets de potion. Boire le même élixir relance sa durée (sans la
  cumuler) ; deux élixirs différents s'additionnent, plafonnés (sorts +100 %, recharges et incantations -75 %, faim du
  grimoire -100 %).
- **Repli** : un alchimiste tente toujours son **travail préféré**, puis les autres dans l'ordre **Livres > Parchemins >
  Potions** (`alchemist.jobOrder`), et travaille au premier possible. Un travail est impossible sans table ou alambic,
  sans livres / lapis / papier / poudre de blaze au stockage (mode `recipes` : fioles, ingrédients), quand son plafond du jour est atteint,
  quand aucun parchemin n'est possible à la table ou quand tous les stocks de potions sont pleins. Son menu affiche la
  raison (« ↪ Livres : pas de livres → Potions ») ; quand rien n'est possible, son statut liste les raisons de chaque
  travail. Après chaque livre, parchemin ou fournée, il revient à son travail préféré s'il est redevenu possible.
- **Plusieurs alchimistes** se répartissent les rôles (`alchemist.distribution: balanced`) pour avoir un peu de tout en
  même temps : parts 2 livres / 1 parchemins / 2 potions (`roleWeights`) parmi les travaux possibles pour la guilde
  (5 alchimistes : 2 livres, 1 parchemins, 2 potions ; 3 alchimistes : un de chaque ; sans alambic, livres et parchemins
  seulement), revue toutes les minutes, chacun gardant son rôle tant qu'il a sa place. Le bouton **Rôle** du menu d'un
  alchimiste (Auto / Livres / Parchemins / Potions) impose son travail préféré (il passe quand même aux autres quand le sien
  est impossible, sauf `manualRoleFallback: false`). `distribution: priority` = tous préfèrent le premier travail de
  `jobOrder`.
- Leur menu affiche leurs statistiques (objets réparés, enclumes usées ; livres faits, meilleur enchantement, parchemins
  écrits, potions préparées, dernière potion), et pour l'alchimiste son rôle et la raison d'un repli.

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

**Autant de coffres que vous voulez** : par défaut, plus aucune limite au nombre de conteneurs enregistrés (coffres,
tonneaux, boîtes de Shulker), de salles des coffres ni de coffres filtrés ; l'admin peut en fixer une dans
`villagers.json5 > storage` (0 = illimité). Des milliers de coffres restent légers : pour chaque objet, le trieur ne lit
que les coffres qui peuvent le recevoir, le plus proche d'abord ; un très grand groupe (plus de 128 coffres d'une même
catégorie au même endroit) est rangé par parties.

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

**Sous-catégories** (elles ne prennent que des objets de leur catégorie parente ; sans coffre de la sous-catégorie, ses
objets vont dans la parente) :

| Catégorie parente | Sous-catégorie | Ce qui y va |
|---|---|---|
| Blocs de construction | **Laine et tapis** | laine, tapis, dalles et escaliers en laine |
| | **Verre** | verre, verre teinté, vitres |
| | **Béton et terre cuite** | béton, béton en poudre, terre cuite, terre cuite émaillée |
| Mobilier et décoration | **Éclairage** | torches, lanternes, bougies, feux de camp, lanternes aquatiques, citrouilles-lanternes, champilampes |
| Outils | **Cartes et boussoles** | cartes vierges et remplies (cartographe, cartes au trésor), boussoles, boussoles de récupération, horloges, longues-vues |
| Livres et enchantements | **Livres enchantés** | les livres enchantés seulement |
| | **Parchemins et grimoires** | parchemins de sort, grimoires, parchemins d'incantation |
| | **Livres et papier** | livres, livres et plumes, livres écrits, papier |
| Potions et alchimie | **Potions** | potions, potions jetables et persistantes, fioles sinistres, élixirs |
| Nourriture | **Cultures** | blé, carottes, pommes de terre, betteraves, pastèques, citrouilles, baies, bottes de foin |

**Catégories transversales** (elles prennent leurs objets avant leur catégorie habituelle) : **Équipement enchanté**
(outils, armes et armures enchantés), **Objets rares** (disques, têtes et crânes, totems, étoiles du Nether, cœurs de la
mer, élytres, œuf de dragon, modèles de forge, pommes dorées enchantées, balises, conduits), **Ingrédients d'alchimie**
(poudre de blaze, verrues du Nether, larmes de ghast, crème de magma, yeux d'araignée, melon scintillant, pattes de lapin,
membranes de phantom, souffle de dragon, fioles vides).

**Où va un objet** : dans le premier coffre de cette liste qui a de la place (le plus proche d'abord) : un coffre filtré
qui liste l'objet, puis un coffre filtré de sa matière, puis de son type ; un coffre d'une catégorie transversale qui le
prend ; un coffre de sa sous-catégorie la plus précise, puis de sa catégorie parente ; un coffre « Divers » ; sinon il
reste au dépôt. Créer un coffre « Livres enchantés » ne casse rien : le trieur y déplace ensuite les livres enchantés
rangés ailleurs. Le bouton « Où ranger l'objet en main ? » du menu Stockage affiche cet ordre pour l'objet tenu.

**Un bâtiment des livres enchantés** (ou un atelier d'alchimie) : choisissez « **Livres enchantés** » pour ses coffres
(juste après « Livres et enchantements » dans la liste), ou faites-en une **salle des coffres réservée** (menu de la salle
→ Catégories de la salle → « Livres enchantés »), ou un **coffre filtré d'un clic** (coffre vide, un livre enchanté en main,
« Coffre filtré »). ⚠ Ne choisissez **pas « Divers »** pour un bâtiment spécialisé : « Divers » reçoit tout ce qui n'a pas
d'autre coffre (le menu l'indique en rouge).

Admins : une catégorie de `villagers.json5` peut avoir un `parent` (`""` = principale, le nom d'une autre catégorie =
sous-catégorie, `"*"` = transversale) ; propriété de règle `@magic` (objets magiques de Guildy). Les catégories par défaut
d'une nouvelle version sont ajoutées une seule fois aux anciens fichiers (`storage.knownCategories`).

Menu Villageois → **Stockage** → « Ajouter un coffre », puis clic droit sur un coffre du territoire et choisissez son
rôle (chaque catégorie montre son icône et des exemples) : le coffre **prend le nom de son rôle**, affiché quand on
l'ouvre. Dans le menu Stockage : clic gauche sur un coffre pour changer son rôle, clic droit pour le retirer ;
« **Afficher les coffres** » les signale par des particules colorées pendant 20 s ; « **Où ranger l'objet en main ?** »
montre l'ordre suivi par le trieur pour l'objet que vous tenez. Les catégories (règles : objets, tags, propriétés) se règlent dans
`villagers.json5`.

#### Coffres filtrés
Un **coffre filtré** n'accepte que certains objets : une **famille** (« Diorite » : diorite, diorite polie, dalles,
escaliers, murets ; « Chêne » : bûches, planches, portes… mais pas le chêne noir ; « Laine » : toutes les couleurs ;
« Blanc » : tout ce qui est blanc) ou une **liste d'objets** (« Blé, Canne à sucre et Graines de blé »). Le filtre se
déduit de ce que vous avez mis dedans. Les trieurs y rangent ces objets **avant** leur coffre de catégorie (liste exacte,
puis famille de matière, puis famille de type, puis catégorie, Divers, dépôt), sortent les objets qu'il refuse (vers leur
catégorie, sinon vers un dépôt) et le rangent par colonnes. Le coffre prend le nom de son filtre, sauf si vous l'avez déjà
nommé.
- `/guild stockage filtre ici` : filtre le coffre que vous regardez d'après son contenu ; `/guild stockage filtre retirer`
  : le retire du stockage. Aussi dans le choix du rôle d'un coffre : « Coffre filtré (d'après son contenu) ».
- `/guild stockage scan <chunks>` : analyse tous les coffres de votre territoire autour de vous (une salle de cent coffres
  déjà remplis, par exemple) et propose pour chacun un filtre, ou une catégorie s'il est trop mélangé (75 % d'une
  catégorie). Survolez une entrée pour voir les objets, cliquez pour la repérer, puis **[Appliquer]** enregistre tout d'un
  coup (valable 2 min). Ajoutez `remplacer` pour changer aussi les coffres déjà enregistrés (jamais les dépôts). Les
  coffres vides sont seulement comptés.

#### Coffres bien rangés
Les coffres d'une même catégorie forment un **groupe** (un par salle des coffres, un pour les coffres hors salle). Le
trieur les range **comme un mur qu'on lit**, vu de face : **par colonnes** (chaque colonne de haut en bas, colonnes de
gauche à droite) ou **par rangées** (chaque rangée de gauche à droite, rangées de haut en bas) : réglage « Ordre de
rangement » du menu Stockage, par colonnes par défaut. Un coffre double compte pour un seul coffre.
- Dans chaque coffre, le trieur range **par colonnes** : chaque colonne de haut en bas, puis la suivante à droite, et
  chaque nouvel objet commence en haut d'une nouvelle colonne tant qu'il reste la place (sinon il suit le précédent).
  Réglage « Rangement dans le coffre » du menu Stockage (ou `/guild stockage rangement lignes`) pour revenir à l'ordre de
  lecture.
- Dans un groupe, les piles sont toujours **fusionnées** et triées dans l'**ordre de l'inventaire créatif** (tous les bois
  ensemble ; casque, plastron, jambières puis bottes de chaque matériau…), les objets simples avant les enchantés,
  renommés ou abîmés ; le coffre 1 est rempli avant le coffre 2. Un objet garde toujours ses enchantements, son nom et son
  usure.
- Quand les dépôts sont vides, le trieur **re-range** : il sort les objets d'une autre catégorie, fusionne les piles, remet
  l'ordre et comble les trous. Les coffres à sa portée (une colonne, un pan de mur) sont réorganisés d'un coup, couvercles
  ouverts ; entre deux endroits éloignés, il porte les piles. Rien n'est jamais perdu ni dupliqué, et un coffre **ouvert
  par un joueur n'est jamais touché**. Plusieurs trieurs se partagent le travail sans se gêner.
- Menu Stockage → **Groupes de coffres** : l'état de chaque groupe (« Rangé » / « À ranger »), son remplissage, ses coffres
  dans l'ordre de lecture, « Afficher » et « Réorganiser maintenant » (aussi par un clic droit sur le groupe).

#### Salle des coffres
Menu Stockage → **Salles des coffres** → « Ajouter une salle des coffres », puis clic droit sur le coffre d'un coin et sur
celui du coin opposé (dans les 60 s, 64 blocs de côté au plus ; salles et coffres sans limite par défaut) : tous les conteneurs du territoire
entre les deux deviennent **automatiques**. Les trieurs donnent à chaque catégorie un bloc de **colonnes entières** (ou de
rangées) à sa taille, plus une marge, dans l'ordre des catégories, « Divers » à la fin ; le reste reste en **Réserve**.
Ensuite la salle bouge peu : une catégorie qui se remplit gagne une colonne voisine, une catégorie qui n'a que quelques
piles reste dans « Divers » plutôt que de tout déplacer, et une colonne vide depuis 30 minutes retourne à la réserve.
Quand un coffre change de catégorie, il change de nom et le trieur déplace ses objets. Un coffre dont vous choisissez le
rôle devient manuel et n'est plus jamais réattribué ; le rôle « Automatique » le rend à la salle. Dans le menu des salles :
clic gauche ouvre la salle (afficher ses coffres, ajouter les coffres posés depuis, **catégories de la salle**, supprimer),
clic droit la supprime (les objets restent dans les coffres). **Salle réservée** : cochez une ou plusieurs catégories
(« Livres enchantés » pour un bâtiment des enchantements, « Potions » + « Ingrédients d'alchimie » pour un atelier) :
la salle ne reçoit plus que celles-ci, le reste de ses coffres en réserve. Les listes longues ont des pages (shift + clic
= 10 pages).

**Salle filtrée** : menu de la salle → **Mode de la salle** → « en faire une salle filtrée ». Chaque coffre automatique de
la salle ne reçoit plus que les **sortes d'objets qu'il contient déjà** (une ou plusieurs : un coffre qui contient de la
diorite et du granite reçoit les deux), et un **coffre vide ne reçoit rien**. Pour qu'un coffre accepte un nouvel objet,
mettez-en un à la main : quelques secondes plus tard, les trieurs y rangent cet objet ; si vous en retirez la dernière
pile, il ne le reçoit plus. Rien n'est déplacé au moment de la conversion.
- Priorité : un coffre de salle filtrée qui contient l'objet passe en premier (même rang qu'un coffre filtré qui liste
  l'objet, le plus proche d'abord), avant les familles, les catégories, « Divers » et le dépôt ; les trieurs y rapportent
  aussi les objets de cette sorte rangés ailleurs.
- Les trieurs ne sortent **jamais** rien d'un coffre de salle filtrée : ils fusionnent seulement les piles à l'intérieur
  de chaque coffre. Les villageois qui puisent dans le stockage y laissent toujours au moins un objet de chaque sorte.
- « Même objet » : le même objet quels que soient son nom, son usure ou ses enchantements ; pour les potions, le même
  effet (versions longue et renforcée comprises) ; tous les livres enchantés ensemble. Réglable :
  `storage.filteredRoomPotions` (`type`, `exact`, `item`) et `storage.filteredRoomBooks` (`item`, `enchantments`).
- Menu de la salle → **Coffres de la salle** : ce que reçoit chaque coffre au survol (« Reçoit : Diorite, Granite +2 »)
  ou « Vide : ne reçoit rien ». « Repasser en salle automatique » remet les coffres en réserve et replanifie la salle.

La nuit, les travailleurs dorment ; les gardes restent dehors. Les villageois peuvent encore **mourir** (gardes au
combat, flèches perdues) : posez des lits près du cœur et équipez vos gardes. Seuls les membres peuvent commercer avec eux.

#### Chantiers : les bâtisseurs construisent vos schématiques

Donnez une schématique à la guilde, posez-la dans le territoire, et les villageois **bâtisseurs** (tâche *Bâtisseur*)
la construisent bloc par bloc, en allant chercher eux-mêmes ce qui manque.

- **Les fichiers** : `.schem` (WorldEdit, FAWE : Sponge 1, 2 et 3), `.litematic` (Litematica, régions fusionnées) et
  `.nbt` (structures vanilla), déposés par l'admin dans `config/guildy/schematics/` (sous-dossiers permis, un README.txt
  y explique tout). Les **structures enregistrées en jeu avec un bloc de structure** apparaissent aussi : on peut créer
  ses schématiques sans rien installer. Les vieux fichiers sont mis à jour (DataFixer de Minecraft), les blocs inconnus
  ignorés et signalés ; un fichier trop gros ou abîmé est refusé avec sa raison. Les anciens `.schematic` de MCEdit
  (avant 1.13) sont refusés : réenregistrez-les en `.schem`.
- **Taille** : par défaut 256 blocs par côté, 2 097 152 blocs de volume (air compris) et 500 000 blocs posés au plus,
  pour un fichier de 64 Mo. Pour un très grand bâtiment, montez les valeurs de la section `schematics` de
  `builds.json5` (jusqu'à 1024 par côté, 64 millions de volume, 16 millions de blocs) puis `/guild admin reload` : les
  fichiers refusés sont relus. Une schématique chargée occupe environ 7 octets de mémoire par bloc de sa boîte
  (20 millions = 140 Mo) : avec de très grandes schématiques, baissez `cacheSize`. Comptez aussi le temps : 2 blocs
  par seconde et par bâtisseur, de jour.
- **Placer un chantier** : menu Villageois (ou menu de la guilde) → *Chantiers* → *Nouveau chantier*, ou
  `/guild chantier`. Choisissez la schématique (taille, nombre de blocs, principaux matériaux) : vous recevez un **Plan
  de chantier**. Clic droit sur un bloc : l'aperçu se pose sur ce bloc, tourné dans le sens de votre regard, et vous
  seul le voyez (contour en particules, vert si le placement est possible, rouge sinon, et **blocs fantômes** à
  l'échelle 1:1). Au-delà de `maxGhostBlocks` (4096) blocs visibles de l'extérieur, seul le contour s'affiche : placez
  alors la construction avec **Litematica** (ci-dessous). Le menu d'ajustement tourne (±90°), met en miroir et déplace
  (±1, Maj : ±5, en X, Z et en hauteur) ; validez. Le fichier est alors copié dans le monde : le modifier ou le supprimer ensuite ne
  change plus le chantier. Le chantier doit être entièrement dans le territoire, sans chevaucher un autre chantier ni
  contenir le cœur, un fanion ou une construction de la guilde. Chantiers actifs : 2 au départ, +1 tous les 5 niveaux de
  guilde (8 au plus).
- **Placer avec Litematica** (conseillé pour les grandes constructions) : le serveur installe
  [Syncmatica](https://modrinth.com/mod/syncmatica) (0.3.20 pour la 26.3) dans `mods/`, les joueurs qui placent ont
  Litematica et Syncmatica (avec MaLiLib). Chargez la schématique dans Litematica, placez-la dans le territoire comme
  d'habitude (tous les blocs visibles, au bloc près), puis partagez le placement (bouton *Share* de Syncmatica dans le
  menu du placement). Ensuite *Chantiers* → **Depuis Litematica**, ou `/guild chantier litematica` : la liste montre les
  placements partagés dans votre territoire ou par un membre ; cliquez, Guildy vérifie le placement (contour vert ou
  rouge, sans blocs fantômes : Litematica les montre déjà) et vous validez. Le chantier se pose **exactement** où
  Litematica le montre, rotation et miroir compris. *Reprendre de Litematica* relit le placement si vous l'avez déplacé
  entre-temps. La guilde est prévenue quand un placement est partagé dans son territoire. Guildy lit seulement les
  fichiers de Syncmatica (`<monde>/syncmatica/placements.json`, `syncmatics/`) : sans Syncmatica, rien ne change. Les
  sous-régions déplacées dans Litematica restent à leur place d'origine.
- **Terrassement intelligent** : là où un bloc doit être posé, la terre, la pierre, le sable, les plantes, la neige, les
  feuilles et les arbres naturels sont retirés (leur butin part au stockage) et le bloc est posé à leur place. L'air de la
  schématique suit l'option *Intérieur* du chantier :

  | Intérieur | Ce qui est creusé |
  |---|---|
  | **Creuser l'intérieur** (défaut) | Seulement l'air **entouré par la construction** : pièces, cour fermée, devant et derrière des portes. Une maison posée dans une montagne ne la coupe pas en deux : la roche autour et au-dessus du toit reste. |
  | Garder le terrain | Rien : les blocs à poser remplacent seulement le terrain. |
  | Vider toute la boîte | Tout l'air de la boîte. |

  L'eau et la lave qui couleraient dans le trou sont colmatées, le sable et le gravier au-dessus d'un trou sont
  respectés. Jamais touchés : les blocs posés par des joueurs (sauf option *Remplacer les blocs construits*), les coffres
  et tous les blocs à données, le cœur, les fanions, les constructions de la guilde, le territoire des autres guildes.
- **Le monde fait foi** : un bloc déjà bien posé, par vous ou présent avant, est compté fait : aidez vos bâtisseurs !
  Après un redémarrage, l'avancement est relu dans le monde.
- **Sur le chantier** : les bâtisseurs se partagent le chantier par parcelles : terrassement depuis le haut, gros œuvre
  couche par couche depuis le bas, finitions (torches, portes, tapis, plantes…) quand leur support est posé, eau et lave
  en dernier. Ils montrent le bloc qu'ils posent (2 blocs par seconde chacun), se téléportent s'ils sont bloqués,
  rapportent les déblais au stockage et rentrent la nuit.
- **Les matériaux** : ils prennent ce qu'il faut dans les **coffres triés des villageois** (par défaut) ou, en option du
  chantier, dans **tous les coffres et tonneaux du territoire**. Les comptes sont ceux d'un joueur (dalle double = 2
  dalles, porte ou lit = 1 objet, cultures = graines ; l'eau est gratuite, la lave coûte un seau qui revient vide). Les
  blocs impossibles en survie (blocs de commande, barrières, portails, spawners…) sont comptés « non constructibles » et
  ignorés ; seuls les textes des panneaux et les motifs des bannières sont recopiés, jamais le contenu d'un coffre.
- **Deux rôles** : *Construire* et *Récolter*, ou *Auto* (la guilde répartit selon les besoins), dans la fiche du
  bâtisseur avec son chantier (*Auto* = le plus prioritaire). Si son rôle n'a rien de possible, il fait l'autre ; sans
  chantier, il va chercher ce qui manque.
- **Ce qui manque, ils le trouvent** (rôle *Récolter*), par la voie **la moins chère qui marche tout de suite** :
  1. **fabrication** avec ce que le stockage contient : recettes d'établi enchaînées jusqu'à 5 étapes (bûches →
     planches → escaliers, portes, trappes ; roche → briques de pierre ; sable → grès taillé ; laine, terre cuite, béton
     en poudre et verre teintés avec des colorants…), à la main en 2 × 2, sinon à un **établi** du territoire ;
  2. **tailleur de pierre**, s'il y en a un : escaliers, dalles, murets et variantes taillées à 1 bloc pour 1 ;
  3. **cuisson** à un **four** du territoire (simulée, plus rapide que vanilla) : verre, roche, roche lisse, briques,
     terre cuite, terre cuite émaillée… avec le combustible du stockage. Le contenu de vos fours n'est jamais touché ;
  4. **transformations** : bûches écorcées, chemins de terre, béton en poudre → béton et terre → boue à une source d'eau ;
  5. **récolte** de blocs naturels, pour de vrai (la roche donne de la pierre taillée, l'argile des boules d'argile…) :
     dans vos **mines réelles** (une fosse à au moins 3 blocs de l'escalier et des galeries prévues), dans les **zones
     de coupe** (arbres entiers, replantés), puis **hors des territoires** jusqu'à 96 blocs du cœur, jamais près du
     spawn : des **fosses propres** de 6 × 6, creusées niveau par niveau avec une rampe de sortie, bien alignées, et
     finies avant d'en ouvrir une autre.

  Deux bâtisseurs ne cherchent jamais la même chose, et ce qu'un chantier compte prendre au stockage n'est jamais
  utilisé comme ingrédient. Ce qu'aucune voie ne permet d'obtenir reste à déposer : « Il manque 12 Bloc de diamant : à
  déposer au stockage », comme un atelier qui manque (« Aucun four dans le territoire pour cuire Verre : posez-en un »).
- **Suivi** : barre de progression pour les membres près du chantier ; menu du chantier : avancement par étape
  (terrassement, gros œuvre, finitions, eau et lave), fin estimée, bâtisseurs, matériaux (nécessaires, posés,
  disponibles, manquants, en cours), obstacles avec leurs coordonnées, « Afficher le contour ». À la fin : titre, son,
  feu d'artifice et message à la guilde.
- **Options** : source des matériaux, intérieur, remplacer les blocs construits, fondations (remplit le vide sous le
  chantier jusqu'au sol, 16 blocs au plus), priorité, pause, renommer, annuler (rien de construit n'est retiré). Placer,
  modifier et annuler demandent la permission de gérer les villageois ; tout membre voit la liste.

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
| **III - Grand carrefour** | niv. 5, 400 or + 2 blocs de diamant | 7 à 9 | « trades de fou » : livres Raccommodage, Solidité III, Efficacité V, Fortune III, Toucher de soie, Butin III, Protection IV, totem d'immortalité, pomme dorée enchantée, modèle de forge (amélioration en netherite), cœur de la mer, trident, carapaces de shulker, parfois des **élytres** (chères) ; jamais d'arme unique (elles se gagnent en combattant, § 11) |

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

L'arme d'un boss tombe à sa mort, **brille** et ne disparaît jamais (insensible au feu, à la lave et aux explosions).
Le premier joueur qui obtient une arme **Légendaire** ou **Mythique** est **annoncé à tout le serveur** (« ✦ Steve a obtenu
[Foudrelame] (butin d'un boss de horde) ! », l'arme s'affiche au survol).

**Vraiment uniques.** Chaque arme unique porte un **numéro de série** : le serveur tient le registre de tous les
exemplaires. Par défaut, **une guilde ne reçoit jamais une arme qu'elle possède déjà** : si votre guilde détient
Foudrelame, aucun boss, quête, expédition ni gardien de ruine ne vous la redonnera (une autre guilde, si). Les armes
uniques ne s'achètent pas : il faut se battre pour les collectionner. Une
arme appartient à la guilde du joueur qui la porte (inventaire ou coffre de l'Ender) : donnée à un membre d'une autre
guilde, elle change de camp. Si toutes les armes possibles d'un butin sont déjà chez vous, vous recevez un **parchemin de
sort** à la place. Une arme **détruite** (lave, feu, cactus, vide, disparue au sol, cassée) peut de nouveau tomber. Les
admins peuvent passer à « un seul exemplaire sur tout le serveur » (`uniqueness: "server"`) ou retirer la limite
(`"none"`). Les armes déjà en circulation reçoivent leur numéro dès qu'un joueur les porte.

**Collection.** Le bouton « Armes uniques » du menu de guilde (ou `/guild uniques`) montre les 33 objets uniques (21 armes et outils, 12 pièces de panoplie) : ceux déjà
obtenues avec leur détenteur actuel, les autres en « ??? » avec leur rareté et où les trouver. Le compteur indique X / 33,
et tout le serveur est prévenu quand une guilde les a toutes réunies.

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
| **Lance du Jaguar céleste** | Légendaire | lance en netherite (Tranchant VI, Marque du chasseur II) | **Bond du jaguar** : clic droit (la charge de la lance), vous bondissez ; à l'atterrissage, les griffes lacèrent les monstres à 3,5 blocs (4 ❤, Lenteur II), pas de dégâts de chute, recharge 10 s |
| **Couronne du Roi de givre** | Épique | casque en diamant (Protection V, Respiration III, +2 ❤ max, brûle deux fois moins longtemps) | **Couronne de givre** : le monstre qui vous frappe est pris dans la glace (Lenteur IV 4 s, 1,5 ❤ de froid), les monstres proches ralentissent ; vous ne gelez jamais, recharge 3 s |
| **Égide des Âmes** | Légendaire | bouclier incassable aux motifs de bannière (Épines III, armure +2, +2 ❤ max) | **Rempart des âmes** : un coup de monstre paré revient à son auteur (100 % des dégâts bloqués, 1,5 ❤ au moins) et vous rend 1,5 ❤, recharge 1,5 s |
| **Masse du Zénith** | Légendaire | masse (Densité IV **et** Châtiment VI, Aura de feu III, Brasier II) | **Ascension solaire** : clic droit, vous jaillissez vers le ciel (sans dégâts de chute) ; à l'atterrissage ou au coup écrasant, un soleil éclate (4 ❤ de feu à 5 blocs, ×2 contre les morts-vivants), recharge 14 s |
| **Lame du Pacte** | Mythique | épée en netherite (Tranchant VII, Vampirisme II, Écho II, Affilage IV) | **Sceau du pacte** : chaque coup scelle le monstre ; au 3e sceau, un pentagramme maudit éclate (6 ❤, Flétrissement II) et la malédiction gagne les monstres à 4 blocs, recharge 2 s |
| **Hache du Seigneur de guerre** | Épique | hache en netherite (Tranchant V, Brasier II, Aura de feu II, Efficacité V) | **Faille infernale** : clic droit, une faille de magma file au sol sur 12 blocs et embrase les monstres qu'elle traverse (3,5 ❤ de feu), recharge 12 s |
| **Plastron du Serpent à plumes** | Épique | plastron en cuir jade (+5 armure, +3 robustesse ; Protection V, Protection contre les projectiles IV) | **Souffle de jade** : sous 40 % de vie, les monstres à 5 blocs sont repoussés et empoisonnés ; vous régénérez et planez comme une plume, recharge 30 s |

**Armes des ruines.** Sept armes tombent surtout des **gardiens des ruines**, et chaque gardien a sa **favorite**, tirée
bien plus souvent (`favorites` dans `uniques.json5`) : Lance du Jaguar céleste (Grand Temple du Jaguar céleste), Plastron
du Serpent à plumes (Temple du Serpent à plumes), Couronne du Roi de givre (Bastion de givre), Masse du Zénith (Ziggourat
des sables), Égide des Âmes (Citadelle des âmes), Hache du Seigneur de guerre (Autel infernal). La **Lame du Pacte** est la
favorite des **gardiens maudits**. On les trouve aussi, rarement, dans une quête légendaire ; elles ne se vendent jamais.

#### Panoplies
Trois **armures complètes**, du casque aux bottes, gardées par les gardiens des ruines. Chaque pièce est une vraie arme
unique (numéro de série, une par guilde, collection) et une bonne armure à elle seule, **sans pouvoir propre** : le
pouvoir vient de l'ensemble. Portez **2 pièces différentes** d'une même panoplie pour un bonus passif, **les 4** pour son
pouvoir (les paliers s'additionnent). Retirer une pièce retire aussitôt le palier perdu.

| Panoplie | Pièces | 2 pièces | 4 pièces | Où la trouver |
|---|---|---|---|---|
| **Hiver éternel** (Épique) | Heaume, Cuirasse, Jambières, Solerets (diamant) | **Sang-froid** : robustesse +2, vous ne gelez jamais | **Cœur de l'hiver** : +2 ❤ max, insensible à la lenteur ; sous 30 % de vie après un coup, une **nova de givre** gèle et repousse les monstres à 6 blocs (3 ❤) et vous donne Résistance II et Absorption II ; recharge 60 s | gardien du **Bastion de givre** |
| **Guerrier jaguar** (Épique) | Masque, Pectoral, Jambières, Sandales (or incassable) | **Agilité du jaguar** : vitesse +10 %, chutes amorties | **Embuscade du jaguar** : dégâts +1 ; accroupi 1,5 s (0,5 s dans le feuillage), vous êtes **camouflé** (les monstres perdent votre trace, jamais les boss) ; votre coup suivant est une **embuscade** : bond, +5 ❤, griffes autour (3 ❤) ; recharge 12 s | **Grand Temple du Jaguar céleste**, **Temple du Serpent à plumes** |
| **Brasier des âmes** (Légendaire) | Heaume, Cuirasse, Grèves, Bottes (netherite) | **Flamme d'âme** : +2 ❤ max, brûle deux fois moins longtemps | **Bûcher des âmes** : robustesse +2 ; toutes les 2 s, une **aura** arrache l'âme des 4 monstres les plus proches (1 ❤ chacun) et vous soigne ; en courant, une **traînée de feu d'âme** embrase les monstres | **Citadelle des âmes**, **Autel infernal** |

Les pièces ne tombent **que** des gardiens de leurs ruines (maudits compris) et, rarement, d'une **quête légendaire** ;
jamais vendues. Une guilde ne reçoit jamais une pièce qu'elle a déjà. La description de chaque pièce liste la panoplie,
coche les pièces portées et montre les bonus atteints ; gagner ou perdre un palier s'affiche au-dessus de la barre
d'action.

**Apparence** (avec le pack de ressources) : chaque pièce a son icône et son look porté, redessinés en 1.14.2 avec bien
plus de détails : plaques de givre ciselées et cœur de glace de l'**Hiver éternel**, fourrure tachetée, crocs et coiffe de
plumes vertes du **Guerrier jaguar**, os et flammes d'âme du **Brasier des âmes**.

**Protection minimale.** Toute armure unique (les 12 pièces de panoplie comme la Couronne du Roi de givre, le Plastron du
Serpent à plumes, les Bottes de Zéphyr et les Bottes du Coursier) protège **au moins comme la pièce en netherite** du même
emplacement (armure 3/8/6/3, robustesse 3, résistance au recul 10 %) ; ce qui manque est ajouté, une valeur plus haute
est gardée, et les exemplaires déjà obtenus sont mis à niveau dès qu'un joueur les a dans son inventaire. Réglage
`armorFloor` d'`uniques.json5` (`"netherite"` par défaut, `"none"`).

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
fonctionne. Guildy fournit aussi des **textures originales** 16×16 dans la direction artistique vanilla, la plupart
**animées** (éclairs, givre, néant, cœur en fusion, filon, lave, abysses, âmes, vent, flammes, lentille de l'arbalète et
ses états de chargement), ainsi que celles des parchemins, du grimoire (un dessin par sort, livre endormi sinon) et du
parchemin d'incantation. Aperçus : [`tools/previews/textures.png`](tools/previews/textures.png) et
`textures_animated.png` (PNG animé, à ouvrir dans un navigateur). Avec le pack, les **armures uniques ont aussi leur
apparence portée** : la Couronne du Roi de givre devient une couronne de cristaux de glace sur un cercle d'argent givré,
le Plastron du Serpent à plumes un plastron d'écailles de jade avec un masque de serpent doré, un serpent d'or dans le
dos et des mantelets de plumes de quetzal, les Bottes de Zéphyr des bottes blanc nuage et or aux chevilles ailées, les
Bottes du Coursier de hautes bottes de cavalier en cuir vert à revers fauves et boucles de laiton. L'**Égide des Âmes**
a un vrai **modèle 3D en main** (écu à pointe, crâne en relief aux yeux luisants, flammes d'âmes animées, poignée au
dos), placé exactement comme le bouclier vanilla, y compris en parade. Aperçu :
[`tools/previews/armor_worn.png`](tools/previews/armor_worn.png) (face, profil, dos, trois-quarts). Les **trois panoplies** ont aussi leurs icônes animées et leur apparence portée,
jambières comprises. Ces textures ne sont
utilisées que lorsqu'un pack est garanti (réglage `customModels` d'`uniques.json5`) :

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
Chaque parchemin a une **recharge partagée** avec son groupe (Attaque, Soutien, Rappel, Invocation, Permutation,
Spectacle ; le Ralliement partage celle du Rappel).
Les sorts ne touchent que les **monstres** : jamais les joueurs, les villageois ni les familiers, et aucun bloc n'est
modifié. Les bienfaits vont au lanceur, aux membres de sa guilde et à ses villageois. Les dégâts des sorts **ignorent
l'armure** (comme la magie des potions vanilla) : un gardien en netherite les prend en plein (la Résistance de sa rage
les réduit encore). Les deux tiers des météores d'une Pluie **suivent un monstre** de la zone pendant leur chute.

| Parchemin | Rareté | Effet (réglages par défaut, `scrolls.json5`) |
|---|---|---|
| **Pluie de météores** | Légendaire | 10 météores autour du point visé (7 suivent un monstre : l'escorte d'abord, puis le gardien), **14 ❤** par impact, monstres embrasés |
| **Tempête de foudre** | Épique | foudre en chaîne sur 8 monstres, **12 ❤** chacun, étourdis 2 s |
| **Blizzard** | Épique | tempête de 8 s sur 7 blocs : Lenteur IV, gel, **3,5 ❤** de froid par seconde |
| **Nova solaire** | Légendaire | sphère de feu jusqu'à 10 blocs : **17 ❤** (×1,5 sur les morts-vivants), monstres repoussés |
| **Singularité** | Mythique | trou noir de 3 s qui aspire tout à 10 blocs, puis implose (**24 ❤**) |
| **Égide** | Épique | dôme de 6 blocs pendant 12 s : Absorption III et Résistance II aux alliés, monstres et projectiles repoussés, 2 ❤ par seconde aux monstres du dôme |
| **Rappel** | Rare | 4 s d'incantation, puis retour au foyer de la guilde (ou au cœur) avec les membres à 6 blocs |
| **Gardiens** | Légendaire | 2 golems de fer vous escortent 60 s |
| **Bénédiction** | Rare | soin de 8 ❤, Régénération II, effets néfastes retirés (vous, vos membres, vos villageois) |
| **Révélation** | Rare | les monstres à 64 blocs brillent 30 s (visibles à travers les murs) |
| **Séisme** | Épique | 3 ondes de choc jusqu'à 10 blocs, **8 ❤** chacune, monstres projetés en l'air |
| **Rayon arcanique** | Légendaire | rayon de 32 blocs pendant 3 s qui suit votre visée, **4 ❤** par contact, cinq fois par seconde |
| **Bannière de guerre** | Épique | plante une bannière de guerre pendant 20 s : à 8 blocs autour, vous, vos membres et vos villageois gagnez **Force II** et **Célérité II** (attaques plus rapides) |
| **Fléau** | Épique | nuée maudite de 10 s sur 6 blocs au point visé : les monstres brillent, Faiblesse I, Lenteur II et **+25 % de dégâts** de toutes sources (boss : +12,5 %) |
| **Permutation** | Rare | échangez votre place avec le monstre ou l'allié de la guilde visé (en vue, 32 blocs) : monstre étourdi 2 s, allié protégé 3 s (Résistance II) ; jamais dans un mur, la lave ou au-dessus du vide |
| **Ralliement** | Épique | 4 s d'incantation, puis **tous les membres de la guilde en ligne**, où qu'ils soient (autres dimensions comprises), reçoivent **[Rejoindre]** dans le chat pendant 30 s : les 8 premiers qui cliquent sont téléportés près de vous |
| **Chaînes spectrales** | Légendaire | enchaîne le monstre visé et 2 voisins (4 blocs) sur place pendant 5 s (boss : 2,5 s), puis les chaînes se brisent (**6 ❤**) |
| **Hâte du vent** | Rare | pendant 30 s, vous et la guilde à 8 blocs gagnez **Vitesse II**, **Saut amélioré II** et ne subissez plus de dégâts de chute |
| **Grand spectacle** | Épique | face à votre public, trace **9 cercles runiques** sur 18 blocs derrière vous et tire un **vrai feu d'artifice** de **40 s** (20 s à 2 min) : ouverture, salves synchronisées, figures (cœurs, étoiles, cercle runique dans le ciel), tirs croisés, accalmie, crescendo et **bouquet final** ; ne blesse personne, ne touche à aucun bloc |

**Grand spectacle.** Tournez-vous vers votre public et lisez le parchemin : pendant les 3 s d'incantation, les cercles
runiques se tracent sur le sol **derrière vous**, du milieu vers les bords, sur une ligne de 18 blocs perpendiculaire à
votre regard. Chaque cercle a besoin d'un **ciel dégagé** (ni toit, ni plafond, ni feuillage), d'un sol à votre niveau,
ni eau ni lave, et pas du territoire d'une autre guilde : les emplacements impossibles sont sautés ; il en faut au moins 3,
sinon le parchemin reste intact. Puis le spectacle commence pour **40 s** : de **vraies fusées** synchronisées au tick
près, aux **couleurs de la bannière** de votre guilde pour l'ouverture, des **salves symétriques** de plus en plus
rapides, des **figures** (cœur, étoile, anneau, spirale, cercle runique dans le ciel), des **tirs croisés**, une
**accalmie** (comètes, saules dorés), un **crescendo** et le **bouquet final** : une gerbe dense, une seconde de silence,
puis une dernière salve géante de tous les cercles. Les fusées **ne touchent rien** : ni joueur, ni villageois, ni animal,
ni bloc ; on peut regarder debout sur un cercle. Un spectacle à la fois par joueur, pas deux à moins de 32 blocs. Avec le
**grimoire maudit** : **70 s**, **14 cercles** sur 24 blocs, couleurs de sang, de vide et d'âmes, pentagrammes et crânes.

**Un sort ne fait pas tout.** Chaque nouveau coup d'un même sort sur une même cible fait un peu moins mal (−25 % à chaque
coup, jamais moins de 20 %) : les 25 météores d'une Pluie maudite écrasent toujours une horde, mais ne s'acharnent plus
sur un seul monstre coriace. Les sorts continus (Blizzard, Rayon arcanique, Égide) ne comptent qu'un coup toutes les 5 s.
Surtout, **un boss** (gardiens des ruines, formes maudites comprises, boss des hordes, Wither, Ender Dragon, Warden) ne
perd jamais plus de **4 %** de sa vie maximale par sort lancé d'un parchemin ou d'un grimoire, et **7 %** avec le grimoire
maudit : les sorts entament un boss, ce sont les combattants qui l'achèvent. Les monstres ordinaires n'ont pas de limite.

**Sorts tactiques.** La **Bannière de guerre** reste plantée où vous l'avez lue : ses tambours battent chaque seconde et
ses bienfaits durent tant qu'on reste dans son cercle (et une seconde après). Le **Fléau** rend les monstres plus fragiles
face à tout (vos armes, vos golems, les autres sorts) ; deux Fléaux ne s'additionnent pas, et les boss n'en reçoivent que
la moitié. La **Permutation** demande de viser directement la créature (un monstre pour le sortir d'un coin, un membre ou
un villageois de la guilde pour le tirer d'affaire) : si l'arrivée est dangereuse (mur, lave, feu, vide sous une cible
volante à plus de 3 blocs), le parchemin reste intact ; un gardien de ruine peut être permuté, mais sa laisse d'arène le
ramène chez lui. Le **Ralliement** appelle **tous les membres en ligne**, à n'importe quelle distance et dans n'importe
quelle dimension (un membre du Nether ou de l'End qui clique arrive à côté de vous), sauf ceux déjà à moins de 8 blocs
et ceux qui dorment, sont morts ou spectateurs ; il ne téléporte **personne sans son accord** (chaque membre clique sur
[Rejoindre]) ; l'appel s'arrête si vous vous déconnectez ou changez de dimension. Si personne ne peut être appelé, le
parchemin reste intact et le message dit **pourquoi** (aucun autre membre en ligne, tous déjà près de vous, endormis…). Il fonctionne aussi pendant
une horde (pour défendre la base) et vers ou depuis un combat de ruine. Les **Chaînes spectrales** clouent les monstres
sur place (ni marche, ni saut, ni recul, ni téléportation), mais ils frappent encore ce qui passe à leur portée.

**Où les trouver** : surtout sur les **boss des ruines** (60 %, 75 % dans le Nether, 1 ou 2 parchemins), plus rarement sur
les boss des hordes (8 %, 30 % pendant une nuit de sang), en récompense de quête (5 %) ou de contrat légendaire (50 %), au
retour des expéditions (3 %), chez le marchand du Carrefour commercial (palier II ; palier III pour les plus rares), et
l'**alchimiste** de votre guilde en écrit (surtout des Rappels) ; les gardiens maudits en donnent plusieurs, dont un
garanti. Ils se rangent dans « Parchemins et grimoires » (sinon avec les livres). Les **élixirs arcanique, de
clairvoyance et de satiété** de l'alchimiste renforcent vos sorts pendant quelques minutes.
Le pack de ressources facultatif leur donne des textures animées (comme aux armes uniques).

#### Le grimoire
Un livre ancien qui **relie jusqu'à 4 parchemins** et relance leurs sorts sans jamais les consommer.
- **Relier** : dans l'inventaire, clic droit avec un parchemin sur le grimoire (ou avec le grimoire sur un parchemin) : un
  parchemin est consommé et devient une page (pas deux fois le même sort).
- **Clic droit** : lance le sort de la page choisie, avec la même visée et les mêmes animations que le parchemin et une
  petite recharge (3 s). L'incantation est plus rapide que celle du parchemin (60 %), mais jamais moins de 1 s : on a le
  temps de voir le cercle runique se dessiner sous ses pieds, puis s'embraser au lancement (Singularité 1,2 s, Rappel
  2,4 s). **Accroupi + clic droit** : page suivante.
- **Accroupi + F** (échange de main) ou `/guild grimoire` : le menu des pages (choisir, effacer une page — le parchemin
  est perdu —, Maj-clic sur un parchemin de l'inventaire pour le relier ; c'est aussi la façon de relier en créatif).
- Chaque sort **coûte de la nourriture** selon sa rareté : Rare 3, Épique 5, Légendaire 7, Mythique 10 points de faim (sur
  20), la saturation part d'abord. Si votre barre de faim est trop basse, le sort ne part pas (gratuit en créatif ; un
  sort raté ne coûte rien).
- **Où le trouver** : boss des ruines (8 %, 12 % dans le Nether), contrats légendaires (4 %), parfois chez le marchand
  du Carrefour commercial (palier III, 400 or).

#### Le grimoire maudit
Un tome noir et violet aux coins d'os, ceint d'une chaîne et d'un cadenas dont l'œil de sang bat comme un cœur. Il se
manie exactement comme le grimoire (4 pages reliées depuis des parchemins, clic droit pour lancer, accroupi + clic droit
pour changer de page, accroupi + F ou `/guild grimoire` pour les pages), mais **ses sorts sont bien plus puissants** :
dégâts, soins et durées ×1,75, portées ×1,35, plus de cibles, effets d'un niveau de plus. Avec lui, la **Pluie de
météores** fait tomber **25 météores** au lieu de 10 sur une zone bien plus large, la **Tempête de foudre** frappe **20
monstres** avec des bonds de 15 blocs, la **Singularité** aspire à **16 blocs**, le **Séisme** lance 5 ondes, les
**Gardiens** sont 3, le **Fléau** fait subir **+35 %** de dégâts pendant 17,5 s, les **Chaînes spectrales** retiennent
**5 monstres** (les boss 65 % du temps) et le **Ralliement** appelle jusqu'à **12 membres**. Contre un boss, un sort du grimoire maudit lui retire au plus
**7 %** de sa vie maximale : quatre Pluies maudites laissent un Archimage déchu maudit à la Menace X à environ 70 % de sa vie. Chaque sort se lance dans un pentagramme de sang, au milieu des âmes et des ténèbres.
- **Son prix** : plus de faim (Rare 4, Épique 7, Légendaire 9, Mythique 12) et **un peu de sang** (½ ❤ pour les sorts
  rares et épiques, 1 ❤ pour les légendaires et mythiques). Il ne vous laisse jamais sous **3 ❤** : en dessous, le sort
  ne part pas. Recharge 5 s. Gratuit en créatif.
- **Où le trouver** : uniquement sur l'**Archimage déchu maudit** (Tour du Mage déchu, réveillé avec un parchemin
  d'incantation maudit), à sa mort définitive : **10 %**. Aucun autre gardien, aucune quête, jamais vendu.

### 13. Ruines et boss
Des ruines oubliées parsèment l'**Overworld** et le **Nether**. Au cœur de chacune dort un **gardien**. Entrer dans la
ruine ne suffit pas : « Une présence dort ici… ». Pour l'éveiller, lisez un **parchemin d'incantation** (maintenez le clic
droit à moins de 16 blocs de l'endroit où il dort). Un cercle runique s'embrase au sol, des filets de sang s'échappent de
tous les joueurs présents vers le cercle au rythme d'un cœur qui s'emballe, une colonne d'énergie s'élève, le sol tremble
et le ciel s'assombrit… puis le gardien et son escorte surgissent dans un éclair (barre de boss) : ondes de choc, anneaux
de feu ou de givre, téléportations, salves, vol de vie, puis une rage folle quand il faiblit. **Prix du sang** : chaque
joueur présent perd 2 ❤ pendant le rituel (jamais en dessous de 2 ❤). Si tout le monde quitte le cercle, meurt ou se
déconnecte, le rituel se brise et le parchemin est rendu. Le parchemin ne fonctionne que dans une ruine dormante (ni dans
une ruine vaincue, ni si le gardien est déjà éveillé). **Parchemins d'incantation** : un avec chaque carte ou boussole de
l'Atelier du cartographe, dans les coffres des ruines (6 à 30 % selon le coffre), et parfois chez le marchand du
Carrefour commercial (palier II, 24 or). Vaincu, il laisse **de l'or** (de 15 à 25 lingots pour une ruine de palier
1 jusqu'à 60 à 90 pour le Jaguar céleste, plus **1 lingot par sbire de l'escorte tué** pendant le combat, 12 au plus ;
au-delà d'une pile, la part de chacun tombe en blocs d'or), des trésors, souvent
un **parchemin de sort**, parfois une **arme unique**, et rapporte de l'XP aux guildes des joueurs qui ont combattu ;
tout le serveur l'apprend. Le gardien revient **7 jours de jeu** plus tard. Si tout le monde s'éloigne, le combat
s'arrête et le gardien se rendort (avec toute sa vie). Les coffres des ruines ont leur propre butin, et chaque pot en cache un objet (rarement un parchemin de Rappel).

| Ruine | Où | Palier | Gardien |
|---|---|---|---|
| **Crypte oubliée** | plaines, forêts, prairies, taïga | 1 | Seigneur des Cryptes (squelette wither, 220 PV) + squelettes et zombies |
| **Tour du Mage déchu** | forêts, jungles, forêts anciennes | 2 | Archimage déchu (évocateur, 260 PV) + vindicateurs et vex |
| **Ziggourat des sables** | désert, badlands | 2 | Pharaon des sables (zombie momifié en or, 300 PV) + momies et squelettes desséchés (flèches de faiblesse) |
| **Bastion de givre** | plaines et taïgas enneigées, pics de glace, bosquets | 3 | Roi de givre (vagabond, 380 PV) + vagabonds |
| **Grand Temple du Jaguar céleste** | jungles (rare : une par carré de 5 à 7 000 blocs) | 5 | Le Jaguar céleste (ocelot géant, 760 PV, chasseur infatigable : bond et onde de choc, coup de griffes venimeux, traque invisible trahie par ses empreintes, rugissement qui ralentit et affaiblit, esprits jaguars, anneau solaire) + Embourbés, squelettes desséchés et araignées venimeuses ; sentinelles : guerriers jaguars morts-vivants sous casques d'or. Maudit : le Jaguar d'obsidienne, chat noir géant |
| **Temple du Serpent à plumes** | jungles | 3 | Le Serpent à plumes (brise géante, 360 PV, dévie les projectiles : combat au corps à corps) + Embourbés (flèches empoisonnées), squelettes desséchés et araignées venimeuses ; sentinelles : morts-vivants sous casques de jade |
| **Autel infernal** | Nether : désolations, forêt carmin | 4 | Seigneur de guerre (piglin barbare, 480 PV) + piglins barbares et cubes de magma |
| **Citadelle des âmes** | Nether : vallée des âmes, deltas de basalte, forêt biscornue | 5 | Champion des âmes (squelette wither, 650 PV) + squelettes wither et blazes |


**Sentinelles** : une ruine dormante n'est pas vide. Dès qu'un joueur en survie approche, des sentinelles prennent leur
poste dans les couloirs, les antichambres et aux entrées de l'arène (2 à 6 selon le palier, une de plus par joueur
supplémentaire et tous les deux niveaux de menace). Elles ne s'annoncent pas : « Des sentinelles veillent sur ce lieu…
tant qu'elles se dressent, le sommeil du gardien ne peut être troublé. » Lire un parchemin d'incantation alors qu'il en
reste dans la salle fait apparaître le cercle… qui se fissure et vole en éclats (« Une présence scelle encore
l'entité… »), le parchemin est conservé. Tuées, elles ne reviennent pas tant que vous restez dans les parages ; si tout le
monde s'éloigne une minute, les survivantes repartent et d'autres vous attendront au prochain passage.

**Or des gardiens** (lingots, avant les bonus de menace +15 % par niveau, de groupe +50 % par joueur en plus, de
résurrection +50 % et de malédiction ×3) : Crypte oubliée 15-25 ; Tour du Mage déchu et Ziggourat des sables 20-32 ;
Bastion de givre et Temple du Serpent à plumes 28-42 ; Autel infernal 36-56 ; Citadelle des âmes 50-75 ; Grand Temple du
Jaguar céleste 60-90. Les sentinelles ne donnent pas d'or (elles reviennent quand tout le monde s'est éloigné).

**Bilan du combat** : à la fin d'un combat de ruine, chaque participant reçoit son bilan dans le chat : durée, ruine,
menace, gardien maudit ou ressuscité, totems d'immortalité utilisés par l'équipe, puis les titres du combat — *Fléau du
gardien* (dégâts au gardien, « avec N totems » s'il en a fallu), *Briseur de gardes* (sentinelles), *Chasseur de sbires*
(escorte), *Ange gardien* (soins donnés aux autres), *Protecteur* (absorption donnée), *Rempart* (dégâts encaissés),
*Increvable* (le plus de totems d'immortalité consommés), *Coup fatal* — et sa propre ligne. Survolez un nom pour voir
tous ses chiffres ; **[Voir le détail]** ouvre une tête par joueur. Tout compte dès l'apparition des sentinelles ;
flèches, sorts, armes uniques, animaux et golems invoqués comptent pour leur propriétaire ; le surplus de soin ne compte
pas (soins comptés : sorts Guildy, potions jetées de soin, de régénération ou d'absorption) ; un totem compte quand il
vous sauve dans l'arène (main ou seconde main). `/guild bilan` (ou `/guild bilan detail`) rouvre le dernier bilan
pendant 30 min. En cas d'échec : bilan court (le premier titre, *Increvable* et les totems).

**Plus on est, plus il est fort** : à son apparition, le gardien compte les joueurs en survie présents (8 au plus). Par
joueur au-delà du premier, il gagne +50 % de vie, +15 % de dégâts et des sbires en plus ; la barre de boss l'affiche
(« — 4 joueurs »). En échange, il rapporte +50 % d'or et d'XP de guilde par joueur en plus, et l'or est partagé : chacun
a sa pile, que lui seul peut ramasser. Ce renfort se multiplie avec la menace (menace III à 3 joueurs : vie ×2,8).

**Menace** : chaque ruine a un niveau de menace (I, II, III… jusqu'à X). Chaque victoire sur son gardien l'élève : à son
retour, il a plus de vie (+20 % par niveau), frappe plus fort, porte plus d'armure, arrive avec plus de sbires, enchaîne
ses capacités plus vite et entre en rage plus tôt — mais il rapporte aussi plus d'or et d'XP, avec plus de chances de
laisser une arme unique ou un parchemin. La menace s'affiche sur la barre de boss, à l'entrée de la ruine, dans l'Atelier
du cartographe et sur les cartes.

**Ne l'achevez pas trop vite !** Tant que la barre de boss affiche « Résurrection possible : 0:32 » (45 s au palier 1,
+15 s par palier, +5 s par niveau de menace), un gardien abattu refuse de mourir : son corps s'enfonce dans le sol puis
s'élève dans une spirale de flammes d'âme et d'étincelles de totem, sous une colonne de lumière, entouré d'anneaux de sang
et d'or et des âmes de son escorte, au milieu des éclairs ; pendant 5 secondes il est invulnérable. Puis il revient dans
un éclair et une onde de choc : **Enragé**, à pleine vie, plus grand, plus rapide, plus fort, avec de nouveaux sbires. Le
vaincre sous cette forme rapporte un butin exceptionnel (+50 % d'or et d'XP, tirages de butin, d'arme unique et de
parchemin en plus) et tout le serveur l'apprend. Il ne ressuscite qu'une fois par combat.

#### Menace VII et plus : la forme redoutable
À partir de la **Menace VII**, un gardien n'est plus le même : normal ou maudit, il se lève sous sa **forme redoutable**
(« Seigneur des Cryptes **Redoutable** », barre de boss « Menace VII — **Forme redoutable** »).
- **Bien plus de vie** : ×2,5 à la Menace VII, puis +0,5 par niveau (×4 à la Menace X), en plus de la menace, du groupe et
  de la malédiction (plafond : 8 000 PV par joueur présent à son apparition).
- **Plus de dégâts** : ses coups, ses capacités, ses projectiles et ceux de son escorte font **+50 %** à la Menace VII,
  jusqu'à **+125 %** à la Menace X, après votre armure (le bonus ne fait jamais dépasser à un coup 80 % de votre vie).
- **Plus rapide** : ses capacités reviennent plus vite (−20 % à VII, −35 % à X).
- **De nouvelles attaques**, propres à chaque gardien, toutes **annoncées** : cercles, lignes et cônes rouges au sol,
  cercles verts pour les abris, un son et un message, puis environ une seconde et quart pour esquiver. Lisez le sol !
- Son escorte a +50 % de vie. Victoire : or et XP ×1,5 (×1,8 à X), un tirage de butin en plus, plus de chances de
  parchemin et d'arme unique.

| Ruine | Attaques redoutables |
|---|---|
| **Crypte oubliée** | *Tombes agrippantes* (enraciné 2,5 s, Wither), *Pointes d'os* (lignes de pointes qui projettent en l'air), *Prison d'os* (un cercle de piliers qui se referme) |
| **Tour du Mage déchu** | *Barrage arcanique* (six projectiles à tête chercheuse), *Puits de gravité* (aspire puis implose), *Images miroir* (l'Archimage se cache parmi ses reflets) |
| **Ziggourat des sables** | *Sables mouvants* (attirent et ralentissent), *Malédiction de la momie* (explose sur les joueurs autour du marqué), *Tempête de sable* (seul l'œil est sûr) |
| **Bastion de givre** | *Pics de glace* (quatre lignes), *Sol gelé* (plaques qui persistent 6 s), *Dôme de blizzard* (un seul abri) |
| **Temple du Serpent à plumes** | *Tempête de plumes* (spirale : placez-vous entre ses branches), *Plongeon* (sur un joueur marqué), *Pluie de poison* |
| **Grand Temple du Jaguar céleste** | *Bonds en chaîne* (trois joueurs marqués), *Rugissement étourdissant* (cône), *Rayon solaire* (balaie vers un joueur) |
| **Autel infernal** | *Geysers de lave*, *Pluie de météores* de magma (sol brûlant), *Anneau de lave* (seules ses brèches laissent passer) |
| **Citadelle des âmes** | *Chaînes d'âmes* (restez à moins de 7 blocs), *Aura de flétrissure* (éloignez-vous), *Éruption d'âmes* (anneau à trois brèches) |

**Vie au-delà de 1 024** : le jeu plafonne la vie d'une créature à 1 024. Désormais la vie prévue d'un gardien (menace ×
groupe × malédiction × forme redoutable) est respectée **à toutes les menaces** : au-delà de 1 024, il garde 1 024 PV
réels et chaque coup reçu (épées, flèches, sorts) est divisé d'autant ; la barre et le bilan du combat montrent la vie
prévue. Les combats maudits ou à plusieurs contre les gros gardiens durent donc plus longtemps qu'avant.

#### Le parchemin d'incantation maudit
Un rouleau noir qui suinte un sang violacé. Il se lit comme le parchemin d'incantation (clic droit maintenu 2,5 s, à moins
de 16 blocs du gardien endormi d'une ruine dormante, et seulement quand aucun gardien de salle ne tient l'arène) et lance
un **rituel maudit** de 10 s : un pentagramme inversé s'embrase, des âmes hurlent, le sang des présents s'écoule (**prix
du sang : 3 ❤ par joueur**, jamais en dessous de 2 ❤). Le gardien surgit alors **Maudit** (barre violette) : vie ×3,
dégâts ×2, plus d'armure, plus grand, capacités plus rapides, escorte maudite ; la menace et le renfort de groupe
s'ajoutent. Il a **trois phases**, puis une **forme finale** : abattu à la fin de la phase III, il se relève toujours
une dernière fois, enragé (50 % de vie, plus grand, plus rapide, plus fort), aux couleurs de la malédiction ; le butin
maudit ne tombe qu'à sa mort définitive.
- À 66 % puis à 33 % de sa vie, il devient invulnérable et s'élève, un pentagramme géant s'embrase sous lui, des chaînes
  d'âmes vous lient, une onde de choc vous repousse, des sbires sortent de terre, puis il revient plus fort.
- **Phase II** : des pentagrammes au sol boivent votre vie et le soignent (sortez-en !), des chaînes d'âmes vous attirent
  vers lui, des météores noirs tombent.
- **Phase III** : ondes de ténèbres, cages de foudre (sortez du cercle), nova de sang (éloignez-vous), ombres de lui-même,
  aura maudite. Chaque attaque est annoncée par un cercle rouge.
- **Victoire** : or et XP ×3, butin multiplié, jusqu'à 2 armes uniques, un parchemin de sort garanti, 35 % de chance d'un
  grimoire, et tout le serveur apprend que « la malédiction est brisée » ; la menace de la ruine monte de 2 niveaux. Si
  tout le monde part ou meurt, il se dissipe et la ruine se rendort (le parchemin est perdu).
- **Où le trouver** : marchand du Carrefour commercial (palier III, 250 or, parfois), gardiens vaincus sous leur forme
  Enragé (15 %), contrats légendaires (10 %).

**Sanctuaires** : une balise dans une ruine et sa pyramide sont protégées : on ne peut ni les casser, ni les faire
exploser, ni les déplacer au piston (« Une force ancienne protège ce sanctuaire »). Le reste de la ruine se casse
normalement. La balise du Grand Temple du Jaguar céleste se voit de très loin.

Les ruines n'apparaissent que dans les chunks **pas encore générés** : il faut explorer de nouvelles zones.
Aperçus des huit ruines (le grand temple a aussi une planche de coupes) : [`tools/ruins/previews/`](tools/ruins/previews/). Pour tester : `/place structure guildy:ruin/<id>`
(dans le Nether pour les deux dernières), `/locate structure #guildy:ruins`.

**Ajouter vos propres ruines** : construisez-la en créatif, marquez la case du boss, celles des sbires et l'entrée par des
panneaux (`[boss]`, `[sbire]`, `[entree]`, `[coffre commun|rare|tresor]`), exportez-la (WorldEdit `.schem`, Litematica
`.litematic` ou bloc de structure `.nbt`) puis convertissez-la avec `python tools/ruins/import_schematic.py` : elle devient
une variante de la ruine choisie (procédure complète dans [`tools/ruins/README.md`](tools/ruins/README.md)).

**L'Avatar : l'étoile du Nether.** Posez une **étoile du Nether** au sol dans le cercle (moins de 16 blocs du gardien
endormi) et lisez un **parchemin d'incantation maudit** : l'étoile frémit et s'entoure de braises pendant le rituel (on ne
peut plus la ramasser). À la fin du rituel, elle est **consumée** et le gardien se lève en **Avatar infernal** : le ciel
vire au rouge sombre, la musique de boss résonne, la barre de boss est rouge et crantée, et tout le serveur l'apprend. Un
parchemin normal ne touche jamais l'étoile ; si le rituel échoue, l'étoile reste au sol.
- **Au moins Menace X** : il se bat comme un gardien maudit de Menace X sous sa forme redoutable, puis ×2 : de 10 000 à
  16 000 ❤ par joueur présent. Ses coups font encore +20 % (jamais plus de 90 % de votre vie d'un seul coup).
- **Trois phases puis sa forme ascendante** (abattu à la fin de la phase III, il se relève une dernière fois, plus vite).
- **Ses attaques** : celles de sa ruine en plus fort dès la phase I, et celles des âmes et du Nether — *Piliers de feu des
  âmes* et *Têtes de wither* (phase I), *Failles du Nether* qui libèrent blazes et squelettes wither, *Pluie de boules de
  feu* (phase II), *Chaînes d'âmes* et *Anneau de feu des âmes* qui se referme (phase III) ; les attaques maudites
  s'ajoutent à partir de la phase II.
- **Butin mythique** : celui d'un gardien maudit, puis or ×1,5, XP ×2, deux tirages de butin en plus, jusqu'à trois armes
  uniques, deux parchemins de plus (un garanti), un grimoire vierge, et le **Cœur de l'Avatar** (trophée). La menace de la
  ruine monte de 2 niveaux.
- Admin : `/guild admin ruin awaken cursed avatar` (sans rituel ni étoile), `/guild admin ruin avatar attack <attaque>`,
  `/guild admin ruin dread <capacité>`.

**Les trouver : l'Atelier du cartographe** (construction, guilde niveau 5, 80 or). Donnez la tâche **Cartographe** à un
villageois, puis clic droit sur le panneau de l'atelier : **« Chercher une ruine »** à la surface (8 papiers + 1 boussole)
ou dans le Nether (+ 4 quartz), pris dans votre inventaire. Le cartographe y travaille le jour (environ 8 minutes de
travail, plus vite à plusieurs, 3 au plus). Ensuite, **« Récupérer »** donne une **carte d'explorateur** marquée d'une
croix (surface) ou une **boussole** qui pointe vers la ruine dans le Nether (coordonnées dans sa description). Chaque
recherche mène à une ruine différente. Le menu de l'atelier liste les ruines connues de la guilde et leur état
(dormante, réveillée, vaincue — de retour dans N jours).

**La légende du Jaguar céleste.** Le Grand Temple du Jaguar céleste ne se trouve pas avec une carte ordinaire :
« Chercher une ruine » ne le propose jamais. À l'Atelier (guilde niveau 7), lancez **« La légende du Jaguar céleste »**
(16 papiers, 1 boussole, 8 émeraudes, 40 or du trésor, environ 16 minutes de travail). Vous recevez une **carte des
statues** : des jaguars de pierre jalonnent le chemin entre votre atelier et le temple, mais aucune carte ne montre le
temple lui-même. Approchez-vous d'une statue : ses yeux de jade s'illuminent, une traînée dorée file vers la statue
suivante et le chat murmure « Le regard du jaguar de pierre se tourne vers le nord-est… le chemin continue. » La dernière
statue se dresse en vue de la balise du temple : « Au loin, la lumière du Jaguar céleste perce la canopée. » Les statues
apparaissent quand on explore leur région, sur un sol naturel, jamais dans un territoire de guilde ; elles sont protégées
comme les sanctuaires. Aperçu : [`tools/ruins/previews/statue_jaguar.png`](tools/ruins/previews/statue_jaguar.png).

### Rôles
**Chef**, **Officier**, **Membre**. Par défaut les membres construisent et interagissent ; les officiers
invitent, excluent, gèrent le territoire, le trésor, les villageois, les constructions, la bannière et les
quêtes ; le chef promeut, rétrograde, transfère la direction et dissout. Modifiable dans `config.json5`
(`rolePermissions`).

---

## Commandes

`/gld` est un alias de `/guild`. Auto-complétion partout ; les noms de guilde avec espaces sont acceptés
(entre guillemets quand un autre argument suit).

### Raccourcis
Les commandes les plus tapées existent aussi au premier niveau. Un raccourci **copie** la sous-commande : mêmes
arguments (`/g info <guilde>`, `/villageois accept`), même auto-complétion, mêmes droits (une commande d'admin reste
réservée aux admins).

| Raccourci | Commande |
|---|---|
| `/g` | `/guild` (le menu, et toutes les sous-commandes : `/g info`, `/g deposit`…) |
| `/home` · `/sethome` | `/guild home` · `/guild sethome` |
| `/fly` | `/guild fly` |
| `/quetes` | `/guild quests` |
| `/villageois` | `/guild villagers` |
| `/bilan` | `/guild bilan` |

Ils se règlent dans `config.json5 > shortcuts` (`enabled`, `aliases` : raccourci → sous-commande de `/guild`, plusieurs
mots possibles, `"guild"` = `/guild` lui-même ; par exemple `"depot": "deposit"`, `"stockage": "stockage"`) et
s'appliquent au démarrage, avec `/reload` ou avec `/guild admin reload`. Un nom déjà pris par Minecraft ou par un autre
mod (par exemple le `/home` d'Essential Commands) n'est **jamais remplacé** : le raccourci est ignoré et signalé dans le
journal du serveur et par `/guild admin shortcuts` ; renommez-le (`"ghome": "home"`) ou retirez-le. `/guild help`
affiche les raccourcis disponibles (cliquables).

### Joueurs
| Commande | Effet |
|---|---|
| `/guild` · `/guild menu` | Menu principal |
| `/guild create <nom>` | Créer une guilde (donne le drapeau fondateur) |
| `/guild invite <joueur>` · `join [guilde]` (`accept`) · `deny [guilde]` | Invitations |
| `/guild leave` · `kick <membre>` · `promote` · `demote` · `transfer <membre>` | Membres |
| `/guild disband` | Dissoudre (chef, confirmation) |
| `/guild info [guilde]` · `list` · `help` | Informations |
| `/guild version` | Version de Guildy et de Minecraft (aussi depuis la console) |
| `/guild deposit [all\|montant]` · `withdraw <montant>` | Trésor |
| `/guild banner` · `banner adopt` | Menu Bannière / adopter la bannière tenue en main |
| `/guild flag` · `flag buy` | Récupérer le drapeau fondateur / acheter un drapeau d'extension (prend d'abord un drapeau offert par un palier de niveau) |
| `/guild claim` · `unclaim` · `show` | Territoire |
| `/guild sethome` · `home` | Point de retour de la guilde |
| `/guild fly` · `fly on\|off` | Vol de guilde dans son territoire (bascule / activer / désactiver) ; jamais pendant une horde. Permission `guildy.fly` (accordée par défaut) |
| `/guild waves` | Hordes : état, prochaine nuit, statistiques, règles |
| `/guild build` | Constructions et plans |
| `/guild quests` | Quêtes |
| `/guild villagers [accept\|refuse\|zones]` · `villagers route new\|finish\|undo\|cancel` | Villageois, zones de travail / tracé d'un parcours de garde |
| `/guild upgrades` | Arbre d'évolution (améliorations permanentes) |
| `/guild trade` | Carrefour commercial : palier, marchands présents, échanges possibles, amélioration |
| `/guild uniques` | Collection d'armes uniques de la guilde |
| `/guild grimoire` | Menu des pages du grimoire tenu en main |
| `/guild ralliement <id>` | Rejoindre un appel au Ralliement (la commande du bouton [Rejoindre], inutile de la taper) |
| `/guild bilan` · `bilan detail` | Dernier bilan de combat de ruine (chat ou menu détaillé) |
| `/guild chantier` · `chantier list\|confirm\|cancel\|litematica` | Chantiers des bâtisseurs : menu, liste dans le chat, valider ou annuler le placement en cours, placements partagés depuis Litematica (alias `/guild chantiers`) |
| `/guild stockage scan <chunks> [remplacer]` · `stockage filtre ici\|retirer` · `stockage rangement colonnes\|lignes` | Coffres filtrés : analyser les coffres autour de soi et les enregistrer, filtrer le coffre regardé ; rangement dans les coffres (aussi `/guild villagers storage …`) |

### Administration
Permission **`guildy.admin`** (LuckPerms…) ; sans gestionnaire de permissions : opérateurs niveau 2
(`general.adminOpLevel`).

| Commande | Effet |
|---|---|
| `/guild admin reload` · `save` | Recharger toute la config / sauvegarder les données |
| `/guild admin bypass` | Ignorer les protections de territoire |
| `/guild admin info\|disband <guilde>` · `forcejoin <joueur> <guilde>` | Gestion des guildes |
| `/guild admin setlevel\|addxp\|addgold <guilde> <valeur>` | Progression et trésor (`setlevel` jusqu'au niveau max, sans récompenses ; `addxp` donne les récompenses des niveaux gagnés) |
| `/guild admin shortcuts` | Raccourcis : actifs, ou ignorés avec la raison (nom invalide ou réservé, en double, cible absente, nom déjà pris par Minecraft ou un autre mod, remplacé ensuite par un autre mod) |
| `/guild admin levels` | Courbe des niveaux : XP totale, XP du niveau et rang aux niveaux clés |
| `/guild admin giveflag <joueur> extension [n]` · `giveflag <joueur> founding <guilde>` · `tp <guilde>` | Drapeaux, téléport au cœur |
| `/guild admin wave start\|stop\|status <guilde>` · `wave setnight <guilde> <n>` · `wave truce <guilde> <nuits>` · `wave schedule <guilde> <nuits>` | Hordes (start = horde de test immédiate ; truce = nuits de trêve restantes, 0 = fin ; schedule = prochaine horde dans N nuits, 0 = cette nuit) |
| `/guild admin structure unlock <guilde> <id>` · `structure check <guilde>` | Constructions |
| `/guild admin quest reroll <guilde>` · `quest complete <guilde> <slot>` · `quest map <guilde>` · `quest legendary <guilde> <slot>` | Quêtes (`legendary` : remplace la quête du panneau par un contrat légendaire) |
| `/guild admin villager spawn <guilde>` · `villager chunks` | Faire venir un candidat villageois ; chunks gardés chargés pour les villageois (par guilde) |
| `/guild admin upgrade unlock\|lock <guilde> <id>` | Arbre d'évolution : `unlock` débloque gratuitement (avec les prérequis manquants), `lock` retire l'amélioration et celles qui en dépendent |
| `/guild admin event list` | Événements : réglages, nuit de sang prévue, raids en attente, marchands présents par guilde |
| `/guild admin event start\|stop <événement> [guilde]` | `blood_night` (tout le serveur : la nuit en cours ou la prochaine), `pillager_raid` (immédiat si un membre est au cœur, sinon dès qu'il y en a un), `caravan`, `book_merchant`, `merchant` (visite du Carrefour) ; `stop` annule / renvoie le marchand |
| `/guild admin trade tier <guilde> <palier>` | Fixe le palier du Carrefour commercial |
| `/guild admin unique list` · `unique give <joueur> <id>` | Armes uniques : liste (rareté, pouvoir, chances par source ; clic sur un nom = préparer le `give`), donner une arme (un doublon est signalé) |
| `/guild admin unique registry [id]` · `unique free <id> [guilde]` | Registre des exemplaires (numéros, détenteurs, doublons) ; marquer une arme comme perdue pour qu'elle puisse retomber |
| `/guild admin rune list` · `rune inscribe <joueur> <id>` · `rune remove <joueur>` | Runes : liste, graver une rune sur la pièce d'armure tenue (règles respectées), retirer la rune de l'objet tenu |
| `/guild admin elixir list` · `elixir give <joueur> <id> [nombre]` · `elixir boosts <joueur> [clear]` | Élixirs : liste, donner, bonus magiques actifs d'un joueur (les effacer) |
| `/guild admin scroll list` · `scroll give <joueur> <id> [nombre]` · `scroll grimoire <joueur> [cursed] [ids]` | Parchemins de sort : liste (clic sur un nom = préparer le `give`), donner des parchemins, donner un grimoire (avec des pages déjà écrites si des ids sont donnés ; `cursed` = grimoire maudit) ; `scroll give <joueur> incantation\|incantation_maudit [nombre]` donne aussi les parchemins d'incantation |
| `/guild admin ruin list` · `info` · `awaken` · `reset` | Ruines : liste, ruine la plus proche, réveiller / rendormir son gardien |
| `/guild admin ruin locate <id>` · `map <joueur> <id>` · `research <guilde> finish` | Localiser une ruine, donner une carte / boussole vers une ruine, terminer la recherche en cours d'une guilde |
| `/guild admin ruin ability <type> [style]` | Jouer un effet de capacité de boss (test visuel) |
| `/guild admin ruin statues <joueur>` · `ruin statues list` | Carte des statues du grand temple le plus proche ; statues en attente / posées / abandonnées |
| `/guild admin ruin awaken cursed avatar` · `ruin avatar attack <attaque>` · `ruin dread <capacité>` | Avatar sans rituel ni étoile ; lancer une attaque de l'Avatar ; lancer une attaque redoutable |
| `/guild admin ruin threat <niveau>` | Niveau de menace de la ruine la plus proche (appliqué au prochain réveil) |
| `/guild admin ruin guards` · `ruin guards clear` | Gardiens de salle de la ruine la plus proche : les poster maintenant / les renvoyer |
| `/guild admin ruin incantation <joueur> [nombre] cursed` · `ruin awaken cursed` | Parchemins maudits ; réveiller le gardien maudit de la ruine la plus proche (sans rituel) |
| `/guild admin ruin cursed phase` · `ruin cursed attack <attaque>` | Phase suivante tout de suite ; lancer une attaque maudite (pentagrams, chains, meteors, darkness, cage, nova, clones) |
| `/guild admin ruin incantation <joueur> [nombre]` | Donner des parchemins d'incantation (`awaken` réveille toujours le gardien sans rituel) |

---

## Configuration

Tout l'équilibrage est dans `config/guildy/` (fichiers commentés en français, **rechargeables à chaud** avec
`/guild admin reload`, valeurs bornées automatiquement) :

| Fichier | Contenu |
|---|---|
| `config.json5` | Guildes (noms, membres, invitations, home), territoire (rayon fondateur, extensions, distances, chunks max par niveau, prix des drapeaux), protections, économie, niveaux (`levels` : niveau max, seuils et formule d'XP, récompenses, paliers, rangs ; chunks au-delà du
niveau 10), bannière (métier à tisser / éditeur gratuit, cœur sur les cartes), rôles, vol de guilde (`fly` : niveau, rôle,
hordes, délai après un coup, avertissement de bord, protection de chute), raccourcis de commandes (`shortcuts`) |
| `waves.json5` | Horaires, jours entre deux hordes (1 à 3), formule de difficulté (et part du niveau au-delà de 10 : `perLevelAbove10`, `maxAbove10`), taille des hordes, élites, boss, buffs, intégrité du cœur, récompenses, défaite, trêve après plusieurs défaites, **thèmes** (liste pondérée : monstres, difficulté minimale, boss) |
| `structures.json5` | Constructions : motifs (lignes + clé bloc/`#tag`/`*` = n'importe quel bloc plein), coûts, niveaux, panneaux |
| `quests.json5` | Modèles de quêtes (17 types : cible, quantités, assortiments, récompenses, durée, poids, niveaux), contrats légendaires (chance, niveau, délai, multiplicateurs), niveau de calcul au-delà de 10 (`levelScalingAbove10`, `maxScalingLevel`), chance d'arme unique, cartes au trésor ; les nouveaux modèles par défaut sont ajoutés une fois aux fichiers existants (`knownTemplates`) |
| `shop.json5` | Catégories et articles (lot, prix d'achat, prix de vente, niveau requis) |
| `villagers.json5` | Arrivées, limites, réglages des tâches, zones de travail et parcours de garde (nombre, rayon, distance au cœur, recherche automatique, marge des objectifs prioritaires, pauses, laisse), gardes (rôles, arc, repli, soins, équipement pris dans les coffres, équipement de base par niveau), vie et refuge de nuit (`vitality`, `night`), plantation (bûcheron, fermier), chunks gardés chargés (`chunkLoading`), candidats en groupe, destinations d'expédition, catégories de rangement (règles), tables de minage simulé par objectif, minage réel (`realMining` : mode par défaut, vitesse, profondeurs par objectif, escalier, galeries, salles, filons, torches, remontée du soir, listes de blocs naturels / minerais / blocs de remplissage), catégories de rangement, départs après défaite, forgeron (`blacksmith`) et alchimiste (`alchemist` : ordre des travaux, répartition des rôles, plafonds par jour,
potions à l'alambic), travaux des champs, stockage (`storage` : limites, 0 = illimité ; sous-catégories `parent` ; salles
réservées ; parties des grands groupes `maxGroupChests` ; salles filtrées `filteredRooms`, `filteredRoomPotions`, `filteredRoomBooks`)
(`farmer.fieldWork`, `farmer.irrigation`), re-rangement (`sorter.tidy`, portée, réservations), rangement par colonnes (`storage.defaultChestLayout`), coffres filtrés et
analyse (`maxFilterChests`, `filterMaxItems`, `scan*`) et salles des coffres
(`storage.maxRooms`, marge, seuils de réattribution) |
| `upgrades.json5` | Arbre d'évolution : branches, améliorations (palier, niveau, prérequis, coût en or et en objets, effets), durée des effets de territoire |
| `trade.json5` | Carrefour commercial : visites (chance, séjour, heure), paliers (niveau, coût, nombre d'échanges) et tables d'échanges (objet, lot, prix en or, stock, poids, enchantement) |
| `events.json5` | Événements : heures des tirages, nuit de sang (chance, conditions, multiplicateurs), raid de pillards (chance, alerte, durée, composition), caravane et érudit (chance, séjour, échanges) |
| `alchemy.json5` | Potions et élixirs des alchimistes : mode de brassage (`brewing` : simple = poudre de blaze et temps par classe de produit, plafond d'élixirs ; recipes = étapes vanilla), stocks par défaut, élixirs (base, ingrédient, effets, bonus magiques), plafonds et affichage des bonus magiques |
| `builds.json5` | Chantiers : limites des schématiques (côté 256, volume 2 097 152, 500 000 blocs, fichier 64 Mo ; jusqu'à 1024 / 64 millions / 16 millions / 1 Go), chantiers actifs par niveau, options par défaut (intérieur, sources, fondations), aperçu, placements de Litematica (section `litematica`), vitesse et sac des bâtisseurs, répartition des rôles, blocs naturels du terrassement, colmatage, barre de progression |
| `supply.json5` | Matériaux des bâtisseurs : fabrication (profondeur des recettes, établi, tailleur de pierre, tous les coffres), cuisson (durée, combustibles), transformations, récolte (mines, zones de coupe, hors territoire, rayon, fosses), sources forcées, objets jamais cherchés |
| `runes.json5` | Runes du forgeron : gravure (niveau de guilde, chance, plafond par jour, examens, matériau, annonce), poids des rangs, objets des runes mythiques, définitions des runes (nom, rang, pièces, poids, bonus d'attributs, pouvoir), réglages des pouvoirs |
| `scrolls.json5` | Parchemins de sort : définitions (sort, paramètres, rareté, incantation, recharge et groupe, modèle), grimoire (`grimoire` : incantation, recharge, coût en faim par rareté, puissance, chances), grimoire maudit
(`cursedGrimoire` : multiplicateurs de puissance / portée / nombre, niveaux en plus, surcharges par sort, faim, sang,
vie minimale, recharge, chance par ruine maudite), chances par source, prix au Carrefour, limites (effets simultanés, budget de particules, rendement décroissant des coups répétés `repeatHitDecay` / `repeatHitFloor`, part maximale de la vie d'un boss par sort `bossCastCap` et `cursedGrimoire.bossCastCap`) |
| `ruins.json5` | Ruines : forme redoutable (`escalation.dreadThreshold`, `escalation.dread`, `dreadAbilities` de chaque ruine), Avatar (`cursed.avatar`), bilan du combat (`stats`), sentinelles (`guards`, annonce à l'écran désactivée), rituel scellé (`summon.sealSeconds`), renfort de groupe (`scaling`), gardiens maudits (`cursed` : rituel, multiplicateurs, phases, attaques, récompenses, sources), menace (`escalation` : bonus par niveau, plafond), résurrection (`resurrection` : fenêtre, durée, bonus de la forme enragée, butin bonus), réveil par parchemin d'incantation ou à l'approche (`summon` : mode, rayon de l'arène, durée du rituel, prix du sang), gardien de chaque ruine (créature, nom, vie, dégâts, armure, taille, équipement, escorte, capacités et recharges), réveil, réinitialisation, retour (`respawnDays`), récompenses ; recherches du cartographe (durée, coûts, rayon de recherche, carte) et légende du Jaguar céleste
(`cartography.statues` : niveau, coûts, nombre et espacement des statues, distance de la dernière au temple, sol,
guidage, carte) |
| `uniques.json5` | Armes uniques : unicité (`uniqueness` : guild / server / none, parchemin de remplacement, annonce de collection), définitions (objet de base, nom, rareté, histoire, enchantements, attributs, incassable, pouvoir, modèle), tables de butin par source (chance + poids), armes favorites des gardiens (`favorites`), panoplies (`sets` : bonus par nombre de pièces portées ; champ `set` des pièces ; pouvoirs `winterHeart`, `jaguarAmbush`, `soulPyre`), protection minimale des armures (`armorFloor`), réglages des pouvoirs (recharges, dégâts, rayons), annonces, modèles personnalisés (`customModels`, Polymer) |
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
- `<monde>/guildy/uniques.json` : registre des armes uniques (numéro, détenteur, état actif / perdu, collections),
  écrit de façon atomique avec `.bak`.
- `<monde>/guildy/ruins.json` : ruines connues (position, état, retour du gardien, niveau de menace), écrit de façon atomique avec `.bak`.
  Un combat de boss n'est pas sauvegardé : après un redémarrage, le gardien et son escorte sont supprimés et la ruine
  redevient dormante.
- `<monde>/guildy/builds/` : copie des schématiques des chantiers validés (l'original peut ensuite changer). Les
  chantiers et les fosses des bâtisseurs sont dans `guilds.json` ; l'avancement d'un chantier n'est pas sauvegardé, il
  est relu dans le monde.
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
- Armes uniques : une arme rangée dans une boîte de Shulker garde son dernier détenteur connu ; `/clear`, la poubelle du
  mode créatif ou un monstre qui disparaît avec l'arme ne sont pas détectés (`/guild admin unique free`).
- Le trident unique reste une image plate en main ; lancé, il garde le modèle 3D vanilla. Les textures portées des
  armures uniques ne sont pas animées (Minecraft n'anime pas les textures d'équipement) et les pointes de la Couronne ne
  dépassent guère du crâne (le casque n'est qu'un pixel plus grand que la tête) ; un bébé monstre qui en porte une garde
  l'apparence vanilla. Avec le pack, le cuir des armures uniques ne se reteint plus (le dessin remplace la teinture) et
  l'Égide des Âmes ne montre plus ses motifs de bannière en main (ils restent visibles sans pack). Pendant la recharge de la Lance du Jaguar céleste, sa charge est bloquée. Les sceaux de la Lame du Pacte
  sont perdus au redémarrage ; le Souffle de jade n'empoisonne pas les morts-vivants.
- Statues du Jaguar céleste : une statue regarde la direction cardinale la plus proche de la suivante (la traînée de
  lumière, elle, est exacte) ; la carte marque les points prévus (la statue peut être 20 à 40 blocs à côté).
- Ruines : l'eau du monde n'entre plus dans les ruines générées à partir de la 1.8.2 (celles déjà générées gardent leurs
  blocs) ; le nombre de gardiens de salle et de joueurs pris en compte est fixé à leur apparition ; une balise posée par un
  joueur dans une ruine devient elle aussi protégée ; un redémarrage pendant un combat maudit fait perdre le parchemin.
- Deux groupes d'une même catégorie (deux salles, ou une salle et des coffres hors salle) ne s'échangent rien ; les
  coffres posés dans une salle après sa création n'y entrent qu'avec Shift + clic dans le menu des salles.
- Un panneau de construction arraché par une explosion/un piston peut tomber en objet (la construction est
  retirée à la vérification suivante, 30 s).
- Un `structures.json5` existant reçoit automatiquement une seule fois les nouvelles constructions par défaut (liste
  `knownDefaults`) ; pour ne pas utiliser le Carrefour commercial, retirez-le de `definitions` (pas de `knownDefaults`).
- Les vagues de test admin lancées pendant une nuit de sang prévue en reçoivent les multiplicateurs (pratique pour tester).
- Le double saut des Bottes de Zéphyr repose sur l'autorisation de vol vanilla (rendue à l'atterrissage) : un anticheat
  externe pourrait le signaler. Une arme unique déjà obtenue garde le nom, la description et les enchantements de sa
  création (son pouvoir et son modèle suivent la config).

- Niveaux : les formules adoucies commencent au niveau 10 (constante) ; `/guild admin setlevel` ne donne pas les
  récompenses de niveau ; un niveau baissé puis regagné ne repaie rien ; baisser `levels.maxLevel` sous le niveau d'une
  guilde ne la fait pas redescendre. Les gardes reçoivent les paliers d'équipement 20 / 30 / 50 seulement si la table
  `guardGear.levels` d'un ancien villagers.json5 était restée celle par défaut.
- Les sorts ignorent l'armure de tous les monstres, pas seulement des gardiens ; une Pluie de météores au grimoire maudit
  peut retirer ~800 PV à un gardien seul (réglable : `params.damage`, `cursedGrimoire.powerMultiplier`). Un tiers des
  météores tombe toujours au hasard ; sur un monstre volant, le météore qui le suit explose en l'air.
- Bilan du combat : un totem ne compte que dans l'arène d'un combat de ruine en cours (pas pendant les hordes).

- Vol de guilde : il repose sur l'autorisation de vol vanilla, comme le double saut (un anticheat externe pourrait le
  signaler) ; la sortie du territoire est vue toutes les 0,25 s (en vol rapide, on dépasse le bord de quelques blocs, la
  chute reste protégée) ; la protection annule une seule chute ; un vol donné par un opérateur ou un autre mod n'est
  jamais pris ni retiré (les Bottes de Zéphyr le laissent aussi tranquille désormais).
- Raccourcis : un mod qui enregistre le même nom après Guildy prend le dessus (signalé « remplacé » dans
  `/guild admin shortcuts`) ; un raccourci ne peut pas préremplir une valeur d'argument (`"deposit all"` marche,
  `"deposit 5"` non) ; noms sans accents ; pas de permission propre (il hérite de la commande cible).

- Stockage : un audit de rangement lit tout un groupe (≈ 3 ms pour 128 coffres pleins) ; les parties d'un très grand
  groupe ne s'échangent pas d'objets (une même pierre peut se retrouver dans deux parties) ; les gardes ne cherchent leur
  équipement que dans les coffres qui peuvent en contenir ; les salles ordinaires ne donnent des colonnes qu'aux catégories
  principales (les sous-catégories y entrent si la salle leur est réservée).
- Alchimiste : le brassage est simulé (les emplacements et les bulles de l'alambic ne bougent pas) ; en mode `recipes`, une recette de
  datapack doit être ajoutée à `alchemy.json5` ; deux alchimistes peuvent viser le même ingrédient rare (le second
  change de fournée sous 30 s) ; la description de la tâche ne change sur un serveur existant qu'en supprimant la clé
  `villager.task.alchemist.desc` de `lang.json`.
- Nouveaux sorts : les marques du Fléau et la protection de chute de la Hâte du vent sont perdues au redémarrage ; le
  Ralliement n'appelle que les membres en ligne au lancement ; les monstres enchaînés peuvent encore frapper ou tirer ;
  la Bannière (20 s) et le Ralliement (30 s) occupent un des 3 sorts actifs par joueur pendant toute leur durée.

- Runes : les runes déjà gravées gardent les valeurs de leur gravure (seuls les réglages des pouvoirs suivent la config) ;
  la Rune du Dragon n'a que des ailes en particules (le plastron garde son apparence) et les feux d'artifice la propulsent
  comme des élytres ; réparer deux pièces dans la grille d'artisanat perd la rune, comme le nom.
- Panoplies : la vie bonus est rendue à la connexion après le chargement de la vie (comme pour les objets vanilla) ;
  pendant la recharge d'un pouvoir de panoplie, ses pièces ne s'équipent pas d'un clic droit (le glisser-déposer marche) ;
  le camouflage du jaguar est une invisibilité vanilla (l'armure portée reste visible).
- Sorts contre les boss : la vie maximale d'un monstre est plafonnée par le jeu à 1024 points, ce qui écrase en partie les
  multiplicateurs de menace, de malédiction et de groupe au-delà ; le plafond par sort suit cette vie réelle.
- Salles filtrées : un objet ajouté ou retiré à la main est vu en une seconde environ (quelques secondes pour des milliers
  de coffres) ; une épée simple dans un coffre fait aussi accepter les épées enchantées (avant « Équipement enchanté »).

- Forme redoutable et Avatar : le ciel rouge de l'Avatar dépend de l'option de vignette du client (activée par défaut) ;
  le feu et le Wither posés par une attaque gardent leurs dégâts vanilla ; dans une arène étroite, une zone peut tomber
  contre un mur ; les durées de combat sont estimées, à vérifier en jeu.
- Chantiers : les entités des schématiques (cadres, porte-armures, peintures) ne sont pas recréées ; une case enterrée est
  travaillée depuis le point d'appui le plus proche (jusqu'à 8 blocs) ; une case retenue (sable au-dessus, eau impossible à
  colmater) ou sans support n'empêche pas la fin et reste signalée ; les cultures sont posées telles quelles.
- Matériaux des bâtisseurs : seulement les blocs naturels des mines et les arbres (ni fleurs, ni Nether) ; un minerai rare
  n'est presque jamais trouvé dans une fosse et reste à déposer ; pas de fosse dans les mines simulées ni les carrières ;
  seul le four classique sert (ni haut fourneau, ni fumoir) ; « tous les coffres » exclut les boîtes de Shulker (512
  conteneurs suivis au plus).
- Grand spectacle : les fusées ne sont visibles qu'à 64 blocs et les figures à 96 blocs ; avec les particules en
  « Minimal », le bouquet final perd des étincelles ; les figures sont tournées vers le public du lancement.

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
