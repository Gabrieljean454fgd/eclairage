# LOCAL — Menu bloc refait (catalogue 2.0, symboles, photo après pose)

Branche `petit/menu-bloc`. Priorité 1 de Sébastien : « pas de categories, blocs non
determines, pas d ajout » — la liste plate du catalogue 1.0 est remplacée par un
sélecteur familles → types, des symboles originaux et l'ajout de types personnels.

## Ce qui est fait

### Catalogue 2.0 — `catalog/lumisite_catalog_2.json`
- 90 items LS_* : 78 codes de l'annexe B + entrées de la table LS→DWG
  (PDL, éclairage supplémentaires) + 6 SON + `LS_AUTRE_A_CLASSER`.
- Par item : `code`, `label` (accents), `famille`, `calque` LUMI-*, `bloc_dwg`
  (nom corrigé, null si indéterminé), `aliases` (anciens `block_name` du
  catalogue 1.0 → relecture des relevés existants), `symbole`, `is_block`.
- Ordre des familles : Éclairage, Électricité, Secours, Sécurité, Incendie,
  Caméra et sûreté, Sonorisation, Autre (en dernier).
- `catalog/table_ls_dwg.csv` : correspondance LS_* → bloc DWG copiée dans le dépôt.
- Outils : `tools/gen_ls_catalog.py` (génère le JSON), `tools/verify_catalog.py`
  (90 items, unicité, ≤ 31 car., DWG présents, anciens noms résolus).

### Symboles — `core/.../catalog/LsSymbols.kt` + `app/.../ui/LsSymbols.kt`
- Description géométrique pure (primitives `LsPrim` normalisées [0,1]²) dans
  `core`, esprit NF EN 60617-11 : cercle+croix (point lumineux), rectangle long
  (réglette), BAES, prise demi-cercle, interrupteur, tableau, caméra, extincteur,
  déclencheur manuel, haut-parleur, etc.
- Couleur par famille (`LsSymbols.familyColor`), symbole de famille
  (`familySymbol`), rendu Compose `LsSymbol` (fond blanc + glyphe) et bitmap
  osmdroid `lsSymbolDrawable` (carte, 32 dp).
- Les symboles remplacent les pastilles : dans le sélecteur (familles et types)
  ET sur la carte (`MapPoint.symbol` → `lsSymbolDrawable`).

### Sélecteur partagé — `app/.../ui/LsPicker.kt` (`LsCatalogPicker`)
- Un seul composable utilisé par **Session > Relevé** (`SurveyPanel`),
  **Session > Annoter > Poser** (`DrawPanel`, outil Poser) et
  **« Changer de famille »** (`SelectedSurveyPanel`).
- Champ recherche (accents insensibles), récents (6) et épinglés en tête
  (persistance `lumisite_quick`/`pins`, anciens noms résolus), grille des
  familles avec symbole, vue famille avec fil d'Ariane et « ‹ Familles ».
- « + Ajouter un type… » : boîte de dialogue → `LS_PERSO_<n>`
  (`is_block=false`, symbole « autre », sans bloc DWG) dans la famille choisie,
  persisté dans `filesDir/catalogue_perso.json` via `CatalogStore.appendPersonal`
  ; visible après redémarrage et exporté avec son libellé (`ls_label`).

### Enregistrement — `SessionController`
- `resolveFamily` : code LS_* → `kind=code`, `payloadSchema="lumisite-equipment/2"`,
  `DxfHint(calque, bloc_dwg)` ; anciens noms (catalogue 1.0) toujours acceptés
  (`byCodeOrLegacy`), repli catalogue 1.0 conservé.
- Payload : `ls_code`, `ls_label`, `ls_famille`, `calque`, `bloc_dwg`,
  `block_name`. `editSurvey` (changer de famille) passe par le même chemin.
- `lastSurveyId` exposé pour rattacher la photo du relevé qui vient d'être posé.
- `lsItem(id)` : résout code LS_* et ancien nom (affichages « Relevé sélectionné »,
  « Objets du relevé », `SurveyEdits.familyPath` → « Sonorisation › Amplificateur »).

### Photo après pose (réglage activé par défaut, préférence existante `lumisite_photo`/`auto`)
- **Session > Relevé** : « Enregistrer le relevé » ouvre aussitôt `PhotoPrompt`
  (appareil photo existant, rôle `vue`) — déjà en place, conservé.
- **Session > Annoter > Poser** : après une pose (tap carte ou doigt), même
  `PhotoPrompt` dans le panneau (`DrawState.photoFor = c.lastSurveyId`).
- Réglages : libellé « Photo après pose (Session > Relevé et Annoter > Poser) ».

### Export
- `GeoJsonExport` : propriétés `ls_code`, `ls_label`, `ls_famille` ajoutées à
  chaque équipement du catalogue 2.0 (payload inchangé côté événements).

## testTags

Requis (sélecteur, identiques dans Relevé et Annoter > Poser) :
`session_releve_categorie_<FAMILLE>`, `session_releve_fil_ariane`,
`session_releve_type_<CODE>`, `session_releve_recherche`,
`session_releve_ajouter_type`, `session_releve_nouveau_type_nom`,
`session_releve_nouveau_type_valider`.

Ajoutés : `session_releve_retour_familles`, `session_releve_epingler`,
`session_releve_nouveau_type_annuler`.

Conservés : `session_releve_enregistrer_le_releve`, `session_releve_checkbox`,
`session_photo_*`, `reglages_photo_automatique`, `session_annoter_*`.

Retirés : `session_releve_famille_*`, `session_annoter_famille_*`
(remplacés par `session_releve_categorie_*` / `session_releve_type_*`).

## Flows Maestro

- `.maestro/build199/b199_releve_catalogue.yaml` : adapté au sélecteur
  (catégories, fil d'Ariane « Éclairage », type `LS_ECLAIRAGE_DOWNLIGHT`,
  recherche « encastr », ajout « Plafonnier test 199 », captures gardées) avec
  gardes de défilement ; pas de `clearState` ajouté.
- 7 flows anciens adaptés (`Amplificateur` → `categorie_SON` +
  `type_LS_SON_AMPLIFICATEUR`) : `maj/maj_avant`, `maj/v6_releve`,
  `scenario_p0_reprise_session`, `scenario_p2_annoter_deplacer`,
  `scenario_p2_photo_implantation`, `scenario_v6_export_medias`,
  `v176/releve_au_centre` (`tools/patch_maestro_famille.py`).

## Ce qui reste manuel / non vérifié ici

- Compilation Gradle et exécution des tests JVM / flows Maestro : faits par le
  relecteur (consigne : pas de build complet ni émulateur dans cette session).
- Rendu visuel des symboles à vérifier sur appareil (lisibilité 24–48 dp,
  couleurs par famille).
- Catalogue : 90 items (les 78 de l'annexe B + compléments table DWG + SON +
  AUTRE) — confirmer que les compléments table sont bien voulus.
- Le comptage « Photos et sons » et les exports DXF côté bureau gardent le
  `block_name` DWG d'origine (pas de changement de contrat).
