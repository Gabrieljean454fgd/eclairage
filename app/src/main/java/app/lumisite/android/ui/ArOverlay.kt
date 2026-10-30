package app.lumisite.android.ui

import androidx.compose.foundation.background
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
import app.lumisite.core.compute.V3
import app.lumisite.core.model.TrackingState

private fun aimState(tracking: TrackingState, aim: AimResult?): Pair<Color, String> = when {
    tracking == TrackingState.INITIALIZING -> Color(0xFFFFC107) to "Initialisation : bougez lentement"
    tracking != TrackingState.TRACKING -> Color(0xFFD50000) to "Suivi perdu"
    aim == AimResult.Grazing -> Color(0xFFD50000) to "Orientez l'appareil vers le sol"
    // Each source has its own colour; only a detected plane is shown as a real target (green).
    aim is AimResult.Hit && aim.source == "ESTIMATED" -> Color(0xFFFF9800) to "Pas de cible : point estimé à 50 cm"
    aim is AimResult.Hit && aim.source == "AR_PLANE" -> Color(0xFF00C853) to "Plan détecté"
    aim is AimResult.Hit && aim.source == "AR_LAST_PLANE" -> Color(0xFF00B0FF) to "Pas de plan : hauteur du dernier objet"
    aim is AimResult.Hit -> Color(0xFFFFEB3B) to "Pas de plan : sol estimé 1,4 m sous l'appareil"
    else -> Color(0xFFD50000) to "Pas de cible"
}

/** Drawn over the camera view (inside its Box): reticle coloured by tracking/aim state and calibration label. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.ArReticle(c: SessionController) {
    val st by c.state.collectAsState()
    val aim by c.ar.aim.collectAsState()
    val (color, _) = aimState(st.tracking, aim)
    Text("+", Modifier.align(Alignment.Center), fontSize = 36.sp, color = color, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 4f)))
    val calLabel = remember(st.events.size) { c.calibrationLabel() }
    Text(calLabel, Modifier.align(Alignment.TopStart).background(Color(0x88000000)).padding(2.dp), color = Color.White, fontSize = 10.sp)
}

/** Below the camera view: aim hint, plane count, torch and two-aim measurement (P2, not journaled). */
@Composable
fun ArControls(c: SessionController) {
    val st by c.state.collectAsState()
    val aim by c.ar.aim.collectAsState()
    val planes by c.ar.planeCount.collectAsState()
    val torch by c.ar.torchOn.collectAsState()
    var a by remember { mutableStateOf<V3?>(null) }
    var measure by remember { mutableStateOf<String?>(null) }
    val (color, hint) = aimState(st.tracking, aim)
    Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$hint · $planes plan(s) au sol", style = MaterialTheme.typography.labelSmall, color = color)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton({ c.ar.setTorch(!torch) }) { Text(if (torch) "Torche : oui" else "Torche : non", fontSize = 11.sp) }
            OutlinedButton({
                val p = (aim as? AimResult.Hit)?.takeIf { it.source != "ESTIMATED" }?.point
                if (p == null) measure = "Mesure : visez une cible"
                else if (a == null) { a = p; measure = "Point A posé, visez B" }
                else { measure = "A–B : %.2f m".format(a!!.distanceTo(p)); a = null }
            }) { Text(if (a == null) "Mesure A" else "Mesure B", fontSize = 11.sp) }
        }
        measure?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
