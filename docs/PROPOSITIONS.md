# LumiSite — Document unique de référence pour Devin

**Date :** 29 octobre 2026 (heure de Paris, UTC+1).
**Pour :** Devin, seul développeur du code. EasyClaw et Grok Bot font des propositions, Sébastien Stinville décide.
**Utilisateur :** Sébastien Stinville, éclairagiste, Eclairage Technique (Toulouse). Il dessine en DWG avec AutoCAD ou CADCRAFT.
**Dépôt :** https://github.com/Gabrieljean454fgd/eclairage, branche `main`, commit de référence `1fb8fc52937be81f0f0d521f62db74349bf76c4d` (« LumiSite 0.1.0 : restauration du code de la tranche 1 (état e5cf515) »). Le 29/10/2026 à 18:26 (Paris), c'était encore la tête de `main`.
**Appareils :** Xiaomi 13T Pro pour les tests Android (ARCore). Une version iPad sous iOS est prévue.

## Journal des versions

| Version | Date (heure de Paris) | Changements |
|---|---|---|
| v1 | 29/10/2026, vers 18 h 40 | Fusion des cinq sources (UX terrain, analyse DWG, propositions, décompilation et mémoire d'EasyClaw, README du dépôt) ; contre-expertise vérifiée en direct. |
| v2 | 29/10/2026, vers 23 h 10 | Retours d'EasyClaw sur la contre-expertise. Trois erreurs reconnues et actées (400 IGN dû au format d'image, limite de 50 000 nœuds, `session_stopped`). Garde-fou de densité OSM (400 « too many nodes », découpage en tuiles). Liste exacte des 25 blocs. Renommage limité aux six noms fautifs. Désaccord sur le nom de référence tranché par D4, avec un test anti-doublon. Échelles de spot purement graphiques. PoC DXF en attente d'accord. Statut des fichiers (`_lumisite_backup`, publication sous `docs/PROPOSITIONS.md`). Lots 2, 3 et 11 ajustés. Sauvegarde de la v1 : `COPIE_SAUVEGARDE_LUMISITE_DOCUMENT_UNIQUE_2026-10-29_2026-10-29_2309.md`. |

**Publication.** Ce document unique sera publié dans le dépôt sous **`docs/PROPOSITIONS.md`**, comme référence pour Devin. Les autres documents de travail restent locaux, hors suivi git (§ 16.1). Ne pas le confondre avec le `PROPOSITIONS.md` d'EasyClaw, un fichier local non suivi à la racine du clone et fusionné ici.

Ce document réunit tout le travail du 29/10/2026 sur LumiSite. Il remplace la lecture séparée des sources listées en annexe (§ 16). Il ne modifie pas le dépôt : aucun fichier source n'a été touché, et il n'y a eu ni commit, ni clone, ni push. Le code a seulement été lu, en lecture seule, par l'API GitHub et raw.githubusercontent.com.

**Mentions de source sur chaque fiche :**
- **[UX terrain]** : `PROPOSITIONS_UX_TERRAIN_2026-10-29.md` (fiches A à Q, lots, checklist) ;
- **[Analyse DWG]** : `ANALYSE_PLAN_ECLAIRAGE03.md` (plan de Saint-Bertrand-de-Comminges) ;
- **[EasyClaw PROPOSITIONS]** : `PROPOSITIONS.md` d'EasyClaw, dont la section 9 (éditeur façon Xodo et PoC DXF) ;
- **[EasyClaw décompilation]** : `DECOMPILATION.md` (CamToPlan, Aruler) et la mémoire d'EasyClaw du 29/10 ;
- **[Contre-expertise]** : corrections d'EasyClaw, et vérifications refaites en direct le 29/10/2026 (§ 12) ;
- **[Décision]** : décision validée par Sébastien. Elle prime sur toute source contraire.

---

## Table des matières

