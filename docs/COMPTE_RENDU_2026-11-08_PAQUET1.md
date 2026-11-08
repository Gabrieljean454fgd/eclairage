# Compte rendu — relance v6, paquet 1 : anomalies terrain du 08/11 (v161, Pixel 7)

Les points ci-dessous sont des **anomalies constatées sur le terrain par Sébastien, à confirmer**. Aucun n'est qualifié de « régression » : il faudrait pour cela qu'un test montre que le point fonctionnait en v148 et ne fonctionne plus en v161.

Conditions de test (aucun test sur le Pixel 7 physique) :
- émulateur Pixel 7, API 34, Google APIs x86_64 ;
- poses AR **simulées** (`simuler_pose_ar`, APK de débogage). ARCore réel, perte de suivi `INSUFFICIENT_FEATURES` et dérive réelle ne sont pas reproductibles ainsi ;
- APK des pré-releases : `lumisite-etape3-v161-77717bc.apk` (v161) et `lumisite-etape3-v148-b5d86ea.apk` (v148), puis APK de ce paquet ;
- tests JVM (`./gradlew :core:test`) sur des scènes synthétiques reconstruites à partir des valeurs de l'addendum (aucun fichier client dans le dépôt).

## Tableau par anomalie

| # | Anomalie | Reproduit ? | Versions testées | Cause (preuve) | Correction | Test de non-retour |
|---|---|---|---|---|---|---|
| S1 | Scène 1 : 4 points AR sur le même point carte, aucune calibration | **Oui : anomalie confirmée**, présente en v148 **et** en v161, donc pas une régression | v148, v161, paquet | L'UI acceptait un 2e point sur le même point carte : `SessionController.addControlPoint` (v161) n'avait aucun contrôle de distance carte. Preuve Maestro sur v161 : après le 2e appui, « Valider le point 3 » s'affiche avec le message « Point enregistré. Ajoutez un 2e point à plus de 2 m… ». Avec 4 cibles identiques, la dispersion carte est nulle, donc `CalibrationSolver.solve` renvoie `null` (`FieldAnomaliesTest.scene1SameMapPointGivesNoCalibration`). | Refus « Point carte déjà utilisé » (`ControlPointCheck.refusal`, `MIN_MAP_SEPARATION_M = 1 m`) | `scenario_v6_meme_point_carte.yaml` : échoue en v161 et en v148, passe sur le paquet. Test JVM scène 1. |
| 1 | Après 4 points, plus de position en temps réel ni de trace | **Oui, dans les conditions de la scène 1** (même point carte) : aucune transformation, donc pas de position ni de trace. **Non reproduit** avec 2 points distincts : position du téléphone et trace présentes en v161, avant et après `adb install -r` (`maj/v6_avant` et `v6_apres`) | v161, paquet ; v148 jusqu'à l'écran Site | Conséquence de S1 : sans `controlSolution`, l'écran affiche « Position site : non calibrée » (v161, `SessionScreen.kt` l. 418) et `TrajectoryCorrector` n'a aucun segment à appliquer | Celle de S1 | Flows S1 et `maj/v6_*` (CI) |
| 2 | « Enregistrer le relevé » ne fait rien, pas de photo | **Oui, dans les conditions de la scène 1** (cause déduite du code, pas rejouée en UI, car le paquet refuse désormais l'état de la scène 1). **Non reproduit** avec un calibrage valide : relevé et photo enregistrés en v161 | v161, paquet | v161 `SessionController.survey` l. 614 : sans calibration, `est == null` provoque `fail(NO_POSITION)` (« Suivi ou calibration manquant… »), sans photo. Le message tient sur 2 lignes au-dessus de la barre et peut passer inaperçu. Si « Position carte » est cochée sans point touché, le bouton reste grisé (v161, `SessionScreen.kt` l. 583 `enabled = family != null && (!useMap \|\| hasPick)`). | Celle de S1 (l'état sans calibration ne peut plus être créé par un point carte réutilisé) | Flows S1 et `maj/v6_releve.yaml` |
| 3 | Après redémarrage : calibrage et dernier bloc absents, perte de données ? | **Non reproduit** : en v161, après arrêt brutal puis `adb install -r`, le bandeau de reprise, le relevé et les points de calibrage précédents sont affichés (« 1 relevé(s), 2 point(s) de calibrage de la session précédente »). Aucune perte de données. Les 2 761 poses de la scène 1 confirment que rien n'a été effacé. En v148, après réinstallation, l'écran Site montre « Relevés : 1 » et la session, mais le flow s'arrête là (carte de session sans testTag `site_session_reprendre` en v148) | v161, v148 (partiel), paquet | — | Message de reprise clarifié : « Position du téléphone perdue à la fermeture… » | `maj/v6_apres.yaml` (CI), `scenario_p0_reprise_session.yaml` |
| 4 | « Ça casse à chaque réinstallation » | **Non reproduit** (v161 : réinstallation par-dessus puis nouveau calibrage et relevé OK). L'explication la plus probable est S1 : la scène 1 était inutilisable dès le calibrage, quelle que soit la réinstallation. Ce n'est pas prouvé sur le téléphone. | v161, paquet | — | — | `maj/v6_*` en CI à chaque push |
| S2 / 5 | Scène 2 : bascule de recalage (+63,59° → −38,88°, résidu 0,96 → 7,25 m), « calibrage inversé » | **Oui (JVM) : anomalie confirmée.** Pas d'inversion de signe ni d'axes : avec des points cohérents, la rotation et le sens sont corrects pour 2, 3, 4 et 6 points. La bascule vient d'un **mauvais appariement** : une paire « téléphone déplacé de 5 cm, cartes à 9,77 m » fait basculer la rotation selon le nombre et l'âge des points. | JVM sur le code v161 (avant correction) et sur le paquet. Non testé sur v148 (même solveur) | `CalibrationSolver.solve` ajustait toutes les paires sans contrôle de cohérence des distances. Le vieillissement à 300 s donne plus de poids aux 2 points récents, dont la paire fautive (`FieldAnomaliesTest.scene2ContradictoryPairFlipsRotation`) | Refus à la saisie et rejet dans le solveur des correspondances contradictoires (`ControlPointCheck.contradiction`) | `FieldAnomaliesTest`, `RecalageMultiPointTest` |
| 6 | Forte dérive le long d'un mur, corrigée seulement au 4e point | **Partiellement** : la part due au mauvais appariement est reproduite (S2). La dérive ARCore elle-même (`INSUFFICIENT_FEATURES`) n'est **pas reproductible** sur l'émulateur. | JVM | Avec 2 points, l'erreur moyenne aux points de contrôle est de 0,30 m ; à partir de 3 points, elle passe à 0,12–0,14 m (`RecalageMultiPointTest`, bruit de 0,1 m). Un calibrage à 2 points est donc plus sensible. | Conseil d'au moins 3 points (idéalement 4 à 6) + qualité affichée + point de contrôle hors calcul | `RecalageMultiPointTest` |
| E | Export : segments ouverts exclus, chemin photo GeoJSON faux, WAV absents | **Oui (code + test JVM)** | code v161 | v161 `ResumableExport` : `.filter { it.stream == stream && !it.open }` excluait les segments ouverts ; `photos[].path` restait le chemin interne de la session ; aucun `sync_*.wav` n'était ajouté | Voir « Corrections » | `ResumableExportTest` |

