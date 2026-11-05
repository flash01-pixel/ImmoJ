package be.immoj.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Annonces ajoutées à la main (« Partager vers ImmoJ » depuis Facebook, un
 * navigateur, Messenger… ou le bouton « + Ajouter une annonce »). Elles vivent
 * sur le téléphone (manuelles.json), dans la même liste que les autres, avec
 * statuts, carte et compteurs. Source : « manuel ».
 */
class Manuelles(private val contexte: Context) {

    @Serializable
    private data class Fichier(
        val annonces: MutableList<Annonce> = mutableListOf(),
        val statuts: MutableMap<String, DepotLocal.StatutLocal> = mutableMapOf(),
    )

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val fichier = File(contexte.filesDir, "manuelles.json")
    private val verrou = Mutex()
    private var donnees: Fichier? = null

    private suspend fun charger(): Fichier = verrou.withLock {
        donnees ?: withContext(Dispatchers.IO) {
            (if (fichier.exists()) runCatching { json.decodeFromString<Fichier>(fichier.readText()) }.getOrNull()
            else null) ?: Fichier()
        }.also { donnees = it }
    }

    private suspend fun sauvegarder(d: Fichier) = withContext(Dispatchers.IO) {
        fichier.writeText(json.encodeToString(d))
    }

    private fun enrichir(a: Annonce, d: Fichier): Annonce {
        val s = d.statuts[a.id]
        return a.copy(statut = s?.statut ?: "nouveau", note = s?.note,
            badgeNouveau = (s?.statut ?: "nouveau") == "nouveau")
    }

    suspend fun lister(): List<Annonce> {
        val d = charger()
        return d.annonces.filter { it.actif }.map { enrichir(it, d) }
    }

    suspend fun annonce(id: String): Annonce? {
        val d = charger()
        return d.annonces.firstOrNull { it.id == id }?.let { enrichir(it, d) }
    }

    /** Ajoute une annonce ; la photo partagée (URI) est copiée dans les fichiers de l'appli. */
    suspend fun ajouter(a: Annonce, photoUri: Uri?): Annonce {
        val d = charger()
        var annonce = a
        if (photoUri != null) {
            val chemin = runCatching { copierPhoto(photoUri, a.id) }.getOrNull()
            if (chemin != null) annonce = a.copy(photos = listOf(chemin) + a.photos, photo = chemin)
        }
        d.annonces.add(0, annonce)
        sauvegarder(d)
        return annonce
    }

    suspend fun supprimer(id: String) {
        val d = charger()
        d.annonces.removeAll { it.id == id }
        d.statuts.remove(id)
        sauvegarder(d)
    }

    suspend fun changerStatut(id: String, statut: StatutPerso, note: String?) {
        val d = charger()
        d.statuts[id] = DepotLocal.StatutLocal(statut.code, note?.ifBlank { null }, Instant.now().toString())
        sauvegarder(d)
    }

    private suspend fun copierPhoto(uri: Uri, id: String): String = withContext(Dispatchers.IO) {
        val dossier = File(contexte.filesDir, "photos").apply { mkdirs() }
        val cible = File(dossier, "$id.jpg")
        contexte.contentResolver.openInputStream(uri)!!.use { entree ->
            cible.outputStream().use { sortie -> entree.copyTo(sortie) }
        }
        "file://${cible.absolutePath}"
    }

    companion object {
        const val SOURCE = "manuel"
        fun estManuelle(id: String) = id.startsWith("manuel-")

        /** Premier lien http(s) d'un texte partagé. */
        fun extraireLien(texte: String?): String? =
            texte?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', '>') }

        /** Montant en € trouvé dans un texte partagé (« 750 €/mois », « 850€ », « 250.000 € »). */
        fun extrairePrix(texte: String?): Int? {
            if (texte == null) return null
            val m = Regex("(\\d[\\d .\\u202f\\u00a0]{0,8})\\s*€").find(texte) ?: return null
            return m.groupValues[1].filter { it.isDigit() }.toIntOrNull()
        }

        /** Titre proposé : première ligne du texte sans le lien, sinon « Annonce partagée ». */
        fun titreDepuis(texte: String?): String {
            val sans = texte?.replace(Regex("https?://\\S+"), "")?.trim().orEmpty()
            val ligne = sans.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            return if (ligne.length >= 4) ligne.take(120) else "Annonce partagée"
        }

        /** Construit l'annonce manuelle (identifiant « manuel-… », source « manuel »). */
        fun creer(
            titre: String, lien: String?, transaction: String, type: String, prix: Int?,
            commune: Commune?, localite: String?, chambres: Int?, surface: Int?, note: String?,
            annonceur: String = "particulier", maintenant: Instant = Instant.now(),
        ): Annonce {
            val id = "manuel-" + UUID.randomUUID().toString()
            val quand = maintenant.toString()
            return Annonce(
                id = id, type = type, transaction = transaction, titre = titre.ifBlank { "Annonce partagée" },
                prix = prix, commune = commune?.nom, localite = localite?.ifBlank { null } ?: commune?.nom,
                codePostal = commune?.codes?.firstOrNull(), latitude = commune?.latitude, longitude = commune?.longitude,
                chambres = chambres, surface = surface, description = note?.ifBlank { null },
                datePublication = quand.take(10), premiereDetection = quand, derniereVue = quand,
                annonceur = annonceur, precisionPosition = if (commune != null) "commune" else null,
                actif = true, statut = "nouveau", badgeNouveau = true,
                sources = listOf(SOURCE), urls = listOfNotNull(lien),
            )
        }
    }
}
