package be.immoj.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Critères de filtrage de la liste et de la carte. Tout est optionnel.
 * Les mêmes champs servent à créer une recherche sauvegardée.
 */
@Serializable
data class Filtres(
    val prixMin: Int? = null,
    val prixMax: Int? = null,
    val types: Set<String> = emptySet(),          // appartement, maison, studio, kot, terrain
    val chambresMin: Int? = null,
    val surfaceMin: Int? = null,
    val periode: String = "tout",                 // "24h", "3j", "7j", "tout" (date de détection)
    val jardin: Boolean = false,
    val animaux: Boolean = false,
    val meuble: Boolean = false,
    val pebMax: String? = null,                   // "C" = A++ … C
    val sources: Set<String> = emptySet(),
    val communes: Set<String> = emptySet(),
    val centreLat: Double? = null,
    val centreLon: Double? = null,
    val rayonKm: Double? = null,
) {
    val rayonDefini: Boolean get() = centreLat != null && centreLon != null && (rayonKm ?: 0.0) > 0

    /** Nombre de critères actifs (pour le badge du bouton Filtres). */
    val nbActifs: Int
        get() = listOf(
            prixMin != null, prixMax != null, types.isNotEmpty(), chambresMin != null,
            surfaceMin != null, periode != "tout", jardin, animaux, meuble, pebMax != null,
            sources.isNotEmpty(), communes.isNotEmpty(), rayonDefini,
        ).count { it }

    /**
     * Paramètres PostgREST correspondant aux critères (hors zone « communes »,
     * gérée à part car elle se combine en OU avec le rayon).
     */
    fun versRequete(): Map<String, String> {
        val p = mutableMapOf<String, String>()
        when {
            // PostgREST n'accepte qu'une valeur par clé : deux bornes passent par « and ».
            prixMin != null && prixMax != null -> p["and"] = "(prix.gte.$prixMin,prix.lte.$prixMax)"
            prixMin != null -> p["prix"] = "gte.$prixMin"
            prixMax != null -> p["prix"] = "lte.$prixMax"
        }
        if (types.isNotEmpty()) p["type"] = "in.(${types.joinToString(",")})"
        chambresMin?.let { p["chambres"] = "gte.$it" }
        surfaceMin?.let { p["surface"] = "gte.$it" }
        val heures = when (periode) { "24h" -> 24L; "3j" -> 72L; "7j" -> 168L; else -> null }
        heures?.let {
            p["premiere_detection"] = "gte." + Instant.now().minus(it, ChronoUnit.HOURS).toString()
        }
        if (jardin) p["jardin"] = "eq.true"
        if (animaux) p["animaux"] = "eq.true"
        if (meuble) p["meuble"] = "eq.true"
        pebMax?.let { max ->
            val ordre = listOf("A++", "A+", "A", "B", "C", "D", "E", "F", "G")
            val admis = ordre.take(ordre.indexOf(max) + 1)
            // Les annonces sans PEB restent visibles (is.null).
            p["or"] = "(peb.in.(${admis.joinToString(",") { "\"$it\"" }}),peb.is.null)"
        }
        if (sources.isNotEmpty()) {
            // « agences » regroupe toutes les sources d'agences (un code par logiciel).
            val codes = sources.flatMap {
                if (it == "agences") listOf("agences:whise", "agences:generique") else listOf(it)
            }
            p["sources"] = "ov.{${codes.joinToString(",")}}"
        }
        return p
    }

    companion object {
        val PERIODES = listOf("24h" to "24 h", "3j" to "3 jours", "7j" to "7 jours", "tout" to "Tout")
        val TYPES = listOf("appartement" to "Appartement", "maison" to "Maison", "studio" to "Studio",
            "kot" to "Kot", "terrain" to "Terrain")
        val PEB = listOf("A", "B", "C", "D", "E", "F")
        val SOURCES = listOf("immovlan" to "Immovlan", "immoweb" to "Immoweb", "2ememain" to "2ememain",
            "zimmo" to "Zimmo", "logicimmo" to "Logic-immo", "agences" to "Sites d'agences")
    }
}
