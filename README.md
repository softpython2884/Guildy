# Guildy

Mod **Fabric 100 % serveur** pour Minecraft **26.3** : survie coopérative en guildes (**PvE uniquement**).
Les joueurs se connectent avec un **client vanilla**, sans mod ni resource pack : tout passe par des menus
(inventaires), des titres, la barre d'action, des bossbars et du chat cliquable.

> Fonder une base avec un drapeau → survivre aux hordes de la nuit → gagner de l'or et du butin →
> agrandir le territoire avec des drapeaux → recruter des villageois et leur confier des tâches →
> quêtes et boutique → se renforcer → des nuits toujours plus dures.

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
revendique un carré de **3×3 chunks** autour. Le cœur est indestructible ; pour déménager, le chef utilise
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

Casser un bloc de la construction la désactive (les panneaux disparaissent) ; les plans se redemandent gratuitement.

### 5. Quêtes
Le tableau affiche **6 quêtes communes** à toute la guilde, mises à jour en direct sur les panneaux :
récolter, tuer des monstres, livrer des objets, survivre à des hordes, explorer un lieu. Clic droit sur un
panneau (ou `/guild quests`) : détail, progression, **Livrer**, **Récupérer la récompense** (or au trésor,
XP de guilde, objets, drapeaux, **cartes au trésor**). Une nouvelle quête remplace chaque quête terminée ou
expirée. Carte au trésor : allez aux coordonnées indiquées, carte en main, pour déterrer un coffre.

### 6. Boutique et or
L'**or** est la monnaie : **1 lingot d'or = 1 or** (un bloc d'or = 9). Clic droit sur le panneau du comptoir :
clic gauche = acheter un lot (maj : ×8), clic droit = vendre un lot (maj : tout vendre). On paie avec l'or de
son inventaire. Le **trésor de guilde** (menu Trésor, `/guild deposit`, `/guild withdraw`) finance les drapeaux,
les constructions et les bannières décoratives. Les niveaux de guilde débloquent plus d'articles et de quêtes.

### 7. Villageois
Avec un **bureau d'accueil** et un **lit libre** dans le territoire, un villageois se présente certains matins :
**[Accepter] / [Refuser]** dans le chat (ou dans le menu). Nombre maximum : 2 + 1 par niveau de guilde.
Menu « Villageois » (`/guild villagers`, ou accroupi + clic droit sur un villageois) pour leur donner une tâche :

| Tâche | Ce qu'il fait |
|---|---|
| **Ranger la base** | vide les coffres de **dépôt** et range chaque objet dans le coffre de sa **catégorie** (Bois, Pierre, Minerais, Cultures, Butin, Outils, Divers) |
| **Mineur** | va à sa zone de travail et en rapporte des minerais (meilleurs en profondeur) |
| **Bûcheron** | coupe les arbres naturels de sa zone et replante |
| **Fermier** | récolte les cultures mûres de sa zone et replante |
| **Garde** | attaque les monstres du territoire, défend le cœur pendant les hordes |

Coffres : menu Villageois → Stockage → « Ajouter un coffre », puis clic droit sur un coffre du territoire.
La nuit, les travailleurs vont se coucher ; les gardes restent dehors. Les villageois peuvent **mourir**
pendant les hordes : protégez-les ! Seuls les membres peuvent commercer avec eux.

### Rôles
**Chef**, **Officier**, **Membre**. Par défaut les membres construisent et interagissent ; les officiers
invitent, excluent, gèrent le territoire, le trésor, les villageois, les constructions, la bannière et les
quêtes ; le chef promeut, rétrograde, transfère la direction et dissout. Modifiable dans `config.json5`
(`rolePermissions`). La bannière de guilde se personnalise dans le menu (couleur + 6 motifs vanilla).

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
| `/guild banner` | Éditeur de bannière |
| `/guild flag` · `flag buy` | Récupérer le drapeau fondateur / acheter un drapeau d'extension |
| `/guild claim` · `unclaim` · `show` | Territoire |
| `/guild sethome` · `home` | Point de retour de la guilde |
| `/guild waves` | Hordes : état, prochaine nuit, statistiques, règles |
| `/guild build` | Constructions et plans |
| `/guild quests` | Quêtes |
| `/guild villagers [accept\|refuse]` | Villageois |

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
| `/guild admin quest reroll <guilde>` · `quest complete <guilde> <slot>` · `quest map <guilde>` | Quêtes |
| `/guild admin villager spawn <guilde>` | Faire venir un candidat villageois |

---

## Configuration

Tout l'équilibrage est dans `config/guildy/` (fichiers commentés en français, **rechargeables à chaud** avec
`/guild admin reload`, valeurs bornées automatiquement) :

