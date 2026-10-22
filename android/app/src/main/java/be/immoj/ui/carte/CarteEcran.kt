package be.immoj.ui.carte

import be.immoj.data.Filtres
import android.content.Context
import android.graphics.DashPathEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import be.immoj.data.Annonce
import be.immoj.data.Commune
import be.immoj.data.distanceKm
import be.immoj.util.Format
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.roundToInt

/** Centre de la province de Luxembourg (Neufchâteau). */
private val CENTRE_PROVINCE = GeoPoint(49.85, 5.43)

/**
 * Contour approximatif de la province de Luxembourg (repère visuel : la
 * recherche ne porte de toute façon que sur les annonces de la province, le
 * collecteur écarte tout ce qui est au-delà de la frontière ou dans une autre
 * province).
 */
private val CONTOUR_PROVINCE = listOf(
    50.37 to 5.40, 50.40 to 5.55, 50.36 to 5.70, 50.33 to 5.85, 50.30 to 6.00, 50.17 to 6.03,
    50.10 to 5.97, 50.02 to 5.92, 49.93 to 5.85, 49.83 to 5.75, 49.72 to 5.91, 49.63 to 5.93,
    49.55 to 5.82, 49.52 to 5.60, 49.52 to 5.45, 49.62 to 5.30, 49.70 to 5.15, 49.78 to 5.02,
    49.88 to 4.98, 49.97 to 5.05, 50.05 to 5.10, 50.12 to 5.22, 50.20 to 5.22, 50.28 to 5.30,
    50.35 to 5.35,
).map { (lat, lon) -> GeoPoint(lat, lon) }


/**
 * Carte OpenStreetMap (osmdroid, gratuit) : un marqueur par annonce et un
 * cercle de recherche que l'on manipule directement sur la carte :
 *  - appui long : place (ou déplace) le centre ;
 *  - le marqueur du centre se fait glisser ;
 *  - la poignée sur le bord du cercle se fait glisser pour changer le rayon ;
 *  - le curseur du panneau du bas règle aussi le rayon ;
 *  - « Retirer » supprime le cercle.
 * Les communes couvertes et le nombre d'annonces s'actualisent en direct.
 */
@Composable
fun CarteEcran(
    annonces: List<Annonce>,
    communes: List<Commune>,
    centreLat: Double?,
    centreLon: Double?,
    rayonKm: Double?,
    onPointChoisi: (Double, Double) -> Unit,
    onRayonChange: (Double) -> Unit,
    onRetirerRayon: () -> Unit,
    ouvrirDetail: (String) -> Unit,
) {
    val contexte = LocalContext.current
    var selection by remember { mutableStateOf<Annonce?>(null) }
    // Rayon en cours de réglage (curseur ou poignée), avant validation dans les filtres.
    var rayonLocal by remember { mutableStateOf<Double?>(null) }
    var centreLocal by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(rayonKm) { rayonLocal = null }
    LaunchedEffect(centreLat, centreLon) { centreLocal = null }

    val centre = centreLocal ?: if (centreLat != null && centreLon != null) GeoPoint(centreLat, centreLon) else null
    val rayon = rayonLocal ?: rayonKm ?: Filtres.RAYON_DEFAUT_KM
    val rayonDefini = centre != null

    val carte = remember {
        configurerOsmdroid(contexte)
        MapView(contexte).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(9.5)
            controller.setCenter(centre ?: CENTRE_PROVINCE)
        }
    }
    val calques = remember { Calques(carte) }
    DisposableEffect(Unit) {
        carte.onResume()
        onDispose { carte.onPause() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { carte },
            modifier = Modifier.fillMaxSize(),
            update = { vue ->
                calques.installer(vue, object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        selection = null
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        onPointChoisi(p.latitude, p.longitude)
                        return true
                    }
                })
                calques.annonces(vue, annonces) { selection = it }
                calques.cercle(
                    vue, centre, if (rayonDefini) rayon else null,
                    onCentreGlisse = { p -> centreLocal = p },
                    onCentreDepose = { p -> onPointChoisi(p.latitude, p.longitude) },
                    onRayonGlisse = { km -> rayonLocal = km },
                    onRayonDepose = { km -> onRayonChange(km) },
                )
                vue.invalidate()
            },
        )

        Card(modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) {
            val sansPosition = annonces.count { it.latitude == null || it.longitude == null }
            Text(
                (if (rayonDefini) "${annonces.size} dans la zone" else "${annonces.size} annonce(s)") +
                    (if (sansPosition > 0) " · $sansPosition sans position" else "") +
                    (if (!rayonDefini) " · appui long = placer un rayon" else ""),
                modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall,
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            selection?.let { a ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(Format.prix(a.prix, a.transaction), style = MaterialTheme.typography.titleMedium)
                        Text("${Format.type(a.type)} · ${a.lieu}")
                        val details = listOfNotNull(a.chambres?.let { "$it ch." }, a.surface?.let { "$it m²" })
                        if (details.isNotEmpty()) Text(details.joinToString(" · "))
                        Button(onClick = { ouvrirDetail(a.id) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Voir l'annonce")
                        }
                    }
                }
            }
            if (rayonDefini && centre != null) {
                val couvertes = communes.filter {
                    distanceKm(centre.latitude, centre.longitude, it.latitude, it.longitude) <= rayon
                }.map { it.nom }.sorted()
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Rayon : ${Format.km(rayon)}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { selection = null; onRetirerRayon() }) { Text("Retirer") }
                        }
                        Slider(
                            value = rayon.toFloat(),
                            onValueChange = { rayonLocal = (it * 2).roundToInt() / 2.0 },
                            onValueChangeFinished = { rayonLocal?.let(onRayonChange) },
                            valueRange = Filtres.RAYON_MIN_KM.toFloat()..Filtres.RAYON_MAX_KM.toFloat(),
                        )
                        Text(
                            if (couvertes.isEmpty()) "Aucune commune de la province dans ce rayon"
                            else "Communes couvertes (${couvertes.size}) : ${couvertes.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Glisse le centre ou la poignée du cercle pour ajuster. Seule la province de " +
                                "Luxembourg est prise en compte.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (centre == null) {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Rechercher autour d'un point ?", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp))
                        OutlinedButton(onClick = {
                            val c = carte.mapCenter
                            onPointChoisi(c.latitude, c.longitude)
                        }) { Text("Placer un rayon ici") }
                    }
                }
            }
        }
    }
}

