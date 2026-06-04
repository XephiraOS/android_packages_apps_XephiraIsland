/*
 * Copyright (C) 2026 XephiraOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.xephira.island.provider

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification listener service that enables the media provider
 * to access active media sessions and intercepts navigation notifications
 * to display turn-by-turn directions in the Dynamic Island.
 */
class NotificationIslandProvider : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val activeSbn = sbn ?: return
        val notification = activeSbn.notification ?: return

        val isNav = notification.category == Notification.CATEGORY_NAVIGATION ||
                activeSbn.packageName.contains("maps") ||
                activeSbn.packageName.contains("navigation")

        if (isNav) {
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val icon = notification.getLargeIcon() ?: notification.smallIcon

            if (title.isNotEmpty()) {
                NavigationTracker.updateNav(
                    NavData(title = title, text = text, icon = icon)
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val activeSbn = sbn ?: return
        if (activeSbn.packageName.contains("maps") || activeSbn.packageName.contains("navigation")) {
            NavigationTracker.updateNav(null)
        }
    }
}
