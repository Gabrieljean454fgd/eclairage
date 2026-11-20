# Recaler sur 2 repères (reprise de session) — v176f F3

Base : `etape-3-v176d-synchro-reelle` (97b2ae6) + 4872f76. Branche `petit/recaler-2-reperes`.
Utilisateur : électricien éclairagiste sur le terrain.

## Écrans touchés

- **Reprise de session** (bandeau `session_reprise_bandeau`, visible quand la position du téléphone est perdue à la
  fermeture ou qu'un seul point de repositionnement est pris) : nouveau bouton « Recaler sur 2 repères »
  (`session_recaler_2_reperes`), affiché dès qu'il reste **au moins 2 repères** de la session précédente.
- **Session > Calibrer** : le bouton arme un panneau guidé (`session_recal2_panneau`, consigne `session_recal2_consigne`,
  `session_recal2_quitter`). Les menus **Calibrer** et **Recaler** ne sont **pas** fusionnés (Sébastien n'a pas tranché) :
  le recalage sur 2 repères vit dans le menu Calibrer, l'action « Recaler » existante est inchangée.
- **Session > carte** : les repères des sessions précédentes (`SessionController.previousControlRows()`) sont dessinés
  en **pointillé bleu « à confirmer »** (`MapPoint.dashed`), avec la légende `session_reperes_a_confirmer`.

## Parcours

1. À la reprise, les anciens repères apparaissent en pointillé « à confirmer ». Le bouton « Recaler sur 2 repères »
   ouvre le panneau guidé dans Session > Calibrer.
2. L'ordre de visée est **indiqué** : la paire de repères la plus écartée d'abord (meilleure condition pour la rotation),
   puis les autres en contrôle (`TwoMarkerRecal.suggestedOrder`).
3. Pour chaque repère : viser la marque réelle **au réticule** (point visé sur un plan détecté, comme le calibrage
   multi-points v176c ; pose du téléphone en poses simulées), puis toucher son pointillé sur la carte.
   - 1re visée : enregistrée (`ControlPointEvent` CALIBRATION + ancre ARCore), en attente.
   - 2e visée : la transformation plan session → site ENU (rotation + translation, échelle fixe) est estimée par
     `TwoMarkerRecal.estimate` (core, fonction pure), journalisée en `CalibrationEvent` (méthode MULTI_POINT, notes
     « recalage sur 2 repères »), et la solution `TWO_MARKERS` alimente le tableau/la qualité existants.
   - Repères suivants : `recal2Control` mesure l'écart et journalise un `ControlPointEvent` `independentControl`
     (hors calcul, cohérent avec le contrôle du 5e point).
4. Garde-fous : même repère reviisé refusé (< 1 m), paire contradictoire refusée (`ControlPointCheck.contradiction`),
   repères à moins de 0,5 m → pas de rotation (la visée reste au tableau, message d'aide vers le calibrage avec cap).

## Fonction pure (core)

`core/src/main/kotlin/app/lumisite/core/position/TwoMarkerRecal.kt` :
- `estimate(a, b)` : transformation rigide (rotation entre les directions visée/plan, pivot au milieu des deux points —
  un écart d'échelle AR/carte est partagé sur les deux repères) + résidus ; `null` si séparation < 0,5 m.
- `controlResidualM(t, o)` : écart du 3e repère (hors calcul).
- `suggestedOrder(targets)` : paire la plus écartée d'abord, puis les autres.

Tests JVM : `core/src/test/kotlin/app/lumisite/core/Recalage2ReperesTest.kt` — transformation retrouvée sur vérité
connue, cas alignés (translation pure), résidu de contrôle > 0,5 m, repères trop proches, ordre conseillé.

## Cible imprimée (ARCore Augmented Images) — point ouvert, non bloquant

Option (désactivée par défaut) : une cible imprimée collée au mur dès le premier calibrage, reconnue hors ligne par
**Augmented Images** pour faciliter les reprises suivantes. **Constat** : `ArTracker` ne branche aucune base d'images
augmentées (`AugmentedImageDatabase`) à ce jour — le support n'est **pas** câblé ; cette tâche ne le bloque pas.
Reste à faire pour l'activer : générer/embarquer une image de référence, configurer la session ARCore
(`Config.addAugmentedImageDatabase`, hors ligne, sans réseau), décider de l'endroit où l'option vit dans Réglages.
**Pas de Cloud Anchors** : réseau obligatoire et données visuelles envoyées chez Google — exclu (doc SYNCHRO/DECISIONS).

## GPS

Le GPS ne sert qu'à l'extérieur : il n'intervient **pas** dans le recalage sur repères (ni dans la visée, ni dans la
transformation, ni dans le contrôle). Il reste indicatif (`session_carte_source`, classification D7 inchangée). Le
panneau guidé le rappelle à l'utilisateur.

## Acceptation visée (à valider sur le terrain)

Après recalage sur 2 repères dans un **bureau éclairé**, l'écart mesuré au 3e repère de contrôle est **< 0,5 m**
(seuil `CalibrationQuality.ALERT_M` ; au-delà, le message « au-delà de 0,5 m, recalage à vérifier » s'affiche et la
qualité passe en « moyen » au moins).

## Vérification

- Tests JVM (relecteur) : `./gradlew :core:test` → `Recalage2ReperesTest`.
- Flow Maestro de spécification : `.maestro/build199/b199_reprise_reperes.yaml` — session avec repères, terminer,
  reprendre (sans `clearState` supplémentaire), repères « à confirmer » visibles + bouton visible + panneau guidé ouvert.
- Non compilé ni exécuté ici (pas de build Gradle ni d'émulateur dans cette tâche) — vérification par le relecteur.

## Hors périmètre (inchangé)

Rectangle du Nouveau site, catalogue, synchro (Nearby/audio), menus AR hors visée nécessaire aux repères.
Fichiers de test verrouillés non touchés.
