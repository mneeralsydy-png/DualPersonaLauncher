package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.content.ComponentName
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
            val prefs = PreferencesManager(context)
            if (!prefs.isStealthModeEnabled()) {
                StealthManager(context).showLauncherIcon()
            }
        }
    }

    fun enableStealthMode() {
        try {
            hideLauncherIcon()
            prefs.setStealthModeEnabled(true)
            hideNotifications()
            clearRecentTasks()
            SecurityLog.log(context, "INFO", "stealth_enable", "App hidden.")
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
        disableStealthMode()
        Handler(Looper.getMainLooper()).postDelayed({
            if (prefs.isSetupComplete()) {
                enableStealthMode()
            }
        }, durationMs)
    }

    private fun hideLauncherIcon() {
        val componentName = ComponentName(context, LAUNCHER_COMPONENT)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun showLauncherIcon() {
        val componentName = ComponentName(context, LAUNCHER_COMPONENT)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun isIconVisible(): Boolean {
        val componentName = ComponentName(context, LAUNCHER_COMPONENT)
        val state = packageManager.getComponentEnabledSetting(componentName)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun hideNotifications() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.cancel(NOTIFICATION_ID_SYSTEM)
            nm?.cancel(NOTIFICATION_ID_GUARD)
        } catch (e: Exception) { }
    }

    @SuppressLint("NewApi")
    private fun clearRecentTasks() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (task in am?.appTasks ?: emptyList()) {
                    if (task.taskInfo.baseActivity?.packageName == context.packageName) {
                        task.finishAndRemoveTask()
                    }
                }
            }
        } catch (e: Exception) { }
    }

    fun getSecretCode(): String = prefs.getSecretCode()

    fun setSecretCode(code: String) {
        prefs.setSecretCode(code)
        SecurityLog.log(context, "INFO", "secret_code_change", "Secret code updated")
    }

    fun verifySecretCode(inputCode: String): Boolean = inputCode == prefs.getSecretCode()

    fun isStealthActive(): Boolean = prefs.isStealthModeEnabled()
}
