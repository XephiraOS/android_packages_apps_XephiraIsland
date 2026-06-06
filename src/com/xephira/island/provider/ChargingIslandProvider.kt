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
import android.os.BatteryManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BoltOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Provides charging information to the Dynamic Island.
 * Shows when the device is plugged in and displays battery level,
 * charging speed, and estimated time to full.
 */
class ChargingIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "charging"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var receiver: BroadcastReceiver? = null
    private var wasCharging = false

    override fun start() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return
                handleBatteryChanged(intent)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { handleBatteryChanged(it) }
    }

    override fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        _content.value = null
    }

    private fun handleBatteryChanged(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingType = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "Fast"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> ""
        }

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = tempTenths / 10

        if (isCharging && percentage >= 0) {
            val justPlugged = !wasCharging
            wasCharging = true

            _content.value = IslandContent(
                id = "charging",
                category = IslandCategory.CHARGING,
                title = "$percentage%",
                subtitle = if (chargingType.isNotEmpty()) "$chargingType Charging" else "Charging",
                accentColor = Color(0xFF00E676),
                progress = percentage / 100f,
                expandedContent = {
                    ChargingExpandedContent(
                        percentage = percentage,
                        chargingType = chargingType,
                        temperatureC = tempC,
                    )
                },
                // Auto-dismiss after 5 seconds if not just plugged in
                dismissible = !justPlugged,
            )
        } else {
            wasCharging = false
            _content.value = null
        }
    }
}

@Composable
fun ChargingExpandedContent(
    percentage: Int,
    chargingType: String,
    temperatureC: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chargingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chargePulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(54.dp)
            ) {
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ringRotate"
                )
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00E676).copy(alpha = 0.05f),
                                Color(0xFF00E5FF).copy(alpha = 0.8f),
                                Color(0xFF00E676).copy(alpha = 0.8f),
                                Color(0xFF00E676).copy(alpha = 0.05f),
                            )
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }

                Icon(
                    Icons.Filled.BoltOutlined,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontSize = 36.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Battery fill bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF00E676),
                                Color(0xFF00E5FF),
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (chargingType.isNotEmpty()) "$chargingType Charging" else "Charging",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "${temperatureC}°C",
                style = MaterialTheme.typography.bodySmall,
                color = if (temperatureC > 40) Color(0xFFFF5252) else Color.White.copy(alpha = 0.5f),
            )
        }
    }
}
