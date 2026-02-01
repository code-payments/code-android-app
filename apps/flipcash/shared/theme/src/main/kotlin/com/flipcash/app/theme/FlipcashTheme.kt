package com.flipcash.app.theme

import androidx.compose.runtime.Composable
import com.flipcash.app.theme.internal.Flipcash2DesignSystem
import com.flipcash.app.theme.internal.FlipcashLegacyDesignSystem

@Composable
fun FlipcashTheme(
    useLegacyColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (useLegacyColors) {
        FlipcashLegacyDesignSystem(content)
    } else {
        Flipcash2DesignSystem(content)
    }
}