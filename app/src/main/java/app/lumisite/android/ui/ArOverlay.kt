package app.lumisite.android.ui

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumisite.android.SessionController
import app.lumisite.core.compute.AimResult
import app.lumisite.core.compute.SupportFromTilt
import app.lumisite.core.compute.V3
import app.lumisite.core.model.TrackingState

private fun aimState(tracking: TrackingState, aim: AimResult?): Pair<Color, String> = when {
    tracking == TrackingState.INITIALIZING -> Color(0xFFFFC107) to "Initialisation : bougez lentement"
    tracking != TrackingState.TRACKING -> Color(0xFFD50000) to "Suivi perdu"
    aim == AimResult.Grazing -> Color(0xFFD50000) to "Orientez l'appareil vers le sol"
    // Each source has its own colour; only a detected plane is shown as a real target (green).
    aim == AimResult.ToComplete -> Color(0xFFFF9800) to "Position à compléter : aucun plan visé"
    aim is AimResult.Hit && aim.source == "AR_PLANE" -> Color(0xFF00C853) to "Sol détecté"
    aim is AimResult.Hit && aim.source == "AR_WALL" -> Color(0xFF00C853) to "Mur détecté"
    aim is AimResult.Hit && aim.source == "AR_CEILING" -> Color(0xFF00C853) to "Plafond détecté"
    aim is AimResult.Hit && aim.source == app.lumisite.core.compute.Aim.SRC_DEPTH -> Color(0xFF00B0FF) to "Distance mesurée (profondeur, ±10 %)"
    aim is AimResult.Hit && aim.source == "AR_LAST_PLANE" -> Color(0xFF00B0FF) to "Pas de plan : hauteur du dernier objet"
    // 2f: plafond virtuel (sol + hauteur saisie) — estimée, jamais silencieuse.
    aim is AimResult.Hit && aim.source == app.lumisite.core.compute.Aim.SRC_CEILING_ESTIMATED -> Color(0xFFFFEB3B) to "Plafond estimé (hauteur sous plafond saisie)"
    aim is AimResult.Hit -> Color(0xFFFFEB3B) to "Pas de plan : sol estimé 1,4 m sous l'appareil"
    else -> Color(0xFFD50000) to "Point visé : pas de cible"
}

/** Drawn over the camera view (inside its Box): only the reticle, coloured by tracking/aim state (v176c: no text in the image). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.ArReticle(c: SessionController) {
    val st by c.state.collectAsState()
    val aim by c.ar.aim.collectAsState()
    val (color, _) = aimState(st.tracking, aim)
    Text("+", Modifier.align(Alignment.Center), fontSize = 36.sp, color = color, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 4f)))
}

/** Libellés projetés (noms courts des luminaires, longueur A–B) par-dessus la vue caméra. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.ArLabels(c: SessionController) {
    val labels by c.ar.screenLabels.collectAsState()
    labels.forEach { lab ->
        Text(
            lab.text,
            modifier = Modifier.align(Alignment.TopStart)
                .offset { androidx.compose.ui.unit.IntOffset(lab.xPx.toInt() - 20, lab.yPx.toInt() - 10) }
                .background(Color(0x99000000)).padding(horizontal = 3.dp, vertical = 1.dp),
            color = Color.White, fontSize = 12.sp,
            style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)),
        )
    }
}

/** Below the camera view: aim hint, plane counters (2 Hz), aimed support (from the tilt, automatic), torch and two-aim
 *  measurement (P2, not journaled). Always fully shown, whatever the open tab (build 199, point 5: no jumping menus). */
