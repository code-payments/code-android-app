package com.getcode.view.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.getcode.util.WindowSize

@Composable
fun windowSizeCheck(): WindowSize = getWindowSize()

private var windowSizeCache: WindowSize? = null

@Composable
fun getWindowSize(): WindowSize {
    windowSizeCache?.let { return it }
    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp.dp
    return when {
        screenHeight < 600.dp -> WindowSize.SMALL
        //windowDpSize.height < 900.dp -> WindowSize.REGULAR
        else -> WindowSize.REGULAR
    }.also {
        windowSizeCache = it
    }
}