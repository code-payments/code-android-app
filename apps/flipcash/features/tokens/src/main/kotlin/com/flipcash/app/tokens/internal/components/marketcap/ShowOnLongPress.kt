package com.flipcash.app.tokens.internal.components.marketcap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction

private class ShowOnLongPress : CartesianMarkerController {

    private var isPressed = false

    override val acceptsLongPress: Boolean
        get() = true

    override val lock = CartesianMarkerController.Lock.Position

    override val consumeMoveEvents: Boolean
        get() = isPressed

    override fun shouldAcceptInteraction(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ) =
        when (interaction) {
            is Interaction.LongPress -> {
                isPressed = true
                true
            }
            is Interaction.Move -> isPressed
            is Interaction.Release -> {
                isPressed = false
                true
            }
            else -> false
        }

    override fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>) =
        interaction !is Interaction.Release
}

@Composable
internal fun CartesianMarkerController.Companion.rememberShowOnLongPress(): CartesianMarkerController =
    remember { ShowOnLongPress() }