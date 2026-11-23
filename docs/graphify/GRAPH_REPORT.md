# Graph Report - eclairage  (2026-11-23)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2378 nodes · 6582 edges · 152 communities (69 shown, 83 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 64 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4488a789`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Community 0
- Community 1
- Community 2
- Community 3
- Community 4
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9
- Community 10
- Community 11
- Community 12
- Community 13
- Community 14
- Community 15
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 22
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 41
- Community 42
- Community 43
- Community 44
- Community 45
- Community 46
- Community 47
- Community 48
- Community 49
- Community 50
- Community 51
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56
- Community 57
- Community 58
- Community 59
- Community 60
- Community 61
- Community 62
- Community 63
- Community 64
- Community 65
- Community 66
- Community 67
- Community 68
- Community 69
- Community 70
- Community 71
- Community 72
- Community 73
- Community 74
- Community 75
- Community 76
- Community 77
- Community 78
- Community 79
- Community 80
- Community 81
- Community 82
- Community 83
- Community 84
- Community 85
- Community 87
- Community 88
- Community 89
- Community 90
- Community 91
- Community 92
- Community 94
- Community 95
- Community 96
- Community 97
- Community 98
- Community 99
- Community 100
- Community 101
- Community 102
- Community 103
- Community 104
- Community 105
- Community 106
- Community 107
- Community 108
- Community 109
- Community 110
- Community 111
- Community 112
- Community 113
- Community 114
- Community 115
- Community 116
- Community 117
- Community 118
- Community 119
- Community 120
- Community 121
- Community 122
- Community 123
- Community 124
- Community 125
- Community 126
- Community 127
- Community 128
- Community 129
- Community 130
- Community 131
- Community 132
- Community 133
- Community 134
- Community 135
- Community 136
- Community 137
- Community 138
- Community 139
- Community 140
- Community 141
- Community 142
- Community 143

## God Nodes (most connected - your core abstractions)
1. `Enu` - 126 edges
2. `LatLon` - 68 edges
3. `BBox` - 66 edges
4. `NearbyMesh` - 65 edges
5. `V3` - 62 edges
6. `Vec2` - 59 edges
7. `SessionEvent` - 59 edges
8. `Stamp` - 51 edges
9. `SurveyEvent` - 49 edges
10. `LocalTangentPlane` - 45 edges

## Surprising Connections (you probably didn't know these)
- `ForcibleControlPoint` --references--> `ControlPointCheck`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/position/CalibrationChecks.kt
- `LiveState` --references--> `Solution`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/position/Positioning.kt
- `ExportResult` --references--> `SessionManifest`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/data/Repository.kt → core/src/main/kotlin/app/lumisite/core/export/Export.kt
- `SiteScreen()` --references--> `Site`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/ui/Screens.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt
- `Heard` --references--> `AudioDetection`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/capture/AudioSync.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt

## Import Cycles
- None detected.

## Communities (152 total, 83 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (21): Similarity2D, Vec2, CalibrationQuality, Grade, Level, BON, INSUFFISANT, MOYEN (+13 more)

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (14): ClockRef, Ref, FormatVersions, SessionExporter, Progress, sha256(), ClockRefTest, Format01CompatTest (+6 more)

### Community 2 - "Community 2"
Cohesion: 0.06
Nodes (13): CatalogStore, CatalogEntry, CatalogFamily, EquipmentCatalog, LegacyKinds, Target, HEIGHT_OR_TEXT, TEXT_ANNOTATION (+5 more)

### Community 3 - "Community 3"
Cohesion: 0.05
Nodes (23): arrowIcon(), dot(), FingerPhase, CANCEL, DOWN, MOVE, UP, Gesture (+15 more)

### Community 4 - "Community 4"
Cohesion: 0.07
Nodes (15): AudioSync, Heard, Round, ChirpDetection, ChirpDetector, DeviceSyncLog, IdFrame, Decoded (+7 more)

### Community 5 - "Community 5"
Cohesion: 0.05
Nodes (17): Ids, RecentZone, MainActivity, Screen, Home, Live, NewSite, Settings (+9 more)

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (7): SessionImporter, CaptationWriter, sealPartialLine(), Streams, ImuSample, CaptationJournalTest, LongExportMemoryTest

### Community 7 - "Community 7"
Cohesion: 0.12
Nodes (4): NearbyMesh, TopOffset, SettingsScreen(), SyncSaved()

### Community 9 - "Community 9"
Cohesion: 0.11
Nodes (4): SyncMethod, AUDIO_CHIRP, MANUAL_TAP, Chirp

