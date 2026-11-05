package be.immoj.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Un seul utilitaire de dates pour toute l'appli.
 *
 * Les horodatages arrivent de Postgres (« 2026-10-22T00:33:09.697056+00:00 »),
 * du collecteur (« 2026-10-22T00:34:13.248276+00:00 »), de l'appli elle-même
 * (« 2026-10-22T00:40:00Z ») ou d'une simple date (« 2026-10-22 »).
 * `Instant.parse` refuse le suffixe « +00:00 » sur Android 13 et moins : on passe
 * par `OffsetDateTime`, avec repli sur `LocalDate`.
 */
object Dates {
    private val fr = Locale.FRANCE
    private val fuseau: ZoneId = ZoneId.of("Europe/Brussels")

    /** Instant d'un horodatage ISO, ou null s'il est illisible. */
    fun instant(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        val t = iso.trim()
        runCatching { return OffsetDateTime.parse(t).toInstant() }
        runCatching { return Instant.parse(t) }
        // Sans fuseau (« 2026-10-22T00:33:09.697056 ») : on suppose UTC.
        runCatching { return OffsetDateTime.parse(t + "Z").toInstant() }
        runCatching { return java.time.LocalDateTime.parse(t).toInstant(ZoneOffset.UTC) }
        runCatching { return LocalDate.parse(t.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant() }
        return null
    }

    /** « il y a 3 h », « hier », « il y a 5 j » ; « — » si illisible. */
    fun depuis(iso: String?, maintenant: Instant = Instant.now()): String {
        val i = instant(iso) ?: return "—"
        val d = Duration.between(i, maintenant)
        return when {
            d.isNegative || d.toMinutes() < 1 -> "à l'instant"
            d.toMinutes() < 60 -> "il y a ${d.toMinutes()} min"
            d.toHours() < 24 -> "il y a ${d.toHours()} h"
            d.toDays() == 1L -> "hier"
            d.toDays() < 30 -> "il y a ${d.toDays()} j"
            else -> dateCourte(iso)
        }
    }

    /** « 22 oct. 2026 ». */
    fun dateCourte(iso: String?): String {
        val i = instant(iso) ?: return "—"
        return i.atZone(fuseau).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", fr))
    }

    /** « 22/10 à 02:34 » (heure de Bruxelles), pour l'en-tête « Données du … ». */
    fun dateHeure(iso: String?): String {
        val i = instant(iso) ?: return "—"
        return i.atZone(fuseau).format(DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm", fr))
    }

    /** Vrai si l'horodatage est plus récent que `heures` heures. */
    fun recent(iso: String?, heures: Long, maintenant: Instant = Instant.now()): Boolean {
        val i = instant(iso) ?: return false
        return i.isAfter(maintenant.minus(Duration.ofHours(heures)))
    }

    /** Vrai si `iso` est strictement après `depuis` (horodatages ISO). */
    fun apres(iso: String?, depuis: String?): Boolean {
        val a = instant(iso) ?: return false
        val b = instant(depuis) ?: return true
        return a.isAfter(b)
    }
}
