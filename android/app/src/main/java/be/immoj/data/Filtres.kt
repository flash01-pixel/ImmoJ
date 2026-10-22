package be.immoj.data

import be.immoj.util.Dates
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
    val afficherRefusees: Boolean = false,        // les annonces « Refusé » sont masquées par défaut
    // --- critères ajoutés au lot 3 ---
    val texte: String = "",                       // recherche libre : titre, description, commune, annonceur
    val annonceur: Set<String> = emptySet(),      // "particulier", "agence", "notaire"
    val chambresMax: Int? = null,
    val surfaceMax: Int? = null,
    val garage: Boolean = false,                  // garage ou parking (lu dans les détails)
    val terrasse: Boolean = false,
    val chargesComprisesMax: Int? = null,         // loyer + charges ≤ X
    val disponibleAvant: String? = null,          // « 2026-12-01 » : disponible au plus tard à cette date
    val avecPhotos: Boolean = false,
    val masquerVues: Boolean = false,             // cache les annonces déjà ouvertes (statut « vu »)
    val prixBaisse: Boolean = false,              // seulement les prix baissés récemment
) {
    val rayonDefini: Boolean get() = centreLat != null && centreLon != null && (rayonKm ?: 0.0) > 0

    /** Nombre de critères actifs (pour le badge du bouton Filtres). */
    val nbActifs: Int
        get() = listOf(
            prixMin != null, prixMax != null, types.isNotEmpty(), chambresMin != null,
            surfaceMin != null, periode != "tout", jardin, animaux, meuble, pebMax != null,
            sources.isNotEmpty(), communes.isNotEmpty(), rayonDefini,
            texte.isNotBlank(), annonceur.isNotEmpty(), chambresMax != null, surfaceMax != null,
            garage, terrasse, chargesComprisesMax != null, disponibleAvant != null, avecPhotos,
            masquerVues, prixBaisse,
        ).count { it }

    /** Sans la zone ni le texte : pour les suggestions et les comptages de facettes. */
    fun sansZone(): Filtres = copy(communes = emptySet(), centreLat = null, centreLon = null, rayonKm = null)

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
            p["sources"] = "ov.{${codesSources(sources).joinToString(",")}}"
        }
        return p
    }

    companion object {
        val PERIODES = listOf("24h" to "24 h", "3j" to "3 jours", "7j" to "7 jours", "tout" to "Tout")
        val TYPES = listOf("appartement" to "Appartement", "maison" to "Maison", "studio" to "Studio",
            "kot" to "Kot", "terrain" to "Terrain", "autre" to "Autre")
        val PEB = listOf("A", "B", "C", "D", "E", "F")
        /** Sources telles que le collecteur les nomme (voir docs/05 pour les agences). */
        val SOURCES = listOf("immovlan" to "Immovlan", "immoweb" to "Immoweb", "2ememain" to "2ememain",
            "notaire" to "Notaires / Biddit", "agences" to "Sites d'agences",
            "immoweb_mail" to "Alertes e-mail")
        /** Tris proposés (code → libellé). */
        val TRIS = listOf("recent" to "Plus récentes", "prix" to "Prix croissant", "prix_desc" to "Prix décroissant",
            "prix_m2" to "Prix au m²", "surface" to "Surface", "distance" to "Distance au centre",
            "chambres" to "Chambres")
        /** Préréglages de prix max (location / achat). */
        val PRIX_LOCATION = listOf(500, 650, 800, 1000, 1200, 1500)
        val PRIX_ACHAT = listOf(150000, 200000, 250000, 300000, 400000, 500000)
        /** Les 44 communes par arrondissement (ordre alphabétique dans chaque groupe). */
        val ARRONDISSEMENTS: Map<String, List<String>> = mapOf(
            "Arlon" to listOf("Arlon", "Attert", "Aubange", "Martelange", "Messancy"),
            "Bastogne" to listOf("Bastogne", "Bertogne", "Fauvillers", "Gouvy", "Houffalize", "Sainte-Ode",
                "Vaux-sur-Sûre", "Vielsalm"),
            "Marche-en-Famenne" to listOf("Durbuy", "Érezée", "Hotton", "La Roche-en-Ardenne", "Manhay",
                "Marche-en-Famenne", "Nassogne", "Rendeux", "Tenneville"),
            "Neufchâteau" to listOf("Bertrix", "Bouillon", "Daverdisse", "Herbeumont", "Léglise", "Libin",
                "Libramont-Chevigny", "Neufchâteau", "Paliseul", "Saint-Hubert", "Tellin", "Wellin"),
            "Virton" to listOf("Chiny", "Étalle", "Florenville", "Habay", "Meix-devant-Virton", "Musson",
                "Rouvroy", "Saint-Léger", "Tintigny", "Virton"),
        )
        /** Une seule constante pour le rayon : défaut et bornes (km). */
        const val RAYON_DEFAUT_KM = 10.0
        const val RAYON_MIN_KM = 0.5
        const val RAYON_MAX_KM = 60.0

        /** Un code de filtre peut regrouper plusieurs sources (« agences » = tous les logiciels). */
        fun codesSources(choisis: Set<String>): List<String> = choisis.flatMap {
            when (it) {
                "agences" -> listOf("agences:whise", "agences:generique", "agences:whise_webulous",
                    "agences:era", "agences:omnicasa_gatsby", "agences:omnicasa_api", "agences:wordpress")
                "immoweb_mail" -> listOf("immoweb_mail", "zimmo_mail", "logicimmo_mail", "zimmo", "logicimmo")
                else -> listOf(it)
            }
        }

        /** Vrai si une source d'annonce correspond à un code de filtre. */
        fun sourceCorrespond(source: String, code: String): Boolean =
            source == code || (code == "agences" && source.startsWith("agences:")) ||
                (code == "immoweb_mail" && (source.endsWith("_mail") || source in setOf("zimmo", "logicimmo")))
    }
}

