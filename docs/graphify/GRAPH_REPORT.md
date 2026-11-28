# Graph Report - eclairage  (2026-11-28)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2773 nodes · 8019 edges · 174 communities (84 shown, 90 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 67 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e9cde5a7`
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
- Community 86
- Community 87
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
- Community 161
- Community 162
- Community 163
- Community 164

## God Nodes (most connected - your core abstractions)
1. `Enu` - 140 edges
2. `V3` - 96 edges
3. `SessionEvent` - 85 edges
4. `Vec2` - 75 edges
5. `LatLon` - 73 edges
6. `BBox` - 67 edges
7. `SurveyEvent` - 62 edges
8. `Stamp` - 58 edges
9. `NearbyMesh` - 53 edges
10. `SessionJournal` - 51 edges

## Surprising Connections (you probably didn't know these)
- `UiState` --references--> `SyncRole2`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/sync/NearbyMesh.kt → core/src/main/kotlin/app/lumisite/core/sync/SyncMesh.kt
- `LiveState` --references--> `GpsFix`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt
- `LiveState` --references--> `GnssQuality`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/position/Positioning.kt
- `ControlRow` --references--> `ControlPointEvent`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/SessionController.kt → core/src/main/kotlin/app/lumisite/core/model/Model.kt
- `ExportResult` --references--> `SessionManifest`  [EXTRACTED]
  app/src/main/java/app/lumisite/android/data/Repository.kt → core/src/main/kotlin/app/lumisite/core/export/Export.kt

## Import Cycles
- None detected.

## Communities (174 total, 90 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.05
Nodes (8): LsCatalogPicker(), TapMode, ORIGIN, RECT, ToolIcons, SyncRole2, CAPTATION_ESCLAVE, SAISIE_MAITRE

