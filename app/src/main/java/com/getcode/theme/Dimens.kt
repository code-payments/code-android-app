package com.getcode.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.BuildConfig

val topBarHeight = 56.dp
const val sheetHeight = 0.93f

internal val LocalDimens = staticCompositionLocalOf<Dimensions> {
    error("No Dimensions provided")
}

@Composable
fun calculateDimensions(
    logDimensions: Boolean = BuildConfig.DEBUG,
    configuration: Configuration = LocalConfiguration.current
): Dimensions {

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val (widthSizeClass, heightSizeClass) = rememberWindowSizeClass(
        logDimensions = logDimensions, configuration = configuration
    )

    return Dimensions(
        screenWidth = screenWidthDp.dp,
        screenHeight = screenHeightDp.dp,
        inset = when (widthSizeClass) {
            WindowSizeClass.COMPACT -> 10.dp
            WindowSizeClass.NORMAL -> 20.dp
            WindowSizeClass.MEDIUM,
            WindowSizeClass.LARGE,
            -> 30.dp
        },
        grid = when (widthSizeClass) {
            WindowSizeClass.COMPACT -> GridDimensionSet(
                2.dp,
                4.dp,
                6.dp,
                8.dp,
                10.dp,
                12.dp,
                14.dp,
                16.dp,
                18.dp,
                20.dp,
                22.dp,
                24.dp,
                26.dp,
                28.dp,
                30.dp,
                34.dp,
            )

            WindowSizeClass.NORMAL -> GridDimensionSet(
                4.dp,
                8.dp,
                12.dp,
                16.dp,
                20.dp,
                24.dp,
                28.dp,
                32.dp,
                36.dp,
                40.dp,
                44.dp,
                48.dp,
                52.dp,
                56.dp,
                60.dp,
                64.dp,
            )

            WindowSizeClass.MEDIUM,
            WindowSizeClass.LARGE,
            -> GridDimensionSet(
                8.dp,
                16.dp,
                24.dp,
                32.dp,
                40.dp,
                48.dp,
                56.dp,
                64.dp,
                72.dp,
                80.dp,
                88.dp,
                96.dp,
                104.dp,
                112.dp,
                120.dp,
                124.dp,
            )
        },
        widthWindowSizeClass = widthSizeClass,
        heightWindowSizeClass = heightSizeClass,
    )
}


@Composable
fun ProvideDimens(
    dimensions: Dimensions,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDimens provides dimensions, content = content)
}

enum class WindowSizeClass { COMPACT, NORMAL, MEDIUM, LARGE }

/** Fixed 8pt grid **/
private val staticGridPreset =
    GridDimensionSet(
        x1 = 4.dp,
        x2 = 8.dp,
        x3 = 12.dp,
        x4 = 16.dp,
        x5 = 20.dp,
        x6 = 24.dp,
        x7 = 28.dp,
        x8 = 32.dp,
        x9 = 36.dp,
        x10 = 40.dp,
        x11 = 44.dp,
        x12 = 48.dp,
        x13 = 52.dp,
        x14 = 56.dp,
        x15 = 60.dp,
        x16 = 64.dp,
    )

class Dimensions(
    val none: Dp = 0.dp,
    val border: Dp = 1.dp,
    val thickBorder: Dp = 2.dp,
    val inset: Dp,
    val screenWidth: Dp = Dp.Unspecified,
    val screenHeight: Dp = Dp.Unspecified,
    val widthWindowSizeClass: WindowSizeClass = WindowSizeClass.NORMAL,
    val heightWindowSizeClass: WindowSizeClass = WindowSizeClass.NORMAL,
    /**
     * Material design has grid spacings by 4dp increments for normal use cases
     * This field is dynamically sized based on screen size
     */
    val grid: GridDimensionSet,
    /**
     * A static grid that is screen size independent based on Material design 4dp spacing
     */
    val staticGrid: GridDimensionSet = staticGridPreset,
) {
    val isMediumWidth: Boolean
        get() = widthWindowSizeClass == WindowSizeClass.MEDIUM

    val isLargeWidth: Boolean
        get() = widthWindowSizeClass == WindowSizeClass.LARGE
}

data class GridDimensionSet(
    val x1: Dp,
    val x2: Dp,
    val x3: Dp,
    val x4: Dp,
    val x5: Dp,
    val x6: Dp,
    val x7: Dp,
    val x8: Dp,
    val x9: Dp,
    val x10: Dp,
    val x11: Dp,
    val x12: Dp,
    val x13: Dp,
    val x14: Dp,
    val x15: Dp,
    val x16: Dp,
)