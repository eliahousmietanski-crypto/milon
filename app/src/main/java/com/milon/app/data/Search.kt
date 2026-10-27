package com.milon.app.data

/** Types de clés de recherche (mêmes valeurs que dans tools/build_db.py). */
object SearchKind {
    const val LEMMA = 1
    const val SKELETON = 2
    const val FORM = 3
    const val ROOT = 4
    const val FR = 5
    const val TRANSLIT = 6
}

/** Une clé indexée : texte normalisé, type, poids (plus petit = meilleur). */
data class SearchKey(val key: String, val kind: Int, val weight: Int)

/** Une requête normalisée une fois pour toutes. */
sealed class Query {
    data class Hebrew(val plain: String, val skeleton: String) : Query()

    /** Le dernier mot est cherché en préfixe (on tape encore), les autres en entier. */
    data class Latin(val words: List<String>) : Query()

    companion object {
        fun parse(raw: String): Query? {
            if (TextNorm.hasHebrew(raw)) {
                val plain = TextNorm.hebrewPlain(raw)
                return if (plain.isEmpty()) null else Hebrew(plain, TextNorm.hebrewSkeleton(plain))
            }
            val words = TextNorm.frWords(raw).ifEmpty { TextNorm.frWords(raw, keepStopwords = true) }
            return if (words.isEmpty()) null else Latin(words)
        }
    }
}

/** Score d'une clé pour une requête : plus petit = plus pertinent, null = pas de correspondance. */
object Scoring {
    fun hebrew(q: Query.Hebrew, key: String, kind: Int, weight: Int): Int? {
        val target = if (kind == SearchKind.SKELETON) q.skeleton else q.plain
        if (!key.startsWith(target)) return null
        val exact = key.length == target.length
        val extra = key.length - target.length
        return when (kind) {
            SearchKind.LEMMA -> if (exact) 0 else 100 + extra
            SearchKind.FORM -> if (exact) 10 else 120 + extra
            SearchKind.SKELETON -> if (exact) 20 + weight * 5 else 140 + extra
            SearchKind.ROOT -> if (exact) 30 else 200 + extra
            else -> null
        }
    }

    /** Score pour un mot de la requête latine ; [prefix] pour le dernier mot. */
    fun latin(word: String, prefix: Boolean, key: String, kind: Int, weight: Int): Int? {
        val exact = key == word
        if (!exact && !(prefix && key.startsWith(word))) return null
        val extra = key.length - word.length
        return when (kind) {
            SearchKind.FR -> if (exact) weight else 50 + weight + extra
            SearchKind.TRANSLIT -> if (exact) 2 else 60 + extra
            else -> null
        }
    }
}

/** Génère les clés d'une entrée (portage de search_keys() de build_db.py, pour les ajouts locaux). */
object KeyGen {
    fun keys(e: Entry): List<SearchKey> {
        val keys = ArrayList<SearchKey>()
        val lemma = TextNorm.hebrewPlain(e.lemma)
        keys += SearchKey(lemma, SearchKind.LEMMA, 0)
        keys += SearchKey(TextNorm.hebrewSkeleton(lemma), SearchKind.SKELETON, 0)
        for (form in e.forms) {
            val p = TextNorm.hebrewPlain(form)
            if (p.isNotEmpty()) {
                keys += SearchKey(p, SearchKind.FORM, 0)
                keys += SearchKey(TextNorm.hebrewSkeleton(p), SearchKind.SKELETON, 1)
            }
        }
        e.root?.let { keys += SearchKey(TextNorm.hebrewPlain(it).replace(" ", ""), SearchKind.ROOT, 0) }
        e.senses.forEachIndexed { i, s ->
            val words = TextNorm.frWords(s.fr)
            words.forEachIndexed { pos, w ->
                val weight = minOf(i, 5) * 4 + minOf(pos, 3) + (if (words.size <= 2) 0 else 1)
                keys += SearchKey(w, SearchKind.FR, weight)
            }
        }
        e.translit?.let { t ->
            TextNorm.latinFold(t).split(' ').filter { it.isNotEmpty() }
                .forEach { keys += SearchKey(it, SearchKind.TRANSLIT, 0) }
        }
        return keys.filter { it.key.isNotEmpty() }
    }

    /** Score d'une entrée locale pour la requête, ou null si elle ne correspond pas. */
    fun score(keys: List<SearchKey>, q: Query): Int? = when (q) {
        is Query.Hebrew -> keys.mapNotNull { Scoring.hebrew(q, it.key, it.kind, it.weight) }.minOrNull()
        is Query.Latin -> {
            var total = 0
            for ((i, w) in q.words.withIndex()) {
                val best = keys.mapNotNull {
                    Scoring.latin(w, i == q.words.lastIndex, it.key, it.kind, it.weight)
                }.minOrNull() ?: return null
                total += best
            }
            total
        }
    }
}
