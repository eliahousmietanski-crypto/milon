package com.milon.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.milon.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Échange des propositions par fichier, et publication sur GitHub par l'éditeur.
 *
 * Fichier de propositions :
 *   {"format": "milon-propositions", "version": 1, "author": "...", "created": "...", "entries": [...]}
 */
object Contributions {
    private const val FORMAT = "milon-propositions"

    class BadFile(message: String) : Exception(message)

    /** Crée le fichier et renvoie l'Intent de partage (WhatsApp, e-mail…). */
    suspend fun exportIntent(context: Context, entries: List<Entry>, author: String): Intent =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date())
            val file = File(dir, "milon-propositions_$stamp.json")
            val json = JSONObject().apply {
                put("format", FORMAT)
                put("version", 1)
                put("author", author)
                put("created", isoNow())
                put("entries", JSONArray(entries.map { it.toJson() }))
            }
            file.writeText(json.toString(2))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Propositions pour le dictionnaire Milon")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Voici ${entries.size} proposition(s) pour le dictionnaire Milon. " +
                        "Ouvrez ce fichier avec l'application Milon pour les valider."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, "Envoyer les propositions")
        }

    /** Lit un fichier de propositions. */
    suspend fun read(context: Context, uri: Uri): Pair<String?, List<Entry>> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw BadFile("Impossible d'ouvrir le fichier.")
        val o = try {
            JSONObject(text)
        } catch (_: org.json.JSONException) {
            throw BadFile("Ce fichier n'est pas un fichier de propositions Milon.")
        }
        if (o.optString("format") != FORMAT) throw BadFile("Ce fichier n'est pas un fichier de propositions Milon.")
        val author = o.optString("author").ifBlank { null }
        val arr = o.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                try {
                    Entry.fromJson(it).let { e -> if (e.author == null) e.copy(author = author) else e }
                } catch (_: org.json.JSONException) {
                    null
                }
            }
        }.filter { it.lemma.isNotBlank() && it.senses.any { s -> s.fr.isNotBlank() } }
        author to entries
    }

    /**
     * Publie les entrées validées dans le dépôt GitHub (data/contrib/). La construction
     * automatique produit ensuite une nouvelle version du dictionnaire pour tous.
     */
    suspend fun publish(token: String, entries: List<Entry>, author: String): String = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val path = "data/contrib/$stamp.jsonl"
        val content = entries.joinToString("\n", postfix = "\n") { it.toJson().toString() }
        val body = JSONObject().apply {
            put("message", "Ajouts validés depuis l'application : ${entries.size} entrée(s)" +
                if (author.isNotBlank()) " ($author)" else "")
            put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
        }
        val conn = URL("https://api.github.com/repos/${BuildConfig.REPO}/contents/$path")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Milon-Android/${BuildConfig.VERSION_NAME}")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            when (val code = conn.responseCode) {
                200, 201 -> path
                401 -> throw IOException("Jeton GitHub refusé : vérifiez-le dans les Réglages.")
                403, 404 -> throw IOException("Ce jeton n'a pas le droit d'écrire dans le dépôt ${BuildConfig.REPO}.")
                else -> throw IOException("Erreur GitHub ($code).")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
