# Graph Report - eclairage  (2026-11-26)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2742 nodes · 7947 edges · 170 communities (84 shown, 86 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 68 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `048c0396`
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
- Community 144
- Community 145
- Community 146
- Community 147
- Community 148
- Community 149
- Community 150
- Community 151
- Community 152
- Community 153
- Community 154
- Community 155
- Community 156
- Community 157
- Community 158
- Community 159
- Community 160

## God Nodes (most connected - your core abstractions)
1. `Enu` - 140 edges
2. `V3` - 90 edges
3. `SessionEvent` - 85 edges
4. `Vec2` - 75 edges
5. `LatLon` - 74 edges
6. `BBox` - 66 edges
7. `SurveyEvent` - 62 edges
8. `Stamp` - 58 edges
9. `NearbyMesh` - 53 edges
10. `SessionJournal` - 51 edges

## Surprising Connections (you probably didn't know these)
- `ExportResult` --references--> `SessionManifest`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/data/Repository.kt → core/src/main/kotlin/app/lumisite/core/export/Export.kt
- `LiveState` --references--> `GpsFix`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt
- `Heard` --references--> `AudioDetection`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/capture/AudioSync.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt
- `Round` --calls--> `AudioDetection`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/capture/AudioSync.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt
- `ControlRow` --references--> `ControlPointEvent`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt

## Import Cycles
- None detected.

## Communities (170 total, 86 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (29): DerivedSummary, DerivedSurveyPoint, DxfPreparation, FileEntry, FormatVersions, hex(), LoadedExport, SessionExporter (+21 more)

### Community 1 - "Community 1"
Cohesion: 0.09
Nodes (36): DebugFlags, Attachment, AudioDetection, CalibrationEvent, CalibrationMethod, MULTI_POINT, ONE_POINT_HEADING, TWO_POINTS (+28 more)

### Community 2 - "Community 2"
Cohesion: 0.05
Nodes (23): shapeLines(), arrowIcon(), dashedDot(), dot(), FingerPhase, CANCEL, DOWN, MOVE (+15 more)

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (10): GeoJsonSeq, CaptationWriter, TrackingState, DEGRADED, INITIALIZING, LOST, STOPPED, TRACKING (+2 more)

### Community 4 - "Community 4"
Cohesion: 0.08
Nodes (8): LastDetails, Preset, MasterPanel(), Prerequisites(), SceneActive(), SlavePanel(), SynchroExportBar(), SyncScreen()

### Community 5 - "Community 5"
Cohesion: 0.06
Nodes (5): GpsRecorder, SensorRecorder, LocationListener, SensorEventListener, StampFactory

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (11): ControlPointFeatures, Repere, rd(), Frames, Releves, CrsInfo, LocalTangentPlane, Device (+3 more)

### Community 7 - "Community 7"
Cohesion: 0.13
Nodes (5): AndroidClock, NearbyMesh, SimPeer, SlaveRecorder, UiState

### Community 8 - "Community 8"
Cohesion: 0.09
Nodes (18): ExportEntryRecord, ExportInput, ResumableExport, FilterInputStream, MediaSummary, Progress, Result, Step (+10 more)

### Community 9 - "Community 9"
Cohesion: 0.11
Nodes (13): Chirp, ChirpDetection, ChirpDetector, DeviceSyncLog, IdFrame, Decoded, MultiChirp, Heard (+5 more)

### Community 10 - "Community 10"
Cohesion: 0.11
Nodes (20): ControlRow, ActionCell(), CalibratePanel(), CameraFrame(), ControlTable(), ForceOverride(), localUri(), NearbyMeshSection() (+12 more)

### Community 11 - "Community 11"
Cohesion: 0.12
Nodes (5): GeoJsonFeatures, GeoJsonSeqWriter, AudioOffset, GeoJsonClockOffsetTest, GeoJsonExportTest

### Community 12 - "Community 12"
Cohesion: 0.16
Nodes (11): ForcibleControlPoint, LiveState, Calibrator, PoseGeometry, Relocalizer, LocalObservation, PoseSample, ReferencePoint (+3 more)

### Community 13 - "Community 13"
Cohesion: 0.12
Nodes (25): apply_decisions(), clock_offset_from_chirps(), emitted_chirps_utc(), extract(), families_in(), iso_to_s(), main(), _norm() (+17 more)

### Community 14 - "Community 14"
Cohesion: 0.07
Nodes (13): LumiTheme(), MapFilters, MapTone, DARK, NORMAL, RED, themeBackground(), ThemeMode (+5 more)

### Community 15 - "Community 15"
Cohesion: 0.09
Nodes (14): PositionStatus, Source, AR, GPS, MANUAL, NONE, OsmFeature, OsmSelection (+6 more)

