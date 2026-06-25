package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.IntSize

// All chat animation spring specs in one place.
internal object ChatAnimations {
    // Message bubble insertion — scale from 0.95 + opacity.
    // Matches iOS insertionSpring: .spring(duration: 0.23, bounce: 0.27).
    val insertion: SpringSpec<Float> = spring(dampingRatio = 0.73f, stiffness = 746f)

    // Typing indicator entry/exit — scale from 0.95 + opacity.
    val typingIndicator: SpringSpec<Float> = spring(dampingRatio = 0.73f, stiffness = Spring.StiffnessHigh)

    // Action bar <-> composer swap — scale from 0.95 + opacity.
    val composerSwap: SpringSpec<Float> = spring(dampingRatio = 0.69f, stiffness = Spring.StiffnessHigh)

    // Delivered label appearance — scale from 0.95 + opacity.
    // Matches iOS deliveredSpring: .spring(duration: 0.4, bounce: 0.12).
    val delivered: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 250f)

    // Delivered -> Read label swap — scale + opacity.
    val readSwap: SpringSpec<Float> = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessHigh)

    // Receipt label exit when a new message is sent — fade out + collapse.
    private val deliveredIntSize: SpringSpec<IntSize> = spring(dampingRatio = 0.88f, stiffness = 250f)
    val receiptExit: ExitTransition = shrinkVertically(deliveredIntSize) + fadeOut(delivered)
}
