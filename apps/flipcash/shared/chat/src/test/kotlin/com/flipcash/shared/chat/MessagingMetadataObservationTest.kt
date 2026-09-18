package com.flipcash.shared.chat

import app.cash.turbine.test
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * `observeMetadata` is the one read the chrome and the access gate share. It carries membership
 * alongside the metadata because [ChatMetadata] has no place for it — membership is a column on
 * the row, and the gate is the first thing that needs to ask.
 */
class MessagingMetadataObservationTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = TestDispatchers(scheduler)

    private val chatId = ChatId(ByteArray(32) { 7 }.toList())
    private val chatIdHex = "07".repeat(32)

    private fun entity(memberCount: Long, isMember: Boolean) = ChatMetadataEntity(
        chatIdHex = chatIdHex,
        chatType = ChatType.GROUP.name,
        lastActivityEpochMs = 1_000,
        lastMessageId = null,
        title = "Flipcash Staff",
        memberCount = memberCount,
        rosterVersion = 1,
        isMember = isMember,
    )

    private fun metadata(memberCount: Long) = ChatMetadata(
        chatId = chatId,
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(1_000),
        title = "Flipcash Staff",
        rosterSummary = RosterSummary(memberCount = memberCount, version = 1),
    )

    @Test
    fun `observeMetadata carries the row's membership and re-emits on change`() =
        runTest(dispatchers.dispatcher) {
            val rows = MutableStateFlow(entity(memberCount = 2, isMember = false))

            val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true) {
                every { observeById(chatId) } returns rows
                every { toMetadata(entity(2, false), any(), any()) } returns metadata(2)
                every { toMetadata(entity(3, true), any(), any()) } returns metadata(3)
            }
            val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true) {
                every { observeMembers(chatId) } returns flowOf(emptyList())
            }
            val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

            val subject = delegate(metadataDataSource, memberDataSource, messageDataSource)

            subject.observeMetadata(chatId).test {
                val first = awaitItem()!!
                assertEquals(2L, first.metadata.rosterSummary.memberCount)
                assertEquals(false, first.isMember)

                rows.value = entity(memberCount = 3, isMember = true)
                val second = awaitItem()!!
                assertEquals(3L, second.metadata.rosterSummary.memberCount)
                assertEquals(true, second.isMember)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeMetadata emits null while the chat is not stored`() =
        runTest(dispatchers.dispatcher) {
            val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true) {
                every { observeById(chatId) } returns flowOf(null)
            }
            val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true) {
                every { observeMembers(chatId) } returns flowOf(emptyList())
            }

            val subject = delegate(metadataDataSource, memberDataSource, mockk(relaxed = true))

            subject.observeMetadata(chatId).test {
                assertEquals(null, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * A chat the device has no row for is fetched, and carries no membership answer.
     *
     * This is the cold open: an invite link or a push tap into a group no sync has ever brought
     * down. Without the fetch the screen has nothing to draw — the Room observation above is the
     * only other source and it emits null — and `GetChat` cannot say whether the viewer is in the
     * chat, so the result withholds rather than guesses. Nothing is written: the membership column
     * would have to hold one guess or the other.
     */
    @Test
    fun `hydrateChat fetches an unstored chat and reports membership as unknown`() =
        runTest(dispatchers.dispatcher) {
            val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true) {
                coEvery { exists(chatId) } returns false
            }
            val chatController = mockk<ChatController>(relaxed = true) {
                coEvery { getChat(chatId) } returns Result.success(metadata(memberCount = 4))
            }

            val subject = delegate(
                metadataDataSource = metadataDataSource,
                memberDataSource = mockk(relaxed = true),
                messageDataSource = mockk(relaxed = true),
                chatController = chatController,
            )

            val hydrated = subject.hydrateChat(chatId)

            assertEquals(4L, hydrated?.metadata?.rosterSummary?.memberCount)
            assertNull(hydrated?.isMember)
            coVerify(exactly = 0) { metadataDataSource.upsert(any<ChatMetadata>()) }
            coVerify(exactly = 0) { metadataDataSource.upsert(any<List<ChatMetadata>>()) }
            coVerify(exactly = 0) {
                metadataDataSource.setMembership(any<ChatId>(), any<Boolean>())
            }
        }

    /**
     * A stored chat is not refetched: `observeMetadata` is already answering for it, with the
     * membership the row holds, and a second copy would only race it.
     */
    @Test
    fun `hydrateChat leaves a stored chat to the observation`() =
        runTest(dispatchers.dispatcher) {
            val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true) {
                coEvery { exists(chatId) } returns true
            }
            val chatController = mockk<ChatController>(relaxed = true)

            val subject = delegate(
                metadataDataSource = metadataDataSource,
                memberDataSource = mockk(relaxed = true),
                messageDataSource = mockk(relaxed = true),
                chatController = chatController,
            )

            assertNull(subject.hydrateChat(chatId))
            coVerify(exactly = 0) { chatController.getChat(any()) }
        }

    private fun delegate(
        metadataDataSource: ChatMetadataDataSource,
        memberDataSource: ChatMemberDataSource,
        messageDataSource: ChatMessageDataSource,
        chatController: ChatController = mockk(relaxed = true),
    ) = MessagingDelegate(
        chatController = chatController,
        messagingController = mockk(relaxed = true),
        metadataDataSource = metadataDataSource,
        messageDataSource = messageDataSource,
        memberDataSource = memberDataSource,
        notificationManager = mockk(relaxed = true),
        userManager = mockk(relaxed = true),
        stateHolder = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
        senderResolver = mockk(relaxed = true),
    )
}
