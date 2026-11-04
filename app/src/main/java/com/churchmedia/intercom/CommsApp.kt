package com.churchmedia.intercom

import android.app.Application
import com.churchmedia.intercom.data.SettingsStore
import com.churchmedia.intercom.session.SessionController

class CommsApp : Application() {

    lateinit var controller: SessionController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = SessionController(
            context = applicationContext,
            settingsStore = SettingsStore(applicationContext),
        )
    }
}
