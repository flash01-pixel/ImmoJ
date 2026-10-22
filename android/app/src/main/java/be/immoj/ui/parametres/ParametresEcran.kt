package be.immoj.ui.parametres

import be.immoj.util.Dates
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import be.immoj.BuildConfig
import be.immoj.ImmoJApp
import be.immoj.data.Reglages
import be.immoj.sync.SyncWorker
import be.immoj.util.Format
import kotlinx.coroutines.launch

/**
 * Écran Paramètres : apparence, alertes, données (actualisation, collecte à
 * distance), connexion Supabase facultative, à propos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametresEcran(app: ImmoJApp, retour: () -> Unit) {
    val portee = rememberCoroutineScope()
    val contexte = LocalContext.current
    val reglages by app.preferences.flux.collectAsState(initial = Reglages())
    var url by remember { mutableStateOf("") }
    var cle by remember { mutableStateOf("") }
    var jeton by remember { mutableStateOf("") }
    var chargeInitiale by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<String?>(null) }
    var enTest by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val r = app.preferences.lire()
        url = r.supabaseUrl
        cle = r.supabaseCle
        jeton = r.githubToken
        chargeInitiale = true
    }
    fun regler(nom: String, valeur: String) = portee.launch { app.preferences.enregistrerReglage(nom, valeur) }
    fun regler(nom: String, valeur: Boolean) = portee.launch { app.preferences.enregistrerBooleen(nom, valeur) }

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
            // ----- Apparence -------------------------------------------------
            Titre("Apparence")
            Text("Thème", style = MaterialTheme.typography.labelLarge)
            Choix(
                listOf("systeme" to "Système", "clair" to "Clair", "sombre" to "Sombre"),
                reglages.theme,
            ) { regler("theme", it) }
            Interrupteur("Photos dans la liste", reglages.photosEnListe) { regler("photosEnListe", it) }

            HorizontalDivider()
            // ----- Alertes ----------------------------------------------------
            Titre("Alertes")
            if (reglages.configure) {
                Text(
                    "Mode Supabase : les alertes arrivent par l'application ntfy (gratuite). " +
                        "Installe-la, puis abonne-toi au sujet « ${reglages.ntfyTopic} ».",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ntfy.sh/${reglages.ntfyTopic}")))
                    }) { Text("S'abonner dans ntfy") }
                    OutlinedButton(onClick = {
                        contexte.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=io.heckel.ntfy")))
                    }) { Text("Installer ntfy") }
                }
            } else {
                Text("Type d'alerte", style = MaterialTheme.typography.labelLarge)
                Choix(
                    listOf("recherches" to "Mes recherches", "toutes" to "Toutes les nouveautés", "aucune" to "Aucune"),
                    reglages.alertes,
                ) { regler("alertes", it) }
                Text(
                    when (reglages.alertes) {
                        "toutes" -> "Une notification pour chaque nouvelle annonce (location ou achat selon l'onglet choisi)."
                        "aucune" -> "Aucune notification ; les nouveautés restent visibles dans l'appli."
                        else -> "Notifications pour les annonces qui correspondent à tes recherches sauvegardées " +
                            "(sans recherche : toutes les nouvelles locations)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Interrupteur("Prévenir des baisses de prix", reglages.alerterBaisses) { regler("alerterBaisses", it) }
                Interrupteur("Silence la nuit (22 h – 7 h)", reglages.heuresCalmes) { regler("heuresCalmes", it) }
                Text("Vérification en arrière-plan", style = MaterialTheme.typography.labelLarge)
                Choix(
                    listOf("1" to "Toutes les heures", "2" to "2 h", "4" to "4 h", "8" to "8 h"),
                    reglages.frequenceHeures.toString(),
                ) {
                    regler("frequenceHeures", it)
                    SyncWorker.planifier(contexte, it.toInt(), remplacer = true)
                }
            }

            HorizontalDivider()
            // ----- Données ----------------------------------------------------
            Titre("Données")
            Text(
                if (!reglages.configure) "Mode sans compte : les annonces viennent du fichier publié par GitHub " +
                    "après chaque collecte (toutes les heures, 7 h – 23 h). Dernière mise à jour : " +
                    (app.depotLocal.derniereMiseAJour()?.let { Format.depuis(it) } ?: "pas encore") + "."
                else if (reglages.integree) "Supabase, configuration intégrée à l'application."
                else "Supabase : ${reglages.supabaseUrl}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "« Actualiser » (bouton ou liste tirée vers le bas) retélécharge vraiment les données. " +
                    "Pour lancer une collecte immédiate sur GitHub depuis l'appli, ajoute un jeton " +
                    "GitHub (Settings → Developer settings → Fine-grained token, dépôt ImmoJ, " +
                    "Actions : Read and write).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = jeton, onValueChange = { jeton = it },
                label = { Text("Jeton GitHub (optionnel)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = chargeInitiale, onClick = {
                    regler("githubToken", jeton.trim())
                    message = "Jeton enregistré (uniquement sur ce téléphone)."
                }) { Text("Enregistrer le jeton") }
                if (jeton.isNotBlank()) {
                    TextButton(onClick = { jeton = ""; regler("githubToken", ""); message = "Jeton retiré." }) {
                        Text("Retirer")
                    }
                }
            }

            HorizontalDivider()
            // ----- Supabase (facultatif) ---------------------------------------
            Titre("Compte Supabase (facultatif)")
            Text(
                "Laisse vide pour rester en mode sans compte. Sinon : Project Settings → API, " +
                    "« Project URL » et clé « anon public ».",
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
                            test = app.depotSupabase.testerConnexion(url, cle) ?: "Connexion réussie ✔"
                            enTest = false
                        }
                    },
                ) { Text("Tester") }
                Button(
                    // Vide + Enregistrer = retour au mode sans compte.
                    enabled = chargeInitiale && (url.isBlank() == cle.isBlank()),
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

            HorizontalDivider()
            Titre("À propos")
            Text(
                "ImmoJ ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE} · commit " +
                    "${BuildConfig.COMMIT.ifBlank { "local" }} · branche ${BuildConfig.BRANCHE.ifBlank { "locale" }} · " +
                    "compilé le ${Dates.dateHeure(BuildConfig.DATE_BUILD).takeIf { it != "—" } ?: "?"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Annonces immobilières de la province de Luxembourg, collectées toutes les heures " +
                    "(portails + sites d'agences), dédoublonnées, filtrées et notifiées.",
                style = MaterialTheme.typography.bodySmall,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(texte, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Interrupteur(libelle: String, valeur: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(libelle, modifier = Modifier.weight(1f))
        Switch(checked = valeur, onCheckedChange = onChange)
    }
}

@Composable
private fun Choix(options: List<Pair<String, String>>, actuel: String, onChoix: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (code, libelle) ->
            FilterChip(selected = actuel == code, onClick = { onChoix(code) }, label = { Text(libelle) })
        }
    }
}
