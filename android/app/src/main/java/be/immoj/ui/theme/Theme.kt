package be.immoj.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Thème Material 3 : vert forêt, clair ou sombre (réglage « Apparence » ou système). */
private val Vert = Color(0xFF1B5E20)
private val VertClair = Color(0xFF81C784)
private val Ambre = Color(0xFFFF8F00)

private val SchemaClair = lightColorScheme(
    primary = Vert,
    secondary = Color(0xFF558B2F),
    tertiary = Ambre,
)
private val SchemaSombre = darkColorScheme(
    primary = VertClair,
    secondary = Color(0xFFAED581),
    tertiary = Color(0xFFFFB300),
)

@Composable
fun ImmoJTheme(mode: String = "systeme", content: @Composable () -> Unit) {
    val sombre = when (mode) {
        "clair" -> false
        "sombre" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (sombre) SchemaSombre else SchemaClair,
        content = content,
    )
}
