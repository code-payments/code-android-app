package com.flipcash.shared.chat

import app.cash.turbine.test
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.chat.internal.SenderResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * `GetProfile` is single-user, so every distinct unknown sender in a group costs one call. The
 * guarantee these tests exist for is that it costs no more than one: the transcript asks for a
 * sender on every emission that sees a bubble from them — a page load, a pending mutation, any
 * write to `user_profiles` — and an unguarded resolver would fan that out into a call per emission.
 *
 * The dedupe marks a user id as in-flight *before* launching the fetch, so the second request
 * is dropped on the calling thread. That makes the behaviour deterministic under
 * `StandardTestDispatcher` rather than dependent on when the coroutine happens to be scheduled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SenderResolverTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = TestDispatchers(scheduler)

    private val userId = ByteArray(16) { 3 }.toList()
    private val userIdHex = "03".repeat(16)
    private val profile = UserProfile.Empty.copy(displayName = "Ada", userId = userId)

    private val profileController = mockk<ProfileController>()
    private val userProfileDataSource = mockk<UserProfileDataSource>(relaxed = true) {
        every { observeProfiles() } returns MutableStateFlow(emptyMap())
    }

    private fun subject() = SenderResolver(
        profileController = profileController,
        userProfileDataSource = userProfileDataSource,
        dispatchers = dispatchers,
    )

    @Test
    fun `a miss triggers one fetch and is written to user_profiles`() = runTest(dispatchers.dispatcher) {
        coEvery { profileController.getProfileForUser(userId) } returns Result.success(profile)
        val resolver = subject()

        resolver.request(userId)
        advanceUntilIdle()

        coVerify(exactly = 1) { profileController.getProfileForUser(userId) }
        coVerify(exactly = 1) { userProfileDataSource.store(userId, profile) }
        resolver.clear()
    }

    @Test
    fun `concurrent requests for the same sender are deduped`() = runTest(dispatchers.dispatcher) {
        coEvery { profileController.getProfileForUser(userId) } returns Result.success(profile)
        val resolver = subject()

        resolver.request(userId)
        resolver.request(userId)
        resolver.request(userId)
        advanceUntilIdle()

        coVerify(exactly = 1) { profileController.getProfileForUser(userId) }
        resolver.clear()
    }

    // region Failures

    @Test
    fun `a transient failure is held for the backoff window, then retried`() =
        runTest(dispatchers.dispatcher) {
            coEvery { profileController.getProfileForUser(userId) } returns
                Result.failure(RuntimeException("offline"))
            val resolver = subject()

            resolver.request(userId)
            runCurrent()

            // A re-emission inside the window is not evidence the network came back, so it does
            // not buy another attempt.
            resolver.request(userId)
            runCurrent()
            coVerify(exactly = 1) { profileController.getProfileForUser(userId) }

            advanceTimeBy(31.seconds)
            runCurrent()
            resolver.request(userId)
            runCurrent()

            coVerify(exactly = 2) { profileController.getProfileForUser(userId) }
            coVerify(exactly = 0) { userProfileDataSource.store(any(), any()) }
            resolver.clear()
        }

    @Test
    fun `a sender the server has no profile for is never asked about again`() =
        runTest(dispatchers.dispatcher) {
            coEvery { profileController.getProfileForUser(userId) } returns
                Result.failure(GetUserProfileError.NotFound())
            val resolver = subject()

            resolver.request(userId)
            advanceUntilIdle()

            // NOT_FOUND is an answer, not a failure to get one: waiting out a backoff and asking
            // again would get the same answer for as long as the transcript keeps showing the
            // bubble.
            advanceTimeBy(5.minutes)
            resolver.request(userId)
            advanceUntilIdle()

            coVerify(exactly = 1) { profileController.getProfileForUser(userId) }
            resolver.clear()
        }

    // endregion

    @Test
    fun `fetches are capped so opening a group does not fan out per bubble`() =
        runTest(dispatchers.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            var started = 0
            coEvery { profileController.getProfileForUser(any()) } coAnswers {
                started++
                gate.await()
                Result.success(profile)
            }
            val resolver = subject()

            repeat(20) { index -> resolver.request(ByteArray(16) { index.toByte() }.toList()) }
            advanceUntilIdle()

            assertEquals(6, started)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(20, started)
            resolver.clear()
        }

    @Test
    fun `resolved profiles are observed off the shared user_profiles table`() =
        runTest(dispatchers.dispatcher) {
            val table = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
            every { userProfileDataSource.observeProfiles() } returns table
            val resolver = subject()

            resolver.profiles.test {
                assertEquals(emptyMap(), awaitItem())

                table.value = mapOf(userIdHex to profile)
                assertEquals("Ada", awaitItem()[userIdHex]?.displayName)

                cancelAndIgnoreRemainingEvents()
            }
            resolver.clear()
        }
}
