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

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification listener service that enables the media provider
 * to access active media sessions. Also intercepts high-priority
 * notifications to display in the Dynamic Island.
 */
class NotificationIslandProvider : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Future: intercept and forward specific notifications to island
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Future: remove island content when notification is dismissed
    }
}
