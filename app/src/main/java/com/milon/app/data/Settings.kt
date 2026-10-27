package com.milon.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Préférences de l'utilisateur. Les valeurs affichées par l'interface sont exposées en StateFlow. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("milon", Context.MODE_PRIVATE)

    private val _author = MutableStateFlow(prefs.getString("author", "") ?: "")
    val author: StateFlow<String> = _author.asStateFlow()
    fun setAuthor(v: String) { prefs.edit { putString("author", v.trim()) }; _author.value = v.trim() }

    private val _editor = MutableStateFlow(prefs.getBoolean("editor", false))
    val editorMode: StateFlow<Boolean> = _editor.asStateFlow()
    fun setEditorMode(v: Boolean) { prefs.edit { putBoolean("editor", v) }; _editor.value = v }

    private val _token = MutableStateFlow(prefs.getString("github_token", "") ?: "")
    val githubToken: StateFlow<String> = _token.asStateFlow()
    fun setGithubToken(v: String) { prefs.edit { putString("github_token", v.trim()) }; _token.value = v.trim() }

    private val _hebrewScale = MutableStateFlow(prefs.getFloat("hebrew_scale", 1f))
    val hebrewScale: StateFlow<Float> = _hebrewScale.asStateFlow()
    fun setHebrewScale(v: Float) { prefs.edit { putFloat("hebrew_scale", v) }; _hebrewScale.value = v }

    private val _hebrewKeyboard = MutableStateFlow(prefs.getBoolean("hebrew_keyboard", true))
    /** Ouvrir le clavier hébreu intégré par défaut dans la recherche. */
    val hebrewKeyboardDefault: StateFlow<Boolean> = _hebrewKeyboard.asStateFlow()
    fun setHebrewKeyboardDefault(v: Boolean) { prefs.edit { putBoolean("hebrew_keyboard", v) }; _hebrewKeyboard.value = v }

    var dataVersion: Int
        get() = prefs.getInt("data_version", 0)
        set(v) = prefs.edit { putInt("data_version", v) }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0)
        set(v) = prefs.edit { putLong("last_update_check", v) }
}
