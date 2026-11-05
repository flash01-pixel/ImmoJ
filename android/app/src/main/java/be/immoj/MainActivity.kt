package be.immoj

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import be.immoj.ui.navigation.AppNavigation
import be.immoj.ui.theme.ImmoJTheme

/** Unique activité : tout l'écran est dessiné en Jetpack Compose. */
class MainActivity : ComponentActivity() {
    private val demandeNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Contenu reçu par « Partager vers ImmoJ » (texte et/ou image), consommé par la navigation. */
    data class Partage(val texte: String?, val image: Uri?, val jeton: Long = System.nanoTime())
    private val partage = mutableStateOf<Partage?>(null)

    private fun lirePartage(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val texte = intent.getStringExtra(Intent.EXTRA_TEXT)
        @Suppress("DEPRECATION")
        val image = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (texte != null || image != null) partage.value = Partage(texte, image)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lirePartage(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lirePartage(intent)
        // Android 13+ : demander l'autorisation d'afficher des notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as ImmoJApp
        setContent {
            val reglages by app.preferences.flux.collectAsState(initial = null)
            ImmoJTheme(mode = reglages?.theme ?: "systeme") {
                val recu by partage
                AppNavigation(app, partage = recu?.let { Triple(it.texte, it.image, it.jeton) })
            }
        }
    }
}