- [Journal des versions](#journal-des-versions)
- [0. Mode d'emploi pour Devin, décisions verrouillées, glossaire](#0-mode-demploi-pour-devin-décisions-verrouillées-glossaire)
  - [0.1 Comment lire ce document](#01-comment-lire-ce-document)
  - [0.2 Décisions verrouillées](#02-décisions-verrouillées)
  - [0.3 Règles de travail](#03-règles-de-travail)
  - [0.4 Ce que le code fait déjà (à ne pas refaire)](#04-ce-que-le-code-fait-déjà-à-ne-pas-refaire)
  - [0.5 Glossaire](#05-glossaire)
- [1. Bugs bloquants P0](#1-bugs-bloquants-p0)
- [2. Parcours et ordre des étapes](#2-parcours-et-ordre-des-étapes)
- [3. Carte, OSM et fonds](#3-carte-osm-et-fonds)
- [4. Synchro acoustique](#4-synchro-acoustique)
- [5. Réalité augmentée et calibration](#5-réalité-augmentée-et-calibration)
- [6. Relevé, objets et typologie](#6-relevé-objets-et-typologie)
- [7. Annotation façon Xodo](#7-annotation-façon-xodo)
- [8. Formats et export GeoJSON](#8-formats-et-export-geojson)
- [9. Outil de bureau : DXF/DWG, bibliothèque de blocs, alias, recalage](#9-outil-de-bureau--dxfdwg-bibliothèque-de-blocs-alias-recalage)
- [10. Analyse du plan de référence eclairage03](#10-analyse-du-plan-de-référence-eclairage03)
- [11. Ergonomie, mise en page et thème](#11-ergonomie-mise-en-page-et-thème)
- [12. Contre-expertise : vérifications](#12-contre-expertise--vérifications)
- [13. Lots ordonnés pour Devin](#13-lots-ordonnés-pour-devin)
- [14. Checklist de test sur Xiaomi 13T Pro](#14-checklist-de-test-sur-xiaomi-13t-pro)
- [15. Décisions encore ouvertes](#15-décisions-encore-ouvertes)
- [16. Annexes](#16-annexes)

---

## 0. Mode d'emploi pour Devin, décisions verrouillées, glossaire

### 0.1 Comment lire ce document

Chaque fiche suit le même plan : **constat** (fichier et cause), **proposition**, **critères d'acceptation** sur le téléphone, **priorité**, **complexité**.

- **Priorités.** **P0** bloque le terrain. **P1** rend le relevé utilisable. **P2** est utile mais peut attendre.
- **Complexité, sans calendrier.** **S** : un écran ou une fonction localisée. **M** : plusieurs classes et des tests `core`. **L** : protocole, géométrie ou rendu nouveau.
- **Ordre de travail.** Le § 13 donne les lots dans l'ordre, chacun sous forme d'un bloc à coller tel quel. Les sections 1 à 11 expliquent le pourquoi.
- **Contradictions.** Quand deux sources se contredisent, la décision validée s'applique et l'écart est signalé par « ⚖️ Tranché : … ». La liste complète est au § 15.3.

### 0.2 Décisions verrouillées

Ces décisions de Sébastien sont fermées. Ne pas les rouvrir dans le code.

| # | Décision | Conséquence pour le code |
|---|---|---|
| D1 | **L'appli terrain n'écrit PAS de DXF.** Elle exporte en **GeoJSON**. | Ne pas écrire de générateur DXF dans `app/` ni dans `core/`. `dxfPreparation.status` reste à `NOT_GENERATED`. Le DXF puis le DWG sont produits par l'outil de bureau (§ 9). |
| D2 | Le paquet GeoJSON doit tenir de gros projets : **un fichier GeoJSONSeq par appareil et par session**, en écriture par ajout ; un **`manifest.json`** avec la version du schéma et les **sha256** ; les **photos en fichiers séparés** ; la géométrie en **WGS84 (RFC 7946)** et les coordonnées locales en mètres dans **`properties.local`**. | § 8. |
| D3 | Un **outil de bureau local** fusionne les appareils (time-codes de synchro), puis génère le DXF et le DWG final en **réutilisant les blocs déjà dessinés du gabarit DWG de Sébastien**. L'export reste mappé au plus près de ses blocs, calques et attributs AutoCAD : les équipements vont sur le calque **`RES - Elec`**. | § 9. Les blocs ne sont jamais redessinés. |
| D4 | **Noms de blocs corrigés :** `bloc secour` → **`bloc secours`**, `tableau divisionaire` → **`tableau divisionnaire`**. Les noms corrigés font référence. Une **table d'alias** permet de lire les anciens DWG. | § 6.3 et § 9.3. Renommage unique dans le gabarit, normalisation à l'import, limité aux six noms fautifs (4 en attente de validation). |
| D5 | Le **sélecteur terrain** propose la **liste fermée des blocs de Sébastien** (environ 25 familles), **mise à jour à chaque ajout de bloc**. | Catalogue versionné, sous forme de données et non de code (§ 6.3). |
| D6 | **Typologie :** des familles courtes, avec des propriétés facultatives (technologie PAR20, MR16 dichroïque, LED, puissance, lecture d'étiquette), des notes texte, vocales ou photo, et un **dictaphone continu sur l'iPad** synchronisé. Transcription **au bureau avec Whisper**. Chaque passage est rattaché aux objets par time-code et proximité, **avec validation humaine**. | § 6.4 à 6.6. |
| D7 | **Les 3 tailles de spot** servent la **lisibilité pour le client** et correspondent à **des technologies différentes** (PAR20, MR16 dichroïque, LED plus basiques). | La taille n'est pas saisie sur le terrain. L'outil de bureau la déduit du sous-type (§ 9.4). |
| D8 | **Géoréférencement :** les données terrain sont calées en **Lambert 93 (EPSG:2154)**. Le **plan de l'architecte est recalé sur elles** par similitude 2D sur des points homologues. **Sans plan d'architecte, on utilise directement le fond OSM.** Le **GEODATA faux** du gabarit est à **remplacer**. | § 9.5. |
| D9 | **Zoom :** il sert à implanter et à se déplacer, pas à voir du détail cartographique. Le **sur-zoom au-delà de 21 (jusqu'à 24–26)** est accepté même flou. Les vecteurs n'ont pas de limite. Les fonds satellite ne sont mis **en cache que sur la zone du site**. | § 3.6 et 3.7. |
| D10 | **Thème :** bouton **en haut à droite** (Système, Clair, Sombre), carte assombrie en mode sombre, **mode nuit rouge en option**. | § 11.3. |
| D11 | L'**analyse de l'APK officiel Xodo 11.3.1** est approuvée : on étudie le comportement et l'ergonomie, **sans copier de code**. | § 7. |
| D12 | **Devin seul code.** EasyClaw et Grok Bot proposent. | Aucun agent autre que Devin ne modifie `app/`, `core/` ou `docs/`. |

### 0.3 Règles de travail

Ces règles viennent des cinq sources.

- **Format 0.1 toujours lisible.** `formatVersion` est verrouillé par le motif `^0\.1\.[0-9]+$` dans `docs/session.schema.json`. `LumiJson` (`core/.../journal/Journal.kt`, l. 20–21) a `ignoreUnknownKeys = true` et `classDiscriminator = "type"` sur une hiérarchie scellée (`sealed interface SessionEvent`, `Model.kt` l. 169).
  - **Ajouter des champs optionnels** est sans risque.
  - **Ajouter un nouveau `type` d'événement** casse la lecture par une version plus ancienne de l'appli. Ne le faire qu'en passant le format en 0.2, avec le lecteur à jour dans la même livraison.
  - `kind` et `positionSource` de `SurveyEvent` sont des **chaînes libres** (`Model.kt` l. 298 et l. 305). Une nouvelle valeur ne casse pas le schéma.
- **Journal en ajout seul.** Une modification est un nouvel événement, jamais une réécriture de ligne.
- **Licences.** Pas de code GPL (StreetComplete, OsmAnd) : le dépôt n'a pas de `LICENSE`. Aucun code, aucune icône et aucune ressource de Xodo/Apryse (PDFTron). Aucun code de CamToPlan ni d'Aruler : on reprend des principes et on les réécrit (§ 5.2).
- **Chaque lot** comprend : une branche, des tests `core` dès que le métier change, une ligne dans `docs/TESTS_TABLETTE.md`, et un essai sur le 13T Pro.
- **Services publics** (OSM, Overpass, Nominatim, IGN) : un appel par geste de l'utilisateur, un User-Agent identifiable, l'attribution visible, et aucune moisson.

### 0.4 Ce que le code fait déjà (à ne pas refaire)

[UX terrain] [EasyClaw PROPOSITIONS] [Contre-expertise]

| Attendu terrain | Déjà dans le code (commit `1fb8fc5`) | Suite |
|---|---|---|
| Modèle de session, journal NDJSON en ajout seul | 30 tests JVM (`:core:test`) | Garder |
| Horodatage UTC + monotone + `sensorNs`, plusieurs bases | `TimeTest` (4 tests) | Base du time-code de synchro |
| WGS84 ↔ ENU local, projection ARCore `(x, −z)` | `GeoTest` (7 tests) | Garder ; `properties.local` = ce repère |
| Calibration 1 point + cap, 2 points, 2 points + distance | `CalibrationTest` (6 tests), `residualM`, `measuredScale` | Garder (la concurrence n'en a pas, § 5.2) |
| Recalage non destructif, trajectoire corrigée | `derived/` recalculé à l'export | Garder ; affichage = même fonction (§ 2.2) |
| Export ZIP + hash + intégrité | `JournalExportTest` (9 tests), `SessionImporter` | Garder dans `raw/` du paquet GeoJSON |
| Recherche de lieu | `OsmClient.search` (Nominatim), champ « Adresse / lieu » dans `SiteEditorScreen` | Garder, enchaîner avec le rectangle |
| Point d'ancrage au toucher | `onTap` → origine `MAP_TAP` | Garder en plus du rectangle |
| Message Overpass en français | `catch` dans `SiteEditorScreen` | Le rendre actionnable |
| Barre d'échelle | `ScaleBarOverlay` dans `OsmMap` | Garder, lisible en sur-zoom |
| Attribution OSM | `CopyrightOverlay` | Étendre à chaque fond |
| Chirp + corrélation | `Chirp`, `ChirpDetector`, `AudioSync` ; `SyncTest` (4 tests) | Marque temporelle, pas modem |
| Préparation DXF | `DxfHint`, `dxfPreparation.status = NOT_GENERATED`, `Export.kt` l. 80 : « blocks are not defined yet » | **Pas de DXF dans l'appli (D1).** `DxfHint.blockName` doit porter le nom **canonique** du catalogue (§ 6.2, point critique) |
| Position courante sur la carte | marqueur `current` dans `SessionScreen` | Ajouter le cap et le suivi |
| Champ libre d'extension | `SurveyEvent.payloadSchema`, `payload` (objet JSON), `attachments` (`Model.kt` l. 307–309) | Porte les détails facultatifs et les pièces jointes sans changer de format |

### 0.5 Glossaire

| Terme | Sens dans ce document |
|---|---|
| **Famille** / **bloc canonique** | Une entrée du catalogue = un bloc de la bibliothèque de Sébastien (`spot`, `projecteur thorn contrast`, `bloc secours`…). Le nom corrigé fait référence (D4). |
| **Alias** | Ancien nom d'un bloc (`bloc secour`) traduit vers le nom canonique à la lecture d'un ancien DWG. |
| **Catalogue** | `catalog/equipment_catalog.json`, versionné, partagé par l'appli et l'outil de bureau. Il contient la liste fermée des familles et leurs alias. |
| **Paquet projet** | Dossier ou ZIP `LumiSite_<project_id>/` : manifeste, catalogue, GeoJSONSeq, photos, audio, bruts (§ 8). |
| **GeoJSONSeq** | RFC 8142 : une Feature GeoJSON par ligne, fichier `.geojsonl`. |
| **`properties.local`** | Coordonnées en mètres dans le repère du site (X est, Y nord, Z haut, origine = origine du site, repère `Enu` du code). |
| **`rev` / `op`** | Révision d'un objet (1, 2…) et opération (`create`, `update`, `delete`). L'état courant est la dernière `rev` de chaque `id`. |
| **Time-code** | `time.captured_utc` + `device_monotonic_ns` + `clock_offset_ms` issu de la synchro acoustique. |
| **Chirp** | Balayage 2–6 kHz de 60 ms, fenêtre de Hann, détecté par corrélation (`core/sync`). |
| **« Calé (N) »** | État affiché quand l'appareil a reconnu N identifiants d'autres appareils au-dessus du seuil de corrélation. |
| **L93** | Lambert 93, EPSG:2154. |
| **Similitude 2D / Helmert 4 paramètres** | Translation (tx, ty), rotation θ, échelle s. Calculée par moindres carrés sur des points homologues. |
| **Points homologues** | Mêmes points physiques (angle de mur, pilier) repérés dans deux repères. |
| **Gabarit** | DXF/DWG modèle de Sébastien : blocs déjà dessinés, calques, styles, présentations `CARTOUCHE ETI A3 - 01/02`. |
| **Sur-zoom** | Affichage au-delà du zoom natif des tuiles. Le fond est agrandi (flou), les vecteurs restent nets. |
| **WMTS / TileMatrixSet** | Service de tuiles IGN Géoplateforme. Le jeu de matrices à utiliser est `PM_0_19` (§ 12). |

---

## 1. Bugs bloquants P0

### 1.1 L'export « part », mais le fichier est introuvable — P0 — S
[UX terrain, fiche A1] [EasyClaw : constat confirmé]

**Constat.**
- `Repository.export` écrit le ZIP dans `context.filesDir/exports/` (`app/src/main/java/app/lumisite/android/data/Repository.kt`). Ce dossier est privé : ni l'appli Fichiers, ni Téléchargements, ni un câble USB sans débogage ne le montrent.
- `ExportPanel` (`ui/SessionScreen.kt`) n'ouvre la feuille de partage qu'après un second bouton « Partager… ».
- Le message de succès ne donne que le nom du fichier.
- Le bouton est désactivé pendant l'écriture (`enabled = !busy`), puis le résultat s'affiche plus bas dans une colonne qui défile. Sur un 13T Pro, on le manque facilement.
- `res/xml/file_paths.xml` n'autorise le `FileProvider` que sur `files/exports/`.

**Proposition.** À la fin d'un export réussi, enchaîner trois gestes :
1. Copier le ZIP dans **Téléchargements** via `MediaStore` (API 29 et plus ; le 13T Pro est au-dessus). Aucune permission de stockage n'est nécessaire.
2. Ouvrir tout de suite la feuille de partage Android (`ACTION_SEND`), sans second bouton caché.
3. Afficher : « Enregistré dans Téléchargements : LumiSite-….zip ». En dessous d'Android 10, passer par le sélecteur de documents.

Le ZIP interne peut rester comme copie de travail. Ne pas changer le format `lumisite.session`. Le paquet GeoJSON (§ 8) utilisera la même destination.

**Acceptation.**
- Après « Exporter », le fichier est dans Téléchargements, s'ouvre sur un PC, et la feuille de partage propose Drive ou le courriel.
- Un message en français dit où se trouve le fichier.
- Si l'export échoue, le bouton ne reste pas grisé et l'erreur reste affichée.

### 1.2 « Terminer la session » ferme ARCore et fait planter l'appli — P0 — S
[UX terrain, fiche A2] [Contre-expertise : confirmé dans le code]

**Constat.**
- `app/src/main/java/app/lumisite/android/SessionController.kt`, l. 136–139 :
  ```kotlin
  fun close() {
      pauseCapture()
      journal.event(SessionStopped(Ids.new("ev"), stamps.now(), "user"))
      journal.close(); ar.close()
  }
  ```
  Cette fonction tourne sur le fil de l'interface, alors que la `GLSurfaceView` (rendu continu) peut encore appeler `session.update()` depuis `ArTracker.onDrawFrame`.
- `ArTracker.pause()` n'attrape pas les exceptions. Une exception sur le fil de l'interface, ou un plantage natif d'ARCore après la fermeture de la session, tue le processus et Android réaffiche le lanceur.
- `MainActivity.kt` appelle `controller.close()` puis change d'écran.
- Il n'y a aucun `BackHandler` : le geste retour système provoque la même chose, car l'activité n'a pas de pile de navigation.

**Proposition.** Fermer dans cet ordre :
1. retirer le rendu OpenGL ;
2. mettre la vue en pause ;
3. fermer la session AR sur le fil qui la dessine (ou après `onPause`) ;
4. entourer le tout d'un `try/catch` ;
5. écrire `session_stopped` ;
6. changer d'écran seulement à la fin.

Le geste retour système depuis une session fait la même chose que le bouton, jamais `finish()`.

**Acceptation.**
- Dix fois de suite, « Terminer la session » revient à la page du site, la session est dans la liste, et l'appli ne retourne pas au bureau Android.
- Le geste retour donne le même résultat.
- Une session rouverte contient l'événement d'arrêt et les relevés précédents.

> Correction de la contre-expertise sur ce point : EasyClaw affirmait que `session_stopped` absent du schéma rendait **tout export invalide en validation stricte**. Le test refait le 29/10 ne le confirme pas : le schéma ne restreint pas `type` à une liste, et un événement `session_stopped` passe la validation (§ 12.5). Il reste un **manque de documentation** : ajouter une branche `if/then` pour `session_stopped` (champ `reason` requis) dans `docs/session.schema.json` et décrire l'événement dans `SESSION_FORMAT.md`.

### 1.3 Le bouton Retour vers la liste des projets est hors écran — P0 — S
[UX terrain, fiche A3]

**Constat.**
- `SiteScreen` a bien un bouton « Retour » (`onBack` → accueil), mais le geste retour système n'est pas intercepté : il ferme l'activité.
- La mise en page est une `Row` 60 % / 40 % qui ne s'adapte pas. « Nouvelle session terrain » et « Retour » sont dans une `Row` qui ne passe pas à la ligne : sur le 13T Pro, « Retour » est poussé hors de l'écran.
- La colonne du site ne défile pas.

**Proposition.**
- Un `BackHandler` sur chaque écran : éditeur → accueil, site → accueil, session → site.
- Le bouton retour en tête de l'écran, et non dans une rangée qui déborde.
- La colonne d'actions défile si la hauteur manque.

**Acceptation.**
- Depuis un projet, le bouton et le geste retour reviennent à la liste de tous les projets.
- Depuis l'accueil, le geste retour quitte l'appli (comportement Android normal).
- Le libellé reste lisible en portrait et en paysage.

### 1.4 « Créer le site » grisé tant que le nom est vide, et après un 502 — P0 — S
[UX terrain, fiche A4] [Contre-expertise : cause confirmée]

**Constat.**
- `app/src/main/java/app/lumisite/android/ui/Screens.kt`, l. 153 : `Button(enabled = origin != null && name.isNotBlank(), …)`. Le champ n'a ni astérisque ni texte « nom requis ».
- Après une erreur Overpass, le `catch` remet `busy` à faux, et « Créer » ne dépend pas de `busy`. **Le 502 ne grise donc pas le bouton.** La vraie cause est le nom vide, combinée à un message d'erreur long et à un bouton placé en bas d'une colonne étroite.
- Il n'y a pas de `finally` : une annulation de coroutine peut laisser `busy` vrai, ce qui fige « Chercher » (l. 109 : `enabled = !busy && query.isNotBlank()`) et « Récupérer ».

**Proposition.**
- Mettre un astérisque sur « Nom du site » et, tant qu'il est vide, afficher « Nom de projet requis » sous le champ.
- Si le nom est vide au moment de créer, proposer le nom par défaut `Site` + date (même forme que le nom de session) et créer.
- Le bouton est disponible dès qu'une origine existe (toucher, recherche ou coordonnées), **que la récupération des contours ait réussi, échoué ou soit en cours**. Libellé secondaire : « Créer sans données OSM ».
- Ajouter `finally { busy = false }`. Un échec de récupération ne change pas l'origine.

**Acceptation.**
- Sans nom, on voit pourquoi le bouton est inactif, et un second appui crée « Site 2026-10-29 … ».
- Après un 502 forcé (mode avion au milieu de « Récupérer »), « Créer » reste utilisable et le site s'ouvre avec une carte sans contours. « Récupérer » redevient cliquable.

### 1.5 Libellé « Nouveau » sur l'accueil — P0 — S
[UX terrain, fiche A5]

**Constat.** Dans le commit `1fb8fc5`, le bouton d'accueil dit « Nouveau site » (`Screens.kt`) et l'écran d'édition est titré « Nouveau site ». Si l'APK testé affichait une phrase plus longue, elle ne vient pas de ce commit : chercher toutes les chaînes visibles.

**Proposition.** Le bouton d'accueil affiche exactement « Nouveau ».

**Acceptation.** L'accueil affiche « Nouveau » et rien d'autre sur ce bouton.

### 1.6 Autres P0 rattachés

Ces points sont détaillés dans leur section ; ils sont P0 parce qu'ils bloquent le terrain.
- **Relevé « enregistré » sans coordonnées** : § 6.1 (fiche G, partie enregistrement).
- **Boutons dessinés sur la caméra** : § 11.1 (fiche H, correctif minimal).
- **Zoom bloqué à 21** : § 3.6 (fiche I).
- **`blockName = "LUMI_${kind}_GENERIC"`** : § 6.2. C'est P0 pour l'objectif « blocs nommés dans AutoCAD », même si l'appli n'écrit pas de DXF.

---

## 2. Parcours et ordre des étapes

### 2.1 Ordre des pastilles : Synchro, Calibrer, Relevé, Recaler, Export — P1 — S
[UX terrain, fiche D]

**Constat.** Les pastilles suivent l'ordre de l'énumération `Mode` (`SessionScreen.kt`) : Calibrer, Recaler, Relevé, Synchro, Export. Rien dans le métier n'impose cet ordre.

**Proposition.** Afficher les pastilles dans cet ordre : **1 Synchro, 2 Calibrer, 3 Relevé, 4 Recaler, 5 Export.** La synchro passe en premier parce que le time-code commun (§ 4) doit exister avant les relevés et le dictaphone.

**Acceptation.** L'ordre visible est Synchro → Calibrer → Relevé → Recaler → Export.

### 2.2 Recaler après le relevé : une seule fonction de position — P1 — M
[UX terrain, fiches D et G] [Contre-expertise EasyClaw : reprise des relevés existants]

**Constat.**
- Un relevé enregistre `estimatedSite` **au moment du clic** (`SessionController.survey`, l. 266–278).
- Un recalage ultérieur ne déplace pas ce point à l'écran. La trajectoire, elle, est recalculée.
- L'export recalcule la position à partir de la pose et de la transformation active (`derivedSurveyPoints`). L'écran et le fichier peuvent donc diverger.

**Proposition.**
- **Une seule fonction de position**, partagée par l'écran et `derivedSurveyPoints`. Un recalage ne met à jour que les poses postérieures à ce recalage, comme le dit déjà `SESSION_FORMAT.md`.
- **Reprise des relevés déjà écrits** (manque relevé par EasyClaw) : au chargement d'une session ancienne, la position affichée est recalculée par cette même fonction. `estimatedSite` reste dans le journal comme valeur historique, sans réécriture.

**Acceptation.**
- Un relevé fait avant un recalage ne saute pas en arrière.
- Un relevé fait après suit la nouvelle transformation, à l'écran et dans le ZIP.
- Une session 0.1 rouverte affiche ses relevés à la position qu'aurait calculée l'export.

### 2.3 Parcours complet visé

[UX terrain] [Décision]

1. **Accueil** : « Nouveau » ; une carte par projet avec une icône d'export du projet (§ 8.7).
2. **Création du site** :
   - recherche d'adresse (IGN, puis Nominatim, § 3.4) ;
   - rectangle façon JOSM (§ 3.3) ;
   - « Récupérer » les contours (API OSM, puis Overpass, § 3.1 et 3.2) ;
   - « Créer », toujours possible (§ 1.4).
3. **Site** : liste des sessions, rectangle réaffiché, relevés visibles sur la carte (§ 6.1).
4. **Session** : Synchro → Calibrer → Relevé → Recaler → Export.
5. **Export** : paquet GeoJSON du projet ou de la session, enregistré dans Téléchargements, avec la feuille de partage (§ 1.1 et § 8).
6. **Bureau** : fusion, recalage L93, DXF puis DWG avec les blocs de Sébastien (§ 9), et transcription du dictaphone (§ 6.6).

---

## 3. Carte, OSM et fonds

> **Priorité de travail** [Contre-expertise] : la **fiche L (API OSM) passe AVANT la fiche C (Overpass)**. Le 29/10, Overpass a répondu 502 ou n'a pas répondu dans les 30 s sur les trois instances testées, alors que l'API OSM a répondu en environ 1 s (§ 12.2 et 12.3). Le rectangle de la fiche C est un prérequis de la fiche L : il est livré dans le même lot (§ 13, lot 3).

### 3.1 Contours façon JOSM : API OSM v0.6 sur le rectangle — P1 — S
[UX terrain, fiche L, corrigée par la contre-expertise]

**Constat.**
- JOSM est rapide parce que « Télécharger depuis OSM » n'interroge pas Overpass. Il appelle directement l'API principale : `GET https://api.openstreetmap.org/api/0.6/map?bbox=ouest,sud,est,nord`. Le serveur renvoie le contenu brut de la bbox (nœuds, chemins, relations), sans requête à interpréter ni file d'attente partagée.
- LumiSite n'utilise aujourd'hui qu'Overpass (`OsmClient.fetchFeatures`, `app/src/main/java/app/lumisite/android/data/OsmClient.kt`).

**Précisions techniques, vérifiées le 29/10/2026 (§ 12.2).**
- **Format.** L'API ne sert pas de PBF. La réponse est en OSM XML, ou en JSON avec `/api/0.6/map.json`.
- **Limite de surface.** 0,25 degré carré par appel. Elle est déclarée par `GET https://api.openstreetmap.org/api/capabilities` (`<area maximum="0.25"/>`). Une bbox de 1° × 1° renvoie HTTP 400 : « The maximum bbox size is 0.250000… ».
- **Limite de 50 000 nœuds par appel `map`.** Elle **existe**, et EasyClaw l'a reconnu. Elle est confirmée par le `config/settings.yml` du serveur (`openstreetmap-website`) : `max_number_of_nodes: 50000`, `max_request_area: 0.25`, `max_number_of_way_nodes: 2000`. Elle figure aussi sur le wiki OSM (codes d'erreur de `map`). Elle **n'apparaît pas dans `capabilities`**, où `<waynodes maximum="2000"/>` est autre chose : le nombre maximal de nœuds **par chemin**, contrôlé à l'enregistrement.
  - Message exact quand on la dépasse : **HTTP 400 « You requested too many nodes (limit is 50000). Either request a smaller area, or use planet.osm »**.
  - **En zone dense, une bbox de 0,002 degré carré peut déjà la dépasser.** Vérifié le 29/10 à 23:09 sur le centre de Paris (bbox `2.33,48.85,2.38,48.89`, soit 0,002 degré carré) : 400 avec ce message. **La surface ne suffit donc pas comme garde-fou.**
- **Géométrie.** Les `way` de `map.json` ne portent **aucune coordonnée**, seulement la liste des identifiants de nœuds (clés : `type, id, timestamp, version, changeset, user, uid, nodes, tags`). Il faut faire la jointure nœud ↔ chemin côté appareil.
- **Relations à écarter.** Sur la petite bbox de la cathédrale, la réponse contient 10 relations, dont des itinéraires de randonnée et de VTT de 630 et 724 membres. Il faut écarter `route`, `superroute`, `route_master`, `boundary` et `public_transport` **avant** de reconstruire les géométries.
- **Rapidité.** C'est l'API qui répond vite ; ce sont les tuiles (`tile.openstreetmap.org`), et non l'API, qui passent par un CDN. La réponse est rapide parce qu'elle est brute et que la zone est petite.

**Proposition.**
- **Source principale** pour un site : `/api/0.6/map.json?bbox=…` sur le rectangle du § 3.3. Overpass ne sert plus que de secours (§ 3.2).
- **Côté appareil :**
  - reconstruire les géométries : les chemins à partir des nœuds, et les multipolygones en **gardant les anneaux intérieurs**, ce que `toFeature` ne fait pas aujourd'hui ;
  - puis filtrer localement sur les tags utiles : `building`, `building:part`, `indoor`, `highway=street_lamp`, `entrance`, `man_made`, `power`, etc.
- **Deux garde-fous complémentaires, tous deux obligatoires :**
  1. **Surface** : refuser avant l'appel une bbox de plus de **0,01 degré carré** environ, bien en dessous de la limite de 0,25, et proposer de réduire le rectangle.
  2. **Densité** : traiter le **HTTP 400 « You requested too many nodes (limit is 50000)… »**, qui peut arriver même sous 0,01 degré carré en ville. Ne jamais boucler, mais proposer à l'utilisateur :
     - soit de **réduire le rectangle** ;
     - soit de **découper la zone** en sous-bbox (par exemple en quatre, récursivement jusqu'à une profondeur bornée), puis de **fusionner** les résultats en dédoublonnant nœuds, chemins et relations par `type` + `id`, avant la reconstruction des géométries. Le nombre d'appels est affiché et plafonné : pas de moisson.
- **Autres cas :**
  - un 400 « maximum bbox size » (surface supérieure à 0,25) ne devrait jamais arriver grâce au garde-fou 1 ; sinon, proposer de réduire ;
  - traiter le **509** (bande passante) et le **429** (trop de requêtes) en passant à Overpass ;
  - un seul appel par geste « Récupérer », jamais de rechargement en boucle ;
  - un User-Agent identifiable.
- **Politique d'usage OSMF.** L'API sert d'abord à l'édition. La lecture ponctuelle de petites zones est tolérée, et c'est ce que fait JOSM. Le téléchargement massif ou automatique est interdit. Référence : https://operations.osmfoundation.org/policies/api/
- **Repris de JOSM :** le rectangle dessiné sur la carte glissante, et la mémorisation des dernières bbox (« zones récentes »).
- **Conserver la réponse brute** (`osm_source.json`, datée) dans le dossier du site et dans `refs/` du paquet (§ 8). On sait ainsi d'où viennent les contours, et on peut les recharger hors ligne.

**Acceptation.**
- Sur un rectangle qui couvre un bâtiment, « Récupérer » renvoie les contours en moins de 3 s en 4G, sans passer par Overpass.
- Si l'API est coupée (URL invalide en test), LumiSite passe à Overpass, puis à « créer sans contours », avec un message en français.
- Un rectangle trop grand est refusé, avec une proposition de réduction.
- Un rectangle dense (test : centre de Paris, 0,002 degré carré) qui reçoit le 400 « too many nodes » donne un message en français. L'utilisateur peut réduire, ou découper puis fusionner. Le résultat fusionné ne contient aucun doublon de nœud ou de chemin, et le nombre d'appels reste plafonné.
- Les itinéraires de randonnée n'apparaissent pas sur la carte.

### 3.2 Overpass en secours : bbox du rectangle, miroirs, repli — P1 — M
[UX terrain, fiche C] [Contre-expertise]

**Constat.**
- `OsmClient.fetchFeatures` interroge un disque de rayon saisi (20 à 500 m, 150 m par défaut) converti en bbox (`bboxAround`).
- Une seule URL (`overpass-api.de`), sans nouvel essai ni miroir.
- La requête `out geom` n'a pas de plafond d'éléments.
- Les relations ne gardent que le premier anneau extérieur (`toFeature`).
- Timeouts actuels : connexion 15 s, lecture 40 s (`OsmClient.read`).
- 321 objets pour un rayon urbain est un résultat normal, pas un bug de lecture.

> Remarque de la contre-expertise : EasyClaw a compté 2 742 éléments (707 Ko) par l'API OSM pour 150 m, et non par Overpass. Les deux chiffres ne mesurent pas la même chose (filtrée côté serveur pour Overpass, brute pour l'API) et ne sont pas comparables. Sur la bbox de la cathédrale, l'API a renvoyé 261 éléments (122 Ko) le 29/10.

**Proposition.**
- Envoyer à Overpass **la bbox du rectangle**, et non un disque plus large. Ajouter un plafond (`out … 500` ou équivalent) et un message si ce plafond est atteint.
- Miroirs dans cet ordre, chacun avec repli : `overpass-api.de`, `overpass.kumi.systems`, `overpass.private.coffee`.
  - Deux ou trois essais par miroir, avec des attentes de 1 s, 2 s puis 4 s.
  - Timeout de lecture d'environ 25 à 30 s, `[timeout:25]` dans la requête.
  - Garder le User-Agent identifié.
- Si tout échoue : carte de fond seule, et le message « Le serveur de contours est indisponible. Le site est utilisable sans contours. » Le bouton « Créer » reste actif (§ 1.4).

**Acceptation.**
- En coupant le réseau, la création aboutit, le message est en français, et la réouverture du site recadre sur le rectangle.
- Avec un rectangle d'un pâté de maisons, il y a bien moins d'objets qu'avec un rayon de 150 m en ville.

### 3.3 Sélection de zone façon JOSM (rectangle) — P1 — M
[UX terrain, fiches C et L]

**Proposition.**
- Garder la recherche de lieu et le point d'ancrage au toucher.
- Ajouter un **rectangle**, soit par deux coins (haut-gauche, bas-droite), soit par un cadre que l'on tire. La bbox du site est ce rectangle.
- Le réafficher à chaque ouverture du site. Il est déjà dans `OsmSnapshot.bbox` ; le stocker aussi quand le site est créé sans OSM.
- Mémoriser les « zones récentes ».
- Le même rectangle borne le cache des fonds (§ 3.7).

**Acceptation.**
- Une recherche « Lyon » centre la carte.
- On trace un rectangle, et la bbox exportée est bien celle du rectangle.
- Le rectangle survit à la fermeture et à la réouverture du site.

### 3.4 Recherche d'adresse : géocodage IGN, puis Nominatim — P1 — S
[UX terrain, fiche L, corrigée par la contre-expertise]

- **En France, d'abord la Géoplateforme IGN** : `https://data.geopf.fr/geocodage/search?q=…`. Elle est sans clé, plus précise sur les adresses françaises, et a répondu 200 en 0,75 s le 29/10.
- ⚖️ **Correction.** Il ne faut pas écrire que ce service « a pris la suite » d'`api-adresse.data.gouv.fr` : ce dernier répondait encore 200 le 29/10 (§ 12.6). Garder une seule URL configurable et vérifier les quotas le jour de l'implémentation.
- **Repli sur Nominatim** (déjà en place) hors de France, ou si l'IGN ne trouve rien. Règles Nominatim :
  - 1 requête par seconde au maximum ;
  - User-Agent obligatoire ;
  - pas d'autocomplétion à chaque frappe : la recherche part sur « Entrée » ou après au moins 1 s sans frappe.
- **Acceptation.** « 10 rue de Rivoli Paris » renvoie d'abord le résultat IGN. Une adresse à l'étranger renvoie le résultat Nominatim.

### 3.5 Fonds de carte : OSM, IGN, Esri — P1 — M
[UX terrain, fiche J] [Contre-expertise : TileMatrixSet IGN]

Un sélecteur de fond, avec l'attribution toujours visible.

| Fond | Usage | Licence et conditions | Cache hors ligne |
|---|---|---|---|
| **OSM standard** (`tile.openstreetmap.org`, déjà en place) | Défaut | Données ODbL. Politique des tuiles OSMF : pas de téléchargement massif, User-Agent identifiable, attribution. Le `TileSourcePolicy` MAPNIK porte déjà `FLAG_NO_BULK` et `FLAG_NO_PREVENTIVE`. | Seulement la bbox du site, aux zooms utiles (par exemple 16 à 19), avec un plafond de tuiles (quelques centaines), sur action explicite de l'utilisateur. Au-delà, il faut un serveur de tuiles dédié. |
| **Plan IGN** et **Orthophoto IGN** | France, le cas de Sébastien | Géoplateforme `https://data.geopf.fr/wmts`, couches `GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2` et `ORTHOIMAGERY.ORTHOPHOTOS`. Licence ouverte Etalab 2.0 : réutilisation, y compris commerciale, en citant l'IGN et la date. Pas de clé. | Autorisé. Bbox du site seulement, zooms natifs (0 à 19), pas de moisson. Vérifier le quota le jour de l'implémentation. |
| **Esri World Imagery** | Secours mondial, **jamais** hors ligne | Les conditions Esri interdisent la moisson, la redistribution et l'auto-hébergement des tuiles. Le Living Atlas n'est pas « gratuit sans condition » pour un usage commercial. | **Aucun** préchargement. Affichage en ligne seulement, avec attribution, et seulement si le projet accepte ces conditions. Sinon, ne pas l'activer. |

**Paramètres IGN WMTS à utiliser** (vérifiés le 29/10, § 12.1) :

| Couche | `TILEMATRIXSET` | `FORMAT` | Zooms | `STYLE` |
|---|---|---|---|---|
| `GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2` | **`PM_0_19`** | `image/png` | 0 à 19 | `normal` |
| `ORTHOIMAGERY.ORTHOPHOTOS` | **`PM_0_19`** | `image/jpeg` | 0 à 19 | `normal` |

Modèle d'URL KVP :
```
https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=<couche>&STYLE=normal&TILEMATRIXSET=PM_0_19&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=<format>
```
- **Le format d'image est imposé par couche** : `image/png` pour `GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2`, `image/jpeg` pour `ORTHOIMAGERY.ORTHOPHOTOS`. **Un mauvais format renvoie HTTP 400**, que la grille soit `PM_0_19` ou `PM` (vérifié le 29/10 à 23:09). Associer le format à la couche dans la configuration, et ne jamais le choisir indépendamment.
- ⚖️ **Tranché, erreur reconnue par EasyClaw.** Le 400 qu'EasyClaw attribuait à `PM` venait d'un **mauvais format d'image**, pas du nom de la grille. Les capabilities déclarent `PM_0_19` pour les **deux** couches. `PM` est un **alias non déclaré** : il répond 200 aujourd'hui, avec des tuiles identiques à l'octet près, mais il peut disparaître. `PM_6_19` renvoie 400. **On garde `PM_0_19`.**
- Côté osmdroid : `XYTileSource` ou une source WMTS, avec l'attribution « © IGN – Géoplateforme » et la date.

**Acceptation.**
- On change de fond sans perdre le rectangle du site, et l'attribution change avec le fond.
- Esri, s'il est proposé, n'offre aucun téléchargement.

### 3.6 Zoom : agrandir pour poser au doigt, pas pour voir plus de détail — P0 — M
[UX terrain, fiche I] [Décision D9] [Contre-expertise : sources osmdroid lues par EasyClaw]

**Constat.**
- `app/src/main/java/app/lumisite/android/ui/MapView.kt`, l. 61 : `minZoomLevel = 3.0; maxZoomLevel = 21.0`, avec `TileSourceFactory.MAPNIK` dont le zoom natif est 19 (osmdroid 6.1.20).
- `mTilesScaledToDpi` vaut faux : il n'y a pas d'agrandissement caché selon la densité de l'écran.
- À 48° de latitude, on a environ 0,20 m par pixel au zoom 19 et 0,05 m au zoom 21. La carte occupe environ 58 % du 13T Pro en paysage, soit 70 à 80 m de large au zoom 21. Le doigt (environ 40 px) couvre près de 2 m : impossible de poser un luminaire.
- Les polygones OSM sont des `Polygon` / `Polyline` osmdroid, recalculés à chaque zoom. Ce sont les tuiles qui fixent la limite actuelle, pas les vecteurs.
- Relevé par EasyClaw dans les sources d'osmdroid 6.1.20 :
  - `MapView.setMaxZoomLevel(Double)` n'applique **aucune borne**, donc 24 ou 26 passent sans contournement ;
  - `TileSystem` a un plafond dur à 29 ;
  - `MapTileApproximater` (dans `MapTileProviderBasic`) agrandit une tuile d'un zoom inférieur.
- **Risque relevé par EasyClaw** : `approximateTileFromLowerZoom` remonte les niveaux un par un. Avec un cache vide et un fort sur-zoom, la recherche peut s'emballer et occuper le processeur à 100 % (issue osmdroid #1106). **À tester en mode avion.**

**Proposition (le sur-zoom est le chemin principal, D9).**
- Lever le plafond d'affichage à **24, voire 26**. Au zoom 24, la même carte fait environ 10 m de large, soit l'échelle d'une pièce. Le fond peut être flou. **Ne jamais télécharger de tuiles au-delà du zoom natif.**
- Les vecteurs OSM et les objets posés ne sont pas limités par le zoom des tuiles. Un plafond technique vers 26 suffit.
- Si `MapTileApproximater` ne suit pas une fois le plafond levé, utiliser une source dont l'URL reste au zoom natif et dont l'affichage est mis à l'échelle.
- **Aides au placement,** plus importantes que le flou du fond :
  - un **réticule** au centre de la carte : on déplace la carte sous la croix, et le doigt ne cache pas le point ;
  - une **loupe** au-dessus du doigt ;
  - des **déplacements fins** de 0,1 m et 0,5 m vers l'est et le nord (`LocalTangentPlane`) ;
  - un bouton **« Suivre »** : la carte se recentre sur la position courante, cap visible (§ 6.1).
- La barre d'échelle reste, avec une lecture du type « largeur visible : N m ».
- Un plan local en mètres sur grille illimitée reste **optionnel (P2)**, seulement si le sur-zoom ne suffit pas dans un intérieur sans aucun vecteur.

**Acceptation.**
- Sur le 13T Pro, on agrandit jusqu'à ce qu'une pièce de 4 m occupe une grande part de la carte. Le fond est flou, les contours et les objets restent nets.
- On pose un point avec la croix, on le décale de 0,1 m, et la loupe apparaît sous le doigt.
- « Suivre » garde le point bleu dans le cadre pendant la marche.
- En mode avion, cache vide, au zoom 26 : l'appli reste fluide.

### 3.7 Cache de la zone du site — P1 — M
[UX terrain, fiche J] [Décision D9] [EasyClaw : `ArchiveFileFactory`]

- `CacheManager.downloadAreaAsync` (osmdroid 6.1.20) télécharge une `BoundingBox` entre deux zooms. L'utiliser pour l'IGN et, avec un plafond, pour OSM. Le cache des tuiles existe déjà (`osmdroidTileCache` dans `MainActivity`).
- **On ne met en cache que la zone du site** (le rectangle), au zoom natif. Le sur-zoom agrandit ensuite ce qui est en cache.
- EasyClaw signale aussi `ArchiveFileFactory` d'osmdroid (archives de tuiles hors ligne), qui pourrait lire une archive préparée au bureau. Piste à évaluer, non validée.
- **Acceptation.** Après « Télécharger la zone » puis le mode avion, le fond de la zone s'affiche encore (flou en sur-zoom) et les vecteurs déjà enregistrés restent nets.

---

## 4. Synchro acoustique

### 4.1 Synchro multi-appareils — P1 pour la note de format, P2 pour le protocole complet — L
[UX terrain, fiche E] [EasyClaw : limite à 3 appareils et plus] [Décision D3, D6]

**Constat.**
- `SyncPanel` demande un code saisi, un « TOP manuel », puis « Émettre + écouter » ou « Écouter ».
- `AudioSync` joue un chirp linéaire de 2 à 6 kHz et de 60 ms (`Chirp`), 1,5 s après le début d'une fenêtre de 4 s, et cherche le motif par corrélation (`ChirpDetector`).
- L'émetteur et le récepteur ne sont pas symétriques.
- La propagation (environ 3 ms par mètre) et la latence du micro ne sont pas compensées ; le README le dit.
- Il n'y a ni état « Calé », ni compte des appareils, ni identifiant dans le signal.
- `SyncMethod` ne connaît que `MANUAL_TAP` et `AUDIO_CHIRP`, et le schéma les fige par un `enum` (`docs/session.schema.json`).

**Choix technique.**
- **Garder le chirp comme top temporel.** Ne pas le remplacer par une trame FSK : la corrélation d'un chirp donne un pic bien plus stable.
- Ajouter **juste après le chirp une courte trame d'identification** de quelques octets : identifiant d'appareil, modèle, compteur. ggwave (MIT) peut servir à cet encodage.
- Émettre et écouter deviennent **un seul bouton « Synchroniser »**.
- **Décalage d'horloge,** dans l'esprit de BeepBeep (Peng et al., SenSys 2007), mais pas comme télémètre :
  - chaque appareil enregistre en continu ;
  - il entend son propre chirp et ceux des autres, et note les index d'échantillons sur **sa** propre horloge ;
  - l'échange des index (dans la trame suivante, ou au bureau en important les exports) sépare la latence d'émission du temps de vol.
- **« Calé (N) »** signifie seulement : « j'ai reconnu N autres identifiants au-dessus du seuil de corrélation déjà utilisé (`snr`, `score`) ». Ce n'est pas une preuve à la milliseconde. Le résidu reste de l'ordre du temps de vol (environ 1 ms pour 3 m) tant que les enregistrements ne sont pas recoupés au bureau.
- **Limite relevée par EasyClaw.** À trois appareils ou plus, un seul chirp suivi d'une trame d'identifiant ne permet pas de distinguer « A a émis, B a reçu » de l'inverse sans l'**ordre relatif des chirps**. Il faut donc que la trame porte le compteur d'émission **et** les index des chirps entendus, puis documenter cette limite.
- Le code de synchro est généré (court, lisible), modifiable, et n'est plus obligatoire. Le top manuel reste dans un menu « secours ».
- Précision annoncée : ne pas promettre mieux que le matériel. Les horodatages sont en nanosecondes (`monotonicNs`), mais la latence réelle d'un micro Android est de l'ordre de la dizaine de millisecondes [EasyClaw PROPOSITIONS § 5.3].

**Format.**
- Ne pas ajouter de valeur à l'énumération `SyncMethod` dans un fichier 0.1 : le schéma la rejetterait (`enum`).
- Étendre `SyncEvent` et `AudioDetection` avec des **champs optionnels** : protocole, identifiants entendus, index du chirp local, index des pairs, compteur, ordre.
- Rédiger **`docs/SYNC_ACOUSTIQUE.md`** pour l'outil de bureau et l'appli iOS :
  - fréquences, durée, fenêtre de Hann, échantillonnage à 48 kHz ;
  - formule de corrélation et seuil ;
  - contenu de la trame d'identification ;
  - formule du décalage une fois les deux enregistrements réunis ;
  - **détection a posteriori des chirps dans un long enregistrement** (dictaphone, § 6.6).

**Usage avec le dictaphone.** Sur l'iPad, l'enregistrement continu contient lui-même les chirps de synchro. Leurs index d'échantillon dans le fichier audio servent d'ancre temporelle pour la transcription.

**Acceptation.**
- Deux téléphones, volume média audible, pièce calme, à 1 m : chacun n'a qu'un bouton « Synchroniser ». Au bout de quelques secondes, les deux affichent « Calé (2) ». Aucun code n'a été saisi.
- Micro coupé sur l'un : l'autre n'affiche pas « Calé » et propose le top manuel.
- Un ancien export 0.1 s'ouvre encore.

---

## 5. Réalité augmentée et calibration

### 5.1 Réticule, plans, état de calibration, mesure — P1 pour le réticule, P2 pour la mesure — M puis L
[UX terrain, fiche F] [EasyClaw décompilation T4]

**Constat.**
- `ArTracker` désactive la détection de plans (`PlaneFindingMode.DISABLED`) et ne dessine que le fond caméra (`BackgroundRenderer`, adapté des exemples ARCore).
- Il n'y a ni réticule, ni plan, ni test de visée, ni distance.
- L'état du suivi est une bannière au-dessus de la colonne, souvent hors de la vue caméra.
- Le message de calibration (résidu, échelle) est un texte dans la colonne.

**Proposition, par paliers.**
1. **Réticule** fixe au centre de la vue caméra, dessiné par Compose **par-dessus** la `GLSurfaceView`, et non dans le nuanceur. Sa couleur suit l'état : initialisation, suivi, perdu.
2. **Plans horizontaux réactivés** (`PlaneFindingMode.HORIZONTAL` seul, comme CamToPlan, pour ne pas chercher les murs inutilement). Ils sont dessinés avec le rendu d'exemple Apache-2.0 du SDK ARCore, en gardant l'en-tête de licence, sans embarquer toute l'appli d'exemple.
3. Après une calibration, afficher **sur l'image** « Calibré » avec le résidu en mètres et l'échelle mesurée, ou « Non calibré » tant qu'il n'y a pas d'événement `calibration`.
4. **Mesure entre deux points** : deux visées, deux ancres, distance euclidienne. C'est le geste des règles AR, écrit dans LumiSite. L'appli Measure de Google est propriétaire : on s'inspire du geste sans la décompiler.

**Acceptation.**
- Sur le 13T Pro, on voit le réticule, puis des plans au sol après un mouvement lent.
- Une calibration réussie change le libellé sur l'image.
- Deux visées sur une distance connue donnent un écart, noté dans `docs/TESTS_TABLETTE.md`.
- Sans ARCore, la vue caméra est absente et un texte le dit, comme aujourd'hui.

### 5.2 Robustesse ARCore reprise de CamToPlan et d'Aruler — P1 — M
[EasyClaw décompilation] Ce sont des principes à **réécrire**, pas du code à copier.

| # | Trouvaille | Où dans LumiSite | Gain |
|---|---|---|---|
| T1 / A5 | Poser un point ne doit jamais faire planter l'appli. Repli à une distance fixe de 50 cm quand aucun plan n'est trouvé, avec un drapeau `ESTIMATED` | `SessionController.kt`, `Model.kt` | Pose robuste pendant 3 h de terrain |
| T2 / A1 | `CameraConfigFilter` : 30 fps, `DepthSensorUsage.DO_NOT_USE`, puis **tri des configurations par surface d'image décroissante** avant `configure()` | `capture/ArTracker.kt` | Résolution maximale, suivi plus stable, moins de dérive |
| T3 / A2 | Revérifier `getDepthMode()` après `configure()` (bug ARCore connu qui réactive la profondeur) | `ArTracker.kt` | Configuration réellement appliquée |
| T4 / A2 | `PlaneFindingMode.HORIZONTAL` seul | `ArTracker.kt` | Performance |
| T5 / A4 | **Rejeter un hit-test à angle rasant** (\|produit scalaire avec la normale\| < 0,03, soit environ 1,7°) et afficher « Orientez l'appareil vers le sol » | `ArTracker.kt` + UI | Pas de point qui saute de plusieurs centimètres |
| — | Chaîne de repli d'Aruler : (1) hit-test ARCore sur les plans, accepté si `isPoseInPolygon` ; (2) sinon le plan du dernier objet ; (3) sinon le plan central de la vue ; (4) sinon « pas de cible ». Chaque niveau rend `null` proprement | `ArTracker.kt` | Aucun plantage |
| — | **Intersection analytique** rayon/plan (un rayon depuis le pixel, `t = (n·p0 − n·o) / (n·d)`), plutôt que la dichotomie de CamToPlan (jusqu'à 1 000 itérations pour 1 mm) | `core` (testable en JVM) | Exact et déterministe |
| A3 | **Torche** : bouton marche/arrêt, **allumée par défaut en faible luminosité** (`LightEstimationMode.ENVIRONMENTAL_HDR` donne l'intensité ambiante). Jamais allumée en permanence comme CamToPlan | `ArTracker.kt` + `SessionScreen.kt` | Le diagnostic d'éclairage se fait souvent dans le noir, le pire cas pour ARCore |
| — | Fréquence d'images : 30 fps par défaut, 60 fps en option (mouvements rapides) | `ArTracker.kt` | Réglable |

**À ne pas reprendre :**
- une classe placée dans le paquet `com.google.ar.core`, comme le fait Aruler : c'est l'espace de noms d'un tiers ;
- la torche allumée en permanence ;
- la dichotomie.

⚖️ **Synthèse de T1 avec le § 6.1** (les deux fiches semblaient se contredire) :
- Avec le **suivi AR actif**, une visée qui n'aboutit pas donne une position de repli à 50 cm, marquée `positionSource = "ESTIMATED"` et affichée d'une couleur distincte. `positionSource` est une chaîne libre (`Model.kt` l. 305) : le format 0.1 n'est pas cassé.
- **Sans suivi ni calibration**, et sans position manuelle, on ne crée pas de point fantôme : l'appli le dit (§ 6.1).
- Ce qu'un point `ESTIMATED` devient à l'export reste une question ouverte (§ 15.2).

**Tests `core` à écrire (A6)** :
- intersection analytique ;
- rejet à angle rasant ;
- tri des configurations ;
- repli `ESTIMATED` ;
- relecture d'une session 0.1 sans ces champs.

---

## 6. Relevé, objets et typologie

### 6.1 Relevé : position, édition, objets visibles — P0 pour l'enregistrement, P1 pour l'édition — M
[UX terrain, fiche G] [Contre-expertise : l. 266–278 confirmées]

**Constat.**
- `SessionController.survey` (l. 266–278) ne calcule `estimatedSite` que si `pose != null && seg != null`, c'est-à-dire avec un suivi et une calibration actifs. Sinon, `positionSource` vaut `"NONE"` ou `"AR_POSE"`, sans position dans le site.
- L'écran ne dessine un point que si `manualSite` ou `estimatedSite` est renseigné. Le clic « Enregistrer » écrit donc souvent un événement **sans coordonnées**, d'où l'impression que rien n'est sauvegardé.
- Les cases « position manuelle » et « trajectoire sans recalage » ne changent pas ce qui est enregistré : la seconde ne fait que masquer le trait gris.
- La page du site ne charge aucun relevé, seulement l'origine et l'OSM.
- On ne peut pas toucher un marqueur de relevé pour le sélectionner.
- Les types sont quatre pastilles écrites en dur : `listOf("LUMINAIRE", "MESURE", "OBSERVATION", "AUTRE")` (`ui/SessionScreen.kt`, l. 274).
- Le point bleu `current` n'a pas de cap, alors que `PoseGeometry.forwardPlaneAngle` sait le calculer.

**Proposition.**
- **Une seule fonction de position** pour l'écran et pour `derivedSurveyPoints` (§ 2.2).
- Si aucune position n'est calculable, l'écrire : « Suivi ou calibration manquant : touchez la carte ou cochez la position manuelle. » Le repli `ESTIMATED` ne s'applique qu'avec un suivi AR actif (§ 5.2).
- **Point courant** : un disque et un secteur de cap, calculés à partir de la pose AR passée par la transformation du site. Ajouter un bouton « Centrer / Suivre ».
- **Sélection d'un relevé** : le déplacer (en le glissant, ou par pas de 0,1 m / 0,5 m vers l'est ou le nord), modifier sa famille et son commentaire, le supprimer. Le journal reste en ajout seul : une modification est un nouvel événement qui remplace le précédent.
- **Palette de types :** le **catalogue canonique** (§ 6.3), chargé comme données, remplace les pastilles en dur.
- **Page du site** : afficher les relevés de toutes ses sessions.

**Acceptation.**
- Sans calibration et sans case manuelle, « Enregistrer » explique pourquoi et ne laisse pas de point fantôme.
- Avec calibration et suivi, le point apparaît tout de suite, survit à « Terminer » puis à la réouverture, et peut être déplacé ou supprimé.
- Le symbole suit le catalogue.

### 6.2 POINT CRITIQUE : `blockName = "LUMI_${kind}_GENERIC"` → noms de blocs canoniques — P0 — S
[EasyClaw mémoire] [Contre-expertise : **confirmé** le 29/10/2026]

**Constat vérifié.**
- Fichier : **`app/src/main/java/app/lumisite/android/SessionController.kt`**, commit `1fb8fc52937be81f0f0d521f62db74349bf76c4d`, **ligne 275** :
  ```kotlin
  dxf = DxfHint(layer = "LUMI_${kind}", blockName = "LUMI_${kind}_GENERIC"),
  ```
- `kind` ne peut valoir que `LUMINAIRE`, `MESURE`, `OBSERVATION` ou `AUTRE` (`SessionScreen.kt`, l. 274). Tous les luminaires partagent donc le même `blockName` (`LUMI_LUMINAIRE_GENERIC`) et le même calque (`LUMI_LUMINAIRE`). Aucun de ces noms n'existe dans le gabarit de Sébastien, et aucun n'est sur `RES - Elec`.
- `DxfHint` est défini dans `core/src/main/kotlin/app/lumisite/core/model/Model.kt`, l. 319 : `data class DxfHint(val layer: String, val blockName: String, val rotationDeg: Double? = null, val attributes: Map<String, String> = emptyMap())`.
- Le schéma n'impose sur `dxf` que `layer` et `blockName` ; `kind` est une chaîne libre. Remplacer ces valeurs **ne change donc pas le format 0.1**.

**Mapping proposé.** L'appli n'écrit toujours pas de DXF (D1). `DxfHint` et le GeoJSON portent seulement le **nom canonique** du catalogue.

```kotlin
// SessionController.survey : la famille vient du catalogue (§ 6.3), plus d'une pastille en dur
fun survey(familyId: String, note: String?, manual: ReferencePoint?, details: JsonObject? = null) {
    val fam = catalog.require(familyId)            // entrée de catalog/equipment_catalog.json
    // … calcul de position inchangé (fonction unique, § 2.2) …
    val e = SurveyEvent(
        id = Ids.new("srv"), t = stamps.now(),
        kind = fam.blockName,                       // ex. "spot", "bloc secours", "tableau divisionnaire"
        // … champs de position inchangés …
        payloadSchema = "lumisite-equipment/1",     // champ existant, optionnel
        payload = buildJsonObject {                 // champ existant, optionnel
            put("catalog_version", catalog.version)
            put("block_name", fam.blockName)
            details?.let { put("details", it) }     // propriétés facultatives (§ 6.4)
        },
        dxf = DxfHint(layer = fam.defaultLayer,     // "RES - Elec"
                      blockName = fam.blockName),   // nom CANONIQUE, jamais "_GENERIC"
    )
}
```

**Table de correspondance pour les sessions déjà enregistrées** (lue par l'outil de bureau, sans réécrire le journal) :

| `blockName` ancien | Traitement au bureau |
|---|---|
| `LUMI_LUMINAIRE_GENERIC` | Pas de bloc déductible. L'objet va dans la liste « à classer », où Sébastien choisit la famille (spot, projecteur, …). |
| `LUMI_MESURE_GENERIC` | Feature `height` si la note contient une hauteur, sinon annotation `texte`, à valider. |
| `LUMI_OBSERVATION_GENERIC` | Feature `annotation` (`annotation_type = texte`). |
| `LUMI_AUTRE_GENERIC` | Liste « à classer ». |
| Tout nom hors catalogue | Liste « hors catalogue » du rapport de génération (§ 9.6), jamais inséré en silence. |

**Acceptation.**
- Un spot posé produit un `SurveyEvent` avec `kind = "spot"` et `dxf.blockName = "spot"`, `dxf.layer = "RES - Elec"`, puis une Feature GeoJSON avec `block_name = "spot"`.
- Aucun `_GENERIC` dans une nouvelle session.
- Une session 0.1 ancienne se relit.
- Test JVM : chargement du catalogue et refus d'une famille inconnue.

### 6.3 Catalogue canonique, liste fermée des blocs de Sébastien, table d'alias — P1 — M
[UX terrain, fiche P] [Analyse DWG § 2.5 et § 4.a.7] [Décisions D4, D5]

⚖️ **Tranché (décision D4), malgré le désaccord d'EasyClaw.**
- L'analyse DWG (§ 2.5 et § 4.a.7) demandait de « reprendre ces noms exactement », fautes comprises. Après la contre-expertise, **EasyClaw propose toujours de garder les noms fautifs comme référence**, de peur d'une légende éclatée en deux lignes.
- **Position retenue, conforme à D4 :**
  - les blocs sont **renommés une seule fois dans le gabarit** ;
  - l'outil de bureau **normalise les anciens noms via les alias à l'import** ;
  - un plan produit ne contient donc **qu'un nom par bloc**, et la légende « Block Legend » reste juste.
- Le risque soulevé par EasyClaw est couvert par un **test d'acceptation** : aucun dessin produit ne contient à la fois l'ancien nom et le nouveau (§ 9.8).
- **La saisie terrain accepte les deux formes** : un ancien nom saisi, lu ou importé est traduit vers le nom canonique par les alias, et seul le nom canonique est écrit dans le GeoJSON.

**Fichier** `catalog/equipment_catalog.json` : versionné (par exemple `lumisite-catalog-1.0`), partagé par l'appli et l'outil de bureau, et **mis à jour à chaque ajout de bloc** (D5). Chaque entrée contient :
- `block_name` (canonique) et `aliases` (anciens noms) ;
- `label` (affiché) et `famille` ;
- `default_layer` (`RES - Elec`) et `orientable` ;
- `scale_rules` (table de présentation, § 9.4) ;
- `details_schema` (propriétés facultatives) ;
- `icon` et `keywords` (mots-clés pour la voix, § 6.6).

**Les 25 noms de blocs exacts lus dans le DWG** (hors xref `02-Plan au sol`), tels qu'EasyClaw les a relevés et qu'ils figurent dans l'analyse DWG :
- `Amplificateur`, `Camera`, `Diffuseur Sonore Radio`, `Déclencheur Manuel`, `Lanterne EP moderne`, `Lustre`, `PDL` ;
- `bloc secour`, `bougeoir électrifié`, `boîte de dérivation`, `enceinte`, `extincteur`, `haut-parleur`, `hublot ecl` ;
- `interrupteur`, `lampe`, `lineaire fluo`, `micro col`, `prise xlr`, `prise électrique`, `projecteur` ;
- `projecteur thorn contrast`, `spot`, `tableau divisionaire`, `tgbt`.

**Renommage : il se limite aux six noms fautifs.** Toutes les autres entrées gardent **exactement** leur nom du DWG, y compris la casse (`Amplificateur`, `Lustre`, `prise xlr`…).

| Nom du DWG (alias) | Nom canonique | Statut |
|---|---|---|
| `bloc secour` | **`bloc secours`** | **Validé** (D4) |
| `tableau divisionaire` | **`tableau divisionnaire`** | **Validé** (D4) |
| `Camera` | `caméra` | Proposé, en attente de Sébastien |
| `hublot ecl` | `hublot éclairage` | Proposé, en attente de Sébastien |
| `lineaire fluo` | `linéaire fluo` | Proposé, en attente de Sébastien |
| `tgbt` | `TGBT` | Proposé, en attente de Sébastien |

- **`prise électrique` et `boîte de dérivation` sont déjà corrects** et ne doivent pas être renommés.
- **`micro col` reste écrit tel quel** tant que Sébastien n'a pas tranché (le sens « col de cygne » n'est pas confirmé).
- **Tant qu'un renommage n'est pas validé,** l'entrée garde le nom du DWG comme `block_name`. Le nom proposé n'est pas utilisé.

**Catalogue initial `lumisite-catalog-1.0`** (293 INSERT dans `eclairage03`) :

| `block_name` | `aliases` | Nb à SBC | Échelles observées | Orientable |
|---|---|---|---|---|
| `Amplificateur` | — | 1 | 1 | non |
| **`bloc secours`** | `bloc secour` | 5 | 1 | oui |
| `boîte de dérivation` | — | 43 | 1 (×12) ; 0,5 (×31) | non |
| `bougeoir électrifié` | — | 1 | 1 | non |
| `Camera` | (`caméra` si validé) | 3 | 1 | oui |
| `Déclencheur Manuel` | — | 2 | 1 | non |
| `Diffuseur Sonore Radio` | — | 2 | 1 | non |
| `enceinte` | — | 2 | 1 | non |
| `extincteur` | — | 4 | 0,6 | non |
| `haut-parleur` | — | 8 | 1 ; miroir ×2 | oui |
| `hublot ecl` | (`hublot éclairage` si validé) | 1 | 1 | non |
| `interrupteur` | — | 13 | 1 ; miroir ×2 | non |
| `lampe` | — | 8 | 1 | non |
| `Lanterne EP moderne` | — | 1 | 1 | non |
| `lineaire fluo` | (`linéaire fluo` si validé) | 3 | 1 ; 1,67 | oui |
| `Lustre` | — | 2 | 1 | non |
| `micro col` | — (tel quel) | 1 | 1 | non |
| `PDL` | — | 2 | 1 | non |
| `prise électrique` | — | 13 | 1 ; miroir ×1 | oui |
| `prise xlr` | — | 6 | 1 | non |
| `projecteur` | — | 10 | 1 | oui |
| `projecteur thorn contrast` | — | 34 | 0,571 (miroir ×29) | oui |
| `spot` | — | 122 | 1,208 / 2,415 / 3,614 (purement graphiques) | oui |
| **`tableau divisionnaire`** | `tableau divisionaire` | 3 | 1 | non |
| `tgbt` | (`TGBT` si validé) | 3 | 1,417 | non |

- Quand un renommage en attente est validé, le nom canonique devient `block_name`, l'ancien passe dans `aliases`, et le catalogue change de version.
- AutoCAD ne distingue pas la casse dans les noms de blocs : `tgbt` → `TGBT` ne change que l'affichage de la légende.
- La colonne « Orientable » vient de l'analyse DWG (rotation porteuse d'une visée).
- Le catalogue ajoute aussi « autre (à classer) », qui ne correspond à aucun bloc et part dans la liste « à classer » du bureau.

**Renommage dans la bibliothèque modèle** (au bureau, sur une copie) :
- renommer **une seule fois** les blocs du gabarit de Sébastien (`RENAME` dans AutoCAD, ou `doc.blocks.rename_block` d'ezdxf) : les deux noms validés tout de suite, les quatre autres après validation ;
- puis vérifier la légende ;
- les anciens DWG restent lisibles, car l'outil de bureau applique la table d'alias à la lecture ;
- un ancien plan peut être mis à niveau par le même script (renommage et nouvelle légende), toujours sur une copie.

**Évolution du catalogue.** On ne crée un nouveau bloc que si un sous-type validé revient souvent (statistiques du § 6.6). Chaque ajout de bloc dans le gabarit donne une nouvelle version du catalogue, qui est poussée vers le sélecteur terrain.

**Acceptation.**
- Le sélecteur propose exactement les 25 familles (plus « autre »), avec `bloc secours` et `tableau divisionnaire`.
- Saisir ou importer `bloc secour` donne `block_name = "bloc secours"`.
- Un ancien DWG contenant `bloc secour` est compté sous `bloc secours` par l'outil de bureau.
- Le gabarit ne contient plus `bloc secour` ni `tableau divisionaire`. `prise électrique` et `boîte de dérivation` n'ont pas changé.
- Aucun dessin produit ne contient à la fois un ancien nom et le nouveau.
- Ajouter une entrée au JSON la fait apparaître dans le sélecteur sans recompiler l'écran.

### 6.4 Saisie rapide et propriétés facultatives — P1 — M
[UX terrain, fiche P] [EasyClaw PROPOSITIONS § 4] [Décisions D6, D7]

- **Famille en 1 tap** : une grille des familles, avec une icône et un mot français. En tête, les **favoris** épinglés et les **6 derniers utilisés**. On pose au réticule ou au toucher (§ 3.6).
- **Visée** (familles orientables) : on la règle en glissant autour du symbole, ou on prend le cap de l'appareil au moment de la pose.
- **Détails facultatifs, jamais bloquants,** en second niveau :
  - `sous_type` : une liste courte par famille, par exemple pour le spot : PAR20, MR16 dichroïque, GU10 LED, LED basique, autre ;
  - `technologie` : LED, halogène, fluo, iodures, incandescence ;
  - `culot` : GU10, GU5.3, E27, E14, R7s, G13… ;
  - `puissance_W`, `temp_couleur_K` ;
  - `ref_etiquette` (texte lu sur l'étiquette) ;
  - `etat` ;
  - `status` : `existant`, `projeté`, `à déposer`, `en défaut`.
- La dernière valeur saisie est proposée par défaut pour l'objet suivant de la même famille.
- **Règles reprises d'EasyClaw :**
  - une puissance non renseignée reste `null`, **jamais 0** ;
  - les valeurs aberrantes sont refusées dès la saisie (puissance négative, 12 000 K) ;
  - `payloadSchema` est versionné.
- **Tailles et miroirs ne sont pas des données terrain** (D7). Les 3 tailles de spot servent la lisibilité pour le client et correspondent à des technologies différentes. L'outil de bureau les applique d'après le sous-type (§ 9.4).
- **Annotations sans équipement** (« Passage de câble », « Câble à nu », « En default », « Capteur », « Cablette de terre », « Niveau ») : 1 tap dans une liste courte, puis un texte facultatif.

**Acceptation.**
- Poser un spot demande un tap sur la famille et un tap sur la carte.
- Les détails peuvent rester vides sans bloquer l'enregistrement.
- Les favoris et les derniers utilisés sont en tête de la grille.

### 6.5 Notes : texte, voix, photo — P1 — M
[UX terrain, fiche P] [EasyClaw PROPOSITIONS § 5 palier 1, § 8 question 4] [Décision D2, D6]

- Les **notes sont attachées à l'objet** :
  - une **note texte** ;
  - une **note vocale** : appui long sur l'objet, puis un clip court `vn_<n>.m4a` avec `start_utc` et `device_monotonic_ns` ;
  - une **photo d'étiquette** ou une vue (`role: "etiquette"` ou `"vue"`), accompagnée de la pose de l'appareil.
- **Photos.** ⚖️ **Tranché.** Le README dit qu'« aucune image n'est enregistrée », et EasyClaw posait la question (§ 8, Q4). La décision D2 tranche : **les photos sont enregistrées en fichiers séparés**, référencées par un chemin relatif et leur `sha256`, jamais en base64. En format 0.1, elles passent par `SurveyEvent.attachments` (champ existant : `file`, `mediaType`, `sha256`).
- La lecture d'étiquette (OCR) se fait au bureau, sous forme de proposition seulement.
- La transcription des notes vocales se fait au bureau avec Whisper local. Il n'y a pas de reconnaissance vocale sur l'appareil : elle demanderait le réseau ou un modèle de plus de 100 Mo, et reconnaîtrait mal le vocabulaire technique [EasyClaw § 5.2].

**Acceptation.** Une note vocale et une photo d'étiquette sont attachées à l'objet et présentes dans le paquet, avec leur `sha256`.

### 6.6 Dictaphone continu sur l'iPad, transcription au bureau, rattachement aux objets — P1 pour le format, P2 pour l'outil — L
[UX terrain, fiche Q] [Décision D6]

**Besoin.**
- Pendant la visite, Sébastien porte un iPad (LumiSite iOS), synchronisé par la synchro acoustique (§ 4) avec les autres appareils.
- Il enregistre en continu, comme un dictaphone : « là je vois devant moi un spot dichroïque, je lis l'étiquette… ».
- Au bureau, la transcription Whisper locale, horodatée sur le time-code commun, rattache chaque phrase à l'objet relevé au même moment et au même endroit.

**Format audio** (dans le paquet, `audio/<device_id>/<session_id>/`).
- **Enregistrement continu** en morceaux de 10 minutes `dict_<n>.flac` : FLAC mono, 48 kHz, 16 bits, sans perte, environ 3 Mo par minute.
  - Le 48 kHz garde la détection des chirps de 2 à 6 kHz.
  - Un morceau fermé est définitif : une coupure ne fait perdre que le morceau en cours.
  - Au bureau, un ré-échantillonnage à 16 kHz alimente Whisper.
- **`dictaphone.json`**, une entrée par morceau :
  - `{file, sha256, sample_rate, start_device_monotonic_ns, start_utc, n_samples}` ;
  - `chirps: [{sample_index, sync_id, peer_device_id, score}]`, c'est-à-dire les chirps **détectés dans l'enregistrement lui-même**. C'est la meilleure ancre temporelle : on passe de l'index d'échantillon au temps commun sans dépendre de l'horloge système.
- **Pose de l'iPad** (si ARKit est actif) : des Features légères `kind: "track"`, une par seconde (position et cap), dans le `.geojsonl` de sa session.
- **Transcription** (au bureau) : `transcript_<n>.json` par morceau, avec les segments et les mots horodatés (`start_s`, `end_s`, `text`, `confidence`), convertis en temps commun, et la version du modèle notée.

**Règle de rattachement** (proposition, réglable).
1. **Instant** : chaque segment Whisper correspond à un temps t, le milieu du segment.
2. **Candidats dans le temps** : objets dont `captured_utc` est compris entre t − 30 s et t + 60 s.
3. **Candidats dans l'espace** : objets à moins de 8 m du locuteur à l'instant t. On utilise la pose de l'iPad, ou à défaut celle de l'appareil appairé du même opérateur. Les objets situés **devant** lui (cône de ±35° autour du cap) sont favorisés quand la phrase contient « devant moi », « là » ou « au-dessus ».
4. **Famille** : un dictionnaire de mots-clés par famille et sous-type (« spot », « dichro », « MR16 », « PAR 20 », « GU10 », « projecteur », « Thorn », « bloc secours », « BAES »…) augmente le score des objets de la bonne famille. Il repère aussi un objet **non relevé** (« il y a aussi un extincteur »), qui devient une proposition d'objet sans position.
5. **Score** : il combine l'écart de temps, la distance et la famille. Au-dessus d'un seuil, la phrase est proposée sur le meilleur candidat. Si deux candidats sont proches, la proposition est marquée « à choisir ».
6. **Extraction** : sous-type, technologie, culot, puissance (« 50 watts », « 35 W »), température de couleur, texte d'étiquette lu, état (« en défaut », « HS »). Le texte brut du segment est toujours gardé en note.

**Validation humaine (obligatoire, D6).**
- Rien n'est écrit dans le DXF sans validation.
- Un écran au bureau liste chaque proposition avec : le segment audio réécoutable, la transcription, l'objet candidat sur la carte, la photo s'il y en a une, et les valeurs proposées.
- Actions possibles : accepter, corriger, réattribuer à un autre objet, rejeter.
- Le résultat est une révision (`rev` + 1, source `voice`, `segment_id`), tracée dans le rapport de génération.
- **Affiner les blocs ensuite** : l'outil compte les sous-types validés par famille et par projet. Si un sous-type revient souvent, Sébastien peut décider d'un nouveau bloc ou d'une taille de présentation. Les anciens objets se reclassent par sous-type, sans retour sur le terrain.

**Confidentialité et consentement.**
- Un enregistrement continu dans un lieu ouvert (cathédrale, chantier) peut capter des tiers : personnel, entreprises, visiteurs. La voix est une donnée personnelle.
- Informer les personnes présentes et obtenir leur accord. Un témoin d'enregistrement reste toujours visible, en plus de l'indicateur iOS.
- Un bouton **pause micro** en 1 tap.
- Traitement **local uniquement** (Whisper sur le poste).
- Possibilité de supprimer un passage au bureau.
- Durée de conservation limitée : l'audio brut est supprimé après validation, sauf besoin justifié.
- À faire relire au regard du RGPD avant tout usage chez un client.

**Acceptation.**
- Visite de test de 30 minutes, avec un téléphone de relevé et un iPad dictaphone synchronisés par chirp.
- Au bureau, les chirps sont retrouvés dans le FLAC et le décalage est affiché.
- « Spot dichroïque MR16, 50 watts », prononcé à moins de 30 s de la pose d'un spot, est proposé sur le bon objet dans au moins 8 cas sur 10.
- Seules les propositions validées apparaissent dans les ATTRIB.
- Une coupure ne fait perdre que le morceau de 10 minutes en cours.
- La pause micro fonctionne et le témoin est visible.

### 6.7 Zonage (secteurs architecturaux) — P1 — M
[EasyClaw PROPOSITIONS § 1, écart E1 du cahier des charges]

**Constat.** Le modèle ne connaît que des relevés ponctuels. Il n'y a aucune notion de zone (cloître, chœur, salle), alors que le cahier des charges demande de « répertorier par zone les points ».

**Proposition**, mise en cohérence avec les décisions :
- **Dans l'export GeoJSON**, une zone est une Feature `kind: "zone"` (Polygon, `zone_id`, `label`, `zone_kind` ∈ ARCHITECTURAL, TECHNIQUE, CIRCULATION, EXTERIEUR, `parent_zone_id`, `source` ∈ MANUAL_MAP, PLAN_IMPORT). Elle est tracée avec l'outil polygone du § 7.
- **Rattachement d'un relevé à sa zone** : calculé, et non stocké dans le relevé. Un test point dans polygone, sur la zone la plus profonde, dans `derived/` ou au bureau.
- **Points d'attention :**
  - sens des sommets explicite ;
  - cas concave, et cas avec trou (cour du cloître) ;
  - un polygone qui se recoupe est refusé à la saisie, jamais corrigé en silence ;
  - `AR_WALK` (tracer une zone en marchant) est repoussé, parce que la dérive ARCore n'est pas mesurée.
- **Tests :** convexe, concave, avec trou, hors zone (`null`), zones imbriquées (la plus profonde l'emporte), polygone qui se recoupe (rejeté avec avertissement).
- ⚖️ **Format.** EasyClaw propose un nouvel événement `ZoneEvent`. Dans le format 0.1, un nouveau `type` casserait la lecture par une version plus ancienne (§ 0.3). Il faut donc soit le livrer avec un format 0.2 et le lecteur à jour, soit porter la zone sur un `SurveyEvent` de `kind = "zone"`, avec la géométrie dans `payload`. Le choix revient à Devin (§ 15.2).

---

## 7. Annotation façon Xodo

[UX terrain, fiche K] [EasyClaw PROPOSITIONS § 9] [Décisions D1, D11]

### 7.1 Ce qui a été analysé dans Xodo 11.3.1, et ce qu'on en retient

- **Approuvé (D11).** On analyse l'APK officiel de Xodo 11.3.1 pour son **comportement et son ergonomie**. **Aucun code, aucune icône, aucun XML et aucune chaîne n'est copié.**
- **Lecture des ressources** [UX terrain] : `resources.arsc` et les XML binaires des layouts et des menus, décodés avec androguard. On en retient l'organisation du geste et le vocabulaire français.
- **Lecture du code décompilé** [EasyClaw] : les classes `com.pdftron.*` (environ 1 900, le SDK PDFTron/Apryse, propriétaire), lues pour quelques constantes de comportement.
- **Xodo ne contient aucun code DXF ni CAO.** Le mot « DXF » apparaît 0 fois dans les 15 833 + 16 811 fichiers `.java` décompilés. Ses « calques » sont les OCG du format PDF (`com.pdftron.pdf.ocg.*`). **Il n'y a rien à récupérer en DXF** : Xodo sert de source d'ergonomie, jamais de source de code [EasyClaw].
- **Le geste, en une phrase.**
  - Une barre d'icônes choisit l'outil, qui reste actif jusqu'à ce qu'on en prenne un autre.
  - Une sélection ouvre un menu court : style, commentaire, copie, suppression.
  - Le style est une feuille : couleur de trait, couleur de remplissage, épaisseur, opacité, préréglages.
  - Une liste à part montre toutes les annotations, filtrable, avec un état vide explicite.
- **Vocabulaire français** repris : Annoter, Dessiner, Mesurer, Style, Trait, Remplissage, Opacité, Préréglages, Commentaire, Supprimer, Copier. LumiSite met **toujours un mot à côté de l'icône**, parce que l'appli sert avec des gants et au soleil.
- **Comportements relevés par EasyClaw** (description du geste, pas de code) :
  - une forme simple se crée en glissant d'un coin à l'autre **ou** par un simple tap, qui donne une taille par défaut (demi-largeur de 50) ;
  - un polygone se construit point par point, chaque point pouvant être annulé ;
  - un nuage de révision est un contour festonné ;
  - un texte libre peut avoir une bulle de rappel (poignée de flèche) ;
  - une note ponctuelle a un jeu fermé de 10 icônes ;
  - un outil de comptage pose des marqueurs étiquetés, regroupés, avec un total ;
  - le trait libre est sous-échantillonné, lissé et sensible à la pression ;
  - la palette compte 26 couleurs, dont « sans remplissage » (contour seul) ;
  - la barre par défaut contient : surlignage, souligné, barré, vague, texte, trait libre, surligneur libre, gomme, annuler.
- **Laissé de côté** (ce n'est pas un PDF) : caviardage, formulaires, signature, tampons réglementaires, aplatissement, lecture vocale, OCR.

### 7.2 Spécification LumiSite (synthèse des deux propositions) — P1 — L

Une barre unique, sous la carte ou à côté, qui ne recouvre ni la carte ni la caméra (§ 11.1) :

| Outil | Geste | Sortie (Feature GeoJSON, § 8) |
|---|---|---|
| **Se déplacer** | La carte suit le doigt, le réticule est inactif | — |
| **Poser un objet du catalogue** | Famille en 1 tap (§ 6.4), puis un toucher ou la croix centrale | `equipment` avec son `block_name` canonique |
| **Forme + texte** (cercle, rectangle) | Glisser d'un coin à l'autre, **ou** tap pour une taille par défaut ; le libellé se place au centre | `shape` (Polygon) avec `label` |
| **Ligne, polyligne, flèche** | Points successifs, puis « Fin » ou un second toucher | `shape` (LineString) |
| **Polygone / zone** | Points successifs, double-tap pour fermer | `zone` ou `shape` (Polygon) (§ 6.7) |
| **Annotation de diagnostic** | 1 tap dans la liste courte (« Passage de câble »…) | `annotation` |
| **Commentaire** | Texte court attaché à l'objet, et non note flottante | `note` sur la Feature |
| **Sélection** | Poignées pour déplacer et redimensionner ; boutons supprimer, dupliquer, style. Pas de groupe au premier jet | `rev` + 1, `op = update` ou `delete` |
| **Style** | Une feuille avec trois réglages visibles : trait, remplissage (transparent compris), épaisseur. Opacité au second niveau | `style` sur la Feature |
| **Annuler** | Dernier geste | — |

- **Couleurs.** Six couleurs nommées (noir, rouge, orange, jaune, vert, bleu), plus « récent » et « sans remplissage ». Une palette étendue (EasyClaw cite 26 couleurs) peut venir en second niveau. Un **préréglage** mémorise le trio couleur, épaisseur et type. Une seule police, lisible.
- **Liste des objets du relevé.** Toucher une ligne sélectionne l'objet sur la carte. État vide : « Aucun objet sur ce relevé ».
- **États de l'outil**, un seul actif à la fois : aucun, en train de tracer, objet sélectionné, feuille de style ouverte, saisie de commentaire. Changer d'outil termine ou annule le tracé en cours, avec une confirmation si des points sont déjà posés.
- **Hors du premier jet** (à décider, § 15.1) : nuage de révision, bulle de rappel, note à icônes, trait libre sensible à la pression et gomme.
- **Implémentation.**
  - Un overlay Compose ou osmdroid dessiné par LumiSite, avec les icônes Material (Apache-2.0, déjà une dépendance).
  - Les géométries sont stockées **en mètres dans le repère du site (`Enu`)**, pas en pixels.
  - Les nouveaux champs sont optionnels, pour que les sessions 0.1 restent lisibles. Les documenter dans `SESSION_FORMAT.md`.
  - EasyClaw propose une géométrie en coordonnées du plan, plus un `site: Enu` optionnel. Ce double repère n'est utile que si un plan est affiché sur la tablette (question ouverte, § 15.2).
- **Pas de DXF dans l'appli (D1).**
  - ⚖️ **Tranché.** EasyClaw (§ 9.4) envisageait un DXF « côté tablette plus tard » en Kotlin, et l'ancienne fiche K faisait écrire le DXF par l'appli. **Ces deux pistes sont abandonnées.**
  - L'appli exporte chaque objet ou forme comme une Feature `equipment`, `annotation`, `shape` ou `zone`. L'outil de bureau les traduit en entités AutoCAD (§ 9.4).

**Acceptation.**
- On pose un rectangle « local TGBT » avec le commentaire « à vérifier ». On le déplace, on l'agrandit, on le supprime, on annule.
- L'export GeoJSON contient la Feature `shape` avec son libellé, son commentaire et sa géométrie en WGS84 et en mètres locaux.
- Un spot posé y figure avec son `block_name` canonique.
- Une session enregistrée avant cette version s'ouvre encore.
- Aucun DXF n'est produit par l'appli.

### 7.3 Le PoC DXF d'EasyClaw : ce qu'il prouve, et où il sert

[EasyClaw PROPOSITIONS § 9.4]

- **Le PoC.** Script `D:\stockage\agraver\_poC\gen_blocs_dxf.py`, exécuté hors du dépôt.
  - Sortie : `poC_blocs_annotes.dxf`, 5 168 octets, DXF R12 (AC1009), écrit à la main sans bibliothèque. Rendu : `poC_blocs_annotes.png`.
  - Audit ezdxf 1.4.5 : **0 erreur, 0 correction**.
  - 4 blocs (`LUMI_CERCLE_TXT`, `LUMI_CARRE_TXT`, `LUMI_PRISE`, `LUMI_NUAGE`) et 5 INSERT avec un attribut `ID` (L1, L2, P12, R1, L3), en rotation 0, 15 et 90°.
  - Le texte est lisible au centre de chaque forme.
- **Ce qu'il prouve.** La structure nécessaire (BLOCK + ATTDEF + INSERT + ATTRIB + SEQEND) est simple et se relit sans erreur.
- **Statut.** Le branchement du PoC sur une vraie session **attend toujours l'accord explicite de Sébastien** (D12). Pour l'instant, EasyClaw a seulement lu une **copie** du DWG, en lecture seule : `D:\stockage\agraver\_poC\sbc\eclairage03_COPIE.dwg`.
- **Où il sert.** **Au bureau uniquement** (D1), comme base de tests de l'outil (§ 9). La sortie réelle part du **gabarit de Sébastien** (AC1015) et de **ses blocs**, et non de blocs `LUMI_*` créés à part.
- **Les blocs `LUMI_CERCLE_TXT` et `LUMI_CARRE_TXT`** pour les formes annotées n'existent pas dans le gabarit. Ils ne seront utilisés que si Sébastien les ajoute à sa bibliothèque ; sinon, les formes sortent en entités simples (§ 9.4). Question ouverte, § 15.1.

---

## 8. Formats et export GeoJSON

[UX terrain, fiches N et B] [Analyse DWG § 4.a] [Décisions D1, D2]

### 8.1 Principe

- L'appli n'écrit **pas** de DXF.
- Le relevé de plusieurs appareils (Android, iPad et iPhone sous iOS, autres capteurs) est fusionné au bureau. L'export terrain est donc un **paquet GeoJSON**.
- L'export actuel `lumisite.session` (journal, poses, `files[]` avec hash, `dxfPreparation`) **reste** : il est rangé tel quel dans `raw/`, comme source brute pour recalculer.

### 8.2 Organisation du paquet (gros projets, résistant aux coupures)

```
LumiSite_<project_id>/                 (dossier, ou .zip « store » sans recompression des JPEG/FLAC)
├── manifest.json                      version du schéma, appareils, sessions, sha256 de chaque fichier
├── catalog/equipment_catalog.json     catalogue canonique + alias (§ 6.3), versionné
├── sessions/<device_id>/
│   ├── <session_id>.geojsonl          GeoJSONSeq (RFC 8142) : 1 Feature par ligne, écrite en ajout pendant la session
│   ├── <session_id>.frames.json       repère du site, transformations AR → site, événements de synchro
│   └── <session_id>.geojson           (option) FeatureCollection consolidée, régénérée à la clôture
├── photos/<device_id>/<session_id>/<photo_id>.jpg
├── audio/<device_id>/<session_id>/    notes vocales vn_<n>.m4a, dictaphone dict_<n>.flac + dictaphone.json (§ 6.6)
├── raw/<device_id>/<session_id>.zip   export lumisite.session inchangé (§ 1.1)
└── refs/                              osm_source.json (§ 3.1), éventuel extrait du plan de l'architecte
```

### 8.3 Règles d'écriture

- **GeoJSONSeq.**
  - Chaque Feature est une ligne autonome terminée par `\n`, ajoutée avec un `fsync` après chaque objet. Le fichier n'est jamais réécrit pendant la session.
  - Après une coupure, seule la dernière ligne peut être tronquée : le lecteur l'ignore et le signale.
- **Journal en ajout seul.** Une modification ou une suppression ajoute la même Feature (même `id`) avec `rev` + 1 et `op` = `update` ou `delete`. L'état courant est la dernière `rev` de chaque `id`.
- **Un fichier par appareil et par session.** Il n'y a jamais d'écriture concurrente ; la fusion se fait au bureau.
- **Photos et audio** : jamais en base64. Ce sont des fichiers à part, référencés par un chemin relatif à la racine du paquet, avec leur `sha256`.
- **Identifiants** : UUID v7, triables par date et uniques dans le projet. ⚖️ L'analyse DWG acceptait aussi `"<device_id>:<compteur>"` ; on retient UUID v7, comme dans la fiche N.
- **Lecture** en flux, ligne par ligne. 10 000 objets représentent de l'ordre de 10 à 20 Mo de texte.
- **Encodage** UTF-8. Les accents des noms de blocs sont conservés.
- **Manifeste** : écriture atomique (fichier temporaire puis renommage), à chaque clôture de session et à chaque export.

### 8.4 Systèmes de coordonnées

- **`geometry` = WGS84 lon/lat (EPSG:4326), conforme à la RFC 7946**, sans membre `crs`.
  - L'altitude, si elle est connue, va en 3e coordonnée (ellipsoïdale).
  - `geometry` vaut `null` si la position absolue n'est pas connue (la RFC le permet).
- **`properties.local` = repère du site en mètres** : `{frame_id, x, y, z}`, avec X = est, Y = nord, Z = haut, et l'origine du site comme origine (repère `Enu` du code). En intérieur, c'est la **donnée maîtresse**.
- **`*.frames.json`** décrit :
  - l'origine du site en WGS84 ;
  - le repère de session ARCore ou ARKit ;
  - les transformations AR → site (calibration, recalage, résidu) ;
  - les événements de synchro ;
  - le cas échéant, une transformation déjà calculée : `helmert2d` avec `tx`, `ty`, `theta_deg`, `scale`, `rms_m` et `control_point_ids`.
- **Option `properties.plan`** : `{plan_id, x, y}`, si l'appareil a déjà été recalé sur un plan affiché. Le bureau peut recalculer ce recalage.
- **Lambert 93 (EPSG:2154)** n'est **pas** écrit par l'appli : il est calculé au bureau (§ 9.5).

### 8.5 Types de Feature (`properties.kind`)

| kind | geometry | Équivalent dans le DWG de Sébastien |
|---|---|---|
| `equipment` | Point | INSERT d'un bloc sur `RES - Elec` |
| `annotation` | Point (ancre) | MULTILEADER « Passage de câble », « Câble à nu », « En default »… |
| `height` | Point | MTEXT « H 15.72 m », « H 6.63 m Sous chauffage » sur `cote` |
| `shape` | LineString / Polygon | Formes du § 7 (par exemple le rectangle « local TGBT ») ; comme les petits contours LWPOLYLINE de `RES - Elec` (« Vitrine ») |
| `zone` | Polygon | Zone ou local (§ 6.7) ; absente du DWG actuel |
| `control_point` | Point | Point homologue de recalage (OSM, plan) |
| `route` | LineString | Câble ou chemin de câbles ; absent du DWG actuel, prévu |
| `track` | Point | Pose de l'iPad toutes les secondes (§ 6.6) ; pas de sortie DXF |

### 8.6 Propriétés d'un `equipment`

| Propriété | Obligatoire | Description |
|---|---|---|
| `rev`, `op` | oui | Révision ; `create`, `update` ou `delete`. L'identifiant est l'`id` de la Feature |
| `kind` | oui | `equipment` |
| `block_name` | oui | Nom **canonique** du bloc, pris dans le catalogue (`projecteur thorn contrast`, `bloc secours`…) |
| `catalog_version` | oui | Version du catalogue utilisée |
| `layer` | oui | Calque cible proposé, par défaut celui du catalogue (`RES - Elec`) |
| `status` | oui | `existant`, `projeté`, `à déposer` ou `en défaut` |
| `local` | oui | `{frame_id, x, y, z}` ; le point est la source ou le pied de l'appareil (= point de base du bloc) |
| `accuracy` | oui | `{sigma_xy_m, sigma_z_m, method}`, avec `method` ∈ `ar_plane_hit`, `ar_lidar`, `gnss`, `map_tap`, `tape_measure` (et `estimated` pour le repli du § 5.2) |
| `aim` | si orientable | `{azimuth_deg, ref: "site_north", tilt_deg?, sigma_deg}`. Convention de l'appli : **0 = nord du repère du site, sens horaire**, de 0 à 360°. L'outil de bureau la convertit en rotation AutoCAD (0 = +X, sens anti-horaire, convergence incluse) |
| `height_m`, `height_ref` | si connu | Hauteur de pose et référence (`sol`, `sous corniche`, `sous chauffage`, `sous lustre`…) |
| `details` | non | Propriétés facultatives (§ 6.4) : `sous_type`, `technologie`, `culot`, `puissance_W`, `temp_couleur_K`, `ref_etiquette`, `etat` |
| `label`, `note` | non | Étiquette affichée (« TGBT Olivetains »), note texte |
| `voice_notes` | non | `[{path, sha256, start_utc, duration_s}]` |
| `photos` | non | `[{photo_id, path, sha256, taken_utc, role: "vue" ou "etiquette", pose}]` |
| `time` | oui | `{captured_utc, device_monotonic_ns, clock_offset_ms, clock_sigma_ms, sync_ref}` : le time-code de synchro (§ 4) |
| `device_id`, `session_id`, `operator` | oui | Traçabilité |
| `registration` | non | `{control_point_ids, plan_id?, osm_refs: [{type, id}]}` |
| `duplicate_of`, `supersedes` | non | Indices pour la fusion |

⚖️ **Écarts tranchés avec l'analyse DWG § 4.a.5.**
- **`aim`** : l'analyse DWG proposait `azimuth_local_deg`, 0 = +X, sens anti-horaire (convention AutoCAD). On retient la convention de la fiche N (nord, sens horaire), plus naturelle sur le terrain, et la conversion se fait au bureau. Cette convention est un choix de la version fusionnée, à confirmer par Devin (§ 15.2).
- **`attributes`** devient **`details`**.
- **`symbol_scale_class`** est supprimé : la taille est déduite du sous-type au bureau (D7).
- **`type_label`** est supprimé : le libellé vient du catalogue.

**Autres types.**
- **`control_point`** :
  - `cp_type` ∈ `angle_mur`, `pilier`, `axe_porte`, `marqueur`, `noeud_osm` ;
  - `osm_ref: {type: "node", id, lon, lat}` ou `plan_ref: {plan_id, x, y}` ;
  - `description`, `photos`, et les champs communs ;
  - au moins 2 points par session, 3 ou plus pour contrôler les résidus.
- **`annotation`** : `annotation_type` ∈ `passage_cable`, `capteur`, `cablette_terre`, `cable_a_nu`, `defaut`, `niveau`, `texte` ; et `text`.
- **`height`** : `value_m` et `height_ref`.
- **`shape` et `zone`** : `label`, `note`, `style`, et pour `zone` les champs du § 6.7.

### 8.7 `manifest.json` et exemple de Feature

```json
{
  "schema": "lumisite-geojson-export",
  "schema_version": "1.0.0",
  "project_id": "SBC-cathedrale",
  "site_name": "Saint-Bertrand-de-Comminges – ancienne cathédrale",
  "catalog_version": "lumisite-catalog-1.0",
  "created_utc": "2026-10-29T17:10:00Z",
  "devices": [
    {"device_id": "and-7f3c", "model": "Xiaomi 13T Pro", "os": "Android", "app_version": "0.x", "sensors": ["ARCore", "GNSS", "micro"]},
    {"device_id": "ios-21aa", "model": "iPad", "os": "iPadOS", "app_version": "0.x", "sensors": ["ARKit", "micro"]}
  ],
  "sessions": [
    {"session_id": "and-7f3c_20261029T0812Z", "device_id": "and-7f3c", "operator": "S.STINVILLE",
     "start_utc": "2026-10-29T08:12:03Z", "end_utc": "2026-10-29T11:40:51Z",
     "features_file": "sessions/and-7f3c/and-7f3c_20261029T0812Z.geojsonl",
     "frames_file": "sessions/and-7f3c/and-7f3c_20261029T0812Z.frames.json",
     "feature_count": 1843, "closed": true}
  ],
  "files": [
    {"path": "sessions/and-7f3c/and-7f3c_20261029T0812Z.geojsonl", "bytes": 2318841, "sha256": "…"},
    {"path": "photos/and-7f3c/and-7f3c_20261029T0812Z/p000123.jpg", "bytes": 1840221, "sha256": "…"}
  ]
}
```

Une ligne du `.geojsonl`, mise en forme pour la lecture. Les valeurs sont **illustratives**, sauf le nom de bloc, le calque, la hauteur de pose (MTEXT « H 6.63 m Sous chauffage » du calque `cote`) et le site.

```json
{
  "type": "Feature",
  "id": "0192d8a4-6b1e-7c3a-9f10-a7f3c0000417",
  "geometry": {"type": "Point", "coordinates": [0.57215, 43.02768]},
  "properties": {
    "schema_version": "1.0.0", "rev": 1, "op": "create", "kind": "equipment",
    "block_name": "spot", "catalog_version": "lumisite-catalog-1.0", "layer": "RES - Elec", "status": "existant",
    "local": {"frame_id": "site:SBC-cathedrale", "x": 12.84, "y": -3.17, "z": 6.63},
    "accuracy": {"sigma_xy_m": 0.08, "sigma_z_m": 0.05, "method": "ar_plane_hit"},
    "aim": {"azimuth_deg": 220.0, "ref": "site_north", "tilt_deg": -35, "sigma_deg": 5},
    "height_m": 6.63, "height_ref": "sous chauffage",
    "details": {"sous_type": "MR16 dichroïque", "technologie": "halogène", "culot": "GU5.3", "puissance_W": 50, "ref_etiquette": null, "etat": "fonctionne"},
    "label": null, "note": "Sur rail, câble apparent",
    "voice_notes": [{"path": "audio/and-7f3c/and-7f3c_20261029T0812Z/vn_000031.m4a", "sha256": "…", "start_utc": "2026-10-29T09:41:09.100Z", "duration_s": 6.2}],
    "photos": [{"photo_id": "p000123", "path": "photos/and-7f3c/and-7f3c_20261029T0812Z/p000123.jpg", "sha256": "…", "taken_utc": "2026-10-29T09:41:07.412Z", "role": "etiquette",
                "pose": {"frame_id": "site:SBC-cathedrale", "x": 10.2, "y": -1.9, "z": 1.6, "yaw": 312.0, "pitch": 28.0, "roll": 0.4}}],
    "time": {"captured_utc": "2026-10-29T09:41:05.880Z", "device_monotonic_ns": 13492837465521, "clock_offset_ms": -42, "clock_sigma_ms": 15, "sync_ref": "sync:20261029T0815Z"},
    "device_id": "and-7f3c", "session_id": "and-7f3c_20261029T0812Z", "operator": "S.STINVILLE",
    "registration": {"control_point_ids": ["cp-a7f3c-0003", "cp-a7f3c-0004"], "osm_refs": [{"type": "way", "id": 0}]},
    "duplicate_of": null
  }
}
```

**À publier dans `docs/`** : `docs/GEOJSON_EXPORT.md` et `docs/lumisite-feature.schema.json`, pour valider les `.geojsonl`.

### 8.8 Exporter toutes les sessions d'un projet — P1 — S
[UX terrain, fiche B]

- **Constat.** L'export se fait par session, et seulement une fois la session ouverte. La carte d'un projet sur l'accueil (`HomeScreen`) n'a pas d'action d'export.
- **Proposition.** Une icône d'export sur chaque carte de projet. Elle produit **le paquet projet du § 8.2**, avec la même destination qu'au § 1.1 (Téléchargements, feuille de partage, phrase de confirmation). Le format interne de session ne change pas.
- **Acceptation.**
  - Un projet de deux sessions donne un fichier qu'on retrouve dans Téléchargements.
  - Chaque session brute de `raw/` se relit avec les contrôles de hash existants (`SessionImporter`).
  - Les `.geojsonl` et le manifeste respectent les § 8.2 à 8.7.

### 8.9 Limites du GeoJSON face au DWG (ce que le bureau doit compléter)

[Analyse DWG] [UX terrain]

| Sujet | Côté GeoJSON | Ce que fait le bureau |
|---|---|---|
| Géométrie des symboles | Seulement un point et un nom de bloc | Insère le bloc **existant** du gabarit, jamais redessiné |
| Rotation | `aim.azimuth_deg`, depuis le nord, sens horaire | Rotation AutoCAD (0 = +X, sens anti-horaire) + convergence des méridiens (environ 1,76° à SBC) |
| Taille et miroir | Absents (D7) | Table de présentation du catalogue, d'après le sous-type |
| Calques | `layer` est une proposition | `RES - Elec`, `cote`, `photométrie`, `diagnostic` du gabarit |
| Hauteur | `height_m` (le DWG est à z = 0) | MTEXT « H x m » sur `cote` et attribut `HAUTEUR_M` |
| Attributs | `details`, `status`, `note` | ATTDEF invisibles ajoutés au gabarit, remplis par ATTRIB |
| Légende avec comptage | Absente | Tableau « Block Legend » reconstruit (pas d'ACAD_TABLE via ezdxf) |
| Fond | Absent (`refs/` éventuel) | Xref de l'architecte recalée (D8) ou contours OSM |
| Précision des coordonnées | WGS84 en degrés ; garder au moins 7 décimales (environ 1 cm) | Projection L93 point par point |
| Formes et annotations | `shape`, `annotation`, `zone` | LWPOLYLINE, MULTILEADER ou MTEXT (ou blocs `LUMI_*` si Sébastien les ajoute) |

---

## 9. Outil de bureau : DXF/DWG, bibliothèque de blocs, alias, recalage

[UX terrain, fiche O] [Analyse DWG § 4.b] [EasyClaw PROPOSITIONS § 2, option B] [Décisions D3, D4, D7, D8]

### 9.1 Principe et emplacement

- Un **outil local**, en **Python avec ezdxf (MIT) et pyproj (MIT)**, **hors de l'appli Android**. EasyClaw propose le module séparé `tools/session2dxf/` (CLI, bibliothèque, `tests/` en pytest) ; le nom peut changer.
- **Son entrée est le paquet GeoJSON** (§ 8). Les ZIP `lumisite.session` de `raw/` servent aux recalculs.
- ⚖️ **Tranché.**
  - EasyClaw (§ 2) prévoyait `.session` → DXF, avec « un calque par type », un calque `RELEVE_CONTROLE` pour les relevés de confiance inférieure à 0,50, des calques `ZONE_<LABEL>`, et une option A « DXF Kotlin dans `core/` ».
  - D1 et D3 l'emportent : **pas de DXF dans l'appli**, et l'export reste **au plus près du gabarit de Sébastien**, avec tous les équipements sur `RES - Elec`.
  - La confiance passe dans l'attribut `SIGMA_XY_M` et dans le rapport de génération. Un calque de contrôle supplémentaire, gelé, reste possible si Sébastien le veut (§ 15.2).

### 9.2 Chaîne de traitement

1. **Import et contrôle.**
   - Lire `manifest.json` et vérifier les `sha256`.
   - Lire chaque `.geojsonl` en flux et garder la dernière `rev` de chaque `id`.
   - Traduire les noms de blocs par la table d'alias (§ 6.3).
   - Signaler les lignes tronquées, les `block_name` hors catalogue et les anciens `LUMI_*_GENERIC` (§ 6.2).
2. **Synchro et fusion de plusieurs appareils.**
   - Ramener les horodatages au temps commun (décalages issus des chirps, § 4, et `time.clock_offset_ms`).
   - **Dédoublonner** les objets de même famille, à une distance inférieure à 2·√(σ₁² + σ₂²), avec des azimuts compatibles. Fusionner par moyenne pondérée en 1/σ², en gardant la liste des `id` sources.
   - **Conflits** (familles différentes au même endroit, ou visées qui diffèrent de plus de 20°) : une liste à valider à l'écran, **jamais tranchée en silence**.
3. **Géoréférencement** (§ 9.5).
4. **Génération du DXF** à partir du gabarit (§ 9.4).
5. **Passage en DWG** (§ 9.7).
6. **Rapport** (§ 9.6).

### 9.3 Bibliothèque de blocs et table d'alias

- **On part du gabarit de Sébastien, jamais d'un fichier vide.** On travaille sur une copie du DXF modèle : blocs déjà dessinés et **renommés** selon le § 6.3, calques, styles, présentations `CARTOUCHE ETI A3 - 01/02` (A3, fenêtre du plan au 1:200, fenêtre de légende, cartouche « Eclairage Technique »).
- Si le modèle n'existe qu'en DWG, l'exporter une fois en DXF depuis AutoCAD (`DXFOUT`) ou avec ODA File Converter.
- Les blocs du gabarit ont leur point de base en (0,0) à la source, et le symbole des appareils à visée est orienté vers +X (Analyse DWG § 2.5).
- **La bibliothèque compte exactement 25 blocs** (hors xref `02-Plan au sol`) : la liste exacte et le catalogue sont au § 6.3.
- **La table d'alias** du § 6.3 sert à la lecture des anciens DWG et des anciens paquets.
  - Validés : `bloc secour` → `bloc secours`, `tableau divisionaire` → `tableau divisionnaire`.
  - En attente de Sébastien : `Camera` → `caméra`, `hublot ecl` → `hublot éclairage`, `lineaire fluo` → `linéaire fluo`, `tgbt` → `TGBT`.
  - Aucun autre renommage ; `micro col` reste tel quel.
- **Normalisation à l'import (D4).** Les blocs sont renommés **une seule fois** dans le gabarit. Tout ancien nom rencontré (paquet, ancien DWG, saisie) est normalisé vers le nom canonique **avant** l'insertion. Un plan produit ne contient donc qu'un nom par bloc, et la « Block Legend » reste juste. ⚖️ EasyClaw préférait garder les noms fautifs comme référence ; c'est écarté par D4 (§ 6.3).

### 9.4 Génération du DXF

- **Un INSERT par objet fusionné**, avec le bloc existant, **jamais redessiné** :
  - point d'insertion = position L93, z = 0 (la hauteur va en attribut) ;
  - rotation = azimut converti (0 = +X, sens anti-horaire, convergence incluse) ;
  - **échelle uniforme** tirée de la table de présentation du catalogue :

    | Bloc | Échelle |
    |---|---|
    | spot | 1,208 par défaut ; 2,415 ou 3,614 selon le sous-type (D7) |
    | boîte de dérivation | 0,5 ou 1 |
    | projecteur thorn contrast | 0,571 |
    | extincteur | 0,6 |
    | TGBT | 1,417 |
    | linéaire fluo | 1 ou 1,67 |
    | autres | 1 |

  - un miroir seulement si la table le demande (les miroirs sont purement graphiques).
- **Correspondance tailles de spot / sous-types.**
  - Les 3 échelles (×1,208, ×2,415, ×3,614) sont **purement graphiques** : le DWG ne contient **aucun ATTDEF ni ATTRIB** qui les relie à une technologie. Rien dans le fichier ne dit quelle taille correspond à quoi.
  - D7 indique qu'elles servent la lisibilité pour le client et correspondent à des technologies différentes. **La correspondance précise avec PAR20, MR16 dichroïque ou LED reste une question ouverte pour Sébastien** (§ 15.2, O2). Tant qu'elle n'est pas fixée, l'outil insère le spot à 1,208 par défaut.
  - À SBC, 65 spots sont à 1,208, 52 à 2,415 et 5 à 3,614.
- **Mêmes calques que le gabarit :**
  - `RES - Elec` (ACI 242) : équipements et étiquettes ;
  - `cote` (18) : MTEXT « H x m » de hauteur 0,5 ;
  - `photométrie` (30) : si des cercles sont générés ;
  - `diagnostic` (242) : légende.
  - Des sous-calques par statut (`RES - Elec-existant`, `-projeté`, `-défaut`) restent une option à valider : Sébastien utilise aujourd'hui un calque unique.
- **Attributs, nouveaux et invisibles** pour ne pas changer le rendu client :
  - des ATTDEF ajoutés aux blocs du gabarit : `ID`, `STATUT`, `SOUS_TYPE`, `TECHNO`, `CULOT`, `PUISSANCE_W`, `HAUTEUR_M`, `REF_ETIQUETTE`, `SIGMA_XY_M`, `SOURCES` (id GeoJSON + `device_id`), `DATE_RELEVE` ;
  - l'analyse DWG ajoute `CIRCUIT` et `TABLEAU` ;
  - ils sont remplis par les ATTRIB et exploitables par l'extraction de données d'AutoCAD ou par BCOUNT ;
  - à défaut, des XDATA (appid `LUMISITE`).
- **Annotations** en MULTILEADER (style du gabarit, texte de 0,36 m, sur `RES - Elec`), avec les intitulés de Sébastien.
- **Formes `shape`** : LWPOLYLINE sur `RES - Elec` avec une étiquette, comme ses petits contours actuels. Elles ne deviennent des blocs `LUMI_*` que si Sébastien les ajoute au gabarit (§ 7.3).
- **Légende « Block Legend »** : un tableau Symbol / Count / Name, une ligne par bloc réellement inséré, trié par nom sans tenir compte de la casse. Count = nombre d'INSERT dans l'espace objet. Il est placé sur `diagnostic`, hors de l'emprise (vers x ≈ -140 m dans le gabarit actuel).
  - ezdxf ne crée pas d'ACAD_TABLE : le tableau est construit en LINE + MTEXT + INSERT du symbole (module `tablepainter`, l'équivalent du bloc `*T39`).
  - Option : des sous-lignes par sous-type validé.
  - Un CSV des comptages est joint. C'est aussi l'export CSV demandé par le cahier des charges (EasyClaw E6).
- **Présentation** : l'outil ne remplit que le titre, la date et le numéro de plan du cartouche.
- **Version DXF** : la même que le gabarit (AC1015, AutoCAD 2000 ; AC1027 si Sébastien l'accepte). `$INSUNITS = 6` (m), `$MEASUREMENT = 1`. Le PoC R12 d'EasyClaw n'est pas la cible.

### 9.5 Géoréférencement et recalage (D8)

- **(a) Terrain → Lambert 93.**
  - Les sessions passent en WGS84 puis en **L93 (EPSG:2154)** avec pyproj.
  - Si besoin, une similitude 2D (Helmert à 4 paramètres, échelle fixée à 1) sur les `control_point` liés à des nœuds OSM, avec l'erreur moyenne et les résidus affichés.
- **(b) Le plan de l'architecte est recalé sur la donnée terrain, et non l'inverse.**
  - Sébastien désigne 2 à 4 points homologues (angles, piliers) sur le plan et dans la donnée terrain.
  - L'outil calcule la similitude plan → L93 par moindres carrés (2 points au minimum, 3 ou plus pour les résidus) et l'applique à **l'insertion de l'xref** : point, rotation et échelle uniforme. L'échelle est fixée à 1 si les deux repères sont métriques, et laissée libre seulement si le PDF importé n'est pas à l'échelle.
  - L'erreur moyenne (RMS) et les résidus de chaque point sont affichés.
- **(c) Sans plan d'architecte**, on utilise directement le fond OSM : contours OSM projetés en L93 sur un calque `OSM` (couleur 7 ou 8) à la place de l'xref.
- **(d) Le DXF est écrit en coordonnées Lambert 93 vraies**, en mètres.
- **Convergence des méridiens.** À Saint-Bertrand-de-Comminges, le nord Lambert 93 s'écarte d'environ **1,76°** du nord géographique, avec un facteur d'échelle d'environ 1,00086 (0,9 mm par mètre).
  - Les positions sont projetées une à une depuis WGS84, et non calculées comme « origine + décalages ENU ».
  - La convergence est ajoutée à chaque azimut avant d'écrire la rotation.
  - Les angles de la similitude du plan s'appliquent aussi aux rotations des blocs.
- **GEODATA.** Le GEODATA faux du gabarit (Lambert 93 placé au point 46,35 N / 0,258 E, soit (489 206 ; 6 587 002), dans le Poitou, à environ 370 km) **est à remplacer** (D8).
  - L'outil le supprime, puis écrit un GEODATA Lambert 93 **correct**, cohérent avec les coordonnées L93 du dessin.
  - Il n'écrit ce GEODATA qu'après un test d'ouverture dans AutoCAD qui le valide. Tant que ce test n'est pas fait, l'outil n'en écrit aucun, car le DXF en L93 vrai se géolocalise depuis AutoCAD.
  - ⚖️ La fiche O disait « supprimer, n'écrire que si un test AutoCAD valide » ; D8 dit « remplacer ». Les deux s'accordent si l'objectif est de remplacer, avec le test AutoCAD comme garde-fou.
- ⚖️ **Tranché.** L'analyse DWG (§ 4.b.2) prenait le repère local de l'xref de l'architecte comme repère cible et admettait un DXF en local avec un décalage documenté. D8 l'emporte : **le repère cible est Lambert 93**, et le plan est recalé sur le terrain.

### 9.6 Rapport de génération et contrôles

- **Le rapport contient :**
  - les comptages par bloc ;
  - les objets fusionnés et leurs sources ;
  - les conflits ;
  - l'erreur moyenne des recalages ;
  - les objets « à classer » et « hors catalogue » ;
  - les propositions vocales validées ou rejetées (§ 6.6).
- **Contrôles à la sortie :**
  - comptage par bloc = nombre d'équipements fusionnés ;
  - aucun nom de bloc hors catalogue ;
  - toutes les entités sur un calque prévu ;
  - RMS inscrit dans le rapport.
- **Tests pytest repris d'EasyClaw (§ 2.4 et § 9.5), adaptés aux décisions :**
  1. position X/Y conservée à 1e-3 près ;
  2. rotation appliquée = azimut converti ;
  3. un DXF relu par `ezdxf.readfile()` sans erreur, avec un audit à 0 erreur ;
  4. une session sans relevé donne un DXF valide et vide ;
  5. une ligne tronquée produit un avertissement, pas un échec ;
  6. le texte saisi se retrouve dans l'ATTRIB `ID` ;
  7. un polygone fermé donne une polyligne fermée (code 70 = 1) ;
  8. `no_fill` donne un contour seul, sans SOLID ;
  9. un objet sans position dans le site ne sort pas dans le DXF, mais reste dans le JSON.

### 9.7 DWG

- Le DXF est la sortie de l'outil. Le DWG s'obtient :
  - par un enregistrement sous AutoCAD ou CADCRAFT ;
  - ou par **ODA File Converter** (gratuit, propriétaire, utilisable en ligne de commande) depuis le DXF.
- **LibreDWG** (GPL-3) sert seulement à **lire** d'anciens DWG au bureau. Il n'est ni lié ni distribué avec LumiSite.
- Remarques DXF valables pour le bureau :
  - un `INSERT` + `ATTRIB` par objet ;
  - jdxf (MIT) trace des traits mais ne fait pas de blocs avec attributs, donc il est insuffisant ;
  - Kabeja lit le DXF mais ne le produit pas pour ce besoin, et il est en LGPL : ne pas l'utiliser ;
  - proj4j (Apache-2.0) est l'option si une partie est écrite en JVM, pyproj côté Python.

### 9.8 Acceptation de l'outil de bureau

- **Test aller-retour.**
  - Les 293 INSERT d'`eclairage03.dxf` sont convertis en GeoJSON de test, puis l'outil régénère un DXF à partir du gabarit.
  - On retrouve les **25 blocs** (noms canoniques), les mêmes comptages que la légende, et les mêmes positions (à 1 mm, transformation près), rotations et échelles.
- **Fusion.** Deux paquets de deux appareils avec 10 objets en double donnent 10 objets fusionnés, et la liste des conflits s'affiche.
- **Ouverture du DXF** dans AutoCAD et dans un visionneur libre :
  - unités en mètres, coordonnées L93 cohérentes avec OSM (un contour OSM projeté en L93 tombe sur le relevé) ;
  - xref de l'architecte posée par similitude, erreur moyenne affichée ;
  - aucun GEODATA du Poitou ;
  - légende égale au nombre d'INSERT.
- **Passage en DWG** réussi, par ODA File Converter ou par AutoCAD.
- **Un seul nom par bloc (D4).** Aucun dessin produit ne contient à la fois un ancien nom et le nouveau, par exemple `bloc secour` **et** `bloc secours` dans la table des blocs ou dans la légende. Test : importer un paquet mêlant les deux formes, générer, puis vérifier que `doc.blocks` et la « Block Legend » n'ont qu'une ligne `bloc secours`, dont le comptage est la somme des deux.

---

## 10. Analyse du plan de référence eclairage03

[Analyse DWG] Analyse faite le 29/10/2026 sur une copie de travail (`/workspace/sbc/`). Les fichiers de Sébastien n'ont pas été modifiés.

### 10.1 Lecture

| Méthode | Résultat |
|---|---|
| ezdxf 1.4.5 + matplotlib | OK sur le DXF (ezdxf ne lit pas le DWG) |
| `apt-get install libredwg` | Échec (HTTP 500, pas de paquet dans Debian main) |
| micromamba + conda-forge `libredwg` (sans sudo) | **OK** : `dwg2dxf` 0.11.3876, `dwgread`, `dwglayers` |
| Relecture ezdxf (`recover`) | 0 erreur, 412 corrections automatiques |

- **Limites.** L'ACAD_TABLE « Block Legend » et un RTEXT ne passent pas en DXF ; le contenu du tableau a été relu via le bloc anonyme `*T39`. `DATATABLE` n'est pas géré.
- **Dates.** `$TDCREATE` a été réécrit par la conversion et n'est pas fiable. `$TDUPDATE` donne une dernière sauvegarde le 20/10/2026 vers 18:45.

### 10.2 Chiffres clés

| Élément | Valeur |
|---|---|
| Version | AC1015 (AutoCAD 2000), code page ANSI_1252 |
| Unités | `$INSUNITS` = 6 (mètres), métrique, 2 décimales |
| Emprise réelle | x -40,18 → 37,34 ; y -22,31 → 35,81, soit **77,5 × 58,1 m** |
| Échelle | Fenêtres A3 à 5 mm par m, soit **1:200** ; légende au 1:154 ; vignette au 1:650 |
| Entités de l'espace objet | 367, dont 294 INSERT (293 équipements + 1 xref), 21 CIRCLE, 20 MULTILEADER, 15 MTEXT, 9 DIMENSION, 5 LWPOLYLINE, 3 LINE |
| Repère | **Local**, origine = insertion de l'xref, aucune rotation, aucun SCU. La cathédrale est vers X ≈ 502 000 / Y ≈ 6 217 600 en L93 (ordre de grandeur) |
| GEODATA | **Faux** : L93 au point 46,35 N / 0,2583 E = (489 206,23 ; 6 587 001,72), dans le Poitou, à environ 370 km. Reste du gabarit, à remplacer (D8) |
| Fond | Xref `.\02-Plan au sol.dwg` (0,0, échelle 1), issue d'un PDF importé (calque `PDF_Geometry`). Le fichier n'a pas été fourni. Aucun raster |
| Nord | Rose des vents et flèche du nord sur le cartouche ; leur direction n'a pas été mesurée |
| Câblage | Aucun |
| Hauteur de texte | 0,36 m (étiquettes), 0,5 m (« H … m » sur `cote`) |
| Cotes | `Standard`, DIMSCALE 2, DIMTXT 0,28 ; 9 cotes : 48,08 ; 25,00 ; 18,00 ; 16,75 ; 10,87 ; 10,71 ; 8,89 ; 2,63 ; 2,46 m |

### 10.3 Calques

| Calque | ACI | Contenu | Imprimé |
|---|---|---|---|
| **`RES - Elec`** | 242 | 293 INSERT d'équipements + l'xref, 20 MULTILEADER, 4 LWPOLYLINE, 1 MTEXT (« H24.9m ») | oui |
| `cote` | 18 | 14 MTEXT « H x m », 9 DIMENSION, 3 LINE, 1 LWPOLYLINE | non (gelé dans les nouvelles fenêtres) |
| `photométrie` | 30 | 21 CIRCLE : 7 de r = 4,445 m, 14 de r = 2,868 m | non (gelé) |
| `diagnostic` | 242 | ACAD_TABLE de la légende ; calque courant | oui, par une fenêtre |
| `-CADRE-CARTOUCHE` | 7 | Cartouche | oui |
| `02-Plan au sol\|…` | 17, 7, 18, 242 | Xref de l'architecte | oui |

### 10.4 Blocs et légende

- **25 blocs, 293 INSERT**, tous sur `RES - Elec`, en z = 0, avec le point de base (0,0) à la source.
- **Aucun attribut** : les sigles TGBT, TD, BD, DM, PDL, A et Xlr sont du texte figé dans les blocs. Aucun ATTDEF ni ATTRIB : **les 3 échelles de spot (×1,208, ×2,415, ×3,614) sont purement graphiques** (§ 9.4).
- **Liste exacte des 25 noms** (hors xref `02-Plan au sol`), confirmée par EasyClaw sur une copie en lecture seule : voir § 6.3. Seuls six noms sont fautifs (`bloc secour`, `tableau divisionaire`, `Camera`, `hublot ecl`, `lineaire fluo`, `tgbt`).
- **La rotation porte la visée.** Le miroir sert à inverser le dessin. L'échelle varie pour la lisibilité (D7).
- **Les comptages correspondent exactement** à la légende `block_legend.png`, au PDF et au bloc `*T39`.

| Bloc (nom actuel) | Nb | Échelles | Rotations observées (°) | Taille (m) |
|---|---|---|---|---|
| Amplificateur | 1 | 1 | 0 | 0,81×0,44 |
| bloc secour → **bloc secours** | 5 | 1 | 269,7 ×3 ; 0,3 ×2 | 0,75×0,37 |
| boîte de dérivation | 43 | 1 ×12 ; 0,5 ×31 | 0 | 0,64×0,45 |
| bougeoir électrifié | 1 | 1 | 0 | 0,19×0,53 |
| Camera | 3 | 1 | 0 ; 316,8 ×2 | 0,77×0,48 |
| Déclencheur Manuel | 2 | 1 | 0 | 0,39×0,26 |
| Diffuseur Sonore Radio | 2 | 1 | 0 | 0,23×0,22 |
| enceinte | 2 | 1 | 0 | 0,46×0,90 |
| extincteur | 4 | 0,6 | 0 | 0,84×0,57 |
| haut-parleur | 8 | 1 ×6 ; miroir ×2 | 0 ; 88,8–90 | 0,20×0,40 |
| hublot ecl | 1 | 1 | 0 | 0,47×0,47 |
| interrupteur | 13 | 1 ×11 ; miroir ×2 | 0 ; 191,4 ×2 | 0,22×0,28 |
| lampe | 8 | 1 | 0 | 0,45×0,45 |
| Lanterne EP moderne | 1 | 1 | 0 | 0,80×0,46 |
| lineaire fluo | 3 | miroir ×1 ; 1,67 ×2 | 179,7 ; 268,7 ×2 | 0,53×0,10 |
| Lustre | 2 | 1 | 0 | 0,67×0,91 |
| micro col | 1 | 1 | 0 | 0,51×0,45 |
| PDL | 2 | 1 | 0 | 1,29×0,71 |
| prise électrique | 13 | 1 ×12 ; miroir ×1 | 268,4 ×8 ; 90,8 ×2 ; 93,6 ; 2,9 ; 178,6 | 0,26×0,30 |
| prise xlr | 6 | 1 | 0 | 1,36×0,34 |
| projecteur | 10 | 1 | 171 ×5 ; 85,4 ×3 ; 11,8 ×2 | 0,40×0,56 |
| projecteur thorn contrast | 34 | 0,571 ×5 ; miroir (-0,571) ×29 | 330,5 ×16 ; 235,8 ×13 ; 206,3 ×2 ; autres | 0,89×0,84 |
| spot | 122 | 1,208 / 2,415 / 3,614 (35+30 / 44+8 / 5), dont 38 en miroir | 35 valeurs (130,1 ×35 ; 47,3 ×9 ; 306,3 ×8…) | 0,28×0,25 |
| tableau divisionaire → **tableau divisionnaire** | 3 | 1 | 2,8 | 0,33×0,24 |
| tgbt | 3 | 1,417 | 353,5 ×2 ; 84,6 | 0,34×0,70 |
| **Total** | **293** | | | |

- **Légende** : ACAD_TABLE « Block Legend » générée par AutoCAD, avec les colonnes Symbol / Count / Name, triée par nom, sur `diagnostic`. Elle se trouve dans l'espace objet vers x ≈ -140, y ≈ 15…25, et mesure 7,67 × 10,05 m. Elle est affichée par une fenêtre au 1:154.
- **Annotations** (20 MULTILEADER, style `Standard`, 0,36 m) :
  - « Passage de câble » ×8, « Capteur » ×2 ;
  - « PDL cathédrale », « PDL cathédrale extérieur », « TGBT Olivetains » ;
  - « Cablette de terre », « Câble à nu », « Vitrine », « Centrale secours », « Ecl. Centrale secours », « Différentiel », « Niveau toiture », « En default ».
- **Petits contours** (4 LWPOLYLINE) : un rectangle de 0,58 × 0,70 m, une bande de 1,84 × 0,03 m (« Vitrine »), et deux heptagones d'environ 1,3 m.

### 10.5 Présentations et livrable PDF

- **Présentations :**
  - `CARTOUCHE ETI A3 - 01` et `- 02` : A3 420 × 297, Adobe PDF.pc3, plan au 1:200 ;
  - `cartouche a4` : ancien gabarit, 1:5000 ;
  - `vierge a3 eti` : 1:100 ;
  - `pdfcreator 1200dpi 1300x890 mm A0` : 1:74 ;
  - `Présentation1` : vide.
- **Cartouche :**
  - « DÉPARTEMENT HAUTE GARONNE » ;
  - « Cathédrale Saint-Bertrand de Comminges – Plan de masse » ;
  - LEGENDE ; « Rapport 1000/200xp » ; « Echelle : 1 / 200 » ; « PLAN N° 1/2 » et « 2/2 » ;
  - date 09/10/2026 ; édité par S.STINVILLE ; contrôle interne G.STINVILLE ;
  - « Bureau de conception lumière – ET Eclairage Technique, 39 rue des Saules 31400 TOULOUSE, www.eclairage-technique.fr » ;
  - clause de propriété (loi du 11 mars 1902).
- **PDF client** :
  - 2 pages A3 paysage, vectorielles, AutoCAD 2021, le 09/10/2026 ;
  - fond de l'architecte en noir, équipements en bordeaux (couleur 242) ;
  - échelle graphique 0-1-2-4-8 m « 200e » et flèche du nord ;
  - la page 1 couvre l'est (chœur, chapelles, cloître), la page 2 l'ouest et le nord (Olivetains, TGBT, PDL) ;
  - les cotes H, les DIMENSION et les cercles de photométrie **ne sont pas imprimés**.

### 10.6 Méthode de travail de Sébastien (ce que l'outil de bureau doit reproduire)

1. **Fond** : le PDF de l'architecte, converti en DWG (PDFIMPORT), est attaché en xref à l'origine, à l'échelle 1, en mètres.
2. **Relevé** : chaque équipement est un INSERT d'un bloc nommé en clair, sur le calque unique `RES - Elec`. Il est orienté (rotation = visée), et parfois agrandi ou mis en miroir pour la lisibilité.
3. **Annotations** en multileaders ; hauteurs en MTEXT sur `cote` ; faisceaux en cercles sur `photométrie` (non imprimés).
4. **Livrable** : une légende automatique avec comptage, et des présentations A3 au 1:200 tracées en PDF.
5. **Pas d'attributs, pas d'identifiants, pas de câblage.** Le « quoi » est dans le nom du bloc, le « où » dans l'insertion, le « vers où » dans la rotation, et les détails dans des textes libres.

### 10.7 Points restés non lisibles

- L'xref `02-Plan au sol.dwg` est absente : le fond n'est connu que par le PDF.
- La mise en forme de l'ACAD_TABLE et le contenu du RTEXT n'ont pas été décodés.
- La direction du nord par rapport au repère n'a pas été mesurée.
- Le sens des 3 tailles de spot est **tranché par D7**. Reste à savoir quelle taille correspond à quelle technologie (§ 15.2).

---

## 11. Ergonomie, mise en page et thème

### 11.1 Mise en page de l'écran de relevé — P0 pour le chevauchement, P1 pour la tablette — M
[UX terrain, fiche H] [Contre-expertise EasyClaw : confirmé]

**Constat.**
- `SessionScreen` est une `Row` 58 % / 42 %.
- La caméra (`GLSurfaceView`, hauteur 180 dp) est **à l'intérieur** de la colonne qui défile avec toutes les pastilles.
- Sur un écran de 6,67" en paysage, la colonne est étroite : la caméra et les boutons se disputent la même bande et se chevauchent quand on fait défiler.
- `configChanges` garde l'activité en rotation, mais la mise en page ne change pas.

**Proposition.** Trois zones qui ne se recouvrent pas :
- la **carte** à gauche (ou en haut en portrait) ;
- la **caméra** en haut à droite, avec une taille maximale fixe ;
- la **barre d'actions** en bas, ou dans une colonne qui ne contient pas la caméra.

Les pastilles d'étapes (§ 2.1), la palette d'objets (§ 6.4) et la barre d'annotation (§ 7.2) sont dans cette barre. Vérifier sur le 13T Pro en paysage et sur une largeur de tablette, avec deux colonnes confortables (iPad).

**Acceptation.**
- En paysage sur le 13T Pro, on voit en même temps la carte, la caméra et les boutons, et aucun bouton n'est dessiné sur l'image de la caméra.
- En portrait, on atteint chaque action en faisant défiler une seule zone, qui ne contient pas la caméra.

### 11.2 Règles d'ergonomie de terrain (rappel)

[UX terrain]

- Toujours un **mot français à côté de chaque icône** (gants, soleil).
- **Jamais de bouton grisé sans explication** (§ 1.4).
- Chaque succès dit **où** se trouve le résultat (§ 1.1).
- Les détails sont facultatifs et jamais bloquants (§ 6.4).
- Le geste retour est cohérent sur tous les écrans (§ 1.3).
- Les messages d'erreur réseau sont en français et proposent une suite (« Créer sans contours »).

### 11.3 Thème : bouton en haut à droite, avec Système, Clair et Sombre — P1 — S
[UX terrain, fiche M] [Contre-expertise EasyClaw : API osmdroid] [Décision D10]

**Proposition.**
- Une icône soleil/lune dans la barre du haut, **à droite**, sur tous les écrans (accueil, projet, session, relevé).
- Elle passe par trois états : **Système** (suit Android), **Clair**, **Sombre**.
- Le choix est mémorisé (DataStore) et appliqué dès le lancement, sans flash blanc.
- Côté Compose : un `MaterialTheme` avec deux `ColorScheme`, choisis selon la préférence, et non plus seulement selon `isSystemInDarkTheme()`.

**Détails terrain.**
- **En mode sombre, la carte est assombrie.**
  - L'API publique existe : `mapView.overlayManager.tilesOverlay.setColorFilter(…)`. `TilesOverlay.INVERT_COLORS` existe depuis osmdroid 5.1 (relevé par EasyClaw dans `TilesOverlay.java`).
  - L'inversion pure dégrade l'eau et les aplats jaunes (issue osmdroid #1804). Préférer une `ColorMatrixColorFilter` sur mesure (proposition d'EasyClaw : luminosité −0,25, saturation −0,6), à régler sur le terrain.
- Les symboles, les étiquettes et le réticule AR gardent un contraste suffisant dans les deux modes (contour blanc ou noir autour des symboles).
- **L'aperçu de la caméra n'est jamais filtré.**
- **Mode nuit rouge, en option (D10)** : un quatrième état, pour les relevés d'éclairage dans le noir. Il gêne moins la vision nocturne et les mesures de luminance. Il peut venir dans un second temps.

**Acceptation.**
- Un appui change le thème tout de suite sur l'écran en cours, sans perdre l'état (relevé en cours, zoom de la carte).
- Le choix survit au redémarrage.
- La carte reste lisible et n'éblouit pas en mode sombre.
- Les textes respectent un contraste suffisant (WCAG AA) dans les deux modes.

---

## 12. Contre-expertise : vérifications

Vérifications refaites en direct le **jeudi 29 octobre 2026, entre 18:22 et 18:27 (heure de Paris, UTC+1)**, depuis la box de travail, avec `curl` et Python 3. Aucun fichier du dépôt n'a été modifié, et il n'y a eu ni clone, ni commit, ni push.

### 12.1 IGN WMTS : TileMatrixSet

| Date / heure (Paris) | Commande | Résultat |
|---|---|---|
| 29/10/2026 18:22:25 | `curl "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetCapabilities&VERSION=1.0.0"`, puis lecture des blocs `<Layer>` en Python | HTTP 200, 3 057 751 octets, 1,79 s. `GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2` : Format `image/png`, TileMatrixSet **`PM_0_19`**, zooms 0 à 19. `ORTHOIMAGERY.ORTHOPHOTOS` : Format `image/jpeg`, TileMatrixSet **`PM_0_19`**, zooms 0 à 19 |
| 29/10/2026 18:22–18:23 | `curl ".../wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=<couche>&STYLE=normal&TILEMATRIXSET=<PM_0_19 ou PM ou PM_6_19>&TILEMATRIX=17&TILEROW=48149&TILECOL=65753&FORMAT=<image/png ou image/jpeg>"` (tuile de la cathédrale au zoom 17) | PLANIGNV2 `PM_0_19` : **200**, png, 6 739 o. PLANIGNV2 `PM` : **200**, png, 6 739 o. ORTHOPHOTOS `PM_0_19` : **200**, jpeg, 10 874 o. ORTHOPHOTOS `PM` : **200**, jpeg, 10 874 o. `PM_6_19` : **400** pour les deux couches. `md5sum` : les tuiles `PM` et `PM_0_19` sont **identiques à l'octet près** |

| 29/10/2026 23:09:15 | Même `GetTile` avec un **format inversé** : PLANIGNV2 en `image/jpeg`, ORTHOPHOTOS en `image/png`, avec `PM_0_19` et `PM` | **400** dans les 4 cas. Le 400 vient du **format**, pas de la grille |

**Conclusion.**
- Les capabilities déclarent `PM_0_19` pour les deux couches (png pour le Plan IGN, jpeg pour l'orthophoto), et `PM` répond aussi 200.
- **Erreur reconnue par EasyClaw (retour du 29/10, vers 23 h)** : son 400 sur `PM` venait d'un **mauvais format d'image**, pas du nom de la grille. Le format est imposé par couche, et un mauvais format renvoie 400.
- `PM` est un **alias non déclaré** : il fonctionne aujourd'hui mais peut disparaître.
- **Recommandation : `TILEMATRIXSET=PM_0_19`**, avec le format associé à chaque couche.

### 12.2 API OSM v0.6 : `map.json`, surface et nœuds

| Date / heure (Paris) | Commande | Résultat |
|---|---|---|
| 29/10/2026 18:22:55 | `curl -A "LumiSite-verif/0.1" https://api.openstreetmap.org/api/capabilities` | HTTP 200, 0,58 s. `<area maximum="0.25"/>`, `<waynodes maximum="2000"/>`, `<relationmembers maximum="32000"/>`, `<timeout seconds="300"/>`, status `online`. **Aucune mention d'une limite de 50 000 nœuds.** |
| 29/10/2026 18:22:56 | `curl -A "LumiSite-verif/0.1" "https://api.openstreetmap.org/api/0.6/map.json?bbox=0.5955,43.0255,0.5975,43.0270"` | HTTP **200**, 121 883 octets, **1,04 s**. 261 éléments : 245 nœuds, 6 chemins, 10 relations. Clés d'un chemin : `changeset, id, nodes, tags, timestamp, type, uid, user, version`, donc **pas de coordonnées dans les chemins**. Relations incluses : itinéraires de randonnée et de vélo (`route` de 630 et 724 membres, `superroute`) |
| 29/10/2026 18:22:58 | `curl ".../api/0.6/map.json?bbox=0.0,43.0,1.0,44.0"` (1 degré carré) | HTTP **400** en 2,39 s : « The maximum bbox size is 0.250000, and your request was too large. Either request a smaller area, or use planet.osm » |
| 29/10/2026 18:23 | `curl "https://wiki.openstreetmap.org/w/index.php?title=API_v0.6&action=raw"`, puis `grep 50.000` | Section « Error codes » de `map` : « HTTP status code 400 (Bad Request) : When any of the node/way/relation limits are exceeded, **in particular if the call would return more than 50'000 nodes**. » |
| 29/10/2026 18:23 | `curl https://raw.githubusercontent.com/openstreetmap/openstreetmap-website/master/config/settings.yml`, puis `grep` | `max_request_area: 0.25` et **`max_number_of_nodes: 50000`** (« Maximum number of nodes that will be returned by the api in a map request ») ; `max_number_of_way_nodes: 2000` (« checked on save ») |
| 29/10/2026 23:09:07 | `curl -A "LumiSite-verif/0.1" "https://api.openstreetmap.org/api/0.6/map.json?bbox=2.33,48.85,2.38,48.89"` (centre de Paris, 0,002 degré carré) | HTTP **400** en 2,05 s, 95 octets : « **You requested too many nodes (limit is 50000). Either request a smaller area, or use planet.osm** » |

**Conclusion.**
- `map.json` répond 200 en environ **1 s** sur la cathédrale ; tu avais mesuré 1,3 s environ. La limite de surface est bien de **0,25 degré carré**.
- **La limite de 50 000 nœuds existe** : elle figure sur le wiki OSM et dans la configuration du serveur. **EasyClaw a reconnu son erreur** (retour du 29/10, vers 23 h). Seule nuance juste de sa part : elle **n'est pas déclarée dans `capabilities`**, où `waynodes = 2000` est une limite **par chemin**, contrôlée à l'enregistrement.
- **Une bbox de 0,002 degré carré en zone dense dépasse déjà la limite** (test à 23:09). D'où les deux garde-fous de la fiche L et du lot 3 : la surface (0,01 degré carré) **et** la gestion du 400 « too many nodes » (réduire, ou découper puis fusionner).
- **Le texte de la fiche L est corrigé en conséquence au § 3.1** :
  - 0,25 degré carré, déclaré par `capabilities` ;
  - 50 000 nœuds au plus par appel `map`, d'après le wiki et le serveur, mais non déclaré par `capabilities` ;
  - les deux dépassements renvoient HTTP 400 ;
  - les chemins ne portent que des identifiants de nœuds ;
  - écarter les relations d'itinéraires avant de reconstruire.

### 12.3 Overpass : trois instances, timeout de 30 s

| Date / heure (Paris) | Commande | Résultat |
|---|---|---|
| 29/10/2026 18:23:22 | `curl -A "LumiSite-verif/0.1" --max-time 30 --data-urlencode 'data=[out:json][timeout:25];node(43.0255,0.5955,43.0270,0.5975)["amenity"];out 5;' https://overpass-api.de/api/interpreter` | **HTTP 502** en 0,79 s : « 502 Gateway Timeout. This Overpass API server is currently at capacity. All query workers are busy and the request queue is full. » |
| 29/10/2026 18:23:23 | Même requête sur `https://overpass.kumi.systems/api/interpreter` | **Pas de réponse en 30 s** (curl, code 28, timeout) |
| 29/10/2026 18:23:53 | Même requête sur `https://overpass.private.coffee/api/interpreter` | **Pas de réponse en 30 s** (curl, code 28, timeout) |

**Conclusion.**
- Les trois instances étaient inutilisables à cette heure : un 502 « at capacity » et deux absences de réponse en 30 s. C'est cohérent avec ce qu'EasyClaw a mesuré plus tôt (un 502, et des dépassements à 60 s).
- **Recommandation : l'API OSM en premier (§ 3.1), et Overpass en secours seulement (§ 3.2).**
- **La fiche L passe AVANT la fiche C** dans les priorités (§ 13, lot 3).

### 12.4 `SessionController.kt` : `blockName = "LUMI_${kind}_GENERIC"`

| Date / heure (Paris) | Commande | Résultat |
|---|---|---|
| 29/10/2026 18:24:31 | `curl https://api.github.com/repos/Gabrieljean454fgd/eclairage` | HTTP 200, branche par défaut `main`, dernier push le 28/10/2026 à 22:45 (Paris) |
| 29/10/2026 18:24–18:26 | `curl "https://api.github.com/repos/Gabrieljean454fgd/eclairage/git/trees/main?recursive=1"` | HTTP 200, arbre `1fb8fc52937be81f0f0d521f62db74349bf76c4d`, 121 entrées, non tronqué. Fichier trouvé : **`app/src/main/java/app/lumisite/android/SessionController.kt`** |
| 29/10/2026 18:26:19 | `curl https://raw.githubusercontent.com/Gabrieljean454fgd/eclairage/main/app/src/main/java/app/lumisite/android/SessionController.kt`, puis `grep -n "GENERIC\|DxfHint\|SessionStopped\|ar.close"` | HTTP 200, 15 250 octets. **Ligne 275 : `dxf = DxfHint(layer = "LUMI_${kind}", blockName = "LUMI_${kind}_GENERIC"),`** dans `fun survey(…)` (l. 266). Aussi l. 136–139 : `fun close() { pauseCapture(); journal.event(SessionStopped(…)); journal.close(); ar.close() }` |
| 29/10/2026 18:26 | `curl "https://api.github.com/repos/Gabrieljean454fgd/eclairage/commits?sha=main&per_page=1"` | Tête de `main` = `1fb8fc52937be81f0f0d521f62db74349bf76c4d`, « LumiSite 0.1.0 : restauration du code de la tranche 1 (état e5cf515) », 28/10/2026 à 22:45 (Paris) |
| 29/10/2026 18:26 | Lecture au commit `1fb8fc5` de `ui/SessionScreen.kt`, `core/.../model/Model.kt`, `ui/MapView.kt`, `ui/Screens.kt`, `core/.../export/Export.kt`, `core/.../journal/Journal.kt` | `SessionScreen.kt` l. 274 : `listOf("LUMINAIRE", "MESURE", "OBSERVATION", "AUTRE")`. `Model.kt` l. 319 : `data class DxfHint(…)`. `MapView.kt` l. 61 : `maxZoomLevel = 21.0`. `Screens.kt` l. 153 : `enabled = origin != null && name.isNotBlank()`. `Export.kt` l. 80 : « blocks are not defined yet ». `Journal.kt` l. 20–21 : `ignoreUnknownKeys = true`, `classDiscriminator = "type"` |

**Conclusion.**
- **Confirmé.** La ligne `blockName = "LUMI_${kind}_GENERIC"` est bien à la ligne **275** de `app/src/main/java/app/lumisite/android/SessionController.kt`.
- C'est le point critique : chaque luminaire porterait le même nom de bloc, absent du gabarit.
- Le mapping vers les noms canoniques est au **§ 6.2** : la famille vient du catalogue, `DxfHint(layer = "RES - Elec", blockName = <nom canonique>)`, et une table de correspondance pour les anciennes sessions. Il est livré dans le **lot 2** (§ 13).

### 12.5 Vérification complémentaire : `session_stopped` et le schéma

| Date / heure (Paris) | Commande | Résultat |
|---|---|---|
| 29/10/2026 18:27 | Lecture de `docs/session.schema.json` au commit `1fb8fc5`, puis validation par `jsonschema` (environnement Python temporaire hors dépôt) d'un événement `{"type":"session_stopped","id":"ev-1","t":{…},"reason":"user"}` contre `$defs/SessionEvent` | **0 erreur.** `SessionEvent.properties.type` est `{"type": "string"}`, **sans énumération**. Les 6 branches `if/then` (calibration, relocalization, sync, survey, tracking_state, time_base_added) ne contraignent que leur propre type. Contrôle : un `survey` incomplet donne bien 3 erreurs (`kind`, `tracking`, `positionSource`) |

**Conclusion.** EasyClaw affirmait que « tout export actuel est invalide en validation stricte » parce que `session_stopped` est absent du schéma. **C'est infirmé, et EasyClaw a reconnu son erreur** (retour du 29/10, vers 23 h). Il y a seulement un **manque de documentation** : ajouter une branche `if/then` pour `session_stopped` (`reason` requis) dans le schéma et dans `SESSION_FORMAT.md` (lot 1).

### 12.6 Vérification complémentaire : géocodage

| Date / heure (Paris) | Commande | Résultat |
|---|---|---|
| 29/10/2026 18:22:51 | `curl "https://data.geopf.fr/geocodage/search?q=10+rue+de+Rivoli+Paris&limit=1"` | HTTP 200, 0,75 s |
| 29/10/2026 18:22:52 | `curl "https://api-adresse.data.gouv.fr/search/?q=10+rue+de+Rivoli+Paris&limit=1"` | HTTP 200, 0,86 s, sans redirection |

**Conclusion.** Les deux services répondent. On n'écrit donc pas que la Géoplateforme « a pris la suite » d'`api-adresse.data.gouv.fr` : sur ce point, la correction d'EasyClaw est confirmée. On utilise la Géoplateforme en premier (§ 3.4).

### 12.7 Bilan des corrections d'EasyClaw

| Point d'EasyClaw | Verdict | Où c'est intégré |
|---|---|---|
| A2 : `close()` sur le fil de l'interface pendant le rendu (l. 136–139) | **Juste**, confirmé | § 1.2 |
| A4 : la cause est le nom vide, pas le 502 | **Juste**, confirmé (l. 153) | § 1.4 |
| G : `est` seulement si `pose` et `seg` existent | **Juste**, confirmé (l. 269–270) | § 6.1 |
| H : `Row` 0,58 / 0,42, caméra dans la colonne qui défile | **Juste** | § 11.1 |
| I : `maxZoomLevel = 21.0` en dur ; `setMaxZoomLevel` sans borne ; risque de boucle de `MapTileApproximater` | **Juste** pour la l. 61 ; le reste vient de sa lecture des sources osmdroid, non refaite ici | § 3.6 |
| `blockName = "LUMI_${kind}_GENERIC"` | **Juste**, confirmé (l. 275) | § 6.2 |
| L avant C | **Juste**, confirmé par les mesures | § 3, § 13 |
| WMTS : `PM` donne 400, l'orthophoto en `PM` | **Faux, erreur reconnue par EasyClaw** : le 400 venait du format d'image ; `PM_0_19` pour les deux couches, `PM` est un alias non déclaré | § 3.5 |
| Pas de limite de 50 000 nœuds | **Faux, erreur reconnue par EasyClaw** : la limite existe (wiki, `settings.yml`), mais elle n'est pas dans `capabilities` ; dépassée dès 0,002 degré carré en zone dense | § 3.1 |
| `map.json` sans géométrie dans les chemins ; écarter les relations d'itinéraires | **Juste**, confirmé | § 3.1 |
| Géocodage : api-adresse existe encore | **Juste**, confirmé | § 3.4 |
| Thème : `setColorFilter`, `INVERT_COLORS`, `ColorMatrix` sur mesure | **Juste** (API publique d'osmdroid) | § 11.3 |
| `session_stopped` rend tout export invalide | **Faux, erreur reconnue par EasyClaw** : la validation passe ; c'est un manque de documentation | § 1.2, § 12.5 |
| Synchro : limite à 3 appareils et plus | **Juste** (raisonnement) | § 4.1 |
| K : DXF en Kotlin, inversion du cahier des charges | **Sans objet** : D1 interdit le DXF dans l'appli, et la fiche K est déjà corrigée | § 7.2 |
| Comptage OSM « 8 fois sous-estimé » | **Non comparable** (Overpass filtré contre API brute) | § 3.2 |
| Photos d'équipement oubliées | **Juste** ; tranché par D2 (fichiers séparés) | § 6.5 |
| Reprise des relevés déjà écrits (fiche D) | **Juste** | § 2.2 |
| `ArchiveFileFactory` non mentionné | Piste notée, non validée | § 3.7 |
| « `PROPOSITIONS.md` n'existe pas » (fiche UX § 1) | **Juste** : le document existait dans le clone local, hors suivi git ; il est maintenant fusionné ici | § 16 |
| Garder les noms fautifs comme référence (légende éclatée) | **Écarté par D4** : renommage unique dans le gabarit + normalisation par alias ; risque couvert par un test | § 6.3, § 9.8 |
| Liste exacte des 25 blocs ; renommage limité aux 6 noms fautifs | **Juste**, intégré | § 6.3, § 9.3 |
| Échelles de spot purement graphiques (aucun ATTDEF/ATTRIB) | **Juste**, intégré | § 9.4, § 10.4 |

### 12.8 Retours d'EasyClaw sur la contre-expertise (29/10/2026, vers 23 h)

- **Trois erreurs reconnues :**
  1. le 400 sur `PM` venait d'un mauvais format d'image, pas du nom de la grille ;
  2. la limite de 50 000 nœuds existe bien ;
  3. l'affirmation sur `session_stopped` était fausse.
- **Compléments intégrés :**
  - format d'image imposé par couche (§ 3.5) ;
  - message exact du 400 « too many nodes » et dépassement possible dès 0,002 degré carré, d'où deux garde-fous (§ 3.1, lot 3) ;
  - liste exacte des 25 blocs (§ 6.3, § 9.3, lot 2) ;
  - renommage limité aux six noms fautifs (§ 6.3) ;
  - échelles de spot purement graphiques (§ 9.4) ;
  - PoC en attente d'accord, et copie du DWG lue en lecture seule (§ 7.3).
- **Désaccord maintenu :** le nom de référence. Il est tranché par D4 (§ 6.3, § 15.3 C24).
- **Vérifications refaites à 23:09** : 400 « too many nodes » sur 0,002 degré carré à Paris ; 400 IGN sur un format inversé.

---

## 13. Lots ordonnés pour Devin

Chaque lot est un bloc **prêt à coller**, qui se suffit à lui-même. Ordre recommandé : 1 → 14. Le lot 8 (thème) est indépendant et peut s'insérer n'importe où. Le lot 11 (outil de bureau) peut avancer en parallèle dès que le lot 6 a fixé le format.

| Lot | Contenu | Priorité | Sections |
|---|---|---|---|
| 1 | Bugs bloquants : export visible, « Terminer », Retour, « Créer », « Nouveau », chevauchement caméra/boutons | P0 | § 1, § 11.1 |
| 2 | Relevé fiable, ordre des étapes, catalogue canonique, **fin des `_GENERIC`** | P0/P1 | § 2, § 6.1–6.3 |
| 3 | Rectangle + **API OSM v0.6** + géocodage IGN (fiche L avant C) | P1 | § 3.1, 3.3, 3.4 |
| 4 | Overpass en secours (fiche C) | P1 | § 3.2 |
| 5 | Zoom à l'échelle d'une pièce, fonds OSM/IGN/Esri, cache de la zone | P0/P1 | § 3.5–3.7 |
| 6 | Export GeoJSON + export du projet | P1 | § 8 |
| 7 | Objets, barre d'annotation façon Xodo, saisie rapide, notes | P1 | § 6.4, 6.5, § 7 |
| 8 | Thème Système / Clair / Sombre (+ nuit rouge en option) | P1 | § 11.3 |
| 9 | Synchro acoustique : documentation, puis bouton unique | P1 doc / P2 code | § 4 |
| 10 | AR : réticule, plans, robustesse, torche, mesure | P1 / P2 | § 5 |
| 11 | Outil de bureau : fusion, L93, DXF/DWG avec les blocs de Sébastien | P1 | § 9, § 10 |
| 12 | Dictaphone iPad et voix | P1 format / P2 outil | § 6.6 |
| 13 | Zonage | P1, après la décision de format | § 6.7 |
| 14 | Plan local en mètres, seulement si le lot 5 ne suffit pas | P2 | § 3.6 |

### Lot 1 — P0 : le téléphone redevient utilisable

```text
LOT 1 — LumiSite — Bugs bloquants P0
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main, base 1fb8fc5. Crée une branche dédiée.
Appareil de test : Xiaomi 13T Pro (Android, ARCore).
Contraintes : le format lumisite.session 0.1 doit rester lisible. Aucun DXF dans l'appli. Pas de code GPL ni propriétaire.

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/data/Repository.kt (export)
- app/src/main/java/app/lumisite/android/ui/SessionScreen.kt (ExportPanel, mise en page)
- app/src/main/res/xml/file_paths.xml
- app/src/main/java/app/lumisite/android/SessionController.kt (close(), l. 136-139)
- app/src/main/java/app/lumisite/android/capture/ArTracker.kt (pause/close)
- app/src/main/java/app/lumisite/android/MainActivity.kt (navigation, BackHandler)
- app/src/main/java/app/lumisite/android/ui/Screens.kt (SiteScreen, SiteEditorScreen, HomeScreen ; l. 109 et 153)
- docs/session.schema.json, docs/SESSION_FORMAT.md, docs/TESTS_TABLETTE.md

CHANGEMENTS
1. Export (A1) : après un export réussi, copier le ZIP dans Téléchargements via MediaStore (API 29+), ouvrir tout de suite
   ACTION_SEND, et afficher « Enregistré dans Téléchargements : <nom>.zip ». Sous Android 10 : sélecteur de documents.
   En cas d'échec, le bouton redevient actif et l'erreur reste affichée.
2. Terminer la session (A2) : fermer dans cet ordre : arrêt du rendu GL, pause de la GLSurfaceView, fermeture de la
   session ARCore sur le fil GL (ou après onPause), le tout dans un try/catch ; puis écrire session_stopped ;
   puis seulement changer d'écran. ArTracker.pause()/close() ne doivent plus laisser passer d'exception.
3. Retour (A3) : BackHandler sur chaque écran (éditeur → accueil, site → accueil, session → site, avec la même
   séquence que « Terminer »). Bouton Retour visible en tête d'écran. Colonne d'actions qui défile.
4. Créer le site (A4) : astérisque sur « Nom du site » et texte « Nom de projet requis » tant qu'il est vide.
   Nom vide au moment de créer → nom par défaut « Site <date> ». Bouton actif dès qu'une origine existe, quel que soit
   l'état de la récupération OSM ; libellé « Créer sans données OSM ». Ajouter finally { busy = false }.
5. Accueil (A5) : le bouton affiche exactement « Nouveau ». Chercher toutes les chaînes visibles concernées.
6. Mise en page (H, correctif minimal) : la GLSurfaceView sort de la colonne qui défile. Taille maximale fixe,
   en haut à droite en paysage. Aucun bouton ne se dessine sur l'image de la caméra.
7. Schéma : ajouter dans docs/session.schema.json une branche if/then pour "session_stopped" (reason requis),
   et la décrire dans SESSION_FORMAT.md. (La validation passe déjà ; c'est un manque de documentation.)

CRITÈRES D'ACCEPTATION (sur le 13T Pro)
- Le ZIP est dans Téléchargements, s'ouvre sur un PC, la feuille de partage propose Drive et le courriel,
  et un message dit où il est.
- Dix fois de suite, « Terminer la session » et le geste retour reviennent à la page du site, sans retour au bureau
  Android. La session rouverte contient session_stopped et les relevés précédents.
- Depuis un projet, le bouton et le geste retour reviennent à la liste des projets. Depuis l'accueil, le geste
  retour quitte l'appli.
- Sans nom : le message s'affiche, puis la création se fait avec un nom par défaut. Après un 502 ou en mode avion
  pendant « Récupérer » : « Créer » fonctionne, et « Récupérer » redevient cliquable.
- L'accueil affiche « Nouveau ».
- En paysage : carte, caméra et boutons visibles sans chevauchement.
- ./gradlew :core:test vert. Un export 0.1 ancien se relit. Une ligne ajoutée dans docs/TESTS_TABLETTE.md.
```

### Lot 2 — P0/P1 : relevé fiable, ordre des étapes, catalogue canonique, fin des `_GENERIC`

```text
LOT 2 — LumiSite — Relevé fiable et noms de blocs canoniques
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main, base 1fb8fc5 (ou fin du lot 1). Branche dédiée.
Contraintes : l'appli n'écrit PAS de DXF (décision de Sébastien). Le format 0.1 reste lisible : on n'ajoute que des
champs optionnels ou des valeurs de chaînes libres (kind et positionSource sont des String). Aucun nouveau "type"
d'événement.

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/SessionController.kt (survey(), l. 266-278 ; la l. 275 contient
  dxf = DxfHint(layer = "LUMI_${kind}", blockName = "LUMI_${kind}_GENERIC"))
- app/src/main/java/app/lumisite/android/ui/SessionScreen.kt (l. 274 : pastilles en dur LUMINAIRE/MESURE/OBSERVATION/AUTRE ;
  ordre des pastilles = ordre de l'énumération Mode)
- core/src/main/kotlin/app/lumisite/core/model/Model.kt (SurveyEvent, DxfHint l. 319)
- core/src/main/kotlin/app/lumisite/core/export/Export.kt (derivedSurveyPoints)
- nouveau : catalog/equipment_catalog.json (ressource de l'appli, aussi copiée dans le paquet d'export plus tard)
- nouveau : core/.../catalog/ (lecture du catalogue et des alias), avec ses tests

CHANGEMENTS
1. Ordre des pastilles : 1 Synchro, 2 Calibrer, 3 Relevé, 4 Recaler, 5 Export.
2. Une seule fonction de position, partagée par l'écran et derivedSurveyPoints : un recalage ne s'applique qu'aux
   poses postérieures. Les sessions anciennes sont affichées avec cette même fonction ; estimatedSite reste dans le
   journal, sans réécriture.
3. Si aucune position n'est calculable (ni suivi + calibration, ni position manuelle) : ne pas créer de point fantôme,
   afficher « Suivi ou calibration manquant : touchez la carte ou cochez la position manuelle ».
4. Relevés visibles tout de suite, après « Terminer » et à la réouverture, et aussi sur la page du site.
   Sélection d'un relevé : déplacer (glisser, ou pas de 0,1 / 0,5 m en est-nord), changer la famille et le
   commentaire, supprimer. Chaque modification est un nouvel événement (journal en ajout seul).
5. Point courant avec secteur de cap (PoseGeometry.forwardPlaneAngle) et bouton « Centrer / Suivre ».
6. Catalogue canonique, chargé comme données (et non codé dans le composable), version "lumisite-catalog-1.0".
   Pour chaque entrée : block_name, aliases, label, famille, default_layer="RES - Elec", orientable, scale_rules,
   details_schema, icon, keywords.
   Les 25 noms EXACTS lus dans le DWG (hors xref 02-Plan au sol), à recopier caractère pour caractère :
   Amplificateur, Camera, Diffuseur Sonore Radio, Déclencheur Manuel, Lanterne EP moderne, Lustre, PDL,
   bloc secour, bougeoir électrifié, boîte de dérivation, enceinte, extincteur, haut-parleur, hublot ecl,
   interrupteur, lampe, lineaire fluo, micro col, prise xlr, prise électrique, projecteur,
   projecteur thorn contrast, spot, tableau divisionaire, tgbt.
   Renommage limité aux SIX noms fautifs :
   - VALIDÉS (block_name = nom corrigé, ancien nom dans aliases) : bloc secour → bloc secours ;
     tableau divisionaire → tableau divisionnaire.
   - EN ATTENTE de Sébastien (block_name = nom du DWG tant que ce n'est pas validé) : Camera → caméra ;
     hublot ecl → hublot éclairage ; lineaire fluo → linéaire fluo ; tgbt → TGBT.
   - NE PAS renommer : prise électrique, boîte de dérivation (déjà corrects), ni aucun autre nom.
     micro col reste tel quel tant que Sébastien n'a pas tranché.
   Ajouter « autre (à classer) », qui ne correspond à aucun bloc.
   La saisie accepte les deux formes : un ancien nom (alias) est traduit vers le block_name canonique, et seul le nom
   canonique est écrit (kind, dxf.blockName, payload.block_name).
   Orientables : spot, projecteur, projecteur thorn contrast, Camera, haut-parleur, lineaire fluo, prise électrique,
   bloc secours.
   Ne pas ajouter de champ « taille » : les 3 échelles de spot sont purement graphiques et appliquées au bureau.
7. Remplacer les pastilles en dur par une grille du catalogue (famille en 1 tap).
8. survey(familyId, note, manual, details?) : kind = fam.blockName ; payloadSchema = "lumisite-equipment/1" ;
   payload = { catalog_version, block_name, details? } ; dxf = DxfHint(layer = fam.defaultLayer, blockName = fam.blockName).
   Plus aucun "_GENERIC" dans une nouvelle session.
9. Documenter dans SESSION_FORMAT.md : kind = nom canonique du catalogue, et la correspondance des anciennes valeurs
   LUMI_LUMINAIRE_GENERIC / LUMI_AUTRE_GENERIC → « à classer », LUMI_OBSERVATION_GENERIC → annotation texte,
   LUMI_MESURE_GENERIC → hauteur ou texte, à valider.

CRITÈRES D'ACCEPTATION
- L'ordre visible est Synchro → Calibrer → Relevé → Recaler → Export.
- Sans calibration et sans case manuelle : message, pas de point. Avec calibration et suivi : le point apparaît tout
  de suite, survit à « Terminer » et à la réouverture, se déplace et se supprime.
- Un relevé fait avant un recalage ne saute pas en arrière. Un relevé fait après suit la nouvelle transformation,
  à l'écran et dans le ZIP.
- Un spot posé : kind="spot", dxf.blockName="spot", dxf.layer="RES - Elec". Les noms "bloc secours" et
  "tableau divisionnaire" apparaissent dans le sélecteur.
- Ajouter une entrée au JSON du catalogue la fait apparaître dans le sélecteur sans modifier l'écran.
- Le catalogue contient exactement les 25 noms ci-dessus (avec les 2 corrections validées) plus « autre ». « prise
  électrique » et « boîte de dérivation » n'ont pas changé, et « micro col » est tel quel.
- Tests JVM : chargement du catalogue et des alias (bloc secour → bloc secours, tableau divisionaire →
  tableau divisionnaire), les deux formes acceptées en entrée, seul le nom canonique en sortie, famille inconnue refusée, position
  avec et sans calibration, relecture d'une session 0.1 sans ces champs. Ligne ajoutée dans docs/TESTS_TABLETTE.md.
```

### Lot 3 — P1 : rectangle de site, API OSM v0.6 en premier, géocodage IGN

```text
LOT 3 — LumiSite — Contours façon JOSM (fiche L, AVANT la fiche C)
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main. Branche dédiée.
Pourquoi d'abord : le 29/10/2026, Overpass répondait 502 ou pas du tout dans les 30 s sur overpass-api.de,
overpass.kumi.systems et overpass.private.coffee, alors que l'API OSM répondait 200 en environ 1 s.

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/data/OsmClient.kt (fetchFeatures, toFeature, bboxAround, search, read)
- app/src/main/java/app/lumisite/android/ui/Screens.kt (SiteEditorScreen)
- app/src/main/java/app/lumisite/android/ui/MapView.kt (dessin du rectangle)
- modèle du site (OsmSnapshot.bbox ; stocker la bbox même pour un site créé sans OSM)
- si possible, la logique pure dans core/ (construction de la bbox, parsing OSM JSON → géométries), avec ses tests

CHANGEMENTS
1. Rectangle de site : par deux coins, ou par un cadre que l'on tire. La bbox du site est ce rectangle. Le réafficher à
   chaque ouverture du site, et mémoriser les « zones récentes ».
2. Source principale des contours :
   GET https://api.openstreetmap.org/api/0.6/map.json?bbox=ouest,sud,est,nord (bbox du rectangle).
   - Les ways ne contiennent QUE des identifiants de nœuds : faire la jointure nœud ↔ way.
   - Écarter avant la reconstruction les relations type=route, superroute, route_master, boundary, public_transport
     (à la cathédrale, des itinéraires de 630 et 724 membres sont dans la réponse).
   - Multipolygones : garder les anneaux intérieurs (ce que toFeature ne fait pas aujourd'hui).
   - Filtrer localement : building, building:part, indoor, highway=street_lamp, entrance, man_made, power.
3. Garde-fous (les DEUX sont obligatoires) :
   - Limites de l'API (settings.yml du serveur) : max_request_area 0.25, max_number_of_nodes 50000,
     max_number_of_way_nodes 2000 (par chemin, à l'enregistrement). La limite de nœuds n'est PAS déclarée dans
     /api/capabilities.
   - Garde-fou SURFACE : refuser avant l'appel une bbox de plus de 0,01 degré carré environ, et proposer de réduire.
   - Garde-fou DENSITÉ : traiter le HTTP 400 dont le texte est exactement
     « You requested too many nodes (limit is 50000). Either request a smaller area, or use planet.osm ».
     En zone dense, il arrive dès 0,002 degré carré (centre de Paris, vérifié le 29/10/2026). Proposer à l'utilisateur :
     (a) réduire le rectangle, ou (b) découper la zone en sous-bbox (quadrants, récursion bornée), appeler chaque
     sous-bbox, puis FUSIONNER en dédoublonnant par type+id avant la reconstruction des géométries.
     Nombre d'appels affiché et plafonné ; aucun rechargement en boucle.
   - Autre 400 (« maximum bbox size ») : proposer de réduire ;
   - 509 / 429 : passer à Overpass (lot 4 ; tant qu'il n'est pas fait, « créer sans contours ») ;
   - un seul appel par geste « Récupérer », User-Agent identifiable, aucune boucle.
   Politique : https://operations.osmfoundation.org/policies/api/
4. Enregistrer la réponse brute datée (osm_source.json) dans le dossier du site, pour le hors ligne et la traçabilité.
5. Géocodage : en France, d'abord https://data.geopf.fr/geocodage/search?q=… (sans clé), puis Nominatim (hors France ou
   sans résultat). Nominatim : 1 requête par seconde au plus, User-Agent, recherche sur « Entrée » ou après 1 s sans frappe.
   Ne pas écrire que l'IGN « remplace » api-adresse.data.gouv.fr (les deux répondaient le 29/10). URL configurable.

CRITÈRES D'ACCEPTATION
- Sur un rectangle qui couvre un bâtiment, les contours arrivent en moins de 3 s en 4G, sans Overpass.
- Une URL d'API invalide (test) mène à « créer sans contours », avec un message en français. Le bouton Créer reste actif.
- Un rectangle trop grand est refusé, avec une proposition de réduction.
- Rectangle dense (test : bbox 2.33,48.85,2.38,48.89) → 400 « too many nodes » → message en français, choix entre
  « Réduire » et « Découper et fusionner ». Le découpage donne des contours complets sans doublon, avec un nombre
  d'appels plafonné.
- Aucun itinéraire de randonnée n'est dessiné. Un bâtiment avec une cour garde son trou.
- « 10 rue de Rivoli Paris » renvoie d'abord le résultat IGN.
- Rouvrir le site recadre sur le rectangle.
- Tests JVM : jointure nœuds/ways, multipolygone avec trou, filtre des relations, refus de bbox, reconnaissance du
  400 « too many nodes », découpage en quadrants, fusion sans doublon par type+id.
```

### Lot 4 — P1 : Overpass en secours

```text
LOT 4 — LumiSite — Overpass en secours (fiche C)
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main, après le lot 3. Branche dédiée.

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/data/OsmClient.kt (fetchFeatures, read)
- app/src/main/java/app/lumisite/android/ui/Screens.kt (messages)

CHANGEMENTS
1. Overpass n'est appelé que si l'API OSM v0.6 échoue (erreur réseau, 509, 429, 5xx).
2. Requête sur la bbox du rectangle (plus de disque bboxAround), avec [timeout:25] et un plafond d'éléments
   (out … 500 ou équivalent). Message si le plafond est atteint.
3. Miroirs dans l'ordre : overpass-api.de, overpass.kumi.systems, overpass.private.coffee. Deux ou trois essais par
   miroir, attentes de 1 s, 2 s puis 4 s. Timeout de lecture de 25 à 30 s. User-Agent conservé.
4. Si tout échoue : carte de fond seule, et « Le serveur de contours est indisponible. Le site est utilisable sans
   contours. » Le bouton Créer reste actif.

CRITÈRES D'ACCEPTATION
- API OSM forcée en échec : bascule visible sur Overpass, puis « sans contours » si Overpass échoue aussi.
- Réseau coupé : la création aboutit et le message est en français.
- Test JVM de la construction de la requête (bbox, plafond, timeout) derrière une interface.
```

### Lot 5 — P0/P1 : zoom à l'échelle d'une pièce, fonds, cache de la zone

```text
LOT 5 — LumiSite — Sur-zoom, aides au placement, fonds OSM/IGN/Esri, cache de la zone
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main. Branche dédiée.
Décision de Sébastien : le zoom sert à implanter et à se déplacer, pas à voir du détail. Le sur-zoom au-delà de 21
(jusqu'à 24-26) est accepté même flou. Les vecteurs n'ont pas de limite. Les fonds satellite ne sont mis en cache que
sur la zone du site.

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/ui/MapView.kt (l. 61 : minZoomLevel = 3.0; maxZoomLevel = 21.0 ; MAPNIK natif 19)
- app/src/main/java/app/lumisite/android/MainActivity.kt (osmdroidTileCache)
- app/src/main/java/app/lumisite/android/ui/SessionScreen.kt (réticule, loupe, pas fins, Suivre)

CHANGEMENTS
1. maxZoomLevel = 26 (setMaxZoomLevel n'a pas de borne dans osmdroid 6.1.20). Ne jamais télécharger de tuiles au-delà
   du zoom natif : MapTileApproximater agrandit. Sinon, une source dont l'URL reste au zoom natif, mise à l'échelle.
2. Risque connu (issue osmdroid #1106) : avec un cache vide et un fort sur-zoom, approximateTileFromLowerZoom peut
   occuper le processeur à 100 %. Tester en mode avion, et borner la recherche si besoin.
3. Aides : réticule central (on déplace la carte sous la croix), loupe au-dessus du doigt, pas de 0,1 m / 0,5 m vers
   l'est et le nord (LocalTangentPlane), bouton « Suivre » avec le cap. Barre d'échelle avec « largeur visible : N m ».
4. Sélecteur de fond, avec l'attribution qui change selon le fond :
   - OSM standard (défaut) : cache de la bbox du site seulement, zooms 16 à 19, plafond de quelques centaines de
     tuiles, sur action explicite (FLAG_NO_BULK respecté) ;
   - Plan IGN : https://data.geopf.fr/wmts, LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2, STYLE=normal,
     TILEMATRIXSET=PM_0_19, FORMAT=image/png, zooms 0 à 19 ;
   - Orthophoto IGN : LAYER=ORTHOIMAGERY.ORTHOPHOTOS, STYLE=normal, TILEMATRIXSET=PM_0_19, FORMAT=image/jpeg, zooms 0 à 19.
     Modèle : ...?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=<couche>&STYLE=normal&TILEMATRIXSET=PM_0_19
     &TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=<format>. Licence Etalab 2.0 : attribution « IGN » + date ;
     cache autorisé sur la bbox du site ;
   - Esri World Imagery : en ligne seulement, AUCUN cache ni téléchargement, attribution, et désactivé si le projet
     n'accepte pas les conditions Esri.
5. « Télécharger la zone » : CacheManager.downloadAreaAsync(bbox du site, zMin, zMax natif).

CRITÈRES D'ACCEPTATION (13T Pro)
- Une pièce de 4 m occupe une grande part de la carte. Le fond est flou, les contours et les objets restent nets.
- Pose à la croix, décalage de 0,1 m, la loupe apparaît sous le doigt. « Suivre » garde le point bleu dans le cadre.
- Changer de fond garde le rectangle, et l'attribution change.
- « Télécharger la zone », puis mode avion : le fond de la zone s'affiche encore. Au zoom 26 avec un cache vide,
  l'appli reste fluide.
- Esri ne propose aucun téléchargement.
```

### Lot 6 — P1 : export GeoJSON et export du projet

```text
LOT 6 — LumiSite — Paquet d'export GeoJSON (format d'échange avec le bureau)
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main, après le lot 2. Branche dédiée.
Décision de Sébastien : l'appli n'écrit PAS de DXF. Elle exporte en GeoJSON, capable de tenir de gros projets.
L'export lumisite.session existant reste inchangé et est rangé dans raw/.

FICHIERS CONCERNÉS
- core/src/main/kotlin/app/lumisite/core/export/ (nouvel écrivain GeoJSONSeq et manifeste)
- app/src/main/java/app/lumisite/android/data/Repository.kt (export de session et de projet)
- app/src/main/java/app/lumisite/android/ui/Screens.kt (HomeScreen : icône d'export sur chaque carte de projet)
- nouveaux : docs/GEOJSON_EXPORT.md, docs/lumisite-feature.schema.json

CHANGEMENTS
1. Paquet LumiSite_<project_id>/ (dossier, ou ZIP « store » sans recompression) :
   manifest.json ; catalog/equipment_catalog.json ;
   sessions/<device_id>/<session_id>.geojsonl (GeoJSONSeq RFC 8142) + <session_id>.frames.json
   [+ <session_id>.geojson consolidé en option] ;
   photos/<device_id>/<session_id>/<photo_id>.jpg ; audio/<device_id>/<session_id>/ ;
   raw/<device_id>/<session_id>.zip (lumisite.session inchangé) ; refs/osm_source.json.
2. .geojsonl : une Feature par ligne, écrite en ajout PENDANT la session avec un fsync après chaque objet.
   Modification ou suppression = même id, rev+1, op=update/delete. Le lecteur ignore et signale une dernière ligne
   tronquée. Un fichier par appareil et par session.
3. geometry = WGS84 lon/lat, RFC 7946, sans membre crs ; null si la position absolue est inconnue.
   properties.local = {frame_id, x, y, z} en mètres (X est, Y nord, Z haut, origine du site).
   Lambert 93 n'est PAS écrit par l'appli. frames.json : origine du site en WGS84, transformations AR → site
   (calibration, recalage, résidu), événements de synchro.
4. kinds : equipment, annotation, height, shape, zone, control_point, route, track.
   equipment (obligatoires) : rev, op, kind, block_name (canonique, catalogue), catalog_version, layer ("RES - Elec"),
   status (existant/projeté/à déposer/en défaut), local, accuracy {sigma_xy_m, sigma_z_m, method}, time
   {captured_utc, device_monotonic_ns, clock_offset_ms, clock_sigma_ms, sync_ref}, device_id, session_id, operator.
   Facultatives : aim {azimuth_deg (0 = nord du site, sens horaire), ref:"site_north", tilt_deg, sigma_deg},
   height_m, height_ref, details {sous_type, technologie, culot, puissance_W, temp_couleur_K, ref_etiquette, etat},
   label, note, voice_notes[], photos[] {photo_id, path, sha256, taken_utc, role, pose}, registration,
   duplicate_of, supersedes.
5. Photos et audio en fichiers séparés, chemin relatif + sha256, jamais en base64. Identifiants UUID v7. UTF-8.
6. manifest.json : schema "lumisite-geojson-export", schema_version "1.0.0", project_id, catalog_version, devices[],
   sessions[] (features_file, frames_file, feature_count, closed), files[] {path, bytes, sha256}. Écriture atomique
   (fichier temporaire puis renommage) à chaque clôture de session et à chaque export.
7. Export du projet (fiche B) : icône sur chaque carte de projet → paquet de toutes les sessions. Même destination
   que le lot 1 : Téléchargements + feuille de partage + phrase de confirmation.
8. Publier docs/GEOJSON_EXPORT.md et docs/lumisite-feature.schema.json.

CRITÈRES D'ACCEPTATION
- Une session de 2 000 objets s'exporte. Le .geojsonl s'ouvre dans QGIS (pilote GeoJSONSeq) et les points tombent au
  bon endroit sur OSM.
- Appli tuée en plein relevé : au redémarrage, tous les objets sauf au plus le dernier sont dans le fichier, et la
  ligne tronquée est signalée.
- Une correction ajoute une ligne rev 2 sans réécrire la ligne 1.
- Chaque fichier a son sha256 dans le manifeste, et un fichier modifié est détecté.
- Un projet de deux sessions donne un paquet retrouvable dans Téléchargements. Chaque raw/ se relit avec SessionImporter.
- Aucun DXF produit. Un export 0.1 ancien se relit.
- Tests JVM : écriture puis relecture avec une ligne tronquée, dernière rev gagnante, manifeste, validation d'un
  .geojsonl contre docs/lumisite-feature.schema.json.
```

### Lot 7 — P1 : objets, barre d'annotation façon Xodo, saisie rapide, notes

```text
LOT 7 — LumiSite — Barre d'outils de relevé et d'annotation, détails facultatifs, notes texte/voix/photo
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main, après les lots 2 et 6. Branche dédiée.
Contraintes : on reprend l'ergonomie de Xodo 11.3.1 (analyse approuvée) SANS aucun code, icône, XML ou chaîne
de Xodo/PDFTron. Icônes Material (Apache-2.0). Aucun DXF dans l'appli. Champs nouveaux optionnels (format 0.1 lisible).

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/ui/SessionScreen.kt, ui/MapView.kt (overlay de dessin, barre)
- app/src/main/java/app/lumisite/android/SessionController.kt (survey, notes, pièces jointes)
- core/src/main/kotlin/app/lumisite/core/model/Model.kt (champs optionnels ; SurveyEvent.payload et attachments existent)
- core/.../export/ (Features equipment / annotation / shape / zone)
- docs/SESSION_FORMAT.md

CHANGEMENTS
1. Barre unique (sans recouvrir la carte ni la caméra), toujours une icône ET un mot français :
   Se déplacer | Poser un objet du catalogue | Forme + texte (cercle, rectangle : glisser d'un coin à l'autre, OU tap
   pour une taille par défaut ; libellé au centre) | Ligne / polyligne / flèche (points successifs, « Fin ») |
   Polygone (double-tap pour fermer) | Annotation de diagnostic (liste : Passage de câble, Câble à nu, En default,
   Capteur, Cablette de terre, Niveau, Texte) | Commentaire (attaché à l'objet) | Sélection (poignées, déplacer,
   redimensionner, supprimer, dupliquer, style) | Annuler.
2. Feuille de style : trait, remplissage (transparent compris), épaisseur ; opacité au second niveau.
   Six couleurs nommées (noir, rouge, orange, jaune, vert, bleu) + « récent » + « sans remplissage ».
   Préréglages (trio couleur / épaisseur / type). Une seule police.
3. Un seul état actif à la fois : aucun / tracé en cours / sélection / style / saisie. Changer d'outil termine ou annule
   le tracé, avec confirmation si des points sont posés.
4. Liste des objets du relevé : toucher une ligne sélectionne l'objet. État vide : « Aucun objet sur ce relevé ».
5. Géométries en mètres dans le repère du site (Enu), pas en pixels.
6. Saisie rapide : grille du catalogue, avec en tête les favoris épinglés et les 6 derniers utilisés. Visée par
   glissement autour du symbole, ou par le cap de l'appareil. Détails facultatifs en second niveau : sous_type (liste
   par famille, ex. spot : PAR20, MR16 dichroïque, GU10 LED, LED basique, autre), technologie, culot, puissance_W,
   temp_couleur_K, ref_etiquette, etat, status. La dernière valeur est reprise pour la même famille.
   Puissance absente = null, jamais 0. Valeurs aberrantes refusées dès la saisie.
   PAS de champ taille ni miroir : le bureau les déduit du sous-type.
7. Notes attachées à l'objet : texte ; note vocale (appui long → clip vn_<n>.m4a avec start_utc et
   device_monotonic_ns) ; photo (role "etiquette" ou "vue", avec la pose). Fichiers séparés + sha256, via
   SurveyEvent.attachments en 0.1 et voice_notes / photos dans le GeoJSON.
8. Export : chaque objet ou forme devient une Feature equipment / annotation / shape / zone du paquet du lot 6.
9. Hors de ce lot (en attente de décision) : nuage de révision, bulle de rappel, note à icônes, trait libre et gomme.

CRITÈRES D'ACCEPTATION
- Rectangle « local TGBT » avec le commentaire « à vérifier » : on le déplace, l'agrandit, le supprime, et on annule.
  Le GeoJSON contient la Feature shape avec libellé, commentaire, géométrie WGS84 et mètres locaux.
- Poser un spot demande un tap sur la famille et un tap sur la carte. Les détails vides ne bloquent pas. Le spot sort
  avec block_name canonique.
- Une note vocale et une photo d'étiquette sont présentes dans le paquet avec leur sha256.
- Une session d'avant ce lot s'ouvre. Aucun DXF produit.
- Tests JVM : géométrie en mètres, relecture 0.1 sans ces champs, validation des valeurs (puissance négative, 12 000 K).
```

### Lot 8 — P1 : thème

```text
LOT 8 — LumiSite — Bouton de thème en haut à droite (Système / Clair / Sombre), nuit rouge en option
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main. Indépendant des autres lots. Branche dédiée.

FICHIERS CONCERNÉS
- thème Compose (MaterialTheme), app/src/main/java/app/lumisite/android/MainActivity.kt, tous les écrans (barre du haut)
- app/src/main/java/app/lumisite/android/ui/MapView.kt (filtre de la couche de tuiles)

CHANGEMENTS
1. Icône soleil/lune en haut à droite sur tous les écrans. Trois états en rotation : Système, Clair, Sombre.
   Préférence en DataStore, appliquée dès le lancement, sans flash blanc. Deux ColorScheme choisis selon la
   préférence (plus seulement isSystemInDarkTheme()).
2. En mode sombre, carte assombrie : mapView.overlayManager.tilesOverlay.setColorFilter(...). Éviter
   TilesOverlay.INVERT_COLORS seul (eau et aplats jaunes dégradés, issue #1804). Préférer une ColorMatrixColorFilter
   sur mesure (point de départ : luminosité -0,25, saturation -0,6), à régler sur le terrain.
3. Symboles, étiquettes et réticule lisibles dans les deux modes (contour blanc ou noir). L'aperçu caméra n'est
   jamais filtré.
4. Option (peut suivre) : 4e état « Nuit rouge » pour les relevés dans le noir.

CRITÈRES D'ACCEPTATION
- Un appui change le thème tout de suite, sans perdre le relevé en cours ni le zoom.
- Le choix survit au redémarrage. La carte n'éblouit pas en sombre. Contraste WCAG AA dans les deux modes.
```

### Lot 9 — P1 doc / P2 code : synchro acoustique

```text
LOT 9 — LumiSite — Synchro acoustique : note de format d'abord, bouton unique ensuite
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main. Branche dédiée.
Contraintes : garder le chirp (2-6 kHz, 60 ms, Hann, corrélation) comme top temporel. Ne pas ajouter de valeur à
l'enum SyncMethod (le schéma 0.1 la rejetterait) : uniquement des champs optionnels.

FICHIERS CONCERNÉS
- core/src/main/kotlin/app/lumisite/core/sync/Sync.kt (Chirp, ChirpDetector, SyncEvent, AudioDetection)
- app/.../AudioSync, SyncPanel (SessionScreen.kt)
- nouveau : docs/SYNC_ACOUSTIQUE.md

CHANGEMENTS
1. Tout de suite : docs/SYNC_ACOUSTIQUE.md pour l'outil de bureau et iOS : fréquences, durée, fenêtre, 48 kHz,
   formule de corrélation, seuil (snr, score), trame d'identification (device_id, modèle, compteur d'émission, index
   des chirps entendus), formule du décalage d'horloge (principe BeepBeep : chaque appareil enregistre son chirp et
   ceux des autres sur sa propre horloge), détection a posteriori dans un long enregistrement (dictaphone),
   limites (résidu de l'ordre du temps de vol, environ 1 ms pour 3 m ; à 3 appareils et plus, l'ordre relatif des
   chirps est nécessaire).
2. Ensuite : un seul bouton « Synchroniser » (émet + écoute), une trame d'identification juste après le chirp
   (ggwave MIT possible), un état « Calé (N) » = N identifiants reconnus au-dessus du seuil. Code de synchro généré,
   modifiable, plus obligatoire. Top manuel dans un menu « secours ».
3. SyncEvent / AudioDetection : champs optionnels protocole, ids entendus, index du chirp local, index des pairs,
   compteur.

CRITÈRES D'ACCEPTATION
- Deux téléphones à 1 m, pièce calme : un seul bouton, « Calé (2) » des deux côtés en quelques secondes, sans code saisi.
- Micro coupé sur l'un : l'autre n'affiche pas « Calé » et propose le top manuel.
- Un export 0.1 ancien s'ouvre. Tests : détection du chirp (déjà couverte) + champs optionnels + relecture 0.1.
```

### Lot 10 — P1 / P2 : réalité augmentée

```text
LOT 10 — LumiSite — AR : réticule, plans, robustesse de pose, torche, puis mesure
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main. Branche dédiée.
Contraintes : aucun code de CamToPlan ni d'Aruler (principes seulement). Les exemples du SDK ARCore (Apache-2.0)
peuvent être copiés en gardant l'en-tête de licence (pas l'appli de démo entière). Format 0.1 lisible.

FICHIERS CONCERNÉS
- app/src/main/java/app/lumisite/android/capture/ArTracker.kt
- app/src/main/java/app/lumisite/android/SessionController.kt, ui/SessionScreen.kt
- core/ (intersection rayon/plan analytique, tests)

CHANGEMENTS
1. Réticule Compose par-dessus la GLSurfaceView, avec une couleur selon l'état (initialisation, suivi, perdu).
2. Configuration : CameraConfigFilter (30 fps, DepthSensorUsage.DO_NOT_USE), tri par surface d'image décroissante,
   setCameraConfig(meilleure) ; PlaneFindingMode.HORIZONTAL ; après configure(), revérifier getDepthMode().
3. Plans horizontaux dessinés (rendu d'exemple ARCore).
4. Hit-test : plans ARCore (isPoseInPolygon) → plan du dernier objet → plan central → « pas de cible ». Intersection
   analytique rayon/plan. Rejet à angle rasant (|n·d| < 0,03) avec « Orientez l'appareil vers le sol ». Jamais de
   plantage : chaque niveau rend null.
5. Repli : avec un suivi actif mais sans cible, position à 50 cm marquée positionSource = "ESTIMATED" (chaîne libre),
   affichée d'une couleur distincte. Sans suivi ni calibration : pas de point (règle du lot 2).
6. Torche : bouton marche/arrêt, allumée par défaut en faible luminosité (LightEstimationMode.ENVIRONMENTAL_HDR).
   Jamais en permanence. 60 fps en option.
7. Sur l'image : « Calibré » + résidu (m) + échelle mesurée, ou « Non calibré ».
8. (P2) Mesure entre deux visées (deux ancres, distance euclidienne).

CRITÈRES D'ACCEPTATION (13T Pro)
- Réticule visible ; plans au sol après un mouvement lent ; le libellé change après la calibration.
- Une visée rasante est refusée avec un message, et ne donne pas de point qui saute.
- La pose de point ne plante jamais pendant 30 minutes de relevé.
- Deux visées sur une distance connue : écart noté dans docs/TESTS_TABLETTE.md.
- Sans ARCore : la vue est absente et un texte l'explique.
- Tests core : intersection analytique, angle rasant, repli ESTIMATED, relecture 0.1.
```

### Lot 11 — P1 : outil de bureau (fusion, Lambert 93, DXF/DWG avec les blocs de Sébastien)

```text
LOT 11 — Outil de bureau LumiSite : paquet GeoJSON → DXF au format de Sébastien → DWG
Emplacement : un module séparé de l'appli Android (EasyClaw propose tools/session2dxf/ ; le nom peut changer).
Python 3, ezdxf (MIT), pyproj (MIT), pytest. LibreDWG (GPL-3) seulement pour LIRE d'anciens DWG, jamais lié ni
distribué. ODA File Converter (gratuit, propriétaire) ou AutoCAD/CADCRAFT pour le DWG.
Entrée : le paquet du lot 6 (manifest.json, .geojsonl, frames.json, photos, audio, raw/).
Gabarit : copie du DXF modèle de Sébastien (blocs déjà dessinés, calques, styles, présentations
CARTOUCHE ETI A3 - 01/02). Si le modèle n'existe qu'en DWG : DXFOUT ou ODA, une fois.
Référence de test : eclairage03.dwg / .dxf (Saint-Bertrand-de-Comminges), 25 blocs, 293 INSERT.

CHANGEMENTS
1. Import : vérifier les sha256 du manifeste, lire les .geojsonl en flux, garder la dernière rev, NORMALISER les
   noms par la table d'alias avant toute insertion : bloc secour → bloc secours, tableau divisionaire →
   tableau divisionnaire (validés) ; Camera → caméra, hublot ecl → hublot éclairage, lineaire fluo → linéaire fluo,
   tgbt → TGBT seulement quand Sébastien les aura validés. Aucun autre renommage (prise électrique, boîte de
   dérivation, micro col inchangés). La bibliothèque compte exactement 25 blocs (hors xref 02-Plan au sol).
   Signaler les lignes tronquées, les noms hors catalogue et les anciens LUMI_*_GENERIC (liste « à classer »).
2. Fusion : horloges ramenées au temps commun (décalages des chirps). Doublons : même famille, distance
   < 2·√(σ1²+σ2²), azimuts compatibles → moyenne pondérée en 1/σ², avec la liste des id sources. Conflits (familles
   différentes, visées qui diffèrent de plus de 20°) → écran de validation, jamais tranchés en silence.
3. Géoréférencement (décision) :
   (a) sessions → WGS84 → Lambert 93 EPSG:2154, point par point (pyproj) ; Helmert 4 paramètres (échelle 1) sur les
       control_point liés à OSM si besoin, avec RMS et résidus ;
   (b) le plan de l'architecte est RECALÉ SUR la donnée terrain : 2 à 4 points homologues, similitude plan → L93
       appliquée à l'insertion de l'xref (échelle libre seulement si le PDF importé n'est pas à l'échelle),
       RMS et résidus affichés ;
   (c) sans plan : contours OSM en L93 sur un calque OSM ;
   (d) DXF en coordonnées L93 vraies, en mètres. La convergence des méridiens (environ 1,76° à SBC) est ajoutée à
       chaque azimut ;
   (e) GEODATA : supprimer le GEODATA faux du gabarit (Poitou, 46,35 N / 0,258 E) et le REMPLACER par un GEODATA
       Lambert 93 correct, écrit seulement après un test d'ouverture AutoCAD validé (sinon aucun).
4. DXF : un INSERT par objet fusionné, avec le BLOC EXISTANT du gabarit (jamais redessiné) ; rotation = azimut
   converti (0 = +X, sens anti-horaire) ; échelle uniforme selon la table de présentation (spot 1,208 / 2,415 / 3,614
   selon le sous-type, échelles purement graphiques (aucun ATTDEF/ATTRIB dans le DWG), correspondance avec PAR20 /
   MR16 / LED à fournir par Sébastien, 1,208 par défaut en attendant ; boîte de dérivation 0,5 ou 1 ; projecteur thorn contrast 0,571 ; extincteur 0,6 ;
   TGBT 1,417 ; linéaire fluo 1 ou 1,67 ; autres 1) ; miroir seulement si la table le demande.
   Calques : RES - Elec (équipements, étiquettes), cote (MTEXT « H x m », 0,5), photométrie, diagnostic (légende).
   ATTDEF invisibles ajoutés aux blocs : ID, STATUT, SOUS_TYPE, TECHNO, CULOT, PUISSANCE_W, HAUTEUR_M, REF_ETIQUETTE,
   SIGMA_XY_M, SOURCES, DATE_RELEVE (+ CIRCUIT, TABLEAU), remplis par ATTRIB (sinon XDATA appid LUMISITE).
   Annotations en MULTILEADER (style du gabarit, 0,36 m). Formes en LWPOLYLINE sur RES - Elec.
   Légende « Block Legend » (Symbol / Count / Name, triée sans tenir compte de la casse, Count = INSERT de l'espace
   objet) en LINE + MTEXT + INSERT (tablepainter), sur diagnostic, hors de l'emprise ; CSV des comptages joint.
   Version DXF = celle du gabarit (AC1015), $INSUNITS = 6.
5. Renommage du gabarit (sur une copie), UNE SEULE FOIS : doc.blocks.rename_block pour les 2 noms validés (puis pour
   les 4 autres après validation), puis vérification de la légende.
6. Rapport : comptages, fusions et sources, conflits, RMS des recalages, « à classer », hors catalogue, propositions
   vocales validées ou rejetées.
7. DWG : ODA File Converter en ligne de commande, ou enregistrement sous AutoCAD / CADCRAFT.

CRITÈRES D'ACCEPTATION
- Aller-retour eclairage03 : 293 INSERT → GeoJSON de test → DXF régénéré depuis le gabarit : 25 blocs aux noms
  canoniques, mêmes comptages que la légende, mêmes positions (1 mm, transformation près), rotations et échelles.
- Deux paquets de deux appareils avec 10 doublons → 10 objets fusionnés, conflits listés.
- Le DXF s'ouvre dans AutoCAD et dans un visionneur libre : mètres, L93 cohérent avec OSM, xref recalée avec son RMS,
  aucun GEODATA du Poitou, légende = nombre d'INSERT. La conversion DWG réussit.
- Un seul nom par bloc : un paquet qui mêle « bloc secour » et « bloc secours » donne un DXF dont doc.blocks et la
  Block Legend n'ont qu'une entrée « bloc secours » (comptage = somme). Aucun dessin produit ne contient l'ancien
  nom et le nouveau à la fois.
- pytest : X/Y à 1e-3 ; rotation ; ezdxf.readfile + audit à 0 erreur ; session vide → DXF valide ; ligne tronquée →
  avertissement ; ATTRIB ID ; polyligne fermée (code 70 = 1) ; no_fill sans SOLID ; objet sans position → pas dans le DXF.
```

### Lot 12 — P1 format / P2 outil : dictaphone sur l'iPad et voix

```text
LOT 12 — Dictaphone continu sur l'iPad (LumiSite iOS) + transcription Whisper au bureau + rattachement aux objets
Côté iOS (appli à venir) et côté outil de bureau (lot 11). Le format audio est figé avec le lot 6.
Décision de Sébastien : dictaphone continu sur l'iPad synchronisé ; transcription au bureau avec Whisper ; chaque
passage est rattaché aux objets par time-code et proximité, AVEC VALIDATION HUMAINE.

CHANGEMENTS (iOS)
1. Enregistrement continu en morceaux de 10 min : audio/<device_id>/<session_id>/dict_<n>.flac (FLAC mono 48 kHz
   16 bits). Un morceau fermé est définitif.
2. dictaphone.json par morceau : {file, sha256, sample_rate, start_device_monotonic_ns, start_utc, n_samples,
   chirps:[{sample_index, sync_id, peer_device_id, score}]} (chirps détectés dans l'enregistrement).
3. Pose de l'iPad (ARKit) : Features kind "track" toutes les 1 s dans le .geojsonl de sa session.
4. Témoin d'enregistrement toujours visible, bouton pause micro en 1 tap.
CHANGEMENTS (bureau)
5. Détection des chirps dans le FLAC → temps commun. Ré-échantillonnage à 16 kHz → Whisper local (whisper / whisper.cpp,
   MIT) → transcript_<n>.json (segments et mots horodatés, version du modèle).
6. Rattachement : t = milieu du segment ; objets avec captured_utc dans [t-30 s ; t+60 s] ; à moins de 8 m du locuteur ;
   bonus dans un cône de ±35° si « devant moi / là / au-dessus » ; mots-clés par famille et sous-type (spot, dichro,
   MR16, PAR 20, GU10, projecteur, Thorn, bloc secours, BAES…) ; détection d'un objet non relevé. Score avec seuil,
   et « à choisir » en cas d'ambiguïté. Extraction : sous-type, technologie, culot, puissance, température, étiquette,
   état. Texte brut gardé en note.
7. Écran de validation : segment réécoutable, transcription, objet sur la carte, photo, valeurs ; accepter / corriger /
   réattribuer / rejeter → révision rev+1, source "voice", segment_id. Rien dans le DXF sans validation.
8. Statistiques des sous-types validés par famille (aide pour décider d'un nouveau bloc).
9. Confidentialité : informer les personnes présentes et obtenir leur accord, traitement local uniquement, suppression
   de passages, conservation limitée, relecture RGPD avant usage chez un client.

CRITÈRES D'ACCEPTATION
- Visite test de 30 min (téléphone + iPad synchronisés) : chirps retrouvés, décalage affiché.
- « spot dichroïque MR16, 50 watts » dit à moins de 30 s d'une pose de spot → proposé sur le bon objet ≥ 8 fois sur 10.
- Seules les propositions validées vont dans les ATTRIB. Une coupure ne fait perdre que le morceau en cours.
- Pause micro et témoin fonctionnels.
```

### Lot 13 — P1 : zonage (après la décision de format)

```text
LOT 13 — LumiSite — Zones (cloître, chœur, salles) et rattachement des relevés
Dépôt : https://github.com/Gabrieljean454fgd/eclairage, branche main, après les lots 6 et 7. Branche dédiée.
Préalable : choisir le support dans la session. Soit SurveyEvent kind="zone" avec la géométrie dans payload
(compatible 0.1), soit un nouvel événement ZoneEvent avec passage du format en 0.2 et lecteur à jour dans la même
livraison.

CHANGEMENTS
1. Tracé d'une zone avec l'outil polygone du lot 7 (source MANUAL_MAP ; PLAN_IMPORT plus tard ; AR_WALK repoussé).
2. Champs : zone_id, label, zone_kind (ARCHITECTURAL, TECHNIQUE, CIRCULATION, EXTERIEUR), anneau en mètres du site,
   parent_zone_id, source.
3. Rattachement calculé (et non stocké dans le relevé) : point dans polygone, la zone la plus profonde l'emporte,
   dans derived/ et au bureau. Export : Feature kind "zone" (Polygon).
4. Polygone qui se recoupe : refusé à la saisie et signalé, jamais corrigé en silence. Sens des sommets documenté.

CRITÈRES D'ACCEPTATION
- Tests JVM : convexe, concave, avec trou (cour), hors zone → null, zones imbriquées → la plus profonde, polygone qui
  se recoupe → rejeté.
- Une session 0.1 ancienne se relit.
```

### Lot 14 — P2 : plan local en mètres

```text
LOT 14 — LumiSite — Plan local en mètres (grille illimitée), seulement si le lot 5 ne suffit pas
À n'ouvrir que si, en intérieur sans aucun vecteur OSM, le sur-zoom à 24-26 du lot 5 ne permet pas de poser
correctement. Vue « plan » en mètres du repère du site (Enu), grille sans limite, mêmes outils (réticule, loupe,
pas fins).
Acceptation : en intérieur vide, pose au centimètre près à l'œil, et objets identiques sur la carte et sur le plan.
```

---

## 14. Checklist de test sur Xiaomi 13T Pro

À recopier dans `docs/TESTS_TABLETTE.md` par Devin (ce document ne modifie pas le dépôt). Chaque ligne renvoie à son lot.

**Lot 1 — Bugs bloquants**
- [ ] Export : le ZIP est dans Téléchargements, la feuille de partage s'ouvre, et une phrase dit où il est.
- [ ] Export en échec : le bouton n'est pas grisé et l'erreur reste affichée.
- [ ] « Terminer la session » et le geste retour reviennent au site, dix fois, sans retour au bureau Android.
- [ ] Depuis un projet, le bouton et le geste retour reviennent à la liste des projets. Depuis l'accueil, le geste retour quitte l'appli.
- [ ] Création sans nom : message, puis nom par défaut. Création après un échec de récupération (mode avion), sans contours.
- [ ] Le bouton d'accueil dit « Nouveau ».
- [ ] En paysage, la caméra et les boutons ne se recouvrent pas.

**Lot 2 — Relevé fiable et catalogue**
- [ ] Ordre des pastilles : Synchro, Calibrer, Relevé, Recaler, Export.
- [ ] Le relevé est visible tout de suite, encore là après la réouverture ; sélection, déplacement, suppression.
- [ ] Sans calibration ni position manuelle : message, pas de point fantôme.
- [ ] Recalage : un relevé antérieur ne bouge pas, un relevé postérieur suit la transformation (écran = ZIP).
- [ ] Catalogue : les 25 noms exacts du DWG, avec `bloc secours` et `tableau divisionnaire` ; `prise électrique`, `boîte de dérivation` et `micro col` inchangés ; un ancien nom saisi donne le nom canonique ; un spot sort avec `blockName = "spot"`, et plus aucun `_GENERIC`.

**Lots 3 et 4 — OSM**
- [ ] Rectangle de site, recadrage à la réouverture, zones récentes.
- [ ] Contours récupérés par l'API OSM sur un bâtiment en moins de 3 s ; pas d'itinéraire de randonnée ; trou de cour conservé.
- [ ] Rectangle trop grand refusé, avec une proposition de réduction.
- [ ] Zone dense : 400 « too many nodes » traité, avec le choix entre réduire ou découper puis fusionner, sans doublon.
- [ ] API en échec : passage à Overpass, puis « sans contours », avec un message en français ; « Créer » reste actif.
- [ ] Recherche « 10 rue de Rivoli Paris » : le résultat IGN arrive d'abord.

**Lot 5 — Carte**
- [ ] Zoom jusqu'à ce qu'une pièce de 4 m remplisse la carte ; fond flou, vecteurs nets.
- [ ] Croix, loupe, pas de 0,1 m, « Suivre » avec le cap.
- [ ] Fonds OSM, Plan IGN et Ortho IGN (`PM_0_19`), avec l'attribution de chacun.
- [ ] Cache de la zone, puis mode avion : le fond s'affiche encore. Au zoom 26 avec un cache vide, l'appli reste fluide. Pas de téléchargement Esri.

**Lot 6 — Export GeoJSON**
- [ ] Paquet projet : un `.geojsonl` par appareil et par session ; ouvert dans QGIS, au bon endroit sur OSM.
- [ ] Appli tuée en plein relevé : tout est là sauf au plus la dernière ligne, qui est signalée.
- [ ] Manifeste et `sha256` vérifiés ; un fichier modifié est détecté.
- [ ] Export de toutes les sessions d'un projet, retrouvable dans Téléchargements. Aucun DXF produit par l'appli.

**Lot 7 — Objets et annotation**
- [ ] Rectangle « local TGBT » avec un commentaire : déplacer, agrandir, supprimer, annuler ; Feature `shape` dans l'export.
- [ ] Famille en 1 tap, favoris et 6 derniers utilisés en tête, détails facultatifs non bloquants.
- [ ] Note texte, note vocale et photo d'étiquette attachées à l'objet, présentes dans le paquet avec leur `sha256`.

**Lot 8 — Thème**
- [ ] Bouton Système / Clair / Sombre en haut à droite sur tous les écrans ; choix gardé au redémarrage ; carte non éblouissante en sombre ; caméra jamais filtrée.

**Lot 9 — Synchro**
- [ ] Un seul bouton, « Calé (2) » à deux appareils, top manuel encore disponible.

**Lot 10 — AR**
- [ ] Réticule AR, plans au sol, état calibré ou non calibré sur l'image.
- [ ] Visée rasante refusée avec un message ; pose de point sans plantage pendant 30 min ; torche en faible lumière.

**Lots 11 et 12 — Bureau et iPad** (hors téléphone, notés pour mémoire)
- [ ] Outil de bureau : aller-retour `eclairage03` avec les mêmes comptages ; fusion de deux appareils avec doublons et conflits listés ; DXF en L93 ; xref recalée avec l'erreur moyenne affichée ; aucun GEODATA du Poitou ; Block Legend = nombre d'INSERT ; jamais l'ancien et le nouveau nom d'un bloc dans un même dessin ; DWG obtenu.
- [ ] Dictaphone iPad : morceaux FLAC, chirps retrouvés au bureau, propositions vocales justes au moins 8 fois sur 10, validation humaine avant tout ATTRIB, pause micro et témoin.

---

## 15. Décisions encore ouvertes

### 15.1 Les 4 questions d'EasyClaw (§ 9.7) et celles de son § 8

| # | Question | État | Réponse ou proposition |
|---|---|---|---|
| E-9.1 | **Périmètre de l'éditeur** : les 6 outils (forme + texte, polygone, nuage, texte + bulle, note, trait libre), ou seulement forme + texte pour commencer ? | **Ouverte** | Proposition : un premier jet avec poser un objet, forme + texte, ligne / polyligne / flèche, polygone, annotation de diagnostic, commentaire, sélection, style, annuler (lot 7). Nuage, bulle, note à icônes et trait libre viendraient ensuite, sur décision de Sébastien. |
| E-9.2 | **Où vit la bibliothèque d'objets** : fichier JSON livré, ou éditable dans l'appli ? | **Tranchée par D5** pour les familles | Liste fermée des blocs de Sébastien, dans un JSON versionné, mise à jour à chaque ajout de bloc (et non éditable sur le terrain). Reste ouvert : les **préréglages de style** personnels, éditables dans l'appli. |
| E-9.3 | **Objet annoté = objet relevé ?** Un carré annoté peut-il porter un type d'équipement (CSV, rapport) ? | **Ouverte** | Proposition : un équipement se pose toujours par le catalogue (`equipment`, `block_name`) ; une forme (`shape`) reste graphique, avec libellé et commentaire. Une forme peut être convertie en équipement par la sélection. À confirmer par Sébastien. |
| E-9.4 | **Validation du PoC DXF** : brancher `gen_blocs_dxf.py` sur un vrai `.session` ? | **Tranchée en partie par D1, D3 et D12** | Le DXF se fait au bureau, mais **à partir du paquet GeoJSON** et des **blocs du gabarit de Sébastien**, et non de blocs `LUMI_*`. Le code de l'outil est écrit par Devin (lot 11). Le PoC reste une base de tests. **Le branchement sur une vraie session attend toujours l'accord explicite de Sébastien (D12).** EasyClaw n'a lu qu'une copie du DWG, en lecture seule (`D:\stockage\agraver\_poC\sbc\eclairage03_COPIE.dwg`). |
| E-8.1 | Le DXF se génère où ? | **Tranchée par D1** | Au bureau. |
| E-8.2 | Bibliothèque livrée ou éditée par l'utilisateur ? | **Tranchée par D5** | Voir E-9.2. |
| E-8.3 | Le plan importé voyage-t-il dans le ZIP d'export ? | **Ouverte** | `refs/` le prévoit en option. À décider selon le poids (un A0 scanné pèse plusieurs Mo). |
| E-8.4 | Photos d'équipement ? | **Tranchée par D2** | Oui, en fichiers séparés avec leur `sha256`. |

### 15.2 Autres décisions ouvertes

| # | Sujet | Qui décide | Proposition actuelle |
|---|---|---|---|
| O1 | Validation des 4 renommages en attente : `Camera` → `caméra`, `hublot ecl` → `hublot éclairage`, `lineaire fluo` → `linéaire fluo`, `tgbt` → `TGBT` ; et le sort de `micro col` (gardé tel quel tant que Sébastien n'a pas tranché) | Sébastien | Renommage limité aux six noms fautifs (dont 2 validés). Aucun autre changement de nom ou de casse. |
| O2 | **Quelle taille de spot (×1,208 / ×2,415 / ×3,614) va avec quelle technologie** (PAR20, MR16 dichroïque, LED) ? Ces échelles sont purement graphiques : aucun ATTDEF ni ATTRIB dans le DWG. Faut-il aussi des tailles pour d'autres familles ? | Sébastien | Table de présentation du catalogue, d'après le sous-type (D7) ; 1,208 par défaut en attendant. |
| O3 | Convention de `aim` dans le GeoJSON : nord du site en sens horaire (fiche N, retenue) ou +X en sens anti-horaire (analyse DWG) | Devin | Nord, sens horaire ; conversion au bureau. |
| O4 | Sous-calques par statut (`RES - Elec-existant`…) et calque de contrôle pour les objets de faible confiance | Sébastien | Calque unique `RES - Elec` par défaut (D3). |
| O5 | Ajouter au gabarit des blocs pour les formes annotées (`LUMI_CERCLE_TXT`, `LUMI_CARRE_TXT`…) ? | Sébastien | Sinon, LWPOLYLINE et MULTILEADER. |
| O6 | Support du zonage : `SurveyEvent kind="zone"` (format 0.1) ou `ZoneEvent` (format 0.2) | Devin | Voir lot 13. |
| O7 | Ce que deviennent au bureau les points `ESTIMATED` (repli à 50 cm) : insérés avec une forte incertitude, ou mis en liste « à vérifier » | Sébastien et Devin | Liste « à vérifier », non insérés sans validation. |
| O8 | **Affichage du plan de l'architecte sur la tablette** (import PDF/PNG comme fond, similitude plan → site avec `Calibrator.twoPoints`, découpage en tuiles d'un A0), proposé par EasyClaw (P3) | Sébastien | D8 règle le recalage au bureau ; l'affichage sur le terrain reste à décider. Sans plan, fond OSM. |
| O9 | Exports PDF et SVG du plan annoté (EasyClaw E7) | Sébastien | Au bureau, plus tard. Le CSV des comptages est déjà prévu (§ 9.4). |
| O10 | Activer Esri World Imagery selon ses conditions d'usage commercial | Sébastien | Désactivé par défaut. |
| O11 | Mode nuit rouge : dans le lot 8 ou plus tard | Sébastien | Plus tard (option, D10). |
| O12 | Version du DXF de sortie : AC1015 (gabarit) ou AC1027 | Sébastien | Celle du gabarit. |
| O13 | Torche allumée par défaut en faible lumière : seuil à fixer | Devin, sur le terrain | Seuil réglé sur le 13T Pro. |
| O14 | `ArchiveFileFactory` (archives de tuiles préparées au bureau) | Devin | Piste à évaluer après le lot 5. |
| O15 | Relecture RGPD du dictaphone avant usage chez un client | Sébastien | Obligatoire avant usage chez un client. |
| O16 | Quotas de la Géoplateforme IGN (WMTS et géocodage) | Devin, le jour de l'implémentation | À vérifier. |

### 15.3 Contradictions entre sources, et comment elles ont été tranchées

| # | Contradiction | Sources | Tranché par | Résultat |
|---|---|---|---|---|
| C1 | L'appli écrit-elle du DXF ? (option A « Kotlin dans `core/` », « tablette plus tard », ancienne fiche K) | EasyClaw § 2.2, § 9.4 ; ancienne fiche K | **D1** | Non. GeoJSON seulement ; DXF et DWG au bureau. |
| C2 | Garder les fautes des noms de blocs (`bloc secour`, `tableau divisionaire`) | Analyse DWG § 2.5 et § 4.a.7 | **D4** | Noms corrigés de référence + table d'alias. Renommage limité aux six noms fautifs (2 validés, 4 en attente). |
| C3 | Repère cible du DXF : local de l'xref, ou décalage documenté | Analyse DWG § 4.b.2 | **D8** | Lambert 93 vrai ; plan de l'architecte recalé sur le terrain ; OSM sans plan. |
| C4 | GEODATA : « supprimer » ou « remplacer » | Fiche O ; analyse DWG | **D8** | Remplacer le faux par un correct en L93, après un test AutoCAD. |
| C5 | Sens des 3 tailles de spot (« à confirmer ») | Analyse DWG § 2.5 | **D7** | Lisibilité client + technologies différentes ; pas de champ taille sur le terrain (`symbol_scale_class` supprimé). |
| C6 | Un calque par type, calques `RELEVE_CONTROLE` et `ZONE_<LABEL>` | EasyClaw § 2.3 | **D3** | Tout sur `RES - Elec` comme dans le gabarit ; confiance en attribut. |
| C7 | Entrée de l'outil de bureau : `.session` | EasyClaw § 2.2 | **D2, D3** | Paquet GeoJSON (le `.session` reste dans `raw/`). |
| C8 | Photos exclues (« aucune image n'est enregistrée ») | README, EasyClaw Q4 | **D2** | Photos en fichiers séparés. |
| C9 | IGN WMTS : `PM` donne 400 et l'orthophoto en `PM` | EasyClaw ; ancienne fiche J | **Vérification du 29/10, erreur reconnue par EasyClaw** | Le 400 venait d'un mauvais format d'image (format imposé par couche). `PM_0_19` pour les deux couches ; `PM` est un alias non déclaré. |
| C10 | Limite de 50 000 nœuds de l'API OSM : « fausse » | EasyClaw contre fiche L | **Vérification du 29/10, erreur reconnue par EasyClaw** | La limite existe (`settings.yml`, wiki), mais n'est pas dans `capabilities`. Message 400 exact, dépassement dès 0,002 degré carré en zone dense : deux garde-fous dans la fiche L et le lot 3. |
| C11 | Ordre L / C (fiche L classée après C) | Lots de la fiche UX contre EasyClaw | **Vérification du 29/10** | L avant C. |
| C12 | `session_stopped` rend tout export invalide | EasyClaw | **Vérification du 29/10, erreur reconnue par EasyClaw** | Faux : la validation passe ; c'est un manque de documentation. |
| C13 | Géocodage IGN « a pris la suite » d'api-adresse | Fiche L contre EasyClaw | **Vérification du 29/10** | Les deux répondent ; on ne parle pas de remplacement. |
| C14 | Nombre d'objets OSM « 8 fois sous-estimé » | EasyClaw contre fiche C | Analyse | Non comparable (Overpass filtré contre API brute). |
| C15 | Pose de point « jamais en échec » (repli à 50 cm) contre « pas de point fantôme » | Décompilation EasyClaw contre fiche G | Synthèse (§ 5.2) | Repli `ESTIMATED` seulement avec un suivi AR actif ; sinon un message. Usage au bureau : O7. |
| C16 | Convention de `aim` | Analyse DWG contre fiche N | Choix de la version fusionnée | Nord, sens horaire (O3). |
| C17 | Identifiants : UUID v7 ou `<device_id>:<compteur>` | Fiche N contre analyse DWG | Choix de la version fusionnée | UUID v7. |
| C18 | `attributes` contre `details` ; `type_label` | Analyse DWG contre fiche N | Choix de la version fusionnée | `details` ; libellé tiré du catalogue. |
| C19 | Palette de 26 couleurs contre 6 couleurs nommées | EasyClaw § 9.3 contre fiche K | Synthèse (§ 7.2) | 6 couleurs au premier niveau, palette étendue au second. |
| C20 | Ordre de travail : « DXF d'abord » | EasyClaw § 7 | **D1** et état du terrain | Bugs P0 d'abord (lot 1) ; outil de bureau en parallèle à partir du lot 6. |
| C21 | « `PROPOSITIONS.md` n'existe pas » | Fiche UX § 1 | Fait | Il existait dans le clone local, hors suivi git ; il est fusionné ici. |
| C22 | Exemple de manifeste avec Pixel 8 Pro / iPhone 15 Pro | Analyse DWG § 4.a.2 | Contexte | Exemple ramené au Xiaomi 13T Pro et à l'iPad (valeurs illustratives). |
| C23 | Nouvel événement `ZoneEvent` contre « pas de nouveau type en 0.1 » | EasyClaw § 1.2 contre fiche K | Règle de format (§ 0.3) | Ouvert entre deux options compatibles (O6). |
| C24 | Nom de référence : EasyClaw veut garder les noms fautifs, de peur d'une légende éclatée | Retour d'EasyClaw du 29/10 (23 h) contre D4 | **D4** | Renommage unique dans le gabarit, normalisation par alias à l'import, saisie qui accepte les deux formes. Test : aucun dessin produit ne contient l'ancien nom et le nouveau. |
| C25 | Renommages de casse et de sigles (`Amplificateur` → `amplificateur`, `prise xlr` → `prise XLR`…) proposés dans la v1 | Fiche P contre retour d'EasyClaw | Consigne du 29/10 (23 h) | Abandonnés : renommage limité aux six noms fautifs. |

---

## 16. Annexes

### 16.1 Chemins sur la machine de Sébastien

| Chemin | Contenu |
|---|---|
| `F:\telechargements\_lumisite_backup\` | **Clone du dépôt** (`.git` intact, `main`, HEAD `1fb8fc5`). Les seuls fichiers Markdown **suivis** par git sont `README.md`, `docs/SESSION_FORMAT.md` et `docs/TESTS_TABLETTE.md` (vérifié dans l'arbre GitHub). À côté, **hors suivi git** (fichiers non suivis, jamais commités), se trouvent les documents de travail : `PROPOSITIONS.md` (EasyClaw), `DECOMPILATION.md`, `PROPOSITIONS_UX_TERRAIN_2026-10-29.md`, `ANALYSE_PLAN_ECLAIRAGE03.md` et le document unique. |
| `D:\stockage\chantier\Saint-Bertrand de Comminges restauration de l'ancienne cathédrale\plan\eclairage03.dwg` | Plan d'éclairage de référence (AC1015) |
| `D:\stockage\agraver\_poC\gen_blocs_dxf.py` | PoC DXF d'EasyClaw ; sorties `poC_blocs_annotes.dxf` (5 168 o, R12) et `poC_blocs_annotes.png` dans le même dossier |
| `D:\stockage\agraver\re_phase1\out\{camtoplan_real,aruler_3.3.6}` | Décompilations de CamToPlan 5.6.1 et d'Aruler 3.3.6 (source de `DECOMPILATION.md`) |
| `D:\stockage\agraver\re_phase1\out\xodo_11.3.1`, `…\Xodo-Pro-v9.2.2_*` | Décompilations de Xodo (lecture du comportement uniquement) |
| `D:\stockage\agraver\re_phase1\analyze_xodo.py` → `XODO_ANALYSE.txt`, `XODO_ANALYSE.json` | Script et résultats d'analyse Xodo d'EasyClaw |

**Publication prévue.** Le document unique sera publié dans le dépôt sous **`docs/PROPOSITIONS.md`**, comme référence pour Devin. Les autres documents de travail restent locaux et hors suivi git. Le `PROPOSITIONS.md` local d'EasyClaw (à la racine du clone) est un autre fichier : il est fusionné ici et n'est pas publié.

| Chemin (suite) | Contenu |
|---|---|
| `D:\stockage\agraver\_poC\sbc\eclairage03_COPIE.dwg` | Copie du DWG lue en lecture seule par EasyClaw (relevé des 25 noms de blocs) |

### 16.2 Sources et rendus sur la box de travail

| Chemin | Contenu |
|---|---|
| `/workspace/lumisite/PROPOSITIONS_UX_TERRAIN_2026-10-29.md` | [UX terrain], fiches A à Q (85 851 o) |
| `/workspace/lumisite/COPIE_SAUVEGARDE_PROPOSITIONS_UX_TERRAIN_2026-10-29_2026-10-29_1815.md` | Version précédente (47 288 o) |
| `/workspace/lumisite/xodo_11.3.1_res_extract.zip` | Ressources Xodo extraites pour l'analyse de l'ergonomie |
| `/workspace/sbc/ANALYSE_PLAN_ECLAIRAGE03.md` | [Analyse DWG] |
| `/workspace/sbc/eclairage03.dwg`, `eclairage03.dxf`, `eclairage03.pdf` | Copie de travail du DWG, DXF converti (LibreDWG), PDF client |
| `/workspace/sbc/block_legend.png` | Capture de la légende Block Legend |
| `/workspace/sbc/rendu_dxf_vue_ensemble.png`, `rendu_dxf_zoom_stalles_choeur.png` | Rendus ezdxf |
| `/workspace/sbc/pdf_page1_100dpi.png`, `pdf_page2_100dpi.png`, `pdf_zoom_stalles_300dpi.png` | Pages du PDF client |
| `/workspace/sbc/scripts/` | Scripts d'analyse (a1 à a4) et de rendu (`render.py`), à lancer avec `/usr/bin/python3` |
| `/workspace/fusion/sources/EASYCLAW_PROPOSITIONS.md` | [EasyClaw PROPOSITIONS] |
| `/workspace/fusion/sources/EASYCLAW_DECOMPILATION.md` | [EasyClaw décompilation] |
| `/workspace/fusion/sources/EASYCLAW_MEMOIRE_2026-10-29.md` | Mémoire et contre-expertise d'EasyClaw |
| `/workspace/fusion/sources/README_depot.md` | README du dépôt |
| `/workspace/fusion/LUMISITE_DOCUMENT_UNIQUE_2026-10-29.md` | **Ce document** (v2), à publier sous `docs/PROPOSITIONS.md` |
| `/workspace/fusion/COPIE_SAUVEGARDE_LUMISITE_DOCUMENT_UNIQUE_2026-10-29_2026-10-29_2309.md` | Sauvegarde de la v1 |

### 16.3 Fichiers du dépôt cités (commit `1fb8fc5`)

| Fichier | Lignes citées |
|---|---|
| `app/src/main/java/app/lumisite/android/SessionController.kt` | 136–139 (`close()`), 266–278 (`survey()`), **275 (`LUMI_${kind}_GENERIC`)** |
| `app/src/main/java/app/lumisite/android/ui/SessionScreen.kt` | 270, 274 (pastilles de types en dur) |
| `app/src/main/java/app/lumisite/android/ui/Screens.kt` | 109 (« Chercher »), 153 (« Créer ») |
| `app/src/main/java/app/lumisite/android/ui/MapView.kt` | 61 (`maxZoomLevel = 21.0`) |
| `app/src/main/java/app/lumisite/android/data/OsmClient.kt` | `search`, `fetchFeatures`, `toFeature`, `bboxAround`, `read` |
| `app/src/main/java/app/lumisite/android/data/Repository.kt` | `export` (`filesDir/exports/`) |
| `app/src/main/java/app/lumisite/android/capture/ArTracker.kt` | `PlaneFindingMode.DISABLED`, `pause()`, `onDrawFrame` |
| `app/src/main/java/app/lumisite/android/MainActivity.kt` | navigation, `osmdroidTileCache` |
| `app/src/main/res/xml/file_paths.xml` | `files/exports/` |
| `core/src/main/kotlin/app/lumisite/core/model/Model.kt` | 169 (`sealed interface SessionEvent`), 179–180 (`SessionStopped`), 295–312 (`SurveyEvent`), 319 (`DxfHint`) |
| `core/src/main/kotlin/app/lumisite/core/journal/Journal.kt` | 20–21 (`ignoreUnknownKeys`, `classDiscriminator`) |
| `core/src/main/kotlin/app/lumisite/core/export/Export.kt` | 80 (« blocks are not defined yet ») |
| `docs/session.schema.json` | `formatVersion` `^0\.1\.[0-9]+$` ; `SessionEvent.allOf` (6 types) ; `sync.method` en `enum` |

### 16.4 Licences : ce qu'on réutilise et ce qu'on ne réutilise pas

| Source | Licence | Usage dans LumiSite |
|---|---|---|
| osmdroid 6.1.20 | Apache-2.0 | Déjà lié : étendre `OsmMap`, sans fork |
| Exemples ARCore | Apache-2.0 | Copier les classes de rendu nécessaires dans `capture/`, en gardant l'en-tête |
| ggwave | MIT | Trame d'identifiant après le chirp |
| proj4j / pyproj | Apache-2.0 / MIT | WGS84 ↔ L93 au bureau (ou en JVM si nécessaire) |
| ezdxf | MIT | Outil de bureau |
| Whisper / whisper.cpp | MIT | Transcription locale au bureau |
| Material Icons | Apache-2.0 | Icônes de la barre |
| Vespucci, Organic Maps | Apache-2.0 | S'inspirer des gestes (cadre de téléchargement, croix, suivi) |
| jdxf | MIT | Insuffisant (pas de blocs avec attributs), non utilisé |
| Kabeja | LGPL | Non utilisé |
| LibreDWG | GPL-3 | Lecture d'anciens DWG au bureau seulement, jamais lié |
| ODA File Converter | Gratuit, propriétaire | DXF → DWG au bureau |
| StreetComplete, OsmAnd | GPL | Idées seulement, aucun code |
| Xodo / Apryse (PDFTron) | Propriétaire | Comportement uniquement ; zéro fichier, zéro icône, zéro SDK |
| CamToPlan, Aruler | Propriétaires | Principes seulement, réécrits |
| Esri World Imagery | Conditions Esri | En ligne seulement, sans cache, si les conditions sont acceptées |
| IGN Géoplateforme | Etalab 2.0 | Tuiles et géocodage, avec attribution |

### 16.5 Références

- **API OSM v0.6 :** https://wiki.openstreetmap.org/wiki/API_v0.6. Capabilities : https://api.openstreetmap.org/api/capabilities. Politique d'usage : https://operations.osmfoundation.org/policies/api/
- **Politique des tuiles OSMF :** https://operations.osmfoundation.org/policies/tiles/
- **Politique Nominatim :** https://operations.osmfoundation.org/policies/nominatim/
- **Overpass :** https://wiki.openstreetmap.org/wiki/Overpass_API
- **JOSM, téléchargement :** https://josm.openstreetmap.de/wiki/Help/Action/Download
- **osmdroid 6.1.20 :** https://github.com/osmdroid/osmdroid/tree/osmdroid-parent-6.1.20
- **Géoplateforme IGN :**
  - WMTS : https://data.geopf.fr/wmts
  - fiche data.gouv : https://www.data.gouv.fr/dataservices/api-geoplateforme-diffusion-dimages-tuilees-wmts
  - géocodage : https://data.geopf.fr/geocodage/search
- **Licence Etalab 2.0 :** https://www.etalab.gouv.fr/wp-content/uploads/2017/04/ETALAB-Licence-Ouverte-v2.0.pdf
- **Esri, attribution :** https://doc.arcgis.com/en/arcgis-online/reference/display-copyrights.htm
- **ARCore :**
  - SDK : https://github.com/google-ar/arcore-android-sdk
  - appareils compatibles : https://developers.google.com/ar/devices
- **BeepBeep (SenSys 2007) :** https://www.microsoft.com/en-us/research/publication/beepbeep-a-high-accuracy-acoustic-ranging-system-using-cots-mobile-devices/
- **ggwave :** https://github.com/ggerganov/ggwave
- **ezdxf :** https://ezdxf.mozman.at/
- **LibreDWG :** https://www.gnu.org/software/libredwg/
- **ODA File Converter :** https://www.opendesign.com/guestfiles/oda_file_converter
- **proj4j :** https://github.com/locationtech/proj4j
- **pyproj :** https://pyproj4.github.io/pyproj/
- **RFC 7946 (GeoJSON) :** https://www.rfc-editor.org/rfc/rfc7946
- **RFC 8142 (GeoJSONSeq) :** https://www.rfc-editor.org/rfc/rfc8142
- **Whisper :** https://github.com/openai/whisper
- **whisper.cpp :** https://github.com/ggerganov/whisper.cpp

---

*Fin du document. Aucun fichier source n'a été modifié. Le dépôt n'a été ni cloné, ni modifié, ni poussé.*
