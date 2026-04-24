package com.dualpersona.launcher.activities

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.engine.EnvironmentEngine
import com.dualpersona.launcher.security.StealthManager
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Environment Settings Activity.
 * Provides settings for the current environment including:
 * - Change PIN
 * - Toggle fingerprint
 * - Stealth mode
 * - App management
 * - Theme
 * - Backup & Restore
 */
class EnvironmentSettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var environmentEngine: EnvironmentEngine
    private lateinit var stealthManager: StealthManager

    private var currentEnvironment = EnvironmentType.PRIMARY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_environment_settings)

        val app = DualPersonaApp.getInstance()
        preferencesManager = app.preferencesManager
        environmentEngine = app.environmentEngine
        stealthManager = StealthManager(this)
        currentEnvironment = environmentEngine.getCurrentEnvironment()

        setupViews()
        setupListeners()
        updateSpaceBadge()
    }

    private fun setupViews() {
        // Update labels based on environment
        val changePinLabel = findViewById<TextView>(R.id.changePinLabel)
        changePinLabel.text = when (currentEnvironment) {
            EnvironmentType.PRIMARY -> getString(R.string.settings_change_primary_pin)
            EnvironmentType.HIDDEN -> getString(R.string.settings_change_hidden_pin)
            EnvironmentType.EMERGENCY -> getString(R.string.settings_change_emergency_pin)
            else -> "Change PIN"
        }

        // Set switch states
        findViewById<SwitchCompat>(R.id.switchFingerprint).isChecked =
            preferencesManager.isFingerprintEnabled
        findViewById<SwitchCompat>(R.id.switchStealth).isChecked =
            stealthManager.isStealthModeActive()
    }

    private fun setupListeners() {
        // Change PIN
        findViewById<LinearLayout>(R.id.btnChangePin).setOnClickListener {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }

        // Fingerprint toggle
        findViewById<SwitchCompat>(R.id.switchFingerprint).setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isFingerprintEnabled = isChecked
            Toast.makeText(this, if (isChecked) "Fingerprint enabled" else "Fingerprint disabled", Toast.LENGTH_SHORT).show()
        }

        // Security Settings
        findViewById<LinearLayout>(R.id.btnSecuritySettings).setOnClickListener {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }

        // Stealth Mode
        findViewById<LinearLayout>(R.id.btnStealthMode).setOnClickListener {
            val switch = findViewById<SwitchCompat>(R.id.switchStealth)
            switch.isChecked = !switch.isChecked
        }
        findViewById<SwitchCompat>(R.id.switchStealth).setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                if (isChecked) {
                    stealthManager.enableStealthMode()
                    Toast.makeText(this@EnvironmentSettingsActivity, "Stealth mode enabled. Dial *#*#1234#*#* to open.", Toast.LENGTH_LONG).show()
                } else {
                    stealthManager.disableStealthMode()
                    Toast.makeText(this@EnvironmentSettingsActivity, "Stealth mode disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // App Manager
        findViewById<LinearLayout>(R.id.btnAppManager).setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }

        // Theme Settings
        findViewById<LinearLayout>(R.id.btnThemeSettings).setOnClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }

        // Backup
        findViewById<LinearLayout>(R.id.btnBackup).setOnClickListener {
            startActivity(Intent(this, BackupRestoreActivity::class.java))
        }

        // Restore
        findViewById<LinearLayout>(R.id.btnRestore).setOnClickListener {
            startActivity(Intent(this, BackupRestoreActivity::class.java))
        }
    }

    private fun updateSpaceBadge() {
        val badgeText = findViewById<TextView>(R.id.badgeText)
        val badgeDot = findViewById<com.google.android.material.card.MaterialCardView>(R.id.spaceBadge)
            ?: return

        val (label, colorRes) = when (currentEnvironment) {
            EnvironmentType.PRIMARY -> "Primary Space" to R.color.space_primary_badge
            EnvironmentType.HIDDEN -> "Hidden Space" to R.color.space_hidden_badge
            EnvironmentType.EMERGENCY -> "Emergency Space" to R.color.space_emergency_badge
            else -> "Unknown" to R.color.space_primary_badge
        }
        badgeText.text = label
    }
}