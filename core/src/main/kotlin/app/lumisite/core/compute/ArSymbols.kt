package app.lumisite.core.compute

import app.lumisite.core.catalog.LsCatalog
import app.lumisite.core.catalog.LsItem
import app.lumisite.core.catalog.LsPrim
import app.lumisite.core.catalog.LsSymbols
import app.lumisite.core.catalog.LsText
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Glyphes LS_* en segments 3D (billboard vertical) pour la surimpression AR.
 * Le carré unitaire du catalogue (x droite, y bas) est posé dans le plan (right, up), centré en [at].
 */
object ArSymbols {
    const val SIZE_M = 0.22
    private const val CIRCLE_SEGMENTS = 24

    /** Axes d'un billboard vertical face à [forward] (axe caméra, y monde vers le haut). */
    fun billboardAxes(forward: V3): Pair<V3, V3> {
        val up = V3(0.0, 1.0, 0.0)
        var right = up cross forward
        if (right.norm() < 1e-6) right = V3(1.0, 0.0, 0.0) else right = right.unit()
        return right to up
    }

    fun itemOf(catalog: LsCatalog, lsCode: String?, legacyName: String? = null): LsItem? {
        val id = lsCode?.takeIf { it.isNotBlank() } ?: legacyName?.takeIf { it.isNotBlank() } ?: return null
        return catalog.byCodeOrLegacy(id)
    }

    fun symbolId(catalog: LsCatalog, lsCode: String?, legacyName: String? = null): String =
        LsSymbols.symbolOf(itemOf(catalog, lsCode, legacyName))

    fun shortLabel(catalog: LsCatalog, lsCode: String?, legacyName: String? = null, payloadLabel: String? = null): String {
        val item = itemOf(catalog, lsCode, legacyName)
        val raw = item?.label ?: payloadLabel?.takeIf { it.isNotBlank() } ?: lsCode ?: legacyName ?: ""
        return if (item != null) LsText.shortLabel(item.label, item.famille) else if (raw.isNotEmpty()) LsText.shortLabel(raw) else ""
    }

    fun segmentsForCode(
        catalog: LsCatalog, lsCode: String?, at: V3, right: V3, up: V3,
        sizeM: Double = SIZE_M, legacyName: String? = null,
    ): List<Pair<V3, V3>> = segments(LsSymbols.glyph(symbolId(catalog, lsCode, legacyName)), at, right, up, sizeM)

    fun segments(prims: List<LsPrim>, at: V3, right: V3, up: V3, sizeM: Double = SIZE_M): List<Pair<V3, V3>> {
        val out = ArrayList<Pair<V3, V3>>()
        fun pt(x: Double, y: Double) = at + right * ((x - 0.5) * sizeM) + up * ((0.5 - y) * sizeM)
        fun ring(cx: Double, cy: Double, r: Double, startDeg: Double, sweepDeg: Double, n: Int, close: Boolean) {
            if (r <= 0.0) return
            val steps = max(2, (n * kotlin.math.abs(sweepDeg) / 360.0).toInt().coerceAtLeast(3))
            val pts = (0..steps).map { i ->
                val a = Math.toRadians(startDeg + sweepDeg * i / steps)
                pt(cx + r * cos(a), cy + r * sin(a))
            }
            for (i in 0 until pts.lastIndex) out += pts[i] to pts[i + 1]
            if (close && pts.size >= 2) out += pts.last() to pts.first()
        }
        for (p in prims) when (p) {
            is LsPrim.Line -> out += pt(p.x1.toDouble(), p.y1.toDouble()) to pt(p.x2.toDouble(), p.y2.toDouble())
            is LsPrim.Circle -> ring(p.cx.toDouble(), p.cy.toDouble(), p.r.toDouble(), 0.0, 360.0, CIRCLE_SEGMENTS, close = true)
            is LsPrim.Arc -> ring(p.cx.toDouble(), p.cy.toDouble(), p.r.toDouble(), p.startDeg.toDouble(), p.sweepDeg.toDouble(), CIRCLE_SEGMENTS, close = false)
            is LsPrim.Rect -> {
                val a = pt(p.left.toDouble(), p.top.toDouble())
                val b = pt(p.right.toDouble(), p.top.toDouble())
                val c = pt(p.right.toDouble(), p.bottom.toDouble())
                val d = pt(p.left.toDouble(), p.bottom.toDouble())
                out += listOf(a to b, b to c, c to d, d to a)
            }
            is LsPrim.Poly -> {
                val pts = (0 until p.pts.size / 2).map { i -> pt(p.pts[2 * i].toDouble(), p.pts[2 * i + 1].toDouble()) }
                if (pts.size >= 2) {
                    for (i in 0 until pts.lastIndex) out += pts[i] to pts[i + 1]
                    if (p.close) out += pts.last() to pts.first()
                }
            }
        }
        return out
    }
}
