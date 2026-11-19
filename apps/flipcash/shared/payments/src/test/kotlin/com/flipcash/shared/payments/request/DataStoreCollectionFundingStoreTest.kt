package com.flipcash.shared.payments.request

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreCollectionFundingStoreTest {

    private val dir: File = createTempDirectory("collection-funding").toFile()
    private val owner = MutableStateFlow<String?>("aa01")
    private val store = DataStoreCollectionFundingStore(
        dataStore = PreferenceDataStoreFactory.create(produceFile = { File(dir, "funding.preferences_pb") }),
        owners = owner,
        currentOwner = { owner.value },
    )

    private val requestId: ID = List(32) { 4 }
    private val other: ID = List(32) { 5 }
    private val first = FundingLegRecord(Mint(List(32) { 1 }), Mint(List(32) { 2 }), SwapId(List(32) { 3 }), List(32) { 6 })
    private val second = FundingLegRecord(Mint(List(32) { 7 }), Mint(List(32) { 2 }), SwapId(List(32) { 8 }), List(32) { 9 })

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `kept legs read back whole, per payment request`() = runTest {
        store.put(requestId, first)
        store.put(requestId, second)
        store.put(other, second)

        assertEquals(setOf(first, second), store.legs(requestId).toSet())
        assertEquals(listOf(second), store.legs(other))
    }

    @Test
    fun `removing a leg keeps the rest, and clearing drops the request`() = runTest {
        store.put(requestId, first)
        store.put(requestId, second)

        store.remove(requestId, first.swapId)
        assertEquals(listOf(second), store.legs(requestId))

        store.clear(requestId)
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `the last paid currency belongs to the account that paid`() = runTest {
        assertNull(store.lastPaid().first())

        store.setLastPaid(first.from)
        assertEquals(first.from, store.lastPaid().first())

        owner.value = "bb02"
        assertNull(store.lastPaid().first())
        assertTrue(store.legs(requestId).isEmpty())
    }
}
