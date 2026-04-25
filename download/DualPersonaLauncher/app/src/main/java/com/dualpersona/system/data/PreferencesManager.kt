package com.dualpersona.system.data

import android.content.Context
import android.content.SharedPreferences

/**
 * PreferencesManager - تخزين آمن ومستقر
 * 
 * يستخدم SharedPreferences العادي (بدون EncryptedSharedPreferences)
 * لأن EncryptedSharedPreferences تسبب انهيار على كثير من الأجهزة
 * كل البيانات المحساسة يتم تشفيرها عبر CredentialManager
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "dual_persona_prefs"

        // Keys - Setup
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_CURRENT_STEP = "current_setup_step"

        // Keys - User profiles
        private const val KEY_PROFILE_NAME_0 = "profile_name_0"
        private const val KEY_PROFILE_NAME_1 = "profile_name_1"
        private const val KEY_SECONDARY_USER_CONFIRMED = "secondary_user_confirmed"
        private const val KEY_SECONDARY_USER_NAME = "secondary_user_name"

        // Keys - Credentials
        private const val KEY_CRED_TYPE_0 = "cred_type_0"
        private const val KEY_CRED_TYPE_1 = "cred_type_1"

        // Keys - Stealth
        private const val KEY_STEALTH_ENABLED = "stealth_enabled"
        private const val KEY_SECRET_CODE = "secret_code"

        // Keys - Security
        private const val KEY_SUSPICIOUS_COUNT = "suspicious_count"
        private const val KEY_BLOCKED_APPS = "blocked_cross_profile_apps"
        private const val KEY_SECURITY_LOG = "security_log"

        // Keys - Service
        private const val KEY_SERVICE_STARTED = "service_started"
        private const val KEY_MAX_FAILED_ATTEMPTS = "max_failed_attempts"
    }

    private val prefs: SharedPreferences = try {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        // Fallback - should never happen with regular SharedPreferences
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ===== Setup =====
    fun isSetupComplete(): Boolean = try { prefs.getBoolean(KEY_SETUP_COMPLETE, false) } catch (e: Exception) { false }
    fun setSetupComplete(complete: Boolean) = try { prefs.edit().putBoolean(KEY_SETUP_COMPLETE, complete).apply() } catch (e: Exception) {}

    fun getCurrentSetupStep(): Int = try { prefs.getInt(KEY_CURRENT_STEP, 0) } catch (e: Exception) { 0 }
    fun setCurrentSetupStep(step: Int) = try { prefs.edit().putInt(KEY_CURRENT_STEP, step).apply() } catch (e: Exception) {}

    // ===== Profile Names =====
    fun getProfileName(slot: Int): String {
        return try {
            val key = if (slot == 0) KEY_PROFILE_NAME_0 else KEY_PROFILE_NAME_1
            prefs.getString(key, if (slot == 0) "الملف الشخصي A" else "الملف الشخصي B") 
                ?: "الملف الشخصي ${slot + 1}"
        } catch (e: Exception) {
            if (slot == 0) "الملف الشخصي A" else "الملف الشخصي B"
        }
    }

    fun setProfileName(slot: Int, name: String) {
        try {
            val key = if (slot == 0) KEY_PROFILE_NAME_0 else KEY_PROFILE_NAME_1
            prefs.edit().putString(key, name).apply()
        } catch (e: Exception) {}
    }

    // ===== Secondary User =====
    fun isSecondaryUserConfirmed(): Boolean = try { prefs.getBoolean(KEY_SECONDARY_USER_CONFIRMED, false) } catch (e: Exception) { false }
    fun setSecondaryUserConfirmed(confirmed: Boolean) = try { prefs.edit().putBoolean(KEY_SECONDARY_USER_CONFIRMED, confirmed).apply() } catch (e: Exception) {}
    fun setSecondaryUserName(name: String) = try { prefs.edit().putString(KEY_SECONDARY_USER_NAME, name).apply() } catch (e: Exception) {}
    fun getSecondaryUserName(): String? = try { prefs.getString(KEY_SECONDARY_USER_NAME, null) } catch (e: Exception) { null }

    fun clearSecondaryUser() = try {
        prefs.edit().remove(KEY_SECONDARY_USER_NAME).remove(KEY_SECONDARY_USER_CONFIRMED).apply()
    } catch (e: Exception) {}

    // ===== Credentials =====
    fun getCredentialType(slot: Int): String {
        return try {
            val key = if (slot == 0) KEY_CRED_TYPE_0 else KEY_CRED_TYPE_1
            prefs.getString(key, "NONE") ?: "NONE"
        } catch (e: Exception) { "NONE" }
    }

    fun setCredentialType(slot: Int, type: String) {
        try {
            val key = if (slot == 0) KEY_CRED_TYPE_0 else KEY_CRED_TYPE_1
            prefs.edit().putString(key, type).apply()
        } catch (e: Exception) {}
    }

    fun getMaxFailedAttempts(): Int = try { prefs.getInt(KEY_MAX_FAILED_ATTEMPTS, 5) } catch (e: Exception) { 5 }
    fun setMaxFailedAttempts(count: Int) = try { prefs.edit().putInt(KEY_MAX_FAILED_ATTEMPTS, count).apply() } catch (e: Exception) {}

    // ===== Stealth =====
    fun isStealthModeEnabled(): Boolean = try { prefs.getBoolean(KEY_STEALTH_ENABLED, false) } catch (e: Exception) { false }
    fun setStealthModeEnabled(enabled: Boolean) = try { prefs.edit().putBoolean(KEY_STEALTH_ENABLED, enabled).apply() } catch (e: Exception) {}

    fun getSecretCode(): String = try { prefs.getString(KEY_SECRET_CODE, "7890") ?: "7890" } catch (e: Exception) { "7890" }
    fun setSecretCode(code: String) = try { prefs.edit().putString(KEY_SECRET_CODE, code).apply() } catch (e: Exception) {}

    // ===== Security =====
    fun getSuspiciousActivityCount(): Int = try { prefs.getInt(KEY_SUSPICIOUS_COUNT, 0) } catch (e: Exception) { 0 }
    fun incrementSuspiciousActivityCount() = try {
        prefs.edit().putInt(KEY_SUSPICIOUS_COUNT, getSuspiciousActivityCount() + 1).apply()
    } catch (e: Exception) {}

    fun getBlockedCrossProfileApps(): List<String> {
        return try {
            val json = prefs.getString(KEY_BLOCKED_APPS, "") ?: ""
            if (json.isBlank()) emptyList() else json.split(",")
        } catch (e: Exception) { emptyList() }
    }

    fun setBlockedCrossProfileApps(apps: List<String>) {
        try { prefs.edit().putString(KEY_BLOCKED_APPS, apps.joinToString(",")).apply() } catch (e: Exception) {}
    }

    // ===== Service =====
    fun isServiceStarted(): Boolean = try { prefs.getBoolean(KEY_SERVICE_STARTED, false) } catch (e: Exception) { false }
    fun setServiceStarted(started: Boolean) = try { prefs.edit().putBoolean(KEY_SERVICE_STARTED, started).apply() } catch (e: Exception) {}

    // ===== Security Log =====
    fun addSecurityLog(entry: String) {
        try {
            val logs = getSecurityLogs()
            val newLogs = (listOf(entry) + logs).take(100)
            prefs.edit().putString(KEY_SECURITY_LOG, newLogs.joinToString("|||")).apply()
        } catch (e: Exception) {}
    }

    fun getSecurityLogs(): List<String> {
        return try {
            val raw = prefs.getString(KEY_SECURITY_LOG, "") ?: ""
            if (raw.isBlank()) emptyList() else raw.split("|||")
        } catch (e: Exception) { emptyList() }
    }

    fun clearSecurityLogs() = try { prefs.edit().remove(KEY_SECURITY_LOG).apply() } catch (e: Exception) {}

    // ===== Reset =====
    fun resetAll() = try { prefs.edit().clear().apply() } catch (e: Exception) {}
}
