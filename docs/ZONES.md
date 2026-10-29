# Zones (lot 13)

**Support choisi** : `ShapeEvent(kind = "zone")` existant (lot 7) + champs **optionnels** `zoneKind`, `parentZoneId`, `holes`, `source`. Pas de passage du format en 0.2 : une session 0.1 se relit sans changement (test `ZonesTest.oldShapeWithoutZoneFieldsStillReads`).

- `zone_id` = `id` de la forme ; `label` ; `zone_kind` ∈ ARCHITECTURAL, TECHNIQUE, CIRCULATION, EXTERIEUR (ARCHITECTURAL par défaut au tracé, modifiable plus tard) ; `parent_zone_id` ; `source` = MANUAL_MAP (PLAN_IMPORT plus tard, AR_WALK repoussé).
- Anneaux en mètres du repère du site (X est, Y nord). **Sens des sommets** : quelconque à la saisie, fermeture implicite (dernier ≠ premier) ; `Zones.signedArea > 0` = sens trigonométrique. L'export GeoJSON reprend les sommets dans l'ordre saisi ; les trous (cour du cloître) sont les anneaux suivants du Polygon.
- **Rattachement calculé, jamais stocké** : `Zones.zoneOf(point, zones)` = point dans le polygone (pair-impair, trous exclus), la zone la plus profonde l'emporte (profondeur = parent + 1 si `parent_zone_id`, sinon nombre de zones qui la contiennent). Un point exactement sur un bord peut tomber d'un côté ou de l'autre.
- **Polygone qui se recoupe** : refusé à la saisie (« Zone refusée : le polygone se recoupe. Retracez-le. »), jamais corrigé en silence.

Reste à faire : choix du `zone_kind` et du parent dans l'interface, tracé des trous à l'écran, colonne « zone » dans derived/ et dans l'outil de bureau.
