package be.immoj.ui.carte

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import be.immoj.data.Annonce
import be.immoj.util.Format
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Calque du cercle de recherche et de son geste de pose.
 *
 * Il dessine lui-même le cercle, le point central, la poignée de rayon et le
 * viseur : rien n'est retiré ni rajouté à la carte pendant un geste, on change
 * des champs et on redessine.
 *
 * Gestes (jamais de déplacement sur un simple appui) :
 *  - appui maintenu n'importe où → mode « pose » : vibration, viseur et aperçu du
 *    cercle 70 dp au-dessus du doigt, la carte ne défile plus, relâcher = poser ;
 *  - doigt posé sur le point central (zone de 48 dp) → glisser le centre ;
 *  - doigt posé sur la poignée → changer le rayon ;
 *  - ACTION_CANCEL (le doigt sort de la carte) → annulation.
 * Les données ne sont recalculées qu'au relâchement (`onCentrePose`, `onRayonPose`) ;
 * pendant le geste, seul `onApercu` est appelé (libellé et compteur en direct).
 */
class CalqueCercle(
    private val densite: Float,
    private val onApercu: (lat: Double, lon: Double, rayonKm: Double) -> Unit,
    private val onCentrePose: (lat: Double, lon: Double) -> Unit,
    private val onRayonPose: (rayonKm: Double) -> Unit,
    private val onAnnulation: () -> Unit,
) : Overlay() {

    enum class Mode { AUCUN, POSE, CENTRE, POIGNEE }

    var centre: GeoPoint? = null
    var rayonKm: Double = 10.0
    /** Texte affiché sous le viseur pendant un geste (« Arlon · 37 annonces »). */
    var libelle: String = ""
    var mode: Mode = Mode.AUCUN
        private set
    private var apercu: GeoPoint? = null          // centre pendant la pose / le glisser
    private var rayonApercu: Double = 10.0

    private val decalagePx = Geometrie.dpEnPx(Geometrie.DECALAGE_VISEUR_DP, densite)
    private val rayonTactilePx = Geometrie.dpEnPx(Geometrie.RAYON_TACTILE_DP, densite)

    private val remplissage = Paint().apply { color = 0x2200AA55; style = Paint.Style.FILL }
    private val contour = Paint().apply {
        color = 0xFF00AA55.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f * densite; isAntiAlias = true
    }
    private val contourApercu = Paint(contour).apply { pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f) }
    private val point = Paint().apply { color = 0xFF00AA55.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val pointBord = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * densite; isAntiAlias = true
    }
    private val poignee = Paint().apply { color = 0xFF1B5E20.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val viseur = Paint().apply {
        color = 0xFFD32F2F.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * densite; isAntiAlias = true
    }
    private val texte = Paint().apply {
        color = Color.WHITE; textSize = 13f * densite; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val fondTexte = Paint().apply { color = 0xCC1B5E20.toInt(); style = Paint.Style.FILL; isAntiAlias = true }

    // ------------------------------------------------------------------ dessin

    override fun draw(canvas: Canvas, projection: Projection) {
        val c = if (mode == Mode.AUCUN) centre else apercu
        val r = if (mode == Mode.AUCUN) rayonKm else rayonApercu
        if (c == null) return
        val px = projection.toPixels(c, null)
        val rayonPx = projection.metersToPixels((r * 1000).toFloat(), c.latitude, projection.zoomLevel)
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), rayonPx, remplissage)
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), rayonPx, if (mode == Mode.POSE) contourApercu else contour)
        // Point central (toujours visible, zone tactile de 48 dp autour)
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), 7f * densite, point)
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), 7f * densite, pointBord)
        // Poignée sur le bord est
        val pp = positionPoignee(projection, c, r)
        canvas.drawCircle(pp.x.toFloat(), pp.y.toFloat(), 9f * densite, poignee)
        canvas.drawCircle(pp.x.toFloat(), pp.y.toFloat(), 9f * densite, pointBord)
        if (mode == Mode.POSE) {
            val taille = 14f * densite
            canvas.drawLine(px.x - taille, px.y.toFloat(), px.x + taille, px.y.toFloat(), viseur)
            canvas.drawLine(px.x.toFloat(), px.y - taille, px.x.toFloat(), px.y + taille, viseur)
        }
        if (mode != Mode.AUCUN && libelle.isNotBlank()) {
            val largeur = texte.measureText(libelle) + 16f * densite
            val haut = px.y + 18f * densite
            val fond = RectF(px.x - largeur / 2, haut, px.x + largeur / 2, haut + 22f * densite)
            canvas.drawRoundRect(fond, 8f * densite, 8f * densite, fondTexte)
            canvas.drawText(libelle, px.x.toFloat(), haut + 15.5f * densite, texte)
        }
    }

    private fun positionPoignee(projection: Projection, c: GeoPoint, r: Double): Point {
        val (lat, lon) = Geometrie.destination(c.latitude, c.longitude, r, 90.0)
        return projection.toPixels(GeoPoint(lat, lon), null)
    }

    // ------------------------------------------------------------------ gestes

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        if (mode != Mode.AUCUN) return true
        mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        mode = Mode.POSE
        rayonApercu = centre?.let { rayonKm } ?: rayonKm
        deplacerApercu(e, mapView)
        return true
    }

    override fun onTouchEvent(e: MotionEvent, mapView: MapView): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val c = centre ?: return false
                val proj = mapView.projection
                val px = proj.toPixels(c, null)
                val pp = positionPoignee(proj, c, rayonKm)
                mode = when {
                    hypot(e.x - pp.x, e.y - pp.y) <= rayonTactilePx -> Mode.POIGNEE
                    hypot(e.x - px.x, e.y - px.y) <= rayonTactilePx -> Mode.CENTRE
                    else -> return false
                }
                apercu = c
                rayonApercu = rayonKm
                mapView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.AUCUN) return false
                when (mode) {
                    Mode.POSE -> deplacerApercu(e, mapView)
                    Mode.CENTRE -> {
                        apercu = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                        signalerApercu(mapView)
                    }
                    Mode.POIGNEE -> {
                        val c = apercu ?: return true
                        val g = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                        rayonApercu = Geometrie.rayonDepuisPoignee(c.latitude, c.longitude, g.latitude, g.longitude)
                        signalerApercu(mapView)
                    }
                    else -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.AUCUN) return false
                val m = mode
                val c = apercu
                mode = Mode.AUCUN
                libelle = ""
                if (c != null) {
                    centre = c
                    rayonKm = rayonApercu
                    when (m) {
                        Mode.POIGNEE -> onRayonPose(rayonApercu)
                        else -> onCentrePose(c.latitude, c.longitude)
                    }
                }
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.AUCUN) return false
                annuler(mapView)
                return true
            }
        }
        return false
    }

    /** Annule le geste en cours (le doigt sort de la carte, ou bouton ✕). */
    fun annuler(mapView: MapView?) {
        if (mode == Mode.AUCUN) return
        mode = Mode.AUCUN
        apercu = null
        libelle = ""
        onAnnulation()
        mapView?.invalidate()
    }

    private fun deplacerApercu(e: MotionEvent, mapView: MapView) {
        val (x, y) = Geometrie.pointSousViseur(e.x, e.y, decalagePx)
        apercu = mapView.projection.fromPixels(x.toInt(), y.toInt()) as GeoPoint
        signalerApercu(mapView)
    }

    private fun signalerApercu(mapView: MapView) {
        apercu?.let { onApercu(it.latitude, it.longitude, rayonApercu) }
        mapView.invalidate()
    }
}

