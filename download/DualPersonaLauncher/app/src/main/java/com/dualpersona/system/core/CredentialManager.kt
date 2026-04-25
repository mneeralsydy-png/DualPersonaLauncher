package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

/**
 * CredentialManager - يدير بيانات اعتماد شاشة القفل
 * يعمل مع شاشة قفل Android الأصلية - لا يستبدلها
 */
class CredentialManager(private val context: Context) {

    private val keyguardManager: KeyguardManager? = try {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    } catch (e: Exception) { null }

    private val prefs: PreferencesManager = PreferencesManager(context)

    companion object {
        const val TAG = "CredentialManager"
    }

    fun storeCredentialMeta(userSlot: Int, credentialType: CredentialType, label: String) {
        try {
            prefs.setCredentialType(userSlot, credentialType.name)
            SecurityLog.log(context, "SUCCESS", "set_credential", "User ${userSlot + 1}: $credentialType configured")
        } catch (e: Exception) {}
    }

    fun getCredentialType(userSlot: Int): CredentialType {
        return try {
            CredentialType.valueOf(prefs.getCredentialType(userSlot))
        } catch (e: Exception) { CredentialType.NONE }
    }

    fun isCredentialConfigured(userSlot: Int): Boolean = getCredentialType(userSlot) != CredentialType.NONE

    @SuppressLint("NewApi")
    fun hasDeviceCredential(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyguardManager?.isDeviceSecure == true
            } else {
                keyguardManager?.isKeyguardSecure == true
            }
        } catch (e: Exception) { false }
    }

    fun detectCredentialType(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (keyguardManager?.isDeviceSecure == true) {
                    return "PIN"
                }
            }
            "NONE"
        } catch (e: Exception) { "NONE" }
    }

    fun isBiometricAvailable(): Boolean {
        return try {
            val biometricManager = androidx.biometric.BiometricManager.from(context)
            biometricManager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) { false }
    }

    fun getBiometricStatus(): BiometricStatus {
        return try {
            val biometricManager = androidx.biometric.BiometricManager.from(context)
            when (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.ENROLLED
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
                else -> BiometricStatus.UNKNOWN
            }
        } catch (e: Exception) { BiometricStatus.UNKNOWN }
    }

    fun openSecuritySettings(): Boolean {
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("android.settings.LOCK_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in intents) {
            try { context.startActivity(intent); return true }
            catch (e: Exception) {}
        }
        return false
    }

    fun openBiometricEnrollment(): Boolean {
        return try {
            context.startActivity(Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            true
        } catch (e: Exception) { false }
    }

    fun onPasswordChanged(userSlot: Int) {
        SecurityLog.log(context, "INFO", "password_changed", "Credential changed for User ${userSlot + 1}")
    }

    fun onPasswordFailed(attemptCount: Int) {
        SecurityLog.log(context, "WARNING", "password_failed", "Failed attempt #$attemptCount")
    }

    fun onPasswordSucceeded(userSlot: Int) {
        SecurityLog.log(context, "SUCCESS", "password_success", "User ${userSlot + 1} authenticated")
    }

    enum class CredentialType {
        NONE, PIN, PATTERN, PASSWORD, FINGERPRINT, BIOMETRIC_PIN, BIOMETRIC_PATTERN
    }

    enum class BiometricStatus {
        ENROLLED, NOT_ENROLLED, NO_HARDWARE, HW_UNAVAILABLE, UPDATE_REQUIRED, UNKNOWN
    }
}
