package com.getcode.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.SnackbarData
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White10
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's toast: a pill one step lighter than the screen, with a hairline border, holding an
 * optional [icon], the message, and the action in its own pill. iOS draws the same design.
 *
 * Show it through [CodeSnackbarHost], which owns its motion, lifetime and swipe to dismiss.
 */
@Composable
fun CodeSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val actionLabel = snackbarData.actionLabel
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(CircleShape)
            // The nearest token to one step above the screen background (Brand).
            .background(CodeTheme.colors.surfaceVariant)
            .border(0.5.dp, White10, CircleShape)
            .padding(
                PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                    end = if (actionLabel != null) 8.dp else 16.dp,
                )
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = snackbarData.message,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain,
            modifier = Modifier
                .weight(1f)
                // The action pill's vertical padding, so a toast is the same height with or without one.
                .padding(vertical = 7.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = CodeTheme.typography.textSmall,
                fontWeight = FontWeight.Bold,
                color = CodeTheme.colors.textMain,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ActionFill)
                    .clickable(role = Role.Button) { snackbarData.performAction() }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
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

// No theme token sits at 12%; White10 is the nearest below it.
private val ActionFill = White.copy(alpha = 0.12f)
