package be.immoj.ui.principal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
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

/** Écran principal : barre du haut, onglets Annonces / Carte / Recherches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalEcran(
    app: ImmoJApp,
    vm: ListeViewModel,
    ongletInitial: String,
    ouvrirDetail: (String) -> Unit,
    ouvrirFiltres: () -> Unit,
    ouvrirParametres: () -> Unit,
    ouvrirSources: () -> Unit,
) {
    val etat by vm.etat.collectAsState()
    var onglet by rememberSaveable { mutableStateOf(ongletInitial) }
    var menuOuvert by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (etat.transaction == "location") "ImmoJ · Location" else "ImmoJ · Achat") },
                actions = {
                    IconButton(onClick = ouvrirFiltres) {
                        BadgedBox(badge = {
                            if (etat.filtres.nbActifs > 0) Badge { Text("${etat.filtres.nbActifs}") }
                        }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtres")
                        }
                    }
                    IconButton(onClick = { vm.charger() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = { menuOuvert = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                        DropdownMenuItem(text = { Text("État des sources") },
                            onClick = { menuOuvert = false; ouvrirSources() })
                        DropdownMenuItem(text = { Text("Paramètres") },
                            onClick = { menuOuvert = false; ouvrirParametres() })
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = onglet == "liste", onClick = { onglet = "liste" },
                    icon = { Icon(Icons.Default.List, contentDescription = null) }, label = { Text("Annonces") })
                NavigationBarItem(selected = onglet == "carte", onClick = { onglet = "carte" },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) }, label = { Text("Carte") })
                NavigationBarItem(selected = onglet == "recherches", onClick = { onglet = "recherches" },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null) }, label = { Text("Recherches") })
            }
        },
    ) { marges ->
        Box(modifier = Modifier.padding(marges).fillMaxSize()) {
            when (onglet) {
                "carte" -> CarteEcran(
                    annonces = etat.annonces,
                    centreLat = etat.filtres.centreLat,
                    centreLon = etat.filtres.centreLon,
                    rayonKm = etat.filtres.rayonKm,
                    onPointChoisi = { lat, lon -> vm.definirCentre(lat, lon) },
                    ouvrirDetail = ouvrirDetail,
                )
                "recherches" -> RecherchesEcran(app = app, appliquer = { r ->
                    r.transaction?.let { vm.changerTransaction(it) }
                    vm.changerFiltres(r.versFiltres())
                    onglet = "liste"
                })
                else -> ListeContenu(etat = etat, vm = vm, ouvrirDetail = ouvrirDetail)
            }
        }
    }
}
