package com.flipcash.shared.chat.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.LocalLinkCardResolution
import com.flipcash.shared.chat.models.LocalWebLinkPreviewMode
import com.flipcash.shared.chat.models.LocalWebPreviewImageLoader
import com.flipcash.shared.chat.models.WebLinkPreviewMode
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * What a web card draws and when it asks. Loading and an empty answer draw nothing, a viewer outside
 * the group asks only when told to, and the whole card opens the page through the host's handler.
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

    private class FakeResolution(private val answer: (LinkCard) -> LinkCard) : LinkCardResolution {
        val calls = AtomicInteger()
        val peeked: MutableMap<String, LinkCard> = mutableMapOf()
        override val revision: StateFlow<Int> = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard? = peeked[(card as LinkCard.Web).url]
        override suspend fun resolve(card: LinkCard): LinkCard {
            calls.incrementAndGet()
            return answer(card)
        }
    }

    private fun card(state: LinkCard.Web.State = LinkCard.Web.State.Loading) =
        LinkCard.Web(url = url, start = 0, end = url.length, state = state)

    private fun resolving(state: LinkCard.Web.State) =
        FakeResolution { (it as LinkCard.Web).copy(state = state) }

    private fun setCard(
        resolution: FakeResolution,
        mode: WebLinkPreviewMode = WebLinkPreviewMode.Automatic,
        uriHandler: UriHandler = RecordingUriHandler(),
        imageLoader: ImageLoader? = null,
        onLongClick: (() -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalLinkCardResolution provides resolution,
                    LocalWebLinkPreviewMode provides mode,
                    LocalWebPreviewImageLoader provides imageLoader,
                    LocalUriHandler provides uriHandler,
                ) {
                    Card(onLongClick)
                }
            }
        }
    }

    @Composable
    private fun Card(onLongClick: (() -> Unit)?) {
        val state = rememberWebLinkCard(card())
        WebLinkCard(state = state, onLongClick = onLongClick, onDoubleClick = null)
    }

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun `a resolved card draws its title description and host`() {
        setCard(resolving(resolved))

        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        composeTestRule.onNodeWithText("A description").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("og:site_name").assertCountEquals(0)
    }

    @Test
    fun `an empty answer draws nothing`() {
        setCard(resolving(LinkCard.Web.State.None))

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `a card still loading in automatic mode draws nothing`() {
        // The lookup never answers in this run: the card stays in its loading state.
        setCard(FakeResolution { it })

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `outside the group nothing is asked until the chip is tapped and then it is asked once`() {
        val resolution = resolving(resolved)
        setCard(resolution, mode = WebLinkPreviewMode.TapToLoad)

        composeTestRule.waitForIdle()
        assertEquals(0, resolution.calls.get())
        // The host reads without its www.
        composeTestRule.onNodeWithText("Show preview · example.com").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)

        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, resolution.calls.get())
        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `a fetch that fails after the chip is tapped shows nothing and no chip`() {
        val resolution = FakeResolution { it }
        setCard(resolution, mode = WebLinkPreviewMode.TapToLoad)

        composeTestRule.onNodeWithTag(WEB_LINK_CHIP_TAG).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, resolution.calls.get())
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `an answer already held is drawn outside the group without a tap`() {
        val resolution = FakeResolution { it }
        resolution.peeked[url] = card(resolved)
        setCard(resolution, mode = WebLinkPreviewMode.TapToLoad)

        composeTestRule.waitForIdle()
        assertEquals(0, resolution.calls.get())
        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
    }

    @Test
    fun `with previews off a non-member sees no chip and nothing is asked`() {
        val resolution = resolving(resolved)
        setCard(resolution, mode = WebLinkPreviewMode.Off)

        composeTestRule.waitForIdle()
        assertEquals(0, resolution.calls.get())
        composeTestRule.onAllNodesWithTag(WEB_LINK_CHIP_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun `with previews off no card is drawn in place of automatic one nor from a held answer`() {
        val resolution = resolving(resolved)
        resolution.peeked[url] = card(resolved)
        setCard(resolution, mode = WebLinkPreviewMode.Off)

        composeTestRule.waitForIdle()
        assertEquals(0, resolution.calls.get())
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("The title").assertCountEquals(0)
    }

    @Test
    fun `flipping previews on while the message is drawn brings the card back`() {
        val resolution = resolving(resolved)
        val mode = mutableStateOf(WebLinkPreviewMode.Off)
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalLinkCardResolution provides resolution,
                    LocalWebLinkPreviewMode provides mode.value,
                    LocalUriHandler provides RecordingUriHandler(),
                ) {
                    Card(onLongClick = null)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)

        mode.value = WebLinkPreviewMode.Automatic
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
    }

    @Test
    fun `a click on a resolved card opens the card url`() {
        val handler = RecordingUriHandler()
        setCard(resolving(resolved), uriHandler = handler)

        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).performClick()

        assertEquals(listOf(url), handler.opened)
    }

    @Test
    fun `an image that fails to load drops its slot and keeps the text`() {
        val loader = ImageLoader.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .components { add(FailingFetcherFactory) }
            .build()
        setCard(
            resolving(resolved.copy(imageUrl = "https://img.example.com/p.png")),
            imageLoader = loader,
        )

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(WEB_LINK_IMAGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                composeTestRule.onAllNodesWithText("The title").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
    }

    @Test
    fun `the text sits inside the panel by ten dp and the picture meets its top and sides`() {
        val loader = ImageLoader.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .components { add(HangingFetcherFactory) }
            .build()
        setCard(
            resolving(resolved.copy(imageUrl = "https://img.example.com/p.png")),
            imageLoader = loader,
        )

        val panel = composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).getUnclippedBoundsInRoot()
        val image = composeTestRule.onNodeWithTag(WEB_LINK_IMAGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val host = composeTestRule.onNodeWithText("example.com", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val description = composeTestRule.onNodeWithText("A description", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(panel.top, image.top)
        assertEquals(panel.left, image.left)
        assertEquals(panel.right, image.right)
        assertEquals(image.bottom + 10.dp, host.top)
        assertEquals(panel.left + 10.dp, host.left)
        assertEquals(panel.bottom - 10.dp, description.bottom)
    }

    @Test
    fun `a picture still loading keeps its slot`() {
        val loader = ImageLoader.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .components { add(HangingFetcherFactory) }
            .build()
        setCard(
            resolving(resolved.copy(imageUrl = "https://img.example.com/p.png")),
            imageLoader = loader,
        )

        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(WEB_LINK_IMAGE_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `no picture is asked for without the preview loader`() {
        setCard(resolving(resolved.copy(imageUrl = "https://img.example.com/p.png")))

        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(WEB_LINK_IMAGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @OptIn(ExperimentalCoilApi::class)
    private object HangingFetcherFactory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher =
            Fetcher { awaitCancellation() }
    }

    @OptIn(ExperimentalCoilApi::class)
    private object FailingFetcherFactory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher =
            Fetcher { throw IOException("no image") }
    }
}
