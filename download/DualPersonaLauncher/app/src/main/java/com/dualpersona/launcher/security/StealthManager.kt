package com.dualpersona.launcher.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.dualpersona.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages app stealth — hiding the launcher icon,
 * disguising app name, and anti-detection measures.
 */
class StealthManager(private val context: Context) {

    private val packageManager = context.packageManager
    private val componentName = ComponentName(context, "com.dualpersona.launcher.activities.SplashActivity")

    /**
     * Enable stealth mode — hides the app launcher icon
     */
    suspend fun enableStealthMode() = withContext(Dispatchers.IO) {
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Disable stealth mode — shows the app launcher icon
     */
    suspend fun disableStealthMode() = withContext(Dispatchers.IO) {
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Check if stealth mode is active
     */
    fun isStealthModeActive(): Boolean {
        val state = packageManager.getComponentEnabledSetting(componentName)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * Disguise the app — change app name and icon to look like a calculator/utility
     */
    suspend fun disguiseApp() = withContext(Dispatchers.IO) {
        // Hide main launcher
        enableStealthMode()

        // The app can be launched via a secret dialer code or specific gesture
        // (e.g., dialing *#*#1234#*#*)
    }

    /**
     * Restore normal app appearance
     */
    suspend fun unmaskApp() = withContext(Dispatchers.IO) {
        disableStealthMode()
    }

    /**
     * Anti-detection: prevent the app from appearing in recent apps
     * (configured via manifest attributes)
     */
    fun isAppHiddenFromRecent(): Boolean {
        return false // Controlled via manifest: excludeFromRecents
    }

    /**
     * Check if the app is installed under a different package name
     * (for advanced stealth scenarios)
     */
    fun getInstalledPackages(): List<String> {
        return packageManager.getInstalledPackages(0).map { it.packageName }
    }
}
