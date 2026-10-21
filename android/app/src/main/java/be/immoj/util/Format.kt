package be.immoj.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mise en forme des prix, dates et libellés, en français. */
object Format {
    private val fr = Locale.FRANCE

    fun prix(valeur: Int?, transaction: String): String {
        if (valeur == null) return "Prix non communiqué"
        val texte = String.format(fr, "%,d", valeur).replace('\u00a0', ' ') + " €"
        return if (transaction == "location") "$texte / mois" else texte
    }

    fun type(code: String): String = when (code) {
        "appartement" -> "Appartement"
        "maison" -> "Maison"
        "studio" -> "Studio"
        "kot" -> "Kot"
        "terrain" -> "Terrain"
        else -> "Bien"
    }

    fun source(code: String): String = when (code) {
        "immovlan" -> "Immovlan"
        "zimmo" -> "Zimmo"
        "logicimmo" -> "Logic-immo"
        "realo" -> "Realo"
        "2ememain" -> "2ememain"
        "immoweb" -> "Immoweb"
        "immoweb_mail" -> "Immoweb (e-mail)"
        else -> code.removePrefix("agence:").replaceFirstChar { it.uppercase() }
    }

    /** « il y a 3 h », « hier », « il y a 5 j » à partir d'un timestamp ISO. */
    fun depuis(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        val instant = runCatching { Instant.parse(normaliserIso(iso)) }.getOrNull() ?: return "—"
        val d = Duration.between(instant, Instant.now())
        return when {
            d.toMinutes() < 1 -> "à l'instant"
            d.toMinutes() < 60 -> "il y a ${d.toMinutes()} min"
            d.toHours() < 24 -> "il y a ${d.toHours()} h"
            d.toDays() == 1L -> "hier"
            d.toDays() < 30 -> "il y a ${d.toDays()} j"
            else -> dateCourte(iso)
        }
    }

    fun dateCourte(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", fr))
        }.getOrDefault(iso.take(10))
    }

    /** Postgres renvoie « 2026-10-21T18:45:10.12345+00:00 » : Instant.parse l'accepte si on garde le fuseau. */
    private fun normaliserIso(iso: String): String {
        val sansFuseau = Regex("[+-]\\d\\d:\\d\\d$|Z$").containsMatchIn(iso)
        return if (sansFuseau) iso else iso + "Z"
    }
}
