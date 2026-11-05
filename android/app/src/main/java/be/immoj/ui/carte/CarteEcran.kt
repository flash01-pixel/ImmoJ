package be.immoj.ui.carte

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.DashPathEffect
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import be.immoj.data.Annonce
import be.immoj.data.Filtres
import be.immoj.ui.liste.ListeEtat
import be.immoj.ui.liste.ListeViewModel
import be.immoj.ui.principal.LocalSnackbar
import be.immoj.util.Format
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Centre de la province de Luxembourg (Neufchâteau). */
private val CENTRE_PROVINCE = GeoPoint(49.85, 5.43)

/**
 * Contour approximatif de la province de Luxembourg (repère visuel : la
 * recherche ne porte de toute façon que sur les annonces de la province).
 */
private val CONTOUR_PROVINCE = listOf(
    50.37 to 5.40, 50.40 to 5.55, 50.36 to 5.70, 50.33 to 5.85, 50.30 to 6.00, 50.17 to 6.03,
    50.10 to 5.97, 50.02 to 5.92, 49.93 to 5.85, 49.83 to 5.75, 49.72 to 5.91, 49.63 to 5.93,
    49.55 to 5.82, 49.52 to 5.60, 49.52 to 5.45, 49.62 to 5.30, 49.70 to 5.15, 49.78 to 5.02,
    49.88 to 4.98, 49.97 to 5.05, 50.05 to 5.10, 50.12 to 5.22, 50.20 to 5.22, 50.28 to 5.30,
    50.35 to 5.35,
).map { (lat, lon) -> GeoPoint(lat, lon) }
private val CADRE_PROVINCE = BoundingBox(50.42, 6.05, 49.50, 4.96)

/**
 * Carte OpenStreetMap (osmdroid) : annonces regroupées, cercle de recherche
 * posé d'un appui maintenu (viseur au-dessus du doigt), déplaçable par son
 * point central, rayon par la poignée, les puces ou le curseur ; « Retirer »
 * en un appui (annulable). Voir [CalqueCercle] et [CalqueAnnonces].
 */
