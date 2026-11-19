package com.flipcash.app.messenger.internal.request

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.theme.DesignSystem
import com.getcode.view.LoadingSuccessState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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

    @Test
    fun `the sheet shows the held share during the success hold`() {
        val pay = RequestPayFlow.State(
            requestId = List(16) { 9 },
            open = true,
            progress = LoadingSuccessState(success = true),
            amount = Fiat(20.0, CurrencyCode.USD),
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

    private val jeffy: Token = mockk(relaxed = true) {
        every { address } returns Mint(List(32) { 7 })
        every { name } returns "Jeffy"
    }

    private fun row(token: Token, usd: Double) = TokenWithLocalizedBalance(
        token = token,
        balance = LocalFiat(
            usdf = Fiat(usd, CurrencyCode.USD),
            nativeAmount = Fiat(usd, CurrencyCode.USD),
        ),
    )

    private val collection = RequestPayFlow.State(
        requestId = List(16) { 9 },
        open = true,
        amount = Fiat(14.0, CurrencyCode.USD),
        creator = creator,
        funding = true,
    )

    private fun setFundingSheet(onPick: (Mint) -> Unit) {
        composeTestRule.setContent {
            DesignSystem {
                RequestPaySheet(
                    pay = collection,
                    members = emptyMap(),
                    token = jeffy,
                    actions = SwipePayActions(onConfirm = {}, onDismiss = {}),
                    collectionCurrencies = listOf(row(Token.usdf, 8.0), row(jeffy, 0.0)),
                    onPickCollectionCurrency = onPick,
                )
            }
        }
    }

    @Test
    fun `a collection's pill opens Select Payment Currency with the paying currency at zero`() {
        setFundingSheet(onPick = {})

        composeTestRule.onNodeWithText("Select Payment Currency").assertDoesNotExist()
        composeTestRule.onNodeWithText("Jeffy").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Select Payment Currency").assertExists()
        composeTestRule.onNodeWithTag(collectionCurrencyRowTag(Token.usdf.address)).assertExists()
        composeTestRule.onNodeWithTag(collectionCurrencyRowTag(jeffy.address)).assertExists()
        composeTestRule.onNodeWithText("Dollars").assertExists()
        // 6.3 draws no balances.
        composeTestRule.onNodeWithText("$8", substring = true).assertDoesNotExist()
    }

    @Test
    fun `picking a currency hands it back and closes the list`() {
        val picks = mutableListOf<Mint>()
        setFundingSheet(onPick = { picks += it })

        composeTestRule.onNodeWithText("Jeffy").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(collectionCurrencyRowTag(Token.usdf.address)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(Token.usdf.address), picks)
        composeTestRule.onNodeWithText("Select Payment Currency").assertDoesNotExist()
    }
}
