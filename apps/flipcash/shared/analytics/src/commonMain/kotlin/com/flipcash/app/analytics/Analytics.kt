package com.flipcash.app.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAnalytics: ProvidableCompositionLocal<FlipcashAnalytics> =
    staticCompositionLocalOf { FlipcashAnalytics.None }

@Composable
fun rememberAnalytics(): FlipcashAnalytics = LocalAnalytics.current
