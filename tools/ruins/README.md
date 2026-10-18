# Ruines de Guildy — outils de construction et d'import

Huit ruines à boss sont générées naturellement dans le monde (Overworld et Nether). Elles sont décrites par des
scripts Python (`ruins/<id>.py`) qui écrivent les gabarits de structure vanilla (`.nbt`) et les fichiers de
génération du monde dans `src/main/resources/data/guildy`. Vous pouvez aussi **construire vos propres ruines en
créatif** et les envoyer : le convertisseur `import_schematic.py` les met au bon format et les ajoute au jeu.

| Ruine | Id | Où | Palier |
|---|---|---|---|
| Crypte oubliée | `crypte_oubliee` | plaines, forêts, prairie, taïga | 1 |
| Tour du Mage déchu | `tour_du_mage` | forêts, taïgas anciennes, jungles, forêt venteuse | 2 |
| Ziggourat des sables | `ziggourat_des_sables` | désert, badlands | 2 |
| Bastion de givre | `bastion_de_givre` | plaines et taïga enneigées, pics de glace, bosquet | 3 |
| Temple du Serpent à plumes | `temple_maya` | jungle, jungle de bambous, jungle clairsemée | 3 |
| Autel infernal | `autel_infernal` | Nether : désolation, forêt carmin | 4 |
| Citadelle des âmes | `citadelle_des_ames` | Nether : vallée des âmes, deltas de basalte, forêt biscornue | 5 |
| Grand Temple du Jaguar céleste | `grand_temple_maya` | jungle, jungle de bambous, jungle clairsemée (rare) | 5 |

Les aperçus sont dans `previews/` (vues isométriques, coupes, plans ; marqueurs : rouge = boss, orange = sbires,
bleu = gardes, vert = entrée). Le grand temple a une deuxième planche, `grand_temple_maya_coupes.png` (vues
sud-ouest et nord-est, coupes verticales, plans de chaque niveau).

### Le Grand Temple du Jaguar céleste

Le repère ultime des jungles : 112 × 116 blocs, une pyramide de dix terrasses (69 × 69 à la base, 50 de haut) et
son temple au sommet, la crête faîtière portant un **vrai beacon** (base de fer cachée dans la couronne, verre doré :
le rayon se voit de très loin) à ~80 blocs au-dessus du sol, plus 18 couches de sous-sol. Autour : la place et son
sacbé, l'observatoire rond, le jeu de balle, le tzompantli, le petit temple de la lune, le cénote sacré, des ceibas
géants. Trois chemins mènent à la salle du Jaguar céleste (arène 29 × 29, 15 de haut, sous le cœur de la pyramide) :
la voie cachée (gueule de la tête de serpent ouest, galeries des glyphes, rideau de lianes lumineuses ou détour par la
galerie des eaux et la salle des offrandes), la voie du roi (escalier en colimaçon du puits de l'arbre du monde depuis
le temple du sommet, tombeau du roi, descente de l'âme) et la voie des eaux (tunnel du cénote, sortie par le sentier
taillé dans sa paroi). Ensemble de structures à part, beaucoup plus rare que les autres ruines (voir plus bas).

### Les statues du jaguar de pierre (monument)

Le grand temple ne se trouve pas avec une carte ordinaire : la recherche « La légende du Jaguar céleste » de l'Atelier
du cartographe donne une **carte des statues**, et le mod pose lui-même, entre l'atelier et le temple, une lignée de
statues tournées chacune vers la suivante. Ce n'est pas une ruine : pas de génération du monde, pas de boss, pas de
coffre. Le script `ruins/statue_jaguar.py` écrit un seul gabarit, `structure/ruin/statue_jaguar.nbt` (15 × 15 × 15),
et son aperçu `previews/statue_jaguar.png`.

