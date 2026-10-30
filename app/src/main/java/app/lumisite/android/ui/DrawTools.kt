package app.lumisite.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.lumisite.android.SessionController
import app.lumisite.core.geo.Enu
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.ShapeStyle
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.survey.Details
import app.lumisite.core.survey.Shapes
import java.io.File
import kotlin.math.hypot

/** Annotation toolbar (fiche F). Material icons (Apache-2.0) + French word; no Xodo/PDFTron resource is used. */
enum class Tool(val icon: ImageVector, val label: String) {
    MOVE(Icons.Default.LocationOn, "Se déplacer"),
    PLACE(Icons.Default.Place, "Poser"),
    SHAPE(Icons.Default.Create, "Forme"),
    LINE(Icons.Default.ArrowForward, "Ligne"),
    POLYGON(Icons.Default.Star, "Polygone"),
    ANNOTATION(Icons.Default.Warning, "Annotation"),
    COMMENT(Icons.Default.Edit, "Commentaire"),
    SELECT(Icons.Default.Done, "Sélection"),
}

val NAMED_COLORS = linkedMapOf(
    "noir" to 0xFF000000.toInt(), "rouge" to 0xFFD32F2F.toInt(), "orange" to 0xFFF57C00.toInt(),
    "jaune" to 0xFFFBC02D.toInt(), "vert" to 0xFF388E3C.toInt(), "bleu" to 0xFF1976D2.toInt(),
)

private data class Preset(val name: String, val stroke: String, val fill: String?, val width: Float, val opacity: Float = 1f)
private val PRESETS = listOf(Preset("Défaut", "rouge", null, 3f), Preset("Câble", "orange", null, 2f), Preset("Zone", "bleu", "bleu", 2f, 0.4f))

/** Single active state: the current tool and the points of the shape being drawn. */
class DrawState {
    var tool by mutableStateOf(Tool.MOVE)
    val pts = mutableStateListOf<Enu>()
    var shapeKind by mutableStateOf("rectangle")
    var arrow by mutableStateOf(false)
    var zone by mutableStateOf(false)
    var annotationType by mutableStateOf(Details.ANNOTATIONS[0])
    var label by mutableStateOf("")
    var comment by mutableStateOf("")
    var stroke by mutableStateOf("rouge")
    var fill by mutableStateOf<String?>(null)
    var width by mutableStateOf(3f)
    var opacity by mutableStateOf(1f)
    var recent by mutableStateOf<String?>(null)
    var family by mutableStateOf<String?>(null)
    var selected by mutableStateOf<String?>(null)
    var pendingSwitch by mutableStateOf<Tool?>(null)

    fun style() = ShapeStyle(stroke, fill, width, opacity)
    fun consumesTap() = tool in setOf(Tool.PLACE, Tool.SHAPE, Tool.LINE, Tool.POLYGON, Tool.ANNOTATION, Tool.SELECT)

    /** Changing tool ends the current drawing, with confirmation if points are placed. */
    fun request(t: Tool) { if (pts.isNotEmpty() && t != tool) pendingSwitch = t else { tool = t; pts.clear() } }

    fun commit(c: SessionController, kind: String, type: String, points: List<Enu>, radius: Double? = null, annotation: String? = null) {
        selected = c.addShape(kind, type, points, radius, label.ifBlank { null }, comment.ifBlank { null }, annotation, style())
        recent = stroke; pts.clear(); label = ""; comment = ""
    }

    fun onTap(c: SessionController, p: Enu) {
        when (tool) {
            Tool.PLACE -> family?.let { f -> c.survey(f, null, ReferencePoint("Relevé", p, c.ltp.toLatLon(p), "MAP_TAP", uncertaintyM = null)) }
                ?: c.notify("Choisissez d'abord une famille.")
            Tool.SHAPE -> {
                pts.add(p)
                if (pts.size == 2) {
                    if (shapeKind == "rectangle") commit(c, "shape", "rectangle", pts.toList())
                    else commit(c, "shape", "circle", listOf(pts[0]), hypot(pts[1].e - pts[0].e, pts[1].n - pts[0].n))
                }
            }
            Tool.LINE, Tool.POLYGON -> pts.add(p)
            Tool.ANNOTATION -> commit(c, "annotation", "point", listOf(p), annotation = annotationType)
            Tool.SELECT -> selected = Shapes.nearest(c.currentShapes(), p)?.id
            else -> {}
        }
    }

