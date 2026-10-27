package com.milon.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.milon.app.BuildConfig
import org.json.JSONObject
import java.io.File

/**
 * La base du dictionnaire (générée par tools/build_db.py), en lecture seule.
 * Livrée dans l'APK (assets/milon.db) puis remplacée par les mises à jour téléchargées.
 */
class DictionaryDb(private val context: Context, private val settings: Settings) {
    private val file = File(context.filesDir, "milon.db")
    private var db: SQLiteDatabase? = null

    data class Info(val dataVersion: Int, val entries: Int, val built: String?)

    @Volatile
    var info: Info = Info(0, 0, null)
        private set

    /** Vrai une fois la base ouverte (ou constatée absente). */
    @Volatile
    var opened = false
        private set

    @Synchronized
    fun open() {
        if (db != null) return
        // Première installation, ou nouvel APK contenant des données plus récentes
        if (!file.exists() || BuildConfig.DATA_VERSION > settings.dataVersion) {
            installFromAssets()
        }
        if (file.exists()) {
            db = openFile(file)
            info = readInfo(db!!)
            settings.dataVersion = info.dataVersion
        }
        opened = true
    }

    private fun installFromAssets() {
        val tmp = File(context.filesDir, "milon.db.asset")
        try {
            context.assets.open("milon.db").use { input ->
                tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
            }
            file.delete()
            tmp.renameTo(file)
        } catch (_: java.io.IOException) {
            tmp.delete() // APK de développement sans base intégrée
        }
    }

