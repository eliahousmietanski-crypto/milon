package com.milon.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.milon.app.LocalApp
import com.milon.app.Routes
import com.milon.app.back
import com.milon.app.data.Contributions
import com.milon.app.data.LocalEntry
import com.milon.app.data.LocalStatus
import com.milon.app.data.summary
import com.milon.app.ui.components.EntryRow
import com.milon.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionsScreen(nav: NavHostController, pendingImport: Uri?, onImportHandled: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    val editor by app.settings.editorMode.collectAsStateWithLifecycle()
    val author by app.settings.author.collectAsStateWithLifecycle()
    val token by app.settings.githubToken.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var locals by remember { mutableStateOf<List<LocalEntry>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var askToken by remember { mutableStateOf(false) }

    LaunchedEffect(changes) { locals = app.repo.localEntries() }

    suspend fun importFile(uri: Uri) {
        try {
            val (from, entries) = Contributions.read(context, uri)
            val n = app.repo.importProposals(entries)
            snackbar.showSnackbar("$n proposition(s) reçue(s)${from?.let { " de $it" } ?: ""}, à valider ci-dessous.")
        } catch (e: Contributions.BadFile) {
            snackbar.showSnackbar(e.message ?: "Fichier illisible.")
        } catch (e: java.io.IOException) {
            snackbar.showSnackbar("Impossible de lire le fichier.")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { importFile(uri) }
    }
    LaunchedEffect(pendingImport) {
        pendingImport?.let {
            onImportHandled()
            importFile(it)
        }
    }

    val mine = locals.filter { it.status == LocalStatus.MINE }
    val review = locals.filter { it.status == LocalStatus.REVIEW }
    val approved = locals.filter { it.status == LocalStatus.APPROVED }
    val published = locals.filter { it.status == LocalStatus.PUBLISHED }

    fun share(list: List<LocalEntry>) = scope.launch {
        val intent = Contributions.exportIntent(context, list.map { it.entry }, author)
        context.startActivity(intent)
        app.repo.markExported(list.map { it.entry.id })
    }

    fun publish() = scope.launch {
        busy = true
        try {
            Contributions.publish(token, approved.map { it.entry }, author)
            app.repo.markPublished(approved.map { it.entry.id })
            snackbar.showSnackbar(
                "${approved.size} mot(s) publié(s). La nouvelle version du dictionnaire sera prête dans quelques minutes."
            )
        } catch (e: java.io.IOException) {
            snackbar.showSnackbar(e.message ?: "Échec de la publication (connexion ?).")
        } finally {
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contributions") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Intro(
                    if (editor) {
                        "Mode éditeur. Importez les fichiers de propositions reçus, validez-les, puis publiez : " +
                            "tous les utilisateurs recevront les nouveaux mots à la prochaine mise à jour."
                    } else {
                        "Vos ajouts et corrections restent sur ce téléphone. Envoyez-les à l'éditeur du dictionnaire " +
                            "(WhatsApp, e-mail…) : une fois validés, ils seront dans la mise à jour pour tous."
                    }
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    OutlinedButton(onClick = { nav.navigate(Routes.edit()) }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ajouter un mot")
                    }
                    OutlinedButton(onClick = {
                        picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    }) {
                        Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Importer")
                    }
                }
            }

            if (mine.isNotEmpty()) {
                section(
                    title = "Mes ajouts (${mine.size})",
                    list = mine,
                    nav = nav,
                    action = {
                        val notSent = mine.filter { !it.exported }
                        Button(onClick = { share(notSent.ifEmpty { mine }) }) {
                            Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (notSent.isNotEmpty()) "Envoyer (${notSent.size} nouveaux)" else "Renvoyer tout")
                        }
                    },
                    trailing = { l -> if (l.exported) Text("envoyé", style = MaterialTheme.typography.labelSmall) },
                )
            }
            if (review.isNotEmpty()) {
                section(
                    title = "Propositions à valider (${review.size})",
                    list = review,
                    nav = nav,
                    action = {
                        if (editor) {
                            Button(onClick = { scope.launch { app.repo.approve(review.map { it.entry.id }) } }) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text("Tout valider")
                            }
                        }
                    },
                    trailing = { l ->
                        if (editor) {
                            Row {
                                IconButton(onClick = { scope.launch { app.repo.approve(listOf(l.entry.id)) } }) {
                                    Icon(Icons.Outlined.Check, "Valider", tint = MaterialTheme.colorScheme.tertiary)
                                }
                                IconButton(onClick = { scope.launch { app.repo.delete(l.entry.id) } }) {
                                    Icon(Icons.Outlined.Close, "Refuser", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    note = if (!editor) "Activez le mode éditeur dans les Réglages pour valider." else null,
                )
            }
            if (approved.isNotEmpty()) {
                section(
                    title = "Validés, à publier (${approved.size})",
                    list = approved,
                    nav = nav,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = { if (token.isBlank()) askToken = true else publish() }) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Publier")
                            }
                            OutlinedButton(onClick = { share(approved) }) { Text("Exporter le fichier") }
                        }
                    },
                    trailing = { l ->
                        IconButton(onClick = { scope.launch { app.repo.delete(l.entry.id) } }) {
                            Icon(Icons.Outlined.Close, "Retirer")
                        }
                    },
                )
            }
            if (published.isNotEmpty()) {
                section(
                    title = "Publiés (${published.size})",
                    list = published,
                    nav = nav,
                    note = "Ils apparaîtront pour tous à la prochaine mise à jour du dictionnaire.",
                )
            }
            if (locals.isEmpty()) {
                item {
                    Text(
                        "Aucune contribution pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }

    if (askToken) {
        AlertDialog(
            onDismissRequest = { askToken = false },
            title = { Text("Jeton GitHub nécessaire") },
            text = {
                Text(
                    "Pour publier directement depuis le téléphone, enregistrez votre jeton GitHub dans les Réglages " +
                        "(section Mode éditeur). Vous pouvez aussi exporter le fichier et l'ajouter au dépôt à la main."
                )
            },
            confirmButton = {
                TextButton(onClick = { askToken = false; nav.navigate(Routes.SETTINGS) }) { Text("Ouvrir les réglages") }
            },
            dismissButton = { TextButton(onClick = { askToken = false }) { Text("Annuler") } },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<LocalEntry>,
    nav: NavHostController,
    action: (@Composable () -> Unit)? = null,
    trailing: (@Composable (LocalEntry) -> Unit)? = null,
    note: String? = null,
) {
    item(key = "h-$title") {
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionTitle(title)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action?.let {
                Spacer(Modifier.padding(top = 4.dp))
                it()
            }
        }
    }
    items(list, key = { "${title.take(3)}-${it.entry.id}" }) { l ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EntryRow(l.entry.summary(null), onClick = { nav.navigate(Routes.entry(l.entry.id)) }, showDivider = false)
                val sub = listOfNotNull(
                    if (l.isCorrection) "correction" else "nouveau mot",
                    l.entry.author?.let { "par $it" },
                ).joinToString(" · ")
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                )
            }
            trailing?.let { Row(Modifier.padding(end = 8.dp)) { it(l) } }
        }
    }
}

@Composable
private fun Intro(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}