### Community 16 - "Community 16"
Cohesion: 0.10
Nodes (4): Captation, SegmentedAppender, NdjsonAppender, JournalSegmentsTest

### Community 17 - "Community 17"
Cohesion: 0.13
Nodes (3): SynchroArchive, SynchroHeader, SynchroArchiveTest

### Community 18 - "Community 18"
Cohesion: 0.16
Nodes (6): TrajectoryCorrector, Segment, Summary, SurveyPositions, Resolved, SurveyPositionsTest

### Community 19 - "Community 19"
Cohesion: 0.13
Nodes (6): CalibMark, Enu, ShapeEvent, Shapes, ShapesDetailsTest, ZoneEventTest

### Community 20 - "Community 20"
Cohesion: 0.12
Nodes (20): BarItem(), DrawPanel(), DrawState, EditPanel, EDIT, MOVE, SelectedShape(), SelectedShapeBar() (+12 more)

### Community 21 - "Community 21"
Cohesion: 0.10
Nodes (25): ArFrameRef, ArPoint, label(), PositionMethod, AR_CAMERA, AR_FLOOR_ESTIMATE, AR_LAST_PLANE, AR_PLANE (+17 more)

### Community 22 - "Community 22"
Cohesion: 0.11
Nodes (16): LatLon, Wgs84, Ecef, SiteRects, Handle, E, MOVE, N (+8 more)

### Community 23 - "Community 23"
Cohesion: 0.18
Nodes (3): UndoPolicy, EditHistoryTest, UndoPolicyTest

### Community 24 - "Community 24"
Cohesion: 0.13
Nodes (16): apply_validated(), collect(), _lonlat(), main(), osm_rings(), _read_pairs(), run(), to_dwg() (+8 more)

### Community 25 - "Community 25"
Cohesion: 0.11
Nodes (15): SessionLayout, Camera, BAND, FULL, HIDDEN, Split, Tab, CALIBRATE (+7 more)

### Community 26 - "Community 26"
Cohesion: 0.16
Nodes (4): V3, ArGeometry, PlaneEq, ArGeometryTest

### Community 27 - "Community 27"
Cohesion: 0.11
Nodes (7): GnssClassifier, GnssObservation, GnssQuality, DEGRADE, FIABLE, INUTILISABLE, GnssThresholds

### Community 28 - "Community 28"
Cohesion: 0.07
Nodes (9): MainActivity, Screen, Home, Live, NewSite, Settings, SiteDetail, Synchro (+1 more)

### Community 29 - "Community 29"
Cohesion: 0.10
Nodes (9): aimState(), ArControls(), ArLabels(), ArReticle(), nearbyPermissions(), nearbyPermissionsGranted(), rememberMeshNowNs(), SynchroJournal() (+1 more)

### Community 30 - "Community 30"
Cohesion: 0.17
Nodes (9): PlaneFilter, Desc, Role, FLOOR, FURNITURE, KEPT, TOO_SMALL, ArDonneesTest (+1 more)

### Community 31 - "Community 31"
Cohesion: 0.10
Nodes (11): cadastreTileSource(), OnlineTileSourceBase, SiteTileCache, tileSourceFor(), OnlineTileSourceBase, OnlineTileSourceBase, Basemap, ESRI (+3 more)

### Community 32 - "Community 32"
Cohesion: 0.15
Nodes (5): CorrectedPoint, MirrorLedger, GrowingSegmentSimplifier, Simplify, FluiditeTest

### Community 33 - "Community 33"
Cohesion: 0.17
Nodes (6): Similarity2D, Vec2, TwoMarkerRecal, Estimate, Observation, Recalage2ReperesTest

### Community 34 - "Community 34"
Cohesion: 0.08
Nodes (5): AnchorKind, FLAG, GROUP, LUMINAIRE, Mark

### Community 35 - "Community 35"
Cohesion: 0.19
Nodes (5): OsmClient, Fetched, BBox, OsmSnapshot, Bboxes

### Community 36 - "Community 36"
Cohesion: 0.11
Nodes (5): drawLsGlyph(), LsSymbol(), lsSymbolDrawable(), pathOf(), ToolIcons

### Community 37 - "Community 37"
Cohesion: 0.13
Nodes (8): BootInfo, DeviceInfo, DeviceName, Ids, LastKnownPosition, appVersionLabel(), SettingsScreen(), SensorDescriptor

### Community 38 - "Community 38"
Cohesion: 0.12
Nodes (3): Angles, ZoneDownload, GeoTest

### Community 40 - "Community 40"
Cohesion: 0.20
Nodes (7): ControlPointCheck, Refusal, CalibrationSolver, ControlPoint, Solution, SolverSettings, FieldAnomaliesTest

