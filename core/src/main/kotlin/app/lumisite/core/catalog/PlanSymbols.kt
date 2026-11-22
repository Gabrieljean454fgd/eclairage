package app.lumisite.core.catalog

/**
 * v176e: original plan (implantation) symbols, described as primitives in a unit box [-1, 1] (y down) so that the picker and
 * the map draw the same glyph at any size. Stroke colour = family colour; `fill = true` fills with the family colour,
 * otherwise the inside stays white. The flag ([FLAG]) is reserved for calibration marks: no catalog type uses it.
 */
object PlanSymbols {
    sealed interface Prim { val fill: Boolean }
    data class Circle(val cx: Float, val cy: Float, val r: Float, override val fill: Boolean = false) : Prim
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Prim { override val fill = false }
    data class Poly(val pts: List<Float>, override val fill: Boolean = false, val closed: Boolean = true) : Prim
    /** Arc of a circle, angles in degrees, 0 = +x, clockwise (y down). */
    data class Arc(val cx: Float, val cy: Float, val r: Float, val start: Float, val sweep: Float) : Prim { override val fill = false }

    /** Outline shared by a family (E4), drawn first and used to tell families apart. */
    enum class Frame { CIRCLE, RECT, CAMERA_BODY, TRIANGLE, SQUARE, HEXAGON, DIAMOND, OCTAGON, NONE }

    data class Symbol(val id: String, val frame: Frame, val prims: List<Prim>)

    const val FLAG = "DRAPEAU"

    private fun rect(l: Float, t: Float, r: Float, b: Float, fill: Boolean = false) = Poly(listOf(l, t, r, t, r, b, l, b), fill)
    private fun cross(r: Float, cx: Float = 0f, cy: Float = 0f): List<Prim> { val k = r * 0.7071f; return listOf(Line(cx - k, cy - k, cx + k, cy + k), Line(cx + k, cy - k, cx - k, cy + k)) }
    private fun polygon(n: Int, r: Float, rot: Double): List<Float> = (0 until n).flatMap { i -> val a = rot + 2 * Math.PI * i / n; listOf((r * Math.cos(a)).toFloat(), (r * Math.sin(a)).toFloat()) }

    fun frameOutline(f: Frame): List<Prim> = when (f) {
        Frame.CIRCLE -> listOf(Circle(0f, 0f, 0.8f))
        Frame.RECT -> listOf(rect(-0.9f, -0.6f, 0.9f, 0.6f))
        Frame.CAMERA_BODY -> listOf(rect(-0.95f, -0.45f, 0.4f, 0.45f), Poly(listOf(0.4f, -0.15f, 0.95f, -0.55f, 0.95f, 0.55f, 0.4f, 0.15f)))
        Frame.TRIANGLE -> listOf(Poly(listOf(0f, -0.85f, 0.9f, 0.75f, -0.9f, 0.75f)))
        Frame.SQUARE -> listOf(rect(-0.75f, -0.75f, 0.75f, 0.75f))
        Frame.HEXAGON -> listOf(Poly(polygon(6, 0.85f, 0.0)))
        Frame.DIAMOND -> listOf(Poly(listOf(0f, -0.9f, 0.9f, 0f, 0f, 0.9f, -0.9f, 0f)))
        Frame.OCTAGON -> listOf(Poly(polygon(8, 0.85f, Math.PI / 8)))
        Frame.NONE -> emptyList()
    }

