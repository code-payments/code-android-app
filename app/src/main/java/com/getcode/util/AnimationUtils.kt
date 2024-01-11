package com.getcode.util

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.navigation.NavBackStackEntry


object AnimationUtils {
    const val animationTime = 350


    val animationBillEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        )
    )

    val animationBillEnterSpring = scaleIn(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 1500f
        ),
        initialScale = 0.5f
    )

    val animationBillExit = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        )
    )
}