package com.flipcash.app.tokens.internal.components.marketcap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction


private class ShowOnShortLongPress(
    private val delayMs: Long = 150,
) : CartesianMarkerController {

    private var isPressed = false
    private var pressStartTime = 0L
    private var lastX = 0f

    override val acceptsLongPress: Boolean
        get() = true

    override val lock = CartesianMarkerController.Lock.Position

    override val consumeMoveEvents: Boolean
        get() = isPressed

    override fun shouldAcceptInteraction(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean {
        return when (interaction) {
            is Interaction.Press -> {
                pressStartTime = System.currentTimeMillis()
                false // don't show yet
            }
            is Interaction.LongPress -> {
                isPressed = true
                true
            }
            is Interaction.Move -> {
                val hasXChanged = interaction.point.x != lastX
                lastX = interaction.point.x

                if (!isPressed && hasXChanged && System.currentTimeMillis() - pressStartTime >= delayMs) {
                    isPressed = true
                }
                isPressed
            }
            is Interaction.Release -> {
                isPressed = false
                pressStartTime = 0L
                true
            }
            else -> false
        }
    }

    override fun shouldShowMarker(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean {
        return isPressed && interaction !is Interaction.Release
    }
}

@Composable
fun CartesianMarkerController.Companion.rememberShowOnShortLongPress(
    delayMs: Long = 150L,
): CartesianMarkerController = remember(delayMs) { ShowOnShortLongPress(delayMs) }