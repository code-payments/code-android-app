package com.flipcash.shared.chat.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.flipcash.shared.chat.models.PhotoStack
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ContentBubble
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Photos sent together draw as one card stack, split across their rows: the first draws the cards,
 * the middle ones nothing, the last the caption. The viewer opened on the stack pages through it.
 */
@RunWith(RobolectricTestRunner::class)
class ChatPhotoStackUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val chatId = ChatId(ByteArray(16) { it.toByte() })

    private fun media(caption: String? = null) = MessageContent.Media(
        items = listOf(
            MediaItem(
                listOf(
                    MediaItemRendition(
                        role = MediaItemRendition.Role.ORIGINAL,
                        blobId = BlobId(ByteArray(32) { 1 }),
                        blob = BlobMetadata(
                            mimeType = "image/jpeg",
                            sizeBytes = 10,
                            downloadUrl = "https://cdn/blob",
                            image = ImageMetadata(width = 800, height = 600, blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
                        ),
                    ),
                ),
            ),
        ),
        caption = caption?.let { MessageContent.Text(it) },
    )

    private fun bubble(id: Long, caption: String? = null) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = media(caption),
        isFromSelf = false,
        timestamp = Instant.fromEpochSeconds(1_000 + id),
        photo = PhotoMessageContext(chatId, sealed = false, redacted = false, previewing = false),
    )

    private val members = listOf(bubble(1), bubble(2), bubble(3, caption = "sunset"))

    private fun stackOf(role: PhotoStack.Role, id: Long) = PhotoStack(role, members).let {
        if (it.members.any { m -> m.messageId == id }) it.copy(role = role) else it
    }

    /** The three rows of the stack as the transcript draws them, each reporting its long press. */
    private fun setStack(
        longPresses: MutableList<Long> = mutableListOf(),
        actions: MutableList<ChatAction> = mutableListOf(),
    ) = rule.setContent {
        DesignSystem {
            CompositionLocalProvider(LocalChatActionHandler provides { actions += it }) {
                Column {
                    listOf(PhotoStack.Role.Front, PhotoStack.Role.Middle, PhotoStack.Role.Last).forEachIndexed { i, role ->
                        ContentBubble(
                            item = members[i],
                            position = BubblePosition.Solo,
                            stack = stackOf(role, members[i].messageId),
                            onLongClick = { longPresses += members[i].messageId },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the last row of a stack with no caption takes no height`() {
        // An empty row under the stack would open space before the next message; the gap below a
        // stack has to be the row gap alone, as under a single photo.
        val plain = listOf(bubble(1), bubble(2))
        rule.setContent {
            DesignSystem {
                Box(Modifier.testTag("last")) {
                    ContentBubble(
                        item = plain[1],
                        position = BubblePosition.Solo,
                        stack = PhotoStack(PhotoStack.Role.Last, plain),
                    )
                }
            }
        }
        assertEquals(0f, rule.onNodeWithTag("last").fetchSemanticsNode().boundsInRoot.height)
    }

    @Test
    fun `the stack shows how many photos it holds and says so to a screen reader`() {
        setStack()

        rule.onNodeWithTag(PHOTO_STACK_PILL_TAG, useUnmergedTree = true).assertIsDisplayed().assertTextEquals("3 photos")
        rule.onNodeWithContentDescription("Stack of 3 photos", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `only the front row draws a photo and the last row keeps the caption`() {
        setStack()

        rule.onAllNodesWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("sunset").assertIsDisplayed()
    }

    @Test
    fun `a long press on the stack acts on the front message and on the caption the last`() {
        val longPresses = mutableListOf<Long>()
        setStack(longPresses)

        rule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf(1L), longPresses)

        rule.onNodeWithText("sunset").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf(1L, 3L), longPresses)
    }

    @Test
    fun `a stack draws one card behind per photo after the first, at most two`() {
        val pair = PhotoStack(PhotoStack.Role.Front, members.take(2))
        rule.setContent {
            DesignSystem {
                Column {
                    ContentBubble(item = members[0], position = BubblePosition.Solo, stack = pair, modifier = Modifier.testTag("pair"))
                    ContentBubble(
                        item = members[0],
                        position = BubblePosition.Solo,
                        stack = stackOf(PhotoStack.Role.Front, 1L),
                        modifier = Modifier.testTag("three"),
                    )
                }
            }
        }

        rule.onNode(hasTestTag(PHOTO_STACK_CARD_TAG) and hasAnyAncestor(hasTestTag("pair")), useUnmergedTree = true)
            .assertExists()
        rule.onAllNodes(hasTestTag(PHOTO_STACK_CARD_TAG) and hasAnyAncestor(hasTestTag("pair")), useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onAllNodes(hasTestTag(PHOTO_STACK_CARD_TAG) and hasAnyAncestor(hasTestTag("three")), useUnmergedTree = true)
            .assertCountEquals(2)
        rule.onNode(hasTestTag(PHOTO_STACK_PILL_TAG) and hasAnyAncestor(hasTestTag("pair")), useUnmergedTree = true)
            .assertTextEquals("2 photos")
    }

    /**
     * The stack takes the box a single photo of the same image takes, fanned cards and all: no
     * taller, so the next message keeps the normal gap, and no wider, so the cards stay off the
     * avatar gutter and the timestamp column a swipe reveals.
     */
    @Test
    // Tall enough that all three rows lay out on screen, unclipped.
    @Config(qualifiers = "w360dp-h1200dp")
    fun `the fanned cards stay inside the box a single photo takes`() {
        rule.setContent {
            DesignSystem {
                Column {
                    ContentBubble(item = bubble(9), position = BubblePosition.Solo, modifier = Modifier.testTag("lone"))
                    for (count in listOf(2, 3)) {
                        ContentBubble(
                            item = members[0],
                            position = BubblePosition.Solo,
                            stack = PhotoStack(PhotoStack.Role.Front, members.take(count)),
                            modifier = Modifier.testTag("stack$count"),
                        )
                    }
                }
            }
        }

        val loneRow = rule.onNodeWithTag("lone").fetchSemanticsNode().boundsInRoot
        // The photo of the lone bubble: the box a stack has to stay inside.
        val single = rule.onNode(
            hasTestTag(PHOTO_BUBBLE_TAG) and hasAnyAncestor(hasTestTag("lone")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        for (count in listOf(2, 3)) {
            val row = rule.onNodeWithTag("stack$count").fetchSemanticsNode().boundsInRoot
            // No taller than the single photo: the box shrinks to the fan, so no empty band is left
            // above or below it for the next message to clear.
            assertTrue(row.height <= loneRow.height + 0.5f, "row height at $count photos: ${row.height} > ${loneRow.height}")
            // Both rows lead from the same edge, so the photo box sits at the same x in each.
            val frame = androidx.compose.ui.geometry.Rect(single.left, row.top, single.right, row.top + row.height)
            val cards = rule.onAllNodes(
                hasTestTag(PHOTO_STACK_CARD_TAG) and hasAnyAncestor(hasTestTag("stack$count")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertEquals(count - 1, cards.size)
            // A pixel of slack: each card's size rounds to whole pixels, which can grow its tilted
            // outline by under a pixel past the exact fit.
            val slack = 1f
            cards.forEach { card ->
                val b = card.boundsInRoot
                assertTrue(b.left >= frame.left - slack && b.right <= frame.right + slack, "$b reaches across $frame at $count photos")
                assertTrue(b.top >= frame.top - slack && b.bottom <= frame.bottom + slack, "$b reaches past $frame at $count photos")
            }
        }
    }

    @Test
    fun `opening the stack carries the front photo and the whole set`() {
        // The tap itself needs the photo to have loaded, which Robolectric's Coil cannot do, so the
        // action the tap sends is checked where it is built.
        val open = openPhotoAction(members[0], stackOf(PhotoStack.Role.Front, 1L))

        assertEquals(1L, open.bubble.messageId)
        assertEquals(listOf(1L, 2L, 3L), open.set.map { it.messageId })
        assertEquals(listOf(5L), openPhotoAction(bubble(5), null).set.map { it.messageId })
    }

    // region viewer

    private fun source(id: Long) = ChatPhotoSource(
        model = fullModel(id),
        fullModel = fullModel(id),
        imageSize = Size(800f, 600f),
        corners = ChatPhotoCorners(0f, 0f, 0f, 0f),
    )

    private fun fullModel(id: Long) = ChatPhoto(
        chatId = chatId,
        rendition = (members.first { it.messageId == id }.content as MessageContent.Media)
            .items.first().renditions.first(),
        senderId = null,
        sealed = false,
    )

    @Test
    fun `the viewer counts through the set as it is paged`() {
        val sources = ChatPhotoSources().apply { register(1L, source(1L)) }
        val pages = mutableListOf<Int>()
        rule.setContent {
            DesignSystem {
                Box {
                    ChatPhotoOverlay(
                        messageId = 1L,
                        sources = sources,
                        onClosed = {},
                        onSaveResult = {},
                        reduceMotion = true,
                        pages = listOf(1L, 2L, 3L).map { ChatPhotoPage(it, fullModel(it)) },
                        onPageChange = { pages += it },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("1 of 3")

        rule.onNodeWithTag(PHOTO_PAGER_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("2 of 3")
        assertEquals(1, pages.last())
    }

    @Test
    fun `the chrome stays shown as the set is paged`() {
        val sources = ChatPhotoSources().apply { register(1L, source(1L)) }
        rule.setContent {
            DesignSystem {
                Box {
                    ChatPhotoOverlay(
                        messageId = 1L,
                        sources = sources,
                        onClosed = {},
                        onSaveResult = {},
                        reduceMotion = true,
                        pages = listOf(1L, 2L, 3L).map { ChatPhotoPage(it, fullModel(it)) },
                    )
                }
            }
        }
        rule.waitForIdle()
        val closes = rule.onAllNodesWithTag(VIEWER_CLOSE_TAG)
        closes.assertAll(isNotEnabled())

        rule.onAllNodesWithTag(VIEWER_IMAGE_TAG)[0].performTouchInput { click(center) }
        // A single tap lands once the double-tap window has passed.
        rule.mainClock.advanceTimeBy(1_000)
        rule.onAllNodesWithTag(VIEWER_CLOSE_TAG).assertAll(isEnabled())

        rule.onNodeWithTag(PHOTO_PAGER_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag(PHOTO_COUNTER_TAG).assertTextEquals("2 of 3")
        rule.onAllNodesWithTag(VIEWER_CLOSE_TAG).assertAll(isEnabled())
    }

    @Test
    fun `a single photo opens with no counter`() {
        val sources = ChatPhotoSources().apply { register(1L, source(1L)) }
        rule.setContent {
            DesignSystem {
                Box {
                    ChatPhotoOverlay(
                        messageId = 1L,
                        sources = sources,
                        onClosed = {},
                        onSaveResult = {},
                        reduceMotion = true,
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onAllNodesWithTag(PHOTO_COUNTER_TAG).assertCountEquals(0)
    }

    // endregion
}
