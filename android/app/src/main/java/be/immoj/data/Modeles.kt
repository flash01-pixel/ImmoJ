package be.immoj.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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
    /** Vignette de liste (première photo) ; `photos` complet n'est chargé que pour la fiche. */
    val photo: String? = null,
    val photos: List<String> = emptyList(),
    val description: String? = null,
    /** Informations complémentaires (libellé → valeur), variables selon la source. */
    val details: Map<String, JsonElement> = emptyMap(),
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

    /** Première photo, quelle que soit la façon dont l'annonce a été chargée. */
    val vignette: String? get() = photo ?: photos.firstOrNull()

    /** Particulier ? (provisoire : Immoweb marque « PRIVATE » ; le champ annonceur arrive au lot 4). */
    val particulier: Boolean get() = agence == "PRIVATE"

    /** Détails lisibles : « Salles de bain » → « 2 », « Garage » → « oui ». */
    val detailsLisibles: List<Pair<String, String>>
        get() = details.mapNotNull { (cle, v) ->
            val p = v as? JsonPrimitive ?: return@mapNotNull null
            val texte = when (p.content) { "true" -> "oui"; "false" -> "non"; else -> p.content }
            if (texte.isBlank() || texte == "null") null else cle to texte
        }
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

/** Une commune de la province (table `communes`). */
@Serializable
data class Commune(
    val nom: String,
    val codes: List<String> = emptyList(),
    val latitude: Double,
    val longitude: Double,
)

/** Ligne de la table `recherches_sauvegardees` (créée par l'appli, lue par le collecteur). */
@Serializable
data class RechercheSauvegardee(
    val id: String? = null,
    val nom: String,
    val transaction: String? = null,
    val communes: List<String>? = null,
    @SerialName("centre_lat") val centreLat: Double? = null,
    @SerialName("centre_lon") val centreLon: Double? = null,
    @SerialName("rayon_km") val rayonKm: Double? = null,
    @SerialName("prix_min") val prixMin: Int? = null,
    @SerialName("prix_max") val prixMax: Int? = null,
    val types: List<String>? = null,
    @SerialName("chambres_min") val chambresMin: Int? = null,
    @SerialName("surface_min") val surfaceMin: Int? = null,
    val jardin: Boolean? = null,
    val animaux: Boolean? = null,
    val meuble: Boolean? = null,
    @SerialName("peb_max") val pebMax: String? = null,
    val sources: List<String>? = null,
    val notifier: Boolean = true,
    val actif: Boolean = true,
) {
    /** Convertit en filtres pour la liste / la carte. */
    fun versFiltres() = Filtres(
        prixMin = prixMin, prixMax = prixMax, types = types?.toSet() ?: emptySet(),
        chambresMin = chambresMin, surfaceMin = surfaceMin,
        jardin = jardin == true, animaux = animaux == true, meuble = meuble == true,
        pebMax = pebMax, sources = sources?.toSet() ?: emptySet(),
        communes = communes?.toSet() ?: emptySet(),
        centreLat = centreLat, centreLon = centreLon, rayonKm = rayonKm,
    )

    /** Résumé lisible des critères. */
    val resume: String
        get() = listOfNotNull(
            transaction?.let { if (it == "location") "Location" else "Achat" },
            if (!communes.isNullOrEmpty()) communes.joinToString(", ") else null,
            if (rayonKm != null && centreLat != null) "rayon ${rayonKm.toInt()} km" else null,
            prixMax?.let { "≤ $it €" },
            chambresMin?.let { "≥ $it ch." },
            if (!types.isNullOrEmpty()) types.joinToString("/") else null,
        ).joinToString(" · ").ifBlank { "Tous critères" }

    companion object {
        fun depuis(nom: String, transaction: String, f: Filtres) = RechercheSauvegardee(
            nom = nom, transaction = transaction,
            communes = f.communes.toList().ifEmpty { null },
            centreLat = if (f.rayonDefini) f.centreLat else null,
            centreLon = if (f.rayonDefini) f.centreLon else null,
            rayonKm = if (f.rayonDefini) f.rayonKm else null,
            prixMin = f.prixMin, prixMax = f.prixMax,
            types = f.types.toList().ifEmpty { null },
            chambresMin = f.chambresMin, surfaceMin = f.surfaceMin,
            jardin = if (f.jardin) true else null,
            animaux = if (f.animaux) true else null,
            meuble = if (f.meuble) true else null,
            pebMax = f.pebMax,
            sources = f.sources.toList().ifEmpty { null },
        )
    }
}