## Corrections livrées

- **Calibrage** (`core/.../position/CalibrationChecks.kt`, `Positioning.kt`, `SessionController.addControlPoint`) :
  - refus d'un point de calibrage dont le point carte est à moins de 1 m d'un point déjà utilisé (« Point carte déjà utilisé… ») ;
  - refus d'une correspondance contradictoire : distance carte incompatible avec le déplacement du téléphone, avec une tolérance de 1,5 m, 30 % et 5 cm/s de dérive entre les deux points ;
  - le solveur écarte aussi ces paires dans les journaux existants (en gardant au moins 2 points), donc une session déjà enregistrée est recalculée sans la paire fautive. Le journal n'est pas modifié (append-only).
- **Session > Calibrer** :
  - conseil « au moins 3 points, idéalement 4 à 6 aux extrémités du bâtiment, jamais alignés, + 1 point de contrôle » ;
  - « Qualité : bon / moyen / insuffisant » avec ses raisons (résidu, nombre de points, alignement). Au-delà de 1 m de résidu, la qualité est « insuffisant » et la mention « imprécis » s'affiche, jamais « précis » ;
  - bouton « Contrôler ici (hors calcul) » : il mesure l'écart à un point connu sans l'utiliser dans le calcul.
  - Le conseil et la qualité sont placés **sous** le tableau, pour que « Valider le point » reste à la même place sur le petit écran du Pixel 7.
