/*
 * Copyright (C) 2026 XephiraOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.xephira.island.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xephira.island.IslandCategory
import com.xephira.island.IslandContent
import com.xephira.island.IslandDisplayMode
import com.xephira.island.IslandState
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════
// Obsidian Glassmorphism Design Tokens
// ═══════════════════════════════════════════════════════
private val IslandBgDeep = Color(0xFF060911)
private val IslandBgSurface = Color(0xFF0A0E16)
private val IslandBorderCyan = Color(0xFF00A3FF)
private val IslandGlowCyan = Color(0xFF00E5FF)
private val IslandTextPrimary = Color(0xFFF0F4FF)
private val IslandTextSecondary = Color(0xFFA0A8B8)

// ═══════════════════════════════════════════════════════
// Liquid Spring Specs — the secret to buttery-smooth feel
// ═══════════════════════════════════════════════════════

/** Ultra-smooth spring for size morphing — low stiffness = slow, elastic feel */
private val LiquidSizeSpec = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 180f,
    visibilityThreshold = 0.5f,
)

/** Snappier spring for quick interactions like tap feedback */
private val SnapSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = 400f,
)

/** Gentle reveal spring for content fading in */
private val RevealSpec = tween<Float>(280, easing = FastOutSlowInEasing)

// ═══════════════════════════════════════════════════════
// Root Overlay
// ═══════════════════════════════════════════════════════

/**
 * Root composable for the Dynamic Island overlay.
 * Manages the full lifecycle of the floating pill: appear → morph → expand → dismiss.
 * All transitions use custom liquid spring physics for organic, fluid motion.
 */
@Composable
fun IslandOverlayContent(
    state: IslandState,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isVisible = state.displayMode != IslandDisplayMode.HIDDEN

    // Liquid scale entrance/exit
    val targetScale = if (isVisible) 1f else 0f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 220f,
        ),
        label = "rootScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 200 else 150,
            easing = FastOutSlowInEasing,
        ),
        label = "rootAlpha",
    )

    if (animatedScale > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 10.dp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = animatedAlpha
                    // Slight Y translation on entrance for a "drop in" feel
                    translationY = (1f - animatedScale) * -24f
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            // Dismiss scrim behind expanded island
            if (state.displayMode == IslandDisplayMode.EXPANDED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(600.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                )
            }

            IslandPill(
                state = state,
                onTap = onTap,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// The Island Pill — morphing container
// ═══════════════════════════════════════════════════════

@Composable
private fun IslandPill(
    state: IslandState,
    onTap: () -> Unit,
) {
    val isExpanded = state.displayMode == IslandDisplayMode.EXPANDED

    // ── Liquid size morphing ──
    // Use raw float animation with custom spring for maximum fluidity
    val targetW = when (state.displayMode) {
        IslandDisplayMode.HIDDEN -> 100f
        IslandDisplayMode.COMPACT -> 190f
        IslandDisplayMode.COMPACT_SPLIT -> 240f
        IslandDisplayMode.EXPANDED -> 330f
    }
    val targetH = when (state.displayMode) {
        IslandDisplayMode.HIDDEN -> 28f
        IslandDisplayMode.COMPACT -> 34f
        IslandDisplayMode.COMPACT_SPLIT -> 34f
        IslandDisplayMode.EXPANDED -> 0f // will use wrapContent
    }
    val targetRadius = if (isExpanded) 28f else 50f

    val animW by animateFloatAsState(targetW, LiquidSizeSpec, label = "w")
    val animH by animateFloatAsState(targetH, LiquidSizeSpec, label = "h")
    val animRadius by animateFloatAsState(targetRadius, LiquidSizeSpec, label = "r")

    // ── Tap press feedback — squish effect ──
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 500f,
        ),
        label = "press",
    )

    // ── Breathing border glow ──
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPhase",
    )
    val borderAlpha = lerp(0.12f, 0.45f, glowPhase)
    val haloAlpha = lerp(0.04f, 0.18f, glowPhase)

    val pillShape = RoundedCornerShape(animRadius.dp)

    Box(contentAlignment = Alignment.TopCenter) {
        // ── Layer 1: Outer halo blur ──
        Box(
            modifier = Modifier
                .width(animW.dp + 8.dp)
                .then(
                    if (isExpanded) Modifier.wrapContentHeight()
                    else Modifier.height(animH.dp + 8.dp)
                )
                .graphicsLayer { alpha = haloAlpha }
                .clip(pillShape)
                .background(IslandGlowCyan.copy(alpha = 0.15f))
                .blur(16.dp)
        )

        // ── Layer 2: Main island body ──
        Box(
            modifier = Modifier
                .width(animW.dp)
                .then(
                    if (isExpanded) Modifier.wrapContentHeight()
                    else Modifier.height(animH.dp)
                )
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(pillShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            IslandBgDeep,
                            IslandBgSurface,
                        )
                    )
                )
                // ── Animated gradient border ──
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            0f to IslandBorderCyan.copy(alpha = borderAlpha * 0.8f),
                            0.25f to IslandGlowCyan.copy(alpha = borderAlpha),
                            0.5f to IslandBorderCyan.copy(alpha = borderAlpha * 0.3f),
                            0.75f to IslandGlowCyan.copy(alpha = borderAlpha * 0.9f),
                            1f to IslandBorderCyan.copy(alpha = borderAlpha * 0.8f),
                        ),
                        cornerRadius = CornerRadius(animRadius.dp.toPx()),
                        style = Stroke(width = stroke),
                    )
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap,
                ),
        ) {
            // ── Content switcher with crossfade ──
            IslandContentSwitcher(state = state)
        }
    }
}