    val all: Map<String, Symbol> = listOf(
        Symbol("ECL_POINT", Frame.CIRCLE, cross(0.8f)),
        Symbol("ECL_ENCASTRE", Frame.CIRCLE, cross(0.8f) + Circle(0f, 0f, 0.32f)),
        Symbol("ECL_SPOT", Frame.CIRCLE, cross(0.8f) + Circle(0f, 0f, 0.2f, fill = true)),
        Symbol("ECL_SUSPENSION", Frame.CIRCLE, cross(0.8f) + Line(0f, -0.8f, 0f, -1f) + Line(-0.3f, -1f, 0.3f, -1f)),
        Symbol("ECL_PATRIMOINE", Frame.CIRCLE, cross(0.8f) + listOf(Line(0f, -0.8f, 0f, -0.98f), Line(0.8f, 0f, 0.98f, 0f), Line(0f, 0.8f, 0f, 0.98f), Line(-0.8f, 0f, -0.98f, 0f))),
        Symbol("ECL_EXTERIEUR", Frame.NONE, listOf(Circle(0f, -0.3f, 0.55f)) + cross(0.55f, 0f, -0.3f) + Line(0f, 0.25f, 0f, 0.95f) + Line(-0.35f, 0.95f, 0.35f, 0.95f)),
        Symbol("ECL_PROJECTEUR", Frame.NONE, listOf(Circle(-0.35f, 0f, 0.5f)) + cross(0.5f, -0.35f, 0f) + Line(0.12f, -0.18f, 0.95f, -0.6f) + Line(0.12f, 0.18f, 0.95f, 0.6f)),
        Symbol("ECL_APPLIQUE", Frame.NONE, listOf(Line(-0.95f, 0.55f, 0.95f, 0.55f), Arc(0f, 0.55f, 0.45f, 180f, 180f)) + cross(0.3f, 0f, 0.35f)),
        Symbol("ECL_REGLETTE", Frame.NONE, listOf(rect(-0.95f, -0.3f, 0.95f, 0.3f), Line(-0.95f, -0.3f, 0.95f, 0.3f), Line(-0.95f, 0.3f, 0.95f, -0.3f))),
        Symbol("SEC_BAES", Frame.RECT, listOf(Circle(0f, 0f, 0.4f)) + cross(0.4f)),
        Symbol("SEC_SOURCE", Frame.RECT, listOf(Circle(-0.2f, 0f, 0.38f)) + cross(0.38f, -0.2f, 0f) + rect(0.45f, -0.35f, 0.8f, 0f, fill = true)),
        Symbol("SEC_ALIM", Frame.RECT, listOf(Poly(listOf(0.1f, -0.5f, -0.25f, 0.05f, 0.05f, 0.05f, -0.1f, 0.5f, 0.3f, -0.1f, 0f, -0.1f), fill = true))),
        Symbol("ELEC_DISTRIB", Frame.TRIANGLE, listOf(Circle(0f, 0.25f, 0.18f, fill = true))),
        Symbol("ELEC_DETECTEUR", Frame.TRIANGLE, listOf(Arc(0f, 0.45f, 0.35f, 200f, 140f), Circle(0f, 0.45f, 0.1f, fill = true))),
        Symbol("ELEC_PRISE", Frame.NONE, listOf(Arc(0f, 0.2f, 0.65f, 180f, 180f), Line(-0.9f, 0.2f, 0.9f, 0.2f), Line(0f, 0.2f, 0f, 0.9f))),
        Symbol("ELEC_INTER", Frame.NONE, listOf(Circle(-0.3f, 0.35f, 0.25f, fill = true), Line(-0.12f, 0.17f, 0.55f, -0.6f), Line(0.55f, -0.6f, 0.85f, -0.35f))),
        Symbol("ELEC_TABLEAU", Frame.NONE, listOf(rect(-0.9f, -0.5f, 0.9f, 0.5f), Poly(listOf(-0.9f, 0.5f, 0.9f, -0.5f, 0.9f, 0.5f), fill = true))),
        Symbol("CAM_CAMERA", Frame.CAMERA_BODY, listOf(Circle(-0.3f, 0f, 0.22f, fill = true))),
        Symbol("CAM_ENREG", Frame.CAMERA_BODY, listOf(Line(-0.75f, -0.15f, 0.2f, -0.15f), Line(-0.75f, 0.15f, 0.2f, 0.15f))),
        Symbol("CAM_ACCES", Frame.CAMERA_BODY, listOf(rect(-0.75f, -0.25f, -0.25f, 0.25f), Circle(0.05f, 0f, 0.15f, fill = true))),
        Symbol("INC_DETECTEUR", Frame.SQUARE, listOf(Circle(0f, 0f, 0.4f))),
        Symbol("INC_CENTRALE", Frame.SQUARE, listOf(rect(-0.75f, -0.75f, 0.75f, -0.15f, fill = true))),
        Symbol("INC_ALARME", Frame.SQUARE, listOf(Line(0f, -0.45f, 0f, 0.15f), Circle(0f, 0.42f, 0.08f, fill = true))),
        Symbol("INC_DM", Frame.SQUARE, listOf(Circle(0f, 0f, 0.38f, fill = true))),
        Symbol("INC_EXTINCTEUR", Frame.SQUARE, listOf(Poly(listOf(0f, -0.5f, 0.45f, 0.4f, -0.45f, 0.4f), fill = true))),
        Symbol("SECU_ECLAIRAGE", Frame.HEXAGON, listOf(Circle(0f, 0f, 0.38f)) + cross(0.38f)),
        Symbol("SECU_SIGNAL", Frame.HEXAGON, listOf(Line(-0.5f, 0f, 0.5f, 0f), Line(0.15f, -0.35f, 0.5f, 0f), Line(0.15f, 0.35f, 0.5f, 0f))),
        Symbol("SON_DIFFUSION", Frame.DIAMOND, listOf(Arc(-0.25f, 0f, 0.35f, -45f, 90f), Arc(-0.25f, 0f, 0.6f, -40f, 80f))),
        Symbol("SON_AMPLI", Frame.DIAMOND, listOf(Poly(listOf(-0.35f, -0.35f, 0.4f, 0f, -0.35f, 0.35f)))),
        Symbol("AUTRE", Frame.OCTAGON, listOf(Line(-0.4f, 0f, 0.4f, 0f), Line(0f, -0.4f, 0f, 0.4f))),
        Symbol(FLAG, Frame.NONE, listOf(Line(-0.6f, -0.95f, -0.6f, 0.95f), Poly(listOf(-0.6f, -0.95f, 0.9f, -0.5f, -0.6f, -0.05f), fill = true))),
    ).associateBy { it.id }

    /** Primitives to draw (frame first), or a plain circle with a cross for an unknown id. */
    fun prims(id: String?): List<Prim> = (all[id] ?: all.getValue("ECL_POINT")).let { frameOutline(it.frame) + it.prims }
}
