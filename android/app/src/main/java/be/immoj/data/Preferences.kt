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
        )
    }

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
