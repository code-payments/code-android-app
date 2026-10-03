package com.getcode.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.SnackbarData
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.glass.FloatingChrome
import com.getcode.ui.components.toast.FloatingToast
import com.getcode.ui.components.toast.LocalFloatingToastHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [SnackbarData] drawn as the app's [FloatingToast], inset like the navigation bar. A screen-level
 * host has no bar haze to sample, so the glass takes the bar's no-haze fill.
 *
 * Show it through [CodeSnackbarHost], which owns its motion, lifetime and swipe to dismiss. Prefer
 * [LocalFloatingToastHost] on a screen under the navigation bar, so the toast rises out of the bar.
 */
@Composable
fun CodeSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    FloatingToast(
        message = snackbarData.message,
        icon = icon,
        actionLabel = snackbarData.actionLabel,
        onAction = snackbarData::performAction,
        modifier = modifier
            .padding(horizontal = FloatingChrome.horizontalInset)
            .padding(vertical = 12.dp),
    )
}

/**
 * Shows [hostState]'s current snackbar with [snackbar], one at a time. It slides up and fades in,
 * leaves the same way, and a swipe down dismisses it. A [SnackbarDuration.Short] snackbar stays 4
 * seconds, or longer when the accessibility settings ask for more time.
 *
 * Material's `SnackbarHost` is not used because its fade and scale cannot be turned off and
 * would play over the slide.
 */
@Composable
fun CodeSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { CodeSnackbar(it) },
) {
    val current = hostState.currentSnackbarData
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        val millis = when (current.duration) {
            SnackbarDuration.Short -> 4_000L
            SnackbarDuration.Long -> 10_000L
            SnackbarDuration.Indefinite -> return@LaunchedEffect
        }
        val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = millis,
            containsIcons = true,
            containsText = true,
            containsControls = current.actionLabel != null,
        ) ?: millis
        delay(timeout)
        current.dismiss()
    }

    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith
                (slideOutVertically { it } + fadeOut()) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomCenter,
        label = "snackbar",
    ) { data ->
        if (data != null) {
            SwipeToDismiss(data) { snackbar(data) }
        } else {
            // An empty slot, so the outgoing snackbar can animate away.
            Box(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwipeToDismiss(data: SnackbarData, content: @Composable () -> Unit) {
    val offset = remember(data) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        modifier = Modifier
            .graphicsLayer { translationY = offset.value }
            .semantics {
                dismiss {
                    data.dismiss()
                    true
                }
            }
            .pointerInput(data) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offset.value > threshold) {
                            data.dismiss()
                        } else {
                            scope.launch { offset.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + dragAmount).coerceAtLeast(0f)) }
                }
            },
    ) {
        content()
    }
}
