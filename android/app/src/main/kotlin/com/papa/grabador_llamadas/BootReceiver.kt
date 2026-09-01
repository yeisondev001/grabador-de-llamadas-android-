package com.papa.grabador_llamadas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("monitorEnabled", true)) {
                try { CallRecorderService.start(context) } catch (_: Exception) {}
            }
        }
    }
}
