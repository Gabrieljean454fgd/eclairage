# Dictaphone continu et voix (lot 12) — format figé

Décision de Sébastien : dictaphone continu sur l'iPad synchronisé, transcription au bureau avec Whisper, rattachement aux objets par time-code et proximité, **avec validation humaine**. L'appli iOS n'existe pas encore : ce document fixe le format qu'elle devra écrire. L'outil de bureau le lit déjà (`tools/session2dxf`, module `voice`).

## Dans le paquet (lot 6)
```
audio/<device_id>/<session_id>/dict_<n>.flac          FLAC mono 48 kHz 16 bits, morceaux de 10 min ; un morceau fermé est définitif
audio/<device_id>/<session_id>/dict_<n>.dictaphone.json
sessions/<device_id>/<session_id>.geojsonl            + Features kind "track" (pose de l'iPad, 1 par seconde)
```
`dict_<n>.dictaphone.json` :
```json
{"file": "dict_1.flac", "sha256": "…", "sample_rate": 48000, "start_device_monotonic_ns": 0, "start_utc": "2026-10-28T10:00:00Z",
 "n_samples": 28800000, "chirps": [{"sample_index": 48123, "sync_id": "AC-1A2B-3", "peer_device_id": "…", "score": 0.71}]}
```
Feature `track` : `geometry` Point WGS84, `properties = {kind: "track", heading_deg (0 = nord, sens horaire), time.captured_utc, device_id, session_id}`.

## Côté iOS (à faire)
Enregistrement continu en morceaux de 10 min ; témoin d'enregistrement toujours visible ; pause micro en un tap ; une coupure ne fait perdre que le morceau en cours.

## Bureau
1. `audio.detect_chirps` : chirps LumiSite (2–6 kHz, 60 ms, Hann) dans le FLAC (ffmpeg) par corrélation normalisée (FFT), seuils `score ≥ 0,3`, `snr ≥ 8`, écart ≥ 0,4 s. Le décalage d'horloge est passé à `propose --clock-offset-s`.
2. `voice transcribe` : Whisper local (openai-whisper, MIT), mots horodatés, version du modèle dans `transcript_<n>.json`. Rien ne sort de la machine.
3. `voice propose` : t = milieu du segment ; objets dont `captured_utc` est dans [t − 30 s ; t + 60 s] ; à moins de 8 m du locuteur (track) ; bonus si « devant moi / là / au-dessus » et l'objet est dans un cône de ±35° ; famille citée obligatoire si un mot-clé est reconnu ; seuil 1,5 ; écart < 0,25 → « à choisir » ; famille citée sans objet → « objet non relevé ? ». Extraction : sous-type, technologie, culot, puissance, température, étiquette, état ; texte brut gardé en note.
4. `voice validate` (écran texte) : accepter / corriger / réattribuer / rejeter. `voice apply` → révisions `rev + 1`, `source: "voice"`, `segment_id`, et statistiques des sous-types par famille.
5. `python -m session2dxf … --validated voice_validated.geojsonl` : **seules les propositions validées** vont dans les ATTRIB.

## Confidentialité
Informer les personnes présentes et obtenir leur accord ; traitement local uniquement ; suppression de passages possible (supprimer le segment du transcript avant `propose`) ; conservation limitée ; relecture RGPD avant usage chez un client.

## Limites
- Nombres dits en lettres (« cinquante watts ») : non extraits ; Whisper écrit généralement des chiffres.
- Pas d'écran graphique de validation (réécoute, carte, photo) : écran texte seulement.
- La trame d'identification (lot 9) n'est pas décodée par l'outil Python : appariement des chirps par ordre.
- Critère « ≥ 8 fois sur 10 » vérifié sur des visites synthétiques seulement ; la visite test de 30 min téléphone + iPad reste à faire.
