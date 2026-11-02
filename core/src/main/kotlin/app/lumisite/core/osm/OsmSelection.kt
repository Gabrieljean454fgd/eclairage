package app.lumisite.core.osm

import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature

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

    /** A tap adds the building if absent, removes it otherwise. */
    fun toggle(selected: List<String>, ref: String): List<String> = if (ref in selected) selected - ref else selected + ref
}
