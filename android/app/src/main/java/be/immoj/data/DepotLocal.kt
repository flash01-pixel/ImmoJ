package be.immoj.data

import be.immoj.util.Dates
import android.content.Context
import be.immoj.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Mode « sans compte » : les annonces viennent d'un fichier JSON publié par le
 * collecteur sur GitHub (branche `donnees`). Tout le reste vit sur le
 * téléphone : statuts, notes, recherches sauvegardées, annonces déjà vues.
 */
class DepotLocal(private val contexte: Context) : Depot {

    @Serializable
    data class StatutLocal(val statut: String, val note: String? = null, val modifieLe: String)

    /** Données personnelles, enregistrées dans perso.json. */
    @Serializable
    data class Perso(
        val statuts: MutableMap<String, StatutLocal> = mutableMapOf(),
        val recherches: MutableList<RechercheSauvegardee> = mutableListOf(),
        val connus: MutableSet<String> = mutableSetOf(),           // ids déjà vus (notifications)
        val prixConnus: MutableMap<String, Int> = mutableMapOf(),  // dernier prix notifié par id
        var derniereSynchro: String? = null,
    )

    /** Résultat d'une synchronisation : ce qui mérite une notification. */
    data class Nouveautes(val nouvelles: List<Annonce>, val baisses: List<Pair<Annonce, Int>>)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val verrou = Mutex()
    private val fichierExport = File(contexte.filesDir, "annonces.json")
    private val fichierPerso = File(contexte.filesDir, "perso.json")
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private var annonces: List<Annonce> = emptyList()
    private var historiques: Map<String, List<PrixHistorique>> = emptyMap()
    private var communesCache: List<Commune> = emptyList()
    private var sources: List<EtatSource> = emptyList()
    private var genereLe: String? = null
    private var charge = false
    private var perso: Perso = Perso()

    val url: String get() = BuildConfig.DONNEES_URL
    fun derniereMiseAJour(): String? = genereLe

    // --- chargement et synchronisation -----------------------------------

    private suspend fun charger() {
        if (charge) return
        withContext(Dispatchers.IO) {
            if (fichierPerso.exists()) {
                perso = runCatching { json.decodeFromString<Perso>(fichierPerso.readText()) }
                    .getOrDefault(Perso())
            }
            if (fichierExport.exists()) {
                runCatching { lireExport(fichierExport.readText()) }
            }
        }
        charge = true
    }

    private fun lireExport(texte: String) {
        val racine = json.parseToJsonElement(texte).jsonObject
        genereLe = racine["genere_le"]?.jsonPrimitive?.content
        communesCache = racine["communes"]?.let { json.decodeFromJsonElement<List<Commune>>(it) }
            ?: emptyList()
        sources = racine["sources"]?.let { json.decodeFromJsonElement<List<EtatSource>>(it) }
            ?: emptyList()
        val liste = racine["annonces"]?.jsonArray ?: return
        val hist = HashMap<String, List<PrixHistorique>>()
        annonces = liste.map { el ->
            val obj = el as JsonObject
            val a: Annonce = json.decodeFromJsonElement(obj)
            obj["historique_prix"]?.let { hist[a.id] = json.decodeFromJsonElement<List<PrixHistorique>>(it) }
            a
        }
        historiques = hist
    }

    private fun sauvegarderPerso() {
        fichierPerso.writeText(json.encodeToString(perso))
    }

