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

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
 * Monitors real-time microphone and camera access requests using AppOpsManager.
 * Shows active privacy capsule badges in the Dynamic Island with quick mute/kill actions.
 */
class PrivacyIslandProvider(
    private val context: Context
) : IslandProvider {

    override val providerId = "privacy"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var appOpsManager: AppOpsManager? = null
    private var audioManager: AudioManager? = null
    private var scope: CoroutineScope? = null

    private val activeOps = mutableMapOf<String, String>() // opName -> packageName

    private val opListener = AppOpsManager.OnOpActiveChangedListener { op, uid, packageName, active ->
        scope?.launch(Dispatchers.Main) {
            val opName = when (op) {
                AppOpsManager.OPSTR_RECORD_AUDIO -> "microphone"
                AppOpsManager.OPSTR_CAMERA -> "camera"
                else -> return@launch
            }

            if (active) {
                activeOps[opName] = packageName
            } else {
                activeOps.remove(opName)
            }

            updateIslandContent()
        }
    }

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val opsToWatch = arrayOf(
            AppOpsManager.OPSTR_RECORD_AUDIO,
            AppOpsManager.OPSTR_CAMERA
        )
        try {
            appOpsManager?.startWatchingActive(opsToWatch, context.mainExecutor, opListener)
        } catch (e: Exception) {
            android.util.Log.e("PrivacyIsland", "Failed to start active ops watcher", e)
        }
    }

    override fun stop() {
        try {
            appOpsManager?.stopWatchingActive(opListener)
        } catch (_: Exception) {}
        scope?.cancel()
        scope = null
        activeOps.clear()
        _content.value = null
    }

    private fun updateIslandContent() {
        if (activeOps.isEmpty()) {
            _content.value = null
            return
        }

        val hasCamera = activeOps.containsKey("camera")
        val hasMic = activeOps.containsKey("microphone")
        val activePkg = activeOps.values.firstOrNull() ?: "Unknown App"
        
        val pm = context.packageManager
        val appLabel = try {
            val appInfo = pm.getApplicationInfo(activePkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            activePkg.substringAfterLast(".")
        }

        val title = when {
            hasCamera && hasMic -> "Camera & Mic Active"
            hasCamera -> "Camera Active"
            else -> "Microphone Active"
        }

        val subtitle = "Used by $appLabel"

        _content.value = IslandContent(
            id = "privacy_shield",
            category = IslandCategory.PHONE_CALL,
            title = title,
            subtitle = subtitle,
            accentColor = Color(0xFF00E676),
            expandedContent = {
                PrivacyExpandedContent(
                    appName = appLabel,
                    packageName = activePkg,
                    hasCamera = hasCamera,
                    hasMic = hasMic,
                    onMuteMic = { toggleMicMute() },
                    isMicMuted = audioManager?.isMicrophoneMute ?: false,
                    onRevoke = { revokeApp(activePkg) }
                )
            },
            dismissible = true
        )
    }

    private fun toggleMicMute() {
        audioManager?.let {
            it.isMicrophoneMute = !it.isMicrophoneMute
            updateIslandContent()
        }
    }

    private fun revokeApp(packageName: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        try {
            am?.killBackgroundProcesses(packageName)
            activeOps.values.removeAll { it == packageName }
            updateIslandContent()
        } catch (_: Exception) {}
    }
}

@Composable
fun PrivacyExpandedContent(
    appName: String,
    packageName: String,
    hasCamera: Boolean,
    hasMic: Boolean,
    onMuteMic: () -> Unit,
    isMicMuted: Boolean,
    onRevoke: () -> Unit
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
                .clip(CircleShape)
                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasCamera) Icons.Filled.Videocam else Icons.Filled.Mic,
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Text(
            text = if (hasCamera && hasMic) "Using Camera & Microphone" else if (hasCamera) "Using Camera" else "Using Microphone",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (hasMic) {
                // Mute mic option
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isMicMuted) Color(0xFFFF1744) else Color(0x33FFFFFF))
                        .clickable { onMuteMic() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mute Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Revoke / Stop App button
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744))
                    .clickable { onRevoke() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Force Stop App",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
