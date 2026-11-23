# Compte rendu v176g — retours terrain du build 199 (23/11/2026)

Branche `etape-3-v176g-retours-build199`, empilée sur `etape-3-v176e-menu-bloc` @ `3253f3f` (build 213). PR vers v176e,
non fusionnée. Accueil : « Build N · v176g ». Les anomalies sont des anomalies constatées, pas des régressions.

## Fait
- **0. Version** : Accueil « Build N · v176g » (`accueil_version`, détail `accueil_version_detail`). `BuildConfig.PAQUET`
  vient du nom de branche (`etape-3-v176g-…` → `v176g`) ; même texte dans le nom de l'APK et le titre de la pré-release.
- **1. Synchro** (`core/sync/SyncDisplay.kt`) : nom lisible (Réglages > Nom de ce téléphone, sinon nom système, sinon
  fabricant + modèle, suffixe de 4 caractères, 40 caractères max., même nom dans `Hello`) ; bandeau unique
  `synchro_bandeau_connexion` (vert / orange / rouge) en haut d'Accueil > Sync et de Session > Synchro ; « aligné à ± N ms »
  au lieu du décalage brut (le brut reste dans `synchro.ndjson`) ; « Dernier message : il y a N s » recalculé chaque seconde ;
  Session > Synchro lit le même état et le même journal (`session_synchro_journal`), bouton « Lancer un top de synchro
  (N esclave(s)) », ancien chemin sous « Secours… » ; pastille `accueil_synchro_active` ; service de premier plan
  (`connectedDevice|microphone`, notification « Synchro LumiSite active ») ; esclave simulé debug
  `adb shell settings put global lumisite_simuler_esclave "Xiaomi 13T Pro"`. Résumé stable « 0 appareil » sans esclave.
