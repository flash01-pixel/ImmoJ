package be.immoj.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManuellesTest {
    @Test
    fun extractionDepuisUnTextePartage() {
        val texte = "Appartement 2 ch à Arlon, 750 €/mois charges comprises\nhttps://www.facebook.com/marketplace/item/123."
        assertEquals("https://www.facebook.com/marketplace/item/123", Manuelles.extraireLien(texte))
        assertEquals(750, Manuelles.extrairePrix(texte))
        assertEquals("Appartement 2 ch à Arlon, 750 €/mois charges comprises", Manuelles.titreDepuis(texte))
        assertEquals(250000, Manuelles.extrairePrix("Maison 250.000 €"))
        assertNull(Manuelles.extraireLien("pas de lien"))
        assertEquals("Annonce partagée", Manuelles.titreDepuis("https://x.be/a"))
    }

    @Test
    fun creationDuneAnnonceManuelle() {
        val arlon = Commune("Arlon", listOf("6700"), 49.683, 5.817)
        val a = Manuelles.creer("Chouette appart", "https://x.be/a", "location", "appartement", 750,
            arlon, "Bonnert", 2, 80, "vu sur Facebook")
        assertTrue(Manuelles.estManuelle(a.id))
        assertEquals(listOf("manuel"), a.sources)
        assertEquals("Arlon", a.commune)
        assertEquals("Bonnert", a.localite)
        assertEquals("6700", a.codePostal)
        assertEquals("commune", a.precisionPosition)
        assertEquals("particulier", a.annonceur)
        assertTrue(a.particulier && a.badgeNouveau && a.actif)
        assertTrue(Filtres(sources = setOf("manuel")).correspond(a))
        assertTrue(Filtres(annonceur = setOf("particulier")).correspond(a))
    }
}
