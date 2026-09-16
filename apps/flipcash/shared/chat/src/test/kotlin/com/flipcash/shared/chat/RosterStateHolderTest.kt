package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.chat.internal.RosterStateHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

/**
 * A roster change applies only when its version is newer than the stored one, and a version that
 * skips ahead means the device missed a change it cannot reconstruct — the repair is a refetch,
 * not an apply.
 */
class RosterStateHolderTest {

    private val chatId = ChatId("11223344")
    private val joinerId = listOf<Byte>(7, 7, 7)
    private val leaverId = listOf<Byte>(8, 8, 8)

    private val controller = mockk<ChatController>(relaxed = true)
    private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)

    private val subject = RosterStateHolder(
        chatController = controller,
        metadataDataSource = metadataDataSource,
        memberDataSource = memberDataSource,
    )

    private fun joiner(userId: List<Byte> = joinerId) = ChatMember(
        userId = userId,
        userProfile = UserProfile.Empty.copy(displayName = "Ada"),
        pointers = emptyList(),
    )

    private fun joined(version: Long, memberCount: Long = 13) = RosterChange.MemberJoined(
        member = joiner(),
        metadata = null,
        rosterSummary = RosterSummary(memberCount = memberCount, version = version),
    )

    private fun left(version: Long, memberCount: Long = 11) = RosterChange.MemberLeft(
        userId = leaverId,
        rosterSummary = RosterSummary(memberCount = memberCount, version = version),
    )

    private fun storedVersion(version: Long) {
        coEvery { metadataDataSource.getRosterVersion(chatId) } returns version
    }

    @Test
    fun `a join one version ahead is applied`() = runTest {
        storedVersion(4)

        subject.apply(chatId, listOf(joined(version = 5)))

        coVerify { memberDataSource.upsert(chatId, listOf(joiner())) }
        coVerify { metadataDataSource.updateRoster(chatId, memberCount = 13, rosterVersion = 5) }
    }

    @Test
    fun `a leave one version ahead removes the member`() = runTest {
        storedVersion(4)

        subject.apply(chatId, listOf(left(version = 5)))

        coVerify { memberDataSource.deleteMember(chatId, leaverId) }
        coVerify { metadataDataSource.updateRoster(chatId, memberCount = 11, rosterVersion = 5) }
    }

    @Test
    fun `a change older than the stored version is dropped`() = runTest {
        storedVersion(9)

        subject.apply(chatId, listOf(joined(version = 4)))

        coVerify(exactly = 0) { memberDataSource.upsert(any(), any()) }
        coVerify(exactly = 0) { metadataDataSource.updateRoster(any(), any(), any()) }
    }

    /** Re-delivery is normal on a stream that guarantees at-least-once. */
    @Test
    fun `a change at the stored version is dropped`() = runTest {
        storedVersion(5)

        subject.apply(chatId, listOf(joined(version = 5)))

        coVerify(exactly = 0) { memberDataSource.upsert(any(), any()) }
    }

    @Test
    fun `a version that skips ahead refetches the chat instead of applying`() = runTest {
        storedVersion(4)
        val refetched = ChatMetadata(
            chatId = chatId,
            type = ChatType.GROUP,
            members = listOf(joiner()),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(1_000),
            rosterSummary = RosterSummary(memberCount = 20, version = 9),
        )
        coEvery { controller.getChat(chatId) } returns Result.success(refetched)

        subject.apply(chatId, listOf(joined(version = 7)))

        coVerify { controller.getChat(chatId) }
        coVerify { metadataDataSource.upsert(refetched) }
        coVerify { memberDataSource.upsert(chatId, refetched.members) }
        // The skipped change is not applied on top of what the refetch returned.
        coVerify(exactly = 0) { metadataDataSource.updateRoster(chatId, 13, 7) }
    }

    @Test
    fun `a failed refetch leaves the stored roster alone`() = runTest {
        storedVersion(4)
        coEvery { controller.getChat(chatId) } returns Result.failure(Throwable("unreachable"))

        subject.apply(chatId, listOf(joined(version = 7)))

        coVerify(exactly = 0) { metadataDataSource.upsert(any<ChatMetadata>()) }
        coVerify(exactly = 0) { metadataDataSource.updateRoster(any(), any(), any()) }
    }

    /**
     * The stream carries no ordering guarantee for roster changes, so a batch can arrive newest
     * first. Applied in arrival order, the older change would look like a gap and trigger a
     * refetch for something the device already had.
     */
    @Test
    fun `a batch is applied in version order`() = runTest {
        val versions = mutableListOf(4L, 5L, 6L)
        coEvery { metadataDataSource.getRosterVersion(chatId) } answers { versions.removeAt(0) }

        subject.apply(chatId, listOf(joined(version = 6), joined(version = 5)))

        coVerify(exactly = 0) { controller.getChat(any()) }
        coVerify { metadataDataSource.updateRoster(chatId, memberCount = 13, rosterVersion = 5) }
        coVerify { metadataDataSource.updateRoster(chatId, memberCount = 13, rosterVersion = 6) }
    }
}
