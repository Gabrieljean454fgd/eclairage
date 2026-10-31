# Instructions de travail pour Devin (référence : prompt v3 du 31/10/2026)

Ce fichier contient le prompt v3 intégral (`PROMPT_DEVIN_V3_ETAPES_2026-10-31.md`), suivi des ajouts du 31/10/2026 (réserves R1 à R5 de l'analyse externe, tâches d'outillage). Le suivi des étapes est dans [`ROADMAP_ETAPES.md`](ROADMAP_ETAPES.md). Le document de propositions historique reste [`PROPOSITIONS.md`](PROPOSITIONS.md).

Le dépôt est **public** : aucun fichier client, aucun secret. Devin ne fusionne rien et ne tranche aucune question ouverte : il la note dans son compte rendu.

---

## Prompt v3 (texte intégral)

Contexte : dépôt Gabrieljean454fgd/eclairage (public : aucun fichier client, aucun secret). Ce prompt remplace le prompt « essai terrain v2 » du 30/10. Il reprend tous ses points, mais découpés en 6 ÉTAPES validées une par une sur le Xiaomi 13T Pro. Base de travail : la tête de la PR #14 (qui inclut les corrections AR de #15). Ne fusionne rien et ne tranche aucune question ouverte.

### RÈGLE DE PROGRESSION (nouvelle, impérative)
- Une étape = une PR (empilée sur la précédente), un commit identifiable et un APK identifiable (versionCode croissant, nom de fichier avec numéro d'étape et hash court du commit).
- Ne commence pas l'étape N+1 tant que Sébastien n'a pas validé le scénario terrain de l'étape N. Arrête-toi après chaque étape et rends ton compte rendu.
- « Build vert 3 fois » ne prouve que la stabilité du build, pas que la fonction marche. La preuve attendue, c'est le scénario terrain écrit dans docs/TESTS_TABLETTE.md, reproductible pas à pas, avec le résultat attendu.
- Tests automatiques : pour chaque scénario de validation, livre aussi un parcours Maestro (fichier YAML dans .maestro/, en libellés réels) qui rejoue la partie « clics » du scénario sur le Pixel 7 de développement. Ajoute les identifiants de test nécessaires dans l'interface. Ces parcours complètent l'essai terrain de Sébastien, ils ne le remplacent pas.
- Une interruption signalée est acceptable. Une fausse continuité ne l'est pas : tout arrêt ou toute reprise d'un flux doit être écrit dans le journal de session.

### RÈGLE DE COMMUNICATION
Dans chaque PR, chaque commit et chaque message, nomme toujours l'écran, le menu et le sous-menu, sous la forme « Écran > Menu > Sous-menu », avec les libellés réellement affichés. Si un libellé change, donne l'ancien et le nouveau. Les libellés cités ici sont indicatifs : utilise les vrais et signale les écarts.

### NOMS DES DEUX MODES (décision de Sébastien)
- « Relevé » : la saisie et l'implantation (plan, annotations, objets, visée). Sur l'écran Accueil, bouton « Nouveau relevé ».
- « Capture » : l'appareil enregistre en continu ses trames de données (audio, vidéo, photos, IMU, GPS, pose AR, scan 3D si le matériel en a un, comme le LiDAR de l'iPhone Pro) pour les synchroniser au bureau. Sur l'écran Accueil, bouton « Capture ». Il remplace l'ancien bouton « Synchro » : la synchro fait partie de la Capture. La Capture peut tourner sur l'appareil principal pendant un Relevé, ou seule sur un appareil secondaire.
- Hypothèses de volume : une session dure de 1 h à plusieurs jours (par ex. 8 h–12 h puis 14 h–18 h, et parfois plusieurs jours sur un même projet), avec 5 à 6 appareils qui capturent en même temps.

### ÉTAPE 1 — PROTÉGER : je relève, je conserve, j'exporte, je récupère
1.1 Signature et mise à jour. Ajoute un keystore de TEST fixe et commun (signingConfigs debug), documenté comme clé de test publique, jamais utilisable en production. versionCode croît à chaque APK, et applicationId ne change pas. Rien n'est à garder sur le téléphone actuel : on désinstalle une dernière fois le lot13. Dans docs/, ajoute une procédure de vérification : afficher le certificat d'un APK (apksigner verify --print-certs) et lire l'erreur d'installation Android en cas d'échec. Ainsi, on confirme la cause au lieu de la supposer.

