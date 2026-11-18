# LOCAL — Session > Annoter : barre de création puis d'édition (v176f F1)

## Ce qui est fait

- **Création / édition séparées** (`DrawTools.kt`) : la rangée du haut ne contient que les outils de
  création (Poser un symbole, Forme, Ligne, Polygone, Annotation, Commentaire) puis Sélection et
  l'historique (Annuler / Refaire). Les actions d'édition n'apparaissent que quand un objet est
  sélectionné.
- **Barre d'édition au toucher** : toucher une forme sur la carte (outil Sélection, marqueur ou liste
  « Objets du relevé ») ou finir un tracé affiche `SelectedShapeBar` — nom de l'objet + 5 actions
  icône + texte court : Supprimer, Modifier, Déplacer, Dupliquer, Fermer.
  - *Supprimer* : suppression logique (`deleted = true`, journal append-only, fichier de session
    intact) ; « Annuler » la restaure.
  - *Modifier* : panneau d'édition (taille, style, libellé, commentaire).
  - *Déplacer* : pas de 10 cm / 50 cm sur N/E + « À la croix ».
  - *Dupliquer* : copie décalée qui prend la sélection.
  - *Fermer* : replie la barre.
- **« Se déplacer » retiré** : le pan à deux doigts (et à un doigt hors outil de tracé) suffisait ;
  l'outil par défaut devient **Sélection**.
- **« Poser » → « Poser un symbole »** avec ligne d'aide.
- **Refaire** : `SessionController.redo()` (pile redo vidée par toute nouvelle action), bouton
  visible seulement après un Annuler. Logique extraite dans `core` : `EditHistory.undoRecord` /
  `redoRecord` (purs, testés JVM — `EditHistoryTest`). Les chaînes de zones restent des `ZoneEvent`.
- `picked` reste renseigné sur chaque tap en Annoter : « Déplacer au point touché » du panneau relevé
  reste utilisable.
- Flow de spécification : `.maestro/build199/b199_annoter_barre.yaml`.

## testTags

| testTag | rôle |
|---|---|
| `session_annoter_poser_symbole` | bouton « Poser un symbole » (ex-« Poser ») |
| `session_annoter_aide` | ligne d'aide sous « Poser un symbole » |
| `session_annoter_barre` | barre horizontale d'édition (visible seulement si objet sélectionné) |
| `session_annoter_supprimer` / `_modifier` / `_deplacer` / `_dupliquer` / `_fermer` | actions de la barre |
| `session_annoter_annuler` | Annuler (inchangé) — restaure après une suppression |
| `session_annoter_refaire` | Refaire (visible après un Annuler) |

Conservés : `session_annoter_shape|line|polygon|annotation|comment|select`, `session_annoter_forme_*`
(rangés : agrandir/réduire/style/texte → « Modifier » ; nudges/à la croix → « Déplacer »).
Retirés : `session_annoter_move` (outil), `session_annoter_forme_supprimer`, `session_annoter_forme_dupliquer`
(remplacés par les actions de la barre).

## Ce qui reste manuel / limites

- Tout vérifier sur l'appareil : flow `b199_annoter_barre` à jouer sous Maestro (spécification,
  non exécuté ici — pas de build Gradle ni d'émulateur dans cette passe).
- La sélection au toucher sur la carte utilise `Shapes.nearest` (3 m max) : les formes multi-points
  (lignes, polygones) n'ont pas de marqueur cliquable, la proximité du tap décide.
- La barre d'édition ne couvre que les **formes/annotations** ; un relevé touché garde son panneau
  existant (Déplacer / Détails / Fermer / Supprimer dans Détails).
- « Déplacer » = pas + « À la croix » ; pas de glisser-déposer au doigt pour les formes (existant
  seulement pour les relevés).
- `Refaire` ne survit pas à une nouvelle action (comportement standard).
- Onglet « Annoter » non renommé (hors périmètre : choix « Remarque métier » / « Défauts » en attente).
