# Synchro maître / esclave — choix du transport (paquet 2)

**Choix** : Google Nearby Connections, stratégie `P2P_STAR`, en local uniquement (Bluetooth, BLE, Wi-Fi direct/hotspot choisis par Play Services). Aucun cloud, aucun Firebase, aucun compte.

**Pourquoi** :
- `P2P_STAR` = un maître (hub) relié à N esclaves (rayons) : c'est exactement le modèle « Saisie (maître) » + « Captation-synchro (esclave) ». `P2P_CLUSTER` (M à N) est inutile et moins débitant ; `P2P_POINT_TO_POINT` limite à un esclave.
- Fonctionne sans réseau sur un chantier (caves, sous-sols) ; découverte et reconnexion gérées par Play Services, déjà présent sur le Pixel 7 et le Xiaomi 13T Pro.
- Charges utiles BYTES (messages JSON courts) et FILE (médias) avec suivi de transfert.

**Rôles** : le maître publie le service (`startAdvertising`), les esclaves le découvrent (`startDiscovery`) et demandent la connexion ; le maître accepte et affiche chaque esclave. Acceptation automatique des deux côtés (même identifiant de service `app.lumisite.synchro`) : pas de code à taper sur le terrain. À revoir si plusieurs équipes travaillent au même endroit.

**Quatre notions séparées à l'écran et dans le journal** :
1. **Connexion** : connexion… / connecté / perdu (perdu = déconnexion Nearby ou silence de plus de 6 s).
2. **Alignement temporel** : aller-retour ping/pong toutes les 2 s, horloges monotones (`t_mono_ns`) ; décalage = meilleur aller-retour des 20 derniers ; « aligné » si ≥ 3 échantillons et incertitude (aller-retour / 2) ≤ 50 ms. Un redémarrage de l'esclave (nouvelle base de temps) remet l'alignement à zéro. Les balises audio restent le marqueur fin (`docs/SYNCHRO_CONCEPTION.md`).
3. **Capture** : en attente / en capture / arrêtée, sur ordre du maître ; un esclave qui rejoint pendant une capture la démarre aussitôt.
4. **Repère spatial** : non partagé. Les esclaves n'envoient que des horodatages et des médias ; seul le maître fait le relevé métier dans son repère site.

**Synchronisé** = connecté **et** aligné. L'écran affiche « 0 appareil » sans esclave, sinon « k/N synchronisé(s) » et l'état de chaque esclave.

**Limites connues** : non testé entre deux appareils réels dans ce paquet (Nearby ne fonctionne pas sur l'émulateur sans Bluetooth/Wi-Fi) ; l'incertitude de 50 ms est un seuil de départ à mesurer sur le terrain.

**Où vont les données** : chaque démarrage de la synchro ouvre `files/synchro/<date>/synchro.ndjson` (append-only : `t_mono_ns`, `utc_ms`, appareil, événement). Côté maître, les médias reçus vont dans `files/synchro/<date>/media/<esclave>_<nom>` avec une fiche JSON (début/fin en horloge de l'esclave, base de temps, décalage et incertitude estimés au moment de la réception). Ils ne sont **pas encore** rattachés à une session ni inclus dans l'export de session : étape suivante.

**v176h — sauvegarde sur l'esclave et lien au site** : le même dossier `files/synchro/<date>/` (maître comme esclave) reçoit en plus `synchro.json`, fiche écrite de façon atomique : `schema` (`lumisite-synchro/1`), `role` (`maitre` / `esclave`), `device`, `device_id`, `started_utc_ms`, `time_base_id` (esclave), `master_name`, `site_id`, `site_name`, `session_id`. Le maître la remplit depuis la session ouverte (Session ouvre → `NearbyMesh.setSite`) et envoie à chaque esclave connecté un message `site` (nom du maître, site, visite). L'esclave y ajoute ses fichiers : `capteurs.csv` (accéléro, gyro, magnéto, baro ; `t_mono_ns,capteur,x,y,z`), `gps.csv` (`t_mono_ns,utc_ms,source,lat,lon,precision_m,alt_m,precision_vert_m`), `capture_*.wav`, et journalise dans `synchro.ndjson` chaque décalage d'horloge reçu du maître (message `clock` : `source` = `top` après un top de synchro AudioOffset, ou `ping` toutes les 15 s ; `decalage_ns` = maître − esclave, `incertitude_ns`). Les fichiers sont vidés chaque seconde ; la synchro tourne dans le service de premier plan, donc survit au Retour et à l'écran éteint ; le dossier reste après « Arrêter ». La profondeur caméra (LiDAR / ToF) est détectée et journalisée, ses images ne sont pas enregistrées. **Export** : Sync > « Exporter la synchro (ZIP) » sur l'esclave produit `LumiSite_synchro_<appareil>_<date>.zip` au format de l'export du site (`LumiSite_<site>/manifest.json` avec tailles et SHA-256, `synchro/<date>/…`), copié dans `Documents/LumiSite/_synchro/`. L'export global du lieu (Repository.exportProject) inclut `synchro/<date>/` pour chaque dossier dont `site_id` est ce site.

**Interruptions** : une perte de connexion est journalisée (« connexion : perdu ») ; l'esclave continue sa capture localement et recherche à nouveau le maître ; un micro indisponible est journalisé comme interruption. Aucune continuité n'est supposée.