- Un jaguar assis taillé dans le tuf, peint d'ocre et de rosettes noires (la peinture est partie par endroits), sur un
  socle maya : dallage ancien aux bords déchiquetés et petite chaussée vers l'avant, assise avec une marche tout autour,
  frise de glyphes (tuf ciselé, incrustations de jade, peinture rouge) avec un masque de jade au milieu de chaque face,
  corniche en surplomb. Le jaguar : pattes avant au bord du socle, poitrail sous un collier de jade et son pendentif,
  hanches repliées, queue enroulée sur le côté, grosse tête avancée, museau blanc, gueule rouge ouverte aux crocs
  blancs, **yeux de jade lumineux** (froglights verdoyantes) au fond des orbites sous de lourds sourcils, oreilles
  courtes (l'une est cassée). Mousse, lianes, fougères, deux pierres d'offrande avec des bougies vertes.
- **Face au nord** dans le gabarit : le mod le tourne vers sa cible (quart de tour le plus proche). Couche 0 = fondation
  enterrée, couche 1 = dallage au niveau du sol naturel ; le mod comble sous l'emprise jusqu'au vrai sol.
- Contrat d'un monument (`check.py`, `check_monument`) : au plus 16 × 16 × 24, aucun marqueur, aucun coffre / pot /
  spawner ni bloc à données, aucune entité, au moins deux yeux lumineux du côté nord, rien qui flotte, pas d'air sous le
  sol, audit de l'eau. Vérifier un fichier : `python tools/ruins/check.py statue.nbt --monument --ground 1`.
- Pour la voir en jeu : `/place template guildy:ruin/statue_jaguar` (sans fondation ni rotation), ou
  `/guild admin ruin statues <joueur>` (vraie lignée de statues vers le grand temple le plus proche, posées quand on
  approche) et `/guild admin ruin statues list`.

## Tester en jeu

```
/place structure guildy:ruin/crypte_oubliee          (là où vous êtes ; dans le Nether pour l'autel et la citadelle)
/place structure guildy:ruin/temple_maya             (64 x 64 : prévoir de la place)
/place structure guildy:ruin/grand_temple_maya       (112 x 116, ~100 de haut : beaucoup de place, loin du spawn)
/locate structure guildy:ruin/tour_du_mage
/locate structure guildy:ruin/grand_temple_maya      (rare : la recherche peut être longue)
/locate structure #guildy:ruins                      (n'importe laquelle des huit)
/execute in minecraft:the_nether run locate structure guildy:ruin/citadelle_des_ames
```

`/place` pose la ruine sur le terrain existant (sans l'adaptation du terrain) ; pour voir le vrai rendu, il faut
aller voir une ruine trouvée avec `/locate` dans des chunks neufs.

---

## Construire une ruine en créatif (pour l'envoyer)

### Règles de taille

- Emprise : **48 × 48 blocs au maximum** (64 × 64 pour les trois grands repères : bastion de givre, temple du
  Serpent à plumes et citadelle des âmes), hauteur **64 au maximum**, sous-sol compris. Le grand temple du Jaguar
  céleste a droit à **116 × 116** et **128 de haut** (une structure ne peut pas dépasser 8 chunks autour de son chunk de
  départ, marge d'adaptation du terrain de 12 blocs comprise ; la pièce est tournée au hasard autour de ce chunk).
- Construisez sur un **monde plat** (superflat). Le **sol** du monde plat est la référence : la couche de blocs
  posée *à la place* du dernier bloc du sol naturel (pavés, dalles, chemin, sol des pièces) est le « rez-de-chaussée ».
  Tout ce qui est en dessous (caves, cryptes, fondations) est du sous-sol.
- Le sol naturel copié avec la construction (herbe, terre, bedrock du monde plat) est rendu au terrain naturel par
  le convertisseur : vous pouvez le copier sans souci.

### Garder le terrain naturel autour : le vide de structure

- Là où le terrain naturel doit rester (bords irréguliers, herbe autour d'un cimetière…), posez du
  **`structure_void`** (`/give @s minecraft:structure_void`) ou ne mettez rien du tout autour du bâtiment : le
  convertisseur considère l'air « à ciel ouvert » comme du vide.
- L'air **sous un toit ou dans une pièce** est gardé : à la génération il creuse le terrain (une colline n'entre
  pas dans vos salles).
- Pour forcer de l'air (par exemple un passage à ciel ouvert taillé dans une colline), mettez des **barrières** :
  elles deviennent de l'air explicite.

### L'arène du boss

- Sol d'au moins **13 × 13** cases praticables, **6 blocs de haut** minimum, quelques **piliers** pour se cacher,
  **au moins deux accès** (deux portes, une porte et un escalier, une brèche…), **pas de lave dans le sol** de
  l'arène (de la lave dans des canaux fermés autour, oui).
- Le coffre **trésor** va dans l'arène ou juste à côté (derrière le trône, sur l'estrade…).
- Grand temple : arène d'au moins **25 × 25** cases et **12 de haut**, au moins 5 blocs libres au-dessus du boss.

### Les marqueurs : des panneaux

Posez un panneau (n'importe quel bois, debout ou mural) et écrivez sur **la première ligne** :

| Texte | Effet |
|---|---|
| `[boss]` | le boss apparaît sur cette case (exactement **un** par ruine) |
| `[sbire]` | un sbire apparaît ici (**2 à 8**) |
| `[garde]` | poste d'un garde de salle (**2 à 8**) : ils protègent la salle du boss avant qu'on l'affronte |
| `[entree]` | l'entrée de la ruine (facultatif, un seul) |
| `[coffre commun]` | le coffre / tonneau sur lequel le panneau est accroché reçoit la table « commun » |
| `[coffre rare]` | idem, table « rare » |
| `[coffre tresor]` | idem, table « trésor » (exactement **un**) |

- Les majuscules et les accents ne comptent pas (`[Entrée]`, `[Coffre Trésor]` marchent).
- Panneaux `[boss]` / `[sbire]` : posez-les **au sol** à l'endroit exact de l'apparition, avec **3 blocs d'air
  au-dessus** et de la place autour (pas collés à un mur).
- Panneaux `[garde]` : sur le chemin qui mène au boss (couloirs, antichambres, entrées de l'arène, un ou deux dans
  l'arène), **au sol** sur un bloc plein, **2 blocs libres au-dessus** et au moins 2 cases libres autour (un garde
  peut tenir un couloir de 3 de large, pas une niche).
- Panneaux `[coffre …]` : **accrochez-les sur la face** du coffre / tonneau (ou posez-les dessus). Si aucun coffre
  n'est à côté, le convertisseur met un coffre à la place du panneau.
- Nombre de coffres : **1 trésor, 1 à 3 rares, 2 à 6 communs**. Un coffre sans panneau devient « commun » (avec un
  avertissement). Ne remplissez pas les coffres : leur contenu est tiré au hasard à l'ouverture.
- Jusqu'à **2 spawners** vanilla par ruine si vous voulez (réglés avec un œuf d'apparition).
- **Pots décorés** : posez-les vides, avec ou sans tessons. Ils reçoivent tous la table « pot » (`guildy:pots/ruin`,
  Nether : `guildy:pots/ruin_nether`) : un seul petit objet (pépites d'or, os, flèches, émeraudes, éclats
  d'améthyste, plumes, tessons de poterie…, rarement un parchemin de Rappel), qui tombe quand on casse le pot.

### L'eau (et la lave)

- Mettez l'eau dans ce qui la retient : un **bassin** ou un **canal creusé dans le sol** (l'eau une couche sous le
  sol où l'on marche), des bords pleins tout autour et un fond plein. Une cascade doit **tomber dans un bassin**.
  Ailleurs, l'eau coule jusqu'à 7 blocs sur les sols, dévale les escaliers et tombe des plafonds.
- Un escalier, une dalle, un mur, une chaîne ou une lanterne collés à deux sources se remplissent d'eau et la
  répandent : gardez-les hors des bassins, ou noyez-les vous-même s'ils doivent en faire partie.
- `check.py` fait couler l'eau du gabarit comme le jeu (même portée, mêmes chutes) et refuse celle qui finit sur un
  sol, une marche, dans l'arène ; il signale les canaux à sec ou à moitié vides.
- Sous le sol, tout ce qui touche une salle doit être construit : un trou laissé au **vide de structure** au milieu
  d'une pièce ou au fond d'une niche montre le terrain… ou l'eau, quand la ruine est creusée sous la nappe (fréquent
  dans les jungles et près des rivières). Le convertisseur et `check.py` le signalent.

### À éviter

- Blocs de commande, barrières (elles deviennent de l'air), blocs de lumière, blocs jigsaw, blocs de structure
  (les marqueurs se font avec les panneaux), bedrock.
- **Blocs d'or, d'émeraude, de diamant, minerais d'or et blackstone dorée** : l'or est la monnaie du serveur.
- Entités (créatures, supports d'armure…) : elles ne sont pas reprises.
- Glace simple et neige près des lumières (elles fondent) : préférez la glace compacte / bleue.
- Blocs flottants : tout bloc doit tenir à la construction (le convertisseur le vérifie).
- Sable / gravier au-dessus du vide : ils tomberaient.

### Exporter

- **WorldEdit / FAWE** : sélectionnez la construction (`//pos1`, `//pos2`, en incluant le sous-sol), `//copy`,
  puis `//schem save nom_de_la_ruine` → fichier `.schem` dans `plugins/WorldEdit/schematics` (serveur) ou
  `config/worldedit/schematics` (solo).
- **Litematica** : faites une sélection (« Area Selection »), puis « Save Schematic » → fichier `.litematic` dans
  `.minecraft/schematics`.
- **Bloc de structure** (vanilla, jusqu'à 48 × 48 × 48) : mode SAVE, réglez la taille, « Save » → fichier `.nbt`
  dans `saves/<monde>/generated/minecraft/structures`.

### Ce qu'il faut envoyer

- le fichier (`.schem`, `.litematic` ou `.nbt`) ;
- le nom de la ruine qu'il complète ou remplace (une des huit), et s'il s'agit d'une **variante** (tirée au hasard
  avec la version actuelle) ou d'un **remplacement** ;
- la **couche du rez-de-chaussée** si la construction a un sous-sol (par exemple « le sol est la 5e couche en
  partant du bas ») ;
- une capture d'écran ou deux, c'est toujours utile.

---

## Intégration (mainteneur)

```bash
# 1. Conversion + vérification + aperçu, sans rien écrire :
python tools/ruins/import_schematic.py ma_crypte.schem --ruin crypte_oubliee --variant chapelle_noire --ground 4 --dry-run
# 2. Écriture de la variante (tools/ruins/imported/<ruine>/<variante>/template.nbt + variant.json) :
python tools/ruins/import_schematic.py ma_crypte.schem --ruin crypte_oubliee --variant chapelle_noire --ground 4
#    options : --replace (remplace la version procédurale), --weight N (fréquence relative), --air keep|void,
#              --natural "" (ne rien rendre au terrain naturel), --default-loot rare|none
# 3. Génération des ressources du mod (gabarits, pools, structures, tags) + vérification + aperçus :
python tools/ruins/build_all.py
# 4. Tests : ./gradlew build (GameTests RuinGameTests : chaque variante respecte le contrat et se génère)
```

Le convertisseur lit les `.schem` Sponge v2 et v3 (WorldEdit, FAWE), les `.litematic` (toutes les régions sont
fusionnées) et les `.nbt` de bloc de structure ; il valide chaque bloc pour Minecraft 26.3, convertit les
panneaux, gère l'air et le vide, recadre, vérifie le contrat (`check.py`) et dessine l'aperçu
(`previews/<ruine>__<variante>.png`). Une variante avec `--ground N > 0` (Overworld) est découpée en deux pièces
reliées par un jigsaw (surface + sous-sol), comme les ruines procédurales.

`python tools/ruins/test_import.py` vérifie le convertisseur : chaque ruine est exportée comme le ferait un
constructeur (panneaux au lieu des marqueurs et des tables de butin, air partout) en `.schem` v2, v3, `.litematic`
et `.nbt`, puis reconvertie et comparée à l'originale.

## Les outils

| Fichier | Rôle |
|---|---|
| `build_all.py` | construit les huit ruines (+ variantes importées) et les monuments (statue du jaguar), écrit les ressources du mod, vérifie, dessine (`python tools/ruins/build_all.py statue_jaguar` : la statue seule) |
| `ruins/<id>.py` | une ruine (ou un monument : `statue_jaguar.py`) par script (lancer seul : construit et dessine) |
| `render.py` | aperçus PNG (`python tools/ruins/render.py <id>` ou `--nbt fichier.nbt`) ; planche de coupes pour les ruines qui déclarent des `sections` ; quatre vues, une coupe et le plan pour un monument |
| `check.py` | vérification du contrat sur les gabarits de `src/main/resources` (ou un fichier `.nbt` donné, `--big` / `--grand` pour les limites des repères, `--monument` pour une statue), audit de l'eau compris |
| `import_schematic.py`, `test_import.py` | convertisseur de schématiques et son test |
| `gen_block_data.py` | régénère `data/blocks.json` (blocs / propriétés) et `data/colors.json` (couleurs des aperçus) pour une autre version de Minecraft, à partir du rapport du générateur de données et du jar client |
| `ruinlib/` | NBT, gabarits, états de blocs, formats, techniques de construction (murs à relief, contreforts, arcs, voûtes, toits, flèches, dômes, matériaux patinés), ruine (dégâts, effondrement sans bloc flottant, gravats), végétation, rendu, écoulement de l'eau et de la lave selon les règles du jeu (`water.py` ; son test : `python -m ruinlib.water` depuis `tools/ruins`) |

Prérequis : Python 3.10+ et Pillow (`pip install pillow`) pour les aperçus ; le reste n'utilise que la
bibliothèque standard.

## Génération dans le monde (rappel technique)

- Overworld : structures jigsaw projetées sur la surface (`WORLD_SURFACE_WG`, `start_height` 0 : la couche 0 de
  la pièce « surface » remplace le bloc du dessus du sol), adaptation du terrain `beard_thin` ; les sous-sols sont
  une deuxième pièce accrochée sous la première par un jigsaw (le terrain est ainsi aplani au vrai niveau du sol).
  Un ensemble de structures `ruins_overworld` (espacement 34 chunks, séparation 12) : la ruine tirée dépend du biome ;
  là où plusieurs ruines partagent un biome, leur poids départage (jungles : temple du Serpent à plumes, poids 2,
  contre tour du mage, poids 1).
- Le grand temple a son propre ensemble `ruins_landmark` (espacement 56 chunks, séparation 20, sel unique) avec une
  zone d'exclusion : jamais à moins de 10 chunks d'un emplacement de `ruins_overworld` (pas de chevauchement avec une
  autre ruine, environ 60 % des emplacements restent valables). Avec la rareté des jungles, il faut compter de l'ordre
  d'un grand temple par carré de 5 à 7 000 blocs de côté (selon la part de jungle du monde).
  Il fait partie du tag `#guildy:ruins/overworld` (`/locate`), mais la recherche de surface de l'atelier l'ignore : seule
  la légende du Jaguar céleste (carte des statues) y mène. Son beacon est un vrai beacon vanilla au sommet
  (base de fer de 4 niveaux 9 × 9 → 3 × 3 cachée dans la couronne de la crête, verre jaune au-dessus, ciel libre) : le
  module des ruines protège le beacon et sa base. Une bande d'air explicite (14 blocs au-dessus du sol, rayon ~66)
  dégage les collines de la jungle sur la place ; la place est pavée presque partout, si bien que les arbres de la
  jungle (générés après les structures) n'y prennent guère racine : surtout la canopée des bords qui déborde.
- Nether : une pièce à hauteur fixe (couche 0 à y = 32, juste au-dessus de la mer de lave), adaptation `beard_box`,
  et une « caverne » d'air explicite autour de la ruine pour qu'elle ne soit jamais noyée dans la netherrack ;
  ensemble `ruins_nether` (espacement 20, séparation 7).
- Marqueurs : blocs de structure en mode DATA (`guildy:boss`, `guildy:minion`, `guildy:guard`, `guildy:entrance`),
  ignorés par la génération vanilla ; le module des ruines les lit dans les pièces de la structure et vide ces cases.
  Les gardes (`guildy:guard`, 2 à 8 par ruine) tiennent les couloirs, antichambres et entrées de l'arène : ils
  défendent la salle du boss avant le combat. `check.py` vérifie leur nombre, leur place (sol plein, 2 blocs libres
  au-dessus, de quoi bouger) et qu'ils sont sur le chemin praticable depuis l'entrée.
- Coffres : tables `guildy:chests/ruin/common|rare|treasure` (Nether : `nether_common|nether_rare|nether_treasure`).
  Pots décorés : `guildy:pots/ruin` (Nether : `guildy:pots/ruin_nether`), un tirage, ouvert quand le pot se casse.
- Eau : les structures ont `"liquid_settings": "ignore_waterlogging"` (comme les chambres d'épreuves). En vanilla, le
  placement noie tout bloc « immergeable » (escaliers, dalles, chaînes, lanternes, pots…) posé là où le monde avait
  de l'eau : les sous-sols creusés sous la nappe (le grand temple descend 18 couches sous la jungle) se retrouvaient
  avec de l'eau qui tombait des voûtes et inondait la salle du boss, les escaliers et les galeries. L'eau du gabarit
  (et ses blocs noyés) est posée telle quelle. Les trous de vide de structure laissés sous le sol au milieu des salles
  (une décoration retirée par l'usure) deviennent de l'air (`split_pieces`).
