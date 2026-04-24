package com.dualpersona.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.activities.LauncherHomeActivity

/**
 * Background service that manages environment state
 * and ensures proper isolation is maintained.
 */
class EnvironmentService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val app = DualPersonaApp.getInstance()
        val environment = app.environmentEngine.getCurrentEnvironment()

        val notificationIntent = Intent(this, LauncherHomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, DualPersonaApp.CHANNEL_SERVICE)
            .setContentTitle("Dual Space: $environment")
            .setContentText("Environment service is running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
    }
}
