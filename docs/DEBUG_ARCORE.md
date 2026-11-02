# Mode debug : enregistrement et relecture ARCore (tâche 2)

Pour les tests automatiques sur le Pixel 7 de développement. Actif seulement dans l'APK debug (celui des pré-releases).

## Enregistrer un jeu de données (RecordingConfig / setMp4DatasetUri)

```bash
adb shell am start -n app.lumisite.android/.MainActivity --es arcore_record auto
# → /sdcard/Android/data/app.lumisite.android/files/arcore/rec-<date>.mp4 (ou --es arcore_record <chemin>)
```
Puis Site > « Nouvelle session terrain » : l'enregistrement démarre avant `resume()` de la session ARCore et
s'arrête à « ← Terminer la session ». Un badge « ● REC ARCore » est affiché dans l'en-tête de Session.

## Relire (setPlaybackDatasetUri avant resume())

```bash
adb push rec.mp4 /sdcard/Android/data/app.lumisite.android/files/arcore/rec.mp4
adb shell am start -S -n app.lumisite.android/.MainActivity --es arcore_playback /sdcard/Android/data/app.lumisite.android/files/arcore/rec.mp4
```
L'en-tête de Session affiche **PLAYBACK** (fond rouge, `testTag` `session_ar_playback`). Les erreurs (fichier absent,
format refusé) sont affichées dans l'état AR et écrites dans le journal de diagnostic (`ARCORE ERREUR`).

Non vérifié : ni enregistrement ni relecture n'ont été exécutés (pas d'appareil ARCore dans l'environnement de Devin ;
l'émulateur x86_64 ne fait pas d'ARCore réel).
