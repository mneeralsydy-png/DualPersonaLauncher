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
    }

    override fun onCreate() {
        super.onCreate()
        try {
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            createNotificationChannels()

            val prefs = PreferencesManager(this)
            if (prefs.isSetupComplete() && !prefs.isStealthModeEnabled()) {
                StealthManager.restoreAppIcon(this)
            }
        } catch (e: Exception) {
            // Never crash on app startup
        }
    }

    override fun attachBaseContext(base: Context) {
        try {
            val arabicLocale = Locale("ar")
            val config = Configuration(base.resources.configuration)
            config.setLocale(arabicLocale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(arabicLocale))
            }
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
        } catch (e: Exception) {
            super.attachBaseContext(base)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            val arabicLocale = Locale("ar")
            val config = Configuration(newConfig)
            config.setLocale(arabicLocale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(arabicLocale))
            }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channels = listOf(
                    NotificationChannel(CHANNEL_SYSTEM, "خدمة النظام", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(CHANNEL_GUARD, "الحماية الأمنية", NotificationManager.IMPORTANCE_MIN),
                    NotificationChannel(CHANNEL_ALERT, "تنبيهات أمنية", NotificationManager.IMPORTANCE_HIGH)
                )
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannels(channels)
            } catch (e: Exception) {}
        }
    }
}
