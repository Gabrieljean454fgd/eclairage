package app.lumisite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lumisite.android.data.Ids
import app.lumisite.android.data.DiagLog
import app.lumisite.android.ui.SettingsScreen
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.osmdroid.config.Configuration
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object NewSite : Screen
    data class SiteDetail(val siteId: String) : Screen
    data class Live(val controller: SessionController) : Screen
    data object Settings : Screen
}

@OptIn(ExperimentalComposeUiApi::class)
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
        DiagLog.init(applicationContext)
        app.lumisite.android.capture.ArDebug.fromIntent(applicationContext, intent, BuildConfig.DEBUG)
        val repo = Repository(applicationContext)
        setContent {
            LumiTheme {
                Surface(Modifier.semantics { testTagsAsResourceId = true }) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                    var homeKey by remember { mutableStateOf(0) }
                    LaunchedEffect(screen) {
                        DiagLog.screen(when (screen) { Screen.Home -> "Accueil"; Screen.NewSite -> "Accueil > Nouveau relevé"; is Screen.SiteDetail -> "Site"
                            is Screen.Live -> "Session"; Screen.Settings -> "Réglages" })
                    }
                    val scope = rememberCoroutineScope()
                    // The theme button has its own row: it never covers a screen title.
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    if (screen !is Screen.Live) androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().statusBarsPadding(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) { ThemeToggle(this@MainActivity, Modifier.padding(end = 4.dp)) }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val s = screen) {
                        Screen.Home -> androidx.compose.runtime.key(homeKey) { HomeScreen(repo, onOpen = { screen = Screen.SiteDetail(it.id) }, onNew = { screen = Screen.NewSite }, onSettings = { screen = Screen.Settings }) }
                        Screen.Settings -> SettingsScreen(repo, onBack = { screen = Screen.Home }, onReimported = { homeKey++ })
                        Screen.NewSite -> SiteEditorScreen(repo.recentZones(), stageOsmSource = { f ->
                            repo.stageOsmSource(f.snapshot.retrievedUtcMs, f.snapshot.endpoint, f.raw)
                        }, onSave = { site, staged ->
                            repo.saveSite(site)
                            staged?.let { scope.launch(Dispatchers.IO) { runCatching { repo.saveOsmSource(site.id, it) } } }
                            site.bbox?.let { repo.addRecentZone(RecentZone(site.name, it)) }
                            screen = Screen.SiteDetail(site.id)
                        }, onCancel = { screen = Screen.Home })
                        is Screen.SiteDetail -> {
                            val site = repo.site(s.siteId)
                            var surveys by remember(s.siteId) { mutableStateOf(emptyList<app.lumisite.core.compute.SurveyPositions.Resolved>()) }
                            LaunchedEffect(s.siteId) { surveys = site?.let { withContext(Dispatchers.IO) { repo.siteSurveys(it) } }.orEmpty() }
                            if (site == null) screen = Screen.Home else SiteScreen(
                                site, repo.sessions(site.id), surveys,
                                onNewSession = { screen = Screen.Live(SessionController(applicationContext, repo, site, Ids.new("ses"), resume = false)) },
                                onResume = { h -> screen = Screen.Live(SessionController(applicationContext, repo, site, h.id, resume = true)) },
                                onBack = { screen = Screen.Home },
                            )
                        }
                        is Screen.Live -> SessionScreen(this@MainActivity, repo, s.controller, onClosed = {
                            screen = Screen.SiteDetail(s.controller.site.id)
                        })
                    }
                    }
                    // On the live screen the toggle sits in the header, next to the camera preview (never drawn over it).
                    }
                } }
            }
        }
    }
}
