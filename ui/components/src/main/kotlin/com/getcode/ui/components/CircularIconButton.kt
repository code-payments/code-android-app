package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.AppBarDefaults.IconSize
import com.getcode.ui.core.rememberedClickable

private val ButtonSize = 40.dp
private val ButtonBackground = Color.White.copy(alpha = 0.1f)


@Composable
fun CircularIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    testTag: String? = null,
    content: @Composable (Dp) -> Unit,
) {
    Box(
        modifier = modifier
            .size(ButtonSize)
            .background(ButtonBackground, CircleShape)
            .clip(CircleShape)
            .rememberedClickable { onClick() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(IconSize)
    }
}