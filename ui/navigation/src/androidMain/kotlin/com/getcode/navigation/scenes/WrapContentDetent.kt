package com.getcode.navigation.scenes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.isSpecified
import com.composeunstyled.BottomSheetState
import com.composeunstyled.SheetDetent
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/** The most of its container a wrap-content sheet may take: the same as [Expanded]. */
internal const val WrapContentMaxFraction = 0.925f

/**
 * Resting detent for a sheet that hugs its content: the height the content asked for, never more
 * than [WrapContentMaxFraction] of the container.
 *
 * compose-unstyled lays a sheet out to its detent height, not to the content. A detent that
 * returns the `sheetHeight` the library hands it puts the sheet in the library's content-height
 * mode, where the sheet's measure writes the content's height into its state and reads it back in
 * the same pass. Every scene sits in a `SharedTransitionLayout`, so the sheet is measured twice a
 * frame; content whose height animates (an `AnimatedContent` with a `SizeTransform`) reports one
 * height to the lookahead pass and another to the main pass, each write invalidates the other,
 * and the frame never finishes. That was the host Invite sheet's ANR.
 *
 * This detent never reads `sheetHeight`, so the library treats it as fixed. [content] records the
 * content's own height after layout, and [Track] re-resolves the detents when it changes, a frame
 * later rather than inside the measure pass.
 */
@Stable
internal class WrapContentDetent {
    private var contentHeight by mutableStateOf(Dp.Unspecified)

    val detent = SheetDetent("wrap-content") { containerHeight, _ ->
        val max = containerHeight * WrapContentMaxFraction
        // Unmeasured, it opens toward the cap; the first layout pulls it in to the content.
        if (contentHeight.isSpecified) contentHeight.coerceAtMost(max) else max
    }

    /**
     * Measures the sheet's content at its natural height, up to the cap, and records it. The
     * content is laid out at the top of whatever height the sheet currently has, so it is clipped
     * while the sheet catches up with it rather than squeezed.
     */
    @Composable
    fun Modifier.content(): Modifier {
        val density = LocalDensity.current
        val maxHeightPx = (LocalWindowInfo.current.containerSize.height * WrapContentMaxFraction)
            .roundToInt()
        return layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minHeight = 0, maxHeight = maxHeightPx.coerceAtLeast(0))
            )
            layout(placeable.width, constraints.constrainHeight(placeable.height)) {
                placeable.place(0, 0)
            }
        }.onSizeChanged { contentHeight = with(density) { it.height.toDp() } }
    }

    /** Moves [state]'s anchors whenever the content's recorded height changes. */
    @Composable
    fun Track(state: BottomSheetState) {
        LaunchedEffect(state) {
            snapshotFlow { contentHeight }
                .drop(1)
                .collect { state.invalidateDetents() }
        }
    }
}

@Composable
internal fun rememberWrapContentDetent(): WrapContentDetent = remember { WrapContentDetent() }
