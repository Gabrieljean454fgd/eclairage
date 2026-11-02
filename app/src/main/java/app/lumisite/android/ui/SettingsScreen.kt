package app.lumisite.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.lumisite.android.data.DiagLog
import app.lumisite.android.data.PublicStore
import app.lumisite.android.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** « Réglages » (stage 1): Stockage (paths, reimport) and Journal (share the diagnostic journal). */
@Composable
fun SettingsScreen(repo: Repository, onBack: () -> Unit, onReimported: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    // After a reinstall the app no longer owns the files it wrote in Documents/LumiSite: the user points to the
    // folder once with the system picker (Storage Access Framework), no storage permission is involved.
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) { msg = "Réimport annulé."; return@rememberLauncherForActivityResult }
        busy = true; err = null; msg = null
        DiagLog.i("RÉIMPORT", "Dossier choisi : $tree")
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) { repo.reimport(tree) }
                msg = if (res.isEmpty()) "Aucune session trouvée sous ce dossier (il faut un dossier contenant session_header.json)."
                else res.joinToString("\n") { r ->
                    "${r.sessionId} (${r.path}) : " + (r.skipped ?: if (r.problems.isEmpty()) "réimportée, toutes les empreintes vérifiées" else "réimportée avec problèmes : ${r.problems.joinToString()}")
                }
                DiagLog.i("RÉIMPORT", msg!!)
                onReimported()
            } catch (e: Throwable) {
                err = "Réimport impossible : ${e.message ?: e}"; DiagLog.e("RÉIMPORT", "Échec", e)
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = onBack, modifier = Modifier.testTag("reglages_retour")) { Text("← Accueil") }

        HorizontalDivider()
        Text("Stockage", style = MaterialTheme.typography.titleMedium)
        Text("Copie visible (survit à la désinstallation) : ${PublicStore.displayPath(listOf("<site>", "<session>"))}/", modifier = Modifier.testTag("reglages_stockage_chemin"))
        Text("Écriture d'abord dans l'appli (${ctx.filesDir.path}/sessions), en segments ; chaque segment terminé est copié et vérifié par empreinte SHA-256.", style = MaterialTheme.typography.bodySmall)
        Text("Exports : Documents/LumiSite/<site>/<session>/exports/ · Journal : ${DiagLog.publicDir}/", style = MaterialTheme.typography.bodySmall)
        Text("Espace libre : ${ctx.filesDir.usableSpace shr 20} Mo", style = MaterialTheme.typography.bodySmall)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Text("Android 9 ou moins : la copie visible demande l'autorisation de stockage (non demandée par cette version).", color = Color.Red, style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy, modifier = Modifier.testTag("reglages_stockage_reimporter"), onClick = {
            DiagLog.screen("Réglages > Stockage > Réimporter une session depuis Documents/LumiSite")
            val initial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/LumiSite") else null
            pickTree.launch(initial)
        }) { Text(if (busy) "Réimport…" else "Réimporter une session depuis Documents/LumiSite") }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reglages_stockage_resultat")) }
        err?.let { Text(it, color = Color.Red) }

        HorizontalDivider()
        Text("Journal", style = MaterialTheme.typography.titleMedium)
        Text("Journal de diagnostic tournant : ${DiagLog.publicDir}/ (copie) et dans l'appli. Il note l'écran actif, les erreurs caméra/ARCore, l'export, la synchro, la mémoire, les arrêts et reprises de flux.", style = MaterialTheme.typography.bodySmall)
        Button(modifier = Modifier.testTag("reglages_journal_partager"), onClick = {
            DiagLog.screen("Réglages > Journal > Partager le journal")
            DiagLog.memory("partage du journal")
            try {
                val f = DiagLog.bundle(repo.exportsDir)
                DiagLog.mirrorNow()
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager le journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Throwable) { err = "Partage du journal impossible : ${e.message ?: e}"; DiagLog.e("JOURNAL", "Partage", e) }
        }) { Text("Partager le journal") }
    }
}
