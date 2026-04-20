@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.flipcash.app.core.ui.transitions

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize.Companion.AnimatedSize
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.Spring.StiffnessLow
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.getcode.animation.LocalSharedTransitionScope

sealed class SharedTransition(
    val key: String,
    open val enterTransition: EnterTransition = fadeIn(),
    open val exitTransition: ExitTransition = fadeOut(),
    open val boundsTransform: BoundsTransform = DefaultTransform,
    open val clipInOverlayDuringTransition: OverlayClip = ParentClip,
) {
    data object CurrencyName: SharedTransition("currency-name") {

    }
    data object CurrencyIcon: SharedTransition("currency-icon") {
        override val clipInOverlayDuringTransition = CircleOverlayClip
    }

    data object CurrencyBill: SharedTransition("currency-bill")
}

private val DefaultTransform = BoundsTransform { _, _ ->
    spring(
        stiffness = StiffnessLow,
        visibilityThreshold = Rect.VisibilityThreshold
    )
}

@ExperimentalSharedTransitionApi
val ParentClip: OverlayClip =
    object : OverlayClip {
        override fun getClipPath(
            sharedContentState: SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Path? {
            return sharedContentState.parentSharedContentState?.clipPathInOverlay
        }
    }

@ExperimentalSharedTransitionApi
val CircleOverlayClip: OverlayClip =
    object : OverlayClip {
        override fun getClipPath(
            sharedContentState: SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Path {
            return Path().apply { addOval(bounds) }
        }
    }

@Composable
fun Modifier.sharedBoundsTransition(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    boundsTransform: BoundsTransform = DefaultTransform,
    zIndexInOverlay: Float = 0f,
    renderInOverlayDuringTransition: Boolean = true,
    resizeMode: SharedTransitionScope.ResizeMode = scaleToBounds(ContentScale.FillWidth, Center),
    placeholderSize: PlaceholderSize = AnimatedSize,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier {
    if(LocalInspectionMode.current) {
        return this
    }

    val animatedScope = animatedVisibilityScope ?: LocalNavAnimatedContentScope.current

    return with(sharedTransitionScope) {
        this@sharedBoundsTransition.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            enter = enter,
            exit = exit,
            animatedVisibilityScope = animatedScope,
            boundsTransform = boundsTransform,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            resizeMode = resizeMode,
            zIndexInOverlay = zIndexInOverlay,
            placeholderSize = placeholderSize,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }
}

@Composable
fun Modifier.sharedBoundsTransition(
    transition: SharedTransition,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    zIndexInOverlay: Float = 0f,
    renderInOverlayDuringTransition: Boolean = true,
    resizeMode: SharedTransitionScope.ResizeMode = scaleToBounds(ContentScale.FillWidth, Center),
    placeholderSize: PlaceholderSize = AnimatedSize,
): Modifier {
    return sharedBoundsTransition(
        key = transition.key,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransitionScope = sharedTransitionScope,
        enter = transition.enterTransition,
        exit = transition.exitTransition,
        boundsTransform = transition.boundsTransform,
        resizeMode = resizeMode,
        placeholderSize = placeholderSize,
        zIndexInOverlay = zIndexInOverlay,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        clipInOverlayDuringTransition = transition.clipInOverlayDuringTransition
    )
}

@Composable
fun Modifier.sharedElementTransition(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    boundsTransform: BoundsTransform = DefaultTransform,
    placeholderSize: PlaceholderSize = AnimatedSize,
    zIndexInOverlay: Float = 0f,
    renderInOverlayDuringTransition: Boolean = true,
): Modifier {
    if(LocalInspectionMode.current) {
        return this
    }

    val animatedScope = animatedVisibilityScope ?: LocalNavAnimatedContentScope.current

    return with(sharedTransitionScope) {
        this@sharedElementTransition.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedScope,
            boundsTransform = boundsTransform,
            placeholderSize = placeholderSize,
            zIndexInOverlay = zIndexInOverlay,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition
        )
    }
}

@Composable
fun Modifier.sharedElementTransition(
    transition: SharedTransition,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    boundsTransform: BoundsTransform? = null,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    placeholderSize: PlaceholderSize = AnimatedSize,
    zIndexInOverlay: Float = 0f,
    renderInOverlayDuringTransition: Boolean = true,
): Modifier {
    return sharedElementTransition(
        key = transition.key,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransitionScope = sharedTransitionScope,
        boundsTransform = boundsTransform ?: transition.boundsTransform,
        placeholderSize = placeholderSize,
        zIndexInOverlay = zIndexInOverlay,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition
    )
}

@Composable
fun Modifier.sharedElementTransitionWithCallerManagedVisibility(
    key: String,
    visible: Boolean,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    boundsTransform: BoundsTransform = DefaultTransform,
    placeholderSize: PlaceholderSize = AnimatedSize,
    zIndexInOverlay: Float = 0f,
    renderInOverlayDuringTransition: Boolean = true,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier {
    if(LocalInspectionMode.current) {
        return this
    }

    return with(sharedTransitionScope) {
        this@sharedElementTransitionWithCallerManagedVisibility.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = visible,
            boundsTransform = boundsTransform,
            placeholderSize = placeholderSize,
            zIndexInOverlay = zIndexInOverlay,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }
}

@Composable
fun Modifier.sharedElementTransitionWithCallerManagedVisibility(
    transition: SharedTransition,
    visible: Boolean,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    boundsTransform: BoundsTransform? = null,
    placeholderSize: PlaceholderSize = AnimatedSize,
    zIndexInOverlay: Float = 0f,
    renderInOverlayDuringTransition: Boolean = true,
): Modifier {
    return sharedElementTransitionWithCallerManagedVisibility(
        key = transition.key,
        visible = visible,
        sharedTransitionScope = sharedTransitionScope,
        boundsTransform = boundsTransform ?: transition.boundsTransform,
        placeholderSize = placeholderSize,
        zIndexInOverlay = zIndexInOverlay,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        clipInOverlayDuringTransition = transition.clipInOverlayDuringTransition
    )
}
