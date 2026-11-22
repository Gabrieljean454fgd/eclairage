package app.lumisite.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lumisite.android.sync.NearbyMesh
import app.lumisite.core.sync.Clock
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.SyncRole2
import app.lumisite.core.sync.MasterMesh
import app.lumisite.core.sync.SyncDisplay
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts a Nearby role after the runtime permissions: same function for Accueil > Sync and Session > Synchro. */
@Composable
fun rememberSyncStarter(): (SyncRole2) -> Unit {
    val ctx = LocalContext.current
    var wanted by remember { mutableStateOf<SyncRole2?>(null) }
    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { wanted?.let { NearbyMesh.start(ctx, it) } }
    return { role ->
        wanted = role
        // Notification « Synchro LumiSite active » of the foreground service (Android 13+), asked with the others.
        val all = if (Build.VERSION.SDK_INT >= 33) perms + Manifest.permission.POST_NOTIFICATIONS else perms
        if (all.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) NearbyMesh.start(ctx, role) else launcher.launch(all)
    }
}

/** Monotonic « now », refreshed every second: « il y a N s » keeps counting without recomposition from the mesh. */
@Composable
fun rememberMonoNow(): Long {
    val t by produceState(app.lumisite.android.data.AndroidClock.monotonicNs()) {
        while (true) { kotlinx.coroutines.delay(1000); value = app.lumisite.android.data.AndroidClock.monotonicNs() }
    }
    return t
}

/** v176g 1b: single connection banner on top of Accueil > Sync and Session > Synchro (green / orange / red). */
@Composable
fun SyncBanner(st: NearbyMesh.UiState) {
    val now = rememberMonoNow()
    val b = if (st.role == SyncRole2.SAISIE_MAITRE) SyncDisplay.masterBanner(st.peers) else {
        val connected = st.slaveMaster?.endsWith(" (connecté)") == true
        when {
            st.slaveMaster == null -> SyncDisplay.slaveBanner(null, null)
            !connected -> SyncDisplay.Banner("Connexion à ${st.slaveMaster.removeSuffix(" (connexion…)")}…", SyncDisplay.Tone.ORANGE)
            else -> SyncDisplay.slaveBanner(st.slaveMaster.removeSuffix(" (connecté)"), st.slaveLastPingNs.takeIf { it > 0 }?.let { (now - it) / 1_000_000_000 })
        }
    }
    val bg = when (b.tone) { SyncDisplay.Tone.VERT -> Color(0xFF2E7D32); SyncDisplay.Tone.ORANGE -> Color(0xFFE65100); SyncDisplay.Tone.ROUGE -> Color(0xFFC62828) }
    androidx.compose.material3.Surface(color = bg, contentColor = Color.White, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(b.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(10.dp).testTag("synchro_bandeau_connexion"))
    }
}

/** Same journal on both screens (15 latest lines, most recent first). */
@Composable
fun SyncJournal(st: NearbyMesh.UiState, tag: String) {
    if (st.log.isEmpty()) return
    Text("Journal (horodaté, le plus récent en haut)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(tag))
    st.log.takeLast(15).reversed().forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
}

/** Accueil > Synchro (paquet 2): role choice, then the 4 separate states (connexion, alignement, capture, repère). */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val st by NearbyMesh.state.collectAsState()
    val begin = rememberSyncStarter()
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text("Ce téléphone : ${st.selfName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_nom_ce_telephone"))
            SyncBanner(st)
            if (st.role == SyncRole2.SAISIE_MAITRE) MasterPanel(st) else SlavePanel(st)
            if (!st.foreground) Text("Service de premier plan refusé : la synchro peut être coupée écran éteint (voir le journal).",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_service_refuse"))
            OutlinedButton({ NearbyMesh.stop() }, modifier = Modifier.testTag("synchro_arreter")) { Text("Arrêter la synchro") }
        }
        OutlinedButton(onBack, modifier = Modifier.testTag("synchro_retour")) { Text("Retour") }
        SyncJournal(st, "synchro_journal")
    }
}

