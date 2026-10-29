package app.lumisite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lumisite.android.data.Ids
import app.lumisite.android.data.Repository
import app.lumisite.android.ui.HomeScreen
import app.lumisite.android.ui.SessionScreen
import app.lumisite.android.ui.SiteEditorScreen
import app.lumisite.android.data.RecentZone
import app.lumisite.android.ui.SiteScreen
import app.lumisite.android.ui.MapPrefs
import app.lumisite.android.ui.LumiTheme
import app.lumisite.android.ui.ThemePrefs
import app.lumisite.android.ui.ThemeToggle
import app.lumisite.android.ui.themeBackground
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.osmdroid.config.Configuration
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object NewSite : Screen
    data class SiteDetail(val siteId: String) : Screen
    data class Live(val controller: SessionController) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = "LumiSite/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid-tiles")
        }
        MapPrefs.load(applicationContext)
        ThemePrefs.load(applicationContext)
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(themeBackground(systemDark)))
        val repo = Repository(applicationContext)
        setContent {
            LumiTheme {
                Surface {
                    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                    Box(Modifier.fillMaxSize()) {
                    when (val s = screen) {
                        Screen.Home -> HomeScreen(repo, onOpen = { screen = Screen.SiteDetail(it.id) }, onNew = { screen = Screen.NewSite })
                        Screen.NewSite -> SiteEditorScreen(repo.recentZones(), onSave = { site, fetched ->
                            repo.saveSite(site)
                            fetched?.let { runCatching { repo.saveOsmSource(site.id, it.snapshot.retrievedUtcMs, it.snapshot.endpoint, it.raw) } }
                            site.bbox?.let { repo.addRecentZone(RecentZone(site.name, it)) }
                            screen = Screen.SiteDetail(site.id)
                        }, onCancel = { screen = Screen.Home })
                        is Screen.SiteDetail -> {
                            val site = repo.site(s.siteId)
                            if (site == null) screen = Screen.Home else SiteScreen(
                                site, repo.sessions(site.id), repo.siteSurveys(site),
                                onNewSession = { screen = Screen.Live(SessionController(applicationContext, repo, site, Ids.new("ses"), resume = false)) },
                                onResume = { h -> screen = Screen.Live(SessionController(applicationContext, repo, site, h.id, resume = true)) },
                                onBack = { screen = Screen.Home },
                            )
                        }
                        is Screen.Live -> SessionScreen(this@MainActivity, repo, s.controller, onClosed = {
                            screen = Screen.SiteDetail(s.controller.site.id)
                        })
                    }
                    // On the live screen the toggle sits in the header, next to the camera preview (never drawn over it).
                    if (screen !is Screen.Live) ThemeToggle(this@MainActivity, Modifier.align(Alignment.TopEnd).statusBarsPadding())
                    }
                }
            }
        }
    }
}
