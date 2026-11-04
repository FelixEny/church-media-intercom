package com.churchmedia.intercom.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.churchmedia.intercom.CommsApp
import com.churchmedia.intercom.MainActivity
import com.churchmedia.intercom.R
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.core.model.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and visible while a session is running.
 *
 * Android 14+ requires the microphone foreground service type to be declared and to
 * be started while the app is in the foreground — both are handled here.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(describePlaceholder())
        observeSession()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun observeSession() {
        if (observing) return
        observing = true
        val controller = (application as CommsApp).controller

        scope.launch {
            controller.state.collectLatest { state ->
                if (state.mode == Mode.IDLE) {
                    ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(describe(state)))
            }
        }
    }

    private fun startInForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun describe(state: AppState): String = when (state.mode) {
        Mode.DIRECTOR -> {
            val count = state.listeners.size
            when {
                state.directorMicLive -> "You are live · $count listening"
                state.holderName != null -> "${state.holderName} is replying"
                else -> "Hosting · $count member${if (count == 1) "" else "s"}"
            }
        }
        Mode.MEMBER -> when (state.link) {
            LinkState.CONNECTED -> if (state.myMicLive) "You are live" else "Connected to ${state.hostName.ifBlank { "director" }}"
            LinkState.RECONNECTING -> "Reconnecting…"
            LinkState.CONNECTING -> "Connecting…"
            else -> "Waiting for the director"
        }
        Mode.IDLE -> describePlaceholder()
    }

    private fun describePlaceholder(): String = "Starting intercom…"

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Comms is on")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.session_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while a Comms session is running"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "comms_session"
        private const val NOTIFICATION_ID = 4821

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SessionService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
