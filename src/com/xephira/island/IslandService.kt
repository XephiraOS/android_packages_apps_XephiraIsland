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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Settings
import com.xephira.island.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Core foreground service that manages the Dynamic Island overlay.
 * Initializes all content providers, merges their content flows by priority,
 * and drives the island UI state through [IslandOverlayManager].
 */
class IslandService : Service() {

    private var overlayManager: IslandOverlayManager? = null
    private var scope: CoroutineScope? = null

    private val providers = mutableListOf<IslandProvider>()
    private val _islandState = MutableStateFlow(IslandState())

    companion object {
        const val CHANNEL_ID = "xephira_island_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE = "com.xephira.island.TOGGLE"
        const val ACTION_EXPAND = "com.xephira.island.EXPAND"
        const val ACTION_COLLAPSE = "com.xephira.island.COLLAPSE"

        /** Settings.Secure key to enable/disable the island */
        const val SETTING_ENABLED = "xephira_dynamic_island_enabled"

        /** Settings.System keys for DerpSpace settings compatibility */
        const val SETTING_ENABLED_SYS = "island_notification"
        const val SETTING_MEDIA_ENABLED_SYS = "island_notification_now_playing"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopProviders()
                Intent.ACTION_SCREEN_ON -> startProviders()
            }
        }
    }

    private val settingsObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            checkSettings()
        }
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        initProviders()
        startProviders()
        observeProviders()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Register content observers for settings
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(SETTING_ENABLED),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_ENABLED_SYS),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_MEDIA_ENABLED_SYS),
            false,
            settingsObserver
        )

        // Initial check
        checkSettings()
    }

    private fun checkSettings() {
        val enabled = Settings.Secure.getInt(contentResolver, SETTING_ENABLED, 1) == 1 &&
                      Settings.System.getInt(contentResolver, SETTING_ENABLED_SYS, 1) == 1

        if (!enabled) {
            overlayManager?.detachOverlay()
            overlayManager = null
            stopSelf()
        } else {
            if (overlayManager == null) {
                overlayManager = IslandOverlayManager(this, _islandState)
                overlayManager?.attachOverlay()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggleIsland()
            ACTION_EXPAND -> expandIsland()
            ACTION_COLLAPSE -> collapseIsland()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            contentResolver.unregisterContentObserver(settingsObserver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        overlayManager?.detachOverlay()
        overlayManager = null
        stopProviders()
        providers.clear()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun initProviders() {
        providers.add(MediaIslandProvider(this))
        providers.add(ChargingIslandProvider(this))
        providers.add(CallIslandProvider(this))
        providers.add(TimerIslandProvider(this))
        providers.add(TorchIslandProvider(this))
        providers.add(VolumeIslandProvider(this))
        providers.add(MapsIslandProvider(this))
        providers.add(PrivacyIslandProvider(this))
        providers.add(SmartContextProvider(this))
    }

    private fun startProviders() {
        providers.forEach { it.start() }
    }

    private fun stopProviders() {
        providers.forEach { it.stop() }
    }

    /**
     * Observes all provider content flows, selects the highest-priority
     * active content, and optionally picks a secondary content for
     * the compact split display mode.
     */
    private fun observeProviders() {
        scope?.launch {
            // Combine all provider content flows into a single merged flow
            val flows = providers.map { provider ->
                provider.content.map { content -> provider.providerId to content }
            }

            combine(flows) { contentArray ->
                val showMedia = Settings.System.getInt(
                    contentResolver,
                    SETTING_MEDIA_ENABLED_SYS,
                    1
                ) == 1

                val activeContents = contentArray
                    .mapNotNull { (_, content) -> content }
                    .filter { content ->
                        if (!showMedia && content.category == IslandCategory.MEDIA) false else true
                    }
                    .sortedByDescending { it.category.priority }

                val primary = activeContents.firstOrNull()
                val secondary = activeContents.getOrNull(1)

                when {
                    primary == null -> IslandState(
                        displayMode = IslandDisplayMode.HIDDEN,
                    )
                    secondary != null -> IslandState(
                        displayMode = _islandState.value.displayMode.let {
                            if (it == IslandDisplayMode.EXPANDED) it
                            else IslandDisplayMode.COMPACT_SPLIT
                        },
                        primaryContent = primary,
                        secondaryContent = secondary,
                    )
                    else -> IslandState(
                        displayMode = _islandState.value.displayMode.let {
                            if (it == IslandDisplayMode.EXPANDED) it
                            else IslandDisplayMode.COMPACT
                        },
                        primaryContent = primary,
                    )
                }
            }.distinctUntilChanged().collect { newState ->
                _islandState.value = newState
            }
        }
    }

    private fun toggleIsland() {
        val current = _islandState.value
        _islandState.value = when (current.displayMode) {
            IslandDisplayMode.HIDDEN -> current
            IslandDisplayMode.EXPANDED -> current.copy(
                displayMode = if (current.secondaryContent != null)
                    IslandDisplayMode.COMPACT_SPLIT else IslandDisplayMode.COMPACT
            )
            else -> current.copy(displayMode = IslandDisplayMode.EXPANDED)
        }
    }

    private fun expandIsland() {
        val current = _islandState.value
        if (current.primaryContent != null) {
            _islandState.value = current.copy(displayMode = IslandDisplayMode.EXPANDED)
        }
    }

    private fun collapseIsland() {
        val current = _islandState.value
        _islandState.value = current.copy(
            displayMode = if (current.secondaryContent != null)
                IslandDisplayMode.COMPACT_SPLIT else IslandDisplayMode.COMPACT
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Xephira Dynamic Island",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Persistent service for Dynamic Island overlay"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Dynamic Island")
            .setContentText("Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