- **Message de reprise** : « Position du téléphone perdue à la fermeture. Touchez 2 points déjà calibrés pour reprendre. » Le bouton `session_reprise_positionner` ouvre toujours Calibrer.
- **Terminologie** :
  - « Position du téléphone (repère site) » à la place de « Position estimée » ;
  - « Point visé : … » pour la cible AR ;
  - le message de pose AR bloquée distingue désormais la position du téléphone du point visé.
- **Export** (`core/.../export/ResumableExport.kt`) :
  - segments ouverts exportés jusqu'à la dernière ligne complète, et signalés dans le manifeste ;
  - chemins photo et audio du GeoJSON = `media/<nom>` dans le ZIP ;
  - fichiers `sync_*.wav` exportés ;
  - sidecar `media/<photo>.json` avec les tags métier ;
  - les données source ne sont pas modifiées. Voir `docs/GEOJSON_EXPORT.md`.

## Tests ajoutés

- JVM :
  - `FieldAnomaliesTest` (scènes 1 et 2) ;
  - `RecalageMultiPointTest` : 2, 3, 4 et 6 points sur un bâtiment non carré, rotation de 30° et translation, bruit de 0,1 m, point éloigné et opposé, points de contrôle hors calcul, sens du déplacement, points quasi alignés, qualité ;
  - `ResumableExportTest` : segment ouvert, WAV, sidecar, chemin photo.
- Maestro :
  - `scenario_v6_meme_point_carte.yaml` (« Calibrage meme point carte refuse ») ;
  - `maj/v6_avant.yaml` + `maj/v6_apres.yaml`, enchaînés en CI dans `.github/scripts/maestro-ci.sh` : calibrage 2 points, position du téléphone, relevé + photo, `am force-stop`, `adb install -r`, reprise (relevé et calibrage précédents affichés), nouveau calibrage, relevé.

## Chauffe (mesure rapide, émulateur, pas le Pixel 7)

- Code vérifié :
  - `SensorRecorder` est la seule source capteurs : IMU à 100 Hz par défaut, magnétomètre à 10 Hz (`ImuRates`) ;
  - `SessionScreen` `ON_PAUSE` appelle `glView.onPause()` puis `SessionController.pauseCapture()`, qui arrête capteurs, GPS et session ARCore (`ar.pause()`) ;
  - `ArTracker.onDrawFrame` ne fait rien si `rendering = false`.
- Mesure (`adb shell top`, émulateur x86_64, poses simulées) :
  - Session ouverte au premier plan : 76 à 100 % d'un cœur. Rendu logiciel de l'émulateur, carte osmdroid et poses simulées : ce chiffre n'est pas représentatif du téléphone.
  - Application en arrière-plan (HOME) : 4 à 7 % d'un cœur, aucune connexion capteur active (`dumpsys sensorservice`). Le fil de débogage `lumisite-poses-simulees` (0,6 %) continue en arrière-plan ; il n'existe que dans l'APK de débogage avec `simuler_pose_ar`.
- Aucune boucle inutile trouvée dans le code de production. Le profil CPU réel (Android Studio Profiler ou `top` sur le Pixel 7) reste à faire sur le téléphone.

## Reste à faire / questions ouvertes (non tranchées)

- Revue automatique PR #20 : un recalage légitime après un saut ARCore brutal (perte de suivi) peut être refusé comme « correspondance contradictoire ». Proposition, non tranchée : garder le refus par défaut et ajouter un « Recaler quand même » journalisé, ou ignorer les points antérieurs à une perte de suivi.

- Valider sur le Pixel 7 physique : ARCore réel, pertes `INSUFFICIENT_FEATURES` et photo avec l'appli caméra externe.
- Anomalie 6 (dérive le long d'un mur) : non mesurable sur émulateur. La part due au mauvais appariement est corrigée. La dérive ARCore propre reste à mesurer sur le terrain avec le point de contrôle.
- Le vieillissement exponentiel des points sur 300 s n'a pas été modifié. Il amplifie l'erreur quand un point fautif est récent. Le changer est une décision de réglage, à valider avec des données terrain.
- Repli « point visé estimé à 50 cm » (`Aim.kt`, `ESTIMATED`) : toujours présent. Sa suppression est prévue au LOT 2.1, pas dans ce paquet.
- LOT 2 (positionnement) : non commencé.
