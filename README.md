# Guildy

Mod **Fabric 100 % serveur** pour Minecraft **26.3** : survie coopérative en guildes (**PvE uniquement**).
Les joueurs se connectent avec un **client vanilla**, sans mod ni resource pack : tout passe par des menus
(inventaires), des titres, la barre d'action, des bossbars et du chat cliquable.

Guide du joueur (HTML) : [`docs/guide-standalone.html`](docs/guide-standalone.html) — à ouvrir dans un navigateur
ou à héberger pour les joueurs.

> Fonder une base avec un drapeau → survivre aux hordes de la nuit → gagner de l'or et du butin →
> agrandir le territoire avec des drapeaux → recruter des villageois et leur confier des tâches →
> quêtes, boutique et marchands de passage → se renforcer → des nuits toujours plus dures (et parfois une nuit de sang).

---

## Installation

| Requis | Version |
|---|---|
| Minecraft (serveur) | 26.3 |
| Java | 25 |
| Fabric Loader | ≥ 0.19.5 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.162.0+26.3 (ou plus récent pour 26.3) |

1. Déposer `guildy-1.0.0.jar` **et** Fabric API dans le dossier `mods/` du serveur.
2. Démarrer le serveur : les fichiers de configuration sont créés dans `config/guildy/`.
3. Rien à installer côté client.

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
Chaque nuit, si un membre est près du cœur, une **horde** sort des **frontières** du territoire et marche vers le cœur.

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
Avec un **bureau d'accueil** et un **lit libre** dans le territoire, un villageois se présente certains matins :
**[Accepter] / [Refuser]** dans le chat (ou dans le menu). Nombre maximum : 2 + 1 par niveau de guilde.
Menu « Villageois » (`/guild villagers`, ou accroupi + clic droit sur un villageois) : la liste montre la tâche, la zone,
le statut et ce que chacun transporte ; le menu d'un villageois règle sa tâche, sa **zone de travail** et sa mission,
permet de l'**appeler** à côté de soi (« Appeler ici », dans le territoire ou une zone de la guilde) ou de le renvoyer.

| Tâche | Ce qu'il fait |
|---|---|
| **Ranger la base** | vide les coffres de **dépôt** et range chaque objet dans le coffre de sa **catégorie** (Bois, Pierre, Minerais, Cultures, Butin, Outils, Divers) |
| **Mineur** | mine à sa **mine** (minage simulé) : pierre et/ou minerais selon l'objectif, meilleurs en profondeur |
| **Bûcheron** | coupe les arbres naturels de sa **coupe de bois** (ou autour de lui) et replante |
| **Fermier** | récolte les cultures mûres de ses **champs** (ou autour de lui) et replante |
| **Garde** | patrouille dans sa zone ou sur son **parcours de garde**, sinon défend le cœur ; toujours au cœur pendant les hordes |
| **Exploration** | part explorer les terres sauvages (il quitte vraiment la base) et revient quelques minutes plus tard avec du butin : graines, pousses, cuir, émeraudes, parfois une carte au trésor — petit risque de ne pas revenir |
| **Expédition au Nether** | passe par un **portail du Nether construit dans le territoire** et revient avec quartz, or, poudre lumineuse, bâtons de blaze… — risque plus élevé |

