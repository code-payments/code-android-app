package com.flipcash.app.internal.startup

import com.getcode.utils.TraceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForwardUserIdTest {

    @AfterTest
    fun tearDown() {
        TraceManager.setOnUserIdChanged(null)
        TraceManager.userId = null
    }

    @Test
    fun `does not call setUser on the thread that changed the id`() = runTest {
        val forwarded = mutableListOf<String?>()
        forwardUserIdOffThread(dispatcher = StandardTestDispatcher(testScheduler)) { forwarded += it }

        TraceManager.userId = "user-1"

        assertTrue(forwarded.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf<String?>("user-1"), forwarded)
    }

    @Test
    fun `forwards changes in the order they were made`() = runTest {
        val forwarded = mutableListOf<String?>()
        forwardUserIdOffThread(dispatcher = StandardTestDispatcher(testScheduler)) { forwarded += it }

        TraceManager.userId = "user-1"
        TraceManager.userId = null
        TraceManager.userId = "user-2"
        advanceUntilIdle()

        assertEquals(listOf("user-1", null, "user-2"), forwarded)
    }
}
