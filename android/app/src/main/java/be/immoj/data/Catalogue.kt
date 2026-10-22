package be.immoj.data

import be.immoj.util.Dates
import be.immoj.util.Format
import java.time.Instant

/**
 * Le catalogue : toutes les annonces actives en mémoire, et tout ce qu'on en
 * calcule sans réseau (filtrage, tri, comptages, suggestions).
 * Pur Kotlin, sans Android : testé par des tests JVM.
 */
object Catalogue {

    /** Annonces d'une transaction, filtrées et triées. */
    fun filtrer(
        index: List<Annonce>, transaction: String, filtres: Filtres,
        maintenant: Instant = Instant.now(),
    ): List<Annonce> = index.asSequence()
        .filter { it.actif && it.transaction == transaction }
        .filter { filtres.afficherRefusees || it.statut != StatutPerso.REFUSE.code }
        .filter { filtres.correspond(it, maintenant) }
        .toList()

    fun trier(annonces: List<Annonce>, tri: String): List<Annonce> = when (tri) {
        "prix" -> annonces.sortedWith(compareBy(nullsLast()) { it.prix })
        "prix_desc" -> annonces.sortedWith(compareByDescending(nullsFirst()) { it.prix })
        else -> annonces.sortedByDescending { Dates.instant(it.premiereDetection) ?: Instant.EPOCH }
    }

    /** Nombre d'annonces actives par transaction (hors refusées, sauf demande). */
    fun totaux(index: List<Annonce>, afficherRefusees: Boolean = false): Map<String, Int> =
        index.filter { it.actif && (afficherRefusees || it.statut != StatutPerso.REFUSE.code) }
            .groupingBy { it.transaction }.eachCount()

    /** Nouveautés (badge « Nouveau », pas encore vues) parmi une liste. */
    fun nouvelles(annonces: List<Annonce>): Int =
        annonces.count { it.badgeNouveau && it.statut == StatutPerso.NOUVEAU.code }

    /** Annonces sans position (invisibles sur la carte). */
    fun sansPosition(annonces: List<Annonce>): Int = annonces.count { it.latitude == null || it.longitude == null }

    /** Horodatage des données : la dernière fois qu'une annonce a été vue par le collecteur. */
    fun dateDonnees(index: List<Annonce>): String? =
        index.mapNotNull { it.derniereVue }.maxByOrNull { Dates.instant(it) ?: Instant.EPOCH }

    /**
     * Comptages « que se passe-t-il si je coche ceci ? » pour l'écran Filtres :
     * pour chaque valeur d'une facette, le nombre d'annonces avec les autres
     * critères inchangés et cette valeur choisie seule dans sa facette.
     */
    fun facettes(index: List<Annonce>, transaction: String, filtres: Filtres,
                 maintenant: Instant = Instant.now()): Facettes {
        val base = index.filter { it.actif && it.transaction == transaction &&
            (filtres.afficherRefusees || it.statut != StatutPerso.REFUSE.code) }
        fun compter(f: Filtres) = base.count { f.correspond(it, maintenant) }
        val types = Filtres.TYPES.associate { (code, _) -> code to compter(filtres.copy(types = setOf(code))) }
        val sources = Filtres.SOURCES.associate { (code, _) -> code to compter(filtres.copy(sources = setOf(code))) }
        val pebs = Filtres.PEB.associateWith { compter(filtres.copy(pebMax = it)) }
        val communes = base.filter { filtres.copy(communes = emptySet(), centreLat = null, centreLon = null,
            rayonKm = null).correspond(it, maintenant) }
            .groupingBy { it.commune ?: "" }.eachCount().filterKeys { it.isNotBlank() }
        val periodes = Filtres.PERIODES.associate { (code, _) -> code to compter(filtres.copy(periode = code)) }
        return Facettes(types, sources, pebs, communes, periodes,
            jardin = compter(filtres.copy(jardin = true)),
            animaux = compter(filtres.copy(animaux = true)),
            meuble = compter(filtres.copy(meuble = true)),
            total = compter(filtres))
    }

