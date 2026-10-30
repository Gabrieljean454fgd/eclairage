# Format d'export `lumisite.session` v0.1.0

Un export est un dossier (ou le ZIP de ce dossier) :

```
session.json                 manifeste versionné : site, OSM, CRS, session, appareil, bases de temps, événements, index des fichiers
raw/poses.ndjson             poses brutes du suivi de plateforme (ARCore), repère de session AR, jamais modifiées
raw/imu.ndjson               échantillons capteurs bruts (accéléro, gyro, magnéto, rotation vector, pression, lumière…)
raw/gps.ndjson               positions GNSS/réseau avec précision
raw/gps_status.ndjson        statut des fournisseurs (désactivé, pas de fix, satellites…)
raw/events.ndjson            copie du journal d'événements tel qu'enregistré
raw/sync_*.wav               (optionnel) enregistrement micro d'une synchro audio
derived/trajectory_site.ndjson   trajectoire recalculée dans le repère site (ENU), avec l'événement de transformation utilisé
derived/survey_points.ndjson     position calculée de chaque relevé + indications DXF
```

Schéma JSON du manifeste : [`session.schema.json`](session.schema.json). Exemple **synthétique** (valeurs inventées, généré par
`./gradlew :core:generateExample`) : [`example/session-example/`](example/session-example) et `example/session-example.zip`.

## Brut, corrigé, calculé

| Catégorie | Où | Nature |
|---|---|---|
| Brut | `raw/*.ndjson`, `files[].category = "raw"` | Ce que la plateforme a fourni, avec les horodatages d'origine. Jamais réécrit. |
| Corrections | événements `calibration` et `relocalization` dans `events` | Saisie utilisateur (point de référence, cap, distance) + pose brute au moment de la saisie + transformation avant/après. Append-only. |
| Calculé | `derived/*`, `derivedSummary`, champs `transform`, `estimatedBefore`, `estimatedSite` | Recalculable à partir du brut et des corrections. `files[].category = "derived"`. |

Chaque fichier est listé dans `files[]` avec nombre d'enregistrements, taille et SHA-256 ; `SessionImporter.load()` vérifie ces valeurs à la relecture.
Une ligne NDJSON tronquée ou illisible (arrêt brutal pendant l'écriture) n'est jamais réécrite : à la reprise, elle est simplement terminée par un saut de ligne pour que les enregistrements suivants restent lisibles. À l'export, ses numéros de ligne sont déclarés dans `integrity` (clé = chemin dans l'export, ex. `raw/imu.ndjson`). L'export est calculé sur un instantané des lignes complètes : nombres d'enregistrements, hashes et fichiers dérivés décrivent exactement les octets exportés, même si l'enregistrement continue pendant l'export.

## Horodatage (`Stamp`)

```json
{"utcEpochMs": 1700000004000, "monotonicNs": 9000000000, "timeBaseId": "tb-1", "sensorNs": 8998800000}
```

