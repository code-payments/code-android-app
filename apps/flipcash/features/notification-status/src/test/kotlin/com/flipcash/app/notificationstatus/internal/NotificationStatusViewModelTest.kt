package com.flipcash.app.notificationstatus.internal

import com.flipcash.app.auth.push.PushRegistrar
import com.flipcash.app.auth.push.PushRegistration
import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.app.auth.push.PushRegistrationResult
import com.flipcash.app.auth.push.PushRegistrationSource
import com.flipcash.app.auth.push.PushRegistrationStore
import com.flipcash.app.notifications.TestPushMark
import com.flipcash.app.push.PushTokenProvider
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.controllers.PushController
import com.flipcash.services.models.SendTestPushError
import io.grpc.Status
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import java.io.IOException
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

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
        coEvery { sendTestPush() } coAnswers {
            delay(500)
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

    private val store = FakeStore()
    private val testPushMark = TestPushMark()

    private fun viewModel() = NotificationStatusViewModel(
        context = mockk(relaxed = true),
        reader = reader,
        registrar = PushRegistrar(pushController, tokenProvider, mockk(relaxed = true), store),
        pushController = pushController,
        testPushMark = testPushMark,
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
    fun `returning to the foreground re-reads the phone without registering again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.stateFlow.value.folded)
        coVerify(exactly = 1) { pushController.addToken(any()) }

        val folded = mutableListOf<Boolean>()
        val watch = launch { viewModel.stateFlow.collect { folded += it.folded } }
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.Refresh)
        advanceUntilIdle()
        watch.cancel()

        verify(exactly = 2) { reader.read() }
        coVerify(exactly = 1) { pushController.addToken(any()) }
        // Registration kept its result, so the page never unfolded.
        assertTrue(folded.all { it })
        assertFalse(viewModel.stateFlow.value.running)
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

    // Test case 11b, end to end: the saved ok softens a real timeout.
    @Test
    fun `a timeout after a saved ok is a warning that says when it was last confirmed`() = runTest(dispatcher) {
        val confirmedAt = Instant.fromEpochSeconds(1_761_600_000)
        store.saved = PushRegistration(confirmedAt, PushRegistrationResult.Ok, PushRegistrationSource.Login, lastOkAt = confirmedAt)
        coEvery { pushController.addToken(any()) } coAnswers { awaitCancellation() }

        val viewModel = viewModel()
        advanceTimeBy(9_999)
        assertEquals(RegistrationState.Checking, viewModel.stateFlow.value.registration)
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        val row = state.status.flipcashRows.single()
        assertEquals(CheckStatus.Warning, row.status)
        assertEquals(RegistrationState.Finished(PushRegistrationOutcome.Transport, confirmedAt), state.registration)
        // The report names the token this attempt sent, and the saved ok time survives the failure.
        assertEquals("abc123", state.tokenSuffix)
        assertEquals(PushRegistrationResult.Transport, store.saved?.result)
        assertEquals(confirmedAt, store.saved?.lastOkAt)
    }

    @Test
    fun `a timeout with no saved ok fails`() = runTest(dispatcher) {
        // A record exists, but it never said ok.
        store.saved = PushRegistration(Instant.fromEpochSeconds(1), PushRegistrationResult.Transport, PushRegistrationSource.Login)
        coEvery { pushController.addToken(any()) } coAnswers { awaitCancellation() }

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(CheckStatus.Fail, viewModel.stateFlow.value.status.flipcashRows.single().status)
    }

    @Test
    fun `checked just now shows once the first run finishes and stays through check again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertFalse(viewModel.stateFlow.value.checked)

        advanceUntilIdle()
        assertTrue(viewModel.stateFlow.value.checked)

        viewModel.dispatchEvent(NotificationStatusViewModel.Event.CheckAgain)
        assertTrue(viewModel.stateFlow.value.running)
        assertTrue(viewModel.stateFlow.value.checked)
    }

    @Test
    fun `check again keeps the last summary until the run finishes, then shows the new one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        // The first run has nothing earlier to show.
        assertEquals(Summary.Checking, viewModel.stateFlow.value.summary)
        advanceUntilIdle()
        assertEquals(Summary.Working, viewModel.stateFlow.value.summary)

        // Battery optimization came back on; the re-run finds it.
        every { reader.read() } returns healthy.copy(ignoringBatteryOptimizations = false)
        val summaries = mutableListOf<Summary>()
        val watch = launch { viewModel.stateFlow.collect { summaries += it.summary } }
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.CheckAgain)
        assertTrue(viewModel.stateFlow.value.running)
        assertEquals(Summary.Working, viewModel.stateFlow.value.summary)
        advanceUntilIdle()
        watch.cancel()

        // Never Checking in between.
        assertEquals(listOf(Summary.Working, Summary.ThingsToCheck(1)), summaries.distinct())
    }

    @Test
    fun `try again keeps the last summary until the run finishes, then shows the new one`() = runTest(dispatcher) {
        coEvery { tokenProvider.getToken() } returns null
        val viewModel = viewModel()
        advanceUntilIdle()
        val failed = viewModel.stateFlow.value.summary
        assertTrue(failed is Summary.ThingsToFix)

        coEvery { tokenProvider.getToken() } returns "token-abc123"
        val summaries = mutableListOf<Summary>()
        val watch = launch { viewModel.stateFlow.collect { summaries += it.summary } }
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.TryAgain)
        assertTrue(viewModel.stateFlow.value.running)
        advanceUntilIdle()
        watch.cancel()

        assertEquals(listOf(failed, Summary.Working), summaries.distinct())
    }

    @Test
    fun `granting the permission keeps the last summary until the run finishes, then shows the new one`() = runTest(dispatcher) {
        // Registration failed too, so the phone read landing before the new registration
        // would show "1 thing to fix" in between.
        every { reader.read() } returns healthy.copy(notificationsAllowed = false)
        coEvery { tokenProvider.getToken() } returns null
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(Summary.NotificationsOff, viewModel.stateFlow.value.summary)

        every { reader.read() } returns healthy
        coEvery { tokenProvider.getToken() } returns "token-abc123"
        val summaries = mutableListOf<Summary>()
        val watch = launch { viewModel.stateFlow.collect { summaries += it.summary } }
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.PermissionGranted)
        assertTrue(viewModel.stateFlow.value.running)
        assertEquals(Summary.NotificationsOff, viewModel.stateFlow.value.summary)
        advanceUntilIdle()
        watch.cancel()

        assertEquals(listOf(Summary.NotificationsOff, Summary.Working), summaries.distinct())
    }

    @Test
    fun `granting the permission runs every check, registration included`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        coVerify(exactly = 1) { pushController.addToken(any()) }

        viewModel.dispatchEvent(NotificationStatusViewModel.Event.PermissionGranted)
        advanceUntilIdle()

        verify(exactly = 2) { reader.read() }
        coVerify(exactly = 2) { pushController.addToken(any()) }
    }

    @Test
    fun `check again keeps try again on the registration row, disabled while it runs`() = runTest(dispatcher) {
        // Slow enough that the re-run is still going when the row is read.
        coEvery { tokenProvider.getToken() } coAnswers { delay(1_000); null }
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(Fix.TryAgain, viewModel.stateFlow.value.status.flipcashRows.single().fix)

        viewModel.dispatchEvent(NotificationStatusViewModel.Event.CheckAgain)
        val row = viewModel.stateFlow.value.status.flipcashRows.single()
        assertEquals(CheckStatus.Checking, row.status)
        assertEquals(Fix.TryAgain, row.fix)
    }

    @Test
    fun `no token clears the suffix from an earlier attempt`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals("abc123", viewModel.stateFlow.value.tokenSuffix)

        coEvery { tokenProvider.getToken() } returns null
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.TryAgain)
        advanceUntilIdle()

        assertEquals(null, viewModel.stateFlow.value.tokenSuffix)
    }

    private fun TestScope.sendTest(viewModel: NotificationStatusViewModel): List<TestResult> {
        val results = mutableListOf<TestResult>()
        backgroundScope.launch {
            viewModel.eventFlow
                .filterIsInstance<NotificationStatusViewModel.Event.TestFinished>()
                .collect { results += it.result }
        }
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.SendTest)
        return results
    }

    @Test
    fun `send test is loading while SendTestPush runs, then reads send again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        assertTrue(viewModel.stateFlow.value.sendingTest)
        assertFalse(viewModel.stateFlow.value.testSent)

        advanceUntilIdle()
        assertEquals(listOf(TestResult.Sent), results)
        assertFalse(viewModel.stateFlow.value.sendingTest)
        assertTrue(viewModel.stateFlow.value.testSent)
        coVerify(exactly = 1) { pushController.sendTestPush() }
    }

    @Test
    fun `send test marks a test in flight before SendTestPush returns, and leaves it for the push`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        sendTest(viewModel)
        // The push can arrive before the response.
        assertTrue(testPushMark.isMarked)

        advanceUntilIdle()
        assertTrue(testPushMark.isMarked)
    }

    @Test
    fun `a failed send clears the test mark`() = runTest(dispatcher) {
        coEvery { pushController.sendTestPush() } returns Result.failure(SendTestPushError.Other())
        val viewModel = viewModel()
        advanceUntilIdle()

        sendTest(viewModel)
        advanceUntilIdle()

        assertFalse(testPushMark.isMarked)
    }

    @Test
    fun `send test while one is in flight is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        sendTest(viewModel)
        viewModel.dispatchEvent(NotificationStatusViewModel.Event.SendTest)
        advanceUntilIdle()

        coVerify(exactly = 1) { pushController.sendTestPush() }
    }

    @Test
    fun `a transport failure says it couldn't reach Flipcash`() = runTest(dispatcher) {
        coEvery { pushController.sendTestPush() } returns Result.failure(
            SendTestPushError.Other(cause = IOException("offline")),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        advanceUntilIdle()

        assertEquals(listOf(TestResult.Unreachable), results)
        assertFalse(viewModel.stateFlow.value.testSent)
        assertFalse(viewModel.stateFlow.value.sendingTest)
    }

    @Test
    fun `a cancelled call says it couldn't reach Flipcash`() = runTest(dispatcher) {
        coEvery { pushController.sendTestPush() } returns Result.failure(
            SendTestPushError.Other(cause = StatusException(Status.CANCELLED)),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        advanceUntilIdle()

        assertEquals(listOf(TestResult.Unreachable), results)
        assertFalse(viewModel.stateFlow.value.testSent)
    }

    @Test
    fun `cancelling the send itself shows no result`() = runTest(dispatcher) {
        // Leaving the page cancels the coroutine; that is not a failed send and gets no toast.
        coEvery { pushController.sendTestPush() } throws CancellationException("left the page")
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        advanceUntilIdle()

        assertEquals(emptyList(), results)
    }

    @Test
    fun `a failed send after one that worked still reads send again`() = runTest(dispatcher) {
        coEvery { pushController.sendTestPush() } returns Result.success(Unit) andThen
            Result.failure(SendTestPushError.Other())
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        advanceUntilIdle()
        assertTrue(viewModel.stateFlow.value.testSent)

        viewModel.dispatchEvent(NotificationStatusViewModel.Event.SendTest)
        advanceUntilIdle()

        assertEquals(listOf(TestResult.Sent, TestResult.Failed), results)
        assertTrue(viewModel.stateFlow.value.testSent)
        assertFalse(viewModel.stateFlow.value.sendingTest)
    }

    @Test
    fun `no answer within 10 seconds says it couldn't reach Flipcash`() = runTest(dispatcher) {
        coEvery { pushController.sendTestPush() } coAnswers { awaitCancellation() }
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        advanceTimeBy(9_999)
        assertTrue(viewModel.stateFlow.value.sendingTest)
        advanceUntilIdle()

        assertEquals(listOf(TestResult.Unreachable), results)
        assertFalse(viewModel.stateFlow.value.testSent)
    }

    @Test
    fun `any other failure says it couldn't send a test`() = runTest(dispatcher) {
        coEvery { pushController.sendTestPush() } returns Result.failure(SendTestPushError.Other())
        val viewModel = viewModel()
        advanceUntilIdle()

        val results = sendTest(viewModel)
        advanceUntilIdle()

        assertEquals(listOf(TestResult.Failed), results)
        assertFalse(viewModel.stateFlow.value.testSent)
    }

    @Test
    fun `permission gone since the page opened sends nothing and shows notifications off`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        every { reader.read() } returns healthy.copy(notificationsAllowed = false)
        val results = sendTest(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 0) { pushController.sendTestPush() }
        assertEquals(emptyList(), results)
        assertEquals(Summary.NotificationsOff, viewModel.stateFlow.value.status.summary)
    }

    private class FakeStore : PushRegistrationStore {
        var saved: PushRegistration? = null
        override suspend fun read() = saved
        override suspend fun save(registration: PushRegistration) { saved = registration }
        override suspend fun clear() { saved = null }
    }
}
