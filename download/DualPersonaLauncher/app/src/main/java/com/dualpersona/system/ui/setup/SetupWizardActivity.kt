package com.dualpersona.system.ui.setup

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class SetupWizardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SetupWizard"
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var userManager: SystemUserManager
    private lateinit var credentialManager: CredentialManager
    private lateinit var stealthManager: StealthManager

    private var currentStep = 0
    private val totalSteps = 5
    private var detectJob: Job? = null
    private var progressDialog: AlertDialog? = null

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                SecurityLog.log(this, "SUCCESS", "device_admin", "Device admin granted")
                showStep(currentStep + 1)
            } else {
                Toast.makeText(this, getString(R.string.dialog_admin_required), Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "deviceAdminLauncher error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            prefs = PreferencesManager(this)
            userManager = SystemUserManager(this)
            credentialManager = CredentialManager(this)
            stealthManager = StealthManager(this)

            currentStep = prefs.getCurrentSetupStep()

            if (prefs.isSetupComplete()) {
                if (prefs.isStealthModeEnabled()) {
                    finish()
                    return
                }
                showSetupCompleteDialog()
                return
            }

            showStep(currentStep)
        } catch (e: Throwable) {
            Log.e(TAG, "onCreate CRASH prevented", e)
            Toast.makeText(this, "Error initializing. Please retry.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ====================================================================
    // STEP NAVIGATION
    // ====================================================================

    private fun showStep(step: Int) {
        try {
            currentStep = step
            prefs.setCurrentSetupStep(step)
            when (step) {
                0 -> showWelcomeStep()
                1 -> showPermissionsStep()
                2 -> showUserAConfigStep()
                3 -> showUserBConfigStep()
                4 -> showSecurityAndFinalizeStep()
                else -> finish()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showStep($step) CRASH prevented", e)
            Toast.makeText(this, "Error loading step. Please retry.", Toast.LENGTH_LONG).show()
        }
    }

    // ====================================================================
    // STEP 0: WELCOME
    // ====================================================================

    private fun showWelcomeStep() {
        setContentView(R.layout.activity_setup_wizard)
        try {
            val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
            val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
            val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
            val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
            val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)

            tvTitle.text = getString(R.string.app_name)
            tvDesc.text = getString(R.string.setup_welcome_desc)
            tvStep.text = "1/$totalSteps"
            progressBar.progress = (1 * 100) / totalSteps

            btnBack.visibility = View.GONE
            btnNext.text = getString(R.string.get_started)
            btnNext.setOnClickListener { showStep(1) }
        } catch (e: Throwable) {
            Log.e(TAG, "showWelcomeStep CRASH prevented", e)
        }
    }

    // ====================================================================
    // STEP 1: PERMISSIONS
    // ====================================================================

    private fun showPermissionsStep() {
        setContentView(R.layout.activity_setup_wizard)
        try {
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

            containerExtra.removeAllViews()

            val cb = android.widget.CheckBox(this).apply {
                text = getString(R.string.perm_device_admin)
                textSize = 16f
                setPadding(0, 24, 0, 8)
                isEnabled = false
            }
            containerExtra.addView(cb)

            val tv = android.widget.TextView(this).apply {
                text = getString(R.string.perm_device_admin_desc)
                textSize = 12f
                setPadding(48, 0, 0, 16)
                setTextColor(getColor(android.R.color.darker_gray))
            }
            containerExtra.addView(tv)

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(this, DualPersonaAdmin::class.java)
            val isAdmin = try { dpm?.isAdminActive(adminComponent) == true } catch (e: Throwable) { false }
            cb.isChecked = isAdmin

            tvDesc.text = getString(R.string.setup_permissions_desc)
            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(0) }

            if (isAdmin) {
                btnNext.text = getString(R.string.next)
                btnNext.setOnClickListener { showStep(2) }
            } else {
                btnNext.text = getString(R.string.grant_permissions)
                btnNext.setOnClickListener {
                    try {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                getString(R.string.dialog_admin_required))
                        }
                        deviceAdminLauncher.launch(intent)
                    } catch (e: Throwable) {
                        Log.e(TAG, "admin launch failed", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showPermissionsStep CRASH prevented", e)
        }
    }

    // ====================================================================
    // STEP 2: USER A (Current Phone - No Changes Needed)
    // ====================================================================

    private fun showUserAConfigStep() {
        setContentView(R.layout.activity_setup_wizard)
        try {
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

            // Info text
            containerExtra.addView(createTextView(getString(R.string.setup_user_a_info),
                14f, getColor(android.R.color.holo_green_dark), 0, 8, 0, 24, bold = false))

            // Name field
            containerExtra.addView(createLabel(getString(R.string.label_profile_name_a)))
            val etName = android.widget.EditText(this).apply {
                hint = getString(R.string.hint_profile_a)
                setText(prefs.getProfileName(0))
                setPadding(0, 16, 0, 32)
            }
            containerExtra.addView(etName)

            // Current credential info
            val credType = detectCurrentCredentialType()
            containerExtra.addView(createTextView(
                getString(R.string.setup_user_a_current_credential, credType),
                14f, getColor(android.R.color.holo_blue_dark), 0, 8, 0, 24, bold = false))

            // Note
            containerExtra.addView(createTextView(getString(R.string.setup_user_a_note),
                12f, getColor(android.R.color.darker_gray), 0, 0, 0, 16, bold = false))

            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(1) }
            btnNext.text = getString(R.string.set_and_continue)
            btnNext.setOnClickListener {
                try {
                    val name = etName.text.toString().ifBlank { getString(R.string.profile_a) }
                    prefs.setProfileName(0, name)

                    val credTypeEnum = when (credType) {
                        "PIN", "رقم PIN" -> CredentialManager.CredentialType.PIN
                        "نمط", "Pattern" -> CredentialManager.CredentialType.PATTERN
                        "كلمة مرور", "Password" -> CredentialManager.CredentialType.PASSWORD
                        else -> CredentialManager.CredentialType.PIN
                    }
                    credentialManager.storeCredentialMeta(0, credTypeEnum, name)
                    showStep(3)
                } catch (e: Throwable) {
                    Log.e(TAG, "UserA save failed", e)
                    showStep(3) // Continue anyway
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showUserAConfigStep CRASH prevented", e)
        }
    }

    private fun detectCurrentCredentialType(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyguardMgr = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (keyguardMgr?.isDeviceSecure == true) {
                    if (credentialManager.getBiometricStatus() == CredentialManager.BiometricStatus.ENROLLED) {
                        return getString(R.string.cred_fingerprint_pin)
                    }
                    return getString(R.string.cred_pin)
                }
            }
            getString(R.string.cred_pin)
        } catch (e: Throwable) {
            getString(R.string.cred_pin)
        }
    }

    // ====================================================================
    // STEP 3: CREATE USER B (The Critical Step)
    // ====================================================================

    private fun showUserBConfigStep() {
        setContentView(R.layout.activity_setup_wizard)
        try {
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
            containerExtra.removeAllViews()

            // Check if User B already exists
            if (userManager.hasSecondaryUser()) {
                tvDesc.text = getString(R.string.setup_user_b_already_exists)
                containerExtra.addView(createLabel(getString(R.string.label_profile_name_b)))
                containerExtra.addView(createTextView(
                    prefs.getSecondaryUserName() ?: prefs.getProfileName(1),
                    16f, getColor(android.R.color.black), 0, 16, 0, 32))
                btnBack.visibility = View.VISIBLE
                btnBack.setOnClickListener { showStep(2) }
                btnNext.text = getString(R.string.next)
                btnNext.setOnClickListener { showStep(4) }
                return
            }

            tvDesc.text = getString(R.string.setup_user_b_desc)

            // Name input
            containerExtra.addView(createLabel(getString(R.string.label_profile_name_b)))
            val etName = android.widget.EditText(this).apply {
                hint = getString(R.string.hint_profile_b)
                setText(prefs.getProfileName(1))
                setPadding(0, 16, 0, 32)
            }
            containerExtra.addView(etName)

            // Instructions
            containerExtra.addView(createTextView(getString(R.string.setup_user_b_instructions),
                13f, getColor(android.R.color.holo_orange_dark), 0, 16, 0, 16, bold = false))

            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(2) }
            btnNext.text = getString(R.string.create_user_b)

            // ===== THE BUTTON THAT WAS CRASHING - Now bulletproof =====
            btnNext.setOnClickListener {
                handleCreateUserB(etName)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showUserBConfigStep CRASH prevented", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Handle "Create User B" button press - COMPLETELY SAFE
     * This method will NEVER crash the app.
     */
    private fun handleCreateUserB(etName: android.widget.EditText) {
        try {
            val name = etName.text.toString().ifBlank { getString(R.string.profile_b) }
            prefs.setProfileName(1, name)

            // Disable button to prevent double-click
            etName.isEnabled = false
            val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
            btnNext?.isEnabled = false
            btnNext?.text = getString(R.string.dialog_creating_user_b)

            showProgress(getString(R.string.dialog_creating_user_b))

            // Run creation on a background thread to NEVER block the UI
            thread(name = "CreateUserB") {
                var createResult: SystemUserManager.CreateResult? = null

                try {
                    // Attempt automatic creation (tries all methods internally)
                    createResult = userManager.createSecondaryUser(name)
                } catch (e: Throwable) {
                    Log.e(TAG, "createSecondaryUser CRASH prevented", e)
                    createResult = SystemUserManager.CreateResult(
                        success = false, method = "ERROR",
                        handle = null, error = "${e.javaClass.simpleName}: ${e.message}"
                    )
                }

                // Switch back to main thread for UI updates
                runOnUiThread {
                    try {
                        hideProgress()

                        // Re-enable button
                        btnNext?.isEnabled = true
                        btnNext?.text = getString(R.string.create_user_b)
                        etName.isEnabled = true

                        if (createResult != null && createResult.success) {
                            onUserBCreated(name)
                        } else {
                            onUserBCreationFailed(name, createResult?.error ?: "Unknown error")
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "UI update after creation CRASH prevented", e)
                        Toast.makeText(this@SetupWizardActivity,
                            "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "handleCreateUserB CRASH prevented", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * User B was created successfully
     */
    private fun onUserBCreated(name: String) {
        try {
            credentialManager.storeCredentialMeta(1, CredentialManager.CredentialType.PIN, name)
            SecurityLog.log(this, "SUCCESS", "setup_user_b", "User B '$name' created")

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_set_user_b_credential_title))
                .setMessage(getString(R.string.dialog_set_user_b_credential_msg, name))
                .setPositiveButton(getString(R.string.dialog_switch_and_set)) { _, _ ->
                    showStep(4)
                }
                .setNegativeButton(getString(R.string.dialog_set_later)) { _, _ ->
                    showStep(4)
                }
                .setCancelable(false)
                .show()
        } catch (e: Throwable) {
            Log.e(TAG, "onUserBCreated CRASH prevented", e)
            showStep(4)
        }
    }

    /**
     * All automatic methods failed - offer manual creation
     */
    private fun onUserBCreationFailed(name: String, errorReason: String) {
        try {
            Log.d(TAG, "Auto-creation failed: $errorReason")

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_auto_create_failed_title))
                .setMessage(getString(R.string.dialog_auto_create_failed_msg, name, name))
                .setPositiveButton(getString(R.string.dialog_open_settings)) { _, _ ->
                    // Open system settings for manual creation
                    val opened = userManager.openUserSettings()
                    if (!opened) {
                        Toast.makeText(this,
                            "Could not open settings. Go to: Settings > System > Multiple users",
                            Toast.LENGTH_LONG).show()
                    }

                    // Start polling for new user
                    showProgress(getString(R.string.dialog_waiting_for_user))

                    detectJob = lifecycleScope.launch {
                        val detectResult = userManager.detectNewSecondaryUser(timeoutMs = 180_000)

                        runOnUiThread {
                            try {
                                hideProgress()
                            } catch (e: Throwable) {}

                            if (detectResult.success) {
                                onUserBCreated(name)
                            } else {
                                Toast.makeText(this@SetupWizardActivity,
                                    getString(R.string.dialog_user_not_detected),
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton(getString(R.string.dialog_retry_later)) { _, _ ->
                    showStep(4)
                }
                .setCancelable(false)
                .show()
        } catch (e: Throwable) {
            Log.e(TAG, "onUserBCreationFailed CRASH prevented", e)
            // Last resort: just skip to next step
            showStep(4)
        }
    }

    // ====================================================================
    // STEP 4: SECURITY & FINALIZE
    // ====================================================================

    private fun showSecurityAndFinalizeStep() {
        setContentView(R.layout.activity_setup_wizard)
        try {
            val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
            val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
            val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
            val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
            val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
            val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

            tvTitle.text = getString(R.string.setup_security_title)
            tvStep.text = "5/$totalSteps"
            progressBar.progress = 100
            tvDesc.text = getString(R.string.setup_security_desc)
            containerExtra.removeAllViews()

            // Summary
            containerExtra.addView(createTextView(getString(R.string.setup_complete_title),
                17f, getColor(android.R.color.black), 0, 0, 0, 16, bold = true))

            containerExtra.addView(createTextView(
                getString(R.string.summary_profile_a, prefs.getProfileName(0)),
                14f, getColor(android.R.color.black), 0, 4, 0, 4, bold = true))
            containerExtra.addView(createTextView(
                "  ${getString(R.string.summary_credential, prefs.getCredentialType(0))}",
                14f, getColor(android.R.color.black), 0, 4, 0, 4))
            containerExtra.addView(createTextView("", 14f, 0, 0, 0, 0, 0, bold = false))
            containerExtra.addView(createTextView(
                getString(R.string.summary_profile_b, prefs.getProfileName(1)),
                14f, getColor(android.R.color.black), 0, 4, 0, 4, bold = true))
            val userBStatus = if (userManager.hasSecondaryUser())
                getString(R.string.summary_status_created)
            else
                getString(R.string.summary_status_not_created)
            containerExtra.addView(createTextView("  $userBStatus",
                14f, getColor(android.R.color.black), 0, 4, 0, 4, bold = false))
            containerExtra.addView(createTextView("", 14f, 0, 0, 0, 0, 0, bold = false))

            // Separator
            val separator = android.view.View(this).apply {
                setBackgroundColor(getColor(android.R.color.darker_gray))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, 24, 0, 24) }
            }
            containerExtra.addView(separator)

            // Secret code
            containerExtra.addView(createLabel(getString(R.string.label_secret_code)))
            val etSecret = android.widget.EditText(this).apply {
                hint = getString(R.string.hint_secret_code)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setText(stealthManager.getSecretCode())
                setPadding(0, 16, 0, 32)
                filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            }
            containerExtra.addView(etSecret)

            // Stealth mode checkbox
            val cbStealth = android.widget.CheckBox(this).apply {
                text = getString(R.string.label_stealth_mode)
                textSize = 16f
                isChecked = true
                setPadding(0, 16, 0, 8)
            }
            containerExtra.addView(cbStealth)

            containerExtra.addView(createTextView(getString(R.string.setup_stealth_info),
                12f, getColor(android.R.color.darker_gray), 48, 0, 0, 24))

            // Important notice
            containerExtra.addView(createTextView(getString(R.string.setup_important_notice),
                13f, getColor(android.R.color.holo_orange_dark), 0, 16, 0, 16))

            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(3) }
            btnNext.text = getString(R.string.activate_now)
            btnNext.setOnClickListener {
                try {
                    val code = etSecret.text.toString().ifBlank { StealthManager.DEFAULT_SECRET_CODE }
                    stealthManager.setSecretCode(code)
                    prefs.setStealthModeEnabled(cbStealth.isChecked)
                    activateSystem()
                } catch (e: Throwable) {
                    Log.e(TAG, "finalize CRASH prevented", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showSecurityStep CRASH prevented", e)
        }
    }

    private fun activateSystem() {
        lifecycleScope.launch {
            try {
                showProgress(getString(R.string.dialog_activating))
                prefs.setSetupComplete(true)
                startServices()
                if (prefs.isStealthModeEnabled()) {
                    stealthManager.enableStealthMode()
                }
                SecurityLog.log(this@SetupWizardActivity, "SUCCESS", "system_activate",
                    "System activated")
                hideProgress()

                AlertDialog.Builder(this@SetupWizardActivity)
                    .setTitle(getString(R.string.setup_success_title))
                    .setMessage(getString(R.string.setup_success_message))
                    .setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Throwable) {
                Log.e(TAG, "activateSystem CRASH prevented", e)
                runOnUiThread {
                    try { hideProgress() } catch (ignored: Throwable) {}
                    Toast.makeText(this@SetupWizardActivity,
                        "Activation error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startServices() {
        try {
            val services = listOf(
                com.dualpersona.system.service.SystemService::class.java,
                com.dualpersona.system.service.GuardService::class.java
            )
            for (svc in services) {
                try {
                    val intent = Intent(this, svc)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to start ${svc.simpleName}", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startServices CRASH prevented", e)
        }
    }

    // ====================================================================
    // UTILITY VIEWS
    // ====================================================================

    private fun createLabel(text: String): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 8, 0, 4)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private fun createTextView(
        text: String, size: Float, color: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        bold: Boolean = false
    ): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = size
            if (color != 0) setTextColor(color)
            setPadding(left, top, right, bottom)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    // ====================================================================
    // PROGRESS DIALOG
    // ====================================================================

    private fun showProgress(message: String) {
        try {
            runOnUiThread {
                try {
                    hideProgress()
                    progressDialog = AlertDialog.Builder(this)
                        .setMessage(message)
                        .setCancelable(false)
                        .create()
                    progressDialog?.show()
                } catch (e: Throwable) {
                    Log.e(TAG, "showProgress error", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showProgress CRASH prevented", e)
        }
    }

    private fun hideProgress() {
        try {
            if (progressDialog != null && progressDialog?.isShowing == true) {
                progressDialog?.dismiss()
            }
            progressDialog = null
        } catch (e: Throwable) {
            progressDialog = null
        }
    }

    // ====================================================================
    // SETUP COMPLETE DIALOG
    // ====================================================================

    private fun showSetupCompleteDialog() {
        try {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_system_active))
                .setMessage(getString(R.string.dialog_system_active_msg, stealthManager.getSecretCode()))
                .setPositiveButton(getString(R.string.dialog_reconfigure)) { _, _ ->
                    prefs.setSetupComplete(false)
                    prefs.setCurrentSetupStep(0)
                    showStep(0)
                }
                .setNegativeButton(getString(R.string.dialog_close)) { _, _ -> finish() }
                .show()
        } catch (e: Throwable) {
            Log.e(TAG, "showSetupCompleteDialog CRASH prevented", e)
            finish()
        }
    }

    // ====================================================================
    // LIFECYCLE
    // ====================================================================

    override fun onDestroy() {
        try {
            detectJob?.cancel()
            hideProgress()
        } catch (e: Throwable) {}
        super.onDestroy()
    }

    override fun onBackPressed() {
        try {
            if (currentStep > 0) {
                showStep(currentStep - 1)
            } else {
                super.onBackPressed()
            }
        } catch (e: Throwable) {
            super.onBackPressed()
        }
    }
}