#### Zones de travail (fanions)
Menu Villageois → **Zones et parcours** → « Créer une zone » : choisissez le type (**Mine**, **Coupe de bois**,
**Champs**, **Patrouille**) et recevez un **fanion** (bannière). **Plantez-le** (clic droit sur un bloc) : la zone est
créée là, le fanion prend son nom et ses réglages s'ouvrent. Clic droit sur un type = créer la zone à vos pieds, sans
fanion (pratique au fond d'une mine).
- Une zone peut **sortir du territoire** (à moins de 192 blocs du cœur), jamais entrer dans celui d'une autre guilde.
  Les villageois n'y travaillent que lorsqu'elle est chargée (un joueur dans les parages).
- **Rayon** en chunks autour du chunk du fanion : 0 = son chunk, 1 = 3×3 chunks (par défaut), jusqu'à 3 = 7×7.
  Une mine n'utilise que son fanion : sa **hauteur fixe la profondeur** (et donc les minerais).
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

**Mode automatique** (aucune zone choisie) : le bûcheron coupe les arbres naturels les plus proches de lui, le fermier
récolte les cultures mûres les plus proches (à moins de 48 blocs de lui et de 192 blocs du cœur, jamais chez une autre
guilde) ; le mineur va à la mine de la guilde la plus proche, sinon mine la pierre à découvert la plus proche (sinon au
cœur) ; le garde défend le cœur. Le statut indique ce qu'il a choisi (« Coupe auto près de x y z »).

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

Coffres : menu Villageois → Stockage → « Ajouter un coffre », puis clic droit sur un coffre du territoire.
La nuit, les travailleurs vont se coucher ; les gardes restent dehors. Les villageois peuvent **mourir**
pendant les hordes : protégez-les ! Seuls les membres peuvent commercer avec eux.

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
| **III - Grand carrefour** | niv. 5, 400 or + 2 blocs de diamant | 7 à 9 | « trades de fou » : livres Raccommodage, Solidité III, Efficacité V, Fortune III, Toucher de soie, Butin III, Protection IV, totem d'immortalité, pomme dorée enchantée, modèle de forge (amélioration en netherite), cœur de la mer, trident, carapaces de shulker, et parfois des **élytres** (chères) |

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
| `/guild admin wave start\|stop\|status <guilde>` · `wave setnight <guilde> <n>` | Hordes (start = horde de test immédiate) |
| `/guild admin structure unlock <guilde> <id>` · `structure check <guilde>` | Constructions |
| `/guild admin quest reroll <guilde>` · `quest complete <guilde> <slot>` · `quest map <guilde>` · `quest legendary <guilde> <slot>` | Quêtes (`legendary` : remplace la quête du panneau par un contrat légendaire) |
| `/guild admin villager spawn <guilde>` | Faire venir un candidat villageois |
| `/guild admin upgrade unlock\|lock <guilde> <id>` | Arbre d'évolution : `unlock` débloque gratuitement (avec les prérequis manquants), `lock` retire l'amélioration et celles qui en dépendent |
| `/guild admin event list` | Événements : réglages, nuit de sang prévue, raids en attente, marchands présents par guilde |
| `/guild admin event start\|stop <événement> [guilde]` | `blood_night` (tout le serveur : la nuit en cours ou la prochaine), `pillager_raid` (immédiat si un membre est au cœur, sinon dès qu'il y en a un), `caravan`, `book_merchant`, `merchant` (visite du Carrefour) ; `stop` annule / renvoie le marchand |
| `/guild admin trade tier <guilde> <palier>` | Fixe le palier du Carrefour commercial |

---

## Configuration

Tout l'équilibrage est dans `config/guildy/` (fichiers commentés en français, **rechargeables à chaud** avec
`/guild admin reload`, valeurs bornées automatiquement) :

| Fichier | Contenu |
|---|---|
| `config.json5` | Guildes (noms, membres, invitations, home), territoire (rayon fondateur, extensions, distances, chunks max par niveau, prix des drapeaux), protections, économie, seuils d'XP des niveaux, bannière (métier à tisser / éditeur gratuit, cœur sur les cartes), rôles |
| `waves.json5` | Horaires, formule de difficulté, taille des hordes, élites, boss, buffs, intégrité du cœur, récompenses, défaite, **thèmes** (liste pondérée : monstres, difficulté minimale, boss) |
| `structures.json5` | Constructions : motifs (lignes + clé bloc/`#tag`), coûts, niveaux, panneaux |
| `quests.json5` | Modèles de quêtes (17 types : cible, quantités, assortiments, récompenses, durée, poids, niveaux), contrats légendaires (chance, niveau, délai, multiplicateurs), chance d'arme unique, cartes au trésor ; les nouveaux modèles par défaut sont ajoutés une fois aux fichiers existants (`knownTemplates`) |
| `shop.json5` | Catégories et articles (lot, prix d'achat, prix de vente, niveau requis) |
| `villagers.json5` | Arrivées, limites, réglages des tâches, zones de travail et parcours de garde (nombre, rayon, distance au cœur, recherche automatique, pauses, laisse), tables de minage par objectif, catégories de rangement, départs après défaite |
| `upgrades.json5` | Arbre d'évolution : branches, améliorations (palier, niveau, prérequis, coût en or et en objets, effets), durée des effets de territoire |
| `trade.json5` | Carrefour commercial : visites (chance, séjour, heure), paliers (niveau, coût, nombre d'échanges) et tables d'échanges (objet, lot, prix en or, stock, poids, enchantement) |
| `events.json5` | Événements : heures des tirages, nuit de sang (chance, conditions, multiplicateurs), raid de pillards (chance, alerte, durée, composition), caravane et érudit (chance, séjour, échanges) |
| `lang.json` | **Tous les textes** affichés aux joueurs (français par défaut, codes couleur `&`, `{0}` = paramètres) |

Une copie des fichiers par défaut (commentés) se trouve dans [`docs/default-config/`](docs/default-config/).

Formules par défaut (modifiables) :
- **Difficulté** D = 1 + 0,5 × palier de nuit + 0,5 × (niveau − 1) + 0,75 × (membres présents − 1), plafonnée à 20.
- **Taille de la horde** = 6 + 3 × D monstres (5 à 80), au plus 30 en vie par base et 150 sur le serveur.
- **Cœur** : 150 + 30 × (niveau − 1) points ; −1/s par monstre au contact (élite −3, boss −8).
- **Victoire** : or = (4 + 2D) × facteur, XP = (20 + 10D) × facteur, facteur = 0,25 + 0,75 × (tués / total).
- **Défaite** : −25 % du trésor, −2 paliers de nuit, 20 % de départ par villageois.

## Données

- `<monde>/guildy/guilds.json` : toutes les guildes (format versionné `dataVersion`), écrit de façon atomique
  (fichier temporaire + remplacement) avec copie `.bak` ; sauvegarde à chaque sauvegarde du monde, toutes les
  5 minutes si nécessaire et à l'arrêt. En cas de fichier illisible, la sauvegarde `.bak` est utilisée.
- Les hordes ne sont pas sauvegardées : après un arrêt (même brutal), les monstres de horde restants sont
  supprimés au chargement et la horde est simplement annulée.
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
- **Mineur simulé** (pas de creusage réel, plus fiable) ; bûcheron/fermier réels et bornés ; chaque
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
- Les projectiles de visiteurs peuvent encore abîmer cadres et tableaux (le corps-à-corps est bloqué) ;
  plaques de pression, fils et piétinement des cultures ne sont pas protégés.
- Le cooldown de `/guild home` est remis à zéro au redémarrage.
- Un panneau de construction arraché par une explosion/un piston peut tomber en objet (la construction est
  retirée à la vérification suivante, 30 s).
- Un `structures.json5` existant reçoit automatiquement une seule fois les nouvelles constructions par défaut (liste
  `knownDefaults`) ; pour ne pas utiliser le Carrefour commercial, retirez-le de `definitions` (pas de `knownDefaults`).
- Les vagues de test admin lancées pendant une nuit de sang prévue en reçoivent les multiplicateurs (pratique pour tester).

---

## Développement

```bash
./gradlew build        # jar : build/libs/guildy-1.0.0.jar (+ tests unitaires et GameTests serveur)
./gradlew test         # tests unitaires (logique pure)
./gradlew runGameTest  # GameTests serveur (scénarios en jeu avec joueurs simulés)
./gradlew runServer    # serveur de développement dans ./run
```

Java 25 requis (`JAVA_HOME`).
