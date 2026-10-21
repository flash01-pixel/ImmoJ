package be.immoj

import android.app.Application
import be.immoj.data.Depot
import be.immoj.data.Preferences

/**
 * Classe Application : crée les objets partagés par tous les écrans
 * (préférences et dépôt de données). Pas de framework d'injection : inutile
 * pour une appli personnelle.
 */
class ImmoJApp : Application() {
    val preferences: Preferences by lazy { Preferences(this) }
    val depot: Depot by lazy { Depot(preferences) }
}
