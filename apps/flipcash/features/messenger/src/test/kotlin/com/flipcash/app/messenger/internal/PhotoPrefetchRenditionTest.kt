package com.flipcash.app.messenger.internal

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import com.flipcash.app.messenger.internal.screens.components.MessageRow
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MediaItemRendition.Role
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.shared.chat.ui.media.BubblePhotoPrefetchTarget
import com.getcode.theme.CodeTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A prefetch is only worth its download if the bubble later asks for the same blob, since the disk
 * cache is keyed by blob id. Each case draws the real transcript row at the screen's width, records
 * the blob the bubble requests, and checks the prefetcher picks it from the same screen metrics.
 *
 * 411dp at 420dpi is about 1080px wide: a direct message's bubble is about 842px, past the 800px
 * rendition, and a group's incoming bubble loses the avatar column and lands under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PhotoPrefetchRenditionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chatId = ChatId(ByteArray(32) { 7 })
    private val sender = SenderIdentity(userId = listOf<Byte>(2), displayName = "Chloe Anderson", picture = null)

    private fun rendition(role: Role, id: Int, width: Int, height: Int) = MediaItemRendition(
        role = role,
        blobId = BlobId(ByteArray(32) { id.toByte() }),
        blob = BlobMetadata(
            mimeType = "image/jpeg",
            sizeBytes = width.toLong() * height,
            downloadUrl = "https://cdn.example/$id",
            image = ImageMetadata(width = width, height = height, blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
        ),
    )

    private val original = rendition(Role.ORIGINAL, id = 1, width = 4032, height = 3024)
    private val display800 = rendition(Role.DISPLAY, id = 2, width = 800, height = 600)
    private val display1600 = rendition(Role.DISPLAY, id = 3, width = 1600, height = 1200)

    private val derived = MediaItem(listOf(original, display800, display1600))
    private val originalOnly = MediaItem(listOf(original))

    @After
    fun resetLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `a direct message photo with derived renditions`() =
        assertSameBlob(derived, sealed = false, group = false, expected = display1600)

    @Test
    fun `a group photo from someone else, drawn beside the avatar column`() =
        assertSameBlob(derived, sealed = false, group = true, expected = display800)

    @Test
    fun `a photo with no derived renditions`() =
        assertSameBlob(originalOnly, sealed = false, group = false, expected = original)

    @Test
    fun `a sealed photo`() =
        assertSameBlob(originalOnly, sealed = true, group = true, expected = original)

    private fun assertSameBlob(item: MediaItem, sealed: Boolean, group: Boolean, expected: MediaItemRendition) {
        val requested = mutableListOf<BlobId>()
        var rowsComposed = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(composeRule.activity)
                .coroutineContext(Dispatchers.Unconfined)
                .components {
                    add(
                        Interceptor { chain ->
                            (chain.request.data as? ChatPhoto)?.let { requested += it.rendition.blobId }
                            ErrorResult(null, chain.request, IllegalStateException("not loaded in tests"))
                        },
                    )
                }
                .build(),
        )
        val bubble = ChatListItem.ContentBubble(
            messageId = 10,
            contentIndex = 0,
            content = MessageContent.Media(items = listOf(item), caption = null),
            isFromSelf = false,
            timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000),
            sender = sender,
            photo = PhotoMessageContext(
                chatId = chatId,
                senderId = sender.userId,
                sealed = sealed,
                redacted = false,
                previewing = false,
            ),
        )

        // The photo's placeholder keeps scheduling frames, so the clock is pumped by hand rather than
        // waited on until idle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashThemeWrapper().Wrap {
                // A StateFlow's replay cache is presented on the first composition, with no paging
                // round trip through the paused clock.
                val messages = MutableStateFlow(PagingData.from(listOf<ChatListItem>(bubble))).collectAsLazyPagingItems()
                // The transcript's list insets its rows by the theme's inset on each side.
                Box(Modifier.fillMaxWidth().padding(horizontal = CodeTheme.dimens.inset)) {
                    if (messages.itemCount > 0) {
                        rowsComposed++
                        MessageRow(
                            index = 0,
                            item = messages[0]!!,
                            messages = messages,
                            separatorConfig = SeparatorConfig.DayOnly,
                            otherReadPointer = null,
                            selecting = false,
                            focused = true,
                            raised = false,
                            animateInsertion = false,
                            showsSenderGutter = group,
                        )
                    }
                }
            }
        }
        repeat(20) {
            composeRule.mainClock.advanceTimeByFrame()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
        }

        val prefetched = BubblePhotoPrefetchTarget(composeRule.activity).rendition(item, groupIncoming = group)

        assertEquals(
            listOf(expected.blobId),
            requested.distinct(),
            "the blob the bubble asked for (row composed $rowsComposed times)",
        )
        assertEquals(expected.blobId, prefetched?.blobId, "the blob the prefetcher picked")
    }
}
