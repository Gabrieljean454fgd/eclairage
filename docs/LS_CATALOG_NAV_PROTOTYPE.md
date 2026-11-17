# Catalogue LS_* — prototype de navigation (préparation v176e)

Date : 2026-11-17. Branche locale `petit/omniroute-catalogue`, base `origin/etape-3-v176d-synchro-reelle` (97b2ae6). Aucun push.

## Ce qui est fait

Logique isolée dans `core/.../catalog/LsCatalog.kt` (fichier nouveau, `Catalog.kt` et `equipment_catalog.json` inchangés) :

- items `LS_*` : code, libellé, famille, sous-famille, type, synonymes ;
- recherche sans accents (code, libellé, type, synonymes) : « encastre » trouve Downlight ;
- navigation Famille → Sous-famille → Type, fil d’Ariane `Éclairage › Encastré › Downlight` ;
- « Autres » toujours en dernier ;
- favoris / récents (plafond 12), opérations pures sur des listes de codes ;
- validation : ASCII, préfixe `LS_`, unicité, longueur ≤ 31.

Fixture seulement : `catalog/lumisite_catalog_2.fixture.json` (10 items). Ce n’est **pas** le catalogue complet 78 + SON (à valider par Sébastien). Les codes de la fixture sont des identifiants courts de test (`LS_ECL_ENC_DOWNLIGHT`), pas encore les codes du plan (`LS_ECLAIRAGE_DOWNLIGHT`, etc.).

Tests JVM : `core/src/test/kotlin/app/lumisite/core/catalog/LsCatalogTest.kt`. Non exécutés le 17/11 après-midi (pas de build Gradle avant 23 h).

## Ce qui reste (hors de cette branche)

- Brancher un composable Material3 (`LazyColumn` / `LazyVerticalGrid`, recherche en haut, flèches, fil d’Ariane) sur **Session > Relevé**, sans bibliothèque de picker. `SessionScreen.kt` et `DrawTools.kt` n’ont pas été touchés (paquet v176e de Devin).
- Catalogue métier complet `catalog/lumisite_catalog_2.json` et table bureau `correspondance_dwg.json` (déjà commencée sur `petit/flash-table-dwg`).
- Flows `v176_catalogue_navigation.yaml`, `v176_catalogue_recherche.yaml`, `v176_symbole_selection.yaml` et symboles carte (E4).
