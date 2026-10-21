package be.immoj.ui.liste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.immoj.data.Annonce
import be.immoj.data.Depot
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
    val annonces: List<Annonce> = emptyList(),
    val chargement: Boolean = false,
    val erreur: String? = null,
)

class ListeViewModel(private val depot: Depot, private val preferences: Preferences) : ViewModel() {
    private val _etat = MutableStateFlow(ListeEtat())
    val etat: StateFlow<ListeEtat> = _etat.asStateFlow()

    init {
        viewModelScope.launch {
            val r = preferences.lire()
            _etat.update { it.copy(transaction = r.transaction, tri = r.tri) }
            charger()
        }
    }

    fun charger() {
        viewModelScope.launch {
            _etat.update { it.copy(chargement = true, erreur = null) }
            try {
                val liste = depot.listerAnnonces(_etat.value.transaction, _etat.value.tri)
                _etat.update { it.copy(annonces = liste, chargement = false) }
            } catch (e: Exception) {
                _etat.update { it.copy(chargement = false, erreur = messageErreur(e)) }
            }
        }
    }

    fun changerTransaction(transaction: String) {
        if (transaction == _etat.value.transaction) return
        _etat.update { it.copy(transaction = transaction) }
        viewModelScope.launch { preferences.enregistrerTransaction(transaction) }
        charger()
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
