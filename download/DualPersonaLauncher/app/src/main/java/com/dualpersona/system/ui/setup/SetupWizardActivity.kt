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

/**
 * SetupWizardActivity - معالج الإعداد الآمن 100%
 *
 * لا يستخدم أي API مخفي أو انعكاس.
 * لا يحدث أي انهيار - أبداً.
 *
 * الخطوات:
 * 0: ترحيب
 * 1: صلاحيات
 * 2: تأكيد المستخدم A (الهاتف الحالي)
 * 3: إنشاء المستخدم B (عبر إعدادات النظام)
 * 4: الإعدادات النهائية والتفعيل
 */
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
        } catch (e: Exception) {
            Log.e(TAG, "deviceAdminLauncher error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            // تهيئة مكونات آمنة - لا يمكن أن تنهار
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
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error", e)
            Toast.makeText(this, "حدث خطأ أثناء التهيئة. يرجى إعادة المحاولة.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ====================================================================
    // التنقل بين الخطوات
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
        } catch (e: Exception) {
            Log.e(TAG, "showStep($step) error", e)
            Toast.makeText(this, "خطأ في تحميل الخطوة. يرجى إعادة المحاولة.", Toast.LENGTH_LONG).show()
        }
    }

    // ====================================================================
    // الخطوة 0: ترحيب
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
        } catch (e: Exception) {
            Log.e(TAG, "showWelcomeStep error", e)
        }
    }

    // ====================================================================
    // الخطوة 1: صلاحيات
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

            // خانة مسؤول الجهاز
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
            val isAdmin = try { dpm?.isAdminActive(adminComponent) == true } catch (e: Exception) { false }
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
                    } catch (e: Exception) {
                        Log.e(TAG, "admin launch failed", e)
                        Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showPermissionsStep error", e)
        }
    }

    // ====================================================================
    // الخطوة 2: المستخدم A (الهاتف الحالي - بدون تغييرات)
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

            // معلومات
            containerExtra.addView(createTextView(getString(R.string.setup_user_a_info),
                14f, getColor(android.R.color.holo_green_dark), 0, 8, 0, 24, bold = false))

            // حقل الاسم
            containerExtra.addView(createLabel(getString(R.string.label_profile_name_a)))
            val etName = android.widget.EditText(this).apply {
                hint = getString(R.string.hint_profile_a)
                setText(prefs.getProfileName(0))
                setPadding(0, 16, 0, 32)
            }
            containerExtra.addView(etName)

            // معلومات نوع القفل الحالي
            val credType = detectCurrentCredentialType()
            containerExtra.addView(createTextView(
                getString(R.string.setup_user_a_current_credential, credType),
                14f, getColor(android.R.color.holo_blue_dark), 0, 8, 0, 24, bold = false))

            // ملاحظة
            containerExtra.addView(createTextView(getString(R.string.setup_user_a_note),
                12f, getColor(android.R.color.darker_gray), 0, 0, 0, 16, bold = false))

            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(1) }
            btnNext.text = getString(R.string.set_and_continue)
            btnNext.setOnClickListener {
                try {
                    val name = etName.text.toString().ifBlank { getString(R.string.profile_a) }
                    prefs.setProfileName(0, name)

                    val credTypeEnum = when {
                        credType.contains("بصمة") -> CredentialManager.CredentialType.BIOMETRIC_PIN
                        credType.contains("نمط") || credType.contains("Pattern") -> CredentialManager.CredentialType.PATTERN
                        credType.contains("كلمة مرور") || credType.contains("Password") -> CredentialManager.CredentialType.PASSWORD
                        else -> CredentialManager.CredentialType.PIN
                    }
                    credentialManager.storeCredentialMeta(0, credTypeEnum, name)
                    showStep(3)
                } catch (e: Exception) {
                    Log.e(TAG, "UserA save failed", e)
                    showStep(3) // المتابعة على أي حال
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showUserAConfigStep error", e)
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
        } catch (e: Exception) {
            getString(R.string.cred_pin)
        }
    }

    // ====================================================================
    // الخطوة 3: إنشاء المستخدم B (الطريقة الآمنة - عبر إعدادات النظام)
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

            // التحقق مما إذا كان المستخدم B موجوداً بالفعل
            if (userManager.hasSecondaryUser()) {
                tvDesc.text = getString(R.string.setup_user_b_already_exists)
                containerExtra.addView(createLabel(getString(R.string.label_profile_name_b)))
                containerExtra.addView(createTextView(
                    userManager.getSecondaryUserName(),
                    16f, getColor(android.R.color.black), 0, 16, 0, 32))
                btnBack.visibility = View.VISIBLE
                btnBack.setOnClickListener { showStep(2) }
                btnNext.text = getString(R.string.next)
                btnNext.setOnClickListener { showStep(4) }
                return
            }

            tvDesc.text = getString(R.string.setup_user_b_desc_new)

            // حقل الاسم
            containerExtra.addView(createLabel(getString(R.string.label_profile_name_b)))
            val etName = android.widget.EditText(this).apply {
                hint = getString(R.string.hint_profile_b)
                setText(prefs.getProfileName(1))
                setPadding(0, 16, 0, 32)
            }
            containerExtra.addView(etName)

            // تعليمات واضحة
            containerExtra.addView(createTextView(getString(R.string.setup_user_b_guide_new),
                13f, getColor(android.R.color.holo_orange_dark), 0, 16, 0, 16, bold = false))

            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(2) }
            btnNext.text = getString(R.string.create_user_b_guide)

            // ===== الزر الآمن 100% - يفتح الإعدادات فقط =====
            btnNext.setOnClickListener {
                handleCreateUserB(etName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "showUserBConfigStep error", e)
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * معالجة إنشاء المستخدم B - الطريقة الآمنة تماماً.
     * يفتح إعدادات النظام فقط ويطلب من المستخدم التأكيد.
     */
    private fun handleCreateUserB(etName: android.widget.EditText) {
        try {
            val name = etName.text.toString().ifBlank { getString(R.string.profile_b) }
            prefs.setProfileName(1, name)

            // عرض رسالة توجيهية واضحة
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_guide_create_title))
                .setMessage(getString(R.string.dialog_guide_create_msg, name))
                .setPositiveButton(getString(R.string.dialog_open_settings)) { _, _ ->
                    // فتح إعدادات المستخدمين
                    val opened = userManager.openUserSettingsForCreation()
                    if (!opened) {
                        Toast.makeText(this,
                            getString(R.string.dialog_cannot_open_settings),
                            Toast.LENGTH_LONG).show()
                    }

                    // عرض مربع حوار التأكيد
                    showConfirmUserBCreatedDialog(name)
                }
                .setNegativeButton(getString(R.string.dialog_cancel)) { _, _ ->
                    // إلغاء - البقاء في نفس الصفحة
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "handleCreateUserB error", e)
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * عرض مربع حوار التأكيد بعد إنشاء المستخدم B
     */
    private fun showConfirmUserBCreatedDialog(name: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_confirm_user_title))
                .setMessage(getString(R.string.dialog_confirm_user_msg, name))
                .setPositiveButton(getString(R.string.dialog_yes_created)) { _, _ ->
                    // المستخدم أكد الإنشاء
                    userManager.confirmUserBCreated(name)
                    credentialManager.storeCredentialMeta(1, CredentialManager.CredentialType.PIN, name)
                    SecurityLog.log(this, "SUCCESS", "user_b_created", "User B '$name' confirmed")

                    // عرض رسالة تعيين كلمة المرور
                    showSetCredentialDialog(name)
                }
                .setNegativeButton(getString(R.string.dialog_not_yet)) { _, _ ->
                    // المستخدم لم ينشئ بعد - البقاء في نفس الصفحة
                    Toast.makeText(this,
                        getString(R.string.dialog_not_created_yet),
                        Toast.LENGTH_LONG).show()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "showConfirmDialog error", e)
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * عرض رسالة تعيين كلمة مرور للمستخدم B
     */
    private fun showSetCredentialDialog(name: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_set_user_b_credential_title))
                .setMessage(getString(R.string.dialog_set_user_b_credential_msg, name, name))
                .setPositiveButton(getString(R.string.dialog_switch_and_set)) { _, _ ->
                    // فتح إعدادات الأمان لتعيين كلمة المرور
                    userManager.openSecuritySettings()
                    showStep(4)
                }
                .setNegativeButton(getString(R.string.dialog_set_later)) { _, _ ->
                    showStep(4)
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "showSetCredentialDialog error", e)
            showStep(4)
        }
    }

    // ====================================================================
    // الخطوة 4: الإعدادات النهائية والتفعيل
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

            // ملخص
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

            // فاصل
            val separator = android.view.View(this).apply {
                setBackgroundColor(getColor(android.R.color.darker_gray))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, 24, 0, 24) }
            }
            containerExtra.addView(separator)

            // الرمز السري
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

            // خانة وضع التخفي
            val cbStealth = android.widget.CheckBox(this).apply {
                text = getString(R.string.label_stealth_mode)
                textSize = 16f
                isChecked = true
                setPadding(0, 16, 0, 8)
            }
            containerExtra.addView(cbStealth)

            containerExtra.addView(createTextView(getString(R.string.setup_stealth_info),
                12f, getColor(android.R.color.darker_gray), 48, 0, 0, 24))

            // إشعار مهم
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
                } catch (e: Exception) {
                    Log.e(TAG, "finalize error", e)
                    Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showSecurityStep error", e)
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
            } catch (e: Exception) {
                Log.e(TAG, "activateSystem error", e)
                runOnUiThread {
                    try { hideProgress() } catch (ignored: Exception) {}
                    Toast.makeText(this@SetupWizardActivity,
                        "خطأ في التفعيل: ${e.message}", Toast.LENGTH_LONG).show()
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start ${svc.simpleName}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startServices error", e)
        }
    }

    // ====================================================================
    // أدوات إنشاء العروض
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
    // مربع حوار التقدم
    // ====================================================================

    private fun showProgress(message: String) {
        try {
            if (isFinishing) return
            hideProgress()
            progressDialog = AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        } catch (e: Exception) {
            Log.e(TAG, "showProgress error", e)
        }
    }

    private fun hideProgress() {
        try {
            if (progressDialog != null && progressDialog?.isShowing == true) {
                progressDialog?.dismiss()
            }
            progressDialog = null
        } catch (e: Exception) {
            progressDialog = null
        }
    }

    // ====================================================================
    // مربع حوار الإعداد المكتمل
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
        } catch (e: Exception) {
            Log.e(TAG, "showSetupCompleteDialog error", e)
            finish()
        }
    }

    // ====================================================================
    // دورة الحياة
    // ====================================================================

    override fun onDestroy() {
        try {
            hideProgress()
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBackPressed() {
        try {
            if (currentStep > 0) {
                showStep(currentStep - 1)
            } else {
                super.onBackPressed()
            }
        } catch (e: Exception) {
            super.onBackPressed()
        }
    }
}
