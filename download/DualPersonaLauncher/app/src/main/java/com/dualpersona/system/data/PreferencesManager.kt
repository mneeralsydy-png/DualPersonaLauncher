package com.dualpersona.system.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dualpersona.system.core.CredentialManager
import com.dualpersona.system.core.EnvironmentConfig
import com.dualpersona.system.core.StealthManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * PreferencesManager - Secure encrypted preferences storage
 *
 * Uses EncryptedSharedPreferences from AndroidX Security Crypto
 * All data is encrypted at rest using AES-256-GCM via Android Keystore
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "dual_persona_secure_prefs"

        // Keys - Setup
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_CURRENT_STEP = "current_setup_step"

        // Keys - User profiles
        private const val KEY_PROFILE_NAME_0 = "profile_name_0" // User A
        private const val KEY_PROFILE_NAME_1 = "profile_name_1" // User B
        private const val KEY_SECONDARY_USER_HANDLE = "secondary_user_handle"
        private const val KEY_SECONDARY_USER_NAME = "secondary_user_name"

        // Keys - Credentials
        private const val KEY_CRED_TYPE_0 = "cred_type_0"
        private const val KEY_CRED_TYPE_1 = "cred_type_1"
        private const val KEY_CRED_LABEL_0 = "cred_label_0"
        private const val KEY_CRED_LABEL_1 = "cred_label_1"
        private const val KEY_CRED_CHANGED_0 = "cred_changed_0"
        private const val KEY_CRED_CHANGED_1 = "cred_changed_1"
        private const val KEY_MAX_FAILED_ATTEMPTS = "max_failed_attempts"
        private const val KEY_FAILED_ATTEMPTS_0 = "failed_attempts_0"
        private const val KEY_FAILED_ATTEMPTS_1 = "failed_attempts_1"

        // Keys - Stealth
        private const val KEY_STEALTH_ENABLED = "stealth_enabled"
        private const val KEY_SECRET_CODE = "secret_code"

        // Keys - Environment
        private const val KEY_THEME_0 = "theme_0"
        private const val KEY_THEME_1 = "theme_1"
        private const val KEY_WALLPAPER_0 = "wallpaper_0"
        private const val KEY_WALLPAPER_1 = "wallpaper_1"
        private const val KEY_NOTIFICATION_POLICY_0 = "notif_policy_0"
        private const val KEY_NOTIFICATION_POLICY_1 = "notif_policy_1"
        private const val KEY_ALLOWED_APPS_0 = "allowed_apps_0"
        private const val KEY_ALLOWED_APPS_1 = "allowed_apps_1"
        private const val KEY_USER_RESTRICTIONS = "user_restrictions"

        // Keys - Security
        private const val KEY_SUSPICIOUS_COUNT = "suspicious_count"
        private const val KEY_LAST_ISOLATION_CHECK = "last_isolation_check"
        private const val KEY_BLOCKED_APPS = "blocked_cross_profile_apps"
        private const val KEY_SECURITY_LOG = "security_log"

        // Keys - Service
        private const val KEY_SERVICE_STARTED = "service_started"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ===== Setup =====

    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
    fun setSetupComplete(complete: Boolean) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, complete).apply()

    fun getCurrentSetupStep(): Int = prefs.getInt(KEY_CURRENT_STEP, 0)
    fun setCurrentSetupStep(step: Int) = prefs.edit().putInt(KEY_CURRENT_STEP, step).apply()

    // ===== Profile Names =====

    fun getProfileName(slot: Int): String {
        val key = if (slot == 0) KEY_PROFILE_NAME_0 else KEY_PROFILE_NAME_1
        return prefs.getString(key, if (slot == 0) "User A" else "User B") ?: "User ${slot + 1}"
    }

    fun setProfileName(slot: Int, name: String) {
        val key = if (slot == 0) KEY_PROFILE_NAME_0 else KEY_PROFILE_NAME_1
        prefs.edit().putString(key, name).apply()
    }

    // ===== Secondary User =====

    fun getSecondaryUserHandle(): android.os.UserHandle? {
        val serial = prefs.getLong(KEY_SECONDARY_USER_HANDLE, -1)
        if (serial == -1L) return null
        return try {
            android.os.UserManager.getUserHandle(serial)
        } catch (e: Exception) {
            null
        }
    }

    fun setSecondaryUserHandle(handle: android.os.UserHandle) {
        val serial = try {
            android.os.UserManager.getInstance(null).getSerialNumberForUser(handle)
        } catch (e: Exception) {
            -1L
        }
        prefs.edit().putLong(KEY_SECONDARY_USER_HANDLE, serial).apply()
    }

    fun setSecondaryUserName(name: String) = prefs.edit().putString(KEY_SECONDARY_USER_NAME, name).apply()
    fun getSecondaryUserName(): String? = prefs.getString(KEY_SECONDARY_USER_NAME, null)
    fun clearSecondaryUser() = prefs.edit()
        .remove(KEY_SECONDARY_USER_HANDLE)
        .remove(KEY_SECONDARY_USER_NAME)
        .apply()

    // ===== Credentials =====

    fun getCredentialType(slot: Int): String {
        val key = if (slot == 0) KEY_CRED_TYPE_0 else KEY_CRED_TYPE_1
        return prefs.getString(key, CredentialManager.CredentialType.NONE.name)
            ?: CredentialManager.CredentialType.NONE.name
    }

    fun setCredentialType(slot: Int, type: String) {
        val key = if (slot == 0) KEY_CRED_TYPE_0 else KEY_CRED_TYPE_1
        prefs.edit().putString(key, type).apply()
    }

    fun getCredentialLabel(slot: Int): String {
        val key = if (slot == 0) KEY_CRED_LABEL_0 else KEY_CRED_LABEL_1
        return prefs.getString(key, "") ?: ""
    }

    fun setCredentialLabel(slot: Int, label: String) {
        val key = if (slot == 0) KEY_CRED_LABEL_0 else KEY_CRED_LABEL_1
        prefs.edit().putString(key, label).apply()
    }

    fun setCredentialChangedTime(slot: Int, time: Long) {
        val key = if (slot == 0) KEY_CRED_CHANGED_0 else KEY_CRED_CHANGED_1
        prefs.edit().putLong(key, time).apply()
    }

    fun getMaxFailedAttempts(): Int = prefs.getInt(KEY_MAX_FAILED_ATTEMPTS, 5)
    fun setMaxFailedAttempts(count: Int) = prefs.edit().putInt(KEY_MAX_FAILED_ATTEMPTS, count).apply()

    fun resetFailedAttempts(slot: Int) {
        val key = if (slot == 0) KEY_FAILED_ATTEMPTS_0 else KEY_FAILED_ATTEMPTS_1
        prefs.edit().putInt(key, 0).apply()
    }

    // ===== Stealth =====

    fun isStealthModeEnabled(): Boolean = prefs.getBoolean(KEY_STEALTH_ENABLED, false)
    fun setStealthModeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_STEALTH_ENABLED, enabled).apply()

    fun getSecretCode(): String = prefs.getString(KEY_SECRET_CODE, StealthManager.DEFAULT_SECRET_CODE)
        ?: StealthManager.DEFAULT_SECRET_CODE

    fun setSecretCode(code: String) = prefs.edit().putString(KEY_SECRET_CODE, code).apply()

    // ===== Environment =====

    fun getProfileTheme(slot: Int): String {
        val key = if (slot == 0) KEY_THEME_0 else KEY_THEME_1
        return prefs.getString(key, EnvironmentConfig.Theme.DEFAULT.name)
            ?: EnvironmentConfig.Theme.DEFAULT.name
    }

    fun setProfileTheme(slot: Int, theme: String) {
        val key = if (slot == 0) KEY_THEME_0 else KEY_THEME_1
        prefs.edit().putString(key, theme).apply()
    }

    fun getWallpaperPath(slot: Int): String {
        val key = if (slot == 0) KEY_WALLPAPER_0 else KEY_WALLPAPER_1
        return prefs.getString(key, "") ?: ""
    }

    fun setWallpaperPath(slot: Int, path: String) {
        val key = if (slot == 0) KEY_WALLPAPER_0 else KEY_WALLPAPER_1
        prefs.edit().putString(key, path).apply()
    }

    fun getNotificationPolicy(slot: Int): String {
        val key = if (slot == 0) KEY_NOTIFICATION_POLICY_0 else KEY_NOTIFICATION_POLICY_1
        return prefs.getString(key, EnvironmentConfig.NotificationPolicy.ALL.name)
            ?: EnvironmentConfig.NotificationPolicy.ALL.name
    }

    fun setNotificationPolicy(slot: Int, policy: String) {
        val key = if (slot == 0) KEY_NOTIFICATION_POLICY_0 else KEY_NOTIFICATION_POLICY_1
        prefs.edit().putString(key, policy).apply()
    }

    fun getAllowedApps(slot: Int): List<String> {
        val key = if (slot == 0) KEY_ALLOWED_APPS_0 else KEY_ALLOWED_APPS_1
        val json = prefs.getString(key, "") ?: ""
        return if (json.isBlank()) emptyList() else json.split(",")
    }

    fun setAllowedApps(slot: Int, packages: List<String>) {
        val key = if (slot == 0) KEY_ALLOWED_APPS_0 else KEY_ALLOWED_APPS_1
        prefs.edit().putString(key, packages.joinToString(",")).apply()
    }

    fun getUserRestrictions(): List<String> {
        val json = prefs.getString(KEY_USER_RESTRICTIONS, "") ?: ""
        return if (json.isBlank()) emptyList() else json.split(",")
    }

    fun setUserRestrictions(restrictions: List<String>) {
        prefs.edit().putString(KEY_USER_RESTRICTIONS, restrictions.joinToString(",")).apply()
    }

    // ===== Security =====

    fun getSuspiciousActivityCount(): Int = prefs.getInt(KEY_SUSPICIOUS_COUNT, 0)
    fun incrementSuspiciousActivityCount() = prefs.edit()
        .putInt(KEY_SUSPICIOUS_COUNT, getSuspiciousActivityCount() + 1).apply()

    fun getBlockedCrossProfileApps(): List<String> {
        val json = prefs.getString(KEY_BLOCKED_APPS, "") ?: ""
        return if (json.isBlank()) emptyList() else json.split(",")
    }

    fun setBlockedCrossProfileApps(apps: List<String>) {
        prefs.edit().putString(KEY_BLOCKED_APPS, apps.joinToString(",")).apply()
    }

    // ===== Service =====

    fun isServiceStarted(): Boolean = prefs.getBoolean(KEY_SERVICE_STARTED, false)
    fun setServiceStarted(started: Boolean) = prefs.edit().putBoolean(KEY_SERVICE_STARTED, started).apply()

    // ===== Reset =====

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
