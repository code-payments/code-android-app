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
import com.flipcash.shared.chat.internal.RosterReconcileScheduler
import com.flipcash.shared.chat.internal.RosterSync
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A group's roster is read in full once, then kept whole by catching up from the top of the roster
 * to the watermark. Leaves the top cannot show go to a full read in WorkManager.
 */
class RosterSyncTest {

    private val chatId = ChatId("c0ffee")

    private val controller = mockk<ChatController>()
    private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val rosterDataSource = mockk<ChatRosterDataSource>(relaxed = true) {
        every { isAvailable } returns true
    }
    private val scheduler = mockk<RosterReconcileScheduler>(relaxed = true)

    private val subject = RosterSync(
        chatController = controller,
        metadataDataSource = metadataDataSource,
        memberDataSource = memberDataSource,
        rosterDataSource = rosterDataSource,
        reconcileScheduler = scheduler,
        dispatchers = TestDispatchers(TestCoroutineScheduler()),
    )

    private val requests = mutableListOf<QueryOptions>()

    /** Member [n], who joined at roster version [n]. */
    private fun member(n: Int) = ChatMember(
        userId = listOf(n.toByte(), (n shr 8).toByte()),
        userProfile = UserProfile.Empty.copy(displayName = "m$n"),
        pointers = emptyList(),
        version = n.toLong(),
    )

    /**
     * Serves a roster of members joined at versions [total] down to 1, most recent first, [perPage]
     * to a page. Page i's token is `[i]`; each page reports [versionOf] of its index.
     */
    private fun serve(
        total: Int,
        perPage: Int = 2,
        versionOf: (Int) -> Long = { total.toLong() },
        memberCount: Long = total.toLong(),
    ) {
        val options = slot<QueryOptions>()
        coEvery { controller.getRoster(chatId, capture(options)) } answers {
            requests += options.captured
            val index = options.captured.token?.single()?.toInt() ?: 0
            val from = total - index * perPage
            val members = (from downTo maxOf(1, from - perPage + 1)).map(::member)
            val hasMore = from - perPage > 0
            Result.success(
                RosterPage(
                    members = members,
                    rosterSummary = RosterSummary(memberCount = memberCount, version = versionOf(index)),
                    pagingToken = if (hasMore) listOf((index + 1).toByte()) else null,
                    hasMore = hasMore,
                )
            )
        }
    }

    private fun state(
        watermark: Long = 0,
        fullySynced: Boolean = true,
        truncated: Boolean = false,
        reconcilePending: Boolean = false,
    ) = RosterSyncState(watermark, fullySynced, truncated, reconcilePending)

    // Full read

    @Test
    fun `a full read runs until has_more is false`() = runTest {
        serve(total = 6)

        assertTrue(subject.fullSync(chatId))

        assertEquals(3, requests.size)
        assertTrue(requests.all { it.limit == RosterSync.PAGE_SIZE })
        assertEquals(listOf(null, listOf<Byte>(1), listOf<Byte>(2)), requests.map { it.token })
        coVerify { memberDataSource.reconcile(chatId, (1..6).map { member(it).userId }.toSet(), readVersion = 6) }
        coVerify { rosterDataSource.markFullySynced(chatId, watermark = 6, truncated = false) }
    }

    @Test
    fun `a full read stops at the page cap and drops no one`() = runTest {
        serve(total = (RosterSync.MAX_PAGES + 5) * 2)

        assertTrue(subject.fullSync(chatId))

        assertEquals(RosterSync.MAX_PAGES, requests.size)
        coVerify(exactly = 0) { memberDataSource.reconcile(any(), any(), any()) }
        coVerify { rosterDataSource.markFullySynced(chatId, watermark = any(), truncated = true) }
    }

    @Test
    fun `a full read reconciles against its lowest page version`() = runTest {
        // Later pages trail: each is only a promise about the roster as of its own version.
        serve(total = 4, versionOf = { 9L - it })

        subject.fullSync(chatId)

        coVerify { memberDataSource.reconcile(chatId, any(), readVersion = 8) }
        coVerify { rosterDataSource.markFullySynced(chatId, watermark = 8, truncated = false) }
    }

    @Test
    fun `a failed page records nothing and asks to be retried`() = runTest {
        serve(total = 6)
        coEvery { controller.getRoster(chatId, match { it.token == listOf<Byte>(1) }) } returns
            Result.failure(Throwable("offline"))

        assertFalse(subject.fullSync(chatId))

        coVerify(exactly = 0) { rosterDataSource.markFullySynced(any(), any(), any()) }
        coVerify(exactly = 0) { memberDataSource.reconcile(any(), any(), any()) }
    }

    @Test
    fun `a full read with no database open asks to be retried`() = runTest {
        every { rosterDataSource.isAvailable } returns false

        assertFalse(subject.fullSync(chatId))

        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
    }

    // Catch-up

    @Test
    fun `missed joins come back in one page and the read stops at the watermark`() = runTest {
        // Held through version 10; members 13, 12 and 11 joined while the stream was away.
        serve(total = 13, perPage = RosterSync.PAGE_SIZE)
        coEvery { memberDataSource.countMembers(chatId) } returns 13

        subject.catchUp(chatId, state(watermark = 10))

        assertEquals(1, requests.size)
        coVerify { memberDataSource.upsert(chatId, listOf(member(13), member(12), member(11))) }
    }

