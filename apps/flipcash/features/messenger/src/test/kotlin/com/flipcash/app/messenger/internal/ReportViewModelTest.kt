package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.reporting.ReportReason
import com.flipcash.services.controllers.ReportingController
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SelectedBottomBarAction
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * the report was sent to a screen that was already gone. These pin the replacement: the send is
 * confirmed while the flow is still up, and it is dismissing that confirmation which closes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val reporting = mockk<ReportingController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val subject = ReportSubject.User(listOf<Byte>(1, 2, 3))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun viewModel() = ReportViewModel(reporting, resources)

    @Test
    fun `a sent report is confirmed, not mentioned in passing`() = runTest {
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)

        viewModel().submit(subject, ReportReason.Spam, details = null)

        val shown = BottomBarManager.messages.value.last()
        assertEquals(BottomBarManager.BottomBarMessageType.SUCCESS, shown.type)
    }

    @Test
    fun `the flow closes when the confirmation is dismissed, not when the report lands`() = runTest {
        coEvery { reporting.report(any(), any()) } returns Result.success(Unit)
        val viewModel = viewModel()

        var closed = false
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.confirmed.collect { closed = true } }

        viewModel.submit(subject, ReportReason.Spam, details = null)
        assertFalse("the flow closed before anyone saw the confirmation", closed)

        BottomBarManager.messages.value.last().onClose(SelectedBottomBarAction(index = 0))
        assertTrue("dismissing the confirmation left the flow open", closed)

        collector.cancel()
    }

    @Test
    fun `a failed report leaves the flow open to try again`() = runTest {
        coEvery { reporting.report(any(), any()) } returns Result.failure(Throwable("nope"))
        val viewModel = viewModel()

        var closed = false
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.confirmed.collect { closed = true } }

        viewModel.submit(subject, ReportReason.Spam, details = null)

        assertEquals(
            BottomBarManager.BottomBarMessageType.ERROR,
            BottomBarManager.messages.value.last().type,
        )
        assertFalse("a failed report closed the flow", closed)

        collector.cancel()
    }

    @Test
    fun `the send is visible while it is in flight`() = runTest {
        val inFlight = CompletableDeferred<Result<Unit>>()
        coEvery { reporting.report(any(), any()) } coAnswers { inFlight.await() }
        val viewModel = viewModel()

        assertFalse(viewModel.submitting.value)

        viewModel.submit(subject, ReportReason.Spam, details = null)
        assertTrue("the button gives no sign the report is being sent", viewModel.submitting.value)

        inFlight.complete(Result.success(Unit))
        assertFalse("the button stayed busy after the report landed", viewModel.submitting.value)
    }
}