/**
 * Les calques de la carte, conservés entre deux recompositions pour ne pas
 * tout reconstruire à chaque mouvement du curseur.
 */
private class Calques(vue: MapView) {
    private var evenements: MapEventsOverlay? = null
    private val contour = Polyline(vue).apply {
        setPoints(CONTOUR_PROVINCE + CONTOUR_PROVINCE.first())
        outlinePaint.color = 0x9900AA55.toInt()
        outlinePaint.strokeWidth = 3f
        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        isEnabled = true
    }
    private val cercle = Polygon(vue).apply {
        fillPaint.color = 0x2200AA55
        outlinePaint.color = 0xFF00AA55.toInt()
        outlinePaint.strokeWidth = 3f
    }
    private val centre = Marker(vue).apply {
        title = "Centre du rayon (glisser pour déplacer)"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        isDraggable = true
    }
    private val poignee = Marker(vue).apply {
        title = "Poignée du rayon (glisser pour agrandir ou réduire)"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        isDraggable = true
        alpha = 0.85f
    }
    private val dossierAnnonces = FolderOverlay()
    private var annoncesAffichees: List<Annonce>? = null

    fun installer(vue: MapView, recepteur: MapEventsReceiver) {
        if (evenements == null) {
            evenements = MapEventsOverlay(recepteur)
            vue.overlays.clear()
            vue.overlays.add(evenements)
            vue.overlays.add(contour)
            vue.overlays.add(dossierAnnonces)
        }
    }

    fun annonces(vue: MapView, annonces: List<Annonce>, onSelection: (Annonce) -> Unit) {
        if (annoncesAffichees === annonces) return
        annoncesAffichees = annonces
        dossierAnnonces.items.clear()
        annonces.filter { it.latitude != null && it.longitude != null }.forEach { a ->
            dossierAnnonces.add(Marker(vue).apply {
                position = GeoPoint(a.latitude!!, a.longitude!!)
                title = Format.prix(a.prix, a.transaction)
                snippet = "${Format.type(a.type)} · ${a.lieu}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> onSelection(a); true }
            })
        }
    }

    fun cercle(
        vue: MapView, centreGeo: GeoPoint?, rayonKm: Double?,
        onCentreGlisse: (GeoPoint) -> Unit, onCentreDepose: (GeoPoint) -> Unit,
        onRayonGlisse: (Double) -> Unit, onRayonDepose: (Double) -> Unit,
    ) {
        vue.overlays.remove(cercle); vue.overlays.remove(centre); vue.overlays.remove(poignee)
        if (centreGeo == null || rayonKm == null) return
        cercle.points = Polygon.pointsAsCircle(centreGeo, rayonKm * 1000)
        centre.position = centreGeo
        poignee.position = centreGeo.destinationPoint(rayonKm * 1000, 90.0)
        centre.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(m: Marker) {}
            override fun onMarkerDrag(m: Marker) {
                cercle.points = Polygon.pointsAsCircle(m.position, rayonKm * 1000)
                poignee.position = m.position.destinationPoint(rayonKm * 1000, 90.0)
                onCentreGlisse(m.position)
                vue.invalidate()
            }
            override fun onMarkerDragEnd(m: Marker) { onCentreDepose(m.position) }
        })
        poignee.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(m: Marker) {}
            override fun onMarkerDrag(m: Marker) {
                val km = (centreGeo.distanceToAsDouble(m.position) / 1000)
                    .coerceIn(Filtres.RAYON_MIN_KM, Filtres.RAYON_MAX_KM)
                cercle.points = Polygon.pointsAsCircle(centreGeo, km * 1000)
                onRayonGlisse((km * 2).roundToInt() / 2.0)
                vue.invalidate()
            }
            override fun onMarkerDragEnd(m: Marker) {
                onRayonDepose((centreGeo.distanceToAsDouble(m.position) / 1000)
                    .coerceIn(Filtres.RAYON_MIN_KM, Filtres.RAYON_MAX_KM))
            }
        })
        // Le cercle sous les annonces, les poignées au-dessus de tout.
        vue.overlays.add(1, cercle)
        vue.overlays.add(centre)
        vue.overlays.add(poignee)
    }
}

private fun configurerOsmdroid(contexte: Context) {
    val conf = Configuration.getInstance()
    conf.load(contexte, contexte.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // OpenStreetMap demande un User-Agent identifiant l'application.
    conf.userAgentValue = "ImmoJ/1.1 (usage personnel)"
    conf.osmdroidBasePath = contexte.cacheDir
    conf.osmdroidTileCache = File(contexte.cacheDir, "tuiles")
}
