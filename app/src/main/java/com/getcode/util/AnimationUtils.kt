package com.getcode.util

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.navigation.NavBackStackEntry


object AnimationUtils {
    private const val animationTime = 350

    val animationBackEnter = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(
            durationMillis = animationTime,
            easing = FastOutSlowInEasing
        )
    )

    val animationBackExit = slideOutHorizontally(
        targetOffsetX ={ -it },
        animationSpec = tween(
            durationMillis = animationTime,
            easing = FastOutSlowInEasing
        )
    )

    val animationFrontEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(
            durationMillis = animationTime,
            easing = FastOutSlowInEasing
        )
    )

    val animationFrontExit = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(
            durationMillis = animationTime,
            easing = FastOutSlowInEasing
        )
    )

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

    fun getExitTransition(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        animatedContentScope.apply {
            return slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(animationTime)
            )
        }
    }

    fun getPopExitTransition(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        animatedContentScope.apply {
            return slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(animationTime)
            )
        }
    }

    fun getEnterTransition(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        animatedContentScope.apply {
            return slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(animationTime)
            )
        }
    }

    fun getPopEnterTransition(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        animatedContentScope.apply {
            return slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(animationTime)
            )
        }
    }

    ///////////////////////////////////////////////////////////


    fun getExitTransitionNav(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        animatedContentScope.apply {
            return fadeOut(
                animationSpec = tween(animationTime)
            )
        }
    }

    fun getPopExitTransitionNav(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        animatedContentScope.apply {
            return fadeOut(
                animationSpec = tween(animationTime)
            )
        }
    }

    fun getEnterTransitionNav(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        animatedContentScope.apply {
            return fadeIn(
                animationSpec = tween(animationTime)
            )
        }
    }

    fun getPopEnterTransitionNav(animatedContentScope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        animatedContentScope.apply {
            return fadeIn(
                animationSpec = tween(animationTime)
            )
        }
    }
}