package com.flipcash.shared.chat.ui.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

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
// Longer than the gap between progress reports, so the fill never stops between them.
private const val FRACTION_MILLIS = 450
private const val FINISH_MILLIS = 300
private const val FULL_HOLD_MILLIS = 150L
private const val SENT_FADE_MILLIS = 250
private const val SLIDE_MILLIS = 1100
internal const val PHOTO_PROGRESS_MIN_VISIBLE_MILLIS = 600L

/**
 * The capsule at the bottom-end of a photo bubble that shows an upload's progress. Fill the
 * bubble's box with it; it positions itself.
 *
 * [ChatPhotoPhase.Uploading] with bytes sent is a fill that follows the byte fraction at a steady
 * pace, trailing it slightly so it keeps moving between reports. When the bytes are done the fill
 * runs to the end of the track before the next phase takes over. Every phase
 * without a byte count yet ([ChatPhotoPhase.Preparing], an upload before its first bytes,
 * [ChatPhotoPhase.Processing], [ChatPhotoPhase.Sending]) is a segment 35% of the track sliding
 * across it. [ChatPhotoPhase.Sent] fades the capsule out; [ChatPhotoPhase.Failed] removes it
 * immediately. With [reduceMotion] nothing animates: the fill jumps, the indeterminate segment sits
 * still and the fade is a cut.
 */
@Composable
fun ChatPhotoProgressOverlay(
    phase: ChatPhotoPhase,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    // The row can be drawn well after the send began (the list scrolls it in), so a quick send
    // would fade the bar out before anyone sees it. Once drawn, it stays up for a minimum. A row
    // first drawn already sent never shows it.
    val drawnSent = remember { phase == ChatPhotoPhase.Sent }
    var shownLongEnough by remember { mutableStateOf(drawnSent) }
    LaunchedEffect(Unit) {
        delay(PHOTO_PROGRESS_MIN_VISIBLE_MILLIS)
        shownLongEnough = true
    }
    val sent = phase == ChatPhotoPhase.Sent && shownLongEnough
    val alpha by animateFloatAsState(
        targetValue = if (sent) 0f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(SENT_FADE_MILLIS),
        label = "photoProgressAlpha",
    )
    if (phase == ChatPhotoPhase.Failed || (sent && alpha == 0f)) return

    val bytes = (phase as? ChatPhotoPhase.Uploading)?.fraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }
    val fill = remember { Animatable(0f) }
    // True from the first bytes until the fill has run to the end of the track.
    var filling by remember { mutableStateOf(false) }
    LaunchedEffect(bytes, phase) {
        when {
            bytes != null -> {
                filling = true
                // A restarted upload starts over rather than shrinking the bar.
                if (reduceMotion || bytes < fill.value) {
                    fill.snapTo(bytes)
                } else {
                    fill.animateTo(bytes, tween(FRACTION_MILLIS, easing = LinearEasing))
                }
            }

            filling -> {
                if (reduceMotion) {
                    fill.snapTo(1f)
                } else {
                    fill.animateTo(1f, tween(FINISH_MILLIS, easing = LinearEasing))
                }
                // Let the full bar be seen before the next phase takes it over.
                delay(FULL_HOLD_MILLIS)
                filling = false
            }
        }
    }
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
                    progressBarRangeInfo = when {
                        bytes != null -> ProgressBarRangeInfo(bytes, 0f..1f)
                        filling -> ProgressBarRangeInfo(1f, 0f..1f)
                        phase == ChatPhotoPhase.Sent -> ProgressBarRangeInfo(1f, 0f..1f)
                        else -> ProgressBarRangeInfo.Indeterminate
                    }
                },
        ) {
            when {
                bytes != null || filling -> Fill(widthFraction = { fill.value }, offsetFraction = { 0f })

                // Hold a full bar while the capsule fades.
                phase == ChatPhotoPhase.Sent -> Fill(widthFraction = { 1f }, offsetFraction = { 0f })

                else -> IndeterminateSegment(reduceMotion)
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
            // After the layout, so the node's bounds are the drawn fill rather than the track.
            .testTag(PHOTO_PROGRESS_FILL_TAG)
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
