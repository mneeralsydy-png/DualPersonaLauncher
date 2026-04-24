package com.dualpersona.system.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.dualpersona.system.DualPersonaApp
import com.dualpersona.system.R
import com.dualpersona.system.core.DataGuard
import com.dualpersona.system.core.SystemUserManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.*

/**
 * SystemService - Main background service
 *
 * Core responsibilities:
 * - Monitors user state changes
 * - Manages user profile configurations
 * - Ensures services stay running
 * - Handles periodic maintenance tasks
 * - Coordinates between SystemUserManager and DataGuard
 *
 * Runs as a foreground service with a persistent notification.
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
        startForegroundNotification()

        val prefs = PreferencesManager(this)
        prefs.setServiceStarted(true)

        // Start monitoring
        startMonitoring()

        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        PreferencesManager(this).setServiceStarted(false)
        SecurityLog.log(this, "INFO", "system_service", "System service destroyed")
    }

    // ===== Foreground Notification =====

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

    // ===== Monitoring Loop =====

    private fun startMonitoring() {
        val prefs = PreferencesManager(this)
        val userManager = SystemUserManager(this)

        // Periodic status check (every 30 seconds)
        serviceScope.launch {
            while (isRunning && isActive) {
                try {
                    // Verify secondary user exists
                    if (prefs.isSetupComplete() && !userManager.hasSecondaryUser()) {
                        SecurityLog.log(this@SystemService, "WARNING", "monitor",
                            "Secondary user missing - may have been removed")
                    }

                    // Check stealth mode consistency
                    val stealthManager = com.dualpersona.system.core.StealthManager(this@SystemService)
                    if (prefs.isStealthModeEnabled() && stealthManager.isIconVisible()) {
                        stealthManager.enableStealthMode()
                    }

                    delay(30_000) // 30 second interval
                } catch (e: Exception) {
                    SecurityLog.log(this@SystemService, "ERROR", "monitor",
                        "Monitor error: ${e.message}")
                    delay(60_000) // Back off on error
                }
            }
        }
    }
}
