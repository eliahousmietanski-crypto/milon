package com.milon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Point d'accès unique aux données : dictionnaire + ajouts locaux de l'utilisateur. */
class Repository(
    val dict: DictionaryDb,
    private val user: UserStore,
    private val settings: Settings,
) {
    private val _changes = MutableStateFlow(0)
    /** Incrémenté à chaque modification : l'interface recharge ce qu'elle affiche. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private class Indexed(val local: LocalEntry, val keys: List<SearchKey>)

    @Volatile
    private var localCache: List<Indexed>? = null

    private fun locals(): List<Indexed> =
        localCache ?: user.localEntries().map { Indexed(it, KeyGen.keys(it.entry)) }.also { localCache = it }

    private fun changed() {
        localCache = null
        _changes.value++
    }

    suspend fun open() = withContext(Dispatchers.IO) { dict.open() }

    // --- Lecture -------------------------------------------------------------

    suspend fun search(raw: String, lang: Lang?): List<EntrySummary> = withContext(Dispatchers.IO) {
        val q = Query.parse(raw) ?: return@withContext emptyList()
        val fromDb = dict.search(q, lang)
        val visible = locals().filter { it.local.status != LocalStatus.REVIEW }
        val overridden = visible.map { it.local.entry.id }.toSet()
        val fromLocal = visible
            .filter { lang == null || it.local.entry.lang == lang }
            .mapNotNull { ix -> KeyGen.score(ix.keys, q)?.let { ix.local.entry.summary(ix.local.status) to it } }
        (fromDb.filter { it.first.uid !in overridden } + fromLocal)
            .sortedBy { it.second }
            .map { it.first }
    }

    data class EntryView(val entry: Entry, val local: LocalEntry?, val inDictionary: Boolean)

    suspend fun entry(uid: String): EntryView? = withContext(Dispatchers.IO) {
        val local = locals().firstOrNull { it.local.entry.id == uid }?.local
        val inDict = dict.exists(uid)
        val e = local?.entry ?: dict.entry(uid) ?: return@withContext null
        EntryView(e, local, inDict)
    }

    private fun summaryOf(uid: String): EntrySummary? =
        locals().firstOrNull { it.local.entry.id == uid }?.let { it.local.entry.summary(it.local.status) }
            ?: dict.summary(uid)

    suspend fun byRoot(root: String): List<EntrySummary> = withContext(Dispatchers.IO) {
        val key = TextNorm.hebrewPlain(root).replace(" ", "")
        val local = locals().filter { ix ->
            ix.local.status != LocalStatus.REVIEW &&
                ix.local.entry.root?.let { TextNorm.hebrewPlain(it).replace(" ", "") } == key
        }
        val ids = local.map { it.local.entry.id }.toSet()
        dict.byRoot(root).filter { it.uid !in ids } + local.map { it.local.entry.summary(it.local.status) }
    }

    suspend fun favorites(): List<EntrySummary> = withContext(Dispatchers.IO) {
        user.favorites().mapNotNull { summaryOf(it) }
    }

    suspend fun history(limit: Int = 200): List<EntrySummary> = withContext(Dispatchers.IO) {
        user.history(limit).mapNotNull { summaryOf(it) }
    }

    suspend fun wordOfTheDay(): EntrySummary? = withContext(Dispatchers.IO) {
        dict.wordOfTheDay(System.currentTimeMillis() / 86_400_000L)
    }

    suspend fun isFavorite(uid: String) = withContext(Dispatchers.IO) { user.isFavorite(uid) }

    suspend fun setFavorite(uid: String, fav: Boolean) = withContext(Dispatchers.IO) {
        user.setFavorite(uid, fav)
        _changes.value++
    }

    suspend fun addHistory(uid: String) = withContext(Dispatchers.IO) { user.addHistory(uid) }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        user.clearHistory()
        _changes.value++
    }

    // --- Ajouts et corrections ----------------------------------------------

    suspend fun localEntries(): List<LocalEntry> = withContext(Dispatchers.IO) { locals().map { it.local } }

    fun newId(lang: Lang): String = "${lang.code}-u${UUID.randomUUID().toString().take(8)}"

    /** Enregistre un ajout ou une correction. En mode éditeur, c'est directement « à publier ». */
    suspend fun save(entry: Entry) = withContext(Dispatchers.IO) {
        val editor = settings.editorMode.value
        val author = settings.author.value.ifBlank { null }
        val e = entry.copy(author = entry.author ?: author, verified = editor || entry.verified)
        user.saveLocal(e, if (editor) LocalStatus.APPROVED else LocalStatus.MINE, dict.exists(e.id))
        changed()
    }

    /** Éditeur : marque une entrée existante comme relue. */
    suspend fun markVerified(entry: Entry) = withContext(Dispatchers.IO) {
        user.saveLocal(entry.copy(verified = true), LocalStatus.APPROVED, dict.exists(entry.id))
        changed()
    }

    suspend fun approve(uids: Collection<String>) = withContext(Dispatchers.IO) {
        val byId = locals().associateBy { it.local.entry.id }
        uids.forEach { uid ->
            byId[uid]?.let { user.saveLocal(it.local.entry.copy(verified = true), LocalStatus.APPROVED, it.local.isCorrection) }
        }
        changed()
    }

    suspend fun delete(uid: String) = withContext(Dispatchers.IO) {
        user.deleteLocal(uid)
        changed()
    }

    suspend fun markExported(uids: Collection<String>) = withContext(Dispatchers.IO) {
        user.markExported(uids)
        changed()
    }

    suspend fun markPublished(uids: Collection<String>) = withContext(Dispatchers.IO) {
        user.setStatus(uids, LocalStatus.PUBLISHED)
        changed()
    }

    /** Propositions reçues : à valider par l'éditeur. Renvoie le nombre d'entrées importées. */
    suspend fun importProposals(entries: List<Entry>): Int = withContext(Dispatchers.IO) {
        entries.forEach { user.saveLocal(it.copy(verified = false), LocalStatus.REVIEW, dict.exists(it.id)) }
        changed()
        entries.size
    }

    /** Après une mise à jour : les entrées publiées avant la construction de la base y sont désormais. */
    suspend fun onDictionaryUpdated() = withContext(Dispatchers.IO) {
        val built = dict.info.built?.let { parseIso(it) } ?: 0L
        locals().map { it.local }
            .filter { it.status == LocalStatus.PUBLISHED && it.updated < built && dict.exists(it.entry.id) }
            .forEach { user.deleteLocal(it.entry.id) }
        changed()
    }

    private fun parseIso(s: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time
    } catch (_: java.text.ParseException) {
        null
    }
}
