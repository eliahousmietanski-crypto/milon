package com.milon.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.milon.app.LocalApp
import com.milon.app.back
import com.milon.app.data.Entry
import com.milon.app.data.EntrySummary
import com.milon.app.data.EquivRef
import com.milon.app.data.Example
import com.milon.app.data.Lang
import com.milon.app.data.Sense
import com.milon.app.ui.components.EntryRow
import com.milon.app.ui.components.HebrewKeyboard
import com.milon.app.ui.components.SectionTitle
import com.milon.app.ui.components.backspace
import com.milon.app.ui.components.insert
import com.milon.app.ui.theme.hebrewStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val posOptions = listOf(
    "nom masc.", "nom fém.", "verbe", "adjectif", "adverbe", "préposition", "conjonction",
    "pronom", "particule", "interjection", "nom propre", "expression",
)
private val hebrewBinyanim = listOf("qal", "nif'al", "pi'el", "pu'al", "hif'il", "hof'al", "hitpa'el")
private val aramaicBinyanim = listOf("pe'al", "itpe'el", "pa'el", "itpa'al", "af'el", "ittaf'al")
private val dialects = mapOf(
    Lang.BIBLICAL to listOf("Torah", "Prophètes", "Hagiographes"),
    Lang.RABBINIC to listOf("Michna", "Talmud", "Midrach", "Kabbale", "Liturgie"),
    Lang.ARAMAIC to listOf("Araméen biblique", "Targoum", "Talmud de Babylone", "Talmud de Jérusalem", "Zohar"),
)

private class SenseForm(fr: String = "", ctx: String = "", note: String = "") {
    var fr by mutableStateOf(fr)
    var ctx by mutableStateOf(ctx)
    var note by mutableStateOf(note)
}

private class ExampleForm(ref: String = "", src: String = "", fr: String = "") {
    var ref by mutableStateOf(ref)
    val src = mutableStateOf(TextFieldValue(src))
    var fr by mutableStateOf(fr)
}

private class EditForm(e: Entry?, initialLemma: String) {
    val lemma = mutableStateOf(TextFieldValue(e?.lemma ?: initialLemma))
    var lang by mutableStateOf(e?.lang)
    var dialect by mutableStateOf(e?.dialect ?: "")
    val root = mutableStateOf(TextFieldValue(e?.root ?: ""))
    var pos by mutableStateOf(e?.pos ?: "")
    var binyan by mutableStateOf(e?.binyan ?: "")
    var translit by mutableStateOf(e?.translit ?: "")
    val forms = mutableStateOf(TextFieldValue(e?.forms?.joinToString(", ") ?: ""))
    val senses = mutableStateListOf<SenseForm>().apply {
        e?.senses?.forEach { add(SenseForm(it.fr, it.ctx ?: "", it.note ?: "")) }
        if (isEmpty()) add(SenseForm())
    }
    val examples = mutableStateListOf<ExampleForm>().apply {
        e?.examples?.forEach { add(ExampleForm(it.ref ?: "", it.src ?: "", it.fr ?: "")) }
    }
    val equiv = mutableStateListOf<EquivRef>().apply { e?.equiv?.let { addAll(it) } }
    var notes by mutableStateOf(e?.notes ?: "")

