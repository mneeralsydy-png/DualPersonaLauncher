package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * CredentialManager - Manages lock screen credentials integration
 *
 * This class handles the credential system that maps unlock methods
 * to user profiles. It works WITH the Android system lock screen,
 * not replacing it.
 *
 * Flow:
 * 1. User A sets their PIN/Pattern/Fingerprint through Android Settings
 * 2. User B sets their credential through Android Settings (on their profile)
 * 3. When the system lock screen unlocks, it automatically loads the
 *    corresponding user's profile - this is native Android behavior
 *
 * The app's role:
 * - Guides user through initial credential setup
 * - Stores credential metadata (not actual credentials)
 * - Monitors credential change events
 * - Provides biometric enrollment guidance
 */
class CredentialManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val prefs: PreferencesManager = PreferencesManager(context)

    companion object {
        private const val KEY_ALIAS = "dual_persona_credential_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CREDENTIAL_META_PREFIX = "cred_meta_"
    }

    // ===== Credential Metadata =====

    /**
     * Store credential metadata for a user profile
     * Note: We NEVER store the actual password/pattern
     * We only store metadata like type (PIN/Pattern/Fingerprint)
     * and hash identifier for verification
     */
    fun storeCredentialMeta(
        userSlot: Int,  // 0 = User A (Owner), 1 = User B
        credentialType: CredentialType,
        label: String
    ) {
        prefs.setCredentialType(userSlot, credentialType.name)
        prefs.setCredentialLabel(userSlot, label)
        SecurityLog.log(context, "SUCCESS", "set_credential",
            "User ${userSlot + 1}: $credentialType configured")
    }

    /**
     * Get the credential type configured for a user slot
     */
    fun getCredentialType(userSlot: Int): CredentialType {
        val typeName = prefs.getCredentialType(userSlot)
        return try {
            CredentialType.valueOf(typeName)
        } catch (e: Exception) {
            CredentialType.NONE
        }
    }

    /**
     * Check if a credential has been configured for a user slot
     */
    fun isCredentialConfigured(userSlot: Int): Boolean {
        return getCredentialType(userSlot) != CredentialType.NONE
    }

    // ===== Credential Verification (System Level) =====

    /**
     * Verify device credential using DevicePolicyManager
     * This checks if the CURRENT user has a valid lock screen credential
     */
    @SuppressLint("NewApi")
    fun hasDeviceCredential(): Boolean {
        return try {
            val method = android.app.admin.DevicePolicyManager::class.java.getMethod("isDeviceSecure")
            method.invoke(devicePolicyManager) as? Boolean ?: false
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            devicePolicyManager.isActivePasswordSufficient
        }
    }

    @Suppress("DEPRECATION")
    fun getPasswordQuality(): Int {
        return try {
            val method = android.app.admin.DevicePolicyManager::class.java.getMethod("getPasswordQuality")
            method.invoke(devicePolicyManager) as? Int ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getPasswordConstraints(): String? {
        return if (Build.VERSION.SDK_INT >= 34) {
            try {
                val method = android.app.admin.DevicePolicyManager::class.java.getMethod("getPasswordConstraints")
                method.invoke(devicePolicyManager)?.toString()
            } catch (e: Exception) { null }
        } else {
            null
        }
    }

    // ===== Biometric Integration =====

    /**
     * Check if biometric hardware is available
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Check biometric enrollment status
     */
    fun getBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.UPDATE_REQUIRED
            else -> BiometricStatus.UNKNOWN
        }
    }

    /**
     * Show biometric enrollment prompt
     * Used during setup to guide user to enroll fingerprint
     */
    suspend fun authenticateBiometric(
        title: String = "Authenticate",
        subtitle: String = "Use your fingerprint to continue"
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    SecurityLog.log(context, "SUCCESS", "biometric_auth", "Authentication succeeded")
                    continuation.resume(true)
                }

                override fun onAuthenticationFailed() {
                    SecurityLog.log(context, "FAILED", "biometric_auth", "Authentication failed")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    SecurityLog.log(context, "ERROR", "biometric_auth",
                        "Error $errorCode: $errString")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }

            // We need an activity for BiometricPrompt, but during setup
            // this will be called from SetupWizardActivity
            // The activity context is needed here
            val prompt = BiometricPrompt(
                context as androidx.fragment.app.FragmentActivity,
                context.mainExecutor,
                callback
            )
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    // ===== Credential Change Monitoring =====

    /**
     * Called when system password changes (via DeviceAdmin callback)
     * Logs the event and updates metadata
     */
    fun onPasswordChanged(userSlot: Int) {
        SecurityLog.log(context, "INFO", "password_changed",
            "Credential changed for User ${userSlot + 1}")
        prefs.setCredentialChangedTime(userSlot, System.currentTimeMillis())
    }

    /**
     * Called when password fails (via DeviceAdmin callback)
     */
    fun onPasswordFailed(attemptCount: Int) {
        SecurityLog.log(context, "WARNING", "password_failed",
            "Failed attempt #$attemptCount")

        // Trigger security measures after too many failures
        if (attemptCount >= prefs.getMaxFailedAttempts()) {
            SecurityLog.log(context, "CRITICAL", "lockout",
                "Device locked out after $attemptCount failed attempts")
        }
    }

    /**
     * Called when password succeeds
     */
    fun onPasswordSucceeded(userSlot: Int) {
        prefs.resetFailedAttempts(userSlot)
        SecurityLog.log(context, "SUCCESS", "password_success",
            "User ${userSlot + 1} authenticated")
    }

    // ===== Crypto Operations for Secure Storage =====

    /**
     * Get or create AES key for encrypting app data
     */
    @Suppress("DEPRECATION")
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        } else {
            throw UnsupportedOperationException("KeyStore requires API 23+")
        }

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data using AES-256-GCM
     */
    fun encrypt(data: String): String {
        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            // Combine IV + encrypted data
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "encrypt", "Encryption failed: ${e.message}")
            ""
        }
    }

    /**
     * Decrypt data using AES-256-GCM
     */
    fun decrypt(encryptedData: String): String {
        return try {
            val key = getOrCreateSecretKey()
            val combined = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)

            val iv = combined.copyOfRange(0, 12) // GCM IV is 12 bytes
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "decrypt", "Decryption failed: ${e.message}")
            ""
        }
    }

    // ===== Data Classes =====

    enum class CredentialType {
        NONE,           // No credential configured
        PIN,            // Numeric PIN
        PATTERN,        // Pattern lock
        PASSWORD,       // Alphanumeric password
        FINGERPRINT,    // Fingerprint only
        BIOMETRIC_PIN,  // Fingerprint + PIN backup
        BIOMETRIC_PATTERN // Fingerprint + Pattern backup
    }

    enum class BiometricStatus {
        ENROLLED,       // Fingerprint enrolled and ready
        NOT_ENROLLED,   // Hardware available, no fingerprint enrolled
        NO_HARDWARE,    // No biometric hardware
        HW_UNAVAILABLE, // Hardware temporarily unavailable
        UPDATE_REQUIRED,// Security update needed
        UNKNOWN         // Unknown status
    }
}
