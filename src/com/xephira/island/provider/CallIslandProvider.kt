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
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
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
 * Provides phone call state information to the Dynamic Island.
 * Monitors incoming, outgoing, and active calls, showing caller info
 * and call duration with expandable controls.
 */
class CallIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "call"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var callStartTime: Long = 0L
    private var callTimerJob: Job? = null
    private var scope: CoroutineScope? = null

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        phoneStateListener = object : PhoneStateListener() {
            @Deprecated("Deprecated in API 31")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallState(state, phoneNumber ?: "")
            }
        }

        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun stop() {
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        callTimerJob?.cancel()
        scope?.cancel()
        scope = null
        _content.value = null
    }

    private fun handleCallState(state: Int, number: String) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                callTimerJob?.cancel()
                _content.value = IslandContent(
                    id = "call_incoming",
                    category = IslandCategory.PHONE_CALL,
                    title = number.ifEmpty { "Unknown" },
                    subtitle = "Incoming Call",
                    accentColor = Color(0xFF4CAF50),
                    expandedContent = {
                        CallExpandedContent(
                            number = number.ifEmpty { "Unknown" },
                            state = "Incoming",
                            duration = null,
                        )
                    },
                    dismissible = false,
                )
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                callStartTime = System.currentTimeMillis()
                startCallTimer(number)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                callTimerJob?.cancel()
                callStartTime = 0L
                _content.value = null
            }
        }
    }

    private fun startCallTimer(number: String) {
        callTimerJob?.cancel()
        callTimerJob = scope?.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - callStartTime
                val minutes = (elapsed / 60000).toInt()
                val seconds = ((elapsed % 60000) / 1000).toInt()
                val durationText = String.format("%d:%02d", minutes, seconds)

                _content.value = IslandContent(
                    id = "call_active",
                    category = IslandCategory.PHONE_CALL,
                    title = number.ifEmpty { "On Call" },
                    subtitle = durationText,
                    accentColor = Color(0xFF4CAF50),
                    expandedContent = {
                        CallExpandedContent(
                            number = number.ifEmpty { "Unknown" },
                            state = "Active",
                            duration = durationText,
                        )
                    },
                    dismissible = false,
                )
                delay(1000)
            }
        }
    }
}

@Composable
fun CallExpandedContent(
    number: String,
    state: String,
    duration: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Caller icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Phone,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )

        Text(
            text = if (duration != null) "$state • $duration" else state,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Call action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // End call button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (state == "Incoming") {
                // Answer call button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Answer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