    fun toEntry(id: String, base: Entry?): Entry = Entry(
        id = id,
        lemma = lemma.value.text.trim(),
        lang = lang!!,
        dialect = dialect.ifBlank { null },
        root = root.value.text.trim().ifBlank { null },
        pos = pos.ifBlank { null },
        binyan = binyan.ifBlank { null },
        gender = base?.gender,
        translit = translit.trim().ifBlank { null },
        forms = forms.value.text.split(',', ';', '،').map { it.trim() }.filter { it.isNotEmpty() },
        senses = senses.filter { it.fr.isNotBlank() }.map {
            Sense(it.fr.trim(), it.ctx.trim().ifBlank { null }, it.note.trim().ifBlank { null })
        },
        examples = examples.map { Example(it.ref.trim().ifBlank { null }, it.src.value.text.trim().ifBlank { null }, it.fr.trim().ifBlank { null }) }
            .filter { it.src != null || it.fr != null },
        equiv = equiv.toList(),
        notes = notes.trim().ifBlank { null },
        source = base?.source ?: "Milon (ajout)",
        verified = false,
        author = base?.author,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditEntryScreen(uid: String?, initialLemma: String, nav: NavHostController) {
    val app = LocalApp.current
    val editor by app.settings.editorMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val softKb = LocalSoftwareKeyboardController.current

    var base by remember { mutableStateOf<Entry?>(null) }
    var form by remember { mutableStateOf<EditForm?>(null) }
    LaunchedEffect(uid) {
        base = uid?.let { app.repo.entry(it)?.entry }
        form = EditForm(base, initialLemma)
    }

    // Champ hébreu actif et choix du clavier (intégré ou celui du téléphone)
    var active by remember { mutableStateOf<MutableState<TextFieldValue>?>(null) }
    var customKb by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    BackHandler(enabled = active != null && customKb) { focusManager.clearFocus() }

    val f = form
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uid == null) "Nouveau mot" else if (editor) "Modifier" else "Proposer une correction") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                },
                actions = {
                    TextButton(enabled = f != null, onClick = {
                        val fm = f ?: return@TextButton
                        error = when {
                            fm.lemma.value.text.isBlank() -> "Indiquez le mot en hébreu ou en araméen."
                            fm.lang == null -> "Choisissez la langue."
                            fm.senses.none { it.fr.isNotBlank() } -> "Indiquez au moins une traduction française."
                            else -> null
                        }
                        if (error == null) {
                            val entry = fm.toEntry(base?.id ?: app.repo.newId(fm.lang!!), base)
                            scope.launch {
                                app.repo.save(entry)
                                Toast.makeText(
                                    context,
                                    if (editor) "Enregistré · à publier depuis Contributions"
                                    else "Enregistré · envoyez-le depuis Contributions",
                                    Toast.LENGTH_LONG,
                                ).show()
                                nav.back()
                            }
                        }
                    }) { Text("Enregistrer") }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(active != null && customKb, enter = expandVertically(), exit = shrinkVertically()) {
                HebrewKeyboard(
                    onText = { s -> active?.let { it.value = it.value.insert(s) } },
                    onBackspace = { active?.let { it.value = it.value.backspace() } },
                    onClose = { focusManager.clearFocus() },
                    onSystemKeyboard = {
                        customKb = false
                        scope.launch { delay(50); softKb?.show() }
                    },
                    withNiqqud = true,
                )
            }
        },
    ) { padding ->
        if (f == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }

            val hebrewField: @Composable (String, MutableState<TextFieldValue>, Boolean, Modifier) -> Unit =
                { label, state, big, mod ->
                    OutlinedTextField(
                        value = state.value,
                        onValueChange = { state.value = it },
                        label = { Text(label) },
                        readOnly = customKb,
                        textStyle = hebrewStyle(if (big) 26f else 20f),
                        trailingIcon = {
                            IconButton(onClick = {
                                customKb = !customKb
                                if (!customKb) softKb?.show() else softKb?.hide()
                            }) {
                                Icon(Icons.Outlined.Keyboard, contentDescription = "Changer de clavier")
                            }
                        },
                        modifier = mod
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (it.isFocused) {
                                    active = state
                                    if (customKb) softKb?.hide()
                                } else if (active === state) {
                                    active = null
                                }
                            },
                    )
                }

            SectionTitle("Mot")
            hebrewField("Mot vocalisé (avec nikoud) *", f.lemma, true, Modifier)
            Spacer(Modifier.height(12.dp))
            Text("Langue *", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lang.entries.forEach { l ->
                    FilterChip(selected = f.lang == l, onClick = { f.lang = l; f.binyan = "" }, label = { Text(l.label) })
                }
            }
            f.lang?.let { l ->
                Text("Corpus (facultatif)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dialects[l].orEmpty().forEach { d ->
                        FilterChip(
                            selected = f.dialect == d,
                            onClick = { f.dialect = if (f.dialect == d) "" else d },
                            label = { Text(d) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            hebrewField("Racine (ex. ברא)", f.root, false, Modifier)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = f.translit, onValueChange = { f.translit = it },
                label = { Text("Prononciation (ex. bara)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Grammaire")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                posOptions.forEach { p ->
                    FilterChip(selected = f.pos == p, onClick = { f.pos = if (f.pos == p) "" else p }, label = { Text(p) })
                }
            }
            if (f.pos == "verbe") {
                Text("Binyan", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (f.lang == Lang.ARAMAIC) aramaicBinyanim else hebrewBinyanim).forEach { b ->
                        FilterChip(selected = f.binyan == b, onClick = { f.binyan = if (f.binyan == b) "" else b }, label = { Text(b) })
                    }
                }
            }

            SectionTitle("Traductions françaises")
            f.senses.forEachIndexed { i, s ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sens ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (f.senses.size > 1) {
                                IconButton(onClick = { f.senses.removeAt(i) }) { Icon(Icons.Outlined.Close, "Retirer") }
                            }
                        }
                        OutlinedTextField(
                            value = s.fr, onValueChange = { s.fr = it },
                            label = { Text("Traduction *") }, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s.ctx, onValueChange = { s.ctx = it },
                            label = { Text("Contexte (ex. sens littéral, Zohar, Talmud)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s.note, onValueChange = { s.note = it },
                            label = { Text("Précision (facultatif)") }, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            OutlinedButton(onClick = { f.senses.add(SenseForm()) }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.padding(start = 6.dp)); Text("Ajouter un sens")
            }

            SectionTitle("Sources et exemples")
            f.examples.forEachIndexed { i, x ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Exemple ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { f.examples.removeAt(i) }) { Icon(Icons.Outlined.Close, "Retirer") }
                        }
                        OutlinedTextField(
                            value = x.ref, onValueChange = { x.ref = it },
                            label = { Text("Référence (ex. Berechit 1:1, Zohar I 15a)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        hebrewField("Citation dans le texte", x.src, false, Modifier)
                        OutlinedTextField(
                            value = x.fr, onValueChange = { x.fr = it },
                            label = { Text("Traduction de la citation") }, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            OutlinedButton(onClick = { f.examples.add(ExampleForm()) }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.padding(start = 6.dp)); Text("Ajouter un exemple")
            }

            SectionTitle("Équivalent hébreu / araméen")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.equiv.forEach { q ->
                    InputChip(
                        selected = false,
                        onClick = { f.equiv.remove(q) },
                        label = { Text(q.lemma ?: q.id, style = hebrewStyle(18f)) },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Retirer") },
                    )
                }
            }
            OutlinedButton(onClick = { picking = true }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.padding(start = 6.dp)); Text("Lier un équivalent")
            }

            SectionTitle("Autres formes et remarques")
            hebrewField("Autres formes (séparées par des virgules)", f.forms, false, Modifier)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = f.notes, onValueChange = { f.notes = it },
                label = { Text("Remarques") }, modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
            )
        }
    }

    if (picking && f != null) {
        EquivPicker(
            exclude = base?.id,
            onPick = { s ->
                if (f.equiv.none { it.id == s.uid }) f.equiv.add(EquivRef(s.uid, s.lemma, s.lang.code))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun EquivPicker(exclude: String?, onPick: (EntrySummary) -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EntrySummary>>(emptyList()) }
    LaunchedEffect(q) {
        delay(150)
        results = app.repo.search(q, null).filter { it.uid != exclude }.take(30)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Lier un équivalent") },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text("Chercher le mot équivalent") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.uid }) { s -> EntryRow(s, onClick = { onPick(s) }) }
                }
            }
        },
    )
}