1.2 Stockage (décision de Sébastien : écriture locale fiable + copie continue). L'acquisition écrit d'abord dans le stockage local de l'appli, de façon atomique et en segments. Chaque segment terminé est copié au fil de l'eau dans un dossier visible qui survit à la désinstallation : Documents/LumiSite/<site>/<session>/ (via MediaStore, sans dépendre d'une autorisation qu'il faudrait redonner après réinstallation). La copie est vérifiée par empreinte. Le chemin s'affiche dans Réglages > Stockage. Une option « Réimporter une session depuis Documents/LumiSite » relit une session copiée après une réinstallation.

1.3 Compatibilité. L'importeur relit les sessions 0.1 et 0.2. Test JVM : une session 0.1 est relue par la nouvelle version.

1.4 Sauvegarde Android (décision de Sébastien : tout peut partir dans la sauvegarde cloud, médias clients compris). Configure allowBackup et dataExtractionRules en conséquence. C'est un filet complémentaire, pas la sauvegarde principale : documente ses limites de taille.

1.5 Journal de diagnostic. C'est un fichier texte tournant dans Documents/LumiSite/_journal/. Il note l'horodatage et l'Écran > Menu > Sous-menu actif, ainsi que les erreurs caméra et ARCore, l'export, la synchro, la mémoire et les arrêts et reprises de flux. Ajoute un bouton Réglages > Journal > Partager le journal.

1.6 Session > Exporter (inopérant : le bouton reste grisé, sans message).
- L'export se fait EN FLUX : aucune série IMU ni aucun média entier n'est chargé en mémoire.
- Il affiche une progression réelle.
- Il écrit dans le dossier visible.
- À la fin, il affiche « Exporté dans <chemin> » avec les boutons Ouvrir et Partager.
- En cas d'échec, il affiche une erreur claire, aussi écrite dans le journal.

Test : export d'une session synthétique de 4 h avec IMU et médias, sans dépassement mémoire.

1.7 Ordre temporel après redémarrage. Les événements ne sont jamais triés sur la seule horloge monotone : chaque événement porte sa base de temps (identifiant de démarrage du téléphone). Test JVM : une session à cheval sur un redémarrage du téléphone garde un ordre et une trajectoire cohérents.

SCÉNARIO DE VALIDATION 1 (sur le Xiaomi)
- Installer N, créer un relevé avec quelques points, une photo et un export.
- Installer N+1 par-dessus : tout est encore là.
- Désinstaller, réinstaller, puis faire Réimporter : la session revient depuis Documents/LumiSite.
- Partager le journal.

### ÉTAPE 2 — RENDRE UTILISABLE : un petit relevé sans blocage
2.1 Session > Annoter : dessin impossible au pouce. En mode dessin, un doigt dessine et deux doigts déplacent la carte.

2.2 Session > Annoter > Polygone : tracé élastique. Chaque appui pose un sommet, et un aperçu fermé suit en temps réel. On ferme en touchant à nouveau le dernier point posé. Pas de bouton « Fermer », pas de double-tap.

2.3 Session > Annoter > Note vocale : on la GARDE pour l'instant (décision de Sébastien). Elle ne sera retirée qu'à l'étape 3, une fois l'audio continu validé et quand on sait retrouver le passage audio d'une annotation.

2.4 Barre d'outils de Session > Annoter : des icônes parlantes avec infobulle à la place des textes longs.

2.5 Ergonomie en portrait sur téléphone, sur tous les écrans.
- Réorganise les commandes plutôt que de tout réduire : des libellés courts, des icônes, des zones tactiles d'au moins 48 dp, et un texte qui reste lisible sur le terrain.
- Respecte les marges système et l'encoche (WindowInsets).
- L'icône du thème jour/nuit, en haut à droite, déborde de l'écran : rends-la entièrement visible. Le menu de thème reste tel quel.

2.6 Accueil > Nouveau relevé > Créer : laisse le bouton actif et valide au moment de l'appui. Le message dit ce qui manque et met le champ en évidence. Un bouton désactivé ne peut rien expliquer.

2.7 Accueil > Nouveau relevé > Sélection OSM : rectangle élastique (appuyer, maintenir, tirer, avec un dessin en temps réel). Si le rectangle a déjà sélectionné des bâtiments, ne redemande pas de les sélectionner : passe à la suite, avec la possibilité d'en ajouter ou d'en retirer.

2.8 Position en temps réel dans Session > Carte.
- Elle affiche une flèche (position et orientation) et la trajectoire, mises à jour en continu.
- Elle affiche TOUJOURS la source (AR, GPS ou manuelle), l'état de calibration, une perte de suivi visible (flèche grisée) et l'âge de la dernière position valide.

