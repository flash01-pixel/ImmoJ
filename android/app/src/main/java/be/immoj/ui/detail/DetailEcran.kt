package be.immoj.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import be.immoj.ImmoJApp
import be.immoj.data.Annonce
import be.immoj.data.Catalogue
import be.immoj.data.StatutPerso
import be.immoj.data.distanceKm
import be.immoj.ui.carte.configurerOsmdroid
import be.immoj.ui.liste.Pastille
import be.immoj.util.Format
import coil.compose.AsyncImage
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * Fiche d'une annonce. Glisser à gauche ou à droite passe à l'annonce suivante
 * ou précédente de la liste courante (`voisins`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailEcran(
    app: ImmoJApp, id: String, retour: () -> Unit,
    onStatutChange: (String, StatutPerso, String?) -> Unit = { _, _, _ -> },
    voisins: List<String> = emptyList(),
    index: List<Annonce> = emptyList(),
    centreLat: Double? = null, centreLon: Double? = null,
    ouvrirAnnonce: (String) -> Unit = {},
) {
    // La liste des voisins est figée à l'ouverture : marquer une annonce ne fait pas sauter les pages.
    val pages = remember(id) { if (id in voisins) voisins else listOf(id) }
    val pager = rememberPagerState(initialPage = pages.indexOf(id).coerceAtLeast(0)) { pages.size }
    HorizontalPager(state = pager, key = { pages[it] }, beyondViewportPageCount = 0) { page ->
        FicheAnnonce(
            app = app, id = pages[page], position = "${page + 1}/${pages.size}", retour = retour,
            onStatutChange = onStatutChange, index = index, centreLat = centreLat, centreLon = centreLon,
            ouvrirAnnonce = ouvrirAnnonce,
        )
    }
}

/** Une fiche : photos, prix, caractéristiques, annonceur, mini-carte, similaires, statut, description. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicheAnnonce(
    app: ImmoJApp, id: String, position: String, retour: () -> Unit,
    onStatutChange: (String, StatutPerso, String?) -> Unit,
    index: List<Annonce>, centreLat: Double?, centreLon: Double?, ouvrirAnnonce: (String) -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = id) { DetailViewModel(app.depot, id, onStatutChange) }
    val etat by vm.etat.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val contexte = LocalContext.current

    LaunchedEffect(etat.message) {
        etat.message?.let { snackbar.showSnackbar(it); vm.effacerMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((etat.annonce?.lieu ?: "Annonce") + if (position.endsWith("/1")) "" else "  ·  $position") },
                navigationIcon = {
                    IconButton(onClick = retour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        bottomBar = { etat.annonce?.let { BarreActions(it, contexte) { s -> vm.changerStatut(s, it.note) } } },
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Format.prix(a.prix, a.transaction), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    if (a.particulier) Pastille("Particulier", MaterialTheme.colorScheme.primary)
                    if (a.statut == StatutPerso.INTERESSE.code) Pastille("★ Favori", MaterialTheme.colorScheme.tertiary)
                }
                PrixDetails(a)
                Text(a.titre ?: Format.type(a.type), style = MaterialTheme.typography.titleMedium)
                Text("${Format.type(a.type)} · ${a.lieu}" + (a.commune?.let { c ->
                    if (c != a.localite) " (commune de $c)" else "" } ?: ""))
                if (centreLat != null && centreLon != null && a.latitude != null && a.longitude != null) {
                    Text("À ${Format.km(distanceKm(centreLat, centreLon, a.latitude, a.longitude))} du centre de ta zone",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Publiée ${Format.depuis(a.datePublication)} · détectée ${Format.depuis(a.premiereDetection)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Caracteristiques(a)
                HorizontalDivider()

                // Annonceur
                Text("Annonceur", style = MaterialTheme.typography.titleSmall)
                Text(a.annonceurLibelle)
                a.telephone?.let { tel ->
                    Button(onClick = { appeler(contexte, tel) }) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tel)
                    }
                }
                Text("Sources", style = MaterialTheme.typography.titleSmall)
                a.sources.zip(a.urls).forEach { (source, url) ->
                    OutlinedButton(
                        onClick = { ouvrirUrl(contexte, url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir sur ${Format.source(source)}")
                    }
                }

                // Mini-carte
                if (a.latitude != null && a.longitude != null) {
                    HorizontalDivider()
                    Text("Situation", style = MaterialTheme.typography.titleSmall)
                    MiniCarte(a.latitude, a.longitude) { itineraire(contexte, a) }
                    Text("Appuie sur la carte pour l'itinéraire.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // Annonces similaires
                val similaires = remember(index, a.id) { Catalogue.similaires(index, a) }
                if (similaires.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Annonces similaires (${similaires.size})", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(similaires, key = { it.id }) { s ->
                            Card(modifier = Modifier.width(160.dp).clickable { ouvrirAnnonce(s.id) }) {
                                AsyncImage(model = s.vignette, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(100.dp))
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(Format.prix(s.prix, s.transaction).replace(" / mois", "/mois"),
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(listOfNotNull(s.chambres?.let { "$it ch." }, s.surface?.let { "$it m²" }, s.lieu)
                                        .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Barre d'actions fixe en bas : Appeler · Ouvrir · Itinéraire · Partager · ★. */
@Composable
private fun BarreActions(a: Annonce, contexte: Context, changerStatut: (StatutPerso) -> Unit) {
    val favori = a.statut == StatutPerso.INTERESSE.code
    NavigationBar {
        NavigationBarItem(selected = false, enabled = a.telephone != null,
            onClick = { a.telephone?.let { appeler(contexte, it) } },
            icon = { Icon(Icons.Default.Call, contentDescription = null) }, label = { Text("Appeler") })
        NavigationBarItem(selected = false, enabled = a.urls.isNotEmpty(),
            onClick = { a.urls.firstOrNull()?.let { ouvrirUrl(contexte, it) } },
            icon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) }, label = { Text("Ouvrir") })
        NavigationBarItem(selected = false, onClick = { itineraire(contexte, a) },
            icon = { Icon(Icons.Default.Directions, contentDescription = null) }, label = { Text("Itinéraire") })
        NavigationBarItem(selected = false, onClick = { partager(contexte, a) },
            icon = { Icon(Icons.Default.Share, contentDescription = null) }, label = { Text("Partager") })
        NavigationBarItem(selected = favori,
            onClick = { changerStatut(if (favori) StatutPerso.VU else StatutPerso.INTERESSE) },
            icon = { Icon(if (favori) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null) },
            label = { Text("Favori") })
    }
}

