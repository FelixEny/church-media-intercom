package com.churchmedia.intercom.session

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
