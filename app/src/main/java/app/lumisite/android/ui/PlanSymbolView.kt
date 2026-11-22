package app.lumisite.android.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import app.lumisite.core.catalog.PlanSymbols

/**
 * v176e: renders a [PlanSymbols] glyph. The picker (Compose) and the map (osmdroid marker bitmap) both call this function,
 * so the glyph is identical. White background disc for contrast on the map, family colour for strokes/fills.
 * Selection = white halo + blue outline around the same glyph; shape and colour are unchanged.
 */
fun drawPlanSymbol(cv: Canvas, id: String?, color: Int, cx: Float, cy: Float, half: Float, density: Float, selected: Boolean = false) {
    val prims = PlanSymbols.prims(id)
    val stroke = (half * 0.11f).coerceAtLeast(1.5f * density)
    fun x(v: Float) = cx + v * half
    fun y(v: Float) = cy + v * half
    fun path(p: PlanSymbols.Prim): Path = Path().apply {
        when (p) {
            is PlanSymbols.Circle -> addCircle(x(p.cx), y(p.cy), p.r * half, Path.Direction.CW)
            is PlanSymbols.Line -> { moveTo(x(p.x1), y(p.y1)); lineTo(x(p.x2), y(p.y2)) }
            is PlanSymbols.Poly -> { moveTo(x(p.pts[0]), y(p.pts[1])); var i = 2; while (i < p.pts.size) { lineTo(x(p.pts[i]), y(p.pts[i + 1])); i += 2 }; if (p.closed) close() }
            is PlanSymbols.Arc -> addArc(RectF(x(p.cx) - p.r * half, y(p.cy) - p.r * half, x(p.cx) + p.r * half, y(p.cy) + p.r * half), p.start, p.sweep)
        }
    }
    val paths = prims.map { it to path(it) }
    if (selected) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = stroke + 8f * density; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(0, 90, 220); style = Paint.Style.STROKE; strokeWidth = stroke + 4f * density; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        paths.forEach { cv.drawPath(it.second, halo) }
        paths.forEach { cv.drawPath(it.second, blue) }
    }
    val fillWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.FILL }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    paths.forEach { (p, path) -> if (p !is PlanSymbols.Line && p !is PlanSymbols.Arc) cv.drawPath(path, if (p.fill) fill else fillWhite) }
    paths.forEach { cv.drawPath(it.second, ink) }
}

/** Same glyph in Compose (picker), [sizeDp] between 24 and 48 dp. */
@Composable
fun PlanSymbol(id: String?, color: Int, sizeDp: Int = 36, modifier: Modifier = Modifier, selected: Boolean = false) {
    ComposeCanvas(modifier.size(sizeDp.dp)) {
        drawIntoCanvas { drawPlanSymbol(it.nativeCanvas, id, color, size.width / 2, size.height / 2, size.minDimension / 2 * 0.86f, density, selected) }
    }
}
