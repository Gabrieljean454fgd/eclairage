# Paquet d'export GeoJSON LumiSite (`lumisite-geojson-export` 1.0.0)

Format d'échange avec l'outil de bureau. **L'appli n'écrit pas de DXF** (D1) : le DXF/DWG est produit au bureau (D3).

## Arborescence

```
LumiSite_<project_id>/
  manifest.json                          schema, schema_version, project_id, catalog_version, devices[], sessions[], files[{path, bytes, sha256}]
  catalog/equipment_catalog.json
  sessions/<device_id>/<session_id>.geojsonl      GeoJSONSeq (RFC 8142), une Feature par ligne
  sessions/<device_id>/<session_id>.frames.json   origine du site (WGS84), calibrations, recalages, synchros
  photos/<device_id>/<session_id>/<photo_id>.jpg  (fichiers séparés, jamais en base64)
  raw/<device_id>/<session_id>.zip                export lumisite.session inchangé (relisible par SessionImporter)
  refs/osm_source.json                            réponses OSM brutes datées (si le site en a)
```

Le ZIP est en mode « store » (sans recompression) et contient le dossier `LumiSite_<project_id>/`.

## Écriture pendant la session

`<session>/features.geojsonl` est écrit en ajout pendant le relevé, avec un fsync après chaque objet.
Une correction ou une suppression ajoute une ligne avec le **même `id`**, `rev + 1` et `op = update | delete`. Les lignes précédentes ne sont jamais réécrites.
Le lecteur ignore une dernière ligne tronquée (appli tuée) et la signale (`sessions[].truncated_lines`).
L'état courant d'un objet est sa ligne de plus haute `rev` ; un objet dont la dernière `op` vaut `delete` est retiré.
Les sessions au format 0.1, sans fichier vivant, sont reconstituées à partir de `events.ndjson`.

## Feature

- `geometry` : `Point` [lon, lat] WGS84 (RFC 7946, sans membre `crs`) ; `null` si la position absolue est inconnue, ou si l'objet est supprimé.
- `properties.local` : `{frame_id: "site", x, y, z}` en mètres (X est, Y nord, Z haut, origine du site). **Lambert 93 n'est pas écrit par l'appli** (D8).
- Propriétés obligatoires : `rev, op, kind, block_name, catalog_version, layer ("RES - Elec"), status, local, accuracy {sigma_xy_m, sigma_z_m, method}, time {captured_utc, device_monotonic_ns, clock_offset_ms, clock_sigma_ms, sync_ref}, device_id, session_id, operator`.
- Facultatives : `aim, height_m, height_ref, details, label, note, voice_notes[], photos[], registration, duplicate_of, supersedes` ; `event_id` = identifiant de l'événement du journal.

Schéma : [`lumisite-feature.schema.json`](lumisite-feature.schema.json) (JSON Schema draft-07).

## Formes, annotations et zones (lot 7)

`kind` = `shape` | `annotation` | `zone`. `geometry` : `Polygon` (rectangle, cercle à 32 côtés, polygone), `LineString` (ligne, polyligne, flèche) ou `Point` (annotation), en WGS84.
`properties.local = {frame_id, points: [[x, y, z]…], radius_m?}` en mètres. Autres propriétés : `shape, label, note, annotation_type, style, attached_to`.
Les photos sont dans `photos/` avec `role` ; les notes vocales sont dans `audio/` et listées dans `voice_notes[] {path, sha256, start_utc, device_monotonic_ns}`.
Le schéma JSON publié ne couvre que les Features `equipment`.

## État actuel (lot 6)

- `status` vaut `existant` tant que l'appli ne propose pas de choix (lot 7).
- `sigma_xy_m`, `sigma_z_m` et les champs d'horloge valent `null` : l'appli ne connaît pas ces valeurs et n'en invente pas. `accuracy.method` contient la source de la position (`AR_POSE`, `MANUAL_MAP`, `MANUAL_EDIT`).
- Les identifiants sont ceux du journal (`srv-xxxxxxxx-xxxx`), pas des UUID v7.
- `operator` vaut `null` (aucune saisie d'opérateur).
