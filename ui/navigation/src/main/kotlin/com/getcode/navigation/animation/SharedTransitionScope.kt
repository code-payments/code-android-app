package com.getcode.navigation.animation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}