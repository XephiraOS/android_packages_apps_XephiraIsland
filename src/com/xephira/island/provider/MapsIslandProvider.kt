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
import android.graphics.drawable.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon as ComposeIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Shared data structure for active navigation updates.
 */
data class NavData(
    val title: String,
    val text: String,
    val icon: Icon? = null
)

/**
 * Singleton repository to post and track navigation updates.
 */
object NavigationTracker {
    private val _currentNav = MutableStateFlow<NavData?>(null)
    val currentNav: Flow<NavData?> = _currentNav

    fun updateNav(data: NavData?) {
        _currentNav.value = data
    }
}

/**
 * Dynamic Island provider for navigation and maps directions.
 * Intercepts status updates via NavigationTracker and renders turn-by-turn alerts.
 */
class MapsIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "maps"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content
    private var scope: CoroutineScope? = null

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope?.launch {
            NavigationTracker.currentNav.collectLatest { navData ->
                if (navData != null) {
                    val drawable = try {
                        navData.icon?.loadDrawable(context)
                    } catch (e: Exception) {
                        null
                    }

                    _content.value = IslandContent(
                        id = "maps_navigation",
                        category = IslandCategory.NAVIGATION,
                        title = navData.title,
                        subtitle = navData.text,
                        icon = drawable,
                        accentColor = Color(0xFF00E676), // Green navigation accent
                        expandedContent = {
                            MapsExpandedContent(
                                title = navData.title,
                                text = navData.text,
                                icon = navData.icon
                            )
                        },
                        dismissible = true
                    )
                } else {
                    _content.value = null
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        _content.value = null
    }
}

@Composable
fun MapsExpandedContent(
    title: String,
    text: String,
    icon: Icon?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBitmap = remember(icon) {
        try {
            icon?.loadDrawable(context)?.toBitmap(128, 128)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                ComposeIcon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}
