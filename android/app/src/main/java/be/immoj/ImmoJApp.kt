package be.immoj

import android.app.Application
import be.immoj.data.Depot
import be.immoj.data.DepotLocal
import be.immoj.data.DepotRouteur
import be.immoj.data.DepotSupabase
import be.immoj.data.Preferences
import be.immoj.sync.Notifications
import be.immoj.sync.SyncWorker

/**
 * Classe Application : crée les objets partagés par tous les écrans
 * (préférences et dépôt de données). Pas de framework d'injection : inutile
 * pour une appli personnelle.
 */
class ImmoJApp : Application() {
    val preferences: Preferences by lazy { Preferences(this) }
    val depotSupabase: DepotSupabase by lazy { DepotSupabase(preferences) }
    val depotLocal: DepotLocal by lazy { DepotLocal(this) }
    val depot: Depot by lazy { DepotRouteur(preferences, depotSupabase, depotLocal) }

    override fun onCreate() {
        super.onCreate()
        Notifications.creerCanal(this)
        // Vérification en arrière-plan toutes les heures (nouveautés et baisses de prix).
        SyncWorker.planifier(this)
    }
}
