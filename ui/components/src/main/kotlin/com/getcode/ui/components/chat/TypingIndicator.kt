package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.ui.components.PriceWithFlagDefaults.Text
import com.getcode.ui.utils.IDPreviewParameterProvider
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    userImages: List<Any> = emptyList(),
) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (userImages.isNotEmpty()) {
            AvatarRow(
                modifier = Modifier.weight(1f, fill = false),
                userImages = userImages,
            )
        }

        TypingDots(modifier = modifier)
    }
}

@Composable
private fun AvatarRow(
    userImages: List<Any>,
    modifier: Modifier = Modifier
) {
    val avatarSize = CodeTheme.dimens.staticGrid.x8
    val overlap = CodeTheme.dimens.staticGrid.x4

    LazyRow(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                clip = false }
            .padding(vertical = 4.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(-overlap, Alignment.Start),
        contentPadding = PaddingValues(end = CodeTheme.dimens.grid.x2)
    ) {
        itemsIndexed(
            items = userImages.takeLast(MaxAvatars),
            key = { _, item -> item.hashCode() }
        ) { index, image ->
            UserAvatar(
                modifier = Modifier
                    .size(avatarSize)
                    .zIndex(userImages.takeLast(MaxAvatars).count() - index.toFloat())
                    .clip(CircleShape)
                    .animateItem(
                        fadeOutSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = 0.5f
                        )
                    ),
                data = image
            ) {
                Image(
                    modifier = Modifier.padding(5.dp),
                    imageVector = Icons.Default.Person,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null
                )
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

private const val MaxAvatars = 10
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

@Composable
@Preview
fun PreviewTypingIndicator() {
    val provider = IDPreviewParameterProvider(MaxAvatars + 2)

    var userCount by remember { mutableIntStateOf(0) }

    // Animation logic
    LaunchedEffect(Unit) {
        while (true) {
            // Ramp up from 0 to max
            for (i in 0..MaxAvatars) {
                userCount = i
                delay(2000L) // 2-second delay
            }
            // Ramp down from max to 0
            for (i in MaxAvatars downTo 0) {
                userCount = i
                delay(2000L) // 2-second delay
            }
        }
    }

    val users by remember(userCount) { derivedStateOf { provider.values.toList().take(userCount) } }
    DesignSystem {
        Box(modifier = Modifier.size(400.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = users.isNotEmpty(),
                transitionSpec = {
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) { it } + scaleIn() + fadeIn() togetherWith fadeOut() + scaleOut() + slideOutVertically { it }
                }
            ) { show ->
                if (show) {
                    TypingIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.grid.x2),
                        userImages = users
                    )
                }
            }
        }
    }
}

@Composable
@Preview
private fun TestAvatarRow() {
    var imageList by remember { mutableStateOf(listOf("user1", "user2", "user3")) }

    DesignSystem {
        Column {
            AvatarRow(
                userImages = imageList,
            )
            Button(onClick = {
                imageList = imageList + "user${imageList.size + 1}"
            }) {
                Text("Add Avatar")
            }

            Button(onClick = {
                imageList = imageList.drop(1)
            }) {
                Text("Remove Avatar")
            }
        }
    }
}

private fun randomColor(): Color {
    return Color(
        red = Random.nextFloat(),
        green = Random.nextFloat(),
        blue = Random.nextFloat(),
        alpha = 1f
    )
}