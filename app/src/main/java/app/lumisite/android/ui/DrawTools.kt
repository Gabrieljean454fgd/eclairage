package app.lumisite.android.ui

import android.Manifest
import app.lumisite.android.data.CatalogStore
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.PlainTooltip
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
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import app.lumisite.core.survey.Sketch
import java.io.File
import kotlin.math.hypot

/** Annotation toolbar (fiche F). Material icons (Apache-2.0) + French word; no Xodo/PDFTron resource is used. */
enum class Tool(val icon: ImageVector, val label: String) {
    MOVE(ToolIcons.Move, "Se déplacer"),
    PLACE(Icons.Default.Place, "Poser"),
    SHAPE(ToolIcons.Rectangle, "Forme"),
    LINE(ToolIcons.Line, "Ligne"),
    POLYGON(ToolIcons.Polygon, "Polygone"),
    ANNOTATION(Icons.Default.Warning, "Annotation"),
    COMMENT(ToolIcons.Comment, "Commentaire"),
    SELECT(ToolIcons.Select, "Sélection"),
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

    /** Polygon guidance, shown over the map (the tool panel below may be scrolled out of view in portrait). */
    fun polygonHint(): String? = if (tool != Tool.POLYGON) null
        else if (pts.size < 3) "${pts.size} sommet(s). Un doigt pose un sommet (l'aperçu suit le doigt), deux doigts déplacent la carte."
        else "${pts.size} sommets. Touchez à nouveau le dernier sommet pour fermer."
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
    /** Record whose photo is proposed right after a placement (« Photo après pose »). */
    var photoFor by mutableStateOf<String?>(null)
    var autoPhoto = true
    var selected by mutableStateOf<String?>(null)
    var pendingSwitch by mutableStateOf<Tool?>(null)
    /** Finger position while drawing (elastic preview), null when no finger is down. */
    var hover by mutableStateOf<Enu?>(null)
    /** Touch tolerance in metres at the current zoom (Sketch.toleranceM). */
    var tolM = 0.5

    fun style() = ShapeStyle(stroke, fill, width, opacity)
    fun consumesTap() = tool in setOf(Tool.PLACE, Tool.SHAPE, Tool.LINE, Tool.POLYGON, Tool.ANNOTATION, Tool.SELECT)
    /** Drawing tools: one finger draws, two fingers move the map (stage 2.1). SELECT keeps taps on map objects. */
    fun capturesFinger() = tool in setOf(Tool.PLACE, Tool.SHAPE, Tool.LINE, Tool.POLYGON, Tool.ANNOTATION)

    fun preview(): List<Enu> = when (tool) {
        Tool.POLYGON -> Sketch.polygonPreview(pts, hover)
        Tool.LINE -> Sketch.linePreview(pts, hover)
        Tool.SHAPE -> if (shapeKind == "rectangle") Sketch.rectanglePreview(pts.firstOrNull(), hover) else Sketch.linePreview(pts, hover)
        else -> emptyList()
    }

    /** Changing tool ends the current drawing, with confirmation if points are placed. */
    fun request(t: Tool) { if (pts.isNotEmpty() && t != tool) pendingSwitch = t else { tool = t; pts.clear() } }

    fun commit(c: SessionController, kind: String, type: String, points: List<Enu>, radius: Double? = null, annotation: String? = null) {
        val id = c.addShape(kind, type, points, radius, label.ifBlank { null }, comment.ifBlank { null }, annotation, style())
        if (id.isEmpty()) return // refused (e.g. self-intersecting zone): keep the drawing so it can be fixed
        selected = id
        recent = stroke; pts.clear(); label = ""; comment = ""
    }

