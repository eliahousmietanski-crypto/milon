package com.milon.app

import android.app.Application
import com.milon.app.data.DictionaryDb
import com.milon.app.data.Repository
import com.milon.app.data.Settings
import com.milon.app.data.UpdateManager
import com.milon.app.data.UserStore

class MilonApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var repo: Repository
        private set
    lateinit var updates: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = Repository(DictionaryDb(this, settings), UserStore(this), settings)
        updates = UpdateManager(this, repo, settings)
    }
}