2.9 Session > Calibration > Calibrer : au moment de l'appui, un repère visible se pose sur le plan, avec la position du téléphone et la direction de la caméra.

2.10 Session > Recaler : le décalage à corriger est visible avant de valider.

SCÉNARIO DE VALIDATION 2
Dans le bureau de Sébastien (Toulouse, avec le cadastre) :
- créer un relevé avec Sélection OSM ;
- calibrer ;
- marcher : la flèche et la trajectoire suivent ;
- dessiner un polygone au pouce et le fermer sur le dernier point ;
- recaler ;
- exporter.

Il ne doit y avoir aucun blocage, et tous les textes doivent être lisibles en portrait.

### ÉTAPE 3 — STABILISER : mode Capture continu et interruptions explicites
3.1 Moteur de session indépendant des écrans. L'acquisition tourne dans un service de premier plan (foreground service avec notification), démarré et arrêté VOLONTAIREMENT. L'écran affiche et commande l'acquisition ; il ne conditionne pas son existence.

3.2 Pour chaque flux, documente et implémente ce qui continue et ce qui s'arrête lors de chaque interruption (photo, appel, pause, écran éteint, caméra prise par une autre appli, redémarrage). Audio, GPS et capteurs continuent dans le service. Caméra et ARCore s'arrêtent quand Android l'impose : l'arrêt et la reprise sont alors écrits dans le journal de session, avec une relocalisation à la reprise.

3.3 Cause de la caméra qui disparaît en cours de session (vue AR, retour seulement en relançant la session) : trouve-la avec le journal de 1.5 et corrige-la.

3.4 Accueil > Capture.
- Audio continu, avec un indicateur d'enregistrement visible et des segments récupérables même après un crash, un timecode dans chaque trame, puis vidéo, photos, IMU, GPS et pose AR selon le matériel.
- Supprime la case « garder l'enregistrement WAV ».
- Une fois l'audio continu validé, retire Session > Annoter > Note vocale. Chaque annotation garde alors un lien vers son instant audio.

3.5 Jauge mémoire dans Accueil > Capture et dans la session.
- Elle affiche l'espace libre et le temps de capture restant estimé selon les flux actifs, et alerte avant saturation.
- Elle propose de décharger les segments terminés vers une carte SD, une clé USB-C ou un disque externe (Storage Access Framework), puis de libérer l'espace local après vérification par empreinte, SANS arrêter la capture.

3.6 Synchronisation multi-appareils (5 à 6 appareils).
- Enregistrer en continu ne suffit pas à synchroniser.
- L'appareil principal émet des signaux sonores de synchro (chirp ou FSK portant un identifiant et un numéro de séquence) au démarrage, puis à intervalles réguliers, et à chaque reprise après une interruption ou un redémarrage.
- Chaque appareil enregistre les instants de détection.
- Le bureau en déduit, par appareil, le décalage, la dérive et l'incertitude, ainsi que leurs périodes de validité.
- L'intervalle de recalage doit être un paramètre, mesuré en essai, pas une valeur figée.
- Le décalage d'horloge exporté ne doit plus être vide.

3.7 Appairage et liaison entre appareils (pré-étape de toute session, en Relevé comme en Capture).
Principe : la radio sert à se reconnaître et à échanger de petits messages d'état ; le son sert au calage temporel précis ; chaque appareil enregistre TOUJOURS tout en local, et n'a jamais besoin de la liaison pour capturer.

a) Écran « Appareils à proximité », ouvert au démarrage depuis Accueil > Nouveau relevé et depuis Accueil > Capture :
- il détecte les appareils LumiSite proches par Bluetooth basse consommation (BLE), qui marche aussi avec l'iPhone et l'iPad plus tard, et liste ceux déjà en Capture ;
- il propose « Synchroniser ces appareils ? » ;
- à la validation, il crée un identifiant de session commun. Un QR code affiché par l'appareil principal sert de secours si la radio échoue.

b) Hub : l'appareil qui lance la session est le hub. Il affiche, pour chaque appareil :
- son nom ;
- sa batterie ;
- sa mémoire restante ;
- l'heure du dernier contact ;
- son décalage estimé ;
- ses flux actifs.

Le hub alerte si un appareil n'est plus entendu. Les messages passent par BLE, ou par le Wi-Fi local s'il y en a un (le partage de connexion du hub convient). Ils ne passent PAS par Internet, et AUCUNE donnée lourde ne transite : tout est fusionné au bureau.

