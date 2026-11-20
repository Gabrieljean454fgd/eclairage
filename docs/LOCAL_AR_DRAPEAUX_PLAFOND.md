# Retours build 199 — points 2c et 2f : drapeaux conservés et plafond estimé

Branche `petit/ar-drapeaux-plafond` (base `petit/inclinaison` = `97b2ae6` + `4872f76` + `65ba9a1`).
Écran concerné : **Session** (AR — `ArTracker.kt`, `ArOverlay.kt`, `SessionController.kt`, `SessionScreen.kt`).

## Fait

### 2c — Drapeaux perdus

- **(a) Ancres dessinées en PAUSED / dégradé** (`ArTracker.kt`, `onDrawFrame`) : les marques (drapeaux
  de calibrage, symboles luminaire) sont dessinées dès que l'état session n'est pas `STOPPED`, à leur
  dernière pose connue, **en gris** (0,55/0,55/0,55) quand le suivi caméra ou l'ancre n'est pas
  `TRACKING`. Couleurs normales sinon. `LineRenderer.draw` prend désormais un triplet
  `(kind, position, live)`. Les contours de plans restent limités à `TRACKING` (ils gèleraient aussi).
  Seul `STOPPED` (de l'ancre ou de la session) retire un marqueur.
- **(b) Ancres recréées à la reprise** (`SessionController.restoreAnchors`) : à la fin de `resolve()`,
  quand une transformation est produite alors qu'il n'y en avait pas (`before == null`) sur une session
  reprise (`TimeBaseAdded` présent — i.e. « calibrage rétabli » par Calibrer ou Recaler), une ancre est
  recréée **par repère actif** (`AnchorKind.FLAG`, position carte → AR, hauteur = sol détecté ou
  caméra − 1,4 m) et **par luminaire** (`AnchorKind.LUMINAIRE`, `Aim.toSession(site)` complet, u → y).
  Journal DiagLog `ANCRES`. Les groupes d'ancres (`AnchorGroups`) ne sont pas restaurés : ils se
  reforment au prochain relevé.
- **Fonction pure dans core** : `Similarity2D.invert` (`Geo.kt`) + `Aim.toSession` (`compute/Aim.kt`),
  inverse exacte de `toSite` (plan (x, −z) ↔ ENU (e, n), u ↔ y). Testée : aller-retour < 1 cm
  (en pratique < 1e-9 m).

### 2f — « Hauteur sous plafond » et plafond estimé

- **Modèle** : `Site.ceilingHeightM` (`Model.kt`, défaut `null` → les `site.json` antérieurs restent
  lisibles). Persisté via `Repository.saveSite` ; `SessionController.site` devient `var` (privée en
  écriture) pour refléter l'enregistrement.
- **Saisie / mesure** (Session > **Calibrer**) : champ « Hauteur (m) » + « Enregistrer la hauteur »
  (`setCeilingHeight`, limites 0,5–20 m, événement `stream_state site/CEILING_HEIGHT_M` journalisé) et
  « Mesurer en visant l'angle mur/plafond » (`measureCeilingHeight` : H = hauteur du point visé
  `AR_WALL`/`AR_CEILING` − niveau du sol détecté).
- **Plafond virtuel** (`ArTracker.updateAim`) : visée PLAFOND sans plan détecté → `Aim.resolve(…,
  virtualCeilingY = sol détecté (ou caméra − 1,4 m) + H)` ; la fonction pure d'intersection rayon /
  plan horizontal réutilise `Aim.intersect` (mêmes refus rasante / portée / derrière la caméra).
- **Jamais de repli silencieux** : source `AR_CEILING_ESTIMATED` (`Aim.SRC_CEILING_ESTIMATED`) —
  réticule jaune « Plafond estimé (hauteur sous plafond saisie) », `positionSource` journalisée sur le
  relevé, DiagLog `POSITION` à l'enregistrement. Sans hauteur saisie : « position à compléter »
  inchangé. Un plafond estimé n'alimente ni `lastObjectY` ni la mesure A–B.
- **Depth API** : non activée (`Config.DepthMode.DISABLED` inchangé).

## testTags ajoutés

| testTag | Écran | Rôle |
|---|---|---|
| `session_calibrer_hauteur_valeur` | Session > Calibrer | hauteur courante (« 2,60 m » / « non saisie… ») |
| `session_calibrer_hauteur_plafond` | Session > Calibrer | champ de saisie (m) |
| `session_calibrer_hauteur_mesurer` | Session > Calibrer | mesure en visant l'angle mur/plafond |
| `session_calibrer_hauteur_enregistrer` | Session > Calibrer | enregistre la hauteur (actif si valeur valide) |

## Flows Maestro

- `.maestro/build199/b199_ar_drapeaux_plafond.yaml` (spécification) : saisie 2,60 m dans Session >
  Calibrer, valeur affichée puis ré-affichée après fermeture/réouverture du panneau.

## Tests JVM (`core/src/test`)

- `ArDrapeauxPlafondTest.kt` : `invert`/`toSession` aller-retour < 1 cm ; visée plafond virtuel
  (`AR_CEILING_ESTIMATED`, hauteur exacte, plafond détecté prioritaire, jamais vers le bas, rasante
  refusée, hors portée → à compléter) ; `site.json` sans le champ toujours lisible.

## Reste manuel (téléphone)

- Pixel 7, Session : poser 4 drapeaux, masquer la caméra / tourner 180° (suivi dégradé) — les drapeaux
  restent visibles **en gris** ; retour au suivi : couleurs normales ; arrêt de la session AR : retirés.
- Session reprise + repositionnement (Calibrer/Recaler) : les drapeaux des repères actifs et des
  luminaires réapparaissent aux positions carte (hauteur des luminaires ≈ hauteur d'origine, le
  repère AR n'a pas de calibration verticale).
- Visée plafond lisse sans hauteur saisie : « position à compléter ». Après saisie (ou mesure en
  visant l'angle mur/plafond) : réticule jaune « Plafond estimé », relevé journalisé
  `positionSource = AR_CEILING_ESTIMATED`.
