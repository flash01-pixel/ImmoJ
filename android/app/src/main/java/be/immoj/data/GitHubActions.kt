package be.immoj.data

import be.immoj.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Déclenche le workflow de collecte sur GitHub (« Actualiser » avec effet réel :
 * le collecteur tourne tout de suite au lieu d'attendre l'heure suivante).
 * Nécessite un jeton GitHub personnel (droits « Actions : écriture » sur le dépôt),
 * saisi dans les paramètres et conservé uniquement sur le téléphone.
 */
object GitHubActions {
    private val client = OkHttpClient()

    /** Dépôt déduit de l'URL des données (…/<proprietaire>/<depot>/<branche>/annonces.json). */
    private fun depot(): String {
        val m = Regex("githubusercontent\\.com/([^/]+)/([^/]+)/").find(BuildConfig.DONNEES_URL)
        return if (m != null) "${m.groupValues[1]}/${m.groupValues[2]}" else "flash01-pixel/ImmoJ"
    }

    suspend fun lancerCollecte(jeton: String) = withContext(Dispatchers.IO) {
        val corps = """{"ref":"main"}""".toRequestBody("application/json".toMediaType())
        val requete = Request.Builder()
            .url("https://api.github.com/repos/${depot()}/actions/workflows/collecte.yml/dispatches")
            .header("Authorization", "Bearer ${jeton.trim()}")
            .header("Accept", "application/vnd.github+json")
            .post(corps)
            .build()
        client.newCall(requete).execute().use { r ->
            if (r.code != 204) {
                val raison = when (r.code) {
                    401 -> "jeton refusé"
                    403 -> "le jeton n'a pas le droit « Actions » sur le dépôt"
                    404 -> "dépôt ou workflow introuvable (jeton sans accès ?)"
                    else -> "HTTP ${r.code}"
                }
                throw IllegalStateException(raison)
            }
        }
    }
}
