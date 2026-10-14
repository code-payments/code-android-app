package com.flipcash.app.messenger.internal.screens.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The jump-to-bottom button is down while a message is raised: the backdrop is modal and the list
 * can't be dragged, so the button must not scroll it either. Opened at an unread divider well off
 * the bottom, where the button is up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class JumpToBottomVisibilityTest {

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
    fun `the button goes away while a message is selected and returns after`() {
        val boundary = UnreadBoundary.At(readThrough = 10, count = 30)
        val newestFirst = (40L downTo 1L).map(::bubble)
        val loaded = LoadState.NotLoading(endOfPaginationReached = true)
        val page = PagingData.from(newestFirst, LoadStates(loaded, loaded, loaded))
        val paged = flowOf(page.withSeparators(boundary, SeparatorConfig.DayOnly))
        var selected by mutableStateOf<ChatListItem.ContentBubble?>(null)

        composeRule.setContent {
            FlipcashThemeWrapper().Wrap {
                MessageList(
                    contentPadding = PaddingValues(),
                    state = ChatViewModel.State(
                        unreadBoundary = boundary,
                        unreadWalkBudget = 30,
                        selection = selected,
                    ),
                    messages = paged.collectAsLazyPagingItems(),
                    separatorConfig = SeparatorConfig.DayOnly,
                    onAction = {},
                    linkCardResolution = Unresolved,
                )
            }
        }
        composeRule.waitUntil(5_000) {
            (1..40).any { composeRule.onAllNodesWithText("m$it").fetchSemanticsNodes().isNotEmpty() }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(button).assertIsDisplayed()

        selected = newestFirst[20]
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithContentDescription(button).assertDoesNotExist()

        selected = null
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithContentDescription(button).assertIsDisplayed()
    }

    private object Unresolved : LinkCardResolution {
        override val revision: StateFlow<Int> = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard = card
        override suspend fun resolve(card: LinkCard): LinkCard = card
    }
}