@Composable
private fun MasterPanel(st: NearbyMesh.UiState) {
    val s = st.master
    val ok = s?.allSynced == true
    Text(s?.text ?: "0 appareil", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("synchro_resume"),
        color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
    Text(if (ok) "synchronisé" else "non synchronisé", modifier = Modifier.testTag("synchro_etat_global"),
        color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
    Prerequisites()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected = st.peers.count { it.link == Link.CONNECTED }
    var micRefused by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micRefused = !it }
    Button(enabled = !st.roundBusy && connected > 0, onClick = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@Button }
        micRefused = false
        scope.launch(Dispatchers.IO) {
            val prefs = ctx.getSharedPreferences("lumisite_sync", android.content.Context.MODE_PRIVATE)
            val counter = (prefs.getInt("counter", 0) + 1) and 0xFF
            prefs.edit().putInt("counter", counter).apply()
            runCatching { NearbyMesh.masterRound(ctx, counter, null) }
        }
    }, modifier = Modifier.fillMaxWidth().testTag("synchro_top")) { Text("Lancer un top de synchro ($connected esclave(s))") }
    Text(if (connected == 0) "Aucun esclave connecté : le top attend au moins un téléphone en « Captation-synchro (esclave) »." else (st.round ?: "Chaque téléphone écoute ${app.lumisite.android.capture.AudioSync.WINDOW_MS / 1000} s et émet son chirp ; l'alignement s'affiche par téléphone."),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_top_etat"))
    if (micRefused) Text("Micro refusé : le top de synchro ne peut pas écouter. Autorisez le micro (Paramètres > Applications > LumiSite > Autorisations).",
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_micro_refuse"))
    SceneActive(st)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ NearbyMesh.startCapture() }, enabled = !st.capturing, modifier = Modifier.testTag("synchro_demarrer_captures")) { Text("Démarrer les captures") }
        OutlinedButton({ NearbyMesh.stopCapture() }, enabled = st.capturing, modifier = Modifier.testTag("synchro_arreter_captures")) { Text("Arrêter") }
    }
    val now = rememberMonoNow()
    st.peers.forEachIndexed { i, p ->
        Card(Modifier.fillMaxWidth().testTag("synchro_esclave_${i + 1}")) {
            Column(Modifier.padding(8.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("synchro_esclave_${i + 1}_nom"))
                Text("Connexion : ${p.link.label}", color = if (p.link == Link.LOST) MaterialTheme.colorScheme.error else Color.Unspecified)
                Text("Alignement temporel : " + if (p.clock == Clock.ALIGNED) SyncDisplay.alignmentText(p.offsetNs, p.uncertaintyNs) else p.clock.label,
                    modifier = Modifier.testTag("synchro_esclave_${i + 1}_alignement"))
                Text("Capture : ${if (p.link == Link.LOST) "inconnue (perdu)" else p.capture.label}" + if (p.media > 0) " · ${p.media} média(s)" else "")
                Text("Dernier message : il y a ${((now - p.lastHeardNs) / 1_000_000_000).coerceAtLeast(0)} s (ping toutes les ${MasterMesh.PING_PERIOD_NS / 1_000_000_000} s, perdu après ${MasterMesh.LOST_AFTER_NS / 1_000_000_000} s)", style = MaterialTheme.typography.bodySmall)
                p.audio?.let { a -> Text("Top : " + a.text(p.name), color = if (a.offsetNs != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error) }
                if (p.sensors.isNotEmpty()) Text("Capteurs : " + p.sensors.filter { it.active }.joinToString { it.label }.ifEmpty { "aucun actif" }, style = MaterialTheme.typography.bodySmall)
                Text("Repère spatial : ${p.frame.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SlavePanel(st: NearbyMesh.UiState) {
    Prerequisites()
    Text("Connexion : " + (st.slaveMaster ?: "aucun maître"), modifier = Modifier.testTag("synchro_esclave_connexion"))
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
