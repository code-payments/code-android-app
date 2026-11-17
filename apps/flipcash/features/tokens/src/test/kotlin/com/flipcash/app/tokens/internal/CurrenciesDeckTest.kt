package com.flipcash.app.tokens.internal

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.CardExpandHostLayout
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Group Money shell's currencies screen ("N currencies >" on the Money tab, node 13729:744)
 * draws the wallet's card deck, and a tapped card opens its currency over the deck through the
 * same card-expand host the wallet uses. The overlay is swapped for a tagged box: the real one
 * needs Hilt, and what is under test is that the host draws something for the tapped mint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CurrenciesDeckTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val tokens = listOf(Token.usdf, Token.usdc).mapIndexed { index, token ->
        TokenWithLocalizedBalance(
            token = token,
            balance = LocalFiat(
                usdf = Fiat(quarks = 1_000_000L),
                nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            ),
            displayName = "Token $index",
        )
    }

    @Test
    fun `the Money tab's currencies screen draws the deck`() {
        assertTrue(showsCurrencyDeck(TokenPurpose.Balance, NavBarShell.GroupMoney))
    }

    @Test
    fun `the classic shell and the other pickers keep the list`() {
        assertFalse(showsCurrencyDeck(TokenPurpose.Balance, NavBarShell.Classic))
        assertFalse(showsCurrencyDeck(TokenPurpose.Select, NavBarShell.GroupMoney))
        assertFalse(showsCurrencyDeck(TokenPurpose.Withdraw, NavBarShell.GroupMoney))
        assertFalse(showsCurrencyDeck(TokenPurpose.Deposit, NavBarShell.GroupMoney))
    }

    @Test
    fun `the deck draws a card for each currency`() {
        setDeck(CardExpansionController())

        composeRule.onNodeWithTag(StackTag).assertExists()
        assertEquals(tokens.size, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun `tapping a card opens that currency over the deck`() {
        val controller = CardExpansionController()
        setDeck(controller)

        openCard(index = 1)

        composeRule.onNodeWithTag(overlayTag(Token.usdc.address)).assertExists()
        assertEquals(Token.usdc.address, controller.expandedKey)
        assertEquals(1f, controller.progress.value)
    }

    @Test
    fun `back closes the open currency into the deck`() {
        val controller = CardExpansionController()
        setDeck(controller)
        openCard(index = 1)
        composeRule.onNodeWithTag(overlayTag(Token.usdc.address)).assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        pump()

        composeRule.onNodeWithTag(overlayTag(Token.usdc.address)).assertDoesNotExist()
        assertNull(controller.expandedKey)
        assertEquals(0f, controller.progress.value)
    }

    private fun setDeck(controller: CardExpansionController) {
        // The card's async icon keeps scheduling work, so drive frames rather than waiting for idle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview {
                CardExpandHostLayout(
                    controller = controller,
                    overlay = { _, mint, _ ->
                        Box(Modifier.fillMaxSize().testTag(overlayTag(mint)))
                    },
                ) {
                    CurrenciesDeck(
                        tokens = tokens,
                        controller = controller,
                        modifier = Modifier.testTag(StackTag),
                    )
                }
            }
        }
        pump()
    }

    private fun openCard(index: Int) {
        composeRule.onAllNodes(hasClickAction())[index].performSemanticsAction(SemanticsActions.OnClick)
        pump()
    }

    // Long enough for the expand and collapse tweens to finish.
    private fun pump() = composeRule.mainClock.advanceTimeBy(2_000L)

    private fun overlayTag(mint: Mint) = "overlay_${mint.base58()}"

    private companion object {
        const val StackTag = "currencies_deck"
    }
}
