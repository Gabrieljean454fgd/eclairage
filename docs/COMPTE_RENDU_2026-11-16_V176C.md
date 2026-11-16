# Compte rendu v176c — AR dépouillée (décision 3)

Branche `etape-3-v176c-ar-depouillee`, empilée sur `etape-3-v176a-bis-rectangles` (PR vers cette branche, non fusionnée).

## Fait
- **Caméra pleine largeur** : cadre 4:3 sur toute la largeur en portrait ; visée, plans, contours, torche et mesure **en dessous**. Panneau ouvert (Calibrer, Relevé…) : même largeur, cadre recadré 2:1 pour laisser la place au panneau et à la carte. Plus aucun texte dans l'image (l'état du calibrage passe sous la caméra) ; seul le réticule reste dessiné.
- **Traits seuls** : `HORIZONTAL_AND_VERTICAL` ; contours des plans en `GL_LINE_LOOP`, arêtes verticales des murs, sans remplissage ni nuage de points. Interrupteur « Contours » et compteurs « Plans : sol · murs · plafond ».
- **Ancres locales** (`Session.createAnchor`) : drapeau orange à chaque point de calibrage/recalage, symbole du luminaire (cercle + croix) à chaque relevé visé ; dessinés seulement quand l'ancre est suivie (`TRACKING`), donc ils réapparaissent quand on revient vers eux.
- **Visée** : sol, mur (`VERTICAL`) et plafond (`HORIZONTAL_DOWNWARD_FACING`), plan le plus proche d'abord ; choix « Tous / Sol / Mur / Plafond » au relevé. **Plus de repli à 50 cm** : sans plan visé, « Position à compléter » et le relevé est refusé (toucher la carte reste possible).
- **Calibrage sur le point visé au sol** (réticule), plus sur la pose du téléphone ; point visé journalisé (`aimSession`, `aimSource`). Poses simulées (debug/CI) : pose du téléphone, comme avant.
- **Résidu par point** dans le tableau ; **5e point = contrôle indépendant** (hors calcul, écart affiché) ; alerte au-delà de 0,5 m ; qualité alignée sur le résidu de chaque point et du contrôle.
- `NOTICE` : attribution du sample ARCore `hello_ar_java` (Apache-2.0), comportement réimplémenté ; pas de SceneView/Filament.

## Tests
- JVM : `AimTest` (plafond, mur, plus proche, « à compléter »), `ArGeometryTest` (projection monde→écran, arêtes verticales, drapeau, symbole), `CalibrationControlTest` (5e point, qualité/résidu). Suite core : 182 tests.
- Maestro : `v176_ar_pleine_largeur`, `v176_ar_contours` + flows existants de Session (Annoter, calibrage multipoints, recaler quand même, même point carte, reprise, relevé sans blocage).

## Captures (émulateur, bloc caméra simulé)
![Caméra pleine largeur](livraisons/v176c/v176_ar_pleine_largeur.png)
![Contours](livraisons/v176c/v176_ar_contours.png)

## Reste / à vérifier au téléphone
- Le rendu réel (traits, arêtes, drapeaux, symboles, réapparition des ancres) n'est pas testable sur émulateur (pas d'ARCore) : à vérifier sur Pixel 7.
- La carte de Session est plus petite en portrait (caméra prioritaire, décision 3).

## CI / pré-release
À compléter.
