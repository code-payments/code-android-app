package com.getcode.ui.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlin.math.pow


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
            dampingRatio = 0.9f,
            stiffness = 400f
        ),
        targetScale = 1.1f
    )

    val modalEnter: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(durationMillis = ModalAnimationSpeed.Normal.duration, delayMillis = ModalAnimationSpeed.Normal.delay)
    )

    val modalEnterSlow: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(durationMillis = ModalAnimationSpeed.Slow.duration, delayMillis = ModalAnimationSpeed.Slow.delay)
    )

    val modalEnterFast: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(durationMillis = ModalAnimationSpeed.Fast.duration, delayMillis = ModalAnimationSpeed.Fast.delay)
    )

    val modalExit: ExitTransition = slideOutVertically(targetOffsetY = { it })

    fun <S> modalAnimationSpec(speed: ModalAnimationSpeed = ModalAnimationSpeed.Normal): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        when (speed) {
            ModalAnimationSpeed.Fast -> modalEnterFast
            ModalAnimationSpeed.Normal -> modalEnter
            ModalAnimationSpeed.Slow -> modalEnterSlow
        }  togetherWith modalExit
    }

    fun ease(
        value: Float,
        fromRange: ClosedFloatingPointRange<Float>,
        toRange: ClosedFloatingPointRange<Float>,
        easeIn: Boolean,
        easeOut: Boolean
    ): Float {
        val normalizedValue = (value - fromRange.start) / (fromRange.endInclusive - fromRange.start)

        val easedValue: Float = if (easeIn && easeOut) {
            if (normalizedValue < 0.5f) {
                4 * normalizedValue * normalizedValue * normalizedValue
            } else {
                1 - (-2 * normalizedValue + 2).toDouble().pow(3.0).toFloat() / 2
            }
        } else if (easeIn) {
            normalizedValue * normalizedValue * normalizedValue
        } else if (easeOut) {
            1 - (1 - normalizedValue).toDouble().pow(3.0).toFloat()
        } else {
            normalizedValue
        }

        return easedValue * (toRange.endInclusive - toRange.start) + toRange.start
    }
}

sealed class ModalAnimationSpeed(
    val duration: Int,
    val delay: Int
) {
    data object Slow : ModalAnimationSpeed(750, 750)
    data object Normal: ModalAnimationSpeed(450, 450)
    data object Fast: ModalAnimationSpeed(200, 200)
}