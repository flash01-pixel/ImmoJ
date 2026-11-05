package be.immoj.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CatalogueTest {
    private val maintenant: Instant = Instant.parse("2026-10-22T12:00:00Z")

    private fun a(
        id: String, transaction: String = "location", type: String = "appartement", prix: Int? = 800,
        commune: String? = "Arlon", chambres: Int? = 2, surface: Int? = 80, jardin: Boolean? = null,
        peb: String? = "C", sources: List<String> = listOf("immoweb"), statut: String = "nouveau",
        detection: String = "2026-10-22T10:00:00+00:00", lat: Double? = 49.68, lon: Double? = 5.81,
        badgeNouveau: Boolean = true, agence: String? = null,
    ) = Annonce(id = id, transaction = transaction, type = type, prix = prix, commune = commune,
        chambres = chambres, surface = surface, jardin = jardin, peb = peb, sources = sources,
        statut = statut, premiereDetection = detection, derniereVue = detection, latitude = lat,
        longitude = lon, badgeNouveau = badgeNouveau, agence = agence)

    private val index = listOf(
        a("1"),
        a("2", type = "maison", prix = 1200, chambres = 4, surface = 160, jardin = true, peb = "B",
            sources = listOf("agences:era")),
        a("3", prix = 650, commune = "Bastogne", chambres = 1, surface = 45, sources = listOf("agences:wordpress"),
            detection = "2026-10-15T10:00:00+00:00", badgeNouveau = false, lat = 50.0, lon = 5.72),
        a("4", statut = "refuse", badgeNouveau = false),
        a("5", transaction = "achat", type = "maison", prix = 250000, sources = listOf("notaire")),
        a("6", transaction = "achat", type = "autre", prix = null, lat = null, lon = null, agence = "PRIVATE"),
    )

    @Test
    fun totauxParTransactionSansLesRefusees() {
        assertEquals(mapOf("location" to 3, "achat" to 2), Catalogue.totaux(index))
        assertEquals(4, Catalogue.totaux(index, afficherRefusees = true)["location"])
    }

    @Test
    fun filtrerMasqueLesRefuseesParDefaut() {
        val loc = Catalogue.filtrer(index, "location", Filtres(), maintenant)
        assertEquals(listOf("1", "2", "3"), loc.map { it.id })
        val avec = Catalogue.filtrer(index, "location", Filtres(afficherRefusees = true), maintenant)
        assertEquals(4, avec.size)
    }

    @Test
    fun filtreSourcesAgencesCouvreTousLesLogiciels() {
        val f = Filtres(sources = setOf("agences"))
        assertEquals(listOf("2", "3"), Catalogue.filtrer(index, "location", f, maintenant).map { it.id })
        val n = Filtres(sources = setOf("notaire"))
        assertEquals(listOf("5"), Catalogue.filtrer(index, "achat", n, maintenant).map { it.id })
    }

    @Test
    fun periodeUtiliseLesDatesAvecDecalage() {
        val f = Filtres(periode = "24h")
        assertEquals(listOf("1", "2"), Catalogue.filtrer(index, "location", f, maintenant).map { it.id })
    }

    @Test
    fun triParPrixEtParDate() {
        val loc = Catalogue.filtrer(index, "location", Filtres(), maintenant)
        assertEquals(listOf("3", "1", "2"), Catalogue.trier(loc, "prix").map { it.id })
        assertEquals(listOf("2", "1", "3"), Catalogue.trier(loc, "prix_desc").map { it.id })
        assertEquals("3", Catalogue.trier(loc, "recent").last().id)
    }

    @Test
    fun compteursNouvellesEtSansPosition() {
        val loc = Catalogue.filtrer(index, "location", Filtres(), maintenant)
        assertEquals(2, Catalogue.nouvelles(loc))
        val achat = Catalogue.filtrer(index, "achat", Filtres(), maintenant)
        assertEquals(1, Catalogue.sansPosition(achat))
        assertEquals("2026-10-22T10:00:00+00:00", Catalogue.dateDonnees(index))
    }

    @Test
    fun facettesComptentChaqueOptionAvecLesAutresCriteres() {
        val f = Filtres(prixMax = 1000)
        val fac = Catalogue.facettes(index, "location", f, maintenant)
        assertEquals(2, fac.total)
        assertEquals(2, fac.types["appartement"])
        assertEquals(0, fac.types["maison"])          // la maison coûte 1 200 €
        assertEquals(1, fac.communes["Arlon"])
        assertEquals(1, fac.communes["Bastogne"])
        assertEquals(1, fac.sources["immoweb"])
        assertEquals(0, fac.jardin)
    }

    @Test
    fun suggestionsQuandAucunResultat() {
        val f = Filtres(jardin = true, prixMax = 1100)   // +15 % = 1 265 € : la maison à 1 200 € passe
        assertEquals(0, Catalogue.filtrer(index, "location", f, maintenant).size)
        val s = Catalogue.suggestions(index, "location", f, maintenant)
        assertTrue(s.any { it.libelle == "Retirer Jardin" && it.gain == 2 })
        assertTrue(s.any { it.libelle.startsWith("Prix max") && it.gain == 1 })
        assertEquals(s.sortedByDescending { it.gain }, s)
    }

    @Test
    fun rayonEtSuggestionDeRayon() {
        val f = Filtres(centreLat = 49.68, centreLon = 5.81, rayonKm = 5.0)
        assertEquals(listOf("1", "2"), Catalogue.filtrer(index, "location", f, maintenant).map { it.id })
        val vide = f.copy(chambresMin = 1, prixMax = 700)
        assertEquals(0, Catalogue.filtrer(index, "location", vide, maintenant).size)
        val s = Catalogue.suggestions(index, "location", vide, maintenant)
        assertTrue(s.any { it.libelle == "Toute la province" && it.gain == 1 })
    }

    @Test
    fun bilanRechercheSauvegardee() {
        val r = RechercheSauvegardee(id = "r1", nom = "Arlon", transaction = "location", communes = listOf("Arlon"))
        val (nb, nouvelles) = Catalogue.bilanRecherche(index, r, "2026-10-22T09:00:00Z", maintenant)
        assertEquals(2, nb)
        assertEquals(2, nouvelles)
        assertEquals(0, Catalogue.bilanRecherche(index, r, "2026-10-22T11:00:00Z", maintenant).second)
    }

    @Test
    fun similairesMemeTypePrixProcheSansRefusees() {
        val base = a("1")   // appartement 800 €, 2 ch., Arlon
        val index = listOf(
            base,
            a("2", prix = 900, lat = 49.69, lon = 5.82),                // proche, même type : oui
            a("3", prix = 650, commune = "Bastogne", lat = 50.0, lon = 5.72),   // dans ±25 %, plus loin
            a("4", prix = 820, statut = "refuse"),                    // refusée : non
            a("5", type = "maison", prix = 800),                      // autre type : non
            a("6", prix = 1500),                                      // trop cher : non
            a("7", chambres = 4, prix = 800),                         // 2 chambres d'écart : non
            a("8", transaction = "achat", prix = 800),                // autre transaction : non
        )
        assertEquals(listOf("2", "3"), Catalogue.similaires(index, base).map { it.id })
        assertEquals(listOf("2"), Catalogue.similaires(index, base, max = 1).map { it.id })
    }
}
