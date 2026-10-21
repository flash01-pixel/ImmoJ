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
import java.util.concurrent.TimeUnit

/**
 * Travail périodique (toutes les heures, quand le réseau est là) : télécharge
 * le fichier d'annonces et notifie les nouveautés et baisses de prix qui
 * correspondent aux recherches sauvegardées. Sans recherche sauvegardée, on
 * notifie toutes les nouvelles locations.
 * En mode Supabase, les notifications passent par ntfy : rien à faire ici.
 */
class SyncWorker(contexte: Context, params: WorkerParameters) : CoroutineWorker(contexte, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ImmoJApp
        if (app.preferences.lire().configure) return Result.success()
        val nouveautes = try {
            app.depotLocal.synchroniser(forcer = true)
        } catch (e: Exception) {
            return Result.retry()
        }
        val recherches = app.depotLocal.recherchesANotifier()
        var nb = 0
        for (a in nouveautes.nouvelles) {
            val nom = correspondance(a, recherches) ?: continue
            nb++
            if (nb <= 5) Notifications.nouvelleAnnonce(applicationContext, a, nom.ifBlank { null })
        }
        if (nb > 5) Notifications.resume(applicationContext, nb)
        for ((a, ancien) in nouveautes.baisses) {
            if (correspondance(a, recherches) != null) Notifications.baissePrix(applicationContext, a, ancien)
        }
        return Result.success()
    }

    /** Nom de la première recherche qui correspond ; "" si aucune recherche (toutes les locations). */
    private fun correspondance(a: Annonce, recherches: List<RechercheSauvegardee>): String? {
        if (recherches.isEmpty()) return if (a.transaction == "location") "" else null
        return recherches.firstOrNull { r ->
            (r.transaction == null || r.transaction == a.transaction) && r.versFiltres().correspond(a)
        }?.nom
    }

    companion object {
        fun planifier(contexte: Context) {
            val demande = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(contexte)
                .enqueueUniquePeriodicWork("synchro", ExistingPeriodicWorkPolicy.KEEP, demande)
        }
    }
}