    fun onTap(c: SessionController, p: Enu) {
        when (tool) {
            Tool.PLACE -> family?.let { f -> if (c.survey(f, null, ReferencePoint("Relevé", p, c.ltp.toLatLon(p), "MAP_TAP", uncertaintyM = null)) && autoPhoto) photoFor = c.lastSurveyId }
                ?: c.notify("Choisissez d'abord un type.")
            Tool.SHAPE -> {
                pts.add(p)
                if (pts.size == 2) {
                    if (shapeKind == "rectangle") commit(c, "shape", "rectangle", pts.toList())
                    else commit(c, "shape", "circle", listOf(pts[0]), hypot(pts[1].e - pts[0].e, pts[1].n - pts[0].n))
                }
            }
            Tool.LINE -> pts.add(p)
            Tool.POLYGON -> when (Sketch.polygonTap(pts, p, tolM)) {
                Sketch.PolygonTap.ADD -> pts.add(p)
                Sketch.PolygonTap.CLOSE -> commit(c, if (zone) "zone" else "shape", "polygon", pts.toList())
                Sketch.PolygonTap.IGNORE -> c.notify("Il faut au moins 3 sommets pour fermer le polygone.")
            }
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

/** Toolbar button (stage 2.4): telling icon, 48 dp touch target, tooltip on long press, label read by accessibility/tests. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(label: String, icon: ImageVector, selected: Boolean, tag: String, onClick: () -> Unit) {
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        androidx.compose.material3.FilledTonalIconToggleButton(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp).testTag(tag)) {
            Icon(icon, contentDescription = label)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawPanel(c: SessionController, d: DrawState, center: Enu?, selectedSurvey: String?, onSelectSurvey: (String?) -> Unit) {
    val events = c.state.collectAsState().value.events
    val shapes = Shapes.current(events)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tool.entries.forEach { t -> ToolButton(t.label, t.icon, d.tool == t, "session_annoter_" + t.name.lowercase()) { d.request(t) } }
        ToolButton("Annuler la dernière action", Icons.Default.Refresh, false, "session_annoter_annuler") { d.pts.clear(); c.undo() }
    }
    Text("Outil : ${d.tool.label}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("session_annoter_outil_actif"))
    d.pendingSwitch?.let { t ->
        AlertDialog(
            onDismissRequest = { d.pendingSwitch = null },
            confirmButton = { TextButton({ d.pts.clear(); d.tool = t; d.pendingSwitch = null }, modifier = Modifier.testTag("session_annoter_abandonner_le_trace")) { Text("Abandonner le tracé") } },
            dismissButton = { TextButton({ d.pendingSwitch = null }, modifier = Modifier.testTag("session_annoter_continuer_le_trace")) { Text("Continuer le tracé") } },
            title = { Text("Tracé en cours") }, text = { Text("${d.pts.size} point(s) posé(s) seront perdus.") },
        )
    }
    @Composable fun labels() {
        OutlinedTextField(d.label, { d.label = it }, label = { Text("Libellé (facultatif)") }, singleLine = true, modifier = Modifier.testTag("session_annoter_libelle_facultatif"))
        OutlinedTextField(d.comment, { d.comment = it }, label = { Text("Commentaire (facultatif)") }, modifier = Modifier.testTag("session_annoter_commentaire_facultatif"))
    }
    when (d.tool) {
        Tool.MOVE -> Text("Se déplacer : faites glisser la carte, les taps ne posent rien.", style = MaterialTheme.typography.bodySmall)
        Tool.PLACE -> {
            val ctx = LocalContext.current
            d.autoPhoto = AutoPhoto.enabled(ctx)
            val pid = d.photoFor
            if (pid != null) androidx.compose.runtime.key(pid) { PhotoPrompt(c, pid) { d.photoFor = null } }
            else { Text("Poser : choisissez un type, puis touchez la carte.", style = MaterialTheme.typography.bodySmall); FamilyQuickGrid(c, d.family) { d.family = it } }
        }
        Tool.SHAPE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(d.shapeKind == "rectangle", { d.shapeKind = "rectangle"; d.pts.clear() }, { Text("Rectangle") }, modifier = Modifier.testTag("session_annoter_rectangle"))
                FilterChip(d.shapeKind == "circle", { d.shapeKind = "circle"; d.pts.clear() }, { Text("Cercle") }, modifier = Modifier.testTag("session_annoter_cercle"))
            }
            Text(if (d.shapeKind == "rectangle") "Touchez deux coins opposés." else "Touchez le centre, puis un point du bord.", style = MaterialTheme.typography.bodySmall)
            labels()
            OutlinedButton(enabled = d.pts.size == 1, onClick = { d.commitDefault(c) }, modifier = Modifier.testTag("session_annoter_taille_par_defaut_2_m")) { Text("Taille par défaut (2 m)") }
        }
        Tool.LINE -> {
            FilterChip(d.arrow, { d.arrow = !d.arrow }, { Text("Flèche") }, modifier = Modifier.testTag("session_annoter_fleche"))
            Text("${d.pts.size} point(s). Touchez les points successifs, puis « Fin ».", style = MaterialTheme.typography.bodySmall)
            labels()
            Button(enabled = d.pts.size >= 2, onClick = { d.commit(c, "shape", if (d.arrow) "arrow" else if (d.pts.size == 2) "line" else "polyline", d.pts.toList()) }, modifier = Modifier.testTag("session_annoter_fin")) { Text("Fin") }
        }
        Tool.POLYGON -> {
            FilterChip(d.zone, { d.zone = !d.zone }, { Text("Zone") }, modifier = Modifier.testTag("session_annoter_zone"))
            labels()
        }
        Tool.ANNOTATION -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Details.ANNOTATIONS.forEach { a -> FilterChip(d.annotationType == a, { d.annotationType = a }, { Text(a) }, modifier = Modifier.testTag("session_annoter_type_" + a.lowercase().replace(Regex("[^a-z0-9]+"), "_"))) }
            }
            labels()
            Text("Touchez la carte pour poser l'annotation « ${d.annotationType} ».", style = MaterialTheme.typography.bodySmall)
        }
        Tool.COMMENT -> {
            Text("Commentaire attaché au relevé sélectionné sur la carte, ou à la forme sélectionnée.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(d.comment, { d.comment = it }, label = { Text("Commentaire") }, modifier = Modifier.testTag("session_annoter_commentaire"))
            Button(enabled = d.comment.isNotBlank() && (d.selected != null || selectedSurvey != null), onClick = {
                val sel = d.selected
                if (sel != null) d.selected = c.editShape(sel) { it.copy(comment = d.comment) } else selectedSurvey?.let { c.editSurvey(it, note = d.comment) }
                d.comment = ""
            }, modifier = Modifier.testTag("session_annoter_attacher_le_commentaire")) { Text("Attacher le commentaire") }
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
        surveys.forEachIndexed { i, sv -> Text("• ${sv.kind}${sv.note?.let { " · $it" } ?: ""}", Modifier.fillMaxWidth().testTag("session_objets_releve_" + (i + 1)).clickable { onSelectSurvey(sv.id); d.selected = null }.padding(4.dp),
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
            OutlinedButton({ edit { Shapes.translate(it, 0.0, st) } }, modifier = Modifier.testTag("session_annoter_forme_nord_plus_$st")) { Text("N +$st") }
            OutlinedButton({ edit { Shapes.translate(it, 0.0, -st) } }, modifier = Modifier.testTag("session_annoter_forme_nord_moins_$st")) { Text("N −$st") }
            OutlinedButton({ edit { Shapes.translate(it, st, 0.0) } }, modifier = Modifier.testTag("session_annoter_forme_est_plus_$st")) { Text("E +$st") }
            OutlinedButton({ edit { Shapes.translate(it, -st, 0.0) } }, modifier = Modifier.testTag("session_annoter_forme_est_moins_$st")) { Text("E −$st") }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled = center != null, onClick = { center?.let { ctr -> edit { val o = Shapes.centroid(it); Shapes.translate(it, ctr.e - o.e, ctr.n - o.n) } } }, modifier = Modifier.testTag("session_annoter_forme_a_la_croix")) { Text("À la croix") }
        OutlinedButton({ edit { Shapes.scale(it, 1.25) } }, modifier = Modifier.testTag("session_annoter_forme_agrandir")) { Text("Agrandir") }
        OutlinedButton({ edit { Shapes.scale(it, 0.8) } }, modifier = Modifier.testTag("session_annoter_forme_reduire")) { Text("Réduire") }
        OutlinedButton({ d.selected = c.duplicateShape(s.id) }, modifier = Modifier.testTag("session_annoter_forme_dupliquer")) { Text("Dupliquer") }
        OutlinedButton({ edit { it.copy(style = d.style()) } }, modifier = Modifier.testTag("session_annoter_forme_appliquer_le_style")) { Text("Appliquer le style") }
        OutlinedButton({ edit(delete = true) }, modifier = Modifier.testTag("session_annoter_forme_supprimer")) { Text("Supprimer") }
    }
    OutlinedTextField(label, { label = it }, label = { Text("Libellé") }, singleLine = true, modifier = Modifier.testTag("session_annoter_forme_libelle"))
    OutlinedTextField(comment, { comment = it }, label = { Text("Commentaire") }, modifier = Modifier.testTag("session_annoter_forme_commentaire"))
    OutlinedButton({ edit { it.copy(label = label.ifBlank { null }, comment = comment.ifBlank { null }) } }, modifier = Modifier.testTag("session_annoter_forme_enregistrer_le_texte")) { Text("Enregistrer le texte") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleSheet(d: DrawState) {
    var more by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Trait :", style = MaterialTheme.typography.labelMedium)
        d.recent?.let { r -> FilterChip(false, { d.stroke = r }, { Text("récent : $r") }, modifier = Modifier.testTag("session_annoter_style_recent")) }
        NAMED_COLORS.keys.forEach { k -> FilterChip(d.stroke == k, { d.stroke = k }, { Text(k) }, modifier = Modifier.testTag("session_annoter_style_trait_" + k)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Remplissage :", style = MaterialTheme.typography.labelMedium)
        FilterChip(d.fill == null, { d.fill = null }, { Text("sans remplissage") }, modifier = Modifier.testTag("session_annoter_style_sans_remplissage"))
        NAMED_COLORS.keys.forEach { k -> FilterChip(d.fill == k, { d.fill = k }, { Text(k) }, modifier = Modifier.testTag("session_annoter_style_remplissage_" + k)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Épaisseur :", style = MaterialTheme.typography.labelMedium)
        listOf(2f, 3f, 6f).forEach { w -> FilterChip(d.width == w, { d.width = w }, { Text("${w.toInt()}") }, modifier = Modifier.testTag("session_annoter_style_epaisseur_" + w.toInt())) }
        PRESETS.forEach { p -> AssistChip({ d.stroke = p.stroke; d.fill = p.fill; d.width = p.width; d.opacity = p.opacity }, { Text(p.name) }, modifier = Modifier.testTag("session_annoter_style_preset_" + p.name.lowercase().replace("é", "e"))) }
        TextButton({ more = !more }, modifier = Modifier.testTag("session_annoter_style_plus")) { Text(if (more) "Moins" else "Plus…") }
    }
    if (more) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Opacité :", style = MaterialTheme.typography.labelMedium)
        listOf(1f, 0.6f, 0.3f).forEach { o -> FilterChip(d.opacity == o, { d.opacity = o }, { Text("${(o * 100).toInt()} %") }, modifier = Modifier.testTag("session_annoter_style_opacite_" + (o * 100).toInt())) }
    }
}

/**
 * v176e menu bloc: one picker for Session > Relevé and Annoter > Poser. Family grid (Éclairage first) with plan symbols,
 * then the family's types grouped by sub-family (symbol + label), breadcrumb, accent-insensitive search (code, label,
 * synonyms), favourites and the 12 recent types on top, « Ajouter un type… » (local LS_PERSO_n). Two taps choose a type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyQuickGrid(c: SessionController, selected: String?, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableStateOf(0) }
    var justAdded by remember { mutableStateOf<String?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val cat = remember(rev) { c.catalog }
    val prefs = remember { ctx.getSharedPreferences("lumisite_quick", Context.MODE_PRIVATE) }
    // Favorites saved before catalog 2 hold old block names: stored once as their LS_* code so the star can remove them.
    var pins by remember {
        val saved = prefs.getStringSet("pins", emptySet())!!.toSet()
        val canonical = saved.map { cat.find(it)?.code ?: it }.toSet()
        if (canonical != saved) prefs.edit().putStringSet("pins", canonical).apply()
        mutableStateOf(canonical)
    }
    val sel = selected?.let { cat.find(it) }
    var fam by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    val events = c.state.collectAsState().value.events
    // Former DWG block names of old sessions are shown with their LS_* type (alias read, the event is not rewritten).
    val recent = events.filterIsInstance<SurveyEvent>().filter { it.supersedes == null }.mapNotNull { cat.find(it.kind)?.code }.reversed().distinct().take(12)
    fun color(e: app.lumisite.core.catalog.CatalogEntry) = android.graphics.Color.parseColor(cat.family(e.famille)?.color ?: "#616161")
    fun symbolOf(e: app.lumisite.core.catalog.CatalogEntry) = e.symbole ?: cat.family(e.famille)?.symbol
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable fun typeRow(e: app.lumisite.core.catalog.CatalogEntry, tag: String) {
        val on = sel?.code == e.code
        val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        if (justAdded == e.code && tag.startsWith("session_releve_type_")) LaunchedEffect(e.code) { reveal.bringIntoView(); justAdded = null }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .bringIntoViewRequester(reveal)
                .background(if (on) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { onPick(e.code) }.padding(horizontal = 4.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanSymbol(symbolOf(e), color(e), 32, selected = on)
            Column(Modifier.padding(start = 8.dp)) {
                Text(e.label, style = MaterialTheme.typography.bodyMedium)
                Text(if (e.isBlock) e.code else "${e.code} · à classer au bureau", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    @Composable fun chips(title: String, prefix: String, keys: List<String>) {
        val es = keys.mapNotNull { cat.find(it) }
        if (es.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            es.forEach { e -> FilterChip(sel?.code == e.code, { onPick(e.code) }, { Text(e.label, maxLines = 1) },
                leadingIcon = { PlanSymbol(symbolOf(e), color(e), 24) }, modifier = Modifier.testTag(prefix + e.code)) }
        }
    }
    OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Rechercher (code, libellé, synonyme)") },
        modifier = Modifier.fillMaxWidth().testTag("session_releve_recherche"))
    val crumb = when {
        sel != null && (fam == null || fam == sel.famille) -> "Catalogue › " + cat.breadcrumb(sel)
        fam != null -> "Catalogue › " + (cat.family(fam!!)?.label ?: fam)
        else -> "Catalogue"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fam != null && query.isBlank()) TextButton({ fam = null; adding = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("session_releve_retour")) { Text("‹ Familles") }
        Text(crumb, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).testTag("session_releve_fil_ariane"))
        if (sel != null) androidx.compose.material3.IconToggleButton(sel.code in pins, {
            pins = if (sel.code in pins) pins - sel.code else pins + sel.code
            prefs.edit().putStringSet("pins", pins).apply()
        }, modifier = Modifier.size(48.dp).testTag("session_releve_favori_basculer")) {
            Icon(Icons.Default.Star, contentDescription = if (sel.code in pins) "Retirer « ${sel.label} » des favoris" else "Ajouter « ${sel.label} » aux favoris",
                tint = if (sel.code in pins) androidx.compose.ui.graphics.Color(0xFFE6B800) else MaterialTheme.colorScheme.outline)
        }
    }
    if (query.isNotBlank()) {
        val hits = cat.search(query)
        Text(if (hits.isEmpty()) "Aucun type pour « $query »" else "${hits.size} résultat(s)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("session_releve_resultats"))
        hits.take(20).forEach { typeRow(it, "session_releve_type_" + it.code) }
    } else {
        chips("Favoris", "session_releve_favori_", pins.filter { cat.find(it) != null }.sorted())
        chips("Récents", "session_releve_recent_", recent)
        val f = fam
        if (f == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cat.familles.forEach { x ->
                Column(
                    Modifier.width(84.dp).heightIn(min = 64.dp).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { fam = x.code; adding = false }.padding(4.dp).testTag("session_releve_categorie_" + x.code),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlanSymbol(x.symbol, android.graphics.Color.parseColor(x.color), 32)
                    Text(x.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            cat.sousFamilles(f).forEach { sf ->
                Text(sf, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                cat.types(f, sf).forEach { typeRow(it, "session_releve_type_" + it.code) }
            }
            if (!adding) OutlinedButton({ adding = true; newName = ""; addError = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("session_releve_ajouter_type")) { Text("Ajouter un type…") }
            else Column {
                Text("Nouveau type dans ${cat.family(f)?.label ?: f} (local, à classer au bureau)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(newName, { newName = it; addError = null }, singleLine = true, label = { Text("Nom du type") }, isError = addError != null,
                    modifier = Modifier.fillMaxWidth().testTag("session_releve_nouveau_type_nom"))
                addError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button({
                        runCatching { CatalogStore.addCustom(ctx, f, newName) }
                            .onSuccess { e -> focus.clearFocus(); adding = false; rev++; justAdded = e.code; onPick(e.code); c.notify("Type ajouté : ${e.label} (${e.code})") }
                            .onFailure { addError = it.message }
                    }, enabled = newName.isNotBlank(), modifier = Modifier.testTag("session_releve_nouveau_type_valider")) { Text("Ajouter") }
                    TextButton({ adding = false }, modifier = Modifier.testTag("session_releve_nouveau_type_annuler")) { Text("Annuler") }
                }
            }
        }
    }
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
    TextButton({ open = !open }, modifier = Modifier.testTag("session_details_afficher")) { Text(if (open) "Masquer les détails" else "Détails (facultatif)") }
    if (!open) return
    val subs = Details.SUBTYPES[family]
    if (subs != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sous-type :", style = MaterialTheme.typography.labelMedium)
        subs.forEach { s -> FilterChip(values["sous_type"] == s, { values["sous_type"] = if (values["sous_type"] == s) "" else s }, { Text(s) }, modifier = Modifier.testTag("session_details_sous_type_" + s)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Statut :", style = MaterialTheme.typography.labelMedium)
        Details.STATUS.forEach { s -> FilterChip(values["status"] == s, { values["status"] = if (values["status"] == s) "" else s }, { Text(s) }, modifier = Modifier.testTag("session_details_statut_" + s)) }
    }
    DETAIL_LABELS.filterKeys { it != "sous_type" || subs == null }.forEach { (k, l) ->
        val v = values[k] ?: ""
        val err = Details.validate(k, v)
        OutlinedTextField(v, { values[k] = it }, label = { Text(l) }, singleLine = true, isError = err != null,
            supportingText = err?.let { e -> { Text(e) } },
            keyboardOptions = if (k == "puissance_W" || k == "temp_couleur_K") KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default, modifier = Modifier.testTag("session_details_" + k.lowercase()))
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
fun AttachmentButtons(c: SessionController, surveyId: String, attachments: List<app.lumisite.core.model.Attachment> = emptyList(), onReselect: (String) -> Unit = {}) {
    val counts = app.lumisite.core.survey.SurveyEdits.attachmentCounts(attachments)
    val ctx = LocalContext.current
    // Survives the activity being recreated while the camera app is in front: "path|role".
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var rec by remember { mutableStateOf<VoiceRec?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { p ->
            val f = File(p.substringBeforeLast('|')); val role = p.substringAfterLast('|')
            if (f.exists() && f.length() > 0) { // some camera apps (HyperOS) return CANCELED although the photo was written
                val dest = c.attachmentFile("ph", "jpg")
                if (f.renameTo(dest) || runCatching { f.copyTo(dest); f.delete() }.isSuccess) c.attach(surveyId, dest, "image/jpeg", role)?.let(onReselect)
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
            }, modifier = Modifier.testTag("session_pieces_photo_" + role)) { Text("$l (${counts[role] ?: 0})") }
        }
        OutlinedButton({
            val r = rec
            if (r == null) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { perm.launch(Manifest.permission.RECORD_AUDIO); return@OutlinedButton }
                val f = c.attachmentFile("vn", "m4a"); val st = c.stampNow()
                rec = runCatching { VoiceRec(ctx, f, st.utcEpochMs, st.monotonicNs) }.onFailure { c.notify("Enregistrement impossible : ${it.message}") }.getOrNull()
            } else { r.stop(); rec = null; c.attach(surveyId, r.file, "audio/mp4", "voix", r.startUtc, r.mono)?.let(onReselect) }
        }, modifier = Modifier.testTag("session_pieces_note_vocale")) { Text(if (rec == null) "Note vocale (${counts["voix"] ?: 0})" else "Arrêter la note") }
    }
    // Leaving the record while recording keeps the note instead of leaving an orphan file.
    DisposableEffect(surveyId) {
        onDispose {
            rec?.let { r -> r.stop(); if (r.file.length() > 0) c.attach(surveyId, r.file, "audio/mp4", "voix", r.startUtc, r.mono) else r.file.delete() }
            rec = null
        }
    }
}

/** Réglages > Photo automatique à l'implantation (on by default). */
object AutoPhoto {
    private const val FILE = "lumisite_photo"
    fun enabled(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("auto", true)
    fun set(ctx: Context, on: Boolean) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("auto", on).apply()
}

/**
 * Session > Relevé, right after a placement: opens the camera at once, then offers
 * « Prendre la photo » (another one), « Reprendre » (replaces the last one in the record) and « Passer ».
 * Each photo is attached to the object (its position) with the capture time stamp.
 */
@Composable
fun PhotoPrompt(c: SessionController, surveyId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var id by rememberSaveable { mutableStateOf(surveyId) }
    var last by rememberSaveable { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableStateOf(0) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var launched by rememberSaveable { mutableStateOf(false) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { p ->
            val f = File(p.substringBeforeLast('|')); val replace = p.substringAfterLast('|').ifEmpty { null }
            if (f.exists() && f.length() > 0) { // some camera apps (HyperOS) return CANCELED although the photo was written
                val st = c.stampNow()
                val dest = c.attachmentFile("ph", "jpg")
                if (f.renameTo(dest) || runCatching { f.copyTo(dest); f.delete() }.isSuccess) {
                    c.attach(id, dest, "image/jpeg", "vue", st.utcEpochMs, st.monotonicNs, replacePath = replace)?.let { id = it }
                    last = c.sessionRelPath(dest); if (replace == null) count++
                } else c.notify("Photo non enregistrée.")
            } else f.delete()
        }
        pending = null
    }
    fun shoot(replace: String?) {
        val f = File(File(ctx.filesDir, "capture").apply { mkdirs() }, "ph_${System.currentTimeMillis()}.jpg"); pending = "${f.absolutePath}|${replace ?: ""}"
        runCatching { take.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)) }.onFailure { pending = null; c.notify("Appareil photo indisponible : ${it.message}") }
    }
    LaunchedEffect(Unit) { if (!launched) { launched = true; shoot(null) } }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(6.dp).testTag("session_photo_proposee")) {
        Text(if (count == 0) "Photo de l'objet posé (facultative)" else "$count photo(s) rattachée(s) à l'objet", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("session_photo_etat"))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button({ shoot(null) }, modifier = Modifier.testTag("session_photo_prendre")) { Text(if (count == 0) "Prendre la photo" else "Autre photo") }
            OutlinedButton({ shoot(last) }, enabled = last != null, modifier = Modifier.testTag("session_photo_reprendre")) { Text("Reprendre") }
            OutlinedButton(onClose, modifier = Modifier.testTag("session_photo_passer")) { Text(if (count == 0) "Passer" else "Terminé") }
        }
    }
}
