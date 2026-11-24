# Compte rendu — Build 248 · ar-donnees · AR surfaces/lignes absents sur Pixel 7

**Date :** 2026-11-24  
**Branche de travail :** `petit/ar-donnees` (worktree `wt_ar-donnees`, SHA `2df9dca`)  
**Branche initialement ouverte dans la session :** `petit/integration-b230` (SHA `85b5b08`). Le problème concerne Build 248 qui est sur `petit/ar-donnees` ; la session a été déplacée sur ce worktree.

## 1. Cause démontrée / mesures encore manquantes

**Aucune cause unique n'est démontrée par la seule lecture du code.** Le parcours configuration → tracking → trackables → compteurs → polygones → renderer a été suivi ; les anomalies suivantes sont prouvées dans le code :

| Défaut | Preuve | Correction apportée |
|---|---|---|
| Les compteurs de plans figent leur dernière valeur quand le suivi est perdu. | `ArTracker.updateAim()` retournait avant de mettre à jour `planeCounts` dès que `state != TRACKING`. | Mise à jour forcée à `0/0/0` hors tracking. |
| La Depth API est systématiquement désactivée, y compris sur Pixel 7 qui la supporte. | `depthMode = Config.DepthMode.DISABLED` en dur. | `Config.DepthMode.AUTOMATIC` quand `isDepthModeSupported(AUTOMATIC)` est vrai, repli sur `DISABLED`. |
| Aucun diagnostic ne distingue "0 plan", "plans non tracking", "polygones invalides" ou "renderer non alimenté". | Seuls les compteurs sol/mur/plafond existaient. | Snapshot `ArPlaneStats` + log `ARCORE_DIAG` toutes les 2 s + ligne UI. |

**Hypothèses nécessitant une mesure sur téléphone :**
- Sur Build 248 les **arêtes ARCore sont désactivées par défaut** (`arCoreEdgesOn = false`). L'utilisateur peut donc voir "aucune ligne" même si les plans sont détectés ; il faut activer le chip **Session > Arêtes ARCore**.
- Si les compteurs restent à 0 alors que le suivi est `TRACKING`, ARCore ne détecte effectivement pas de plans. Les causes possibles sont alors : scène peu texturée, luminosité, ou choix de config caméra. Le nouveau log `ARCORE_DIAG` permettra de le vérifier.

## 2. Changements

### `app/src/main/java/app/lumisite/android/capture/ArTracker.kt`
- Activation de `DepthMode.AUTOMATIC` avec `Session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)` et repli `DISABLED` ; `DepthSensorUsage.DO_NOT_USE` conservé (pas d'exigence ToF).
- Ajout de `ArPlaneStats` (data class) et `planeStats: MutableStateFlow<ArPlaneStats?>`.
- `updateAim()` met à jour les compteurs et le snapshot même hors `TRACKING`, compte les plans subsumés, et publie : état tracking + failure reason, depth support/active, nombre total/tracking/subsumé de plans, répartition par type et par `trackingState`, polygones valides/invalides avec aire min/max/moyenne, `cameraY`, `floorY`, et indicateur `rendererGotPolygons`.
- Log `ARCORE_DIAG` rate-limité à 2 s ; snapshot UI à 500 ms.

### `app/src/main/java/app/lumisite/android/ui/ArOverlay.kt`
- Ligne de diagnostic sous les compteurs (`session_ar_diag`) affichant `tracking`, `depth`, plans, polygones, `cameraY`, `floorY`, `renderer`.

### `core/src/main/kotlin/app/lumisite/core/compute/ArPlaneDiagnose.kt` (nouveau)
- Logique pure et JVM-testable de calcul des statistiques sur les plans ARCore.

### `core/src/test/kotlin/app/lumisite/core/ArPlaneDiagnoseTest.kt` (nouveau)
- Tests JVM : stats vides, comptage par type/état, validité des polygones, aires, indicateur renderer.

## 3. Tests

Commandes réellement exécutées :

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
.\gradlew.bat :core:test --tests "app.lumisite.core.ArPlaneDiagnoseTest" `
    --tests "app.lumisite.core.ArDonneesTest" `
    --tests "app.lumisite.core.AimTest" `
    --tests "app.lumisite.core.PlaneFilterTest" --no-daemon
```

Résultat : **BUILD SUCCESSFUL**, 31 tests passés.

Le `jvmToolchain` a dû être temporairement porté à 21 faute de JDK 17 sur la station ; le code source reste compatible Java 17 et le `core/build.gradle.kts` a été rétabli.

Le `:core:test` complet n'a pas pu terminer dans cet environnement (arrêt du daemon, probablement OOM ou test bloquant indépendant des modifications AR). Le build Android n'a pas été exécuté faute de SDK Android et de Java 17.

## 4. Limites

- Aucun test physique sur Pixel 7 n'a été réalisé (conformément à la consigne "pas d'installation/APK").
- L'impact performance de `DepthMode.AUTOMATIC` n'est pas mesuré ; le log indiquera le mode actif.
- Le build Android n'a pas été validé en CI/local.
- La logique de filtre sol/table de `PlaneFilter` n'a pas été modifiée ; le diagnostic indiquera si `cameraY`/`floorY` expliquent un sol manquant.

## 5. Scénario Pixel 7 à exécuter par le propriétaire

1. Installer l'APK de test (non fait ici).
2. Accueil > Nouveau relevé > créer un site.
3. Session : attendre `TRK` vert.
4. Observer la ligne **Diag** sous les compteurs.
5. Activer le chip **Arêtes ARCore**.
6. Marcher lentement face à un mur et un sol texturés.

**Résultat attendu :**
- `depth=AUTOMATIC` sur Pixel 7 (sinon `DISABLED`).
- Après quelques secondes : `plans N/N tracking > 0`, `poly X/Y > 0`, `renderer=true`.
- Des arêtes colorées apparaissent (sol∩mur vert/bleu, mur∩mur bleu, mur∩plafond violet).

**Données à partager :**
- Capture d'écran de la ligne **Diag** (`session_ar_diag`).
- Extrait du journal (`Réglages > Journal > Partager`) filtré sur les lignes `ARCORE_DIAG` et `ARCORE`.
- Indiquer si le chip **Arêtes ARCore** était actif.
