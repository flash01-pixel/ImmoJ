package be.immoj.ui.filtres

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.immoj.data.Annonce
import be.immoj.data.Catalogue
import be.immoj.data.Commune
import be.immoj.data.Filtres
import be.immoj.data.correspond
import be.immoj.data.RechercheSauvegardee
import be.immoj.util.Format
import kotlin.math.roundToInt

/**
 * Filtres en feuille du bas : sections repliables, curseurs à deux poignées
 * (prix, surface) avec champs et préréglages, communes par arrondissement avec
 * recherche et comptage, critères du lot 3. Tout s'applique immédiatement ;
 * chaque option affiche son nombre ; sans résultat, suggestions avec gain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltresFeuille(
    filtres: Filtres,
    index: List<Annonce>,
    transaction: String,
    communes: List<Commune>,
    appliquer: (Filtres) -> Unit,
    enregistrerRecherche: (RechercheSauvegardee) -> Unit,
    ouvrirCarte: () -> Unit,
    fermer: () -> Unit,
) {
    val f = filtres
    val feuille = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val facettes = remember(index, transaction, f) { Catalogue.facettes(index, transaction, f) }
    val suggestions = remember(index, transaction, f) {
        if (facettes.total == 0) Catalogue.suggestions(index, transaction, f) else emptyList()
    }
    val total = facettes.total
    var dialogueCommunes by remember { mutableStateOf(false) }
    var dialogueNom by remember { mutableStateOf(false) }
    var avantReinit by remember { mutableStateOf<Filtres?>(null) }
    val location = transaction == "location"
    val prixPlafond = if (location) 3000f else 1_000_000f
    val prixPas = if (location) 50 else 10_000

    ModalBottomSheet(onDismissRequest = fermer, sheetState = feuille) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Filtres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                avantReinit?.let { av ->
                    TextButton(onClick = { appliquer(av); avantReinit = null }) { Text("Annuler") }
                }
                if (f.nbActifs > 0) TextButton(onClick = {
                    avantReinit = f
                    appliquer(Filtres(afficherRefusees = f.afficherRefusees))
                }) { Text("Réinitialiser") }
            }
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggestions.isNotEmpty()) {
                    Text("Aucune annonce avec ces critères. Pour en voir :", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    suggestions.take(6).forEach { s ->
                        AssistChip(onClick = { appliquer(s.filtres) }, label = { Text("${s.libelle} : +${s.gain}") })
                    }
                    HorizontalDivider()
                }

                Section("Zone", ouvertParDefaut = true) {
                    OutlinedButton(onClick = { dialogueCommunes = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (f.communes.isEmpty()) "Choisir des communes (sinon toute la province)"
                        else "${f.communes.size} commune(s) : ${f.communes.sorted().joinToString(", ")}")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { fermer(); ouvrirCarte() }) {
                            Text(if (f.centreLat == null) "Rayon sur la carte" else "Rayon ${Format.km(f.rayonKm ?: Filtres.RAYON_DEFAUT_KM)} sur la carte")
                        }
                        if (f.rayonDefini) TextButton(onClick = { appliquer(f.copy(centreLat = null, centreLon = null, rayonKm = null)) }) {
                            Text("Retirer")
                        }
                    }
                    if (f.centreLat != null) {
                        var rayonLocal by remember(f.rayonKm) { mutableStateOf((f.rayonKm ?: Filtres.RAYON_DEFAUT_KM).toFloat()) }
                        Text("Rayon : ${Format.km(rayonLocal.toDouble())}")
                        Slider(value = rayonLocal, onValueChange = { rayonLocal = it },
                            onValueChangeFinished = { appliquer(f.copy(rayonKm = rayonLocal.toDouble())) },
                            valueRange = Filtres.RAYON_MIN_KM.toFloat()..Filtres.RAYON_MAX_KM.toFloat())
                    }
                }

                Section("Prix (${if (location) "loyer mensuel" else "prix de vente"})", ouvertParDefaut = true) {
                    Plage(
                        min = f.prixMin, max = f.prixMax, plafond = prixPlafond, pas = prixPas, unite = "€",
                        onChange = { mn, mx -> appliquer(f.copy(prixMin = mn, prixMax = mx)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        (if (location) Filtres.PRIX_LOCATION else Filtres.PRIX_ACHAT).take(4).forEach { p ->
                            FilterChip(selected = f.prixMax == p, onClick = { appliquer(f.copy(prixMax = if (f.prixMax == p) null else p)) },
                                label = { Text("≤ ${Format.nombre(p)}") })
                        }
                    }
                    if (location) {
                        ChampNombre("Loyer charges comprises ≤ €", f.chargesComprisesMax, Modifier.fillMaxWidth()) {
                            appliquer(f.copy(chargesComprisesMax = it))
                        }
                    }
                    Interrupteur("Prix baissé récemment (${facettesPrixBaisse(index, transaction, f)})", f.prixBaisse) {
                        appliquer(f.copy(prixBaisse = it))
                    }
                }

                Section("Type et annonceur", ouvertParDefaut = true) {
                    Puces(Filtres.TYPES, f.types, facettes.types) { appliquer(f.copy(types = it)) }
                    Text("Annonceur", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("particulier" to "Particulier", "agence" to "Agence", "notaire" to "Notaire").forEach { (code, lib) ->
                            val n = index.count { it.actif && it.transaction == transaction && it.typeAnnonceur == code }
                            FilterChip(selected = code in f.annonceur,
                                onClick = { appliquer(f.copy(annonceur = if (code in f.annonceur) f.annonceur - code else f.annonceur + code)) },
                                label = { Text("$lib ($n)") })
                        }
                    }
                }

                Section("Taille") {
                    Text("Chambres", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChampNombre("Min", f.chambresMin, Modifier.weight(1f)) { appliquer(f.copy(chambresMin = it)) }
                        ChampNombre("Max", f.chambresMax, Modifier.weight(1f)) { appliquer(f.copy(chambresMax = it)) }
                    }
                    Text("Surface (m²)", style = MaterialTheme.typography.labelLarge)
                    Plage(min = f.surfaceMin, max = f.surfaceMax, plafond = 400f, pas = 5, unite = "m²",
                        onChange = { mn, mx -> appliquer(f.copy(surfaceMin = mn, surfaceMax = mx)) })
                }

                Section("Options") {
                    Interrupteur("Jardin (${facettes.jardin})", f.jardin) { appliquer(f.copy(jardin = it)) }
                    Interrupteur("Terrasse", f.terrasse) { appliquer(f.copy(terrasse = it)) }
                    Interrupteur("Garage / parking", f.garage) { appliquer(f.copy(garage = it)) }
                    Interrupteur("Animaux acceptés (${facettes.animaux})", f.animaux) { appliquer(f.copy(animaux = it)) }
                    Interrupteur("Meublé (${facettes.meuble})", f.meuble) { appliquer(f.copy(meuble = it)) }
                    Interrupteur("Avec photos", f.avecPhotos) { appliquer(f.copy(avecPhotos = it)) }
                    var dispo by remember(f.disponibleAvant) { mutableStateOf(f.disponibleAvant ?: "") }
                    OutlinedTextField(value = dispo, onValueChange = { t ->
                        dispo = t
                        if (t.isBlank()) appliquer(f.copy(disponibleAvant = null))
                        else if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(t)) appliquer(f.copy(disponibleAvant = t))
                    }, label = { Text("Disponible avant le (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                Section("PEB au moins") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Filtres.PEB.forEach { peb ->
                            FilterChip(selected = f.pebMax == peb,
                                onClick = { appliquer(f.copy(pebMax = if (f.pebMax == peb) null else peb)) },
                                label = { Text("$peb (${facettes.pebs[peb] ?: 0})") })
                        }
                    }
                    Text("Les annonces sans PEB restent affichées.", style = MaterialTheme.typography.bodySmall)
                }

                Section("Détectées depuis") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Filtres.PERIODES.forEach { (code, libelle) ->
                            FilterChip(selected = f.periode == code, onClick = { appliquer(f.copy(periode = code)) },
                                label = { Text("$libelle (${facettes.periodes[code] ?: 0})") })
                        }
                    }
                }

                Section("Sources") {
                    Puces(Filtres.SOURCES, f.sources, facettes.sources) { appliquer(f.copy(sources = it)) }
                }

                Section("Mes statuts") {
                    Interrupteur("Masquer les annonces déjà vues", f.masquerVues) { appliquer(f.copy(masquerVues = it)) }
                    Interrupteur("Afficher les annonces refusées", f.afficherRefusees) { appliquer(f.copy(afficherRefusees = it)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dialogueNom = true }, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
                Button(onClick = fermer, modifier = Modifier.weight(1f)) {
                    Text(if (total == 0) "Aucune annonce" else "Voir ${Format.nombre(total)} annonce${if (total > 1) "s" else ""}")
                }
            }
        }
    }

    if (dialogueCommunes) {
        DialogueCommunesArrondissements(communes, f.communes, facettes.communes, fermer = { dialogueCommunes = false }) {
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
                    Text("${Format.nombre(total)} annonce(s) aujourd'hui. Tu recevras une notification pour chaque " +
                        "nouvelle annonce et chaque baisse de prix correspondant à ces critères (zone, prix, type, " +
                        "chambres, surface, jardin, animaux, meublé, PEB, sources).")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = nom, onValueChange = { nom = it }, label = { Text("Nom") }, singleLine = true)
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

private fun facettesPrixBaisse(index: List<Annonce>, transaction: String, f: Filtres): Int =
    index.count { it.actif && it.transaction == transaction && it.badgePrixBaisse && f.copy(prixBaisse = false).correspond(it) }

/** Section repliable. */
@Composable
private fun Section(titre: String, ouvertParDefaut: Boolean = false, contenu: @Composable () -> Unit) {
    var ouvert by rememberSaveable(titre) { mutableStateOf(ouvertParDefaut) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { ouvert = !ouvert }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titre, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (ouvert) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (ouvert) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { contenu() }
        HorizontalDivider()
    }
}

