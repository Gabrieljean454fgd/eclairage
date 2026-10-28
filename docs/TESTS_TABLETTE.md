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