- `monotonicNs` : `SystemClock.elapsedRealtimeNanos()` (inclut la veille, repart à zéro au redémarrage). Base de référence pour les durées et l'ordre.
- `utcEpochMs` : heure civile `System.currentTimeMillis()` au moment de l'enregistrement. Peut sauter (NTP, réglage manuel).
- `sensorNs` : horodatage natif quand il existe (`SensorEvent.timestamp`, `Frame.timestamp` ARCore, `Location.elapsedRealtimeNanos`).
- `timeBaseId` : renvoie à `session.timeBases[]`, qui donne la paire de référence (UTC, monotone) capturée ensemble. Une reprise de session
  (redémarrage de l'app/appareil) ajoute une base et un événement `time_base_added` ; on ne compare pas des `monotonicNs` de bases différentes.

## Repères et coordonnées

- Géographique : WGS84, EPSG:4326 (`lat`, `lon` en degrés). Données OSM dans ce système.
- Site (depuis le lot 3) : `bbox` = rectangle choisi (`south`, `west`, `north`, `east`), conservé même sans OSM.
  `osm.features[]` peut porter `holes` (anneaux intérieurs, cours) et `outers` (anneaux extérieurs supplémentaires).
  Réponses brutes datées de l'API OSM dans `sites/<id>/osm_source.json` (`retrievedUtcMs`, `endpoint`, `responses[{bbox, body}]`).
- `SITE_ENU` : plan tangent local à l'origine du site (est, nord, haut ; mètres). Définition PROJ dans `site.crs[].definition`.
- `AR_SESSION` : repère monde ARCore de la session (droit, +Y vers le haut, mètres, origine et cap arbitraires à chaque démarrage).
  Le plan horizontal AR est projeté par `(x, −z)` puis converti en `SITE_ENU` par une similarité 2D (`scale`, `rotationRad`, `tx`, `ty`) :
  `E = s·(cosθ·x' − sinθ·y') + tx`, `N = s·(sinθ·x' + cosθ·y') + ty`, avec `(x', y') = (x, −z)`. La hauteur `u` est la hauteur AR brute (non calée).
- Poses : `p = [x, y, z]` (m), `q = [x, y, z, w]` (quaternion caméra, convention ARCore).

## Événements (`events[]`, discriminant `type`)

`session_started`, `session_stopped`, `time_base_added`, `tracking_state`, `calibration`, `relocalization`, `sync`, `survey`.
Un consommateur doit ignorer les champs et types qu'il ne connaît pas (versions mineures futures).

- `session_stopped` : `reason` (chaîne, requise ; `user` quand la session est terminée par « Terminer la session », le bouton
  Retour ou le geste retour). Écrit après l'arrêt du rendu caméra et la fermeture d'ARCore, juste avant de quitter l'écran.
  Une session sans `session_stopped` (arrêt brutal) reste lisible : à la reprise, un `time_base_added` est ajouté.
- `calibration` : `method` (`ONE_POINT_HEADING`, `TWO_POINTS`, `TWO_POINTS_KNOWN_DISTANCE`), `observations` (pose brute + point de référence
  avec source et incertitude), cap saisi et sa source, distance connue, `transform` résultante, `measuredScale`, `scaleFixed`, `residualM`.
- `relocalization` : observation, `estimatedBefore` / `correctedAfter` / `deltaM`, `transformBefore` / `transformAfter`, modèle de correction.
  S'applique aux poses **postérieures** ; les poses antérieures gardent la transformation qui était active (pas de lissage rétroactif dans v0.1).
- `sync` : `syncCode` commun entre appareils, méthode (`MANUAL_TAP`, `AUDIO_CHIRP`), rôle, `eventMonotonicNs` estimé, détails de détection audio
  (score, plancher de bruit, paire `AudioTimestamp`). Ni la propagation acoustique (~2,9 ms/m) ni la latence d'entrée audio ne sont compensées.
- `survey` : `kind`, `note`, pose brute, état de suivi, source de position (`AR_POSE`, `MANUAL_MAP`, `MANUAL_EDIT` ; `NONE` dans les
  anciens fichiers seulement), dernière position GPS, `payloadSchema` + `payload` (objet JSON libre), `attachments`, `dxf`.
  - Depuis le lot 2 : `kind` = nom de bloc canonique du catalogue (`catalog/equipment_catalog.json`, ex. `spot`),
    `payloadSchema = "lumisite-equipment/1"`, `payload = { catalog_version, block_name, details? }`,
    `dxf = { layer: "RES - Elec", blockName: <nom de bloc> }`. Plus aucun `LUMI_<KIND>_GENERIC` n'est écrit.
  - Anciennes sessions : `LUMINAIRE`/`AUTRE` → « à classer », `OBSERVATION` → annotation texte, `MESURE` → hauteur ou texte
    (lus, jamais réécrits).
  - Édition append-only (champs facultatifs) : `supersedes` = id du relevé remplacé ; `deleted: true` le supprime. L'état courant
    = relevés non remplacés et non supprimés (`SurveyPositions.current`).
  - Position : une seule fonction (`SurveyPositions.resolve`) pour l'écran, la page du site et `derived/survey_points.ndjson`
    (manuelle, sinon pose × transformation active à l'instant du relevé ; un recalage ne déplace que les relevés postérieurs).
    `estimatedSite` reste la valeur historique écrite au moment du relevé.

## Préparation DXF (aucun DXF généré)

`dxfPreparation` décrit comment produire un DXF plus tard : unités `m`, repère plan `SITE_ENU`, `DXF X = E`, `DXF Y = N`, fichiers sources des points et
de la trajectoire. Chaque relevé peut porter `dxf.layer`, `dxf.blockName`, `dxf.rotationDeg`, `dxf.attributes` (futurs attributs de bloc).
Si un plan architectural est importé plus tard, `site.plans[].planToSite` donnera la transformation plan → `SITE_ENU` à inverser pour exporter dans le
repère du plan AutoCAD.
