package be.immoj.ui.carte

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import be.immoj.util.Format
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/** Centre de la province de Luxembourg (Neufchâteau). */
private val CENTRE_PROVINCE = GeoPoint(49.85, 5.43)

/**
 * Carte OpenStreetMap (osmdroid, gratuit) : un marqueur par annonce.
 * Un appui long sur la carte définit le centre du rayon de recherche.
 */
@Composable
fun CarteEcran(
    annonces: List<Annonce>,
    centreLat: Double?,
    centreLon: Double?,
    rayonKm: Double?,
    onPointChoisi: (Double, Double) -> Unit,
    ouvrirDetail: (String) -> Unit,
) {
    val contexte = LocalContext.current
    var selection by remember { mutableStateOf<Annonce?>(null) }

    val carte = remember {
        configurerOsmdroid(contexte)
        MapView(contexte).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(9.5)
            controller.setCenter(
                if (centreLat != null && centreLon != null) GeoPoint(centreLat, centreLon) else CENTRE_PROVINCE
            )
        }
    }
    DisposableEffect(Unit) {
        carte.onResume()
        onDispose { carte.onPause() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { carte },
            modifier = Modifier.fillMaxSize(),
            update = { vue ->
                vue.overlays.clear()
                vue.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        selection = null
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        onPointChoisi(p.latitude, p.longitude)
                        return true
                    }
                }))
                // Cercle du rayon de recherche
                if (centreLat != null && centreLon != null && rayonKm != null && rayonKm > 0) {
                    val cercle = Polygon(vue).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(centreLat, centreLon), rayonKm * 1000)
                        fillPaint.color = 0x2200AA55
                        outlinePaint.color = 0xFF00AA55.toInt()
                        outlinePaint.strokeWidth = 3f
                    }
                    vue.overlays.add(cercle)
                    vue.overlays.add(Marker(vue).apply {
                        position = GeoPoint(centreLat, centreLon)
                        title = "Centre du rayon"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    })
                }
                // Un marqueur par annonce géolocalisée
                annonces.filter { it.latitude != null && it.longitude != null }.forEach { a ->
                    vue.overlays.add(Marker(vue).apply {
                        position = GeoPoint(a.latitude!!, a.longitude!!)
                        title = Format.prix(a.prix, a.transaction)
                        snippet = "${Format.type(a.type)} · ${a.lieu}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ -> selection = a; true }
                    })
                }
                vue.invalidate()
            },
        )

        Card(modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) {
            Text(
                "${annonces.count { it.latitude != null }} annonce(s) · appui long = centre du rayon",
                modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall,
            )
        }

        selection?.let { a ->
            Card(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
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
    }
}

private fun configurerOsmdroid(contexte: Context) {
    val conf = Configuration.getInstance()
    conf.load(contexte, contexte.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // OpenStreetMap demande un User-Agent identifiant l'application.
    conf.userAgentValue = "ImmoJ/0.2 (usage personnel)"
    conf.osmdroidBasePath = contexte.cacheDir
    conf.osmdroidTileCache = File(contexte.cacheDir, "tuiles")
}
