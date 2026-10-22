package be.immoj

import android.app.Application
import be.immoj.data.Depot
import be.immoj.data.DepotLocal
import be.immoj.data.DepotRouteur
import be.immoj.data.DepotSupabase
import be.immoj.data.Manuelles
import be.immoj.data.Preferences
import be.immoj.sync.Notifications
import be.immoj.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Classe Application : crée les objets partagés par tous les écrans
 * (préférences et dépôt de données). Pas de framework d'injection : inutile
 * pour une appli personnelle.
 */
class ImmoJApp : Application() {
    val preferences: Preferences by lazy { Preferences(this) }
    val depotSupabase: DepotSupabase by lazy { DepotSupabase(preferences, filesDir) }
    val depotLocal: DepotLocal by lazy { DepotLocal(this) }
    val manuelles: Manuelles by lazy { Manuelles(this) }
    val depot: Depot by lazy { DepotRouteur(preferences, depotSupabase, depotLocal, manuelles) }

    override fun onCreate() {
        super.onCreate()
        Notifications.creerCanal(this)
        // Vérification en arrière-plan (fréquence réglable dans les paramètres).
        SyncWorker.planifier(this)
        CoroutineScope(Dispatchers.IO).launch {
            val h = preferences.lire().frequenceHeures
            if (h != 1) SyncWorker.planifier(this@ImmoJApp, h, remplacer = true)
        }
    }
}