    /**
     * Télécharge le fichier publié par GitHub si le cache a plus de [maxAge]
     * (ou toujours si `forcer`). Renvoie les nouveautés depuis la dernière
     * synchronisation ; lève une exception seulement s'il n'y a aucun cache.
     */
    suspend fun synchroniser(forcer: Boolean = false, maxAge: Duration = Duration.ofMinutes(15)): Nouveautes {
        val rien = Nouveautes(emptyList(), emptyList())
        return verrou.withLock {
            charger()
            val derniere = Dates.instant(perso.derniereSynchro)
            val recent = derniere != null && Duration.between(derniere, Instant.now()) < maxAge
            if (recent && !forcer && annonces.isNotEmpty()) return@withLock rien

            val texte: String = try {
                withContext(Dispatchers.IO) {
                    val reponse = client.newCall(Request.Builder().url(url).build()).execute()
                    if (!reponse.isSuccessful) throw IllegalStateException(
                        "Téléchargement des annonces refusé (HTTP ${reponse.code}). " +
                            "Le dépôt GitHub est-il public et la collecte a-t-elle déjà tourné ?")
                    reponse.body?.string() ?: ""
                }
            } catch (e: Exception) {
                if (annonces.isNotEmpty()) return@withLock rien   // réseau absent : on garde le cache
                throw e
            }
            val anciensIds = annonces.map { it.id }.toSet()
            val premiereFois = perso.connus.isEmpty() && anciensIds.isEmpty()
            lireExport(texte)
            withContext(Dispatchers.IO) { fichierExport.writeText(texte) }

            val nouvelles = mutableListOf<Annonce>()
            val baisses = mutableListOf<Pair<Annonce, Int>>()
            for (a in annonces) {
                if (a.id !in perso.connus) {
                    perso.connus.add(a.id)
                    if (!premiereFois) nouvelles.add(a)
                } else {
                    val ancien = perso.prixConnus[a.id]
                    if (ancien != null && a.prix != null && a.prix < ancien) baisses.add(a to ancien)
                }
                a.prix?.let { perso.prixConnus[a.id] = it }
            }
            perso.derniereSynchro = Instant.now().toString()
            withContext(Dispatchers.IO) { sauvegarderPerso() }
            Nouveautes(nouvelles, baisses)
        }
    }

    // --- interface Depot -------------------------------------------------

    private fun enrichir(a: Annonce): Annonce {
        val s = perso.statuts[a.id]
        val statut = s?.statut ?: "nouveau"
        return a.copy(
            statut = statut,
            note = s?.note,
            badgeNouveau = statut == "nouveau" && Dates.recent(a.premiereDetection, 48),
            badgePrixBaisse = Dates.recent(a.prixBaisseLe, 24 * 7),
        )
    }

    /** Retélécharge le fichier publié, quel que soit l'âge du cache. */
    override suspend fun rafraichir() { synchroniser(forcer = true) }

    override suspend fun indexEnCache(): List<Annonce> {
        charger()
        return annonces.filter { it.actif }.map { enrichir(it) }
    }

    override suspend fun index(forcer: Boolean): List<Annonce> {
        synchroniser(forcer = forcer)
        return annonces.filter { it.actif }.map { enrichir(it) }
    }

    override suspend fun annonce(id: String): Annonce? {
        charger()
        return annonces.firstOrNull { it.id == id }?.let { enrichir(it) }
    }

    override suspend fun historiquePrix(id: String): List<PrixHistorique> {
        charger()
        return historiques[id] ?: emptyList()
    }

    override suspend fun changerStatut(id: String, statut: StatutPerso, note: String?) {
        charger()
        perso.statuts[id] = StatutLocal(statut.code, note?.ifBlank { null }, Instant.now().toString())
        withContext(Dispatchers.IO) { sauvegarderPerso() }
    }

    override suspend fun etatSources(): List<EtatSource> { synchroniser(); return sources }

    override suspend fun communes(): List<Commune> { charger(); return communesCache }

    override suspend fun recherches(): List<RechercheSauvegardee> { charger(); return perso.recherches.toList() }

    override suspend fun creerRecherche(r: RechercheSauvegardee): RechercheSauvegardee {
        charger()
        val avecId = r.copy(id = UUID.randomUUID().toString())
        perso.recherches.add(0, avecId)
        withContext(Dispatchers.IO) { sauvegarderPerso() }
        return avecId
    }

    override suspend fun modifierRecherche(id: String, champs: Map<String, Boolean>) {
        charger()
        val i = perso.recherches.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = perso.recherches[i]
            perso.recherches[i] = r.copy(
                notifier = champs["notifier"] ?: r.notifier,
                actif = champs["actif"] ?: r.actif,
            )
            withContext(Dispatchers.IO) { sauvegarderPerso() }
        }
    }

    override suspend fun supprimerRecherche(id: String) {
        charger()
        perso.recherches.removeAll { it.id == id }
        withContext(Dispatchers.IO) { sauvegarderPerso() }
    }

    /** Recherches sauvegardées actives avec notifications (pour le travail en arrière-plan). */
    suspend fun recherchesANotifier(): List<RechercheSauvegardee> {
        charger()
        return perso.recherches.filter { it.actif && it.notifier }
    }
}
