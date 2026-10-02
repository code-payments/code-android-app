package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.ChatArchiveDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.internal.RealChatArchiveStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RealChatArchiveStoreTest {

    private val a = ChatId("aabbccdd")
    private val b = ChatId("11223344")
    private val store: ChatArchiveStore = RealChatArchiveStore(ChatArchiveDataSource()) { 1_000L }

    @Before
    fun setUp() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "chat-archive-store-test")
        runBlocking { store.clearAll() }
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    @Test
    fun `archive marks the chat archived and observeArchived reports it`() = runBlocking {
        store.archive(a)
        assertTrue(store.isArchived(a))
        assertFalse(store.isArchived(b))
        assertEquals(setOf(a), store.observeArchived().first())
    }

    @Test
    fun `archiving twice keeps one record`() = runBlocking {
        store.archive(a)
        store.archive(a)
        assertEquals(setOf(a), store.observeArchived().first())
    }

    @Test
    fun `unarchive clears only that chat`() = runBlocking {
        store.archive(a)
        store.archive(b)
        store.unarchive(a)
        assertFalse(store.isArchived(a))
        assertTrue(store.isArchived(b))
        assertEquals(setOf(b), store.observeArchived().first())
    }

    @Test
    fun `clearAll empties the set`() = runBlocking {
        store.archive(a)
        store.archive(b)
        store.clearAll()
        assertFalse(store.isArchived(a))
        assertEquals(emptySet(), store.observeArchived().first())
    }

    @Test
    fun `None archives nothing`() = runBlocking {
        assertEquals(emptySet(), ChatArchiveStore.None.observeArchived().first())
        assertFalse(ChatArchiveStore.None.isArchived(a))
    }
}