/** Curseur à deux poignées + deux champs (min / max). `plafond` = valeur max du curseur. */
@Composable
private fun Plage(min: Int?, max: Int?, plafond: Float, pas: Int, unite: String, onChange: (Int?, Int?) -> Unit) {
    var plage by remember(min, max) { mutableStateOf((min?.toFloat() ?: 0f)..(max?.toFloat() ?: plafond)) }
    RangeSlider(
        value = plage,
        onValueChange = { plage = it },
        onValueChangeFinished = {
            val mn = (plage.start / pas).roundToInt() * pas
            val mx = (plage.endInclusive / pas).roundToInt() * pas
            onChange(if (mn <= 0) null else mn, if (mx >= plafond.toInt()) null else mx)
        },
        valueRange = 0f..plafond,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChampNombre("Min $unite", min, Modifier.weight(1f)) { onChange(it, max) }
        ChampNombre("Max $unite", max, Modifier.weight(1f)) { onChange(min, it) }
    }
}

@Composable
private fun ChampNombre(libelle: String, valeur: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    var texte by remember(valeur) { mutableStateOf(valeur?.toString() ?: "") }
    OutlinedTextField(
        value = texte,
        onValueChange = { t -> texte = t.filter { it.isDigit() }; onChange(texte.toIntOrNull()) },
        label = { Text(libelle) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun Puces(options: List<Pair<String, String>>, choisis: Set<String>, comptes: Map<String, Int>, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rangee.forEach { (code, libelle) ->
                    FilterChip(selected = code in choisis,
                        onClick = { onChange(if (code in choisis) choisis - code else choisis + code) },
                        label = { Text("$libelle (${comptes[code] ?: 0})") })
                }
            }
        }
    }
}

