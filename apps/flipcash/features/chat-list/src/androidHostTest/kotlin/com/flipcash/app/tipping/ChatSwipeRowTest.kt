package com.flipcash.app.tipping

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.ui.components.rememberSwipeRevealGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The main list's trailing swipe: mute inside, archive outermost, and a full swipe archives.
 * The gesture is the one a device sees: a slow partial drag that leaves both actions revealed,
 * then a fast full swipe from there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ChatSwipeRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var archived = 0
    private var muted = 0

    private fun show() {
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                ChatSwipeRow(
                    isMuted = false,
                    isUnread = false,
                    swipeEnabled = true,
                    onToggleRead = {},
                    onMute = { muted++ },
                    onArchive = { archived++ },
                    stateKey = "chat",
                    revealGroup = rememberSwipeRevealGroup(),
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp).testTag(ROW))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun rowLeft() = composeRule.onNodeWithTag(ROW).getUnclippedBoundsInRoot().left

    @Test
    fun `archive is outermost and a full swipe from the revealed row archives`() {
        show()

        composeRule.onNodeWithTag(ROW).performTouchInput {
            swipeLeft(startX = right - 1f, endX = right - 120.dp.toPx(), durationMillis = 1_500)
        }
        composeRule.waitForIdle()
        assertTrue(rowLeft() < 0.dp, "the short drag should leave the row revealed")
        assertEquals(0, archived)
        assertEquals(0, muted)

        val mute = composeRule.onNodeWithContentDescription(MUTE).getUnclippedBoundsInRoot()
        val archive = composeRule.onNodeWithContentDescription(ARCHIVE).getUnclippedBoundsInRoot()
        assertTrue(archive.left > mute.left, "archive should sit outside mute, at the trailing edge")

        composeRule.onNodeWithTag(ROW).performTouchInput {
            swipeLeft(startX = right - 1f, endX = left, durationMillis = 120)
        }
        composeRule.waitForIdle()

        assertEquals(1, archived)
        assertEquals(0, muted)
    }

    private companion object {
        const val ROW = "row"
        const val MUTE = "Mute notifications"
        const val ARCHIVE = "Archive chat"
    }
}
