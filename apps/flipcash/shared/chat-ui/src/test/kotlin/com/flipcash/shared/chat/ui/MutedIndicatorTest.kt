package com.flipcash.shared.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ViewerState
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * The indicator's two questions: whether it draws, and whether a screen reader hears it.
 *
 * The second is the reason this is a Compose test rather than a unit test of [rememberIsMuted].
 * Both surfaces that draw the bell sit inside a merging semantics node — a clickable row, a
 * tappable title — and what such a node does with a child's description is the platform's choice,
 * not ours. Compose concatenates rather than discarding, which is why the bell carries its own
 * description instead of the containers appending to theirs; if that ever stopped being true the
 * indicator would go silent with nothing else failing.
 */
@RunWith(RobolectricTestRunner::class)
class MutedIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mutedLabel = "Muted"

    private fun setIndicator(viewerState: ViewerState?) {
        composeTestRule.setContent {
            DesignSystem {
                MutedIndicator(viewerState = viewerState)
            }
        }
    }

    // ---------------------------------------------------------------
    // Whether it draws
    // ---------------------------------------------------------------

    @Test
    fun `drawn while muted forever`() {
        setIndicator(ViewerState(mute = MuteState.Forever))
        composeTestRule.onNodeWithContentDescription(mutedLabel).assertIsDisplayed()
    }

    @Test
    fun `drawn while a timed mute is still running`() {
        setIndicator(ViewerState(mute = MuteState.Until(Clock.System.now() + 1.hours)))
        composeTestRule.onNodeWithContentDescription(mutedLabel).assertIsDisplayed()
    }

    @Test
    fun `not drawn once a timed mute has lapsed`() {
        // The state the server last sent, read after its deadline. Nothing arrives to clear it, so
        // this is what a chat that has gone audible on its own actually looks like in the store.
        setIndicator(ViewerState(mute = MuteState.Until(Clock.System.now() - 1.hours)))
        composeTestRule.onNodeWithContentDescription(mutedLabel).assertDoesNotExist()
    }

    @Test
    fun `not drawn when the chat is not muted`() {
        setIndicator(ViewerState(mute = null))
        composeTestRule.onNodeWithContentDescription(mutedLabel).assertDoesNotExist()
    }

    @Test
    fun `not drawn when there is no viewer state`() {
        setIndicator(null)
        composeTestRule.onNodeWithContentDescription(mutedLabel).assertDoesNotExist()
    }

    // ---------------------------------------------------------------
    // Whether a screen reader hears it
    // ---------------------------------------------------------------

    @Test
    fun `description survives a merging container`() {
        composeTestRule.setContent {
            DesignSystem {
                Row(modifier = Modifier.clickable {}) {
                    Text("Alice")
                    MutedIndicator(viewerState = ViewerState(mute = MuteState.Forever))
                }
            }
        }

        // The clickable is the merged node; finding the description on it is what says the bell
        // reached the label the row is read out as, rather than only existing under it.
        composeTestRule
            .onNode(hasClickAction() and hasContentDescription(mutedLabel))
            .assertExists()
    }

    @Test
    fun `merging container says nothing about muting when the chat is audible`() {
        composeTestRule.setContent {
            DesignSystem {
                Row(modifier = Modifier.clickable {}) {
                    Text("Alice")
                    MutedIndicator(viewerState = null)
                }
            }
        }

        composeTestRule.onNodeWithContentDescription(mutedLabel).assertDoesNotExist()
    }
}
