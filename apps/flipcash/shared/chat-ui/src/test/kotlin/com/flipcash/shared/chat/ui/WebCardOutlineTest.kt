package com.flipcash.shared.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * L6: a bare web card draws an outline, so its edge shows against the chat even when the page's
 * image is opaque and close to the chat's own colour (nhl.com's is). A card inside a bubble has the
 * bubble for an edge and draws none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h600dp-xhdpi")
class WebCardOutlineTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val url = "https://www.example.com/a"
    private val resolved = LinkCard.Web.State.Resolved(
        title = "The title",
        description = "A description",
        imageUrl = null,
        host = "example.com",
    )

    private fun state(resolved: LinkCard.Web.State.Resolved?, loading: Boolean) = WebLinkCardState(
        url = url,
        resolved = resolved,
        chipHost = null,
        loading = loading,
        host = "example.com",
        ask = {},
    )

    /**
     * The pixel at mid width on the panel's bottom row, and one a few rows above it. Both sit on the
     * text area's plain fill (no shimmer or text that low), so they differ only if an edge is drawn.
     */
    private fun edgeAndInside(state: WebLinkCardState, bare: Boolean): Pair<Color, Color> {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DesignSystem {
                Box(modifier = Modifier.background(Color.Black).padding(40.dp)) {
                    WebLinkCard(
                        state = state,
                        modifier = Modifier.width(300.dp),
                        bareShape = if (bare) RoundedCornerShape(18.dp) else null,
                    )
                }
            }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        val tag = if (state.loading) WEB_LINK_PLACEHOLDER_TAG else WEB_LINK_CARD_TAG
        val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val x = pixels.width / 2
        return pixels[x, pixels.height - 1] to pixels[x, pixels.height - 4]
    }

    @Test
    fun `a bare resolved card draws an edge unlike its panel`() {
        val (edge, inside) = edgeAndInside(state(resolved, loading = false), bare = true)
        assertNotEquals(inside, edge, "the bottom row should be the outline, not the panel")
    }

    @Test
    fun `a bare placeholder draws the same edge`() {
        val (edge, inside) = edgeAndInside(state(null, loading = true), bare = true)
        assertNotEquals(inside, edge, "the bottom row should be the outline, not the panel")
    }

    @Test
    fun `a card inside a bubble draws no outline`() {
        val (edge, inside) = edgeAndInside(state(resolved, loading = false), bare = false)
        assertEquals(inside, edge, "an in-bubble card's bottom row should be its panel")
    }
}
