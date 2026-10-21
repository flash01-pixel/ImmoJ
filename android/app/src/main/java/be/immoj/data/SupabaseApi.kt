package be.immoj.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
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

    /** Annonces dans un rayon (fonction SQL) ; les mêmes filtres s'appliquent au résultat. */
    @POST("rest/v1/rpc/annonces_dans_rayon")
    suspend fun annoncesDansRayon(
        @Body arguments: Map<String, Double>,
        @QueryMap filtres: Map<String, String>,
    ): List<Annonce>

    @GET("rest/v1/communes")
    suspend fun communes(
        @Query("select") select: String = "nom,codes,latitude,longitude",
        @Query("order") ordre: String = "nom.asc",
    ): List<Commune>

    @GET("rest/v1/recherches_sauvegardees")
    suspend fun recherches(
        @Query("select") select: String = "*",
        @Query("order") ordre: String = "cree_le.desc",
    ): List<RechercheSauvegardee>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/recherches_sauvegardees")
    suspend fun creerRecherche(@Body recherche: RechercheSauvegardee): List<RechercheSauvegardee>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/recherches_sauvegardees")
    suspend fun modifierRecherche(
        @Query("id") id: String,
        @Body champs: Map<String, Boolean>,
    ): Response<Unit>

    @DELETE("rest/v1/recherches_sauvegardees")
    suspend fun supprimerRecherche(@Query("id") id: String): Response<Unit>

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
