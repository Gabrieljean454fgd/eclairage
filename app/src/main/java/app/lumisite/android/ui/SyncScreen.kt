package app.lumisite.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lumisite.android.sync.NearbyMesh
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.SyncRole2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Accueil > Synchro (paquet 2): role choice, then the 4 separate states (connexion, alignement, capture, repère). */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by NearbyMesh.state.collectAsState()
    var wanted by remember { mutableStateOf<SyncRole2?>(null) }
    val perms = remember { nearbyPermissions() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { wanted?.let { NearbyMesh.start(ctx, it) } }
    fun begin(role: SyncRole2) {
        wanted = role
        if (nearbyPermissionsGranted(ctx)) NearbyMesh.start(ctx, role) else launcher.launch(perms)
    }
    var userStoppedCapture by remember { mutableStateOf(false) }
    // Retour terrain 22/11 : la capture démarre par défaut dès que le maître tourne ; l'utilisateur peut l'arrêter.
    androidx.compose.runtime.LaunchedEffect(st.running, st.role, st.capturing) {
        if (st.running && st.role == SyncRole2.SAISIE_MAITRE && !st.capturing && !userStoppedCapture) {
            NearbyMesh.startCapture()
        }
        if (!st.running) userStoppedCapture = false
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onBack, modifier = Modifier.testTag("synchro_retour")) { Text("← Retour") }
        Text("Sync", style = MaterialTheme.typography.headlineSmall)
        Text("Réseau local (Nearby, sans cloud). Le maître fait le relevé ; les esclaves envoient horodatages et médias.", style = MaterialTheme.typography.bodySmall)
        if (!st.running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ begin(SyncRole2.SAISIE_MAITRE) }, modifier = Modifier.testTag("synchro_mode_maitre")) { Text("Saisie (maître)") }
                OutlinedButton({ begin(SyncRole2.CAPTATION_ESCLAVE) }, modifier = Modifier.testTag("synchro_mode_esclave")) { Text("Captation-synchro (esclave)") }
            }
        } else {
            Text("Mode : " + if (st.role == SyncRole2.SAISIE_MAITRE) "Saisie (maître)" else "Captation-synchro (esclave)",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("synchro_mode_actif"))
            // build 199 point 1b: the single connection banner replaces the frozen « Recherche d'esclaves » line.
            val nowNs = rememberMeshNowNs()
            SynchroBanner(st, nowNs)
            if (st.radio.contains("impossible", ignoreCase = true) || st.radio.contains("refusée", ignoreCase = true))
                Text(st.radio, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_radio"))
            Prerequisites()
            if (st.role == SyncRole2.SAISIE_MAITRE) MasterPanel(st, nowNs, onStartCapture = { userStoppedCapture = false; NearbyMesh.startCapture() }, onStopCapture = { userStoppedCapture = true; NearbyMesh.stopCapture() }) else SlavePanel(st)
            OutlinedButton({ NearbyMesh.stop() }, modifier = Modifier.testTag("synchro_arreter")) { Text("Arrêter la synchro") }
        }
        SynchroJournal(st.log, "synchro_journal")
    }
}

