# Compte rendu — v176i : surimpression AR appuyée sur les données du site (23/11/2026)

Branche `etape-3-v176i-ar-donnees-site`, empilée sur `etape-3-v176h-synchro-esclave-sauvegarde` (PR #30). Accueil : « Build N · v176i ». Pas de fusion.

Retour terrain du build 246 : « l'AR n'est toujours pas conforme et ça ne tient pas compte [de] mes données ».

## Ce qui change

| Demande | Réponse |
|---|---|
| Symboles catalogue des luminaires du Relevé | Chaque luminaire de la visite, à son ancre ARCore, est dessiné avec son symbole d'implantation (`PlanSymbols`, même glyphe que le sélecteur et la carte), couché à plat, couleur de la famille (gris si l'ancre n'est plus suivie). |
| Drapeaux / repères de toutes les visites | Repères de la visite : drapeaux ancrés (inchangé). Repères et luminaires des **autres visites du site** : dessinés sur le sol depuis le plan (transformation carte → AR du calibrage en cours), repères en orange clair, luminaires avec leur symbole. |
| Contours du site | Rectangles du Nouveau site et bâtiments OSM choisis, en cyan, **au sol** (aucun mur extrudé, aucune hauteur inventée). |
| Ne pas confondre une table avec le sol | La couche plan est posée sur le sol détecté (`floorY` = plan horizontal le plus bas, mobilier 0,3–2,0 m écarté, v176g § 2e). Sans sol détecté, rien n'est posé et l'écran le dit. |
| Aligner les traits AR de la mesure A–B | Le segment A–B est dessiné dans l'image entre les deux points visés (A → point visé en direct pendant la visée de B), croix aux extrémités. |
| Ne pas inventer de murs | Seuls sont dessinés : plans suivis / arêtes d'intersection (v176g), ancres, et la couche plan au sol. Rien avant un calibrage. |

État lisible sous la caméra : `session_ar_plan_etat`, par exemple « Plan : 12 luminaire(s) · 5 repère(s) (3 d'autres visites) · contours 18 trait(s) », ou « calibrez… », ou « visez le sol… (aucun sol détecté) ». Bouton `session_ar_plan` pour masquer ou afficher le plan. Les éléments à plus de 60 m de l'origine du calibrage sont ignorés.

## Tests

- JVM : `ArSiteOverlayTest` (inverse de la transformation de calibrage, glyphe à plat et à la bonne taille, éléments de la visite et lointains écartés, contours depuis les rectangles, état jamais « plan posé » sans calibrage ni sol).
- Maestro (émulateur, AR simulée) : `v176i_ar_plan_site.yaml`, `b199_ar_menus_stables.yaml`, `v176_ar_contours.yaml`.

## Reste manuel (Pixel 7, bureau éclairé)

1. Site avec une visite précédente (repères + luminaires) : nouvelle visite, calibrer sur 2 repères. Les anciens repères (orange clair) et les symboles apparaissent au sol à leur place (± 0,5 m).
2. Viser une table : le plan reste au sol, pas sur la table.
3. Mesure A–B sur deux angles de carrelage : le trait jaune suit les deux points.
4. Contours cyan alignés sur les murs réels après calibrage.
