package com.dualpersona.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.dualpersona.system.core.DataGuard
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

class UserSwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        val prefs = PreferencesManager(context)
        if (!prefs.isSetupComplete()) return

        val dataGuard = DataGuard(context)

        when (intent.action) {
            "android.intent.action.USER_SWITCHED" -> {
                val userId = intent.getIntExtra("android.intent.extra.user_handle", -1)
                val isSecondary = userId != 0

                SecurityLog.log(context, "INFO", "user_switched",
                    "Switched to user ID: $userId (isSecondary: $isSecondary)")

                try {
                    dataGuard.verifyIsolation()
                } catch (e: Exception) { }
            }
        }
    }
}