@Composable
private fun Interrupteur(libelle: String, actif: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(libelle, modifier = Modifier.weight(1f))
        Switch(checked = actif, onCheckedChange = onChange)
    }
}

/** Communes avec recherche, groupées par arrondissement, comptées. */
@Composable
private fun DialogueCommunesArrondissements(
    communes: List<Commune>, choisies: Set<String>, comptes: Map<String, Int>,
    fermer: () -> Unit, onChange: (Set<String>) -> Unit,
) {
    var selection by remember { mutableStateOf(choisies) }
    var recherche by remember { mutableStateOf("") }
    val connues = communes.map { it.nom }.toSet()
    val groupes = Filtres.ARRONDISSEMENTS.mapValues { (_, noms) -> noms.filter { it in connues || communes.isEmpty() } }
    val autres = communes.map { it.nom }.filter { nom -> Filtres.ARRONDISSEMENTS.values.none { nom in it } }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text("Communes (${selection.size})") },
        text = {
            Column {
                OutlinedTextField(value = recherche, onValueChange = { recherche = it }, singleLine = true,
                    placeholder = { Text("Rechercher une commune") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(modifier = Modifier.height(380.dp)) {
                    (groupes + ("Autres" to autres)).forEach { (arrondissement, noms) ->
                        val visibles = noms.filter { recherche.isBlank() || it.contains(recherche, ignoreCase = true) }
                        if (visibles.isEmpty()) return@forEach
                        item(key = "arr-$arrondissement") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text("Arrondissement de $arrondissement", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    selection = if (visibles.all { it in selection }) selection - visibles.toSet() else selection + visibles
                                }) { Text(if (visibles.all { it in selection }) "Aucune" else "Toutes") }
                            }
                        }
                        items(visibles, key = { it }) { nom ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { selection = if (nom in selection) selection - nom else selection + nom }) {
                                Checkbox(checked = nom in selection, onCheckedChange = { coche ->
                                    selection = if (coche) selection + nom else selection - nom
                                })
                                Spacer(Modifier.width(4.dp))
                                Text("$nom (${comptes[nom] ?: 0})")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChange(selection); fermer() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { selection = emptySet() }) { Text("Tout décocher") } },
    )
}
