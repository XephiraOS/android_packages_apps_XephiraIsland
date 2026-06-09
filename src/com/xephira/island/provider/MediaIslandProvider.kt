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

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Provides media playback information to the Dynamic Island.
 * Monitors active MediaSessions and displays track info, album art,
 * and provides playback controls in expanded mode.
 */
class MediaIslandProvider(
    private val context: Context,
) : IslandProvider {

    override val providerId = "media"

    private val _content = MutableStateFlow<IslandContent?>(null)
    override val content: Flow<IslandContent?> = _content

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var scope: CoroutineScope? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val controller = controllers?.firstOrNull()
        if (controller != null) {
            attachController(controller)
        } else {
            detachController()
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateContent()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateContent()
        }

        override fun onSessionDestroyed() {
            detachController()
        }
    }

    override fun start() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(context, NotificationIslandProvider::class.java)
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            controllers?.firstOrNull()?.let { attachController(it) }
        } catch (e: SecurityException) {
            // Notification listener may not be enabled yet
        }
    }

    override fun stop() {
        detachController()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {}
        scope?.cancel()
        scope = null
    }

    private fun attachController(controller: MediaController) {
        detachController()
        activeController = controller
        controller.registerCallback(mediaCallback)
        updateContent()
    }

    private fun detachController() {
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
        _content.value = null
    }

    private fun updateContent() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        if (!isPlaying && playbackState?.state != PlaybackState.STATE_PAUSED) {
            _content.value = null
            return
        }

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val accentColor = extractDominantColor(albumArt)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L
        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else -1f

        _content.value = IslandContent(
            id = "media_${controller.packageName}",
            category = IslandCategory.MEDIA,
            title = title,
            subtitle = artist,
            accentColor = accentColor,
            progress = progress,
            expandedContent = {
                MediaExpandedContent(
                    title = title,
                    artist = artist,
                    albumArt = albumArt,
                    isPlaying = isPlaying,
                    progress = progress,
                    accentColor = accentColor,
                    onPlayPause = {
                        if (isPlaying) controller.transportControls.pause()
                        else controller.transportControls.play()
                    },
                    onNext = { controller.transportControls.skipToNext() },
                    onPrevious = { controller.transportControls.skipToPrevious() },
                )
            },
            onTap = {
                if (isPlaying) controller.transportControls.pause()
                else controller.transportControls.play()
            },
        )

        // Auto-update progress
        if (isPlaying) {
            scope?.launch {
                delay(1000)
                updateContent()
            }
        }
    }

    private fun extractDominantColor(bitmap: android.graphics.Bitmap?): Color {
        if (bitmap == null) return Color(0xFF00E5FF)
        try {
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 4, 4, false)
            var sumR = 0
            var sumG = 0
            var sumB = 0
            var count = 0
            for (x in 0 until 4) {
                for (y in 0 until 4) {
                    val color = scaled.getPixel(x, y)
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF
                    val brightness = 0.299f * r + 0.587f * g + 0.114f * b
                    if (brightness in 35.0f..220.0f) {
                        sumR += r
                        sumG += g
                        sumB += b
                        count++
                    }
                }
            }
            scaled.recycle()
            if (count > 0) {
                return Color(sumR / count, sumG / count, sumB / count)
            }
        } catch (_: Exception) {}
        return Color(0xFF00E5FF)
    }

    override fun onIslandTapped() {
        val controller = activeController ?: return
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) controller.transportControls.pause()
        else controller.transportControls.play()
    }
}

@Composable
fun MediaExpandedContent(
    title: String,
    artist: String,
    albumArt: android.graphics.Bitmap?,
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            accentColor.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
