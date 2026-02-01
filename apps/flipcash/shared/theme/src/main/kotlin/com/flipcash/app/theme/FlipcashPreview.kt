package com.flipcash.app.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.flipcash.app.theme.internal.Flipcash2DesignSystem
import com.flipcash.app.theme.internal.FlipcashLegacyDesignSystem
import com.getcode.theme.CodeTheme

@Composable
fun FlipcashPreview(
    showBackground: Boolean = false,
    useLegacyColors: Boolean = false,
    content: @Composable () -> Unit
) {
    val previewContent = @Composable {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .background(if (showBackground) CodeTheme.colors.background else Color.Transparent)
        ) {
            content()
        }
    }

    if (useLegacyColors) {
        FlipcashLegacyDesignSystem(previewContent)
    } else {
        Flipcash2DesignSystem(previewContent)
    }
}