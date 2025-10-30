package com.flipcash.app.bill.customization.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.measuredIntSize

@Composable
internal fun ControlPanelItem(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = CodeTheme.shapes.large,
    onMeasured: (IntSize) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(componentSize) {
        onMeasured(componentSize)
    }

    Box(
        modifier = modifier
            .measuredIntSize { componentSize = it }
            .border(
                border = BorderStroke(
                    width = CodeTheme.dimens.border,
                    color = Color.White.copy(0.48f)
                ),
                shape = shape
            )
            .clip(shape)
    ) {
        content()
    }
}