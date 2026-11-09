@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.lumisite.android.ui

import app.lumisite.core.position.CalibrationQuality
import app.lumisite.core.export.ResumableExport
import app.lumisite.android.ExportWorker

import app.lumisite.android.ControlRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import app.lumisite.core.survey.Details
import app.lumisite.core.survey.Shapes
import androidx.compose.runtime.mutableStateMapOf
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lumisite.core.export.SessionExporter
import app.lumisite.android.data.DiagLog
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.lumisite.android.SessionController
import app.lumisite.core.compute.SurveyPositions
import app.lumisite.android.capture.ArTracker
import app.lumisite.android.capture.AudioSync
import app.lumisite.android.data.Repository
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.RelocalizationEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.SyncRole
import androidx.compose.material3.TextButton
import app.lumisite.core.model.TrackingState
import app.lumisite.core.sync.DeviceSyncLog
import app.lumisite.core.sync.SyncEstimator
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Mode { NONE, SYNC, CALIBRATE, SURVEY, DRAW, RELOCALIZE, EXPORT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(activity: Activity, repo: Repository, c: SessionController, onClosed: () -> Unit) {
    val st by c.state.collectAsState()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.NONE) }
    var picked by remember { mutableStateOf<LatLon?>(null) }
    var mapCenter by remember { mutableStateOf<LatLon?>(c.site.origin) }
    var arReady by remember { mutableStateOf(false) }
    var arAvail by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var perms by remember { mutableStateOf(false) }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }
    var selectedSurvey by remember { mutableStateOf<String?>(null) }
    // Session > Annoter > objet > « Déplacer » : one finger drags the symbol (the map does not move), then Valider / Annuler.
    var movingId by remember { mutableStateOf<String?>(null) }
    var moveGrab by remember { mutableStateOf<Enu?>(null) }
    var moveTarget by remember { mutableStateOf<Enu?>(null) }
    val draw = remember { DrawState() }
    var follow by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms = true }
    fun granted(p: String) = ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(perms) {
        if (!perms) return@LaunchedEffect
        c.startCapture(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION))
        var a = ArTracker.availability(activity)
        repeat(10) { if (a.isTransient) { delay(300); a = ArTracker.availability(activity) } }
        arAvail = a
        if (a == ArCoreApk.Availability.SUPPORTED_INSTALLED && granted(Manifest.permission.CAMERA)) {
            arReady = c.ar.create()
            if (arReady) { c.ar.resume(); c.setArStatus("ARCore actif") } else c.setArStatus("ARCore n'a pas démarré (${c.ar.lastError}) : positions manuelles (carte) uniquement.")
        } else c.setArStatus(when {
            !granted(Manifest.permission.CAMERA) -> "Caméra refusée : suivi AR impossible, mode manuel (carte)."
            a == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> "ARCore non supporté sur cet appareil : mode manuel (carte)."
            else -> "ARCore : $a"
        })
        if (!arReady) c.markArUnavailable("AR_NOT_RUNNING")
        if (app.lumisite.android.DebugFlags.simulatedPoses) c.startSimulatedPoses()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    if (c.closed) return@LifecycleEventObserver
                    if (arReady) c.ar.resume() else if (arAvail != null) c.markArUnavailable("AR_NOT_RUNNING")
                    glView?.onResume(); if (perms) c.startCapture(granted(Manifest.permission.ACCESS_FINE_LOCATION))
                }
                Lifecycle.Event.ON_PAUSE -> { glView?.onPause(); c.pauseCapture() }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var closing by remember { mutableStateOf(false) }
    // Same sequence for "Terminer", the top back button and the system back gesture:
    // stop GL drawing, pause the GLSurfaceView (blocks until the GL thread is idle), close ARCore, write session_stopped, then navigate.
    var confirmExport by remember { mutableStateOf<Int?>(null) }
    fun finish(confirmed: Boolean = false) {
        if (closing) return
        // D14: during an export, Terminer asks for a confirmation (double appui) and never stops the export (WorkManager).
        val running = ExportWorker.live[c.sessionId]
        if (running != null && !confirmed) { confirmExport = running; return }
        closing = true
        running?.let { pct -> android.widget.Toast.makeText(activity, "Export en cours ($pct %), il continue en arrière-plan", android.widget.Toast.LENGTH_LONG).show() }
        c.ar.rendering = false
        runCatching { glView?.onPause() }
        runCatching { c.close("user") }
        onClosed()
    }
    BackHandler { finish() }
    confirmExport?.let { pct ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { confirmExport = null },
            // Dialogs are separate windows: re-enable testTag → resource-id for Maestro.
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text("Export en cours ($pct %)") },
            text = { Text("L'export continue en arrière-plan après la fin de la session. Android peut le différer : il reprendra où il s'est arrêté, sans refaire ce qui est vérifié. Terminer la session ?") },
            confirmButton = { Button({ confirmExport = null; finish(confirmed = true) }, Modifier.testTag("session_terminer_confirmer")) { Text("Terminer quand même") } },
            dismissButton = { OutlinedButton({ confirmExport = null }, Modifier.testTag("session_terminer_rester")) { Text("Rester") } })
    }

    fun refPoint(label: String): ReferencePoint? {
        val p = picked ?: run { c.notify("Touchez d'abord la carte pour choisir le point."); return null }
        return ReferencePoint(label, c.ltp.toEnu(p).let { Enu(it.e, it.n, 0.0) }, p, "MAP_TAP", uncertaintyM = null)
    }

    val events = st.events
    val relocLines = events.filterIsInstance<RelocalizationEvent>().map {
        MapLine(c.ltp.toLatLon(it.estimatedBefore), c.ltp.toLatLon(it.correctedAfter), android.graphics.Color.RED, "Recalage %.2f m".format(it.deltaM))
    }
    val surveys = SurveyPositions.resolveAll(events)
    val surveyPts = surveys.mapNotNull { r ->
        (if (r.event.id == movingId) moveTarget ?: r.site else r.site)?.let { MapPoint(c.ltp.toLatLon(it.copy(u = 0.0)), "${r.event.kind} ${r.event.note ?: ""}", if (r.event.id == selectedSurvey) android.graphics.Color.rgb(255, 140, 0) else android.graphics.Color.MAGENTA, r.event.id) }
    }
    val shapes = Shapes.current(events)
    val currentLl = st.currentSite?.let { c.ltp.toLatLon(it.copy(u = 0.0)) }
    val heading = c.currentHeadingDeg()
    val sector = if (st.currentSite != null && heading != null) {
        val o = st.currentSite!!
        fun at(deg: Double, r: Double) = Math.toRadians(deg).let { c.ltp.toLatLon(Enu(o.e + r * kotlin.math.sin(it), o.n + r * kotlin.math.cos(it), 0.0)) }
        listOf(c.ltp.toLatLon(o.copy(u = 0.0)), at(heading - 25, 3.0), at(heading, 3.5), at(heading + 25, 3.0))
    } else emptyList()

    val posStatus = app.lumisite.core.compute.PositionStatus.of(now, c.isCalibrated(), st.tracking, st.lastArValidMs, st.lastGps?.t?.utcEpochMs, picked != null)
    // D8: the displayed AR position glides to its new value in ~0.5 s (display only; recorded data are never smoothed).
    val glide = remember { androidx.compose.animation.core.Animatable(androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.VectorConverter) }
    var glideInit by remember { mutableStateOf(false) }
    LaunchedEffect(st.lastArSite) {
        st.lastArSite?.let {
            val o = androidx.compose.ui.geometry.Offset(it.e.toFloat(), it.n.toFloat())
            if (!glideInit || (glide.value - o).getDistance() > 200f) { glide.snapTo(o); glideInit = true }
            else glide.animateTo(o, androidx.compose.animation.core.tween(500))
        }
    }
    val arrows = buildList {
        when (posStatus.source) {
            app.lumisite.core.compute.PositionStatus.Source.AR -> st.lastArSite?.let {
                add(MapArrow(c.ltp.toLatLon(app.lumisite.core.geo.Enu(glide.value.x.toDouble(), glide.value.y.toDouble(), 0.0)), st.lastHeadingDeg, if (posStatus.lost) android.graphics.Color.GRAY else android.graphics.Color.rgb(0, 120, 255),
                    if (posStatus.lost) "Dernière position AR valide (suivi perdu)" else "Position et orientation (AR)"))
            }
            // D1: the GNSS position is only indicative; grey when D7 says it is unusable.
            app.lumisite.core.compute.PositionStatus.Source.GPS -> st.lastGps?.let { add(MapArrow(LatLon(it.lat, it.lon), null, if (st.gnssQuality == app.lumisite.core.position.GnssQuality.INUTILISABLE) android.graphics.Color.GRAY else android.graphics.Color.rgb(0, 150, 136), "Position GPS (${st.gnssQuality.label})")) }
            else -> {}
        }
        st.calibMark?.let { add(MapArrow(c.ltp.toLatLon(it.site.copy(u = 0.0)), it.headingDeg, android.graphics.Color.rgb(255, 140, 0), "${it.label} : position du téléphone et direction de la caméra")) }
    }
    val relocTarget = if (mode == Mode.RELOCALIZE) picked?.let { c.ltp.toEnu(it).copy(u = 0.0) } else null
    val relocPreview = if (relocTarget != null && st.currentSite != null)
        listOf(MapLine(c.ltp.toLatLon(st.currentSite!!.copy(u = 0.0)), picked!!, android.graphics.Color.rgb(255, 140, 0), "Décalage à corriger")) else emptyList()
    val controlPts = (c.controlRows("CALIBRATION").map { it to "Calibrage" } + c.controlRows("RELOCALIZATION").map { it to "Recalage" }).map { (r, k) ->
        MapPoint(c.ltp.toLatLon(r.e.observation.reference.site.copy(u = 0.0)), "$k ${r.n} (${r.status})", if (k == "Calibrage") android.graphics.Color.rgb(255, 140, 0) else android.graphics.Color.rgb(128, 0, 200), "cp:" + r.e.id)
    }
    val map: @Composable (Modifier) -> Unit = { m ->
        Box(m.testTag("session_carte")) {
            OsmMap(
                Modifier.fillMaxSize(), c.site.origin, 19.0,
                MapContent(
                    origin = c.site.origin, features = c.site.osm?.features.orEmpty(), selectedFeatureRef = c.site.selectedBuildingOsmRef,
                    trajectory = st.trajectory, uncorrected = emptyList(),
                    lines = relocLines + relocPreview + shapeLines(c, shapes, draw.selected), points = surveyPts + shapePoints(c, shapes, draw) + controlPts, picked = picked,
                    follow = if (follow) currentLl else null,
                    selectedFeatureRefs = c.site.selectedBuildingOsmRefs.toSet(),
                    preview = draw.preview().map { c.ltp.toLatLon(it) }, arrows = arrows,
                ),
                onTap = { if (mode == Mode.DRAW && draw.consumesTap()) draw.onTap(c, c.ltp.toEnu(it).copy(u = 0.0)) else picked = it },
                onPointTap = { id -> if (id.startsWith("cp:")) Unit else if (id.startsWith("shape:")) draw.selected = id.removePrefix("shape:") else selectedSurvey = if (selectedSurvey == id) null else id },
                siteBbox = c.site.bbox ?: c.site.osm?.bbox, reticle = true,
                onViewport = { ctr, _ -> mapCenter = ctr },
                onScale = { mpd -> draw.tolM = mpd * app.lumisite.core.survey.Sketch.TOUCH_DP },
                onFinger = if (movingId != null) { phase, ll ->
                    val p = c.ltp.toEnu(ll)
                    val start = surveys.firstOrNull { it.event.id == movingId }?.site
                    when (phase) {
                        // A second drag continues from the current target.
                        FingerPhase.DOWN -> { val t = moveTarget; moveGrab = if (t != null && start != null) Enu(p.e - (t.e - start.e), p.n - (t.n - start.n), 0.0) else p }
                        FingerPhase.MOVE, FingerPhase.UP -> if (start != null) moveGrab?.let { g -> moveTarget = app.lumisite.core.survey.DragMove.target(start, g, p) }
                        FingerPhase.CANCEL -> Unit
                    }
                } else if (mode == Mode.DRAW && draw.capturesFinger()) { phase, ll ->
                    val p = c.ltp.toEnu(ll).copy(u = 0.0)
                    when (phase) {
                        FingerPhase.DOWN, FingerPhase.MOVE -> draw.hover = p
                        FingerPhase.UP -> { draw.hover = null; draw.onTap(c, p) }
                        FingerPhase.CANCEL -> draw.hover = null
                    }
                } else null,
            )
            // Right column, bottom up, no overlap: the scale (« largeur visible ») stays bottom-left, the legend top-left under half
            // the width so it never covers Session > carte > « Fond ». Touch targets ≥ 48 dp.
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { picked = mapCenter }, modifier = Modifier.heightIn(min = 48.dp).background(Color(0xCCFFFFFF)).testTag("session_point_a_la_croix")) {
                    Text("Point à la croix")
                }
                OutlinedButton(onClick = { follow = !follow }, modifier = Modifier.heightIn(min = 48.dp).background(Color(0xCCFFFFFF)).testTag("session_trajectoire_estimee_vert_non_gar")) {
                    Text(if (follow) "Suivre : actif" else "Centrer / Suivre")
                }
            }
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.5f)) {
                Text(posStatus.text() + " · GNSS " + st.gnssQuality.label, Modifier.background(if (posStatus.lost) Color(0xE6B00020) else Color(0xE6FFFFFF)).padding(6.dp).testTag("session_carte_source"),
                    color = if (posStatus.lost) Color.White else Color.Black, style = MaterialTheme.typography.labelMedium)
                Text("Vert : trajectoire estimée (non garantie) · rouge : corrections",
                    Modifier.background(Color(0xCCFFFFFF)).padding(4.dp), color = Color.Black, style = MaterialTheme.typography.labelSmall)
                if (mode == Mode.DRAW) draw.polygonHint()?.let {
                    Text(it, Modifier.background(Color(0xE6FFF3C4)).padding(6.dp).testTag("session_annoter_polygone_aide"),
                        color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    // Compact status line at the very top (prompt v4 2.1): « AR ● » and « TRK » pills (tap: details), recording indicators, theme.
    val statusRow: @Composable () -> Unit = {
        var showTrk by remember { mutableStateOf(false) }
        var showAr by remember { mutableStateOf(false) }
        val playback by c.ar.playback.collectAsState()
        val recording by c.ar.recording.collectAsState()
        val (trkBg, trkLabel) = trackingLabel(st.tracking)
        Column(Modifier.fillMaxWidth().testTag("session_etat")) {
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                Pill(if (arReady) "AR ●" else "AR ○", if (arReady) Color(0xFF1B8A4B) else Color(0xFF555555), "session_etat_ar") { showAr = !showAr }
                Pill("TRK", trkBg, "session_etat_trk") { showTrk = !showTrk }
                if (playback) Pill("PLAYBACK", Color(0xFFB00020), "session_ar_playback")
                if (recording) Pill("● REC ARCore", Color(0xFFB00020), "session_ar_enregistrement")
                ThemeToggle(activity)
            }
            if (showTrk) Text(trkLabel + (st.trackingReason?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("session_etat_trk_detail"))
            if (showAr) Text(st.arStatus + if (arReady) " · Caméra active pour le suivi AR, aucune image ni vidéo enregistrée." else "", style = MaterialTheme.typography.labelSmall)
        }
    }
    // Camera block: preview (always shown when ARCore runs) with its aim / torch / measure controls beside it. Nothing drawn over it.
    val simulatedArHeader = remember {
        app.lumisite.android.BuildConfig.DEBUG && android.provider.Settings.Global.getString(activity.contentResolver, "lumisite_simuler_entete_ar") == "1"
    }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!arReady && simulatedArHeader) {
                // Debug/CI only (settings put global lumisite_simuler_entete_ar 1): same footprint as the real camera block.
                androidx.compose.foundation.layout.Box(Modifier.width(200.dp).height(150.dp).background(Color.DarkGray).testTag("session_ar_simule")) { Text("AR simulé", color = Color.White, modifier = Modifier.align(Alignment.Center)) }
                ArControls(c)
            }
            if (arReady) {
                androidx.compose.foundation.layout.Box(Modifier.width(200.dp).height(150.dp)) {
                    AndroidView(factory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            preserveEGLContextOnPause = true; setEGLContextClientVersion(2)
                            setRenderer(c.ar); renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            glView = this
                        }
                    }, modifier = Modifier.width(200.dp).height(150.dp))
                    ArReticle(c)
                }
                ArControls(c)
            }
            if (!arReady && !simulatedArHeader) Text("Caméra AR indisponible : ${st.arStatus}", style = MaterialTheme.typography.labelSmall)
        }
    }
    // Last message (confirmation / refusal) fixed above the action bar, so it stays visible whatever the panel scroll.
    val messageBar: @Composable () -> Unit = {
        st.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 2.dp).testTag("session_message")) }
    }
    // D13: persistent banner while a resumed session is not tied to the new AR frame (or only by one point).
    val resumeBanner: @Composable () -> Unit = {
        val reposition = c.needsReposition()
        if (reposition || c.approximateReposition()) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp).testTag("session_reprise_bandeau")) {
            Text(if (reposition) "Position du téléphone perdue à la fermeture. Touchez 2 points déjà calibrés pour reprendre."
                else "Repositionnement approximatif (1 point : translation et cap seulement). Ajoutez un 2e point.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${SurveyPositions.current(st.events).size} relevé(s), ${c.previousControlRows().size} point(s) de calibrage de la session précédente",
                    Modifier.weight(1f).testTag("session_reprise_nb"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                TextButton({ mode = Mode.CALIBRATE }, modifier = Modifier.heightIn(min = 48.dp).testTag("session_reprise_positionner")) { Text("Calibrer") }
            }
        }
    }
    val actionBar: @Composable () -> Unit = {
        // 7 equal cells (icon + short label, ≥ 56 dp high), one line, fixed at the bottom: never scrolled, never over the camera.
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).testTag("session_barre_actions")) {
            listOf(Mode.SYNC to ("🔊" to "Synchro"), Mode.CALIBRATE to ("🎯" to "Calibrer"), Mode.SURVEY to ("📍" to "Relevé"), Mode.DRAW to ("✏️" to "Annoter"),
                Mode.RELOCALIZE to ("🧭" to "Recaler"), Mode.EXPORT to ("📤" to "Export")).forEach { (m, il) ->
                val tag = "session_" + when (m) { Mode.CALIBRATE -> "calibrer"; Mode.RELOCALIZE -> "recaler"; Mode.SURVEY -> "releve"; Mode.DRAW -> "annoter"; Mode.SYNC -> "synchro"; Mode.EXPORT -> "export"; else -> "aucun" }
                ActionCell(il.first, il.second, mode == m, tag, Modifier.weight(1f)) { mode = if (mode == m) Mode.NONE else m; c.clearMessage() }
            }
            ActionCell("⏹", "Terminer", false, "session_terminer_la_session", Modifier.weight(1f), enabled = !closing) { finish() }
        }
    }
    val panel: @Composable (Modifier) -> Unit = { m ->
        // Back to the top when the mode or the selected object changes: the object panel and the mode controls come first.
        val panelScroll = rememberScrollState()
        LaunchedEffect(mode, selectedSurvey) { panelScroll.scrollTo(0) }
        Column(m.verticalScroll(panelScroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (arAvail == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED || arAvail == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD) {
                Button(onClick = { ArTracker.ensureInstalled(activity, true); perms = false; perms = true }, modifier = Modifier.testTag("session_installer_mettre_a_jour_arcore")) { Text("Installer / mettre à jour ARCore") }
            }
            surveys.firstOrNull { it.event.id == selectedSurvey }?.let { r ->
                SelectedSurveyPanel(c, r, picked, moving = movingId == r.event.id, moveTarget = moveTarget,
                    onMove = { start -> movingId = if (start) r.event.id else null; moveGrab = null; moveTarget = null },
                    onReselect = { selectedSurvey = it },
                    onDone = { selectedSurvey = null; movingId = null })
            }
            val pickedTxt = picked?.let { val e = c.ltp.toEnu(it); "Point choisi : E %.2f, N %.2f m".format(e.e, e.n) } ?: "Touchez la carte pour choisir le point de référence."
            when (mode) {
                Mode.CALIBRATE -> {
                    CalibratePanel(c, pickedTxt, ::refPoint)
                    st.calibMark?.let { k -> Text("Repère posé sur le plan (orange) — ${k.label} : téléphone en E %.2f, N %.2f m".format(k.site.e, k.site.n) +
                        (k.headingDeg?.let { " ; caméra vers %.0f° (depuis le nord)".format(it) } ?: ""), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session_calibrer_repere")) }
                }
                Mode.RELOCALIZE -> {
                    val est = st.currentSite
                    Text(when {
                        relocTarget == null -> "Touchez la carte au point où vous êtes : le décalage s'affichera avant de valider."
                        est == null -> "Décalage inconnu : suivi AR ou calibration manquant."
                        else -> "Décalage à corriger (orange) : %.2f m (E %+.2f m, N %+.2f m)".format(kotlin.math.hypot(relocTarget.e - est.e, relocTarget.n - est.n), relocTarget.e - est.e, relocTarget.n - est.n)
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("session_recaler_decalage"))
                    RelocPanel(c, pickedTxt, ::refPoint)
                }
                Mode.SURVEY -> SurveyPanel(c, st.tracking, picked != null, ::refPoint, c.needsReposition())
                Mode.DRAW -> DrawPanel(c, draw, mapCenter?.let { c.ltp.toEnu(it).copy(u = 0.0) }, selectedSurvey) { selectedSurvey = it }
                Mode.SYNC -> SyncPanel(activity, c)
                Mode.EXPORT -> ExportPanel(activity, repo, c)
                Mode.NONE -> {}
            }
            // Generic status after the mode panel, so the controls of the active mode stay at the top of the small panel.
            val gps = st.lastGps
            Text(
                "Poses : ${st.poseCount} · IMU : ${st.imuCount} · GPS : " +
                    (gps?.let { "%.6f, %.6f ±%s m (%s)".format(it.lat, it.lon, it.horizontalAccuracyM?.let { a -> "%.0f".format(a) } ?: "?", it.provider) } ?: "aucun fix") +
                    " · statut ${st.gpsStatus}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                st.currentSite?.let { "Position du téléphone (repère site) E %.2f m, N %.2f m".format(it.e, it.n) } ?: "Position du téléphone : non calibrée",
                style = MaterialTheme.typography.bodySmall,
            )
            st.distanceSinceFixM?.let { Text("Distance parcourue depuis la dernière calibration/recalage : %.1f m (la dérive augmente avec la distance)".format(it), style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Text("Journal : ${events.size} événements (calibrations ${events.count { it is app.lumisite.core.model.CalibrationEvent }}, recalages ${events.count { it is RelocalizationEvent }}, relevés ${events.count { it is SurveyEvent }})", style = MaterialTheme.typography.labelSmall)
        }
    }

    // Layout (prompt v4 2.1): status line on top, camera block (≤ 25 % of the height), large map, panel, action bar fixed at the bottom.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Portrait with a short height (keyboard open with adjustResize): while typing, the camera block is clipped to nothing
        // (still composed, so the camera GL view is not torn down) and the map yields room to the input panel. Landscape keeps it.
        val short = maxHeight < 480.dp && maxWidth <= maxHeight
        val topMax = if (short) 0.dp else maxHeight * 0.25f
        val statusBlock: @Composable (Modifier) -> Unit = { m ->
            Column(m.heightIn(max = topMax).verticalScroll(rememberScrollState()).testTag("session_bloc_ar")) { header() }
        }
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                map(Modifier.weight(0.55f).fillMaxHeight())
                Column(Modifier.weight(0.45f).fillMaxHeight()) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { statusRow(); statusBlock(Modifier) }
                    HorizontalDivider(); panel(Modifier.weight(1f).padding(horizontal = 8.dp))
                    resumeBanner()
                    messageBar()
                    actionBar()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { statusRow(); statusBlock(Modifier) }
                map(Modifier.fillMaxWidth().weight(if (short) 0.25f else 0.6f))
                panel(Modifier.weight(if (short) 0.75f else 0.4f).padding(horizontal = 8.dp, vertical = 4.dp))
                resumeBanner()
                messageBar()
                actionBar()
            }
        }
    }
}

/** TRK pill colour (green = tracking, orange = limited, red = lost, grey = off) and the full label shown on tap. */
private fun trackingLabel(s: TrackingState): Pair<Color, String> = when (s) {
    TrackingState.TRACKING -> Color(0xFF1B8A4B) to "Suivi : initialisé (TRACKING)"
    TrackingState.INITIALIZING -> Color(0xFFD08000) to "Suivi : initialisation… bougez lentement l'appareil"
    TrackingState.DEGRADED -> Color(0xFFD08000) to "Suivi : dégradé (pause temporaire)"
    TrackingState.LOST -> Color(0xFFB00020) to "Suivi : perdu"
    TrackingState.STOPPED -> Color(0xFF555555) to "Suivi : arrêté"
    TrackingState.UNAVAILABLE -> Color(0xFF555555) to "Suivi AR indisponible : positions manuelles uniquement"
}

@Composable
private fun Pill(text: String, bg: Color, tag: String, onClick: (() -> Unit)? = null) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Text(text, Modifier.background(bg, shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 10.dp, vertical = 4.dp).testTag(tag), color = Color.White, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun ActionCell(icon: String, label: String, selected: Boolean, tag: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 56.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).testTag(tag).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(icon, fontSize = 18.sp)
        Text(label, fontSize = 10.sp, maxLines = 1, softWrap = false, color = if (enabled) Color.Unspecified else Color.Gray)
    }
}

@Composable
private fun TiltText(c: SessionController) {
    val st by c.state.collectAsState()
    val pose = st.lastPose?.takeIf { st.tracking == TrackingState.TRACKING }
    val tilt = pose?.let { app.lumisite.core.position.Tilt.fromArQuaternion(it.q.toDoubleArray()) } ?: c.sensors.cameraTiltDeg
    Text(tilt?.let { "Inclinaison de visée (calculée, " + (if (pose != null) "pose ARCore" else "capteur de rotation") + ") : " + "%+.0f°".format(it) } ?: "Inclinaison de visée : capteurs indisponibles",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session_inclinaison"))
}

/** Session > Calibrer / Recaler > tableau: one row per point (n°, time, plan coordinates, residual, status, « Supprimer »). */
@Composable
private fun ControlTable(c: SessionController, rows: List<ControlRow>, prefix: String, previous: List<ControlRow> = emptyList()) {
    val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT) }
    Column(Modifier.fillMaxWidth().background(Color(0x11000000)).padding(4.dp).testTag(prefix + "_tableau")) {
        Text(if (rows.isEmpty() && previous.isEmpty()) "Aucun point." else "N° · heure · E / N (m) · résidu · statut", style = MaterialTheme.typography.labelSmall)
        rows.forEach { r ->
            val site = r.e.observation.reference.site
            Row(Modifier.fillMaxWidth().testTag(prefix + "_ligne_" + r.n), verticalAlignment = Alignment.CenterVertically) {
                Text("${r.n} · ${fmt.format(java.util.Date(r.e.t.utcEpochMs))} · " + "%.1f / %.1f".format(site.e, site.n) + " · " + (r.residualM?.let { "%.2f m".format(it) } ?: "—") + " · " + r.status,
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (r.status == "rejeté") Color(0xFFB00020) else Color.Unspecified)
                TextButton({ c.removeControlPoint(r.e.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag(prefix + "_supprimer_" + r.n)) { Text("Supprimer") }
            }
        }
        // D13: points of the earlier AR frame, kept for the record (not used by the live solve, not deletable here).
        previous.forEach { r ->
            val site = r.e.observation.reference.site
            Text("${r.n} · ${fmt.format(java.util.Date(r.e.t.utcEpochMs))} · " + "%.1f / %.1f".format(site.e, site.n) + " · " + r.status,
                Modifier.fillMaxWidth().heightIn(min = 32.dp).testTag(prefix + "_precedente_" + r.n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalibratePanel(c: SessionController, pickedTxt: String, ref: (String) -> ReferencePoint?) {
    var heading by remember { mutableStateOf("") }
    c.state.collectAsState().value
    val rows = c.controlRows("CALIBRATION")
    Text("Calibrage multi-points : touchez sur la carte le point où vous êtes, validez ; 1er point avec le cap de visée, puis 2e, 3e… point.", style = MaterialTheme.typography.labelLarge)
    Text(pickedTxt, style = MaterialTheme.typography.bodySmall)
    TiltText(c)
    val compass = c.sensors.cameraHeadingDeg
    if (rows.isEmpty()) {
        OutlinedTextField(heading, { heading = it }, label = { Text("Cap de visée du 1er point (°)") }, modifier = Modifier.testTag("session_calibrer_cap_de_visee"))
        OutlinedButton(enabled = compass != null, onClick = { heading = "%.1f".format(java.util.Locale.ROOT, compass ?: 0.0) }, modifier = Modifier.testTag("session_calibrer_boussole_0f_prec")) { Text(compass?.let { "Boussole %.0f° (préc. ${c.sensors.headingAccuracy})".format(it) } ?: "Boussole indisponible") }
    }
    Button(onClick = {
        val r = ref("Calibrage ${rows.size + 1}") ?: return@Button
        val h = if (rows.isEmpty()) heading.replace(',', '.').toDoubleOrNull() else null
        c.addControlPoint("CALIBRATION", r, h, h?.let { if (compass != null && "%.1f".format(java.util.Locale.ROOT, compass) == heading) "COMPASS_ROTATION_VECTOR" else "MANUAL" })
    }, modifier = Modifier.testTag("session_calibrer_valider_la_calibration")) { Text("Valider le point ${rows.size + 1}") }
    ForceOverride(c, "CALIBRATION", "session_calibrer")
    if (rows.size >= 2) OutlinedButton({ ref("Contrôle")?.let { c.controlCheck(it) } }, modifier = Modifier.testTag("session_calibrer_controler_ici")) { Text("Contrôler ici (hors calcul)") }
    ControlTable(c, rows, "session_calibrer", c.previousControlRows())
    Text("Conseil : au moins 3 points, idéalement 4 à 6 aux extrémités du bâtiment, jamais alignés, + 1 point de contrôle.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session_calibrer_conseil"))
    c.calibrationGrade()?.let { g ->
        val col = when (g.level) { CalibrationQuality.Level.BON -> Color(0xFF2E7D32); CalibrationQuality.Level.MOYEN -> Color(0xFFEF6C00); else -> Color(0xFFC62828) }
        Text("Qualité : ${g.level.label}" + (if (g.reasons.isEmpty()) "" else " (" + g.reasons.joinToString(", ") + ")"),
            color = col, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("session_calibrer_qualite"))
    }
    OutlinedButton({ c.clearControlPoints(relocalizationsOnly = false) }, modifier = Modifier.testTag("session_calibrer_nouveau_calibrage")) { Text("Nouveau calibrage") }
}

/**
 * Décision A (09/11): after a point refused as contradictory, « Recaler quand même » records it anyway, with a cause chosen
 * by the user (journaled with the overridden refusal). Older points are kept: the user removes them from the table.
 */
@Composable
private fun ForceOverride(c: SessionController, kind: String, prefix: String) {
    val f = c.state.collectAsState().value.forcible?.takeIf { it.kind == kind } ?: return
    var ask by remember { mutableStateOf(false) }
    OutlinedButton({ ask = true }, modifier = Modifier.testTag(prefix + "_recaler_quand_meme")) { Text("Recaler quand même") }
    if (ask) {
        val loss = c.lastTrackingLoss()
        androidx.compose.material3.AlertDialog(onDismissRequest = { ask = false }, modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text("Recaler quand même ?") },
            text = {
                Text(f.refusal.message + "\n\nLe point sera enregistré à la position actuelle du téléphone, avec la cause choisie. Les points précédents sont conservés." +
                    (loss?.let { "\nDernière perte de suivi ARCore : $it." } ?: "\nAucune perte de suivi ARCore journalisée dans cette session."),
                    modifier = Modifier.testTag("session_forcer_explication"))
            },
            confirmButton = {
                Column {
                    TextButton({ ask = false; c.forceControlPoint("perte de suivi ARCore" + (loss?.let { " ($it)" } ?: "")) },
                        modifier = Modifier.testTag("session_forcer_cause_perte_suivi")) { Text("Cause : perte de suivi ARCore") }
                    TextButton({ ask = false; c.forceControlPoint("point carte vérifié sur place") },
                        modifier = Modifier.testTag("session_forcer_cause_point_verifie")) { Text("Cause : point carte vérifié sur place") }
                }
            },
            dismissButton = { TextButton({ ask = false }, modifier = Modifier.testTag("session_forcer_annuler")) { Text("Annuler") } })
    }
}

@Composable
private fun RelocPanel(c: SessionController, pickedTxt: String, ref: (String) -> ReferencePoint?) {
    var headingCorr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    c.state.collectAsState().value
    val rows = c.controlRows("RELOCALIZATION")
    Text("Recalage : placez-vous sur un point connu, touchez-le sur la carte et confirmez. Chaque recalage s'ajoute aux calibrages (les plus récents pèsent plus) ; la trajectoire antérieure est conservée.", style = MaterialTheme.typography.labelLarge)
    Text(pickedTxt, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(headingCorr, { headingCorr = it }, label = { Text("Correction de cap (°, optionnel, + = horaire)") }, modifier = Modifier.testTag("session_recaler_correction_de_cap_optionnel_hora"))
    OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.testTag("session_recaler_note"))
    Button(onClick = { ref("Recalage ${rows.size + 1}")?.let { c.addControlPoint("RELOCALIZATION", it, headingCorrectionDeg = headingCorr.replace(',', '.').toDoubleOrNull(), note = note.ifBlank { null }) } }, modifier = Modifier.testTag("session_recaler_je_suis_ici_recaler")) { Text("Je suis ici : recaler") }
    ForceOverride(c, "RELOCALIZATION", "session_recaler")
    ControlTable(c, rows, "session_recaler")
    OutlinedButton({ c.clearControlPoints(relocalizationsOnly = true) }, modifier = Modifier.testTag("session_recaler_nouveau_recalage")) { Text("Nouveau recalage") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyGrid(c: SessionController, selected: String?, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        c.catalog.entries.forEach { f -> FilterChip(selected == f.blockName, { onPick(f.blockName) }, { Text(f.label) }, modifier = Modifier.testTag("session_releve_famille_" + f.blockName)) }
    }
}

@Composable
private fun SurveyPanel(c: SessionController, tracking: TrackingState, hasPick: Boolean, ref: (String) -> ReferencePoint?, arBlocked: Boolean = false) {
    var family by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var useMap by remember { mutableStateOf(tracking != TrackingState.TRACKING || arBlocked) }
    LaunchedEffect(arBlocked) { if (arBlocked) useMap = true }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Survives the activity being recreated while the camera app is in front.
    var photoFor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    // While the photo is proposed the panel shows only that (short, so it is on screen without scrolling).
    photoFor?.let { id -> key(id) { PhotoPrompt(c, id) { photoFor = null } }; return }
    Text("Relevé : famille du catalogue ${c.catalog.version} (1 tap), position AR calibrée ou point touché sur la carte.", style = MaterialTheme.typography.labelLarge)
    FamilyQuickGrid(c, family) { family = it }
    val details = remember { mutableStateMapOf<String, String>() }
    DetailsForm(family, details)
    OutlinedTextField(note, { note = it }, label = { Text("Commentaire (facultatif)") }, modifier = Modifier.testTag("session_releve_commentaire_facultatif"))
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useMap, { if (!arBlocked) useMap = it }, enabled = !arBlocked, modifier = Modifier.testTag("session_releve_checkbox")); Text("Position manuelle (point touché sur la carte)", style = MaterialTheme.typography.bodySmall) }
    if (arBlocked) Text("Position du téléphone perdue à la fermeture : relevé par point visé AR impossible. Touchez la carte, ou Calibrer (2 points).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("session_releve_pose_ar_desactivee"))
    Button(enabled = family != null && (!useMap || hasPick), onClick = {
        val manual = if (useMap) ref("Relevé") else null
        val d = try { Details.build(details) } catch (e: IllegalArgumentException) { c.notify(e.message ?: "Détail invalide"); return@Button }
        if (!useMap || manual != null) { if (c.survey(family!!, note, manual, d)) { note = ""; LastDetails.byFamily[family!!] = details.toMap(); if (AutoPhoto.enabled(ctx)) photoFor = c.lastSurveyId } }
    }, modifier = Modifier.testTag("session_releve_enregistrer_le_releve")) { Text("Enregistrer le relevé") }
}

@Composable
private fun SelectedSurveyPanel(c: SessionController, r: SurveyPositions.Resolved, picked: LatLon?, moving: Boolean, moveTarget: Enu?, onMove: (Boolean) -> Unit, onReselect: (String) -> Unit, onDone: () -> Unit) {
    var note by remember(r.event.id) { mutableStateOf(r.event.note ?: "") }
    var changeFamily by remember(r.event.id) { mutableStateOf(false) }
    var details by remember(r.event.id) { mutableStateOf(false) }
    val site = r.site
    Column(Modifier.fillMaxWidth().background(Color(0x22FF8C00)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Relevé sélectionné : ${r.event.kind}" + (if (r.event.attachments.isNotEmpty()) " · ${r.event.attachments.size} pièce(s) jointe(s)" else ""), style = MaterialTheme.typography.labelLarge)
        (if (moving) moveTarget ?: site else site)?.let { Text("E %.2f, N %.2f m".format(it.e, it.n), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session_objet_coordonnees")) }
        if (moving) {
            Text("Glissez le symbole avec un doigt (deux doigts : déplacer la carte), puis validez.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = moveTarget != null, onClick = { moveTarget?.let { c.editSurvey(r.event.id, moveTo = it) }?.let(onReselect); onMove(false) }, modifier = Modifier.testTag("session_objet_valider_le_deplacement")) { Text("Valider le déplacement") }
                OutlinedButton({ onMove(false) }, modifier = Modifier.testTag("session_objet_annuler_le_deplacement")) { Text("Annuler") }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (site != null) Button({ onMove(true) }, modifier = Modifier.testTag("session_objet_deplacer")) { Text("Déplacer") }
            OutlinedButton({ details = !details }, modifier = Modifier.testTag("session_objet_details")) { Text(if (details) "Détails ▾" else "Détails ▸") }
            OutlinedButton({ onDone() }, modifier = Modifier.testTag("session_objet_fermer")) { Text("Fermer") }
        }
        if (!details) return@Column
        AttachmentButtons(c, r.event.id)
        if (site != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(0.1, 0.5).forEach { d ->
                OutlinedButton({ c.editSurvey(r.event.id, moveTo = site.copy(n = site.n + d))?.let(onReselect) }, modifier = Modifier.testTag("session_objet_n")) { Text("N +$d") }
                OutlinedButton({ c.editSurvey(r.event.id, moveTo = site.copy(n = site.n - d))?.let(onReselect) }, modifier = Modifier.testTag("session_objet_n_2")) { Text("N −$d") }
                OutlinedButton({ c.editSurvey(r.event.id, moveTo = site.copy(e = site.e + d))?.let(onReselect) }, modifier = Modifier.testTag("session_objet_e")) { Text("E +$d") }
                OutlinedButton({ c.editSurvey(r.event.id, moveTo = site.copy(e = site.e - d))?.let(onReselect) }, modifier = Modifier.testTag("session_objet_e_2")) { Text("E −$d") }
            }
        }
        OutlinedButton(enabled = picked != null, onClick = { picked?.let { c.editSurvey(r.event.id, moveTo = c.ltp.toEnu(it).let { e -> Enu(e.e, e.n, site?.u ?: 0.0) }); onDone() } }, modifier = Modifier.testTag("session_objet_deplacer_au_point_touche_sur")) {
            Text("Déplacer au point touché sur la carte")
        }
        OutlinedTextField(note, { note = it }, label = { Text("Commentaire") }, modifier = Modifier.testTag("session_objet_commentaire"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ c.editSurvey(r.event.id, note = note); onDone() }, modifier = Modifier.testTag("session_objet_enregistrer")) { Text("Enregistrer") }
            OutlinedButton({ changeFamily = !changeFamily }, modifier = Modifier.testTag("session_objet_changer_de_famille")) { Text("Changer de famille") }
            OutlinedButton({ c.editSurvey(r.event.id, delete = true); onDone() }, modifier = Modifier.testTag("session_objet_supprimer")) { Text("Supprimer", color = Color.Red) }
        }
        if (changeFamily) FamilyGrid(c, r.event.kind) { c.editSurvey(r.event.id, familyId = it); onDone() }
    }
}

@Composable
private fun SyncPanel(activity: Activity, c: SessionController) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            result = withContext(Dispatchers.IO) {
                try {
                    val tmp = File(activity.cacheDir, "import.zip")
                    activity.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    val other = SessionImporter.load(tmp)
                    val est = SyncEstimator.estimate(
                        DeviceSyncLog(c.deviceId(), c.timeBaseId(), c.syncEvents()),
                        DeviceSyncLog(other.manifest.session.device.id, other.manifest.session.timeBases.first().id, other.syncEvents),
                    )
                    "Export relu : ${other.manifest.session.device.model}, ${other.poses.size} poses, problèmes : ${other.problems.ifEmpty { listOf("aucun") }}\n" +
                        (if (app.lumisite.core.sync.SyncReciprocity.mutual(c.syncEvents(), other.syncEvents)) "Calé avec cet appareil : chacun a entendu le chirp de l'autre.\n"
                         else "Non calé avec cet appareil : l'échange n'est pas réciproque (un des deux n'a pas entendu l'autre).\n") +
                        (est?.let { "Écart d'horloge monotone (cet appareil − autre) : moyenne %.1f ms, dispersion %.1f ms sur %d événement(s)%s.\n%s".format(
                            it.meanOffsetNs / 1e6, it.spreadNs / 1e6, it.sharedCodes, it.driftPpm?.let { d -> ", dérive %.1f ppm".format(d) } ?: "", it.caveats.joinToString("\n")) }
                            ?: "Aucun code de synchro commun.")
                } catch (e: Exception) { "Import impossible : ${e.message}" }
            }
        }
    }
    var label by remember { mutableStateOf("S" + java.text.SimpleDateFormat("HHmm", java.util.Locale.ROOT).format(java.util.Date())) }
    var secours by remember { mutableStateOf(false) }
    var cale by remember { mutableStateOf<Int?>(null) }
    Text("Synchronisation : touchez « Synchroniser » sur tous les appareils à quelques secondes d'intervalle. Chacun écoute ${AudioSync.WINDOW_MS / 1000} s et émet son chirp (2–6 kHz, 60 ms) suivi de son identifiant.", style = MaterialTheme.typography.labelLarge)
    Text("L'enregistrement audio (WAV) est toujours gardé dans le dossier de la session.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(label, { label = it.uppercase().take(12) }, label = { Text("Code de synchro (généré, facultatif)") }, singleLine = true, modifier = Modifier.testTag("session_synchro_code_de_synchro_genere_facultati"))
    Button(enabled = !busy, onClick = {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@Button }
        busy = true; cale = null; result = "Écoute et émission en cours…"
        scope.launch {
            result = withContext(Dispatchers.IO) {
                try {
                    val prefs = activity.getSharedPreferences("lumisite_sync", android.content.Context.MODE_PRIVATE)
                    val counter = (prefs.getInt("counter", 0) + 1) and 0xFF
                    prefs.edit().putInt("counter", counter).apply()
                    val wav = File(c.sessionDir(), "sync_${label}_${System.currentTimeMillis()}.wav")
                    val r = AudioSync.round(AudioSync.WINDOW_MS, kotlin.random.Random.nextInt(AudioSync.EMIT_MIN_MS, AudioSync.EMIT_MAX_MS),
                        c.ownSyncId(), counter, wav, AudioSync.unprocessedSupported(activity))
                    val n = c.syncRound(label, r, counter); cale = n
                    val phases = r.phasesMs.entries.joinToString(" · ") { "${it.key} ${it.value} ms" }
                    app.lumisite.android.data.DiagLog.i("SYNCHRO", "Durées : $phases")
                    "${r.heard.size} chirp(s) entendu(s), ${r.heard.count { it.frameOk }} identifié(s)." + if (n < 2) " Aucun autre appareil entendu : rapprochez les appareils, vérifiez le micro, ou utilisez le top manuel (Secours)." else " Calage confirmé seulement si l'autre appareil m'a aussi entendu : vérifiez en important son export." + "\nDurées : $phases"
                } catch (e: Exception) { "Échec audio : ${e.message}. Utilisez le top manuel (Secours)." }
            }
            busy = false
            if ((cale ?: 0) < 2) secours = true
        }
    }, modifier = Modifier.testTag("session_synchro_synchroniser")) { Text("Synchroniser") }
    // Locally only "heard" is known; "Calé" needs the other journal (SyncReciprocity, shown after import).
    cale?.let { n -> Text(if (n >= 2) "Entendu (${n - 1} autre(s)), à confirmer" else "Non calé", color = if (n >= 2) Color(0xFFE65100) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
    TextButton({ secours = !secours }, modifier = Modifier.testTag("session_synchro_code_du_top_manuel_meme")) { Text(if (secours) "Masquer le secours" else "Secours…") }
    if (secours) {
        OutlinedTextField(code, { code = it.uppercase().take(12) }, label = { Text("Code du top manuel (même code partout)") }, singleLine = true, modifier = Modifier.testTag("session_synchro_code_du_top_manuel_meme_2"))
        OutlinedButton(enabled = code.isNotBlank(), onClick = { c.syncManual(code) }, modifier = Modifier.testTag("session_synchro_top_manuel_appuyer_au_signal")) { Text("TOP manuel (appuyer au signal)") }
    }
    OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.testTag("session_synchro_estimer_l_ecart_avec_l")) { Text("Estimer l'écart avec l'export d'un autre appareil…") }
    val st by c.state.collectAsState()
    Text("Événements de synchro : " + st.events.filterIsInstance<SyncEvent>().joinToString { "${it.syncCode}/${it.method}" }.ifEmpty { "aucun" }, style = MaterialTheme.typography.bodySmall)
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExportPanel(activity: Activity, repo: Repository, c: SessionController) {
    val scope = rememberCoroutineScope()
    val resultView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    // D14: the export runs in WorkManager (ExportWorker); this panel only shows its state, polled from WorkManager.
    var pressed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<androidx.work.WorkInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(c.sessionId) {
        while (true) { info = withContext(Dispatchers.IO) { ExportWorker.latest(activity, c.sessionId) }; kotlinx.coroutines.delay(300) }
    }
    val running = info?.state?.isFinished == false
    val done = info?.state == androidx.work.WorkInfo.State.SUCCEEDED
    val failed = info?.state == androidx.work.WorkInfo.State.FAILED

    fun localUri(zip: File) = FileProvider.getUriForFile(activity, activity.packageName + ".files", zip)
    fun share(zip: File) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, localUri(zip)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager l'export"))
    }
    fun open(zip: File, publicUri: android.net.Uri?) {
        val uri = publicUri?.takeIf { it.scheme == "content" } ?: localUri(zip)
        try { activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (e: android.content.ActivityNotFoundException) { err = "Aucune appli ne sait ouvrir un ZIP sur ce téléphone : utilisez « Partager »."; DiagLog.e("EXPORT", "Ouvrir : aucune appli pour application/zip", e) }
    }

    Text("Export en arrière-plan (reprenable s'il est interrompu) : releves.geojson, session.json, raw/, derived/, media/, LISEZMOI.txt, vérifié (tailles et SHA-256), puis copié dans Documents/LumiSite/<site>/<session>/exports/.", style = MaterialTheme.typography.labelLarge)
    Button(modifier = Modifier.testTag("session_export_exporter_la_session"), enabled = !running && !pressed, onClick = {
        pressed = true; err = null
        DiagLog.i("EXPORT", "Début de l'export de la session ${c.sessionId}"); DiagLog.memory("avant export")
        scope.launch {
            try {
                // Closes the open segments so the snapshot contains everything recorded until now.
                withContext(Dispatchers.IO) { c.mirrorNow() }
                ExportWorker.enqueue(activity, c.site.id, c.sessionId)
            } catch (e: Throwable) {
                err = "Échec du lancement de l'export : ${e.message ?: e}. L'erreur est écrite dans Réglages > Journal."
                DiagLog.e("EXPORT", "Lancement de l'export de ${c.sessionId}", e)
            }
            // Keeps "Préparation…" until WorkManager reports the work (no gap between the press and the bar).
            while (ExportWorker.latest(activity, c.sessionId)?.state?.isFinished != false && ExportWorker.live.containsKey(c.sessionId)) kotlinx.coroutines.delay(100)
            pressed = false
        }
    }) { Text(if (running || pressed) "Export…" else "Exporter la session") }
    // The bar stays after the end (« Terminé 100 % »): a short export is never invisible.
    if (pressed || running || done) {
        val pr = info?.progress
        val pct = if (done && !pressed) 100 else pr?.getInt(ExportWorker.KEY_PCT, 0) ?: 0
        val step = if (done && !pressed) ResumableExport.Step.TERMINE.label else pr?.getString(ExportWorker.KEY_STEP) ?: ResumableExport.Step.PREPARATION.label
        val detail = pr?.getString(ExportWorker.KEY_DETAIL)
        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().testTag("session_export_progression"))
        Text("$step… $pct %" + (detail?.takeIf { it != step && !it.startsWith(step) }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("session_export_etape"))
        if (!done || pressed) Text("Vous pouvez terminer la session : l'export continue en arrière-plan (le système peut le différer ; il reprend où il s'est arrêté).", style = MaterialTheme.typography.bodySmall)
    }
    err?.let { Text(it, color = Color.Red, modifier = Modifier.testTag("session_export_erreur")) }
    if (failed && !pressed) Text("Échec de l'export : ${info?.outputData?.getString(ExportWorker.KEY_ERROR) ?: "voir Réglages > Journal"}. Un nouvel appui reprend où il s'est arrêté.",
        color = Color.Red, modifier = Modifier.testTag("session_export_erreur"))
    if (done && !pressed) info?.outputData?.let { o ->
        val zip = File(o.getString(ExportWorker.KEY_ZIP) ?: return@let)
        val publicPath = o.getString(ExportWorker.KEY_PUBLIC)
        val problems = o.getString(ExportWorker.KEY_PROBLEMS).orEmpty()
        LaunchedEffect(zip.path) { resultView.bringIntoView() }
        Text("Exporté dans ${publicPath ?: zip.path}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("session_export_resultat"))
        if (publicPath == null) Text("Copie dans Documents/LumiSite impossible : le ZIP reste dans l'appli, utilisez « Partager ». Détail dans Réglages > Journal.", color = Color.Red)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.bringIntoViewRequester(resultView)) {
            Button(onClick = { open(zip, o.getString(ExportWorker.KEY_URI)?.let(android.net.Uri::parse)) }, modifier = Modifier.testTag("session_export_ouvrir")) { Text("Ouvrir") }
            OutlinedButton(onClick = { share(zip) }, modifier = Modifier.testTag("session_export_partager")) { Text("Partager") }
        }
        Text("Fichier : ${zip.name} (${zip.length() / 1024} Ko), ${o.getInt(ExportWorker.KEY_FILES, 0)} fichiers\nVérification : ${problems.ifEmpty { "OK, tailles et empreintes SHA-256 vérifiées" }}",
            style = MaterialTheme.typography.bodySmall)
        o.getString(ExportWorker.KEY_MEDIA)?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session_export_medias")) }
    }
}
