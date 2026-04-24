package com.dualpersona.system.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.dualpersona.system.core.StealthManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import com.dualpersona.system.ui.dashboard.DashboardActivity

/**
 * SecretDialReceiver - Access app via dialer secret code
 *
 * When the user dials *#*#CODE#*#*, this receiver:
 * 1. Verifies the code matches configured secret code
 * 2. Temporarily reveals the app (if in stealth mode)
 * 3. Opens the hidden DashboardActivity
 *
 * This is the ONLY way to access the app after stealth mode
 * is enabled (except for the initial setup).
 */
class SecretDialReceiver : BroadcastReceiver() {

    companion object {
        // The secret code from the dialer URI
        // When user dials *#*#7890#*#*, the data URI will contain "7890"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)
        if (!prefs.isSetupComplete()) return

        // Extract the dialed code from the URI data
        val uri = intent.data ?: return
        val dialedCode = uri.lastPathSegment ?: return

        val stealthManager = StealthManager(context)
        val expectedCode = stealthManager.getSecretCode()

        if (dialedCode == expectedCode) {
            SecurityLog.log(context, "SUCCESS", "secret_code_access",
                "Dashboard accessed via secret code")

            // Temporarily reveal app and open dashboard
            stealthManager.disableStealthMode()

            val dashboardIntent = Intent(context, DashboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(dashboardIntent)
        } else {
            SecurityLog.log(context, "WARNING", "secret_code_wrong",
                "Wrong secret code dialed: $dialedCode")
        }
    }
}
