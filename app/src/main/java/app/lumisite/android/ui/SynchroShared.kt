package app.lumisite.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lumisite.android.data.AndroidClock
import app.lumisite.android.sync.NearbyMesh
import app.lumisite.core.sync.Clock
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.Peer
import app.lumisite.core.sync.SyncDisplay
import app.lumisite.core.sync.SyncRole2
import kotlinx.coroutines.delay

/**
 * Build 199 (points 1b-1e) : le bandeau de connexion, les cartes esclaves et le journal sont partagés entre
 * Accueil > Sync et Session > Synchro — les deux lisent le même NearbyMesh.state.
 */

/** Horloge monotone qui retarde d'une seconde : « il y a N s » / « dernier ping il y a N s » avancent seuls. */
@Composable
fun rememberMeshNowNs(): Long {
    val now by produceState(AndroidClock.monotonicNs()) { while (true) { delay(1000); value = AndroidClock.monotonicNs() } }
    return now
}

/** Permissions demandées pour la synchro Nearby (Bluetooth, appareil à proximité, localisation ancienne, micro). */
fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
    if (Build.VERSION.SDK_INT >= 33) addAll(listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS))
    if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.RECORD_AUDIO)
}.toTypedArray()

fun nearbyPermissionsGranted(ctx: Context): Boolean =
    nearbyPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

/** Bandeau de connexion unique (testTag synchro_bandeau_connexion) : vert connecté, orange recherche, rouge perdu. */
@Composable
fun SynchroBanner(st: NearbyMesh.UiState, nowNs: Long, modifier: Modifier = Modifier) {
    val banner = if (st.role == SyncRole2.SAISIE_MAITRE) SyncDisplay.masterBanner(st.peers)
    else SyncDisplay.slaveBanner(st.slaveMasterName,
        st.slaveLastPingNs.takeIf { it > 0 }?.let { ((nowNs - it) / 1_000_000_000).coerceAtLeast(0) })
    val color = when (banner.level) {
        SyncDisplay.Level.OK -> Color(0xFF2E7D32)
        SyncDisplay.Level.WAIT -> Color(0xFFEF6C00)
        SyncDisplay.Level.ERROR -> Color(0xFFB00020)
    }
    Text(banner.text, modifier = modifier.fillMaxWidth()
        .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
        .testTag("synchro_bandeau_connexion"),
        color = color, style = MaterialTheme.typography.titleSmall)
}

/** Carte d'un esclave (maître) : nom, connexion, alignement (incertitude), capture, dernier message (testTag synchro_esclave_N_*). */
@Composable
fun SynchroPeerCard(p: Peer, index: Int, nowNs: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            // Le testTag est sur le nom : assertVisible {id, text} de Maestro exige le même nœud.
            Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("synchro_esclave_${index + 1}"))
            Text("Connexion : ${p.link.label}", color = if (p.link == Link.LOST) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.testTag("synchro_esclave_${index + 1}_connexion"))
            Text("Alignement temporel : " + if (p.clock == Clock.ALIGNED) SyncDisplay.alignmentText(p.offsetNs, p.uncertaintyNs) else p.clock.label)
            Text("Capture : ${if (p.link == Link.LOST) "inconnue (perdu)" else p.capture.label}" + if (p.media > 0) " · ${p.media} média(s)" else "")
            val ageS = if (p.lastHeardNs > 0) ((nowNs - p.lastHeardNs) / 1_000_000_000).coerceAtLeast(0) else null
            Text("Dernier message : " + (ageS?.let { "il y a $it s" } ?: "aucun reçu") +
                " (ping toutes les ${app.lumisite.core.sync.MasterMesh.PING_PERIOD_NS / 1_000_000_000} s, perdu après ${app.lumisite.core.sync.MasterMesh.LOST_AFTER_NS / 1_000_000_000} s)",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("synchro_esclave_${index + 1}_dernier"))
            p.audio?.let { a -> Text("Top : " + a.text(p.name), color = if (a.offsetNs != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error) }
            if (p.sensors.isNotEmpty()) Text("Capteurs : " + p.sensors.filter { it.active }.joinToString { it.label }.ifEmpty { "aucun actif" }, style = MaterialTheme.typography.bodySmall)
            Text("Repère spatial : ${p.frame.label}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Journal Nearby (testTag fourni par l'appelant), 15 dernières lignes, plus récent en haut. */
@Composable
fun SynchroJournal(lines: List<String>, tag: String) {
    if (lines.isEmpty()) return
    Column(Modifier.testTag(tag)) {
        Text("Journal (horodaté, le plus récent en haut)", style = MaterialTheme.typography.titleSmall)
        lines.takeLast(15).reversed().forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
