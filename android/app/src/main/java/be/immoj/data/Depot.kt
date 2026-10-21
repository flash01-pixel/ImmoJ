package be.immoj.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Dépôt de données : seul point de contact entre les écrans et les données.
 * Deux implémentations : Supabase ([DepotSupabase]) et le mode « sans compte »
 * ([DepotLocal], fichier JSON publié par GitHub + stockage sur le téléphone).
 * Toutes les fonctions sont `suspend` et lèvent une exception en cas d'erreur :
 * les ViewModels la transforment en message.
 */
interface Depot {
    suspend fun listerAnnonces(transaction: String, tri: String, filtres: Filtres): List<Annonce>
    suspend fun annonce(id: String): Annonce?
    suspend fun historiquePrix(id: String): List<PrixHistorique>
    suspend fun changerStatut(id: String, statut: StatutPerso, note: String?)
    suspend fun etatSources(): List<EtatSource>
    suspend fun communes(): List<Commune>
    suspend fun recherches(): List<RechercheSauvegardee>
    suspend fun creerRecherche(r: RechercheSauvegardee): RechercheSauvegardee
    suspend fun modifierRecherche(id: String, champs: Map<String, Boolean>)
    suspend fun supprimerRecherche(id: String)
}

/** Choisit le dépôt selon les réglages : Supabase si configuré, sinon le mode local. */
class DepotRouteur(
    private val preferences: Preferences,
    private val supabase: DepotSupabase,
    private val local: DepotLocal,
) : Depot {
    private suspend fun actif(): Depot = if (preferences.lire().configure) supabase else local

    override suspend fun listerAnnonces(transaction: String, tri: String, filtres: Filtres) =
        actif().listerAnnonces(transaction, tri, filtres)
    override suspend fun annonce(id: String) = actif().annonce(id)
    override suspend fun historiquePrix(id: String) = actif().historiquePrix(id)
    override suspend fun changerStatut(id: String, statut: StatutPerso, note: String?) =
        actif().changerStatut(id, statut, note)
    override suspend fun etatSources() = actif().etatSources()
    override suspend fun communes() = actif().communes()
    override suspend fun recherches() = actif().recherches()
    override suspend fun creerRecherche(r: RechercheSauvegardee) = actif().creerRecherche(r)
    override suspend fun modifierRecherche(id: String, champs: Map<String, Boolean>) =
        actif().modifierRecherche(id, champs)
    override suspend fun supprimerRecherche(id: String) = actif().supprimerRecherche(id)
}

/** Dépôt Supabase : API REST (PostgREST), client Retrofit reconstruit si l'URL ou la clé change. */
class DepotSupabase(private val preferences: Preferences) : Depot {

    private val json = Json {
        ignoreUnknownKeys = true   // une colonne ajoutée côté Supabase ne casse pas l'appli
        coerceInputValues = true   // null → valeur par défaut
        explicitNulls = false
    }

    private var apiCache: SupabaseApi? = null
    private var cleCache: String = ""

    private suspend fun api(): SupabaseApi {
        val r = preferences.lire()
        if (!r.configure) throw IllegalStateException("Supabase n'est pas configuré (voir Paramètres).")
        val cle = r.supabaseUrl + "|" + r.supabaseCle
        apiCache?.takeIf { cleCache == cle }?.let { return it }
        return construire(r.supabaseUrl, r.supabaseCle).also {
            apiCache = it
            cleCache = cle
        }
    }

    private fun construire(url: String, cleAnon: String): SupabaseApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chaine ->
                val requete = chaine.request().newBuilder()
                    .header("apikey", cleAnon)
                    .header("Authorization", "Bearer $cleAnon")
                    .header("Accept", "application/json")
                    .build()
                chaine.proceed(requete)
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        return Retrofit.Builder()
            .baseUrl("$url/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseApi::class.java)
    }

