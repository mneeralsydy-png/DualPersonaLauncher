package com.dualpersona.launcher.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.security.AuthManager
import com.dualpersona.launcher.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Security Settings Activity.
 * Allows changing PIN and configuring security options.
 */
class SecuritySettingsActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_settings)

        authManager = AuthManager(this)
        preferencesManager = DualPersonaApp.getInstance().preferencesManager

        setupToolbar()
        setupSecurityOptions()
        setupChangePin()
    }

    private fun setupToolbar() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupSecurityOptions() {
        // Auto-lock switch
        findViewById<SwitchCompat>(R.id.switchAutoLock).apply {
            isChecked = preferencesManager.isAutoLockEnabled
            setOnCheckedChangeListener { _, isChecked ->
                preferencesManager.isAutoLockEnabled = isChecked
            }
        }

        // Intrusion detection
        findViewById<SwitchCompat>(R.id.switchIntrusion).apply {
            isChecked = preferencesManager.isIntrusionDetectionEnabled
            setOnCheckedChangeListener { _, isChecked ->
                preferencesManager.isIntrusionDetectionEnabled = isChecked
            }
        }

        // Self destruct
        findViewById<SwitchCompat>(R.id.switchSelfDestruct).apply {
            isChecked = preferencesManager.isSelfDestructEnabled
            setOnCheckedChangeListener { _, isChecked ->
                preferencesManager.isSelfDestructEnabled = isChecked
                if (isChecked) {
                    Toast.makeText(
                        this@SecuritySettingsActivity,
                        "Warning: Hidden space data will be permanently deleted after max failed attempts!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Max attempts display
        findViewById<TextView>(R.id.textMaxAttempts).text =
            preferencesManager.maxFailedAttempts.toString()
    }

    private fun setupChangePin() {
        findViewById<MaterialButton>(R.id.btnChangePin).setOnClickListener {
            val oldPin = findViewById<EditText>(R.id.inputOldPin).text.toString()
            val newPin = findViewById<EditText>(R.id.inputNewPin).text.toString()
            val confirmPin = findViewById<EditText>(R.id.inputConfirmNewPin).text.toString()

            if (oldPin.isEmpty() || newPin.isEmpty() || confirmPin.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPin != confirmPin) {
                Toast.makeText(this, "New PINs don't match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val env = DualPersonaApp.getInstance().environmentEngine.getCurrentEnvironment()

            lifecycleScope.launch {
                val success = authManager.changePin(oldPin, newPin, env)
                if (success) {
                    Toast.makeText(this@SecuritySettingsActivity, "PIN changed successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@SecuritySettingsActivity, "Wrong current PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}