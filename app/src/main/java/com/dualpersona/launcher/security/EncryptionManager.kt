package com.dualpersona.launcher.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

class EncryptionManager(context: Context) {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val secureRandom = SecureRandom()

    init {
        ensureMasterKeyExists()
    }

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

    fun hashPin(pin: String, existingSalt: String? = null): PinHashResult {
        val salt = existingSalt ?: generateSalt()
        val spec: KeySpec = PBEKeySpec(
            pin.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            SecurityConstants.PBKDF2_ITERATIONS,
            SecurityConstants.PBKDF2_KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return PinHashResult(
            salt = salt,
            hash = Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    fun verifyPin(pin: String, storedHash: String, salt: String): Boolean {
        val result = hashPin(pin, salt)
        return result.hash == storedHash
    }

    fun encrypt(plaintext: String, key: SecretKey = getMasterKey()): String {
        val iv = ByteArray(SecurityConstants.IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(ciphertext: String, key: SecretKey = getMasterKey()): String {
        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, SecurityConstants.IV_LENGTH)
        val encrypted = combined.copyOfRange(SecurityConstants.IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

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

    fun decryptBytes(data: ByteArray, key: SecretKey = getMasterKey()): ByteArray {
        val iv = data.copyOfRange(0, SecurityConstants.IV_LENGTH)
        val encrypted = data.copyOfRange(SecurityConstants.IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(SecurityConstants.AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(SecurityConstants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        return cipher.doFinal(encrypted)
    }

    fun generateSalt(): String {
        val salt = ByteArray(SecurityConstants.SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

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
