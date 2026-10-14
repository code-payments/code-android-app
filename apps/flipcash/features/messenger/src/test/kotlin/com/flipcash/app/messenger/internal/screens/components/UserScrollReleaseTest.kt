package com.flipcash.app.messenger.internal.screens.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.withSeparators
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.SeparatorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What releases the jump-to-bottom badge's freeze: a finger on the transcript after it has
 * settled, and nothing the list does by itself. isScrollInProgress can't tell them apart, so the
 * list listens for the drag instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class UserScrollReleaseTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val start = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val button = "Jump to latest message"

    private fun bubble(id: Long) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("m$id"),
        isFromSelf = false,
        timestamp = start + id.minutes,
    )

    @Test
    fun `the jump button's own scroll does not release the badge, a drag does`() {
        val boundary = UnreadBoundary.At(readThrough = 10, count = 30)
        val newestFirst = (40L downTo 1L).map(::bubble)
        val loaded = LoadState.NotLoading(endOfPaginationReached = true)
        val page = PagingData.from(newestFirst, LoadStates(loaded, loaded, loaded))
        val paged = flowOf(page.withSeparators(boundary, SeparatorConfig.DayOnly))
        var released = 0

        composeRule.setContent {
            FlipcashThemeWrapper().Wrap {
                MessageList(
                    contentPadding = PaddingValues(),
                    state = ChatViewModel.State(unreadBoundary = boundary, unreadWalkBudget = 30),
                    messages = paged.collectAsLazyPagingItems(),
                    separatorConfig = SeparatorConfig.DayOnly,
                    onAction = {},
                    linkCardResolution = Unresolved,
                    onUserScrolled = { released++ },
                )
            }
        }
        composeRule.waitUntil(5_000) {
            (1..40).any { composeRule.onAllNodesWithText("m$it").fetchSemanticsNodes().isNotEmpty() }
        }
        composeRule.waitForIdle()
        // The opening walk to the divider was a programmatic scroll.
        assertEquals(0, released, "the opening walk released the badge")

        composeRule.onNodeWithContentDescription(button).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("m40").assertIsDisplayed()
        assertEquals(0, released, "the jump button's animated scroll released the badge")

        composeRule.onNodeWithText("m40").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertEquals(1, released)
    }

    private object Unresolved : LinkCardResolution {
        override val revision: StateFlow<Int> = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard = card
        override suspend fun resolve(card: LinkCard): LinkCard = card
    }
}
