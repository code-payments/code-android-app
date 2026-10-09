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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

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
        val cutoffs = mutableListOf<Long>()

        /** Rows the table itself drops for age when it is read, as `observeAll` reports them. */
        var droppedByAge: List<LinkPreviewRecord> = emptyList()
        val opened = MutableStateFlow<List<LinkPreviewRecord>>(emptyList())

        fun reopen() {
            opened.value = rows.values.toList()
        }

        val source: LinkPreviewDataSource = mock {
            on { observeAll(any(), any()) } doAnswer {
                cutoffs += it.getArgument<() -> Long>(0)()
                if (droppedByAge.isNotEmpty()) it.getArgument<(List<LinkPreviewRecord>) -> Unit>(1)(droppedByAge)
                opened
            }
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

    private var clock = 100.days.inWholeMilliseconds

    private class RecordingImages : WebImageStore {
        val removed = mutableListOf<String>()
        override fun remove(url: String) {
            removed += url
        }
    }

    private val images = RecordingImages()

    private fun TestScope.memory(table: FakeTable, viewer: List<Byte>? = myId) = PersistedLinkCardMemory(
        store = table.source,
        userManager = mock<UserManager> { on { accountId } doReturn viewer },
        resources = mock<ResourceHelper>(),
        images = images,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        now = { clock },
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
    fun `an unchanged answer rewrites its row once a day, so a card in use does not expire`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putGroup(chatId, group)
        advanceUntilIdle()

        clock += 23.hours.inWholeMilliseconds
        memory.putGroup(chatId, group)
        advanceUntilIdle()
        assertEquals(1, table.writes.size)

        clock += 2.hours.inWholeMilliseconds
        memory.putGroup(chatId, group)
        advanceUntilIdle()
        assertEquals(2, table.writes.size)
        assertEquals(clock, table.rows.values.single().updatedAt)
    }

    @Test
    fun `rows not written in 30 days are dropped when the table is read`() = runTest {
        val table = FakeTable()
        table.reopen()
        memory(table)
        advanceUntilIdle()

        assertEquals(listOf(clock - 30.days.inWholeMilliseconds), table.cutoffs)
    }

    @Test
    fun `a group the server no longer has is deleted from disk`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putGroup(chatId, group)
        advanceUntilIdle()

        memory.removeGroup(chatId)
        advanceUntilIdle()

        assertNull(memory.groups[chatId])
        assertTrue(table.rows.isEmpty())
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

    private val webResolved = LinkCard.Web.State.Resolved(
        title = "Example",
        description = null,
        imageUrl = "https://example.com/i.png",
        host = "example.com",
    )
    private val webKey = "https://example.com/a"

    @Test
    fun `a web answer round-trips under a web key with the agreed fields`() = runTest {
        val table = FakeTable()
        memory(table).apply {
            putWeb(webKey, webResolved)
            putWeb("https://example.com/none", LinkCard.Web.State.None)
        }
        advanceUntilIdle()
        assertEquals(
            """{"title":"Example","description":null,"imageUrl":"https://example.com/i.png","host":"example.com"}""",
            table.rows["web:$webKey"]!!.json,
        )
        assertEquals(
            """{"title":null,"description":null,"imageUrl":null,"host":null}""",
            table.rows["web:https://example.com/none"]!!.json,
        )

        table.reopen()
        val restarted = memory(table)
        advanceUntilIdle()
        restarted.awaitLoaded()
        assertEquals(webResolved, restarted.webs[webKey])
        assertEquals(LinkCard.Web.State.None, restarted.webs["https://example.com/none"])
    }

    private suspend fun TestScope.webReloaded(state: LinkCard.Web.State, ageMs: Long): LinkCardMemory {
        val table = FakeTable()
        memory(table).putWeb(webKey, state)
        advanceUntilIdle()
        clock += ageMs
        table.reopen()
        val restarted = memory(table)
        advanceUntilIdle()
        restarted.awaitLoaded()
        return restarted
    }

    @Test
    fun `a resolved web row reads as absent only once it is older than the resolved TTL`() = runTest {
        val ttl = 168.hours.inWholeMilliseconds
        assertEquals(webResolved, webReloaded(webResolved, ttl).webs[webKey])
        assertNull(webReloaded(webResolved, ttl + 1).webs[webKey])
    }

    @Test
    fun `a none web row reads as absent only once it is older than the empty TTL`() = runTest {
        val ttl = 24.hours.inWholeMilliseconds
        assertEquals(LinkCard.Web.State.None, webReloaded(LinkCard.Web.State.None, ttl).webs[webKey])
        assertNull(webReloaded(LinkCard.Web.State.None, ttl + 1).webs[webKey])
    }

    @Test
    fun `a none web row is not kept for the resolved TTL`() = runTest {
        assertNull(webReloaded(LinkCard.Web.State.None, 25.hours.inWholeMilliseconds).webs[webKey])
        assertEquals(webResolved, webReloaded(webResolved, 25.hours.inWholeMilliseconds).webs[webKey])
    }

    @Test
    fun `an unchanged web answer is not written again within a day`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.awaitLoaded()
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()
        assertEquals(1, table.writes.count { it == "web:$webKey" })
    }

    @Test
    fun `a failed web lookup through the resolver leaves nothing on disk`() = runTest {
        val table = FakeTable()
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { Result.failure(IllegalStateException("unused")) },
            tokenMetadata = { Result.failure(IllegalStateException("unused")) },
            group = { Result.failure(IllegalStateException("unused")) },
            user = { Result.failure(IllegalStateException("unused")) },
            web = { Result.failure(java.io.IOException("offline")) },
            memory = memory(table),
        )
        resolver.resolve(LinkCard.Web(url = webKey, start = 0, end = 21))
        advanceUntilIdle()
        assertTrue(table.writes.isEmpty())
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun `an entry loaded from a 23 hour old none row expires an hour and a millisecond later`() = runTest {
        val loaded = webReloaded(LinkCard.Web.State.None, 23.hours.inWholeMilliseconds)
        assertEquals(LinkCard.Web.State.None, loaded.webs[webKey])
        clock += 1.hours.inWholeMilliseconds
        assertEquals(LinkCard.Web.State.None, loaded.webs[webKey])
        clock += 1
        assertNull(loaded.webs[webKey])
    }

    private fun webRow(key: String, state: LinkCard.Web.State.Resolved, updatedAt: Long) = LinkPreviewRecord(
        key = "web:$key",
        json = """{"title":"${state.title}","description":null,"imageUrl":${state.imageUrl?.let { "\"$it\"" }},"host":"${state.host}"}""",
        updatedAt = updatedAt,
    )

    @Test
    fun `a stale resolved row takes its picture with it when the table is read`() = runTest {
        val table = FakeTable()
        table.rows["web:$webKey"] = webRow(webKey, webResolved, updatedAt = clock - 169.hours.inWholeMilliseconds)
        table.reopen()

        memory(table)
        advanceUntilIdle()

        assertTrue(table.rows.isEmpty())
        assertEquals(listOf(webResolved.imageUrl), images.removed)
    }

    @Test
    fun `a fresh resolved row keeps its picture`() = runTest {
        val table = FakeTable()
        table.rows["web:$webKey"] = webRow(webKey, webResolved, updatedAt = clock - 167.hours.inWholeMilliseconds)
        table.reopen()

        memory(table)
        advanceUntilIdle()

        assertTrue(images.removed.isEmpty())
    }

    @Test
    fun `a row the table drops for age takes its picture with it`() = runTest {
        val table = FakeTable()
        table.droppedByAge = listOf(webRow(webKey, webResolved, updatedAt = clock - 31.days.inWholeMilliseconds))
        table.reopen()

        memory(table)
        advanceUntilIdle()

        assertEquals(listOf(webResolved.imageUrl), images.removed)
    }

    @Test
    fun `a group row dropped for age removes no picture`() = runTest {
        val table = FakeTable()
        table.droppedByAge = listOf(LinkPreviewRecord("group:ab", "{}", updatedAt = 0))
        table.reopen()

        memory(table)
        advanceUntilIdle()

        assertTrue(images.removed.isEmpty())
    }

    @Test
    fun `an answer replaced by none removes the picture of the one it replaced`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()

        memory.putWeb(webKey, LinkCard.Web.State.None)
        advanceUntilIdle()

        assertEquals(listOf(webResolved.imageUrl), images.removed)
    }

    @Test
    fun `an answer replaced with a different picture removes the old one only`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()

        memory.putWeb(webKey, webResolved.copy(imageUrl = "https://example.com/j.png"))
        advanceUntilIdle()

        assertEquals(listOf(webResolved.imageUrl), images.removed)
    }

    @Test
    fun `an answer put again with the same picture removes nothing`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved)
        memory.putWeb(webKey, webResolved.copy(title = "Retitled"))
        advanceUntilIdle()

        assertTrue(images.removed.isEmpty())
    }

    @Test
    fun `an answer with no picture replaced by one removes nothing`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved.copy(imageUrl = null))
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()

        assertTrue(images.removed.isEmpty())
    }

    @Test
    fun `a database closing removes the pictures of the answers it held`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()
        table.reopen()
        advanceUntilIdle()
        assertTrue(images.removed.isEmpty())

        // Logged out: no database, so no rows, so no pictures to read.
        table.opened.value = emptyList()
        advanceUntilIdle()

        assertEquals(listOf(webResolved.imageUrl), images.removed)
    }

    @Test
    fun `a reload that still has the row keeps its picture`() = runTest {
        val table = FakeTable()
        val memory = memory(table)
        advanceUntilIdle()
        memory.putWeb(webKey, webResolved)
        advanceUntilIdle()

        table.reopen()
        advanceUntilIdle()

        assertTrue(images.removed.isEmpty())
    }
}
