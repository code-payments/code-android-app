package com.flipcash.app.permissions.internal.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.flipcash.app.core.ui.DeviceFrame
import com.flipcash.app.core.ui.SwitchPreview
import kotlinx.coroutines.delay

private class SwitchAnimationState(
    val scale: Float,
    val isInsidePhone: Boolean,
    val checked: Boolean,
)

@Composable
private fun rememberSwitchAnimation(animate: Boolean = true, checked: Boolean = false): SwitchAnimationState {
    val progress = remember { Animatable(if (animate) 0f else 2f) }
    var checked by remember { mutableStateOf(checked) }

    if (animate) {
        LaunchedEffect(Unit) {
            delay(300)
            // Phase 1: scale up inside phone
            progress.animateTo(1f, tween(600))
            delay(600)
            // Phase 2: pop out of phone
            progress.animateTo(2f, tween(600))
            delay(300)
            checked = true
        }
    }

    val p = progress.value

    return SwitchAnimationState(
        scale = when {
            p <= 1f -> 0.75f
            else -> lerp(0.75f, 1.2f, p - 1f)
        },
        isInsidePhone = p < 1.05f,
        checked = checked,
    )
}

@Composable
internal fun AnimatedSwitchPreview(animate: Boolean = false) {
    val animation = rememberSwitchAnimation(animate)

    DeviceFrame(
        clipToFrame = animation.isInsidePhone,
        contentAlignment = Alignment.Center,
    ) {
        SwitchPreview(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = animation.scale
                    scaleY = animation.scale
                },
            checked = animation.checked
        )
    }
}