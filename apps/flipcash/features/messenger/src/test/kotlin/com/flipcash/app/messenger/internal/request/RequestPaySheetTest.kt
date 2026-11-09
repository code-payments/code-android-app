package com.flipcash.app.messenger.internal.request

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.services.models.paymentrequest.Share
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.DesignSystem
import com.getcode.view.LoadingSuccessState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The swipe-to-pay sheet draws its share from the pay state, so it keeps its amount through the
 * success hold, when the live request already reads paid or has left the deck.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class RequestPaySheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val creator: ID = List(16) { 1 }
    private val debtor: ID = List(16) { 2 }

    @Test
    fun `the sheet shows the held share during the success hold`() {
        val pay = RequestPayFlow.State(
            requestId = List(16) { 9 },
            open = true,
            progress = LoadingSuccessState(success = true),
            share = Share(debtor, Fiat(20.0, CurrencyCode.USD), paid = null),
            creator = creator,
        )

        composeTestRule.setContent {
            DesignSystem {
                RequestPaySheet(
                    pay = pay,
                    members = emptyMap(),
                    token = null,
                    actions = SwipePayActions(onConfirm = {}, onDismiss = {}),
                )
            }
        }

        composeTestRule.onNodeWithText("$20", substring = true).assertExists()
    }

    @Test
    fun `nothing draws once the sheet is closed`() {
        composeTestRule.setContent {
            DesignSystem {
                RequestPaySheet(
                    pay = RequestPayFlow.State(),
                    members = emptyMap(),
                    token = null,
                    actions = SwipePayActions(onConfirm = {}, onDismiss = {}),
                )
            }
        }

        composeTestRule.onNodeWithText("$20", substring = true).assertDoesNotExist()
    }
}
