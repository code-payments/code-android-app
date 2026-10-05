package com.flipcash.shared.chat.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
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
    fun overlayShowsWhileUploading() {
        setOverlay(ChatPhotoPhase.Uploading(0.4f))
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(PHOTO_PROGRESS_FILL_TAG).assertExists()
    }

    @Test
    fun overlayShowsEmptyTrackWhilePreparing() {
        setOverlay(ChatPhotoPhase.Preparing)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(PHOTO_PROGRESS_FILL_TAG).assertDoesNotExist()
    }

    @Test
    fun overlayHidesWhenSent() {
        setOverlay(ChatPhotoPhase.Sent)
        rule.onNodeWithTag(PHOTO_PROGRESS_TAG).assertDoesNotExist()
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