    fun commitDefault(c: SessionController) {
        val p = pts.firstOrNull() ?: return
        if (shapeKind == "rectangle") commit(c, "shape", "rectangle", listOf(Enu(p.e - 1, p.n - 1, 0.0), Enu(p.e + 1, p.n + 1, 0.0)))
        else commit(c, "shape", "circle", listOf(p), 1.0)
    }
}

fun shapeLines(c: SessionController, shapes: List<ShapeEvent>, selected: String?): List<MapLine> = shapes.flatMap { s ->
    val color = if (s.id == selected) android.graphics.Color.rgb(255, 0, 255) else NAMED_COLORS[s.style.stroke] ?: android.graphics.Color.RED
    Shapes.outline(s).takeIf { it.size > 1 }?.zipWithNext()?.mapIndexed { i, (a, b) ->
        MapLine(c.ltp.toLatLon(a), c.ltp.toLatLon(b), color, if (i == 0) listOfNotNull(s.label, s.comment).joinToString(" · ").ifBlank { null } else null)
    }.orEmpty()
}

fun shapePoints(c: SessionController, shapes: List<ShapeEvent>, d: DrawState): List<MapPoint> =
    shapes.filter { Shapes.outline(it).size == 1 }.map { s ->
        MapPoint(c.ltp.toLatLon(s.points[0]), listOfNotNull(s.annotationType, s.label, s.comment).joinToString(" · "),
            if (s.id == d.selected) android.graphics.Color.rgb(255, 0, 255) else NAMED_COLORS[s.style.stroke] ?: android.graphics.Color.RED, "shape:${s.id}")
    } + d.pts.mapIndexed { i, p -> MapPoint(c.ltp.toLatLon(p), "Point ${i + 1} du tracé", android.graphics.Color.DKGRAY) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawPanel(c: SessionController, d: DrawState, center: Enu?, selectedSurvey: String?, onSelectSurvey: (String?) -> Unit) {
    val events = c.state.collectAsState().value.events
    val shapes = Shapes.current(events)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tool.entries.forEach { t -> FilterChip(d.tool == t, { d.request(t) }, { Text(t.label) }, leadingIcon = { Icon(t.icon, null) }) }
        AssistChip({ d.pts.clear(); c.undo() }, { Text("Annuler") }, leadingIcon = { Icon(Icons.Default.Refresh, null) })
    }
    d.pendingSwitch?.let { t ->
        AlertDialog(
            onDismissRequest = { d.pendingSwitch = null },
            confirmButton = { TextButton({ d.pts.clear(); d.tool = t; d.pendingSwitch = null }) { Text("Abandonner le tracé") } },
            dismissButton = { TextButton({ d.pendingSwitch = null }) { Text("Continuer le tracé") } },
            title = { Text("Tracé en cours") }, text = { Text("${d.pts.size} point(s) posé(s) seront perdus.") },
        )
    }
    @Composable fun labels() {
        OutlinedTextField(d.label, { d.label = it }, label = { Text("Libellé (facultatif)") }, singleLine = true)
        OutlinedTextField(d.comment, { d.comment = it }, label = { Text("Commentaire (facultatif)") })
    }
    when (d.tool) {
        Tool.MOVE -> Text("Se déplacer : faites glisser la carte, les taps ne posent rien.", style = MaterialTheme.typography.bodySmall)
        Tool.PLACE -> { Text("Poser : touchez une famille, puis la carte.", style = MaterialTheme.typography.bodySmall); FamilyQuickGrid(c, d.family) { d.family = it } }
        Tool.SHAPE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(d.shapeKind == "rectangle", { d.shapeKind = "rectangle"; d.pts.clear() }, { Text("Rectangle") })
                FilterChip(d.shapeKind == "circle", { d.shapeKind = "circle"; d.pts.clear() }, { Text("Cercle") })
            }
            Text(if (d.shapeKind == "rectangle") "Touchez deux coins opposés." else "Touchez le centre, puis un point du bord.", style = MaterialTheme.typography.bodySmall)
            labels()
            OutlinedButton(enabled = d.pts.size == 1, onClick = { d.commitDefault(c) }) { Text("Taille par défaut (2 m)") }
        }
        Tool.LINE -> {
            FilterChip(d.arrow, { d.arrow = !d.arrow }, { Text("Flèche") })
            Text("${d.pts.size} point(s). Touchez les points successifs, puis « Fin ».", style = MaterialTheme.typography.bodySmall)
            labels()
            Button(enabled = d.pts.size >= 2, onClick = { d.commit(c, "shape", if (d.arrow) "arrow" else if (d.pts.size == 2) "line" else "polyline", d.pts.toList()) }) { Text("Fin") }
        }
        Tool.POLYGON -> {
            FilterChip(d.zone, { d.zone = !d.zone }, { Text("Zone") })
            Text("${d.pts.size} point(s). Touchez les sommets, puis « Fermer ».", style = MaterialTheme.typography.bodySmall)
            labels()
            Button(enabled = d.pts.size >= 3, onClick = { d.commit(c, if (d.zone) "zone" else "shape", "polygon", d.pts.toList()) }) { Text("Fermer le polygone") }
        }
        Tool.ANNOTATION -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Details.ANNOTATIONS.forEach { a -> FilterChip(d.annotationType == a, { d.annotationType = a }, { Text(a) }) }
            }
            labels()
            Text("Touchez la carte pour poser l'annotation « ${d.annotationType} ».", style = MaterialTheme.typography.bodySmall)
        }
        Tool.COMMENT -> {
            Text("Commentaire attaché au relevé sélectionné sur la carte, ou à la forme sélectionnée.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(d.comment, { d.comment = it }, label = { Text("Commentaire") })
            Button(enabled = d.comment.isNotBlank() && (d.selected != null || selectedSurvey != null), onClick = {
                val sel = d.selected
                if (sel != null) d.selected = c.editShape(sel) { it.copy(comment = d.comment) } else selectedSurvey?.let { c.editSurvey(it, note = d.comment) }
                d.comment = ""
            }) { Text("Attacher le commentaire") }
        }
        Tool.SELECT -> {
            val s = shapes.firstOrNull { it.id == d.selected }
            if (s == null) Text("Touchez une forme sur la carte ou dans la liste.", style = MaterialTheme.typography.bodySmall)
            else SelectedShape(c, d, s, center)
        }
    }
    if (d.tool in setOf(Tool.SHAPE, Tool.LINE, Tool.POLYGON, Tool.ANNOTATION, Tool.SELECT)) StyleSheet(d)
    Text("Objets du relevé", style = MaterialTheme.typography.labelLarge)
    val surveys = app.lumisite.core.compute.SurveyPositions.current(events)
    if (shapes.isEmpty() && surveys.isEmpty()) Text("Aucun objet sur ce relevé", style = MaterialTheme.typography.bodySmall)
    Column {
        surveys.forEach { sv -> Text("• ${sv.kind}${sv.note?.let { " · $it" } ?: ""}", Modifier.fillMaxWidth().clickable { onSelectSurvey(sv.id); d.selected = null }.padding(4.dp),
            color = if (sv.id == selectedSurvey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        shapes.forEach { sh -> Text("◇ ${sh.annotationType ?: sh.geometryType}${sh.label?.let { " · $it" } ?: ""}${sh.comment?.let { " · $it" } ?: ""}",
            Modifier.fillMaxWidth().clickable { d.selected = sh.id; d.tool = Tool.SELECT; onSelectSurvey(null) }.padding(4.dp),
            color = if (sh.id == d.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun SelectedShape(c: SessionController, d: DrawState, s: ShapeEvent, center: Enu?) {
    var label by remember(s.id) { mutableStateOf(s.label ?: "") }
    var comment by remember(s.id) { mutableStateOf(s.comment ?: "") }
    fun edit(delete: Boolean = false, f: (ShapeEvent) -> ShapeEvent = { it }) { d.selected = c.editShape(s.id, delete, f).takeUnless { delete } }
    Text("Forme sélectionnée : ${s.annotationType ?: s.geometryType}", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0.1, 0.5).forEach { st ->
            OutlinedButton({ edit { Shapes.translate(it, 0.0, st) } }) { Text("N +$st") }
            OutlinedButton({ edit { Shapes.translate(it, 0.0, -st) } }) { Text("N −$st") }
            OutlinedButton({ edit { Shapes.translate(it, st, 0.0) } }) { Text("E +$st") }
            OutlinedButton({ edit { Shapes.translate(it, -st, 0.0) } }) { Text("E −$st") }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled = center != null, onClick = { center?.let { ctr -> edit { val o = Shapes.centroid(it); Shapes.translate(it, ctr.e - o.e, ctr.n - o.n) } } }) { Text("À la croix") }
        OutlinedButton({ edit { Shapes.scale(it, 1.25) } }) { Text("Agrandir") }
        OutlinedButton({ edit { Shapes.scale(it, 0.8) } }) { Text("Réduire") }
        OutlinedButton({ d.selected = c.duplicateShape(s.id) }) { Text("Dupliquer") }
        OutlinedButton({ edit { it.copy(style = d.style()) } }) { Text("Appliquer le style") }
        OutlinedButton({ edit(delete = true) }) { Text("Supprimer") }
    }
    OutlinedTextField(label, { label = it }, label = { Text("Libellé") }, singleLine = true)
    OutlinedTextField(comment, { comment = it }, label = { Text("Commentaire") })
    OutlinedButton({ edit { it.copy(label = label.ifBlank { null }, comment = comment.ifBlank { null }) } }) { Text("Enregistrer le texte") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleSheet(d: DrawState) {
    var more by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Trait :", style = MaterialTheme.typography.labelMedium)
        d.recent?.let { r -> FilterChip(false, { d.stroke = r }, { Text("récent : $r") }) }
        NAMED_COLORS.keys.forEach { k -> FilterChip(d.stroke == k, { d.stroke = k }, { Text(k) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Remplissage :", style = MaterialTheme.typography.labelMedium)
        FilterChip(d.fill == null, { d.fill = null }, { Text("sans remplissage") })
        NAMED_COLORS.keys.forEach { k -> FilterChip(d.fill == k, { d.fill = k }, { Text(k) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Épaisseur :", style = MaterialTheme.typography.labelMedium)
        listOf(2f, 3f, 6f).forEach { w -> FilterChip(d.width == w, { d.width = w }, { Text("${w.toInt()}") }) }
        PRESETS.forEach { p -> AssistChip({ d.stroke = p.stroke; d.fill = p.fill; d.width = p.width; d.opacity = p.opacity }, { Text(p.name) }) }
        TextButton({ more = !more }) { Text(if (more) "Moins" else "Plus…") }
    }
    if (more) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Opacité :", style = MaterialTheme.typography.labelMedium)
        listOf(1f, 0.6f, 0.3f).forEach { o -> FilterChip(d.opacity == o, { d.opacity = o }, { Text("${(o * 100).toInt()} %") }) }
    }
}

/** Catalogue grid with pinned favourites and the 6 last used families first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyQuickGrid(c: SessionController, selected: String?, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("lumisite_quick", Context.MODE_PRIVATE) }
    var pins by remember { mutableStateOf(prefs.getStringSet("pins", emptySet())!!.toSet()) }
    val events = c.state.collectAsState().value.events
    val recent = events.filterIsInstance<SurveyEvent>().filter { it.supersedes == null }.map { it.kind }.reversed().distinct().take(6)
    fun label(k: String) = c.catalog.entries.firstOrNull { it.blockName == k }?.label ?: k
    @Composable fun row(title: String, keys: List<String>) {
        if (keys.isEmpty()) return
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            keys.forEach { k -> FilterChip(selected == k, { onPick(k) }, { Text(label(k)) }) }
        }
    }
    row("Épinglés :", pins.filter { p -> c.catalog.entries.any { it.blockName == p } }.sorted())
    row("Récents :", recent)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        c.catalog.entries.forEach { f -> FilterChip(selected == f.blockName, { onPick(f.blockName) }, { Text(f.label) }) }
    }
    if (selected != null) TextButton({
        pins = if (selected in pins) pins - selected else pins + selected
        prefs.edit().putStringSet("pins", pins).apply()
    }) { Text(if (selected in pins) "Désépingler ${label(selected)}" else "Épingler ${label(selected)}") }
}

/** Last details entered per family, reused for the next object of the same family (process memory). */
object LastDetails { val byFamily = HashMap<String, Map<String, String>>() }

private val DETAIL_LABELS = mapOf(
    "sous_type" to "Sous-type", "technologie" to "Technologie", "culot" to "Culot", "puissance_W" to "Puissance (W)",
    "temp_couleur_K" to "Température de couleur (K)", "ref_etiquette" to "Référence étiquette", "etat" to "État",
)

/** Optional second-level details. Empty fields never block; invalid values are refused at entry. No size/mirror field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsForm(family: String?, values: SnapshotStateMap<String, String>) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(family) { values.clear(); family?.let { f -> LastDetails.byFamily[f]?.let { values.putAll(it) } } }
    TextButton({ open = !open }) { Text(if (open) "Masquer les détails" else "Détails (facultatif)") }
    if (!open) return
    val subs = Details.SUBTYPES[family]
    if (subs != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sous-type :", style = MaterialTheme.typography.labelMedium)
        subs.forEach { s -> FilterChip(values["sous_type"] == s, { values["sous_type"] = if (values["sous_type"] == s) "" else s }, { Text(s) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Statut :", style = MaterialTheme.typography.labelMedium)
        Details.STATUS.forEach { s -> FilterChip(values["status"] == s, { values["status"] = if (values["status"] == s) "" else s }, { Text(s) }) }
    }
    DETAIL_LABELS.filterKeys { it != "sous_type" || subs == null }.forEach { (k, l) ->
        val v = values[k] ?: ""
        val err = Details.validate(k, v)
        OutlinedTextField(v, { values[k] = it }, label = { Text(l) }, singleLine = true, isError = err != null,
            supportingText = err?.let { e -> { Text(e) } },
            keyboardOptions = if (k == "puissance_W" || k == "temp_couleur_K") KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)
    }
}

class VoiceRec(ctx: Context, val file: File, val startUtc: Long, val mono: Long) {
    private val mr: MediaRecorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(44_100); setAudioEncodingBitRate(96_000)
        setOutputFile(file.absolutePath)
        prepare(); start()
    }
    fun stop() { runCatching { mr.stop() }; mr.release() }
}

/** Photo (role "etiquette" or "vue") and voice note attached to a survey record, as separate files with sha256. */
@Composable
fun AttachmentButtons(c: SessionController, surveyId: String) {
    val ctx = LocalContext.current
    // Survives the activity being recreated while the camera app is in front: "path|role".
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var rec by remember { mutableStateOf<VoiceRec?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { p ->
            val f = File(p.substringBeforeLast('|')); val role = p.substringAfterLast('|')
            if (ok && f.exists() && f.length() > 0) {
                val dest = c.attachmentFile("ph", "jpg")
                if (f.renameTo(dest) || runCatching { f.copyTo(dest); f.delete() }.isSuccess) c.attach(surveyId, dest, "image/jpeg", role)
                else c.notify("Photo non enregistrée.")
            } else f.delete()
        }
        pending = null
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        c.notify(if (granted) "Micro autorisé : touchez à nouveau « Note vocale »." else "Micro refusé : note vocale impossible.")
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("etiquette" to "Photo étiquette", "vue" to "Photo vue").forEach { (role, l) ->
            OutlinedButton({
                // Only capture/ is exposed through the FileProvider; the photo is moved into the session afterwards.
                val f = File(File(ctx.filesDir, "capture").apply { mkdirs() }, "ph_${System.currentTimeMillis()}.jpg"); pending = "${f.absolutePath}|$role"
                runCatching { take.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)) }.onFailure { c.notify("Appareil photo indisponible : ${it.message}") }
            }) { Text(l) }
        }
        OutlinedButton({
            val r = rec
            if (r == null) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { perm.launch(Manifest.permission.RECORD_AUDIO); return@OutlinedButton }
                val f = c.attachmentFile("vn", "m4a"); val st = c.stampNow()
                rec = runCatching { VoiceRec(ctx, f, st.utcEpochMs, st.monotonicNs) }.onFailure { c.notify("Enregistrement impossible : ${it.message}") }.getOrNull()
            } else { r.stop(); rec = null; c.attach(surveyId, r.file, "audio/mp4", "voix", r.startUtc, r.mono) }
        }) { Text(if (rec == null) "Note vocale" else "Arrêter la note") }
    }
    // Leaving the record while recording keeps the note instead of leaving an orphan file.
    DisposableEffect(surveyId) {
        onDispose {
            rec?.let { r -> r.stop(); if (r.file.length() > 0) c.attach(surveyId, r.file, "audio/mp4", "voix", r.startUtc, r.mono) else r.file.delete() }
            rec = null
        }
    }
}
