package com.dualpersona.launcher.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dualpersona.launcher.utils.PrefKeys.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Secure preferences manager using EncryptedSharedPreferences
 * All sensitive data is encrypted at rest
 */
class PreferencesManager(context: Context) {

    // Encrypted prefs for sensitive data (PINs, hashes)
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "dual_persona_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Regular prefs for non-sensitive settings
    private val regularPrefs: SharedPreferences =
        context.getSharedPreferences("dual_persona_prefs", Context.MODE_PRIVATE)

    // In-memory cache for frequently accessed values
    private val cache = ConcurrentHashMap<String, Any?>()

    // ==================== Setup ====================

    var isSetupComplete: Boolean
        get() = regularPrefs.getBoolean(IS_SETUP_COMPLETE, false)
        set(value) = regularPrefs.edit { putBoolean(IS_SETUP_COMPLETE, value) }

    var currentEnvironment: String
        get() = regularPrefs.getString(CURRENT_ENVIRONMENT, EnvironmentType.PRIMARY) ?: EnvironmentType.PRIMARY
        set(value) = regularPrefs.edit { putString(CURRENT_ENVIRONMENT, value) }

    // ==================== PIN Hashes (Encrypted) ====================

    fun setPinHash(environment: String, hash: String) {
        val key = when (environment) {
            EnvironmentType.PRIMARY -> PRIMARY_PIN_HASH
            EnvironmentType.HIDDEN -> HIDDEN_PIN_HASH
            EnvironmentType.EMERGENCY -> EMERGENCY_PIN_HASH
            else -> return
        }
        encryptedPrefs.edit { putString(key, hash) }
        cache[key] = hash
    }

    fun getPinHash(environment: String): String? {
        val key = when (environment) {
            EnvironmentType.PRIMARY -> PRIMARY_PIN_HASH
            EnvironmentType.HIDDEN -> HIDDEN_PIN_HASH
            EnvironmentType.EMERGENCY -> EMERGENCY_PIN_HASH
            else -> return null
        }
        return cache.getOrPut(key) {
            encryptedPrefs.getString(key, null)
        } as? String
    }

    fun setPinSalt(environment: String, salt: String) {
        val key = when (environment) {
            EnvironmentType.PRIMARY -> PRIMARY_PIN_SALT
            EnvironmentType.HIDDEN -> HIDDEN_PIN_SALT
            EnvironmentType.EMERGENCY -> EMERGENCY_PIN_SALT
            else -> return
        }
        encryptedPrefs.edit { putString(key, salt) }
    }

    fun getPinSalt(environment: String): String? {
        val key = when (environment) {
            EnvironmentType.PRIMARY -> PRIMARY_PIN_SALT
            EnvironmentType.HIDDEN -> HIDDEN_PIN_SALT
            EnvironmentType.EMERGENCY -> EMERGENCY_PIN_SALT
            else -> return null
        }
        return encryptedPrefs.getString(key, null)
    }

    // ==================== Pattern Hashes (Encrypted) ====================

    fun setPatternHash(environment: String, hash: String) {
        val key = when (environment) {
            EnvironmentType.PRIMARY -> PRIMARY_PATTERN_HASH
            EnvironmentType.HIDDEN -> HIDDEN_PATTERN_HASH
            else -> return
        }
        encryptedPrefs.edit { putString(key, hash) }
    }

    fun getPatternHash(environment: String): String? {
        val key = when (environment) {
            EnvironmentType.PRIMARY -> PRIMARY_PATTERN_HASH
            EnvironmentType.HIDDEN -> HIDDEN_PATTERN_HASH
            else -> return null
        }
        return encryptedPrefs.getString(key, null)
    }

    // ==================== Security Settings ====================

    var isFingerprintEnabled: Boolean
        get() = regularPrefs.getBoolean(ENABLE_FINGERPRINT, false)
        set(value) = regularPrefs.edit { putBoolean(ENABLE_FINGERPRINT, value) }

    var isStealthModeEnabled: Boolean
        get() = regularPrefs.getBoolean(ENABLE_STEALTH_MODE, false)
        set(value) = regularPrefs.edit { putBoolean(ENABLE_STEALTH_MODE, value) }

    var isAutoLockEnabled: Boolean
        get() = regularPrefs.getBoolean(AUTO_LOCK_ENABLED, true)
        set(value) = regularPrefs.edit { putBoolean(AUTO_LOCK_ENABLED, value) }

    var autoLockDelaySeconds: Int
        get() = regularPrefs.getInt(AUTO_LOCK_DELAY_SECONDS, SecurityConstants.DEFAULT_AUTO_LOCK_DELAY)
        set(value) = regularPrefs.edit { putInt(AUTO_LOCK_DELAY_SECONDS, value) }

    var maxFailedAttempts: Int
        get() = regularPrefs.getInt(MAX_FAILED_ATTEMPTS, SecurityConstants.DEFAULT_MAX_ATTEMPTS)
        set(value) = regularPrefs.edit { putInt(MAX_FAILED_ATTEMPTS, value) }

    var isIntrusionDetectionEnabled: Boolean
        get() = regularPrefs.getBoolean(ENABLE_INTRUSION_DETECTION, false)
        set(value) = regularPrefs.edit { putBoolean(ENABLE_INTRUSION_DETECTION, value) }

    var isSelfDestructEnabled: Boolean
        get() = regularPrefs.getBoolean(ENABLE_SELF_DESTRUCT, false)
        set(value) = regularPrefs.edit { putBoolean(ENABLE_SELF_DESTRUCT, value) }

    // ==================== Theme Settings ====================

    var themePrimaryColor: Int
        get() = regularPrefs.getInt(THEME_PRIMARY_COLOR, 0xFF1A73E8.toInt())
        set(value) = regularPrefs.edit { putInt(THEME_PRIMARY_COLOR, value) }

    var themeAccentColor: Int
        get() = regularPrefs.getInt(THEME_ACCENT_COLOR, 0xFFFF6D00.toInt())
        set(value) = regularPrefs.edit { putInt(THEME_ACCENT_COLOR, value) }

    var isDarkMode: Boolean
        get() = regularPrefs.getBoolean(THEME_DARK_MODE, false)
        set(value) = regularPrefs.edit { putBoolean(THEME_DARK_MODE, value) }

    var gridColumns: Int
        get() = regularPrefs.getInt(THEME_GRID_COLUMNS, 4)
        set(value) = regularPrefs.edit { putInt(THEME_GRID_COLUMNS, value) }

    // ==================== Backup Settings ====================

    var lastBackupTime: Long
        get() = regularPrefs.getLong(LAST_BACKUP_TIME, 0)
        set(value) = regularPrefs.edit { putLong(LAST_BACKUP_TIME, value) }

    // ==================== Utility ====================

    fun clearAll() {
        regularPrefs.edit { clear() }
        encryptedPrefs.edit { clear() }
        cache.clear()
    }

    fun clearEnvironmentData(environment: String) {
        // Clear only environment-specific cached data
        val keysToRemove = cache.keys.filter { it.contains(environment) }
        keysToRemove.forEach { cache.remove(it) }
    }
}
