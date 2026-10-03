package com.getcode.ui.components.toast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One toast on screen in a [FloatingToastHost]. */
@Immutable
class FloatingToastData internal constructor(
    val message: String,
    val icon: ImageVector?,
    val actionLabel: String?,
    val hugContent: Boolean,
    val passThrough: Boolean,
    val duration: SnackbarDuration,
    // Toasts that share a slot swap in place rather than replaying the entrance.
    internal val slot: Long,
    private val result: CompletableDeferred<SnackbarResult>,
) {
    fun performAction() {
        result.complete(SnackbarResult.ActionPerformed)
    }

    fun dismiss() {
        result.complete(SnackbarResult.Dismissed)
    }

    internal suspend fun await(): SnackbarResult = result.await()
}

/**
 * The toasts shown by a [FloatingToastHost], one at a time. A new toast replaces the one on screen
 * rather than queueing behind it, and the replaced toast's [show] returns
 * [SnackbarResult.Dismissed].
 */
@Stable
class FloatingToastHostState {
    var current: FloatingToastData? by mutableStateOf(null)
        private set

    /**
     * Whether something that must not be drawn over (a sheet, a modal, a prompt) is on screen above
     * the host. Covering dismisses the toast on screen, and a toast shown while covered is dismissed
     * without appearing: a toast left on top would take that layer's taps, and one hidden beneath it
     * would time out unseen with its action unreachable.
     */
    var isCovered: Boolean = false
        set(value) {
            field = value
            if (value) current?.dismiss()
        }

    private var nextSlot = 0L

    /**
     * Shows [message] until it times out, is dismissed or replaced, or its action is performed, and
     * returns which. Cancelling the caller takes the toast down.
     *
     * @param hugContent wrap the message rather than fill the bar's width.
     * @param passThrough take no pointer input at all, so taps reach what is beneath; there is no
     * swipe to dismiss and the toast should have no action.
     * @param inPlace when a toast is already showing, swap its content without replaying the
     * entrance, for a message that updates as the user acts (a countdown).
     */
    suspend fun show(
        message: String,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        hugContent: Boolean = false,
        passThrough: Boolean = false,
        inPlace: Boolean = false,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ): SnackbarResult {
        if (isCovered) return SnackbarResult.Dismissed
        val previous = current
        val data = FloatingToastData(
            message = message,
            icon = icon,
            actionLabel = actionLabel,
            hugContent = hugContent,
            passThrough = passThrough,
            duration = duration,
            slot = if (inPlace && previous != null) previous.slot else nextSlot++,
            result = CompletableDeferred(),
        )
        // Set before the previous toast resolves, so the host never sees an empty frame between them.
        current = data
        previous?.dismiss()
        try {
            return data.await()
        } finally {
            if (current === data) current = null
        }
    }
}

/**
 * The app's toast host, provided at the root so a toast floats with the navigation bar. Null where
 * no root host is installed (previews, tests).
 */
val LocalFloatingToastHost = staticCompositionLocalOf<FloatingToastHostState?> { null }

/**
 * Shows [hostState]'s current toast as a [FloatingToast], inset like the navigation bar so it is
 * never wider than it. Place it at the bottom of the screen, above the bar, and compose it *before*
 * the bar so the bar draws over it.
 *
 * A toast slides up by its own height while fading in, and leaves the same way in reverse. A
 * [SnackbarDuration.Short] toast stays 4 seconds, or longer when the accessibility settings ask for
 * more time, and a swipe down dismisses it unless it passes through.
 *
 * [hazeState] must not belong to a `hazeSource` that contains this host.
 */
@Composable
fun FloatingToastHost(
    hostState: FloatingToastHostState,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val current = hostState.current
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
            containsIcons = current.icon != null,
            containsText = true,
            containsControls = current.actionLabel != null,
        ) ?: millis
        delay(timeout)
        current.dismiss()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FloatingChrome.horizontalInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = current,
            contentKey = { it?.slot },
            transitionSpec = {
                // The motion lives on the progress below, so entering and leaving share one curve.
                EnterTransition.None togetherWith ExitTransition.None using
                    // Content draws unclipped, so the container has nothing to animate.
                    SizeTransform(clip = false) { _, _ -> snap() }
            },
            contentAlignment = Alignment.BottomCenter,
            label = "floatingToast",
        ) { data ->
            if (data == null) {
                // An empty slot, so the outgoing toast can animate away.
                Box(Modifier.fillMaxWidth())
                return@AnimatedContent
            }
            // Driven by the enter/exit transition itself, so AnimatedContent keeps the outgoing toast
            // until it has finished leaving.
            val progress = transition.animateFloat(
                transitionSpec = {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                },
                label = "floatingToastProgress",
            ) { state -> if (state == EnterExitState.Visible) 1f else 0f }

            val motion = Modifier.graphicsLayer {
                val p = progress.value
                alpha = p
                translationY = (1f - p) * size.height
            }
            SwipeToDismiss(data, motion) {
                FloatingToast(
                    message = data.message,
                    icon = data.icon,
                    actionLabel = data.actionLabel,
                    onAction = data::performAction,
                    hugContent = data.hugContent,
                    hazeState = hazeState,
                )
            }
        }
    }
}

@Composable
private fun SwipeToDismiss(
    data: FloatingToastData,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (data.passThrough) {
        // No pointer or dismiss handlers at all: taps go to whatever is beneath.
        Box(modifier) { content() }
        return
    }
    val offset = remember(data.slot) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        modifier = modifier
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

// How far below its resting place a rising toast starts: enough to tuck its squashed bottom edge
// behind the bar it grows out of.
