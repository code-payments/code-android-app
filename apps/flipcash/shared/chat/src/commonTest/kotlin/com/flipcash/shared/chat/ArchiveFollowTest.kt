package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Following the archive on the event stream: when the stream is asked to carry archived groups,
 * what answers a follow, and what of an archived group's updates still lands while it is not
 * followed.
 *
 * The seq and ack bookkeeping under this is the controller's, and is covered in
 * `EventStreamingControllerTest` and `FollowArchiveSequencerTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveFollowTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(9, 9, 9)
    private val groupHex = "aabbccdd"
    private val group = ChatId(groupHex)
    private val summary = RosterSummary(memberCount = 2, version = 5)

    private val chatUpdates = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)
    private val acks = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    private val serverArchived = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val followCalls = mutableListOf<Boolean>()
    private var following = false

    private lateinit var controller: EventStreamingController
    private lateinit var messagingController: ChatMessagingController
    private lateinit var metadataDataSource: ChatMetadataDataSource
    private lateinit var stateHolder: ChatStateHolder
    private lateinit var delegate: EventStreamDelegate

    @BeforeTest
    fun setUp() {
        controller = mockk(relaxed = true)
        every { controller.chatUpdates } returns chatUpdates.receiveAsFlow()
        every { controller.followArchiveAcks } returns acks
        every { controller.setFollowArchive(any()) } answers {
            val follow = firstArg<Boolean>()
            if (follow != following) followCalls += follow
            following = follow
        }

        messagingController = mockk(relaxed = true)
        every { messagingController.getDelta(any(), any(), any()) } returns emptyFlow()

        metadataDataSource = mockk(relaxed = true)
        every { metadataDataSource.observeArchived() } returns serverArchived
        every { metadataDataSource.chatIdHex(group) } returns groupHex
        coEvery { metadataDataSource.getChatType(any()) } returns ChatType.GROUP
        coEvery { metadataDataSource.archivedAt(any()) } returns null
        coEvery { metadataDataSource.updateViewerState(any(), any()) } returns
            ChatMetadataDataSource.ViewerStateWrite.Applied

        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns selfId

        stateHolder = ChatStateHolder()
        delegate = EventStreamDelegate(
            eventStreamingController = controller,
            messagingController = messagingController,
            metadataDataSource = metadataDataSource,
            messageDataSource = mockk(relaxed = true),
            memberDataSource = mockk(relaxed = true),
            tokenCoordinator = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = stateHolder,
            analytics = mockk(relaxed = true),
            exchange = mockk(relaxed = true),
        )
    }

    private fun TestScope.collectEvents(): List<EventStreamDelegate.Event> {
        val events = mutableListOf<EventStreamDelegate.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.events.collect { events += it }
        }
        return events
    }

    private fun TestScope.startFollowing() {
        backgroundScope.launch { delegate.followArchiveWhileVisible() }
        runCurrent()
    }

    private fun TestScope.openStream() {
        delegate.initialize(backgroundScope)
        delegate.open()
        runCurrent()
    }

    private fun List<EventStreamDelegate.Event>.stale() =
        count { it == EventStreamDelegate.Event.ArchivedGroupsStale }

    // region Following

    @Test
    fun `opening the archive follows it, and its ack re-reads the archived groups`() = runTest {
        val events = collectEvents()
        startFollowing()

        delegate.setArchiveVisible(true)
        runCurrent()
        assertEquals(listOf(true), followCalls)
        assertEquals(0, events.stale())

        acks.emit(true)
        runCurrent()
        assertEquals(1, events.stale())
    }

    @Test
    fun `a reconnect that follows again re-reads again`() = runTest {
        val events = collectEvents()
        startFollowing()
        delegate.setArchiveVisible(true)
        runCurrent()

        acks.emit(true)
        runCurrent()
        // The controller re-follows right after the new stream's Params and acks it.
        acks.emit(true)
        runCurrent()

        assertEquals(2, events.stale())
    }

    @Test
    fun `a follow nothing answers re-reads once the wait runs out`() = runTest {
        val events = collectEvents()
        startFollowing()
        delegate.setArchiveVisible(true)
        runCurrent()

        advanceTimeBy(EventStreamDelegate.FOLLOW_ACK_TIMEOUT - 1.milliseconds)
        assertEquals(0, events.stale())
        advanceTimeBy(2.milliseconds)
        assertEquals(1, events.stale())

        // A late ack still re-reads: it says the stream carries the groups from now on.
        acks.emit(true)
        runCurrent()
        assertEquals(2, events.stale())
    }

    @Test
    fun `an ack that confirms an unfollow re-reads nothing`() = runTest {
        val events = collectEvents()
        startFollowing()
        delegate.setArchiveVisible(true)
        runCurrent()

        acks.emit(false)
        runCurrent()

        assertEquals(0, events.stale())
    }

    @Test
    fun `leaving the archive stops following once the grace runs out`() = runTest {
        startFollowing()
        delegate.setArchiveVisible(true)
        runCurrent()

        delegate.setArchiveVisible(false)
        advanceTimeBy(EventStreamDelegate.FOLLOW_OFF_GRACE - 1.milliseconds)
        assertTrue(following)
        advanceTimeBy(2.milliseconds)

        assertFalse(following)
        assertEquals(listOf(true, false), followCalls)
    }

    @Test
    fun `going from the archive into an archived group keeps following`() = runTest {
        serverArchived.value = mapOf(groupHex to 1_000L)
        startFollowing()
        delegate.setArchiveVisible(true)
        runCurrent()

        stateHolder.update { it.copy(activeChat = group) }
        delegate.setArchiveVisible(false)
        advanceTimeBy(EventStreamDelegate.FOLLOW_OFF_GRACE * 3)

        assertTrue(following)
        assertEquals(listOf(true), followCalls)
    }

    @Test
    fun `opening an archived group from outside the archive follows it`() = runTest {
        serverArchived.value = mapOf(groupHex to 1_000L)
        startFollowing()

        stateHolder.update { it.copy(activeChat = group) }
        runCurrent()

        assertTrue(following)
    }

    @Test
    fun `opening an archived DM does not follow`() = runTest {
        serverArchived.value = mapOf(groupHex to 1_000L)
        coEvery { metadataDataSource.getChatType(group) } returns ChatType.TIP_DM
        startFollowing()

        stateHolder.update { it.copy(activeChat = group) }
        runCurrent()

        assertFalse(following)
    }

    @Test
    fun `a chat archived only on this device does not follow`() = runTest {
        // Not in the server's archive: the stream already carries it.
        startFollowing()

        stateHolder.update { it.copy(activeChat = group) }
        runCurrent()

        assertFalse(following)
    }

    @Test
    fun `signing out stops following`() = runTest {
        startFollowing()
        delegate.setArchiveVisible(true)
        runCurrent()

        delegate.clearAll()
        runCurrent()

        assertFalse(following)
    }

    // endregion

    // region Catch-up on follow

    private fun TestScope.openArchivedGroupHolding(cursor: Long, serverArchived: Boolean = true) {
        if (serverArchived) {
            this@ArchiveFollowTest.serverArchived.value = mapOf(groupHex to 1_000L)
            coEvery { metadataDataSource.archivedAt(group) } returns 1_000L
        }
        coEvery { metadataDataSource.getLatestEventSequence(group) } returns cursor
        openStream()
        startFollowing()
        stateHolder.update { it.copy(activeChat = group) }
        runCurrent()
    }

    @Test
    fun `a confirmed follow fetches what an open archived group missed, from its cursor`() = runTest {
        openArchivedGroupHolding(cursor = 42L)
        verify(exactly = 0) { messagingController.getDelta(any(), any(), any()) }

        acks.emit(true)
        runCurrent()

        verify(exactly = 1) { messagingController.getDelta(group, 42L, any()) }
    }

    @Test
    fun `a follow nothing answers fetches no catch-up`() = runTest {
        openArchivedGroupHolding(cursor = 42L)

        advanceTimeBy(EventStreamDelegate.FOLLOW_ACK_TIMEOUT * 2)
        runCurrent()

        verify(exactly = 0) { messagingController.getDelta(any(), any(), any()) }
    }

    @Test
    fun `an open archived group with no cursor yet fetches no catch-up`() = runTest {
        openArchivedGroupHolding(cursor = 0L)

        acks.emit(true)
        runCurrent()

        verify(exactly = 0) { messagingController.getDelta(any(), any(), any()) }
    }

    @Test
    fun `a confirmed follow fetches nothing for an open inbox group`() = runTest {
        // Followed for the Archived screen while an inbox group is open: the stream carried it.
        coEvery { metadataDataSource.getLatestEventSequence(group) } returns 42L
        openStream()
        startFollowing()
        delegate.setArchiveVisible(true)
        stateHolder.update { it.copy(activeChat = group) }
        runCurrent()

        acks.emit(true)
        runCurrent()

        verify(exactly = 0) { messagingController.getDelta(any(), any(), any()) }
    }

    // endregion

    // region Updates after the follow is off

    private fun rosterUpdate(vararg changes: RosterChange, metadata: List<MetadataUpdate> = emptyList()) =
        ChatUpdate(chatId = group, rosterUpdates = changes.toList(), metadataUpdates = metadata)

    private fun List<EventStreamDelegate.Event>.rosterChanges() =
        filterIsInstance<EventStreamDelegate.Event.RosterChanged>().flatMap { it.changes }

    @Test
    fun `an archived group's updates that land after the follow is off are applied`() = runTest {
        // Server-archived and not followed: the stream sends a few more before the OFF reaches
        // the server. They are real events, and a pending unarchive shows this group in the inbox.
        coEvery { metadataDataSource.archivedAt(group) } returns 1_000L
        val events = collectEvents()
        openStream()
        val viewerState = ViewerState(version = 7)
        val otherLeave = RosterChange.MemberLeft(otherId, summary)
        val ownLeave = RosterChange.MemberLeft(selfId, summary)

        chatUpdates.send(
            rosterUpdate(
                otherLeave,
                ownLeave,
                metadata = listOf(MetadataUpdate.ViewerStateChanged(viewerState)),
            ),
        )
        runCurrent()

        assertEquals(listOf<RosterChange>(otherLeave, ownLeave), events.rosterChanges())
        coVerify { metadataDataSource.updateViewerState(group, viewerState) }
    }

    @Test
    fun `a group archived only on this device keeps its live updates`() = runTest {
        // The server does not hold it as archived, so the stream is meant to carry it.
        coEvery { metadataDataSource.archivedAt(group) } returns null
        val events = collectEvents()
        openStream()
        val change = RosterChange.MemberLeft(otherId, summary)

        chatUpdates.send(rosterUpdate(change))
        runCurrent()

        assertEquals(listOf<RosterChange>(change), events.rosterChanges())
    }

    // endregion
}