- **2. AR** :
  - support déduit de l'inclinaison (`core/compute/SupportFromTilt.kt` : < −35° sol, > +35° plafond, sinon mur,
    hystérésis 5°) ; plus de puces Sol / Mur / Plafond ; ligne « Visée : mur (auto) » (`session_ar_support_auto`) ;
  - rangée Contours / Torche / Mesure toujours présente ; bloc caméra de hauteur constante dans Aucun / Calibrer /
    Relevé / Synchro ; compteurs de plans échantillonnés à 2 Hz, largeur stable ;
  - ancres dessinées en gris pendant un suivi dégradé (seul `STOPPED` les retire) ; recréées depuis le plan (repères
    actifs et luminaires) dès que le calibrage est rétabli (calibrage ou « Recaler ») ;
  - arêtes par intersections de plans bornées aux polygones (`core/compute/ArPlanes.kt` : plinthe sol∩mur, angle
    mur∩mur, mur∩plafond ; sol vert, murs bleus, plafond violet) ; polygones bruts seulement en diagnostic ;
  - filtre mobilier avant la visée SOL : sol = plan horizontal le plus bas (± 10 cm), plans horizontaux entre 0,3 et
    2,0 m au-dessus ignorés, plans < 0,25 m² ignorés, murs alignés (± 10°) sur un mur OSM préférés après calibrage ;
  - « Hauteur sous plafond » par site (saisie `session_ar_hauteur_saisie` / `session_ar_hauteur_valider`, ou mesurée
    `session_ar_hauteur_mesurer`) ; visée plafond sans plan détecté → plafond virtuel sol + H, source
    `AR_CEILING_ESTIMATED` journalisée, qualité « estimée ». Sans hauteur connue : « à compléter », jamais de repli
    silencieux. Depth API laissée désactivée (non évaluée sur le Xiaomi 13T Pro : à vérifier sur l'appareil).
- **3. Carte** : en Annoter et Export, caméra repliée en bande de 96 dp (`session_camera_repliee`), vue AR toujours
  composée ; carte ≥ 50 % avec panneau ouvert ; bouton « Carte plein écran » / « Quitter plein écran »
  (`session_carte_plein_ecran`) dans tous les onglets, barre d'actions visible ; objets de la carte avec une zone de
  toucher de 48 dp.
- **4. Nouveau site, adresses récentes** (`core/osm/AddressHistory.kt`) : `recent_addresses.json` (200 max., sans
  doublon, lat/lon si connues), suggestions hors réseau dès la première lettre (`nouveau_site_adresse_recente_N`), chaque
  mot tapé doit commencer un mot de l'adresse, les plus proches d'abord (tranches de 500 m), puis les plus récentes ;
  recherche IGN `&lat=&lon=` et Nominatim `&viewbox=` sans `bounded`.
- **Nouveau site, anomalie du build 248** : le nom ne reçoit plus les coordonnées (Terminé sur le clavier efface le
  focus ; latitude / longitude en champs d'une ligne, clavier décimal) ; « Utiliser ces coordonnées » remplit
  l'origine et centre la carte au zoom 18 ; le flow vérifie que le nom reste « Maestro bureau ». Le rectangle de
  sélection et la hauteur de la carte portrait (38 %) ne changent pas (`v176_nouveau_site_rectangle` vert).
- **Export GeoJSON, top de synchro** : `time.clock_offset_ms`, `time.clock_sigma_ms`, `time.sync_ref` viennent du top
  Nearby (AudioOffset), enregistré dans la session comme événement `sync` (`TOP-<round>`). Maître = référence (0) ;
  incertitude = la plus grande des esclaves alignés. **Sans top, les trois valeurs restent `null`** (voir
  `docs/GEOJSON_EXPORT.md`). Test JVM `ClockRefTest`.

## Anomalies du 17/11 (build 199)
| Flux | Reproduit sur le build 199 | Cause | Correction |
|---|---|---|---|
| `scenario_p2b_synchro_sans_esclave` (« 0 appareil ») | non (cause lue dans le code) | résumé remplacé par le libellé d'état | résumé stable « 0 appareil », état partagé |
| `scenario_p2_calibrage_multipoints` (ligne 2) | non (même enchaînement reproduit sur le build v176g avant correction) | la ligne 2 n'existait qu'une fois le point 2 enregistré ; l'avis « Correspondance contradictoire » la cachait | ligne suivante « à poser » toujours affichée (`session_calibrer_ligne_N_a_poser`, bouton `session_calibrer_poser_N`) ; `session_calibrer_ligne_N` reste réservé aux points enregistrés |
| `v176_ar_pleine_largeur` (`session_ar_simule`) | non (pas d'ARCore sur l'émulateur) | assert du mode simulé | flow sur `session_ar_camera` (réel ou simulé) |
| `v176_detail_compteurs` (« Photo vue (1) ») | non (dépend de l'appli photo du téléphone) | appareil photo système resté en confirmation ; certaines applis renvoient « annulé » alors que la photo est écrite | flow qui valide la confirmation ; la photo est gardée si le fichier est écrit |

## Flows
Nouveaux : `b199_sync_etat_partage`, `b199_sync_nom_esclave_simule`, `b199_ar_menus_stables`, `b199_carte_annoter`,
`b199_adresses_recentes` (`b199_releve_catalogue` est livré avec v176e). Adaptés (libellé ou testTag caduc, même critère) :
`v176_ar_contours` (plus de puces), `v176_ar_pleine_largeur`, `v176_sync_scene_active` (« Mode : Saisie (maître) »),
`scenario_p2_calibrage_multipoints` (assert ajouté : ligne 2 « à poser »), `scenario_v6_recaler_quand_meme` (appui relatif
à la carte, défilement à petits pas : le bloc caméra garde sa hauteur en Calibrer), `scenario1_1_creer_releve` (nom inchangé), `v176_detail_compteurs`. Aucun `name:` ne contient « > ».

## Reste manuel (Pixel 7 maître + Xiaomi 13T Pro esclave)
- Bandeau vert en < 20 s, même état Accueil / Session > Synchro, écran éteint 2 min.
- Visée auto à l'inclinaison, 4 drapeaux puis demi-tour : réapparition à ± 20 cm.
- Table ignorée pour la visée sol ; plafond virtuel avec hauteur saisie.
- Photo système HyperOS : compteur « Photo vue (1) ».

## CI / pré-release
À compléter.
