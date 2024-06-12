package com.getcode.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.SwitchColors
import androidx.compose.material.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.getcode.theme.SystemGreen
import com.getcode.theme.White
import com.getcode.ui.utils.addIf

object CodeToggleSwitchDefaults {

    val colors: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            disabledCheckedThumbColor = White.copy(ContentAlpha.disabled),
            disabledUncheckedThumbColor = White.copy(ContentAlpha.disabled),
            disabledCheckedTrackColor = SystemGreen.copy(ContentAlpha.disabled),
            disabledUncheckedTrackColor = Color(0xFF201D2F).copy(ContentAlpha.disabled),
            checkedThumbColor = White,
            uncheckedThumbColor = White,
            checkedTrackColor = SystemGreen,
            checkedTrackAlpha = 1f,
            uncheckedTrackAlpha = 1f,
            uncheckedTrackColor = Color(0xFF201D2F)
        )
}

@Composable
fun CodeToggleSwitch(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val width = 51.dp
    val height = 31.dp
    val gapBetweenThumbAndTrackEdge = 2.dp

    val thumbRadius = (height / 2) - gapBetweenThumbAndTrackEdge
    val animatePosition = animateFloatAsState(
        targetValue = if (checked) {
            with(LocalDensity.current) { (width - thumbRadius - gapBetweenThumbAndTrackEdge).toPx() }
        } else {
            with (LocalDensity.current) { (thumbRadius + gapBetweenThumbAndTrackEdge).toPx() }
        }, label = "thumb position"
    )

    val colors = CodeToggleSwitchDefaults.colors
    val trackColor by colors.trackColor(enabled = enabled, checked = checked)
    val thumbColor by colors.thumbColor(enabled = enabled, checked = checked)
    Canvas(
        modifier = Modifier
            .size(width = width, height = height)
            .addIf(onCheckedChange != null) {
                Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onCheckedChange?.invoke(!checked)
                        }
                    )
                }
            }.then(modifier)
    ) {
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(x = 45.dp.toPx(), y = 45.dp.toPx())
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadius.toPx(),
            center = Offset(
                x = animatePosition.value,
                y = size.height / 2
            )
        )
    }
}