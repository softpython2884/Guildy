# Guildy

Mod **Fabric 100 % serveur** pour Minecraft **26.3** : survie coopérative en guildes (PvE uniquement).
Les joueurs se connectent avec un **client vanilla**, sans mod ni resource pack.

> Fonder une base avec un drapeau → survivre aux hordes de la nuit → gagner de l'or et du butin →
> agrandir le territoire avec des drapeaux → recruter des villageois et leur confier des tâches →
> quêtes et boutique → se renforcer → des nuits toujours plus dures.

## Installation

| Requis | Version |
|---|---|
| Minecraft (serveur) | 26.3 |
| Java | 25 |
| Fabric Loader | ≥ 0.19.5 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.162.0+26.3 (ou plus récent pour 26.3) |

1. Déposer `guildy-1.0.0.jar` **et** Fabric API dans le dossier `mods/` du serveur.
2. Démarrer le serveur : les fichiers de configuration sont créés dans `config/guildy/`.
3. Aucun mod côté client.

Bibliothèques **embarquées** dans le jar (rien à installer) : SGUI (menus serveur, Patbox) et
fabric-permissions-api (lucko, compatible LuckPerms).

<!-- GAMEPLAY / COMMANDS / CONFIG SECTIONS COMPLETED AFTER INTEGRATION -->

## Données et fichiers

| Fichier | Rôle |
|---|---|
| `config/guildy/config.json5` | Guildes, territoire, protection, économie, niveaux, rôles |
| `config/guildy/waves.json5` | Vagues : difficulté, thèmes, récompenses, défaite |
| `config/guildy/structures.json5` | Constructions (plans, coûts, niveaux) |
| `config/guildy/quests.json5` | Modèles de quêtes et récompenses |
| `config/guildy/shop.json5` | Catalogue de la boutique |
| `config/guildy/villagers.json5` | Villageois : arrivées, tâches, catégories de rangement |
| `config/guildy/lang.json` | Tous les textes joueurs (français par défaut, codes couleur `&`) |
| `<monde>/guildy/guilds.json` | Données des guildes (versionnées, écriture atomique + `.bak`) |

Tous les fichiers `.json5` sont commentés et **rechargeables à chaud** avec `/guild admin reload`.

## Compiler depuis les sources

```bash
./gradlew build        # jar : build/libs/guildy-1.0.0.jar (lance aussi les tests unitaires et GameTests)
./gradlew runServer    # serveur de développement dans ./run
./gradlew runGameTest  # GameTests serveur uniquement
```

Java 25 requis (`JAVA_HOME`).
