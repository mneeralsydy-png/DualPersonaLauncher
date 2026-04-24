package com.dualpersona.launcher.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualpersona.launcher.R
import com.dualpersona.launcher.security.AuthManager
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.PreferencesManager
import com.dualpersona.launcher.utils.SecurityConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var preferencesManager: PreferencesManager

    private lateinit var titleText: TextView
    private lateinit var descText: TextView
    private lateinit var pinCard: View
    private lateinit var pinLabel: TextView
    private lateinit var inputPin: EditText

    private var currentPage = 0
    private var primaryPin = ""
    private var hiddenPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        authManager = AuthManager(this)
        preferencesManager = PreferencesManager(this)

        titleText = findViewById(R.id.setupTitle)
        descText = findViewById(R.id.setupDesc)
        pinCard = findViewById(R.id.pinCard)
        pinLabel = findViewById(R.id.pinLabel)
        inputPin = findViewById(R.id.inputPin)

        findViewById<View>(R.id.btnNext).setOnClickListener { onNextClicked() }
        findViewById<View>(R.id.btnBack).setOnClickListener { onBackClicked() }
        findViewById<View>(R.id.btnSkip).setOnClickListener { onSkipClicked() }

        showPage(0)
    }

    private fun showPage(page: Int) {
        currentPage = page
        inputPin.text.clear()
        pinCard.visibility = View.GONE

        when (page) {
            0 -> {
                titleText.text = getString(R.string.setup_welcome_title)
                descText.text = getString(R.string.setup_welcome_desc)
                descText.visibility = View.VISIBLE
            }
            1 -> {
                titleText.text = getString(R.string.setup_set_primary_pin)
                descText.visibility = View.GONE
                pinCard.visibility = View.VISIBLE
                pinLabel.text = "Primary Space PIN"
            }
            2 -> {
                titleText.text = getString(R.string.setup_confirm_pin)
                descText.visibility = View.GONE
                pinCard.visibility = View.VISIBLE
                pinLabel.text = "Confirm Primary PIN"
            }
            3 -> {
                titleText.text = getString(R.string.setup_set_hidden_pin)
                descText.visibility = View.GONE
                pinCard.visibility = View.VISIBLE
                pinLabel.text = "Hidden Space PIN"
            }
            4 -> {
                titleText.text = getString(R.string.setup_confirm_pin)
                descText.visibility = View.GONE
                pinCard.visibility = View.VISIBLE
                pinLabel.text = "Confirm Hidden PIN"
            }
            5 -> {
                titleText.text = getString(R.string.setup_emergency_pin)
                descText.text = getString(R.string.setup_emergency_desc)
                descText.visibility = View.VISIBLE
                pinCard.visibility = View.VISIBLE
                pinLabel.text = "Emergency PIN (optional)"
            }
            6 -> {
                titleText.text = getString(R.string.setup_enable_fingerprint)
                descText.text = "You can enable fingerprint login later in settings."
                descText.visibility = View.VISIBLE
            }
            7 -> {
                titleText.text = getString(R.string.setup_complete)
                descText.text = "All done! Your Dual Space is ready."
                descText.visibility = View.VISIBLE
            }
        }

        // Update nav buttons
        findViewById<View>(R.id.btnBack).visibility =
            if (page == 0) View.GONE else View.VISIBLE

        val isLastPage = page == 7
        val nextBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNext)
        nextBtn.text = if (isLastPage) getString(R.string.setup_finish) else getString(R.string.setup_next)

        val showSkip = page == 5 || page == 6
        findViewById<View>(R.id.btnSkip).visibility =
            if (showSkip) View.VISIBLE else View.GONE
    }

    private fun onNextClicked() {
        when (currentPage) {
            0 -> showPage(1)
            1 -> {
                val pin = inputPin.text.toString()
                if (pin.length < SecurityConstants.MIN_PIN_LENGTH) {
                    Toast.makeText(this, R.string.error_pin_too_short, Toast.LENGTH_SHORT).show()
                    return
                }
                primaryPin = pin
                showPage(2)
            }
            2 -> {
                val pin = inputPin.text.toString()
                if (pin != primaryPin) {
                    Toast.makeText(this, R.string.setup_pin_mismatch, Toast.LENGTH_SHORT).show()
                    return
                }
                authManager.setupPin(primaryPin, EnvironmentType.PRIMARY)
                showPage(3)
            }
            3 -> {
                val pin = inputPin.text.toString()
                if (pin.length < SecurityConstants.MIN_PIN_LENGTH) {
                    Toast.makeText(this, R.string.error_pin_too_short, Toast.LENGTH_SHORT).show()
                    return
                }
                hiddenPin = pin
                showPage(4)
            }
            4 -> {
                val pin = inputPin.text.toString()
                if (pin != hiddenPin) {
                    Toast.makeText(this, R.string.setup_pin_mismatch, Toast.LENGTH_SHORT).show()
                    return
                }
                authManager.setupPin(hiddenPin, EnvironmentType.HIDDEN)
                showPage(5)
            }
            5 -> {
                val pin = inputPin.text.toString()
                if (pin.length >= SecurityConstants.MIN_PIN_LENGTH) {
                    authManager.setupPin(pin, EnvironmentType.EMERGENCY)
                }
                showPage(6)
            }
            6 -> showPage(7)
            7 -> completeSetup()
        }
    }

    private fun onBackClicked() {
        if (currentPage > 0) showPage(currentPage - 1)
    }

    private fun onSkipClicked() {
        when (currentPage) {
            5 -> showPage(6)
            6 -> showPage(7)
        }
    }

    private fun completeSetup() {
        preferencesManager.isSetupComplete = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.dualpersona.launcher.engine.EnvironmentEngine(this@SetupWizardActivity)
                    .populateEnvironment(EnvironmentType.PRIMARY)
                com.dualpersona.launcher.engine.EnvironmentEngine(this@SetupWizardActivity)
                    .populateEnvironment(EnvironmentType.HIDDEN)
            } catch (_: Exception) {}
        }
        startActivity(Intent(this, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
