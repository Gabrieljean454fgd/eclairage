package app.lumisite.core.osm

import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import app.lumisite.core.model.Site

/** Accueil > Nouveau relevé > Sélection OSM (stage 2.7): the drawn rectangle already selects the buildings it covers. */
object OsmSelection {
    /** Buildings whose outline centroid lies inside the rectangle, in feature order. */
    fun buildingsIn(rect: BBox, features: List<OsmFeature>): List<String> = features.filter { f ->
        f.category == "building" && f.geometry.isNotEmpty() && run {
            val lat = f.geometry.sumOf { it.lat } / f.geometry.size
            val lon = f.geometry.sumOf { it.lon } / f.geometry.size
            lat in rect.south..rect.north && lon in rect.west..rect.east
        }
    }.map { it.ref }

    /** v176a-bis: buildings selected by any of the site rectangles (their union), in feature order, without duplicates. */
    fun buildingsIn(rects: List<BBox>, features: List<OsmFeature>): List<String> =
        rects.flatMap { buildingsIn(it, features) }.toSet().let { s -> features.map { it.ref }.filter { it in s }.distinct() }

    /** Emprise d'ouverture de la carte : bâtiments choisis, sinon zone définie pour le site. */
    fun workBounds(site: Site): BBox? {
        val selected = site.selectedBuildingOsmRefs.toSet() + listOfNotNull(site.selectedBuildingOsmRef)
        val points = site.osm?.features.orEmpty().filter { it.ref in selected }.flatMap { f ->
            f.outers.ifEmpty { listOf(f.geometry) }.filter { it.size >= 3 }.flatten()
        }
        if (points.isNotEmpty()) {
            val box = BBox(points.minOf { it.lat }, points.minOf { it.lon }, points.maxOf { it.lat }, points.maxOf { it.lon })
            if (box.south < box.north && box.west < box.east) return box
        }
        return SiteRects.envelope(site.rects) ?: site.bbox ?: site.osm?.bbox
    }

    /** A tap adds the building if absent, removes it otherwise. */
    fun toggle(selected: List<String>, ref: String): List<String> = if (ref in selected) selected - ref else selected + ref
}