### Community 10 - "Community 10"
Cohesion: 0.09
Nodes (18): ExportEntryRecord, ExportInput, ResumableExport, FilterInputStream, MediaSummary, Progress, Result, Step (+10 more)

### Community 11 - "Community 11"
Cohesion: 0.08
Nodes (13): Copied, copy(), displayPath(), mimeOf(), relativePath(), safe(), siteFolder(), write() (+5 more)

### Community 12 - "Community 12"
Cohesion: 0.16
Nodes (31): ControlRow, DebugFlags, Attachment, AudioDetection, CalibrationEvent, CalibrationMethod, MULTI_POINT, ONE_POINT_HEADING (+23 more)

### Community 13 - "Community 13"
Cohesion: 0.12
Nodes (27): apply_validated(), apply_decisions(), clock_offset_from_chirps(), emitted_chirps_utc(), extract(), families_in(), iso_to_s(), main() (+19 more)

### Community 15 - "Community 15"
Cohesion: 0.12
Nodes (18): ActionCell(), CalibratePanel(), CameraFrame(), ControlTable(), FamilyGrid(), ForceOverride(), localUri(), NudgeCross() (+10 more)

### Community 16 - "Community 16"
Cohesion: 0.07
Nodes (13): LumiTheme(), MapFilters, MapTone, DARK, NORMAL, RED, themeBackground(), ThemeMode (+5 more)

### Community 17 - "Community 17"
Cohesion: 0.12
Nodes (9): Angles, LatLon, LocalTangentPlane, Wgs84, Ecef, DragMove, ScreenProjection, DragMoveTest (+1 more)

### Community 18 - "Community 18"
Cohesion: 0.16
Nodes (8): TrajectoryCorrector, Segment, Summary, SurveyPositions, Resolved, ReferencePoint, ResumeSessionTest, SurveyPositionsTest

### Community 19 - "Community 19"
Cohesion: 0.14
Nodes (13): Aim, Plane, Ray, At, Grazing, Miss, AimResult, Grazing (+5 more)

### Community 20 - "Community 20"
Cohesion: 0.09
Nodes (26): ArFrameRef, ArPoint, label(), PositionMethod, AR_CAMERA, AR_FLOOR_ESTIMATE, AR_LAST_PLANE, AR_PLANE (+18 more)

### Community 21 - "Community 21"
Cohesion: 0.12
Nodes (4): AddressStore, AddressHistory, Entry, AddressHistoryTest

### Community 22 - "Community 22"
Cohesion: 0.14
Nodes (5): OsmClient, Fetched, BBox, OsmSnapshot, Bboxes

### Community 23 - "Community 23"
Cohesion: 0.16
Nodes (17): run(), fit_similarity(), l93_to_lonlat(), dxf_to_features(), feature(), make_package(), make_template(), args() (+9 more)

### Community 24 - "Community 24"
Cohesion: 0.11
Nodes (14): SyncDisplay, Banner, Tone, ORANGE, ROUGE, VERT, Frame, NOT_SHARED (+6 more)

### Community 25 - "Community 25"
Cohesion: 0.20
Nodes (7): Calibrator, PoseGeometry, Relocalizer, LocalObservation, PoseSample, CalibrationTest, ResumableExportTest

### Community 26 - "Community 26"
Cohesion: 0.16
Nodes (23): UiState, Capture, CAPTURING, IDLE, STOPPED, MeshMsg, ClockInfo, Hello (+15 more)

### Community 27 - "Community 27"
Cohesion: 0.11
Nodes (7): DrawPanel(), FamilyQuickGrid(), StyleSheet(), ToolButton(), drawPlanSymbol(), PlanSymbol(), ToolIcons

### Community 28 - "Community 28"
Cohesion: 0.16
Nodes (6): DrawState, SelectedShape(), shapeLines(), shapePoints(), ShapeEvent, Shapes

### Community 29 - "Community 29"
Cohesion: 0.18
Nodes (4): ArPlanes, Edge, Info, ArPlanesTest

### Community 30 - "Community 30"
Cohesion: 0.14
Nodes (5): CorrectedPoint, MirrorLedger, GrowingSegmentSimplifier, Simplify, FluiditeTest

### Community 31 - "Community 31"
Cohesion: 0.11
Nodes (13): SiteRects, Handle, E, MOVE, N, NE, NW, S (+5 more)

### Community 33 - "Community 33"
Cohesion: 0.15
Nodes (18): _add_annotation(), _attr_values(), DxfOptions, DxfStats, _ensure_attdefs(), generate(), _legend(), _remove_geodata() (+10 more)

