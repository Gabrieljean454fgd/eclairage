# Tests matériels à faire sur la tablette

Rien de ce qui suit n'a été vérifié sur un appareil réel. L'émulateur ne valide ni ARCore réel, ni GPS réel, ni micro/haut-parleur réels.

## 0. Pré-requis
- [ ] Vérifier que le modèle figure sur https://developers.google.com/ar/devices (sinon : l'app fonctionne en positions manuelles uniquement).
- [ ] Installer `app-debug.apk` (`adb install -r app-debug.apk`), accepter caméra et localisation.
- [ ] Au premier lancement d'une session : si « Installer / mettre à jour ARCore » apparaît, l'installer depuis le Play Store.

## 1. Site et OSM (réseau disponible)
- [ ] Recherche Nominatim d'une adresse connue → la carte se centre au bon endroit.
- [ ] « Récupérer » Overpass (rayon 150 m) → contours de bâtiments affichés ; sélection d'un bâtiment.
- [ ] Création d'un site par coordonnées seules, sans OSM (mode avion activé) → le site est créé, la carte peut rester vide.
- [ ] Attribution « © OpenStreetMap contributors » visible sur la carte.

## 2. Suivi ARCore
- [ ] Bandeau : INITIALIZING puis TRACKING après quelques secondes de mouvement lent.
- [ ] Masquer la caméra avec la main → DEGRADED/LOST affiché, puis retour à TRACKING.
- [ ] Faire 10 m en ligne droite mesurés au décamètre : noter la distance affichée (calibration à 2 points, champ distance connue → `measuredScale`).
- [ ] Boucle fermée de ~50 m qui revient au point de départ : noter l'écart de fermeture (dérive) avant recalage.
- [ ] Mettre l'app en arrière-plan 30 s puis revenir : le suivi reprend ou passe en LOST ; vérifier que l'enregistrement continue (compteurs).
- [ ] Vérifier qu'aucune image/vidéo n'est enregistrée dans le dossier de session (seulement NDJSON + WAV de synchro si utilisé).

## 3. Calibration et recalage
- [ ] Calibration 1 point + cap saisi manuellement (cap mesuré au plan ou à la boussole de chantier) : la trajectoire s'aligne sur la carte.
- [ ] Calibration 1 point avec bouton « Boussole » : comparer à un cap connu ; en intérieur, noter l'erreur (souvent > 10° près des structures métalliques).
- [ ] Calibration 2 points (A, B à > 5 m) : vérifier `residualM` et `measuredScale` dans l'export.
- [ ] Recalage sur un point connu après 30–50 m de marche : ligne rouge affichée, trajectoire grise (non recalée) conservée.

## 4. GPS
- [ ] En extérieur : fix avec précision affichée (± m), satellites utilisés/visibles dans `raw/gps.ndjson`.
- [ ] En intérieur : statut « pas de fix » ou précision dégradée enregistrés, l'app reste utilisable.
- [ ] GPS désactivé dans les réglages : statut DISABLED enregistré, aucune erreur bloquante.

