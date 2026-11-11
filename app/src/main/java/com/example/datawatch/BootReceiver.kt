package com.example.datawatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED &&
            Usage.prefs(c).getBoolean("enabled", false) && Usage.hasAccess(c)
        ) ContextCompat.startForegroundService(c, Intent(c, UsageService::class.java))
    }
}
