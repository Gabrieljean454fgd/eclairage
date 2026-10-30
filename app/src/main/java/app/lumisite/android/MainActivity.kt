package app.lumisite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lumisite.android.data.Ids
import app.lumisite.android.data.Repository
import app.lumisite.android.data.VisibleStorage
import app.lumisite.android.ui.SettingsScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.lumisite.android.ui.HomeScreen
import app.lumisite.android.ui.SessionScreen
import app.lumisite.android.ui.SiteEditorScreen
import app.lumisite.android.ui.SiteScreen
import org.osmdroid.config.Configuration
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object NewSite : Screen
    data class SiteDetail(val siteId: String) : Screen
    data class Live(val controller: SessionController) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var storage: VisibleStorage

    override fun onStop() {
        super.onStop()
        if (::storage.isInitialized) Thread { storage.sync(filesDir) }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = "LumiSite/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid-tiles")
        }
        val repo = Repository(applicationContext)
        storage = VisibleStorage(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) { storage.sync(filesDir); delay(5_000) }
        }
        setContent {
            MaterialTheme {
                Surface {
                    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                    val restored by storage.restored.collectAsState()
                    when (val s = screen) {
                        Screen.Home -> HomeScreen(repo, refreshKey = restored, onOpen = { screen = Screen.SiteDetail(it.id) }, onNew = { screen = Screen.NewSite },
                            storageWarning = storage.treeUri == null, onSettings = { screen = Screen.Settings })
                        Screen.Settings -> SettingsScreen(storage, onBack = { screen = Screen.Home })
                        Screen.NewSite -> SiteEditorScreen(onSave = { repo.saveSite(it); screen = Screen.SiteDetail(it.id) }, onCancel = { screen = Screen.Home })
                        is Screen.SiteDetail -> {
                            val site = repo.site(s.siteId)
                            if (site == null) screen = Screen.Home else SiteScreen(
                                site, repo.sessions(site.id),
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
