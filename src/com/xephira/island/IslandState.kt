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

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color

/**
 * Represents the visual state of the Dynamic Island.
 */
enum class IslandDisplayMode {
    /** Island is completely hidden */
    HIDDEN,
    /** Compact pill showing minimal info */
    COMPACT,
    /** Compact pill split into two halves showing two providers */
    COMPACT_SPLIT,
    /** Expanded showing full provider content */
    EXPANDED,
}

/**
 * Categories of island content, ordered by priority (highest first).
 */
enum class IslandCategory(val priority: Int) {
    PHONE_CALL(100),
    TIMER(80),
    NAVIGATION(70),
    MEDIA(50),
    CHARGING(40),
    NOTIFICATION(20),
}

/**
 * Data class representing a single island content item from any provider.
 */
data class IslandContent(
    val id: String,
    val category: IslandCategory,
    val title: String,
    val subtitle: String = "",
    val icon: Drawable? = null,
    val accentColor: Color = Color(0xFF00A3FF),
    val progress: Float = -1f,
    val expandedContent: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    val onTap: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val dismissible: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Snapshot of the full island state for the UI layer.
 */
data class IslandState(
    val displayMode: IslandDisplayMode = IslandDisplayMode.HIDDEN,
    val primaryContent: IslandContent? = null,
    val secondaryContent: IslandContent? = null,
    val isAnimating: Boolean = false,
)
