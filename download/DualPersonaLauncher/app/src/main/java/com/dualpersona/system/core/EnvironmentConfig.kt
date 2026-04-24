package com.dualpersona.system.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

/**
 * EnvironmentConfig - Configures each user's environment
 *
 * Manages per-user settings:
 * - Allowed/blocked apps per profile
 * - Custom names for each profile
 * - Theme preferences per profile
 * - Wallpapers per profile
 * - Notification settings per profile
 *
 * Each user gets a completely independent Android environment
 * with their own apps, settings, storage, and data.
 */
class EnvironmentConfig(private val context: Context) {

    private val prefs: PreferencesManager = PreferencesManager(context)
    private val packageManager: PackageManager = context.packageManager

    // ===== Profile Names =====

    /**
     * Set display name for a user profile
     */
    fun setProfileName(userSlot: Int, name: String) {
        prefs.setProfileName(userSlot, name)
        SecurityLog.log(context, "INFO", "profile_name",
            "User ${userSlot + 1} renamed to: $name")
    }

    /**
     * Get display name for a user profile
     */
    fun getProfileName(userSlot: Int): String {
        return prefs.getProfileName(userSlot)
    }

    // ===== App Management =====

    /**
     * Get all installed apps on the device
     */
    fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        return resolveInfos.map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                appName = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager),
                isSystem = isSystemApp(resolveInfo.activityInfo.packageName)
            )
        }.sortedBy { it.appName.lowercase() }
    }

    /**
     * Check if an app is a system app
     */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val flags = packageManager.getApplicationInfo(packageName, 0).flags
            flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Set allowed apps for a user profile
     */
    fun setAllowedApps(userSlot: Int, packageNames: List<String>) {
        prefs.setAllowedApps(userSlot, packageNames)
        SecurityLog.log(context, "INFO", "allowed_apps",
            "User ${userSlot + 1}: ${packageNames.size} apps configured")
    }

    /**
     * Get allowed apps for a user profile
     */
    fun getAllowedApps(userSlot: Int): List<String> {
        return prefs.getAllowedApps(userSlot)
    }

    /**
     * Check if an app is allowed for the current user
     */
    fun isAppAllowed(userSlot: Int, packageName: String): Boolean {
        val allowedApps = getAllowedApps(userSlot)
        return if (allowedApps.isEmpty()) {
            true // No restrictions configured - all apps allowed
        } else {
            packageName in allowedApps || isSystemApp(packageName)
        }
    }

    // ===== Theme Configuration =====

    /**
     * Set theme for a user profile
     */
    fun setTheme(userSlot: Int, theme: Theme) {
        prefs.setProfileTheme(userSlot, theme.name)
    }

    /**
     * Get theme for a user profile
     */
    fun getTheme(userSlot: Int): Theme {
        val themeName = prefs.getProfileTheme(userSlot)
        return try {
            Theme.valueOf(themeName)
        } catch (e: Exception) {
            Theme.DEFAULT
        }
    }

    // ===== Wallpaper =====

    /**
     * Set wallpaper path for a user profile
     * (Wallpapers are applied natively by Android per-user)
     */
    fun setWallpaperPath(userSlot: Int, path: String) {
        prefs.setWallpaperPath(userSlot, path)
    }

    /**
     * Get wallpaper path for a user profile
     */
    fun getWallpaperPath(userSlot: Int): String {
        return prefs.getWallpaperPath(userSlot)
    }

    // ===== Notification Policy =====

    /**
     * Configure notification behavior per profile
     */
    fun setNotificationPolicy(userSlot: Int, policy: NotificationPolicy) {
        prefs.setNotificationPolicy(userSlot, policy.name)
    }

    /**
     * Get notification policy for a user profile
     */
    fun getNotificationPolicy(userSlot: Int): NotificationPolicy {
        val policyName = prefs.getNotificationPolicy(userSlot)
        return try {
            NotificationPolicy.valueOf(policyName)
        } catch (e: Exception) {
            NotificationPolicy.ALL
        }
    }

    // ===== Data Classes =====

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable,
        val isSystem: Boolean
    )

    enum class Theme {
        DEFAULT,      // System default
        DARK,         // Dark mode
        LIGHT,        // Light mode
        AMOLED,       // True black (for OLED)
        BUSINESS,     // Professional/business style
        PERSONAL      // Casual/personal style
    }

    enum class NotificationPolicy {
        ALL,          // Show all notifications
        SILENT,       // Hide all notifications
        HEADS_UP,     // Only important notifications
        CUSTOM        // Custom per-app rules
    }
}
