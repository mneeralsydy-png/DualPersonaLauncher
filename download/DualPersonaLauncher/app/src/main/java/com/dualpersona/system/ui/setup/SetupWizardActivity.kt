package com.dualpersona.system.ui.setup

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualpersona.system.R
import com.dualpersona.system.core.*
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import com.dualpersona.system.receiver.DualPersonaAdmin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SetupWizardActivity - Multi-step setup wizard
 *
 * Guides the user through:
 * Step 1: Welcome & Overview
 * Step 2: Permissions (Device Admin, Accessibility)
 * Step 3: User A Configuration (name, credential type)
 * Step 4: User B Configuration (create secondary user, credential type)
 * Step 5: Security Settings (secret code, stealth mode)
 * Step 6: Confirmation & Finalize
 */
class SetupWizardActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var userManager: SystemUserManager
    private lateinit var credentialManager: CredentialManager
    private lateinit var stealthManager: StealthManager

    private var currentStep = 0
    private val totalSteps = 6

    // ===== Device Admin Request =====
    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            SecurityLog.log(this, "SUCCESS", "device_admin", "Device admin granted")
            updateStep()
        } else {
            Toast.makeText(this, "Device Admin is required for system integration", Toast.LENGTH_LONG).show()
        }
    }

    // ===== Set Lock Screen Credential =====
    private val setCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // User returned from system settings
        updateStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        userManager = SystemUserManager(this)
        credentialManager = CredentialManager(this)
        stealthManager = StealthManager(this)

        currentStep = prefs.getCurrentSetupStep()

        if (prefs.isSetupComplete()) {
            // Setup already complete - check stealth mode
            if (prefs.isStealthModeEnabled()) {
                // App is in stealth mode - show nothing, finish
                finish()
                return
            }
            // Show a brief confirmation
            showSetupCompleteDialog()
            return
        }

        showStep(currentStep)
    }

    // ===== Step Rendering =====

    private fun showStep(step: Int) {
        currentStep = step
        prefs.setCurrentSetupStep(step)

        when (step) {
            0 -> showWelcomeStep()
            1 -> showPermissionsStep()
            2 -> showUserAConfigStep()
            3 -> showUserBConfigStep()
            4 -> showSecurityStep()
            5 -> showFinalizeStep()
            else -> finish()
        }
    }

    private fun updateStep() {
        showStep(currentStep + 1)
    }

    // ===== STEP 0: Welcome =====
    private fun showWelcomeStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)

        tvTitle.text = "Dual Persona System"
        tvDesc.text = getString(R.string.setup_welcome_desc)
        tvStep.text = "1/$totalSteps"
        progressBar.progress = (1 * 100) / totalSteps

        btnBack.visibility = View.GONE
        btnNext.text = getString(R.string.get_started)
        btnNext.setOnClickListener { showStep(1) }
    }

    // ===== STEP 1: Permissions =====
    private fun showPermissionsStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_permissions_title)
        tvStep.text = "2/$totalSteps"
        progressBar.progress = (2 * 100) / totalSteps

        // Build permission checklist
        containerExtra.removeAllViews()
        val permChecks = mutableListOf<android.widget.CheckBox>()

        val permissions = listOf(
            "Device Administrator" to "Required for system-level user management",
            "Notification Access" to "For security alerts and service management",
            "Accessibility Service" to "For enhanced data monitoring (optional)"
        )

        for ((name, desc) in permissions) {
            val cb = android.widget.CheckBox(this).apply {
                text = name
                textSize = 16f
                setPadding(0, 24, 0, 8)
                isEnabled = false
            }
            containerExtra.addView(cb)
            permChecks.add(cb)

            val tv = android.widget.TextView(this).apply {
                text = desc
                textSize = 12f
                setPadding(48, 0, 0, 16)
                setTextColor(getColor(android.R.color.darker_gray))
            }
            containerExtra.addView(tv)
        }

        // Check current states
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DualPersonaAdmin::class.java)
        permChecks[0].isChecked = dpm.isAdminActive(adminComponent)

        tvDesc.text = getString(R.string.setup_permissions_desc)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(0) }

        btnNext.text = getString(R.string.grant_permissions)
        btnNext.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                // Request Device Admin
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Dual Persona System needs Device Admin to manage user profiles and system-level features."
                    )
                }
                deviceAdminLauncher.launch(intent)
            } else {
                showStep(2)
            }
        }

        // Auto-advance if all permissions granted
        if (dpm.isAdminActive(adminComponent)) {
            btnNext.text = getString(R.string.next)
            btnNext.setOnClickListener { showStep(2) }
        }
    }

    // ===== STEP 2: User A Configuration =====
    private fun showUserAConfigStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_user_a_title)
        tvStep.text = "3/$totalSteps"
        progressBar.progress = (3 * 100) / totalSteps
        tvDesc.text = getString(R.string.setup_user_a_desc)

        containerExtra.removeAllViews()

        // Profile name input
        containerExtra.addView(createLabel("Profile Name (User A):"))
        val etName = android.widget.EditText(this).apply {
            hint = "e.g., Personal"
            setText(prefs.getProfileName(0))
            setPadding(0, 16, 0, 32)
        }
        containerExtra.addView(etName)

        // Credential type selector
        containerExtra.addView(createLabel("Lock Screen Credential:"))
        val credTypes = arrayOf("PIN", "Pattern", "Password", "Fingerprint + PIN", "Fingerprint + Pattern")
        val spinner = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupWizardActivity,
                android.R.layout.simple_spinner_item, credTypes)
            setPadding(0, 16, 0, 32)
        }
        containerExtra.addView(spinner)

        // Info text
        val tvInfo = android.widget.TextView(this).apply {
            text = getString(R.string.setup_credential_info)
            textSize = 13f
            setTextColor(getColor(android.R.color.holo_blue_dark))
            setPadding(0, 16, 0, 16)
        }
        containerExtra.addView(tvInfo)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(1) }

        btnNext.text = getString(R.string.set_and_continue)
        btnNext.setOnClickListener {
            val name = etName.text.toString().ifBlank { "User A" }
            prefs.setProfileName(0, name)

            val credType = when (spinner.selectedItemPosition) {
                0 -> CredentialManager.CredentialType.PIN
                1 -> CredentialManager.CredentialType.PATTERN
                2 -> CredentialManager.CredentialType.PASSWORD
                3 -> CredentialManager.CredentialType.BIOMETRIC_PIN
                4 -> CredentialManager.CredentialType.BIOMETRIC_PATTERN
                else -> CredentialManager.CredentialType.PIN
            }
            credentialManager.storeCredentialMeta(0, credType, name)

            // Open system security settings to set lock screen credential
            openSecuritySettings()

            // Continue after return
            showStep(3)
        }
    }

    // ===== STEP 3: User B Configuration =====
    private fun showUserBConfigStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_user_b_title)
        tvStep.text = "4/$totalSteps"
        progressBar.progress = (4 * 100) / totalSteps

        // Check multi-user support
        if (!userManager.isMultiUserSupported()) {
            tvDesc.text = getString(R.string.setup_multi_user_not_supported)
            btnNext.text = getString(R.string.skip)
            btnNext.setOnClickListener { showStep(4) }
            btnBack.setOnClickListener { showStep(2) }
            return
        }

        tvDesc.text = getString(R.string.setup_user_b_desc)
        containerExtra.removeAllViews()

        // Profile name input
        containerExtra.addView(createLabel("Profile Name (User B):"))
        val etName = android.widget.EditText(this).apply {
            hint = "e.g., Work"
            setText(prefs.getProfileName(1))
            setPadding(0, 16, 0, 32)
        }
        containerExtra.addView(etName)

        // Credential type selector
        containerExtra.addView(createLabel("Lock Screen Credential:"))
        val credTypes = arrayOf("PIN", "Pattern", "Password", "Fingerprint + PIN", "Fingerprint + Pattern")
        val spinner = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupWizardActivity,
                android.R.layout.simple_spinner_item, credTypes)
            setPadding(0, 16, 0, 32)
        }
        containerExtra.addView(spinner)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(2) }

        btnNext.text = getString(R.string.create_user_b)
        btnNext.setOnClickListener {
            val name = etName.text.toString().ifBlank { "User B" }
            prefs.setProfileName(1, name)

            val credType = when (spinner.selectedItemPosition) {
                0 -> CredentialManager.CredentialType.PIN
                1 -> CredentialManager.CredentialType.PATTERN
                2 -> CredentialManager.CredentialType.PASSWORD
                3 -> CredentialManager.CredentialType.BIOMETRIC_PIN
                4 -> CredentialManager.CredentialType.BIOMETRIC_PATTERN
                else -> CredentialManager.CredentialType.PIN
            }
            credentialManager.storeCredentialMeta(1, credType, name)

            // Create secondary user
            lifecycleScope.launch {
                showProgress("Creating User B...")
                val result = userManager.createSecondaryUser(name)
                result.onFailure {
                    Toast.makeText(this@SetupWizardActivity,
                        "Failed to create user: ${it.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Apply initial restrictions
                val handle = prefs.getSecondaryUserHandle()
                if (handle != null) {
                    userManager.applyUserRestrictions(handle)
                }

                SecurityLog.log(this@SetupWizardActivity, "SUCCESS", "setup_user_b",
                    "User B '$name' created successfully")

                hideProgress()
                showStep(4)
            }
        }
    }

    // ===== STEP 4: Security Settings =====
    private fun showSecurityStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_security_title)
        tvStep.text = "5/$totalSteps"
        progressBar.progress = (5 * 100) / totalSteps
        tvDesc.text = getString(R.string.setup_security_desc)

        containerExtra.removeAllViews()

        // Secret code input
        containerExtra.addView(createLabel("Secret Access Code (dial *#*#CODE#*#*):"))
        val etSecret = android.widget.EditText(this).apply {
            hint = "4-digit code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setText(stealthManager.getSecretCode())
            setPadding(0, 16, 0, 32)
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        containerExtra.addView(etSecret)

        // Enable stealth mode toggle
        val cbStealth = android.widget.CheckBox(this).apply {
            text = "Enable Stealth Mode (hide app after setup)"
            textSize = 16f
            isChecked = true // Recommended
            setPadding(0, 16, 0, 8)
        }
        containerExtra.addView(cbStealth)

        val tvStealthInfo = android.widget.TextView(this).apply {
            text = getString(R.string.setup_stealth_info)
            textSize = 12f
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(48, 0, 0, 32)
        }
        containerExtra.addView(tvStealthInfo)

        // Max failed attempts
        containerExtra.addView(createLabel("Max Failed Attempts Before Lockout:"))
        val etMaxAttempts = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("5")
            setPadding(0, 16, 0, 32)
            filters = arrayOf(android.text.InputFilter.LengthFilter(2))
        }
        containerExtra.addView(etMaxAttempts)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(3) }

        btnNext.text = getString(R.string.next)
        btnNext.setOnClickListener {
            val code = etSecret.text.toString().ifBlank { StealthManager.DEFAULT_SECRET_CODE }
            stealthManager.setSecretCode(code)
            prefs.setMaxFailedAttempts(etMaxAttempts.text.toString().toIntOrNull() ?: 5)
            prefs.setStealthModeEnabled(cbStealth.isChecked)
            showStep(5)
        }
    }

    // ===== STEP 5: Finalize =====
    private fun showFinalizeStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_complete_title)
        tvStep.text = "6/$totalSteps"
        progressBar.progress = 100
        tvDesc.text = getString(R.string.setup_complete_desc)

        containerExtra.removeAllViews()

        // Summary
        val summaryLines = mutableListOf<String>()

        summaryLines.add("Profile A: ${prefs.getProfileName(0)}")
        summaryLines.add("  Credential: ${prefs.getCredentialType(0)}")
        summaryLines.add("")
        summaryLines.add("Profile B: ${prefs.getProfileName(1)}")
        summaryLines.add("  Credential: ${prefs.getCredentialType(1)}")
        summaryLines.add("  Status: ${if (userManager.hasSecondaryUser()) "Created" else "Not Created"}")
        summaryLines.add("")
        summaryLines.add("Secret Code: *#*#${stealthManager.getSecretCode()}#*#*")
        summaryLines.add("Stealth Mode: ${if (prefs.isStealthModeEnabled()) "Enabled" else "Disabled"}")

        for (line in summaryLines) {
            val tv = android.widget.TextView(this).apply {
                text = line
                textSize = 15f
                setPadding(0, 4, 0, 4)
                if (!line.startsWith("  ")) {
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            containerExtra.addView(tv)
        }

        // Important notice
        val tvNotice = android.widget.TextView(this).apply {
            text = getString(R.string.setup_important_notice)
            textSize = 13f
            setTextColor(getColor(android.R.color.holo_orange_dark))
            setPadding(0, 32, 0, 16)
        }
        containerExtra.addView(tvNotice)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(4) }

        btnNext.text = getString(R.string.activate_now)
        btnNext.setOnClickListener {
            activateSystem()
        }
    }

    // ===== Activation =====

    private fun activateSystem() {
        lifecycleScope.launch {
            showProgress("Activating Dual Persona System...")

            // 1. Mark setup complete
            prefs.setSetupComplete(true)

            // 2. Start services
            startServices()

            // 3. Enable stealth mode if configured
            if (prefs.isStealthModeEnabled()) {
                stealthManager.enableStealthMode()
            }

            // 4. Log activation
            SecurityLog.log(this@SetupWizardActivity, "SUCCESS", "system_activate",
                "Dual Persona System activated successfully")

            hideProgress()

            // 5. Show success dialog
            AlertDialog.Builder(this@SetupWizardActivity)
                .setTitle(getString(R.string.setup_success_title))
                .setMessage(getString(R.string.setup_success_message))
                .setPositiveButton("OK") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun startServices() {
        // Start System Service
        val systemIntent = android.content.Intent(this,
            com.dualpersona.system.service.SystemService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(systemIntent)
        } else {
            startService(systemIntent)
        }

        // Start Guard Service
        val guardIntent = android.content.Intent(this,
            com.dualpersona.system.service.GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(guardIntent)
        } else {
            startService(guardIntent)
        }
    }

    // ===== Helpers =====

    private fun openSecuritySettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun createLabel(text: String): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 8, 0, 4)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private lateinit var progressDialog: android.app.AlertDialog

    private fun showProgress(message: String) {
        progressDialog = android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()
        progressDialog.show()
    }

    private fun hideProgress() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun showSetupCompleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("System Active")
            .setMessage("Dual Persona System is already configured.\n\n" +
                    "Secret Code: *#*#${stealthManager.getSecretCode()}#*#*")
            .setPositiveButton("Reconfigure") { _, _ ->
                prefs.setSetupComplete(false)
                prefs.setCurrentSetupStep(0)
                showStep(0)
            }
            .setNegativeButton("Close") { _, _ ->
                finish()
            }
            .show()
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            showStep(currentStep - 1)
        } else {
            super.onBackPressed()
        }
    }
}
