package com.getcode.navigation.scenes

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.getcode.navigation.FullscreenOverlay
import com.getcode.navigation.NavMetadataKeys

/** An [OverlayScene] that draws a [FullscreenOverlay] entry over everything beneath it. */
internal class FullscreenOverlayScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    override val metadata: Map<String, Any>,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FullscreenOverlayScene<*>) return false
        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()

    override val content: @Composable (() -> Unit) = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // NavDisplay appends an overlay pushed over another overlay to the end of its
                // list and draws the list in reverse, so without this the overlay lands under
                // the sheet it was opened from. Every overlay scene is a sibling in NavDisplay's
                // parent, so zIndex orders them.
                .zIndex(1f)
                // The entries beneath stay composed and interactive, so swallow every touch the
                // overlay's own controls leave unclaimed; otherwise a tap on the backdrop lands
                // on the sheet or screen underneath.
                .pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
                },
        ) {
            entry.Content()
        }
    }
}

/**
 * A [SceneStrategy] that draws entries marked [FullscreenOverlay] over the scene beneath them
 * instead of replacing it, so an open sheet survives the overlay being pushed and popped.
 *
 * Add it before [ModalBottomSheetSceneStrategy]. The overlay route animates itself in and out:
 * transitions between overlay scenes are [androidx.compose.animation.EnterTransition.None].
 */
class FullscreenOverlaySceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>,
    ): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val isOverlay = lastEntry.metadata[NavMetadataKeys.IsFullscreenOverlay.key] as? Boolean ?: false
        if (!isOverlay) return null

        val beneath = entries.dropLast(1).ifEmpty { return null }

        @Suppress("UNCHECKED_CAST")
        return FullscreenOverlayScene(
            key = lastEntry.contentKey as T,
            previousEntries = beneath,
            overlaidEntries = beneath,
            entry = lastEntry,
            metadata = lastEntry.metadata,
        )
    }
}
