package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

/**
 * StealthManager - Hides and reveals the app completely
 *
 * After setup is complete, the app hides itself:
 * - Removes launcher icon
 * - Stops showing notifications
 * - Becomes accessible ONLY via secret dialer code
 * - All services continue running in background
 *
 * The app uses Android's native component enable/disable mechanism
 * to hide its launcher icon. This makes the app completely invisible
 * in the app drawer and recent apps.
 *
 * To access the app again, the user dials a secret code (e.g., *#*#7890#*#*)
 * which triggers SecretDialReceiver → shows DashboardActivity
 */
class StealthManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val prefs: PreferencesManager = PreferencesManager(context)

    companion object {
        // Default secret code: *#*#7890#*#*
        // User can change this during setup
        const val DEFAULT_SECRET_CODE = "7890"

        // Component names for enable/disable
        const val LAUNCHER_COMPONENT = "com.dualpersona.system.ui.setup.SetupWizardActivity"
    }

    // ===== Hide / Show App =====

    /**
     * Hide the app completely from the device
     *
     * This makes the app:
     * - Invisible in app drawer (no launcher icon)
     * - Not appear in search results
     * - Not shown in recent apps
     * - All services continue running
     *
     * The app can only be accessed via secret dialer code
     */
    fun enableStealthMode() {
        try {
            // Hide launcher icon
            hideLauncherIcon()

            // Save stealth state
            prefs.setStealthModeEnabled(true)

            // Cancel all visible notifications
            hideNotifications()

            // Remove from recent tasks
            clearRecentTasks()

            SecurityLog.log(context, "INFO", "stealth_enable",
                "App hidden. Access via secret dialer code.")

        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "stealth_enable",
                "Failed to enable stealth: ${e.message}")
        }
    }

    /**
     * Show the app again
     * Used when accessing via secret dialer code
     */
    fun disableStealthMode() {
        try {
            // Show launcher icon
            showLauncherIcon()

            prefs.setStealthModeEnabled(false)

            SecurityLog.log(context, "INFO", "stealth_disable", "App revealed")

        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "stealth_disable",
                "Failed to disable stealth: ${e.message}")
        }
    }

    /**
     * Temporarily reveal app for dashboard access
     * App will re-hide after a timeout
     */
    fun temporaryReveal(durationMs: Long = 60_000) {
        disableStealthMode()

        // Schedule re-hiding
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (prefs.isSetupComplete()) {
                enableStealthMode()
            }
        }, durationMs)
    }

    // ===== Icon Management =====

    /**
     * Hide launcher icon using PackageManager
     */
    private fun hideLauncherIcon() {
        val componentName = ComponentName(context, LAUNCHER_COMPONENT)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Show launcher icon
     */
    fun showLauncherIcon() {
        val componentName = ComponentName(context, LAUNCHER_COMPONENT)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Check if launcher icon is currently visible
     */
    fun isIconVisible(): Boolean {
        val componentName = ComponentName(context, LAUNCHER_COMPONENT)
        val state = packageManager.getComponentEnabledSetting(componentName)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    // ===== Notification Control =====

    private fun hideNotifications() {
        try {
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager

            // Cancel our notification channels
            notificationManager.cancel(DualPersonaApp.NOTIFICATION_ID_SYSTEM)
            notificationManager.cancel(DualPersonaApp.NOTIFICATION_ID_GUARD)
        } catch (e: Exception) {
            // Ignore - might not have permission
        }
    }

    // ===== Recent Tasks =====

    @SuppressLint("NewApi")
    private fun clearRecentTasks() {
        try {
            val activityManager = context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as android.app.ActivityManager

            // Clear our app's recent tasks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (task in activityManager.appTasks) {
                    if (task.taskInfo.baseActivity?.packageName == context.packageName) {
                        task.finishAndRemoveTask()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore - might not have permission
        }
    }

    // ===== Secret Code =====

    /**
     * Get the current secret dialer code
     */
    fun getSecretCode(): String {
        return prefs.getSecretCode()
    }

    /**
     * Set a new secret dialer code
     */
    fun setSecretCode(code: String) {
        prefs.setSecretCode(code)
        SecurityLog.log(context, "INFO", "secret_code_change", "Secret code updated")
    }

    /**
     * Verify a dialer code
     */
    fun verifySecretCode(inputCode: String): Boolean {
        return inputCode == prefs.getSecretCode()
    }

    // ===== Status =====

    /**
     * Check if stealth mode is currently active
     */
    fun isStealthActive(): Boolean {
        return prefs.isStealthModeEnabled()
    }

    companion object {
        /**
         * Restore app icon (used on app start if not in stealth)
         */
        fun restoreAppIcon(context: Context) {
            val prefs = PreferencesManager(context)
            if (!prefs.isStealthModeEnabled()) {
                StealthManager(context).showLauncherIcon()
            }
        }
    }
}
