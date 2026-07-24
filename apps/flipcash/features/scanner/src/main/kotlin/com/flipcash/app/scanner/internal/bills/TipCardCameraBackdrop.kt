package com.flipcash.app.scanner.internal.bills

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.flipcash.app.bills.components.cards.TipCardBlurRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// How long to keep trying for a usable frame after the card appears (the stream may not be ready
// on the very first attempt), and the gap between attempts.
private const val CAPTURE_TIMEOUT_MS = 1_000L
private const val CAPTURE_RETRY_MS = 50L

/**
 * Builds the frosted-camera backdrop for a tip card. When [enabled], it takes a SINGLE frozen
 * snapshot of the live [previewView] (camera only — the translucent card composited on top is
 * never captured, so there is no feedback loop) and returns a composable that draws that blurred
 * frame aligned to the card's bounds. Returns null until a frame is captured.
 *
 * A single snapshot is deliberate: [PreviewView.getBitmap] is a main-thread GPU→CPU readback, and
 * doing it continuously stutters the live preview even on flagship hardware. A tip card is a static
 * modal, so one frozen frame — captured as the card animates in — is enough.
 *
 * Haze can't blur the camera (it's an AndroidView outside the Compose layer), so we feed it the
 * pixels ourselves via [PreviewView.getBitmap] and blur with [Modifier.blur].
 *
 * @param containerOriginInWindow window-space origin of the full-screen scanner surface, used to
 *   line the camera frame up under the card wherever the card sits.
 */
@Composable
internal fun rememberCameraTipCardBackdrop(
    previewView: PreviewView?,
    enabled: Boolean,
    containerOriginInWindow: Offset,
): (@Composable BoxScope.() -> Unit)? {
    var frame by remember(previewView) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(previewView, enabled) {
        if (previewView == null || !enabled) {
            frame = null
            return@LaunchedEffect
        }
        // Grab one frame, retrying briefly until the stream yields a bitmap, then stop — no ongoing
        // readback, so the live preview is never starved.
        var waited = 0L
        while (isActive && frame == null && waited < CAPTURE_TIMEOUT_MS) {
            runCatching { previewView.bitmap }.getOrNull()?.let { frame = it.asImageBitmap() }
            if (frame == null) {
                delay(CAPTURE_RETRY_MS)
                waited += CAPTURE_RETRY_MS
            }
        }
    }

    val current = frame ?: return null
    return {
        CameraBackdropLayer(
            frame = current,
            containerOriginInWindow = containerOriginInWindow,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun CameraBackdropLayer(
    frame: ImageBitmap,
    containerOriginInWindow: Offset,
    modifier: Modifier = Modifier,
) {
    var selfOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { selfOriginInWindow = it.positionInWindow() }
            .blur(TipCardBlurRadius)
            .drawBehind {
                // The frame fills the scanner surface; translate it so the slice behind the card
                // (this layer) lines up with what the camera is actually showing there.
                drawImage(frame, topLeft = containerOriginInWindow - selfOriginInWindow)
            }
    )
}
