package com.flipcash.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Placement invariant for [TokenCardStack]: however far the list scrolls, no card may be placed below
 * the stack's own measured height. The stack reports the *fanned* height, so a card pushed past it
 * draws over whatever the enclosing list puts next (the wallet's "Recent" section) — which is exactly
 * what happened when the collapse cap was clamped at 0 and the pin inset had no fanned slack to eat,
 * as with a single card.
 */
@RunWith(RobolectricTestRunner::class)
// Tall viewport so the stack (up to 480dp fanned) is never clipped by the root, which would
// shrink its reported bounds and fake a violation.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TokenCardStackPlacementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun stubImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            SuccessResult(
                                image = ColorImage(color = 0x330D3B22),
                                request = chain.request,
                                dataSource = DataSource.MEMORY,
                            ) as ImageResult
                        },
                    )
                }
                .build()
        }
    }

    @Test
    fun `lone card never leaves the stack bounds`() = assertStaysInBounds(cards = 1)

    /** Two cards fan by 64dp — less slack than the 88dp inset, so they must not pin either. */
    @Test
    fun `short stack never leaves the stack bounds`() = assertStaysInBounds(cards = 2)

    @Test
    fun `tall stack never leaves the stack bounds`() = assertStaysInBounds(cards = 5)

    /**
     * Renders [cards] tokens and scrolls the stack far past the point where every card has collapsed,
     * asserting each card is still inside the stack's reported bounds.
     */
    private fun assertStaysInBounds(cards: Int) {
        val tokens = List(cards) { index ->
            TokenWithLocalizedBalance(
                token = if (index == 0) Token.usdf else Token.usdc,
                balance = LocalFiat(
                    usdf = Fiat(quarks = 1_000_000L),
                    nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
                ),
                displayName = "Token $index",
            )
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview {
                Box(modifier = Modifier.width(360.dp)) {
                    TokenCardStack(
                        tokens = tokens,
                        modifier = Modifier.testTag(StackTag),
                        // Status bar + a grid unit, as the wallet screen passes.
                        pinInset = 88.dp,
                        // Well past `collapseComplete` for any of these stacks: the deck has finished
                        // collapsing and is scrolling off with the list.
                        scrolledPast = { 5_000f },
                    )
                }
            }
        }
        // Pump frames rather than waiting for idle — the card's async icon keeps scheduling work.
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val stackNode = composeRule.onNodeWithTag(StackTag).fetchSemanticsNode()
        val stackTop = stackNode.positionInRoot.y.toDp()
        val stackBottom = stackTop + stackNode.size.height.toDp()
        val cardNodes = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(cardNodes.size == cards, "expected $cards cards, found ${cardNodes.size}")

        cardNodes.forEachIndexed { index, node ->
            val top = node.positionInRoot.y.toDp()
            val bottom = top + node.size.height.toDp()
            assertTrue(
                top >= stackTop - Tolerance,
                "card $index top ($top) is above the stack ($stackTop)",
            )
            assertTrue(
                bottom <= stackBottom + Tolerance,
                "card $index bottom ($bottom) is below the stack ($stackBottom)",
            )
        }
    }

    private fun Float.toDp(): Dp = with(composeRule.density) { this@toDp.toDp() }
    private fun Int.toDp(): Dp = with(composeRule.density) { this@toDp.toDp() }

    private companion object {
        const val StackTag = "tokenCardStack"
        val Tolerance = 1.dp
    }
}
