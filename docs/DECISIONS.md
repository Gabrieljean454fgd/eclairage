# Décisions LumiSite — table unique

Une seule numérotation. **D1–D12** : document unique du 29/10 (`docs/PROPOSITIONS.md` § 0.2). Les décisions prises ensuite
dans les prompts v4–v6 (numérotées D13–D28 dans ces prompts et dans les commentaires du code) sont renommées **P-D13…P-D28**
pour ne plus se confondre avec D1–D12. **DS1–DS3** : décisions de Sébastien du 15/11 (`docs/PROPOSITIONS.md` § 17) ;
elles priment sur tout document antérieur. En cas de contradiction, la décision la plus récente s'applique.

## D1–D12 (document unique, 29/10)

| N° | Décision (résumé) | Source |
|---|---|---|
| D1 | L'appli terrain n'écrit pas de DXF ; elle exporte en GeoJSON. `dxfPreparation.status` reste `NOT_GENERATED`. | PROPOSITIONS § 0.2 ; confirmée par DS1 |
| D2 | Paquet GeoJSON : WGS84 (RFC 7946), coordonnées locales dans `properties.local`, manifeste avec sha256, photos en fichiers séparés. | PROPOSITIONS § 0.2, § 8 |
| D3 | Un outil de bureau local fusionne les appareils et produit DXF/DWG à partir des blocs du gabarit de Sébastien (calque `RES - Elec`). | PROPOSITIONS § 0.2, § 9 |
| D4 | Noms de blocs corrigés (`bloc secours`, `tableau divisionnaire`) + table d'alias pour lire les anciens. | PROPOSITIONS § 0.2 |
| D5 | Sélecteur terrain = liste fermée des familles, catalogue versionné (données, pas code). Codes `LS_*` : voir DS2. | PROPOSITIONS § 0.2, § 6.3 |
| D6 | Typologie courte + propriétés facultatives ; notes texte/vocales/photo ; transcription au bureau (Whisper). | PROPOSITIONS § 0.2 |
| D7 | Les 3 tailles de spot sont déduites au bureau du sous-type, pas saisies au terrain. | PROPOSITIONS § 0.2 |
| D8 | Géoréférencement Lambert 93 ; plan d'architecte recalé sur les données ; sans plan, fond OSM. | PROPOSITIONS § 0.2 |
| D9 | Zoom : sur-zoom au-delà de 21 accepté ; cache satellite limité à la zone du site. | PROPOSITIONS § 0.2 |
| D10 | Thème Système/Clair/Sombre, mode nuit rouge en option. | PROPOSITIONS § 0.2 |
| D11 | Analyse du comportement de Xodo 11.3.1 approuvée, sans copie de code. | PROPOSITIONS § 0.2 |
| D12 | Devin seul code ; les autres agents proposent. | PROPOSITIONS § 0.2 |

## P-D13… (prompts v4–v6, ex-D13…D28)

| N° | Décision (résumé) | Où dans le code / les docs |
|---|---|---|
| P-D13 | Reprise d'une session = nouveau segment AR : chaque pose et chaque objet utilisent la transformation de leur propre segment ; les points de l'ancien repère AR sont gardés (« session précédente »), jamais réutilisés en silence. | `core/compute/Compute.kt` (`TrajectoryCorrector`), `SessionController` |
| P-D14 | Export reprenable en flux (WorkManager, `exports/<id>/manifest.json`), jamais interrompu par « Terminer ». | `core/export/ResumableExport.kt`, `ExportWorker.kt` |
| P-D15 | Poids IMU : accéléromètre et gyroscope à 100 Hz (réglable), magnétomètre et rotation à 10 Hz ; segments fermés compressés en gzip hors fil principal. | `docs/COMPTE_RENDU_2026-11-02.md`, journal |
| P-D16 | Formats : `releves.geojson` = une FeatureCollection RFC 7946, `LISEZMOI.txt`, anciens formats toujours lisibles (aucune migration destructive). | `docs/GEOJSON_EXPORT.md`, `ResumableExport.kt` |
| P-D17…P-D28 | Numéros utilisés dans les prompts v4–v6 ; leur intitulé n'est pas repris dans le dépôt. À compléter par Sébastien (source : prompt et date) avant toute nouvelle référence. | — |
| P-Décision A (09/11) | Un point de calibrage contradictoire est refusé par défaut ; « Recaler quand même » l'enregistre après confirmation, sans rien effacer. | `SessionController`, flow `scenario_v6_recaler_quand_meme.yaml` |

## DS1–DS3 (Sébastien, 15/11)

| N° | Décision | Source |
|---|---|---|
| DS1 | Aucun DXF dans l'application (lot 5A annulé) ; conversion au bureau. Détecteur d'ellipses 5B (MIT) après le lot 4, expérimental, désactivé par défaut. | PROPOSITIONS § 17 |
| DS2 | L'appli stocke des codes `LS_*` ; `catalog/correspondance_dwg.json` (code → bloc DWG) n'est lu qu'au bureau ; anciens `block_name` traduits par alias à la lecture. | PROPOSITIONS § 17 |
| DS3 | Vue AR : caméra sur toute la largeur, infos en dessous, pas de mode agrandi ; seulement des traits (contours, arêtes verticales des murs, bords), drapeaux de calibrage et symbole du luminaire ; ni remplissage, ni nuage de points, ni texte dans l'image. | PROPOSITIONS § 17 |

## Glossaire (termes de l'interface)

| Terme | Sens |
|---|---|
| **Site** | Lieu relevé : nom, origine WGS84, rectangle(s), contours OSM facultatifs. Créé par Accueil > « Nouveau relevé » (écran « Nouveau site »). |
| **Session** | Une visite terrain sur un site : journal append-only (poses, capteurs, événements), interrompue et reprise sans perte. |
| **Relevé** | Un objet posé dans une session (luminaire, prise, BAES…) : famille/type, position, commentaire, photos et sons. |
| **Repère** | Point de calibrage : un point de la carte associé au point visé dans l'AR ; drapeau orange réservé. |
| **Annotation** | Dessin libre sur la carte (trait, polygone, texte, symbole) dans Session > Annoter ; distinct d'un relevé. |
| **Position du téléphone** | Pose ARCore de l'appareil. **Point visé** : intersection du réticule avec un plan détecté. |
