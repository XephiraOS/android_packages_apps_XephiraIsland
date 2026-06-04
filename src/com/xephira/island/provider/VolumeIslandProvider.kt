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
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Monitors system volume key presses and ringer mode state.
 * Intercepts volume adjustments, displays a dynamic percentage bar
 * in the island, and auto-dismisses after 2.5 seconds.
 * Expanded mode provides a fine-grain slider and Ringer Mode selector.
 */
class VolumeIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "volume"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var audioManager: AudioManager? = null
    private var receiver: BroadcastReceiver? = null
    private var dismissJob: Job? = null
    private var scope: CoroutineScope? = null

    companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    }

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == VOLUME_CHANGED_ACTION) {
                    val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                    // We primarily respond to Media (3) or Ring/Notification streams
                    if (streamType == AudioManager.STREAM_MUSIC || streamType == AudioManager.STREAM_RING) {
                        showVolumeOverlay(streamType)
                    }
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))
    }

    override fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        dismissJob?.cancel()
        scope?.cancel()
        scope = null
        _content.value = null
    }

    private fun showVolumeOverlay(streamType: Int) {
        val am = audioManager ?: return
        val currentVolume = am.getStreamVolume(streamType)
        val maxVolume = am.getStreamMaxVolume(streamType)
        if (maxVolume <= 0) return

        val progress = currentVolume.toFloat() / maxVolume.toFloat()
        val percentage = (progress * 100).toInt()

        val title = when (streamType) {
            AudioManager.STREAM_MUSIC -> "Media Volume"
            AudioManager.STREAM_RING -> "Ring Volume"
            else -> "Volume"
        }

        val icon = if (currentVolume == 0) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp

        _content.value = IslandContent(
            id = "volume_change",
            category = IslandCategory.VOLUME,
            title = "$title: $percentage%",
            accentColor = Color(0xFF00E5FF),
            progress = progress,
            expandedContent = {
                VolumeExpandedContent(
                    currentVolume = currentVolume,
                    maxVolume = maxVolume,
                    ringerMode = am.ringerMode,
                    onVolumeChanged = { newVolume ->
                        am.setStreamVolume(streamType, newVolume, 0)
                        // Trigger quick redraw
                        showVolumeOverlay(streamType)
                    },
                    onRingerModeChanged = { mode ->
                        am.ringerMode = mode
                        // Refresh overlay
                        showVolumeOverlay(streamType)
                    }
                )
            },
            dismissible = true
        )

        // Auto-dismiss after 2.5 seconds of no volume updates
        dismissJob?.cancel()
        dismissJob = scope?.launch {
            delay(2500)
            _content.value = null
        }
    }
}

@Composable
fun VolumeExpandedContent(
    currentVolume: Int,
    maxVolume: Int,
    ringerMode: Int,
    onVolumeChanged: (Int) -> Unit,
    onRingerModeChanged: (Int) -> Unit,
) {
    var sliderValue by remember(currentVolume) { mutableStateOf(currentVolume.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ringer mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring mode
            RingerModeButton(
                icon = Icons.Filled.Notifications,
                label = "Ring",
                isActive = ringerMode == AudioManager.RINGER_MODE_NORMAL,
                onClick = { onRingerModeChanged(AudioManager.RINGER_MODE_NORMAL) }
            )

            // Vibrate mode
            RingerModeButton(
                icon = Icons.Filled.Vibration,
                label = "Vibrate",
                isActive = ringerMode == AudioManager.RINGER_MODE_VIBRATE,
                onClick = { onRingerModeChanged(AudioManager.RINGER_MODE_VIBRATE) }
            )

            // Silent mode
            RingerModeButton(
                icon = Icons.Filled.NotificationsOff,
                label = "Silent",
                isActive = ringerMode == AudioManager.RINGER_MODE_SILENT,
                onClick = { onRingerModeChanged(AudioManager.RINGER_MODE_SILENT) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vol Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (currentVolume == 0) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onVolumeChanged(it.toInt())
                },
                valueRange = 0f..maxVolume.toFloat(),
                steps = maxVolume - 1,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                    thumbColor = Color(0xFF00A3FF)
                )
            )
        }
    }
}

@Composable
fun RingerModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) Color(0xFF00E5FF).copy(alpha = 0.2f)
                    else Color.White.copy(alpha = 0.05f)
                )
                .border(
                    width = 1.dp,
                    color = if (isActive) Color(0xFF00E5FF) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f)
        )
    }
}