| Fichier | Contenu |
|---|---|
| `config.json5` | Guildes (noms, membres, invitations, home), territoire (rayon fondateur, extensions, distances, chunks max par niveau, prix des drapeaux), protections, économie, seuils d'XP des niveaux, rôles |
| `waves.json5` | Horaires, formule de difficulté, taille des hordes, élites, boss, buffs, intégrité du cœur, récompenses, défaite, **thèmes** (liste pondérée : monstres, difficulté minimale, boss) |
| `structures.json5` | Constructions : motifs (lignes + clé bloc/`#tag`), coûts, niveaux, panneaux |
| `quests.json5` | Modèles de quêtes (type, cible, quantités, récompenses, durée, poids, niveau) |
| `shop.json5` | Catégories et articles (lot, prix d'achat, prix de vente, niveau requis) |
| `villagers.json5` | Arrivées, limites, réglages des tâches, tables de minage, catégories de rangement, départs après défaite |
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

## Choix de conception

- **Condition de défaite = le cœur** (le drapeau fondateur) : objectif clair à défendre, visible en bossbar ;
  murs et gardes sont des stratégies valables, mais la récompense dépend des monstres tués.
- **Conséquence de défaite modérée** (trésor −25 %, difficulté qui recule, quelques villageois qui partent) :
  ça pique sans spirale infernale, et l'équipement des joueurs n'est jamais perdu.
- **Pas de horde sans défenseur** : il faut un membre près du cœur à la tombée de la nuit (pas de raid hors ligne).
- **Monnaie = lingot d'or vanilla**, trésor de guilde = compteur ; aucun objet custom, prix de vente toujours
  inférieurs aux prix d'achat et récompenses de livraison sous les prix boutique (pas de boucle d'or).
- **Drapeaux et plans = objets vanilla** marqués (bannière, papier) : impossible à fabriquer en survie.
- **Mineur simulé** (pas de creusage réel, plus fiable) ; bûcheron/fermier réels et bornés ; chaque
  déplacement de villageois a un délai maximum et une solution de repli (jamais bloqué).
- **Performance** : index chunk → guilde en mémoire, tâches réparties sur les ticks (joueurs toutes les
  10 ticks, villageois et hordes une fois par seconde), plafonds de monstres, uniquement des chunks chargés,
  chaque erreur isolée et journalisée sans faire tomber le serveur.

## Limites connues

- Les dégâts à distance (flèches) des monstres ne sont pas multipliés ; ghasts/phantoms n'attaquent que les joueurs.
- Dormir **avant** le début d'une horde saute la nuit (sans récompense) ; pendant une horde, c'est interdit.
- Un monstre qui ne peut pas atteindre un cœur emmuré finit par être retiré puis réapparaît ailleurs.
- Les villageois occupés à une tâche ne réapprovisionnent plus leurs échanges (ils ne vont plus à leur poste).
- Les projectiles de visiteurs peuvent encore abîmer cadres et tableaux (le corps-à-corps est bloqué) ;
  plaques de pression, fils et piétinement des cultures ne sont pas protégés.
- Le cooldown de `/guild home` est remis à zéro au redémarrage.
- Un panneau de construction arraché par une explosion/un piston peut tomber en objet (la construction est
  retirée à la vérification suivante, 30 s).

---

## Développement

```bash
./gradlew build        # jar : build/libs/guildy-1.0.0.jar (+ tests unitaires et GameTests serveur)
./gradlew test         # tests unitaires (logique pure)
./gradlew runGameTest  # GameTests serveur (scénarios en jeu avec joueurs simulés)
./gradlew runServer    # serveur de développement dans ./run
```

Java 25 requis (`JAVA_HOME`).
