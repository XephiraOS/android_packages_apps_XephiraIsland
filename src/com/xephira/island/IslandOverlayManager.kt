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

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xephira.island.ui.IslandOverlayContent
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the system overlay window that hosts the Dynamic Island
 * Compose UI. Creates a floating window positioned at the top-center
 * of the screen and injects the Compose view tree with proper
 * lifecycle management.
 */
class IslandOverlayManager(
    private val context: Context,
    private val islandState: StateFlow<IslandState>,
) {
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: IslandLifecycleOwner? = null

    fun attachOverlay() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        lifecycleOwner = IslandLifecycleOwner().also {
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        overlayView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val state by islandState.collectAsState()
                IslandOverlayContent(
                    state = state,
                    onTap = {
                        val current = islandState.value
                        when (current.displayMode) {
                            IslandDisplayMode.COMPACT,
                            IslandDisplayMode.COMPACT_SPLIT -> {
                                // Send expand intent to service
                                val expandIntent = android.content.Intent(context, IslandService::class.java)
                                expandIntent.action = IslandService.ACTION_EXPAND
                                context.startService(expandIntent)
                            }
                            IslandDisplayMode.EXPANDED -> {
                                val collapseIntent = android.content.Intent(context, IslandService::class.java)
                                collapseIntent.action = IslandService.ACTION_COLLAPSE
                                context.startService(collapseIntent)
                            }
                            else -> {}
                        }
                    },
                    onDismiss = {
                        val collapseIntent = android.content.Intent(context, IslandService::class.java)
                        collapseIntent.action = IslandService.ACTION_COLLAPSE
                        context.startService(collapseIntent)
                    },
                )
            }
        }

        // Update flags when expanded to receive touch events
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detachOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView = null
        lifecycleOwner = null
    }
}

/**
 * Custom LifecycleOwner + SavedStateRegistryOwner for hosting
 * Compose in a WindowManager overlay without an Activity.
 */
internal class IslandLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
