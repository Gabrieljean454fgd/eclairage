# LOCAL — synchro qui reste active (retour build 199, point 1f)

Branche `petit/synchro-service` (base `petit/synchro-affichage` + ses commits). Travail local, non poussé.
Écrans nommés : **Accueil > Sync** (`SyncScreen`), **Session > Synchro** (`SyncPanel` de `SessionScreen`), **Accueil** (`HomeScreen`).

## Ce qui est fait

- **Service de premier plan `SynchroService`** (`app/.../sync/SynchroService.kt`) : démarré par `NearbyMesh.start`
  (rôle maître ou esclave, tous les chemins : Accueil > Sync et Session > Synchro), arrêté par `NearbyMesh.stop`
  (boutons « Arrêter la synchro » des deux écrans **et** action « Arrêter » de la notification). `START_NOT_STICKY` :
  si le processus meurt, la synchro Nearby (objet en mémoire) est perdue de toute façon — pas de demi-reprise.
- **Manifeste** : `FOREGROUND_SERVICE_CONNECTED_DEVICE` + `FOREGROUND_SERVICE_MICROPHONE`, service
  `.sync.SynchroService` avec `foregroundServiceType="connectedDevice|microphone"`, `exported=false`.
  Android 14+ : `connectedDevice` est licite car les permissions Nearby (`NEARBY_WIFI_DEVICES`, `BLUETOOTH_*`,
  `ACCESS_FINE_LOCATION` ancien) sont accordées avant `NearbyMesh.start` ; `microphone` couvre l'enregistrement
  audio de l'esclave écran éteint (`RECORD_AUDIO` accordée au même moment).
- **Notification permanente** : canal `synchro` (« Synchro entre téléphones », `IMPORTANCE_LOW`) créé une fois ;
  texte « Synchro LumiSite active » + état via `SyncService.notice` (maître : « N esclave(s) » connectés ;
  esclave : « Connecté à <maître> » ou « Recherche du maître… »). Action « Arrêter » → `ACTION_STOP` →
  `NearbyMesh.stop()` puis `stopSelf()`. Le service collecte `NearbyMesh.state` et republie la notification
  seulement quand le texte change. Clic = ouvre `MainActivity`.
- **Logique pure dans `core/`** : `SyncService` (`core/.../sync/SyncService.kt`) — `shouldRun`,
  `foregroundTypes(recordAudioGranted)` (microphone seulement si `RECORD_AUDIO` accordé ; le masque runtime
  reste un sous-ensemble du manifeste), `notice(role, connectedSlaves, masterName)`. Tests JVM `SyncServiceTest`.
- **`POST_NOTIFICATIONS`** ajoutée à `nearbyPermissions()` (API 33+) : sans elle la notification permanente
  n'apparaît pas dans la barre. Le launcher de permissions démarre le rôle même en cas de refus (comportement
  existant inchangé pour la synchro elle-même).
- **Échec non bloquant** : si `startForegroundService`/`startForeground` est refusé (permission manquante,
  restriction arrière-plan), c'est journalisé (`DiagLog`, tag `SYNCHRO`) et la synchro continue comme avant —
  le service n'est jamais une condition du fonctionnement Nearby.

## testTags

Aucun testTag ajouté : la notification système n'est pas un nœud Compose. Les testTags existants
(`synchro_mode_actif`, `synchro_bandeau_connexion`, `accueil_synchro_active`, `session_synchro_etat`,
`session_synchro_arreter`, `synchro_arreter`) suffisent aux flows.

## Flow Maestro

- `.maestro/build199/b199_sync_service.yaml` : démarre le rôle maître, vérifie bandeau + pastille Accueil +
  état Session > Synchro, puis arrête depuis l'écran et vérifie « Aucun rôle actif ». La notification et
  l'écran éteint ne sont **pas** vérifiables par Maestro (pas de lecture du shade) — vérification manuelle.

## Reste manuel / à faire par le relecteur

1. Compiler (`:app:assembleDebug`) et lancer `:core:test` (dont `SyncServiceTest`).
2. Sur le **Xiaomi 13T Pro** (HyperOS) en esclave : démarrer **Accueil > Sync > Captation-synchro (esclave)**,
   autoriser les permissions (micro + notifications demandés), éteindre l'écran **2 min**, rallumer :
   - la notification « Synchro LumiSite active · Connecté à … » est toujours là ;
   - la synchro n'a pas été coupée (retour dans l'appli : bandeau vert / capture repartie si en cours).
3. Idem sur le **Pixel 7** maître : notification « … · N esclave(s) », écran éteint 2 min, connexion conservée.
4. Tester l'action **« Arrêter »** de la notification : la synchro s'arrête (badge Accueil disparu,
   « Aucun rôle actif » dans Session > Synchro) et la notification disparaît.
5. Cas limite : refus de la permission notifications → la synchro démarre quand même (pas de notification).
6. `adb shell dumpsys activity services app.lumisite.android | grep SynchroService` pour confirmer le
   `foregroundServiceType` si besoin.

Non touché : bandeau/état partagé (point 1b/1e), AR, adresses, catalogue, rectangle de sélection du
Nouveau site. Tests verrouillés intacts.
