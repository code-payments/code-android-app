package com.flipcash.services.controllers

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.models.chat.BlobUpdate
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class EventStreamingControllerTest {

    private val repository = FakeEventStreamingRepository()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = EventStreamingController(repository, userManager)

    private fun stubOwner() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
    }

    @Test
    fun `open does nothing when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        controller.open(scope)

        // No stream opened
        assert(!repository.opened)
    }

    @Test
    fun `open creates stream when account cluster exists`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        controller.open(scope)

        assert(repository.opened)
    }

    @Test
    fun `close destroys the stream reference`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        controller.open(scope)

        controller.close()

        assertNotNull(repository.lastStreamRef)
        verify { repository.lastStreamRef!!.destroy() }
    }

    @Test
    fun `chatUpdates flow is accessible`() {
        assertNotNull(controller.chatUpdates)
    }

    @Test
    fun `concurrent open calls create only one stream`() {
        stubOwner()
        val scope = CoroutineScope(Dispatchers.Default)
        val threadCount = 32
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        repeat(threadCount) {
            thread {
                startLatch.await()
                controller.open(scope)
                doneLatch.countDown()
            }
        }

        // Release all threads at once to maximize the check-then-act race.
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)

        // Exactly one gRPC stream must be opened; opening two produces the
        // server-side "ABORTED: stream already exists" duel.
        assertEquals(1, repository.openCount)
    }
}

private class FakeEventStreamingRepository : EventStreamingRepository {
    private val lock = Any()
    var openCount = 0
        private set
    val opened: Boolean get() = openCount > 0
    var lastStreamRef: EventStreamReference? = null
        private set

    override fun openEventStream(
        scope: CoroutineScope,
        owner: Ed25519.KeyPair,
        onEvent: (ChatUpdate) -> Unit,
        onBlobUpdate: (BlobUpdate) -> Unit,
        onError: (Throwable) -> Unit,
    ): EventStreamReference {
        // Widen the controller's check-then-act window so an unsynchronized
        // open() lets multiple threads reach here concurrently. Mock creation
        // and counting are serialized here so the assertion measures the
        // controller's concurrency, not mockk's.
        Thread.sleep(20)
        return synchronized(lock) {
            openCount++
            val ref = mockk<EventStreamReference>(relaxed = true)
            lastStreamRef = ref
            ref
        }
    }
}