c) Son : chaque appareil émet périodiquement sa propre signature sonore (chirp ou FSK court portant son identifiant et un numéro de séquence), à des instants décalés pour éviter les collisions. Chaque appareil note les signatures qu'il entend. Le bureau aligne ainsi toutes les paires d'appareils, sans dépendre d'un seul émetteur. Le son ne sert pas à transporter des données (son débit est trop faible), seulement à se signaler et à dater.

d) Continuité :
- un appareil hors de portée continue sa capture et ses signatures ;
- il rejoint automatiquement la session quand il revient ;
- si le hub disparaît, un autre appareil reprend le rôle de hub ;
- toutes les pertes et reprises de liaison sont écrites dans le journal de session.

Test JVM : la fusion au bureau de 3 journaux simulés, avec des signatures croisées, une coupure de liaison et la perte du hub, donne un alignement cohérent et une incertitude par appareil.

SCÉNARIO DE VALIDATION 3
- Capture de 2 h sur 2 appareils, avec photo, appel entrant, écran éteint 10 min et pause.
- Le journal de session montre chaque arrêt et chaque reprise.
- L'audio est complet en segments.
- La jauge alerte, et un déchargement vers un support externe se fait sans arrêter la capture.
- Le bureau aligne les 2 appareils et donne une incertitude chiffrée.
- Sur 3 appareils, l'écran « Appareils à proximité » les trouve tous. On en éloigne un 10 minutes : le hub l'indique, puis il revient seul. On éteint le hub : un autre prend le relais, et la capture continue partout.

### ÉTAPE 4 — VALIDER LE MÉTIER : visée mur et plafond, photo, surcouche du bâti
4.1 Appui sur un luminaire dans la vue caméra (Session > Relever).
- L'appui prend AUTOMATIQUEMENT une photo, pour savoir de quoi il s'agit.
- Il enregistre la position et l'orientation de la caméra, le point visé et sa source : plan détecté, profondeur ou triangulation.
- Une direction seule ne donne pas de distance. Le repli « 50 cm devant le téléphone » ne doit JAMAIS devenir silencieusement la position d'un objet : sans géométrie, l'objet est marqué « position à compléter ».

4.2 Hauteur des luminaires au plafond (décision de Sébastien : deux visées depuis deux endroits différents).
- Session > Relever > Viser à nouveau : l'appli guide une seconde visée du même objet depuis un autre point.
- Elle calcule la position 3D par triangulation, avec l'angle entre les deux visées et l'incertitude, et refuse un angle trop faible.
- Quand ARCore détecte un plan de plafond ou fournit une profondeur fiable, elle l'utilise comme estimation de départ.

4.3 Surcouche du bâti dans la vue AR (idée à laquelle Sébastien tient).
- Des traits simples et légers en surimpression de la vidéo, montrant uniquement les limites principales : sol/mur, mur/plafond et mur/mur.
- EXCLURE le mobilier : tables, ordinateurs, armoires. Les plans horizontaux à hauteur de table ne sont pas le sol.
- Approche attendue, à documenter :
  - Le sol est le grand plan horizontal le plus bas.
  - Le plafond est le plan horizontal le plus haut, ou il est déduit des visées.
  - Les murs sont les plans verticaux qui touchent le sol et dépassent la hauteur du mobilier.
  - Une segmentation par apprentissage (mur, sol, plafond, mobilier) est une option à évaluer, pas un prérequis.
- Si un contour connu (polygone de la pièce) est recalé, il est superposé aussi.
- Dis honnêtement ce qui marche sur le Xiaomi, qui n'a pas de capteur de profondeur matériel, et ce qui demanderait un LiDAR.

SCÉNARIO DE VALIDATION 4
Dans le bureau encombré de Sébastien :
- La surcouche montre les limites sol/mur/plafond sans se faire piéger par les tables.
- Un downlight au plafond est visé deux fois.
- Sa position et sa hauteur se comparent à une mesure au mètre, avec l'écart chiffré.
- Sa photo est rattachée.

### ÉTAPE 5 — FERMER LA CHAÎNE : session réelle, export, bureau, DXF
5.1 Contrat unique entre l'appli et le bureau.
- Un seul format d'export, avec un schéma publié à la version réellement écrite (0.2).
- Tous les champs que tools/session2dxf lit sont réellement écrits : visée, hauteur, incertitude σ, décalage d'horloge, pose des photos, piste du locuteur et points de contrôle.
- Un test vérifie que l'appli et le bureau sont d'accord sur ce contrat.

5.2 Session réelle (étape 4) → export → session2dxf → DXF avec les blocs du gabarit de Sébastien (testé sur un gabarit SYNTHÉTIQUE dans le dépôt, avec des attributs invisibles compatibles AutoCAD R2000).

