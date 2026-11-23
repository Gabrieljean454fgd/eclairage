# Compte rendu — v176h : sauvegarde et export de la synchro sur l'esclave (23/11/2026)

Branche `etape-3-v176h-synchro-esclave-sauvegarde`, empilée sur `etape-3-v176g-retours-build199` (PR #29). Accueil : « Build N · v176h ». Pas de fusion.

Le nom de paquet « v176h » vient du nom de branche (BuildConfig.PAQUET) : c'est le 2e point de l'ordre du 23/11 (« sauvegarde et export de la synchro sur l'esclave »), placé après v176g et avant v176f.

## Ce qui change

| Demande | Réponse |
|---|---|
| L'esclave enregistre durablement ce qu'il capte et reçoit | Même dossier `files/synchro/<date>/` : `synchro.ndjson` (journal append-only), `synchro.json` (fiche), `capteurs.csv`, `gps.csv` (nouveau), `capture_*.wav`. Fichiers vidés chaque seconde. |
| Horodatage, offset d'horloge | Chaque ligne a `t_mono_ns` + `utc_ms` ; `gps.csv` a l'horodatage monotone du fix. Le maître envoie à l'esclave le décalage mesuré (message `clock`) après chaque top AudioOffset et toutes les 15 s (pings) ; l'esclave le journalise (`decalage_ns` maître − esclave, `incertitude_ns`). |
| Nom du maître, site, visite | Message `site` envoyé par le maître à la connexion et à l'ouverture d'une session ; écrit dans `synchro.json` de l'esclave (`master_name`, `site_id`, `site_name`, `session_id`). Affiché dans Sync > « Sauvegarde sur ce téléphone ». |
| GPS, mesures de précision | `gps.csv` : source, lat, lon, précision horizontale et verticale. État « GPS : … (± N m) » affiché (`synchro_gps`). |
| LiDAR / profondeur | Détectée et journalisée (« caméra N, non enregistrée ») ; les images de profondeur ne sont pas enregistrées (pas d'API stable sans ARCore actif sur l'esclave). |
| Survit à la veille et au Retour | Service de premier plan (v176g) ; le dossier et l'export restent disponibles après « Arrêter la synchro ». |
| Export depuis l'esclave | Sync > « Exporter la synchro (ZIP) » (`synchro_exporter`) : ZIP au format de l'export du site (`LumiSite_<site>/manifest.json` + SHA-256, `synchro/<date>/…`), vérifié avant écriture, copié dans `Documents/LumiSite/_synchro/`. |
| Inclus dans l'export global du lieu | `Repository.exportProject` ajoute `synchro/<date>/` pour chaque dossier dont `site_id` est le site exporté (maître ; esclave si l'export du lieu est fait sur l'esclave). |

Format documenté : `docs/SYNCHRO_NEARBY.md` (§ v176h) et `docs/GEOJSON_EXPORT.md` (arborescence).

## testTags ajoutés

`synchro_sauvegarde`, `synchro_gps`, `synchro_exporter`, `synchro_export_resultat`.

## Tests

- JVM : `SyncFolderTest` (fiche lue/écrite, filtre par site, paquet esclave vérifié par `ExportPackage.verify`, export du lieu avec `synchro/`, messages `site` et `clock`).
- Maestro (émulateur API 34, pas un téléphone réel) : `v176h_sync_esclave_sauvegarde.yaml` (esclave → fichiers listés, GPS, ZIP prêt, sauvegarde visible après arrêt), `v176_sync_esclave_capteurs.yaml`.

## Reste manuel (Pixel 7 maître + Xiaomi 13T Pro esclave)

1. Ouvrir une session sur le Pixel, démarrer Sync maître, l'esclave se connecte : sur le Xiaomi, « Sauvegarde sur ce téléphone » montre « site « … » · visite … · maître Pixel 7 (…) ».
2. Lancer un top : le journal de l'esclave montre « horloge (top) : maître − esclave aligné à ± … ».
3. Écran éteint 2 min, Retour : `capteurs.csv` et `gps.csv` continuent de grossir.
4. Exporter le ZIP sur le Xiaomi, l'ouvrir : `manifest.json`, `synchro/<date>/synchro.json`.
5. Exporter le lieu depuis le Pixel : `synchro/<date>/` présent.