@Composable
fun ArControls(c: SessionController, measureReset: Int = 0, onMeasureArmed: (Boolean) -> Unit = {}) {
    val st by c.state.collectAsState()
    val aim by c.ar.aim.collectAsState()
    val counts by c.ar.planeCounts.collectAsState()
    val stats by c.ar.planeStats.collectAsState()
    val contours by c.ar.contoursOn.collectAsState()
    val arCoreEdges by c.ar.arCoreEdgesOn.collectAsState()
    val surface by c.ar.aimSurface.collectAsState()
    val torch by c.ar.torchOn.collectAsState()
    var a by remember { mutableStateOf<V3?>(null) }
    var measure by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(measureReset) {
        if (measureReset > 0) { a = null; measure = null; c.ar.clearMeasure(); onMeasureArmed(false) }
    }
    val (color, hint) = aimState(st.tracking, aim)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val calLabel = remember(st.events.size) { c.calibrationLabel() }
        Text("$hint · $calLabel", style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.testTag("session_ar_visee"))
        // build 199 (point 5): fixed-width counters (monospace) so the line does not move while planes are found.
        Text("Plans : sol ${counts.first} · murs ${counts.second} · plafond ${counts.third}",
            style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.testTag("session_ar_plans"))
        // Build 248 : ligne de diagnostic légère (pas de log par frame, rafraîchie à 500 ms).
        stats?.let {
            Text("Diag : ${it.trackingState}${it.failureReason?.let { r -> " ($r)" } ?: ""} · depth=${it.depthActive} · " +
                "plans ${it.totalPlanes}/${it.trackingPlanes} · poly ${it.validPolygons}/${it.totalPlanes} · " +
                "camY=${"%.2f".format(it.cameraYM)} solY=${it.floorYM?.let { y -> "%.2f".format(y) } ?: "-"} " +
                "renderer=${it.rendererGotPolygons}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("session_ar_diag"))
        }
        // build 199 (point 6): aimed support follows the camera tilt (sol / mur / plafond), no Tous / Sol / Mur / Plafond chips.
        val support = surface?.let { runCatching { SupportFromTilt.Support.valueOf(it) }.getOrNull() } ?: SupportFromTilt.Support.MUR
        Text(SupportFromTilt.label(support), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("session_ar_support_auto"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // « Contours » switch of the stripped AR lines (plane outlines, vertical wall edges).
            androidx.compose.material3.FilterChip(selected = contours, onClick = { c.ar.contoursOn.value = !contours },
                label = { Text(if (contours) "Contours du site" else "Contours masqués") }, modifier = Modifier.testTag("session_ar_contours"))
            androidx.compose.material3.FilterChip(selected = arCoreEdges, onClick = { c.ar.arCoreEdgesOn.value = !arCoreEdges },
                label = { Text(if (arCoreEdges) "Arêtes ARCore" else "Arêtes ARCore : off") }, modifier = Modifier.testTag("session_ar_aretes_arcore"))
            // Session > Torche (lamp icon) and Session > Mesure (ruler icon), 48 dp touch targets.
            OutlinedButton({ c.ar.setTorch(!torch) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = if (torch) "Torche : oui" else "Torche : non" }.testTag("session_ar_torche_oui")) {
                Text(if (torch) "🔦●" else "🔦", fontSize = 16.sp)
            }
            OutlinedButton({
                val p = (aim as? AimResult.Hit)?.takeIf { it.source != "ESTIMATED" && it.source != "AR_FLOOR_ESTIMATE" && it.source != app.lumisite.core.compute.Aim.SRC_CEILING_ESTIMATED }?.point
                if (p == null) { measure = "Mesure : visez une cible"; onMeasureArmed(true) }
                else if (a == null) { c.ar.clearMeasure(); a = p; measure = "Point A posé, visez B"; onMeasureArmed(true) }
                else {
                    c.ar.setMeasure(a!!, p)
                    measure = app.lumisite.core.compute.ArMeasure.label(a!!, p).second
                    a = null
                    onMeasureArmed(false)
                }
            }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = if (a == null) "Mesure A" else "Mesure B" }.testTag("session_ar_mesure_a")) { Text(if (a == null) "📏 A" else "📏 B", fontSize = 16.sp) }
        }
        measure?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
