package be.immoj.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Une annonce telle que renvoyée par la vue Supabase `v_annonces_liste`
 * (fiche + statut + badges + sources agrégées).
 */
@Serializable
data class Annonce(
    val id: String,
    val type: String = "autre",
    val transaction: String = "location",
    val titre: String? = null,
    val prix: Int? = null,
    @SerialName("prix_precedent") val prixPrecedent: Int? = null,
    @SerialName("prix_baisse_le") val prixBaisseLe: String? = null,
    val charges: Int? = null,
    val commune: String? = null,
    val localite: String? = null,
    @SerialName("code_postal") val codePostal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val chambres: Int? = null,
    val surface: Int? = null,
    val peb: String? = null,
    val jardin: Boolean? = null,
    val terrasse: Boolean? = null,
    val animaux: Boolean? = null,
    val meuble: Boolean? = null,
    @SerialName("date_publication") val datePublication: String? = null,
    @SerialName("premiere_detection") val premiereDetection: String? = null,
    @SerialName("derniere_vue") val derniereVue: String? = null,
    val photos: List<String> = emptyList(),
    val description: String? = null,
    val agence: String? = null,
    val telephone: String? = null,
    val actif: Boolean = true,
    val statut: String = "nouveau",
    val note: String? = null,
    @SerialName("badge_nouveau") val badgeNouveau: Boolean = false,
    @SerialName("badge_prix_baisse") val badgePrixBaisse: Boolean = false,
    val sources: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
) {
    /** « 6700 Arlon » ou la localité affichée par la source. */
    val lieu: String
        get() = listOfNotNull(codePostal, localite ?: commune).joinToString(" ")
}

/** Ligne de la table `statuts` (écrite par l'appli). */
@Serializable
data class Statut(
    @SerialName("annonce_id") val annonceId: String,
    val statut: String,
    val note: String? = null,
    @SerialName("modifie_le") val modifieLe: String,
)

/** Un point de l'historique des prix. */
@Serializable
data class PrixHistorique(
    val prix: Int,
    val source: String? = null,
    val date: String,
)

/** Ligne de la vue `v_etat_sources`. */
@Serializable
data class EtatSource(
    val source: String,
    @SerialName("derniere_collecte") val derniereCollecte: String? = null,
    @SerialName("derniere_ok") val derniereOk: Boolean = false,
    val bloque: Boolean = false,
    val erreur: String? = null,
    @SerialName("nb_trouvees") val nbTrouvees: Int = 0,
    @SerialName("nb_nouvelles") val nbNouvelles: Int = 0,
    @SerialName("derniere_reussite") val derniereReussite: String? = null,
    @SerialName("nb_actives") val nbActives: Int = 0,
)

/** Statuts personnels possibles, avec leur libellé en français. */
enum class StatutPerso(val code: String, val libelle: String) {
    NOUVEAU("nouveau", "Nouveau"),
    VU("vu", "Vu"),
    INTERESSE("interesse", "Intéressé"),
    CONTACTE("contacte", "Contacté"),
    VISITE_PREVUE("visite_prevue", "Visite prévue"),
    REFUSE("refuse", "Refusé");

    companion object {
        fun depuisCode(code: String?): StatutPerso = entries.firstOrNull { it.code == code } ?: NOUVEAU
    }
}
