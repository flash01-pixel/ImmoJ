package be.immoj.ui.liste

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
fun ListeContenu(etat: ListeEtat, vm: ListeViewModel, ouvrirDetail: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = etat.transaction == "location",
                onClick = { vm.changerTransaction("location") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Location") }
            SegmentedButton(
                selected = etat.transaction == "achat",
                onClick = { vm.changerTransaction("achat") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Achat") }
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
                    CarteAnnonce(a) { ouvrirDetail(a.id) }
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
fun CarteAnnonce(a: Annonce, auClic: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = auClic)) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = a.photos.firstOrNull(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
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
