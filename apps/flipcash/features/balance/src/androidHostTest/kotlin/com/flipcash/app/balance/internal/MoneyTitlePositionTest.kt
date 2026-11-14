package com.flipcash.app.balance.internal

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpRect
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.shared.transactionhistory.FeedSyncState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithLargeTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Under Group Money, Groups and Money sit side by side in the nav bar, so switching tabs must leave
 * the title where it is. The Groups tab's bar is drawn over the Money tab, both filling the same
 * root, and the two titles and end controls are compared where they land.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class MoneyTitlePositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD).let {
        LocalFiat(underlyingTokenAmount = it, nativeAmount = it, rate = Rate.oneToOne, mint = mint)
    }

    private fun setTabs() {
        val balanceState = WalletViewModel.State(
            receivedMoney = true,
            holdsBalance = true,
            feedSyncState = FeedSyncState.Synced,
        )
        val tokenState = SelectTokenViewModel.State(
            purpose = TokenPurpose.Balance,
            tokens = listOf(
                TokenWithLocalizedBalance(
                    token = Token.usdf.copy(address = mint),
                    balance = usd(10.0),
                    appreciation = usd(1.0),
                )
            ),
        )
        composeRule.setContent {
            FlipcashPreview {
                Box(Modifier.fillMaxSize()) {
                    // The Groups tab's bar, as ChatsTitleBar draws it under Group Money.
                    Box(Modifier.fillMaxSize()) {
                        AppBarWithLargeTitle(
                            title = "Groups",
                            endContent = { AppBarDefaults.Add {} },
                        )
                    }
                    Box(Modifier.fillMaxSize()) {
                        WalletScreenContent(
                            balanceState = balanceState,
                            tokenState = tokenState,
                            dispatchEvent = {},
                            money = true,
                            topBar = { MoneyTitleBar(profileAvatar = {}, dispatchEvent = {}) },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style
    }

    private val DpRect.centerY get() = (top + bottom) / 2

    @Test
    fun `the Money title lands where the Groups title does`() {
        setTabs()

        val groups = composeRule.onNodeWithText("Groups")
        val money = composeRule.onNodeWithText("Money")
        val groupsBounds = groups.getBoundsInRoot()
        val moneyBounds = money.getBoundsInRoot()
        val seen = "Groups $groupsBounds, Money $moneyBounds"

        assertEquals(groupsBounds.left, moneyBounds.left, "start, $seen")
        assertEquals(groupsBounds.top, moneyBounds.top, "top, $seen")
        assertEquals(groupsBounds.bottom, moneyBounds.bottom, "bottom, $seen")
        assertEquals(groups.textStyle().fontSize, money.textStyle().fontSize, "font size")
    }

    @Test
    fun `the profile avatar lands where Add does, centred on the title`() {
        setTabs()

        val add = composeRule.onNodeWithTag("action_add").getBoundsInRoot()
        val avatar = composeRule.onNodeWithTag("money_profile").getBoundsInRoot()
        val title = composeRule.onNodeWithText("Money").getBoundsInRoot()
        val seen = "Add $add, avatar $avatar, Money title $title"

        assertEquals(add.right, avatar.right, "end, $seen")
        assertEquals(add.centerY, avatar.centerY, "centre, $seen")
        // The bar centres each child in whole pixels, so an odd leftover pixel puts the two centres
        // up to half a pixel apart.
        assertEquals(title.centerY.value, avatar.centerY.value, 0.5f, "centred on the title, $seen")
    }
}
