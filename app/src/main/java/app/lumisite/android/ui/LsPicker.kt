package app.lumisite.android.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.lumisite.android.SessionController
import app.lumisite.core.catalog.LsCatalog
import app.lumisite.core.catalog.LsItem
import app.lumisite.core.catalog.LsSymbols
import app.lumisite.core.model.SurveyEvent

/**
 * Sélecteur catalogue 2.0, partagé entre Session > Relevé et Session > Annoter > Poser.
 * Racine : recherche, favoris/récents, grille des familles (Éclairage en premier) avec symbole.
 * Famille : fil d'Ariane, « Ajouter un type… », puis les types avec symbole + libellé.
 * [onPick] reçoit le code LS_* choisi (SessionController.survey accepte aussi les anciens noms).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LsCatalogPicker(c: SessionController, selected: String?, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("lumisite_quick", Context.MODE_PRIVATE) }
    var pins by remember { mutableStateOf(prefs.getStringSet("pins", emptySet())!!.toSet()) }
    val cat = c.lsCatalog.collectAsState().value
    val events = c.state.collectAsState().value.events
    var famille by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }

    val recent = events.filterIsInstance<SurveyEvent>().filter { it.supersedes == null && it.deleted != true }
        .asReversed().mapNotNull { cat.byCodeOrLegacy(it.kind) }.distinctBy { it.code }.take(6)
    val pinned = pins.mapNotNull { cat.byCodeOrLegacy(it) }.distinctBy { it.code }.sortedBy { it.label }

    @Composable fun typeChip(it: LsItem) {
        FilterChip(selected == it.code, { onPick(it.code) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LsSymbol(LsSymbols.symbolOf(it), Color(LsSymbols.familyColor(it.famille)), 24.dp)
                    Text(it.label, style = MaterialTheme.typography.bodySmall)
                }
            }, modifier = Modifier.testTag("session_releve_type_" + it.code))
    }

    OutlinedTextField(query, { query = it }, label = { Text("Rechercher un type") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("session_releve_recherche"))

    if (query.isNotBlank()) {
        val found = cat.search(query)
        if (found.isEmpty()) Text("Aucun type pour « $query ».", style = MaterialTheme.typography.bodySmall)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { found.forEach { typeChip(it) } }
        TextButton({ addDialog = true }, modifier = Modifier.testTag("session_releve_ajouter_type")) { Text("+ Ajouter un type…") }
    } else if (famille == null) {
        if (recent.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Récents :", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            recent.forEach { typeChip(it) }
        }
        if (pinned.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Épinglés :", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            pinned.forEach { typeChip(it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cat.families().forEach { f ->
                FilterChip(false, { famille = f; query = "" },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LsSymbol(LsSymbols.familySymbol(f), Color(LsSymbols.familyColor(f)), 24.dp)
                            Text(LsCatalog.familyLabel(f), style = MaterialTheme.typography.bodySmall)
                        }
                    }, modifier = Modifier.testTag("session_releve_categorie_" + f))
            }
        }
    } else {
        val f = famille!!
        val selItem = cat.byCode(selected ?: "")?.takeIf { it.famille == f }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ famille = null }, modifier = Modifier.testTag("session_releve_retour_familles")) { Text("‹ Familles") }
            Text(LsCatalog.breadcrumb(LsCatalog.familyLabel(f), null, selItem?.label),
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("session_releve_fil_ariane"))
        }
        TextButton({ addDialog = true }, modifier = Modifier.testTag("session_releve_ajouter_type")) { Text("+ Ajouter un type…") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cat.types(f, null).forEach { typeChip(it) }
        }
    }

    // « Épingler » garde le type choisi en tête du sélecteur (comme l'ancienne grille).
    cat.byCodeOrLegacy(selected ?: "")?.let { sel ->
        TextButton({
            pins = if (sel.code in pins) pins - sel.code else pins + sel.code
            prefs.edit().putStringSet("pins", pins).apply()
        }, modifier = Modifier.testTag("session_releve_epingler")) { Text(if (sel.code in pins) "Désépingler ${sel.label}" else "Épingler ${sel.label}") }
    }

    if (addDialog) {
        var nom by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addDialog = false }, modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text("Nouveau type (à classer au bureau)") },
            text = {
                Column {
                    Text("Il est enregistré dans « " + LsCatalog.familyLabel(famille ?: "AUTRE") + " » sans bloc DWG, visible ensuite dans la liste.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(nom, { nom = it }, label = { Text("Nom du type") }, singleLine = true,
                        modifier = Modifier.testTag("session_releve_nouveau_type_nom"))
                }
            },
            confirmButton = {
                Button(enabled = nom.isNotBlank(), onClick = {
                    c.addPersonalType(famille ?: "AUTRE", nom)?.let { onPick(it.code); query = it.label }
                    addDialog = false
                }, modifier = Modifier.testTag("session_releve_nouveau_type_valider")) { Text("Créer") }
            },
            dismissButton = { OutlinedButton({ addDialog = false }, modifier = Modifier.testTag("session_releve_nouveau_type_annuler")) { Text("Annuler") } })
    }
}
