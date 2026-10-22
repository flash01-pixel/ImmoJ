package be.immoj.ui.sources

import be.immoj.data.Annonce
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.immoj.ImmoJApp
import be.immoj.data.EtatSource
import be.immoj.ui.liste.messageErreur
import be.immoj.util.Format

/** État des sources : dernière collecte, dernière réussite, nombre d'annonces actives. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesEcran(app: ImmoJApp, retour: () -> Unit, index: List<Annonce> = emptyList()) {
    var sources by remember { mutableStateOf<List<EtatSource>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val actives = index.filter { it.actif }
    val parSource = remember(index) {
        actives.flatMap { a -> a.sources.map { it to a } }.groupBy({ it.first }, { it.second })
    }

    LaunchedEffect(Unit) {
        try {
            sources = app.depot.etatSources()
        } catch (e: Exception) {
            erreur = messageErreur(e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("État des sources") },
                navigationIcon = {
                    IconButton(onClick = retour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { marges ->
        val liste = sources
        when {
            erreur != null -> Box(Modifier.padding(marges).fillMaxSize(), Alignment.Center) {
                Text(erreur ?: "", color = MaterialTheme.colorScheme.error)
            }
            liste == null -> Box(Modifier.padding(marges).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            liste.isEmpty() -> Box(Modifier.padding(marges).fillMaxSize(), Alignment.Center) {
                Text("Aucune collecte enregistrée pour l'instant.")
            }
            else -> LazyColumn(
                modifier = Modifier.padding(marges),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    val loc = actives.count { it.transaction == "location" }
                    val part = actives.count { it.particulier }
                    Text(
                        "${Format.nombre(actives.size)} annonces actives : ${Format.nombre(loc)} locations, " +
                            "${Format.nombre(actives.size - loc)} achats · ${Format.nombre(part)} de particuliers",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(liste, key = { it.source }) { s ->
                    CarteSource(s, parSource[s.source]?.size ?: s.nbActives,
                        parSource[s.source]?.count { it.particulier } ?: 0)
                }
            }
        }
    }
}

@Composable
private fun CarteSource(s: EtatSource, nbActives: Int, nbParticuliers: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.derniereOk) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.Error, contentDescription = "En panne", tint = MaterialTheme.colorScheme.error)
                }
                Text(Format.source(s.source), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Annonces actives : $nbActives" + (if (nbParticuliers > 0) " · $nbParticuliers de particuliers" else ""))
            Text("Dernière collecte : ${Format.depuis(s.derniereCollecte)} " +
                "(${s.nbTrouvees} vues, ${s.nbNouvelles} nouvelles)")
            Text("Dernière réussite : ${Format.depuis(s.derniereReussite)}")
            if (!s.derniereOk) {
                Text(
                    (if (s.bloque) "Bloquée par le site. " else "Erreur : ") + (s.erreur ?: ""),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
