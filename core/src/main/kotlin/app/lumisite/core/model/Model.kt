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
    /** "AR_POSE" | "MANUAL_MAP" | "MANUAL_EDIT" (moved after creation) | "NONE" (legacy only) */
    val positionSource: String,
    val manualSite: Enu? = null,
    val lastGps: GpsFix? = null,
    val payloadSchema: String? = null,
    val payload: JsonObject? = null,
    val attachments: List<Attachment> = emptyList(),
    val dxf: DxfHint? = null,
    /** Edit of an earlier record: id of the survey event this one replaces (optional, format 0.1 compatible). */
    val supersedes: String? = null,
    /** true: the record [supersedes] points to is deleted. */
    val deleted: Boolean? = null,
) : SessionEvent

@Serializable
data class Attachment(val file: String, val mediaType: String, val sha256: String? = null)

/** Hints for a future DXF conversion: no DXF is produced in this slice. */
@Serializable
data class DxfHint(val layer: String, val blockName: String, val rotationDeg: Double? = null, val attributes: Map<String, String> = emptyMap())

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
) : SessionEvent
