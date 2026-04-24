package com.dualpersona.system.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.dualpersona.system.R
import com.dualpersona.system.data.SecurityLog

class DualPersonaAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        SecurityLog.log(context, "SUCCESS", "device_admin_enabled", "Device admin activated")
        android.widget.Toast.makeText(context,
            context.getString(R.string.admin_activated),
            android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        SecurityLog.log(context, "WARNING", "device_admin_disabled", "Device admin deactivated")
        android.widget.Toast.makeText(context,
            context.getString(R.string.admin_deactivated),
            android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        SecurityLog.log(context, "WARNING", "device_admin_disable_requested",
            "User requested to disable device admin")
        return context.getString(R.string.admin_disable_warning)
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        SecurityLog.log(context, "INFO", "password_changed", "System lock screen credential changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        SecurityLog.log(context, "WARNING", "password_failed", "Incorrect credential entered")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        SecurityLog.log(context, "SUCCESS", "password_succeeded", "System lock screen credential verified")
    }
}