    private fun openFile(f: File): SQLiteDatabase = SQLiteDatabase.openDatabase(
        f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    private fun readInfo(d: SQLiteDatabase): Info {
        val meta = HashMap<String, String>()
        d.rawQuery("SELECT k, v FROM meta", null).use { c ->
            while (c.moveToNext()) meta[c.getString(0)] = c.getString(1)
        }
        return Info(
            meta["data_version"]?.toIntOrNull() ?: 0,
            meta["entries"]?.toIntOrNull() ?: 0,
            meta["built"],
        )
    }

    /** Vérifie un fichier téléchargé puis le met en place. Renvoie la nouvelle version. */
    @Synchronized
    fun replaceWith(candidate: File): Info {
        val newInfo = openFile(candidate).use { d ->
            val i = readInfo(d)
            d.rawQuery("SELECT count(*) FROM entries", null).use { c ->
                c.moveToFirst()
                check(c.getInt(0) == i.entries) { "Base téléchargée incomplète" }
            }
            i
        }
        db?.close()
        db = null
        file.delete()
        check(candidate.renameTo(file)) { "Impossible d'installer la nouvelle base" }
        db = openFile(file)
        info = newInfo
        settings.dataVersion = newInfo.dataVersion
        return newInfo
    }

    private fun <T> withDb(default: T, block: (SQLiteDatabase) -> T): T {
        val d = synchronized(this) { db } ?: return default
        return block(d)
    }

    /** Recherche ; [lang] null = toutes les langues. */
    fun search(q: Query, lang: Lang?, limit: Int = 150): List<Pair<EntrySummary, Int>> = withDb(emptyList()) { d ->
        val scores = HashMap<Long, Int>()
        val langCode = lang?.code ?: ""
        when (q) {
            is Query.Hebrew -> {
                scanKeys(d, q.plain, null, "1,3,4", langCode) { entry, key, kind, w ->
                    Scoring.hebrew(q, key, kind, w)?.let { s -> scores.merge(entry, s) { a, b -> minOf(a, b) } }
                }
                scanKeys(d, q.skeleton, null, "2", langCode) { entry, key, kind, w ->
                    Scoring.hebrew(q, key, kind, w)?.let { s -> scores.merge(entry, s) { a, b -> minOf(a, b) } }
                }
            }
            is Query.Latin -> {
                val perWord = q.words.mapIndexed { i, word ->
                    val prefix = i == q.words.lastIndex
                    HashMap<Long, Int>().also { cur ->
                        scanKeys(d, word, if (prefix) null else word, "5,6", langCode) { entry, key, kind, w ->
                            Scoring.latin(word, prefix, key, kind, w)?.let { s -> cur.merge(entry, s) { a, b -> minOf(a, b) } }
                        }
                    }
                }
                // Les entrées qui contiennent tous les mots ; à défaut, celles qui en
                // contiennent une partie, pénalisées de 100 par mot manquant.
                val all = perWord.first().keys.filter { e -> perWord.all { e in it } }
                val candidates = all.ifEmpty { perWord.flatMap { it.keys }.distinct() }
                for (e in candidates) {
                    val present = perWord.filter { e in it }
                    scores[e] = present.sumOf { it.getValue(e) } + 100 * (perWord.size - present.size)
                }
            }
        }
        val top = scores.entries.sortedWith(compareBy({ it.value }, { it.key })).take(limit)
        if (top.isEmpty()) return@withDb emptyList()
        val byId = summaries(d, "id IN (${top.joinToString(",") { it.key.toString() }})")
            .associateBy { it.first }
        top.mapNotNull { (id, score) -> byId[id]?.let { it.second to score } }
    }

    /** Parcourt les clés commençant par [prefix] (ou égales à [exact]). */
    private inline fun scanKeys(
        d: SQLiteDatabase, prefix: String, exact: String?, kinds: String, lang: String,
        onRow: (entry: Long, key: String, kind: Int, w: Int) -> Unit,
    ) {
        val sql = buildString {
            append("SELECT k.entry, k.key, k.kind, k.w FROM keys k")
            if (lang.isNotEmpty()) append(" JOIN entries e ON e.id = k.entry")
            if (exact != null) append(" WHERE k.key = ?") else append(" WHERE k.key >= ? AND k.key < ?")
            append(" AND k.kind IN ($kinds)")
            if (lang.isNotEmpty()) append(" AND e.lang = ?")
            append(" LIMIT 4000")
        }
        val args = buildList {
            if (exact != null) add(exact) else { add(prefix); add(prefix + '￿') }
            if (lang.isNotEmpty()) add(lang)
        }.toTypedArray()
        d.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) onRow(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3))
        }
    }

    private fun summaries(d: SQLiteDatabase, where: String, args: Array<String>? = null, order: String = ""):
        List<Pair<Long, EntrySummary>> =
        d.rawQuery(
            "SELECT id, uid, lemma, lang, root, pos, gloss, verified FROM entries WHERE $where $order", args
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.toSummary()) }
        }

    private fun Cursor.toSummary() = EntrySummary(
        uid = getString(1),
        lemma = getString(2),
        lang = Lang.of(getString(3)),
        root = if (isNull(4)) null else getString(4),
        pos = if (isNull(5)) null else getString(5),
        gloss = getString(6),
        verified = getInt(7) == 1,
    )

    fun entry(uid: String): Entry? = withDb(null) { d ->
        d.rawQuery("SELECT json FROM entries WHERE uid = ?", arrayOf(uid)).use { c ->
            if (c.moveToFirst()) Entry.fromJson(JSONObject(c.getString(0))) else null
        }
    }

    fun summary(uid: String): EntrySummary? = withDb(null) { d ->
        summaries(d, "uid = ?", arrayOf(uid)).firstOrNull()?.second
    }

    fun exists(uid: String): Boolean = withDb(false) { d ->
        d.rawQuery("SELECT 1 FROM entries WHERE uid = ?", arrayOf(uid)).use { it.moveToFirst() }
    }

    /** Toutes les entrées d'une même racine. */
    fun byRoot(root: String): List<EntrySummary> = withDb(emptyList()) { d ->
        val key = TextNorm.hebrewPlain(root).replace(" ", "")
        summaries(d, "root_plain = ?", arrayOf(key), "ORDER BY lang, plain").map { it.second }
    }

    /** Un mot différent chaque jour. */
    fun wordOfTheDay(day: Long): EntrySummary? = withDb(null) { d ->
        val n = info.entries
        if (n == 0) return@withDb null
        // Mélange déterministe pour ne pas suivre l'ordre alphabétique
        val id = ((day * 2654435761L) % n + n) % n + 1
        summaries(d, "id = ?", arrayOf(id.toString())).firstOrNull()?.second
    }
}
