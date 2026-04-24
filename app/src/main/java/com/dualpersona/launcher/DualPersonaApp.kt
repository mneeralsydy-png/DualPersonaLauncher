package com.dualpersona.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dualpersona.launcher.data.AppDatabase
import com.dualpersona.launcher.engine.EnvironmentEngine
import com.dualpersona.launcher.security.EncryptionManager
import com.dualpersona.launcher.utils.PreferencesManager

class DualPersonaApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var encryptionManager: EncryptionManager
        private set
    lateinit var environmentEngine: EnvironmentEngine
        private set
    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesManager = PreferencesManager(this)
        encryptionManager = EncryptionManager(this)
        database = AppDatabase.getInstance(this)
        environmentEngine = EnvironmentEngine(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_SECURITY,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for failed login attempts and security events"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_SERVICE,
                "Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service notifications"
            },
            NotificationChannel(
                CHANNEL_BACKUP,
                "Backup & Restore",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Backup and restore notifications"
            }
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(channels)
    }

    companion object {
        const val CHANNEL_SECURITY = "channel_security"
        const val CHANNEL_SERVICE = "channel_service"
        const val CHANNEL_BACKUP = "channel_backup"

        @Volatile
        private lateinit var instance: DualPersonaApp

        fun getInstance(): DualPersonaApp = instance
    }
}
