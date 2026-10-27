package com.milon.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Vérifie que TextNorm.kt donne exactement les mêmes résultats que tools/textnorm.py. */
class TextNormTest {
    @Test
    fun sameResultsAsPython() {
        val file = listOf(File("../tools/norm_vectors.json"), File("tools/norm_vectors.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val input = c.getString("in")
            assertEquals("plain($input)", c.getString("plain"), TextNorm.hebrewPlain(input))
            assertEquals("skeleton($input)", c.getString("skeleton"), TextNorm.hebrewSkeleton(TextNorm.hebrewPlain(input)))
            assertEquals("latin($input)", c.getString("latin"), TextNorm.latinFold(input))
            val fr = c.getJSONArray("fr").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals("fr($input)", fr, TextNorm.frWords(input))
        }
    }

    @Test
    fun hebrewQueryMatchesFullSpelling() {
        val q = Query.parse("בורא") as Query.Hebrew
        val key = TextNorm.hebrewSkeleton(TextNorm.hebrewPlain("בֹּרֵא"))
        assertEquals(20, Scoring.hebrew(q, key, SearchKind.SKELETON, 0))
    }
}
