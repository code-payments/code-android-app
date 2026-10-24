package com.flipcash.app.core.ui

import androidx.compose.runtime.Composable

/** Screen height in pixels: `Configuration.screenHeightDp` on Android, the window's container height on desktop. */
@Composable
internal expect fun screenHeightPx(): Float
