package be.immoj.ui.parametres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import be.immoj.ImmoJApp
import kotlinx.coroutines.launch

/**
 * Écran Paramètres : URL du projet Supabase et clé anon.
 * Affiché automatiquement au premier lancement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametresEcran(app: ImmoJApp, retour: () -> Unit) {
    val portee = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var cle by remember { mutableStateOf("") }
    var chargeInitiale by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<String?>(null) }
    var enTest by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = app.preferences.lire()
        url = r.supabaseUrl
        cle = r.supabaseCle
        chargeInitiale = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = retour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { marges ->
        Column(
            modifier = Modifier.padding(marges).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connexion à Supabase", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dans Supabase : Project Settings → API. Copie « Project URL » et la clé « anon public ».",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL du projet") },
                placeholder = { Text("https://xxxxxxxx.supabase.co") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cle, onValueChange = { cle = it },
                label = { Text("Clé anon (publishable)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !enTest && url.isNotBlank() && cle.isNotBlank(),
                    onClick = {
                        portee.launch {
                            enTest = true
                            test = app.depot.testerConnexion(url, cle) ?: "Connexion réussie ✔"
                            enTest = false
                        }
                    },
                ) { Text("Tester") }
                Button(
                    enabled = chargeInitiale && url.isNotBlank() && cle.isNotBlank(),
                    onClick = {
                        portee.launch {
                            app.preferences.enregistrerConnexion(url, cle)
                            retour()
                        }
                    },
                ) { Text("Enregistrer") }
                if (enTest) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp).width(24.dp))
                }
            }
            test?.let {
                Text(it, color = if (it.startsWith("Connexion réussie")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.padding(8.dp))
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Text(
                "Les notifications sont envoyées par le collecteur via ntfy. Installe l'appli ntfy et abonne-toi " +
                    "au même topic que celui configuré dans les secrets GitHub (NTFY_TOPIC).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
