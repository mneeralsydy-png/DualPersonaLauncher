package com.dualpersona.launcher.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dualpersona.launcher.R
import com.dualpersona.launcher.engine.EnvironmentEngine
import com.dualpersona.launcher.security.AuthManager
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.IntentExtras
import com.dualpersona.launcher.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Custom Lock Screen Activity.
 *
 * This is the primary entry point for unlocking the device.
 * It displays a PIN keypad and determines which environment
 * (Primary, Hidden, or Emergency) to open based on the input.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var environmentEngine: EnvironmentEngine
    private lateinit var preferencesManager: PreferencesManager

    // Views
    private lateinit var dotViews: List<View>
    private lateinit var textError: TextView
    private lateinit var textAttempts: TextView
    private lateinit var textEnvironmentLabel: TextView
    private lateinit var btnFingerprint: View
    private lateinit var btnDelete: View
    private lateinit var btnEmergency: View

    // State
    private var enteredPin = StringBuilder()
    private var targetEnvironment: String? = null
    private var isSwitching = false
    private var maxPinLength = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        initManagers()
        initViews()
        initKeypad()
        handleIntent()
        setupFingerprint()
    }

    private fun initManagers() {
        authManager = AuthManager(this)
        environmentEngine = EnvironmentEngine(this)
        preferencesManager = PreferencesManager(this)
        maxPinLength = preferencesManager.maxFailedAttempts.coerceAtMost(6)
    }

    private fun initViews() {
        dotViews = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4),
            findViewById(R.id.dot5),
            findViewById(R.id.dot6)
        )
        textError = findViewById(R.id.textError)
        textAttempts = findViewById(R.id.textAttempts)
        textEnvironmentLabel = findViewById(R.id.textEnvironmentLabel)
        btnFingerprint = findViewById(R.id.btnFingerprint)
        btnDelete = findViewById(R.id.btnDelete)
        btnEmergency = findViewById(R.id.btnEmergency)

        // Show 6 dots if needed
        if (maxPinLength > 4) {
            dotViews[4].visibility = View.VISIBLE
            dotViews[5].visibility = View.VISIBLE
        }
    }

    private fun handleIntent() {
        targetEnvironment = intent.getStringExtra(IntentExtras.ENVIRONMENT_TYPE)
        isSwitching = intent.getBooleanExtra(IntentExtras.IS_SWITCHING, false)

        if (isSwitching && targetEnvironment != null) {
            textEnvironmentLabel.text = getString(R.string.switch_confirm)
            textEnvironmentLabel.visibility = View.VISIBLE
        }

        // Show emergency button if emergency PIN is set
        if (preferencesManager.getPinHash(EnvironmentType.EMERGENCY) != null) {
            btnEmergency.visibility = View.VISIBLE
        }
    }

    private fun initKeypad() {
        val buttonIds = listOf(
            R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6,
            R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btn0
        )

        buttonIds.forEach { id ->
            findViewById<MaterialButton>(id)?.setOnClickListener {
                onNumberPressed(id.toString().removePrefix("btn"))
            }
        }

        btnDelete.setOnClickListener { onDeletePressed() }

        btnEmergency.setOnClickListener {
            Toast.makeText(this, "Emergency mode activated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onNumberPressed(number: String) {
        if (enteredPin.length >= maxPinLength) return

        vibrate(50)
        enteredPin.append(number)
        updateDots()

        if (enteredPin.length == maxPinLength) {
            Handler(Looper.getMainLooper()).postDelayed({ authenticatePin() }, 200)
        }
    }

    private fun onDeletePressed() {
        if (enteredPin.isNotEmpty()) {
            vibrate(30)
            enteredPin.deleteCharAt(enteredPin.length - 1)
            updateDots()
            hideError()
        }
    }

    private fun updateDots() {
        dotViews.forEachIndexed { index, dot ->
            if (index < maxPinLength) {
                if (index < enteredPin.length) {
                    dot.setBackgroundResource(R.drawable.pin_dot_active)
                    animateDotPop(dot)
                } else {
                    dot.setBackgroundResource(R.drawable.pin_dot_inactive)
                }
            }
        }
    }

    private fun animateDotPop(dot: View) {
        val scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 0.5f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 0.5f, 1.2f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 200
            interpolator = OvershootInterpolator()
            start()
        }
    }

    private fun authenticatePin() {
        val pin = enteredPin.toString()
        lifecycleScope.launch {
            val result = authManager.authenticateWithPin(pin)

            if (result.success) {
                hideError()
                onAuthSuccess(result.environment)
            } else {
                onAuthFailure(result.error)
            }
        }
    }

    private fun onAuthSuccess(environment: String) {
        vibrate(100)
        animateSuccess()

        Handler(Looper.getMainLooper()).postDelayed({
            environmentEngine.applyEnvironment(environment)

            // Navigate to launcher home
            val intent = Intent(this, LauncherHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(IntentExtras.ENVIRONMENT_TYPE, environment)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 300)
    }

    private fun onAuthFailure(error: AuthManager.AuthError?) {
        vibrate(500)
        showError()

        when (error) {
            is AuthManager.AuthError.WrongPin -> {
                textError.text = getString(R.string.lock_screen_error_wrong_pin)
            }
            is AuthManager.AuthError.TooManyAttempts -> {
                textError.text = "Too many attempts. Please try again later."
                disableKeypad()
            }
            is AuthManager.AuthError.AccountLocked -> {
                textError.text = "Account locked. Wait before trying again."
                disableKeypad()
            }
            is AuthManager.AuthError.SelfDestruct -> {
                textError.text = "Security alert: Hidden space data will be wiped."
                triggerSelfDestruct(error.environment)
                return
            }
            else -> {
                textError.text = getString(R.string.lock_screen_error_wrong_pin)
            }
        }

        // Update attempts display
        val remaining = authManager.getRemainingAttempts()
        if (remaining <= 3 && remaining > 0) {
            textAttempts.text = "$remaining attempts remaining"
            textAttempts.visibility = View.VISIBLE
        }

        // Clear PIN after delay
        Handler(Looper.getMainLooper()).postDelayed({
            clearPin()
        }, 1500)
    }

    private fun showError() {
        textError.visibility = View.VISIBLE

        // Animate dots to error state
        dotViews.forEach { dot ->
            if (dot.visibility == View.VISIBLE) {
                dot.setBackgroundResource(R.drawable.pin_dot_error)
            }
        }

        // Shake animation
        val shakeX = ObjectAnimator.ofFloat(
            findViewById<View>(R.id.pinDotsContainer),
            "translationX",
            0f, -20f, 20f, -15f, 15f, -10f, 10f, 0f
        )
        shakeX.duration = 500
        shakeX.start()
    }

    private fun hideError() {
        textError.visibility = View.INVISIBLE
        textAttempts.visibility = View.INVISIBLE
    }

    private fun clearPin() {
        enteredPin.clear()
        updateDots()
    }

    private fun animateSuccess() {
        dotViews.forEach { dot ->
            if (dot.visibility == View.VISIBLE && dot.tag != null) {
                dot.setBackgroundResource(R.drawable.pin_dot_active)
            }
        }
    }

    private fun disableKeypad() {
        val keypad = findViewById<View>(R.id.keypadContainer)
        keypad.alpha = 0.3f
        keypad.isEnabled = false
    }

    private fun triggerSelfDestruct(environment: String) {
        Toast.makeText(
            this,
            "Self-destruct triggered for security",
            Toast.LENGTH_LONG
        ).show()

        lifecycleScope.launch {
            try {
                com.dualpersona.launcher.isolation.DataIsolationManager(this@LockScreenActivity)
                    .wipeEnvironment(environment)
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    // ==================== Fingerprint ====================

    private fun setupFingerprint() {
        if (!preferencesManager.isFingerprintEnabled) {
            btnFingerprint.visibility = View.GONE
            return
        }

        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                btnFingerprint.visibility = View.VISIBLE
                btnFingerprint.setOnClickListener { authenticateWithFingerprint() }
            }
            else -> {
                btnFingerprint.visibility = View.GONE
            }
        }
    }

    private fun authenticateWithFingerprint() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Fingerprint opens the primary space by default
                    onAuthSuccess(EnvironmentType.PRIMARY)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    vibrate(200)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Authentication")
            .setSubtitle("Use your fingerprint to unlock")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // ==================== Utility ====================

    private fun vibrate(durationMs: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing lock screen
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
        handleIntent()
        clearPin()
    }
}
