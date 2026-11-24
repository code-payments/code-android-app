package com.flipcash.services.controllers

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.internal.network.services.FollowArchiveSender
import com.flipcash.services.models.chat.BlobUpdate
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    // region FollowArchive

    private val sent = mutableListOf<Pair<Boolean, Long>>()

    /** Plays the stream attempt starting: Params went out and the sender is live. */
    private fun startAttempt() {
        repository.lastOnStreamStarted!!.invoke { follow, seq -> sent += follow to seq }
    }

    @Test
    fun `a stream that starts while the archive is wanted is told to follow right away`() = runTest {
        stubOwner()
        controller.setFollowArchive(true)
        controller.open(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        startAttempt()

        assertEquals(listOf(true to 1L), sent)
    }

    @Test
    fun `a stream that starts while the archive is not wanted sends nothing`() = runTest {
        stubOwner()
        controller.open(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        startAttempt()

        assertEquals(emptyList(), sent)
    }

    @Test
    fun `each send on a stream takes the next seq`() = runTest {
        stubOwner()
        controller.open(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        startAttempt()

        controller.setFollowArchive(true)
        controller.setFollowArchive(false)
        controller.setFollowArchive(true)

        assertEquals(listOf(true to 1L, false to 2L, true to 3L), sent)
    }

    @Test
    fun `setting the same follow state twice sends once`() = runTest {
        stubOwner()
        controller.open(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        startAttempt()

        controller.setFollowArchive(true)
        controller.setFollowArchive(true)

        assertEquals(listOf(true to 1L), sent)
    }

    @Test
    fun `only the ack for the latest seq is reported`() = runTest {
        stubOwner()
        val acks = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.followArchiveAcks.collect { acks += it }
        }
        controller.open(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        startAttempt()
        controller.setFollowArchive(true) // seq 1
        controller.setFollowArchive(false) // seq 2

        // The ack for ON arrives after OFF was sent: it describes a replaced request.
        repository.lastOnFollowArchiveAck!!.invoke(1L)
        assertEquals(emptyList(), acks)

        repository.lastOnFollowArchiveAck!!.invoke(2L)
        assertEquals(listOf(false), acks)
    }

    @Test
    fun `a reconnect starts seq over and follows again after Params`() = runTest {
        stubOwner()
        val acks = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.followArchiveAcks.collect { acks += it }
        }
        controller.open(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        startAttempt()
        controller.setFollowArchive(true) // seq 1
        controller.setFollowArchive(false) // seq 2
        controller.setFollowArchive(true) // seq 3

        // The bidi loop reconnects: a new attempt, a new server-side stream.
        startAttempt()

        assertEquals(true to 1L, sent.last())
        // seq 3 belonged to the dead stream; seq 1 is the live one's.
        repository.lastOnFollowArchiveAck!!.invoke(3L)
        assertEquals(emptyList(), acks)
        repository.lastOnFollowArchiveAck!!.invoke(1L)
        assertEquals(listOf(true), acks)
    }

    @Test
    fun `wanting the archive survives close and reopen`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        controller.open(scope)
        startAttempt()
        controller.setFollowArchive(true)

        controller.close()
        controller.open(scope)
        startAttempt()

        assertEquals(listOf(true to 1L, true to 1L), sent)
    }

    @Test
    fun `turning follow off with no live stream sends nothing and sticks`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        controller.open(scope)
        startAttempt()
        controller.setFollowArchive(true)
        controller.close()

        controller.setFollowArchive(false)
        controller.open(scope)
        startAttempt()

        assertEquals(listOf(true to 1L), sent)
    }

    @Test
    fun `callbacks from a stream that was replaced are ignored`() = runTest {
        stubOwner()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val acks = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.followArchiveAcks.collect { acks += it }
        }
        controller.setFollowArchive(true)
        controller.open(scope)
        val oldStarted = repository.lastOnStreamStarted!!
        val oldAck = repository.lastOnFollowArchiveAck!!
        controller.close()
        controller.open(scope)

        oldStarted.invoke { follow, seq -> sent += follow to seq }
        oldAck.invoke(1L)

        assertEquals(emptyList(), sent)
        assertEquals(emptyList(), acks)
    }

    // endregion
}

private class FakeEventStreamingRepository : EventStreamingRepository {
    private val lock = Any()
    var openCount = 0
        private set
    val opened: Boolean get() = openCount > 0
    var lastStreamRef: EventStreamReference? = null
        private set
    var lastOnStreamStarted: ((FollowArchiveSender) -> Unit)? = null
        private set
    var lastOnFollowArchiveAck: ((Long) -> Unit)? = null
        private set
    var lastOnError: ((Throwable) -> Unit)? = null
        private set

    override fun openEventStream(
        scope: CoroutineScope,
        owner: Ed25519.KeyPair,
        onEvent: (ChatUpdate) -> Unit,
        onBlobUpdate: (BlobUpdate) -> Unit,
        onError: (Throwable) -> Unit,
        onStreamStarted: (sendFollowArchive: FollowArchiveSender) -> Unit,
        onFollowArchiveAck: (seq: Long) -> Unit,
    ): EventStreamReference {
        lastOnStreamStarted = onStreamStarted
        lastOnFollowArchiveAck = onFollowArchiveAck
        lastOnError = onError
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
