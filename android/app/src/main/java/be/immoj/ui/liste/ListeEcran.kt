package be.immoj.ui.liste

import kotlinx.coroutines.launch
import be.immoj.ui.principal.LocalSnackbar
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import be.immoj.util.Dates
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.immoj.data.Annonce
import be.immoj.data.StatutPerso
import be.immoj.util.Format
import coil.compose.AsyncImage

/** Onglet « Annonces » : bascule location/achat, tri, liste. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ListeContenu(etat: ListeEtat, vm: ListeViewModel, ouvrirDetail: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = etat.transaction == "location",
                onClick = { vm.changerTransaction("location") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Location (${Format.nombre(etat.totaux["location"] ?: 0)})") }
            SegmentedButton(
                selected = etat.transaction == "achat",
                onClick = { vm.changerTransaction("achat") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Achat (${Format.nombre(etat.totaux["achat"] ?: 0)})") }
        }
        // Compteur : « 37 annonces · 5 nouvelles », ou « 37 sur 397 » quand des filtres jouent.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val n = etat.annonces.size
            val total = etat.nbTotalTransaction
            val texte = buildString {
                append(if (n == total) "${Format.nombre(n)} annonce${if (n > 1) "s" else ""}"
                    else "${Format.nombre(n)} sur ${Format.nombre(total)}")
                if (etat.nbNouvelles > 0) append(" · ${etat.nbNouvelles} nouvelle${if (etat.nbNouvelles > 1) "s" else ""}")
            }
            Text(texte, style = MaterialTheme.typography.titleSmall)
            Text(
                "Données du ${Dates.dateHeure(etat.dateDonnees)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        etat.libelleZone()?.let { zone ->
            val portee = rememberCoroutineScope()
            val snackbar = LocalSnackbar.current
            InputChip(
                selected = true,
                onClick = {
                    val avant = vm.retirerRayon()
                    portee.launch {
                        if (snackbar?.showSnackbar("Zone retirée", actionLabel = "Annuler") == SnackbarResult.ActionPerformed) {
                            vm.changerFiltres(avant)
                        }
                    }
                },
                label = { Text(zone) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer la zone") },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (etat.nbRefuseesMasquees > 0) {
            Text(
                "${etat.nbRefuseesMasquees} refusée${if (etat.nbRefuseesMasquees > 1) "s" else ""} masquée" +
                    "${if (etat.nbRefuseesMasquees > 1) "s" else ""} · afficher",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable { vm.changerFiltres(etat.filtres.copy(afficherRefusees = true)) },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tri :", style = MaterialTheme.typography.labelLarge)
            FilterChip(selected = etat.tri == "recent", onClick = { vm.changerTri("recent") },
                label = { Text("Plus récentes") })
            FilterChip(selected = etat.tri == "prix", onClick = { vm.changerTri("prix") },
                label = { Text("Prix croissant") })
            FilterChip(selected = etat.tri == "prix_desc", onClick = { vm.changerTri("prix_desc") },
                label = { Text("Prix décroissant") })
        }

        // Tirer la liste vers le bas = vraie actualisation (nouveau téléchargement).
        PullToRefreshBox(
            isRefreshing = etat.chargement && etat.annonces.isNotEmpty(),
            onRefresh = { vm.charger(forcer = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
        when {
            etat.chargement && etat.annonces.isEmpty() -> Centre { CircularProgressIndicator() }
            etat.erreur != null -> Centre {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(etat.erreur, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.charger() }) { Text("Réessayer") }
                }
            }
            etat.annonces.isEmpty() -> Centre {
                Text(
                    if (etat.filtres.nbActifs > 0) "Aucune annonce ne correspond aux filtres."
                    else "Aucune annonce pour le moment.\nLe collecteur n'a peut-être pas encore tourné.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(etat.annonces, key = { it.id }) { a ->
                    CarteAnnonce(a, photo = etat.photosEnListe) { ouvrirDetail(a.id) }
                }
            }
        }
        }
    }
}

@Composable
private fun Centre(contenu: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        contenu()
    }
}

/** Une annonce dans la liste : photo, prix, lieu, caractéristiques, badges. */
@Composable
fun CarteAnnonce(a: Annonce, photo: Boolean = true, auClic: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = auClic)) {
        Row(modifier = Modifier.padding(12.dp)) {
            if (photo) {
                AsyncImage(
                    model = a.vignette,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    Format.prix(a.prix, a.transaction),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${Format.type(a.type)} · ${a.lieu}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    a.chambres?.let { "$it ch." },
                    a.surface?.let { "$it m²" },
                    a.peb?.let { "PEB $it" },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a.badgeNouveau) Pastille("Nouveau", MaterialTheme.colorScheme.primary)
                    if (a.badgePrixBaisse) Pastille("Prix baissé", MaterialTheme.colorScheme.tertiary)
                    val statut = StatutPerso.depuisCode(a.statut)
                    if (statut != StatutPerso.NOUVEAU) Pastille(statut.libelle, MaterialTheme.colorScheme.secondary)
                    if (a.particulier) Pastille("Particulier", MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${a.sources.joinToString(", ") { Format.source(it) }} · ${Format.depuis(a.premiereDetection)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Pastille(texte: String, couleur: Color) {
    AssistChip(
        onClick = {},
        label = { Text(texte, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(labelColor = couleur),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = couleur),
        modifier = Modifier.height(24.dp),
    )
}
