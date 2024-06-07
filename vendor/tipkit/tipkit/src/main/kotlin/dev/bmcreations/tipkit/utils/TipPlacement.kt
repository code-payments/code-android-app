
package dev.bmcreations.tipkit.utils

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import dev.bmcreations.tipkit.data.TipPaddingPixels
import kotlin.math.roundToInt

/**
 * Determines the placement of the tip factoring in [constraints],
 * anchor size and position, [tip] size, and desired [alignment].
 *
 * @param constraints The [Constraints] of the screen
 * @param alignment The [Alignment] line to follow. This is where the tip will align relative to the
 * the anchor. For example, [Alignment.BottomCenter] implies that the tip will render below the anchor
 * centered horizontally, whereas [Alignment.TopStart] implies that the tip will render above and to the left
 * of the anchor.
 * @param anchorPosition The (x,y) coordinates ([Offset]) of the anchor (top left position)
 * @param anchorSize The [IntSize] of the anchor
 * @param tip The measured [Placeable] of the tip component
 * @param padding The space in pixels between the anchor and aligned tip.
 */
fun getCoordinatesForPlacement(
    constraints: Constraints,
    alignment: Alignment,
    anchorPosition: Offset,
    anchorSize: IntSize,
    padding: TipPaddingPixels,
    tip: Placeable,
    log: (String) -> Unit,
): Pair<Int, Int> {

    val isXLessThanZero = anchorPosition.x - tip.width < 0
    val isYLessThanZero = anchorPosition.y - tip.height < 0


    return when (alignment) {
        Alignment.TopCenter -> {
            val isAnchorCovered = anchorPosition.y.roundToInt() + anchorSize.height < tip.height
            when {
                isYLessThanZero && isAnchorCovered -> {
                    log("Anchor will be covered; shifting below anchor")
                    getCoordinatesForPlacement(
                        constraints,
                        Alignment.BottomCenter, anchorPosition, anchorSize, padding, tip, log
                    )
                }
                else -> {
                    val x = anchorPosition.x.roundToInt() + anchorSize.width / 2 - tip.width / 2
                    val y = anchorPosition.y.roundToInt() - anchorSize.height - padding.bottom.roundToInt()
                    x to y
                }
            }
        }

        Alignment.BottomCenter -> {
            val isBeyondHeightBounds =
                anchorPosition.x + tip.height > constraints.maxHeight
            val isAnchorCovered = anchorPosition.y.roundToInt() + anchorSize.height < constraints.maxHeight - tip.height

            when {
                isBeyondHeightBounds && isAnchorCovered -> {
                    log("Anchor will be covered; shifting above anchor")
                    getCoordinatesForPlacement(
                        constraints,
                        Alignment.TopCenter,
                        anchorPosition,
                        anchorSize,
                        padding,
                        tip,
                        log
                    )
                }
                else -> {
                    var x = anchorPosition.x.roundToInt() + anchorSize.width / 2 - tip.width / 2
                    if (x < padding.start) {
                        x = padding.start.roundToInt()
                    }
                    val y = anchorPosition.y.roundToInt() + anchorSize.height + padding.top.roundToInt()
                    x to y
                }
            }
        }

        Alignment.CenterStart -> {
            val isBeyondHeightBounds =
                anchorPosition.y + tip.height > constraints.maxHeight

            when {
                isXLessThanZero -> {
                    log("Tip will be offscreen; shifting to end of anchor")
                    getCoordinatesForPlacement(
                        constraints,
                        Alignment.CenterEnd,
                        anchorPosition,
                        anchorSize,
                        padding,
                        tip,
                        log
                    )
                }
                isBeyondHeightBounds -> {
                    log("Tip will be offscreen; shifting above anchor")
                    getCoordinatesForPlacement(
                        constraints,
                        Alignment.TopCenter,
                        anchorPosition,
                        anchorSize,
                        padding,
                        tip,
                        log
                    )
                }
                else -> {
                    val x = anchorPosition.x.roundToInt() - tip.width - padding.start.roundToInt()
                    val y = anchorPosition.y.roundToInt() + anchorSize.height / 2 - tip.height / 2
                    x to y
                }
            }
        }

        Alignment.CenterEnd -> {
            val isBeyondWidthBounds =
                anchorPosition.x + anchorSize.width + tip.width > constraints.maxWidth

            when {
                isBeyondWidthBounds -> {
                    log("Tip will be offscreen; shifting below anchor")
                    getCoordinatesForPlacement(
                        constraints,
                        Alignment.BottomCenter,
                        anchorPosition,
                        anchorSize,
                        padding,
                        tip,
                        log
                    )
                }
                else -> {
                    val x = anchorPosition.x.roundToInt() + anchorSize.width + padding.start.roundToInt()
                    val y = anchorPosition.y.roundToInt() + anchorSize.height / 2 - tip.height / 2
                    x to y
                }
            }
        }

        Alignment.Center -> {
            0 to 0
        }

        Alignment.TopStart -> {
            0 to 0
        }

        Alignment.TopEnd -> {
            0 to 0
        }

        Alignment.BottomStart -> {
            val x = anchorPosition.x.roundToInt()
            val y = anchorPosition.y.roundToInt() + anchorSize.height + padding.top.roundToInt()
            x to y
        }

        Alignment.BottomEnd -> {
            0 to 0
        }

        else -> anchorPosition.x.roundToInt() to anchorPosition.y.roundToInt()
    }
}
