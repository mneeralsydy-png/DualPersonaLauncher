package com.dualpersona.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dualpersona.launcher.service.LockScreenService

/**
 * Receiver that starts services on device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start the lock screen service
            val serviceIntent = Intent(context, LockScreenService::class.java).apply {
                action = LockScreenService.ACTION_SHOW_LOCK
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
