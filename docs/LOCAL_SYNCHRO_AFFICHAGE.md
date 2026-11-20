# LOCAL — synchro lisible et unique (retours build 199, points 1a-1e et 1g)

Branche `petit/synchro-affichage` (base `etape-3-v176d-synchro-reelle` + `4872f76`). Travail local, non poussé.
Écrans nommés : **Accueil > Sync** (`SyncScreen`), **Session > Synchro** (`SyncPanel` de `SessionScreen`), **Réglages** (`SettingsScreen`), **Accueil** (`HomeScreen`).

## Ce qui est fait

- **1a — Nom de l'appareil** : `DeviceName` (`data/Platform.kt`) lit Réglages > « Nom de ce téléphone » (nouveau champ, `reglages_nom_telephone`), sinon `Settings.Global.DEVICE_NAME`, sinon `FABRICANT + MODEL` ; format final via `SyncDisplay.deviceLabel` (suffixe 4 caractères de l'id, 40 car. max). Utilisé comme `endpointName` Nearby **et** dans `Hello` (même chaîne `name` dans `NearbyMesh.start`). Plus de `Build.MODEL` brut (« 23078PND5G ») côté esclave.
- **1b — Bandeau unique** : `SynchroBanner` (`ui/SynchroShared.kt`, testTag `synchro_bandeau_connexion`) en haut d'**Accueil > Sync** et de **Session > Synchro**, calculé par `SyncDisplay.masterBanner` / `slaveBanner` (vert connecté, orange recherche/connexion, rouge perdu ; côté esclave « Connecté à X · dernier ping il y a N s »). La ligne figée « Recherche d'esclaves (annonce active) » ne s'affiche plus que si la radio signale une erreur (`synchro_radio` conservé pour les pannes).
- **1c — Incertitude, pas le décalage brut** : carte esclave = `SyncDisplay.alignmentText` (« aligné à ± 33,9 ms »). Côté journal : `MeshLog.screen` (core `SyncMesh.kt`) garde le brut dans `synchro.ndjson` (`what`) et affiche l'incertitude à l'écran ; test JVM `SyncMeshScreenTest`.
- **1d — Âge qui défile** : `rememberMeshNowNs()` (tick 1 s) alimente « Dernier message : il y a N s » et « dernier ping il y a N s » ; toutes les écritures d'état de `NearbyMesh` passent par `MutableStateFlow.update {}`.
- **1e — État partagé** : `Session > Synchro` lit le même `NearbyMesh.state` (rôle `session_synchro_etat`, bandeau, cartes esclaves, journal `session_synchro_journal` 15 lignes). Le bouton `session_synchro_synchroniser` devient « Lancer un top de synchro (N esclave(s)) » quand ce téléphone est maître (il appelle déjà `NearbyMesh.masterRound` puis `c.syncRound`). Bouton « Démarrer la synchro maître » (`session_synchro_demarrer_maitre`) + « Arrêter » (`session_synchro_arreter`) dans l'onglet. **Accueil** : pastille `accueil_synchro_active` « Sync maître active · N esclave(s) » (ou « Sync esclave active · … »).
- **1g — Esclave simulé (debug)** : `BuildConfig.DEBUG` + `Settings.Global lumisite_simuler_esclave` = nom → `injectSimulatedSlave` injecte un pair dans `MasterMesh` (connexion + `Hello` + réponses automatiques : pong sur la même horloge monotone ⇒ alignement rapide, `Status` capture, `SyncResult` vide au top). Aucun radio. Effacé par `stop()`.

## testTags ajoutés / modifiés

| testTag | Écran | Rôle |
|---|---|---|
| `synchro_bandeau_connexion` | Accueil > Sync **et** Session > Synchro | bandeau (même nœud porteur du texte) |
| `accueil_synchro_active` | Accueil | pastille synchro active |
| `session_synchro_etat` | Session > Synchro | rôle (« Rôle : Saisie (maître) ») |
| `session_synchro_journal` | Session > Synchro | journal Nearby (15 lignes) |
| `session_synchro_demarrer_maitre` / `session_synchro_arreter` | Session > Synchro | démarrer/arrêter le rôle |
| `synchro_esclave_N` | les deux | **déplacé sur le Text du nom** (Maestro `id`+`text` = même nœud) |
| `synchro_esclave_N_connexion`, `synchro_esclave_N_dernier` | les deux | connexion / âge dernier message |
| `reglages_nom_telephone`, `reglages_nom_telephone_enregistrer`, `reglages_nom_telephone_applique` | Réglages | nom de ce téléphone |

## Flows Maestro (`.maestro/build199/`)

- `b199_sync_etat_partage.yaml`, `b199_sync_nom_esclave_simule.yaml` : couverts par cette implémentation (commentaires adb ajoutés : `settings put/delete global lumisite_simuler_esclave` avant/après — le réglage global survit à `clearState`).
- Point d'attention : le panneau Session > Synchro est scrollable et dense ; si `assertVisible` de `session_synchro_synchroniser` échoue sur viewport, ajouter `scrollUntilVisible` (le nœud existe dans la sémantique Compose).

## Reste manuel / à faire par le relecteur

1. Compiler (`:app:assembleDebug`) et lancer `:core:test` (dont `SyncMeshScreenTest`, `SyncDisplayTest` inchangé).
2. Maestro : `b199_sync_etat_partage.yaml`, `b199_sync_nom_esclave_simule.yaml` (avec le `settings put global` préalable), puis déplacer les flows verts à la racine `.maestro/`.
3. Terrain Pixel 7 maître + Xiaomi 13T Pro esclave : nom « Xiaomi 13T Pro (xxxx) » visible, bandeau vert, « aligné à ± X ms », « il y a N s » qui défile, retour Accueil → Session sans perte d'état.
4. Après les flows simulés : `adb shell settings delete global lumisite_simuler_esclave` pour ne pas fausser les autres tests.

Non touché : AR, adresses, catalogue, rectangle de sélection du Nouveau site. Tests verrouillés intacts.
