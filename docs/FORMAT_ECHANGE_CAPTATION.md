# Format d'échange — mode Captation (D9)

Statut : **spécification du mode captation** ; le journal de session Android écrit déjà ces trames (§6). Il n'y a aucun code du mode captation (maître / esclave) dans l'application, et aucune implémentation iOS. Ce document sert de contrat pour un futur client Android secondaire ou iPhone / iPad (16 Pro avec LiDAR).

## 1. Fichier
- Le format est JSONL : une trame JSON par ligne, en UTF-8 terminé par `\n`.
- Il y a un fichier par appareil et par session : `captation/<device_id>.jsonl`. Il peut être coupé en segments `captation/<device_id>.<n6>.jsonl`, sur le même principe de segments fsyncés que le journal Android (`docs/STOCKAGE_ET_JOURNAL.md`).
- Une ligne tronquée par un arrêt brutal n'est jamais réécrite : elle est ignorée et signalée à la consolidation (R1/R2).

## 2. Champs communs à chaque trame
| Champ | Type | Sens |
|---|---|---|
| `t_mono_ns` | entier | Horloge monotone de l'appareil émetteur, en ns. Android : `SystemClock.elapsedRealtimeNanos()`. iOS : `clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW)`, ou le `timestamp` CoreMotion/ARKit converti en ns. Elle repart de zéro au redémarrage : changer alors de `time_base`. |
| `clock_offset_ns` | entier ou `null` | Écart estimé à ajouter pour passer dans l'horloge du maître (`t_maître = t_mono_ns + clock_offset_ns`). `null` tant qu'aucune synchro n'est faite ; jamais inventé. |
| `seq` | entier | Numéro de trame strictement croissant par appareil et par session, sans trou. Un trou = trame perdue, signalée. |
| `device_id` | chaîne | Identifiant stable de l'appareil pour la session. |
| `time_base` | chaîne | Identifiant de la base de temps (change à chaque redémarrage de l'appareil). |
| `type` | chaîne | Type de trame (§3). |
| `v` | objet | Valeurs propres au type. |

Exemple :
```json
{"t_mono_ns":81234567890123,"clock_offset_ns":null,"seq":1042,"device_id":"pixel7-a1","time_base":"tb-3","type":"imu.gyro","v":{"x":0.0012,"y":-0.0201,"z":0.0007,"unit":"rad/s"}}
```

## 3. Types de trames
| `type` | `v` |
|---|---|
| `imu.accel`, `imu.gyro`, `imu.mag` | `x`, `y`, `z`, `unit` ; `sensor_ns` (horodatage capteur d'origine) si différent de `t_mono_ns` |
| `imu.rotation` | quaternion `x`, `y`, `z`, `w`, `source` (`ROTATION_VECTOR`, `GAME_ROTATION_VECTOR`, `CMAttitude`) |
| `pose` | `p` [x, y, z] en m, `q` [x, y, z, w], `tracking` (`TRACKING` / `PAUSED` / `STOPPED`), `reason`, `source` (`ARCORE_CAMERA_POSE`, `ARKIT_CAMERA_TRANSFORM`). Repère : axe Y vers le haut, comme ARCore et ARKit. |
| `gnss` | `lat`, `lon`, `alt`, `acc_m`, `fix_age_ms`, `sats_used`, `cn0_top4_dbhz` |
| `audio.chirp_emit` | `chirp_id`, `counter`, `f0_hz`, `f1_hz`, `duration_ms`, `write_t_mono_ns` |
| `audio.chirp_heard` | `emitter_id`, `counter`, `sample_index`, `rate_hz`, `score`, `audio_ts_frame`, `audio_ts_ns`, `audio_ts_base` |
| `audio.file` | `path`, `rate_hz`, `start_t_mono_ns` (WAV gardé pour recalcul au bureau) |
| `lidar.depth` | `path` (fichier binaire de profondeur), `width`, `height`, `intrinsics` [fx, fy, cx, cy], `pose_seq` (trame `pose` associée) |
| `scan.mesh` | `path` (maillage OBJ/PLY), `pose_seq`, `anchor_id` |
| `link.state` | `peer_id`, `state` (`CONNECTED` / `LOST` / `RESUMED`), `transport` |
| `gap` | `stream`, `from_seq`, `to_seq`, `reason` : coupure signalée, jamais comblée (R1) |

## 4. Synchronisation des horloges
- **Sur place, plus tard** : échange de type NTP sur le réseau local (4 horodatages, on garde l'aller-retour le plus court), qui donne un `clock_offset_ns` provisoire.
- **Au bureau** : recalage fin par les chirps audio (`audio.chirp_emit` / `audio.chirp_heard`, principe BeepBeep). Le WAV est toujours gardé (`docs/SYNC_ACOUSTIQUE.md`). La dérive est estimée par régression linéaire sur plusieurs chirps au cours de la session.
- Le décalage provisoire et le décalage recalé sont tous deux gardés. Le fichier d'origine n'est jamais réécrit : la consolidation écrit un fichier dérivé.

## 5. Transport entre appareils
- Nearby Connections (`P2P_STAR`) ne sert qu'à l'état (connexion, pertes, reprises), jamais aux données lourdes. Tout est consolidé au bureau.
- **Entre Android et iOS**, Nearby Connections exige que les appareils soient sur **le même réseau Wi-Fi**. Le mode hors ligne (sans réseau) n'existe qu'entre appareils Android.

## 6. Journal Android : écrit dans ce format depuis v0.3.0 (relance V4 n°2)
- Chaque session Android écrit **dès maintenant** ses trames D9 dans le flux `captation` : segments `seg/captation.<n6>.ndjson` (contenu JSONL, mêmes règles de segments fsyncés et de réparation que les autres flux, R2), exportés dans `raw/captation.jsonl` (Session > Export).
- Types écrits par Android : `pose`, `imu.accel`, `imu.gyro`, `imu.mag`, `imu.rotation`, `imu.other` (`sensor_type`, `values`), `gnss` (`lat`, `lon`, `alt`, `acc_m`, `provider`, `fix_utc_ms`, `sats_used`, `sats_visible`, `speed_mps`, `bearing_deg` ; `null` si inconnu), `gnss.status` (`provider`, `status`, `detail`). Chaque trame IMU porte aussi `sensor_name`, `accuracy` et `sensor_ns` si connu.
- `device_id` = identifiant d'appareil de l'en-tête de session ; `time_base` = `timeBaseId` ; `clock_offset_ns` = `null` (appareil unique, pas de maître) ; `seq` part de 1 et continue sans trou après un redémarrage (relu sur la dernière ligne).
- Les flux historiques (`raw_poses`, `raw_imu`, `raw_gps`, `raw_gps_status`, `events`) restent écrits et restent la source de la relecture, du réimport et de l'export existants : les sessions antérieures (sans flux `captation`) se lisent comme avant. Les événements métier (relevés, calibrages…) restent dans `events.ndjson` (pas de type D9 pour eux).
- Coût : chaque échantillon est écrit deux fois (environ 2× le volume des flux bruts). Retirer l'écriture historique est possible plus tard, une fois les lecteurs passés au format D9.
