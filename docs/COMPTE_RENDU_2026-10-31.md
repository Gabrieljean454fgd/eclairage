# Compte rendu du 31/10/2026 (nuit comprise)

Rien n'est fusionné. Rien n'a été testé sur le Xiaomi 13T Pro ni sur ARCore réel. Les parcours Maestro ont été exécutés après le test Pixel 7 du 31/10
sur un émulateur « Pixel 7 » (API 34, x86_64, Google APIs, sans ARCore) : voir la section « Corrections après le test Pixel 7 » ci-dessous.
Tests JVM (`./gradlew :core:test`) et assemblage APK : en local et dans GitHub Actions. Un build vert ne prouve pas que la fonction marche.

| PR | Branche | Base | État |
|---|---|---|---|
| [#16](https://github.com/Gabrieljean454fgd/eclairage/pull/16) | `etape-0-instructions` | `lot13` | docs seules, ouverte |
| [#17](https://github.com/Gabrieljean454fgd/eclairage/pull/17) | `etape-1` | `etape-0-instructions` | à valider (scénario 1) |
| [#18](https://github.com/Gabrieljean454fgd/eclairage/pull/18) | `etape-2` | `etape-1` | BROUILLON, en attente de validation de l'étape 1 |

APK à installer pour le scénario 1 : pré-release **`etape1-50f2b9a`** → `lumisite-etape1-v104-50f2b9a….apk` (code de l'étape 1 + corrections de revue).
Les commits de documentation postérieurs produisent un APK de même code avec un versionCode supérieur (utilisable comme « N+1 » du scénario 1).
APK étape 2 (ne pas valider avant l'étape 1) : `etape2-0aa2474` (v107), puis le suivant sur `etape-2`.

## Tâche 0 — instructions
`docs/INSTRUCTIONS_DEVIN.md` (prompt v3 intégral), `docs/ROADMAP_ETAPES.md`, lien dans `docs/PROPOSITIONS.md`. PR #16, commit `62a2170`. Fait.

## Tâche 1 — APK automatique
`.github/workflows/apk-etape.yml` : push `etape-*` ou manuel → `:core:test :app:assembleDebug`, signature clé de TEST, pré-release `etape<N>-<hash7>`,
APK `lumisite-etape<N>-v<versionCode>-<hash>.apk`, notes = `docs/livraisons/etape-<N>.md`. versionCode = nombre de commits (+ base) ; `applicationId` inchangé. Fait, observé vert.

## Étape 1 (PR #17)
| Point | Écran > Menu > Sous-menu | Statut | Tests JVM | Maestro | À vérifier sur le Xiaomi |
|---|---|---|---|---|---|
| 1.1 Signature | — (installation) ; doc `docs/SIGNATURE_APK.md` | fait | — | — | installer N puis N+1 par-dessus sans erreur ; `apksigner verify --print-certs` sur l'APK téléchargé |
| 1.2 Stockage | Accueil > « Réglages » > Stockage (chemin) ; > « Réimporter une session depuis Documents/LumiSite » | fait (non testé sur appareil) | `JournalSegmentsTest` | `scenario1_4_reimporter.yaml` | copie visible dans Documents/LumiSite après désinstallation ; sélecteur SAF ; réimport complet et vérifié |
| 1.3 Compatibilité 0.1/0.2 | Accueil > « Importer » | fait | `Format01CompatTest` | — | import d'une vraie session 0.1/0.2 si Sébastien en a une |
| 1.4 Sauvegarde Android | — (`allowBackup`, `dataExtractionRules`, `fullBackupContent`) | fait (config) | — | — | `adb shell bmgr backupnow app.lumisite.android` ; limite cloud 25 Mio documentée dans `docs/STOCKAGE_ET_JOURNAL.md` |
| 1.5 Journal | Accueil > « Réglages » > Journal > « Partager le journal » ; fichier `Documents/LumiSite/_journal/` | fait | — | `scenario1_5_partager_journal.yaml` | erreurs ARCore réelles et arrêts/reprises visibles dans le journal partagé |
| 1.6 Export | Session > « Export » > « Exporter la session » | fait | `LongExportMemoryTest` (4 h, IMU 3×50 Hz, médias, JVM `-Xmx64m`) | `scenario1_3_exporter.yaml` | progression, « Exporté dans … », Ouvrir, Partager ; pièce jointe manquante ⇒ erreur |
| 1.7 Ordre après redémarrage | — (base de temps `bootId`/`bootCount`) | fait | `RebootOrderTest` | — | session à cheval sur un redémarrage réel |
| Testabilité | tous écrans (`testTag`, `testTagsAsResourceId`) | fait ; parcours verts sur émulateur Pixel 7 (voir plus bas) | — | `.maestro/scenario1_*.yaml` | réexécuter sur le Pixel 7 physique |
| Relecture ARCore | lancement `--es arcore_playback <chemin>` / `--es arcore_record auto` ; badge « PLAYBACK », « ● REC ARCore » | fait, non testé (ARCore réel requis) | — | — | enregistrer puis rejouer un MP4 (`docs/DEBUG_ARCORE.md`) |

Corrections de revue automatique appliquées (`50f2b9a`) : réimport refusant les noms hors dossier, copie marquée faite seulement après `copies.json`,
fsync sur horloge monotone, pièces jointes absentes signalées à l'export, arrêt du copieur, instantanés du journal distincts, extraction zip bornée.

Limite connue : l'export compresse un dossier de préparation déjà écrit (lecture en flux, mais espace disque ≈ 2× la session pendant l'export).

## Étape 2 (PR #18, BROUILLON, en attente de validation de l'étape 1)
Détail par point dans `docs/livraisons/etape-2.md` (2.1 à 2.10 codés). Tests JVM : `Etape2Test`. Maestro : `scenario2_releve_sans_blocage.yaml` (vert sur émulateur, voir plus bas).
Libellés changés : Accueil « Nouveau » → « Nouveau relevé » ; « Deux coins sur la carte » → « Tracer le rectangle » ; « Fermer le polygone » supprimé.
À vérifier sur le Xiaomi : scénario 2 de `docs/TESTS_TABLETTE.md` (geste un/deux doigts, fermeture sur le dernier sommet, lisibilité en portrait, encoche).

## Étape 3 — non commencée (spécifications seulement, dans la PR #18)
`docs/ETAPE3_SPECIFICATIONS.md` : service de capture et tableau d'interruptions par flux, format des segments audio, appairage BLE/hub,
signatures sonores, protocole de mesure de précision (R3). Tests JVM : `MultiDeviceSyncTest` (3 appareils, dérive, coupure, perte du hub).

## Questions ouvertes (non tranchées)
1. Sauvegarde Android : médias inclus (décision de Sébastien) mais la limite de 25 Mio par appli rend la sauvegarde cloud partielle sur une vraie session : accepter, ou exclure les médias de la sauvegarde cloud ?
2. Le journal public (`Documents/LumiSite/_journal/`) contient le modèle du téléphone, le boot_id et les piles d'erreur : faut-il les retirer de la copie publique ?
3. Nom du dossier de site : `Documents/LumiSite/<site>_<id>/` (id ajouté pour éviter les collisions entre sites homonymes) au lieu de `<site>/` : OK ?
4. Étape 2 : tolérance de fermeture du polygone (28 dp) et taille de la flèche à ajuster sur le terrain.
5. Étape 3 : bande de fréquence des signatures, `syncIntervalS`, `hubTimeout`, BLE seul ou Wi-Fi local.

## Corrections après le test Pixel 7 du 31/10 (APK `lumisite-etape2-v110-dba4327`)
Constat de Jean : installation OK, Robo Firebase sans crash, Maestro 1 parcours sur 6 (seul 1.5 passait).
Les noms d'APK corrigés sont ceux des pré-releases `etape1-<hash>` et `etape2-<hash>` publiées par ces commits (voir la description des PR #17 et #18).

| # | Écran > Menu > Sous-menu | Cause trouvée | Correction | Branche |
|---|---|---|---|---|
| 1 | Accueil > « Nouveau » (étape 2 : « Nouveau relevé ») > formulaire « Nouveau site » | le clavier couvrait Latitude ; en portrait le formulaire n'avait pas la place de défiler | `adjustResize` + `imePadding()` ; en portrait carte en haut (38 %) et formulaire défilable dessous ; flux 1.1 : `hideKeyboard` + `scrollUntilVisible` avant chaque champ | etape-1, etape-2 |
| 1b | même formulaire | le champ Nom gardait le focus : un appui hors du champ (carte) laissait la saisie suivante s'ajouter au nom (« Maestro bureau43.6045… ») | un appui sur la carte, « Utiliser ces coordonnées » ou « Tracer le rectangle » retire le focus ; flux 1.1 vérifie « Origine : 43.6045000, 1.4440000 (COORDINATES) » | etape-1, etape-2 |
| 2 | Session > rangée « Synchro / Calibrer / Relevé / Annoter / Recaler / Export » | rangée défilante horizontale : « Annoter » et « Export » hors écran en portrait (flux 1.2, 1.3, 1.4 en échec) | boutons répartis sur plusieurs lignes (tous visibles) | etape-1, etape-2 |
| 3 | Session > « Export » > « Exporter la session » | « Exporté dans … », Ouvrir et Partager apparaissaient sous le pli du panneau | le résultat est ramené à l'écran à la fin de l'export | etape-1, etape-2 |
| 4 | Session > « Annoter » > Polygone (étape 2) | les sommets étaient bien posés, mais l'aide « Touchez à nouveau le dernier sommet » était sous le pli du panneau ; et les appuis du flux visaient des points d'écran (70 %, 85 %) qui tombent dans le panneau en portrait | l'aide « N sommets. Touchez à nouveau le dernier sommet pour fermer. » s'affiche sur la carte (`session_annoter_polygone_aide`) ; carte tagguée `session_carte` ; flux 1.2 et 2 : appuis relatifs à la carte, vérification du message puis de la fermeture (« 0 sommet ») | etape-2 (flux 1.2 d'etape-1 inchangé : pas de fermeture au dernier sommet à l'étape 1) |
| 5 | tous les flux `.maestro/` | `name:` contenait `>` (InvalidPathException sous Windows) | `name:` sans `> : / \ \| ? *` ; les libellés « Écran > Menu > Sous-menu » restent en commentaire | etape-1, etape-2 |
| 6 | Accueil > « Nouveau relevé » > « Tracer le rectangle » (étape 2) | surface affichée « 0.00000 degré carré » pour un petit rectangle | affichage « environ L × H m » | etape-2 |
| 7 | Session > « Calibrer » > « Cap de visée » (et Recaler, Annoter) — relecture automatique de la PR #17 | avec le clavier ouvert, l'en-tête AR et les lignes de modes gardaient leur hauteur et écrasaient le panneau de saisie | en-tête + modes limités à 35 % de la hauteur (défilables) : le champ saisi reste visible ; vérifié sur émulateur (saisie « 123 » visible clavier ouvert) | etape-1, etape-2 |

`clearState: true` conservé. Démarrage : les flux attendent `accueil_*` jusqu'à 90 s (sur l'émulateur, un lancement après `clearState` a pris jusqu'à 63 s, « Process failed to attach » côté système).

Résultat sur émulateur Pixel 7 (API 34 x86_64), APK local identique au code poussé :

| Parcours | etape-1 | etape-2 |
|---|---|---|
| `scenario1_1_creer_releve.yaml` | vert | vert |
| `scenario1_2_annoter.yaml` | vert | vert (fermeture au dernier sommet vérifiée) |
| `scenario1_3_exporter.yaml` | vert | vert (exécuté via 1.4) |
| `scenario1_4_reimporter.yaml` | vert (sélecteur Android, dossier proposé accepté) | vert |
| `scenario1_5_partager_journal.yaml` | vert | vert |
| `scenario2_releve_sans_blocage.yaml` | — | vert (rectangle OSM récupéré en ligne, polygone, recalage, export) |

Ce qui reste :
- réexécuter les 6 parcours sur le Pixel 7 physique avec les nouveaux APK (l'émulateur n'a ni ARCore ni le même clavier/temps de démarrage) ;
- 1.4 simule la réinstallation par `clearState` : la vraie désinstallation puis réimport reste à faire sur le Xiaomi (R2) ;
- le flux 2 dépend du réseau (API OSM) pour la présélection des bâtiments ; sans réseau le site est créé sans contours ;
- sur le Xiaomi : lisibilité de l'aide polygone posée sur la carte, et réglage de la tolérance de fermeture (question ouverte 4).
