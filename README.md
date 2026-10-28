# LumiSite — diagnostic d'éclairage de terrain (tranche 1)

Application Android native (tablette d'abord) pour parcourir un site, positionner des relevés sur un plan, les rattacher à une trame temporelle et exporter les données pour un traitement de bureau (génération DXF ultérieure, **non incluse** ici).

Cette tranche valide le noyau d'acquisition et de positionnement : modèle de session, horodatages, repères de coordonnées, journal local append-only, calibration et recalage non destructifs, synchronisation expérimentale et export versionné.

## Structure

| Module | Rôle | Dépendances |
|---|---|---|
| `core/` | Métier pur Kotlin/JVM : temps (`time`), coordonnées (`geo`), modèle (`model`), journal NDJSON (`journal`), calibration/recalage/trajectoire (`compute`), synchro (`sync`), export/import (`export`) | kotlinx.serialization uniquement, **aucune dépendance Android** |
| `app/` | Intégrations Android : ARCore (`capture/ArTracker`), IMU (`SensorRecorder`), GPS (`GpsRecorder`), chirp audio (`AudioSync`), OSM/Nominatim/Overpass (`data/OsmClient`), stockage (`data/Repository`), UI Compose (`ui/`), orchestration (`SessionController`) | ARCore 1.57, osmdroid 6.1.20, Jetpack Compose |
| `docs/` | Format d'export, schéma JSON, exemple, tests tablette | — |

## Prérequis

- JDK 17, Android SDK (platform 35, build-tools 35), `local.properties` avec `sdk.dir=...`.
- Aucune clé d'API ni service payant. Services publics utilisés (réseau optionnel) : tuiles OSM, Nominatim (recherche), Overpass (objets OSM). Usage ponctuel déclenché par l'utilisateur, User-Agent identifié, attribution « © OpenStreetMap contributors » affichée. Pour un usage intensif ou commercial, prévoir un serveur de tuiles / Nominatim / Overpass dédié (politiques d'usage OSMF).
- Tablette : Android 7.0+ (minSdk 24). Le suivi AR exige un appareil **certifié ARCore** (https://developers.google.com/ar/devices) ; sinon l'app fonctionne en positions manuelles (tap sur la carte).

## Commandes

```bash
./gradlew :core:test                 # tests automatisés (JVM)
./gradlew :app:assembleDebug         # APK : app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:generateExample      # régénère docs/example/session-example (données synthétiques)
```

Émulateur (facultatif ; ARCore n'y démarre pas, voir plus bas) :

```bash
sdkmanager "system-images;android-34;google_apis_playstore;x86_64" "emulator"
avdmanager create avd -n tab -k "system-images;android-34;google_apis_playstore;x86_64" -d pixel_tablet
emulator -avd tab -memory 2048 -cores 2
```

## Parcours dans l'app

1. **Accueil** → « Nouveau site » : recherche Nominatim ou coordonnées saisies ; « Récupérer » charge les objets OSM (bâtiments, lampadaires, entrées, indoor, chemins) dans un rayon donné ; sélection facultative d'un bâtiment. Un site peut être créé sans aucune donnée OSM. Un snapshot (source, date, IDs, tags, géométrie, CRS, licence ODbL) est figé dans le site.
2. **Site** → « Nouvelle session » : ouvre la caméra AR (si disponible), démarre IMU, GPS, journal.
3. **Session** : onglets *Calibrer* (1 point + cap, ou 2 points + distance connue), *Recaler* (point connu ; l'avant/après est conservé), *Relevé* (type, note, position AR ou tap carte), *Synchro* (TOP manuel par code partagé ou chirp audio expérimental, import de l'export d'un autre appareil), *Export* (ZIP + partage).
4. Une session interrompue (arrêt forcé, redémarrage) se rouvre depuis le site : une nouvelle base de temps est ajoutée, rien n'est réécrit.

## Choix techniques importants

- **Temps** : chaque mesure porte l'UTC civil, l'horloge monotone (`elapsedRealtimeNanos`) et, si disponible, le timestamp natif du capteur. Chaque `TimeBase` ancre une paire UTC/monotone ; deux bases monotones ne sont jamais comparées directement.
- **Coordonnées** : WGS84 (EPSG:4326) pour le géographique, plan local ENU (mètres) centré sur l'origine du site, repère monde ARCore projeté en `(x, -z)`. Calibration et recalage = similarités 2D (échelle, rotation, translation) stockées comme événements.
- **Non destructif** : flux bruts (`raw/`) en append-only ; calibrations, recalages et synchros sont des événements immuables ; la trajectoire corrigée (`derived/`) est recalculée à l'export à partir du brut.
- **IMU** : enregistrée brute (~50 Hz) pour post-traitement ; elle n'est **pas** utilisée comme source de position absolue. La position vient d'ARCore (visuel-inertiel), d'une saisie manuelle ou, à titre indicatif, du GPS.
- **Synchro** : l'écart entre appareils est estimé à partir d'événements communs (dispersion et dérive conservées). Le chirp audio (2–6 kHz, 60 ms) ne compense ni la propagation acoustique (~3 ms/m) ni les latences audio : c'est une estimation, pas une synchronisation exacte.
- **Images** : la caméra sert uniquement au suivi AR ; aucune image ni vidéo n'est enregistrée.

### Pour une future version iOS
- `core/` peut passer en Kotlin Multiplatform et être consommé depuis Swift : temps, géo, modèle, calcul et synchro n'utilisent que Kotlin standard et kotlinx.serialization ; seuls `journal/` et `export/` utilisent `java.io`/`java.util.zip` et seront à porter (okio ou équivalent).
- Équivalents à brancher : ARKit (`ARFrame.camera.transform`, `trackingState`) ↔ `ArTracker`, CoreMotion ↔ `SensorRecorder`, CoreLocation ↔ `GpsRecorder`, AVAudioEngine ↔ `AudioSync`, MapKit/MapLibre + tuiles OSM ↔ osmdroid. ARKit est droitier Y-up comme ARCore : la projection `(x, -z)` reste valable.
- Le format d'export est indépendant de la plateforme (l'appareil est décrit par `session.device` : fabricant, modèle, OS, support AR, capteurs).

### Pour la future génération DXF
- Le manifeste contient `dxfPreparation` : statut `NOT_GENERATED`, CRS plan `SITE_ENU` (origine et définition dans `site.crs`), unités (m), correspondance d'axes (DXF X = E, Y = N, Z = U), fichiers de points et de trajectoire à utiliser.
- Chaque relevé (`survey`) porte son `kind`, sa position, son orientation, l'état du suivi, sa source de position et un bloc `dxf` (`layer`, `blockName`, `attributes`) : de quoi générer un `INSERT` de bloc par relevé. Les blocs eux-mêmes ne sont pas définis et le DXF n'est pas généré dans cette tranche.
- Reprojection éventuelle vers un CRS national (ex. Lambert 93, EPSG:2154) à faire au bureau à partir de l'origine WGS84 du site.

## Export

Voir [`docs/SESSION_FORMAT.md`](docs/SESSION_FORMAT.md), le schéma [`docs/session.schema.json`](docs/session.schema.json) et l'exemple **synthétique** (valeurs inventées, générées par `ExampleGenerator`) dans [`docs/example/`](docs/example/).

## Ce qui a réellement été vérifié

| Niveau | Vérifié | Non vérifié |
|---|---|---|
| Tests automatisés JVM (`:core:test`) | 30 tests, 0 échec : horodatages et bases de temps, WGS84↔ENU, similarités 2D, calibration 1/2 points, recalage, trajectoire corrigée, journal append-only, ligne tronquée par un arrêt brutal puis reprise, export pendant que le journal est encore écrit, ligne illisible déclarée dans `integrity` (avertissement et non erreur), export/relecture/hashes/corruption, estimation de synchro et détection de chirp sur signal synthétique | — |
| Build | `:app:assembleDebug` réussi | Build release / signature |
| Exemple d'export | Validé contre `session.schema.json` (jsonschema Draft 2020-12), NDJSON relus | — |
| Émulateur Android 14 (API 34) x86_64, profil tablette | Installation de l'APK et d'ARCore, lancement, recherche Nominatim, chargement Overpass (408 objets), centrage de la carte sur le site, création de site, ouverture puis reprise de session (après arrêt forcé de l'émulateur), compteur IMU (capteurs simulés), relevé avec position manuelle sur la carte, TOP de synchro manuel, refus explicite de calibrer sans suivi AR, export ZIP avec relecture automatique (hashes et comptes OK, lignes tronquées par les arrêts forcés signalées en avertissement), feuille de partage Android, bascule en positions manuelles quand ARCore échoue | Suivi ARCore (la session ARCore échoue sur l'émulateur), GPS réel, micro/haut-parleur, synchro entre appareils |
| Tablette réelle | **Rien** | Tout : voir [`docs/TESTS_TABLETTE.md`](docs/TESTS_TABLETTE.md) |

## Limites connues

- Précision de trajectoire non mesurée. ARCore dérive sur les longues distances et dans les zones pauvres en texture ou mal éclairées (un comble pour un diagnostic d'éclairage nocturne) ; le recalage manuel est le garde-fou prévu.
- Le cap boussole est peu fiable en intérieur ; préférer un cap relevé sur plan ou la calibration à 2 points.
- OSM ne fournit ni plans intérieurs ni contours garantis ; les plans architecturaux sont prévus dans le modèle (`Site.plans` / `PlanReference` avec points de contrôle et transformation plan→site) mais leur import (PDF/image géoréférencée) n'est pas implémenté.
- Services OSM publics : peuvent être lents ou indisponibles (HTTP 502 Overpass observé) ; l'app reste utilisable sans.
- Pas d'application de bureau ni de DXF dans cette tranche.
- Synchro multi-appareils expérimentale : non mesurée sur matériel.
