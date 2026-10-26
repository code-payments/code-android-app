package com.flipcash.app.internal.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.asNavBarTab
import com.getcode.navigation.core.CodeNavigator
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * The nav bar's search button growing into Search's field, and back.
 *
 * The button lives in the root nav bar and the field in the Search entry, so they are in different
 * parts of the tree. [fraction] runs from the button (0) to the field (1): the root draws a glass pill
 * between the two ([Overlay]), the button fades out at the start and the field fades in at the end.
 *
 * The keyboard cues it. Opening waits for the keyboard to start rising, then grows the pill over
 * about the keyboard's own slide, so the field lands with the keyboard whatever its startup time;
 * closing shrinks it back as the keyboard drops. A predictive back follows the Search entry's own exit, so the field shrinks into the button
 * under the finger.
 */
@Stable
internal class SearchMorph(private val navigator: CodeNavigator) {
    val fraction = Animatable(0f)

    private var buttonRect by mutableStateOf(Rect.Zero)
    private var fieldRect by mutableStateOf(Rect.Zero)

    /** The field's centre with the keyboard down: the lowest it sits. */
    private var fieldRestCenterY by mutableStateOf(0f)

    /** Set while ✕ or the leading button closes Search, so the entry's exit doesn't drive it too. */
    private var closing = false

    private var scope: CoroutineScope? = null

    /** Outlives the Search entry, so the shrink can finish while the entry fades out. */
    private var rootScope: CoroutineScope? = null

    fun open() {
        navigator.push(AppRoute.Messaging.Search)
    }

    /**
     * Grows the field as the keyboard rises, and follows a predictive back. Call from inside the
     * Search nav entry.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Publish() {
        scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val ime = WindowInsets.ime
        val imeTarget = WindowInsets.imeAnimationTarget
        LaunchedEffect(Unit) {
            // Back from a chat opened from the results: the field is already up.
            if (fraction.value == 1f) return@LaunchedEffect
            closing = false
            // Starts as the keyboard does, and plays at the keyboard's own pace. Following the inset
            // frame by frame would skip the morph wherever the inset arrives in one step.
            withTimeoutOrNull(KeyboardOpenWait) {
                snapshotFlow { imeTarget.getBottom(density) }.first { it > 0 }
            }
            fraction.animateTo(1f, MorphSpec)
        }

        // Predictive back: follow the entry's exit while it heads back to a tab.
        val own = LocalNavAnimatedContentScope.current.transition
        LaunchedEffect(own) {
            var seeking = false
            snapshotFlow {
                val leavingToTab = own.targetState == EnterExitState.PostExit && run {
                    val top = navigator.currentRouteKey
                    top is AppRoute.Messaging.Search || (top as? AppRoute)?.asNavBarTab() != null
                }
                val total = own.totalDurationNanos
                if (!leavingToTab || total <= 0) null else (own.playTimeNanos.toFloat() / total)
            }.collect { played ->
                when {
                    closing -> Unit
                    played != null -> {
                        seeking = true
                        fraction.snapTo((1f - played).coerceIn(0f, 1f))
                    }
                    // A cancelled gesture puts the field back. Only after a seek, so opening is left
                    // to the keyboard.
                    seeking -> {
                        seeking = false
                        if (navigator.currentRouteKey is AppRoute.Messaging.Search) {
                            fraction.animateTo(1f, SettleSpec)
                        }
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                // Gone without closing through the morph (a back that committed): the button comes
                // back. A chat pushed over Search keeps Search in the stack, and the field up.
                // On the root scope: the entry's own is cancelled as the entry leaves, before a
                // launch on it could run, which left the bar's chrome hidden after a predictive back.
                if (!closing && navigator.backStack.none { it is AppRoute.Messaging.Search }) {
                    rootScope?.launch { fraction.snapTo(0f) }
                }
            }
        }
    }

    /**
     * Shrinks the field into the button as the keyboard drops, then pops Search while the rest of the
     * shrink plays, so the screen behind fades in with it. The caller asks the keyboard down; [raised]
     * is its height when it was up.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun close(raised: Int, imeBottom: () -> Int) {
        closing = true
        // Shrinks alongside the keyboard going down, and pops at once, so the screen behind fades in
        // with the shrink rather than after it.
        val shrink = rootScope
        if (shrink != null) {
            shrink.launch { fraction.animateTo(0f, MorphSpec) }
        } else {
            fraction.animateTo(0f, MorphSpec)
        }
        navigator.pop()
    }

    /** The nav bar's search button: measured for the morph, and faded out as it starts. */
    fun Modifier.morphButton(): Modifier = this
        .onGloballyPositioned { buttonRect = it.boundsInRoot() }
        // The pill takes the button's place, in the same glass and with the same glyph, as soon as
        // the morph starts.
        .graphicsLayer { alpha = if (fraction.value > 0f) 0f else 1f }