## 5. Hors réseau
- [ ] Session démarrée puis mode avion : poses, relevés, calibrations et export continuent (fond de carte : seules les tuiles déjà en cache s'affichent).
- [ ] Forcer l'arrêt de l'app pendant une session, la rouvrir : la session reprend (événement `time_base_added`), l'export relit les données d'avant.

## 6. Relevés et export
- [ ] 5 relevés avec position AR, 2 avec position manuelle (carte) → présents dans `derived/survey_points.ndjson`.
- [ ] Export → message « OK, hashes vérifiés » ; partage du ZIP par e-mail/Drive ; ouverture sur PC et relecture de `session.json`.
- [ ] Session de 30 min : taille de l'export, consommation batterie, échauffement (IMU à ~50 Hz, poses à 10 Hz).

## 7. Synchronisation (2 appareils au minimum)
- [ ] TOP manuel avec le même code sur les deux appareils → importer l'export de l'autre appareil → écart et dispersion affichés.
- [ ] Chirp audio, appareils à 1 m puis 5 m, environnement calme puis bruyant : noter le score, la confiance, l'écart et sa dispersion sur ≥ 5 événements.
- [ ] Répéter à 15 min d'intervalle pour estimer la dérive (ppm).
- [ ] Vérifier que le chirp est audible et pas gênant (volume média de la tablette).

## 8. Checklist Xiaomi 13T Pro (docs/PROPOSITIONS.md § 14)

Chaque ligne renvoie à son lot. Une case n'est cochée qu'après vérification sur le téléphone.

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
- [ ] Bouton Jour / Nuit / Nuit rouge (expérimental) en haut à droite, sur sa propre ligne (ne recouvre aucun titre) ; choix gardé au redémarrage ; carte non éblouissante en sombre ; caméra jamais filtrée.

**Lot 9 — Synchro**
- [ ] Un seul bouton, « Calé (2) » à deux appareils, top manuel encore disponible.

**Lot 10 — AR**
- [ ] Réticule AR, plans au sol, état calibré ou non calibré sur l'image.
- [ ] Visée rasante refusée avec un message ; pose de point sans plantage pendant 30 min ; torche en faible lumière.

**Lots 11 et 12 — Bureau et iPad** (hors téléphone, notés pour mémoire)
- [ ] Outil de bureau : aller-retour `eclairage03` avec les mêmes comptages ; fusion de deux appareils avec doublons et conflits listés ; DXF en L93 ; xref recalée avec l'erreur moyenne affichée ; aucun GEODATA du Poitou ; Block Legend = nombre d'INSERT ; jamais l'ancien et le nouveau nom d'un bloc dans un même dessin ; DWG obtenu.
- [ ] Dictaphone iPad : morceaux FLAC, chirps retrouvés au bureau, propositions vocales justes au moins 8 fois sur 10, validation humaine avant tout ATTRIB, pause micro et témoin.

## 9. Journal des lots (ce qui a été vérifié hors téléphone)

| Lot | Vérifié sans téléphone | Reste à vérifier sur le 13T Pro |
|---|---|---|
| 1 | `./gradlew :core:test` vert, `assembleDebug` OK ; schéma JSON valide. | Toute la checklist Lot 1 ci-dessus (rien n'a été vérifié sur appareil). |
| 2 | Tests JVM `CatalogTest` (25 noms, alias, refus des inconnus, anciens kinds) et `SurveyPositionsTest` (pas de position sans calibration, recalage sur les seuls relevés postérieurs, édition/suppression append-only, lecture v0.1) ; `assembleDebug` OK. | Toute la checklist Lot 2 : interface, cap, Centrer/Suivre, réouverture, page du site. |
| 3 | Tests JVM `OsmApiTest` (jointure nœuds/ways, cour conservée, itinéraires exclus, fusion type+id, garde-fou surface, classement des erreurs 400/429/509, découpage borné et plafonné, IGN puis Nominatim) ; `assembleDebug` OK. Aucun appel réseau réel aux API depuis les tests. | Toute la checklist Lots 3 et 4 : délai < 3 s en 4G, rectangle dense 2.33,48.85,2.38,48.89, URL invalide, « 10 rue de Rivoli Paris », réouverture du rectangle. |
| 4 | Tests JVM `OverpassTest` (requête sur le rectangle avec `[timeout:25]` et `out geom tags 500`, bascule seulement sur réseau/429/509/5xx, 3 miroirs dans l'ordre avec 2 essais et attentes 1/2/4 s, échec total) ; `assembleDebug` OK. Aucun appel réel aux miroirs. | API OSM forcée en échec (URL invalide `https://exemple.invalid/api/0.6`) : bascule visible sur Overpass ; mode avion : message « Le serveur de contours est indisponible… » et création aboutie. |
| 5 | Tests JVM `BasemapsTest` (URL WMTS IGN, Esri sans cache ni téléchargement, OSM sans téléchargement en masse, comptage et plafond de tuiles, largeur visible, cache : satellite limité à l'emprise, OSM/Plan IGN mis en cache même sans emprise, recherche d'approximation bornée à 8 niveaux) ; `assembleDebug` OK. Ni le sur-zoom, ni la loupe, ni le cache n'ont été vus sur un écran. | Pièce de 4 m au zoom 24–26 ; croix + « Point à la croix » ; décalage 0,1 m ; loupe sous le doigt (zoom ≥ 20) ; « Suivre » ; changement de fond (rectangle conservé, attribution changée) ; « Télécharger la zone » en Plan IGN puis mode avion ; zoom 26 cache vide en mode avion (issue osmdroid #1106) ; Esri sans téléchargement. |
| 6 | Tests JVM `GeoJsonExportTest` (lon/lat et repère local, écriture vivante + ligne tronquée signalée, correction rev 2 sans réécrire la ligne 1, suppression, paquet + manifeste sha256 + fichier modifié détecté, raw/ relu par `SessionImporter`, ZIP store, validation contre `docs/lumisite-feature.schema.json`) ; `assembleDebug` OK. | Session de 2 000 objets ; ouverture du .geojsonl dans QGIS (GeoJSONSeq) sur fond OSM ; appli tuée en plein relevé ; « Exporter » sur la carte d'un projet de deux sessions → Téléchargements + partage. |
| 7 | Tests JVM `ShapesDetailsTest` (rectangle en mètres → Polygon WGS84 + mètres locaux, libellé et commentaire ; relecture d'un événement 0.1 sans les nouveaux champs ; refus de -5 W, 0 W et 12 000 K ; champ vide absent ; chaîne d'édition, annulation, suppression, sélection par proximité) ; `assembleDebug` OK. | Barre « Annoter » : rectangle « local TGBT » + « à vérifier », le déplacer, l'agrandir, le supprimer, annuler ; spot en 2 taps (Poser) ; photo d'étiquette + note vocale sur un relevé puis export du projet (sha256 dans le manifeste) ; ouvrir une session antérieure. |
| 8 | `assembleDebug` OK (aucun test JVM : lot purement UI). | Bouton ☀/☾/● en haut à droite sur sa propre ligne : rotation Jour → Nuit → Nuit rouge (expérimental) sans perdre le relevé ni le zoom ; choix conservé après redémarrage ; pas de flash blanc au lancement (fenêtre de démarrage noire quel que soit le thème) ; carte assombrie (réglage −25 % / −60 % à ajuster) ; caméra non filtrée ; contraste WCAG AA à mesurer. |
| 9 | Tests JVM `SyncAcousticTest` (2 émetteurs synthétiques + bruit : indices exacts, identifiants et compteurs décodés ; silence = rien ; trame corrompue = checksum faux ; champs optionnels aller-retour et relecture d'un SyncEvent 0.1) ; `assembleDebug` OK. | Deux téléphones à 1 m, pièce calme : « Synchroniser » sur chacun → « Calé (2) » des deux côtés sans code saisi ; micro coupé sur l'un → l'autre n'affiche pas « Calé » et ouvre Secours ; estimer l'écart par import d'export. |
| 10 | Tests JVM `SurveyPositionsTest.aimedPointIsUsedNotThePhonePosition` (point visé → position du site et Feature exportée, plus la position du téléphone) ; `AimTest` (intersection analytique rayon/plan, repli plan du dernier objet puis sol estimé, refus à angle rasant, repli ESTIMATED à 50 cm, pas de point sans suivi, axe caméra, relecture 0.1) ; `assembleDebug` OK. | Réticule visible et coloré ; « N plan(s) au sol » après un mouvement lent ; libellé « Calibré · résidu · échelle » après calibration ; visée rasante refusée avec message ; torche auto en faible luminosité (seuil 0,15 à régler) et bouton ; mesure A–B sur une distance connue (noter l'écart ici) ; 30 min de relevé sans plantage ; sans ARCore : vue absente + texte. |
| 11 | Outil de bureau `tools/session2dxf` : 10 tests pytest (aller-retour synthétique 293 INSERT aux 25 noms, positions < 1 mm, rotations, échelles ; 2 appareils / 10 doublons → 10 fusions + 1 conflit ; alias → un seul bloc « bloc secours » ; session vide ; ligne tronquée ; ATTRIB ID ; polyligne fermée ; sans position exclu ; sha256 ; GEODATA supprimé ; recalage xref). Pas d'appareil concerné. | Aller-retour sur le vrai `eclairage03.dxf` et le gabarit de Sébastien ; ouverture AutoCAD + visionneur libre ; conversion DWG ODA. |
| 12 | Bureau : 6 tests pytest voix (extraction ; « spot dichroïque MR16, 50 watts » → bon spot 10 visites synthétiques sur 10 (critère ≥ 8) ; « à choisir » / « objet non relevé ? » ; seules les validations vont dans les ATTRIB ; réattribuer / corriger ; 3 chirps retrouvés à l'échantillon dans un WAV et un FLAC de 12 s). Appli iOS non développée. | Visite test de 30 min téléphone + iPad (appli iOS à écrire) ; Whisper réel sur un enregistrement de terrain. |
| 13 | Tests JVM `ZonesTest` (4) : convexe, concave, cloître avec cour, hors zone → null, zones imbriquées → la plus profonde, polygone qui se recoupe → rejeté, relecture d'une forme 0.1. `:core:test :app:assembleDebug` OK. | Tracer une zone en forme de 8 → message de refus ; tracer église / chœur et vérifier l'export Feature `zone`. |
