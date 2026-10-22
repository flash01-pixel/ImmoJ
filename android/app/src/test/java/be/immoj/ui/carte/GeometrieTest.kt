package be.immoj.ui.carte

import be.immoj.data.Annonce
import be.immoj.data.Commune
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometrieTest {
    private val communes = listOf(
        Commune("Arlon", listOf("6700"), 49.683, 5.817),
        Commune("Bastogne", listOf("6600"), 50.004, 5.718),
        Commune("Libramont-Chevigny", listOf("6800"), 49.920, 5.380),
    )

    @Test
    fun leViseurEstAuDessusDuDoigt() {
        val (x, y) = Geometrie.pointSousViseur(300f, 900f, Geometrie.dpEnPx(Geometrie.DECALAGE_VISEUR_DP, 3f))
        assertEquals(300f, x, 0.001f)
        assertEquals(690f, y, 0.001f)          // 70 dp × 3 = 210 px plus haut
    }

    @Test
    fun rayonDepuisLaPoigneeArrondiEtBorne() {
        // Poignée à ~10 km à l'est d'Arlon.
        val (lat, lon) = Geometrie.destination(49.683, 5.817, 10.0, 90.0)
        assertEquals(10.0, Geometrie.rayonDepuisPoignee(49.683, 5.817, lat, lon), 0.5)
        assertEquals(0.5, Geometrie.rayonDepuisPoignee(49.683, 5.817, 49.683, 5.817), 0.0)     // borne basse
        val (loinLat, loinLon) = Geometrie.destination(49.683, 5.817, 200.0, 0.0)
        assertEquals(60.0, Geometrie.rayonDepuisPoignee(49.683, 5.817, loinLat, loinLon), 0.0)  // borne haute
        assertEquals(7.5, Geometrie.arrondir(7.6), 0.0)
        assertEquals(7.5, Geometrie.arrondir(7.3), 0.0)
    }

    @Test
    fun destinationRevientAuBonEndroit() {
        val (lat, lon) = Geometrie.destination(50.0, 5.5, 1.0, 180.0)
        assertTrue(lat < 50.0 && lat > 49.98)
        assertEquals(5.5, lon, 0.001)
    }

    @Test
    fun communeLaPlusProcheEtCouvertes() {
        assertEquals("Bastogne", Geometrie.communeLaPlusProche(50.01, 5.70, communes)?.nom)
        assertEquals(listOf("Arlon"), Geometrie.communesCouvertes(49.7, 5.8, 5.0, communes))
        assertEquals(3, Geometrie.communesCouvertes(49.85, 5.6, 60.0, communes).size)
    }

    @Test
    fun regroupementParGrille() {
        fun a(id: String, lat: Double?, lon: Double?) = Annonce(id = id, latitude = lat, longitude = lon)
        val annonces = listOf(a("1", 49.681, 5.816), a("2", 49.683, 5.818), a("3", 50.004, 5.718), a("4", null, null))
        val groupes = Geometrie.regrouper(annonces, 0.04)
        assertEquals(2, groupes.size)
        assertEquals(2, groupes.first { it.taille == 2 }.annonces.size)
        assertEquals(3, Geometrie.regrouper(annonces, 0.0).size)      // zoom fort : un marqueur chacun
        assertEquals(0.0, Geometrie.celluleSelonZoom(16.0), 0.0)
        assertTrue(Geometrie.celluleSelonZoom(9.0) > Geometrie.celluleSelonZoom(12.0))
    }
}
