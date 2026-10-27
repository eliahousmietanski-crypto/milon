package com.milon.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.milon.app.LocalApp
import com.milon.app.Routes
import com.milon.app.back
import com.milon.app.data.Entry
import com.milon.app.data.Lang
import com.milon.app.data.LocalStatus
import com.milon.app.data.Repository
import com.milon.app.data.TextNorm
import com.milon.app.ui.components.EmptyState
import com.milon.app.ui.components.LangBadge
import com.milon.app.ui.components.Pill
import com.milon.app.ui.components.SectionTitle
import com.milon.app.ui.theme.hebrewStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(uid: String, nav: NavHostController) {
    val app = LocalApp.current
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    val editor by app.settings.editorMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var view by remember { mutableStateOf<Repository.EntryView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }

    LaunchedEffect(uid, changes) {
        view = app.repo.entry(uid)
        favorite = app.repo.isFavorite(uid)
        loaded = true
    }
    LaunchedEffect(uid) { app.repo.addHistory(uid) }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Le mot apparaît dans la barre une fois l'en-tête parti en haut
                    if (scroll.state.contentOffset < -120f) view?.let { Text(it.entry.lemma, style = hebrewStyle(22f)) }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    val v = view ?: return@TopAppBar
                    IconButton(onClick = {
                        favorite = !favorite
                        scope.launch { app.repo.setFavorite(uid, favorite) }
                    }) {
                        Icon(
                            if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favori",
                            tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { context.startActivity(shareIntent(v.entry)) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Partager")
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Plus") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (v.local != null || editor) "Modifier" else "Proposer une correction") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menu = false; nav.navigate(Routes.edit(uid)) },
                            )
                            if (editor && !v.entry.verified) {
                                DropdownMenuItem(
                                    text = { Text("Marquer comme relu") },
                                    leadingIcon = { Icon(Icons.Outlined.Verified, null) },
                                    onClick = { menu = false; scope.launch { app.repo.markVerified(v.entry) } },
                                )
                            }
                            if (v.local != null) {
                                DropdownMenuItem(
                                    text = { Text(if (v.inDictionary) "Annuler ma modification" else "Supprimer mon ajout") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = {
                                        menu = false
                                        scope.launch {
                                            app.repo.delete(uid)
                                            if (!v.inDictionary) nav.back()
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) { padding ->
        val v = view
        when {
            v != null -> EntryContent(
                v, editor, nav,
                modifier = Modifier.padding(padding),
                onApprove = { scope.launch { app.repo.approve(listOf(uid)) } },
                onReject = { scope.launch { app.repo.delete(uid); nav.back() } },
            )
            loaded -> EmptyState("Mot introuvable", "Il a peut-être été supprimé ou renommé.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryContent(
    v: Repository.EntryView,
    editor: Boolean,
    nav: NavHostController,
    modifier: Modifier,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val e = v.entry
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // En-tête
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SelectionContainer {
                Text(e.lemma, style = hebrewStyle(46f, bold = true), textAlign = TextAlign.Center)
            }
            e.translit?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LangBadge(e.lang, long = true, extra = e.dialect)
                listOfNotNull(e.pos, e.binyan, e.gender).forEach {
                    Pill(it, colors.surfaceContainerHigh, colors.onSurface)
                }
            }
            e.root?.let { root ->
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { nav.navigate(Routes.root(root)) },
                    label = {
                        Text("Racine  ", style = MaterialTheme.typography.labelLarge)
                        Text(TextNorm.displayRoot(root), style = hebrewStyle(18f, bold = true))
                    },
                    leadingIcon = { Icon(Icons.Outlined.AccountTree, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }

        v.local?.let { local ->
            Spacer(Modifier.height(12.dp))
            val text = when (local.status) {
                LocalStatus.MINE -> "Votre ${if (local.isCorrection) "correction" else "ajout"} : visible seulement sur ce téléphone " +
                    "tant qu'il n'a pas été validé. Envoyez-le depuis « Contributions »."
                LocalStatus.REVIEW -> "Proposition${e.author?.let { " de $it" } ?: ""} à valider" +
                    if (local.isCorrection) " (correction d'un mot existant)." else "."
                LocalStatus.APPROVED -> "Validé, en attente de publication (Contributions → Publier)."
                LocalStatus.PUBLISHED -> "Publié : sera dans la prochaine mise à jour du dictionnaire."
            }
            Notice(text)
            if (local.status == LocalStatus.REVIEW && editor) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onApprove) {
                        Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(6.dp)); Text("Valider")
                    }
                    OutlinedButton(onClick = onReject) {
                        Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(6.dp)); Text("Refuser")
                    }
                }
            }
        } ?: run {
            if (!e.verified) {
                Spacer(Modifier.height(12.dp))
                Notice("Traduction préparée automatiquement, pas encore relue. Vous pouvez proposer une correction (menu ⋮).")
            }
        }

        // Sens
        SectionTitle(if (e.senses.size > 1) "Sens" else "Traduction")
        e.senses.forEachIndexed { i, s ->
            Row(Modifier.padding(bottom = 12.dp)) {
                if (e.senses.size > 1) {
                    Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    s.ctx?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = colors.secondary)
                    }
                    SelectionContainer { Text(s.fr, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                    s.note?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }

        if (e.examples.isNotEmpty()) {
            SectionTitle("Sources et exemples")
            e.examples.forEach { x ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        x.ref?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.secondary) }
                        x.src?.let {
                            SelectionContainer {
                                Text(
                                    it,
                                    style = hebrewStyle(21f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                )
                            }
                        }
                        x.fr?.let {
                            Text("« $it »", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                        }
                    }
                }
            }
        }

        if (e.equiv.isNotEmpty()) {
            SectionTitle("Équivalents")
            e.equiv.forEach { q ->
                Surface(
                    onClick = { nav.navigate(Routes.entry(q.id)) },
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        q.lang?.let { LangBadge(Lang.of(it)) }
                        Spacer(Modifier.weight(1f))
                        Text(q.lemma ?: q.id, style = hebrewStyle(22f))
                    }
                }
            }
        }

        if (e.forms.isNotEmpty()) {
            SectionTitle("Autres formes")
            SelectionContainer {
                Text(e.forms.joinToString("  ·  "), style = hebrewStyle(20f), modifier = Modifier.fillMaxWidth())
            }
        }

        e.notes?.let {
            SectionTitle("Remarques")
            SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Spacer(Modifier.height(24.dp))
        val footer = listOfNotNull(
            e.source?.let { "Source : $it" },
            e.author?.let { "Contribution : $it" },
            if (e.verified) "Relu ✓" else null,
        ).joinToString("   ·   ")
        if (footer.isNotEmpty()) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareIntent(e: Entry): Intent {
    val text = buildString {
        append(e.lemma)
        e.translit?.let { append(" ($it)") }
        append(" — ").append(e.lang.label)
        e.root?.let { append(", racine ").append(TextNorm.displayRoot(it)) }
        append("\n")
        e.senses.forEachIndexed { i, s ->
            append("\n").append(if (e.senses.size > 1) "${i + 1}. " else "")
            s.ctx?.let { append("[$it] ") }
            append(s.fr)
        }
        e.examples.firstOrNull()?.let { x ->
            append("\n\n")
            x.src?.let { append(it).append("\n") }
            x.fr?.let { append("« $it »") }
            x.ref?.let { append(" (").append(it).append(")") }
        }
        append("\n\n— Dictionnaire Milon")
    }
    return Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
        "Partager",
    )
}
