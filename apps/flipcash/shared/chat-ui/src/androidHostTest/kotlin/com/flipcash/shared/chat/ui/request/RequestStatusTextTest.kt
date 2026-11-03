package com.flipcash.shared.chat.ui.request

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.services.models.paymentrequest.StatusLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestStatusTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    // 22: the state side is pinned in PaymentRequestTest.
    @Test
    fun `exactly 24 hours left reads in hours`() {
        composeRule.setContent { Text(StatusLine.HoursLeft(24).text()) }
        composeRule.onNodeWithText("24 hours left to pay").assertExists()
    }

    @Test
    fun `one hour and one minute read in the singular`() {
        composeRule.setContent {
            Text(StatusLine.HoursLeft(1).text())
            Text(StatusLine.MinutesLeft(1).text())
        }
        composeRule.onNodeWithText("1 hour left to pay").assertExists()
        composeRule.onNodeWithText("1 minute left to pay").assertExists()
    }
}
