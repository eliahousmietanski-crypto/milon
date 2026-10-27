package com.milon.app.data

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation du texte pour la recherche.
 *
 * Ces règles DOIVENT rester identiques à tools/textnorm.py : la base est indexée
 * par ce script, et la saisie de l'utilisateur est normalisée ici.
 * tools/norm_vectors.json sert de test commun (voir TextNormTest).
 */
object TextNorm {
    private val finals = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')
    private val ligatures = mapOf('װ' to "וו", 'ױ' to "וי", 'ײ' to "יי")
    private val spaces = Regex(" +")

    val frStopwords: Set<String> = """a au aux avec ce ces cet cette d de des du en et l la le les leur leurs
        lui n ne ou par pas pour qu que qui s sa se ses son sur un une y etc qqch
        qqn quelque chose quelqu""".split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

    fun isHebrewLetter(c: Char) = c in 'א'..'ת'

    fun hasHebrew(s: String) = s.any { isHebrewLetter(it) || it in ligatures }

    /** Lettres seules, sans nikoud ni téamim, finales ramenées à la forme normale. */
    fun hebrewPlain(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKD)
        val sb = StringBuilder(n.length)
        for (c in n) {
            when {
                isHebrewLetter(c) -> sb.append(finals[c] ?: c)
                c in ligatures -> sb.append(ligatures[c])
                c.isWhitespace() || c == '־' || c == '-' -> sb.append(' ')
            }
        }
        return sb.toString().replace(spaces, " ").trim()
    }

    /** Retire ו et י (sauf en début de mot) : écriture pleine et défective se retrouvent. */
    fun hebrewSkeleton(plain: String): String =
        plain.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w ->
            w.first() + w.drop(1).filter { it != 'ו' && it != 'י' }
        }

    fun latinFold(s: String): String {
        val lower = s.lowercase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
        val n = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val sb = StringBuilder(n.length)
        for (c in n) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(if (c in 'a'..'z' || c in '0'..'9') c else ' ')
        }
        return sb.toString().replace(spaces, " ").trim()
    }

    fun frWords(s: String, keepStopwords: Boolean = false): List<String> {
        val words = latinFold(s).split(' ').filter { it.isNotEmpty() }
        return if (keepStopwords) words else words.filter { it !in frStopwords }
    }

    /** Racine affichée avec des séparateurs : ברא → ב־ר־א */
    fun displayRoot(root: String): String {
        val letters = Normalizer.normalize(root, Normalizer.Form.NFKD).filter { isHebrewLetter(it) }
        return if (letters.length in 2..5) letters.toList().joinToString("־") else root
    }
}
