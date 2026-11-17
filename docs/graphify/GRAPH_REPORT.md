# Graph Report - eclairage  (2026-11-17)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2046 nodes · 5675 edges · 125 communities (62 shown, 63 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 51 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `97b2ae60`
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
- Community 40
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
- Community 75
- Community 76
- Community 78
- Community 79
- Community 80
- Community 81
- Community 82
- Community 83
- Community 84
- Community 85
- Community 86
- Community 87
- Community 88
- Community 89
- Community 90
- Community 91
- Community 92
- Community 93
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

## God Nodes (most connected - your core abstractions)
1. `Enu` - 115 edges
2. `LatLon` - 64 edges
3. `BBox` - 62 edges
4. `Vec2` - 57 edges
5. `SessionEvent` - 57 edges
6. `Stamp` - 49 edges
7. `SurveyEvent` - 47 edges
8. `V3` - 40 edges
9. `LocalTangentPlane` - 39 edges
10. `ShapeEvent` - 38 edges

## Surprising Connections (you probably didn't know these)
- `FamilyQuickGrid()` --calls--> `label()`  [INFERRED]
  app/src/main/java/app/lumisite/android/ui/DrawTools.kt → core/src/main/kotlin/app/lumisite/core/contract/Contract.kt
- `ForcibleControlPoint` --references--> `ControlPointCheck`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/position/CalibrationChecks.kt
- `LiveState` --references--> `GnssQuality`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/position/Positioning.kt
- `LiveState` --references--> `Solution`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/position/Positioning.kt
- `RecentZone` --references--> `BBox`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/data/Repository.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt

## Import Cycles
- None detected.

## Communities (125 total, 63 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.05
Nodes (25): Similarity2D, Vec2, CalibrationQuality, Grade, Level, BON, INSUFFISANT, MOYEN (+17 more)

### Community 1 - "Community 1"
Cohesion: 0.05
Nodes (25): Copied, copy(), displayPath(), mimeOf(), relativePath(), safe(), siteFolder(), write() (+17 more)

### Community 3 - "Community 3"
Cohesion: 0.05
Nodes (15): arrowIcon(), dot(), FingerPhase, CANCEL, DOWN, MOVE, UP, Gesture (+7 more)

### Community 4 - "Community 4"
Cohesion: 0.10
Nodes (15): FormatVersions, LoadedExport, SessionImporter, Streams, DxfHint, GpsFix, GpsStatusSample, ImuSample (+7 more)

### Community 5 - "Community 5"
Cohesion: 0.09
Nodes (15): Aim, Plane, Ray, At, Grazing, Miss, AimResult, Grazing (+7 more)

### Community 6 - "Community 6"
Cohesion: 0.12
Nodes (35): CalibMark, ControlRow, DebugFlags, ForcibleControlPoint, LiveState, CrsInfo, Attachment, AudioDetection (+27 more)

### Community 7 - "Community 7"
Cohesion: 0.09
Nodes (18): ExportEntryRecord, ExportInput, ResumableExport, FilterInputStream, MediaSummary, Progress, Result, Step (+10 more)

### Community 8 - "Community 8"
Cohesion: 0.10
Nodes (23): shapeLines(), MapArrow, MapContent, MapLine, MapPoint, ActionCell(), CalibratePanel(), CameraFrame() (+15 more)

### Community 9 - "Community 9"
Cohesion: 0.08
Nodes (11): CatalogStore, CatalogEntry, EquipmentCatalog, LegacyKinds, Target, HEIGHT_OR_TEXT, TEXT_ANNOTATION, UNCLASSIFIED (+3 more)

### Community 10 - "Community 10"
Cohesion: 0.12
Nodes (25): apply_decisions(), clock_offset_from_chirps(), emitted_chirps_utc(), extract(), families_in(), iso_to_s(), main(), _norm() (+17 more)

### Community 11 - "Community 11"
Cohesion: 0.12
Nodes (12): Chirp, ChirpDetection, ChirpDetector, DeviceSyncLog, IdFrame, Decoded, MultiChirp, Heard (+4 more)

