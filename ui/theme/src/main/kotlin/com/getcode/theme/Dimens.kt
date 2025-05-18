package com.getcode.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.BuildConfig

val topBarHeight = 56.dp

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
                2.5.dp,
                5.dp,
                7.5.dp,
                10.dp,
                12.5.dp,
                15.dp,
                17.5.dp,
                20.dp,
                22.5.dp,
                25.dp,
                27.5.dp,
                30.dp,
                32.5.dp,
                35.dp,
                37.5.dp,
                40.dp,
                42.5.dp,
                45.dp,
                47.5.dp,
                50.dp
            )

            WindowSizeClass.NORMAL -> GridDimensionSet(
                5.dp,
                10.dp,
                15.dp,
                20.dp,
                25.dp,
                30.dp,
                35.dp,
                40.dp,
                45.dp,
                50.dp,
                55.dp,
                60.dp,
                65.dp,
                70.dp,
                75.dp,
                80.dp,
                85.dp,
                90.dp,
                95.dp,
                100.dp
            )

            WindowSizeClass.MEDIUM,
            WindowSizeClass.LARGE,
            -> GridDimensionSet(
                7.5.dp,
                15.dp,
                22.5.dp,
                30.dp,
                37.5.dp,
                45.dp,
                52.5.dp,
                60.dp,
                67.5.dp,
                75.dp,
                82.5.dp,
                90.dp,
                97.5.dp,
                105.dp,
                112.5.dp,
                120.dp,
                127.5.dp,
                135.dp,
                142.5.dp,
                150.dp
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
        x17 = 68.dp,
        x18 = 72.dp,
        x19 = 76.dp,
        x20 = 80.dp,
    )

/** Fixed 5pt grid **/
private val static5GridPreset =
    GridDimensionSet(
        x1 = 5.dp,
        x2 = 10.dp,
        x3 = 15.dp,
        x4 = 20.dp,
        x5 = 25.dp,
        x6 = 30.dp,
        x7 = 35.dp,
        x8 = 40.dp,
        x9 = 45.dp,
        x10 = 50.dp,
        x11 = 55.dp,
        x12 = 60.dp,
        x13 = 65.dp,
        x14 = 70.dp,
        x15 = 75.dp,
        x16 = 80.dp,
        x17 = 85.dp,
        x18 = 90.dp,
        x19 = 95.dp,
        x20 = 100.dp
    )

class Dimensions(
    val none: Dp = 0.dp,
    val border: Dp = 1.dp,
    val thickBorder: Dp = 2.dp,
    val modalHeightRatio: Float = 0.925f,
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
    val staticGrid: GridDimensionSet = static5GridPreset,
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
    val x17: Dp,
    val x18: Dp,
    val x19: Dp,
    val x20: Dp,
)
