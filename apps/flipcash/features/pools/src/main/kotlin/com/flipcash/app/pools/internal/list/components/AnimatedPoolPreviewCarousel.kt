package com.flipcash.app.pools.internal.list.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.pools.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.debugBounds
import com.getcode.ui.utils.ConstraintMode
import com.getcode.ui.utils.constrain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun AnimatedPoolPreviewCarousel(
    titles: List<String>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val constraints = this@BoxWithConstraints
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(0.05f),
                            1f to Color.Transparent,
                        ),
                    ),
                    shape = RoundedCornerShape(50.6.dp)
                )
                .background(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.01f to Color(0xFF053209),
                            0.4f to Color.Transparent
                        ),
                        start = Offset.Zero,
                        end = Offset(
                            x = with(density) { constraints.maxWidth.toPx() },
                            y = with(density) { constraints.maxHeight.toPx() },
                        )
                    ),
                    shape = RoundedCornerShape(50.6.dp)
                )
                .padding(
                    horizontal = CodeTheme.dimens.inset,
                )
                .padding(top = CodeTheme.dimens.inset)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dynamic Island
                Box(
                    modifier = Modifier
                        .background(
                            color = CodeTheme.colors.background,
                            shape = CircleShape
                        )
                        .width(CodeTheme.dimens.grid.x20)
                        .height(CodeTheme.dimens.grid.x6)
                )

                // Text Carousel
                TextCarousel(
                    modifier = Modifier
                        .padding(
                            top = constraints.maxHeight * 0.17f,
                            bottom = constraints.maxHeight * 0.089f,
                        )
                        .fillMaxWidth(0.7f),
                    titles = titles,
                    interval = 3.seconds,
                    maxWidth = constraints.maxWidth,
                    maxHeight = constraints.maxHeight * 0.23f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    MockQuestionCell(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.label_yes)
                    )
                    MockQuestionCell(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.label_no)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextCarousel(
    titles: List<String>,
    interval: Duration,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
) {
    val density = LocalDensity.current
    var index by remember { mutableIntStateOf(initialIndex) }

    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    // Timer to update index
    DisposableEffect(lifecycleScope) {
        val timer = Timer()
        flow {
            emit(Unit)
        }.onEach {
            index = (index + 1) % titles.size // Cycle through indices
        }.launchIn(lifecycleScope)

        // Simulate timer with periodic updates
        timer.scheduleAtFixedRate(0L, interval.inWholeMilliseconds) {
            index = (index + 1) % titles.size
        }

        onDispose {
            timer.cancel()
        }
    }

    val screenTitle = CodeTheme.typography.screenTitle
    val lineHeight = screenTitle.lineHeight
    val twoLineHeightPx = with(density) { (lineHeight.value * 3).sp.toPx() }
    val twoLineHeightDp = with(density) { twoLineHeightPx.toDp() }

    AnimatedContent(
        modifier = modifier.height(twoLineHeightDp),
        targetState = index,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn() + slideInVertically(),
                initialContentExit = fadeOut() + slideOutVertically(),
            ).using(
                SizeTransform(clip = false)
            )
        },
        label = "TextTransition"
    ) { targetIndex ->
        titles.getOrNull(targetIndex)?.let { item ->
            Text(
                text = item,
                textAlign = TextAlign.Center,
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MockQuestionCell(
    modifier: Modifier = Modifier,
    text: String,
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), shape = CodeTheme.shapes.medium)
            .aspectRatio(1.09f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CodeTheme.typography.displaySmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

private val sampleTitles = listOf(
    "Will Joe and Sally have a baby girl?",
    "Will anyone put a ball in the water during today’s golf round?",
    "Will Jack bring a date to the wedding?",
    "Will Caleb dunk this basketball?",
    "Will Jill text her ex before dawn?",
)

@Preview
@Composable
private fun PreviewCarousel() {
    FlipcashDesignSystem {
        Box(
            modifier = Modifier
                .background(CodeTheme.colors.background)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedPoolPreviewCarousel(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .aspectRatio(0.9315f)
                    .padding(16.dp),
                titles = sampleTitles
            )
        }
    }
}