# LOCAL — Adresses récentes et proches (retour build 199, point 4)

Branche `petit/adresses`, worktree `wt_adresses`. Écran concerné : **Accueil > Nouveau relevé** > champ « Adresse / lieu ».

## Ce qui est fait

- **Historique local** `filesDir/recent_addresses.json` (200 max, sans doublon, lat/lon conservées si connues) :
  - `core/osm/AddressHistory.kt` : `SavedAddress` rendu `@Serializable` (aucune logique touchée, tests JVM inchangés).
  - `Repository.recentAddresses()` / `addRecentAddress(text, lat, lon)` : lecture/écriture via `LumiJson`, même patron que `recent_zones.json`.
- **Enregistrement** dans `SiteEditorScreen` (chaque adresse validée) :
  - « Rechercher » (action `ImeAction.Search` du clavier) : le texte tapé est enregistré même sans réseau ni résultat.
  - Résultat de recherche choisi : le nom du lieu + ses coordonnées sont enregistrés.
  - Suggestion récente choisie : rafraîchie ; si des coordonnées sont connues, l'origine est posée directement (fonctionne hors ligne), sinon recherche en ligne relancée.
- **Suggestions hors réseau** dès la première lettre, avant les résultats en ligne : `AddressHistory.suggest` (chaque mot tapé préfixe un mot de l'adresse ; tranches de 500 m autour de la position actuelle puis récence). Une suggestion strictement identique au texte tapé n'est pas réaffichée.
- **Recherche en ligne biaisée** vers la position actuelle (`LastKnownPosition.get` = dernier correctif GPS/réseau, null sans permission) : IGN `&lat=..&lon=..`, Nominatim `&viewbox=…` sans `bounded` (`OsmClient.search(query, near)`).
- **Flow** `.maestro/build199/b199_adresses_recentes.yaml` ajusté pour être déterministe : `hideKeyboard` + un seul `back` (BackHandler → Accueil), `extendedWaitUntil` pour la suggestion. Pas de `clearState` ajouté.

## testTags

- `nouveau_site_adresse_recente_N` (N = 1..5, index +1) sur chaque suggestion d'adresse récente, texte = l'adresse seule.

## Fichiers touchés

- `core/.../osm/AddressHistory.kt` (@Serializable seulement)
- `app/.../data/Repository.kt`, `data/OsmClient.kt`, `data/Platform.kt`
- `app/.../ui/Screens.kt` (`SiteEditorScreen` prend désormais `repo: Repository`), `MainActivity.kt` (appel mis à jour)
- `.maestro/build199/b199_adresses_recentes.yaml`

## Ce qui reste manuel

- Build Gradle complet + `:core:test` (relecteur, pas de build ici).
- Passage réel de `.maestro/build199/b199_adresses_recentes.yaml` sur le Pixel 7 (Maestro) : vérifier que `pressKey: Enter` déclenche bien l'action « Rechercher » du clavier.
- Vérifier que `.maestro/v176_nouveau_site_rectangle.yaml` reste vert (rectangle non touché).
- Test terrain : suggestions proches d'abord quand le GPS a un correctif ; IGN/Nominatim reçoivent bien `lat`/`lon` et `viewbox`.
