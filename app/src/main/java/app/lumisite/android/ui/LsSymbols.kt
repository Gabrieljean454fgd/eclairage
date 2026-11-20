package app.lumisite.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lumisite.core.catalog.LsPrim
import app.lumisite.core.catalog.LsSymbols
import org.osmdroid.views.MapView

/**
 * Rendu des symboles LS_* (description pure : app.lumisite.core.catalog.LsSymbols, carré unitaire [0,1]²).
 * - [LsSymbol] : Compose Canvas, pour le sélecteur de famille/type (24-48 dp).
 * - [lsSymbolDrawable] : bitmap osmdroid, pour les relevés posés sur la carte.
 */

/** Dessine les primitives dans le carré courant du DrawScope (taille = côté du carré). */
fun DrawScope.drawLsGlyph(symbolId: String?, color: Color, strokePx: Float = size.minDimension * 0.07f) {
    val s = size.minDimension
    fun X(v: Float) = v * s
    val stroke = Stroke(width = strokePx)
    for (p in LsSymbols.glyph(symbolId)) when (p) {
        is LsPrim.Line -> drawLine(color, Offset(X(p.x1), X(p.y1)), Offset(X(p.x2), X(p.y2)), strokeWidth = strokePx)
        is LsPrim.Circle -> if (p.fill) drawCircle(color, X(p.r), Offset(X(p.cx), X(p.cy)))
            else drawCircle(color, X(p.r), Offset(X(p.cx), X(p.cy)), style = stroke)
        is LsPrim.Arc -> drawArc(color, p.startDeg, p.sweepDeg, false,
            topLeft = Offset(X(p.cx - p.r), X(p.cy - p.r)), size = androidx.compose.ui.geometry.Size(X(p.r) * 2, X(p.r) * 2), style = stroke)
        is LsPrim.Rect -> {
            val tl = Offset(X(p.left), X(p.top)); val sz = androidx.compose.ui.geometry.Size(X(p.right - p.left), X(p.bottom - p.top))
            if (p.fill) drawRect(color, tl, sz) else drawRect(color, tl, sz, style = stroke)
        }
        is LsPrim.Poly -> {
            val path = Path().apply {
                for (i in p.pts.indices step 2) {
                    val x = X(p.pts[i]); val y = X(p.pts[i + 1])
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                if (p.close) close()
            }
            drawPath(path, color, style = if (p.fill) Fill else stroke)
        }
    }
}

/** Symbole LS_* en Compose : fond blanc discret + glyphe couleur de famille. */
@Composable
fun LsSymbol(symbolId: String?, color: Color, sizeDp: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(sizeDp)) {
        drawCircle(Color.White, size.minDimension * 0.48f)
        drawLsGlyph(symbolId, color)
    }
}

private fun pathOf(p: LsPrim.Poly, s: Float): android.graphics.Path = android.graphics.Path().apply {
    for (i in p.pts.indices step 2) {
        val x = p.pts[i] * s; val y = p.pts[i + 1] * s
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    if (p.close) close()
}

/** Bitmap carré du symbole pour un Marker osmdroid (remplace les pastilles [dot]). */
fun lsSymbolDrawable(mv: MapView, symbolId: String?, color: Int, sizeDp: Int = 32): android.graphics.drawable.Drawable {
    val d = mv.resources.displayMetrics.density
    val px = (sizeDp * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val s = px.toFloat()
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = s * 0.07f
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = android.graphics.Paint.Style.FILL }
    val halo = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL }
    cv.drawCircle(s / 2, s / 2, s * 0.48f, halo)
    val rect = android.graphics.RectF()
    for (p in LsSymbols.glyph(symbolId)) when (p) {
        is LsPrim.Line -> cv.drawLine(p.x1 * s, p.y1 * s, p.x2 * s, p.y2 * s, stroke)
        is LsPrim.Circle -> cv.drawCircle(p.cx * s, p.cy * s, p.r * s, if (p.fill) fill else stroke)
        is LsPrim.Arc -> {
            rect.set((p.cx - p.r) * s, (p.cy - p.r) * s, (p.cx + p.r) * s, (p.cy + p.r) * s)
            cv.drawArc(rect, p.startDeg, p.sweepDeg, false, stroke)
        }
        is LsPrim.Rect -> {
            rect.set(p.left * s, p.top * s, p.right * s, p.bottom * s)
            cv.drawRect(rect, if (p.fill) fill else stroke)
        }
        is LsPrim.Poly -> cv.drawPath(pathOf(p, s), if (p.fill) fill else stroke)
    }
    return android.graphics.drawable.BitmapDrawable(mv.resources, bmp)
}
