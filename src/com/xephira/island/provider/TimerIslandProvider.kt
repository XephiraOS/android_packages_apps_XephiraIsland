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

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Provides timer/stopwatch/alarm information to the Dynamic Island.
 * Monitors the system clock app's timer broadcasts and shows a
 * live countdown with an animated circular progress ring.
 */
class TimerIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "timer"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var receiver: BroadcastReceiver? = null
    private var scope: CoroutineScope? = null
    private var timerEndTime: Long = 0L
    private var timerTotalDuration: Long = 0L
    private var countdownJob: Job? = null

    companion object {
        // Standard timer broadcast actions
        const val ACTION_TIMER_STARTED = "android.intent.action.TIMER_STARTED"
        const val ACTION_TIMER_STOPPED = "android.intent.action.TIMER_STOPPED"
        const val ACTION_TIMER_DONE = "android.intent.action.TIMER_DONE"
        const val EXTRA_TIMER_LENGTH = "android.intent.extra.TIMER_LENGTH"
    }

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_TIMER_STARTED -> {
                        val lengthMs = intent.getLongExtra(EXTRA_TIMER_LENGTH, 0L)
                        if (lengthMs > 0) {
                            timerTotalDuration = lengthMs
                            timerEndTime = System.currentTimeMillis() + lengthMs
                            startCountdown()
                        }
                    }
                    ACTION_TIMER_STOPPED, ACTION_TIMER_DONE -> {
                        countdownJob?.cancel()
                        timerEndTime = 0L
                        _content.value = null
                    }
                    // Also listen for alarm alerts
                    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                        checkNextAlarm()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_TIMER_STARTED)
            addAction(ACTION_TIMER_STOPPED)
            addAction(ACTION_TIMER_DONE)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        context.registerReceiver(receiver, filter)
    }

    override fun stop() {
        countdownJob?.cancel()
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        scope?.cancel()
        scope = null
        _content.value = null
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope?.launch {
            while (isActive) {
                val remaining = timerEndTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    _content.value = null
                    break
                }

                val minutes = (remaining / 60000).toInt()
                val seconds = ((remaining % 60000) / 1000).toInt()
                val timeText = String.format("%d:%02d", minutes, seconds)
                val progress = if (timerTotalDuration > 0)
                    (remaining.toFloat() / timerTotalDuration.toFloat()).coerceIn(0f, 1f)
                else 0f

                _content.value = IslandContent(
                    id = "timer",
                    category = IslandCategory.TIMER,
                    title = timeText,
                    subtitle = "Timer",
                    accentColor = Color(0xFFFF9100),
                    progress = progress,
                    expandedContent = {
                        TimerExpandedContent(
                            timeText = timeText,
                            progress = progress,
                        )
                    },
                    dismissible = false,
                )
                delay(200) // Update 5 times per second for smooth progress
            }
        }
    }

    private fun checkNextAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = alarmManager?.nextAlarmClock
        // Future: show upcoming alarm in island
    }
}

@Composable
fun TimerExpandedContent(
    timeText: String,
    progress: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Animated circular timer ring
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center,
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "timerGlow")
            val ringGlow by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "timerRingGlow",
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 6.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Track
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Progress arc
                drawArc(
                    color = Color(0xFFFF9100).copy(alpha = ringGlow),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontSize = 28.sp,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Timer Running",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}
