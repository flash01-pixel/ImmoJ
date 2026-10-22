package be.immoj.data

import android.content.Context
import be.immoj.BuildConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences as DsPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<DsPreferences> by preferencesDataStore(name = "immoj")

/** Réglages de l'appli, enregistrés sur le téléphone. */
data class Reglages(
    val supabaseUrl: String = "",
    val supabaseCle: String = "",
    val transaction: String = "location",   // "location" ou "achat"
    val tri: String = "recent",             // "recent" ou "prix"
    val filtres: Filtres = Filtres(),
    val ntfyTopic: String = "",
    val theme: String = "systeme",          // "systeme", "clair", "sombre"
    val alertes: String = "recherches",     // "recherches", "toutes", "aucune"
    val alerterBaisses: Boolean = true,     // notifier les baisses de prix
    val frequenceHeures: Int = 1,           // vérification en arrière-plan (1, 2, 4, 8 h)
    val heuresCalmes: Boolean = true,       // pas de notification entre 22 h et 7 h
    val photosEnListe: Boolean = true,      // vignette dans la liste
    val githubToken: String = "",           // jeton pour « Lancer une collecte maintenant »
    val visitesRecherches: Map<String, String> = emptyMap(),  // id de recherche → dernière consultation
    val modeListe: String = "cartes",       // "cartes", "detaille", "compact", "grille"
    val derniereVisiteListe: String? = null, // séparateur « Nouveau depuis ta dernière visite »
) {
    /** Vrai quand l'appli peut se connecter à Supabase. */
    val configure: Boolean get() = supabaseUrl.startsWith("http") && supabaseCle.length > 20

    /** Vrai si la connexion vient de la configuration intégrée à l'APK (rien à saisir). */
    val integree: Boolean
        get() = supabaseUrl == BuildConfig.SUPABASE_URL && BuildConfig.SUPABASE_URL.isNotBlank()
}

class Preferences(private val context: Context) {
    private object Cles {
        val URL = stringPreferencesKey("supabase_url")
        val CLE = stringPreferencesKey("supabase_cle")
        val TRANSACTION = stringPreferencesKey("transaction")
        val TRI = stringPreferencesKey("tri")
        val FILTRES = stringPreferencesKey("filtres")
        val THEME = stringPreferencesKey("theme")
        val ALERTES = stringPreferencesKey("alertes")
        val BAISSES = stringPreferencesKey("alerter_baisses")
        val FREQUENCE = stringPreferencesKey("frequence_heures")
        val CALMES = stringPreferencesKey("heures_calmes")
        val PHOTOS = stringPreferencesKey("photos_en_liste")
        val GITHUB = stringPreferencesKey("github_token")
        val VISITES = stringPreferencesKey("visites_recherches")
        val MODE_LISTE = stringPreferencesKey("mode_liste")
        val VISITE_LISTE = stringPreferencesKey("derniere_visite_liste")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flux: Flow<Reglages> = context.dataStore.data.map { p ->
        Reglages(
            // Les réglages saisis à la main priment ; sinon ceux intégrés à l'APK.
            supabaseUrl = p[Cles.URL]?.ifBlank { null } ?: BuildConfig.SUPABASE_URL,
            supabaseCle = p[Cles.CLE]?.ifBlank { null } ?: BuildConfig.SUPABASE_ANON_KEY,
            ntfyTopic = BuildConfig.NTFY_TOPIC,
            transaction = p[Cles.TRANSACTION] ?: "location",
            tri = p[Cles.TRI] ?: "recent",
            filtres = p[Cles.FILTRES]?.let { runCatching { json.decodeFromString<Filtres>(it) }.getOrNull() }
                ?: Filtres(),
            theme = p[Cles.THEME] ?: "systeme",
            alertes = p[Cles.ALERTES] ?: "recherches",
            alerterBaisses = p[Cles.BAISSES] != "non",
            frequenceHeures = p[Cles.FREQUENCE]?.toIntOrNull() ?: 1,
            heuresCalmes = p[Cles.CALMES] != "non",
            photosEnListe = p[Cles.PHOTOS] != "non",
            githubToken = p[Cles.GITHUB] ?: "",
            visitesRecherches = p[Cles.VISITES]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap(),
            modeListe = p[Cles.MODE_LISTE] ?: "cartes",
            derniereVisiteListe = p[Cles.VISITE_LISTE],
        )
    }

    /** Note le début de cette visite de la liste et renvoie la précédente (pour le séparateur). */
    suspend fun ouvrirVisiteListe(): String? {
        val precedente = lire().derniereVisiteListe
        context.dataStore.edit { it[Cles.VISITE_LISTE] = java.time.Instant.now().toString() }
        return precedente
    }

    /** Note qu'une recherche sauvegardée vient d'être consultée (compteur « nouvelles depuis »). */
    suspend fun marquerRechercheVisitee(id: String) {
        val visites = lire().visitesRecherches + (id to java.time.Instant.now().toString())
        context.dataStore.edit { it[Cles.VISITES] = json.encodeToString(visites) }
    }

    /** Enregistre un réglage simple par son nom (voir [Reglages]). */
    suspend fun enregistrerReglage(nom: String, valeur: String) {
        val cle = when (nom) {
            "theme" -> Cles.THEME
            "alertes" -> Cles.ALERTES
            "alerterBaisses" -> Cles.BAISSES
            "frequenceHeures" -> Cles.FREQUENCE
            "heuresCalmes" -> Cles.CALMES
            "photosEnListe" -> Cles.PHOTOS
            "githubToken" -> Cles.GITHUB
            "modeListe" -> Cles.MODE_LISTE
            else -> return
        }
        context.dataStore.edit { it[cle] = valeur }
    }

    suspend fun enregistrerBooleen(nom: String, valeur: Boolean) =
        enregistrerReglage(nom, if (valeur) "oui" else "non")

    suspend fun lire(): Reglages = flux.first()

    suspend fun enregistrerConnexion(url: String, cle: String) {
        context.dataStore.edit {
            it[Cles.URL] = url.trim().trimEnd('/')
            it[Cles.CLE] = cle.trim()
        }
    }

    suspend fun enregistrerTransaction(transaction: String) {
        context.dataStore.edit { it[Cles.TRANSACTION] = transaction }
    }

    suspend fun enregistrerTri(tri: String) {
        context.dataStore.edit { it[Cles.TRI] = tri }
    }

    suspend fun enregistrerFiltres(filtres: Filtres) {
        context.dataStore.edit { it[Cles.FILTRES] = json.encodeToString(filtres) }
    }
}
