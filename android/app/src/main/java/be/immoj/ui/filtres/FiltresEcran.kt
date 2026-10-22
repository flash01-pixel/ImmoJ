package be.immoj.ui.filtres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.immoj.data.Annonce
import be.immoj.data.Catalogue
import be.immoj.data.Commune
import be.immoj.data.Facettes
import be.immoj.data.Filtres
import be.immoj.data.RechercheSauvegardee
import be.immoj.util.Format
import kotlinx.coroutines.launch

/**
 * Écran des filtres. Chaque changement s'applique immédiatement à la liste
 * (rien n'est perdu au retour) ; le bouton du bas dit combien d'annonces on
 * verra. Chaque option affiche son nombre d'annonces. Sans résultat, des
 * suggestions élargissent la recherche avec leur gain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltresEcran(
    filtres: Filtres,
    index: List<Annonce>,
    transaction: String,
    communes: List<Commune>,
    appliquer: (Filtres) -> Unit,
    enregistrerRecherche: (RechercheSauvegardee) -> Unit,
    ouvrirCarte: () -> Unit,
    retour: () -> Unit,
) {
    val f = filtres
    val portee = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialogueCommunes by remember { mutableStateOf(false) }
    var dialogueNom by remember { mutableStateOf(false) }
    val facettes: Facettes = remember(index, transaction, f) { Catalogue.facettes(index, transaction, f) }
    val suggestions = remember(index, transaction, f) {
        if (facettes.total == 0) Catalogue.suggestions(index, transaction, f) else emptyList()
    }
    val total = facettes.total

    fun reinitialiser() {
        val avant = f
        appliquer(Filtres(afficherRefusees = f.afficherRefusees))
        portee.launch {
            if (snackbar.showSnackbar("Filtres réinitialisés", actionLabel = "Annuler") == SnackbarResult.ActionPerformed) {
                appliquer(avant)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filtres") },
                navigationIcon = {
                    IconButton(onClick = retour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (f.nbActifs > 0) TextButton(onClick = { reinitialiser() }) { Text("Réinitialiser") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { dialogueNom = true }, modifier = Modifier.weight(1f)) {
                    Text("Enregistrer")
                }
                Button(onClick = retour, modifier = Modifier.weight(1f)) {
                    Text(if (total == 0) "Aucune annonce" else "Voir ${Format.nombre(total)} annonce${if (total > 1) "s" else ""}")
                }
            }
        },
    ) { marges ->
        Column(
            modifier = Modifier.padding(marges).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (suggestions.isNotEmpty()) {
                Text("Aucune annonce avec ces critères. Pour en voir :", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error)
                suggestions.take(6).forEach { s ->
                    AssistChip(onClick = { appliquer(s.filtres) }, label = { Text("${s.libelle} : +${s.gain}") })
                }
                HorizontalDivider()
            }

            Titre("Zone")
            OutlinedButton(onClick = { dialogueCommunes = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (f.communes.isEmpty()) "Choisir des communes (sinon toute la province)"
                    else "${f.communes.size} commune(s) : ${f.communes.sorted().joinToString(", ")}"
                )
            }
            Text("ET/OU rayon autour d'un point", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ouvrirCarte) {
                    Text(
                        if (f.centreLat == null) "Choisir le point sur la carte"
                        else "Point : %.3f, %.3f".format(f.centreLat, f.centreLon)
                    )
                }
                if (f.centreLat != null) {
                    TextButton(onClick = { appliquer(f.copy(centreLat = null, centreLon = null, rayonKm = null)) }) {
                        Text("Retirer")
                    }
                }
            }
            if (f.centreLat != null) {
                val rayon = f.rayonKm ?: Filtres.RAYON_DEFAUT_KM
                var rayonLocal by remember(f.rayonKm) { mutableStateOf(rayon.toFloat()) }
                Text("Rayon : ${Format.km(rayonLocal.toDouble())}")
                Slider(
                    value = rayonLocal,
                    onValueChange = { rayonLocal = it },
                    onValueChangeFinished = { appliquer(f.copy(rayonKm = rayonLocal.toDouble())) },
                    valueRange = Filtres.RAYON_MIN_KM.toFloat()..Filtres.RAYON_MAX_KM.toFloat(),
                )
            }

            HorizontalDivider()
            Titre("Prix (${if (transaction == "location") "loyer mensuel" else "prix de vente"})")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampNombre("Min €", f.prixMin, Modifier.weight(1f)) { appliquer(f.copy(prixMin = it)) }
                ChampNombre("Max €", f.prixMax, Modifier.weight(1f)) { appliquer(f.copy(prixMax = it)) }
            }

            Titre("Type de bien")
            Puces(Filtres.TYPES, f.types, facettes.types) { appliquer(f.copy(types = it)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampNombre("Chambres min", f.chambresMin, Modifier.weight(1f)) { appliquer(f.copy(chambresMin = it)) }
                ChampNombre("Surface min m²", f.surfaceMin, Modifier.weight(1f)) { appliquer(f.copy(surfaceMin = it)) }
            }

            Titre("Détectées depuis")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filtres.PERIODES.forEach { (code, libelle) ->
                    FilterChip(
                        selected = f.periode == code,
                        onClick = { appliquer(f.copy(periode = code)) },
                        label = { Text("$libelle (${facettes.periodes[code] ?: 0})") },
                    )
                }
            }

            Titre("Options")
            Interrupteur("Jardin (${facettes.jardin})", f.jardin) { appliquer(f.copy(jardin = it)) }
            Interrupteur("Animaux acceptés (${facettes.animaux})", f.animaux) { appliquer(f.copy(animaux = it)) }
            Interrupteur("Meublé (${facettes.meuble})", f.meuble) { appliquer(f.copy(meuble = it)) }
            Interrupteur("Afficher les annonces refusées", f.afficherRefusees) { appliquer(f.copy(afficherRefusees = it)) }

            Titre("PEB au moins")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filtres.PEB.forEach { peb ->
                    FilterChip(
                        selected = f.pebMax == peb,
                        onClick = { appliquer(f.copy(pebMax = if (f.pebMax == peb) null else peb)) },
                        label = { Text("$peb (${facettes.pebs[peb] ?: 0})") },
                    )
                }
            }
            Text("Les annonces sans PEB restent affichées.", style = MaterialTheme.typography.bodySmall)

            Titre("Sources")
            Puces(Filtres.SOURCES, f.sources, facettes.sources) { appliquer(f.copy(sources = it)) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (dialogueCommunes) {
        DialogueCommunes(communes, f.communes, facettes.communes, fermer = { dialogueCommunes = false }) {
            appliquer(f.copy(communes = it))
        }
    }
    if (dialogueNom) {
        var nom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogueNom = false },
            title = { Text("Enregistrer la recherche") },
            text = {
                Column {
                    Text(
                        "${Format.nombre(total)} annonce(s) aujourd'hui. Tu recevras une notification pour " +
                            "chaque nouvelle annonce et chaque baisse de prix correspondant à ces critères."
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nom, onValueChange = { nom = it },
                        label = { Text("Nom") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = nom.isNotBlank(), onClick = {
                    enregistrerRecherche(RechercheSauvegardee.depuis(nom.trim(), transaction, f))
                    dialogueNom = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { dialogueNom = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Titre(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ChampNombre(libelle: String, valeur: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    var texte by remember { mutableStateOf(valeur?.toString() ?: "") }
    OutlinedTextField(
        value = texte,
        onValueChange = { t ->
            texte = t.filter { it.isDigit() }
            onChange(texte.toIntOrNull())
        },
        label = { Text(libelle) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun Puces(
    options: List<Pair<String, String>>, choisis: Set<String>, comptes: Map<String, Int>,
    onChange: (Set<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rangee.forEach { (code, libelle) ->
                    FilterChip(
                        selected = code in choisis,
                        onClick = { onChange(if (code in choisis) choisis - code else choisis + code) },
                        label = { Text("$libelle (${comptes[code] ?: 0})") },
                    )
                }
            }
        }
    }
}

@Composable
private fun Interrupteur(libelle: String, actif: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(libelle)
        Switch(checked = actif, onCheckedChange = onChange)
    }
}

@Composable
private fun DialogueCommunes(
    communes: List<Commune>,
    choisies: Set<String>,
    comptes: Map<String, Int>,
    fermer: () -> Unit,
    onChange: (Set<String>) -> Unit,
) {
    var selection by remember { mutableStateOf(choisies) }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text("Communes (${selection.size})") },
        text = {
            if (communes.isEmpty()) {
                Text("Liste des communes indisponible (vérifie la connexion à Supabase).")
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(communes, key = { it.nom }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = c.nom in selection, onCheckedChange = { coche ->
                                selection = if (coche) selection + c.nom else selection - c.nom
                            })
                            Spacer(Modifier.width(4.dp))
                            Text("${c.nom} (${comptes[c.nom] ?: 0})")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChange(selection); fermer() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { selection = emptySet() }) { Text("Tout décocher") } },
    )
}
