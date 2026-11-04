package com.flipcash.app.tipping.internal

import androidx.test.core.app.ApplicationProvider
import com.flipcash.analytics.ChatReadSource
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.core.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatFeeds
import com.flipcash.shared.chat.ChatListProjection
import com.flipcash.shared.chat.ChatMarkedUnreadStore
import com.flipcash.shared.chat.ChatMuteToggle
import com.flipcash.shared.chat.ChatReadActions
import com.flipcash.shared.chat.ChatReadToggle
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Rate
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The chat list's row actions and select mode: entering and leaving it, what the bar offers, and
 * the spec cases that live above the shared rules (Read All's Cancel, bulk Mute, bulk Archive with
 * Undo, and Requests chats taking no read action). The rules themselves are tested in shared/chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatListSelectionTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @Before
    fun setUp() = BottomBarManager.clear()

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun id(n: Int) = ChatId(ByteArray(16) { n.toByte() }.toList())

    private fun summary(n: Int, type: ChatType = ChatType.TIP_DM, unread: Int = 0, marked: Boolean = false) =
        ChatSummary(
            metadata = ChatMetadata(
                chatId = id(n),
                type = type,
                members = emptyList(),
                lastMessage = null,
                lastActivity = Instant.fromEpochSeconds(1_000L + n),
                title = "chat $n",
                latestEventSequence = 0,
            ),
            unreadCount = unread,
            markedUnread = marked,
        )

    // --- select mode, through the reducer ---

    private fun reduce(state: ChatsViewModel.State, event: ChatsViewModel.Event) =
        ChatsViewModel.updateStateForEvent(event)(state)

    private val unreadA = ConversationReference(chatId = id(1), unreadCount = 3)
    private val readC = ConversationReference(chatId = id(3))
    private val readE = ConversationReference(chatId = id(5))

    private val loaded = reduce(
        ChatsViewModel.State(),
        ChatsViewModel.Event.ChatsUpdated(
            Loadable.Loaded(listOf(unreadA, readC, readE)),
            ChatListProjection.Empty.copy(main = listOf(unreadA, readC, readE).map { it.chatId.toString() }),
        ),
    )

    private fun ChatsViewModel.State.select(vararg ids: ChatId): ChatsViewModel.State =
        ids.fold(reduce(this, ChatsViewModel.Event.SelectionStarted(ids.first()))) { state, chatId ->
            if (chatId == ids.first()) state else reduce(state, ChatsViewModel.Event.SelectionToggled(chatId))
        }

    @Test
    fun `a long press enters select mode with that chat selected`() {
        val selecting = reduce(loaded, ChatsViewModel.Event.SelectionStarted(readC.chatId))
        assertTrue(selecting.isSelecting)
        assertEquals(listOf(readC), selecting.selectedChats)
        assertEquals(1, selecting.selectionActions.count)
    }

    @Test
    fun `Select Chats enters select mode with nothing selected`() {
        val selecting = reduce(loaded, ChatsViewModel.Event.SelectionStarted(null))
        assertTrue(selecting.isSelecting)
        assertEquals(0, selecting.selectionActions.count)
    }

    @Test
    fun `deselecting the last row stays in select mode with nothing selected`() {
        val selecting = reduce(loaded, ChatsViewModel.Event.SelectionStarted(readC.chatId))
        val emptied = reduce(selecting, ChatsViewModel.Event.SelectionToggled(readC.chatId))
        assertTrue(emptied.isSelecting)
        assertEquals(0, emptied.selectionActions.count)
        assertFalse(reduce(emptied, ChatsViewModel.Event.SelectionEnded).isSelecting)
    }

    // Spec case 12.
    @Test
    fun `an unread chat in the selection makes the toggle Mark as Read`() {
        assertEquals(ChatReadToggle.MarkRead, loaded.select(unreadA.chatId, readC.chatId).selectionActions.read)
    }

    // Spec case 13.
    @Test
    fun `an all-read selection makes the toggle Mark as Unread`() {
        assertEquals(ChatReadToggle.MarkUnread, loaded.select(readC.chatId, readE.chatId).selectionActions.read)
    }

    @Test
    fun `Read All is disabled when nothing in the main list is unread`() {
        val allRead = reduce(
            loaded,
            ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(listOf(readC, readE)), ChatListProjection.Empty),
        )
        assertFalse(allRead.canReadAll)
        assertTrue(loaded.canReadAll)
        val marked = readC.copy(markedUnread = true)
        assertTrue(
            reduce(loaded, ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(listOf(marked)), ChatListProjection.Empty))
                .canReadAll
        )
    }

    // --- the view model, for the actions that leave select mode ---

    private val rowActions = mockk<ChatRowActions>(relaxed = true)

    private fun TestScope.viewModel(main: List<ChatSummary>): HiltChatsViewModel {
        val feeds = MutableStateFlow(ChatFeeds(main = main, archived = emptyList()))
        val chatCoordinator = mockk<ChatCoordinator>(relaxed = true).also {
            every { it.feedWithArchived(*anyVararg()) } returns feeds
            every { it.currentFeedWithArchived(*anyVararg()) } returns feeds.value
            every { it.observeSenderProfiles() } returns flowOf(emptyMap())
        }
        every { rowActions.summaries(any()) } answers {
            val ids = firstArg<Collection<ChatId>>()
            main.filter { it.metadata.chatId in ids }
        }
        return HiltChatsViewModel(
            exchange = mockk<Exchange>(relaxed = true).also {
                every { it.observePreferredRate() } returns flowOf(Rate.oneToOne)
                every { it.observeRates() } returns flowOf(emptyMap())
            },
            chatCoordinator = chatCoordinator,
            userManager = mockk<UserManager>(relaxed = true),
            tokenCoordinator = mockk<TokenCoordinator>(relaxed = true).also {
                every { it.tokens } returns flowOf(emptyList())
                every { it.cachedTokens() } returns emptyList()
            },
            resources = FakeResourceHelper(R::class.java),
            dispatchers = TestDispatchers(testScheduler),
            archiveStore = ChatArchiveStore.None,
            lobbyRequests = mockk(relaxed = true),
            hostLobbies = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
            paymentRequests = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
            rowActions = rowActions,
        )
    }

    // Spec case 11.
    @Test
    fun `cancelling Read All changes nothing`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(listOf(summary(1, unread = 2)))
        vm.requestReadAll()

        val prompt = BottomBarManager.messages.value.single()
        assertTrue(prompt.showCancel)
        assertEquals("prompt_title_readAllChats", prompt.title)
        // Cancel is the prompt's own button and closes it; nothing else runs.
        BottomBarManager.clear()
        advanceUntilIdle()
        coVerify(exactly = 0) { rowActions.readAll() }

        vm.requestReadAll()
        BottomBarManager.messages.value.single().actions.single().onClick()
        advanceUntilIdle()
        coVerify(exactly = 1) { rowActions.readAll() }
    }

    // Spec case 14.
    @Test
    fun `bulk Mute opens one sheet for the whole selection`() = runTest(mainCoroutineRule.dispatcher) {
        val muted = summary(1, type = ChatType.GROUP)
        val audible = summary(2)
        val vm = viewModel(listOf(muted, audible))
        advanceUntilIdle()
        vm.startSelection(muted.metadata.chatId)
        vm.toggleSelection(audible.metadata.chatId)
        assertEquals(ChatMuteToggle.Mute, vm.stateFlow.value.selectionActions.mute)

        val sheet = vm.muteSelection()

        assertEquals(listOf(id(1), id(2)) to listOf(ChatType.GROUP, ChatType.TIP_DM), sheet)
        assertFalse(vm.stateFlow.value.isSelecting)
    }

    // Spec case 16.
    @Test
    fun `bulk Archive archives every selected chat and Undo restores them all`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = viewModel(listOf(summary(1), summary(2)))
            advanceUntilIdle()
            vm.startSelection(id(1))
            vm.toggleSelection(id(2))

            val archived = vm.archiveSelection()
            advanceUntilIdle()

            assertEquals(setOf(id(1), id(2)), archived.toSet())
            coVerify(exactly = 1) { rowActions.archive(match { it.toSet() == setOf(id(1), id(2)) }) }
            assertFalse(vm.stateFlow.value.isSelecting)

            vm.unarchive(archived)
            advanceUntilIdle()
            coVerify(exactly = 1) { rowActions.unarchive(match { it.toSet() == setOf(id(1), id(2)) }) }
        }

    @Test
    fun `the archive toast reads Chat archived for one and counts more`() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertEquals("Chat archived", resources.getQuantityString(R.plurals.message_chatsArchived, 1, 1))
        assertEquals("2 chats archived", resources.getQuantityString(R.plurals.message_chatsArchived, 2, 2))
    }

    // --- ChatRowActions: what each path sends ---

    private val marks = mockk<ChatMarkedUnreadStore>(relaxed = true)
    private val analytics = RecordingAnalytics()

    private fun actions(main: List<ChatSummary>): Pair<ChatRowActions, ChatCoordinator> {
        val coordinator = mockk<ChatCoordinator>(relaxed = true).also {
            every { it.currentFeedWithArchived(*anyVararg()) } returns ChatFeeds(main, emptyList())
        }
        return ChatRowActions(
            chatCoordinator = coordinator,
            readActions = ChatReadActions(coordinator, marks),
            archiveStore = ChatArchiveStore.None,
            analytics = analytics,
        ) to coordinator
    }

    // Spec case 1: marks locally, sends nothing, reports the swipe.
    @Test
    fun `a swipe on a read chat marks it unread without an RPC`() = runTest(mainCoroutineRule.dispatcher) {
        val (actions, coordinator) = actions(listOf(summary(3)))

        actions.toggleRead(id(3), ChatReadSource.SWIPE)

        coVerify(exactly = 1) { marks.mark(id(3)) }
        coVerify(exactly = 0) { coordinator.markAsRead(any()) }
        assertEquals(
            listOf(ChatEvents.markedUnread(chatType = ChatType.TIP_DM.analytics, source = ChatReadSource.SWIPE)),
            analytics.events,
        )
    }

    // Spec case 3: the swipe offers Mark as Read on an unread chat and advances READ.
    @Test
    fun `a swipe on an unread chat advances READ`() = runTest(mainCoroutineRule.dispatcher) {
        val (actions, coordinator) = actions(listOf(summary(1, unread = 3)))

        actions.toggleRead(id(1), ChatReadSource.SWIPE)

        coVerify(exactly = 1) { coordinator.markAsRead(id(1)) }
        coVerify(exactly = 0) { marks.mark(any()) }
        assertEquals(
            listOf(ChatEvents.markedRead(chatType = ChatType.TIP_DM.analytics, source = ChatReadSource.SWIPE)),
            analytics.events,
        )
    }

    @Test
    fun `select mode reports one event per selected chat`() = runTest(mainCoroutineRule.dispatcher) {
        val (actions, _) = actions(listOf(summary(3), summary(5, type = ChatType.GROUP)))

        actions.toggleRead(listOf(id(3), id(5)))

        assertEquals(
            listOf(
                ChatEvents.markedUnread(chatType = ChatType.TIP_DM.analytics, source = ChatReadSource.SELECTION),
                ChatEvents.markedUnread(chatType = ChatType.GROUP.analytics, source = ChatReadSource.SELECTION),
            ),
            analytics.events,
        )
    }

    // Spec case 9's count reaches the event.
    @Test
    fun `Read All reports how many chats it changed`() = runTest(mainCoroutineRule.dispatcher) {
        val (actions, _) = actions(listOf(summary(1, unread = 2), summary(2, marked = true), summary(3)))

        actions.readAll()

        assertEquals(listOf(ChatEvents.readAll(count = 2)), analytics.events)
    }

    // Spec case 18: a chat still in Requests is in neither list, so no read action reaches it.
    @Test
    fun `a chat still in Requests takes no read action`() = runTest(mainCoroutineRule.dispatcher) {
        val (actions, coordinator) = actions(listOf(summary(1)))
        val requested = id(9)

        actions.toggleRead(requested, ChatReadSource.SWIPE)
        actions.toggleRead(listOf(requested))

        coVerify(exactly = 0) { marks.mark(any()) }
        coVerify(exactly = 0) { coordinator.markAsRead(any()) }
        assertTrue(analytics.events.isEmpty())
        assertNull(actions.summaries(listOf(requested)).firstOrNull())
    }
}
