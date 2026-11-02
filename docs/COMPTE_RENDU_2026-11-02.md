# Compte rendu — prompt v5 (02/11/2026)

Tests : CI (émulateur Pixel 7, API 34) et émulateur local seulement ; aucun test sur le Pixel 7 physique (fait par un autre agent). Rien n'est fusionné (PR #17, #18, #19 ouvertes).

## Lot M — corrections du matin

### M1 — Accueil > « Nouveau » > « Créer sans contours OSM » : bouton sous la barre de navigation (etape-1, fusionné vers etape-2 et etape-3)
- **Cause réelle** : `targetSdk 35` → Android 15+ impose l'affichage bord à bord ; aucune marge n'était réservée à la barre de navigation. Sur le Pixel 7 (navigation à 3 boutons), `nouveau_site_creer` était dessiné sous la barre et l'appui au centre tombait sur Retour. L'émulateur API 34 n'impose pas le bord à bord, d'où le vert en CI. La cause « dossiers non recréés » du compte rendu du 01/11 (1.3) était fausse ; elle y est rectifiée.
- **Correctif** : bord à bord activé sur tous les niveaux d'API (`enableEdgeToEdge()`), et marge `safeDrawing` (barres système, encoche, clavier) appliquée à la racine, donc sur Accueil, Accueil > Nouveau, Site, Réglages et Session. L'émulateur API 34 se comporte maintenant comme le Pixel.
- **CI** : `.github/scripts/maestro-ci.sh` passe l'émulateur en navigation à 3 boutons (`cmd overlay enable com.android.internal.systemui.navbar.threebutton`) avant tous les parcours ; `scenario1_1_creer_releve` n'est pas modifié. Vérifié en local : parcours 1.1 vert en navigation à 3 boutons.
- À vérifier sur le Pixel 7 : appui au centre de « Créer sans contours OSM » en navigation à 3 boutons et par gestes.

