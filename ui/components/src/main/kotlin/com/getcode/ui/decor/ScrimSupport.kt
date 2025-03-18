package com.getcode.ui.decor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.getcode.theme.Black40
import com.getcode.ui.core.rememberedClickable

sealed interface ScrimState {
    data object Visible: ScrimState
    data object Hidden: ScrimState

    val isVisible: Boolean
        get() = this is Visible
}

sealed interface ScrimStateChange {
    data object Show: ScrimStateChange
    data object Hide: ScrimStateChange
}

val LocalScrim = staticCompositionLocalOf<ScrimState> { ScrimState.Hidden }
val LocalScrimHandler = staticCompositionLocalOf<(ScrimStateChange) -> Unit> { { } }

@Composable
fun ScrimSupport(screenContent: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        var scrimState by remember { mutableStateOf<ScrimState>(ScrimState.Hidden) }
        val handleStateChange = { event: ScrimStateChange ->
            scrimState = when (event) {
                ScrimStateChange.Hide -> ScrimState.Hidden
                is ScrimStateChange.Show -> ScrimState.Visible
            }
        }

        CompositionLocalProvider(
            LocalScrim provides scrimState,
            LocalScrimHandler provides handleStateChange
        ) {
            screenContent()
        }

        val scrimAlpha by animateFloatAsState(if (scrimState.isVisible) 1f else 0f, label = "scrim visibility")

        when (scrimState) {
            is ScrimState.Visible -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Black40)
                        .rememberedClickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() }) {}
                )
            }

            ScrimState.Hidden -> Unit
        }
    }
}