### Community 34 - "Community 34"
Cohesion: 0.13
Nodes (9): PositionStatus, Source, AR, GPS, MANUAL, NONE, OsmFeature, OsmSelection (+1 more)

### Community 35 - "Community 35"
Cohesion: 0.15
Nodes (7): Releves, CrsInfo, Device, SessionHeader, TimeBase, Fixtures, RebootOrderTest

### Community 36 - "Community 36"
Cohesion: 0.13
Nodes (9): collect(), _lonlat(), main(), osm_rings(), _read_pairs(), to_dwg(), lonlat_to_l93(), Package (+1 more)

### Community 37 - "Community 37"
Cohesion: 0.15
Nodes (17): PlanSymbols, Arc, Circle, Frame, CAMERA_BODY, CIRCLE, DIAMOND, HEXAGON (+9 more)

### Community 38 - "Community 38"
Cohesion: 0.15
Nodes (8): DerivedSummary, DerivedSurveyPoint, DxfPreparation, FileEntry, hex(), LoadedExport, SessionManifest, Frames

### Community 39 - "Community 39"
Cohesion: 0.17
Nodes (8): OsmGuard, Failure, BBOX_TOO_LARGE, OTHER, RATE_LIMITED, TOO_MANY_NODES, OsmHttpException, OsmApiTest

### Community 41 - "Community 41"
Cohesion: 0.20
Nodes (4): AudioOffset, HeardChirp, SyncRoundCalc, SyncRoundTest

### Community 42 - "Community 42"
Cohesion: 0.19
Nodes (8): Clock, ALIGNED, ALIGNING, NOT_ALIGNED, ClockAlign, ClockSample, MasterMesh, SyncMeshTest

### Community 43 - "Community 43"
Cohesion: 0.14
Nodes (3): aimState(), ArControls(), ArReticle()

### Community 44 - "Community 44"
Cohesion: 0.15
Nodes (3): BoundedApproximater, SiteTileProvider, TileCachePolicy

### Community 46 - "Community 46"
Cohesion: 0.21
Nodes (8): ArSiteOverlay, Group, Item, Kind, LUMINAIRE, REPERE, Scene, ArSiteOverlayTest

### Community 48 - "Community 48"
Cohesion: 0.21
Nodes (6): HubElection, MultiDeviceAlign, ClockModel, Detection, Result, MultiDeviceSyncTest

### Community 49 - "Community 49"
Cohesion: 0.15
Nodes (10): cadastreTileSource(), OnlineTileSourceBase, tileSourceFor(), OnlineTileSourceBase, OnlineTileSourceBase, Basemap, ESRI, IGN_ORTHO (+2 more)

### Community 50 - "Community 50"
Cohesion: 0.24
Nodes (4): GeoJsonSeq, GeoJsonSeqWriter, ReadResult, GeoJsonExportTest

### Community 51 - "Community 51"
Cohesion: 0.21
Nodes (8): alias_map(), _dwg_name(), _find_root(), load_package(), normalise(), read_geojsonl(), sha256(), test_catalog2_codes_follow_pending_renames()

### Community 52 - "Community 52"
Cohesion: 0.24
Nodes (6): Support, MUR, PLAFOND, SOL, SupportFromTilt, SupportFromTiltTest

### Community 53 - "Community 53"
Cohesion: 0.21
Nodes (9): bundle(), e(), i(), init(), memory(), rotate(), screen(), set (+1 more)

### Community 54 - "Community 54"
Cohesion: 0.27
Nodes (5): CallCapReached, FormPoster, MirrorFetcher, Overpass, OverpassTest

### Community 56 - "Community 56"
Cohesion: 0.14
Nodes (6): azimuth_to_rotation(), convergence_deg(), local_to_lonlat(), Similarity, test_azimuth_reference_on_a_meridian(), test_convergence_and_rotation()

### Community 57 - "Community 57"
Cohesion: 0.27
Nodes (3): downloadZone(), TileMath, BasemapsTest

### Community 58 - "Community 58"
Cohesion: 0.13
Nodes (15): with, api-level, arch, disable-animations, distribution, emulator-options, fetch-depth, include-hidden-files (+7 more)

### Community 59 - "Community 59"
Cohesion: 0.14
Nodes (14): CalibMark, ForcibleControlPoint, LiveState, TrackingState, DEGRADED, INITIALIZING, LOST, STOPPED (+6 more)

