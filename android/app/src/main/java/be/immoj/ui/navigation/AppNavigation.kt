package be.immoj.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import be.immoj.ImmoJApp
import be.immoj.ui.detail.DetailEcran
import be.immoj.ui.filtres.FiltresEcran
import be.immoj.ui.liste.ListeViewModel
import be.immoj.ui.liste.messageErreur
import be.immoj.ui.parametres.ParametresEcran
import be.immoj.ui.principal.PrincipalEcran
import be.immoj.ui.sources.SourcesEcran
import kotlinx.coroutines.launch

/** Les écrans de l'appli et leurs routes. */
object Routes {
    const val PRINCIPAL = "principal?onglet={onglet}"
    const val DETAIL = "detail/{id}"
    const val FILTRES = "filtres"
    const val PARAMETRES = "parametres"
    const val SOURCES = "sources"
    fun principal(onglet: String = "liste") = "principal?onglet=$onglet"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun AppNavigation(app: ImmoJApp) {
    val nav = rememberNavController()
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val reglages by app.preferences.flux.collectAsState(initial = null)
    // Tant que les réglages ne sont pas lus, on n'affiche rien (une fraction de seconde).
    val r = reglages ?: return
    // Sans configuration Supabase, l'appli fonctionne en mode « sans compte »
    // (fichier publié par GitHub) : on va directement à la liste.
    @Suppress("UNUSED_VARIABLE") val reglagesLus = r
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
                ouvrirFiltres = { nav.navigate(Routes.FILTRES) },
                ouvrirParametres = { nav.navigate(Routes.PARAMETRES) },
                ouvrirSources = { nav.navigate(Routes.SOURCES) },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entree ->
            val id = entree.arguments?.getString("id") ?: return@composable
            DetailEcran(app = app, id = id, retour = { nav.popBackStack() })
        }
        composable(Routes.FILTRES) {
            val etat by vm.etat.collectAsState()
            FiltresEcran(
                initial = etat.filtres,
                communes = etat.communes,
                transaction = etat.transaction,
                appliquer = { vm.changerFiltres(it) },
                enregistrerRecherche = { recherche ->
                    portee.launch {
                        val message = try {
                            app.depot.creerRecherche(recherche)
                            "Recherche « ${recherche.nom} » enregistrée"
                        } catch (e: Exception) {
                            messageErreur(e)
                        }
                        Toast.makeText(contexte, message, Toast.LENGTH_LONG).show()
                    }
                },
                ouvrirCarte = {
                    nav.navigate(Routes.principal("carte")) { popUpTo(0) }
                },
                retour = { nav.popBackStack() },
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
            SourcesEcran(app = app, retour = { nav.popBackStack() })
        }
    }
}