/** Même logique que [Filtres.versRequete], appliquée en mémoire (les deux modes). */
fun Filtres.correspond(a: Annonce, maintenant: Instant = Instant.now()): Boolean {
    if (prixMin != null && (a.prix == null || a.prix < prixMin)) return false
    if (prixMax != null && (a.prix == null || a.prix > prixMax)) return false
    if (types.isNotEmpty() && a.type !in types) return false
    if (chambresMin != null && (a.chambres == null || a.chambres < chambresMin)) return false
    if (surfaceMin != null && (a.surface == null || a.surface < surfaceMin)) return false
    val heures = when (periode) { "24h" -> 24L; "3j" -> 72L; "7j" -> 168L; else -> null }
    if (heures != null && !Dates.recent(a.premiereDetection, heures, maintenant)) return false
    if (jardin && a.jardin != true) return false
    if (animaux && a.animaux != true) return false
    if (meuble && a.meuble != true) return false
    pebMax?.let { max ->
        val ordre = listOf("A++", "A+", "A", "B", "C", "D", "E", "F", "G")
        if (a.peb != null && ordre.indexOf(a.peb) > ordre.indexOf(max)) return false
    }
    if (sources.isNotEmpty() && a.sources.none { s -> sources.any { Filtres.sourceCorrespond(s, it) } }) {
        return false
    }
    // --- critères du lot 3 ---
    if (chambresMax != null && (a.chambres ?: 0) > chambresMax) return false
    if (surfaceMax != null && (a.surface ?: 0) > surfaceMax) return false
    if (garage && !a.aGarage) return false
    if (terrasse && a.terrasse != true) return false
    if (chargesComprisesMax != null && (a.prix == null || a.prix + (a.charges ?: 0) > chargesComprisesMax)) return false
    if (disponibleAvant != null) {
        val dispo = a.disponibleLe ?: return false
        if (dispo > disponibleAvant) return false
    }
    if (avecPhotos && a.vignette == null) return false
    if (masquerVues && a.statut == StatutPerso.VU.code) return false
    if (prixBaisse && !a.badgePrixBaisse) return false
    if (annonceur.isNotEmpty() && a.typeAnnonceur !in annonceur) return false
    if (texte.isNotBlank()) {
        val mots = texte.lowercase().split(" ").filter { it.isNotBlank() }
        val corpus = listOfNotNull(a.titre, a.description, a.commune, a.localite, a.codePostal, a.agence)
            .joinToString(" ").lowercase()
        if (!mots.all { it in corpus }) return false
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
