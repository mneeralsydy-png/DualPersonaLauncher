package com.dualpersona.system.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

class StealthManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val prefs: PreferencesManager = PreferencesManager(context)

    companion object {
        const val DEFAULT_SECRET_CODE = "7890"
        const val LAUNCHER_COMPONENT = "com.dualpersona.system.ui.setup.SetupWizardActivity"
        const val NOTIFICATION_ID_SYSTEM = 1001
        const val NOTIFICATION_ID_GUARD = 1002

        fun restoreAppIcon(context: Context) {
            try {
                val prefs = PreferencesManager(context)
                if (!prefs.isStealthModeEnabled()) {
                    StealthManager(context).showLauncherIcon()
                }
            } catch (e: Exception) {}
        }
    }

    fun enableStealthMode() {
        try {
            hideLauncherIcon()
            prefs.setStealthModeEnabled(true)
            hideNotifications()
            SecurityLog.log(context, "INFO", "stealth_enable", "App hidden")
        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "stealth_enable", "Failed: ${e.message}")
        }
    }

    fun disableStealthMode() {
        try {
            showLauncherIcon()
            prefs.setStealthModeEnabled(false)
            SecurityLog.log(context, "INFO", "stealth_disable", "App revealed")
        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "stealth_disable", "Failed: ${e.message}")
        }
    }

    fun temporaryReveal(durationMs: Long = 60_000) {
        try {
            disableStealthMode()
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (prefs.isSetupComplete()) {
                        enableStealthMode()
                    }
                } catch (e: Exception) {}
            }, durationMs)
        } catch (e: Exception) {}
    }

    private fun hideLauncherIcon() {
        val componentName = android.content.ComponentName(context, LAUNCHER_COMPONENT)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun showLauncherIcon() {
        val componentName = android.content.ComponentName(context, LAUNCHER_COMPONENT)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun isIconVisible(): Boolean {
        return try {
            val componentName = android.content.ComponentName(context, LAUNCHER_COMPONENT)
            packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (e: Exception) { true }
    }

    private fun hideNotifications() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.cancel(NOTIFICATION_ID_SYSTEM)
            nm?.cancel(NOTIFICATION_ID_GUARD)
        } catch (e: Exception) {}
    }

    fun getSecretCode(): String = try { prefs.getSecretCode() } catch (e: Exception) { DEFAULT_SECRET_CODE }

    fun setSecretCode(code: String) {
        try {
            prefs.setSecretCode(code)
            SecurityLog.log(context, "INFO", "secret_code_change", "Secret code updated")
        } catch (e: Exception) {}
    }

    fun verifySecretCode(inputCode: String): Boolean = inputCode == getSecretCode()

    fun isStealthActive(): Boolean = try { prefs.isStealthModeEnabled() } catch (e: Exception) { false }
}
