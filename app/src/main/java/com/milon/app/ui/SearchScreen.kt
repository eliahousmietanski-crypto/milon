package com.milon.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.milon.app.LocalApp
import com.milon.app.Routes
import com.milon.app.data.EntrySummary
import com.milon.app.data.Lang
import com.milon.app.data.Repository
import com.milon.app.data.TextNorm
import com.milon.app.ui.components.EmptyState
import com.milon.app.ui.components.EntryRow
import com.milon.app.ui.components.HebrewKeyboard
import com.milon.app.ui.components.LangBadge
import com.milon.app.ui.components.SectionTitle
import com.milon.app.ui.components.backspace
import com.milon.app.ui.components.insert
import com.milon.app.ui.theme.HebrewFamily
import com.milon.app.ui.theme.hebrewStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** État de la recherche, conservé pendant la navigation. */
class SearchState(private val repo: Repository, private val scope: CoroutineScope) {
    var query by mutableStateOf(TextFieldValue(""))
        private set
    var lang by mutableStateOf<Lang?>(null)
        private set
    var results by mutableStateOf<List<EntrySummary>>(emptyList())
        private set
    var searchedText by mutableStateOf("")
        private set
    var customKeyboard by mutableStateOf<Boolean?>(null)

    // Accueil
    var wordOfDay by mutableStateOf<EntrySummary?>(null)
        private set
    var recent by mutableStateOf<List<EntrySummary>>(emptyList())
        private set
    var info by mutableStateOf(repo.dict.info)
        private set
    var opened by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun onQuery(v: TextFieldValue) {
        val changed = v.text != query.text
        query = v
        if (changed) launchSearch(debounce = true)
    }

    fun onLang(l: Lang?) {
        lang = l
        launchSearch(debounce = false)
    }

    fun clear() = onQuery(TextFieldValue(""))

    fun refresh() {
        launchSearch(debounce = false)
        scope.launch {
            info = repo.dict.info
            opened = repo.dict.opened
            wordOfDay = repo.wordOfTheDay()
            recent = repo.history(6)
        }
    }

    private fun launchSearch(debounce: Boolean) {
        job?.cancel()
        val text = query.text
        job = scope.launch {
            if (text.isBlank()) {
                results = emptyList()
                searchedText = ""
                return@launch
            }
            if (debounce) delay(120)
            results = repo.search(text, lang)
            searchedText = text
        }
    }
}

@Composable
fun SearchScreen(state: SearchState, nav: NavHostController) {
    val app = LocalApp.current
    val keyboardDefault by app.settings.hebrewKeyboardDefault.collectAsStateWithLifecycle()
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    val customKb = state.customKeyboard ?: keyboardDefault
    var kbOpen by remember { mutableStateOf(state.query.text.isEmpty() && keyboardDefault) }
    val focus = remember { FocusRequester() }
    val softKb = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(changes) { state.refresh() }

    // Faire défiler les résultats ferme le clavier pour libérer l'écran
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            kbOpen = false
            softKb?.hide()
        }
    }
    BackHandler(enabled = kbOpen && customKb) { kbOpen = false }

    fun open(uid: String) {
        softKb?.hide()
        nav.navigate(Routes.entry(uid))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        SearchBar(
            value = state.query,
            onValueChange = state::onQuery,
            readOnly = customKb,
            focus = focus,
            onFieldTap = { if (customKb) kbOpen = true },
            onClear = state::clear,
            customKb = customKb,
            onToggleKeyboard = {
                if (customKb) {
                    state.customKeyboard = false
                    kbOpen = false
                    focus.requestFocus()
                    softKb?.show()
                } else {
                    state.customKeyboard = true
                    softKb?.hide()
                    kbOpen = true
                }
            },
            nav = nav,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            item { FilterChip(selected = state.lang == null, onClick = { state.onLang(null) }, label = { Text("Tout") }) }
            items(Lang.entries) { l ->
                FilterChip(
                    selected = state.lang == l,
                    onClick = { state.onLang(if (state.lang == l) null else l) },
                    label = { Text(l.short) },
                )
            }
        }
        UpdateBanner()

        Box(Modifier.weight(1f)) {
            when {
                state.query.text.isBlank() -> HomeContent(state, ::open, nav)
                state.results.isEmpty() && state.searchedText == state.query.text -> NoResults(state, nav)
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.uid }) { e ->
                        EntryRow(e, onClick = { open(e.uid) })
                    }
                    item { AddWordFooter(state.query.text, nav) }
                }
            }
        }

        AnimatedVisibility(visible = kbOpen && customKb, enter = expandVertically(), exit = shrinkVertically()) {
            HebrewKeyboard(
                onText = { state.onQuery(state.query.insert(it)) },
                onBackspace = { state.onQuery(state.query.backspace()) },
                onClose = { kbOpen = false },
                onSystemKeyboard = {
                    state.customKeyboard = false
                    kbOpen = false
                    focus.requestFocus()
                    softKb?.show()
                },
            )
        }
    }
}

