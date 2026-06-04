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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Switch
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

/**
 * Simple settings activity to toggle the Dynamic Island on/off.
 * Can be launched from XephiraOS Settings or as standalone.
 */
class IslandSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 128, 64, 64)
        }

        val title = TextView(this).apply {
            text = "Dynamic Island"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val enableSwitch = Switch(this).apply {
            text = "Enable Dynamic Island"
            textSize = 16f
            isChecked = Settings.Secure.getInt(
                contentResolver,
                IslandService.SETTING_ENABLED,
                1
            ) == 1

            setOnCheckedChangeListener { _, isChecked ->
                Settings.Secure.putInt(
                    contentResolver,
                    IslandService.SETTING_ENABLED,
                    if (isChecked) 1 else 0
                )
                val serviceIntent = Intent(this@IslandSettingsActivity, IslandService::class.java)
                if (isChecked) {
                    startForegroundService(serviceIntent)
                } else {
                    stopService(serviceIntent)
                }
            }
        }

        layout.addView(title)
        layout.addView(enableSwitch)
        setContentView(layout)
    }
}