### Community 41 - "Community 41"
Cohesion: 0.16
Nodes (8): OsmGuard, Failure, BBOX_TOO_LARGE, OTHER, RATE_LIMITED, TOO_MANY_NODES, OsmHttpException, OsmApiTest

### Community 43 - "Community 43"
Cohesion: 0.21
Nodes (4): Repository, ExportResult, ReimportResult, Site

### Community 44 - "Community 44"
Cohesion: 0.12
Nodes (4): BoundedApproximater, MapPrefs, SiteTileProvider, TileCachePolicy

### Community 45 - "Community 45"
Cohesion: 0.14
Nodes (11): Aim, Plane, Ray, At, Grazing, Miss, AimResult, Grazing (+3 more)

### Community 46 - "Community 46"
Cohesion: 0.18
Nodes (3): LsCatalog, LsItem, LsText

### Community 47 - "Community 47"
Cohesion: 0.16
Nodes (4): downloadZone(), MapListener, TileMath, BasemapsTest

### Community 48 - "Community 48"
Cohesion: 0.11
Nodes (7): accueilBuildLabel(), HomeScreen(), placeName(), SiteEditorScreen(), TapMode, ORIGIN, RECT

### Community 49 - "Community 49"
Cohesion: 0.15
Nodes (8): SynchroBanner(), SyncDisplay, Banner, Level, ERROR, OK, WAIT, SyncDisplayTest

### Community 50 - "Community 50"
Cohesion: 0.19
Nodes (6): SupportFromTilt, Support, MUR, PLAFOND, SOL, SupportFromTiltTest

### Community 51 - "Community 51"
Cohesion: 0.20
Nodes (15): l93_to_lonlat(), dxf_to_features(), feature(), make_package(), make_template(), args(), ins_by_id(), test_alias_one_block_name() (+7 more)

### Community 52 - "Community 52"
Cohesion: 0.20
Nodes (6): HubElection, MultiDeviceAlign, ClockModel, Detection, Result, MultiDeviceSyncTest

### Community 53 - "Community 53"
Cohesion: 0.15
Nodes (15): Capture, CAPTURING, IDLE, STOPPED, ClockAlign, ClockSample, Frame, NOT_SHARED (+7 more)

### Community 54 - "Community 54"
Cohesion: 0.19
Nodes (8): Clock, ALIGNED, ALIGNING, NOT_ALIGNED, MasterMesh, MeshLog, SyncMeshScreenTest, SyncMeshTest

### Community 55 - "Community 55"
Cohesion: 0.13
Nodes (9): azimuth_to_rotation(), convergence_deg(), fit_similarity(), local_to_lonlat(), lonlat_to_l93(), Similarity, test_azimuth_reference_on_a_meridian(), test_convergence_and_rotation() (+1 more)

### Community 56 - "Community 56"
Cohesion: 0.12
Nodes (3): AudioSync, Heard, Round

### Community 59 - "Community 59"
Cohesion: 0.24
Nodes (4): Ids, Entropy, UuidV7, UuidV7Test

### Community 60 - "Community 60"
Cohesion: 0.16
Nodes (4): chirp(), detect_chirps(), read_audio(), test_chirps_found_in_long_recording()

### Community 61 - "Community 61"
Cohesion: 0.24
Nodes (4): HeardChirp, SyncPrereq, SyncRoundCalc, SyncRoundTest

### Community 62 - "Community 62"
Cohesion: 0.15
Nodes (4): Clock, TimeMath, TimeTest, Clock

### Community 63 - "Community 63"
Cohesion: 0.19
Nodes (9): Copied, copy(), displayPath(), mimeOf(), relativePath(), safe(), siteFolder(), write() (+1 more)

### Community 64 - "Community 64"
Cohesion: 0.27
Nodes (3): AddressHistory, SavedAddress, AddressHistoryTest

### Community 66 - "Community 66"
Cohesion: 0.18
Nodes (6): LegacyKinds, Target, HEIGHT_OR_TEXT, TEXT_ANNOTATION, UNCLASSIFIED, CatalogTest

### Community 67 - "Community 67"
Cohesion: 0.19
Nodes (3): LsCatalogException, LsRecents, LsCatalogTest

### Community 68 - "Community 68"
Cohesion: 0.24
Nodes (5): CallCapReached, FormPoster, MirrorFetcher, Overpass, OverpassTest

### Community 71 - "Community 71"
Cohesion: 0.19
Nodes (7): LsPrim, Arc, Circle, Line, Poly, Rect, LsSymbols

### Community 72 - "Community 72"
Cohesion: 0.23
Nodes (9): bundle(), e(), i(), init(), memory(), rotate(), screen(), set (+1 more)

### Community 74 - "Community 74"
Cohesion: 0.21
Nodes (7): CalibrationQuality, Grade, Level, BON, INSUFFISANT, MOYEN, CalibrationControlTest

