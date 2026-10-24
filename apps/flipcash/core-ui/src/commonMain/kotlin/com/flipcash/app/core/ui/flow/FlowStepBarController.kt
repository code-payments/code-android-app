package com.flipcash.app.core.ui.flow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Mutable chrome state for a [SteppedFlowScaffold]'s top bar. The scaffold updates [progress];
 * each step wires [onBack] / [onEndAction] via the composition local below.
 */
class FlowStepBarController {
    var progress: Float by mutableFloatStateOf(0f)
    var onBack: (() -> Unit)? by mutableStateOf(null)
    var onEndAction: (() -> Unit)? by mutableStateOf(null)
}

val LocalFlowStepBar = staticCompositionLocalOf<FlowStepBarController> {
    error("No FlowStepBarController provided")
}
