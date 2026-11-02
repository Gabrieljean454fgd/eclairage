# Compte rendu — 01/11/2026 (Prompt v4)

Mis à jour à la fin de chaque palier. Rien n'est fusionné. Tests réalisés sur émulateur Pixel 7 (API 34, x86_64, sans ARCore réel) et en CI ; **aucun test sur le Pixel 7 physique ni avec ARCore réel** : tout ce qui suit reste à vérifier sur le téléphone.

## Palier P1 — réparer et republier

| Point | Écran > Menu > Sous-menu | Statut |
|---|---|---|
| 1.1 | Session > barre d'actions (« ← Terminer la session », « Synchro », « Calibrer », « Relevé », « Annoter », « Recaler », « Export ») | fait (à vérifier sur Pixel 7) |
| 1.2 | Session > carte > « Point à la croix », « Centrer / Suivre », légende, « Fond : … » | fait (à vérifier sur Pixel 7) |
| 1.3 | Accueil > « Nouveau » > « Créer » (etape-1) | cause probable corrigée, non confirmée sur Pixel 7 |
| 1.4 | Parcours Maestro 1.1 à 1.5 et 2 + CI | voir ci-dessous |

### 1.1 — Cause et correctif
- **Cause** (déduite du code, non observée sur le téléphone) : depuis a4a4da9, l'en-tête AR (bandeau de suivi, aperçu caméra 150 dp, torche/mesure) **et** la barre de modes étaient dans la même colonne défilante plafonnée à 35 % de la hauteur. Avec ARCore actif, l'en-tête AR occupe à lui seul ces 35 % ; la barre de modes se retrouvait sous le pli de cette colonne, donc hors écran. Compose ne compose pas l'arbre d'accessibilité des éléments entièrement hors de la zone visible d'un parent défilant tronqué de cette façon, d'où des testTags « absents » côté Maestro. Sur émulateur, sans ARCore, l'en-tête était petit et la barre restait visible.
- **Correctif** : la barre d'actions (Terminer la session + 6 modes, testTags inchangés) est **fixe**, jamais dans un défilement, placée en premier. Seul le bloc d'état/AR (`session_bloc_ar`) défile, plafonné à 25 % de la hauteur. La correction clavier de Session > « Calibrer » > « Cap de visée » est conservée : le panneau de saisie garde le reste de la hauteur.
- **Simulation du cas réel** : en APK debug uniquement, `adb shell settings put global lumisite_simuler_entete_ar 1` affiche un bloc « AR simulé » de même encombrement que le vrai (200×150 dp + torche/mesure). Les parcours locaux et la CI tournent avec ce réglage.
- La barre compacte à icônes (point 2.1) n'est pas faite dans ce palier (P2).
- **Relecture automatique de la [PR #17](https://github.com/Gabrieljean454fgd/eclairage/pull/17)** (2 remarques, corrigées) :
  - écran étroit (360 dp) avec clavier ouvert : « ← Terminer la session » et les 6 modes sont maintenant dans une seule rangée qui passe à la ligne sur toute la largeur (2 lignes sur Pixel 7, environ 3 sur 360 dp, au lieu d'un mode par ligne) ; en portrait, quand la hauteur utile descend sous 480 dp (clavier ouvert ; le paysage garde le bloc AR et ses commandes torche / mesure), le bloc AR est replié (toujours présent, aperçu caméra non détruit) et la carte cède de la place au panneau de saisie. Testé sur émulateur Pixel 7 seulement ; **non testé en 360 dp** (le parcours 1.1 lui-même n'a pas pu être rejoué à cette taille : défilement du formulaire, à voir séparément) ;
  - session ouverte quand Android recrée l'activité (mode nuit, langue) : l'ancien `SessionController` est maintenant fermé (hors du fil d'interface) ; l'écran Site s'affiche et « Reprendre » attend la fin de cette fermeture (10 s au plus) avant de rouvrir la session, pour qu'il n'y ait jamais deux écritures sur la même session. Non testé sur appareil. Une première version fermait la session à toute destruction de l'activité, sur le fil d'interface : elle provoquait un écran noir au relancement dans le parcours 1.4 ; elle a été limitée à la recréation.

### 1.2 — Chevauchements de la carte
- « Point à la croix » et « Centrer / Suivre » sont empilés en colonne en bas à droite (hauteur ≥ 48 dp chacun), au-dessus du bord bas ; « largeur visible » reste en bas à gauche ; la légende de trajectoire est en haut à gauche, limitée à 45 % de la largeur, donc ne recouvre plus Session > carte > « Fond : … » (en haut à droite).
- La barre d'actions n'est plus recouverte par la carte (elle est hors de la carte, au-dessus).
- Le cartouche « Source : GPS · non calibré… » sera raccourci au point 2.1 (P2).

### 1.3 — Accueil > « Nouveau » > « Créer » : site absent sur Pixel 7
- **Cause la plus probable** (trouvée dans le code, non reproduite sur le Pixel 7) : `Repository` créait les dossiers `sites/`, `sessions/`, `exports/` **une seule fois** au démarrage. Maestro `clearState` (`pm clear`) peut vider le dossier privé alors que le processus garde ses chemins ; l'écriture du site se faisait ensuite dans un dossier absent, `renameTo` échouait **sans erreur**, et l'écran Site, ne trouvant pas le site, revenait silencieusement à l'Accueil vide. C'est exactement le symptôme décrit. Seconde cause possible : la recréation de l'activité par Android (écran courant non sauvegardé).
- **Correctifs** : dossiers recréés à chaque accès ; écriture du site atomique avec `fsync`, échec de renommage levé, relecture immédiate ; en cas d'échec, message « Site non enregistré : … » et entrée dans le journal (Réglages > Journal) au lieu d'un retour silencieux ; si un site ouvert est introuvable, message « Site introuvable (voir Réglages > Journal) » et entrée de journal ; écran courant conservé si Android recrée l'activité (une session en cours revient sur son écran Site, d'où « Reprendre »).
- **À vérifier sur le Pixel 7** : si le symptôme persiste, le journal (Réglages > Journal) contient désormais la raison exacte.

### 1.4 — Parcours Maestro
- Flows inchangés dans leurs vérifications (`session_terminer_la_session`, `session_annoter` toujours vérifiés « visible »). Seul changement : attente du premier écran portée à 90 s dans 1.1 et 1.4 (lancement à froid lent sur émulateur).
- Nouveau script CI `.github/scripts/maestro-ci.sh` et job `maestro` dans `.github/workflows/apk-etape.yml` : émulateur Pixel 7 API 34, en-tête AR simulé, application vidée avant chaque parcours, 2 essais par parcours ; la pré-release n'est publiée que si tous les parcours passent.
- Résultats en CI (GitHub Actions, émulateur Pixel 7 API 34, en-tête AR simulé), tous au premier essai :
  - etape-1 `a23e71d` : 1.1, 1.2, 1.3, 1.4, 1.5 ✅ (5/5) ;
  - etape-2 `ff0dd61` : 1.1, 1.2, 1.3, 1.4, 1.5, 2 ✅ (6/6).
- Mêmes résultats en local sur l'émulateur Pixel 7. Vérification supplémentaire en local : Session > « Calibrer » > « Cap de visée », saisie « 123 » clavier ouvert, valeur visible, puis « ← Terminer la session », « Annoter » et « Export » visibles une fois le clavier fermé.

### Pré-releases P1 (signées avec la clé de test publique, certificat SHA-256 `8830d132…746fe4`, vérifié par `apksigner verify --print-certs`)
| Branche | Tag | APK |
|---|---|---|
| etape-1 | `etape1-a23e71d` | `lumisite-etape1-v113-a23e71d.apk` |
| etape-2 | `etape2-ff0dd61` | `lumisite-etape2-v125-ff0dd61.apk` |

Pré-releases intermédiaires (remplacées) : `etape1-c3c749b` (v110) avant les corrections de la relecture automatique.

### À vérifier sur le Pixel 7 (P1)
1. Session avec ARCore réel et suivi `INSUFFICIENT_LIGHT` : les 7 actions visibles sans défilement (`session_synchro`, `session_calibrer`, `session_releve`, `session_annoter`, `session_recaler`, `session_export`, `session_terminer_la_session`).
2. Session > carte : plus aucun chevauchement entre la source de position, « Fond : … », « largeur visible », « Point à la croix » et « Centrer / Suivre ».
3. Accueil > « Nouveau » (étape 1) ou « Nouveau relevé » (étape 2) > « Créer » : le site est listé et l'écran Site s'ouvre ; sinon, envoyer Réglages > Journal (la cause y est écrite).
4. Les 6 parcours Maestro.

### Questions ouvertes (P1)
- La cause du point 1.3 est déduite du code et non reproduite sur le Pixel 7 : à confirmer par le parcours 1.1 sur le téléphone.

## Palier P2 — sur etape-2

### P2a — Session : ergonomie (prompt v4 point 2.1 ; « 2.2 ergonomie » dans l'ordre du complément)
Statut : fait sur émulateur, à vérifier sur Pixel 7 avec ARCore réel.

Changements de libellés (ancien → nouveau) :
- Session > grand bandeau « Suivi : initialisé (TRACKING) / initialisation… / perdu » → pastille « TRK » (vert : suivi, orange : initialisation ou dégradé, rouge : perdu, gris : arrêté ou indisponible). Toucher la pastille affiche le libellé complet et la raison (infobulle).
- Session > texte « ARCore actif… » → pastille « AR ● » (« AR ○ » si inactif). La toucher affiche l'état détaillé.
- Session > carte > « Source : GPS · non calibré · dernière position valide il y a 12 s » → « GPS · non calibré · 12 s ».
- Session > « Torche : oui / non » → icône lampe 🔦 (« 🔦● » allumée) ; « Mesure A / B » → icône règle « 📏 A / 📏 B ». Les testTags `session_ar_torche_oui` et `session_ar_mesure_a` sont inchangés, et l'ancien libellé est conservé en description d'accessibilité.
- Session > barre d'actions : déplacée tout en bas, sur une seule ligne de 7 cases (icône + libellé court, 56 dp de haut) : « Synchro », « Calibrer », « Relevé », « Annoter », « Recaler », « Export », « Terminer » (ancien « ← Terminer la session »). testTags inchangés.
- Session > « Afficher la trajectoire sans recalage » : **supprimé**. La trajectoire affichée est toujours la meilleure estimation, recalée quand il y a des recalages. Le testTag `session_checkbox`, qui n'était utilisé par aucun parcours, disparaît avec l'option, comme demandé.

Disposition en portrait : pastilles en haut, puis bloc caméra (200×150 dp) avec ses commandes à côté, puis la carte (60 % de la hauteur restante, au lieu de 45 %), le panneau, et la barre d'actions. La caméra n'est jamais recouverte. Avec le clavier ouvert en portrait, le bloc caméra est replié le temps de la saisie.

Flow Maestro : `.maestro/scenario_p2_session_barre_actions.yaml` (« Session barre d'actions ») vérifie, sans défilement, la barre, les 7 actions, les pastilles AR et TRK et la carte, et que l'option supprimée n'est plus affichée. Test JVM mis à jour : `Etape2Test` (texte court de la source).

### P2b — Positionnement intérieur, sous-lot 1 : solveur, GNSS, tableaux (prompt v4 2.4, décisions D1–D5, D7)
Statut : partiel. Calculs testés en JVM, interface testée sur émulateur avec des poses AR **simulées** ; rien n'est vérifié avec ARCore réel ni sur le Pixel 7.

- **`core` / `app.lumisite.core.position.CalibrationSolver` (D3, D4, D5)** : transformation 2D directe (rotation + translation, échelle 1), moindres carrés pondérés de type Helmert / Umeyama sans échelle, sans bibliothèque.
  - N = 1 : translation + cap. N ≥ 2 : rotation par moindres carrés. Si les points sont à moins de 2 m les uns des autres, la rotation précédente est gardée.
  - Résidu calculé pour chaque point.
  - Rejet d'un point si résidu > max(2σ ; 1,0 m), au plus 3 itérations, jamais moins de 2 points conservés. σ est calculé sur les autres points ; c'est mon interprétation de D4, car un point aberrant gonfle sinon son propre seuil. Le point rejeté reste dans le tableau, marqué « rejeté ».
  - Poids exp(−âge / 5 min), et un nouveau segment à chaque recalage. Le dernier recalage est appliqué exactement (« Je suis ici »), marqué « ancre ». C'est ce qui garantit qu'un recalage déplace la position de la valeur attendue (test D2).
- **`GnssClassifier` (D7)** : fiable / dégradé / inutilisable selon les satellites utilisés (`GnssStatus.usedInFix`), la moyenne C/N0 des 4 meilleurs, la précision (`Location.getAccuracy`) et l'âge du dernier fix GNSS.
  - Hystérésis : passage à « inutilisable » après 3 s de mauvais échantillons, retour à « fiable » après 10 s de bons échantillons.
  - Seuils réglables (`GnssThresholds`), écrits dans Réglages > Journal à chaque session, et chaque changement d'état est journalisé.
  - Affichage : Session > carte > « AR · calibré · 3 s · GNSS inutilisable ». La flèche GPS devient grise quand le GNSS est inutilisable.
  - D1 : le GNSS n'entre jamais dans la position AR calibrée.
- **D2, journal** : une fois par seconde, Réglages > Journal reçoit une ligne `POSITION` avec la source (AR+calibrage / AR figée / non calibrée), le numéro de segment, l'événement actif, la rotation (°) et la translation (m), la pose AR dans le plan (x, −z) et la position sur le site.
- **Recaler sans effet, saut à 40 m** : la cause n'est pas confirmée faute de journal du Pixel 7. Hypothèses lues dans le code :
  1. Calibrer et Recaler exigent un suivi AR « TRACKING ». Avec `INSUFFICIENT_LIGHT` (bureau peu éclairé), ils étaient refusés avec un simple message, et la flèche restait figée sur la dernière position valide. Le message donne maintenant l'état et la raison du suivi.
  2. L'ancien recalage gardait la rotation de la calibration 1 point. Avec un cap faux (boussole en intérieur), chaque déplacement repartait en biais, donc la position redevenait fausse juste après le recalage. Les recalages entrent maintenant dans l'estimation de la rotation.
  3. Le point GPS qui saute de 40 m est le GPS lui-même en intérieur. Il est désormais classé « inutilisable » et grisé.
  
  Les lignes `POSITION` et `GNSS` du journal Pixel 7 permettront de trancher.
- **Session > Calibrer** (libellés ancien → nouveau) :
  - « 1 point + cap » / « 2 points », « Capturer A », « Capturer B et valider », « Distance A-B mesurée » et « Échelle fixée à 1 » sont **remplacés** par le calibrage multi-points : « Valider le point N » (testTag `session_calibrer_valider_la_calibration` conservé).
  - Le champ « Cap de visée du 1er point (°) » (testTag `session_calibrer_cap_de_visee` conservé) n'apparaît que pour le premier point.
  - Tableau `session_calibrer_tableau`, une ligne par point (`session_calibrer_ligne_N`) : n°, heure, E / N, résidu, statut (retenu / rejeté / ancre / en attente), bouton « Supprimer » (`session_calibrer_supprimer_N`). Bouton « Nouveau calibrage » (`session_calibrer_nouveau_calibrage`).
  - Chaque point est marqué sur le plan (orange, « Calibrage N »).
  - Testtags supprimés avec l'ancien mode 2 points, utilisés par aucun parcours : `session_calibrer_1_point_cap`, `session_calibrer_2_points`, `session_calibrer_capturer_a`, `session_calibrer_capturer_b_et_valider`, `session_calibrer_distance_a_b_mesuree_m`, `session_calibrer_checkbox`.
- **Session > Recaler** : même tableau (`session_recaler_tableau`, `session_recaler_ligne_N`, `session_recaler_supprimer_N`) et bouton « Nouveau recalage » (`session_recaler_nouveau_recalage`). Points en violet sur le plan.
  - Interprétation : « Nouveau calibrage » vide les deux tableaux, « Nouveau recalage » seulement les recalages. Un tableau vidé laisse la dernière transformation active jusqu'au point suivant.
- **Inclinaison de visée** : affichée en lecture seule dans Session > Calibrer (« Inclinaison de visée (calculée, pose ARCore / capteur de rotation) : −23° ») par `Tilt`.
  - **Question ouverte** : le code ne contient aucun champ « hauteur / angle de visée » à supprimer. Le seul champ de visée est « Cap de visée », que D3 exige pour N = 1 : je l'ai gardé pour le 1er point seulement.
- **Ordre du panneau** : les commandes du mode actif passent avant les lignes d'état génériques (poses, GPS, distance), qui sont déplacées sous le mode.
- Tests JVM : `PositioningTest` (14 tests).
  - Solveur : 1 point avec cap, 1 point sans cap refusé, 2 points avec rotation de 90°, 6 points bruités, point aberrant rejeté mais gardé dans le tableau, jamais moins de 2 points.
  - Recalage qui déplace la position de la valeur attendue.
  - Trajectoire simulée de 10 min avec dérive (1°/min + 1 cm/s) et 9 recalages : erreur moyenne divisée par plus de 2.
  - GNSS en intérieur avec une précision qui saute à 40 m → « inutilisable » ; critères D7 un par un.
  - Inclinaison, glissement d'affichage D8.
- Flow Maestro : `.maestro/scenario_p2_calibrage_multipoints.yaml` (« Session calibrage multipoints ») : 2 points validés → 2 lignes → « Supprimer » → 1 ligne.
  - L'émulateur n'ayant pas ARCore, le parcours lance l'appli avec l'argument `simuler_pose_ar` : poses AR synthétiques sur un cercle de 6 m, APK de débogage seulement, source `SIMULATED_DEBUG` dans le journal.
  - Ce parcours vérifie l'interface et le tableau, **pas** le suivi AR réel. Aucun utilisateur ne peut l'activer sans lancer l'appli par adb ou Maestro.
- D6 et D8 : voir le sous-lot 2 ci-dessous.

### P2a — publication
- Pré-release `etape2-e916bbc`, APK `lumisite-etape2-v128-e916bbc.apk`. CI verte (APK, parcours Maestro sur émulateur, publication). Pas de validation Pixel 7 physique ni ARCore réel.

### P2b — Positionnement intérieur, sous-lot 2 : ancres ARCore (D6) et glissement d'affichage (D8)
Statut : fait dans le code, **non vérifiable sur émulateur** (pas d'ARCore). À vérifier sur le Pixel 7.
- **D6, points de contrôle** : chaque point validé dans Session > Calibrer ou Session > Recaler crée une Anchor ARCore à la pose du téléphone (`ArTracker.requestAnchor`, sur le fil GL). « Supprimer », « Nouveau calibrage » et « Nouveau recalage » la détachent.
  - Au plus une fois par seconde, les poses des ancres suivies sont relues. Le solveur repart de ces poses actuelles plutôt que des poses enregistrées.
  - Si la position courante bouge de plus de 10 cm, la transformation est recalculée et journalisée (`CalibrationEvent`, note « ancres ARCore »). Le seuil de 10 cm est mon choix, pour éviter d'écrire un événement par seconde.
  - Chaque mise à jour des ancres écrit une ligne `POSITION` dans Réglages > Journal.
- **D6, objets implantés** : une ancre partagée par groupe d'objets situés à moins de 8 m de son point de création (`AnchorGroups`, test JVM). Le rattachement est seulement journalisé (`ANCRES`) : les surcouches AR qui l'exploiteront font partie de la P3, reportée. Les coordonnées enregistrées des objets ne bougent pas avec ces ancres.
- **D8** : la flèche AR de Session > carte glisse vers sa nouvelle position en 0,5 s. Au-delà de 200 m, elle saute directement (changement de repère). Ce lissage ne touche que l'affichage, jamais le journal.
- Position figée et grisée pendant une perte de suivi AR (D1) : comportement déjà en place (la flèche grise sur la dernière pose suivie, sans extrapolation), inchangé.
- Tests : `PositioningTest.objectsShareAnAnchorWithin8m` (15 tests au total). Le parcours `scenario_p2_calibrage_multipoints` a été rejoué avec ces changements.

### P2c — Version affichée (prompt v4 2.2, ordre « 2.1 version à l'accueil » des décisions)
Statut : fait, testé sur émulateur.
- **Accueil** : le titre « LumiSite : diagnostic éclairage (prototype) » devient « LumiSite : diagnostic éclairage », avec en dessous la version `v0.3.0-alpha (build <versionCode>)` (testTag `accueil_version`).
- **Réglages > À propos** (nouvelle section) : « LumiSite v0.3.0-alpha (build <versionCode>) » (testTag `reglages_a_propos_version`).
- `versionName` passe de `0.2.0-v<versionCode>` à `0.3.0-alpha`. Le numéro de build est le versionCode, qui continue de croître à chaque commit. Le versionName figure aussi dans le manifeste d'export et dans le User-Agent OSM.
- Flow Maestro : `.maestro/scenario_p2_version_accueil.yaml` (« Version visible sur Accueil et Reglages »).

### Publications P2b / P2c
- P2b sous-lot 1 : pré-release `etape2-2476563`, APK `lumisite-etape2-v129-2476563.apk` (CI verte).
- P2b sous-lot 2 (781a0ab) : **pas de pré-release séparée**. GitHub a annulé le build en attente quand le commit P2c a été poussé : une seule exécution en attente est gardée par branche. Son contenu est inclus dans la pré-release P2c.
- P2c : pré-release `etape2-11925f3`, APK `lumisite-etape2-v131-11925f3.apk` (CI verte, contient aussi D6 / D8).

### P2d — Session > Annoter : sélection directe et déplacement (prompt v4 2.5)
Statut : fait, testé sur émulateur.
- **Cause du déplacement incorrect** (lue dans le code, reproduite sur émulateur) :
  1. L'épingle « Point choisi » se posait exactement sur l'objet (après « Point à la croix ») et captait l'appui, si bien que l'objet devenait insélectionnable.
  2. Après une modification (N +0,1, « Déplacer au point touché… »), le relevé reçoit un nouvel identifiant (journal append-only). La sélection pointait encore l'ancien identifiant et le panneau de l'objet disparaissait.
  3. Avec un outil de dessin actif (Poser, Polygone…), l'appui sur la carte était pris par l'outil, et « Déplacer au point touché sur la carte » ne recevait jamais de point.
  4. Le panneau du bas gardait sa position de défilement : le panneau de l'objet sélectionné, placé en haut, restait hors de l'écran.
- **Corrections** :
  - L'épingle « Point choisi » laisse passer l'appui.
  - La sélection suit le nouvel identifiant après chaque modification.
  - Le panneau revient en haut quand on change de mode ou d'objet.
  - Le dernier message (« Relevé … enregistré », refus, etc.) est maintenant fixé au-dessus de la barre d'actions (`session_message`) au lieu du haut du panneau défilant, où il était souvent invisible.
- **Session > Annoter > objet sélectionné** (appui sur son symbole sur la carte, ou dans la liste « Objets du relevé », qui reste) :
  - Le symbole passe en orange.
  - Affichage réduit à « Relevé sélectionné : <famille> » et aux coordonnées (`session_objet_coordonnees`).
  - Trois boutons : « Déplacer » (`session_objet_deplacer`), « Détails ▸ » (`session_objet_details`) et « Fermer » (`session_objet_fermer`).
  - Les anciens contrôles (pièces jointes, N/E ±, « Déplacer au point touché sur la carte », commentaire, « Enregistrer », « Changer de famille », « Supprimer ») sont dans le panneau repliable « Détails ». Leurs testTags sont inchangés ; aucun parcours existant ne les utilisait.
- **« Déplacer »** : on glisse le symbole avec un doigt, et la carte ne bouge pas (deux doigts la déplacent).
  - Le symbole garde l'écart de prise, il ne saute pas sous le doigt.
  - « Valider le déplacement » (`session_objet_valider_le_deplacement`) écrit un relevé modifié (`MANUAL_EDIT`), et « Annuler » (`session_objet_annuler_le_deplacement`) n'écrit rien. Un second glisser repart de la position en cours.
  - Le bouton existe ; l'appui long n'est pas implémenté.
- Tests JVM : `DragMoveTest`.
  - Aller-retour écran ↔ WGS84 en projection Web Mercator (`ScreenProjection`, même formule que la carte).
  - Un glisser de (dx, dy) pixels déplace l'objet de (dx, −dy) × mètres par pixel dans le plan, et le symbole se retrouve sous la position glissée à 0,5 px près.
  - Sans mouvement du doigt, l'objet ne bouge pas.
  - Sur l'appareil, la conversion pixel → WGS84 est faite par la projection osmdroid. Le test couvre la formule et la conversion vers le plan (`DragMove`, `LocalTangentPlane`).
- Flow Maestro : `.maestro/scenario_p2_annoter_deplacer.yaml` (« Session annoter selection directe et deplacement ») : un objet est posé à la croix, puis sélectionné par appui sur la carte, glissé et validé. Les coordonnées affichées doivent différer de celles copiées avant le déplacement.
- Reste discutable (non tranché) : le panneau Session > Relevé garde la grille complète des familles au-dessus de « Enregistrer le relevé », ce qui oblige à défiler sur un écran de la taille du Pixel 7.

### P2e — Session > Relevé : photo automatique à l'implantation (complément du prompt v4)
Statut : fait, testé sur émulateur (sans photo réelle).
- **Session > Relevé > « Enregistrer le relevé »** : dès que l'objet est enregistré, l'appareil photo s'ouvre.
  - C'est l'application photo du téléphone, lancée par `ActivityResultContracts.TakePicture`, la même que pour « Photo vue » / « Photo étiquette ». Je n'ai pas fait de capture CameraX intégrée : elle aurait coûté plus de crédits, et la caméra est déjà occupée par ARCore pendant la session.
  - Au retour, le panneau Relevé n'affiche que la proposition (`session_photo_proposee`) :
    - **« Prendre la photo »** (`session_photo_prendre`), qui devient « Autre photo » après une première photo : on peut ajouter plusieurs photos ;
    - **« Reprendre »** (`session_photo_reprendre`) : la nouvelle photo remplace la précédente dans la fiche de l'objet. L'ancien fichier reste dans `attachments/` (journal append-only, rien n'est effacé) ;
    - **« Passer »** (`session_photo_passer`), qui devient « Terminé » après une photo.
  - Chaque photo est rattachée à l'objet (pièce jointe `image/jpeg`, rôle `vue`, horodatage UTC et monotone de la prise). La position est celle de l'objet.
- **Accueil > Réglages > Relevé > « Photo automatique à l'implantation »** (`reglages_photo_automatique`) : interrupteur, activé par défaut.
- Flow Maestro `.maestro/scenario_p2_photo_implantation.yaml` (« Session releve photo automatique a l implantation »), passé sur émulateur :
  1. Pose d'un objet, puis vérification que l'appareil photo est au premier plan (la Session n'est plus visible).
  2. Retour sans photo.
  3. La proposition s'affiche, avec « Reprendre » inactif.
  4. « Passer » la ferme.
- Flow `.maestro/scenario_p2_version_accueil.yaml` : vérifie aussi que l'interrupteur est visible et activé par défaut.
- **Non testé par Maestro** : la prise de vue réelle (on ne pilote pas l'application photo de l'émulateur) et l'effet de l'interrupteur désactivé. À vérifier sur le Pixel 7.
- Flows P2d et P2e : le panneau Relevé défile jusqu'à « Enregistrer le relevé » par une boucle `repeat … while notVisible`. Le nombre de gestes nécessaire variait d'une exécution à l'autre ; aucune assertion n'est retirée.

### P2f — Session > Synchro : audio toujours gardé et diagnostic de la lenteur (prompt v4 2.3)
Statut : fait pour « audio toujours gardé » et pour la mesure. **L'accélération n'est pas faite** : c'est une question ouverte, voir plus bas.
- **Session > Synchro** : la case « Garder l'enregistrement audio (WAV) pour analyse » (`session_synchro_checkbox`) est supprimée, à la demande. Elle est remplacée par le texte « L'enregistrement audio (WAV) est toujours gardé dans le dossier de la session. ». Chaque « Synchroniser » écrit `sync_<code>_<horodatage>.wav` dans le dossier de session.
- **Mesure** : chaque tour journalise ses durées par phase dans Réglages > Journal, rubrique `SYNCHRO` : `ouverture_micro`, `ecoute_fenetre`, `ecriture_wav`, `detection`. Elles s'affichent aussi sous le résultat.
- **Cause de la lenteur** (lue dans le code et mesurée sur la JVM ; pas encore de mesure sur le Pixel 7) :
  - Le bouton écoute une **fenêtre fixe de 12 s** (`AudioSync.WINDOW_MS`). Chaque appareil émet son chirp à un instant tiré au hasard entre 1 et 10 s, pour que plusieurs appareils déclenchés à quelques secondes d'intervalle s'entendent mutuellement. Un tour dure donc au moins 12 s, quel que soit l'appareil.
  - La détection sur 12 s à 48 kHz prend environ 0,8 s sur la JVM de la VM de CI (test `SyncTimingTest`). Sur téléphone, elle est probablement plus longue (non mesurée). Elle tourne déjà hors du fil UI (`Dispatchers.IO`).
  - Le WAV (≈ 1,1 Mo) s'écrit en une fois après l'écoute.
  - L'affichage réagit immédiatement : « Écoute et émission en cours… » paraît dès l'appui, sans attendre le micro.
- **Question ouverte (non tranchée)** : raccourcir la fenêtre, par exemple à 6 s avec une émission entre 1 et 4 s, accélérerait le bouton. En contrepartie, les appareils devraient être déclenchés presque en même temps, sinon ils ne s'entendent plus. Autre option : garder AudioRecord ouvert en continu pendant la session, mais cela occupe le micro en permanence. Aucun changement tant que ce n'est pas décidé ; les durées journalisées sur le Pixel 7 aideront à choisir.
- Test JVM : `SyncTimingTest`, qui mesure la détection sur une fenêtre de 12 s et vérifie qu'elle retrouve le chirp injecté.
- Flow Maestro : `.maestro/scenario_p2_synchro_audio_garde.yaml` (« Session synchro audio toujours garde »). La synchro audio réelle entre deux appareils n'est pas testable sur l'émulateur.

## Priorité 3 (branche `etape-3`, PR brouillon)

### P3a — Carte > fonds IGN (D12)
Statut : fait, testé sur émulateur avec le réseau de la VM.
- **Existant avant ce lot** : Session > carte > « Fond : … » proposait déjà « Plan IGN » (`GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2`) et « Orthophoto IGN », servis par `https://data.geopf.fr/wmts`, sans clé, avec l'attribution IGN dans le cartouche osmdroid.
- **Ajout** : l'entrée de menu « Cadastre IGN (Parcellaire Express) par-dessus » (case `carte_cadastre`) dans le même menu.
  - La couche `CADASTRALPARCELS.PARCELLAIRE_EXPRESS` est dessinée en PNG transparent au-dessus de n'importe quel fond.
  - Le choix est gardé entre deux lancements.
  - Quand la couche est active, l'attribution « Cadastre : IGN – Parcellaire Express (PCI), Etalab 2.0 » s'affiche sur la carte (`carte_attribution_cadastre`).
- **Cache** : seules les tuiles consultées sont gardées (cache osmdroid). La politique de la source interdit le téléchargement en masse et le préchargement (`FLAG_NO_BULK | FLAG_NO_PREVENTIVE`). Les règles de cache des fonds (OSM, Plan IGN, imagerie limitée au site) sont inchangées.
- **Vérifié** : une tuile du cadastre est servie sans clé (HTTP 200, image/png, zoom 17 sur Toulouse), et le style `normal` est bien celui par défaut d'après le GetCapabilities.
- Test JVM : `BasemapsTest.cadastreUrlUsesGeoplateformeWithoutKey`.
- Flow Maestro : `.maestro/scenario_p3_cadastre_ign.yaml` (« Session carte cadastre IGN »), passé sur émulateur : Plan IGN, puis cadastre activé (attribution visible), puis désactivé.

### P3b — Spec du mode captation (D9)
Statut : spécification écrite, sans code, comme demandé : `docs/FORMAT_ECHANGE_CAPTATION.md`.
- Format JSONL. Champs `t_mono_ns` (= `elapsedRealtimeNanos`), `clock_offset_ns`, `seq`, `device_id`, `time_base`, `type` et `v`.
- Types de trames : IMU, pose, GNSS, chirps audio, LiDAR / scan 3D, état de liaison, coupures `gap`.
- Synchro prévue : échange de type NTP en local, puis recalage par chirp au bureau.
- Contrainte notée : Nearby entre Android et iOS = même Wi-Fi obligatoire.
- **Question ouverte** : écrire le journal de session « dès maintenant » dans ce format n'est pas fait. La correspondance avec le journal actuel est complète (§6 de la spec). Je propose un export dérivé plutôt qu'une réécriture du stockage.
||||||| bcf8971


### Corrections CI après P2d–P2f (flows Maestro, sans assertion retirée)

- **Session > Calibrer** (`scenario_p2_calibrage_multipoints`) : `session_calibrer_ligne_2` introuvable. Cause : depuis P2d, le dernier message (« Calibrage : 2 point(s)… ») est fixé au-dessus de la barre d'actions ; les glissements du flow partaient de 88 % de la hauteur, donc sur ce message, et le panneau ne défilait pas (la ligne 2 existait, sous le message). Correction : glissements courts dans le panneau jusqu'à la ligne 2, puis retour jusqu'à la ligne 1 ; les deux lignes restent vérifiées « visible », puis « Supprimer » → 1 ligne.
- **Session > Annoter** (`scenario_p2_annoter_deplacer`) : depuis P2e, « Enregistrer le relevé » ouvre l'appareil photo (Session > Relevé > photo automatique, activée par défaut). Le flow fait maintenant « retour » puis Session > Relevé > « Passer », comme `scenario_p2_photo_implantation`, avant les vérifications inchangées.
- **Accueil > Réglages** (`scenario1_4_reimporter`, échec unique sur le run de 74badcd : `accueil_reglages` non visible après relance `clearState`) : non reproduit en local (vert), vert sur les runs CI suivants ; classé comme instabilité de l'émulateur (écran noir au lancement déjà observé), à surveiller.

**Résultat CI** : 12/12 flows verts sur etape-2 (05a462e) → pré-release `etape2-05a462e`, APK `lumisite-etape2-v136-05a462e.apk` (contient P2d, P2e, P2f) ; 13/13 sur etape-3 (3f94f25, ajoute `scenario_p3_cadastre_ign`) → pré-release `etape3-3f94f25`, APK `lumisite-etape3-v141-3f94f25.apk` (ajoute D12 ; D9 = documentation). Émulateur CI uniquement : rien de vérifié sur le Pixel 7 physique ni avec ARCore réel.

## Relance V4 n°1 (02/11) — retours Pixel 7 réel sur `etape2-05a462e` (Maestro 11/12)

| Écran > Menu > Sous-menu | Statut | Cause / correction | Flow Maestro |
|---|---|---|---|
| Site (après Accueil > Nouveau relevé > Créer) — titre du site | fait | Cause : l'écran Site était toujours en deux colonnes (carte 60 % / détails 40 %) ; sur 412 dp, la colonne de droite (~165 dp moins la marge et le bouton « ← Retour ») laissait quelques dp au titre, coupé lettre par lettre. Correction : colonne unique sous 600 dp de large (carte en haut, détails sur toute la largeur), deux colonnes au-delà ; titre sur 2 lignes au maximum avec points de suspension (testTag `site_titre`). | `scenario1_1_creer_releve` : `assertVisible` du titre complet « Maestro bureau » sur `site_titre` |
| Session > Calibrer > multi-points (« Valider le point 1 ») | fait | Cause : le mode test `simuler_pose_ar` ne démarrait les poses synthétiques que si ARCore n'avait pas démarré ; sur un Pixel réel, ARCore démarre mais reste en INSUFFICIENT_LIGHT / sans suivi, donc aucune pose et refus. Correction (APK de débogage uniquement) : avec `simuler_pose_ar`, les poses synthétiques sont utilisées et les trames ARCore réelles ignorées, qu'ARCore soit présent ou non (journal : source SIMULATED_DEBUG). Pour l'utilisateur réel, le refus est explicite : « Calibrage impossible : pose AR indisponible (état : raison) : éclairez la scène ou bougez lentement. » (même message pour Session > Recaler). | `scenario_p2_calibrage_multipoints` (inchangé) |
| Accueil > Réglages > Retour système | fait | Cause : l'écran Réglages n'interceptait pas le Retour Android (pas de BackHandler), donc l'activité se fermait. Correction : Retour = Accueil. | nouveau `scenario_p2_reglages_retour` |

Vérifié en local sur émulateur (pixel7, API 34, sans ARCore) : les trois flows ci-dessus passent. Le cas « ARCore présent mais sans suivi » n'est pas reproductible sur l'émulateur : à vérifier sur le Pixel 7 réel.

**Résultat CI de la relance n°1** : 13/13 flows verts sur etape-2 (08f0514, dont le nouveau `scenario_p2_reglages_retour`) → pré-release `etape2-08f0514`, APK `lumisite-etape2-v138-08f0514.apk` ; etape-3 (bf65b07, + `scenario_p3_cadastre_ign`) verte → pré-release `etape3-bf65b07`, APK `lumisite-etape3-v145-bf65b07.apk`. Émulateur uniquement. Reste dans l'ordre v4 : rien (D9 était le dernier point ; le reste de la P3 est reporté). Questions ouvertes inchangées : accélération de Session > Synchro (fenêtre de 12 s, AudioRecord réutilisé) et écriture directe du journal au format D9.

## Relance V4 n°2 (02/11) — retours Pixel 7 réel : `etape2-08f0514` 13/13, `etape3-bf65b07` 14/14 (branche `etape-3` seule ; `etape-2` non touchée)

### Corrigé
1. **Session > carte > « Fond : OSM standard » > « Cadastre IGN (Parcellaire Express) par-dessus »** : parcelles invisibles par-dessus OSM (tuiles en HTTP 200), visibles par-dessus Plan IGN.
   - Cause (lue dans le code osmdroid, cohérente avec le constat) : le fournisseur de tuiles du cadastre n'était pas relié au gestionnaire de rafraîchissement de la carte. Une tuile cadastre téléchargée ne redessinait donc jamais la carte ; par-dessus Plan IGN, l'arrivée des tuiles du fond (plus lentes) provoquait des rafraîchissements qui affichaient le cadastre au passage, alors que le fond OSM, déjà en cache, n'en provoquait plus. De plus, la couche était insérée une seule fois puis effacée à chaque reconstruction des surcouches.
   - Correctif : le fournisseur cadastre déclenche le rafraîchissement de la carte, et la couche est réinsérée juste au-dessus du fond à chaque reconstruction, quel que soit le fond (OSM, Plan IGN, Esri).
   - Vérification : nouveau repère `carte_cadastre_tuiles_dessinees`, présent seulement quand au moins une tuile cadastre a réellement été peinte. Le flow `scenario_p3_cadastre_ign` vérifie maintenant le cas « OSM standard » puis le cas « Plan IGN » (attente du repère, captures `cadastre_sur_osm` et `cadastre_sur_plan_ign`). Vert sur émulateur et parcelles visibles à l'œil sur la capture OSM (Toulouse, Capitole). Esri non couvert par le flow (proposé seulement après acceptation des conditions Esri).
2. **Session > carte > attribution du cadastre** : « Cadastre : IGN – … » chevauchait « largeur visible ». Les deux libellés sont maintenant empilés en bas à gauche (attribution au-dessus, puis « largeur visible », puis la ligne © OpenStreetMap), limités à la moitié gauche : ils ne recouvrent ni « Point à la croix », ni « Centrer / Suivre », ni « Fond : … », quel que soit le fond. Nouveau testTag `carte_largeur_visible`, vérifié visible dans le flow avec le cadastre actif.
3. **Journal de session au format D9** (`docs/FORMAT_ECHANGE_CAPTATION.md` §6) : pendant la session, chaque pose, échantillon IMU, fix GNSS et statut GNSS est écrit **directement** en trame JSONL D9 (`t_mono_ns`, `clock_offset_ns` = null, `seq`, `device_id`, `time_base`, `type`, `v`) dans le flux segmenté `captation` (segments fsyncés et réparés comme les autres, R2). `seq` repart de la dernière valeur écrite après un redémarrage, sans trou. Export : `raw/captation.jsonl` ajouté dans Session > Export > Exporter la session.
   - Compatibilité : les flux historiques restent écrits à l'identique et restent la source de la relecture, du réimport et de l'export ; une session antérieure (sans flux `captation`) se lit et s'exporte comme avant. Coût : environ 2× le volume des flux bruts.
   - Test JVM `CaptationJournalTest` : champs D9, types (`pose`, `imu.gyro`, `imu.rotation`, `gnss`), unités, `seq` 1→4 puis 5 après reprise sans en-tête, lecture historique inchangée. Tous les tests `core` passent (dont les 10 tests export/journal existants).

### Session > Synchro : non modifié (Sébastien tranchera) — gains attendus
- Mesure actuelle : 12 s d'écoute à chaque appui, puis environ 0,8 s de détection (fenêtre de 12 s à 48 kHz).
- **Raccourcir l'écoute** (ex. 3 s) : gain d'environ 9 s par appui, et détection environ 4× plus courte (de l'ordre de 0,2 s, proportionnelle à la fenêtre). Risque : rater le chirp de l'autre appareil s'il n'est pas émis dans la fenêtre ; il faudrait alors planifier les émissions.
- **Garder le micro et le haut-parleur ouverts** d'un appui à l'autre : gain limité au temps d'ouverture et d'initialisation (durées par phase écrites dans le journal ; quelques dizaines à quelques centaines de ms, à confirmer sur le journal du Pixel 7) ; ne raccourcit pas les 12 s. Coût : micro actif en permanence (batterie, indicateur de confidentialité Android).
- Les deux options se cumulent ; l'essentiel du gain vient de la longueur de la fenêtre.

### Reste à vérifier sur le Pixel 7
- Session > carte : cadastre par-dessus OSM standard et Esri World Imagery sur l'appareil réel.
- Taille des sessions avec le flux `captation` en plus.

Le reste de la P3 est reporté.