    @Test
    fun `a catch-up reads further pages only while every member is newer`() = runTest {
        serve(total = 13, perPage = 2)
        coEvery { memberDataSource.countMembers(chatId) } returns 13

        subject.catchUp(chatId, state(watermark = 10))

        // 13,12 | 11,10: the second page reaches the watermark.
        assertEquals(2, requests.size)
        coVerify { memberDataSource.upsert(chatId, listOf(member(11))) }
    }

    @Test
    fun `an equal count moves the watermark to the page's version, not the stream's`() = runTest {
        serve(total = 13, perPage = RosterSync.PAGE_SIZE, versionOf = { 13 })
        coEvery { metadataDataSource.getRosterVersion(chatId) } returns 15
        coEvery { memberDataSource.countMembers(chatId) } returns 13

        subject.catchUp(chatId, state(watermark = 10))

        coVerify { rosterDataSource.setWatermark(chatId, 13) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `more held than the count marks a reconcile and schedules it once`() = runTest {
        serve(total = 13, perPage = RosterSync.PAGE_SIZE, memberCount = 12)
        coEvery { memberDataSource.countMembers(chatId) } returns 13

        subject.catchUp(chatId, state(watermark = 10))

        coVerify { rosterDataSource.markReconcilePending(chatId) }
        verify(exactly = 1) { scheduler.schedule(chatId) }
        coVerify(exactly = 0) { rosterDataSource.setWatermark(any(), any()) }
    }

    @Test
    fun `fewer held than the count waits for the join on the stream`() = runTest {
        serve(total = 13, perPage = RosterSync.PAGE_SIZE, memberCount = 14)
        coEvery { memberDataSource.countMembers(chatId) } returns 13

        subject.catchUp(chatId, state(watermark = 10))

        coVerify(exactly = 0) { rosterDataSource.setWatermark(any(), any()) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `a roster cut off at the cap still moves its watermark`() = runTest {
        serve(total = 13, perPage = RosterSync.PAGE_SIZE, memberCount = 5_000)
        coEvery { memberDataSource.countMembers(chatId) } returns 2_000

        subject.catchUp(chatId, state(watermark = 10, truncated = true))

        coVerify { rosterDataSource.setWatermark(chatId, 13) }
    }

    @Test
    fun `more missed joins than the cap reads hands over to a full read`() = runTest {
        serve(total = (RosterSync.MAX_PAGES + 2) * 2, perPage = 2)

        subject.catchUp(chatId, state(watermark = 1))

        assertEquals(RosterSync.MAX_PAGES, requests.size)
        coVerify { rosterDataSource.markReconcilePending(chatId) }
        verify(exactly = 1) { scheduler.schedule(chatId) }
    }

    // Triggers

    @Test
    fun `a DM is never read`() = runTest {
        coEvery { metadataDataSource.getChatType(chatId) } returns ChatType.CONTACT_DM

        subject.onOpen(chatId)

        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `a small group never read is read on open`() = runTest {
        group(memberCount = 40, syncState = null)
        serve(total = 40, perPage = RosterSync.PAGE_SIZE)

        subject.onOpen(chatId)

        assertEquals(1, requests.size)
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `a large group never read is handed to WorkManager`() = runTest {
        group(memberCount = 250, syncState = state(fullySynced = false))

        subject.onOpen(chatId)

        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
        verify(exactly = 1) { scheduler.schedule(chatId) }
    }

    @Test
    fun `a pending reconcile is re-queued on open`() = runTest {
        group(memberCount = 40, syncState = state(watermark = 10, reconcilePending = true))

        subject.onOpen(chatId)

        verify(exactly = 1) { scheduler.schedule(chatId) }
        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
    }

    @Test
    fun `a group whose roster moved past the watermark catches up on open`() = runTest {
        group(memberCount = 13, syncState = state(watermark = 10))
        coEvery { metadataDataSource.getRosterVersion(chatId) } returns 13
        serve(total = 13, perPage = RosterSync.PAGE_SIZE)

        subject.onOpen(chatId)

        assertEquals(1, requests.size)
    }

    @Test
    fun `a group at its watermark is not read`() = runTest {
        group(memberCount = 13, syncState = state(watermark = 13))
        coEvery { metadataDataSource.getRosterVersion(chatId) } returns 13

        subject.onOpen(chatId)

        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
    }

    @Test
    fun `a gap in a group never read waits for its open`() = runTest {
        coEvery { rosterDataSource.getSyncState(chatId) } returns null

        subject.onGap(chatId)

        coVerify(exactly = 0) { controller.getRoster(any(), any()) }
    }

    @Test
    fun `a gap in a group read in full catches up`() = runTest {
        coEvery { rosterDataSource.getSyncState(chatId) } returns state(watermark = 10)
        serve(total = 13, perPage = RosterSync.PAGE_SIZE)

        subject.onGap(chatId)

        assertEquals(1, requests.size)
    }

    @Test
    fun `refreshing rewrites only the first page`() = runTest {
        serve(total = 6)

        subject.refreshFirstPage(chatId)

        assertEquals(listOf<List<Byte>?>(null), requests.map { it.token })
        coVerify(exactly = 1) { memberDataSource.upsert(chatId, any()) }
        coVerify(exactly = 0) { rosterDataSource.markFullySynced(any(), any(), any()) }
    }

    private fun group(memberCount: Long, syncState: RosterSyncState?) {
        coEvery { metadataDataSource.getChatType(chatId) } returns ChatType.GROUP
        coEvery { metadataDataSource.getMemberCount(chatId) } returns memberCount
        coEvery { rosterDataSource.getSyncState(chatId) } returns syncState
    }
}
