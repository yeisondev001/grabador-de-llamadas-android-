package com.papa.grabador_llamadas

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_CALL) return
        CallRecorderService.instance?.onWsPosted(sbn.isOngoing)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_CALL) return
        val others = activeNotifications?.any {
            it.packageName == sbn.packageName &&
                it.notification?.category == Notification.CATEGORY_CALL &&
                it.isOngoing
        } == true
        if (others) return
        CallRecorderService.instance?.onWsRemoved()
    }
}
