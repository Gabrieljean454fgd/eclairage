# Contrat de données LumiSite (LOT 1.0)

Code : `core/src/main/kotlin/app/lumisite/core/contract/Contract.kt` (tests : `ContractTest`).

## Unités
- Longueurs en **mètres**, angles en **radians** (les degrés ne servent qu'à l'affichage), temps en **nanosecondes**
  d'horloge monotone (`t_mono_ns` / `monotonicNs`), `utcEpochMs` pour l'affichage seulement.

## Repères (jamais mélangés en silence)
| Repère | Type Kotlin | Axes | Validité |
|---|---|---|---|
| Site / plan | `SitePoint` (`Enu` dans le journal) | E (est), N (nord) en m depuis l'origine du site | permanent |
| Session AR | `ArPoint` + `ArFrameRef(timeBaseId, epoch)` | x, y (haut), z d'ARCore en m | une base de temps et une époque (démarrage ou reprise) |

- Seule conversion : `SiteLink(segment).toSite(arPoint)`. Elle refuse un point d'une autre base de temps ou d'une autre
  époque. Chaque segment (`TrajectoryCorrector.Segment`) porte sa transformation, sa base de temps et son époque ; une
  reprise ouvre une nouvelle époque sans effacer les segments précédents (D13).
- Les ancres ARCore ne sont pas une sauvegarde : un objet est persisté avec sa pose AR brute et sa position site
  recalculable par la transformation de **son** segment.

## Hauteur
- `SiteHeight.AboveFloor(m, solRef)` seulement si un sol de référence est connu ; sinon `SiteHeight.Unqualified`,
  affiché « hauteur non qualifiée ». Le `y` AR n'est jamais présenté comme une hauteur.

## Provenance de chaque position
`PositionProvenance` : méthode (`PositionMethod`, depuis `positionSource` du journal), `t_mono_ns`, base de temps,
repère (`site:map`, `site:<id de la transformation>` ou `none`) et qualité (`bonne`, `moyenne`, `faible`, `manuelle`,
`aucune`). Elle est écrite dans `releves.geojson` (`properties.provenance`).

## Identifiants stables
- `site.id`, `session.id`, `device.id`, `timeBase.id`, `event.id` (préfixés, jamais réutilisés). Une modification
  d'objet est un nouvel événement `supersedes` → l'id GeoJSON reste celui de la première version (`rev` croissant).

## Temps et synchro future (sans l'implémenter)
- Chaque flux enregistre `t_mono_ns` et l'id de sa base de temps ; chaque changement de base de temps est un événement
  `TimeBaseAdded`. Les interruptions (photo, pause, appel, écran éteint) sont des `StreamStateEvent` STOPPED/RESUMED
  et des `TrackingStateChanged` : des **trous explicites**, jamais une continuité. Le recalage multi-appareils pourra
  se faire après coup (`t_principal = a·t_secondaire + b`) sans refaire les enregistrements.

## Persistance
- Journal append-only (segments `seg/*.ndjson`, bientôt `.ndjson.gz` une fois fermés), pas de base Room. Aucune
  migration destructive : les anciens fichiers racine (`events`, `poses`, …) restent lus.