// ═══════════════════════════════════════════════════════
// Content Switcher — smooth crossfade between modes
// ═══════════════════════════════════════════════════════

@Composable
private fun IslandContentSwitcher(state: IslandState) {
    // Use Crossfade for silky-smooth content transitions
    Crossfade(
        targetState = state.displayMode,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "contentSwitch",
    ) { mode ->
        when (mode) {
            IslandDisplayMode.COMPACT -> {
                state.primaryContent?.let { CompactContent(it) }
            }
            IslandDisplayMode.COMPACT_SPLIT -> {
                CompactSplitContent(
                    primary = state.primaryContent,
                    secondary = state.secondaryContent,
                )
            }
            IslandDisplayMode.EXPANDED -> {
                state.primaryContent?.let { ExpandedContent(it) }
            }
            IslandDisplayMode.HIDDEN -> {
                Spacer(modifier = Modifier.size(1.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Compact Mode — single provider pill
// ═══════════════════════════════════════════════════════

@Composable
private fun CompactContent(content: IslandContent) {
    // Slide-in animation for content changes
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(content.id) {
        appeared = false
        delay(30)
        appeared = true
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "compactAlpha",
    )
    val contentSlide by animateFloatAsState(
        targetValue = if (appeared) 0f else 8f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "compactSlide",
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationX = contentSlide
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: animated category icon
        CategoryIcon(
            category = content.category,
            accentColor = content.accentColor,
            size = 18,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Center: title with marquee-like feel
        Text(
            text = content.title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
            ),
            color = IslandTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Right: progress ring or live dot
        if (content.progress >= 0f) {
            MiniProgressRing(
                progress = content.progress,
                color = content.accentColor,
            )
        } else {
            BreathingDot(color = content.accentColor)
        }
    }
}

// ═══════════════════════════════════════════════════════
// Compact Split — two providers side-by-side
// ═══════════════════════════════════════════════════════

@Composable
private fun CompactSplitContent(
    primary: IslandContent?,
    secondary: IslandContent?,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left half
        primary?.let { content ->
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(content.category, content.accentColor, size = 14)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = IslandTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Center divider — animated pulse
        val infiniteTransition = rememberInfiniteTransition(label = "divider")
        val dividerAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "divAlpha",
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(3.dp)
                .clip(CircleShape)
                .background(IslandGlowCyan.copy(alpha = dividerAlpha))
        )

        // Right half
        secondary?.let { content ->
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = IslandTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                CategoryIcon(content.category, content.accentColor, size = 14)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Expanded Mode — full provider content with liquid reveal
// ═══════════════════════════════════════════════════════

@Composable
private fun ExpandedContent(content: IslandContent) {
    // Staggered reveal animation
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60) // tiny delay so the morph completes first
        revealed = true
    }

    val headerAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(250, 0, FastOutSlowInEasing),
        label = "headerAlpha",
    )
    val bodyAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(280, 80, FastOutSlowInEasing),
        label = "bodyAlpha",
    )
    val bodySlideY by animateFloatAsState(
        targetValue = if (revealed) 0f else 16f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 250f),
        label = "bodySlide",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Category header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = headerAlpha }
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(content.category, content.accentColor, size = 14)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = content.category.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = content.accentColor.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.weight(1f))
            // Live indicator
            BreathingDot(color = content.accentColor)
        }

        // ── Subtle divider line ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(0.5.dp)
                .graphicsLayer { alpha = headerAlpha * 0.3f }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            IslandGlowCyan.copy(alpha = 0.3f),
                            Color.Transparent,
                        )
                    )
                )
        )

        // ── Provider expanded content with slide-up reveal ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = bodyAlpha
                    translationY = bodySlideY
                }
        ) {
            content.expandedContent?.invoke()
                ?: DefaultExpandedContent(content)
        }
    }
}

