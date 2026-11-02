package com.flipcash.shared.chat.ui.request

import com.flipcash.services.models.paymentrequest.StatusLine
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RequestStatusTextTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    // 22: the state side is pinned in PaymentRequestTest.
    @Test
    fun `exactly 24 hours left reads in hours`() {
        assertEquals("24 hours left to pay", StatusLine.HoursLeft(24).text(resources))
    }

    @Test
    fun `one hour and one minute read in the singular`() {
        assertEquals("1 hour left to pay", StatusLine.HoursLeft(1).text(resources))
        assertEquals("1 minute left to pay", StatusLine.MinutesLeft(1).text(resources))
    }
}
