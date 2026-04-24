package com.dualpersona.system.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.dualpersona.system.DualPersonaApp
import com.dualpersona.system.core.DataGuard
import com.dualpersona.system.core.StealthManager
import com.dualpersona.system.core.SystemUserManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.*

/**
 * GuardService - Security monitoring service
 *
 * Runs continuously in background to:
 * - Periodically verify data isolation between users
 * - Detect suspicious cross-user activity
 * - Monitor for security threats
 * - Log security events
 * - Alert on policy violations
 *
 * Uses a persistent foreground notification to prevent
 * being killed by the system.
 */
class GuardService : Service() {

    private val guardScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    // How often to run full isolation check (5 minutes)
    private val ISOLATION_CHECK_INTERVAL = 5 * 60 * 1000L

    // How often to check for suspicious activity (2 minutes)
    private val ACTIVITY_CHECK_INTERVAL = 2 * 60 * 1000L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        SecurityLog.log(this, "INFO", "guard_service", "Guard service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        startGuarding()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        guardScope.cancel()
        SecurityLog.log(this, "INFO", "guard_service", "Guard service destroyed")
    }

    // ===== Foreground Notification =====

    private fun startForegroundNotification() {
        val notification = createNotification()
        startForeground(DualPersonaApp.NOTIFICATION_ID_GUARD, notification)
    }

    private fun createNotification(): Notification {
        val channelId = DualPersonaApp.CHANNEL_GUARD

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Security Guard")
                .setContentText("Monitoring data isolation")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Security Guard")
                .setContentText("Monitoring")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build()
        }
    }

    // ===== Guard Loop =====

    private fun startGuarding() {
        val prefs = PreferencesManager(this)
        val dataGuard = DataGuard(this)

        // Isolation check loop
        guardScope.launch {
            while (isRunning && isActive) {
                if (prefs.isSetupComplete()) {
                    try {
                        val report = dataGuard.verifyIsolation()
                        if (!report.allPassed) {
                            SecurityLog.log(this@GuardService, "WARNING", "guard_isolation",
                                "Isolation check failed")
                        }
                    } catch (e: Exception) {
                        SecurityLog.log(this@GuardService, "ERROR", "guard_isolation",
                            "Check error: ${e.message}")
                    }
                }
                delay(ISOLATION_CHECK_INTERVAL)
            }
        }

        // Activity monitoring loop
        guardScope.launch {
            while (isRunning && isActive) {
                if (prefs.isSetupComplete()) {
                    try {
                        monitorSuspiciousActivity()
                    } catch (e: Exception) {
                        // Silent fail for monitoring
                    }
                }
                delay(ACTIVITY_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Monitor for suspicious cross-user activities
     */
    private fun monitorSuspiciousActivity() {
        val prefs = PreferencesManager(this)
        val suspiciousCount = prefs.getSuspiciousActivityCount()

        if (suspiciousCount > 0) {
            SecurityLog.log(this, "INFO", "guard_activity",
                "Suspicious activity count: $suspiciousCount")

            // If too many suspicious events, log a critical alert
            if (suspiciousCount >= 10) {
                SecurityLog.log(this, "CRITICAL", "guard_alert",
                    "High number of suspicious activities detected")

                // Send high-priority notification
                sendSecurityAlert("Security Alert",
                    "Multiple suspicious activities detected. Check security logs.")
            }
        }
    }

    /**
     * Send a security alert notification
     */
    private fun sendSecurityAlert(title: String, message: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = DualPersonaApp.CHANNEL_ALERT

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .build()
            }

            notificationManager.notify(9999, notification)
        } catch (e: Exception) {
            SecurityLog.log(this, "ERROR", "guard_notification",
                "Failed to send alert: ${e.message}")
        }
    }
}
