package be.immoj.ui.carte

import be.immoj.data.Annonce
import be.immoj.data.Commune
import be.immoj.data.Filtres
import be.immoj.data.distanceKm
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Géométrie de la carte, sans Android : testée par des tests JVM.
 * Décalage du viseur, rayon depuis la poignée, bornes, commune la plus proche,
 * regroupement des marqueurs.
 */
object Geometrie {
    /** Le viseur et l'aperçu du cercle se dessinent à cette hauteur au-dessus du doigt. */
    const val DECALAGE_VISEUR_DP = 70f
    /** Zone tactile du point central et de la poignée (rayon, en dp) : 48 dp de diamètre. */
    const val RAYON_TACTILE_DP = 24f
    /** Rayons proposés en puces, en km. */
    val PALIERS_KM = listOf(2.0, 5.0, 10.0, 15.0, 20.0, 30.0)

    /** Position à viser quand le doigt est en (x, y) : `decalagePx` plus haut. */
    fun pointSousViseur(x: Float, y: Float, decalagePx: Float): Pair<Float, Float> = x to (y - decalagePx)

    fun dpEnPx(dp: Float, densite: Float): Float = dp * densite

    /** Rayon (km) entre le centre et la poignée, arrondi au demi-kilomètre et borné. */
    fun rayonDepuisPoignee(centreLat: Double, centreLon: Double, lat: Double, lon: Double): Double =
        borner(arrondir(distanceKm(centreLat, centreLon, lat, lon)))

    fun arrondir(km: Double): Double = (km * 2).roundToInt() / 2.0

    fun borner(km: Double): Double = km.coerceIn(Filtres.RAYON_MIN_KM, Filtres.RAYON_MAX_KM)

    /** Point à `km` du centre dans la direction `cap` (degrés, 90 = est) : position de la poignée. */
    fun destination(lat: Double, lon: Double, km: Double, cap: Double): Pair<Double, Double> {
        val r = 6371.0
        val d = km / r
        val phi1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val theta = Math.toRadians(cap)
        val phi2 = asin(sin(phi1) * cos(d) + cos(phi1) * sin(d) * cos(theta))
        val l2 = l1 + atan2(sin(theta) * sin(d) * cos(phi1), cos(d) - sin(phi1) * sin(phi2))
        return Math.toDegrees(phi2) to Math.toDegrees(l2)
    }

    /** Commune de la province la plus proche d'un point (null si la liste est vide). */
    fun communeLaPlusProche(lat: Double, lon: Double, communes: List<Commune>): Commune? =
        communes.minByOrNull { distanceKm(lat, lon, it.latitude, it.longitude) }

    /** Communes dont le centre est dans le rayon, triées par nom. */
    fun communesCouvertes(lat: Double, lon: Double, rayonKm: Double, communes: List<Commune>): List<String> =
        communes.filter { distanceKm(lat, lon, it.latitude, it.longitude) <= rayonKm }.map { it.nom }.sorted()

    /** Annonces géolocalisées dans le rayon. */
    fun dansRayon(annonces: List<Annonce>, lat: Double, lon: Double, rayonKm: Double): List<Annonce> =
        annonces.filter { a ->
            a.latitude != null && a.longitude != null && distanceKm(lat, lon, a.latitude, a.longitude) <= rayonKm
        }

    /** Un groupe de marqueurs proches (ou un marqueur seul). */
    data class Groupe(val latitude: Double, val longitude: Double, val annonces: List<Annonce>) {
        val taille: Int get() = annonces.size
        val seule: Annonce? get() = annonces.singleOrNull()
    }

    /**
     * Regroupe les annonces par cellule d'une grille de `cellule` degrés (plus le
     * zoom est faible, plus la cellule est grande). Le groupe est placé au
     * barycentre de ses annonces.
     */
    fun regrouper(annonces: List<Annonce>, cellule: Double): List<Groupe> {
        if (cellule <= 0.0) {
            return annonces.filter { it.latitude != null && it.longitude != null }
                .map { Groupe(it.latitude!!, it.longitude!!, listOf(it)) }
        }
        return annonces.filter { it.latitude != null && it.longitude != null }
            .groupBy { floor(it.latitude!! / cellule).toLong() to floor(it.longitude!! / cellule).toLong() }
            .values.map { groupe ->
                Groupe(groupe.map { it.latitude!! }.average(), groupe.map { it.longitude!! }.average(), groupe)
            }
    }

    /** Taille de cellule (degrés) selon le niveau de zoom osmdroid : ~40 px de côté. */
    fun celluleSelonZoom(zoom: Double): Double = when {
        zoom >= 15.0 -> 0.0            // marqueurs individuels
        zoom >= 13.0 -> 0.004
        zoom >= 11.0 -> 0.015
        zoom >= 9.5 -> 0.04
        else -> 0.08
    }
}
