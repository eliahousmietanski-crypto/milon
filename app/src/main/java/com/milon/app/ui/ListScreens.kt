package com.milon.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.milon.app.LocalApp
import com.milon.app.Routes
import com.milon.app.back
import com.milon.app.data.EntrySummary
import com.milon.app.data.TextNorm
import com.milon.app.ui.components.EmptyState
import com.milon.app.ui.components.EntryRow
import com.milon.app.ui.theme.hebrewStyle
import kotlinx.coroutines.launch

enum class ListKind(val title: String, val empty: String) {
    FAVORITES("Favoris", "Touchez l'étoile d'un mot pour le retrouver ici."),
    HISTORY("Historique", "Les mots consultés apparaîtront ici."),
}

@Composable
fun EntryListScreen(kind: ListKind, nav: NavHostController) {
    val app = LocalApp.current
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<EntrySummary>?>(null) }
    LaunchedEffect(changes) {
        items = when (kind) {
            ListKind.FAVORITES -> app.repo.favorites()
            ListKind.HISTORY -> app.repo.history()
        }
    }
    ListScaffold(
        title = { Text(kind.title) },
        nav = nav,
        actions = {
            if (kind == ListKind.HISTORY && !items.isNullOrEmpty()) {
                TextButton(onClick = { scope.launch { app.repo.clearHistory() } }) { Text("Effacer") }
            }
        },
        items = items,
        empty = { EmptyState(kind.empty) },
    )
}

@Composable
fun RootScreen(root: String, nav: NavHostController) {
    val app = LocalApp.current
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<EntrySummary>?>(null) }
    LaunchedEffect(root, changes) { items = app.repo.byRoot(root) }
    ListScaffold(
        title = { Text("Racine  " + TextNorm.displayRoot(root), style = hebrewStyle(22f, bold = true)) },
        nav = nav,
        items = items,
        empty = { EmptyState("Aucun mot pour cette racine") },
        header = items?.let { "${it.size} mot${if (it.size > 1) "s" else ""} de la même famille" },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScaffold(
    title: @Composable () -> Unit,
    nav: NavHostController,
    items: List<EntrySummary>?,
    empty: @Composable () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    header: String? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        when {
            items == null -> Unit
            items.isEmpty() -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { empty() }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                header?.let {
                    item {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(items, key = { it.uid }) { e -> EntryRow(e, onClick = { nav.navigate(Routes.entry(e.uid)) }) }
            }
        }
    }
}
