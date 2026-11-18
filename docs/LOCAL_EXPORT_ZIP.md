# Export local : un seul ZIP, IMU compressé, repères dans le GeoJSON (v176f F2)

Écran concerné : **Session > Export**. Branche `petit/export-zip`, base `etape-3-v176d-synchreelle` (97b2ae6) + 4872f76.

## Ce qui change pour l'utilisateur

- **Un seul fichier ZIP** par export de session (déjà le cas côté `ExportWorker`/WorkManager ; le panneau affiche désormais
  « Un seul fichier ZIP — Fichier : `<session>.zip` », `testTag session_export_zip`). Contenu : `releves.geojson`,
  `session.json`, `raw/`, `derived/`, `media/`, `LISEZMOI.txt`.
- **`raw/imu.csv.gz`** remplace `raw/imu.ndjson` : même échantillons, même ordre de journal, en CSV gzipé
  (~22× plus petit : 86 Mo → 4,1 Mo sur la capture du 15/11, voir `docs/PROPOSITIONS.md` §2.1). Le journal sur
  l'appareil (`seg/`) est inchangé.
- **`releves.geojson`** contient en plus les **repères de calibrage** (`control_point`) nommés — visibles dans JOSM
  et QGIS (point + style « flag » orange) — et des **propriétés plates** `name` + `lumisite:*` sur toutes les Features.

## Format `raw/imu.csv.gz` (`lumisite-imu-csv/1`)

- Lignes `#` = commentaires : `# lumisite-imu-csv/1`, puis `# time_base=<id> t_mono_ns_initial=<ns>` à chaque base
  de temps (après un redémarrage, `dt_us` repart de 0).
- Une ligne d'en-tête de colonnes : `dt_us,sensor_dt_us,type,x,y,z,w`.
- `dt_us` : µs depuis `t_mono_ns_initial` de la base ; `sensor_dt_us` : timestamp capteur sur la même origine
  (vide si absent) ; `type` : `imu.accel` / `imu.gyro` / `imu.mag` / `imu.rotation` / `imu.other.<Sensor.TYPE_*>` ;
  `x,y,z` = 3 premières composantes, `w` = les suivantes séparées par `;` (quaternion des vecteurs de rotation).
- Valeurs arrondies à 6 décimales, formatage déterministe. `sensor_name` et `accuracy` ne sont pas dans le CSV
  (une réimportation donne `sensorName=""`, `accuracy=-1`, UTC dérivée des bases de temps).
- Lisible par pandas (`comment='#'`), tableur après décompression.

## Repères `control_point` dans `releves.geojson`

Une Feature `Point` par point **actif** du journal (`id = "cp-<event_id>"`), geometry WGS84, style JOSM
`marker-symbol=flag`, `marker-color=#FF8C00`. Propriétés :

- `name` = étiquette du repère (« Calibrage 1 », « Recalage 2 », « Contrôle »…), `lumisite:kind=control_point`,
  `lumisite:role` = `CALIBRATION`/`RELOCALIZATION`, `lumisite:methode` = source du point (`MAP_TAP`…).
- `lumisite:statut` : mêmes mots que le tableau de l'app — `retenu` / `rejeté` / `ancre` / `contrôle`
  (5e point indépendant, hors calcul) / `en attente` / `session précédente`.
- `lumisite:residu_m` = écart entre le repère et la position calculée ; `accuracy.sigma_xy_m` = ce résidu ;
  `lumisite:qualite` alignée sur le résidu : **bon ≤ 0,5 m, moyen ≤ 1 m, insuffisant au-delà** (seuils
  `CalibrationQuality`). Repères d'un épisode AR précédent (après reprise) : « session précédente », sans résidu.

### Approximation assumée

L'app recalcule le solve en direct avec les **ancres ARCore affinées** depuis l'enregistrement (non journalisées).
L'export recalcule une fois depuis la pose/visée **enregistrée** : `nowNs` = dernier horodatage de point de
l'épisode, rotation de repli = dernière transformation de l'épisode. Écarts possibles de quelques centimètres sur
des points forcés ou des géométries dégénérées ; les résidus restent cohérents avec le journal.

## Propriétés plates (toutes les Features)

`name` (note du relevé sinon famille ; `label` pour les formes), `lumisite:kind`, `lumisite:code` (bloc catalogue
en attendant les codes `LS_*`), `lumisite:methode`, `lumisite:qualite`, `lumisite:hauteur`, `lumisite:residu_m`.
Pour un équipement positionné par AR, `accuracy.sigma_xy_m` = résidu RMS de la calibration qui l'a positionné.

## Acceptation (tests JVM `core/src/test/.../ExportZipTest.kt`)

- Export d'**environ 30 min** de capture (accéléro+gyro 100 Hz, mag 10 Hz, rotation 5 Hz, poses 10 Hz) :
  **< 20 Mo** — mesuré sur fixture, large marge (~5-7 Mo attendus).
- Le GeoJSON contient **le luminaire** (`lumisite:kind=equipment`, nommé) **et les repères nommés**.
- Gzip : le fichier se décompresse, en-tête + colonnes + `time_base` stables, arrondi 6 décimales vérifié.
- `SessionImporter.verify` / `load` acceptent `raw/imu.csv.gz` **et** l'ancien `raw/imu.ndjson`
  (exports de projet et archives plus anciennes). `FORMAT_VERSION` = `0.2.2`.

## Compatibilité

- `SessionExporter.exportToDir` (export de projet, `raw/<device>/<session>.zip`) **inchangé** : `raw/imu.ndjson`
  y est conservé (NDJSON complet, capteurs nommés et accuracy).
- `SessionImporter` lit les deux formats ; un export 0.2.2 ouvert par une ancienne app donne un problème
  « raw/imu.ndjson missing » sans bloquer le reste (poses, événements, GeoJSON restent lisibles).
- Aucun DXF dans l'appli ; AR, synchro, catalogue, Nouveau site et rectangle de sélection non touchés.

## Fichiers

- `core/.../export/ImuCsv.kt` (nouveau), `ControlPointFeatures.kt` (nouveau)
- `core/.../export/ResumableExport.kt`, `Export.kt` (version 0.2.2, verify/import IMU double format, GeoJSON)
- `app/.../ui/SessionScreen.kt` (`session_export_zip`, texte)
- Tests : `ExportZipTest.kt` (nouveau), `SegmentFormatsTest.kt` (nom d'entrée)
- `.maestro/build199/b199_export_zip_unique.yaml`, `docs/SESSION_FORMAT.md`, `docs/session.schema.json`,
  `docs/GEOJSON_EXPORT.md`, ce fichier.
