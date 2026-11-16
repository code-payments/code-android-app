package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.messenger.internal.ReportViewModel.Event
import com.flipcash.reporting.ReportReason
import com.getcode.view.LoadingSuccessState
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportStateTest {

    private fun reduce(state: LoadingSuccessState, event: Event) = ReportViewModel.updateStateForEvent(event)(state)

    private val submit = Event.Submit(ReportSubject.User(listOf<Byte>(1)), ReportReason.Spam, details = null)

    @Test
    fun `submit leaves the button alone until the send starts`() {
        val sent = LoadingSuccessState(success = true)
        assertEquals(LoadingSuccessState(), reduce(LoadingSuccessState(), submit))
        assertEquals(sent, reduce(sent, submit))
    }

    @Test
    fun `a send moves the button through loading to success`() {
        val sending = reduce(LoadingSuccessState(), Event.Sending)
        assertEquals(LoadingSuccessState(loading = true), sending)
        assertEquals(LoadingSuccessState(success = true), reduce(sending, Event.Sent))
    }

    @Test
    fun `a failed send returns the button to idle so it can be pressed again`() {
        val sending = reduce(LoadingSuccessState(), Event.Sending)
        assertEquals(LoadingSuccessState(), reduce(sending, Event.SendFailed))
    }
}
