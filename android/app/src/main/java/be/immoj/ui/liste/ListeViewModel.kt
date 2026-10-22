package be.immoj.ui.liste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.immoj.data.Annonce
import be.immoj.data.Catalogue
import be.immoj.data.Commune
import be.immoj.data.Depot
import be.immoj.data.Filtres
import be.immoj.data.GitHubActions
import be.immoj.data.Preferences
import be.immoj.data.StatutPerso
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État partagé par la liste, la carte, les filtres et les recherches.
 * L'index (toutes les annonces actives) est chargé une fois ; filtrage, tri et
 * compteurs se font en mémoire, instantanément, sans réseau.
 */
data class ListeEtat(
    val transaction: String = "location",
    val tri: String = "recent",
    val filtres: Filtres = Filtres(),
    val index: List<Annonce> = emptyList(),          // toutes les annonces actives
    val annonces: List<Annonce> = emptyList(),       // celles de la transaction, filtrées, triées
    val communes: List<Commune> = emptyList(),
    val chargement: Boolean = false,
    val erreur: String? = null,
    val photosEnListe: Boolean = true,
    val dateDonnees: String? = null,                 // horodatage de la dernière collecte vue
) {
    /** Totaux par transaction : « Location (397) · Achat (1 189) ». */
    val totaux: Map<String, Int> get() = Catalogue.totaux(index, filtres.afficherRefusees)
    val nbTotalTransaction: Int get() = totaux[transaction] ?: 0
    val nbNouvelles: Int get() = Catalogue.nouvelles(annonces)
    val nbSansPosition: Int get() = Catalogue.sansPosition(annonces)
    /** Nouveautés non vues, toutes transactions (pastille de l'onglet Annonces). */
    val nbNonVues: Int get() = Catalogue.nouvelles(index.filter { it.transaction == transaction })
    val nbRefuseesMasquees: Int
        get() = if (filtres.afficherRefusees) 0
        else index.count { it.actif && it.transaction == transaction && it.statut == StatutPerso.REFUSE.code }
}

class ListeViewModel(private val depot: Depot, private val preferences: Preferences) : ViewModel() {
    private val _etat = MutableStateFlow(ListeEtat())
    val etat: StateFlow<ListeEtat> = _etat.asStateFlow()
    private var travail: Job? = null

    init {
        viewModelScope.launch {
            val r = preferences.lire()
            _etat.update { it.copy(transaction = r.transaction, tri = r.tri, filtres = r.filtres) }
            // Démarrage instantané depuis le cache, puis vraie mise à jour.
            val cache = runCatching { depot.indexEnCache() }.getOrDefault(emptyList())
            if (cache.isNotEmpty()) remplacerIndex(cache)
            charger(forcer = cache.isEmpty())
            runCatching { depot.communes() }.onSuccess { c -> _etat.update { it.copy(communes = c) } }
        }
        viewModelScope.launch {
            preferences.flux.collect { r -> _etat.update { it.copy(photosEnListe = r.photosEnListe) } }
        }
    }

    /** Remplace l'index et recalcule la liste affichée (sans réseau). */
    private fun remplacerIndex(index: List<Annonce>) {
        _etat.update { it.copy(index = index, dateDonnees = Catalogue.dateDonnees(index)) }
        recalculer()
    }

    private fun recalculer() {
        _etat.update { e ->
            e.copy(annonces = Catalogue.trier(Catalogue.filtrer(e.index, e.transaction, e.filtres), e.tri))
        }
    }

    /**
     * Recharge l'index. Avec `forcer`, retéléchargement réel (sinon le cache de
     * quelques minutes suffit). Un chargement en cours est annulé : le dernier
     * demandé est toujours celui qui gagne.
     */
    fun charger(forcer: Boolean = false) {
        travail?.cancel()
        travail = viewModelScope.launch {
            _etat.update { it.copy(chargement = true, erreur = null) }
            try {
                remplacerIndex(depot.index(forcer))
                _etat.update { it.copy(chargement = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.update { it.copy(chargement = false, erreur = if (it.index.isEmpty()) messageErreur(e) else null) }
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
        recalculer()
    }

    /** Les filtres s'appliquent immédiatement, en mémoire : rien à « valider ». */
    fun changerFiltres(filtres: Filtres) {
        _etat.update { it.copy(filtres = filtres) }
        viewModelScope.launch { preferences.enregistrerFiltres(filtres) }
        recalculer()
    }

    /** Change le rayon (km) autour du centre déjà choisi (curseur ou poignée sur la carte). */
    fun definirRayon(km: Double) {
        val f = _etat.value.filtres
        if (f.centreLat == null) return
        changerFiltres(f.copy(rayonKm = km.coerceIn(Filtres.RAYON_MIN_KM, Filtres.RAYON_MAX_KM)))
    }

    /** Supprime le cercle de recherche (retour à toute la province / aux communes cochées). */
    fun retirerRayon() {
        changerFiltres(_etat.value.filtres.copy(centreLat = null, centreLon = null, rayonKm = null))
    }

    /** Définit le centre du rayon (depuis la carte) sans changer le reste. */
    fun definirCentre(lat: Double, lon: Double) {
        val f = _etat.value.filtres
        changerFiltres(f.copy(centreLat = lat, centreLon = lon, rayonKm = f.rayonKm ?: Filtres.RAYON_DEFAUT_KM))
    }

    fun changerTri(tri: String) {
        if (tri == _etat.value.tri) return
        _etat.update { it.copy(tri = tri) }
        viewModelScope.launch { preferences.enregistrerTri(tri) }
        recalculer()
    }

    /** Un statut a changé (depuis la fiche) : la liste le reflète tout de suite. */
    fun appliquerStatut(id: String, statut: StatutPerso, note: String?) {
        _etat.update { e ->
            e.copy(index = e.index.map { a ->
                if (a.id == id) a.copy(statut = statut.code, note = note, badgeNouveau = false) else a
            })
        }
        recalculer()
    }

    /** Nombre d'annonces qu'auraient des filtres donnés (aperçu dans l'écran Filtres). */
    fun compter(filtres: Filtres): Int =
        Catalogue.filtrer(_etat.value.index, _etat.value.transaction, filtres).size
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
