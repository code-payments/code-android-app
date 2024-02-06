package com.getcode.ui.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun PaddingValues.calculateVerticalPadding() = calculateTopPadding() + calculateBottomPadding()

@Composable
fun PaddingValues.calculateStartPadding(): Dp {
    val ldr = LocalLayoutDirection.current
    return calculateLeftPadding(ldr)
}

@Composable
fun PaddingValues.calculateEndPadding(): Dp {
    val ldr = LocalLayoutDirection.current
    return calculateRightPadding(ldr)
}

@Composable
fun PaddingValues.calculateHorizontalPadding(): Dp {
    val ldr = LocalLayoutDirection.current
    return calculateLeftPadding(ldr) + calculateRightPadding(ldr)
}

@Composable
fun PaddingValues.minus(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues {
    return PaddingValues(
        start = calculateStartPadding() - start,
        top = calculateTopPadding() - top,
        end = calculateEndPadding() - end,
        bottom = calculateBottomPadding() - bottom,
    )
}

@Composable
fun PaddingValues.plus(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues {
    return PaddingValues(
        start = calculateStartPadding() + start,
        top = calculateTopPadding() + top,
        end = calculateEndPadding() + end,
        bottom = calculateBottomPadding() + bottom,
    )
}