@Composable
private fun SearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    focus: FocusRequester,
    onFieldTap: () -> Unit,
    onClear: () -> Unit,
    customKb: Boolean,
    onToggleKeyboard: () -> Unit,
    nav: NavHostController,
) {
    val colors = MaterialTheme.colorScheme
    val isHebrew = TextNorm.hasHebrew(value.text) || value.text.isEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        readOnly = readOnly,
                        singleLine = true,
                        cursorBrush = SolidColor(colors.primary),
                        textStyle = TextStyle(
                            fontFamily = if (isHebrew) HebrewFamily else null,
                            fontSize = if (isHebrew) 22.sp else 18.sp,
                            color = colors.onSurface,
                            textDirection = TextDirection.Content,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        decorationBox = { inner ->
                            if (value.text.isEmpty()) {
                                Text(
                                    "Chercher : שָׁלוֹם, ברא, paix…",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        },
                    )
                    // Le champ en lecture seule ne reçoit pas les appuis : on ouvre le clavier intégré
                    if (readOnly) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = onFieldTap,
                                )
                        )
                    }
                }
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "Effacer") }
                }
                TextButton(onClick = onToggleKeyboard) {
                    Text(
                        if (customKb) "Aa" else "א",
                        fontFamily = if (customKb) null else HebrewFamily,
                        fontSize = 18.sp,
                    )
                }
            }
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Favoris") },
                    leadingIcon = { Icon(Icons.Outlined.StarOutline, null) },
                    onClick = { menu = false; nav.navigate(Routes.list(ListKind.FAVORITES)) },
                )
                DropdownMenuItem(
                    text = { Text("Historique") },
                    leadingIcon = { Icon(Icons.Outlined.History, null) },
                    onClick = { menu = false; nav.navigate(Routes.list(ListKind.HISTORY)) },
                )
                DropdownMenuItem(
                    text = { Text("Ajouter un mot") },
                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { menu = false; nav.navigate(Routes.edit()) },
                )
                DropdownMenuItem(
                    text = { Text("Contributions") },
                    leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                    onClick = { menu = false; nav.navigate(Routes.CONTRIB) },
                )
                DropdownMenuItem(
                    text = { Text("Réglages") },
                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                    onClick = { menu = false; nav.navigate(Routes.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun HomeContent(state: SearchState, open: (String) -> Unit, nav: NavHostController) {
    val info = state.info
    LazyColumn(Modifier.fillMaxSize()) {
        if (!state.opened) return@LazyColumn
        if (info.entries == 0) {
            item {
                EmptyState(
                    "Dictionnaire non installé",
                    "Connectez-vous à internet puis lancez une mise à jour depuis les Réglages.",
                ) {
                    OutlinedButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Ouvrir les réglages") }
                }
            }
            return@LazyColumn
        }
        state.wordOfDay?.let { w ->
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Mot du jour", Modifier.padding(start = 4.dp))
                    Surface(
                        onClick = { open(w.uid) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(w.lemma, style = hebrewStyle(40f, bold = true))
                            Spacer(Modifier.height(4.dp))
                            LangBadge(w.lang, long = true)
                            Spacer(Modifier.height(8.dp))
                            Text(w.gloss, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                        }
                    }
                }
            }
        }
        if (state.recent.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp),
                ) {
                    SectionTitle("Consultés récemment", Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate(Routes.list(ListKind.HISTORY)) }) { Text("Tout voir") }
                }
            }
            items(state.recent, key = { "r-" + it.uid }) { e -> EntryRow(e, onClick = { open(e.uid) }) }
        }
        item {
            Text(
                "${"%,d".format(info.entries).replace(',', ' ')} mots · données n° ${info.dataVersion}\n" +
                    "Cherchez en hébreu ou en araméen (avec ou sans nikoud), par racine, ou en français.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun NoResults(state: SearchState, nav: NavHostController) {
    EmptyState(
        "Aucun résultat",
        if (state.lang != null) "Essayez sans le filtre « ${state.lang!!.short} »."
        else "Vérifiez l'orthographe, ou cherchez la racine du mot.",
    ) {
        if (state.lang != null) {
            OutlinedButton(onClick = { state.onLang(null) }) { Text("Chercher dans tout le dictionnaire") }
        }
        AddWordButton(state.query.text, nav)
    }
}

@Composable
private fun AddWordFooter(query: String, nav: NavHostController) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AddWordButton(query, nav)
    }
}

@Composable
private fun AddWordButton(query: String, nav: NavHostController) {
    TextButton(onClick = {
        nav.navigate(Routes.edit(lemma = if (TextNorm.hasHebrew(query)) query.trim() else null))
    }) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Ajouter un mot au dictionnaire")
    }
}