### Community 75 - "Community 75"
Cohesion: 0.35
Nodes (13): MeshMsg, ClockInfo, Hello, Media, Ping, Pong, Sensors, StartCapture (+5 more)

### Community 76 - "Community 76"
Cohesion: 0.13
Nodes (15): with, api-level, arch, disable-animations, distribution, emulator-options, fetch-depth, include-hidden-files (+7 more)

### Community 77 - "Community 77"
Cohesion: 0.28
Nodes (11): _add_annotation(), _attr_values(), DxfOptions, DxfStats, _ensure_attdefs(), generate(), _legend(), _remove_geodata() (+3 more)

### Community 79 - "Community 79"
Cohesion: 0.26
Nodes (6): alias_map(), _find_root(), load_package(), normalise(), read_geojsonl(), sha256()

### Community 80 - "Community 80"
Cohesion: 0.29
Nodes (6): ArSiteOverlays, Anchor, Kind, FLAG, LUMINAIRE, VisitEvents

### Community 81 - "Community 81"
Cohesion: 0.24
Nodes (3): DragMove, ScreenProjection, DragMoveTest

### Community 84 - "Community 84"
Cohesion: 0.29
Nodes (7): OsmElements, OsmMember, OsmNode, OsmRelation, OsmWay, parse(), tags()

### Community 87 - "Community 87"
Cohesion: 0.29
Nodes (4): ArPlaneDiagnose, PlaneDesc, Stats, ArPlaneDiagnoseTest

### Community 89 - "Community 89"
Cohesion: 0.24
Nodes (5): SyncRole2, CAPTATION_ESCLAVE, SAISIE_MAITRE, Notice, SyncServiceTest

### Community 90 - "Community 90"
Cohesion: 0.17
Nodes (11): concurrency, cancel-in-progress, group, jobs, maestro, needs, runs-on, timeout-minutes (+3 more)

### Community 92 - "Community 92"
Cohesion: 0.27
Nodes (11): steps, GH_TOKEN, MAESTRO, steps, steps, env, id, if (+3 more)

### Community 94 - "Community 94"
Cohesion: 0.28
Nodes (4): SyncService, ForegroundType, CONNECTED_DEVICE, MICROPHONE

### Community 98 - "Community 98"
Cohesion: 0.22
Nodes (9): outputs, runs-on, timeout-minutes, apk, apk, n, short, tag (+1 more)

### Community 99 - "Community 99"
Cohesion: 0.22
Nodes (9): publier_sans_maestro, on, push, workflow_dispatch, default, description, type, branches (+1 more)

### Community 100 - "Community 100"
Cohesion: 0.25
Nodes (8): Mode, CALIBRATE, DRAW, EXPORT, NONE, RELOCALIZE, SURVEY, SYNC

### Community 108 - "Community 108"
Cohesion: 0.50
Nodes (3): MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED, MAESTRO_CLI_NO_ANALYTICS, maestro-ci.sh script

### Community 109 - "Community 109"
Cohesion: 0.50
Nodes (4): release, if, needs, runs-on

## Knowledge Gaps
- **146 isolated node(s):** `AboveFloor`, `Unqualified`, `Home`, `NewSite`, `Settings` (+141 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 582 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **86 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Enu` connect `Community 19` to `Community 0`, `Community 1`, `Community 3`, `Community 4`, `Community 6`, `Community 10`, `Community 11`, `Community 12`, `Community 15`, `Community 18`, `Community 20`, `Community 21`, `Community 22`, `Community 23`, `Community 27`, `Community 30`, `Community 32`, `Community 33`, `Community 38`, `Community 39`, `Community 42`, `Community 45`, `Community 57`, `Community 73`, `Community 80`, `Community 81`, `Community 103`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **What connects `AboveFloor`, `Unqualified`, `Home` to the rest of the system?**
  _146 weakly-connected nodes found - possible missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05591147350029121 - nodes in this community are weakly interconnected._
- **Why does `V3` connect `Community 26` to `Community 96`, `Community 33`, `Community 34`, `Community 3`, `Community 39`, `Community 107`, `Community 45`, `Community 80`, `Community 50`, `Community 18`, `Community 27`, `Community 29`, `Community 30`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.09417249417249417 - nodes in this community are weakly interconnected._
- **Why does `BBox` connect `Community 35` to `Community 1`, `Community 2`, `Community 3`, `Community 15`, `Community 22`, `Community 27`, `Community 30`, `Community 31`, `Community 38`, `Community 41`, `Community 43`, `Community 44`, `Community 47`, `Community 48`, `Community 68`, `Community 80`, `Community 82`, `Community 83`, `Community 84`?**
  _High betweenness centrality (0.058) - this node is a cross-community bridge._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.05325814536340852 - nodes in this community are weakly interconnected._