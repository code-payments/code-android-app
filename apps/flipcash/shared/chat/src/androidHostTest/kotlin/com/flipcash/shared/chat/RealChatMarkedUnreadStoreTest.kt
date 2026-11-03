package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.init
import com.flipcash.app.persistence.sources.ChatMarkedUnreadDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.internal.RealChatMarkedUnreadStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RealChatMarkedUnreadStoreTest {

    private val a = ChatId("aabbccdd")
    private val b = ChatId("11223344")
    private val c = ChatId("55667788")
    private val store: ChatMarkedUnreadStore = RealChatMarkedUnreadStore(ChatMarkedUnreadDataSource()) { 1_000L }

    private fun signIn(entropy: String) =
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), entropy)

    @Before
    fun setUp() {
        signIn(ACCOUNT_X)
        runBlocking { store.clearAll() }
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    @Test
    fun `mark adds the chat and observeMarked reports it`() = runBlocking {
        store.mark(a)
        assertEquals(setOf(a), store.observeMarked().first())
    }

    @Test
    fun `clear removes only that chat`() = runBlocking {
        store.mark(a)
        store.mark(b)
        store.clear(a)
        assertEquals(setOf(b), store.observeMarked().first())
    }

    @Test
    fun `clear by set removes only those chats`() = runBlocking {
        listOf(a, b, c).forEach { store.mark(it) }
        store.clear(listOf(a, c))
        assertEquals(setOf(b), store.observeMarked().first())
    }

    // Test case 8: marks survive logout, per account. The database file is per account, so
    // logging out closes X's marks away from Y and signing back in as X brings them back.
    @Test
    fun `marks survive logout per account`() = runBlocking {
        store.mark(a)
        store.mark(b)

        FlipcashDatabase.closeDb()
        signIn(ACCOUNT_Y)
        assertEquals(emptySet(), store.observeMarked().first())

        FlipcashDatabase.closeDb()
        signIn(ACCOUNT_X)
        assertEquals(setOf(a, b), store.observeMarked().first())
    }

    // Erasing the cache (clearCache) wipes the marks.
    @Test
    fun `clearAll empties the marked set`() = runBlocking {
        store.mark(a)
        store.mark(b)
        store.clearAll()
        assertEquals(emptySet(), store.observeMarked().first())
    }

    @Test
    fun `no database means nothing marked`() = runBlocking {
        FlipcashDatabase.closeDb()
        assertEquals(emptySet(), store.observeMarked().first())
    }

    private companion object {
        // The database file is named from the first 6 bytes of the entropy, so these differ there.
        const val ACCOUNT_X = "xxxxxx-chat-marked-unread-store-test"
        const val ACCOUNT_Y = "yyyyyy-chat-marked-unread-store-test"
    }
}
