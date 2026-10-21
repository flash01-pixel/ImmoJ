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

/** Même logique que [Filtres.versRequete], appliquée en mémoire (mode sans compte). */
fun Filtres.correspond(a: Annonce): Boolean {
    if (prixMin != null && (a.prix == null || a.prix < prixMin)) return false
    if (prixMax != null && (a.prix == null || a.prix > prixMax)) return false
    if (types.isNotEmpty() && a.type !in types) return false
    if (chambresMin != null && (a.chambres == null || a.chambres < chambresMin)) return false
    if (surfaceMin != null && (a.surface == null || a.surface < surfaceMin)) return false
    val heures = when (periode) { "24h" -> 24L; "3j" -> 72L; "7j" -> 168L; else -> null }
    if (heures != null) {
        val detection = a.premiereDetection?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (detection == null || detection.isBefore(Instant.now().minus(heures, ChronoUnit.HOURS))) return false
    }
    if (jardin && a.jardin != true) return false
    if (animaux && a.animaux != true) return false
    if (meuble && a.meuble != true) return false
    pebMax?.let { max ->
        val ordre = listOf("A++", "A+", "A", "B", "C", "D", "E", "F", "G")
        if (a.peb != null && ordre.indexOf(a.peb) > ordre.indexOf(max)) return false
    }
    if (sources.isNotEmpty()) {
        val codes = sources.flatMap {
            if (it == "agences") listOf("agences:whise", "agences:generique") else listOf(it)
        }
        if (a.sources.none { it in codes }) return false
    }
    val zone = communes.isNotEmpty() || rayonDefini
    if (zone) {
        val dansCommunes = communes.isNotEmpty() && a.commune != null && a.commune in communes
        val dansRayon = rayonDefini && a.latitude != null && a.longitude != null &&
            distanceKm(centreLat!!, centreLon!!, a.latitude, a.longitude) <= rayonKm!!
        if (!dansCommunes && !dansRayon) return false
    }
    return true
}

/** Distance à vol d'oiseau en km (haversine). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(a))
}
