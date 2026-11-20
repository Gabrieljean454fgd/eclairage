# Intégration b230 — fusion des paquets locaux

Branche : `petit/integration-b230` (déjà basée sur `origin/etape-3-v176d-synchro-reelle`, 6 branches fusionnées avant cette passe : adresses, annoter-barre, ar-geometrie, carte-annoter, export-zip + 1).
Aucun push, aucun build Gradle (non compilé — voir § Limites).

## Branches fusionnées (dans l'ordre)

| # | Branche | Résultat | Commit de fusion |
|---|---|---|---|
| 1 | `petit/synchro-affichage` | ✅ fusionnée, 2 conflits résolus | `c004d75` |
| 2 | `petit/synchro-service` | ✅ fusion automatique (ort), aucun conflit | `39486cc` |
| 3 | `petit/ar-drapeaux-plafond` | ✅ fusionnée, 3 fichiers en conflit | `88cd5c3` |
| 4 | `petit/menu-bloc` | ✅ fusionnée, 4 fichiers en conflit + 1 correction post-fusion | `076b905` + `25adefb` |
| 5 | `petit/recaler-2-reperes` | ✅ fusionnée, 2 fichiers en conflit | `513760e` |

Aucune branche laissée de côté (pas de `merge --abort`).

## Conflits résolus et comment

### 1. synchro-affichage
- **`data/Platform.kt`** : deux nouveaux objets top-level au même endroit — `LastKnownPosition` (HEAD, adresses récentes) et `DeviceName` (branche, nom du téléphone pour Nearby). **Les deux conservés**, l'un après l'autre (import `SyncDisplay` déjà présent).
- **`ui/Screens.kt`** : imports ajoutés par la branche (`RecentZone`, `NearbyMesh`, `Link`, `SyncRole2`) ; HEAD vide à cet endroit → **imports de la branche conservés**.

### 2. synchro-service
Fusion automatique : `SynchroService` (service de premier plan), `SyncService` core + tests, manifest, flows et doc. Complète synchro-affichage sans chevauchement textuel.

### 3. ar-drapeaux-plafond
- **`SessionController.kt`** : HEAD configure `ar.rawPolygonsOn` (ar-geometrie) ; la branche ajoute `ar.ceilingHeightM = site.ceilingHeightM` (2f) sur la même ligne d'init. **Les deux affectations conservées** (deux lignes).
- **`capture/ArTracker.kt`** (3 zones) : les deux branches modifient `LineRenderer.draw` et son appel.
  - Appel : logique de la **branche** conservée (ancres dessinées même en suivi dégradé, grises, 2c ; plans seulement en TRACKING) **+** 4e argument `rawPolygonsOn.value` (HEAD, diagnostic 2d).
  - Signature : combinée en `draw(cam, planes, marks: List<Triple<AnchorKind, V3, Boolean>>, rawPolygons: Boolean)` ; helpers `rank`/`color` de HEAD (arêtes colorées par type de plan, 2d) conservés ; doc de la branche sur `marks` conservée.
  - Corps : rendu des arêtes/polygones de HEAD (2d) conservé ; boucle des marques de la **branche** conservée (gris quand la pose est figée), avec `ArGeometry` non qualifié (import existant).
- **`docs/TESTS_BUILD199.md`** : **les deux lignes de tableau** conservées (`b199_ar_aretes` 2d/2e et `b199_ar_drapeaux_plafond` 2c/2f).

### 4. menu-bloc
- **`SessionController.kt`** : `const val LS_PAYLOAD_SCHEMA = "lumisite-equipment/2"` (branche) conservée **+** `var site = site; private set` (HEAD, nécessaire à la hauteur sous plafond 2f) conservé à la place du `val site` de la branche.
- **`ui/DrawTools.kt`** (2 zones) :
  - Outil PLACE : version de la **branche** (`LsCatalogPicker`, catalogue LS_*) — `FamilyQuickGrid` n'existe plus dans la branche, la version HEAD ne compilait plus avec. Le testTag `session_annoter_aide` (exigé par `.maestro/build199/b199_annoter_barre.yaml`) a été **reporté** sur le nouveau texte d'aide (correction `25adefb`).
  - Liste « Objets du relevé » : label LS `c.lsItem(sv.kind)?.label ?: sv.kind` (branche) **+** `d.select(null)` (HEAD, réinitialise aussi `editPanel`).
