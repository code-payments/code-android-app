package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Dots in a bubble, led by an overlapping stack of the last [MaxAvatars] of [typists]. An empty
 * [typists] draws the dots alone.
 *
 * The stack reads left to right: the oldest typist sits at the back on the left and the newest on
 * top at the right. [avatar] draws one typist inside a circle the stack sizes, clips and animates;
 * it is measured to fill that circle. [key] identifies a typist across updates, so an avatar slides
 * to its new place as others join and leave rather than animating out and back in.
 */
@Composable
fun <T> TypingIndicator(
    typists: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    avatar: @Composable (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(
            typists = typists,
            key = key,
            avatar = avatar,
        )

        TypingDots(modifier = modifier)
    }
}

/** One avatar in the stack, kept after its typist leaves until it has animated out. */
private class StackEntry<T>(
    val key: Any,
    item: T,
    slot: Float,
    presence: Float,
) {
    var item by mutableStateOf(item)
    var leaving by mutableStateOf(false)
    val slot = Animatable(slot)
    val presence = Animatable(presence)
}

@Composable
private fun <T> AvatarStack(
    typists: List<T>,
    key: (T) -> Any,
    avatar: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarSize = AvatarSize
    val step = avatarSize - AvatarOverlap
    val gap = CodeTheme.dimens.grid.x1
    val density = LocalDensity.current
    val stepPx = with(density) { step.toPx() }
    val ringPx = with(density) { AvatarRing.toPx() }

    val shown = typists.takeLast(MaxAvatars)
    // Avatars on the first frame are part of the indicator's own entrance, so they start in place
    // rather than growing out of it a second time.
    val entries = remember {
        mutableStateListOf<StackEntry<T>>().apply {
            shown.forEachIndexed { index, item -> add(StackEntry(key(item), item, index.toFloat(), 1f)) }
        }
    }
    val shownKeys = shown.map(key)
    val count by animateFloatAsState(
        targetValue = shown.size.toFloat(),
        animationSpec = AvatarMotion,
        label = "avatarCount",
    )

    LaunchedEffect(shownKeys) {
        shown.forEachIndexed { index, item ->
            val itemKey = key(item)
            val entry = entries.firstOrNull { it.key == itemKey && !it.leaving }
                ?: StackEntry(itemKey, item, index.toFloat(), 0f).also { entries.add(it) }
            entry.item = item
            launch { entry.slot.animateTo(index.toFloat(), AvatarMotion) }
            launch { entry.presence.animateTo(1f, AvatarMotion) }
        }
        entries.filter { !it.leaving && it.key !in shownKeys }.forEach { entry ->
            entry.leaving = true
            launch {
                entry.presence.animateTo(0f, AvatarMotion)
                entries.remove(entry)
            }
        }
    }

    // Stacking order: later slots on top, and a leaving avatar beneath everything, so the ones
    // that close the gap slide over it.
    fun StackEntry<T>.z(): Float = if (leaving) -1f else slot.value

    // Avatars grow from and shrink to their left edge. From AvatarEnterScale up, that keeps an
    // arriving avatar's right edge inside the stack's animated width, clear of the bubble.
    fun StackEntry<T>.scale(): Float = lerp(AvatarEnterScale, 1f, presence.value)

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                // n avatars are n steps plus one overlap wide; the gap to the bubble grows in with
                // the first avatar, so the dots never jump.
                val c = count
                val width = (c * step.toPx() + (AvatarOverlap + gap).toPx() * c.coerceAtMost(1f))
                    .roundToInt()
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                layout(width, placeable.height) { placeable.place(0, 0) }
            },
    ) {
        entries.forEach { entry ->
            key(entry.key) {
                Box(
                    modifier = Modifier
                        .zIndex(entry.z())
                        .offset { IntOffset((entry.slot.value * stepPx).roundToInt(), 0) }
                        .size(avatarSize)
                        .graphicsLayer {
                            val scale = entry.scale()
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha = entry.presence.value
                            // Its own buffer, so the rings cut below clear only this avatar.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            // Cut a ring where each avatar above this one sits, so overlapping
                            // faces stay apart over any background.
                            // Worked in this layer's unscaled space, which is scaled about its left edge.
                            val myScale = entry.scale()
                            val myZ = entry.z()
                            val radius = size.minDimension / 2
                            entries.forEach { other ->
                                if (other === entry || other.z() <= myZ) return@forEach
                                val otherScale = other.scale()
                                val otherCenterX =
                                    (other.slot.value - entry.slot.value) * stepPx + radius * otherScale
                                drawCircle(
                                    color = Color.Black.copy(alpha = other.presence.value),
                                    radius = (radius * otherScale + ringPx) / myScale,
                                    center = Offset(otherCenterX / myScale, center.y),
                                    blendMode = BlendMode.DstOut,
                                )
                            }
                        }
                        .clip(CircleShape),
                    propagateMinConstraints = true,
                ) {
                    avatar(entry.item)
                }
            }
        }
    }
}

