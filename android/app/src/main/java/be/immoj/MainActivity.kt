package be.immoj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import be.immoj.ui.navigation.AppNavigation
import be.immoj.ui.theme.ImmoJTheme

/** Unique activité : tout l'écran est dessiné en Jetpack Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ImmoJApp
        setContent {
            ImmoJTheme {
                AppNavigation(app)
            }
        }
    }
}
