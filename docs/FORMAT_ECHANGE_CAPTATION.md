# Format d'échange — mode Captation (D9)

Statut : **spécification seulement**. Il n'y a aucun code du mode captation (maître / esclave) dans l'application, et aucune implémentation iOS. Ce document sert de contrat pour un futur client Android secondaire ou iPhone / iPad (16 Pro avec LiDAR).

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

## 6. Correspondance avec le journal Android actuel
Le journal de session Android (`raw/*.ndjson`, `docs/SESSION_FORMAT.md`) porte déjà des horodatages `{utcEpochMs, monotonicNs, timeBaseId, sensorNs}` avec `monotonicNs = SystemClock.elapsedRealtimeNanos()`. On a donc :
- `t_mono_ns` = `monotonicNs` ;
- `time_base` = `timeBaseId` ;
- `seq` = rang de la ligne dans l'ordre des segments du flux ;
- `clock_offset_ns` = `null` (appareil unique, pas de maître).

**Non fait (question ouverte)** : D9 demande que le journal de session soit écrit « dès maintenant dans ce format ». Changer le format d'écriture du journal touche tout le stockage et la compatibilité des exports 0.1 / 0.2. Je ne l'ai pas fait dans ce lot. Je propose plutôt un export `captation/<device_id>.jsonl` dérivé du journal existant, sans perte, puisque tous les champs existent déjà. À trancher.
