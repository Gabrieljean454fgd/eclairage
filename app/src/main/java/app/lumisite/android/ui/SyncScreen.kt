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

/** Accueil > Synchro (paquet 2): role choice, then the 4 separate states (connexion, alignement, capture, repère). */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by NearbyMesh.state.collectAsState()
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
    fun begin(role: SyncRole2) {
        wanted = role
        if (perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) NearbyMesh.start(ctx, role) else launcher.launch(perms)
    }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Synchro", style = MaterialTheme.typography.headlineSmall)
        Text("Réseau local (Nearby, sans cloud). Le maître fait le relevé ; les esclaves envoient horodatages et médias.", style = MaterialTheme.typography.bodySmall)
        if (!st.running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ begin(SyncRole2.SAISIE_MAITRE) }, modifier = Modifier.testTag("synchro_mode_maitre")) { Text("Saisie (maître)") }
                OutlinedButton({ begin(SyncRole2.CAPTATION_ESCLAVE) }, modifier = Modifier.testTag("synchro_mode_esclave")) { Text("Captation-synchro (esclave)") }
            }
        } else {
            Text("Mode : " + if (st.role == SyncRole2.SAISIE_MAITRE) "Saisie (maître)" else "Captation-synchro (esclave)",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("synchro_mode_actif"))
            Text(st.radio, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_radio"))
            if (st.role == SyncRole2.SAISIE_MAITRE) MasterPanel(st) else SlavePanel(st)
            OutlinedButton({ NearbyMesh.stop() }, modifier = Modifier.testTag("synchro_arreter")) { Text("Arrêter la synchro") }
        }
        OutlinedButton(onBack, modifier = Modifier.testTag("synchro_retour")) { Text("Retour") }
        if (st.log.isNotEmpty()) {
            Text("Journal", style = MaterialTheme.typography.titleSmall)
            st.log.takeLast(10).reversed().forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ NearbyMesh.startCapture() }, enabled = !st.capturing, modifier = Modifier.testTag("synchro_demarrer_captures")) { Text("Démarrer les captures") }
        OutlinedButton({ NearbyMesh.stopCapture() }, enabled = st.capturing, modifier = Modifier.testTag("synchro_arreter_captures")) { Text("Arrêter") }
    }
    st.peers.forEachIndexed { i, p ->
        Card(Modifier.fillMaxWidth().testTag("synchro_esclave_${i + 1}")) {
            Column(Modifier.padding(8.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall)
                Text("Connexion : ${p.link.label}", color = if (p.link == Link.LOST) MaterialTheme.colorScheme.error else Color.Unspecified)
                Text("Alignement temporel : ${p.clock.label}" + (if (p.clock == Clock.ALIGNED) " (décalage %.1f ms ± %.1f ms)".format((p.offsetNs ?: 0) / 1e6, (p.uncertaintyNs ?: 0) / 1e6) else ""))
                Text("Capture : ${if (p.link == Link.LOST) "inconnue (perdu)" else p.capture.label}" + if (p.media > 0) " · ${p.media} média(s)" else "")
                Text("Repère spatial : ${p.frame.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SlavePanel(st: NearbyMesh.UiState) {
    Text("Connexion : " + (st.slaveMaster ?: "aucun maître"), modifier = Modifier.testTag("synchro_esclave_connexion"))
    Text("Alignement temporel : calculé par le maître (pings reçus)" + if (st.slaveLastPingNs > 0) "" else " · aucun ping reçu")
    Text("Capture : ${st.slaveCapture.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("synchro_esclave_capture"))
    Text("Repère spatial : non partagé (horodatage seul)", style = MaterialTheme.typography.bodySmall)
}
