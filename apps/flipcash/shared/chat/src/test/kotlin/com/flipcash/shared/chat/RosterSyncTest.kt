package com.flipcash.shared.chat

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ChatRosterDataSource
import com.flipcash.app.persistence.sources.RosterSyncState
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.chat.internal.RosterSync
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A group's roster is read page by page to `has_more = false`, or to the page cap, and only a read
 * that saw one unchanging roster may drop the members it did not return.
 */
class RosterSyncTest {

    private val chatId = ChatId("c0ffee")

    private val controller = mockk<ChatController>()
    private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val rosterDataSource = mockk<ChatRosterDataSource>(relaxed = true)

    private val subject = RosterSync(
        chatController = controller,
        metadataDataSource = metadataDataSource,
        memberDataSource = memberDataSource,
        rosterDataSource = rosterDataSource,
        dispatchers = TestDispatchers(TestCoroutineScheduler()),
    )

    private val requests = mutableListOf<QueryOptions>()

    private fun member(n: Int) = ChatMember(
        userId = listOf(n.toByte(), (n shr 8).toByte()),
        userProfile = UserProfile.Empty.copy(displayName = "m$n"),
        pointers = emptyList(),
    )

    /**
     * Serves [pages] pages of [perPage] members, with [versionOf] as each page's roster version.
     * Page i's token is `[i]`; the last page has no more.
     */
    private fun serve(
        pages: Int,
        perPage: Int = 2,
        versionOf: (Int) -> Long = { 7 },
        memberCount: Long = pages.toLong() * perPage,
    ) {
        val options = slot<QueryOptions>()
        coEvery { controller.getRoster(chatId, capture(options)) } answers {
            requests += options.captured
            val index = options.captured.token?.single()?.toInt() ?: 0
            val hasMore = index + 1 < pages
            Result.success(
                RosterPage(
                    members = (0 until perPage).map { member(index * perPage + it) },
                    rosterSummary = RosterSummary(memberCount = memberCount, version = versionOf(index)),
                    pagingToken = if (hasMore) listOf((index + 1).toByte()) else null,
                    hasMore = hasMore,
                )
            )
        }
    }

    @Test
    fun `reads every page until has_more is false`() = runTest {
        serve(pages = 3)

        subject.syncAll(chatId)

        assertEquals(3, requests.size)
        assertTrue(requests.all { it.limit == RosterSync.PAGE_SIZE })
        assertEquals(listOf(null, listOf<Byte>(1), listOf<Byte>(2)), requests.map { it.token })
        coVerify(exactly = 3) { memberDataSource.upsert(chatId, any()) }
        coVerify { memberDataSource.retainOnly(chatId, (0 until 6).map { member(it).userId }.toSet()) }
        coVerify { metadataDataSource.updateRoster(chatId, memberCount = 6, rosterVersion = 7) }
        coVerify { rosterDataSource.markSynced(chatId, version = 7, truncated = false, needsResync = false) }
    }

    @Test
    fun `stops at the page cap and keeps what it read`() = runTest {
        serve(pages = RosterSync.MAX_PAGES + 5)

        subject.syncAll(chatId)

        assertEquals(RosterSync.MAX_PAGES, requests.size)
        // A partial read cannot tell who left, so nothing is dropped.
        coVerify(exactly = 0) { memberDataSource.retainOnly(any(), any()) }
        coVerify { rosterDataSource.markSynced(chatId, version = 7, truncated = true, needsResync = false) }
    }

    @Test
    fun `a roster that changed mid-read drops no one and asks to be read again`() = runTest {
        serve(pages = 2, versionOf = { 7L + it })

        subject.syncAll(chatId)

        coVerify(exactly = 0) { memberDataSource.retainOnly(any(), any()) }
        coVerify(exactly = 0) { metadataDataSource.updateRoster(any(), any(), any()) }
        coVerify { rosterDataSource.markSynced(chatId, version = 8, truncated = false, needsResync = true) }
    }

    @Test
    fun `a read the stream has already overtaken drops no one`() = runTest {
        serve(pages = 1)
        coEvery { metadataDataSource.getRosterVersion(chatId) } returns 9

        subject.syncAll(chatId)

        coVerify(exactly = 0) { memberDataSource.retainOnly(any(), any()) }
        coVerify { rosterDataSource.markSynced(chatId, version = 7, truncated = false, needsResync = true) }
    }

    @Test
    fun `a failed page records nothing, so the next open tries again`() = runTest {
        serve(pages = 3)
        coEvery { controller.getRoster(chatId, match { it.token == listOf<Byte>(1) }) } returns
            Result.failure(Throwable("offline"))

        subject.syncAll(chatId)

        coVerify(exactly = 0) { rosterDataSource.markSynced(any(), any(), any(), any()) }
        coVerify(exactly = 0) { memberDataSource.retainOnly(any(), any()) }
    }

    @Test
    fun `a group never read needs a read`() = runTest {
        coEvery { rosterDataSource.getSyncState(chatId) } returns null

        assertTrue(subject.needsFullSync(chatId))
    }

    @Test
    fun `a group holding fewer members than its count needs a read`() = runTest {
        synced(truncated = false)
        coEvery { memberDataSource.countMembers(chatId) } returns 40
        coEvery { metadataDataSource.getMemberCount(chatId) } returns 41

        assertTrue(subject.needsFullSync(chatId))
    }

    @Test
    fun `a complete group does not`() = runTest {
        synced(truncated = false)
        coEvery { memberDataSource.countMembers(chatId) } returns 41
        coEvery { metadataDataSource.getMemberCount(chatId) } returns 41

        assertFalse(subject.needsFullSync(chatId))
    }

    @Test
    fun `a group cut off at the cap is not read again on every open`() = runTest {
        synced(truncated = true)
        coEvery { memberDataSource.countMembers(chatId) } returns 2_000
        coEvery { metadataDataSource.getMemberCount(chatId) } returns 5_000

        assertFalse(subject.needsFullSync(chatId))
    }

    @Test
    fun `a version gap forces a read even when the counts agree`() = runTest {
        synced(truncated = true, needsResync = true)

        assertTrue(subject.needsFullSync(chatId))
    }

    @Test
    fun `a DM is never read`() = runTest {
        coEvery { metadataDataSource.getChatType(chatId) } returns ChatType.CONTACT_DM

        subject.syncIfNeeded(chatId)

        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
    }

    @Test
    fun `opening a group reads it once`() = runTest {
        coEvery { metadataDataSource.getChatType(chatId) } returns ChatType.GROUP
        coEvery { rosterDataSource.getSyncState(chatId) } returns null
        serve(pages = 1)

        subject.syncIfNeeded(chatId)

        assertEquals(1, requests.size)
    }

    @Test
    fun `refreshing rewrites only the first page`() = runTest {
        serve(pages = 3)

        subject.refreshFirstPage(chatId)

        assertEquals(listOf<List<Byte>?>(null), requests.map { it.token })
        coVerify(exactly = 1) { memberDataSource.upsert(chatId, any()) }
        coVerify(exactly = 0) { rosterDataSource.markSynced(any(), any(), any(), any()) }
    }

    private fun synced(truncated: Boolean, needsResync: Boolean = false) {
        coEvery { rosterDataSource.getSyncState(chatId) } returns
            RosterSyncState(syncedVersion = 7, truncated = truncated, needsResync = needsResync)
    }
}
