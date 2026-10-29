# Synchro acoustique LumiSite (format et méthode)

Document de référence pour l'outil de bureau et pour une future appli iOS. L'implémentation Android se trouve dans `core/sync/Sync.kt` (`Chirp`, `ChirpDetector`, `IdFrame`, `MultiChirp`, `SyncEstimator`) et `app/capture/AudioSync.kt`. Le champ `method` reste `AUDIO_CHIRP` : l'enum `SyncMethod` n'a pas changé (sinon le schéma 0.1 la rejetterait). Tous les ajouts sont des champs optionnels.

## Signal émis (protocole `lumisite-chirp-id/1`)

| Partie | Valeur |
|---|---|
| Échantillonnage | 48 000 Hz, mono, PCM 16 bits |
| Chirp (top temporel) | balayage linéaire 2 000 → 6 000 Hz, 60 ms (2 880 échantillons), fenêtre de Hann ; `x[i] = w[i]·sin(2π(f0·t + ½·k·t²))`, `k = (f1−f0)/T` |
| Pause | 20 ms |
| Trame d'identification | 32 bits en FSK binaire, 10 ms par bit (480 éch.) avec fenêtre de Hann : 7 000 Hz = 0, 8 500 Hz = 1 |
| Bits (poids fort en premier) | `emitter_id` (16) · `counter` (8) · `checksum` (8) = `id_hi ⊕ id_lo ⊕ counter ⊕ 0xA5` |
| `emitter_id` | `deviceId.hashCode() & 0xFFFF` (`String.hashCode` Java), écrit en hexadécimal sur 4 caractères |
| `counter` | compteur d'émission propre à l'appareil, modulo 256 |

Durée totale : 60 + 20 + 320 = 400 ms. Implémentation propre, sans dépendance ggwave.

## Détection

- **Corrélation normalisée** : `score(s) = |Σ x[s+k]·c[k]| / (‖x[s..s+M]‖·‖c‖)`, entre 0 et 1.
- **Recherche** : balayage grossier au pas de 4 échantillons, puis affinage à l'échantillon près autour de chaque pic.
- **Seuil** : `score ≥ 0,3` et `score / médiane(scores) ≥ 8` (`snr`).
- **Pics retenus** : maxima locaux séparés d'au moins 400 ms.
- **Trame** : décodée à `début_chirp + 80 ms`. Pour chaque bit, on compare l'énergie Goertzel à 8 500 Hz et à 7 000 Hz. La trame est valide si le checksum est bon.
- **Horodatage** : indice d'échantillon converti en temps monotone de l'appareil avec `AudioRecord.getTimestamp(TIMEBASE_BOOTTIME)`. Formule : `t = ts_ns + (indice − ts_frame)·10⁹/48 000`. Si ce timestamp manque, on prend l'heure de début d'enregistrement, ce qui est moins précis (`timestampTimebase = "NONE …"`).

## Déroulé d'une manche « Synchroniser »

Chaque appareil enregistre 6 s et émet son signal à un instant aléatoire, entre 0,8 et 4,2 s, pour limiter les collisions. Il entend ensuite son propre chirp et ceux des autres, tous sur **sa propre horloge** (principe BeepBeep).

Pour chaque chirp identifié, l'appareil écrit un `SyncEvent` :

- `syncCode = "AC-<emitter_id>-<counter>"` : identique sur tous les appareils qui ont entendu ce chirp, donc aucun code n'est à saisir ;
- `role` = `EMITTER` pour son propre chirp, `RECEIVER` pour les autres ;
- `eventMonotonicNs` ;
- `detection` (+ `emitterId`, `frameCounter`, `frameChecksumOk`) ;
- `protocol`, `heardIds` (identifiants reconnus, le sien compris), `localChirpIndex`, `peerChirpIndices`, `emitCounter` ;
- `label` : code lisible, généré et modifiable. Il n'est **pas** utilisé pour l'appariement.

L'appli affiche **« Calé (N) »** quand son propre chirp a été entendu et identifié. N est le nombre d'identifiants distincts reconnus, le sien compris. Sinon elle affiche « Non calé » et ouvre le menu Secours (top manuel avec code saisi, `MANUAL_TAP`).

## Calcul du décalage d'horloge

Prenons deux appareils A et B. A émet à l'instant physique `Ta`, B à l'instant `Tb`. Chaque appareil note les deux instants sur sa propre horloge : `A_a, A_b` pour A et `B_a, B_b` pour B. On note `d` le temps de vol entre les deux appareils et `θ = horloge_B − horloge_A`. Alors :

```
B_a − A_a = θ + d − la_B + la_A      (chirp de A)
B_b − A_b = θ − d − la_B + la_A      (chirp de B)
θ ≈ ½[(B_a − A_a) + (B_b − A_b)]     (le temps de vol s'annule ; latences d'entrée la_* supposées égales)
d ≈ ½[(B_a − A_a) − (B_b − A_b)]
```

`SyncEstimator.estimate` calcule les écarts par code commun (`perCodeOffsetsNs`), puis leur moyenne, leur dispersion et la dérive en ppm s'il y a au moins 2 codes espacés. Avec un code par chirp, la moyenne sur les deux chirps d'une même manche donne directement la formule BeepBeep ci-dessus.

## Détection a posteriori (dictaphone)

Un long enregistrement, par exemple un WAV à 48 kHz de l'outil dictaphone, peut être traité au bureau avec le même `MultiChirp.detectAll`. On obtient la liste des chirps (indice, score) et leurs trames. Il reste à convertir les indices dans le temps de l'enregistreur : début du fichier + indice / fréquence d'échantillonnage. Le recalage se fait ensuite sur les mêmes `syncCode`.

## Limites

- **Résidu** : de l'ordre du temps de vol (≈ 2,9 ms/m, donc ≈ 1 ms pour 3 m s'il n'est pas annulé par l'aller-retour), plus la différence de latence d'entrée entre modèles. Aucune de ces deux erreurs n'est compensée.
- **Trois appareils ou plus** : l'ordre relatif des chirps (`localChirpIndex`, `peerChirpIndices`) est indispensable pour vérifier la cohérence. Deux chirps à moins de 400 ms d'écart se masquent : relancer la manche.
- **Collision d'identifiants** : 16 bits, probabilité ≈ N²/131 072 ; à vérifier au bureau avec `device_id`.
- **Environnement** : micro `UNPROCESSED` quand il est disponible. Un environnement bruyant ou réverbérant peut faire échouer le checksum ; dans ce cas la manche n'est pas comptée.
- **Validation** : aucune sur téléphone réel ; seuls des signaux synthétiques ont été testés (`SyncAcousticTest`).
