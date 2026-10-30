package com.flipcash.shared.transactionhistory

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveHasReceivedMoneyTest {

    private val syncState = MutableStateFlow(FeedSyncState.Unknown)
    private val inRoom = MutableStateFlow(false)

    private fun kotlinx.coroutines.test.TestScope.collect(): List<Boolean?> {
        val values = mutableListOf<Boolean?>()
        resolveHasReceivedMoney(syncState) { inRoom }
            .onEach { values += it }
            .launchIn(backgroundScope)
        return values
    }

    @Test
    fun `absence is unknown until the feed has synced`() =
        runTest(UnconfinedTestDispatcher()) {
            val values = collect()
            assertEquals(listOf<Boolean?>(null), values)
        }

    @Test
    fun `absence after a sync is a definite no`() =
        runTest(UnconfinedTestDispatcher()) {
            val values = collect()
            syncState.value = FeedSyncState.Synced
            assertEquals(listOf(null, false), values)
        }

    @Test
    fun `an unreachable feed ends the wait with a no`() =
        runTest(UnconfinedTestDispatcher()) {
            val values = collect()
            syncState.value = FeedSyncState.Unavailable
            assertEquals(listOf(null, false), values)
        }

    @Test
    fun `a cached entry is proof before the feed has synced`() =
        runTest(UnconfinedTestDispatcher()) {
            inRoom.value = true
            val values = collect()
            assertEquals(listOf<Boolean?>(true), values)
        }

    /** The fetch writes Room, then flips to Synced: the sync must never read as a stale `false`. */
    @Test
    fun `money written before the sync lands never reads as no`() =
        runTest(UnconfinedTestDispatcher()) {
            val values = collect()
            inRoom.value = true
            syncState.value = FeedSyncState.Synced
            assertEquals(listOf(null, true), values)
        }
}

