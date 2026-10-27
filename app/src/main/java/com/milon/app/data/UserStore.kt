package com.milon.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** État d'une entrée ajoutée ou corrigée sur ce téléphone. */
enum class LocalStatus(val label: String) {
    /** Mon ajout, pas encore validé par l'éditeur. */
    MINE("Mon ajout"),
    /** Proposition reçue d'un autre utilisateur, en attente de validation. */
    REVIEW("À valider"),
    /** Validée par l'éditeur, prête à être publiée. */
    APPROVED("À publier"),
    /** Publiée : sera dans la prochaine mise à jour du dictionnaire. */
    PUBLISHED("Publiée");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: MINE
    }
}

data class LocalEntry(
    val entry: Entry,
    val status: LocalStatus,
    val updated: Long,
    val exported: Boolean,
    /** L'entrée corrige une entrée existante du dictionnaire (même id). */
    val isCorrection: Boolean,
)

/** Données propres à l'utilisateur : favoris, historique, ajouts et propositions. */
class UserStore(context: Context) : SQLiteOpenHelper(context, "user.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites (uid TEXT PRIMARY KEY, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history (uid TEXT PRIMARY KEY, ts INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE local_entries (
                uid TEXT PRIMARY KEY,
                json TEXT NOT NULL,
                status TEXT NOT NULL,
                updated INTEGER NOT NULL,
                exported INTEGER NOT NULL DEFAULT 0,
                correction INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // --- Favoris -------------------------------------------------------------

    fun isFavorite(uid: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM favorites WHERE uid = ?", arrayOf(uid)).use { it.moveToFirst() }

    fun setFavorite(uid: String, fav: Boolean) {
        if (fav) {
            writableDatabase.insertWithOnConflict(
                "favorites", null,
                ContentValues().apply { put("uid", uid); put("ts", System.currentTimeMillis()) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } else {
            writableDatabase.delete("favorites", "uid = ?", arrayOf(uid))
        }
    }

    fun favorites(): List<String> = uids("SELECT uid FROM favorites ORDER BY ts DESC")

    // --- Historique ----------------------------------------------------------

    fun addHistory(uid: String) {
        val db = writableDatabase
        db.insertWithOnConflict(
            "history", null,
            ContentValues().apply { put("uid", uid); put("ts", System.currentTimeMillis()) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        db.execSQL("DELETE FROM history WHERE uid NOT IN (SELECT uid FROM history ORDER BY ts DESC LIMIT 200)")
    }

    fun history(limit: Int = 200): List<String> = uids("SELECT uid FROM history ORDER BY ts DESC LIMIT $limit")

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    private fun uids(sql: String): List<String> =
        readableDatabase.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    // --- Ajouts locaux -------------------------------------------------------

    fun localEntries(): List<LocalEntry> =
        readableDatabase.rawQuery(
            "SELECT json, status, updated, exported, correction FROM local_entries ORDER BY updated DESC", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalEntry(
                            entry = Entry.fromJson(JSONObject(c.getString(0))),
                            status = LocalStatus.of(c.getString(1)),
                            updated = c.getLong(2),
                            exported = c.getInt(3) == 1,
                            isCorrection = c.getInt(4) == 1,
                        )
                    )
                }
            }
        }

    fun saveLocal(entry: Entry, status: LocalStatus, isCorrection: Boolean) {
        writableDatabase.insertWithOnConflict(
            "local_entries", null,
            ContentValues().apply {
                put("uid", entry.id)
                put("json", entry.toJson().toString())
                put("status", status.name)
                put("updated", System.currentTimeMillis())
                put("exported", 0)
                put("correction", if (isCorrection) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun setStatus(uids: Collection<String>, status: LocalStatus) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            uids.forEach {
                val values = ContentValues().apply {
                    put("status", status.name)
                    put("updated", System.currentTimeMillis())
                }
                db.update("local_entries", values, "uid = ?", arrayOf(it))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markExported(uids: Collection<String>) {
        val db = writableDatabase
        uids.forEach {
            db.update("local_entries", ContentValues().apply { put("exported", 1) }, "uid = ?", arrayOf(it))
        }
    }

    fun deleteLocal(uid: String) {
        writableDatabase.delete("local_entries", "uid = ?", arrayOf(uid))
    }
}
