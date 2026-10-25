Guildy - schematiques des chantiers
===================================

Deposez ici les fichiers que les villageois batisseurs pourront construire (sous-dossiers permis) :
  - .schem / .schematic : WorldEdit, FAWE (format Sponge versions 1, 2 et 3) ;
  - .litematic          : Litematica (toutes les regions sont fusionnees en une seule boite) ;
  - .nbt                : structure vanilla (bloc de structure).
Les anciens .schematic MCEdit (avant 1.13, identifiants numeriques) ne sont pas lus : reenregistrez-les en
.schem avec WorldEdit.

Les structures sauvegardees en jeu avec un bloc de structure (dossier <monde>/generated/<namespace>/structure/)
sont aussi proposees, dans un groupe a part : un moyen de creer des schematiques en survie.

Dans le jeu : menu des villageois -> Chantiers -> Nouveau chantier (ou /guild chantier). Choisissez une
schematique, recevez le "Plan de chantier", faites clic droit sur un bloc pour poser l'apercu, ajustez
(rotation, miroir, deplacement) puis validez.

Les fichiers trop anciens sont mis a jour avec le DataFixer de Minecraft (d'apres leur DataVersion) ; les blocs
inconnus sont ignores et signales. Limites (taille, volume, nombre de blocs) : config/guildy/builds.json5.
Un fichier valide pour un chantier est copie dans <monde>/guildy/builds/ : le modifier ou le supprimer ici ne
change plus le chantier.