### Community 1 - "Community 1"
Cohesion: 0.08
Nodes (18): SessionImporter, Ndjson, SessionData, SessionJournal, SessionReader, Streams, GpsFix, ImuSample (+10 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (8): Frames, GeoJsonFeatures, GeoJsonSeq, GeoJsonSeqWriter, LocalTangentPlane, ReadResult, SessionStopped, GeoJsonExportTest

### Community 3 - "Community 3"
Cohesion: 0.07
Nodes (22): ControlPointFeatures, Repere, rd(), ExportEntryRecord, ExportInput, Releves, ResumableExport, FilterInputStream (+14 more)

### Community 4 - "Community 4"
Cohesion: 0.10
Nodes (14): Aim, Plane, Ray, At, Grazing, Miss, AimResult, Grazing (+6 more)

### Community 5 - "Community 5"
Cohesion: 0.06
Nodes (9): ImuCsv, Writer, Clock, StampFactory, TimeBase, TimeMath, ExportZipTest, TimeTest (+1 more)

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (36): AimCue, CalibMark, ForcibleControlPoint, LiveState, Attachment, AudioDetection, CalibrationMethod, MULTI_POINT (+28 more)

### Community 7 - "Community 7"
Cohesion: 0.08
Nodes (23): BarItem(), DrawPanel(), DrawState, EditPanel, EDIT, MOVE, LastDetails, Preset (+15 more)

### Community 8 - "Community 8"
Cohesion: 0.15
Nodes (11): Calibrator, PoseGeometry, Relocalizer, CalibrationEvent, LocalObservation, ReferencePoint, RelocalizationEvent, Stamp (+3 more)

### Community 9 - "Community 9"
Cohesion: 0.13
Nodes (9): Enu, SessionEvent, ShapeEvent, SurveyEvent, ZoneEvent, UndoPolicy, EditHistory, Shapes (+1 more)

### Community 10 - "Community 10"
Cohesion: 0.08
Nodes (10): ZoneDownload, GnssClassifier, GnssQuality, DEGRADE, FIABLE, INUTILISABLE, GnssThresholds, SolverSettings (+2 more)

### Community 11 - "Community 11"
Cohesion: 0.10
Nodes (20): ControlRow, DebugFlags, ActionCell(), CalibratePanel(), CameraFrame(), ControlTable(), ForceOverride(), localUri() (+12 more)

### Community 12 - "Community 12"
Cohesion: 0.13
Nodes (12): DerivedSummary, DerivedSurveyPoint, DxfPreparation, FileEntry, FormatVersions, hex(), LoadedExport, SessionExporter (+4 more)

### Community 13 - "Community 13"
Cohesion: 0.08
Nodes (19): MapContent, MapLine, MapPoint, placeName(), SiteEditorScreen(), LatLon, SiteRects, Handle (+11 more)

### Community 14 - "Community 14"
Cohesion: 0.08
Nodes (12): drawLsGlyph(), LsSymbol(), lsSymbolDrawable(), pathOf(), LsPrim, Arc, Circle, Line (+4 more)

### Community 15 - "Community 15"
Cohesion: 0.11
Nodes (6): OsmClient, Fetched, BBox, OsmSnapshot, Bboxes, OsmGuard

### Community 16 - "Community 16"
Cohesion: 0.12
Nodes (25): apply_decisions(), clock_offset_from_chirps(), emitted_chirps_utc(), extract(), families_in(), iso_to_s(), main(), _norm() (+17 more)

### Community 17 - "Community 17"
Cohesion: 0.12
Nodes (12): Chirp, ChirpDetection, ChirpDetector, DeviceSyncLog, IdFrame, Decoded, MultiChirp, Heard (+4 more)

### Community 18 - "Community 18"
Cohesion: 0.15
Nodes (8): TrajectoryCorrector, Segment, Summary, SurveyPositions, Resolved, PoseSample, ResumeSessionTest, SurveyPositionsTest

### Community 19 - "Community 19"
Cohesion: 0.09
Nodes (14): PositionStatus, Source, AR, GPS, MANUAL, NONE, OsmFeature, OsmSelection (+6 more)

### Community 20 - "Community 20"
Cohesion: 0.09
Nodes (26): ArFrameRef, ArPoint, label(), PositionMethod, AR_CAMERA, AR_FLOOR_ESTIMATE, AR_LAST_PLANE, AR_PLANE (+18 more)

### Community 21 - "Community 21"
Cohesion: 0.13
Nodes (3): Zones, ZoneEventTest, ZonesTest

### Community 22 - "Community 22"
Cohesion: 0.13
Nodes (11): Repository, ExportResult, ReimportResult, fmtTime(), HomeScreen(), SiteScreen(), CrsInfo, Device (+3 more)

### Community 23 - "Community 23"
Cohesion: 0.18
Nodes (3): NearbyMesh, SimPeer, UiState

### Community 24 - "Community 24"
Cohesion: 0.13
Nodes (16): apply_validated(), collect(), _lonlat(), main(), osm_rings(), _read_pairs(), run(), to_dwg() (+8 more)

### Community 25 - "Community 25"
Cohesion: 0.08
Nodes (3): SensorRecorder, EndpointDiscoveryCallback, SensorEventListener

### Community 26 - "Community 26"
Cohesion: 0.07
Nodes (4): LayerGroup, MapArrow, MapLayers, MapListener

### Community 28 - "Community 28"
Cohesion: 0.09
Nodes (8): LumiTheme(), themeBackground(), ThemeMode, DAY, NIGHT, NIGHT_RED, ThemePrefs, ThemeToggle()

### Community 30 - "Community 30"
Cohesion: 0.12
Nodes (15): SessionLayout, Camera, BAND, FULL, HIDDEN, Split, Tab, CALIBRATE (+7 more)

### Community 31 - "Community 31"
Cohesion: 0.11
Nodes (10): Copied, copy(), displayPath(), mimeOf(), relativePath(), safe(), siteFolder(), write() (+2 more)

### Community 32 - "Community 32"
Cohesion: 0.15
Nodes (5): CorrectedPoint, MirrorLedger, GrowingSegmentSimplifier, Simplify, FluiditeTest

### Community 33 - "Community 33"
Cohesion: 0.18
Nodes (6): Similarity2D, Vec2, TwoMarkerRecal, Estimate, Observation, Recalage2ReperesTest

### Community 34 - "Community 34"
Cohesion: 0.08
Nodes (5): AnchorKind, FLAG, GROUP, LUMINAIRE, Mark

### Community 35 - "Community 35"
Cohesion: 0.17
Nodes (3): ArGeometry, PlaneEq, ArGeometryTest

### Community 37 - "Community 37"
Cohesion: 0.17
Nodes (3): ArMeasure, ArSymbols, ArDonneesTest

### Community 38 - "Community 38"
Cohesion: 0.22
Nodes (8): PlaneFilter, Desc, Role, FLOOR, FURNITURE, KEPT, TOO_SMALL, PlaneFilterTest

### Community 40 - "Community 40"
Cohesion: 0.14
Nodes (8): BootInfo, DeviceInfo, DeviceName, Ids, LastKnownPosition, appVersionLabel(), SettingsScreen(), SensorDescriptor

### Community 41 - "Community 41"
Cohesion: 0.12
Nodes (4): BoundedApproximater, MapPrefs, SiteTileProvider, TileCachePolicy

### Community 42 - "Community 42"
Cohesion: 0.11
Nodes (13): arrowIcon(), dashedDot(), dot(), FingerPhase, CANCEL, DOWN, MOVE, UP (+5 more)

### Community 43 - "Community 43"
Cohesion: 0.20
Nodes (3): AddressHistory, SavedAddress, AddressHistoryTest

### Community 45 - "Community 45"
Cohesion: 0.16
Nodes (9): SynchroPeerCard(), SyncDisplay, Banner, Level, ERROR, OK, WAIT, Peer (+1 more)

### Community 46 - "Community 46"
Cohesion: 0.20
Nodes (3): LsCatalog, LsItem, LsText

### Community 47 - "Community 47"
Cohesion: 0.19
Nodes (6): SupportFromTilt, Support, MUR, PLAFOND, SOL, SupportFromTiltTest

### Community 48 - "Community 48"
Cohesion: 0.20
Nodes (15): l93_to_lonlat(), dxf_to_features(), feature(), make_package(), make_template(), args(), ins_by_id(), test_alias_one_block_name() (+7 more)

### Community 50 - "Community 50"
Cohesion: 0.13
Nodes (9): azimuth_to_rotation(), convergence_deg(), fit_similarity(), local_to_lonlat(), lonlat_to_l93(), Similarity, test_azimuth_reference_on_a_meridian(), test_convergence_and_rotation() (+1 more)

### Community 51 - "Community 51"
Cohesion: 0.12
Nodes (3): AudioSync, Heard, Round

### Community 52 - "Community 52"
Cohesion: 0.15
Nodes (4): aimState(), ArControls(), ArLabels(), ArReticle()

### Community 53 - "Community 53"
Cohesion: 0.18
Nodes (4): Angles, Wgs84, Ecef, GeoTest

### Community 54 - "Community 54"
Cohesion: 0.22
Nodes (4): Ids, Entropy, UuidV7, UuidV7Test

### Community 55 - "Community 55"
Cohesion: 0.18
Nodes (6): Failure, BBOX_TOO_LARGE, OTHER, RATE_LIMITED, TOO_MANY_NODES, OsmApiTest

### Community 57 - "Community 57"
Cohesion: 0.22
Nodes (6): HubElection, MultiDeviceAlign, ClockModel, Detection, Result, MultiDeviceSyncTest

### Community 58 - "Community 58"
Cohesion: 0.20
Nodes (8): Clock, ALIGNED, ALIGNING, NOT_ALIGNED, MasterMesh, MeshLog, SyncMeshScreenTest, SyncMeshTest

### Community 59 - "Community 59"
Cohesion: 0.17
Nodes (9): CalibrationQuality, Grade, Level, BON, INSUFFISANT, MOYEN, CalibrationSolver, Solution (+1 more)

### Community 60 - "Community 60"
Cohesion: 0.16
Nodes (4): chirp(), detect_chirps(), read_audio(), test_chirps_found_in_long_recording()

### Community 61 - "Community 61"
Cohesion: 0.16
Nodes (9): Triangulation, Result, Point, Refused, ANGLE, BASELINE, OUT_OF_RANGE, RAY_GAP (+1 more)

### Community 62 - "Community 62"
Cohesion: 0.15
Nodes (10): cadastreTileSource(), OnlineTileSourceBase, tileSourceFor(), OnlineTileSourceBase, OnlineTileSourceBase, Basemap, ESRI, IGN_ORTHO (+2 more)

### Community 63 - "Community 63"
Cohesion: 0.18
Nodes (3): LsCatalogException, LsRecents, LsCatalogTest

### Community 64 - "Community 64"
Cohesion: 0.24
Nodes (6): CallCapReached, OsmHttpException, FormPoster, MirrorFetcher, Overpass, OverpassTest

### Community 65 - "Community 65"
Cohesion: 0.24
Nodes (3): downloadZone(), TileMath, BasemapsTest

### Community 68 - "Community 68"
Cohesion: 0.21
Nodes (12): NearbyMeshSection(), nearbyPermissions(), nearbyPermissionsGranted(), rememberMeshNowNs(), SynchroBanner(), SynchroJournal(), MasterPanel(), Prerequisites() (+4 more)

### Community 70 - "Community 70"
Cohesion: 0.28
Nodes (4): ControlPointCheck, Refusal, ControlPoint, FieldAnomaliesTest

### Community 71 - "Community 71"
Cohesion: 0.15
Nodes (13): Capture, CAPTURING, IDLE, STOPPED, Frame, NOT_SHARED, Link, CONNECTED (+5 more)

### Community 74 - "Community 74"
Cohesion: 0.33
Nodes (14): MeshMsg, ClockInfo, Hello, Media, Ping, Pong, Sensors, StartCapture (+6 more)

### Community 75 - "Community 75"
Cohesion: 0.23
Nodes (9): bundle(), e(), i(), init(), memory(), rotate(), screen(), set (+1 more)

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

### Community 82 - "Community 82"
Cohesion: 0.23
Nodes (3): Captation, CaptationWriter, GpsStatusSample

### Community 83 - "Community 83"
Cohesion: 0.29
Nodes (7): OsmElements, OsmMember, OsmNode, OsmRelation, OsmWay, parse(), tags()

### Community 84 - "Community 84"
Cohesion: 0.32
Nodes (3): accueilBuildLabel(), PaquetName, PaquetNameTest

### Community 86 - "Community 86"
Cohesion: 0.29
Nodes (4): ArPlaneDiagnose, PlaneDesc, Stats, ArPlaneDiagnoseTest

### Community 89 - "Community 89"
Cohesion: 0.22
Nodes (5): SyncService, ForegroundType, CONNECTED_DEVICE, MICROPHONE, Notice

### Community 93 - "Community 93"
Cohesion: 0.27
Nodes (11): steps, GH_TOKEN, MAESTRO, steps, steps, env, id, if (+3 more)

### Community 98 - "Community 98"
Cohesion: 0.22
Nodes (9): outputs, runs-on, timeout-minutes, apk, apk, n, short, tag (+1 more)

### Community 99 - "Community 99"
Cohesion: 0.22
Nodes (9): publier_sans_maestro, on, push, workflow_dispatch, default, description, type, branches (+1 more)

### Community 100 - "Community 100"
Cohesion: 0.25
Nodes (7): Screen, Home, Live, NewSite, Settings, SiteDetail, Synchro

### Community 101 - "Community 101"
Cohesion: 0.25
Nodes (8): Mode, CALIBRATE, DRAW, EXPORT, NONE, RELOCALIZE, SURVEY, SYNC

### Community 102 - "Community 102"
Cohesion: 0.25
Nodes (7): concurrency, cancel-in-progress, group, jobs, name, permissions, contents

### Community 103 - "Community 103"
Cohesion: 0.25
Nodes (8): maestro, needs, runs-on, strategy, timeout-minutes, suite, fail-fast, matrix

### Community 106 - "Community 106"
Cohesion: 0.48
Nodes (6): MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED, MAESTRO_CLI_NO_ANALYTICS, record(), run_scenarios(), run_upgrade_tests(), maestro-ci.sh script

### Community 107 - "Community 107"
Cohesion: 0.33
Nodes (5): MapFilters, MapTone, DARK, NORMAL, RED

### Community 108 - "Community 108"
Cohesion: 0.33
Nodes (5): LegacyKinds, Target, HEIGHT_OR_TEXT, TEXT_ANNOTATION, UNCLASSIFIED

### Community 113 - "Community 113"
Cohesion: 0.50
Nodes (4): release, if, needs, runs-on

## Knowledge Gaps
- **151 isolated node(s):** `Home`, `NewSite`, `Settings`, `Synchro`, `AboveFloor` (+146 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 590 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **90 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Enu` connect `Community 9` to `Community 1`, `Community 2`, `Community 4`, `Community 6`, `Community 7`, `Community 8`, `Community 10`, `Community 11`, `Community 12`, `Community 15`, `Community 18`, `Community 19`, `Community 20`, `Community 21`, `Community 27`, `Community 29`, `Community 32`, `Community 37`, `Community 53`, `Community 56`, `Community 72`, `Community 80`, `Community 87`?**
  _High betweenness centrality (0.080) - this node is a cross-community bridge._
- **What connects `Home`, `NewSite`, `Settings` to the rest of the system?**
  _151 weakly-connected nodes found - possible missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.0539906103286385 - nodes in this community are weakly interconnected._
- **Why does `V3` connect `Community 4` to `Community 34`, `Community 35`, `Community 37`, `Community 6`, `Community 38`, `Community 10`, `Community 47`, `Community 80`, `Community 18`, `Community 29`, `Community 52`, `Community 61`?**
  _High betweenness centrality (0.065) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.07887323943661972 - nodes in this community are weakly interconnected._
- **Why does `BBox` connect `Community 15` to `Community 0`, `Community 65`, `Community 64`, `Community 26`, `Community 6`, `Community 41`, `Community 42`, `Community 10`, `Community 13`, `Community 80`, `Community 83`, `Community 19`, `Community 22`, `Community 55`, `Community 90`, `Community 29`, `Community 31`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.07270865335381464 - nodes in this community are weakly interconnected._