/** Prix au m², loyer + charges, ancien prix. */
@Composable
private fun PrixDetails(a: Annonce) {
    val lignes = buildList {
        if (a.badgePrixBaisse && a.prixPrecedent != null) add("Avant : ${Format.prix(a.prixPrecedent, a.transaction)}")
        a.prixM2?.let { m2 ->
            add(if (a.transaction == "location") String.format(Locale.FRANCE, "%.1f €/m² par mois", m2)
                else "${Format.nombre(m2.toInt())} €/m²")
        }
        if (a.transaction == "location" && a.charges != null && a.prix != null) {
            add("Loyer ${Format.nombre(a.prix)} € + ${Format.nombre(a.charges)} € de charges = " +
                "${Format.nombre(a.prix + a.charges)} € / mois")
        }
    }
    if (lignes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lignes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

/** Mini-carte OpenStreetMap non interactive, centrée sur le bien ; un appui lance l'itinéraire. */
@Composable
private fun MiniCarte(lat: Double, lon: Double, auClic: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))) {
        AndroidView(
            factory = { ctx ->
                configurerOsmdroid(ctx)
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(14.5)
                    controller.setCenter(GeoPoint(lat, lon))
                    overlays.add(Marker(this).apply {
                        position = GeoPoint(lat, lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setInfoWindow(null)
                    })
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        // Capte les gestes : la carte ne bouge pas, l'appui ouvre l'itinéraire.
        Box(modifier = Modifier.matchParentSize().clickable(onClick = auClic))
    }
}

private fun appeler(contexte: Context, tel: String) =
    contexte.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")))

private fun ouvrirUrl(contexte: Context, url: String) =
    contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun itineraire(contexte: Context, a: Annonce) {
    val cible = if (a.latitude != null && a.longitude != null) "${a.latitude},${a.longitude}"
        else Uri.encode("${a.lieu}, Belgique")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$cible(${Uri.encode(a.titre ?: a.lieu)})"))
    runCatching { contexte.startActivity(intent) }
        .onFailure { ouvrirUrl(contexte, "https://www.openstreetmap.org/search?query=$cible") }
}

private fun partager(contexte: Context, a: Annonce) {
    val texte = listOfNotNull(
        a.titre, "${Format.prix(a.prix, a.transaction)} · ${Format.type(a.type)} · ${a.lieu}",
        listOfNotNull(a.chambres?.let { "$it ch." }, a.surface?.let { "$it m²" }).joinToString(" · ").ifBlank { null },
        a.urls.firstOrNull(),
    ).joinToString("\n")
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, texte) }
    contexte.startActivity(Intent.createChooser(intent, "Partager l'annonce"))
}

/** Toutes les photos : bandeau défilant avec compteur ; un appui ouvre le plein écran. */
@Composable
private fun Photos(a: Annonce) {
    if (a.photos.isEmpty()) return
    var pleinEcran by remember(a.id) { mutableStateOf<Int?>(null) }
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(a.photos) { index, url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Photo ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.height(220.dp).width(300.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { pleinEcran = index },
                )
            }
        }
        Text(
            "${a.photos.size} photo(s) · appuie pour agrandir",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    pleinEcran?.let { depart ->
        Dialog(onDismissRequest = { pleinEcran = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val pager = rememberPagerState(initialPage = depart) { a.photos.size }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = a.photos[page],
                        contentDescription = "Photo ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clickable { pleinEcran = null },
                    )
                }
                Text(
                    "${pager.currentPage + 1} / ${a.photos.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                )
                TextButton(onClick = { pleinEcran = null }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text("Fermer", color = Color.White)
                }
            }
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
        a.disponibleLe?.let { "Disponible le : ${Format.dateCourte(it + "T00:00:00Z")}" },
    )
    // Détails supplémentaires lus sur la fiche (variables selon la source).
    val dejaAffiches = setOf("Chambres", "Surface habitable", "PEB", "Charges", "Jardin", "Terrasse",
        "Animaux", "Meublé", "Disponibilité", "Libre le")
    val extras = a.detailsLisibles.filter { it.first !in dejaAffiches }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lignes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        extras.forEach { (cle, valeur) -> Text("$cle : $valeur", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Choix du statut personnel (puces) et note libre. */
@Composable
private fun StatutEtNote(a: Annonce, onEnregistrer: (StatutPerso, String?) -> Unit) {
    var note by remember(a.id) { mutableStateOf(a.note ?: "") }
    val statut = StatutPerso.depuisCode(a.statut)

    Text("Mon statut", style = MaterialTheme.typography.titleSmall)
    // Deux rangées de puces pour tenir sur un écran de téléphone.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatutPerso.entries.chunked(3).forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rangee.forEach { s ->
                    FilterChip(
                        selected = statut == s,
                        onClick = { onEnregistrer(s, note) },
                        label = { Text(if (s == StatutPerso.INTERESSE) "★ Favori" else s.libelle) },
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
