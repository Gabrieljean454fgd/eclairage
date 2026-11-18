# Retours build 199 — points 0, 5/6 (2a, 2b) : version Accueil, support AR automatique, menus stables

Branche `petit/inclinaison` (base `97b2ae6` + `4872f76`). Écrans concernés : **Accueil** et **Session** (bloc caméra AR).

## Fait

### Point 0 — Accueil : « Build N · <paquet> »
- `app/build.gradle.kts` : nouveau champ `BuildConfig.PAQUET`, calculé à la compilation depuis
  `LUMISITE_PAQUET`, sinon `GITHUB_HEAD_REF`, sinon `GITHUB_REF_NAME`, sinon la branche git courante
  (`git rev-parse --abbrev-ref HEAD`, « HEAD » détaché ignoré).
- Transformation dans `core/src/main/kotlin/app/lumisite/core/version/PaquetName.kt` (pur, testé JVM) :
  `etape-3-v176g-xxx` → `v176g`, `etape-3-local-xxx` → `local-xxx`, absent/vide → `dev`, autre nom gardé
  tel quel. La même transformation est rejouée dans le script Gradle (le champ est prêt à afficher) et la
  fonction est idempotente, donc l'appel côté appli reste correct.
- `Screens.kt` : `accueil_version` affiche `Build <versionCode> · <paquet>` via `accueilBuildLabel()`.
  Réglages > À propos (`reglages_a_propos_version`) inchangé : `LumiSite v0.3.0-alpha (build N)`.
- Flow : `.maestro/scenario_p2_version_accueil.yaml` étendu (assert `Build [0-9]+ · \S+`).

### Point 2a/6 — Session > Relevé en AR : support automatique
- `ArOverlay.kt` : puces `session_ar_surface_tous/sol/mur/plafond` **supprimées**. Nouvelle ligne
  `Visée : sol|mur|plafond (auto)` (`session_ar_support_auto`, libellés `SupportFromTilt.label`).
- `ArTracker.kt` : l'élévation de l'axe caméra vient de `Tilt.fromArQuaternion` (pose ARCore,
  même convention que `SupportFromTilt.elevationFromForward`), repli `sensorTiltDeg` =
  `Tilt.fromRotationMatrix` via `SensorRecorder.cameraTiltDeg` (câblé dans `SessionController`).
  `SupportFromTilt.classify` avec hystérésis (`lastSupport` sur le thread GL) remplace `aimSurface`
  dans `Aim.resolve` : filtre `planeSource(support)`, replis sol estimé/dernier objet seulement en SOL.

### Point 2b/5 — Menus stables
- `SessionScreen.kt` : `camAspect` fixe à 4:3 — plus de bascule 4:3 → 2:1 quand un onglet s'ouvre
  (hauteur du bloc caméra constante entre Aucun / Calibrer / Relevé / Synchro).
- `ArOverlay.kt` : paramètre `compact` supprimé — Contours / Torche / Mesure toujours affichés.
- `ArTracker.kt` : compteurs de plans publiés au plus toutes les 500 ms (2 Hz) ; ligne compteurs en
  chasse fixe (`FontFamily.Monospace`) pour une largeur stable.

## testTags
| testTag | Écran | Rôle |
|---|---|---|
| `accueil_version` | Accueil | `Build N · <paquet>` |
| `session_ar_support_auto` | Session | `Visée : … (auto)` (remplace `session_ar_surface_*`) |
| `session_ar_plans` | Session | compteurs sol/murs/plafond, monospace, 2 Hz |
| `session_ar_contours`, `session_ar_torche_oui`, `session_ar_mesure_a` | Session | toujours visibles |

## Flows Maestro
- `.maestro/build199/b199_ar_menus_stables.yaml` : déjà conforme (spec), non modifié.
- `.maestro/v176_ar_contours.yaml` : adapté (puces retirées, nouvelles lignes).
- `.maestro/scenario_p2_version_accueil.yaml` : étendu.

## Reste manuel (téléphone)
- Pixel 7 : visée sol → `Visée : sol (auto)`, mur → `mur`, plafond → `plafond`, sans clignotement
  aux angles de pièce (hystérésis 5°).
- Ouvrir Calibrer / Relevé / Synchro : les boutons sous la caméra ne bougent pas pendant 10 s de visée.
- Relevé en visée mur/plafond : « position à compléter » si aucun plan du bon type n'est détecté (pas de repli sol).
- Le repli capteur (ARCore perdu) n'est vérifiable qu'au téléphone : l'inclinaison capteur alimente alors la visée.
