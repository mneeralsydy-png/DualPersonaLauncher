package com.dualpersona.launcher.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.dualpersona.launcher.R
import com.dualpersona.launcher.security.AuthManager
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.PreferencesManager
import com.dualpersona.launcher.utils.SecurityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Setup Wizard Activity — guides user through initial setup:
 * 1. Welcome screen
 * 2. Set Primary PIN
 * 3. Confirm Primary PIN
 * 4. Set Hidden PIN
 * 5. Confirm Hidden PIN
 * 6. Emergency PIN (optional)
 * 7. Fingerprint (optional)
 * 8. Complete
 */
class SetupWizardActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var authManager: AuthManager
    private lateinit var preferencesManager: PreferencesManager

    private var primaryPin = ""
    private var hiddenPin = ""
    private var emergencyPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        authManager = AuthManager(this)
        preferencesManager = PreferencesManager(this)

        viewPager = findViewById(R.id.setupViewPager)
        viewPager.isUserInputEnabled = false // Disable swipe

        findViewById<Button>(R.id.btnNext).setOnClickListener { onNextClicked() }
        findViewById<Button>(R.id.btnBack).setOnClickListener { onBackClicked() }
        findViewById<Button>(R.id.btnSkip).setOnClickListener { onSkipClicked() }
    }

    private fun onNextClicked() {
        val currentPage = viewPager.currentItem

        when (currentPage) {
            PAGE_WELCOME -> viewPager.currentItem = PAGE_SET_PRIMARY_PIN
            PAGE_SET_PRIMARY_PIN -> {
                val pin = getPinInput(R.id.inputPrimaryPin)
                if (!validatePin(pin)) return
                primaryPin = pin
                viewPager.currentItem = PAGE_CONFIRM_PRIMARY_PIN
            }
            PAGE_CONFIRM_PRIMARY_PIN -> {
                val pin = getPinInput(R.id.inputConfirmPrimaryPin)
                if (pin != primaryPin) {
                    Toast.makeText(this, R.string.setup_pin_mismatch, Toast.LENGTH_SHORT).show()
                    return
                }
                authManager.setupPin(primaryPin, EnvironmentType.PRIMARY)
                viewPager.currentItem = PAGE_SET_HIDDEN_PIN
            }
            PAGE_SET_HIDDEN_PIN -> {
                val pin = getPinInput(R.id.inputHiddenPin)
                if (!validatePin(pin)) return
                hiddenPin = pin
                viewPager.currentItem = PAGE_CONFIRM_HIDDEN_PIN
            }
            PAGE_CONFIRM_HIDDEN_PIN -> {
                val pin = getPinInput(R.id.inputConfirmHiddenPin)
                if (pin != hiddenPin) {
                    Toast.makeText(this, R.string.setup_pin_mismatch, Toast.LENGTH_SHORT).show()
                    return
                }
                authManager.setupPin(hiddenPin, EnvironmentType.HIDDEN)
                viewPager.currentItem = PAGE_EMERGENCY_PIN
            }
            PAGE_EMERGENCY_PIN -> {
                val pin = getPinInput(R.id.inputEmergencyPin)
                if (pin.isNotEmpty() && pin.length >= SecurityConstants.MIN_PIN_LENGTH) {
                    emergencyPin = pin
                    authManager.setupPin(emergencyPin, EnvironmentType.EMERGENCY)
                }
                viewPager.currentItem = PAGE_FINGERPRINT
            }
            PAGE_FINGERPRINT -> {
                viewPager.currentItem = PAGE_COMPLETE
            }
            PAGE_COMPLETE -> {
                completeSetup()
            }
        }

        updateNavButtons()
    }

    private fun onBackClicked() {
        val currentPage = viewPager.currentItem
        if (currentPage > PAGE_WELCOME) {
            viewPager.currentItem = currentPage - 1
            updateNavButtons()
        }
    }

    private fun onSkipClicked() {
        val currentPage = viewPager.currentItem
        when (currentPage) {
            PAGE_EMERGENCY_PIN -> viewPager.currentItem = PAGE_FINGERPRINT
            PAGE_FINGERPRINT -> viewPager.currentItem = PAGE_COMPLETE
        }
        updateNavButtons()
    }

    private fun validatePin(pin: String): Boolean {
        if (pin.length < SecurityConstants.MIN_PIN_LENGTH) {
            Toast.makeText(this, R.string.error_pin_too_short, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun getPinInput(editTextId: Int): String {
        return findViewById<EditText>(editTextId).text.toString()
    }

    private fun updateNavButtons() {
        val page = viewPager.currentItem
        findViewById<View>(R.id.btnBack).visibility =
            if (page == PAGE_WELCOME) View.GONE else View.VISIBLE

        val isLastPage = page == PAGE_COMPLETE
        findViewById<Button>(R.id.btnNext).text =
            if (isLastPage) getString(R.string.setup_finish) else getString(R.string.setup_next)

        val showSkip = page == PAGE_EMERGENCY_PIN || page == PAGE_FINGERPRINT
        findViewById<View>(R.id.btnSkip).visibility =
            if (showSkip) View.VISIBLE else View.GONE
    }

    private fun completeSetup() {
        preferencesManager.isSetupComplete = true

        CoroutineScope(Dispatchers.IO).launch {
            // Populate primary environment with installed apps
            try {
                com.dualpersona.launcher.engine.EnvironmentEngine(this@SetupWizardActivity)
                    .populateEnvironment(EnvironmentType.PRIMARY)
                // Also populate hidden with same apps
                com.dualpersona.launcher.engine.EnvironmentEngine(this@SetupWizardActivity)
                    .populateEnvironment(EnvironmentType.HIDDEN)
            } catch (_: Exception) {}
        }

        startActivity(Intent(this, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    companion object {
        const val PAGE_WELCOME = 0
        const val PAGE_SET_PRIMARY_PIN = 1
        const val PAGE_CONFIRM_PRIMARY_PIN = 2
        const val PAGE_SET_HIDDEN_PIN = 3
        const val PAGE_CONFIRM_HIDDEN_PIN = 4
        const val PAGE_EMERGENCY_PIN = 5
        const val PAGE_FINGERPRINT = 6
        const val PAGE_COMPLETE = 7
    }
}
