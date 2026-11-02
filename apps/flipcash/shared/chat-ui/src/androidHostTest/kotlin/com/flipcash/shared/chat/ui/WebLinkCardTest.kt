package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * What a web card draws: the preview its sender built and nothing else. A card with no preview
 * draws nothing, and the whole card opens the page through the host's handler.
 */
@RunWith(RobolectricTestRunner::class)
class WebLinkCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val url = "https://www.example.com/a"
    private val resolved = LinkCard.Web.State.Resolved(
        title = "The title",
        description = "A description",
        imageUrl = null,
        host = "example.com",
    )

    private fun card(state: LinkCard.Web.State = LinkCard.Web.State.Loading) =
        LinkCard.Web(url = url, start = 0, end = url.length, state = state)

    private fun setCard(seed: LinkCard.Web, uriHandler: UriHandler = RecordingUriHandler()) {
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    Card(seed)
                }
            }
        }
    }

    @Composable
    private fun Card(seed: LinkCard.Web) {
        WebLinkCard(state = rememberWebLinkCard(seed), onLongClick = null, onDoubleClick = null)
    }

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun `a preview the sender built draws its title description and host`() {
        setCard(card(resolved))

        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        composeTestRule.onNodeWithText("A description").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
    }

    @Test
    fun `a card still loading draws nothing`() {
        setCard(card(LinkCard.Web.State.Loading))

        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun `a card with an empty answer draws nothing`() {
        setCard(card(LinkCard.Web.State.None))

        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun `a click on the card opens the card url`() {
        val handler = RecordingUriHandler()
        setCard(card(resolved), uriHandler = handler)

        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).performClick()

        assertEquals(listOf(url), handler.opened)
    }
}
