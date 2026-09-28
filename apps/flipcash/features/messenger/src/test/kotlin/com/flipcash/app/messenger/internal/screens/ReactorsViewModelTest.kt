package com.flipcash.app.messenger.internal.screens

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.repository.ReactorsPage
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.reactions.ReactionPill
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The open sheet follows the message's live reactions and fills in reactors' names as their
 * profiles arrive, rather than showing whatever it held when it opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactorsViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)

    private val chat = ChatId(ByteArray(32) { 1 })
    private val messageId = 7L
    private val alice: ID = List(32) { 2 }
    private val bob: ID = List(32) { 3 }

    /** Who has reacted with 👍, as the server would answer a reactors fetch right now. */
    private var thumbsUp = listOf(alice)

    private val liveReactions = MutableStateFlow<Map<Long, MessageReactions>>(emptyMap())
    private val senderProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { observeChatReactions(chat) } answers { liveReactions }
        every { observeMembers(chat) } returns emptyFlow()
        every { observeSenderProfiles() } answers { senderProfiles }
        coEvery { getMessage(chat, messageId) } returns null
        coEvery { getReactorsPage(chat, messageId, "👍", any(), any()) } answers {
            Result.success(
                ReactorsPage(
                    reactors = thumbsUp.mapIndexed { i, id -> Reactor(id, Instant.fromEpochSeconds(i.toLong())) },
                    hasMore = false,
                ),
            )
        }
    }

    private val userProfileDataSource = mockk<UserProfileDataSource> {
        every { observeProfiles() } returns MutableStateFlow(emptyMap())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ReactorsViewModel(
        chatCoordinator = chatCoordinator,
        userManager = mockk<UserManager>(relaxed = true),
        resources = mockk<ResourceHelper>(relaxed = true),
        userProfileDataSource = userProfileDataSource,
        dispatchers = TestDispatcherProvider(dispatcher),
    ).apply { dispatchEvent(ReactorsViewModel.Event.Open(chat, messageId)) }

    private fun reacted(count: Long) = mapOf(
        messageId to MessageReactions(
            pills = listOf(ReactionPill(emoji = "👍", count = count, selfReacted = false, pending = false)),
            selfReactions = emptyList(),
        ),
    )

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    private fun ReactorsViewModel.rowIds() = stateFlow.value.rows.map { it.userId }

    @Test
    fun `a reaction added while the sheet is open adds its row`() = runTest(scheduler) {
        liveReactions.value = reacted(count = 1)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(alice), model.rowIds())

        thumbsUp = listOf(alice, bob)
        liveReactions.value = reacted(count = 2)
        advanceUntilIdle()

        assertEquals(2L, model.stateFlow.value.pills.single().count)
        assertEquals(setOf(alice, bob), model.rowIds().toSet())
    }

    @Test
    fun `an unnamed reactor is asked for once and named when the profile lands`() = runTest(scheduler) {
        liveReactions.value = reacted(count = 1)
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(null, model.stateFlow.value.rows.single().display)
        verify(exactly = 1) { chatCoordinator.requestSenderProfile(alice) }

        senderProfiles.value = mapOf(alice.hexEncodedString() to profile("Alice"))
        advanceUntilIdle()

        assertEquals("Alice", model.stateFlow.value.rows.single().display?.name)
        verify(exactly = 1) { chatCoordinator.requestSenderProfile(alice) }
    }
}
