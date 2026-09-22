package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.reporting.ReportReason
import com.flipcash.services.controllers.ReportingController
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SelectedBottomBarAction
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.MinimumLoadingDuration
import com.getcode.view.SuccessHoldDuration
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What a report does once it has been sent.
 *
 * The flow used to close the moment submit was called and let a message land behind it, which said
 * the report was sent to a screen that was already gone. These pin the replacement: the button
 * carries the send and holds its checkmark long enough to be read, the confirmation follows while
 * the flow is still up, and it is dismissing that confirmation which closes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val reporting = mockk<ReportingController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val subject = ReportSubject.User(listOf<Byte>(1, 2, 3))

    // Shared with `runTest` below, so the hold the view model waits out is the same virtual clock
    // the test advances. A dispatcher on its own scheduler would make that delay real time.
    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun viewModel() = ReportViewModel(reporting, resources)

    @Test
    fun `a sent report is confirmed, not mentioned in passing`() = runTest(scheduler) {
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)

        viewModel().submit(subject, ReportReason.Spam, details = null)
        advanceUntilIdle()

        val shown = BottomBarManager.messages.value.last()
        assertEquals(BottomBarManager.BottomBarMessageType.SUCCESS, shown.type)
    }

    @Test
    fun `an instant send still shows its spinner`() = runTest(scheduler) {
        // The report call answers inside a frame, so without a floor the button crossfaded from
        // its label straight to the checkmark and the send read as having never happened.
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)
        val model = viewModel()

        model.submit(subject, ReportReason.Spam, details = null)

        assertTrue(model.state.value.loading)
        advanceTimeBy(MinimumLoadingDuration.inWholeMilliseconds - 1)
        assertTrue(model.state.value.loading)
        advanceTimeBy(2)
        assertTrue(model.state.value.success)
    }

    @Test
    fun `a slow send is not held back any further`() = runTest(scheduler) {
        // The floor is a minimum, not an addition: a send that already outlasted it goes straight
        // to the checkmark rather than sitting on a spinner for another half second.
        val answer = CompletableDeferred<Result<Unit>>()
        coEvery { reporting.report(any(), any()) } coAnswers { answer.await() }
        val model = viewModel()

        model.submit(subject, ReportReason.Spam, details = null)
        advanceTimeBy(MinimumLoadingDuration.inWholeMilliseconds * 2)
        assertTrue(model.state.value.loading)

        answer.complete(Result.success(Unit))
        advanceTimeBy(1)

        assertTrue(model.state.value.success)
    }

    @Test
    fun `the checkmark is held long enough to be read before the confirmation`() = runTest(scheduler) {
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)
        val viewModel = viewModel()

        viewModel.submit(subject, ReportReason.Spam, details = null)
        // Past the spinner's floor, which is where the checkmark's own hold starts.
        // `advanceTimeBy` stops short of the endpoint, so the floor's own instant needs the +1.
        advanceTimeBy(MinimumLoadingDuration.inWholeMilliseconds + 1)

        assertTrue("the button never showed the send succeeded", viewModel.state.value.success)
        assertTrue(
            "the confirmation covered the checkmark before it could be read",
            BottomBarManager.messages.value.isEmpty(),
        )

        advanceTimeBy(SuccessHoldDuration)
        advanceUntilIdle()
        assertEquals(
            BottomBarManager.BottomBarMessageType.SUCCESS,
            BottomBarManager.messages.value.last().type,
        )
    }

    @Test
    fun `the button stays done while the confirmation is up`() = runTest(scheduler) {
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)
        val viewModel = viewModel()

        viewModel.submit(subject, ReportReason.Spam, details = null)
        advanceUntilIdle()

        assertTrue(
            "the button went back to inviting a second report of the same thing",
            viewModel.state.value.success,
        )
    }

    @Test
    fun `the flow closes when the confirmation is dismissed, not when the report lands`() = runTest(scheduler) {
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)
        val viewModel = viewModel()

        var closed = false
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.confirmed.collect { closed = true }
        }

        viewModel.submit(subject, ReportReason.Spam, details = null)
        advanceUntilIdle()
        assertFalse("the flow closed before anyone saw the confirmation", closed)

        BottomBarManager.messages.value.last().onClose(SelectedBottomBarAction(index = 0))
        assertTrue("dismissing the confirmation left the flow open", closed)

        collector.cancel()
    }

    @Test
    fun `a failed report leaves the flow open to try again`() = runTest(scheduler) {
        coEvery { reporting.report(any(), any()) } returns Result.failure(Throwable("nope"))
        val viewModel = viewModel()

        var closed = false
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.confirmed.collect { closed = true }
        }

        viewModel.submit(subject, ReportReason.Spam, details = null)
        advanceUntilIdle()

        assertEquals(
            BottomBarManager.BottomBarMessageType.ERROR,
            BottomBarManager.messages.value.last().type,
        )
        assertFalse("a failed report closed the flow", closed)
        assertTrue("the retry was left disabled", viewModel.state.value.isIdle)

        collector.cancel()
    }

    @Test
    fun `the send is visible while it is in flight`() = runTest(scheduler) {
        val inFlight = CompletableDeferred<Result<Unit>>()
        coEvery { reporting.report(any(), any()) } coAnswers { inFlight.await() }
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.isIdle)

        viewModel.submit(subject, ReportReason.Spam, details = null)
        assertTrue("the button gives no sign the report is being sent", viewModel.state.value.loading)

        inFlight.complete(Result.success(Unit))
        advanceUntilIdle()
        assertFalse("the button was still spinning after the report landed", viewModel.state.value.loading)
    }

    @Test
    fun `a second press cannot file the same report twice`() = runTest(scheduler) {
        val inFlight = CompletableDeferred<Result<Unit>>()
        coEvery { reporting.report(any(), any()) } coAnswers { inFlight.await() }
        val viewModel = viewModel()

        viewModel.submit(subject, ReportReason.Spam, details = null)
        viewModel.submit(subject, ReportReason.Spam, details = null)

        inFlight.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(
            "the report was filed twice, and answered twice",
            1,
            BottomBarManager.messages.value.size,
        )
    }
}
