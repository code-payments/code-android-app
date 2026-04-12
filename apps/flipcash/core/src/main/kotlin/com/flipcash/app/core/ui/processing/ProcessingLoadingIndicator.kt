package com.flipcash.app.core.ui.processing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun ProcessingLoadingIndicator(
    processingState: LoadingSuccessState,
    modifier: Modifier = Modifier,
    duration: Duration = 60.seconds,
) {
    var elapsedMs by remember(processingState) { mutableLongStateOf(0L) }
    val isLoading = processingState.state is LoadingSuccessState.State.Loading

    LaunchedEffect(isLoading) {
        if (isLoading) {
            val startTime = withFrameMillis { it } - elapsedMs
            while (isActive) {
                withFrameMillis { frameTime ->
                    elapsedMs = (frameTime - startTime).coerceAtLeast(0)
                }
            }
        }
    }

    val timedProgress = remember(elapsedMs) {
        val fraction = (elapsedMs / duration.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
        val eased = 1f - (1f - fraction).pow(2)
        0.05f + eased * 0.85f
    }

    var fillComplete by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = when (processingState.state) {
            LoadingSuccessState.State.Loading -> timedProgress
            LoadingSuccessState.State.Success -> 1f
            LoadingSuccessState.State.Error -> timedProgress
            else -> 0.05f
        },
        animationSpec = when (processingState.state) {
            LoadingSuccessState.State.Success -> tween(durationMillis = 400, easing = FastOutSlowInEasing)
            else -> tween(durationMillis = 100)
        },
        finishedListener = { value ->
            if (processingState.state is LoadingSuccessState.State.Success && value == 1f) {
                fillComplete = true
            }
        },
        label = "progress",
    )

    val displayedState = remember(processingState.state, fillComplete) {
        when {
            processingState.state is LoadingSuccessState.State.Success && !fillComplete -> LoadingSuccessState.State.Loading
            else -> processingState.state
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) fillComplete = false
    }

    AnimatedContent(
        targetState = displayedState,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        modifier = modifier,
    ) { targetState ->
        when (targetState) {
            LoadingSuccessState.State.Error -> Image(
                modifier = modifier,
                painter = painterResource(R.drawable.ic_circle_exclamation_large),
                contentDescription = null,
            )
            LoadingSuccessState.State.Idle -> Spacer(modifier)
            LoadingSuccessState.State.Loading -> CodeCircularProgressIndicator(
                modifier = modifier,
                progress = animatedProgress,
                strokeWidth = CodeTheme.dimens.grid.x1,
                color = Color.White,
                backgroundColor = Color.White.copy(0.30f),
                strokeCap = StrokeCap.Butt,
            )
            LoadingSuccessState.State.Success -> Image(
                modifier = modifier,
                painter = painterResource(R.drawable.ic_circle_check_large),
                contentDescription = null,
            )
        }
    }
}