    /**
     * Liste des annonces actives d'une transaction, filtrées et triées.
     * Zone : communes ET/OU rayon. Si les deux sont définis, on fait deux
     * requêtes et on fusionne (l'un ou l'autre suffit).
     */
    override suspend fun listerAnnonces(transaction: String, tri: String, filtres: Filtres): List<Annonce> {
        val ordre = when (tri) {
            "prix" -> "prix.asc.nullslast"
            "prix_desc" -> "prix.desc.nullslast"
            else -> "premiere_detection.desc"
        }
        val base = mutableMapOf(
            "select" to "*",
            "transaction" to "eq.$transaction",
            "actif" to "eq.true",
            "order" to ordre,
            "limit" to "300",
        )
        base.putAll(filtres.versRequete())
        val a = api()
        val resultats = LinkedHashMap<String, Annonce>()
        val zoneCommunes = filtres.communes.isNotEmpty()
        if (zoneCommunes) {
            val q = base + ("commune" to "in.(${filtres.communes.joinToString(",") { "\"$it\"" }})")
            a.annonces(q).forEach { resultats[it.id] = it }
        }
        if (filtres.rayonDefini) {
            val args = mapOf("lat" to filtres.centreLat!!, "lon" to filtres.centreLon!!,
                "km" to filtres.rayonKm!!)
            a.annoncesDansRayon(args, base).forEach { resultats[it.id] = it }
        }
        if (!zoneCommunes && !filtres.rayonDefini) {
            a.annonces(base).forEach { resultats[it.id] = it }
        }
        // Après fusion de deux requêtes, on retrie localement.
        return when (tri) {
            "prix" -> resultats.values.sortedWith(compareBy(nullsLast()) { it.prix })
            "prix_desc" -> resultats.values.sortedWith(compareByDescending(nullsFirst()) { it.prix })
            else -> resultats.values.sortedByDescending { it.premiereDetection ?: "" }
        }
    }

    override suspend fun communes(): List<Commune> = api().communes()

    override suspend fun recherches(): List<RechercheSauvegardee> = api().recherches()

    override suspend fun creerRecherche(r: RechercheSauvegardee): RechercheSauvegardee =
        api().creerRecherche(r).first()

    override suspend fun modifierRecherche(id: String, champs: Map<String, Boolean>) {
        val r = api().modifierRecherche("eq.$id", champs)
        if (!r.isSuccessful) throw IllegalStateException("Modification refusée (HTTP ${r.code()})")
    }

    override suspend fun supprimerRecherche(id: String) {
        val r = api().supprimerRecherche("eq.$id")
        if (!r.isSuccessful) throw IllegalStateException("Suppression refusée (HTTP ${r.code()})")
    }

    override suspend fun annonce(id: String): Annonce? =
        api().annonces(mapOf("select" to "*", "id" to "eq.$id")).firstOrNull()

    override suspend fun historiquePrix(id: String): List<PrixHistorique> = api().historiquePrix(id)

    override suspend fun changerStatut(id: String, statut: StatutPerso, note: String?) {
        val reponse = api().enregistrerStatut(
            Statut(annonceId = id, statut = statut.code, note = note?.ifBlank { null },
                modifieLe = Instant.now().toString())
        )
        if (!reponse.isSuccessful) {
            throw IllegalStateException("Supabase a refusé l'écriture (HTTP ${reponse.code()}). " +
                "Les règles RLS (policies.sql) sont-elles installées ?")
        }
    }

    override suspend fun etatSources(): List<EtatSource> = api().etatSources()

    /** Teste une URL et une clé sans les enregistrer. Renvoie null si OK, sinon le message. */
    suspend fun testerConnexion(url: String, cle: String): String? = try {
        val r = construire(url.trim().trimEnd('/'), cle.trim()).testerConnexion()
        when {
            r.isSuccessful -> null
            r.code() == 401 || r.code() == 403 -> "Clé refusée (HTTP ${r.code()}). Vérifie la clé anon."
            r.code() == 404 -> "Table « communes » introuvable : schema.sql n'a pas été exécuté ?"
            else -> "Réponse inattendue : HTTP ${r.code()}"
        }
    } catch (e: Exception) {
        "Connexion impossible : ${e.message}"
    }
}
