package com.getcode.ui.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*


object AnimationUtils {
    const val animationTime = 350


    val animationBillEnterGive = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        )
    )

    val animationBillEnterGrabbed = scaleIn(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 1500f
        ),
        initialScale = 0.5f
    )

    val animationBillExitReturned = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        )
    )

    val animationBillExitGrabbed = fadeOut(tween(durationMillis = 100)) + scaleOut(
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 400f
        ),
        targetScale = 1.1f
    )
}