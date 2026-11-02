# Feuille de route : les 6 étapes du prompt v3

Référence complète : [`INSTRUCTIONS_DEVIN.md`](INSTRUCTIONS_DEVIN.md). Scénarios terrain pas à pas : [`TESTS_TABLETTE.md`](TESTS_TABLETTE.md).

**Statuts possibles :** à faire · en cours · livrée (PR ouverte, APK publié, en attente de l'essai de Sébastien) · validée par Sébastien.
Seul Sébastien fait passer une étape à « validée ». L'étape N+1 n'est ouverte à la validation qu'après la validation de l'étape N.

| Étape | Objet | Statut | PR / branche | APK |
|---|---|---|---|---|
| 1 | Protéger : je relève, je conserve, j'exporte, je récupère | à faire | `etape-1` | `lumisite-etape1-v<versionCode>-<hash>.apk` |
| 2 | Rendre utilisable : un petit relevé sans blocage | à faire | `etape-2` (brouillon tant que l'étape 1 n'est pas validée) | `lumisite-etape2-…` |
| 3 | Stabiliser : mode Capture continu, interruptions explicites | à faire (spécifications seulement) | — | — |
| 4 | Valider le métier : visée mur/plafond, photo, surcouche du bâti | à faire | — | — |
| 5 | Fermer la chaîne : session réelle, export, bureau, DXF | à faire | — | — |
| 6 | Étendre : multi-appareils longue durée, superposition AR complète | à faire | — | — |

Les statuts réels sont mis à jour à chaque compte rendu (`docs/COMPTE_RENDU_<date>.md`).

## Étape 1 — Protéger
Points : 1.1 keystore de test fixe et versionCode croissant · 1.2 stockage local en segments + copie vérifiée vers `Documents/LumiSite/<site>/<session>/` + réimport · 1.3 relecture des sessions 0.1 et 0.2 · 1.4 sauvegarde Android · 1.5 journal de diagnostic tournant + Partager le journal · 1.6 export en flux avec progression · 1.7 base de temps par démarrage du téléphone.

Scénario de validation 1 (Xiaomi 13T Pro) :
1. Installer N, créer un relevé avec quelques points, une photo et un export.
2. Installer N+1 par-dessus : tout est encore là.
3. Désinstaller, réinstaller, puis Réimporter : la session revient depuis `Documents/LumiSite`.
4. Partager le journal.

Statut au 01/11 (prompt v4, palier P1) : corrections Pixel 7 publiées (`etape1-a23e71d`, `lumisite-etape1-v113-a23e71d.apk`), parcours 1.1–1.5 verts en CI émulateur ; validation sur Pixel 7 physique / ARCore réel à faire. Détail : `docs/COMPTE_RENDU_2026-11-01.md`.

Réserve applicable : **R2** (segment en cours lors d'un arrêt brutal ; réimport après désinstallation testé sur le vrai téléphone), **R1** (arrêts et reprises écrits, jamais comblés).

## Étape 2 — Rendre utilisable
Points : 2.1 un doigt dessine, deux doigts déplacent · 2.2 polygone élastique fermé sur le dernier point · 2.3 note vocale conservée · 2.4 icônes avec infobulle · 2.5 portrait, 48 dp, WindowInsets, icône de thème visible · 2.6 bouton Créer actif, validation à l'appui · 2.7 rectangle OSM élastique · 2.8 flèche, trajectoire, source, calibration, âge de la position · 2.9 repère posé à la calibration · 2.10 décalage visible avant recalage.

Scénario de validation 2 (bureau de Sébastien, Toulouse) : créer un relevé avec sélection OSM ; calibrer ; marcher (la flèche et la trajectoire suivent) ; dessiner un polygone au pouce et le fermer sur le dernier point ; recaler ; exporter. Aucun blocage, textes lisibles en portrait.

Statut au 01/11 (prompt v4) : P1 publié (`etape2-ff0dd61`). P2 sur etape-2 : ergonomie Session (`etape2-e916bbc`), positionnement D1–D5/D7 (`etape2-2476563`), D6/D8 + version v0.3.0-alpha (`etape2-11925f3`), Session > Annoter sélection directe + déplacement (74badcd), photo automatique à la pose (8c0d702), Synchro audio toujours gardé + durées (c66c159) — pré-releases suivantes selon la CI, voir `docs/COMPTE_RENDU_2026-11-01.md`. P3 sur etape-3 (brouillon) : cadastre IGN D12 fait, spec captation D9 écrite et journal de session écrit au format D9 (flux `captation`, relance V4 n°2), cadastre visible par-dessus tous les fonds ; reste de la P3 reporté.

Réserve applicable : **R1** (perte de suivi visible : flèche grisée, âge de la position ; aucune trajectoire inventée pendant une perte).

## Étape 3 — Stabiliser
Points : 3.1 service de premier plan · 3.2 règles d'interruption par flux · 3.3 caméra qui disparaît · 3.4 Accueil > Capture (audio continu en segments) · 3.5 jauge mémoire et déchargement · 3.6 synchro sonore périodique · 3.7 appairage BLE, hub, signatures sonores, continuité.

Scénario de validation 3 : capture de 2 h sur 2 appareils avec photo, appel, écran éteint 10 min, pause ; journal complet ; audio complet en segments ; jauge et déchargement sans arrêt ; alignement chiffré ; 3 appareils, éloignement, perte du hub.

Réserves applicables : **R1**, **R2**, **R3** (mesurer la précision des signatures sonores : bruit, réverbération, distance, collisions).

## Étape 4 — Valider le métier
Points : 4.1 appui = photo + pose + point visé + source, jamais de repli silencieux à 50 cm · 4.2 deuxième visée et triangulation avec refus d'angle faible · 4.3 surcouche sol/mur/plafond sans mobilier.

Scénario de validation 4 : surcouche non piégée par les tables ; downlight visé deux fois ; position et hauteur comparées au mètre (écart chiffré) ; photo rattachée.

Réserves applicables : **R4** (poses exactes à chaque visée ; géométrie défavorable = refus ou forte incertitude), **R5** (exclusion du mobilier corrigeable à la main, jamais bloquante).

## Étape 5 — Fermer la chaîne
Points : 5.1 contrat unique appli ↔ bureau (schéma 0.2, champs lus par `tools/session2dxf` réellement écrits, test de contrat) · 5.2 session réelle → export → `session2dxf` → DXF sur gabarit synthétique.

Scénario de validation 5 : le DXF s'ouvre dans AutoCAD, objets aux bonnes positions sur le calque `RES - Elec`, avec leurs attributs.

## Étape 6 — Étendre
Points : 6.1 capture 5–6 appareils sur une journée puis plusieurs jours (dérive, recalage, volumes) · 6.2 superposition AR 3D du contour recalé · 6.3 préparation du portage iPhone/iPad.

## Réserves de l'analyse externe (rappel)
- **R1.** Continuité ≠ absence d'interruption : coupures et reprises signalées, jamais comblées par une trajectoire inventée.
- **R2.** Segment en cours lors d'un arrêt brutal : définir et tester ce qui est récupéré (fsync régulier, petits segments, réparation au redémarrage) ; réimport après désinstallation testé sur le vrai téléphone.
- **R3.** Signatures sonores = hypothèse : mesurer leur précision (bruit, réverbération, distance, collisions).
- **R4.** Triangulation avec les poses exactes de chaque visée ; géométrie défavorable = refus ou forte incertitude.
- **R5.** Exclusion automatique du mobilier toujours corrigeable à la main, jamais bloquante.

## Écarts entre les libellés du prompt v3 et les libellés réels (état de la tête de la PR #14)
Le prompt dit lui-même que ses libellés sont indicatifs. Écarts constatés dans le code au 31/10 :

| Libellé du prompt v3 | Libellé réellement affiché |
|---|---|
| Accueil > Nouveau relevé | Accueil > « Nouveau » (crée un site), puis Site > « Nouvelle session terrain » |
| Accueil > Capture | n'existe pas (étape 3) ; la synchro est dans Session > « Synchro » |
| Session > Exporter | Session > « Export » > « Exporter la session » |
| Session > Calibration > Calibrer | Session > « Calibrer » > « Valider la calibration » |
| Session > Relever | Session > « Relevé » > « Enregistrer le relevé » |
| Session > Carte | pas d'onglet : la carte est toujours affichée dans l'écran Session |
| Réglages > Stockage, Réglages > Journal | aucun écran Réglages avant l'étape 1 |
