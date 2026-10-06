package com.flipcash.shared.chat.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ChatPhotoMediaUiTest {

    @get:Rule
    val rule = createComposeRule()

    // region progress overlay

    private fun setOverlay(phase: ChatPhotoPhase) = rule.setContent {
        DesignSystem {
            Box(Modifier.size(200.dp)) { ChatPhotoProgressOverlay(phase, reduceMotion = true) }
        }
    }

    @Test
    fun overlayShowsByteProgressWhileUploading() {
        setOverlay(ChatPhotoPhase.Uploading(0.4f))
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertIsDisplayed()
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f)))
        rule.onNodeWithTag(PHOTO_PROGRESS_FILL_TAG).assertExists()
    }

    @Test
    fun overlayIsIndeterminateWhilePreparing() = assertIndeterminate(ChatPhotoPhase.Preparing)

    @Test
    fun overlayIsIndeterminateBeforeTheFirstBytes() = assertIndeterminate(ChatPhotoPhase.Uploading(0f))

    @Test
    fun overlayIsIndeterminateWhileProcessing() = assertIndeterminate(ChatPhotoPhase.Processing)

    @Test
    fun overlayIsIndeterminateWhileSending() = assertIndeterminate(ChatPhotoPhase.Sending)

    private fun assertIndeterminate(phase: ChatPhotoPhase) {
        setOverlay(phase)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertIsDisplayed()
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        rule.onNodeWithTag(PHOTO_PROGRESS_FILL_TAG).assertExists()
    }

    @Test
    fun overlayHidesWhenSent() {
        setOverlay(ChatPhotoPhase.Sent)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertDoesNotExist()
    }

    @Test
    fun overlayStaysUpForAMinimumOnceDrawnEvenIfSentArrivesSoonAfter() {
        var phase by mutableStateOf<ChatPhotoPhase>(ChatPhotoPhase.Sending)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DesignSystem {
                Box(Modifier.size(200.dp)) { ChatPhotoProgressOverlay(phase, reduceMotion = true) }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        phase = ChatPhotoPhase.Sent
        advanceFramesFor(PHOTO_PROGRESS_MIN_VISIBLE_MILLIS / 2)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertIsDisplayed()

        advanceFramesFor(PHOTO_PROGRESS_MIN_VISIBLE_MILLIS)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertDoesNotExist()
    }

    // One frame at a time, idling between them so each state change recomposes before the clock
    // moves on.
    private fun advanceFramesFor(millis: Long) {
        val end = rule.mainClock.currentTime + millis
        while (rule.mainClock.currentTime < end) {
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
        }
    }

    private fun setAnimatedOverlay(initial: ChatPhotoPhase): (ChatPhotoPhase) -> Unit {
        var phase by mutableStateOf(initial)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DesignSystem {
                Box(Modifier.size(200.dp)) { ChatPhotoProgressOverlay(phase, reduceMotion = false) }
            }
        }
        return { phase = it }
    }

    private fun fillWidth(): Int =
        rule.onNodeWithTag(PHOTO_PROGRESS_FILL_TAG, useUnmergedTree = true).fetchSemanticsNode().size.width

    private fun trackWidth(): Int =
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG, useUnmergedTree = true).fetchSemanticsNode().size.width

    @Test
    fun byteFillKeepsMovingBetweenProgressReports() {
        val setPhase = setAnimatedOverlay(ChatPhotoPhase.Uploading(0.2f))
        advanceFramesFor(1_000)
        setPhase(ChatPhotoPhase.Uploading(0.5f))
        // A report every 300 ms: the fill must still be moving when the next one is due.
        advanceFramesFor(200)
        val at200 = fillWidth()
        advanceFramesFor(100)
        val at300 = fillWidth()
        assertTrue(at300 > at200, "fill stalled between reports: $at200 -> $at300")
    }

    @Test
    fun byteFillRunsToFullBeforeTheBarTurnsIndeterminate() {
        val setPhase = setAnimatedOverlay(ChatPhotoPhase.Uploading(0.6f))
        advanceFramesFor(1_000)
        val beforeDone = fillWidth()
        setPhase(ChatPhotoPhase.Processing)
        advanceFramesFor(50)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG)
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f)))
        assertTrue(fillWidth() >= beforeDone, "fill went backwards: $beforeDone -> ${fillWidth()}")

        advanceFramesFor(1_000)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG)
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
    }

    @Test
    fun byteFillReachesTheFullTrack() {
        val setPhase = setAnimatedOverlay(ChatPhotoPhase.Uploading(0.6f))
        advanceFramesFor(1_000)
        setPhase(ChatPhotoPhase.Processing)
        var widest = 0
        repeat(40) {
            advanceFramesFor(16)
            if (rule.onNodeWithTag(PHOTO_PROGRESS_TAG).fetchSemanticsNode().config
                    .getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo) { null }
                    != ProgressBarRangeInfo.Indeterminate
            ) widest = maxOf(widest, fillWidth())
        }
        assertEquals(trackWidth(), widest)
    }

    @Test
    fun overlayHidesImmediatelyWhenFailed() {
        setOverlay(ChatPhotoPhase.Failed)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertDoesNotExist()
    }

    // endregion

    // region bubble

    @Test
    fun blurhashOnlyBubbleIsNotClickable() {
        var clicks = 0
        rule.setContent {
            DesignSystem {
                ChatPhotoBubble(
                    isFromSelf = false, maxWidth = 240.dp, imageWidth = 100, imageHeight = 100,
                    blurHash = null, blurhashOnly = true, onClick = { clicks++ },
                )
            }
        }
        rule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun loadedBubbleIsClickable() {
        var clicks = 0
        rule.setContent {
            DesignSystem {
                ChatPhotoBubble(
                    isFromSelf = false, maxWidth = 240.dp, imageWidth = 100, imageHeight = 100,
                    blurHash = null, onClick = { clicks++ },
                )
            }
        }
        rule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).performClick()
        // The bubble waits out the double-tap window before reporting a single tap.
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, clicks)
    }

    @Test
    fun unavailableBubbleSaysSoAndIsNotClickable() {
        var clicks = 0
        rule.setContent {
            DesignSystem {
                ChatPhotoBubble(
                    isFromSelf = false, maxWidth = 240.dp, imageWidth = 100, imageHeight = 100,
                    blurHash = null, unavailable = true, onClick = { clicks++ },
                )
            }
        }
        rule.onNodeWithText("This photo can’t be displayed").assertIsDisplayed()
        rule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun captionIsItsOwnBubble() {
        rule.setContent {
            DesignSystem {
                ChatPhotoBubble(
                    isFromSelf = true, maxWidth = 240.dp, imageWidth = 100, imageHeight = 100,
                    blurHash = null, caption = "Lunch",
                )
            }
        }
        rule.onNodeWithTag(PHOTO_CAPTION_TAG).assertIsDisplayed()
    }

    // endregion

    // region chips

    private fun setChips(state: ComposerPhotoChipState, onRetry: (String) -> Unit = {}) = rule.setContent {
        DesignSystem {
            ComposerPhotoChips(
                chips = listOf(ComposerPhotoChip("a", null, state)),
                onRemove = {},
                onRetry = onRetry,
            )
        }
    }

    @Test
    fun uploadedShowsNothing() {
        setChips(ComposerPhotoChipState.Uploaded)
        rule.onNodeWithContentDescription("Retry photo upload").assertDoesNotExist()
        rule.onNodeWithContentDescription("Photo can't be sent").assertDoesNotExist()
        rule.onNodeWithContentDescription("Remove photo").assertExists()
    }

    @Test
    fun retryableOffersRetry() {
        var retried: String? = null
        setChips(ComposerPhotoChipState.Failed(retryable = true)) { retried = it }
        rule.onNodeWithContentDescription("Retry photo upload").performClick()
        assertEquals("a", retried)
        rule.onNodeWithContentDescription("Photo can't be sent").assertDoesNotExist()
    }

    @Test
    fun notRetryableShowsError() {
        setChips(ComposerPhotoChipState.Failed(retryable = false))
        rule.onNodeWithContentDescription("Photo can't be sent").assertExists()
        rule.onNodeWithContentDescription("Retry photo upload").assertDoesNotExist()
    }

    // endregion

    // region viewer

    @Test
    fun shareIsDisabledUntilTheFullImageLoads() {
        rule.setContent {
            DesignSystem {
                ChatMediaViewer(
                    model = null, fullImageLoaded = false, onFullImageLoaded = {},
                    onShare = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag(VIEWER_SHARE_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(VIEWER_CLOSE_TAG).assertIsEnabled()
    }

    @Test
    fun shareIsEnabledOnceLoaded() {
        rule.setContent {
            DesignSystem {
                ChatMediaViewer(
                    model = null, fullImageLoaded = true, onFullImageLoaded = {},
                    onShare = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag(VIEWER_SHARE_TAG).assertIsEnabled()
    }

    @Test
    fun doubleTapTogglesZoom() {
        val zoom = ChatMediaZoomState()
        rule.setContent {
            DesignSystem {
                ChatMediaViewer(
                    model = null, fullImageLoaded = true, onFullImageLoaded = {},
                    onShare = {}, onDismiss = {}, zoomState = zoom, reduceMotion = true,
                )
            }
        }
        assertFalse(zoom.isZoomed)
        rule.onNodeWithTag(VIEWER_IMAGE_TAG).performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertTrue(zoom.isZoomed)
        assertEquals(2.5f, zoom.scale)
        rule.onNodeWithTag(VIEWER_IMAGE_TAG).performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertFalse(zoom.isZoomed)
    }

    // endregion
}
