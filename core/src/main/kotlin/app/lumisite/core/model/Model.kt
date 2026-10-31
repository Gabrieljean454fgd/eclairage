package app.lumisite.core.model

import app.lumisite.core.geo.CrsInfo
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.time.Stamp
import app.lumisite.core.time.TimeBase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------- Site & map data

@Serializable
data class Site(
    val id: String,
    val name: String,
    /** Origin of the site ENU frame. Chosen by the user (search result, coordinates or map tap). */
    val origin: LatLon,
    val originSource: String,
    val createdUtcMs: Long,
    val crs: List<CrsInfo>,
    val osm: OsmSnapshot? = null,
    /** Optional building chosen among OSM features (may be absent: OSM is often incomplete). */
    val selectedBuildingOsmRef: String? = null,
    /** Site rectangle chosen by the user, kept even when the site was created without OSM data. */
    val bbox: BBox? = null,
    /** Reserved for imported architectural plans (none in this slice). */
    val plans: List<PlanReference> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class OsmSnapshot(
    val source: String,
    val endpoint: String,
    val query: String,
    val retrievedUtcMs: Long,
    val license: String = "ODbL 1.0",
    val attribution: String = "© OpenStreetMap contributors",
    val bbox: BBox,
    val crs: String = "EPSG:4326",
    val features: List<OsmFeature>,
    /** Raw response kept verbatim for re-processing (may be truncated to [rawMaxBytes]). */
    val rawResponse: String? = null,
)

@Serializable
data class BBox(val south: Double, val west: Double, val north: Double, val east: Double)

@Serializable
data class OsmFeature(
    /** "node" | "way" | "relation" */
    val osmType: String,
    val osmId: Long,
    val tags: Map<String, String>,
    /** Outline (closed ring for buildings) or single point, WGS84. Empty if geometry unavailable. */
    val geometry: List<LatLon>,
    val category: String,
    /** Inner rings of a multipolygon (courtyards). */
    val holes: List<List<LatLon>> = emptyList(),
    /** Additional outer rings of a multipolygon ([geometry] is the largest). */
    val outers: List<List<LatLon>> = emptyList(),
) {
    val ref: String get() = "$osmType/$osmId"
}

/** Placeholder for a future imported plan (PDF/DWG/DXF/image) georeferenced to the site frame. */
@Serializable
data class PlanReference(
    val id: String,
    val fileName: String,
    val mediaType: String,
    /** Transform from plan units (x right, y up) to site ENU metres. */
    val planToSite: Similarity2D? = null,
    val controlPoints: List<ControlPoint> = emptyList(),
    val levelName: String? = null,
)

@Serializable
data class ControlPoint(val label: String, val plan: Vec2, val site: Enu)

// ---------------------------------------------------------------- Session

@Serializable
data class Device(
    val id: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
    val arSupport: String,
    val sensors: List<SensorDescriptor> = emptyList(),
)

@Serializable
data class SensorDescriptor(
    val type: Int,
    val stringType: String,
    val name: String,
    val vendor: String,
    val resolution: Float? = null,
    val maxRange: Float? = null,
    val minDelayUs: Int? = null,
)

@Serializable
data class SessionHeader(
    val id: String,
    val siteId: String,
    val name: String,
    val createdUtcMs: Long,
    val device: Device,
    val timeBases: List<TimeBase>,
    /**
     * The AR session frame created at start: right-handed, y up (gravity), metres. Its horizontal plane is
     * mapped to site ENU using (x, -z) -> plane, see docs/SESSION_FORMAT.md.
     */
    val localFrame: CrsInfo,
)

// ---------------------------------------------------------------- Raw streams (one NDJSON file each)

enum class TrackingState { INITIALIZING, TRACKING, DEGRADED, LOST, STOPPED, UNAVAILABLE }

/** Raw pose from the platform tracker (ARCore camera pose), session frame. Never modified. */
@Serializable
data class PoseSample(
    val t: Stamp,
    /** Translation x, y, z in metres (session frame). */
    val p: List<Double>,
    /** Quaternion x, y, z, w. */
    val q: List<Double>,
    val tracking: TrackingState,
    val failureReason: String? = null,
    val source: String = "ARCORE_CAMERA_POSE",
)

@Serializable
data class GpsFix(
    val t: Stamp,
    val provider: String,
    val lat: Double,
    val lon: Double,
    val horizontalAccuracyM: Float? = null,
    val altitudeM: Double? = null,
    val verticalAccuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    /** Location.getTime(), UTC from the fix itself. */
    val fixUtcMs: Long? = null,
    val satellitesUsed: Int? = null,
    val satellitesVisible: Int? = null,
)

/** Status of a provider (GPS absent indoors is expected and recorded, not hidden). */
@Serializable
data class GpsStatusSample(val t: Stamp, val provider: String, val status: String, val detail: String? = null)

@Serializable
data class ImuSample(
    val t: Stamp,
    val sensorType: Int,
    val sensorName: String,
    val values: List<Float>,
    /** SensorManager accuracy constant (0..3). */
    val accuracy: Int,
)

// ---------------------------------------------------------------- Events (events.ndjson, append-only)

@Serializable
sealed interface SessionEvent {
    val id: String
    val t: Stamp
}

@Serializable
@SerialName("session_started")
data class SessionStarted(override val id: String, override val t: Stamp, val note: String? = null) : SessionEvent

@Serializable
@SerialName("session_stopped")
data class SessionStopped(override val id: String, override val t: Stamp, val reason: String) : SessionEvent

/** Added when a session is resumed (app restart, reboot): a new monotonic reference is created. */
@Serializable
@SerialName("time_base_added")
data class TimeBaseAdded(override val id: String, override val t: Stamp, val timeBase: TimeBase, val reason: String) : SessionEvent

/**
 * Stop, resume or repair of a capture stream (stage 1, reserves R1/R2): every interruption is written, never bridged.
 * [state]: STOPPED | RESUMED | REPAIRED (segment left open by an abrupt stop, closed at next opening) | COPY_FAILED.
 */
@Serializable
@SerialName("stream_state")
data class StreamStateEvent(
    override val id: String,
    override val t: Stamp,
    /** Stream name ("imu", "gps", "poses", "camera", "arcore", "audio", "events", "mirror"...). */
    val stream: String,
    val state: String,
    val reason: String? = null,
    val detail: String? = null,
) : SessionEvent

@Serializable
@SerialName("tracking_state")
data class TrackingStateChanged(
    override val id: String,
    override val t: Stamp,
    val state: TrackingState,
    val reason: String? = null,
) : SessionEvent

/** A point picked by the user on the map/plan, with the source and uncertainty that are known. */
@Serializable
data class ReferencePoint(
    val label: String,
    val site: Enu,
    val latLon: LatLon? = null,
    /** "MAP_TAP" | "COORDINATES" | "PLAN" | "OSM_FEATURE" */
    val source: String,
    val uncertaintyM: Double? = null,
)

@Serializable
data class LocalObservation(
    /** Raw pose (session frame) at the moment the user confirmed the point. */
    val pose: PoseSample?,
    val reference: ReferencePoint,
)

enum class CalibrationMethod { ONE_POINT_HEADING, TWO_POINTS, TWO_POINTS_KNOWN_DISTANCE }

@Serializable
@SerialName("calibration")
data class CalibrationEvent(
    override val id: String,
    override val t: Stamp,
    val method: CalibrationMethod,
    val observations: List<LocalObservation>,
    /** Heading used for ONE_POINT_HEADING, degrees from north, and where it came from. */
    val headingDeg: Double? = null,
    val headingSource: String? = null,
    val headingUncertaintyDeg: Double? = null,
    val knownDistanceM: Double? = null,
    /** Result: session plane -> site ENU plane. Computed, stored for traceability. */
    val transform: Similarity2D,
    /** Scale measured from 2 points before being fixed (diagnostic of AR/map consistency). */
    val measuredScale: Double? = null,
    val scaleFixed: Boolean,
    val residualM: Double? = null,
    val notes: String? = null,
) : SessionEvent

@Serializable
@SerialName("relocalization")
data class RelocalizationEvent(
    override val id: String,
    override val t: Stamp,
    val observation: LocalObservation,
    /** Estimated site position before correction (derived from raw pose + active transform). */
    val estimatedBefore: Enu,
    val correctedAfter: Enu,
    val deltaM: Double,
    val transformBefore: Similarity2D,
    val transformAfter: Similarity2D,
    /** "TRANSLATION" or "TRANSLATION_AND_HEADING" */
    val correctionModel: String,
    val headingCorrectionDeg: Double? = null,
    val source: String = "USER_MANUAL",
    val notes: String? = null,
) : SessionEvent

enum class SyncMethod { MANUAL_TAP, AUDIO_CHIRP }
enum class SyncRole { EMITTER, RECEIVER, PARTICIPANT }

@Serializable
@SerialName("sync")
data class SyncEvent(
    override val id: String,
    override val t: Stamp,
    /** Identifier typed/shared on all devices for the same physical event. */
    val syncCode: String,
    val method: SyncMethod,
    val role: SyncRole,
    /** Best estimate of the physical event instant in this device monotonic clock. */
    val eventMonotonicNs: Long,
    val detection: AudioDetection? = null,
    val notes: String? = null,
    /** Lot 9, all optional (0.1 files stay valid): "lumisite-chirp-id/1" when an id frame follows the chirp. */
    val protocol: String? = null,
    /** Emitter ids (hex) recognised in this round, own id included. */
    val heardIds: List<String>? = null,
    /** Index (0-based, recording order) of this device's own chirp in the round, and of the peers' chirps. */
    val localChirpIndex: Int? = null,
    val peerChirpIndices: List<Int>? = null,
    /** Emission counter of this device for the round. */
    val emitCounter: Int? = null,
    /** Human-readable round code (generated, editable, not used for matching). */
    val label: String? = null,
) : SessionEvent

@Serializable
data class AudioDetection(
    val sampleRateHz: Int,
    val chirpStartHz: Double,
    val chirpEndHz: Double,
    val chirpDurationMs: Double,
    val detectedSampleIndex: Long,
    val peakScore: Double,
    val noiseFloorScore: Double,
    /** AudioTimestamp pair used to map frames -> monotonic ns (framePosition, nanoTime). */
    val timestampFrame: Long?,
    val timestampNs: Long?,
    val timestampTimebase: String?,
    /** Emitter only: monotonic ns when write() of the chirp returned (not the acoustic instant). */
    val emitWriteNs: Long? = null,
    val recordingFile: String? = null,
    /** Lot 9: decoded identification frame of the heard chirp. */
    val emitterId: String? = null,
    val frameCounter: Int? = null,
    val frameChecksumOk: Boolean? = null,
)

/** Generic field survey record; [payload] is the extension point for photometry, equipment, forms. */
@Serializable
@SerialName("survey")
data class SurveyEvent(
    override val id: String,
    override val t: Stamp,
    val kind: String,
    val note: String? = null,
    val pose: PoseSample? = null,
    val tracking: TrackingState,
    /** Position at record time as computed by the app with the transform active then (derived). */
    val estimatedSite: Enu? = null,
    /** "AR_POSE" | "AR_PLANE" | "AR_LAST_PLANE" | "AR_FLOOR_ESTIMATE" | "ESTIMATED" (50 cm fallback) | "MANUAL_MAP" | "MANUAL_EDIT" (moved after creation) | "NONE" (legacy only) */
    val positionSource: String,
    val manualSite: Enu? = null,
    val lastGps: GpsFix? = null,
    val payloadSchema: String? = null,
    val payload: JsonObject? = null,
    val attachments: List<Attachment> = emptyList(),
    val dxf: DxfHint? = null,
    /** Edit of an earlier record: id of the survey event this one replaces (optional, format 0.1 compatible). */
    /** Time of the original pose when this record is an edit (t = edit time): positions resolve on it. */
    val poseT: Stamp? = null,
    /** Aimed point (camera-centre ray hit) in the AR session frame [x, y, z] m; positions resolve on it when present. */
    val aimSession: List<Double>? = null,
    val supersedes: String? = null,
    /** true: the record [supersedes] points to is deleted. */
    val deleted: Boolean? = null,
) : SessionEvent

@Serializable
data class Attachment(
    val file: String,
    val mediaType: String,
    val sha256: String? = null,
    /** Photo role: "etiquette" | "vue"; "voix" for voice notes (lot 7). */
    val role: String? = null,
    val startUtcMs: Long? = null,
    val monotonicNs: Long? = null,
)

/** Hints for a future DXF conversion: no DXF is produced in this slice. */
@Serializable
data class DxfHint(val layer: String, val blockName: String, val rotationDeg: Double? = null, val attributes: Map<String, String> = emptyMap())

/**
 * Zone (format 0.2, decision O6): explicit event instead of ShapeEvent(kind = "zone"), which is still read (0.1 exports).
 * Outer ring and holes in site metres, closed implicitly. Edits are append-only ([supersedes] + optional [deleted]).
 */
@Serializable
@SerialName("zone")
data class ZoneEvent(
    override val id: String,
    override val t: Stamp,
    val points: List<Enu>,
    val holes: List<List<Enu>>? = null,
    /** ARCHITECTURAL | TECHNIQUE | CIRCULATION | EXTERIEUR */
    val zoneKind: String = "ARCHITECTURAL",
    val parentZoneId: String? = null,
    val label: String? = null,
    val comment: String? = null,
    val style: ShapeStyle = ShapeStyle(),
    /** MANUAL_MAP (PLAN_IMPORT later). */
    val source: String? = null,
    val supersedes: String? = null,
    val deleted: Boolean? = null,
) : SessionEvent {
    fun asShape() = ShapeEvent(id, t, "zone", "polygon", points, label = label, comment = comment, style = style, supersedes = supersedes,
        deleted = deleted, zoneKind = zoneKind, parentZoneId = parentZoneId, holes = holes, source = source)

    companion object {
        fun of(s: ShapeEvent) = ZoneEvent(s.id, s.t, s.points, s.holes, s.zoneKind ?: "ARCHITECTURAL", s.parentZoneId, s.label, s.comment,
            s.style, s.source, s.supersedes, s.deleted)
    }
}

/** Drawing style of a shape (fiche F): named colours, optional fill, width in dp. */
@Serializable
data class ShapeStyle(val stroke: String = "rouge", val fill: String? = null, val widthDp: Float = 3f, val opacity: Float = 1f)

/**
 * Shape, annotation or zone drawn on the map (lot 7). Geometry in metres in the site frame, never in pixels.
 * Edits are append-only like surveys: [supersedes] + optional [deleted].
 */
@Serializable
@SerialName("shape")
data class ShapeEvent(
    override val id: String,
    override val t: Stamp,
    /** "shape" | "annotation" | "zone" */
    val kind: String,
    /** "circle" | "rectangle" | "line" | "polyline" | "arrow" | "polygon" | "point" */
    val geometryType: String,
    val points: List<Enu>,
    val radiusM: Double? = null,
    val label: String? = null,
    val comment: String? = null,
    /** Diagnostic annotation type (Passage de câble, Câble à nu, …). */
    val annotationType: String? = null,
    val style: ShapeStyle = ShapeStyle(),
    /** Survey this shape/comment is attached to. */
    val attachedTo: String? = null,
    val supersedes: String? = null,
    val deleted: Boolean? = null,
    /** Zones (lot 13, kind = "zone"): ARCHITECTURAL | TECHNIQUE | CIRCULATION | EXTERIEUR. zone_id = id. */
    val zoneKind: String? = null,
    val parentZoneId: String? = null,
    /** Inner rings (courtyard). Outer ring and holes: any orientation, closed implicitly (last ≠ first). */
    val holes: List<List<Enu>>? = null,
    /** MANUAL_MAP (PLAN_IMPORT later). */
    val source: String? = null,
) : SessionEvent
