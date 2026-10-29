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

@Composable
fun SiteEditorScreen(onSave: (Site) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OsmClient.Place>>(emptyList()) }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf<LatLon?>(null) }
    var originSource by remember { mutableStateOf("") }
    var center by remember { mutableStateOf(LatLon(46.6, 2.4)) }
    var recenter by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(5.5) }
    var osm by remember { mutableStateOf<OsmSnapshot?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var radius by remember { mutableStateOf("150") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun setOrigin(p: LatLon, src: String) { origin = p; originSource = src; center = p; recenter++; latText = "%.7f".format(Locale.ROOT, p.lat); lonText = "%.7f".format(Locale.ROOT, p.lon) }

    Row(Modifier.fillMaxSize()) {
        OsmMap(
            Modifier.weight(0.6f).fillMaxSize(), center, zoom,
            MapContent(origin = origin, features = osm?.features.orEmpty(), selectedFeatureRef = selected),
            recenterKey = recenter,
            onTap = { setOrigin(it, "MAP_TAP") },
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
            Text("1. Rechercher (Nominatim/OSM) ou saisir des coordonnées, ou toucher la carte", style = MaterialTheme.typography.labelLarge)
            Row {
                OutlinedTextField(query, { query = it }, label = { Text("Adresse / lieu") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(enabled = !busy && query.isNotBlank(), onClick = {
                    busy = true; status = null
                    scope.launch {
                        try { results = withContext(Dispatchers.IO) { OsmClient.search(query) }; if (results.isEmpty()) status = "Aucun résultat." }
                        catch (e: Exception) { status = "Recherche impossible (réseau ?) : ${e.message}. Utilisez des coordonnées ou la carte." }
                        finally { busy = false }
                    }
                }) { Text("Chercher") }
            }
            results.forEach { r ->
                Text(r.name, Modifier.fillMaxWidth().clickable { setOrigin(LatLon(r.lat, r.lon), "NOMINATIM ${r.osmRef ?: ""}".trim()); zoom = 18.0; if (name.isBlank()) name = r.name.substringBefore(","); results = emptyList() }.padding(6.dp), style = MaterialTheme.typography.bodySmall)
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
            Text("2. (Optionnel) Récupérer les objets OSM autour de l'origine (Overpass API)", style = MaterialTheme.typography.labelLarge)
            Row {
                OutlinedTextField(radius, { radius = it }, label = { Text("Rayon (m, ≤ 500)") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(enabled = !busy && origin != null, onClick = {
                    val r = radius.toDoubleOrNull()?.coerceIn(20.0, 500.0) ?: 150.0
                    busy = true; status = "Requête Overpass…"
                    scope.launch {
                        try {
                            osm = withContext(Dispatchers.IO) { OsmClient.fetchFeatures(origin!!, r, System.currentTimeMillis()) }
                            val counts = osm!!.features.groupingBy { it.category }.eachCount()
                            status = "OSM : ${osm!!.features.size} objets $counts. Touchez un bâtiment pour le sélectionner. Les contours OSM peuvent être absents ou imprécis."
                        } catch (e: Exception) { status = "Overpass indisponible : ${e.message}. Le site peut être créé sans données OSM." }
                        finally { busy = false }
                    }
                }) { Text("Récupérer") }
            }
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
                        osm = osm, selectedBuildingOsmRef = selected,
                    ))
                }) { Text(if (osm == null) "Créer sans données OSM" else "Créer le site") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    }
}

@Composable
fun SiteScreen(site: Site, sessions: List<SessionHeader>, onNewSession: () -> Unit, onResume: (SessionHeader) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize()) {
        OsmMap(Modifier.weight(0.6f).fillMaxSize(), site.origin, 18.0, MapContent(origin = site.origin, features = site.osm?.features.orEmpty(), selectedFeatureRef = site.selectedBuildingOsmRef), onTap = {})
        Column(Modifier.weight(0.4f).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("← Retour") }
                Spacer(Modifier.width(8.dp))
                Text(site.name, style = MaterialTheme.typography.titleLarge)
            }
            Text("Origine %.7f, %.7f (%s)".format(site.origin.lat, site.origin.lon, site.originSource), style = MaterialTheme.typography.bodySmall)
            Text(site.osm?.let { "OSM : ${it.features.size} objets, récupérés le ${fmtTime(it.retrievedUtcMs)} (${it.license})" } ?: "Pas de données OSM (site défini manuellement)", style = MaterialTheme.typography.bodySmall)
            Text("Plans importés : ${site.plans.size} (import prévu dans une tranche ultérieure)", style = MaterialTheme.typography.bodySmall)
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
