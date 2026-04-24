package com.dualpersona.system.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.dualpersona.system.data.SecurityLog

/**
 * DualPersonaAdmin - Device Administrator receiver
 *
 * Handles system-level events:
 * - Device admin enable/disable
 * - Password changes
 * - Password failures
 * - Password successes
 *
 * These callbacks allow the app to monitor lock screen credential
 * events at the system level without replacing the lock screen.
 */
class DualPersonaAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        SecurityLog.log(context, "SUCCESS", "device_admin_enabled",
            "Device admin activated")
        Toast.makeText(context, "Dual Persona System: Device Admin activated",
            Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        SecurityLog.log(context, "WARNING", "device_admin_disabled",
            "Device admin deactivated")
        Toast.makeText(context, "Dual Persona System: Device Admin deactivated",
            Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        SecurityLog.log(context, "WARNING", "device_admin_disable_requested",
            "User requested to disable device admin")
        return "Warning: Disabling Dual Persona System will compromise dual profile security. " +
               "Your secondary profile data will remain but management features will be lost."
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        SecurityLog.log(context, "INFO", "password_changed",
            "System lock screen credential changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        SecurityLog.log(context, "WARNING", "password_failed",
            "Incorrect credential entered on system lock screen")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        SecurityLog.log(context, "SUCCESS", "password_succeeded",
            "System lock screen credential verified")
    }

    override fun onPasswordExpiring(context: Context, intent: Intent) {
        SecurityLog.log(context, "INFO", "password_expiring",
            "Device password is about to expire")
    }
}
