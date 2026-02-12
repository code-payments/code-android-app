package com.flipcash.app.tokens.internal.components.processing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.tokens.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun ProcessingLoadingIndicator(
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

    // Tracks whether the fill-to-100% animation has finished.
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

    // Show the ring while loading or filling to 100%.
    // Once filled, show the result icon via Crossfade.
    val displayedState = remember(processingState.state, fillComplete) {
        when {
            processingState.state is LoadingSuccessState.State.Success && !fillComplete -> LoadingSuccessState.State.Loading
            else -> processingState.state
        }
    }

    // Reset fillComplete when a new loading cycle starts.
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

@Preview
@Composable
private fun ProcessingLoadingIndicatorPreview() {
    FlipcashPreview(showBackground = true) {
        var state by remember { mutableStateOf(LoadingSuccessState()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ProcessingLoadingIndicator(
                processingState = state,
                modifier = Modifier.size(48.dp),
                duration = 20.seconds,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CodeButton(onClick = { state = LoadingSuccessState(loading = true) }) {
                    Text("Start")
                }
                CodeButton(onClick = { state = LoadingSuccessState(success = true) }) {
                    Text("Success")
                }
                CodeButton(onClick = { state = LoadingSuccessState(error = true) }) {
                    Text("Error")
                }
                CodeButton(onClick = { state = LoadingSuccessState() }) {
                    Text("Reset")
                }
            }
        }
    }
}