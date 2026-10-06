package com.flipcash.shared.chat.ui.media

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme

/** Where an outgoing photo is in its trip, as the progress capsule on the bubble shows it. */
sealed interface ChatPhotoPhase {
    /** Encoding and picking constraints; nothing to show yet. */
    data object Preparing : ChatPhotoPhase

    /** Bytes going up. [fraction] is 0..1. */
    data class Uploading(val fraction: Float) : ChatPhotoPhase

    /** Uploaded; the server is processing the blob. */
    data object Processing : ChatPhotoPhase

    /** Blob ready; the message itself is going out. */
    data object Sending : ChatPhotoPhase

    /** The message is delivered. The capsule fades away. */
    data object Sent : ChatPhotoPhase

    /** The send failed. The capsule is gone at once; the row's failure affordance takes over. */
    data object Failed : ChatPhotoPhase
}

internal const val PHOTO_PROGRESS_TAG = "chat_photo_progress"
internal const val PHOTO_PROGRESS_FILL_TAG = "chat_photo_progress_fill"

private const val SEGMENT_FRACTION = 0.35f
private const val FRACTION_MILLIS = 200
private const val SENT_FADE_MILLIS = 250
private const val SLIDE_MILLIS = 1100

/**
 * The capsule at the bottom-end of a photo bubble that shows an upload's progress. Fill the
 * bubble's box with it; it positions itself.
 *
 * [ChatPhotoPhase.Preparing] is an empty track, [ChatPhotoPhase.Uploading] a fill that follows the
 * byte fraction, [ChatPhotoPhase.Processing] and [ChatPhotoPhase.Sending] a segment 35% of the track
 * sliding across it. [ChatPhotoPhase.Sent] fades the capsule out; [ChatPhotoPhase.Failed] removes it
 * immediately. With [reduceMotion] nothing animates: the fill jumps, the indeterminate segment sits
 * still and the fade is a cut.
 */
@Composable
fun ChatPhotoProgressOverlay(
    phase: ChatPhotoPhase,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    val sent = phase == ChatPhotoPhase.Sent
    val alpha by animateFloatAsState(
        targetValue = if (sent) 0f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(SENT_FADE_MILLIS),
        label = "photoProgressAlpha",
    )
    if (phase == ChatPhotoPhase.Failed || (sent && alpha == 0f)) return

    val description = stringResource(R.string.description_chatPhotoUploadProgress)
    val grid = CodeTheme.dimens.staticGrid
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .padding(grid.x2)
                .size(grid.x13, grid.x1)
                .alpha(alpha)
                .shadow(CodeTheme.dimens.thickBorder, CircleShape)
                .clip(CircleShape)
                .background(CodeTheme.colors.background.copy(alpha = 0.35f))
                .testTag(PHOTO_PROGRESS_TAG)
                .semantics {
                    contentDescription = description
                    if (phase is ChatPhotoPhase.Uploading) {
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(phase.fraction.coerceIn(0f, 1f), 0f..1f)
                    }
                },
        ) {
            when (phase) {
                is ChatPhotoPhase.Uploading -> {
                    val fraction by animateFloatAsState(
                        targetValue = phase.fraction.coerceIn(0f, 1f),
                        animationSpec = if (reduceMotion) snap() else tween(FRACTION_MILLIS),
                        label = "photoProgressFraction",
                    )
                    Fill(widthFraction = { fraction }, offsetFraction = { 0f })
                }

                ChatPhotoPhase.Processing, ChatPhotoPhase.Sending ->
                    IndeterminateSegment(reduceMotion)

                // Hold a full bar while the capsule fades.
                ChatPhotoPhase.Sent -> Fill(widthFraction = { 1f }, offsetFraction = { 0f })

                ChatPhotoPhase.Preparing, ChatPhotoPhase.Failed -> Unit
            }
        }
    }
}

@Composable
private fun IndeterminateSegment(reduceMotion: Boolean) {
    if (reduceMotion) {
        Fill(widthFraction = { SEGMENT_FRACTION }, offsetFraction = { (1f - SEGMENT_FRACTION) / 2f })
        return
    }
    val transition = rememberInfiniteTransition(label = "photoProgressSlide")
    // The segment enters past the leading edge and leaves past the trailing one.
    val offset by transition.animateFloat(
        initialValue = -SEGMENT_FRACTION,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SLIDE_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "photoProgressOffset",
    )
    Fill(widthFraction = { SEGMENT_FRACTION }, offsetFraction = { offset })
}

@Composable
private fun Fill(widthFraction: () -> Float, offsetFraction: () -> Float) {
    Box(
        modifier = Modifier
            .testTag(PHOTO_PROGRESS_FILL_TAG)
            .layout { measurable, constraints ->
                // Read in layout so a moving segment re-lays-out without recomposing.
                val width = (constraints.maxWidth * widthFraction()).toInt().coerceAtLeast(0)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width, maxWidth = width),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((constraints.maxWidth * offsetFraction()).toInt(), 0)
                }
            }
            .fillMaxSize()
            .background(ChatMediaBlue),
    )
}

// UIKit's systemBlue in dark mode, which iOS uses for the upload fill and the camera's more dot.
internal val ChatMediaBlue = Color(0xFF0A84FF)

@Preview(widthDp = 200, heightDp = 100)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Overlay_Uploading() {
    Box(Modifier.size(CodeTheme.dimens.staticGrid.x20 * 2, CodeTheme.dimens.staticGrid.x20).background(Color.DarkGray)) {
        ChatPhotoProgressOverlay(ChatPhotoPhase.Uploading(0.6f))
    }
}

@Preview(widthDp = 200, heightDp = 100)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Overlay_Processing() {
    Box(Modifier.size(CodeTheme.dimens.staticGrid.x20 * 2, CodeTheme.dimens.staticGrid.x20).background(Color.DarkGray)) {
        ChatPhotoProgressOverlay(ChatPhotoPhase.Processing)
    }
}
