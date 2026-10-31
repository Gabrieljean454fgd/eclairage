Étape 1 « Protéger » — points livrés (libellés réels « Écran > Menu > Sous-menu ») :

- 1.1 Clé de test publique fixe (signingConfigs debug, `app/keystore/lumisite-test.jks`), versionCode = nombre de commits, applicationId `app.lumisite.android` inchangé ; procédure `docs/SIGNATURE_APK.md`.
- 1.2 Journal en segments atomiques (fsync 2 s, réparation au redémarrage) ; copie continue vérifiée SHA-256 dans `Documents/LumiSite/<site>/<session>/` ; Accueil > « Réglages » > Stockage (chemin) et « Réimporter une session depuis Documents/LumiSite ».
- 1.3 L'importeur relit les sessions 0.1, 0.2.0 et 0.2.1 (tests JVM).
- 1.4 Sauvegarde Android activée (allowBackup, dataExtractionRules, fullBackupContent), limite 25 Mo documentée.
- 1.5 Journal de diagnostic tournant (`Documents/LumiSite/_journal/`), Accueil > « Réglages » > Journal > « Partager le journal ».
- 1.6 Session > « Export » > « Exporter la session » : export en flux, progression réelle, « Exporté dans <chemin> », « Ouvrir » / « Partager », erreur claire écrite au journal ; test 4 h sous 64 Mo de tas.
- 1.7 Base de temps par démarrage (bootId) ; ordre (base, monotone) ; test JVM d'une session à cheval sur un redémarrage.
- Tâche 2 : testTag sur les éléments interactifs (testTagsAsResourceId), parcours Maestro `.maestro/scenario1_*.yaml`, relecture/enregistrement ARCore debug (extra `arcore_playback` / `arcore_record`, badge PLAYBACK).

Aucun de ces points n'est validé sur le Xiaomi 13T Pro : scénario 1 dans `docs/TESTS_TABLETTE.md`.
