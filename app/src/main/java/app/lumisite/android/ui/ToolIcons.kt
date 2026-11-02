package app.lumisite.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outline icons for Session > Annoter (stage 2.4), drawn here so no extra icon library is needed. */
object ToolIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
    }.build()

    /** Four arrows: move the map. */
    val Move = icon("move") {
        moveTo(12f, 3f); lineTo(12f, 21f); moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(9f, 6f); lineTo(12f, 3f); lineTo(15f, 6f); moveTo(9f, 18f); lineTo(12f, 21f); lineTo(15f, 18f)
        moveTo(6f, 9f); lineTo(3f, 12f); lineTo(6f, 15f); moveTo(18f, 9f); lineTo(21f, 12f); lineTo(18f, 15f)
    }
    val Rectangle = icon("rectangle") { moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 18f); lineTo(4f, 18f); close() }
    /** Polyline with its vertices. */
    val Line = icon("line") {
        moveTo(3f, 19f); lineTo(10f, 9f); lineTo(15f, 14f); lineTo(21f, 5f)
        moveTo(3f, 18.5f); lineTo(3f, 19.5f); moveTo(10f, 8.5f); lineTo(10f, 9.5f); moveTo(15f, 13.5f); lineTo(15f, 14.5f)
    }
    val Polygon = icon("polygon") { moveTo(12f, 3f); lineTo(21f, 9.5f); lineTo(17.5f, 20f); lineTo(6.5f, 20f); lineTo(3f, 9.5f); close() }
    /** Speech bubble. */
    val Comment = icon("comment") { moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 16f); lineTo(10f, 16f); lineTo(6f, 20f); lineTo(6f, 16f); lineTo(4f, 16f); close() }
    /** Pointer arrow. */
    val Select = icon("select") { moveTo(5f, 3f); lineTo(5f, 19f); lineTo(9.5f, 14.5f); lineTo(13f, 21f); lineTo(15.5f, 20f); lineTo(12f, 13.5f); lineTo(18f, 13.5f); close() }
}
