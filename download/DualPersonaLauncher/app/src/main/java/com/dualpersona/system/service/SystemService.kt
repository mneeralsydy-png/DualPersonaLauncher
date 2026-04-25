package com.dualpersona.system.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.dualpersona.system.DualPersonaApp
import com.dualpersona.system.R
import com.dualpersona.system.core.StealthManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.*

/**
 * SystemService - خدمة النظام الرئيسية
 *
 * آمنة 100% - لا تستخدم أي API مخفي.
 */
class SystemService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        SecurityLog.log(this, "INFO", "system_service", "System service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundNotification()

            val prefs = PreferencesManager(this)
            prefs.setServiceStarted(true)

            startMonitoring()
        } catch (e: Exception) {
            SecurityLog.log(this, "ERROR", "system_service", "Start error: ${e.message}")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        try {
            PreferencesManager(this).setServiceStarted(false)
        } catch (e: Exception) {}
        SecurityLog.log(this, "INFO", "system_service", "System service destroyed")
    }

    private fun startForegroundNotification() {
        val notification = createNotification()
        startForeground(
            DualPersonaApp.NOTIFICATION_ID_SYSTEM,
            notification
        )
    }

    private fun createNotification(): Notification {
        val channelId = DualPersonaApp.CHANNEL_SYSTEM

        val intent = Intent(this, com.dualpersona.system.ui.setup.SetupWizardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_system_title))
                .setContentText(getString(R.string.notification_system_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notification_system_title))
                .setContentText(getString(R.string.notification_system_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isRunning && isActive) {
                try {
                    val prefs = PreferencesManager(this@SystemService)

                    // التحقق من وضع التخفي
                    if (prefs.isSetupComplete()) {
                        try {
                            val stealthManager = StealthManager(this@SystemService)
                            if (prefs.isStealthModeEnabled() && stealthManager.isIconVisible()) {
                                stealthManager.enableStealthMode()
                            }
                        } catch (e: Exception) {
                            // تجاهل خطأ التخفي
                        }
                    }

                    delay(30_000)
                } catch (e: Exception) {
                    delay(60_000)
                }
            }
        }
    }
}