    /**
     * Quand il n'y a aucun résultat : les assouplissements qui en apporteraient,
     * avec leur gain (« Retirer Jardin : +8 », « Rayon 20 km : +14 »).
     */
    fun suggestions(index: List<Annonce>, transaction: String, filtres: Filtres,
                    maintenant: Instant = Instant.now()): List<Suggestion> {
        val base = index.filter { it.actif && it.transaction == transaction }
        // Même règle que filtrer() : les refusées ne comptent que si on demande à les voir.
        fun compter(f: Filtres) = base.count {
            (f.afficherRefusees || it.statut != StatutPerso.REFUSE.code) && f.correspond(it, maintenant)
        }
        val actuel = compter(filtres)
        val candidats = mutableListOf<Pair<String, Filtres>>()
        if (filtres.jardin) candidats += "Retirer Jardin" to filtres.copy(jardin = false)
        if (filtres.animaux) candidats += "Retirer Animaux" to filtres.copy(animaux = false)
        if (filtres.meuble) candidats += "Retirer Meublé" to filtres.copy(meuble = false)
        if (filtres.pebMax != null) candidats += "Retirer PEB" to filtres.copy(pebMax = null)
        if (filtres.types.isNotEmpty()) candidats += "Tous les types" to filtres.copy(types = emptySet())
        if (filtres.sources.isNotEmpty()) candidats += "Toutes les sources" to filtres.copy(sources = emptySet())
        if (filtres.periode != "tout") candidats += "Toute période" to filtres.copy(periode = "tout")
        filtres.chambresMin?.let { if (it > 1) candidats += "Chambres ≥ ${it - 1}" to filtres.copy(chambresMin = it - 1) }
        filtres.surfaceMin?.let { candidats += "Sans surface min" to filtres.copy(surfaceMin = null) }
        filtres.prixMax?.let {
            val plus = (it * 1.15).toInt()
            candidats += "Prix max ${Format.nombre(plus)} €" to filtres.copy(prixMax = plus)
        }
        filtres.prixMin?.let { candidats += "Sans prix min" to filtres.copy(prixMin = null) }
        if (filtres.rayonDefini) {
            val r = filtres.rayonKm!!
            for (plus in listOf(r + 5, r + 10, r * 2)) {
                if (plus <= Filtres.RAYON_MAX_KM) candidats += "Rayon ${Format.km(plus)}" to filtres.copy(rayonKm = plus)
            }
            candidats += "Toute la province" to filtres.copy(centreLat = null, centreLon = null, rayonKm = null)
        }
        if (filtres.communes.isNotEmpty()) candidats += "Toutes les communes" to filtres.copy(communes = emptySet())
        if (!filtres.afficherRefusees) candidats += "Afficher les refusées" to filtres.copy(afficherRefusees = true)
        return candidats.map { (libelle, f) -> Suggestion(libelle, f, compter(f) - actuel) }
            .filter { it.gain > 0 }
            .sortedByDescending { it.gain }
    }

    /** Nombre d'annonces et de nouveautés depuis une visite pour une recherche sauvegardée. */
    fun bilanRecherche(index: List<Annonce>, r: RechercheSauvegardee, derniereVisite: String?,
                       maintenant: Instant = Instant.now()): Pair<Int, Int> {
        val f = r.versFiltres()
        val trans = r.transaction
        val liste = index.filter { (trans == null || it.transaction == trans) && it.actif &&
            it.statut != StatutPerso.REFUSE.code && f.correspond(it, maintenant) }
        val nouvelles = liste.count { Dates.apres(it.premiereDetection, derniereVisite) }
        return liste.size to nouvelles
    }
}

data class Facettes(
    val types: Map<String, Int>,
    val sources: Map<String, Int>,
    val pebs: Map<String, Int>,
    val communes: Map<String, Int>,
    val periodes: Map<String, Int>,
    val jardin: Int,
    val animaux: Int,
    val meuble: Int,
    val total: Int,
)

data class Suggestion(val libelle: String, val filtres: Filtres, val gain: Int)
