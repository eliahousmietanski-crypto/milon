package com.milon.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.milon.app.BuildConfig
import com.milon.app.LocalApp
import com.milon.app.back
import com.milon.app.data.UpdateManager
import com.milon.app.ui.components.SectionTitle
import com.milon.app.ui.theme.hebrewStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val s = app.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val author by s.author.collectAsStateWithLifecycle()
    val editor by s.editorMode.collectAsStateWithLifecycle()
    val token by s.githubToken.collectAsStateWithLifecycle()
    val scale by s.hebrewScale.collectAsStateWithLifecycle()
    val kbDefault by s.hebrewKeyboardDefault.collectAsStateWithLifecycle()
    val update by app.updates.state.collectAsStateWithLifecycle()
    val changes by app.repo.changes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // --- Dictionnaire
            SectionTitle("Dictionnaire")
            val info = remember(changes, update) { app.repo.dict.info }
            Text("${info.entries} mots · données n° ${info.dataVersion}", style = MaterialTheme.typography.bodyLarge)
            info.built?.let {
                Text(
                    "Construites le ${it.take(10).split('-').reversed().joinToString("/")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Application version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = update !is UpdateManager.State.Checking && update !is UpdateManager.State.Downloading,
                onClick = { scope.launch { app.updates.check() } },
            ) {
                Text(if (update is UpdateManager.State.Checking) "Recherche…" else "Rechercher une mise à jour")
            }
            Column(Modifier.padding(top = 4.dp)) { UpdateBanner(showUpToDate = true) }
            Text(
                "Sans connexion, le dictionnaire reste entièrement utilisable. Les mises à jour sont " +
                    "vérifiées automatiquement quand le téléphone est connecté.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Affichage
            SectionTitle("Affichage")
            Text("Taille du texte hébreu", style = MaterialTheme.typography.bodyLarge)
            var sliderValue by remember { mutableFloatStateOf(scale) }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { s.setHebrewScale(sliderValue) },
                valueRange = 0.8f..1.6f,
                steps = 7,
            )
            Text(
                "בְּרֵאשִׁית בָּרָא אֱלֹהִים",
                style = hebrewStyle(22f * sliderValue / scale.coerceAtLeast(0.1f)),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                "Clavier hébreu intégré",
                "S'ouvre dans la recherche ; touchez « Aa » pour revenir au clavier du téléphone.",
                kbDefault,
            ) { s.setHebrewKeyboardDefault(it) }

            // --- Contributions
            SectionTitle("Contributions")
            var name by remember { mutableStateOf(author) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; s.setAuthor(it) },
                label = { Text("Votre nom (joint à vos propositions)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Mode éditeur
            SectionTitle("Mode éditeur")
            SwitchRow(
                "Je suis l'éditeur du dictionnaire",
                "Permet de valider les propositions reçues et de publier les mises à jour pour tous.",
                editor,
            ) { s.setEditorMode(it) }
            if (editor) {
                var tokenText by remember { mutableStateOf(token) }
                var show by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it; s.setGithubToken(it) },
                    label = { Text("Jeton GitHub (publication)") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Afficher")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Créez un jeton « fine-grained » sur GitHub limité au dépôt ${BuildConfig.REPO}, avec l'autorisation " +
                        "« Contents : Read and write ». Il reste uniquement sur ce téléphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))
                    )
                }) { Text("Créer un jeton sur GitHub") }
            }

            // --- À propos
            SectionTitle("Sources")
            Text(
                "• Hébreu biblique : Brown–Driver–Briggs (BDB), édition numérique Open Scriptures (CC BY 4.0).\n" +
                    "• Araméen et hébreu rabbinique : Marcus Jastrow, Dictionary of the Targumim, the Talmud Babli " +
                    "and Yerushalmi, and the Midrashic Literature (1903, domaine public).\n" +
                    "• Vocabulaire du Zohar et ajouts : rédaction Milon.\n\n" +
                    "Les traductions françaises sont préparées à partir de ces sources puis relues. " +
                    "Les mots marqués ⓘ n'ont pas encore été relus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
