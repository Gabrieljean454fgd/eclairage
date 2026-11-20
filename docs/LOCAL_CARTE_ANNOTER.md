# LOCAL — Carte utilisable dans Annoter et Export (build 199, retour point 3)

Branche `petit/carte-annoter`, base `etape-3-v176d-synchro-reelle` + 4872f76.
Écran concerné : **Session** (`app/.../ui/SessionScreen.kt`).

## Ce qui est fait

- `core/.../ui/SessionLayout.kt` (fonctions pures, testées par `SessionLayoutTest`) :
  - `cameraFor(tab, fullMap)` : Annoter (`DRAW`) et Export (`EXPORT`) → caméra `BAND` (~96 dp) ; les autres onglets → `FULL` ; « Carte plein écran » → `HIDDEN` partout.
  - `resolve(tab, fullMap, panelCollapsed)` : parts carte/panneau en portrait — 66 %/34 % en Annoter-Export (carte ≥ ~50 % de l'écran avec le panneau ouvert, bande de 96 dp + ~200 dp de chrome), 45 %/55 % pour les autres onglets, 60 %/40 % sans onglet, 100 % + bandeau si plein écran ou panneau replié.
  - `landscapeMapWeight(fullMap)` : 55 % → 72 % en paysage.
  - Constantes `CAMERA_BAND_DP = 96`, `PANEL_STRIP_DP = 48`, `MAP_TOUCH_DP = 48`.

- `SessionScreen.kt` :
  - Un seul bloc caméra (`cameraBlock`) pour les trois états : `BAND` = Box de 96 dp + `clipToBounds` (le contenu garde sa taille naturelle via `wrapContentHeight(unbounded)`), `HIDDEN` = Box de 0 dp. La `GLSurfaceView` reste composée et à sa taille dans tous les cas → la session AR n'est jamais détruite. Le tag `session_bloc_ar` est retiré quand le bloc est masqué (HIDDEN ou clavier).
  - `session_carte_plein_ecran` : bouton ≥ 48 dp dans la colonne en bas à droite de la carte → présent dans **tous** les onglets. Bascule `mapFull` : caméra à 0 dp, panneau ouvert → bandeau.
  - `session_panneau_replier` (en tête du panneau, onglet ouvert) replie le panneau en bandeau ; `session_panneau_replie` (bandeau ≥ 48 dp) le rouvre au toucher.
  - Ouvrir un onglet de la barre d'actions quitte le plein écran et rouvre le panneau ; la barre `session_barre_actions` reste toujours visible.
  - Clavier (portrait < 480 dp) : comportement inchangé (panneau dominant 75 %).

- `MapView.kt` : `tappableDot` — les marqueurs d'objets (relevés, points de tracé « shape: », points de contrôle « cp: ») sont dessinés sur un bitmap transparent de 48 dp (point visible ~20 dp centré) → zone de toucher ≥ 48 dp (l'icône osmdroid définit la zone de hit).

- Flow `.maestro/build199/b199_carte_annoter.yaml` mis à jour (Annoter → bande → plein écran → bandeau panneau → Export).

## testTags ajoutés

| Tag | Où | Rôle |
|---|---|---|
| `session_camera_repliee` | bloc caméra, Annoter/Export | caméra repliée en bande ~96 dp |
| `session_carte_plein_ecran` | overlay carte, bas-droite | bascule carte plein écran |
| `session_panneau_replier` | tête du panneau | replie le panneau |
| `session_panneau_replie` | bandeau sous la carte | rouvre le panneau |

Non touché : sélecteur de famille du Relevé (`session_releve_famille_*`), AR (overlay, visée, `ArReticle`, `ArControls`), synchro, rectangle du Nouveau site, fichiers de test verrouillés.

## Ce qui reste manuel / à vérifier sur appareil

- En « Carte plein écran », le suivi AR continue (GLSurfaceView composée hors écran) : vérifier que `session_etat_ar` reste « AR ● » et que le suivi n'est pas perdu au retour.
- La part réelle de la carte en Annoter (~51 % sur ~890 dp de haut) — viser un téléphone réel, pas l'émulateur.
- Zone de toucher des objets : viser le bord d'un point à ~20 dp du centre.
- `session_bloc_ar` en bande garde sa hauteur sémantique complète (contenu clippé) : Maestro le voit « visible » tant qu'il n'est pas HIDDEN — prévu dans le flow.
