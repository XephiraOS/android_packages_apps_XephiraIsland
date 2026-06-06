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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Monitors device sensor events, charging transitions, and accessory plug states (such as headphone connection).
 * Computes contextual user actions (e.g. launching music player) dynamically inside the Dynamic Island.
 */
class SmartContextProvider(
    private val context: Context
) : IslandProvider {

    override val providerId = "smart_context"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var scope: CoroutineScope? = null

    private val contextReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    if (state == 1) {
                        showHeadsetConnected()
                    } else {
                        if (_content.value?.id == "smart_headset") {
                            _content.value = null
                        }
                    }
                }
            }
        }
    }

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(contextReceiver, filter)
    }

    override fun stop() {
        try {
            context.unregisterReceiver(contextReceiver)
        } catch (_: Exception) {}
        scope?.cancel()
        scope = null
        _content.value = null
    }

    private fun showHeadsetConnected() {
        _content.value = IslandContent(
            id = "smart_headset",
            category = IslandCategory.VOLUME,
            title = "Audio Connected",
            subtitle = "Headphones plugged in",
            accentColor = Color(0xFF00E5FF),
            expandedContent = {
                SmartHeadsetExpandedContent(
                    onLaunchMusic = { launchMusicApp() },
                    onDismiss = { _content.value = null }
                )
            },
            dismissible = true
        )

        scope?.launch {
            delay(8000)
            if (_content.value?.id == "smart_headset") {
                _content.value = null
            }
        }
    }

    private fun launchMusicApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.spotify.music")
                ?: pm.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        }
        _content.value = null
    }
}

@Composable
fun SmartHeadsetExpandedContent(
    onLaunchMusic: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Headphones,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Smart Audio Context",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Text(
            text = "Headphones connected. Switch audio feed to music player?",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable { onDismiss() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ignore",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF00E5FF))
                    .clickable { onLaunchMusic() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Music",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
