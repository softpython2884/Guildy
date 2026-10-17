# Ruines de Guildy — outils de construction et d'import

Six ruines à boss sont générées naturellement dans le monde (Overworld et Nether). Elles sont décrites par des
scripts Python (`ruins/<id>.py`) qui écrivent les gabarits de structure vanilla (`.nbt`) et les fichiers de
génération du monde dans `src/main/resources/data/guildy`. Vous pouvez aussi **construire vos propres ruines en
créatif** et les envoyer : le convertisseur `import_schematic.py` les met au bon format et les ajoute au jeu.

| Ruine | Id | Où | Palier |
|---|---|---|---|
| Crypte oubliée | `crypte_oubliee` | plaines, forêts, prairie, taïga | 1 |
| Tour du Mage déchu | `tour_du_mage` | forêts, taïgas anciennes, jungles, forêt venteuse | 2 |
| Ziggourat des sables | `ziggourat_des_sables` | désert, badlands | 2 |
| Bastion de givre | `bastion_de_givre` | plaines et taïga enneigées, pics de glace, bosquet | 3 |
| Autel infernal | `autel_infernal` | Nether : désolation, forêt carmin | 4 |
| Citadelle des âmes | `citadelle_des_ames` | Nether : vallée des âmes, deltas de basalte, forêt biscornue | 5 |

Les aperçus sont dans `previews/` (vues isométriques, coupes, plans ; marqueurs : rouge = boss, orange = sbires,
vert = entrée).

## Tester en jeu

```
/place structure guildy:ruin/crypte_oubliee          (là où vous êtes ; dans le Nether pour les deux dernières)
/locate structure guildy:ruin/tour_du_mage
/locate structure #guildy:ruins                      (n'importe laquelle des six)
/execute in minecraft:the_nether run locate structure guildy:ruin/citadelle_des_ames
```

`/place` pose la ruine sur le terrain existant (sans l'adaptation du terrain) ; pour voir le vrai rendu, il faut
aller voir une ruine trouvée avec `/locate` dans des chunks neufs.

---

## Construire une ruine en créatif (pour l'envoyer)

### Règles de taille

- Emprise : **48 × 48 blocs au maximum** (64 × 64 pour les deux grands repères : bastion de givre et citadelle des
  âmes), hauteur **64 au maximum**, sous-sol compris.
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

### Les marqueurs : des panneaux

Posez un panneau (n'importe quel bois, debout ou mural) et écrivez sur **la première ligne** :

| Texte | Effet |
|---|---|
| `[boss]` | le boss apparaît sur cette case (exactement **un** par ruine) |
| `[sbire]` | un sbire apparaît ici (**2 à 8**) |
| `[entree]` | l'entrée de la ruine (facultatif, un seul) |
| `[coffre commun]` | le coffre / tonneau sur lequel le panneau est accroché reçoit la table « commun » |
| `[coffre rare]` | idem, table « rare » |
| `[coffre tresor]` | idem, table « trésor » (exactement **un**) |

- Les majuscules et les accents ne comptent pas (`[Entrée]`, `[Coffre Trésor]` marchent).
- Panneaux `[boss]` / `[sbire]` : posez-les **au sol** à l'endroit exact de l'apparition, avec **3 blocs d'air
  au-dessus** et de la place autour (pas collés à un mur).
- Panneaux `[coffre …]` : **accrochez-les sur la face** du coffre / tonneau (ou posez-les dessus). Si aucun coffre
  n'est à côté, le convertisseur met un coffre à la place du panneau.
- Nombre de coffres : **1 trésor, 1 à 3 rares, 2 à 6 communs**. Un coffre sans panneau devient « commun » (avec un
  avertissement). Ne remplissez pas les coffres : leur contenu est tiré au hasard à l'ouverture.
- Jusqu'à **2 spawners** vanilla par ruine si vous voulez (réglés avec un œuf d'apparition).

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
- le nom de la ruine qu'il complète ou remplace (une des six), et s'il s'agit d'une **variante** (tirée au hasard
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
| `build_all.py` | construit les six ruines (+ variantes importées), écrit les ressources du mod, vérifie, dessine |
| `ruins/<id>.py` | une ruine par script (lancer seul : construit et dessine cette ruine) |
| `render.py` | aperçus PNG (`python tools/ruins/render.py <id>` ou `--nbt fichier.nbt`) |
| `check.py` | vérification du contrat sur les gabarits de `src/main/resources` (ou un fichier `.nbt` donné) |
| `import_schematic.py`, `test_import.py` | convertisseur de schématiques et son test |
| `gen_block_data.py` | régénère `data/blocks.json` (blocs / propriétés) et `data/colors.json` (couleurs des aperçus) pour une autre version de Minecraft, à partir du rapport du générateur de données et du jar client |
| `ruinlib/` | NBT, gabarits, états de blocs, formats, techniques de construction (murs à relief, contreforts, arcs, voûtes, toits, flèches, dômes, matériaux patinés), ruine (dégâts, effondrement sans bloc flottant, gravats), végétation, rendu |

Prérequis : Python 3.10+ et Pillow (`pip install pillow`) pour les aperçus ; le reste n'utilise que la
bibliothèque standard.

## Génération dans le monde (rappel technique)

- Overworld : structures jigsaw projetées sur la surface (`WORLD_SURFACE_WG`, `start_height` 0 : la couche 0 de
  la pièce « surface » remplace le bloc du dessus du sol), adaptation du terrain `beard_thin` ; les sous-sols sont
  une deuxième pièce accrochée sous la première par un jigsaw (le terrain est ainsi aplani au vrai niveau du sol).
  Un ensemble de structures `ruins_overworld` (espacement 34 chunks, séparation 12) : la ruine tirée dépend du biome.
- Nether : une pièce à hauteur fixe (couche 0 à y = 32, juste au-dessus de la mer de lave), adaptation `beard_box`,
  et une « caverne » d'air explicite autour de la ruine pour qu'elle ne soit jamais noyée dans la netherrack ;
  ensemble `ruins_nether` (espacement 20, séparation 7).
- Marqueurs : blocs de structure en mode DATA (`guildy:boss`, `guildy:minion`, `guildy:entrance`), ignorés par la
  génération vanilla ; le module des ruines les lit dans les pièces de la structure et vide ces cases.
- Coffres : tables `guildy:chests/ruin/common|rare|treasure` (Nether : `nether_common|nether_rare|nether_treasure`).
