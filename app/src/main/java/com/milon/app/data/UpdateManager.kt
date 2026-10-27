package com.milon.app.data

import android.content.Context
import com.milon.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Mises à jour : le manifeste de la dernière release GitHub indique la version des
 * données et de l'application. Sans connexion, rien ne se passe et tout reste utilisable.
 */
class UpdateManager(
    private val context: Context,
    private val repo: Repository,
    private val settings: Settings,
) {
    data class Manifest(
        val dataVersion: Int,
        val entries: Int,
        val dbUrl: String,
        val dbSha256: String,
        val appVersionCode: Int,
        val appVersionName: String?,
        val apkUrl: String?,
    )

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Available(val manifest: Manifest, val newData: Boolean, val newApp: Boolean) : State()
        data class Downloading(val progress: Float) : State()
        data class Done(val entries: Int) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val manifestUrl = "https://github.com/${BuildConfig.REPO}/releases/latest/download/manifest.json"

    /** Vérification discrète au démarrage, au plus toutes les 6 heures. */
    suspend fun checkInBackground() {
        if (System.currentTimeMillis() - settings.lastUpdateCheck < 6 * 3600_000L) return
        check(silent = true)
    }

    suspend fun check(silent: Boolean = false) {
        if (_state.value is State.Downloading) return
        _state.value = State.Checking
        _state.value = try {
            val m = fetchManifest()
            settings.lastUpdateCheck = System.currentTimeMillis()
            val newData = m.dataVersion > repo.dict.info.dataVersion
            val newApp = m.appVersionCode > BuildConfig.VERSION_CODE && m.apkUrl != null
            if (newData || newApp) State.Available(m, newData, newApp) else State.UpToDate
        } catch (e: IOException) {
            if (silent) State.Idle else State.Error("Pas de connexion, ou serveur injoignable.")
        } catch (e: org.json.JSONException) {
            if (silent) State.Idle else State.Error("Réponse du serveur illisible.")
        }
    }

    private suspend fun fetchManifest(): Manifest = withContext(Dispatchers.IO) {
        val text = open(manifestUrl).inputStream.use { it.readBytes().decodeToString() }
        val o = JSONObject(text)
        Manifest(
            dataVersion = o.getInt("dataVersion"),
            entries = o.optInt("entries"),
            dbUrl = o.getString("dbUrl"),
            dbSha256 = o.getString("dbSha256"),
            appVersionCode = o.optInt("appVersionCode", 0),
            appVersionName = o.optString("appVersionName").ifBlank { null },
            apkUrl = o.optString("apkUrl").ifBlank { null },
        )
    }

    /** Télécharge et installe la nouvelle base. */
    suspend fun downloadData(m: Manifest) {
        _state.value = State.Downloading(0f)
        val gz = File(context.cacheDir, "milon.db.gz")
        val tmp = File(context.filesDir, "milon.db.download")
        try {
            withContext(Dispatchers.IO) {
                val conn = open(m.dbUrl)
                val total = conn.contentLengthLong.takeIf { it > 0 }
                val digest = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    gz.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var done = 0L
                        var lastShown = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (total != null) {
                                val p = done.toFloat() / total
                                if (p - lastShown > 0.01f) { lastShown = p; _state.value = State.Downloading(p) }
                            }
                        }
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha.equals(m.dbSha256, ignoreCase = true)) throw IOException("Fichier corrompu (empreinte incorrecte)")
                GZIPInputStream(gz.inputStream().buffered()).use { input ->
                    tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
                }
                repo.dict.replaceWith(tmp)
            }
            repo.onDictionaryUpdated()
            _state.value = State.Done(repo.dict.info.entries)
        } catch (e: Exception) {
            _state.value = State.Error("Échec de la mise à jour : ${e.message ?: e.javaClass.simpleName}")
        } finally {
            gz.delete()
            tmp.delete()
        }
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        // Les téléchargements GitHub redirigent vers un autre domaine : on suit à la main
        repeat(5) {
            val c = current.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Milon-Android/${BuildConfig.VERSION_NAME}")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirection invalide")
                c.disconnect()
                current = URL(current, loc)
            } else if (code == 200) {
                return c
            } else {
                c.disconnect()
                throw IOException("HTTP $code")
            }
        }
        throw IOException("Trop de redirections")
    }
}
