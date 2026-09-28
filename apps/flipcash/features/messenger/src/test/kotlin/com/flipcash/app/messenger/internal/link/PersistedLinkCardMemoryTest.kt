package com.flipcash.app.messenger.internal.link

import com.flipcash.app.persistence.sources.LinkPreviewDataSource
import com.flipcash.app.persistence.sources.LinkPreviewRecord
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.bytes
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a cold start sees: an answer stored by one [PersistedLinkCardMemory] is what a fresh one
 * loads, so the first visit after a restart draws the card at its resolved size.
 */
class PersistedLinkCardMemoryTest {

    private val myId = UUID.fromString("6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162").bytes
    private val theirId = UUID.fromString("2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b").bytes
    private val chatId = ChatId(UUID.fromString("0d8e3c7a-5b2f-4a91-8e6d-3c1b0a9f8e7d").bytes)

    private val group = LinkCard.GroupInvite.State.Resolved(
        title = "All Jeffy Holders",
        picture = null,
        memberCount = 42,
        requirement = LinkCard.GroupInvite.Requirement(amount = "$100", currencyName = "Jeffy", staffOnly = false),
    )

    private fun profile(userId: List<Byte> = theirId) = UserProfile(
        displayName = "Satoshi",
        socialAccounts = emptyList(),
        phoneNumber = VerifiableContactMethod("+15555550100", verified = true),
        email = VerifiableContactMethod("s@example.com", verified = true),
        userId = userId,
        username = "satoshi",
    )

    /** A `link_previews` table in a map: what was written is what the next database open reads. */
    private class FakeTable {
        val rows = linkedMapOf<String, LinkPreviewRecord>()
        val writes = mutableListOf<String>()
        val opened = MutableStateFlow<List<LinkPreviewRecord>>(emptyList())

        fun reopen() {
            opened.value = rows.values.toList()
        }

        val source: LinkPreviewDataSource = mock {
            on { observeAll() } doReturn opened
            onBlocking { upsert(any()) } doSuspendableAnswer {
                val record = it.getArgument<LinkPreviewRecord>(0)
                rows[record.key] = record
                writes += record.key
            }
            onBlocking { delete(any()) } doSuspendableAnswer {
                rows.remove(it.getArgument<String>(0))
                Unit
            }
        }
    }

    private fun TestScope.memory(table: FakeTable, viewer: List<Byte>? = myId) = PersistedLinkCardMemory(
        store = table.source,
        userManager = mock<UserManager> { on { accountId } doReturn viewer },
        resources = mock<ResourceHelper>(),
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `a group resolved before a restart loads resolved after it`() = runTest {
        val table = FakeTable()
        memory(table).putGroup(chatId, group)
        advanceUntilIdle()

        table.reopen()
        val restarted = memory(table)
        advanceUntilIdle()
        restarted.awaitLoaded()

        assertEquals(group, restarted.groups[chatId])
    }

    @Test
    fun `a person loads by id and by handle, judged against whoever is signed in now`() = runTest {
        val table = FakeTable()
        val before = memory(table, viewer = myId)
        val byId = LinkCard.User.Identity.ById(theirId)
        val byName = LinkCard.User.Identity.ByUsername("satoshi")
        val state = userCardState(profile(), viewerId = myId, joined = { "" })!!
        before.putUser(byId, state)
        before.putUser(byName, state)
        advanceUntilIdle()

        table.reopen()
        // The same device, now signed in as the person the link names.
        val restarted = memory(table, viewer = theirId)
        advanceUntilIdle()

        val loaded = restarted.users[byId]!!
        assertEquals("Satoshi", loaded.name)
        assertTrue(loaded.isOwn)
        assertEquals(loaded, restarted.users[byName])
    }

    @Test
    fun `a person's contact methods are not written to disk`() = runTest {
        val table = FakeTable()
        val identity = LinkCard.User.Identity.ById(theirId)
        memory(table).putUser(identity, userCardState(profile(), viewerId = myId, joined = { "" })!!)
        advanceUntilIdle()

        val json = table.rows.values.single().json
        assertFalse("+15555550100" in json)
        assertFalse("s@example.com" in json)
    }

    @Test
    fun `an unchanged answer is not written again`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        memory.putGroup(chatId, group)
        memory.putGroup(chatId, group.copy())
        advanceUntilIdle()

        assertEquals(1, table.writes.size)
    }

    @Test
    fun `a row that no longer decodes is dropped rather than loaded`() = runTest {
        val table = FakeTable()
        val key = "group:" + chatId.bytes.joinToString("") { "%02x".format(it) }
        table.rows[key] = LinkPreviewRecord(key, """{"shape":"from another build"}""", 0)
        table.reopen()

        val memory = memory(table)
        advanceUntilIdle()

        assertNull(memory.groups[chatId])
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun `a database closing takes its answers with it`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        memory.putGroup(chatId, group)
        advanceUntilIdle()
        table.reopen()
        advanceUntilIdle()

        // Logged out: no database, nothing to read.
        table.opened.value = emptyList()
        advanceUntilIdle()

        assertNull(memory.groups[chatId])
    }
}
