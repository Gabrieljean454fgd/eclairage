package app.lumisite.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumisite.android.data.VisibleStorage

/** Réglages > Stockage: the visible folder that survives uninstalling the app. */
@Composable
fun SettingsScreen(storage: VisibleStorage, onBack: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) { storage.setTree(uri); refresh++ } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onBack) { Text("← Retour") }; Text("Réglages", style = MaterialTheme.typography.titleLarge) }
        Text("Stockage", style = MaterialTheme.typography.titleMedium)
        @Suppress("UNUSED_EXPRESSION") refresh
        val path = storage.displayPath()
        Text(if (path != null) "Dossier : $path" else "Aucun dossier choisi : les sessions sont seulement dans le stockage privé, effacé si l'appli est désinstallée.")
        Text("Une copie des sites et sessions y est tenue à jour toutes les 5 s et conservée après désinstallation. Créez par exemple Documents/LumiSite.", style = MaterialTheme.typography.bodySmall)
        storage.lastError?.let { Text("Dernière erreur de copie : $it", color = MaterialTheme.colorScheme.error) }
        Button(onClick = { pick.launch(null) }) { Text(if (path == null) "Choisir le dossier" else "Changer de dossier") }
    }
}
