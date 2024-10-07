package com.getcode.ui.theme

import androidx.compose.runtime.Composable
import com.getcode.theme.DesignSystem

@Composable
fun CodeTheme(content: @Composable () -> Unit) {
    DesignSystem(content = content)
}