- **`ui/MapView.kt`** : icône des points = symbole LS si présent (branche), sinon `tappableDot` 48 dp (HEAD, zone de toucher build 199).
- **`ui/SessionScreen.kt`** : `onTap` = picked toujours à jour (HEAD, « Déplacer au point touché ») **+** paramètre `AutoPhoto.enabled(activity)` (branche, photo après pose) ; `draw.select(...)` (HEAD) au lieu de `draw.selected = ...`.

### 5. recaler-2-reperes
- **`ui/MapView.kt`** (3 zones) :
  - `MapPoint` : champs `symbol` (menu-bloc) **et** `dashed` (branche) tous deux conservés.
  - Icône : priorité symbole LS → `dashedDot` (repère « à confirmer », pointillé) → `tappableDot`.
  - Fonctions `tappableDot` (HEAD) et `dashedDot` (branche) : **les deux conservées** (usages distincts).
- **`ui/SessionScreen.kt`** (2 zones) :
  - `onPointTap` : logique de visée guidée « Recaler sur 2 repères » (branche, préfixes `prev:`) combinée avec `draw.select(...)` (HEAD) ; `onTap` inchangé (picked + AutoPhoto).
  - Barre d'actions : logique HEAD (`panelCollapsed`/`mapFull` à l'ouverture d'un onglet) **+** `recal2Reset()` quand on quitte Calibrer (branche).

## Corrections post-fusion
- `25adefb` : retrait du cas `Tool.MOVE ->` rapporté par la branche menu-bloc (résidu de sa base ancienne : `MOVE` n'existe plus dans l'enum `Tool` depuis annoter-barre, qui impose `assertNotVisible: session_annoter_move`) ; `session_annoter_aide` conservé sur l'aide de « Poser ».

## Contrôles effectués (sans Gradle)
- ✅ Aucun marqueur `<<<<<<<` / `>>>>>>>` dans le dépôt (`git grep -nE '^(<<<<<<<|>>>>>>>)'` vide).
- ✅ Accolades équilibrées dans les 7 fichiers Kotlin touchés (Platform, Screens, SessionController, ArTracker, DrawTools, MapView, SessionScreen).
- ✅ Aucun import dupliqué ; aucune fonction définie deux fois au même niveau (les `create`/`draw` multiples sont dans des classes distinctes ; les deux `fun edit` sont locales à deux composables distincts).
- ✅ testTags : aucune perte non intentionnelle. Disparitions voulues et déjà documentées : `session_annoter_famille_*` et `session_releve_famille_*` (remplacés par le sélecteur catalogue LS, flows Maestro patchés dans menu-bloc), `session_ar_surface_*` (suppression documentée dans TESTS_BUILD199, antérieure à cette passe), `session_annoter_forme_dupliquer/supprimer` (renommés `session_annoter_dupliquer/supprimer` avant cette passe). `synchro_journal` et `synchro_esclave_${"$"}{i+1}` toujours présents (passés en paramètre à `SynchroJournal`/`SynchroShared`). Tous les ids référencés par `b199_annoter_barre.yaml` existent dans le code fusionné.

## Branches non fusionnées
Aucune des 5 branches demandées n'a été écartée. Les autres branches locales (`petit/tests-build199`, `petit/inclinaison`, `petit/swe2-*`, etc.) ne faisaient pas partie de la tâche.

## Limites
**Non compilé** : aucun build Gradle lancé (consigne). La compilation, les tests JVM et les flows Maestro sont **à vérifier par la CI / après 23 h**, en particulier :
- `ArTracker.kt` (signature combinée de `LineRenderer.draw`),
- `DrawTools.kt` (sélecteur LS dans l'outil Poser),
- `SessionScreen.kt` (visée guidée recal2 + AutoPhoto + picked).
