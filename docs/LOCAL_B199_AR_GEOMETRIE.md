# Retours build 199 — points 2d et 2e : arêtes de la pièce en AR, mobilier ignoré à la visée

Branche `petit/ar-geometrie` (base `petit/inclinaison` : `97b2ae6` + `4872f76` + `65ba9a1`). Écrans concernés :
**Session** (bloc caméra AR) et **Réglages** (nouvelle section Diagnostic).

## Fait

### Point 2d — Session : les traits sont les intersections de plans

Avant : les traits blancs étaient les polygones ARCore bruts (enveloppes de points détectés), pas les arêtes
de la pièce.

- `core/compute/ArGeometry.kt` (pur, tests JVM) :
  - `intersectPlanes(a, b)` : droite d'intersection de deux plans (point + direction), rejet si les normales
    font moins de **10°** (`PARALLEL_MIN_DEG`) — en dessous la droite tremble trop pour être tracée ;
  - `clipToPolygon(o, d, poly, n)` : portion d'une droite `o + t·d` à l'intérieur d'un polygone convexe
    (intervalle en t ; les polygones ARCore sont convexes, les normales de bord sont orientées vers le
    centroïde donc le sens des sommets importe peu) ;
  - `edgeSegment(eqA, polyA, eqB, polyB)` : segment d'arête borné aux **deux** polygones
    (plinthe sol∩mur, angle mur∩mur, mur∩plafond), rejeté s'il fait moins de 2 cm (`MIN_EDGE_M`) ;
  - `polygonAreaM2` : aire du polygone ARCore (lacet sur les paires x,z locales), sert au filtre 2e ;
  - `V3.cross` ajouté (dans `Aim.kt`).
- `ArTracker.kt` (`LineRenderer.draw`) : pour chaque paire de plans détectés, l'arête est calculée et
  dessinée ; couleur du **plan le plus haut** (plafond > mur > sol) — plinthe sol∩mur en **bleu**,
  angle mur∩mur en bleu, mur∩plafond en **violet**, jonction entre deux plans sol en **vert**.
- Polygones bruts (boucles blanches + arêtes verticales d'extrémité de mur) : conservés uniquement en
  diagnostic, via **Réglages > Diagnostic > « Polygones ARCore bruts en AR »** (persisté, relu à
  l'ouverture de session — `ArDiagnose.rawPolygons` → `ArTracker.rawPolygonsOn`). L'interrupteur
  « Contours » de l'écran Session masque toujours tout le tracé.
- Référence lue : `hello_ar_java` d'ARCore (Apache-2.0, cf. NOTICE) pour le comportement, réimplémentation.

### Point 2e — Visée SOL : le mobilier ne gagne plus contre le sol

Avant : `Aim.resolve` prenait le plan le plus proche du rayon, donc une table (plan horizontal au-dessus du
sol) gagnait sur le sol.

- `core/compute/PlaneFilter.kt` (pur, tests JVM), branché **avant** `Aim.resolve` dans
  `ArTracker.updateAim` :
  - **sol** = plan horizontal (`AR_PLANE`) le plus bas ; les plans à ±10 cm (`FLOOR_BAND_M`) sont le même sol ;
  - **mobilier** = plan horizontal entre **0,3 m et 2,0 m** au-dessus du sol (bornes incluses) → ignoré ;
  - **poussière** = tout plan < **0,25 m²** (`MIN_AREA_M2`) → ignoré, quel que soit le type ;
  - murs et plafonds passent tels quels ; un horizontal sous +30 cm (estrade) ou au-dessus de +2 m
    (mezzanine) n'est pas du mobilier et reste visé ; un plan horizontal seul est le sol, quelle que soit
    sa hauteur.
- Conséquence : le repli `AR_FLOOR_ESTIMATE` d'`Aim.resolve` utilise désormais le vrai sol détecté (le plus
  bas), plus une table.

## testTags

| testTag | Écran | Rôle |
|---|---|---|
| `reglages_ar_polygones_bruts` | Réglages > Diagnostic | interrupteur « Polygones ARCore bruts en AR », appliqué à la prochaine session |
| `session_ar_contours`, `session_ar_support_auto`, `session_ar_plans` | Session | inchangés |

## Flows Maestro

- `.maestro/build199/b199_ar_aretes.yaml` (nouveau, spécification) : commandes sous la caméra, puis
  Accueil > Réglages > présence et bascule de l'option diagnostic. Le tracé des arêtes n'est pas testable
  en CI (AR simulé).
- `.maestro/build199/b199_ar_menus_stables.yaml`, `.maestro/v176_ar_contours.yaml` : inchangés (le
  comportement des interrupteurs est le même).

## Tests JVM ajoutés

- `ArGeometryTest` : intersection sol∩mur, rejet des plans quasi parallèles / opposés / dégénérés, plinthe
  bornée aux deux polygones, angle vertical mur∩mur, absence d'arête hors polygones, découpe de droite
  (sens des sommets inversé), aire par lacet.
- `PlaneFilterTest` : table ignorée, bande sol ±10 cm, bornes incluses 0,3/2,0 m, estrade et mezzanine
  gardées, plan seul = sol, plans < 0,25 m² rejetés tous types, murs/plafonds non filtrés, pas de sol →
  pas de filtre mobilier.

## Reste manuel (téléphone)

- Pixel 7 : les arêtes suivent les plinthes, angles verticaux et raccords mur/plafond, colorées par type ;
  les polygones bruts n'apparaissent qu'avec l'option Réglages > Diagnostic activée.
- Visée sol au-dessus d'une table : le point reste sur le sol ; visée au travers d'une étagère > 2 m ou
  d'une estrade < 30 cm : le plan est visé normalement.
- Petits plans parasites (< 25 × 25 cm) non visés.
- Bascule de « Visée : … (auto) » inchangée (inclinaison, hystérésis).
