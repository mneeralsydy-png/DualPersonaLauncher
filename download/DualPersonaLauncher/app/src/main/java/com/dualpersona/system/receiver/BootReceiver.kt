package com.dualpersona.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import com.dualpersona.system.service.GuardService
import com.dualpersona.system.service.SystemService

/**
 * BootReceiver - Starts services after device boot
 *
 * Ensures Dual Persona System services are always running
 * after device restart. This is critical for the system
 * to maintain functionality across reboots.
 *
 * Services started:
 * 1. SystemService - Main user management
 * 2. GuardService - Security monitoring
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        val prefs = PreferencesManager(context)
        if (!prefs.isSetupComplete()) return

        SecurityLog.log(context, "INFO", "boot", "Device booted - starting services")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                startServices(context)
            }
        }
    }

    private fun startServices(context: Context) {
        // Start System Service
        val systemIntent = Intent(context, SystemService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(systemIntent)
        } else {
            context.startService(systemIntent)
        }

        // Start Guard Service
        val guardIntent = Intent(context, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(guardIntent)
        } else {
            context.startService(guardIntent)
        }

        SecurityLog.log(context, "SUCCESS", "boot_services",
            "All services started after boot")
    }
}
