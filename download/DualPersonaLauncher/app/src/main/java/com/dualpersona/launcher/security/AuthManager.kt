package com.dualpersona.launcher.security

import android.content.Context
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.PreferencesManager
import com.dualpersona.launcher.utils.SecurityConstants
import com.dualpersona.launcher.utils.PrefKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages authentication flow — verifies PIN/Pattern input
 * and determines which environment to unlock.
 *
 * Security features:
 * - Rate limiting on failed attempts
 * - Intrusion detection (camera capture)
 * - Self-destruct option after max attempts
 */
class AuthManager(private val context: Context) {

    private val encryptionManager = EncryptionManager(context)
    private val preferencesManager = PreferencesManager(context)

    // Track failed attempts in memory
    private var failedAttemptCount = 0
    private var lastFailedAttemptTime = 0L
    private var isLockedOut = false

    data class AuthResult(
        val success: Boolean,
        val environment: String = EnvironmentType.UNKNOWN,
        val error: AuthError? = null
    )

    sealed class AuthError {
        data object WrongPin : AuthError()
        data object WrongPattern : AuthError()
        data object AccountLocked : AuthError()
        data object TooManyAttempts : AuthError()
        data class SelfDestruct(val environment: String) : AuthError()
    }

    /**
     * Authenticate with a PIN code.
     * Checks against all configured environments and returns the matched one.
     */
    suspend fun authenticateWithPin(pin: String): AuthResult = withContext(Dispatchers.Default) {
        if (pin.length < SecurityConstants.MIN_PIN_LENGTH) {
            return@withContext AuthResult(
                success = false,
                error = AuthError.WrongPin
            )
        }

        // Check lockout
        if (isLockedOut) {
            return@withContext AuthResult(
                success = false,
                error = AuthError.AccountLocked
            )
        }

        // Check Primary PIN
        val primaryHash = preferencesManager.getPinHash(EnvironmentType.PRIMARY)
        val primarySalt = preferencesManager.getPinSalt(EnvironmentType.PRIMARY)
        if (primaryHash != null && primarySalt != null) {
            if (encryptionManager.verifyPin(pin, primaryHash, primarySalt)) {
                resetFailedAttempts()
                return@withContext AuthResult(
                    success = true,
                    environment = EnvironmentType.PRIMARY
                )
            }
        }

        // Check Hidden PIN
        val hiddenHash = preferencesManager.getPinHash(EnvironmentType.HIDDEN)
        val hiddenSalt = preferencesManager.getPinSalt(EnvironmentType.HIDDEN)
        if (hiddenHash != null && hiddenSalt != null) {
            if (encryptionManager.verifyPin(pin, hiddenHash, hiddenSalt)) {
                resetFailedAttempts()
                return@withContext AuthResult(
                    success = true,
                    environment = EnvironmentType.HIDDEN
                )
            }
        }

        // Check Emergency PIN
        val emergencyHash = preferencesManager.getPinHash(EnvironmentType.EMERGENCY)
        val emergencySalt = preferencesManager.getPinSalt(EnvironmentType.EMERGENCY)
        if (emergencyHash != null && emergencySalt != null) {
            if (encryptionManager.verifyPin(pin, emergencyHash, emergencySalt)) {
                resetFailedAttempts()
                return@withContext AuthResult(
                    success = true,
                    environment = EnvironmentType.EMERGENCY
                )
            }
        }

        // No match — increment failed attempts
        handleFailedAttempt()
    }

    /**
     * Authenticate with a pattern (list of cell indices)
     */
    suspend fun authenticateWithPattern(pattern: List<Int>): AuthResult = withContext(Dispatchers.Default) {
        if (pattern.size < 4) {
            return@withContext AuthResult(
                success = false,
                error = AuthError.WrongPattern
            )
        }

        if (isLockedOut) {
            return@withContext AuthResult(
                success = false,
                error = AuthError.AccountLocked
            )
        }

        val patternString = pattern.joinToString(",")

        // Check Primary Pattern
        val primaryPatternHash = preferencesManager.getPatternHash(EnvironmentType.PRIMARY)
        if (primaryPatternHash != null) {
            val result = encryptionManager.hashPin(patternString, primaryPatternHash.substringBefore(":"))
            if (result.hash == primaryPatternHash.substringAfter(":")) {
                resetFailedAttempts()
                return@withContext AuthResult(
                    success = true,
                    environment = EnvironmentType.PRIMARY
                )
            }
        }

        // Check Hidden Pattern
        val hiddenPatternHash = preferencesManager.getPatternHash(EnvironmentType.HIDDEN)
        if (hiddenPatternHash != null) {
            val result = encryptionManager.hashPin(patternString, hiddenPatternHash.substringBefore(":"))
            if (result.hash == hiddenPatternHash.substringAfter(":")) {
                resetFailedAttempts()
                return@withContext AuthResult(
                    success = true,
                    environment = EnvironmentType.HIDDEN
                )
            }
        }

        handleFailedAttempt()
    }

    /**
     * Setup a new PIN for an environment. Returns the hashed result for storage.
     */
    fun setupPin(pin: String, environment: String): Pair<String, String> {
        val result = encryptionManager.hashPin(pin)
        preferencesManager.setPinHash(environment, result.hash)
        preferencesManager.setPinSalt(environment, result.salt)
        return Pair(result.hash, result.salt)
    }

    /**
     * Setup a new pattern for an environment.
     */
    fun setupPattern(pattern: List<Int>, environment: String): Pair<String, String> {
        val patternString = pattern.joinToString(",")
        val result = encryptionManager.hashPin(patternString)
        preferencesManager.setPatternHash(environment, "${result.salt}:${result.hash}")
        return Pair(result.salt, result.hash)
    }

    /**
     * Change PIN for an environment. Verifies old PIN first.
     */
    suspend fun changePin(oldPin: String, newPin: String, environment: String): Boolean {
        val oldHash = preferencesManager.getPinHash(environment)
        val oldSalt = preferencesManager.getPinSalt(environment)
        if (oldHash == null || oldSalt == null) return false

        if (!encryptionManager.verifyPin(oldPin, oldHash, oldSalt)) {
            return false
        }

        val result = encryptionManager.hashPin(newPin)
        preferencesManager.setPinHash(environment, result.hash)
        preferencesManager.setPinSalt(environment, result.salt)
        return true
    }

    // ==================== Failed Attempt Handling ====================

    private fun handleFailedAttempt(): AuthResult {
        failedAttemptCount++
        lastFailedAttemptTime = System.currentTimeMillis()

        val maxAttempts = preferencesManager.maxFailedAttempts

        if (failedAttemptCount >= maxAttempts) {
            if (preferencesManager.isSelfDestructEnabled) {
                return AuthResult(
                    success = false,
                    error = AuthError.SelfDestruct(EnvironmentType.HIDDEN)
                )
            }
            isLockedOut = true
            return AuthResult(
                success = false,
                error = AuthError.TooManyAttempts
            )
        }

        return AuthResult(
            success = false,
            error = AuthError.WrongPin
        )
    }

    private fun resetFailedAttempts() {
        failedAttemptCount = 0
        isLockedOut = false
    }

    /**
     * Get current failed attempt count
     */
    fun getFailedAttemptCount(): Int = failedAttemptCount

    /**
     * Get remaining attempts before lockout
     */
    fun getRemainingAttempts(): Int {
        return preferencesManager.maxFailedAttempts - failedAttemptCount
    }

    /**
     * Check if account is locked
     */
    fun isAccountLocked(): Boolean = isLockedOut
}
