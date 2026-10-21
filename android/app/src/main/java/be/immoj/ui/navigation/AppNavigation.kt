package be.immoj.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import be.immoj.ImmoJApp
import be.immoj.ui.detail.DetailEcran
import be.immoj.ui.liste.ListeEcran
import be.immoj.ui.parametres.ParametresEcran
import be.immoj.ui.sources.SourcesEcran

/** Les écrans de l'appli et leurs routes. */
object Routes {
    const val LISTE = "liste"
    const val DETAIL = "detail/{id}"
    const val PARAMETRES = "parametres"
    const val SOURCES = "sources"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun AppNavigation(app: ImmoJApp) {
    val nav = rememberNavController()
    val reglages by app.preferences.flux.collectAsState(initial = null)
    // Tant que les réglages ne sont pas lus, on n'affiche rien (une fraction de seconde).
    val r = reglages ?: return
    // Premier lancement : on commence par l'écran de configuration.
    val depart = if (r.configure) Routes.LISTE else Routes.PARAMETRES

    NavHost(navController = nav, startDestination = depart) {
        composable(Routes.LISTE) {
            ListeEcran(
                app = app,
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
            DetailEcran(app = app, id = id, retour = { nav.popBackStack() })
        }
        composable(Routes.PARAMETRES) {
            ParametresEcran(
                app = app,
                retour = {
                    // Après la première configuration, on va à la liste au lieu de « revenir ».
                    if (!nav.popBackStack()) nav.navigate(Routes.LISTE) { popUpTo(0) }
                },
            )
        }
        composable(Routes.SOURCES) {
            SourcesEcran(app = app, retour = { nav.popBackStack() })
        }
    }
}
