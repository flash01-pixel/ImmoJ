package be.immoj.util

import java.util.Locale

/** Mise en forme des prix, dates et libellés, en français. */
object Format {
    private val fr = Locale.FRANCE

    fun prix(valeur: Int?, transaction: String): String {
        if (valeur == null) return "Prix non communiqué"
        val texte = String.format(fr, "%,d", valeur).replace('\u00a0', ' ') + " €"
        return if (transaction == "location") "$texte / mois" else texte
    }

    /** « 7,5 km » ou « 10 km ». */
    fun km(valeur: Double): String =
        if (valeur == valeur.toLong().toDouble()) "${valeur.toLong()} km"
        else String.format(fr, "%.1f km", valeur)

    fun type(code: String): String = when (code) {
        "appartement" -> "Appartement"
        "maison" -> "Maison"
        "studio" -> "Studio"
        "kot" -> "Kot"
        "chambre" -> "Chambre"
        "terrain" -> "Terrain"
        "immeuble" -> "Immeuble"
        "commerce" -> "Commerce / bureau"
        "garage" -> "Garage / parking"
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
        "notaire" -> "Notaires"
        "alertes_mail" -> "Alertes e-mail"
        "agences:generique" -> "Sites d'agences (maison)"
        "agences:whise" -> "Agences Whise"
        "agences:whise_webulous" -> "Agences Whise (Honesty…)"
        "agences:era" -> "Agences ERA"
        "agences:omnicasa_gatsby", "agences:omnicasa_api" -> "Agences Omnicasa"
        "agences:wordpress" -> "Agences WordPress"
        else -> if (code.startsWith("agences:")) "Agence" else code.replaceFirstChar { it.uppercase() }
    }

    /** « il y a 3 h », « hier », « il y a 5 j » à partir d'un horodatage ISO (voir [Dates]). */
    fun depuis(iso: String?): String = Dates.depuis(iso)

    fun dateCourte(iso: String?): String = Dates.dateCourte(iso)

    /** « 1 189 » (séparateur de milliers, espace insécable remplacée par une espace). */
    fun nombre(valeur: Int): String = String.format(fr, "%,d", valeur).replace('\u00a0', ' ')
}
