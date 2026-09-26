package com.getcode.libs.emojis.reactions

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecentReactionsStoreTest {

    // The DataStore file outlives each test in this process, so every test uses owners of its own.
    private var owner: String? = null
    private val store = DataStoreRecentReactionsStore(
        context = ApplicationProvider.getApplicationContext(),
        owner = { owner },
    )

    @Test
    fun `each account keeps its own recents`() = runTest {
        owner = "alice"
        store.record("🎉")
        store.record("🎉")

        owner = "bob"
        assertTrue(store.stats().isEmpty(), "bob sees alice's recents")
        store.record("🍕")

        owner = "alice"
        assertEquals(setOf("🎉"), store.stats().keys)
        assertEquals(2, store.stats().getValue("🎉").count)
    }

    @Test
    fun `nothing is recorded or read without a signed-in account`() = runTest {
        owner = null
        store.record("🎉")
        assertTrue(store.stats().isEmpty())

        owner = "carol"
        assertTrue(store.stats().isEmpty())
    }
}
