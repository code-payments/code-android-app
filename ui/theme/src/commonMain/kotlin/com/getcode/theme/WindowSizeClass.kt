package com.getcode.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.getcode.utils.BuildFlags
import com.getcode.utils.Log

data class DeviceWindowSizeClass internal constructor(
    val widthSizeClass: WindowSizeClass,
    val heightSizeClass: WindowSizeClass,
)

@Composable
fun rememberWindowSizeClass(
    logDimensions: Boolean = BuildFlags.isDebug,
    screenSizeDp: ScreenSizeDp = currentScreenSizeDp()
): DeviceWindowSizeClass {
    val screenWidthDp = screenSizeDp.widthDp
    val screenHeightDp = screenSizeDp.heightDp

    return remember(screenWidthDp, screenHeightDp) {
        val screenWidthSizeClass = when {
            screenWidthDp <= 320 -> WindowSizeClass.COMPACT // compact phone (we device from above document to have more granular control over what we deem as 'compact')
            screenWidthDp <= 600 -> WindowSizeClass.NORMAL // our defacto phone threshold
            screenWidthDp <= 840 -> WindowSizeClass.MEDIUM // tablets (either in a resized window or smaller tablet) OR large unfolded inner displays
            else -> WindowSizeClass.LARGE // tablets at full width (non resized/multi window) OR large unfolded inner displays in landscape
        }

        val screenHeightSizeClass = when {
            screenHeightDp <= 480 -> WindowSizeClass.COMPACT // compact phone
            screenHeightDp <= 900 -> WindowSizeClass.NORMAL // our defacto phone threshold
            else -> WindowSizeClass.LARGE
        }

        DeviceWindowSizeClass(screenWidthSizeClass, screenHeightSizeClass)
    }.also {
        if (logDimensions) {
            Log.d("dimens:: width = { dp: $screenWidthDp, class: ${it.widthSizeClass.name} }, height = { dp:$screenHeightDp, class: ${it.heightSizeClass.name} }")
        }
    }
}
