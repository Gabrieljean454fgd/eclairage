package app.lumisite.android.ui

import android.content.Context
import androidx.compose.ui.platform.testTag
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp

/** Theme choice (O11, 30/10): Jour → Nuit → Nuit rouge (expérimental, still under field test). */
enum class ThemeMode(val label: String, val glyph: String) {
    DAY("Thème : jour", "☀"), NIGHT("Thème : nuit", "☾"), NIGHT_RED("Thème : nuit rouge (expérimental)", "●");
    fun next() = entries[(ordinal + 1) % entries.size]
}

/** Map rendering derived from the theme; the camera preview is never filtered. */
enum class MapTone { NORMAL, DARK, RED }

val LocalMapTone = staticCompositionLocalOf { MapTone.NORMAL }

/** Stored in SharedPreferences, read synchronously before the first frame (no white flash). */
object ThemePrefs {
    private const val FILE = "lumisite_theme"
    var mode by mutableStateOf(ThemeMode.DAY)
    /** No choice saved yet (or the former "Système"): start from the system setting. Former Clair/Sombre map to Jour/Nuit. */
    fun load(ctx: Context) {
        val systemDark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        mode = when (val v = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("mode", null)) {
            "LIGHT" -> ThemeMode.DAY
            "DARK" -> ThemeMode.NIGHT
            else -> ThemeMode.entries.firstOrNull { it.name == v } ?: if (systemDark) ThemeMode.NIGHT else ThemeMode.DAY
        }
    }
    fun cycle(ctx: Context) {
        mode = mode.next()
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }
    @Suppress("UNUSED_PARAMETER")
    fun isDark(systemDark: Boolean) = mode != ThemeMode.DAY
}

private val Light = lightColorScheme()
private val Dark = darkColorScheme()
private val NightRed = darkColorScheme(
    primary = Color(0xFFFF5252), onPrimary = Color.Black, secondary = Color(0xFFD32F2F), onSecondary = Color.Black,
    background = Color.Black, onBackground = Color(0xFFFF6E6E), surface = Color.Black, onSurface = Color(0xFFFF6E6E),
    surfaceVariant = Color(0xFF1A0000), onSurfaceVariant = Color(0xFFE57373), outline = Color(0xFF8B0000),
    primaryContainer = Color(0xFF3A0000), onPrimaryContainer = Color(0xFFFF8A80), error = Color(0xFFFF8A80),
)

@Composable
fun LumiTheme(content: @Composable () -> Unit) {
    val dark = ThemePrefs.isDark(isSystemInDarkTheme())
    val scheme = when { ThemePrefs.mode == ThemeMode.NIGHT_RED -> NightRed; dark -> Dark; else -> Light }
    val tone = when { ThemePrefs.mode == ThemeMode.NIGHT_RED -> MapTone.RED; dark -> MapTone.DARK; else -> MapTone.NORMAL }
    CompositionLocalProvider(LocalMapTone provides tone) { MaterialTheme(colorScheme = scheme, content = content) }
}

fun themeBackground(systemDark: Boolean): Int = when {
    ThemePrefs.mode == ThemeMode.NIGHT_RED -> android.graphics.Color.BLACK
    ThemePrefs.isDark(systemDark) -> 0xFF1C1B1F.toInt()
    else -> android.graphics.Color.WHITE
}

/** Sun/moon button: one tap switches immediately; state (session, zoom) is kept because only the colour scheme changes. */
@Composable
fun ThemeToggle(ctx: Context, modifier: Modifier = Modifier) {
    IconButton(onClick = { ThemePrefs.cycle(ctx) }, modifier = Modifier.testTag("theme_iconbutton").then(modifier.semantics { contentDescription = ThemePrefs.mode.label })) {
        Text(ThemePrefs.mode.glyph, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Tile filters. Dark: brightness −25 %, saturation −60 % (starting point to tune on site; no INVERT_COLORS, osmdroid #1804). */
object MapFilters {
    val dark = ColorMatrixColorFilter(ColorMatrix().apply {
        setSaturation(0.4f)
        postConcat(ColorMatrix(floatArrayOf(0.75f, 0f, 0f, 0f, 0f, 0f, 0.75f, 0f, 0f, 0f, 0f, 0f, 0.75f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    })
    val red = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0.24f, 0.47f, 0.09f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
    )))
    fun of(t: MapTone) = when (t) { MapTone.NORMAL -> null; MapTone.DARK -> dark; MapTone.RED -> red }
}
