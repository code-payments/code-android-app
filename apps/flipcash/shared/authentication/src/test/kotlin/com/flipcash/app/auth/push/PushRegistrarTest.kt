package com.flipcash.app.auth.push

import com.flipcash.app.push.PushTokenProvider
import com.flipcash.services.controllers.PushController
import com.flipcash.services.models.AddTokenError
import com.flipcash.services.user.UserManager
import io.grpc.Status
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PushRegistrarTest {

    private val now = Instant.fromEpochSeconds(1_761_658_860)
    private val clock = object : Clock {
        override fun now() = this@PushRegistrarTest.now
    }

    private val pushController: PushController = mockk()
    private val tokenProvider: PushTokenProvider = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val store = FakeStore()

    private val registrar = PushRegistrar(pushController, tokenProvider, userManager, store, clock)

    @Test
    fun `ok updates the in-memory token and saves the record`() = runTest {
        coEvery { tokenProvider.getToken() } returns "token-abc123"
        coEvery { pushController.addToken("token-abc123") } returns Result.success(Unit)

        val attempt = registrar.register(PushRegistrationSource.Login)

        assertEquals(PushRegistrationOutcome.Ok, attempt.outcome)
        assertEquals("abc123", attempt.tokenSuffix)
        verify { userManager.set(pushToken = "token-abc123") }
        assertEquals(PushRegistration(now, PushRegistrationResult.Ok, PushRegistrationSource.Login), store.saved)
    }

    @Test
    fun `refresh sends the given token without fetching one`() = runTest {
        coEvery { pushController.addToken("fresh") } returns Result.success(Unit)

        registrar.register(PushRegistrationSource.Refresh, token = "fresh")

        coVerify(exactly = 0) { tokenProvider.getToken() }
        assertEquals(PushRegistrationSource.Refresh, store.saved?.source)
    }

    // Test case 10
    @Test
    fun `invalid push token fails and try again deletes the local token first`() = runTest {
        coEvery { tokenProvider.getToken() } returns "stale"
        coEvery { pushController.addToken(any()) } returns Result.failure(AddTokenError.InvalidPushToken())

        val first = registrar.register(PushRegistrationSource.StatusPage)
        assertEquals(PushRegistrationOutcome.InvalidPushToken, first.outcome)
        assertEquals(PushRegistrationResult.InvalidPushToken, store.saved?.result)

        coEvery { tokenProvider.getToken() } returns "new"
        coEvery { pushController.addToken("new") } returns Result.success(Unit)
        registrar.register(PushRegistrationSource.StatusPage, resetToken = true)

        coVerifyOrder {
            tokenProvider.deleteToken()
            tokenProvider.getToken()
            pushController.addToken("new")
        }
    }

    // Test case 11 (the registrar half: the model turns this into Fail or Warning)
    @Test
    fun `no answer within the timeout is a transport failure after 10 s`() = runTest {
        coEvery { tokenProvider.getToken() } returns "token"
        coEvery { pushController.addToken(any()) } coAnswers { awaitCancellation() }

        val attempt = registrar.register(PushRegistrationSource.StatusPage, timeout = 10.seconds)

        assertEquals(PushRegistrationOutcome.Transport, attempt.outcome)
        assertEquals(10_000, currentTime)
        assertEquals(PushRegistrationResult.Transport, store.saved?.result)
    }

    // Test case 11b: the saved ok is handed back so the page can soften the failure
    @Test
    fun `transport failure returns the previously saved record`() = runTest {
        val earlier = PushRegistration(Instant.fromEpochSeconds(1_761_600_000), PushRegistrationResult.Ok, PushRegistrationSource.Login)
        store.saved = earlier
        coEvery { tokenProvider.getToken() } returns "token"
        coEvery { pushController.addToken(any()) } returns
            Result.failure(AddTokenError.Other(cause = StatusException(Status.UNAVAILABLE)))

        val attempt = registrar.register(PushRegistrationSource.StatusPage)

        assertEquals(PushRegistrationOutcome.Transport, attempt.outcome)
        assertEquals(earlier, attempt.previous)
    }

    @Test
    fun `io failure deep in the cause chain is transport`() = runTest {
        coEvery { tokenProvider.getToken() } returns "token"
        coEvery { pushController.addToken(any()) } returns
            Result.failure(AddTokenError.Other(cause = RuntimeException(IOException("offline"))))

        assertEquals(PushRegistrationOutcome.Transport, registrar.register(PushRegistrationSource.Login).outcome)
    }

    @Test
    fun `any other error is recorded as unknown`() = runTest {
        coEvery { tokenProvider.getToken() } returns "token"
        coEvery { pushController.addToken(any()) } returns Result.failure(AddTokenError.Unrecognized())

        val attempt = registrar.register(PushRegistrationSource.Login)

        assertEquals(PushRegistrationOutcome.Failed, attempt.outcome)
        assertEquals(PushRegistrationResult.Unknown, store.saved?.result)
    }

    @Test
    fun `no token never calls AddToken and saves nothing`() = runTest {
        coEvery { tokenProvider.getToken() } returns null

        val attempt = registrar.register(PushRegistrationSource.StatusPage)

        assertEquals(PushRegistrationOutcome.NoToken, attempt.outcome)
        coVerify(exactly = 0) { pushController.addToken(any()) }
        assertNull(store.saved)
    }

    @Test
    fun `record round-trips through its stored form`() {
        val record = PushRegistration(now, PushRegistrationResult.Transport, PushRegistrationSource.StatusPage)
        val encoded = DataStorePushRegistrationStore.encode(record)

        assertEquals("transport|statusPage|${now.toEpochMilliseconds()}", encoded)
        assertEquals(record, DataStorePushRegistrationStore.decode(encoded))
        assertNull(DataStorePushRegistrationStore.decode("ok|nowhere|1"))
    }

    private class FakeStore : PushRegistrationStore {
        var saved: PushRegistration? = null
        override suspend fun read() = saved
        override suspend fun save(registration: PushRegistration) { saved = registration }
        override suspend fun clear() { saved = null }
    }
}
