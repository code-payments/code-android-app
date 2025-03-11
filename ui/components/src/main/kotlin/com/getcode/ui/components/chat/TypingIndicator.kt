package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.ui.utils.IDPreviewParameterProvider
import kotlinx.coroutines.delay

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    userImages: List<Any> = emptyList(),
) {
    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Row
        AvatarRow(
            userImages = userImages,
            maxAvatars = MaxAvatars
        )

        // Typing Dots
        TypingDots()
    }
}

@Composable
private fun AvatarRow(
    userImages: List<Any>,
    maxAvatars: Int,
    modifier: Modifier = Modifier
) {
    val avatarSize = CodeTheme.dimens.staticGrid.x8
    val overlap = CodeTheme.dimens.staticGrid.x4
    LazyRow(
        modifier = modifier
            .graphicsLayer { clip = false }
            .padding(vertical = 4.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(-overlap)
    ) {
        itemsIndexed(
            items = userImages.take(maxAvatars),
            key = { _, item -> item.hashCode() }
        ) { index, image ->
            UserAvatar(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .animateItem(
                        fadeOutSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = 0.9f
                        )
                    )
                    .zIndex(index.toFloat()),
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = CodeTheme.colors.brandDark,
                shape = CodeTheme.shapes.small.copy(
                    topStart = CornerSize(3.dp)
                )
            )
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x3
            ),
        horizontalArrangement = Arrangement.spacedBy(
            space = CodeTheme.dimens.grid.x1,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseColor = CodeTheme.colors.onSurface
        var currentIndex by remember { mutableIntStateOf(0) }

        val animatedAlphas = List(DotCount) { index ->
            val targetAlpha = if (index == currentIndex) 1f else 0.4f
            animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = StepDuration),
                label = "dotAlpha_$index"
            )
        }

        val animatedScales = List(DotCount) { index ->
            val targetScale = if (index == currentIndex) 1f else 0.6f
            animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = StepDuration),
                label = "dotScale_$index"
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(StepDuration.toLong())
                currentIndex = when (currentIndex) {
                    DotCount -> 0
                    DotCount - 1 -> DotCount
                    else -> currentIndex + 1
                }

                // Add extra delay when resetting
                if (currentIndex == DotCount) {
                    delay(StepDuration.toLong())
                }
            }
        }

        animatedAlphas.zip(animatedScales).forEach { (alpha, scale) ->
            Box(
                modifier = Modifier
                    .size(CodeTheme.dimens.grid.x2)
                    .scale(scale.value)
                    .background(
                        color = baseColor.copy(alpha = alpha.value),
                        shape = CircleShape
                    )
            )
        }
    }
}

private const val MaxAvatars = 10
private const val DotCount = 3
private const val StepDuration = 500

@Composable
@Preview
fun PreviewTypingIndicator() {
    val provider = IDPreviewParameterProvider(4)

    var userCount by remember { mutableIntStateOf(0) }

    // Animation logic
    LaunchedEffect(Unit) {
        while (true) {
            // Ramp up from 0 to 4
            for (i in 0..4) {
                userCount = i
                delay(2000L) // 2-second delay
            }
            // Ramp down from 4 to 0
            for (i in 3 downTo 0) {
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
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) { it } + scaleIn() + fadeIn() togetherWith fadeOut() + scaleOut() + slideOutVertically { it }
                }
            ) { show ->
                if (show) {
                    TypingIndicator(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.grid.x2),
                        userImages = users
                    )
                }
            }
        }
    }
}