package com.getcode.ui.components.text.animated

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

internal fun defaultDigitEnter(initialOffsetY: Int): EnterTransition {
    return (slideInVertically(
        initialOffsetY = { initialOffsetY },
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = 80,
            easing = LinearOutSlowInEasing
        )
    ) + fadeIn())
}

internal fun defaultDigitExit(targetOffsetY: Int): ExitTransition {
    return fadeOut() + slideOutVertically(
        targetOffsetY = { targetOffsetY },
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )
}