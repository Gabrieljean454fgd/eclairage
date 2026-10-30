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
import app.lumisite.android.data.Repository
import app.lumisite.android.ui.HomeScreen
import app.lumisite.android.ui.SessionScreen
import app.lumisite.android.ui.SiteEditorScreen
import app.lumisite.android.ui.SiteScreen
import org.osmdroid.config.Configuration
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val repo = Repository(applicationContext)
        setContent {
            MaterialTheme {
                Surface {
                    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                    when (val s = screen) {
                        Screen.Home -> HomeScreen(repo, onOpen = { screen = Screen.SiteDetail(it.id) }, onNew = { screen = Screen.NewSite })
                        Screen.NewSite -> SiteEditorScreen(onSave = { repo.saveSite(it); screen = Screen.SiteDetail(it.id) }, onCancel = { screen = Screen.Home })
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
                        is Screen.Live -> SessionScreen(this, repo, s.controller, onClosed = {
                            screen = Screen.SiteDetail(s.controller.site.id)
                        })
                    }
                }
            }
        }
    }
}
