package com.dualpersona.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.activities.LockScreenActivity

/**
 * Foreground service that keeps the lock screen active
 * and monitors screen state for auto-lock functionality.
 */
class LockScreenService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_LOCK -> showLockScreen()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showLockScreen() {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        startActivity(lockIntent)
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, LockScreenActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, DualPersonaApp.CHANNEL_SERVICE)
            .setContentTitle("Dual Space Active")
            .setContentText("Secure environment is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW_LOCK = "action_show_lock"
        const val ACTION_STOP = "action_stop"
        private const val NOTIFICATION_ID = 2001
    }
}
