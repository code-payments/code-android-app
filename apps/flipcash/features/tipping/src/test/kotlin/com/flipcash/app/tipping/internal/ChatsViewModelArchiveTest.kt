package com.flipcash.app.tipping.internal

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.tipping.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatFeeds
import com.flipcash.shared.chat.ChatSummary
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Archiving the last chat in the main list moves it to the Archived row in one step. The screen
 * reads both lists from one feed, so no state between the two shows the no-chats prompt.
 * `ChatArchiveFeedTest` pins the feed side: the delegate emits both lists from one state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelArchiveTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chat = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(ByteArray(16) { 3 }.toList()),
            type = ChatType.GROUP,
            members = emptyList(),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(1_000),
            title = "Ballers",
            latestEventSequence = 0,
        ),
        unreadCount = 0,
    )

    @Test
    fun `archiving the last main chat never shows the no-chats prompt`() =
        runTest(mainCoroutineRule.dispatcher) {
            val feeds = MutableStateFlow(ChatFeeds(main = listOf(chat), archived = emptyList()))
            val chatCoordinator = mockk<ChatCoordinator>(relaxed = true).also {
                every { it.feedWithArchived(*anyVararg()) } returns feeds
                every { it.currentFeedWithArchived(*anyVararg()) } returns null
                every { it.observeSenderProfiles() } returns flowOf(emptyMap())
            }
            val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true).also {
                every { it.tokens } returns flowOf(emptyList())
                every { it.cachedTokens() } returns emptyList()
            }
            val vm = ChatsViewModel(
                chatCoordinator = chatCoordinator,
                userManager = mockk<UserManager>(relaxed = true),
                tokenCoordinator = tokenCoordinator,
                resources = FakeResourceHelper(R::class.java),
                dispatchers = TestDispatchers(testScheduler),
                archiveStore = ChatArchiveStore.None,
            )

            val states = mutableListOf<ChatsViewModel.State>()
            backgroundScope.launch { vm.stateFlow.collect { states += it } }
            advanceUntilIdle()

            feeds.value = ChatFeeds(main = emptyList(), archived = listOf(chat))
            advanceUntilIdle()

            val loaded = states.filter { it.chats.dataOrNull != null }
            assertTrue(loaded.isNotEmpty())
            assertTrue(loaded.none { it.hasNoChatsAtAll }, "a loaded state showed the no-chats prompt")
            val last = states.last()
            assertEquals(emptyList(), last.chats.dataOrNull)
            assertTrue(last.showsArchivedRow)
        }
}
