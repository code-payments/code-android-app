package com.flipcash.shared.chat

import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The heartbeat is the only thing that reopens a stream the reconnect loop has stopped retrying —
 * a status outside its retryable set, a ping timeout, or its attempts running out. Polling for
 * that on a fixed interval costs up to the whole interval in missed messages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventStreamHeartbeatTest {

    private class Harness {
        val failures = Channel<Unit>(capacity = Channel.CONFLATED)

        val controller = mockk<EventStreamingController>(relaxed = true).also { controller ->
            every { controller.streamFailures } returns failures.receiveAsFlow()
            every { controller.chatUpdates } returns emptyFlow()
            every { controller.blobUpdates } returns emptyFlow()
            // Nothing is connected for the whole test: every wake finds the stream down.
            every { controller.isStreamActive } returns false
            every { controller.isConnected } returns false
        }

        val delegate = EventStreamDelegate(
            eventStreamingController = controller,
            messagingController = mockk(relaxed = true),
            metadataDataSource = mockk(relaxed = true),
            messageDataSource = mockk(relaxed = true),
            memberDataSource = mockk(relaxed = true),
            tokenCoordinator = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
            stateHolder = ChatStateHolder(),
            analytics = mockk(relaxed = true),
            exchange = mockk(relaxed = true),
        )

        fun start(scope: TestScope) {
            delegate.initialize(scope.backgroundScope)
            delegate.startHeartbeat { }
            scope.runCurrent()
        }
    }

    @Test
    fun `a stream that gives up is reopened without waiting out the tick`() = runTest {
        val harness = Harness()
        harness.start(this)

        harness.failures.trySend(Unit)
        advanceTimeBy(100.milliseconds)
        runCurrent()

        verify(exactly = 1) { harness.controller.open(any()) }
    }

    @Test
    fun `a stream failing as soon as it opens is not reopened in a hot loop`() = runTest {
        val harness = Harness()
        harness.start(this)

        harness.failures.trySend(Unit)
        advanceTimeBy(100.milliseconds)
        runCurrent()

        // The reopened stream fails immediately too.
        harness.failures.trySend(Unit)
        advanceTimeBy(500.milliseconds)
        runCurrent()
        verify(exactly = 1) { harness.controller.open(any()) }

        advanceTimeBy(1.seconds)
        runCurrent()
        verify(exactly = 2) { harness.controller.open(any()) }
    }

    @Test
    fun `a dead stream is still reopened at the tick`() = runTest {
        val harness = Harness()
        harness.start(this)

        advanceTimeBy(31.seconds)
        runCurrent()

        verify(exactly = 1) { harness.controller.open(any()) }
    }
}
