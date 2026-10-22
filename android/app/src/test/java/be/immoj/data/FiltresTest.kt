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