@Composable
private fun TypingDots(
    modifier: Modifier = Modifier,
) {
    val shape = CodeTheme.shapes.medium
    Row(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clip(shape)
            .then(modifier)
            .border(
                color = CodeTheme.colors.chat.typingIndicator.border,
                width = CodeTheme.dimens.border,
                shape = CodeTheme.shapes.medium,
            )
            .background(
                color = CodeTheme.colors.chat.typingIndicator.background,
                shape = CodeTheme.shapes.medium,
            )
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        horizontalArrangement = Arrangement.spacedBy(
            space = CodeTheme.dimens.grid.x1,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseColor = CodeTheme.colors.chat.typingIndicator.dots

        val easeIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
        val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

        val infiniteTransition = rememberInfiniteTransition(label = "typingWave")

        val dotOpacities = List(DotCount) { index ->
            val delayMs = WaveLeadIn + index * WaveStagger
            val riseEnd = delayMs + DotRise
            val fallEnd = riseEnd + DotFall

            infiniteTransition.animateFloat(
                initialValue = WaveBaseOpacity,
                targetValue = WaveBaseOpacity,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = WavePeriod
                        WaveBaseOpacity at 0 using easeOut
                        WaveBaseOpacity at delayMs using easeIn
                        WavePeakOpacity at riseEnd using easeOut
                        WaveBaseOpacity at fallEnd using easeOut
                        WaveBaseOpacity at WavePeriod using easeOut
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dotOpacity_$index"
            )
        }

        dotOpacities.forEach { opacity ->
            Box(
                modifier = Modifier
                    .size(DotSize)
                    .drawBehind {
                        drawCircle(color = baseColor.copy(alpha = opacity.value))
                    }
            )
        }
    }
}

private const val MaxAvatars = 3
private const val AvatarEnterScale = 0.4f
// Matches the sender avatar beside a group's messages.
private val AvatarSize = 24.dp
private val AvatarOverlap = 8.dp
private val AvatarRing = 2.dp
private val AvatarMotion = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private const val DotCount = 3
private val DotSize = 7.dp

// Wave animation parameters
private const val WavePeriod = 1300       // ms — full cycle length
private const val WaveLeadIn = 200        // ms — silence before first dot rises
private const val WaveStagger = 160       // ms — delay between successive dots
private const val DotRise = 200           // ms — time to rise to peak opacity
private const val DotFall = 300           // ms — time to fall back to base opacity
private const val WaveBaseOpacity = 0.3f
private const val WavePeakOpacity = 0.85f

/**
 * Scripted joins and leaves: the first three typists arrive one by one, a fourth pushes the oldest
 * off the back, one in the middle stops, then everyone stops.
 */
@Composable
@Preview
fun PreviewTypingIndicator() {
    val script = remember {
        listOf(
            listOf("A"),
            listOf("A", "B"),
            listOf("A", "B", "C"),
            listOf("A", "B", "C", "D"),
            listOf("B", "D"),
            listOf("D"),
            emptyList(),
        )
    }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600L)
            step = (step + 1) % script.size
        }
    }
    val users = script[step]

    DesignSystem {
        Box(
            modifier = Modifier
                .size(width = 360.dp, height = 160.dp)
                .background(CodeTheme.colors.background),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                targetState = users.isNotEmpty(),
                transitionSpec = {
                    (scaleIn(initialScale = 0.95f, transformOrigin = TransformOrigin(0f, 0.5f)) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.95f, transformOrigin = TransformOrigin(0f, 0.5f)) + fadeOut()) using
                            SizeTransform(clip = false)
                },
            ) { show ->
                if (show) {
                    TypingIndicator(
                        typists = users,
                        key = { it },
                        avatar = { PreviewAvatar(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewAvatar(name: String) {
    val colors = listOf(Color(0xFFE0A458), Color(0xFF5B8DEF), Color(0xFF3FB68B), Color(0xFFD9637C))
    Box(
        modifier = Modifier.background(colors[(name.first() - 'A') % colors.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name, color = Color.White, style = CodeTheme.typography.caption)
    }
}
