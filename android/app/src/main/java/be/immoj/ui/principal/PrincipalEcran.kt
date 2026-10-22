package be.immoj.ui.principal

import be.immoj.data.RechercheSauvegardee
import be.immoj.ui.filtres.FiltresFeuille
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import be.immoj.ImmoJApp
import be.immoj.ui.carte.CarteEcran
import be.immoj.ui.liste.ListeContenu
import be.immoj.ui.liste.ListeViewModel
import be.immoj.ui.recherches.RecherchesEcran

/** Modes d'affichage de la liste. */
val MODES_LISTE = listOf("cartes" to "Cartes", "detaille" to "Détaillé", "compact" to "Compact", "grille" to "Grille")

/** Snackbar de l'écran principal, partagé par la liste et la carte (« Zone retirée — Annuler »). */
val LocalSnackbar = compositionLocalOf<SnackbarHostState?> { null }

/** Écran principal : barre du haut, onglets Annonces / Carte / Recherches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalEcran(
    app: ImmoJApp,
    vm: ListeViewModel,
    ongletInitial: String,
    ouvrirDetail: (String) -> Unit,
    ouvrirParametres: () -> Unit,
    ouvrirSources: () -> Unit,
    ouvrirAjout: () -> Unit = {},
) {
    val etat by vm.etat.collectAsState()
    val portee = rememberCoroutineScope()
    val contexte = LocalContext.current
    var onglet by rememberSaveable { mutableStateOf(ongletInitial) }
    var menuOuvert by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var filtresOuverts by rememberSaveable { mutableStateOf(false) }
    var menuMode by rememberSaveable { mutableStateOf(false) }
    // Position de défilement conservée entre les onglets et au retour d'une fiche.
    val etatListe = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (etat.transaction == "location") "ImmoJ · Location" else "ImmoJ · Achat") },
                actions = {
                    IconButton(onClick = { filtresOuverts = true }) {
                        BadgedBox(badge = {
                            if (etat.filtres.nbActifs > 0) Badge { Text("${etat.filtres.nbActifs}") }
                        }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtres")
                        }
                    }
                    IconButton(onClick = { menuMode = true }) {
                        Icon(Icons.Default.ViewAgenda, contentDescription = "Mode d'affichage")
                    }
                    DropdownMenu(expanded = menuMode, onDismissRequest = { menuMode = false }) {
                        MODES_LISTE.forEach { (code, libelle) ->
                            DropdownMenuItem(text = { Text((if (etat.modeListe == code) "● " else "   ") + libelle) },
                                onClick = { menuMode = false; vm.changerMode(code) })
                        }
                    }
                    IconButton(onClick = { vm.charger(forcer = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = { menuOuvert = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                        DropdownMenuItem(text = { Text("Lancer une collecte maintenant") },
                            onClick = {
                                menuOuvert = false
                                portee.launch {
                                    Toast.makeText(contexte, vm.collecterMaintenant(), Toast.LENGTH_LONG).show()
                                }
                            })
                        DropdownMenuItem(text = { Text("+ Ajouter une annonce") },
                            onClick = { menuOuvert = false; ouvrirAjout() })
                        DropdownMenuItem(text = { Text("État des sources") },
                            onClick = { menuOuvert = false; ouvrirSources() })
                        DropdownMenuItem(text = { Text("Paramètres") },
                            onClick = { menuOuvert = false; ouvrirParametres() })
                    }
                },
            )
        },
        floatingActionButton = {
            if (onglet == "liste") {
                FloatingActionButton(onClick = ouvrirAjout) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter une annonce")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = onglet == "liste", onClick = { onglet = "liste" },
                    icon = {
                        BadgedBox(badge = {
                            if (etat.nbNonVues > 0) Badge { Text("${etat.nbNonVues}") }
                        }) { Icon(Icons.Default.List, contentDescription = null) }
                    }, label = { Text("Annonces") })
                NavigationBarItem(selected = onglet == "carte", onClick = { onglet = "carte" },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) }, label = { Text("Carte") })
                NavigationBarItem(selected = onglet == "recherches", onClick = { onglet = "recherches" },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null) }, label = { Text("Recherches") })
            }
        },
    ) { marges ->
        Box(modifier = Modifier.padding(marges).fillMaxSize()) {
            CompositionLocalProvider(LocalSnackbar provides snackbar) {
            when (onglet) {
                "carte" -> CarteEcran(etat = etat, vm = vm, ouvrirDetail = ouvrirDetail)
                "recherches" -> RecherchesEcran(app = app, vm = vm, appliquer = { r ->
                    r.transaction?.let { vm.changerTransaction(it) }
                    vm.changerFiltres(r.versFiltres())
                    onglet = "liste"
                })
                else -> ListeContenu(etat = etat, vm = vm, ouvrirDetail = ouvrirDetail, etatListe = etatListe,
                    ouvrirFiltres = { filtresOuverts = true }, ouvrirCarte = { onglet = "carte" })
            }
            }
        }
    }
    if (filtresOuverts) {
        FiltresFeuille(
            filtres = etat.filtres, index = etat.index, transaction = etat.transaction, communes = etat.communes,
            appliquer = { vm.changerFiltres(it) },
            enregistrerRecherche = { recherche: RechercheSauvegardee ->
                portee.launch {
                    val message = try {
                        app.depot.creerRecherche(recherche)
                        "Recherche « ${recherche.nom} » enregistrée"
                    } catch (e: Exception) {
                        be.immoj.ui.liste.messageErreur(e)
                    }
                    Toast.makeText(contexte, message, Toast.LENGTH_LONG).show()
                }
            },
            ouvrirCarte = { onglet = "carte" },
            fermer = { filtresOuverts = false },
        )
    }

}
