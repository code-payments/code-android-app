package com.flipcash.app.permissions.internal.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.flipcash.app.core.ui.DeviceFrame
import com.flipcash.app.core.ui.NotificationPreview
import com.flipcash.shared.permissions.R
import kotlinx.coroutines.delay

private class NotificationAnimationState(
    val offsetYPx: Float,
    val scale: Float,
    val isInsidePhone: Boolean,
)

@Composable
private fun rememberNotificationAnimation(): NotificationAnimationState {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(300)
        progress.animateTo(1f, tween(600))
        delay(600)
        progress.animateTo(2f, tween(600))
    }

    val p = progress.value

    return NotificationAnimationState(
        offsetYPx = with(density) {
            when {
                p <= 1f -> lerp(-120f, 10f, p).dp.toPx()
                else -> lerp(10f, 80f, p - 1f).dp.toPx()
            }
        },
        scale = when {
            p <= 1f -> 0.75f
            else -> lerp(0.75f, 1.2f, p - 1f)
        },
        isInsidePhone = p < 1.05f,
    )
}

@Composable
internal fun AnimatedNotificationPreview() {
    val animation = rememberNotificationAnimation()

    DeviceFrame(
        clipToFrame = animation.isInsidePhone
    ) {
        NotificationPreview(
            modifier = Modifier
                .graphicsLayer {
                    translationY = animation.offsetYPx
                    scaleX = animation.scale
                    scaleY = animation.scale
                },
            title = stringResource(R.string.notification_preview_title),
            body = stringResource(R.string.notification_preview_body),
        )
    }
}