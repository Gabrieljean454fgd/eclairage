# Tests ajoutés après le retour terrain du build 199 (17/11/2026)

Branche locale `petit/tests-build199` (base `97b2ae6`, v176d), **jamais poussée**. Rédigés par Grok Bot, code original.
Rien n'a été exécuté : pas de JDK sur le box, et les tests lourds sur TORNADO5 n'ont lieu qu'après 23 h.

## Tests JVM (module `core`, passent sans changer l'appli)
| Fichier | Point du retour | Vérifie |
|---|---|---|
| `core/.../compute/SupportFromTilt.kt` + `SupportFromTiltTest.kt` | 6 | support sol / mur / plafond déduit de l'inclinaison (gravité ou pose ARCore), seuils ±35°, hystérésis 5° |
| `core/.../osm/AddressHistory.kt` + `AddressHistoryTest.kt` | 3 | historique local d'adresses (dédoublonnage, 200 max), suggestions par début de mot, plus proches d'abord, paramètres de proximité IGN (`lat`/`lon`) et Nominatim (`viewbox`) |
| `core/.../sync/SyncDisplay.kt` + `SyncDisplayTest.kt` | 1, 2 | nom lisible du téléphone (Xiaomi : `23078PND5G` remplacé par le nom de l'appareil), bandeau vert dès qu'un esclave est connecté, décalage brut d'horloge monotone masqué |

Commande (après 23 h, ou en CI) : `./gradlew :core:test --tests "*SupportFromTilt*" --tests "*AddressHistory*" --tests "*SyncDisplay*"`

## Flows Maestro de spécification (`.maestro/build199/`, hors boucle CI racine)
Ils **échouent tant que le paquet v176g n'est pas livré** : ils fixent les `testTag` attendus. Devin les rend verts puis les
déplace à la racine `.maestro/`. Tous effacent les données (clearState) : sur le Pixel, sauvegarde run-as + tar avant, restauration après.

| Flow | Point | Nouveaux testTags exigés |
|---|---|---|
| `b199_sync_etat_partage.yaml` | 2 | `synchro_bandeau_connexion`, `accueil_synchro_active`, `session_synchro_etat`, `session_synchro_journal` |
| `b199_sync_nom_esclave_simule.yaml` | 1 | réglage debug `lumisite_simuler_esclave`, `synchro_esclave_1_connexion`, `synchro_esclave_1_dernier` |
| `b199_ar_menus_stables.yaml` | 5, 6 | `session_ar_support_auto` ; disparition de `session_ar_surface_*` |
| `b199_carte_annoter.yaml` | 7, 9 | `session_camera_repliee`, `session_carte_plein_ecran` |
| `b199_adresses_recentes.yaml` | 3 | `nouveau_site_adresse_recente_N` |
| `b199_releve_catalogue.yaml` | 8 | `session_releve_categorie_<FAMILLE>`, `session_releve_fil_ariane`, `session_releve_type_<CODE>`, `session_releve_recherche`, `session_releve_ajouter_type`, `session_releve_nouveau_type_nom`, `session_releve_nouveau_type_valider` |

À adapter quand le sélecteur disparaît : `.maestro/v176_ar_contours.yaml` (il touche `session_ar_surface_plafond` / `_tous`).
À garder vert sans modification : `.maestro/v176_nouveau_site_rectangle.yaml` (point 4, rectangle « impeccable »).

## Vérifications manuelles (Pixel 7 maître + Xiaomi 13T Pro esclave, après 23 h)
1. Maître : le nom « Xiaomi 13T Pro (xxxx) » et « connecté » en vert en moins de 20 s ; retour Accueil puis Session : la synchro tourne toujours, même journal.
2. Écran éteint 2 min : la connexion survit (service de premier plan) ou se rétablit seule.
3. AR : 4 drapeaux posés, on tourne de 180° puis on revient : ils réapparaissent (gris si suivi dégradé). Les boutons sous la caméra ne bougent pas pendant 10 s de visée.
4. Inclinaison : vers le sol / le mur / le plafond, la ligne « Visée : … (auto) » change sans clignoter.
