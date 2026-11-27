package app.lumisite.core.compute

import app.lumisite.core.catalog.LsCatalog
import app.lumisite.core.model.BBox
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.Site
import app.lumisite.core.session.ControlPointPolicy
import app.lumisite.core.survey.Zones
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** Ancres AR (drapeaux, luminaires) et contours du lieu, tous recalés par la calibration, toutes visites confondues. */
object ArSiteOverlays {
    enum class Kind { FLAG, LUMINAIRE }

    data class SiteContours(val rings: List<List<V3>> = emptyList(), val corners: List<Pair<V3, V3>> = emptyList())

    data class VisitEvents(val visitId: String, val events: List<SessionEvent>)

    data class Anchor(
        val id: String,
        val visitId: String,
        val kind: Kind,
        val session: V3,
        val lsCode: String? = null,
        val symbolId: String? = null,
        val shortLabel: String? = null,
    )

    /**
     * Drapeaux des repères de calibrage et luminaires de toutes les visites, dans le repère de session courant.
     * Les drapeaux sont plantés au niveau du sol [floorY]. Les relevés supprimés ou remplacés sont ignorés.
     */
    fun anchors(visits: List<VisitEvents>, transform: Similarity2D, floorY: Double, catalog: LsCatalog): List<Anchor> {
        val out = ArrayList<Anchor>()
        for (v in visits) {
            ControlPointPolicy.active(v.events).forEach { e: ControlPointEvent ->
                val at = Aim.toSession(e.observation.reference.site, transform).copy(y = floorY)
                out += Anchor(e.id, v.visitId, Kind.FLAG, at)
            }
            SurveyPositions.resolveAll(v.events).forEach { r ->
                val site = r.site ?: return@forEach
                val ev = r.event
                val ls = ev.payload?.get("ls_code")?.jsonPrimitive?.contentOrNull
                val label = ev.payload?.get("ls_label")?.jsonPrimitive?.contentOrNull
                val legacy = ev.payload?.get("block_name")?.jsonPrimitive?.contentOrNull ?: ev.dxf?.blockName
                out += Anchor(
                    id = ev.id, visitId = v.visitId, kind = Kind.LUMINAIRE,
                    session = Aim.toSession(site, transform),
                    lsCode = ls,
                    symbolId = ArSymbols.symbolId(catalog, ls, legacy),
                    shortLabel = ArSymbols.shortLabel(catalog, ls, legacy, label),
                )
            }
        }
        return out
    }

    /**
     * Contours du lieu dans le repère de session, à [floorY] : bâtiment OSM sélectionné, rectangles du site,
     * et zones du relevé (toutes visites). Chaque polyligne est fermée (premier point répété à la fin si besoin).
     */
    fun siteContours(
        site: Site, transform: Similarity2D, floorY: Double, ltp: LocalTangentPlane,
        visits: List<VisitEvents> = emptyList(),
    ): SiteContours {
        val rings = ArrayList<List<V3>>()
        val corners = ArrayList<Pair<V3, V3>>()
        val selected = buildSet {
            site.selectedBuildingOsmRef?.let { add(it) }
            addAll(site.selectedBuildingOsmRefs)
        }
        if (selected.isNotEmpty()) site.osm?.features.orEmpty().filter { it.ref in selected }.forEach { f ->
            f.outers.ifEmpty { listOf(f.geometry) }.filter { it.size >= 3 }.forEach { outer ->
                val building = ring(outer, ltp, transform, floorY)
                rings += building
                corners += buildingCorners(building)
            }
            f.holes.forEach { if (it.size >= 3) rings += ring(it, ltp, transform, floorY) }
        }
        val rects = site.rects.ifEmpty { listOfNotNull(site.bbox) }
        rects.forEach { rings += box(it, ltp, transform, floorY) }
        for (v in visits) {
            Zones.current(v.events).forEach { z ->
                if (z.points.size >= 3) rings += enuRing(z.points, transform, floorY)
                z.holes.orEmpty().forEach { if (it.size >= 3) rings += enuRing(it, transform, floorY) }
            }
        }
        return SiteContours(rings.filter { it.size >= 2 }, corners)
    }

    /** Petits traits verticaux aux angles de l'emprise. Leur hauteur est graphique. */
    private fun buildingCorners(ring: List<V3>): List<Pair<V3, V3>> {
        val vertices = ring.dropLast(1)
        if (vertices.size < 3) return emptyList()
        return vertices.mapIndexedNotNull { i, p ->
            val incoming = p - vertices[(i + vertices.lastIndex) % vertices.size]
            val outgoing = vertices[(i + 1) % vertices.size] - p
            if (incoming.norm() < 0.05 || outgoing.norm() < 0.05 || (incoming.unit() dot outgoing.unit()) > 0.966) null
            else p to p.copy(y = p.y + 0.8)
        }
    }

    private fun ring(latLon: List<app.lumisite.core.geo.LatLon>, ltp: LocalTangentPlane, tr: Similarity2D, floorY: Double): List<V3> {
        val pts = latLon.map { ll ->
            val enu = ltp.toEnu(ll)
            Aim.toSession(enu.copy(u = 0.0), tr).copy(y = floorY)
        }
        return closed(pts)
    }

    private fun enuRing(points: List<app.lumisite.core.geo.Enu>, tr: Similarity2D, floorY: Double): List<V3> =
        closed(points.map { Aim.toSession(it.copy(u = 0.0), tr).copy(y = floorY) })

    private fun box(b: BBox, ltp: LocalTangentPlane, tr: Similarity2D, floorY: Double): List<V3> = ring(
        listOf(LatLon(b.south, b.west), LatLon(b.south, b.east), LatLon(b.north, b.east), LatLon(b.north, b.west)),
        ltp, tr, floorY,
    )

    private fun closed(pts: List<V3>): List<V3> =
        if (pts.size >= 2 && pts.first().distanceTo(pts.last()) > 1e-6) pts + pts.first() else pts
}

/** Mesure A–B : une vraie ligne 3D entre les deux ancres, plus un libellé au milieu. */
object ArMeasure {
    fun line(a: V3, b: V3, tickM: Double = 0.05): List<Pair<V3, V3>> {
        val ab = b - a
        val len = ab.norm()
        if (len < 1e-9) return emptyList()
        val dir = ab.unit()
        var tick = V3(0.0, 1.0, 0.0) cross dir
        if (tick.norm() < 1e-6) tick = V3(1.0, 0.0, 0.0) else tick = tick.unit()
        tick *= tickM
        return listOf(a to b, a + tick to a - tick, b + tick to b - tick)
    }

    fun label(a: V3, b: V3, liftM: Double = 0.08): Pair<V3, String> {
        val mid = (a + b) * 0.5 + V3(0.0, liftM, 0.0)
        return mid to String.format(Locale.FRANCE, "%.2f m", a.distanceTo(b))
    }
}
