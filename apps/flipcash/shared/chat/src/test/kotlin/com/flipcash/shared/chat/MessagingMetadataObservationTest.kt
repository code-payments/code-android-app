package com.flipcash.shared.chat

import app.cash.turbine.test
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
                assertFalse(first.isMember)

                rows.value = entity(memberCount = 3, isMember = true)
                val second = awaitItem()!!
                assertEquals(3L, second.metadata.rosterSummary.memberCount)
                assertTrue(second.isMember)

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

    private fun delegate(
        metadataDataSource: ChatMetadataDataSource,
        memberDataSource: ChatMemberDataSource,
        messageDataSource: ChatMessageDataSource,
    ) = MessagingDelegate(
        chatController = mockk(relaxed = true),
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