/**
 * Calque des annonces : marqueurs regroupés avec leur nombre, étiquette de prix
 * sur les marqueurs seuls, annonces hors zone en grisé. Dessin direct (pas un
 * objet Marker par annonce), redessiné à la demande.
 */
class CalqueAnnonces(
    private val densite: Float,
    private val onAnnonce: (Annonce) -> Unit,
    private val onGroupe: (GeoPoint) -> Unit,
) : Overlay() {
    var dansZone: List<Annonce> = emptyList()
    var horsZone: List<Annonce> = emptyList()
    private var groupes: List<Geometrie.Groupe> = emptyList()
    private var groupesHors: List<Geometrie.Groupe> = emptyList()
    private var zoomCalcule = -1.0
    private var pixels: List<Pair<Point, Geometrie.Groupe>> = emptyList()

    private val fond = Paint().apply { color = 0xFF1B5E20.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val fondHors = Paint().apply { color = 0x66777777; style = Paint.Style.FILL; isAntiAlias = true }
    private val bord = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f * densite; isAntiAlias = true
    }
    private val texte = Paint().apply {
        color = Color.WHITE; textSize = 11f * densite; isAntiAlias = true; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** À appeler quand les listes changent (recalcul des groupes au prochain dessin). */
    fun invalider() { zoomCalcule = -1.0 }

    override fun draw(canvas: Canvas, projection: Projection) {
        val zoom = projection.zoomLevel
        if (zoom != zoomCalcule) {
            val cellule = Geometrie.celluleSelonZoom(zoom)
            groupes = Geometrie.regrouper(dansZone, cellule)
            groupesHors = Geometrie.regrouper(horsZone, cellule)
            zoomCalcule = zoom
        }
        val liste = ArrayList<Pair<Point, Geometrie.Groupe>>(groupes.size)
        for (g in groupesHors) dessiner(canvas, projection, g, true)
        for (g in groupes) liste += dessiner(canvas, projection, g, false) to g
        pixels = liste
    }

    private fun dessiner(canvas: Canvas, projection: Projection, g: Geometrie.Groupe, hors: Boolean): Point {
        val p = projection.toPixels(GeoPoint(g.latitude, g.longitude), null)
        val seule = g.seule
        val libelle = if (seule != null) prixCourt(seule) else "${g.taille}"
        val largeur = texte.measureText(libelle) + 12f * densite
        val hauteur = 20f * densite
        val rect = RectF(p.x - largeur / 2, p.y - hauteur, p.x + largeur / 2, p.y.toFloat())
        canvas.drawRoundRect(rect, hauteur / 2, hauteur / 2, if (hors) fondHors else fond)
        if (!hors) canvas.drawRoundRect(rect, hauteur / 2, hauteur / 2, bord)
        canvas.drawText(libelle, p.x.toFloat(), p.y - 6f * densite, texte)
        return p
    }

    private fun prixCourt(a: Annonce): String {
        val p = a.prix ?: return "?"
        return if (p >= 10000) "${(p / 1000)} k" else Format.nombre(p)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val rayon = Geometrie.dpEnPx(22f, densite)
        val touche = pixels.minByOrNull { hypot(e.x - it.first.x, e.y - it.first.y + 10f * densite) }
            ?: return false
        val (p, g) = touche
        if (hypot(e.x - p.x, e.y - p.y + 10f * densite) > rayon) return false
        val seule = g.seule
        if (seule != null) onAnnonce(seule) else onGroupe(GeoPoint(g.latitude, g.longitude))
        return true
    }
}