### Community 61 - "Community 61"
Cohesion: 0.25
Nodes (4): chirp(), detect_chirps(), read_audio(), test_chirps_found_in_long_recording()

### Community 63 - "Community 63"
Cohesion: 0.21
Nodes (5): Sketch, PolygonTap, ADD, CLOSE, IGNORE

### Community 64 - "Community 64"
Cohesion: 0.32
Nodes (10): MasterPanel(), Prerequisites(), rememberMonoNow(), rememberSyncStarter(), SceneActive(), SlavePanel(), SyncBanner(), SyncJournal() (+2 more)

### Community 66 - "Community 66"
Cohesion: 0.24
Nodes (6): SyncRole, EMITTER, PARTICIPANT, RECEIVER, SyncReciprocity, SyncReciprocityTest

### Community 67 - "Community 67"
Cohesion: 0.33
Nodes (7): OsmElements, OsmMember, OsmNode, OsmRelation, OsmWay, parse(), tags()

### Community 68 - "Community 68"
Cohesion: 0.24
Nodes (12): steps, GH_TOKEN, MAESTRO, N_ETAPE, steps, steps, env, id (+4 more)

### Community 74 - "Community 74"
Cohesion: 0.20
Nodes (10): outputs, runs-on, timeout-minutes, apk, apk, n, pkt, short (+2 more)

### Community 76 - "Community 76"
Cohesion: 0.22
Nodes (9): Tool, ANNOTATION, COMMENT, LINE, MOVE, PLACE, POLYGON, SELECT (+1 more)

### Community 79 - "Community 79"
Cohesion: 0.22
Nodes (9): publier_sans_maestro, on, push, workflow_dispatch, default, description, type, branches (+1 more)

### Community 80 - "Community 80"
Cohesion: 0.25
Nodes (8): Mode, CALIBRATE, DRAW, EXPORT, NONE, RELOCALIZE, SURVEY, SYNC

### Community 81 - "Community 81"
Cohesion: 0.25
Nodes (8): EdgeKind, ANGLE, CORNICHE, PLINTHE, Kind, CEILING, FLOOR, WALL

### Community 83 - "Community 83"
Cohesion: 0.25
Nodes (7): concurrency, cancel-in-progress, group, jobs, name, permissions, contents

### Community 87 - "Community 87"
Cohesion: 0.50
Nodes (4): AnchorKind, FLAG, GROUP, LUMINAIRE

### Community 90 - "Community 90"
Cohesion: 0.50
Nodes (3): MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED, MAESTRO_CLI_NO_ANALYTICS, maestro-ci.sh script

### Community 91 - "Community 91"
Cohesion: 0.50
Nodes (4): maestro, needs, runs-on, timeout-minutes

### Community 92 - "Community 92"
Cohesion: 0.50
Nodes (4): release, if, needs, runs-on

## Knowledge Gaps
- **145 isolated node(s):** `Grazing`, `Miss`, `Grazing`, `NoTarget`, `ToComplete` (+140 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 577 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **83 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Enu` connect `Community 47` to `Community 0`, `Community 1`, `Community 6`, `Community 8`, `Community 9`, `Community 12`, `Community 15`, `Community 17`, `Community 18`, `Community 20`, `Community 25`, `Community 27`, `Community 28`, `Community 30`, `Community 34`, `Community 35`, `Community 38`, `Community 45`, `Community 46`, `Community 50`, `Community 59`, `Community 63`, `Community 65`, `Community 72`, `Community 73`, `Community 82`?**
  _High betweenness centrality (0.090) - this node is a cross-community bridge._
- **What connects `Grazing`, `Miss`, `Grazing` to the rest of the system?**
  _145 weakly-connected nodes found - possible missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05948295584534431 - nodes in this community are weakly interconnected._
- **Why does `BBox` connect `Community 22` to `Community 0`, `Community 34`, `Community 3`, `Community 67`, `Community 5`, `Community 70`, `Community 6`, `Community 39`, `Community 9`, `Community 11`, `Community 44`, `Community 45`, `Community 12`, `Community 46`, `Community 17`, `Community 54`, `Community 57`, `Community 31`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.06572769953051644 - nodes in this community are weakly interconnected._
- **Why does `LatLon` connect `Community 17` to `Community 0`, `Community 34`, `Community 3`, `Community 35`, `Community 5`, `Community 67`, `Community 39`, `Community 9`, `Community 11`, `Community 12`, `Community 45`, `Community 46`, `Community 15`, `Community 18`, `Community 22`, `Community 30`, `Community 31`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.06060606060606061 - nodes in this community are weakly interconnected._