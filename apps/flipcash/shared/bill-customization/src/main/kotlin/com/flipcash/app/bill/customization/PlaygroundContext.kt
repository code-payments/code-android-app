package com.flipcash.app.bill.customization

import com.flipcash.app.bill.customization.models.PlaygroundFeature

sealed interface PlaygroundContext {
    val renderAsOverlay: Boolean
    val availableFeatures: List<PlaygroundFeature>

    /** Playground opened as a standalone overlay (Advanced → Bill Creator). */
    data object Standalone : PlaygroundContext {
        override val renderAsOverlay: Boolean = true
        override val availableFeatures: List<PlaygroundFeature>
            get() = PlaygroundFeature.entries
    }

    /** Playground embedded inside the Currency Creator flow. */
    data object CurrencyCreator : PlaygroundContext {
        override val renderAsOverlay: Boolean = false
        override val availableFeatures: List<PlaygroundFeature>
            get() = listOf(PlaygroundFeature.Background)
    }
}
