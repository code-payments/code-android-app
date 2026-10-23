package com.flipcash.app.core.updater

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `SessionController.onAppInForeground` is invoked both by the transition into `AuthState.Ready`
 * and by `ON_RESUME`, and at login those arrive together. A poller that restarted on every call
 * cancelled the fetch the first call had started and re-served its start delay, so the second
 * event pushed the first fetch of a fresh session further away.
 *
 * Time is advanced in bounded steps rather than with `advanceUntilIdle`, which does not run
 * `backgroundScope`'s tasks — and an unbounded advance would not terminate against a poll loop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkUpdaterTest {

    private class CountingUpdater : NetworkUpdater() {
        var updates = 0
            private set

        override suspend fun doUpdate() {
            updates++
        }
    }

    @Test
    fun `a repeated poll leaves the running loop alone`() = runTest {
        val updater = CountingUpdater()

        updater.poll(scope = backgroundScope, frequency = 20.seconds)
        advanceTimeBy(1.milliseconds)
        assertEquals(1, updater.updates, "the first poll fetches immediately")

        updater.poll(scope = backgroundScope, frequency = 20.seconds)
        advanceTimeBy(1.seconds)
        assertEquals(1, updater.updates, "the duplicate must not re-fetch off-cadence")

        advanceTimeBy(20.seconds)
        assertEquals(2, updater.updates, "and the original cadence is intact")
    }

    @Test
    fun `a repeated poll does not re-serve the start delay`() = runTest {
        val updater = CountingUpdater()

        updater.poll(scope = backgroundScope, frequency = 20.seconds, startIn = 2.seconds)
        advanceTimeBy(1.seconds)
        updater.poll(scope = backgroundScope, frequency = 20.seconds, startIn = 2.seconds)

        // t = 2.5s. On the original schedule the fetch has landed; a restart would have pushed it
        // to t = 3s.
        advanceTimeBy(1500.milliseconds)
        assertEquals(1, updater.updates, "the fetch lands on the original schedule, not a second late")
    }

    @Test
    fun `stopping releases the loop so the next foreground restarts it`() = runTest {
        val updater = CountingUpdater()

        updater.poll(scope = backgroundScope, frequency = 20.seconds)
        advanceTimeBy(1.milliseconds)
        updater.stop()

        updater.poll(scope = backgroundScope, frequency = 20.seconds)
        advanceTimeBy(1.milliseconds)
        assertEquals(2, updater.updates)
    }

    @Test
    fun `a poll under a different key replaces the running loop`() = runTest {
        val updater = CountingUpdater()

        updater.poll(key = "a", scope = backgroundScope, frequency = 20.seconds)
        advanceTimeBy(1.milliseconds)

        updater.poll(key = "b", scope = backgroundScope, frequency = 20.seconds)
        advanceTimeBy(1.milliseconds)
        assertEquals(2, updater.updates)
    }
}
