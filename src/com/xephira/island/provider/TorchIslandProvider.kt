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

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Monitors and controls the system flashlight (torch).
 * Displays an active torch indicator in the Dynamic Island with
 * an expanded view containing a large interactive toggle button.
 */
class TorchIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "torch"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var cameraManager: CameraManager? = null
    private var activeCameraId: String? = null
    private var isTorchOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            activeCameraId = cameraId
            isTorchOn = enabled
            updateContent()
        }
    }

    override fun start() {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        try {
            cameraManager?.registerTorchCallback(context.mainExecutor, torchCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stop() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
        _content.value = null
    }

    private fun updateContent() {
        if (isTorchOn) {
            _content.value = IslandContent(
                id = "torch_active",
                category = IslandCategory.TORCH,
                title = "Flashlight Active",
                subtitle = "Tap to expand",
                accentColor = Color(0xFFFFD600), // Vivid gold yellow
                expandedContent = {
                    TorchExpandedContent(
                        onToggle = { toggleTorch() }
                    )
                },
                onTap = { toggleTorch() },
                dismissible = true
            )
        } else {
            _content.value = null
        }
    }

    private fun toggleTorch() {
        val camId = activeCameraId ?: return
        try {
            cameraManager?.setTorchMode(camId, !isTorchOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun TorchExpandedContent(
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD600).copy(alpha = 0.2f))
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.FlashlightOff,
                contentDescription = "Turn Off",
                tint = Color(0xFFFFD600),
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Flashlight is On",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Tap button to turn off",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}
