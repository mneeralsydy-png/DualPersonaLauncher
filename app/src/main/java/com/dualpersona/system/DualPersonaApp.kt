package com.dualpersona.system

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.dualpersona.system.core.StealthManager
import com.dualpersona.system.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale

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

        if (prefs.isSetupComplete() && !prefs.isStealthModeEnabled()) {
            StealthManager.restoreAppIcon(this)
        }
    }

    override fun attachBaseContext(base: Context) {
        // Force Arabic locale for the entire app
        val arabicLocale = Locale("ar")
        val config = Configuration(base.resources.configuration)
        config.setLocale(arabicLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(arabicLocale))
        }
        val context = base.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-force Arabic on configuration changes
        val arabicLocale = Locale("ar")
        val config = Configuration(newConfig)
        config.setLocale(arabicLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(arabicLocale))
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "خدمة النظام",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "إدارة بيئات الهوية المزدوجة"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_GUARD,
                    "الحماية الأمنية",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "مراقبة عزل البيانات والأمان"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ALERT,
                    "تنبيهات أمنية",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات أمنية مهمة"
                }
            )

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(channels)
        }
    }
}
