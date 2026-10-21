package be.immoj.ui.recherches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.immoj.ImmoJApp
import be.immoj.data.RechercheSauvegardee
import be.immoj.ui.liste.messageErreur
import kotlinx.coroutines.launch

/**
 * Recherches sauvegardées : liste, activation des notifications, suppression,
 * et « Appliquer » pour charger les critères dans la liste.
 */
@Composable
fun RecherchesEcran(app: ImmoJApp, appliquer: (RechercheSauvegardee) -> Unit) {
    val portee = rememberCoroutineScope()
    var recherches by remember { mutableStateOf<List<RechercheSauvegardee>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }

    suspend fun recharger() {
        try {
            recherches = app.depot.recherches()
            erreur = null
        } catch (e: Exception) {
            erreur = messageErreur(e)
        }
    }
    LaunchedEffect(Unit) { recharger() }

    val liste = recherches
    when {
        erreur != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(erreur ?: "", color = MaterialTheme.colorScheme.error)
        }
        liste == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        liste.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text(
                "Aucune recherche sauvegardée.\nDans Filtres, choisis tes critères puis « Enregistrer » : " +
                    "le collecteur t'enverra une notification pour chaque nouvelle annonce correspondante.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(liste, key = { it.id ?: it.nom }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.nom, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                portee.launch {
                                    r.id?.let { runCatching { app.depot.supprimerRecherche(it) } }
                                    recharger()
                                }
                            }) { Icon(Icons.Default.Delete, contentDescription = "Supprimer") }
                        }
                        Text(r.resume, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Notifications", modifier = Modifier.weight(1f))
                            Switch(checked = r.notifier && r.actif, onCheckedChange = { actif ->
                                portee.launch {
                                    r.id?.let {
                                        runCatching { app.depot.modifierRecherche(it, mapOf("notifier" to actif, "actif" to true)) }
                                    }
                                    recharger()
                                }
                            })
                        }
                        TextButton(onClick = { appliquer(r) }) { Text("Appliquer ces critères à la liste") }
                    }
                }
            }
        }
    }
}
