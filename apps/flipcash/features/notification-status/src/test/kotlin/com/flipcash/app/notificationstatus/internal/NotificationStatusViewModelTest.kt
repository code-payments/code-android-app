package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistrar
import com.flipcash.app.auth.push.PushRegistration
import com.flipcash.app.auth.push.PushRegistrationStore
import com.flipcash.app.push.PushTokenProvider
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.controllers.PushController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationStatusViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val healthy = DeviceSnapshot(
        notificationsAllowed = true,
        channels = StatusChannel.entries.map { ChannelSetting(it, importance = 4) },
        ignoringBatteryOptimizations = true,
        backgroundRestricted = false,
        standbyBucket = "active",
        playServices = PlayServices.Available,
    )

    private val reader: DeviceSnapshotReader = mockk { every { read() } returns healthy }
    private val pushController: PushController = mockk {
        // A round trip, so the run can be seen in progress.
        coEvery { addToken(any()) } coAnswers {
            delay(1_000)
            Result.success(Unit)
        }
    }
    private val tokenProvider: PushTokenProvider = mockk(relaxed = true) {
        coEvery { getToken() } returns "token-abc123"
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = NotificationStatusViewModel(
        context = mockk(relaxed = true),
        reader = reader,
        registrar = PushRegistrar(pushController, tokenProvider, mockk(relaxed = true), FakeStore()),
        testPoster = mockk(relaxed = true),
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    @Test
    fun `check again resets every row to checking in place and calls AddToken again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val settled = viewModel.stateFlow.value.status
        assertEquals(Summary.Working, settled.summary)
        coVerify(exactly = 1) { pushController.addToken("token-abc123") }

        viewModel.dispatchEvent(NotificationStatusViewModel.Event.CheckAgain)

        val during = viewModel.stateFlow.value
        assertEquals(settled.rows.map { it.key }, during.status.rows.map { it.key })
        assertTrue(during.status.rows.all { it.status == CheckStatus.Checking })
        assertTrue(during.running)
        // Folded when the run started, so it stays folded rather than opening and closing again.
        assertTrue(during.folded)

        advanceUntilIdle()
        coVerify(exactly = 2) { pushController.addToken("token-abc123") }
        val after = viewModel.stateFlow.value
        assertEquals(Summary.Working, after.status.summary)
        assertFalse(after.running)
    }

    @Test
    fun `check again while a run is in progress is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        // The open's registration is still on its way.
        assertTrue(viewModel.stateFlow.value.running)

        viewModel.dispatchEvent(NotificationStatusViewModel.Event.CheckAgain)
        advanceUntilIdle()

        coVerify(exactly = 1) { pushController.addToken(any()) }
    }

    private class FakeStore : PushRegistrationStore {
        var saved: PushRegistration? = null
        override suspend fun read() = saved
        override suspend fun save(registration: PushRegistration) { saved = registration }
        override suspend fun clear() { saved = null }
    }
}
