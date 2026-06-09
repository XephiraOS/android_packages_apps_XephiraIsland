/*
 * Copyright (C) 2026 XephiraOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.xephira.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Starts the Dynamic Island service on device boot if enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                IslandService.SETTING_ENABLED,
                1
            ) == 1 && Settings.System.getInt(
                context.contentResolver,
                IslandService.SETTING_ENABLED_SYS,
                1
            ) == 1

            if (enabled) {
                val serviceIntent = Intent(context, IslandService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
