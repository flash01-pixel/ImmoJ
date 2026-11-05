package be.immoj.ui.ajouter

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.immoj.ImmoJApp
import be.immoj.data.Commune
import be.immoj.data.Filtres
import be.immoj.data.Manuelles
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * « Partager vers ImmoJ » / « + Ajouter une annonce » : fiche manuelle (lien, prix,
 * commune, note, photo facultative) qui rejoint la même liste que les autres.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjouterEcran(app: ImmoJApp, textePartage: String?, photoPartagee: Uri?, retour: () -> Unit,
                 ouvrirAnnonce: (String) -> Unit) {
    val portee = rememberCoroutineScope()
    var titre by remember { mutableStateOf(Manuelles.titreDepuis(textePartage)) }
    var lien by remember { mutableStateOf(Manuelles.extraireLien(textePartage) ?: "") }
    var prix by remember { mutableStateOf(Manuelles.extrairePrix(textePartage)?.toString() ?: "") }
    var transaction by remember { mutableStateOf("location") }
    var type by remember { mutableStateOf("appartement") }
    var chambres by remember { mutableStateOf("") }
    var surface by remember { mutableStateOf("") }
    var localite by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(textePartage?.replace(Regex("https?://\\S+"), "")?.trim() ?: "") }
    var annonceur by remember { mutableStateOf("particulier") }
    var communes by remember { mutableStateOf<List<Commune>>(emptyList()) }
    var commune by remember { mutableStateOf<Commune?>(null) }
    var menuCommune by remember { mutableStateOf(false) }
    var rechercheCommune by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { communes = runCatching { app.depot.communes() }.getOrDefault(emptyList()) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Ajouter une annonce") }, navigationIcon = {
            IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        })
    }) { marges ->
        Column(
            modifier = Modifier.padding(marges).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Cette annonce vient d'ailleurs (Facebook, Messenger, bouche-à-oreille…). Elle restera sur ton " +
                "téléphone, dans la même liste que les autres, avec son statut et sa place sur la carte.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photoPartagee != null) {
                AsyncImage(model = photoPartagee, contentDescription = "Photo partagée", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = transaction == "location", onClick = { transaction = "location" }, label = { Text("Location") })
                FilterChip(selected = transaction == "achat", onClick = { transaction = "achat" }, label = { Text("Achat") })
            }
            OutlinedTextField(value = titre, onValueChange = { titre = it }, label = { Text("Titre") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lien, onValueChange = { lien = it }, label = { Text("Lien (facultatif)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = prix, onValueChange = { prix = it.filter(Char::isDigit) },
                    label = { Text(if (transaction == "location") "Loyer €/mois" else "Prix €") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = chambres, onValueChange = { chambres = it.filter(Char::isDigit) },
                    label = { Text("Chambres") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = surface, onValueChange = { surface = it.filter(Char::isDigit) },
                    label = { Text("m²") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Text("Type", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Filtres.TYPES.filter { it.first != "autre" }.chunked(3).forEach { rangee ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rangee.forEach { (code, lib) ->
                            FilterChip(selected = type == code, onClick = { type = code }, label = { Text(lib) })
                        }
                    }
                }
            }
            OutlinedButton(onClick = { menuCommune = true }, modifier = Modifier.fillMaxWidth()) {
                Text(commune?.nom ?: "Commune (obligatoire pour la carte)")
            }
            DropdownMenu(expanded = menuCommune, onDismissRequest = { menuCommune = false }) {
                OutlinedTextField(value = rechercheCommune, onValueChange = { rechercheCommune = it },
                    placeholder = { Text("Rechercher") }, singleLine = true, modifier = Modifier.padding(horizontal = 8.dp))
                communes.filter { rechercheCommune.isBlank() || it.nom.contains(rechercheCommune, ignoreCase = true) }
                    .take(12).forEach { c ->
                        DropdownMenuItem(text = { Text(c.nom) }, onClick = { commune = c; menuCommune = false })
                    }
            }
            OutlinedTextField(value = localite, onValueChange = { localite = it },
                label = { Text("Localité / village (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Annonceur", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("particulier" to "Particulier", "agence" to "Agence", "inconnu" to "Je ne sais pas").forEach { (code, lib) ->
                    FilterChip(selected = annonceur == code, onClick = { annonceur = code }, label = { Text(lib) })
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note / description") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                if (titre.isBlank() && lien.isBlank()) { erreur = "Donne au moins un titre ou un lien."; return@Button }
                portee.launch {
                    val a = Manuelles.creer(titre = titre, lien = lien.ifBlank { null }, transaction = transaction,
                        type = type, prix = prix.toIntOrNull(), commune = commune, localite = localite,
                        chambres = chambres.toIntOrNull(), surface = surface.toIntOrNull(), note = note, annonceur = annonceur)
                    val enregistree = app.manuelles.ajouter(a, photoPartagee)
                    ouvrirAnnonce(enregistree.id)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer dans ImmoJ") }
        }
    }
}
