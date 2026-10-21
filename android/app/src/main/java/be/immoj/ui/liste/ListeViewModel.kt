package be.immoj.ui.liste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.immoj.data.Annonce
import be.immoj.data.Commune
import be.immoj.data.Depot
import be.immoj.data.Filtres
import be.immoj.data.GitHubActions
import be.immoj.data.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran liste. */
data class ListeEtat(
    val transaction: String = "location",
    val tri: String = "recent",
    val filtres: Filtres = Filtres(),
    val annonces: List<Annonce> = emptyList(),
    val communes: List<Commune> = emptyList(),
    val chargement: Boolean = false,
    val erreur: String? = null,
    val photosEnListe: Boolean = true,
)

class ListeViewModel(private val depot: Depot, private val preferences: Preferences) : ViewModel() {
    private val _etat = MutableStateFlow(ListeEtat())
    val etat: StateFlow<ListeEtat> = _etat.asStateFlow()

    init {
        viewModelScope.launch {
            val r = preferences.lire()
            _etat.update { it.copy(transaction = r.transaction, tri = r.tri, filtres = r.filtres) }
            charger()
            runCatching { depot.communes() }.onSuccess { c -> _etat.update { it.copy(communes = c) } }
        }
        // Les réglages d'affichage suivent les paramètres en direct.
        viewModelScope.launch {
            preferences.flux.collect { r -> _etat.update { it.copy(photosEnListe = r.photosEnListe) } }
        }
    }

    /**
     * Recharge la liste. Avec `forcer`, l'appli retélécharge vraiment les données
     * (mode sans compte : nouveau fichier ; Supabase : nouvelle requête) au lieu
     * de réutiliser le cache de quelques minutes.
     */
    fun charger(forcer: Boolean = false) {
        viewModelScope.launch {
            _etat.update { it.copy(chargement = true, erreur = null) }
            try {
                val e = _etat.value
                if (forcer) depot.rafraichir()
                val liste = depot.listerAnnonces(e.transaction, e.tri, e.filtres)
                _etat.update { it.copy(annonces = liste, chargement = false) }
            } catch (e: Exception) {
                _etat.update { it.copy(chargement = false, erreur = messageErreur(e)) }
            }
        }
    }

    /**
     * Demande à GitHub de lancer une collecte tout de suite (jeton GitHub dans les
     * paramètres), puis recharge. Renvoie un message pour l'utilisateur.
     */
    suspend fun collecterMaintenant(): String {
        val jeton = preferences.lire().githubToken
        if (jeton.isBlank()) return "Ajoute un jeton GitHub dans les paramètres pour lancer une collecte."
        return try {
            GitHubActions.lancerCollecte(jeton)
            "Collecte lancée sur GitHub : les nouvelles annonces arrivent dans 3 à 5 minutes " +
                "(tire la liste vers le bas pour actualiser)."
        } catch (e: Exception) {
            "Impossible de lancer la collecte : ${e.message}"
        }
    }

    fun changerTransaction(transaction: String) {
        if (transaction == _etat.value.transaction) return
        _etat.update { it.copy(transaction = transaction) }
        viewModelScope.launch { preferences.enregistrerTransaction(transaction) }
        charger()
    }

    fun changerFiltres(filtres: Filtres) {
        _etat.update { it.copy(filtres = filtres) }
        viewModelScope.launch { preferences.enregistrerFiltres(filtres) }
        charger()
    }

    /** Change le rayon (km) autour du centre déjà choisi (curseur ou poignée sur la carte). */
    fun definirRayon(km: Double) {
        val f = _etat.value.filtres
        if (f.centreLat == null) return
        changerFiltres(f.copy(rayonKm = km.coerceIn(0.5, 60.0)))
    }

    /** Supprime le cercle de recherche (retour à toute la province / aux communes cochées). */
    fun retirerRayon() {
        changerFiltres(_etat.value.filtres.copy(centreLat = null, centreLon = null, rayonKm = null))
    }

    /** Définit le centre du rayon (depuis la carte) sans changer le reste. */
    fun definirCentre(lat: Double, lon: Double) {
        val f = _etat.value.filtres
        changerFiltres(f.copy(centreLat = lat, centreLon = lon, rayonKm = f.rayonKm ?: 15.0))
    }

    fun changerTri(tri: String) {
        if (tri == _etat.value.tri) return
        _etat.update { it.copy(tri = tri) }
        viewModelScope.launch { preferences.enregistrerTri(tri) }
        charger()
    }
}

/** Transforme une exception technique en phrase lisible. */
fun messageErreur(e: Exception): String = when {
    e is IllegalStateException -> e.message ?: "Erreur"
    e is java.net.UnknownHostException -> "Pas de connexion Internet, ou URL Supabase incorrecte."
    e is java.net.SocketTimeoutException -> "Supabase ne répond pas (délai dépassé)."
    e is retrofit2.HttpException && e.code() == 401 -> "Clé Supabase refusée (401). Vérifie les Paramètres."
    e is retrofit2.HttpException && e.code() == 404 -> "Vue introuvable (404) : schema.sql a-t-il été exécuté ?"
    e is retrofit2.HttpException -> "Erreur Supabase HTTP ${e.code()}"
    else -> e.message ?: e.javaClass.simpleName
}
