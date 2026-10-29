package app.lumisite.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumisite.android.data.Ids
import app.lumisite.android.data.OsmClient
import app.lumisite.android.data.Repository
import app.lumisite.android.data.RecentZone
import app.lumisite.core.model.BBox
import app.lumisite.core.osm.Bboxes
import app.lumisite.core.osm.CallCapReached
import app.lumisite.core.osm.Geocoding
import app.lumisite.core.osm.OsmGuard
import app.lumisite.core.osm.OsmHttpException
import app.lumisite.core.osm.Overpass
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.model.OsmSnapshot
import app.lumisite.core.model.SessionHeader
import app.lumisite.core.model.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(ms))

@Composable
fun HomeScreen(repo: Repository, onOpen: (Site) -> Unit, onNew: () -> Unit) {
    val sites = remember { repo.sites() }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("LumiSite : diagnostic éclairage (prototype)", style = MaterialTheme.typography.headlineSmall)
        Text("Toutes les données restent sur l'appareil jusqu'à l'export.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNew) { Text("Nouveau") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sites) { s ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(s) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text("%.6f, %.6f · %s · OSM : %s".format(s.origin.lat, s.origin.lon, s.originSource,
                            s.osm?.let { "${it.features.size} objets" } ?: "aucune donnée"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private enum class TapMode { ORIGIN, CORNERS }

@Composable
fun SiteEditorScreen(recentZones: List<RecentZone>, onSave: (Site, OsmClient.Fetched?) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var lastSearched by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Geocoding.Place>>(emptyList()) }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf<LatLon?>(null) }
    var originSource by remember { mutableStateOf("") }
    var center by remember { mutableStateOf(LatLon(46.6, 2.4)) }
    var recenter by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(5.5) }
    var rect by remember { mutableStateOf<BBox?>(null) }
    var cornerA by remember { mutableStateOf<LatLon?>(null) }
    var tapMode by remember { mutableStateOf(TapMode.ORIGIN) }
    var fetched by remember { mutableStateOf<OsmClient.Fetched?>(null) }
    var failure by remember { mutableStateOf<OsmGuard.Failure?>(null) }
    var tooLarge by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var radius by remember { mutableStateOf("80") }
    var apiUrl by remember { mutableStateOf(OsmClient.osmApiBase) }
    var showUrls by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val osm = fetched?.snapshot

    fun setOrigin(p: LatLon, src: String) { origin = p; originSource = src; center = p; recenter++; latText = "%.7f".format(Locale.ROOT, p.lat); lonText = "%.7f".format(Locale.ROOT, p.lon) }
    fun setRect(b: BBox) { rect = b; fetched = null; failure = null; tooLarge = !OsmGuard.areaOk(b); if (origin == null) setOrigin(Bboxes.center(b), "BBOX_CENTER") }

    fun doSearch() {
        val q = query.trim()
        if (busy || q.length < 3 || q == lastSearched) return
        lastSearched = q; busy = true; status = null
        scope.launch {
            try { results = withContext(Dispatchers.IO) { OsmClient.search(q) }; if (results.isEmpty()) status = "Aucun résultat." }
            catch (e: Exception) { status = "Recherche impossible (réseau ?) : ${e.message}. Utilisez des coordonnées ou la carte." }
            finally { busy = false }
        }
    }
    LaunchedEffect(query) { delay(1_000); doSearch() }

    val unavailable = "Le serveur de contours est indisponible. Le site est utilisable sans contours."

    fun fetch(split: Boolean) {
        val b = rect ?: return
        if (!OsmGuard.areaOk(b)) { tooLarge = true; status = "Rectangle trop grand (%.4f degré carré, maximum %.2f) : réduisez-le.".format(Bboxes.areaDeg2(b), OsmGuard.MAX_AREA_DEG2); return }
        OsmClient.osmApiBase = apiUrl.trim().trimEnd('/')
        busy = true; failure = null
        status = if (split) "Découpage en sous-zones (au plus ${OsmGuard.MAX_CALLS} appels)…" else "Requête API OSM…"
        scope.launch {
            val now = System.currentTimeMillis()
            fun show(f: OsmClient.Fetched, via: String) {
                fetched = f
                val counts = f.snapshot.features.groupingBy { it.category }.eachCount()
                status = "$via : ${f.snapshot.features.size} objets $counts en ${f.calls} appel(s)." +
                    (if (f.capped) " Plafond de ${Overpass.MAX_ELEMENTS} éléments atteint : contours incomplets, réduisez le rectangle." else "") +
                    " Touchez un bâtiment pour le sélectionner. Les contours OSM peuvent être absents ou imprécis."
            }
            try {
                show(withContext(Dispatchers.IO) { if (split) OsmClient.fetchMapSplit(b, now) else OsmClient.fetchMap(b, now) }, "API OSM")
            } catch (e: Exception) {
                if (Overpass.shouldFallback(e)) {
                    status = "API OSM indisponible (${(e as? OsmHttpException)?.let { "HTTP ${it.code}" } ?: e.javaClass.simpleName}) : bascule sur Overpass…"
                    try { show(withContext(Dispatchers.IO) { OsmClient.fetchOverpass(b, now) }, "Overpass (secours)") }
                    catch (e2: Exception) { status = unavailable }
                } else status = when (e) {
                    is OsmHttpException -> { failure = e.failure; when (e.failure) {
                        OsmGuard.Failure.TOO_MANY_NODES -> if (split) "Zone encore trop dense après découpage : réduisez le rectangle." else
                            "Zone trop dense pour l'API OSM (plus de 50 000 nœuds). Réduisez le rectangle, ou découpez-le et fusionnez les résultats."
                        OsmGuard.Failure.BBOX_TOO_LARGE -> "L'API OSM refuse ce rectangle (taille maximale dépassée) : réduisez-le."
                        else -> "API OSM : erreur HTTP ${e.code}. $unavailable"
                    } }
                    is CallCapReached -> "Plafond de ${e.calls} appels atteint : réduisez le rectangle. Le site est utilisable sans contours."
                    else -> "Contours indisponibles (${e.javaClass.simpleName}: ${e.message}). Le site est utilisable sans contours."
                }
            } finally { busy = false }
        }
    }

    Row(Modifier.fillMaxSize()) {
        OsmMap(
            Modifier.weight(0.6f).fillMaxSize(), center, zoom,
            MapContent(origin = origin, features = osm?.features.orEmpty(), selectedFeatureRef = selected, rect = rect, picked = cornerA),
            recenterKey = recenter,
            onTap = {
                if (tapMode == TapMode.CORNERS) {
                    val a0 = cornerA
                    if (a0 == null) cornerA = it else { setRect(Bboxes.fromCorners(a0, it)); cornerA = null; tapMode = TapMode.ORIGIN }
                } else setOrigin(it, "MAP_TAP")
            },
            onFeatureTap = { f -> selected = if (selected == f.ref) null else f.ref },
        )
        Column(Modifier.weight(0.4f).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCancel) { Text("← Retour") }
                Spacer(Modifier.width(8.dp))
                Text("Nouveau site", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                name, { name = it }, label = { Text("Nom du site *") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = name.isBlank(), supportingText = { if (name.isBlank()) Text("Nom de projet requis") },
            )
            HorizontalDivider()
            Text("1. Rechercher (IGN, puis Nominatim), saisir des coordonnées ou toucher la carte", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                query, { query = it }, label = { Text("Adresse / lieu") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { lastSearched = ""; doSearch() }),
            )
            results.forEach { r ->
                Text("${r.name} (${r.source})", Modifier.fillMaxWidth().clickable {
                    setOrigin(LatLon(r.lat, r.lon), "${r.source} ${r.ref ?: ""}".trim()); zoom = 18.0
                    if (name.isBlank()) name = r.name.substringBefore(","); results = emptyList()
                }.padding(6.dp), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                OutlinedTextField(latText, { latText = it }, label = { Text("Latitude (WGS84)") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(lonText, { lonText = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = {
                val la = latText.replace(',', '.').toDoubleOrNull(); val lo = lonText.replace(',', '.').toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) status = "Coordonnées invalides." else { setOrigin(LatLon(la, lo), "COORDINATES"); zoom = 18.0 }
            }) { Text("Utiliser ces coordonnées") }
            Text(origin?.let { "Origine : %.7f, %.7f (%s)".format(it.lat, it.lon, originSource) } ?: "Origine non définie", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("2. Rectangle du site (conservé même sans OSM)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { tapMode = TapMode.CORNERS; cornerA = null }) {
                    Text(if (tapMode == TapMode.CORNERS) (if (cornerA == null) "Touchez le 1er coin…" else "Touchez le 2e coin…") else "Deux coins sur la carte")
                }
                OutlinedTextField(radius, { radius = it }, label = { Text("Demi-côté (m)") }, modifier = Modifier.width(110.dp), singleLine = true)
                OutlinedButton(enabled = origin != null, onClick = { setRect(OsmClient.bboxAround(origin!!, radius.toDoubleOrNull()?.coerceIn(10.0, 500.0) ?: 80.0)) }) { Text("Autour de l'origine") }
            }
            if (recentZones.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Zones récentes :", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                recentZones.forEach { z -> AssistChip({ setRect(z.bbox); center = Bboxes.center(z.bbox); zoom = 17.0; recenter++ }, { Text(z.name) }) }
            }
            rect?.let { b -> Text("Rectangle : %.5f degré carré%s".format(Bboxes.areaDeg2(b), if (tooLarge) " — trop grand (max %.2f)".format(OsmGuard.MAX_AREA_DEG2) else ""), style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Text("3. (Optionnel) Contours OSM du rectangle (API OSM v0.6)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && rect != null, onClick = { fetch(split = false) }) { Text("Récupérer") }
                if (tooLarge || failure == OsmGuard.Failure.TOO_MANY_NODES || failure == OsmGuard.Failure.BBOX_TOO_LARGE)
                    OutlinedButton(enabled = !busy, onClick = { rect?.let { setRect(Bboxes.scaled(it, 0.5)) }; status = "Rectangle réduit de moitié (côtés). Touchez « Récupérer »." }) { Text("Réduire") }
                if (failure == OsmGuard.Failure.TOO_MANY_NODES)
                    OutlinedButton(enabled = !busy, onClick = { fetch(split = true) }) { Text("Découper et fusionner") }
            }
            TextButton(onClick = { showUrls = !showUrls }) { Text(if (showUrls) "Masquer l'URL du service" else "URL du service OSM…") }
            if (showUrls) OutlinedTextField(apiUrl, { apiUrl = it }, label = { Text("API OSM v0.6") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            selected?.let { Text("Bâtiment sélectionné : $it", style = MaterialTheme.typography.bodySmall) }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            HorizontalDivider()
            Row {
                // Enabled as soon as an origin exists, whatever the state of the OSM request.
                Button(enabled = origin != null, onClick = {
                    val o = origin!!
                    val siteName = name.trim().ifEmpty { "Site " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()) }
                    onSave(Site(
                        id = Ids.new("site"), name = siteName, origin = o, originSource = originSource, createdUtcMs = System.currentTimeMillis(),
                        crs = listOf(
                            app.lumisite.core.geo.CrsInfo("EPSG:4326", "GEOGRAPHIC", "WGS84 lat/lon degrees", axes = listOf("lat", "lon"), units = "deg"),
                            LocalTangentPlane(o).crsInfo(),
                        ),
                        osm = osm, selectedBuildingOsmRef = selected, bbox = rect ?: osm?.bbox,
                    ), fetched)
                }) { Text(if (osm == null) "Créer sans contours OSM" else "Créer le site") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    }
}

@Composable
fun SiteScreen(site: Site, sessions: List<SessionHeader>, surveys: List<SurveyPositions.Resolved>, onNewSession: () -> Unit, onResume: (SessionHeader) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ltp = remember(site.id) { LocalTangentPlane(site.origin) }
    Row(Modifier.fillMaxSize()) {
        OsmMap(Modifier.weight(0.6f).fillMaxSize(), site.origin, 18.0, MapContent(
            origin = site.origin, features = site.osm?.features.orEmpty(), selectedFeatureRef = site.selectedBuildingOsmRef,
            rect = site.bbox ?: site.osm?.bbox,
            points = surveys.mapNotNull { r -> r.site?.let { MapPoint(ltp.toLatLon(it.copy(u = 0.0)), "${r.event.kind} ${r.event.note ?: ""}", android.graphics.Color.MAGENTA) } },
        ), onTap = {})
        Column(Modifier.weight(0.4f).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("← Retour") }
                Spacer(Modifier.width(8.dp))
                Text(site.name, style = MaterialTheme.typography.titleLarge)
            }
            Text("Origine %.7f, %.7f (%s)".format(site.origin.lat, site.origin.lon, site.originSource), style = MaterialTheme.typography.bodySmall)
            Text(site.osm?.let { "OSM : ${it.features.size} objets, récupérés le ${fmtTime(it.retrievedUtcMs)} (${it.license})" } ?: "Pas de données OSM (site défini manuellement)", style = MaterialTheme.typography.bodySmall)
            Text("Plans importés : ${site.plans.size} (import prévu dans une tranche ultérieure)", style = MaterialTheme.typography.bodySmall)
            Text("Relevés : ${surveys.size} (${surveys.count { it.site == null }} sans position)", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onNewSession) { Text("Nouvelle session terrain") }
            HorizontalDivider()
            Text("Sessions", style = MaterialTheme.typography.titleMedium)
            sessions.forEach { h ->
                Card(Modifier.fillMaxWidth().clickable { onResume(h) }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(h.name); Text("${h.id} · ${h.device.model} · reprendre / exporter", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