@Composable
fun CarteEcran(etat: ListeEtat, vm: ListeViewModel, ouvrirDetail: (String) -> Unit) {
    val contexte = LocalContext.current
    val densite = LocalDensity.current.density
    val portee = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var selection by remember { mutableStateOf<Annonce?>(null) }
    var apercu by remember { mutableStateOf<String?>(null) }         // libellé pendant un geste
    var rayonCurseur by remember { mutableStateOf<Float?>(null) }    // curseur en cours de réglage
    var panneauOuvert by remember { mutableStateOf(false) }
    var enGeste by remember { mutableStateOf(false) }

    val f = etat.filtres
    val centre = if (f.centreLat != null && f.centreLon != null) GeoPoint(f.centreLat, f.centreLon) else null
    val rayon = rayonCurseur?.toDouble() ?: f.rayonKm ?: Filtres.RAYON_DEFAUT_KM
    LaunchedEffect(f.rayonKm) { rayonCurseur = null }

    val carte = remember {
        configurerOsmdroid(contexte)
        MapView(contexte).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(9.5)
            controller.setCenter(centre ?: CENTRE_PROVINCE)
        }
    }
    val calqueCercle = remember {
        CalqueCercle(
            densite = densite,
            onApercu = { lat, lon, km ->
                val commune = Geometrie.communeLaPlusProche(lat, lon, etat.communes)
                val n = vm.compterDansCercle(lat, lon, km)
                val texte = "${commune?.nom ?: "?"} · $n annonce${if (n > 1) "s" else ""}"
                apercu = texte
                enGeste = true
            },
            onCentrePose = { lat, lon -> enGeste = false; apercu = null; vm.definirCentre(lat, lon) },
            onRayonPose = { km -> enGeste = false; apercu = null; vm.definirRayon(km) },
            onAnnulation = { enGeste = false; apercu = null },
        )
    }
    val calqueAnnonces = remember {
        CalqueAnnonces(
            densite = densite,
            onAnnonce = { selection = it },
            onGroupe = { p -> carte.controller.animateTo(p, carte.zoomLevelDouble + 2.0, 300L) },
        )
    }
    LaunchedEffect(Unit) {
        carte.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { selection = null; return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false   // géré par CalqueCercle
        }))
        carte.overlays.add(Polyline(carte).apply {
            setPoints(CONTOUR_PROVINCE + CONTOUR_PROVINCE.first())
            outlinePaint.color = 0x9900AA55.toInt()
            outlinePaint.strokeWidth = 3f * densite
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        })
        carte.overlays.add(calqueAnnonces)
        carte.overlays.add(calqueCercle)   // au-dessus : ses gestes passent en premier
    }
    DisposableEffect(Unit) {
        carte.onResume()
        onDispose { carte.onPause() }
    }

    // Mise à jour des calques sans jamais les retirer (pas de saccade pendant un geste).
    LaunchedEffect(etat.annonces, etat.annoncesHorsZone) {
        calqueAnnonces.dansZone = etat.annonces
        calqueAnnonces.horsZone = etat.annoncesHorsZone
        calqueAnnonces.invalider()
        carte.invalidate()
    }
    LaunchedEffect(centre, rayon, apercu) {
        if (calqueCercle.mode == CalqueCercle.Mode.AUCUN) {
            calqueCercle.centre = centre
            calqueCercle.rayonKm = rayon
        }
        calqueCercle.libelle = apercu ?: ""
        carte.invalidate()
    }

    val demandePosition = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) maPosition(contexte)?.let { carte.controller.animateTo(it, 12.0, 400L) }
    }

    fun retirer() {
        selection = null
        val avant = vm.retirerRayon()
        portee.launch {
            if (snackbar?.showSnackbar("Zone retirée", actionLabel = "Annuler") == SnackbarResult.ActionPerformed) {
                vm.changerFiltres(avant)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { carte }, modifier = Modifier.fillMaxSize())

        // ---- Haut : puce de zone (toujours visible) + compteur ----
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            etat.libelleZone()?.let { zone ->
                InputChip(
                    selected = true, onClick = { retirer() }, label = { Text(zone) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer la zone") },
                )
            }
            Card {
                Text(
                    (if (centre != null) "${etat.annonces.size} dans la zone" else "${etat.annonces.size} annonces") +
                        (if (etat.nbSansPosition > 0) " · ${etat.nbSansPosition} sans position" else "") +
                        (if (centre == null) " · appui maintenu = poser un cercle" else ""),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (enGeste) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Relâche pour poser le cercle", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { calqueCercle.annuler(carte) }) {
                        Icon(Icons.Default.Close, contentDescription = "Annuler")
                    }
                }
            }
        }

        // ---- Droite : Ma position, Toute la province ----
        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallFloatingActionButton(onClick = {
                val ok = ContextCompat.checkSelfPermission(contexte, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (ok) maPosition(contexte)?.let { carte.controller.animateTo(it, 12.0, 400L) }
                    ?: portee.launch { snackbar?.showSnackbar("Position inconnue (active la localisation)") }
                else demandePosition.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Icon(Icons.Default.MyLocation, contentDescription = "Ma position") }
            SmallFloatingActionButton(onClick = { carte.zoomToBoundingBox(CADRE_PROVINCE, true, 32) }) {
                Icon(Icons.Default.ZoomOutMap, contentDescription = "Toute la province")
            }
        }

        // ---- Bas : annonce sélectionnée, puis panneau compact du rayon ----
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
            if (centre != null) {
                val couvertes = Geometrie.communesCouvertes(centre.latitude, centre.longitude, rayon, etat.communes)
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Rayon ${Format.km(rayon)}", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = rayon.toFloat(),
                                onValueChange = { rayonCurseur = (Geometrie.arrondir(it.toDouble())).toFloat() },
                                onValueChangeFinished = { rayonCurseur?.let { vm.definirRayon(it.toDouble()) } },
                                valueRange = Filtres.RAYON_MIN_KM.toFloat()..Filtres.RAYON_MAX_KM.toFloat(),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text("${couvertes.size} commune${if (couvertes.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { panneauOuvert = !panneauOuvert }) {
                                Icon(if (panneauOuvert) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Détails")
                            }
                        }
                        if (panneauOuvert) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Geometrie.PALIERS_KM.forEach { km ->
                                    FilterChip(selected = rayon == km, onClick = { vm.definirRayon(km) },
                                        label = { Text(Format.km(km)) })
                                }
                            }
                            Text(
                                if (couvertes.isEmpty()) "Aucune commune de la province dans ce rayon"
                                else couvertes.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Glisse le point central ou la poignée ; appui maintenu ailleurs pour reposer le cercle.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dernière position connue (sans Play Services), ou null. */
private fun maPosition(contexte: Context): GeoPoint? {
    if (ContextCompat.checkSelfPermission(contexte, Manifest.permission.ACCESS_COARSE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED) return null
    val lm = contexte.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    } catch (_: SecurityException) {
        null
    }
}

/** Configuration osmdroid commune (carte principale et mini-carte de la fiche). */
internal fun configurerOsmdroid(contexte: Context) {
    val conf = Configuration.getInstance()
    conf.load(contexte, contexte.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // OpenStreetMap demande un User-Agent identifiant l'application.
    conf.userAgentValue = "ImmoJ/1.2 (usage personnel)"
    conf.osmdroidBasePath = contexte.cacheDir
    conf.osmdroidTileCache = File(contexte.cacheDir, "tuiles")
}
