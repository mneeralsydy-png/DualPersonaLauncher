package com.dualpersona.system

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.dualpersona.system.core.StealthManager
import com.dualpersona.system.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DualPersonaApp : Application() {

    companion object {
        const val CHANNEL_SYSTEM = "dual_persona_system"
        const val CHANNEL_GUARD = "dual_persona_guard"
        const val CHANNEL_ALERT = "dual_persona_alert"

        const val NOTIFICATION_ID_SYSTEM = 1001
        const val NOTIFICATION_ID_GUARD = 1002

        lateinit var appScope: CoroutineScope
            private set

        lateinit var prefs: PreferencesManager
            private set
    }

    override fun onCreate() {
        super.onCreate()

        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        prefs = PreferencesManager(this)

        createNotificationChannels()

        // Auto-start services if setup is complete and not in stealth
        if (prefs.isSetupComplete() && !prefs.isStealthModeEnabled()) {
            StealthManager.restoreAppIcon(this)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Pre-init for direct boot support
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Manages dual persona environments"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_GUARD,
                    "Security Guard",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Monitors data isolation and security"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ALERT,
                    "Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Important security notifications"
                }
            )

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(channels)
        }
    }
}