    /** Search's field: measured for the morph, and faded in as it ends. */
    fun Modifier.morphField(): Modifier = this
        .onGloballyPositioned {
            fieldRect = it.boundsInRoot()
            fieldRestCenterY = maxOf(fieldRestCenterY, fieldRect.center.y)
        }
        .graphicsLayer { alpha = fieldAlpha(fraction.value) }

    /**
     * Search's leading button and ✕: in with the field, so they never show alongside the nav bar's
     * search button.
     */
    fun Modifier.morphBarButton(): Modifier = graphicsLayer { alpha = fieldAlpha(fraction.value) }

    /** The bar's tab pill, which hands the bar over to Search. */
    fun Modifier.morphChrome(): Modifier = graphicsLayer { alpha = 1f - fraction.value }

    /** Whether the bar's tab pill and search button are in the tree (and can take taps). */
    @Composable
    fun chromeShown(): Boolean = remember { derivedStateOf { fraction.value < 1f } }.value

    /**
     * The pill between the button and the field, in the same glass as the button. Place it above the
     * nav content and the bar, filling the root.
     */
    @Composable
    fun Overlay(hazeState: HazeState?) {
        rootScope = rememberCoroutineScope()
        val drawing by remember {
            derivedStateOf { fraction.value > 0f && fraction.value < 1f && buttonRect != Rect.Zero && fieldRect != Rect.Zero }
        }
        if (!drawing) return
        var origin by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val rect = pillRect(fraction.value).translate(-origin)
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                rect.width.roundToInt().coerceAtLeast(0),
                                rect.height.roundToInt().coerceAtLeast(0),
                            )
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                        }
                    }
                    // Hands over to the field as it fades in.
                    .graphicsLayer { alpha = 1f - fieldAlpha(fraction.value) }
                    .floatingGlass(hazeState),
            ) {
                // The button's glyph, kept at the pill's leading edge: centred while the pill is the
                // button's circle, and where the field's own magnifier sits once it is the field.
                BoxWithConstraints(Modifier.fillMaxHeight()) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = ((maxHeight - PillIconSize) / 2).coerceAtLeast(0.dp))
                            .size(PillIconSize)
                            .align(Alignment.CenterStart),
                    )
                }
            }
        }
    }

    /**
     * The pill between the button and the field. It is lifted by however far the keyboard has lifted
     * the field, because the keyboard draws over the app and would otherwise cover it at the bottom.
     */
    private fun pillRect(f: Float): Rect {
        val width = lerpFloat(buttonRect.width, fieldRect.width, f)
        val height = lerpFloat(buttonRect.height, fieldRect.height, f)
        val left = lerpFloat(buttonRect.left, fieldRect.left, f)
        val lift = (fieldRestCenterY - fieldRect.center.y).coerceAtLeast(0f)
        val centerY = lerpFloat(buttonRect.center.y, fieldRestCenterY, f) - lift
        return Rect(left, centerY - height / 2, left + width, centerY + height / 2)
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction


/** The field fades in over the last stretch, once the pill has nearly reached its size. */
private fun fieldAlpha(fraction: Float): Float =
    ((fraction - FieldFadeStart) / (1f - FieldFadeStart)).coerceIn(0f, 1f)

private const val FieldFadeStart = 0.8f

/** The nav bar search button's glyph size, so the pill starts as that button. */
private val PillIconSize = 28.dp

/** Finishes the morph once the keyboard stops driving it. No bounce, so it never overshoots. */
private val SettleSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** The morph itself: a little longer than the keyboard's slide, so the growth reads before the field fades in. */
private val MorphSpec = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)

/**
 * Longest opening waits for the keyboard to start rising. Keyboards take several hundred ms to start
 * (about 630 ms on the Pixel 10 emulator).
 */
private val KeyboardOpenWait = 1200.milliseconds
