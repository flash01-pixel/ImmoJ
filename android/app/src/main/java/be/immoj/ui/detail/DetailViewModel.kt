package be.immoj.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.immoj.data.Annonce
import be.immoj.data.Depot
import be.immoj.data.PrixHistorique
import be.immoj.data.StatutPerso
import be.immoj.ui.liste.messageErreur
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailEtat(
    val annonce: Annonce? = null,
    val historique: List<PrixHistorique> = emptyList(),
    val chargement: Boolean = true,
    val erreur: String? = null,
    val message: String? = null,   // confirmation ou erreur d'enregistrement du statut
)

class DetailViewModel(
    private val depot: Depot, private val id: String,
    private val onStatutChange: (StatutPerso, String?) -> Unit = { _, _ -> },
) : ViewModel() {
    private val _etat = MutableStateFlow(DetailEtat())
    val etat: StateFlow<DetailEtat> = _etat.asStateFlow()

    init { charger() }

    fun charger() {
        viewModelScope.launch {
            _etat.update { it.copy(chargement = true, erreur = null) }
            try {
                var a = depot.annonce(id)
                val h = runCatching { depot.historiquePrix(id) }.getOrDefault(emptyList())
                // Ouvrir une annonce « Nouveau » la marque « Vu » (la pastille de l'onglet suit).
                if (a != null && a.statut == StatutPerso.NOUVEAU.code) {
                    runCatching { depot.changerStatut(id, StatutPerso.VU, a.note) }.onSuccess {
                        a = a!!.copy(statut = StatutPerso.VU.code, badgeNouveau = false)
                        onStatutChange(StatutPerso.VU, a!!.note)
                    }
                }
                _etat.update { it.copy(annonce = a, historique = h, chargement = false,
                    erreur = if (a == null) "Annonce introuvable." else null) }
            } catch (e: Exception) {
                _etat.update { it.copy(chargement = false, erreur = messageErreur(e)) }
            }
        }
    }

    fun changerStatut(statut: StatutPerso, note: String?) {
        viewModelScope.launch {
            try {
                depot.changerStatut(id, statut, note)
                onStatutChange(statut, note)
                _etat.update { it.copy(message = "Statut enregistré : ${statut.libelle}",
                    annonce = it.annonce?.copy(statut = statut.code, note = note)) }
            } catch (e: Exception) {
                _etat.update { it.copy(message = messageErreur(e)) }
            }
        }
    }

    fun effacerMessage() = _etat.update { it.copy(message = null) }
}
