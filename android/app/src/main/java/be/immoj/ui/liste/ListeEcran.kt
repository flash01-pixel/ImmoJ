package be.immoj.ui.liste

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.immoj.data.Annonce
import be.immoj.data.Filtres
import be.immoj.data.StatutPerso
import be.immoj.ui.principal.LocalSnackbar
import be.immoj.util.Dates
import be.immoj.util.Format
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Onglet « Annonces » : bascule location/achat avec compteurs, recherche texte,
 * filtres rapides, tri, et la liste dans le mode choisi (cartes, détaillé,
 * compact, grille). Glisser à gauche = « Pas pour moi », à droite = favori ★.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ListeContenu(
    etat: ListeEtat, vm: ListeViewModel, ouvrirDetail: (String) -> Unit,
    etatListe: LazyListState = rememberLazyListState(),
    ouvrirFiltres: () -> Unit = {}, ouvrirCarte: () -> Unit = {},
) {
    val portee = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var rechercheOuverte by remember { mutableStateOf(etat.filtres.texte.isNotBlank()) }
    var menuTri by remember { mutableStateOf(false) }
    var menuPrix by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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

        // ---- Filtres rapides (une ligne défilante) ----
        val f = etat.filtres
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { rechercheOuverte = !rechercheOuverte }) {
                Icon(Icons.Default.Search, contentDescription = "Rechercher")
            }
            Box {
                PuceRapide(f.prixMax?.let { "≤ ${Format.nombre(it)} €" } ?: "Prix max", f.prixMax != null,
                    onClick = { menuPrix = true }, onRetirer = { vm.changerFiltres(f.copy(prixMax = null)) })
                DropdownMenu(expanded = menuPrix, onDismissRequest = { menuPrix = false }) {
                    val presets = if (etat.transaction == "location") Filtres.PRIX_LOCATION else Filtres.PRIX_ACHAT
                    presets.forEach { p ->
                        DropdownMenuItem(text = { Text("≤ ${Format.nombre(p)} €") },
                            onClick = { menuPrix = false; vm.changerFiltres(f.copy(prixMax = p)) })
                    }
                    DropdownMenuItem(text = { Text("Autre montant…") }, onClick = { menuPrix = false; ouvrirFiltres() })
                }
            }
            PuceRapide(f.chambresMin?.let { "$it+ ch." } ?: "Chambres", f.chambresMin != null,
                onClick = { vm.changerFiltres(f.copy(chambresMin = when (f.chambresMin) { null -> 2; 2 -> 3; else -> null })) },
                onRetirer = { vm.changerFiltres(f.copy(chambresMin = null)) })
            PuceRapide(if (f.types.isEmpty()) "Type" else f.types.joinToString("/") { Format.type(it) }, f.types.isNotEmpty(),
                onClick = ouvrirFiltres, onRetirer = { vm.changerFiltres(f.copy(types = emptySet())) })
            PuceRapide(etat.libelleZone() ?: (if (f.communes.isNotEmpty()) "${f.communes.size} commune(s)" else "Zone"),
                f.rayonDefini || f.communes.isNotEmpty(),
                onClick = { if (f.rayonDefini || f.communes.isNotEmpty()) ouvrirFiltres() else ouvrirCarte() },
                onRetirer = {
                    val avant = vm.retirerRayon()
                    vm.changerFiltres(avant.copy(centreLat = null, centreLon = null, rayonKm = null, communes = emptySet()))
                    portee.launch {
                        if (snackbar?.showSnackbar("Zone retirée", actionLabel = "Annuler") == SnackbarResult.ActionPerformed) {
                            vm.changerFiltres(avant)
                        }
                    }
                })
            PuceRapide("Particuliers", "particulier" in f.annonceur,
                onClick = { vm.changerFiltres(f.copy(annonceur = if ("particulier" in f.annonceur) emptySet() else setOf("particulier"))) },
                onRetirer = { vm.changerFiltres(f.copy(annonceur = emptySet())) })
            PuceRapide("Jardin", f.jardin, onClick = { vm.changerFiltres(f.copy(jardin = !f.jardin)) },
                onRetirer = { vm.changerFiltres(f.copy(jardin = false)) })
            PuceRapide("Animaux", f.animaux, onClick = { vm.changerFiltres(f.copy(animaux = !f.animaux)) },
                onRetirer = { vm.changerFiltres(f.copy(animaux = false)) })
            PuceRapide("Nouveautés 24 h", f.periode == "24h",
                onClick = { vm.changerFiltres(f.copy(periode = if (f.periode == "24h") "tout" else "24h")) },
                onRetirer = { vm.changerFiltres(f.copy(periode = "tout")) })
            if (f.nbActifs > 0) {
                AssistChip(onClick = { vm.changerFiltres(Filtres(afficherRefusees = f.afficherRefusees)) },
                    label = { Text("Tout effacer") })
            }
        }
        if (rechercheOuverte) {
            OutlinedTextField(
                value = f.texte, onValueChange = { vm.chercher(it) },
                placeholder = { Text("Titre, description, commune, annonceur…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { vm.chercher(""); rechercheOuverte = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer la recherche")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ---- Compteur + tri ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val n = etat.annonces.size
            val total = etat.nbTotalTransaction
            Column {
                Text(buildString {
                    append(if (n == total) "${Format.nombre(n)} annonce${if (n > 1) "s" else ""}"
                        else "${Format.nombre(n)} sur ${Format.nombre(total)}")
                    if (etat.nbNouvelles > 0) append(" · ${etat.nbNouvelles} nouvelle${if (etat.nbNouvelles > 1) "s" else ""}")
                }, style = MaterialTheme.typography.titleSmall)
                Text("Données du ${Dates.dateHeure(etat.dateDonnees)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                AssistChip(onClick = { menuTri = true },
                    label = { Text(Filtres.TRIS.firstOrNull { it.first == etat.tri }?.second ?: "Tri") })
                DropdownMenu(expanded = menuTri, onDismissRequest = { menuTri = false }) {
                    Filtres.TRIS.forEach { (code, libelle) ->
                        if (code == "distance" && !f.rayonDefini) return@forEach
                        DropdownMenuItem(text = { Text(libelle) }, onClick = { menuTri = false; vm.changerTri(code) })
                    }
                }
            }
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (etat.filtres.nbActifs > 0) "Aucune annonce ne correspond aux filtres."
                            else "Aucune annonce pour le moment.\nLe collecteur n'a peut-être pas encore tourné.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (etat.filtres.nbActifs > 0) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = ouvrirFiltres) { Text("Voir des suggestions") }
                        }
                    }
                }
                etat.modeListe == "grille" -> Grille(etat, ouvrirDetail)
                else -> Liste(etat, vm, ouvrirDetail, etatListe)
            }
        }
    }
}

