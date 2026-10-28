package app.lumisite.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.lumisite.android.SessionController
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
import app.lumisite.core.model.TrackingState
import app.lumisite.core.sync.DeviceSyncLog
import app.lumisite.core.sync.SyncEstimator
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Mode { NONE, CALIBRATE, RELOCALIZE, SURVEY, SYNC, EXPORT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(activity: Activity, repo: Repository, c: SessionController, onClose: () -> Unit) {
    val st by c.state.collectAsState()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.NONE) }
    var picked by remember { mutableStateOf<LatLon?>(null) }
    var arReady by remember { mutableStateOf(false) }
    var arAvail by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var perms by remember { mutableStateOf(false) }
    var showUncorrected by remember { mutableStateOf(true) }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

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
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    if (arReady) c.ar.resume() else if (arAvail != null) c.markArUnavailable("AR_NOT_RUNNING")
                    glView?.onResume(); if (perms) c.startCapture(granted(Manifest.permission.ACCESS_FINE_LOCATION))
                }
                Lifecycle.Event.ON_PAUSE -> { glView?.onPause(); c.pauseCapture() }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun refPoint(label: String): ReferencePoint? {
        val p = picked ?: run { c.notify("Touchez d'abord la carte pour choisir le point."); return null }
        return ReferencePoint(label, c.ltp.toEnu(p).let { Enu(it.e, it.n, 0.0) }, p, "MAP_TAP", uncertaintyM = null)
    }

    val events = st.events
    val relocLines = events.filterIsInstance<RelocalizationEvent>().map {
        MapLine(c.ltp.toLatLon(it.estimatedBefore), c.ltp.toLatLon(it.correctedAfter), android.graphics.Color.RED, "Recalage %.2f m".format(it.deltaM))
    }
    val surveyPts = events.filterIsInstance<SurveyEvent>().mapNotNull { s ->
        (s.manualSite ?: s.estimatedSite)?.let { MapPoint(c.ltp.toLatLon(it.copy(u = 0.0)), "${s.kind} ${s.note ?: ""}", android.graphics.Color.MAGENTA) }
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.58f).fillMaxSize()) {
            OsmMap(
                Modifier.fillMaxSize(), c.site.origin, 19.0,
                MapContent(
                    origin = c.site.origin, features = c.site.osm?.features.orEmpty(), selectedFeatureRef = c.site.selectedBuildingOsmRef,
                    trajectory = st.trajectory, uncorrected = if (showUncorrected) st.uncorrected else emptyList(),
                    lines = relocLines, points = surveyPts, current = st.currentSite?.let { c.ltp.toLatLon(it.copy(u = 0.0)) }, picked = picked,
                ),
                onTap = { picked = it },
            )
            Text(
                "Trajectoire estimée (vert) : non garantie. Gris pointillé : sans recalage. Rouge : corrections.",
                Modifier.align(Alignment.TopStart).background(Color(0xCCFFFFFF)).padding(6.dp), style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(Modifier.weight(0.42f).padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TrackingBanner(st.tracking, st.trackingReason)
            Text(st.arStatus, style = MaterialTheme.typography.bodySmall)
            if (arAvail == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED || arAvail == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD) {
                Button(onClick = { ArTracker.ensureInstalled(activity, true); perms = false; perms = true }) { Text("Installer / mettre à jour ARCore") }
            }
            if (arReady) {
                Text("● Caméra active pour le suivi AR. Aucune image ni vidéo n'est enregistrée.", color = Color(0xFFB00020), style = MaterialTheme.typography.labelMedium)
                AndroidView(factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        preserveEGLContextOnPause = true; setEGLContextClientVersion(2)
                        setRenderer(c.ar); renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        glView = this
                    }
                }, modifier = Modifier.fillMaxWidth().height(180.dp))
            }
            val gps = st.lastGps
            Text(
                "Poses : ${st.poseCount} · IMU : ${st.imuCount} · GPS : " +
                    (gps?.let { "%.6f, %.6f ±%s m (%s)".format(it.lat, it.lon, it.horizontalAccuracyM?.let { a -> "%.0f".format(a) } ?: "?", it.provider) } ?: "aucun fix") +
                    " · statut ${st.gpsStatus}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                st.currentSite?.let { "Position estimée (repère site) E %.2f m, N %.2f m".format(it.e, it.n) } ?: "Position site : non calibrée",
                style = MaterialTheme.typography.bodySmall,
            )
            st.distanceSinceFixM?.let { Text("Distance parcourue depuis la dernière calibration/recalage : %.1f m (la dérive augmente avec la distance)".format(it), style = MaterialTheme.typography.bodySmall) }
            st.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mode.entries.filter { it != Mode.NONE }.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = if (mode == m) Mode.NONE else m; c.clearMessage() }, label = {
                        Text(when (m) { Mode.CALIBRATE -> "Calibrer"; Mode.RELOCALIZE -> "Recaler"; Mode.SURVEY -> "Relevé"; Mode.SYNC -> "Synchro"; Mode.EXPORT -> "Export"; else -> "" })
                    })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showUncorrected, { showUncorrected = it }); Text("Afficher la trajectoire sans recalage", style = MaterialTheme.typography.bodySmall)
            }
            val pickedTxt = picked?.let { val e = c.ltp.toEnu(it); "Point choisi : E %.2f, N %.2f m".format(e.e, e.n) } ?: "Touchez la carte pour choisir le point de référence."
            when (mode) {
                Mode.CALIBRATE -> CalibratePanel(c, pickedTxt, st.pendingCalibrationA != null, ::refPoint)
                Mode.RELOCALIZE -> RelocPanel(c, pickedTxt, ::refPoint)
                Mode.SURVEY -> SurveyPanel(c, st.tracking, picked != null, ::refPoint)
                Mode.SYNC -> SyncPanel(activity, c)
                Mode.EXPORT -> ExportPanel(activity, repo, c)
                Mode.NONE -> {}
            }
            HorizontalDivider()
            OutlinedButton(onClick = onClose) { Text("Terminer la session") }
            Text("Journal : ${events.size} événements (calibrations ${events.count { it is app.lumisite.core.model.CalibrationEvent }}, recalages ${events.count { it is RelocalizationEvent }}, relevés ${events.count { it is SurveyEvent }})", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TrackingBanner(s: TrackingState, reason: String?) {
    val (bg, label) = when (s) {
        TrackingState.TRACKING -> Color(0xFF1B8A4B) to "Suivi : initialisé (TRACKING)"
        TrackingState.INITIALIZING -> Color(0xFF8A6D1B) to "Suivi : initialisation… bougez lentement l'appareil"
        TrackingState.DEGRADED -> Color(0xFFD08000) to "Suivi : dégradé (pause temporaire)"
        TrackingState.LOST -> Color(0xFFB00020) to "Suivi : perdu"
        TrackingState.STOPPED -> Color(0xFF555555) to "Suivi : arrêté"
        TrackingState.UNAVAILABLE -> Color(0xFF555555) to "Suivi AR indisponible : positions manuelles uniquement"
    }
    Text(label + (reason?.let { " ($it)" } ?: ""), Modifier.fillMaxWidth().background(bg).padding(8.dp), color = Color.White)
}

@Composable
private fun CalibratePanel(c: SessionController, pickedTxt: String, hasA: Boolean, ref: (String) -> ReferencePoint?) {
    var method by remember { mutableStateOf(0) }
    var heading by remember { mutableStateOf("") }
    var known by remember { mutableStateOf("") }
    var fixScale by remember { mutableStateOf(true) }
    Text("Calibration initiale : aligne le repère AR sur le repère du site.", style = MaterialTheme.typography.labelLarge)
    Row { FilterChip(method == 0, { method = 0 }, { Text("1 point + cap") }); Spacer(Modifier.width(6.dp)); FilterChip(method == 1, { method = 1 }, { Text("2 points") }) }
    Text(pickedTxt, style = MaterialTheme.typography.bodySmall)
    if (method == 0) {
        val compass = c.sensors.cameraHeadingDeg
        Text("Placez-vous sur le point, visez avec la caméra une direction connue et saisissez son cap (° depuis le nord), ou utilisez la boussole (non fiable en intérieur).", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(heading, { heading = it }, label = { Text("Cap de visée (°)") })
        Row {
            OutlinedButton(enabled = compass != null, onClick = { heading = "%.1f".format(java.util.Locale.ROOT, compass ?: 0.0) }) { Text(compass?.let { "Boussole %.0f° (préc. ${c.sensors.headingAccuracy})".format(it) } ?: "Boussole indisponible") }
        }
        Button(onClick = {
            val r = ref("Calibration P1"); val h = heading.replace(',', '.').toDoubleOrNull()
            if (r != null && h == null) c.notify("Saisissez un cap de visée valide (degrés depuis le nord).")
            if (r != null && h != null) c.calibrateOnePoint(r, h, if (compass != null && "%.1f".format(java.util.Locale.ROOT, compass) == heading) "COMPASS_ROTATION_VECTOR" else "MANUAL", if (compass != null) 15.0 else null)
        }) { Text("Valider la calibration") }
    } else {
        Text("Placez-vous sur A, touchez A sur la carte, capturez. Marchez jusqu'à B (≥ 0,5 m, idéalement > 5 m), touchez B, validez.", style = MaterialTheme.typography.bodySmall)
        if (!hasA) Button(onClick = { ref("Calibration A")?.let(c::captureCalibrationA) }) { Text("Capturer A") }
        else {
            OutlinedTextField(known, { known = it }, label = { Text("Distance A-B mesurée (m, optionnel)") })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fixScale, { fixScale = it }); Text("Échelle fixée à 1 (AR métrique)", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { ref("Calibration B")?.let { c.calibrateTwoPoints(it, fixScale, known.replace(',', '.').toDoubleOrNull()) } }) { Text("Capturer B et valider") }
        }
    }
}

@Composable
private fun RelocPanel(c: SessionController, pickedTxt: String, ref: (String) -> ReferencePoint?) {
    var headingCorr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    Text("Recalage : placez-vous sur un point connu, touchez-le sur la carte et confirmez. La trajectoire antérieure est conservée.", style = MaterialTheme.typography.labelLarge)
    Text(pickedTxt, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(headingCorr, { headingCorr = it }, label = { Text("Correction de cap (°, optionnel, + = horaire)") })
    OutlinedTextField(note, { note = it }, label = { Text("Note") })
    Button(onClick = { ref("Recalage")?.let { c.relocalize(it, headingCorr.replace(',', '.').toDoubleOrNull(), note.ifBlank { null }) } }) { Text("Je suis ici : recaler") }
}

@Composable
private fun SurveyPanel(c: SessionController, tracking: TrackingState, hasPick: Boolean, ref: (String) -> ReferencePoint?) {
    var kind by remember { mutableStateOf("LUMINAIRE") }
    var note by remember { mutableStateOf("") }
    var useMap by remember { mutableStateOf(tracking != TrackingState.TRACKING) }
    Text("Relevé : associé à la position estimée courante et à l'heure.", style = MaterialTheme.typography.labelLarge)
    Row { listOf("LUMINAIRE", "MESURE", "OBSERVATION", "AUTRE").forEach { k -> FilterChip(kind == k, { kind = k }, { Text(k) }); Spacer(Modifier.width(4.dp)) } }
    OutlinedTextField(note, { note = it }, label = { Text("Note (facultative)") })
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useMap, { useMap = it }); Text("Position manuelle (point touché sur la carte)", style = MaterialTheme.typography.bodySmall) }
    Button(enabled = !useMap || hasPick, onClick = { c.survey(kind, note, if (useMap) ref("Relevé") else null); note = "" }) { Text("Enregistrer le relevé") }
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
                        (est?.let { "Écart d'horloge monotone (cet appareil − autre) : moyenne %.1f ms, dispersion %.1f ms sur %d événement(s)%s.\n%s".format(
                            it.meanOffsetNs / 1e6, it.spreadNs / 1e6, it.sharedCodes, it.driftPpm?.let { d -> ", dérive %.1f ppm".format(d) } ?: "", it.caveats.joinToString("\n")) }
                            ?: "Aucun code de synchro commun.")
                } catch (e: Exception) { "Import impossible : ${e.message}" }
            }
        }
    }
    Text("Synchronisation multi-appareils : saisissez le même code sur tous les appareils pour le même événement physique.", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(code, { code = it.uppercase().take(12) }, label = { Text("Code de synchro (ex. S1)") })
    Button(enabled = code.isNotBlank(), onClick = { c.syncManual(code) }) { Text("TOP manuel (appuyer au signal)") }
    Text("Expérimental : chirp audio 2–6 kHz, 60 ms. Tous les appareils écoutent 4 s ; l'émetteur joue le chirp après 1,5 s. Délai de propagation (~2,9 ms/m) et latence audio non compensés. Un enregistrement WAV du micro est conservé dans la session.", style = MaterialTheme.typography.bodySmall)
    Row {
        listOf(SyncRole.EMITTER to "Émettre + écouter", SyncRole.RECEIVER to "Écouter").forEach { (role, label) ->
            OutlinedButton(enabled = code.isNotBlank() && !busy, onClick = {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@OutlinedButton }
                busy = true; result = "Écoute en cours…"
                scope.launch {
                    result = withContext(Dispatchers.IO) {
                        try {
                            val wav = File(c.sessionDir(), "sync_${code}_${System.currentTimeMillis()}.wav")
                            val r = AudioSync.listen(4000, if (role == SyncRole.EMITTER) 1500 else null, wav)
                            c.syncAudio(code, role, r.eventMonotonicNs, r.detection, r.trusted, r.message); r.message
                        } catch (e: Exception) { "Échec audio : ${e.message}" }
                    }
                    busy = false
                }
            }) { Text(label) }
            Spacer(Modifier.width(6.dp))
        }
    }
    OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Estimer l'écart avec l'export d'un autre appareil…") }
    val st by c.state.collectAsState()
    Text("Événements de synchro : " + st.events.filterIsInstance<SyncEvent>().joinToString { "${it.syncCode}/${it.method}" }.ifEmpty { "aucun" }, style = MaterialTheme.typography.bodySmall)
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ExportPanel(activity: Activity, repo: Repository, c: SessionController) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf<Repository.ExportResult?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    Text("Export JSON versionné (session.json + NDJSON raw/ et derived/), puis relecture automatique.", style = MaterialTheme.typography.labelLarge)
    Button(enabled = !busy, onClick = {
        busy = true; err = null
        scope.launch {
            try { c.flush(); out = withContext(Dispatchers.IO) { repo.export(c.site, c.sessionId) } } catch (e: Exception) { err = e.toString() }
            busy = false
        }
    }) { Text(if (busy) "Export…" else "Exporter la session") }
    err?.let { Text(it, color = Color.Red) }
    out?.let { r ->
        Text("Fichier : ${r.zip.name} (${r.zip.length() / 1024} Ko)\n" + r.manifest.files.joinToString("\n") { "${it.category} · ${it.path} · ${it.records} enr." } +
            "\nRelecture : ${r.verification.problems.ifEmpty { listOf("OK, hashes et nombres d'enregistrements vérifiés") }}" +
            r.verification.warnings.joinToString("") { "\nAvertissement : $it" }, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", r.zip)
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager l'export"))
        }) { Text("Partager…") }
    }
}
