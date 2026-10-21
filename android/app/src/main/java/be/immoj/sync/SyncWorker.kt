package be.immoj.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import be.immoj.ImmoJApp
import be.immoj.data.Annonce
import be.immoj.data.RechercheSauvegardee
import be.immoj.data.Reglages
import be.immoj.data.correspond
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Travail périodique (fréquence réglable, quand le réseau est là) : télécharge
 * le fichier d'annonces et notifie selon le réglage « Alertes » :
 *  - « recherches » : nouveautés et baisses de prix correspondant aux recherches
 *    sauvegardées (sans recherche : toutes les nouvelles locations) ;
 *  - « toutes » : toutes les nouvelles annonces de la transaction choisie ;
 *  - « aucune » : rien.
 * En mode Supabase, les notifications passent par ntfy : rien à faire ici.
 */
class SyncWorker(contexte: Context, params: WorkerParameters) : CoroutineWorker(contexte, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ImmoJApp
        val reglages = app.preferences.lire()
        if (reglages.configure) return Result.success()
        val nouveautes = try {
            app.depotLocal.synchroniser(forcer = true)
        } catch (e: Exception) {
            return Result.retry()
        }
        if (reglages.alertes == "aucune" || !plageAutorisee(reglages)) return Result.success()
        val recherches = app.depotLocal.recherchesANotifier()
        var nb = 0
        for (a in nouveautes.nouvelles) {
            val nom = correspondance(a, recherches, reglages) ?: continue
            nb++
            if (nb <= 5) Notifications.nouvelleAnnonce(applicationContext, a, nom.ifBlank { null })
        }
        if (nb > 5) Notifications.resume(applicationContext, nb)
        if (reglages.alerterBaisses) {
            for ((a, ancien) in nouveautes.baisses) {
                if (correspondance(a, recherches, reglages) != null) {
                    Notifications.baissePrix(applicationContext, a, ancien)
                }
            }
        }
        return Result.success()
    }

    /** Heures calmes : pas de notification entre 22 h et 7 h (les nouveautés restent dans l'appli). */
    private fun plageAutorisee(r: Reglages): Boolean {
        if (!r.heuresCalmes) return true
        val h = LocalTime.now().hour
        return h in 7..21
    }

    /**
     * Nom de la première recherche qui correspond ; "" quand l'annonce est à
     * notifier sans recherche particulière ; null pour ne pas notifier.
     */
    private fun correspondance(a: Annonce, recherches: List<RechercheSauvegardee>, r: Reglages): String? {
        if (r.alertes == "toutes") return if (a.transaction == r.transaction) "" else null
        if (recherches.isEmpty()) return if (a.transaction == "location") "" else null
        return recherches.firstOrNull { rs ->
            (rs.transaction == null || rs.transaction == a.transaction) && rs.versFiltres().correspond(a)
        }?.nom
    }

    companion object {
        /** Planifie (ou replanifie) la vérification périodique selon la fréquence choisie. */
        fun planifier(contexte: Context, heures: Int = 1, remplacer: Boolean = false) {
            val demande = PeriodicWorkRequestBuilder<SyncWorker>(heures.coerceIn(1, 24).toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(contexte).enqueueUniquePeriodicWork(
                "synchro",
                if (remplacer) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                demande,
            )
        }
    }
}
