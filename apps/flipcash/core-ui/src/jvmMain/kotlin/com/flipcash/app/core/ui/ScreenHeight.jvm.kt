package com.flipcash.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
internal actual fun screenHeightPx(): Float = LocalWindowInfo.current.containerSize.height.toFloat()