### Community 12 - "Community 12"
Cohesion: 0.07
Nodes (14): Ids, MainActivity, Screen, Home, Live, NewSite, Settings, SiteDetail (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.07
Nodes (13): LumiTheme(), MapFilters, MapTone, DARK, NORMAL, RED, themeBackground(), ThemeMode (+5 more)

### Community 14 - "Community 14"
Cohesion: 0.11
Nodes (9): GeoJsonSeq, TrackingState, DEGRADED, INITIALIZING, LOST, STOPPED, TRACKING, UNAVAILABLE (+1 more)

### Community 15 - "Community 15"
Cohesion: 0.17
Nodes (8): Calibrator, PoseGeometry, Relocalizer, LocalObservation, ReferencePoint, CalibrationTest, RebootOrderTest, ResumeSessionTest

### Community 16 - "Community 16"
Cohesion: 0.12
Nodes (9): Angles, LatLon, LocalTangentPlane, Wgs84, Ecef, DragMove, ScreenProjection, DragMoveTest (+1 more)

### Community 17 - "Community 17"
Cohesion: 0.17
Nodes (7): NearbyMesh, UiState, MasterPanel(), Prerequisites(), SceneActive(), SlavePanel(), SyncScreen()

### Community 18 - "Community 18"
Cohesion: 0.07
Nodes (4): CaptationWriter, NdjsonAppender, ReadResult, sealPartialLine()

### Community 19 - "Community 19"
Cohesion: 0.10
Nodes (25): ArFrameRef, ArPoint, label(), PositionMethod, AR_CAMERA, AR_FLOOR_ESTIMATE, AR_LAST_PLANE, AR_PLANE (+17 more)

### Community 21 - "Community 21"
Cohesion: 0.17
Nodes (6): OsmClient, Fetched, BBox, OsmSnapshot, Bboxes, OsmGuard

### Community 22 - "Community 22"
Cohesion: 0.16
Nodes (7): Enu, Sketch, PolygonTap, ADD, CLOSE, IGNORE, Zones

### Community 23 - "Community 23"
Cohesion: 0.14
Nodes (15): l93_to_lonlat(), alias_map(), _find_root(), load_package(), normalise(), read_geojsonl(), sha256(), dxf_to_features() (+7 more)

### Community 24 - "Community 24"
Cohesion: 0.21
Nodes (5): TrajectoryCorrector, Segment, Summary, PoseSample, SurveyPositionsTest

### Community 25 - "Community 25"
Cohesion: 0.15
Nodes (6): CallCapReached, OsmHttpException, FormPoster, MirrorFetcher, Overpass, OverpassTest

### Community 26 - "Community 26"
Cohesion: 0.14
Nodes (10): apply_validated(), collect(), _lonlat(), main(), osm_rings(), _read_pairs(), run(), to_dwg() (+2 more)

### Community 27 - "Community 27"
Cohesion: 0.14
Nodes (5): CorrectedPoint, MirrorLedger, GrowingSegmentSimplifier, Simplify, FluiditeTest

### Community 28 - "Community 28"
Cohesion: 0.11
Nodes (13): SiteRects, Handle, E, MOVE, N, NE, NW, S (+5 more)

### Community 29 - "Community 29"
Cohesion: 0.08
Nodes (4): AnchorKind, FLAG, GROUP, LUMINAIRE

### Community 31 - "Community 31"
Cohesion: 0.12
Nodes (4): BoundedApproximater, MapPrefs, SiteTileProvider, TileCachePolicy

### Community 32 - "Community 32"
Cohesion: 0.14
Nodes (9): PositionStatus, Source, AR, GPS, MANUAL, NONE, OsmFeature, OsmSelection (+1 more)

### Community 33 - "Community 33"
Cohesion: 0.17
Nodes (5): SurveyPositions, Resolved, Releves, SurveyEvent, ResumableExportTest

### Community 34 - "Community 34"
Cohesion: 0.22
Nodes (13): _az_diff(), merge(), MergeResult, Obj, make_template(), args(), ins_by_id(), test_alias_one_block_name() (+5 more)

### Community 35 - "Community 35"
Cohesion: 0.20
Nodes (5): SelectedShape(), shapePoints(), ShapeEvent, Shapes, ShapesDetailsTest

### Community 36 - "Community 36"
Cohesion: 0.12
Nodes (18): AudioOffset, Capture, CAPTURING, IDLE, STOPPED, Frame, NOT_SHARED, Link (+10 more)

### Community 37 - "Community 37"
Cohesion: 0.19
Nodes (6): Clock, Stamp, StampFactory, TimeMath, TimeTest, Clock

### Community 38 - "Community 38"
Cohesion: 0.12
Nodes (3): GpsRecorder, SensorRecorder, SensorEventListener

### Community 39 - "Community 39"
Cohesion: 0.10
Nodes (10): Tool, ANNOTATION, COMMENT, LINE, MOVE, PLACE, POLYGON, SELECT (+2 more)

### Community 40 - "Community 40"
Cohesion: 0.18
Nodes (6): Failure, BBOX_TOO_LARGE, OTHER, RATE_LIMITED, TOO_MANY_NODES, OsmApiTest

### Community 41 - "Community 41"
Cohesion: 0.22
Nodes (6): HubElection, MultiDeviceAlign, ClockModel, Detection, Result, MultiDeviceSyncTest

### Community 42 - "Community 42"
Cohesion: 0.18
Nodes (8): Clock, ALIGNED, ALIGNING, NOT_ALIGNED, ClockAlign, ClockSample, MasterMesh, SyncMeshTest

### Community 43 - "Community 43"
Cohesion: 0.13
Nodes (8): azimuth_to_rotation(), convergence_deg(), fit_similarity(), local_to_lonlat(), lonlat_to_l93(), Similarity, test_azimuth_reference_on_a_meridian(), test_convergence_and_rotation()

### Community 44 - "Community 44"
Cohesion: 0.15
Nodes (3): aimState(), ArControls(), ArReticle()

### Community 45 - "Community 45"
Cohesion: 0.15
Nodes (10): cadastreTileSource(), OnlineTileSourceBase, tileSourceFor(), OnlineTileSourceBase, OnlineTileSourceBase, Basemap, ESRI, IGN_ORTHO (+2 more)

### Community 46 - "Community 46"
Cohesion: 0.24
Nodes (4): HeardChirp, SyncPrereq, SyncRoundCalc, SyncRoundTest

### Community 47 - "Community 47"
Cohesion: 0.24
Nodes (3): downloadZone(), TileMath, BasemapsTest

### Community 48 - "Community 48"
Cohesion: 0.27
Nodes (5): DrawPanel(), DrawState, FamilyQuickGrid(), StyleSheet(), ToolButton()

### Community 50 - "Community 50"
Cohesion: 0.23
Nodes (9): bundle(), e(), i(), init(), memory(), rotate(), screen(), set (+1 more)

### Community 51 - "Community 51"
Cohesion: 0.13
Nodes (15): with, api-level, arch, disable-animations, distribution, emulator-options, fetch-depth, include-hidden-files (+7 more)

### Community 52 - "Community 52"
Cohesion: 0.28
Nodes (11): _add_annotation(), _attr_values(), DxfOptions, DxfStats, _ensure_attdefs(), generate(), _legend(), _remove_geodata() (+3 more)

### Community 53 - "Community 53"
Cohesion: 0.25
Nodes (4): chirp(), detect_chirps(), read_audio(), test_chirps_found_in_long_recording()

### Community 55 - "Community 55"
Cohesion: 0.29
Nodes (7): OsmElements, OsmMember, OsmNode, OsmRelation, OsmWay, parse(), tags()

### Community 56 - "Community 56"
Cohesion: 0.40
Nodes (11): MeshMsg, Hello, Media, Ping, Pong, Sensors, StartCapture, Status (+3 more)

### Community 58 - "Community 58"
Cohesion: 0.24
Nodes (6): SyncRole, EMITTER, PARTICIPANT, RECEIVER, SyncReciprocity, SyncReciprocityTest

### Community 59 - "Community 59"
Cohesion: 0.17
Nodes (11): concurrency, cancel-in-progress, group, jobs, maestro, needs, runs-on, timeout-minutes (+3 more)

### Community 64 - "Community 64"
Cohesion: 0.27
Nodes (11): steps, GH_TOKEN, MAESTRO, steps, steps, env, id, if (+3 more)

### Community 66 - "Community 66"
Cohesion: 0.31
Nodes (3): AudioSync, Heard, Round

### Community 68 - "Community 68"
Cohesion: 0.22
Nodes (9): outputs, runs-on, timeout-minutes, apk, apk, n, short, tag (+1 more)

### Community 69 - "Community 69"
Cohesion: 0.22
Nodes (9): publier_sans_maestro, on, push, workflow_dispatch, default, description, type, branches (+1 more)

### Community 70 - "Community 70"
Cohesion: 0.25
Nodes (8): Mode, CALIBRATE, DRAW, EXPORT, NONE, RELOCALIZE, SURVEY, SYNC

### Community 75 - "Community 75"
Cohesion: 0.50
Nodes (3): MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED, MAESTRO_CLI_NO_ANALYTICS, maestro-ci.sh script

### Community 76 - "Community 76"
Cohesion: 0.50
Nodes (4): release, if, needs, runs-on

### Community 78 - "Community 78"
Cohesion: 0.67
Nodes (3): TapMode, ORIGIN, RECT

## Knowledge Gaps
- **121 isolated node(s):** `Home`, `NewSite`, `Settings`, `Synchro`, `AboveFloor` (+116 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 520 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **63 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `BBox` connect `Community 21` to `Community 32`, `Community 1`, `Community 2`, `Community 3`, `Community 65`, `Community 0`, `Community 6`, `Community 8`, `Community 40`, `Community 28`, `Community 47`, `Community 55`, `Community 25`, `Community 60`, `Community 31`?**
  _High betweenness centrality (0.071) - this node is a cross-community bridge._
- **What connects `Home`, `NewSite`, `Settings` to the rest of the system?**
  _121 weakly-connected nodes found - possible missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05193171608265948 - nodes in this community are weakly interconnected._
- **Why does `Enu` connect `Community 22` to `Community 0`, `Community 1`, `Community 2`, `Community 4`, `Community 5`, `Community 6`, `Community 8`, `Community 14`, `Community 15`, `Community 16`, `Community 18`, `Community 19`, `Community 24`, `Community 27`, `Community 32`, `Community 33`, `Community 35`, `Community 48`, `Community 49`, `Community 54`, `Community 61`, `Community 63`, `Community 65`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.05350140056022409 - nodes in this community are weakly interconnected._
- **Why does `LatLon` connect `Community 16` to `Community 0`, `Community 32`, `Community 2`, `Community 3`, `Community 1`, `Community 65`, `Community 6`, `Community 8`, `Community 40`, `Community 12`, `Community 15`, `Community 21`, `Community 55`, `Community 25`, `Community 27`, `Community 28`?**
  _High betweenness centrality (0.057) - this node is a cross-community bridge._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.05128205128205128 - nodes in this community are weakly interconnected._