@Composable
private fun MasterPanel(st: NearbyMesh.UiState, nowNs: Long, onStartCapture: () -> Unit, onStopCapture: () -> Unit) {
    val s = st.master
    val ok = s?.allSynced == true
    Text(s?.text ?: "0 appareil", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("synchro_resume"),
        color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
    Text(if (ok) "synchronisé" else "non synchronisé", modifier = Modifier.testTag("synchro_etat_global"),
        color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected = st.peers.count { it.link == Link.CONNECTED }
    Button(enabled = !st.roundBusy && connected > 0, onClick = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@Button
        scope.launch(Dispatchers.IO) {
            val prefs = ctx.getSharedPreferences("lumisite_sync", android.content.Context.MODE_PRIVATE)
            val counter = (prefs.getInt("counter", 0) + 1) and 0xFF
            prefs.edit().putInt("counter", counter).apply()
            runCatching { NearbyMesh.masterRound(ctx, counter, null) }
        }
    }, modifier = Modifier.fillMaxWidth().testTag("synchro_top")) { Text("Lancer un top de synchro (tous les téléphones émettent)") }
    Text(if (connected == 0) "Aucun esclave connecté : le top attend au moins un téléphone en « Captation-synchro (esclave) »." else (st.round ?: "Chaque téléphone écoute ${app.lumisite.android.capture.AudioSync.WINDOW_MS / 1000} s et émet son chirp ; le décalage s'affiche par téléphone."),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_top_etat"))
    SceneActive(st)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st.capturing) {
            OutlinedButton(onStopCapture, modifier = Modifier.testTag("synchro_arreter_captures")) { Text("Arrêter la capture") }
        } else {
            Button(onStartCapture, modifier = Modifier.testTag("synchro_demarrer_captures")) { Text("Reprendre la capture") }
        }
    }
    st.peers.forEachIndexed { i, p -> SynchroPeerCard(p, i, nowNs) }
}

@Composable
private fun SlavePanel(st: NearbyMesh.UiState) {
    Text("Connexion : " + (st.slaveMasterName ?: "aucun maître"), modifier = Modifier.testTag("synchro_esclave_connexion"))
    Text("Alignement temporel : calculé par le maître (pings reçus)" + if (st.slaveLastPingNs > 0) "" else " · aucun ping reçu")
    Text("Capture : ${st.slaveCapture.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("synchro_esclave_capture"))
    Text("Repère spatial : non partagé (horodatage seul)", style = MaterialTheme.typography.bodySmall)
    st.round?.let { Text(it, modifier = Modifier.testTag("synchro_esclave_top")) }
    Text("Capteurs de ce téléphone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("synchro_capteurs"))
    st.sensors.forEach { c -> Text(c.text, color = if (c.active) Color(0xFF2E7D32) else Color.Unspecified, modifier = Modifier.testTag("synchro_capteur_${c.kind.lowercase()}")) }
}

/** v176d D5: « Scène active » on the master: slaves with role, sensors, capture, received media. */
@Composable
private fun SceneActive(st: NearbyMesh.UiState) {
    val live = st.peers.filter { it.link != Link.LOST }
    Text("Scène active", style = MaterialTheme.typography.titleSmall)
    if (live.isEmpty()) Text("Scène active : aucun esclave. Lancez « Captation-synchro (esclave) » sur l'autre téléphone, à quelques mètres, localisation activée.",
        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("synchro_scene_active"))
    else Column(Modifier.testTag("synchro_scene_active")) {
        live.forEach { p ->
            Text("${p.name} · esclave (captation) · capture ${p.capture.label} · ${p.media} média(s) reçu(s) · capteurs : " +
                p.sensors.filter { it.active }.joinToString { it.label }.ifEmpty { "non reçus" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** v176d: explicit messages when location, BLUETOOTH_SCAN / NEARBY_WIFI_DEVICES or the battery exemption are missing (HyperOS). */
@Composable
private fun Prerequisites() {
    val ctx = LocalContext.current
    val tick by produceState(0) { while (true) { kotlinx.coroutines.delay(3000); value++ } }
    val msgs = remember(tick) {
        val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val loc = if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else true
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        app.lumisite.core.sync.SyncPrereq.messages(loc, Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_SCAN),
            Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.NEARBY_WIFI_DEVICES), pm.isIgnoringBatteryOptimizations(ctx.packageName), Build.VERSION.SDK_INT)
    }
    if (msgs.isEmpty()) return
    Column(Modifier.testTag("synchro_prerequis")) {
        msgs.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton({ runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            modifier = Modifier.testTag("synchro_reglages_batterie")) { Text("Ouvrir les réglages de batterie") }
    }
}
