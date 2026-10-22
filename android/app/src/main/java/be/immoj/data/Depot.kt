package be.immoj.data

import kotlinx.serialization.encodeToString
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.io.File
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
    /** Force une vraie mise à jour (nouveau téléchargement / nouvelle requête). */
    suspend fun rafraichir() {}

    /**
     * L'index : TOUTES les annonces actives (les deux transactions), avec les
     * champs utiles à la liste, aux filtres et aux compteurs, mais sans
     * description, détails ni photos complètes. Jamais tronqué. Le filtrage, le
     * tri et les comptages se font ensuite en mémoire ([Catalogue]).
     * `forcer` : retélécharger même si un cache récent existe.
     */
    suspend fun index(forcer: Boolean = false): List<Annonce>

    /** Index déjà en cache sur le téléphone (démarrage instantané), ou liste vide. */
    suspend fun indexEnCache(): List<Annonce> = emptyList()

    suspend fun listerAnnonces(transaction: String, tri: String, filtres: Filtres): List<Annonce> =
        Catalogue.trier(Catalogue.filtrer(index(), transaction, filtres), tri)
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

    override suspend fun rafraichir() = actif().rafraichir()
    override suspend fun index(forcer: Boolean) = actif().index(forcer)
    override suspend fun indexEnCache() = actif().indexEnCache()
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
class DepotSupabase(private val preferences: Preferences, private val cacheDir: File? = null) : Depot {

    private val json = Json {
        ignoreUnknownKeys = true   // une colonne ajoutée côté Supabase ne casse pas l'appli
        coerceInputValues = true   // null → valeur par défaut
        explicitNulls = false
    }

    /** Champs de l'index (tout sauf description, détails, photos complètes). */
    private val champsIndex = listOf(
        "id", "type", "transaction", "titre", "prix", "prix_precedent", "prix_baisse_le", "charges",
        "commune", "localite", "code_postal", "latitude", "longitude", "chambres", "surface", "peb",
        "jardin", "terrasse", "animaux", "meuble", "date_publication", "premiere_detection",
        "derniere_vue", "photo", "agence", "annonceur", "precision_position", "actif", "statut", "note",
        "badge_nouveau", "badge_prix_baisse", "sources",
    ).joinToString(",")
    private val verrouIndex = Mutex()
    private var indexMemoire: List<Annonce> = emptyList()
    private var indexCharge: Instant? = null
    private val fichierIndex: File? get() = cacheDir?.let { File(it, "index-supabase.json") }

    override suspend fun indexEnCache(): List<Annonce> {
        if (indexMemoire.isNotEmpty()) return indexMemoire
        val f = fichierIndex ?: return emptyList()
        return withContext(Dispatchers.IO) {
            if (!f.exists()) emptyList()
            else runCatching { json.decodeFromString<List<Annonce>>(f.readText()) }.getOrDefault(emptyList())
        }.also { if (it.isNotEmpty() && indexMemoire.isEmpty()) indexMemoire = it }
    }

    /**
     * Télécharge l'index par pages de 1 000 lignes (PostgREST) jusqu'à la fin :
     * aucune limite silencieuse. Réutilise la copie en mémoire pendant 10 minutes
     * sauf `forcer`.
     */
    override suspend fun index(forcer: Boolean): List<Annonce> = verrouIndex.withLock {
        val recent = indexCharge?.let { Duration.between(it, Instant.now()) < Duration.ofMinutes(10) } == true
        if (!forcer && recent && indexMemoire.isNotEmpty()) return@withLock indexMemoire
        if (!forcer && indexMemoire.isEmpty()) indexEnCache()
        val a = api()
        val tout = ArrayList<Annonce>()
        var depart = 0
        val page = 1000
        while (true) {
            val lot = a.annonces(mapOf("select" to champsIndex, "actif" to "eq.true",
                "order" to "premiere_detection.desc,id.asc", "limit" to "$page", "offset" to "$depart"))
            tout.addAll(lot)
            if (lot.size < page) break
            depart += page
        }
        indexMemoire = tout
        indexCharge = Instant.now()
        fichierIndex?.let { f ->
            withContext(Dispatchers.IO) { runCatching { f.writeText(json.encodeToString(tout)) } }
        }
        tout
    }

    override suspend fun rafraichir() { index(forcer = true) }

    /** Le statut change : l'index en mémoire et en cache suivent, sans retéléchargement. */
    private suspend fun appliquerStatut(id: String, statut: StatutPerso, note: String?) {
        if (indexMemoire.isEmpty()) return
        indexMemoire = indexMemoire.map { if (it.id == id) it.copy(statut = statut.code, note = note) else it }
        fichierIndex?.let { f ->
            withContext(Dispatchers.IO) { runCatching { f.writeText(json.encodeToString(indexMemoire)) } }
        }
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
        appliquerStatut(id, statut, note)
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
