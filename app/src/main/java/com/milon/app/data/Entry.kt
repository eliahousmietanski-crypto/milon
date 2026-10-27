package com.milon.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class Lang(val code: String, val label: String, val short: String) {
    BIBLICAL("hb", "Hébreu biblique", "Biblique"),
    RABBINIC("hr", "Hébreu rabbinique", "Rabbinique"),
    ARAMAIC("ar", "Araméen", "Araméen");

    companion object {
        fun of(code: String?): Lang = entries.firstOrNull { it.code == code } ?: BIBLICAL
    }
}

data class Sense(val fr: String, val ctx: String? = null, val note: String? = null)

data class Example(val ref: String? = null, val src: String? = null, val fr: String? = null)

data class EquivRef(val id: String, val lemma: String? = null, val lang: String? = null)

/** Une entrée complète du dictionnaire (format identique aux fichiers JSONL de data/). */
data class Entry(
    val id: String,
    val lemma: String,
    val lang: Lang,
    val dialect: String? = null,
    val root: String? = null,
    val pos: String? = null,
    val binyan: String? = null,
    val gender: String? = null,
    val translit: String? = null,
    val forms: List<String> = emptyList(),
    val senses: List<Sense> = emptyList(),
    val examples: List<Example> = emptyList(),
    val equiv: List<EquivRef> = emptyList(),
    val notes: String? = null,
    val source: String? = null,
    val verified: Boolean = false,
    val author: String? = null,
) {
    val gloss: String get() = senses.take(3).joinToString(" ; ") { it.fr }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("lemma", lemma)
        put("lang", lang.code)
        putOpt("dialect", dialect.nullIfBlank())
        putOpt("root", root.nullIfBlank())
        putOpt("pos", pos.nullIfBlank())
        putOpt("binyan", binyan.nullIfBlank())
        putOpt("gender", gender.nullIfBlank())
        putOpt("translit", translit.nullIfBlank())
        if (forms.isNotEmpty()) put("forms", JSONArray(forms))
        put("senses", JSONArray().apply {
            senses.forEach { s ->
                put(JSONObject().apply {
                    put("fr", s.fr)
                    putOpt("ctx", s.ctx.nullIfBlank())
                    putOpt("note", s.note.nullIfBlank())
                })
            }
        })
        if (examples.isNotEmpty()) put("examples", JSONArray().apply {
            examples.forEach { x ->
                put(JSONObject().apply {
                    putOpt("ref", x.ref.nullIfBlank())
                    putOpt("src", x.src.nullIfBlank())
                    putOpt("fr", x.fr.nullIfBlank())
                })
            }
        })
        // Dans les fichiers sources, les équivalences ne sont que des id
        if (equiv.isNotEmpty()) put("equiv", JSONArray(equiv.map { it.id }))
        putOpt("notes", notes.nullIfBlank())
        putOpt("source", source.nullIfBlank())
        if (verified) put("verified", true)
        putOpt("author", author.nullIfBlank())
    }

    companion object {
        fun fromJson(o: JSONObject): Entry = Entry(
            id = o.getString("id"),
            lemma = o.getString("lemma"),
            lang = Lang.of(o.optString("lang")),
            dialect = o.str("dialect"),
            root = o.str("root"),
            pos = o.str("pos"),
            binyan = o.str("binyan"),
            gender = o.str("gender"),
            translit = o.str("translit"),
            forms = o.optJSONArray("forms").strings(),
            senses = o.optJSONArray("senses").objects().map {
                Sense(it.optString("fr"), it.str("ctx"), it.str("note"))
            },
            examples = o.optJSONArray("examples").objects().map {
                Example(it.str("ref"), it.str("src"), it.str("fr"))
            },
            equiv = o.optJSONArray("equiv")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    when (val v = arr.opt(i)) {
                        is JSONObject -> EquivRef(v.getString("id"), v.str("lemma"), v.str("lang"))
                        is String -> EquivRef(v)
                        else -> null
                    }
                }
            } ?: emptyList(),
            notes = o.str("notes"),
            source = o.str("source"),
            verified = o.optBoolean("verified", false),
            author = o.str("author"),
        )
    }
}

/** Ligne de résultat de recherche. */
data class EntrySummary(
    val uid: String,
    val lemma: String,
    val lang: Lang,
    val root: String?,
    val pos: String?,
    val gloss: String,
    val verified: Boolean,
    val local: LocalStatus? = null,
)

fun Entry.summary(local: LocalStatus? = null) =
    EntrySummary(id, lemma, lang, root, pos, gloss, verified, local)

private fun String?.nullIfBlank() = this?.trim()?.takeIf { it.isNotEmpty() }

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).trim().takeIf { it.isNotEmpty() } else null

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
