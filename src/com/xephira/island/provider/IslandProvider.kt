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

import com.xephira.island.IslandContent
import kotlinx.coroutines.flow.Flow

/**
 * Base interface for all Dynamic Island content providers.
 * Each provider monitors a specific system event source and emits
 * [IslandContent] items when there is relevant data to display.
 */
interface IslandProvider {

    /** Unique identifier for this provider */
    val providerId: String

    /**
     * A flow of the current content this provider wants to display.
     * Emit null when this provider has nothing to show.
     */
    val content: Flow<IslandContent?>

    /** Called when the provider should begin monitoring system events */
    fun start()

    /** Called when the provider should stop monitoring and release resources */
    fun stop()

    /** Called when the user taps the island while this provider's content is showing */
    fun onIslandTapped() {}

    /** Called when the user long-presses the island */
    fun onIslandLongPressed() {}

    /** Called when this provider's content is dismissed by the user */
    fun onDismissed() {}
}
