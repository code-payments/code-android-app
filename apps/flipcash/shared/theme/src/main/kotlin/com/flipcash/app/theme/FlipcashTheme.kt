package com.flipcash.app.theme

import androidx.compose.runtime.Composable
import com.flipcash.app.theme.internal.Flipcash2DesignSystem

@Composable
fun FlipcashTheme(
    content: @Composable () -> Unit,
) {
    Flipcash2DesignSystem(content)
}