package be.immoj.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import be.immoj.ImmoJApp
import be.immoj.ui.detail.DetailEcran
import be.immoj.ui.liste.ListeViewModel
import be.immoj.ui.parametres.ParametresEcran
import be.immoj.ui.principal.PrincipalEcran
import be.immoj.ui.sources.SourcesEcran

/** Les écrans de l'appli et leurs routes. */
object Routes {
    const val PRINCIPAL = "principal?onglet={onglet}"
    const val DETAIL = "detail/{id}"
    const val PARAMETRES = "parametres"
    const val SOURCES = "sources"
    fun principal(onglet: String = "liste") = "principal?onglet=$onglet"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun AppNavigation(app: ImmoJApp) {
    val nav = rememberNavController()
    val reglages by app.preferences.flux.collectAsState(initial = null)
    // Tant que les réglages ne sont pas lus, on n'affiche rien (une fraction de seconde).
    if (reglages == null) return
    // Sans configuration Supabase, l'appli fonctionne en mode « sans compte »
    // (fichier publié par GitHub) : on va directement à la liste.
    val depart = Routes.PRINCIPAL

    // ViewModel partagé par la liste, la carte et les filtres (durée de vie : l'activité).
    val vm: ListeViewModel = viewModel { ListeViewModel(app.depot, app.preferences) }

    NavHost(navController = nav, startDestination = depart) {
        composable(
            Routes.PRINCIPAL,
            arguments = listOf(navArgument("onglet") { type = NavType.StringType; defaultValue = "liste" }),
        ) { entree ->
            PrincipalEcran(
                app = app,
                vm = vm,
                ongletInitial = entree.arguments?.getString("onglet") ?: "liste",
                ouvrirDetail = { id -> nav.navigate(Routes.detail(id)) },
                ouvrirParametres = { nav.navigate(Routes.PARAMETRES) },
                ouvrirSources = { nav.navigate(Routes.SOURCES) },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entree ->
            val id = entree.arguments?.getString("id") ?: return@composable
            val etat by vm.etat.collectAsState()
            DetailEcran(
                app = app, id = id, retour = { nav.popBackStack() },
                onStatutChange = { idA, s, n -> vm.appliquerStatut(idA, s, n) },
                voisins = etat.annonces.map { it.id },
                index = etat.index,
                centreLat = etat.filtres.centreLat, centreLon = etat.filtres.centreLon,
                ouvrirAnnonce = { autre -> nav.navigate(Routes.detail(autre)) },
            )
        }
        composable(Routes.PARAMETRES) {
            ParametresEcran(
                app = app,
                retour = {
                    // Après la première configuration, on va à la liste au lieu de « revenir ».
                    if (!nav.popBackStack()) nav.navigate(Routes.principal()) { popUpTo(0) }
                },
            )
        }
        composable(Routes.SOURCES) {
            val etat by vm.etat.collectAsState()
            SourcesEcran(app = app, retour = { nav.popBackStack() }, index = etat.index)
        }
    }
}
