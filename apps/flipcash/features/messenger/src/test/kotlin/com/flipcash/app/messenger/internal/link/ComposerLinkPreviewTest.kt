package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.DetectedUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The composer's preview follows the draft's first web link, and the × holds only for that link.
 *
 * Runs on real dispatchers: the page comes back on OkHttp's thread, which virtual time can't wait on.
 */
@RunWith(RobolectricTestRunner::class)
class ComposerLinkPreviewTest {

    private val chatId = ChatId(UUID.randomUUID().toString().toByteArray().take(16))

    private val dispatchers = object : DispatcherProvider {
        override val Default: CoroutineDispatcher = Dispatchers.Default
        override val Main: CoroutineDispatcher = Dispatchers.Default
        override val IO: CoroutineDispatcher = Dispatchers.IO
    }

    // Every detected link is a web card.
    private val classifier = mock<LinkCardClassifier> {
        on { firstCard(any()) } doAnswer { call ->
            @Suppress("UNCHECKED_CAST")
            (call.arguments[0] as List<DetectedUrl>).firstOrNull()?.let { LinkCard.Web(it.url, it.start, it.end) }
        }
    }

    // Every page has a title naming its url, and no image.
    private val pages = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val html = "<html><head><meta property=\"og:title\" content=\"Title of ${request.url}\"></head></html>"
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }
        .build()

    private fun withPreview(draft: MutableStateFlow<String>, block: suspend (ComposerLinkPreview) -> Unit) =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default)
            val preview = ComposerLinkPreview(
                classifier = classifier,
                lookup = WebLinkLookup(pages, dispatchers = dispatchers),
                imageClient = mock(),
                uploads = mock(),
                images = mock(),
                context = RuntimeEnvironment.getApplication(),
                dispatchers = dispatchers,
            )
            try {
                preview.bind(scope, chatId, draft)
                block(preview)
            } finally {
                scope.cancel()
            }
        }

    private suspend fun ComposerLinkPreview.awaitCard(url: String): ComposerLinkPreviewState.Ready =
        withTimeout(5.seconds) {
            state.first { it is ComposerLinkPreviewState.Ready && it.url == url } as ComposerLinkPreviewState.Ready
        }

    /** Long enough for a draft change to pass the debounce and settle. */
    private suspend fun settle() = delay(ComposerLinkPreview.DEBOUNCE + 300.milliseconds)

    @Test
    fun `a draft link shows a ready preview`() = withPreview(MutableStateFlow("look https://example.com/a")) { preview ->
        val card = preview.awaitCard("https://example.com/a")

        assertEquals("example.com", card.host)
        assertEquals("Title of https://example.com/a", card.title)
    }

    @Test
    fun `a capitalised scheme gets no preview, since the send could not carry it`() =
        withPreview(MutableStateFlow("Https://example.com/a")) { preview ->
            settle()

            assertNull(preview.state.value)
            assertNull(preview.take("Https://example.com/a"))
        }

    @Test
    fun `removing keeps the preview off while the link stays`() {
        val draft = MutableStateFlow("look https://example.com/a")
        withPreview(draft) { preview ->
            preview.awaitCard("https://example.com/a")

            preview.dismiss()
            draft.value = "look https://example.com/a and more"
            settle()

            assertNull(preview.state.value)
            assertNull(preview.take(draft.value))
        }
    }

    @Test
    fun `editing the removed link brings a preview back`() {
        val draft = MutableStateFlow("look https://example.com/a")
        withPreview(draft) { preview ->
            preview.awaitCard("https://example.com/a")

            preview.dismiss()
            draft.value = "look https://example.com/b"

            preview.awaitCard("https://example.com/b")
        }
    }

    @Test
    fun `typing the removed link again after deleting it brings it back`() {
        val draft = MutableStateFlow("look https://example.com/a")
        withPreview(draft) { preview ->
            preview.awaitCard("https://example.com/a")

            preview.dismiss()
            draft.value = "look"
            settle()
            draft.value = "look https://example.com/a"

            preview.awaitCard("https://example.com/a")
        }
    }

    @Test
    fun `take hands over the ready preview and drops the card`() {
        val draft = MutableStateFlow("look https://example.com/a")
        withPreview(draft) { preview ->
            preview.awaitCard("https://example.com/a")

            val taken = preview.take(draft.value)

            assertEquals("https://example.com/a", taken?.url)
            assertEquals("Title of https://example.com/a", taken?.title)
            assertNull(preview.state.value)
        }
    }
}