SCÉNARIO DE VALIDATION 5
Sébastien ouvre le DXF dans AutoCAD : les objets relevés sont aux bonnes positions, sur le calque RES - Elec, avec leurs attributs.

### ÉTAPE 6 — ÉTENDRE : multi-appareils longue durée, superposition AR complète
6.1 Capture sur 5 à 6 appareils pendant une journée (8 h–12 h et 14 h–18 h) puis sur plusieurs jours. Mesure la dérive, la fréquence de recalage nécessaire et les volumes par appareil et par heure.

6.2 Superposition AR complète du contour recalé, en 3D, avec la précision mesurée.

6.3 Préparer le portage iPhone/iPad (LiDAR pour le scan 3D en Capture), sans le coder maintenant.

### COMPTE RENDU APRÈS CHAQUE ÉTAPE
Pour chaque point, donne :
- l'Écran > Menu > Sous-menu ;
- le statut (fait, partiel ou non fait) ;
- la PR, le commit et le nom de l'APK ;
- les tests JVM ajoutés ;
- le scénario terrain à exécuter et ce qui reste incertain.

Puis ARRÊTE-TOI et attends la validation de Sébastien.

---

## Ajouts du 31/10/2026 (consignes de Jean)

### Règle de communication
Nommer toujours « Écran > Menu > Sous-menu » avec les libellés réels.

### Réserves de l'analyse externe, à respecter
- **R1.** Continuité ne veut pas dire absence d'interruption. Les coupures et les reprises sont signalées, jamais comblées par une trajectoire inventée.
- **R2.** Le segment en cours lors d'un arrêt brutal : définir et tester ce qui en est récupéré (fsync régulier, petite taille de segment, réparation au redémarrage). Tester aussi le réimport après désinstallation, sur le vrai téléphone.
- **R3.** Les signatures sonores sont une hypothèse. Prévoir la mesure de leur précision (bruit, réverbération, distance, collisions), pas seulement leur émission.
- **R4.** La triangulation (étape 4) utilise les poses exactes du téléphone au moment de chaque visée. Une géométrie défavorable donne un refus ou une forte incertitude.
- **R5.** L'exclusion automatique du mobilier doit toujours pouvoir être corrigée à la main, et elle ne bloque jamais le parcours de base.

### APK automatique
Un workflow GitHub Actions, à chaque push sur une branche d'étape (`etape-*`) et à la demande :
- lance `./gradlew :core:test :app:assembleDebug` ;
- signe l'APK avec le keystore de TEST fixe (étape 1.1) ;
- publie une pré-release GitHub nommée `etape<N>-<hash court>`, avec l'APK nommé `lumisite-etape<N>-v<versionCode>-<hash>.apk`, et dans sa description la liste des points livrés.

versionCode croissant (dérivé du nombre de commits) ; applicationId inchangé (`app.lumisite.android`).

### Testabilité (tests automatiques sur un Pixel 7)
- `testTag` sur les éléments interactifs de tous les écrans, avec `testTagsAsResourceId = true`, nommés d'après les écrans (par ex. `accueil_nouveau_releve`, `session_exporter`).
- Un parcours Maestro par scénario de validation : fichiers YAML à la **racine** du dossier `.maestro/` (Maestro ne lit pas les sous-dossiers). Ils couvrent la partie « clics » : création d'un relevé, Session > Annoter, Session > Exporter, réimport. Aucun exemple factice.
- Un mode debug de relecture ARCore : enregistrement via `RecordingConfig` / `setMp4DatasetUri` ; relecture via `setPlaybackDatasetUri` avant `resume()`, déclenchée par un extra de lancement `--es arcore_playback <chemin>` ; un indicateur « PLAYBACK » visible à l'écran.

### Étape 2 préparée sans être ouverte à la validation
Si l'étape 1 est finie : coder l'étape 2 dans une PR `etape-2` empilée sur `etape-1`, en **brouillon**, intitulée « en attente de validation de l'étape 1 ». Ne pas commencer l'étape 3, mais en écrire les spécifications dans `docs/` (service de capture et règles d'interruption par flux, format des segments, protocole d'appairage BLE/hub et des signatures sonores, méthode de mesure de la précision de synchro) avec les tests JVM qui en découlent.

### Compte rendu
À écrire aussi dans `docs/COMPTE_RENDU_<date>.md`. Pour chaque point : Écran > Menu > Sous-menu ; statut ; PR, commit et nom de l'APK publié ; tests JVM ; parcours Maestro correspondant ; ce qui reste à vérifier sur le Xiaomi 13T Pro ; questions ouvertes.
