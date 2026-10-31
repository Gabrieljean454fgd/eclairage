# Étape 3 — spécifications (NON implémentée ; préparation pendant l'attente des validations 1 et 2)

Statut : spécification + tests JVM du calcul bureau seulement. Aucun service, aucune radio, aucun son n'est codé dans l'appli.
Les écrans cités (« Accueil > Capture », « Appareils à proximité ») n'existent pas encore.

## 1. Service de capture et interruptions par flux (3.1, 3.2)
- `CaptureService` : foreground service (`foregroundServiceType="microphone|location|camera"`), démarré par Accueil > « Capture » > « Démarrer »
  et arrêté seulement par « Arrêter » ou par la notification. L'écran se lie au service (bind) pour afficher l'état ; fermer l'écran n'arrête rien.
- Chaque arrêt et chaque reprise d'un flux devient un `StreamStateEvent(stream, state, reason)` dans `events` (déjà présent pour la pause), et
  une ligne `FLUX` dans le journal de diagnostic.

| Interruption | Audio | GPS | IMU | Caméra / ARCore | Écrit dans le journal |
|---|---|---|---|---|---|
| Photo (appli caméra externe) | continue | continue | continue | arrêt (caméra prise) | STOPPED reason=CAMERA_TAKEN, puis RESUMED + relocalisation demandée |
| Appel entrant | arrêt si Android coupe le micro (AudioRecord renvoie 0 / silence imposé) | continue | continue | continue si l'écran reste au premier plan | STOPPED/RESUMED reason=CALL |
| Pause (bouton) | arrêt volontaire | arrêt | arrêt | arrêt | PAUSED/RESUMED (déjà en étape 1) |
| Écran éteint | continue | continue | continue (avec wakelock partiel) | arrêt (Android) | STOPPED reason=SCREEN_OFF, puis relocalisation |
| Caméra prise par une autre appli | continue | continue | continue | arrêt | STOPPED reason=CAMERA_TAKEN |
| Redémarrage du téléphone | perdu jusqu'au relancement | idem | idem | idem | nouvelle `TimeBase` (bootId), `RESUMED reason=PHONE_REBOOT` ; jamais de trajectoire comblée (R1) |
| Arrêt brutal / crash | segment en cours : récupéré jusqu'au dernier fsync (≤ 2 s) | idem | idem | idem | `.part` réparé au redémarrage (étape 1.2) |

## 2. Format des segments (3.4)
Reprend l'étape 1 : `seg/<flux>.<seq6>.part` → `.ndjson` après fsync. Audio : `seg/audio.<seq6>.wav` de 60 s (PCM 16 bits mono 48 kHz,
en-tête WAV réécrit à la fermeture ; un `.part` est réparé en recalculant la taille). Chaque segment audio a une ligne d'index dans
`audio_index.ndjson` : `{seq, firstSampleMonotonicNs, timeBaseId, sampleRate, samples}` = timecode par trame (échantillon n ↦ temps).
Même principe pour la vidéo (MP4 fragmenté par segments) quand elle sera ajoutée.

## 3. Appairage BLE / hub (3.7 a, b, d)
- Annonce BLE : service UUID LumiSite, données = `{sessionId court (4 o), deviceId court (4 o), rôle (hub/membre), batterie %, Mio libres /256, flux actifs (bits)}` ≤ 31 o.
- « Appareils à proximité » liste les annonces reçues ; « Synchroniser ces appareils ? » crée `sessionId` sur le hub ; QR de secours = `lumisite://join?session=…&hub=…`.
- Messages d'état (≤ 1 s, BLE GATT ou UDP Wi-Fi local) : `{deviceId, t_local, batterie, mémoire, flux, dernière signature entendue}`. Aucune donnée lourde.
- Hub perdu (pas de message depuis `hubTimeout`, paramètre) : le membre vivant au plus petit `deviceId` devient hub (`HubElection.hub`).
  Pertes/reprises de liaison → `LinkEvent(device, LOST|BACK)` dans le journal de session. La capture ne dépend jamais de la liaison.

## 4. Signatures sonores et alignement bureau (3.6, 3.7 c)
- Signature : chirp 17–20 kHz de 100 ms suivi d'un FSK 8 symboles (deviceId 16 bits, seq 16 bits, CRC), émise toutes les `syncIntervalS`
  (paramètre, 60 s par défaut **à mesurer**), décalée de `slot = hash(deviceId) mod N × 2 s` pour éviter les collisions, et à chaque reprise.
- Chaque appareil journalise `SignatureEmitted{seq, t_local}` et `SignatureHeard{emitter, seq, t_local, snr}`.
- Bureau : `MultiDeviceAlign.align` modélise chaque horloge `t_ref = offset + (1+drift)·t_local` et résout par moindres carrés toutes les paires
  entendues (pas un seul émetteur). Sortie par appareil : décalage, dérive, σ (résidus), période de validité = entre deux bases de temps.
  Un appareil jamais relié à la référence n'est **pas** aligné (null), jamais deviné. Correction de propagation si la distance est connue.

## 5. Mesure de la précision de synchro (R3 : hypothèse à mesurer)
Banc : 3 téléphones + un clap de référence enregistré par tous (ou un câble jack commun). Matrice d'essais, 30 signatures par cas :
bruit ambiant (calme / bureau / chantier, dB mesurés), réverbération (bureau / couloir / hall), distance (1, 5, 10, 20 m), collisions
(deux émissions forcées dans le même créneau). Mesures : taux de détection, taux de faux positifs, erreur de datation vs référence
(médiane, p95), σ estimée vs erreur réelle. Critère proposé (à valider par Sébastien) : p95 < 5 ms à 10 m au bureau.

## Tests JVM livrés
`core/src/test/kotlin/app/lumisite/core/MultiDeviceSyncTest.kt` : 3 appareils avec dérive (+40 ppm, −25 ppm), signatures croisées,
B hors de portée 10 min, perte du hub A après 2 h 30 → erreur < 2 ms et σ par appareil ; appareil isolé non aligné ; bruit plus fort ⇒ σ plus grande ;
bascule de hub déterministe.

## Questions ouvertes (non tranchées)
- Bande 17–20 kHz audible par certains et filtrée par certains micros : à mesurer sur le Xiaomi.
- Valeurs de `syncIntervalS` et `hubTimeout`.
- Wi-Fi local vs BLE seul pour les messages d'état.
