package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.LocalLinkCardResolution
import com.flipcash.shared.chat.models.LocalWebLinkPreviewMode
import com.flipcash.shared.chat.models.WebLinkPreviewMode
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A message that is only a web link draws its resolved card with no bubble around it, in the
 * bubble's corners. Anything short of a resolved card -- loading, empty, failed, the chip, the
 * previews being off -- keeps the text bubble with the link, so a message never disappears.
 */
@RunWith(RobolectricTestRunner::class)
class WebBareCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val url = "https://www.example.com/a"
    private val resolved = LinkCard.Web.State.Resolved(
        title = "The title",
        description = "A description",
        imageUrl = null,
        host = "example.com",
    )

    private class FakeResolution(
        var answer: suspend (LinkCard) -> LinkCard,
        val peeked: MutableMap<String, LinkCard> = mutableMapOf(),
    ) : LinkCardResolution {
        override val revision = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard? = peeked[(card as LinkCard.Web).url]
        override suspend fun resolve(card: LinkCard): LinkCard = answer(card)
    }

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private fun answering(state: LinkCard.Web.State) =
        FakeResolution({ (it as LinkCard.Web).copy(state = state) })

    private val neverAnswers get() = FakeResolution({ it })

    private fun webItem(
        text: String = url,
        isFromSelf: Boolean = false,
        isEdited: Boolean = false,
        content: MessageContent = MessageContent.Text(text),
        quote: ChatQuote? = null,
    ): ChatListItem.ContentBubble {
        val start = text.indexOf(url)
        return ChatListItem.ContentBubble(
            messageId = 1,
            contentIndex = 0,
            content = content,
            isFromSelf = isFromSelf,
            timestamp = Instant.fromEpochSeconds(1_000),
            isEdited = isEdited,
            quote = quote,
            linkCard = LinkCard.Web(url = url, start = start, end = start + url.length),
        )
    }

    private val reply = ChatQuote(
        messageId = 9,
        authorName = "Ada",
        snippet = ChatQuoteSnippet.Text("did you see this?"),
        accent = null,
        nameAccent = null,
    )

    private var expectedShape: Shape? = null

    private fun setBubble(
        resolution: LinkCardResolution,
        item: ChatListItem.ContentBubble = webItem(),
        mode: WebLinkPreviewMode = WebLinkPreviewMode.Automatic,
        position: BubblePosition = BubblePosition.Solo,
        uriHandler: UriHandler = RecordingUriHandler(),
        onDoubleClick: (() -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalLinkCardResolution provides resolution,
                    LocalWebLinkPreviewMode provides mode,
                    LocalUriHandler provides uriHandler,
                ) {
                    expectedShape = bubbleShape(position, item.isFromSelf)
                    ContentBubble(
                        item = item,
                        position = position,
                        onLongClick = {},
                        onDoubleClick = onDoubleClick,
                    )
                }
            }
        }
    }

    private val drawnBare = SemanticsMatcher.keyIsDefined(LinkCardShapeKey)

    private fun assertBare() {
        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodes(drawnBare, useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(url, substring = true).assertCountEquals(0)
    }

    private fun assertPlaceholderInPlaceOfBubble() {
        composeTestRule.onNodeWithTag(WEB_LINK_PLACEHOLDER_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodes(drawnBare, useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(url, substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertNoPlaceholder() {
        composeTestRule.onAllNodesWithTag(WEB_LINK_PLACEHOLDER_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertInTextBubble() {
        composeTestRule.onAllNodes(drawnBare, useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onNodeWithText(url, substring = true).assertIsDisplayed()
    }

    @Test
    fun `a link-only message with a resolved card draws the card alone in the bubble's corners`() {
        setBubble(answering(resolved), position = BubblePosition.First)

        composeTestRule.waitForIdle()
        assertBare()
        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        val shape = composeTestRule.onAllNodes(drawnBare, useUnmergedTree = true)
            .fetchSemanticsNodes().single().config[LinkCardShapeKey]
        assertEquals(expectedShape, shape)
    }

    @Test
    fun `a card still loading holds its place with a placeholder and no link text`() {
        setBubble(neverAnswers, position = BubblePosition.First)

        composeTestRule.waitForIdle()
        assertPlaceholderInPlaceOfBubble()
        val node = composeTestRule.onNodeWithTag(WEB_LINK_PLACEHOLDER_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(listOf(url), node.config[SemanticsProperties.ContentDescription])
        assertEquals(expectedShape, node.config[LinkCardShapeKey])
        // The host comes from the link that was sent, without its "www.".
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
    }

    @Test
    fun `a loading card that resolves swaps to the card in place`() {
        val gate = CompletableDeferred<Unit>()
        setBubble(FakeResolution({ gate.await(); (it as LinkCard.Web).copy(state = resolved) }))
        composeTestRule.waitForIdle()
        assertPlaceholderInPlaceOfBubble()

        val placeholderBounds = composeTestRule
            .onNodeWithTag(WEB_LINK_PLACEHOLDER_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        gate.complete(Unit)
        composeTestRule.waitForIdle()

        assertBare()
        assertNoPlaceholder()
        // Same slot: the card takes the width and the corner of the panel it replaces.
        val cardBounds = composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).getUnclippedBoundsInRoot()
        assertEquals(placeholderBounds.right - placeholderBounds.left, cardBounds.right - cardBounds.left)
        assertEquals(placeholderBounds.left, cardBounds.left)
    }

    @Test
    fun `a loading card that comes back empty falls back to the text bubble with the link`() {
        val gate = CompletableDeferred<Unit>()
        setBubble(FakeResolution({ gate.await(); (it as LinkCard.Web).copy(state = LinkCard.Web.State.None) }))
        composeTestRule.waitForIdle()
        assertPlaceholderInPlaceOfBubble()

        gate.complete(Unit)
        composeTestRule.waitForIdle()

        assertInTextBubble()
        assertNoPlaceholder()
    }

    @Test
    fun `an answer held as empty draws the text bubble on the first frame with no placeholder`() {
        val resolution = neverAnswers
        resolution.peeked[url] = LinkCard.Web(url, 0, url.length, LinkCard.Web.State.None)
        composeTestRule.mainClock.autoAdvance = false

        setBubble(resolution)

        assertInTextBubble()
        assertNoPlaceholder()
    }

    @Test
    fun `text beside a loading link keeps the text bubble and no placeholder`() {
        setBubble(neverAnswers, item = webItem(text = "look at $url now"))

        composeTestRule.waitForIdle()
        assertInTextBubble()
        assertNoPlaceholder()
    }

    @Test
    fun `a placeholder opens the link on a single tap after the double-tap window`() {
        val handler = RecordingUriHandler()
        setBubble(neverAnswers, uriHandler = handler, onDoubleClick = {})
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(WEB_LINK_PLACEHOLDER_TAG).performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)

        assertEquals(listOf(url), handler.opened)
    }

    @Test
    fun `a double tap on the placeholder reacts and opens nothing`() {
        val handler = RecordingUriHandler()
        var reacts = 0
        setBubble(neverAnswers, uriHandler = handler, onDoubleClick = { reacts++ })
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(WEB_LINK_PLACEHOLDER_TAG).performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, reacts)
        assertEquals(emptyList(), handler.opened)
    }

    @Test
    fun `a long press on the placeholder acts on the message`() {
        var longPresses = 0
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalLinkCardResolution provides neverAnswers,
                    LocalWebLinkPreviewMode provides WebLinkPreviewMode.Automatic,
                ) {
                    ContentBubble(item = webItem(), position = BubblePosition.Solo, onLongClick = { longPresses++ })
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(WEB_LINK_PLACEHOLDER_TAG).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, longPresses)
    }

    @Test
    fun `the chip shows no placeholder, and tapping it holds the place with one`() {
        setBubble(neverAnswers, mode = WebLinkPreviewMode.TapToLoad)
        composeTestRule.waitForIdle()
        assertInTextBubble()
        assertNoPlaceholder()
        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
        assertPlaceholderInPlaceOfBubble()
    }

    @Test
    fun `with previews off a loading link stays a plain text bubble with no placeholder`() {
        setBubble(neverAnswers, mode = WebLinkPreviewMode.Off)

        composeTestRule.waitForIdle()
        assertInTextBubble()
        assertNoPlaceholder()
    }

    @Test
    fun `an empty answer keeps the text bubble with the link`() {
        setBubble(answering(LinkCard.Web.State.None))

        composeTestRule.waitForIdle()
        assertInTextBubble()
    }

    @Test
    fun `the tap-to-load chip keeps the text bubble with the link`() {
        setBubble(neverAnswers, mode = WebLinkPreviewMode.TapToLoad)

        composeTestRule.waitForIdle()
        assertInTextBubble()
        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).assertIsDisplayed()
    }

    @Test
    fun `a fetch that failed after the chip keeps the text bubble with the link`() {
        setBubble(answering(LinkCard.Web.State.None), mode = WebLinkPreviewMode.TapToLoad)

        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).performClick()
        composeTestRule.waitForIdle()

        assertInTextBubble()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `with previews off the message stays a plain text bubble`() {
        val resolution = answering(resolved)
        resolution.peeked[url] = LinkCard.Web(url, 0, url.length, resolved)
        setBubble(resolution, mode = WebLinkPreviewMode.Off)

        composeTestRule.waitForIdle()
        assertInTextBubble()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun `text beside the link keeps the card inside the bubble under the text`() {
        setBubble(answering(resolved), item = webItem(text = "look at $url now"))

        composeTestRule.waitForIdle()
        assertInTextBubble()
        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `a link wrapped in punctuation counts as the link alone`() {
        setBubble(answering(resolved), item = webItem(text = "($url)."))

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(drawnBare, useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(url, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a card already held is drawn bare on the first frame with no bubble before it`() {
        // The lookup never answers, so the held answer is all the first frame has.
        val resolution = neverAnswers
        resolution.peeked[url] = LinkCard.Web(url, 0, url.length, resolved)
        composeTestRule.mainClock.autoAdvance = false

        setBubble(resolution)

        assertBare()
        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
    }

    @Test
    fun `a held answer that expires and comes back empty returns to the text bubble`() {
        val resolution = answering(resolved)
        resolution.peeked[url] = LinkCard.Web(url, 0, url.length, resolved)
        setBubble(resolution)
        composeTestRule.waitForIdle()
        assertBare()

        resolution.peeked.clear()
        resolution.answer = { (it as LinkCard.Web).copy(state = LinkCard.Web.State.None) }
        resolution.revision.value += 1
        composeTestRule.waitForIdle()

        assertInTextBubble()
    }

    @Test
    fun `a link-only reply keeps its citation above the bare card`() {
        val item = webItem(
            content = MessageContent.Reply(repliedMessageId = 9, content = listOf(MessageContent.Text(url))),
            quote = reply,
        )
        setBubble(answering(resolved), item = item)

        composeTestRule.waitForIdle()
        assertBare()
        val quote = composeTestRule.onNodeWithTag(REPLY_QUOTE_TAG).getUnclippedBoundsInRoot()
        val card = composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).getUnclippedBoundsInRoot()
        assertTrue(quote.bottom <= card.top, "citation $quote sits above card $card")
    }

    @Test
    fun `a double tap on the bare card reacts and opens nothing`() {
        val handler = RecordingUriHandler()
        var reacts = 0
        setBubble(answering(resolved), uriHandler = handler, onDoubleClick = { reacts++ })
        composeTestRule.waitForIdle()
        assertBare()

        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, reacts)
        assertEquals(emptyList(), handler.opened)
    }

    @Test
    fun `a double tap on the card inside a bubble reacts and opens nothing`() {
        val handler = RecordingUriHandler()
        var reacts = 0
        setBubble(
            answering(resolved),
            item = webItem(text = "look at $url now"),
            uriHandler = handler,
            onDoubleClick = { reacts++ },
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, reacts)
        assertEquals(emptyList(), handler.opened)
    }

    @Test
    fun `a single tap on the bare card opens the page after the double-tap window`() {
        val handler = RecordingUriHandler()
        setBubble(answering(resolved), uriHandler = handler, onDoubleClick = {})
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).performClick()
        assertEquals(emptyList(), handler.opened)
        composeTestRule.mainClock.advanceTimeBy(1_000)

        assertEquals(listOf(url), handler.opened)
    }

    @Test
    fun `the tap-to-load chip acts on the first tap and takes no double tap`() {
        var reacts = 0
        setBubble(neverAnswers, mode = WebLinkPreviewMode.TapToLoad, onDoubleClick = { reacts++ })
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
        assertEquals(0, reacts)
    }

    @Test
    fun `an edited bare web card is told to draw its marker below`() {
        var bare: Boolean? = null
        val resolution = answering(resolved)
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(LocalLinkCardResolution provides resolution) {
                    bare = webItem(isEdited = true).rememberRendersBare()
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(true, bare)
    }

    @Test
    fun `a web card that came back empty does not count as bare`() {
        var bare: Boolean? = null
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(LocalLinkCardResolution provides answering(LinkCard.Web.State.None)) {
                    bare = webItem(isEdited = true).rememberRendersBare()
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(false, bare)
    }

    @Test
    fun `a web card still loading counts as bare so its marker clears the placeholder`() {
        var bare: Boolean? = null
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(LocalLinkCardResolution provides neverAnswers) {
                    bare = webItem(isEdited = true).rememberRendersBare()
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(true, bare)
    }
}
