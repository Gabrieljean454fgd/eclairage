# Compte rendu v176a-bis — Nouveau site : rectangles multiples (16/11/2026)

Branche `etape-3-v176a-bis-rectangles`, empilée sur `etape-3-v176b-fluidite`. PR : à compléter (non fusionnée).

## Fait
- **Accueil > Nouveau relevé > « Tracer rectangle »** : bouton bascule, orange avec le libellé « Tracer rectangle : actif » quand le mode est actif.
- **Mode actif** : un glisser à un doigt trace un nouveau rectangle (plusieurs autorisés). Toucher un rectangle le sélectionne : trait plein bleu et 8 poignées (4 coins, 4 côtés). Glisser un coin ou un côté l'étire, glisser l'intérieur le déplace. « Supprimer » retire le rectangle sélectionné. Deux doigts déplacent ou zooment toujours la carte.
- **Mode inactif** : pan/zoom normaux de la carte, aucun rectangle ajouté.
- **Créer le site** : le site garde la liste des rectangles (`Site.rects`, nouveau champ, vide pour les anciens sites). `Site.bbox` reste l'enveloppe des rectangles. Les bâtiments OSM présélectionnés sont ceux de l'union des rectangles (`OsmSelection.buildingsIn(rects, …)`), pas ceux de l'enveloppe. La requête OSM porte sur l'enveloppe (limites de l'API inchangées).
- **Site** : la carte affiche les rectangles du site quand il y en a plusieurs.
- **Logique pure** dans `core/osm/SiteRects.kt` (touche coin/côté/intérieur avec une tolérance en mètres, étirement et déplacement, enveloppe, poignées). Comportement inspiré de la sélection de zone de JOSM, sans code (JOSM est GPL).
- **Tests JVM** : `SiteRectsTest`, 3 tests. La suite `:core:test` passe : 175 tests.
- **Flow Maestro** `v176_nouveau_site_rectangle` : tracer 2 rectangles, désactiver, déplacer la carte (toujours 2 rectangles), réactiver, tracer un 3e, l'étirer par un coin (la taille change), le déplacer (la taille ne change pas), le supprimer, créer le site. Vérifié sur l'émulateur : le `site-….json` créé contient 2 rectangles et une `bbox` égale à leur enveloppe.

## Captures (émulateur Pixel 7 API 34)
- `docs/livraisons/v176a-bis/v176_rectangle_deux.png` : deux rectangles tracés.
- `docs/livraisons/v176a-bis/v176_rectangle_etire.png` : 3e rectangle sélectionné, poignées, après étirement.
- `docs/livraisons/v176a-bis/v176_rectangle_site.png` : écran Site, les 2 rectangles et les bâtiments de leur union.

## Limites
- Maestro ne sait pas faire un geste à deux doigts : le zoom n'est pas couvert par le flow, seul le déplacement à un doigt hors mode l'est. Le zoom à deux doigts est à vérifier au téléphone.

## CI et pré-release
- PR : https://github.com/Gabrieljean454fgd/eclairage/pull/25 (non fusionnée).
- CI verte : https://github.com/Gabrieljean454fgd/eclairage/actions/runs/43968308172 (APK, tests JVM, tous les flows Maestro dont `v176_nouveau_site_rectangle`).
- Pré-release : https://github.com/Gabrieljean454fgd/eclairage/releases/tag/etape3-v176a-bis-46707f1, APK `lumisite-etape3-v176a-bis-v189-46707f1.apk`, versionCode 189.
- Revue automatique : sélection de bâtiments vidée quand les rectangles changent (corrigé) ; rectangles très éloignés : « Réduire » ne ramène pas l'enveloppe sous la limite OSM (signalé, à arbitrer : récupérer chaque rectangle séparément).
