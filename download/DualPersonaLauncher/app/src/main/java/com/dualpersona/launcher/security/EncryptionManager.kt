package com.dualpersona.launcher.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dualpersona.launcher.utils.SecurityConstants
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES-256-GCM encryption and PBKDF2 hashing manager.
 * Uses Android KeyStore for master key protection.
 */
class EncryptionManager(context: Context) {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val secureRandom = SecureRandom()

    init {
        ensureMasterKeyExists()
    }

    // ==================== Master Key Management ====================

    private fun ensureMasterKeyExists() {
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(SecurityConstants.AES_KEY_SIZE)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }

    private fun getMasterKey(): SecretKey {
        return keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
    }

    // ==================== PBKDF2 Hashing (for PINs) ====================

    /**
     * Hash a PIN using PBKDF2 with HMAC-SHA256.
     * Returns Base64-encoded string of salt:hash
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun hashPin(pin: String, existingSalt: String? = null): PinHashResult {
        val salt = existingSalt ?: generateSalt()

        val spec: KeySpec = PBEKeySpec(
            pin.toCharArray(),
            Base64.decode(salt),
            SecurityConstants.PBKDF2_ITERATIONS,
            SecurityConstants.PBKDF2_KEY_LENGTH
        )

        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded

        return PinHashResult(
            salt = salt,
            hash = Base64.encode(hash)
        )
    }

    /**
     * Verify a PIN against stored hash
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun verifyPin(pin: String, storedHash: String, salt: String): Boolean {
        val result = hashPin(pin, salt)
        return result.hash == storedHash
    }

    // ==================== AES-256-GCM Encryption/Decryption ====================

    /**
     * Encrypt plaintext using AES-256-GCM.
     * Returns Base64-encoded string of IV:ciphertext:tag
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(plaintext: String, key: SecretKey = getMasterKey()): String {
        val iv = ByteArray(SecurityConstants.IV_LENGTH).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine IV + encrypted data
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encode(combined)
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(ciphertext: String, key: SecretKey = getMasterKey()): String {
        val combined = Base64.decode(ciphertext)

        val iv = combined.copyOfRange(0, SecurityConstants.IV_LENGTH)
        val encrypted = combined.copyOfRange(SecurityConstants.IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    /**
     * Encrypt byte array (for file encryption)
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encryptBytes(data: ByteArray, key: SecretKey = getMasterKey()): ByteArray {
        val iv = ByteArray(SecurityConstants.IV_LENGTH).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val encrypted = cipher.doFinal(data)

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return combined
    }

    /**
     * Decrypt byte array (for file decryption)
     */
    fun decryptBytes(data: ByteArray, key: SecretKey = getMasterKey()): ByteArray {
        val iv = data.copyOfRange(0, SecurityConstants.IV_LENGTH)
        val encrypted = data.copyOfRange(SecurityConstants.IV_LENGTH, data.size)

        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        return cipher.doFinal(encrypted)
    }

    // ==================== Utility ====================

    fun generateSalt(): String {
        val salt = ByteArray(SecurityConstants.SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return Base64.encode(salt)
    }

    /**
     * Generate a random AES key (for data encryption keys)
     */
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(SecurityConstants.AES_KEY_SIZE, secureRandom)
        return keyGenerator.generateKey()
    }

    data class PinHashResult(
        val salt: String,
        val hash: String
    )

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "dual_persona_master_key"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
