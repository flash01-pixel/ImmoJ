package be.immoj

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13+ : demander l'autorisation d'afficher des notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as ImmoJApp
        setContent {
            ImmoJTheme {
                AppNavigation(app)
            }
        }
    }
}
