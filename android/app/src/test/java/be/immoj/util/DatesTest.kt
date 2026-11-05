package be.immoj.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DatesTest {
    private val maintenant: Instant = Instant.parse("2026-10-22T12:00:00Z")

    @Test
    fun litLesFormatsDePostgresDuCollecteurEtDeLappli() {
        assertEquals(Instant.parse("2026-10-22T00:33:09.697056Z"), Dates.instant("2026-10-22T00:33:09.697056+00:00"))
        assertEquals(Instant.parse("2026-10-22T00:34:13Z"), Dates.instant("2026-10-22T02:34:13+02:00"))
        assertEquals(Instant.parse("2026-10-22T00:40:00Z"), Dates.instant("2026-10-22T00:40:00Z"))
        assertEquals(Instant.parse("2026-10-22T00:33:09Z"), Dates.instant("2026-10-22T00:33:09"))
        assertEquals(Instant.parse("2026-10-22T00:00:00Z"), Dates.instant("2026-10-22"))
        assertNull(Dates.instant(null))
        assertNull(Dates.instant(""))
        assertNull(Dates.instant("hier"))
    }

    @Test
    fun depuisEnFrancais() {
        assertEquals("il y a 3 h", Dates.depuis("2026-10-22T09:00:00+00:00", maintenant))
        assertEquals("il y a 5 min", Dates.depuis("2026-10-22T11:55:00Z", maintenant))
        assertEquals("hier", Dates.depuis("2026-10-21T06:00:00+00:00", maintenant))
        assertEquals("il y a 5 j", Dates.depuis("2026-10-17T12:00:00+00:00", maintenant))
        assertEquals("—", Dates.depuis(null, maintenant))
        assertEquals("à l'instant", Dates.depuis("2026-10-22T12:00:30Z", maintenant))
    }

    @Test
    fun recentEtApres() {
        assertTrue(Dates.recent("2026-10-22T00:33:09.697056+00:00", 24, maintenant))
        assertFalse(Dates.recent("2026-10-20T00:33:09.697056+00:00", 24, maintenant))
        assertFalse(Dates.recent(null, 24, maintenant))
        assertTrue(Dates.apres("2026-10-22T10:00:00+00:00", "2026-10-22T09:00:00Z"))
        assertFalse(Dates.apres("2026-10-22T08:00:00+00:00", "2026-10-22T09:00:00Z"))
        assertTrue(Dates.apres("2026-10-22T08:00:00+00:00", null))   // jamais visité : tout est nouveau
    }

    @Test
    fun dateHeureEnHeureDeBruxelles() {
        assertEquals("22/10 à 02:34", Dates.dateHeure("2026-10-22T00:34:13.248276+00:00"))
        assertNotNull(Dates.dateCourte("2026-10-22"))
    }
}