/** Puce de filtre rapide : active = colorée avec ✕, sinon simple appui. */
@Composable
private fun PuceRapide(libelle: String, active: Boolean, onClick: () -> Unit, onRetirer: () -> Unit) {
    if (active) {
        InputChip(selected = true, onClick = onClick, label = { Text(libelle) },
            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.clickable { onRetirer() }) })
    } else {
        FilterChip(selected = false, onClick = onClick, label = { Text(libelle) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Liste(etat: ListeEtat, vm: ListeViewModel, ouvrirDetail: (String) -> Unit, etatListe: LazyListState) {
    val snackbar = LocalSnackbar.current
    val portee = rememberCoroutineScope()
    val nouvelles = etat.annonces.filter { etat.nouvelleDepuisVisite(it) }
    val anciennes = etat.annonces.filter { !etat.nouvelleDepuisVisite(it) }
    val compact = etat.modeListe == "compact"
    LazyColumn(
        state = etatListe,
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 12.dp),
    ) {
        if (nouvelles.isNotEmpty() && anciennes.isNotEmpty()) {
            item(key = "sep-nouveau") {
                Text("Nouveau depuis ta dernière visite (${nouvelles.size})", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        items(nouvelles, key = { it.id }) { a -> Ligne(a, etat, vm, ouvrirDetail, snackbar, portee) }
        if (nouvelles.isNotEmpty() && anciennes.isNotEmpty()) {
            item(key = "sep-ancien") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Déjà vues lors de ta dernière visite", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        items(anciennes, key = { it.id }) { a -> Ligne(a, etat, vm, ouvrirDetail, snackbar, portee) }
    }
}

/** Une annonce avec ses gestes : glisser à gauche = « Pas pour moi », à droite = favori ★. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Ligne(
    a: Annonce, etat: ListeEtat, vm: ListeViewModel, ouvrirDetail: (String) -> Unit,
    snackbar: androidx.compose.material3.SnackbarHostState?, portee: kotlinx.coroutines.CoroutineScope,
) {
    val etatGeste = rememberSwipeToDismissBoxState(confirmValueChange = { valeur ->
        when (valeur) {
            SwipeToDismissBoxValue.EndToStart -> {
                val ancien = vm.marquer(a, StatutPerso.REFUSE)
                portee.launch {
                    if (snackbar?.showSnackbar("Pas pour moi : ${a.lieu}", actionLabel = "Annuler") == SnackbarResult.ActionPerformed) {
                        vm.retablir(a, ancien)
                    }
                }
                true
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                val deja = a.statut == StatutPerso.INTERESSE.code
                val ancien = vm.marquer(a, if (deja) StatutPerso.VU else StatutPerso.INTERESSE)
                portee.launch {
                    val msg = if (deja) "Retiré des favoris" else "Ajouté aux favoris ★"
                    if (snackbar?.showSnackbar(msg, actionLabel = "Annuler") == SnackbarResult.ActionPerformed) {
                        vm.retablir(a, ancien)
                    }
                }
                false   // la carte reste en place
            }
            else -> false
        }
    })
    SwipeToDismissBox(
        state = etatGeste,
        backgroundContent = {
            val droite = etatGeste.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .background(if (droite) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (droite) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (droite) Icons.Default.Star else Icons.Default.ThumbDown, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (droite) "Favori" else "Pas pour moi")
                }
            }
        },
    ) {
        when (etat.modeListe) {
            "compact" -> LigneCompacte(a, etat) { ouvrirDetail(a.id) }
            "detaille" -> CarteDetaillee(a, etat) { ouvrirDetail(a.id) }
            else -> CarteAnnonce(a, photo = etat.photosEnListe, nouvelle = etat.nouvelleDepuisVisite(a)) { ouvrirDetail(a.id) }
        }
    }
}

@Composable
private fun Grille(etat: ListeEtat, ouvrirDetail: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), state = rememberLazyGridState(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(etat.annonces, key = { it.id }) { a ->
            Card(modifier = Modifier.clickable { ouvrirDetail(a.id) }) {
                Column {
                    AsyncImage(model = a.vignette, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(Format.prix(a.prix, a.transaction), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${Format.type(a.type)} · ${a.localite ?: a.commune ?: ""}", style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(a.chambres?.let { "$it ch." }, a.surface?.let { "$it m²" }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall)
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

/** Point « non lu » (annonce jamais ouverte). */
@Composable
private fun PointNonLu(a: Annonce) {
    if (a.statut == StatutPerso.NOUVEAU.code) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(6.dp))
    }
}

/** Mode compact : une ligne « prix · type · commune · ch. · m² · âge ». */
@Composable
private fun LigneCompacte(a: Annonce, etat: ListeEtat, auClic: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = auClic).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PointNonLu(a)
        Text(Format.prix(a.prix, a.transaction).replace(" / mois", ""), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
        Text(
            listOfNotNull(Format.type(a.type), a.localite ?: a.commune, a.chambres?.let { "$it ch." },
                a.surface?.let { "$it m²" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (a.statut == StatutPerso.INTERESSE.code) Icon(Icons.Default.Star, contentDescription = "Favori",
            tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
        Text(Format.depuis(a.datePublication ?: a.premiereDetection).replace("il y a ", ""),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp))
    }
    HorizontalDivider()
}

/** Mode détaillé : grande photo, puis les mêmes informations que la carte. */
@Composable
private fun CarteDetaillee(a: Annonce, etat: ListeEtat, auClic: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = auClic)) {
        Column {
            AsyncImage(model = a.vignette, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp))
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PointNonLu(a)
                    Text(Format.prix(a.prix, a.transaction), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(a.titre ?: Format.type(a.type), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${Format.type(a.type)} · ${a.lieu}", style = MaterialTheme.typography.bodyMedium)
                Text(listOfNotNull(a.chambres?.let { "$it ch." }, a.surface?.let { "$it m²" }, a.peb?.let { "PEB $it" },
                    a.prixM2?.let { "${Format.nombre(it.toInt())} €/m²" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall)
                Badges(a)
                Text("${a.annonceurLibelle} · ${Format.depuis(a.premiereDetection)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Badges(a: Annonce) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (a.badgeNouveau) Pastille("Nouveau", MaterialTheme.colorScheme.primary)
        if (a.badgePrixBaisse) Pastille("Prix baissé", MaterialTheme.colorScheme.tertiary)
        val statut = StatutPerso.depuisCode(a.statut)
        if (statut == StatutPerso.INTERESSE) Pastille("★ Favori", MaterialTheme.colorScheme.tertiary)
        else if (statut != StatutPerso.NOUVEAU) Pastille(statut.libelle, MaterialTheme.colorScheme.secondary)
        if (a.particulier) Pastille("Particulier", MaterialTheme.colorScheme.primary)
    }
}

/** Une annonce dans la liste (mode cartes) : photo, prix, lieu, caractéristiques, badges. */
@Composable
fun CarteAnnonce(a: Annonce, photo: Boolean = true, nouvelle: Boolean = false, auClic: () -> Unit) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PointNonLu(a)
                    Text(
                        Format.prix(a.prix, a.transaction),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
                Badges(a)
                Text(
                    "${a.annonceurLibelle} · ${Format.depuis(a.datePublication ?: a.premiereDetection)}",
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