/**
 * Fallback expanded content if the provider doesn't supply custom UI.
 */
@Composable
private fun DefaultExpandedContent(content: IslandContent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleMedium,
            color = IslandTextPrimary,
        )
        if (content.subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = IslandTextSecondary,
            )
        }
        if (content.progress >= 0f) {
            Spacer(modifier = Modifier.height(12.dp))
            ExpandedProgressBar(
                progress = content.progress,
                color = content.accentColor,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Shared UI Components — micro-animated for fluid feel
// ═══════════════════════════════════════════════════════

@Composable
private fun CategoryIcon(
    category: IslandCategory,
    accentColor: Color,
    size: Int,
) {
    val icon = when (category) {
        IslandCategory.PHONE_CALL -> Icons.Filled.Phone
        IslandCategory.TIMER -> Icons.Filled.Timer
        IslandCategory.NAVIGATION -> Icons.Filled.Navigation
        IslandCategory.MEDIA -> Icons.Filled.MusicNote
        IslandCategory.CHARGING -> Icons.Filled.BoltOutlined
        IslandCategory.NOTIFICATION -> Icons.Filled.Notifications
    }

    // Subtle continuous rotation for "alive" feel on certain categories
    val infiniteTransition = rememberInfiniteTransition(label = "iconPulse_$category")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "iconScale",
    )

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier
            .size(size.dp)
            .scale(if (category == IslandCategory.CHARGING) iconScale else 1f),
    )
}

/**
 * Breathing dot — the classic "alive" indicator with smooth sine-wave alpha.
 */
@Composable
private fun BreathingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotScale",
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .scale(dotScale)
            .clip(CircleShape)
            .background(color.copy(alpha = dotAlpha))
    )
}

/**
 * Compact circular progress ring for the pill's right side.
 */
@Composable
private fun MiniProgressRing(
    progress: Float,
    color: Color,
) {
    // Animate progress changes smoothly
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "miniProgress",
    )

    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { animProgress },
            modifier = Modifier.size(18.dp),
            color = color,
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeWidth = 2.dp,
        )
    }
}

/**
 * Full-width progress bar for expanded view with gradient fill.
 */
@Composable
private fun ExpandedProgressBar(
    progress: Float,
    color: Color,
) {
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "expandedProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animProgress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.6f),
                            color,
                        )
                    )
                )
        )
    }
}

// ═══════════════════════════════════════════════════════
// Utility
// ═══════════════════════════════════════════════════════

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