### M4 — `docs/COMPTE_RENDU_2026-11-01.md` (etape-3)
- Ligne `||||||| bcf8971` (reste d'un conflit de fusion) supprimée ; `git grep -nE '^(<<<<<<<|\|\|\|\|\|\|\||>>>>>>>)'` ne renvoie plus rien.

### Plan révisé (22 h)
- M2 (contraste des libellés) et l'option B (« Enregistrer le relevé » fixé) étaient codés et verts en local sur etape-2 ; non publiés, ils passent au LOT 4 (etape-3). M3 et les options A et C aussi.

## CI rouge sur M1/M4 (relance de 23 h 06)
- **Parcours en échec** : `scenario1_4_reimporter` seul (logs des jobs maestro de 9b70be8, 5e5f42f, 4991e77), à « Réglages > Stockage > Réimporter ».
- **Cause** (reproduite sur l'émulateur local en navigation à 3 boutons) : le sélecteur de dossier système (DocumentsUI, pas LumiSite) dessine « USE THIS FOLDER » avec son centre sur le bord haut de la barre de navigation à 3 boutons, que M1 active désormais en CI. L'appui au centre est absorbé par la barre, le sélecteur reste ouvert et `reglages_stockage_resultat` n'apparaît jamais. Ce n'est pas un défaut des marges de LumiSite.
- **Correctif du flow** (aucune assertion retirée) : attente explicite du sélecteur, puis appui au centre ; s'il est encore affiché, nouvel appui sur la moitié haute visible du bouton. Commit `8b501cb` (etape-1), fusionné vers etape-2 (`e54c512`) puis etape-3.
- Local : 1.4 vert en navigation à 3 boutons.
- Régression introduite puis corrigée sur etape-3 (CI de `9b30de5`) : `scenario1_5_partager_journal` rouge, parce que le nouveau réglage « Capteurs » (fréquence IMU) avait repoussé « Partager le journal » hors de l'écran. Le réglage est déplacé en bas de Réglages, sous Journal. Les parcours 1.4, 1.5 et `scenario_p2_reglages_retour` sont verts en local.

## LOT 1 — Fiabilité (etape-3)

### 1.0 Contrat de données — fait
- `core/.../contract/Contract.kt` + `docs/CONTRAT_DONNEES.md` : types distincts `SitePoint` (E/N + `SiteHeight`) et `ArPoint` (repère AR d'une base de temps et d'un segment). La seule conversion est `SiteLink.toSite()`, qui refuse un point d'un autre repère. Unités m, rad, ns. Provenance de chaque position : méthode, `t_mono_ns`, base de temps, UTC, repère, qualité.
- **Hauteur** : `SiteHeight.Unqualified` (« hauteur non qualifiée ») tant qu'aucun sol de référence n'est connu ; aucune hauteur en mètres n'est affichée sans sol.
- Synchro préparée, pas implémentée : chaque ligne de chaque flux porte `t_mono_ns` et l'id de la base de temps ; chaque redémarrage ou reprise journalise `TimeBaseAdded` ; pauses, photo externe, perte de suivi et arrière-plan sont des `TrackingStateChanged` / trous explicites, jamais comblés. Pas de Room : journal append-only conservé.
- Tests JVM : `ContractTest` (3).

### 1.1 Site > Sessions > (carte de session) : reprise sans perte — fait
- **Cause** : à la reprise, le `TimeBaseAdded` fermait la chronologie et la transformation du segment courant (vide) était appliquée à tout ; les objets des segments précédents n'avaient plus de coordonnées plan. Les données sur disque étaient intactes.
- **Correctif** : `TrajectoryCorrector.timeline` garde la transformation de chaque segment (base de temps, époque, borne de fin) ; chaque objet est résolu avec **son** segment, jamais avec le courant. Les ancres ARCore ne servent pas de sauvegarde.
- Session : bandeau « Session reprise : vos relevés sont affichés. Repositionnez-vous (2 points) pour continuer en AR. » avec un bouton vers Session > Calibrer (`session_reprise_positionner`) ; lignes du tableau de calibrage « session précédente » (`session_calibrer_precedente_N`) ; tant que le nouveau repère n'est pas relié, la pose AR est refusée avec un message, la pose par toucher de la carte reste possible ; 1 seul point de repositionnement = « approximatif ».
- Correctif annexe : `SessionController.logTracking` est appelé par le fil principal et le fil AR ; il est désormais sérialisé (une transition « suivi perdu → suivi » pouvait être perdue après la photo).
- Tests : JVM `ResumeSessionTest` (calibrage + 3 relevés + trace, fermeture, reprise : mêmes coordonnées plan) ; flow `scenario_p0_reprise_session.yaml` (« Session reprise donnees affichees ») vert sur l'émulateur local.

### 1.2 Session > Export > « Exporter la session » : export persistant — fait (émulateur)
- `ExportWorker` (`CoroutineWorker`, `setForeground` de type `dataSync`, notification avec pourcentage), `enqueueUniqueWork` par session avec `KEEP` : un second appui affiche l'export en cours, il n'en crée pas un deuxième.
- `core/.../export/ResumableExport.kt` : états Préparation → Instantané → Assemblage → Vérification → Copie → Terminé ; manifeste persistant `exports/<export-id>/manifest.json` (chaque entrée écrite : taille, SHA-256, CRC, position dans le ZIP) ; ZIP écrit en flux depuis les segments, sans copie intermédiaire ; à la relance, on tronque après la dernière entrée vérifiée et on reprend là. Vérification complète (tailles + SHA-256) dans le fichier temporaire, puis copie dans Documents/LumiSite. Seuls les temporaires de l'export sont supprimés, jamais les données de session.
- Barre visible dès l'appui (« Préparation… 0 % »), puis l'étape en cours ; elle reste à « Terminé 100 % » à la fin. Site > Sessions : état sous chaque carte (`site_session_etat`) : « Export en cours xx % », « Export en attente » (interrompu, reprendra), « Exportée ».
- « Terminer » pendant un export : confirmation (« Terminer quand même » / « Rester »), puis l'export continue en arrière-plan. Android peut le différer : aucune exécution continue n'est promise, il reprend où il s'est arrêté.
- Tests JVM : `ResumableExportTest` (interruption puis reprise sans réécrire les entrées vérifiées, `SessionImporter.verify` OK, temporaires nettoyés). Flows : `scenario_p0_export_progression.yaml` (« Export barre immediate », barre en moins de 1 s), `scenario_p0_export_terminer.yaml` (« Export continue apres terminer »), et `scenario1_3_exporter` toujours vert.
- **Pas fait** : le test « session de 30 min sous `-Xmx64m` » (l'écriture est en flux, mais ce n'est pas mesuré sous cette limite).

### 1.3 Poids — fait pour l'IMU (mesure par simulation JVM), partiel pour le reste
- D15 : accéléromètre et gyroscope à 100 Hz (Réglages : 50 / 100 / 200 Hz, `reglages_imu_<N>hz`), magnétomètre et vecteur de rotation à 10 Hz. Gravité, rotation « jeu » et variantes non calibrées ne sont plus enregistrées : Android les calcule à partir des mêmes capteurs. Chaque segment IMU fermé est compressé en `.ndjson.gz` par le copieur (fil du miroir, jamais le fil d'interface) ; le lecteur et l'export lisent les deux formats. **Plus de doublon D9** : chaque mesure est écrite une seule fois (flux historiques). Le format de trame D9 est conservé et testé derrière une option.
- Mesures `WeightBudgetTest` : code réel du journal, valeurs bruitées. Ce n'est **pas** une mesure sur téléphone.

| IMU | 10 min | 35 min |
|---|---|---|
| avant (9 capteurs à 50 Hz + doublon D9) | 129 Mo | ≈ 452 Mo |
| après, sans gzip | 26 Mo | ≈ 91 Mo |
| après, gzip | 3,6 Mo | **12,6 Mo** |

- Mesuré sur le Pixel par Sébastien (avant) : IMU 660 Mo pour 35 min (le Pixel livre plus vite que la cadence demandée à 50 Hz). Poses 5,9 Mo, GPS 0,3 Mo, photos 3,9 Mo : inchangés. Pour 10 min, ×0,29 : poses ≈ 1,7 Mo, GPS ≈ 0,1 Mo.
- **Audio PCM** (48 kHz mono 16 bits, conservé tel quel par décision P2f) : 96 Ko/s, soit 57,6 Mo pour 10 min et 201,6 Mo pour 35 min. **L'objectif « < 40 Mo pour 35 min » est atteint hors audio** (IMU 12,6 + poses 5,9 + GPS 0,3 + photos 3,9 ≈ 23 Mo), **pas avec l'audio brut**. Question ouverte : compression de l'audio (FLAC sans perte, environ ÷2) ou maintien du PCM.
- À vérifier sur le Pixel 7 : cadence réellement livrée à 100 Hz, et taille réelle après 35 min.

### 1.4 Formats — fait
- Export : `releves.geojson`, une seule `FeatureCollection` RFC 7946. La géométrie est en WGS84 (lon, lat) ; E/N et la hauteur (ou « non qualifiée ») sont dans `properties.local`, et la provenance dans `properties.provenance`. Il n'y a jamais de coordonnées locales dans `coordinates`. NDJSON est réservé aux journaux bruts (`raw/`). `LISEZMOI.txt` explique chaque fichier.
- Test JVM : `ResumableExportTest.relevesGeojson…`.

### 1.5 Conservation à la mise à jour — fait (CI)
- JVM : `SegmentFormatsTest`. Une session à l'ancien format 0.1/0.2 (un fichier par flux à la racine, sans `seg/`) est lue et exportée sans qu'aucun fichier ne soit modifié, et la reprise écrit dans `seg/` à côté. Les segments `.gz` et un arrêt entre compression et suppression ne font lire aucune ligne deux fois.
- Aucune migration : rien n'est réécrit ni supprimé.
- Test de mise à jour en CI (`.github/scripts/maestro-ci.sh`, après les parcours) : installation de la dernière pré-release de la même étape (version N, même clé de test), `.maestro/maj/maj_avant.yaml` (« Mise a jour avant creer session » : site + relevé, sans Terminer), arrêt brutal, `adb install -r` de l'APK du commit (N+1), puis `.maestro/maj/maj_apres.yaml` (« Mise a jour apres session lisible et exportable » : Accueil > « Maestro bureau » > « Relevés : 1 », Site > Sessions > reprise, Session > Export vérifié SHA-256). Les deux moitiés sont dans `.maestro/maj/` et non à la racine : elles ne tiennent pas seules, c'est le script qui les enchaîne autour de l'installation. Vérifié en local avec une mise à jour sur le même APK (clé de debug locale) ; la vraie mise à jour N → N+1 n'est vérifiée qu'en CI.

### Questions ouvertes (non tranchées)
1. Copie unique D9 : les flux historiques sont gardés comme seule copie (lecteur, export et réimport existants). Faut-il plutôt basculer sur `captation.ndjson` seul ?
2. Audio : PCM brut (≈ 200 Mo / 35 min) ou FLAC ?
3. Capteurs dérivés (gravité, rotation « jeu », non calibrés) : plus enregistrés. À rétablir derrière un réglage si Sébastien en a l'usage ?
