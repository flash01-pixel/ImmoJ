package be.immoj.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import be.immoj.ImmoJApp
import be.immoj.data.Annonce
import be.immoj.data.StatutPerso
import be.immoj.util.Format
import coil.compose.AsyncImage

/** Fiche détaillée d'une annonce : photos, infos, liens, agence, statut et note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailEcran(app: ImmoJApp, id: String, retour: () -> Unit) {
    val vm: DetailViewModel = viewModel(key = id) { DetailViewModel(app.depot, id) }
    val etat by vm.etat.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val contexte = LocalContext.current

    LaunchedEffect(etat.message) {
        etat.message?.let { snackbar.showSnackbar(it); vm.effacerMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(etat.annonce?.lieu ?: "Annonce") },
                navigationIcon = {
                    IconButton(onClick = retour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { marges ->
        val a = etat.annonce
        when {
            etat.chargement -> Box(Modifier.padding(marges).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            a == null -> Box(Modifier.padding(marges).fillMaxSize(), Alignment.Center) {
                Text(etat.erreur ?: "Annonce introuvable.", color = MaterialTheme.colorScheme.error)
            }
            else -> Column(
                modifier = Modifier.padding(marges).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Photos(a)
                Text(Format.prix(a.prix, a.transaction), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                if (a.badgePrixBaisse && a.prixPrecedent != null) {
                    Text("Avant : ${Format.prix(a.prixPrecedent, a.transaction)}",
                        color = MaterialTheme.colorScheme.tertiary)
                }
                Text(a.titre ?: Format.type(a.type), style = MaterialTheme.typography.titleMedium)
                Text("${Format.type(a.type)} · ${a.lieu}" + (a.commune?.let { c ->
                    if (c != a.localite) " (commune de $c)" else "" } ?: ""))

                Caracteristiques(a)
                HorizontalDivider()

                // Liens vers chaque source d'origine
                Text("Sources", style = MaterialTheme.typography.titleSmall)
                a.sources.zip(a.urls).forEach { (source, url) ->
                    OutlinedButton(
                        onClick = { contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir sur ${Format.source(source)}")
                    }
                }

                // Agence
                if (a.agence != null || a.telephone != null) {
                    HorizontalDivider()
                    Text("Agence", style = MaterialTheme.typography.titleSmall)
                    a.agence?.let { Text(it) }
                    a.telephone?.let { tel ->
                        Button(onClick = {
                            contexte.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")))
                        }) {
                            Icon(Icons.Default.Call, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tel)
                        }
                    }
                }

                // Historique des prix
                if (etat.historique.size > 1) {
                    HorizontalDivider()
                    Text("Historique des prix", style = MaterialTheme.typography.titleSmall)
                    etat.historique.forEach { h ->
                        Text("${Format.dateCourte(h.date)} : ${Format.prix(h.prix, a.transaction)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider()
                StatutEtNote(a, onEnregistrer = { s, n -> vm.changerStatut(s, n) })

                HorizontalDivider()
                Text("Description", style = MaterialTheme.typography.titleSmall)
                Text(a.description ?: "Pas de description.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Publiée le ${Format.dateCourte(a.datePublication)} · détectée ${Format.depuis(a.premiereDetection)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Photos(a: Annonce) {
    if (a.photos.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(a.photos) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.height(220.dp).width(300.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun Caracteristiques(a: Annonce) {
    fun ouiNon(v: Boolean?) = when (v) { true -> "oui"; false -> "non"; null -> null }
    val lignes = listOfNotNull(
        a.chambres?.let { "Chambres : $it" },
        a.surface?.let { "Surface : $it m²" },
        a.peb?.let { "PEB : $it" },
        a.charges?.let { "Charges : $it € / mois" },
        ouiNon(a.jardin)?.let { "Jardin : $it" },
        ouiNon(a.terrasse)?.let { "Terrasse : $it" },
        ouiNon(a.animaux)?.let { "Animaux acceptés : $it" },
        ouiNon(a.meuble)?.let { "Meublé : $it" },
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lignes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Choix du statut personnel (puces) et note libre. */
@Composable
private fun StatutEtNote(a: Annonce, onEnregistrer: (StatutPerso, String?) -> Unit) {
    var statut by remember(a.id) { mutableStateOf(StatutPerso.depuisCode(a.statut)) }
    var note by remember(a.id) { mutableStateOf(a.note ?: "") }

    Text("Mon statut", style = MaterialTheme.typography.titleSmall)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Deux rangées de puces pour tenir sur un écran de téléphone.
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatutPerso.entries.chunked(3).forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rangee.forEach { s ->
                    FilterChip(
                        selected = statut == s,
                        onClick = { statut = s; onEnregistrer(s, note) },
                        label = { Text(s.libelle) },
                    )
                }
            }
        }
    }
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Note personnelle") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Button(onClick = { onEnregistrer(statut, note) }) { Text("Enregistrer la note") }
}
