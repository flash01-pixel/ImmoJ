package be.immoj.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltresTest {
    @Test
    fun codesDeSourcesRegroupent() {
        val codes = Filtres.codesSources(setOf("agences"))
        assertTrue("agences:era" in codes && "agences:wordpress" in codes && "agences:omnicasa_api" in codes)
        assertTrue(Filtres.sourceCorrespond("agences:whise_webulous", "agences"))
        assertFalse(Filtres.sourceCorrespond("immoweb", "agences"))
        assertTrue(Filtres.sourceCorrespond("zimmo_mail", "immoweb_mail"))
    }

    @Test
    fun requetePostgrestPourLesSources() {
        val p = Filtres(sources = setOf("notaire", "agences")).versRequete()
        assertTrue(p["sources"]!!.startsWith("ov.{"))
        assertTrue(p["sources"]!!.contains("agences:era"))
        assertTrue(p["sources"]!!.contains("notaire"))
    }

    @Test
    fun distanceHaversine() {
        // Arlon → Bastogne ≈ 36 km à vol d'oiseau.
        val d = distanceKm(49.683, 5.817, 50.004, 5.718)
        assertTrue(d in 34.0..38.0)
        assertEquals(0.0, distanceKm(50.0, 5.5, 50.0, 5.5), 0.0001)
    }

    @Test
    fun nbActifsCompteLesCriteres() {
        assertEquals(0, Filtres().nbActifs)
        assertEquals(3, Filtres(prixMax = 900, jardin = true, communes = setOf("Arlon")).nbActifs)
        assertEquals(1, Filtres(centreLat = 49.7, centreLon = 5.8, rayonKm = 10.0).nbActifs)
    }
}

class FiltresLot3Test {
    private fun a(
        id: String, prix: Int? = 800, charges: Int? = null, chambres: Int? = 2, surface: Int? = 80,
        statut: String = "nouveau", agence: String? = null, sources: List<String> = listOf("immoweb"),
        titre: String? = "Appartement lumineux", description: String? = null, photo: String? = "p.jpg",
        details: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(), baisse: Boolean = false,
        annonceur: String = "inconnu", precision: String? = null,
    ) = Annonce(id = id, prix = prix, charges = charges, chambres = chambres, surface = surface, statut = statut,
        agence = agence, sources = sources, titre = titre, description = description, photo = photo,
        details = details, badgePrixBaisse = baisse, commune = "Arlon", annonceur = annonceur,
        precisionPosition = precision)

    @Test
    fun rechercheTexteMultiMots() {
        val f = Filtres(texte = "lumineux arlon")
        assertTrue(f.correspond(a("1")))
        assertFalse(f.correspond(a("2", titre = "Maison", description = "Jardin à Arlon")))
        assertTrue(Filtres(texte = "PRIVATE").correspond(a("3", agence = "PRIVATE")))
    }

    @Test
    fun annonceurEtCharges() {
        // Champ du collecteur (lot 4) prioritaire, règle de secours pour les anciennes données.
        assertEquals("particulier", a("1", agence = "PRIVATE").typeAnnonceur)
        assertEquals("particulier", a("1", annonceur = "particulier").typeAnnonceur)
        assertEquals("agence", a("1", annonceur = "agence", agence = "PRIVATE").typeAnnonceur)
        assertEquals("notaire", a("2", sources = listOf("notaire")).typeAnnonceur)
        assertEquals("agence", a("3", agence = "Honesty").typeAnnonceur)
        assertEquals("inconnu", a("5", sources = listOf("immovlan")).typeAnnonceur)
        assertEquals("Particulier", a("1", annonceur = "particulier").annonceurLibelle)
        assertEquals("Honesty", a("3", annonceur = "agence", agence = "Honesty").annonceurLibelle)
        assertTrue(a("6", precision = "commune").positionApproximative)
        assertFalse(a("7", precision = "exacte").positionApproximative)
        assertTrue(Filtres(annonceur = setOf("particulier")).correspond(a("1", agence = "PRIVATE")))
        assertFalse(Filtres(annonceur = setOf("particulier")).correspond(a("3", agence = "Honesty")))
        assertTrue(Filtres(chargesComprisesMax = 900).correspond(a("4", prix = 800, charges = 100)))
        assertFalse(Filtres(chargesComprisesMax = 850).correspond(a("4", prix = 800, charges = 100)))
    }

    @Test
    fun bornesMaxPhotosVuesBaisses() {
        assertFalse(Filtres(chambresMax = 1).correspond(a("1")))
        assertTrue(Filtres(surfaceMax = 80).correspond(a("1")))
        assertFalse(Filtres(avecPhotos = true).correspond(a("2", photo = null)))
        assertFalse(Filtres(masquerVues = true).correspond(a("3", statut = "vu")))
        assertTrue(Filtres(prixBaisse = true).correspond(a("4", baisse = true)))
        assertEquals(3, Filtres(texte = "x", garage = true, chambresMax = 2).nbActifs)
    }

    @Test
    fun garageEtDisponibiliteDepuisLesDetails() {
        val d = mapOf(
            "Garage" to kotlinx.serialization.json.JsonPrimitive(1),
            "Disponibilité" to kotlinx.serialization.json.JsonPrimitive("2026-12-01"),
        )
        val x = a("1", details = d)
        assertTrue(x.aGarage)
        assertEquals("2026-12-01", x.disponibleLe)
        assertTrue(Filtres(garage = true, disponibleAvant = "2026-12-15").correspond(x))
        assertFalse(Filtres(disponibleAvant = "2026-11-01").correspond(x))
        assertFalse(a("2", details = mapOf("Garage" to kotlinx.serialization.json.JsonPrimitive(false))).aGarage)
    }

    @Test
    fun trisDuLot3() {
        val liste = listOf(a("1", prix = 800, surface = 80), a("2", prix = 600, surface = 40), a("3", prix = 900, surface = null))
        assertEquals(listOf("1", "2", "3"), Catalogue.trier(liste, "prix_m2").map { it.id })
        assertEquals(listOf("1", "2", "3"), Catalogue.trier(liste, "surface").map { it.id })
        val loin = a("4").copy(latitude = 50.0, longitude = 5.7)
        val pres = a("5").copy(latitude = 49.69, longitude = 5.82)
        assertEquals(listOf("5", "4"), Catalogue.trier(listOf(loin, pres), "distance", 49.683, 5.817).map { it.id })
    }
}
