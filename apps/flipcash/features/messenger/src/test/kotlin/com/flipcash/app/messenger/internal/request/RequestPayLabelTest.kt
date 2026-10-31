package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.flipcash.features.messenger.R
import com.getcode.view.LoadingSuccessState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** Paying a request reads "Pay"; sending to a group or from Currency Info still reads "Send". */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RequestPayLabelTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `request pay swipe reads swipe to pay`() {
        val state = requestPaySwipeState(RequestPayFlow.State(open = true), token = null)

        assertEquals("Swipe to Pay", context.getString(state.swipeLabel))
    }

    @Test
    fun `send swipe keeps swipe to send`() {
        val state = SwipePayUiState(token = null, progress = LoadingSuccessState(), tokenPickable = true)

        assertEquals("Swipe to Send", context.getString(state.swipeLabel))
    }

    @Test
    fun `request details button reads pay and the share`() {
        assertEquals("Pay $20", context.getString(R.string.action_payShare, "$20"))
    }
}
