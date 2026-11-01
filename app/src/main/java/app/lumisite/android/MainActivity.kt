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
    /** Live session shown on screen, closed if Android destroys the activity (it comes back as its Site screen, « Reprendre »). */
    private var live: SessionController? = null
    private var resuming = false

    companion object {
        /** Sessions being closed after an activity re-creation, by session id (process-wide: survives the activity). */
        private val closing = java.util.concurrent.ConcurrentHashMap<String, Thread>()
    }

    override fun onDestroy() {
        // Only on re-creation (night mode, language…): the restored state shows the Site screen, the old controller must not keep
        // its journal / sensors / copy worker open. Closed off the main thread (ARCore / copy can be slow); « Reprendre » waits for
        // this close to finish (see [closing]) so two writers never share a session. A plain finish or process kill is unchanged:
        // the segments are already fsynced and repaired on the next start.
        val c = live
        live = null
        if (c != null && isChangingConfigurations) {
            DiagLog.i("SESSION", "Activité recréée pendant la session ${c.sessionId} : session fermée, reprise possible depuis l'écran Site")
            closing[c.sessionId] = Thread({ runCatching { c.close("activity_recreated") }; closing.remove(c.sessionId) }, "lumisite-close-session").also { it.start() }
        }
        super.onDestroy()
    }

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
        if (savedInstanceState != null) DiagLog.i("APP", "Activité recréée par Android (état restauré)")
        app.lumisite.android.capture.ArDebug.fromIntent(applicationContext, intent, BuildConfig.DEBUG)
        val repo = Repository(applicationContext)
        setContent {
            LumiTheme {
                Surface(Modifier.semantics { testTagsAsResourceId = true }) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    // Saved across activity re-creation; a live session comes back as its site screen (the session can be resumed there).
                    var screen by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
                        save = { when (it) { Screen.Home -> "home"; Screen.NewSite -> "new"; Screen.Settings -> "settings"; is Screen.SiteDetail -> "site:" + it.siteId; is Screen.Live -> "site:" + it.controller.site.id } },
                        restore = { v -> when { v == "new" -> Screen.NewSite; v == "settings" -> Screen.Settings; v.startsWith("site:") -> Screen.SiteDetail(v.removePrefix("site:")); else -> Screen.Home } },
                    )) { mutableStateOf<Screen>(Screen.Home) }
                    var homeKey by remember { mutableStateOf(0) }
                    LaunchedEffect(screen) {
                        live = (screen as? Screen.Live)?.controller
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
                            try { repo.saveSite(site) } catch (e: Exception) {
                                DiagLog.e("SITE", "Accueil > Nouveau relevé > Créer : enregistrement du site impossible", e)
                                android.widget.Toast.makeText(this@MainActivity, "Site non enregistré : ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                return@SiteEditorScreen
                            }
                            DiagLog.i("SITE", "Site créé ${site.id}")
                            staged?.let { scope.launch(Dispatchers.IO) { runCatching { repo.saveOsmSource(site.id, it) } } }
                            site.bbox?.let { repo.addRecentZone(RecentZone(site.name, it)) }
                            screen = Screen.SiteDetail(site.id)
                        }, onCancel = { screen = Screen.Home })
                        is Screen.SiteDetail -> {
                            val site = repo.site(s.siteId)
                            var surveys by remember(s.siteId) { mutableStateOf(emptyList<app.lumisite.core.compute.SurveyPositions.Resolved>()) }
                            LaunchedEffect(s.siteId) { surveys = site?.let { withContext(Dispatchers.IO) { repo.siteSurveys(it) } }.orEmpty() }
                            if (site == null) {
                                LaunchedEffect(s.siteId) {
                                    DiagLog.e("SITE", "Site ${s.siteId} introuvable dans le stockage local : retour à l'Accueil")
                                    android.widget.Toast.makeText(this@MainActivity, "Site introuvable (voir Réglages > Journal)", android.widget.Toast.LENGTH_LONG).show()
                                    screen = Screen.Home
                                }
                            } else SiteScreen(
                                site, repo.sessions(site.id), surveys,
                                onNewSession = { screen = Screen.Live(SessionController(applicationContext, repo, site, Ids.new("ses"), resume = false)) },
                                onResume = { h -> if (!resuming) { resuming = true; scope.launch {
                                    // One resume at a time: further taps are ignored while waiting; if the activity goes away during the
                                    // wait, the coroutine is cancelled before any controller is created.
                                    try {
                                        withContext(Dispatchers.IO) { closing[h.id]?.join(10_000) }
                                        screen = Screen.Live(SessionController(applicationContext, repo, site, h.id, resume = true))
                                    } finally { resuming = false }
                                } } },
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
