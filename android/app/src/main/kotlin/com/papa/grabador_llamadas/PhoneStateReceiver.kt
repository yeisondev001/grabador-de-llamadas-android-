package com.papa.grabador_llamadas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallRecorderService.instance?.onGsmRinging()
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val svc = CallRecorderService.instance
                if (svc != null) {
                    svc.onGsmCall(true)
                } else {
                    try { CallRecorderService.start(context) } catch (_: Exception) {}
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallRecorderService.instance?.onGsmCall(false)
            }
        }
    }
}
