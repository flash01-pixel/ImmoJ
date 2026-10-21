package be.immoj.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Interface Retrofit vers l'API REST de Supabase (PostgREST).
 *
 * Les en-têtes `apikey` et `Authorization` sont ajoutés par un intercepteur
 * dans [Depot]. Syntaxe des filtres : `colonne=eq.valeur`, `order=col.desc`...
 */
interface SupabaseApi {

    @GET("rest/v1/v_annonces_liste")
    suspend fun annonces(@QueryMap filtres: Map<String, String>): List<Annonce>

    @GET("rest/v1/historique_prix")
    suspend fun historiquePrix(
        @Query("annonce_id") annonceId: String,
        @Query("order") ordre: String = "date.asc",
        @Query("select") select: String = "prix,source,date",
    ): List<PrixHistorique>

    /** Insère ou met à jour mon statut pour une annonce (upsert sur annonce_id). */
    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/statuts")
    suspend fun enregistrerStatut(
        @Body statut: Statut,
        @Query("on_conflict") conflit: String = "annonce_id",
    ): Response<Unit>

    @GET("rest/v1/v_etat_sources")
    suspend fun etatSources(@Query("order") ordre: String = "source.asc"): List<EtatSource>

    /** Requête minimale pour tester la connexion. */
    @GET("rest/v1/communes")
    suspend fun testerConnexion(
        @Query("select") select: String = "nom",
        @Query("limit") limite: Int = 1,
    ): Response<List<Map<String, String>>>
}
