package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
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
 * CredentialManager - يدير بيانات اعتماد شاشة القفل
 *
 * يعمل مع شاشة قفل Android الأصلية - لا يستبدلها.
 * لا يستخدم أي API مخفي أو انعكاس.
 *
 * كل مستخدم Android يحدد بيانات اعتماده الخاصة من خلال:
 * الإعدادات > الأمان > قفل الشاشة
 */
class CredentialManager(private val context: Context) {

    // استخدام KeyguardManager بدلاً من الانعكاس - آمن 100%
    private val keyguardManager: KeyguardManager? = try {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    } catch (e: Exception) {
        null
    }

    private val prefs: PreferencesManager = PreferencesManager(context)

    companion object {
        private const val KEY_ALIAS = "dual_persona_credential_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    // ===== بيانات الاعتماد الوصفية =====

    fun storeCredentialMeta(
        userSlot: Int,
        credentialType: CredentialType,
        label: String
    ) {
        prefs.setCredentialType(userSlot, credentialType.name)
        prefs.setCredentialLabel(userSlot, label)
        SecurityLog.log(context, "SUCCESS", "set_credential",
            "User ${userSlot + 1}: $credentialType configured")
    }

    fun getCredentialType(userSlot: Int): CredentialType {
        val typeName = prefs.getCredentialType(userSlot)
        return try {
            CredentialType.valueOf(typeName)
        } catch (e: Exception) {
            CredentialType.NONE
        }
    }

    fun isCredentialConfigured(userSlot: Int): Boolean {
        return getCredentialType(userSlot) != CredentialType.NONE
    }

    // ===== التحقق من بيانات الاعتماد (عبر API عام فقط) =====

    /**
     * التحقق مما إذا كان الجهاز مزوداً بقفل شاشة.
     * يستخدم KeyguardManager.isDeviceSecure() - API عام.
     */
    @SuppressLint("NewApi")
    fun hasDeviceCredential(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyguardManager?.isDeviceSecure == true
            } else {
                keyguardManager?.isKeyguardSecure == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * الكشف عن نوع قفل الشاشة الحالي.
     * يستخدم API عام فقط.
     */
    fun detectCredentialType(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (keyguardManager?.isDeviceSecure == true) {
                    if (getBiometricStatus() == BiometricStatus.ENROLLED) {
                        return "FINGERPRINT_PIN"
                    }
                    return "PIN"
                }
            }
            "NONE"
        } catch (e: Exception) {
            "NONE"
        }
    }

    // ===== التكامل مع البصمة =====

    fun isBiometricAvailable(): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    fun getBiometricStatus(): BiometricStatus {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.UPDATE_REQUIRED
                else -> BiometricStatus.UNKNOWN
            }
        } catch (e: Exception) {
            BiometricStatus.UNKNOWN
        }
    }

    /**
     * فتح إعدادات الأمان لتعيين قفل الشاشة.
     */
    fun openSecuritySettings(): Boolean {
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.LOCK_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // تجربة الطريقة التالية
            }
        }
        return false
    }

    /**
     * فتح إعدادات البصمة لتسجيل بصمة جديدة.
     */
    fun openBiometricEnrollment(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

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

    // ===== مراقبة تغييرات كلمة المرور =====

    fun onPasswordChanged(userSlot: Int) {
        SecurityLog.log(context, "INFO", "password_changed",
            "Credential changed for User ${userSlot + 1}")
        prefs.setCredentialChangedTime(userSlot, System.currentTimeMillis())
    }

    fun onPasswordFailed(attemptCount: Int) {
        SecurityLog.log(context, "WARNING", "password_failed",
            "Failed attempt #$attemptCount")
        if (attemptCount >= prefs.getMaxFailedAttempts()) {
            SecurityLog.log(context, "CRITICAL", "lockout",
                "Device locked out after $attemptCount failed attempts")
        }
    }

    fun onPasswordSucceeded(userSlot: Int) {
        prefs.resetFailedAttempts(userSlot)
        SecurityLog.log(context, "SUCCESS", "password_success",
            "User ${userSlot + 1} authenticated")
    }

    // ===== عمليات التشفير =====

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

    fun encrypt(data: String): String {
        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            SecurityLog.log(context, "ERROR", "encrypt", "Encryption failed: ${e.message}")
            ""
        }
    }

    fun decrypt(encryptedData: String): String {
        return try {
            val key = getOrCreateSecretKey()
            val combined = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)

            val iv = combined.copyOfRange(0, 12)
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

    // ===== أنواع البيانات =====

    enum class CredentialType {
        NONE,
        PIN,
        PATTERN,
        PASSWORD,
        FINGERPRINT,
        BIOMETRIC_PIN,
        BIOMETRIC_PATTERN
    }

    enum class BiometricStatus {
        ENROLLED,
        NOT_ENROLLED,
        NO_HARDWARE,
        HW_UNAVAILABLE,
        UPDATE_REQUIRED,
        UNKNOWN
    }
}
