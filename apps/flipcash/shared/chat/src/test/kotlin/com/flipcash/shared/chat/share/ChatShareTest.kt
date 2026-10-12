package com.flipcash.shared.chat.share

import android.content.Intent
import android.net.Uri
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.bytes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatShareTest {

    private val png = Uri.parse("content://other.app/shot.png")
    private val jpg = Uri.parse("content://other.app/pic.jpg")
    private val pdf = Uri.parse("content://other.app/doc.pdf")
    private val unknown = Uri.parse("content://other.app/mystery")
    private val types = mapOf(png to "image/png", jpg to "image/jpeg", pdf to "application/pdf")
    private val mimeOf: (Uri) -> String? = { types[it] }

    private fun parse(
        action: String? = Intent.ACTION_SEND,
        text: CharSequence? = null,
        streams: List<Uri> = emptyList(),
    ) = parseChatShare(action, text, streams, mimeOf = mimeOf)

    @Test
    fun singleImage() {
        assertEquals(ChatShareRequest(null, listOf(png)), parse(streams = listOf(png)))
    }

    @Test
    fun multipleImages() {
        val request = parse(Intent.ACTION_SEND_MULTIPLE, streams = listOf(png, jpg))
        assertEquals(listOf(png, jpg), request?.imageSources)
    }

    @Test
    fun textOnly() {
        assertEquals(ChatShareRequest("https://flipcash.com", emptyList()), parse(text = "  https://flipcash.com\n"))
    }

    @Test
    fun mixedTextAndImage() {
        assertEquals(ChatShareRequest("look", listOf(png)), parse(text = "look", streams = listOf(png)))
    }

    @Test
    fun nonImageStreamIsRejected() {
        assertNull(parse(streams = listOf(pdf)))
        assertNull(parse(streams = listOf(unknown)))
    }

    @Test
    fun nonImageStreamIsDroppedButTextKept() {
        assertEquals(ChatShareRequest("note", listOf(png)), parse(text = "note", streams = listOf(pdf, png)))
    }

    @Test
    fun blankTextWithNoImagesIsNothing() {
        assertNull(parse(text = "   "))
    }

    @Test
    fun otherActionsAreNotShares() {
        assertNull(parse(action = Intent.ACTION_VIEW, text = "hi"))
        assertNull(parse(action = null, text = "hi"))
    }

    @Test
    fun shortcutIdMapsToNoChatYet() {
        assertNull(chatIdFromShortcutId("anything"))
    }

    // --- PendingChatShares -------------------------------------------------------------------

    private val chatA = ChatId(UUID.fromString("0189d5f1-3c6a-7b4e-8f21-9c3d4e5f6a7b").bytes)
    private val chatB = ChatId(UUID.fromString("0189d5f1-3c6a-7b4e-8f21-9c3d4e5f6a7c").bytes)
    private val share = PendingChatShare("hi", emptyList())

    @Test
    fun incomingHoldsFromExpectUntilOfferedOrCancelled() {
        val shares = PendingChatShares()
        assertEquals(false, shares.incoming.value)
        shares.expectIncoming()
        assertEquals(true, shares.incoming.value)
        shares.offer(share)
        assertEquals(false, shares.incoming.value)
        shares.expectIncoming()
        shares.cancelIncoming()
        assertEquals(false, shares.incoming.value)
    }

    @Test
    fun releaseClearsWithoutDeletingTheCopies() {
        val file = File.createTempFile("share", ".jpg").apply { writeText("x") }
        val shares = PendingChatShares().apply { offer(PendingChatShare(null, listOf(Uri.fromFile(file)))) }
        shares.release()
        assertNull(shares.pending.value)
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun presentingIsClaimedOnce() {
        val shares = PendingChatShares().apply { offer(share) }
        assertNotNull(shares.beginPresenting())
        assertNull(shares.beginPresenting())
    }

    @Test
    fun discardDeletesCopiesAndClears() {
        val file = File.createTempFile("share", ".jpg").apply { writeText("x") }
        val shares = PendingChatShares().apply { offer(PendingChatShare(null, listOf(Uri.fromFile(file)))) }
        shares.discard()
        assertNull(shares.pending.value)
        assertTrue(!file.exists())
    }

    @Test
    fun newShareReplacesAndCleansUpTheOld() {
        val file = File.createTempFile("share", ".jpg").apply { writeText("x") }
        val shares = PendingChatShares().apply { offer(PendingChatShare(null, listOf(Uri.fromFile(file)))) }
        shares.offer(share)
        assertEquals(share, shares.pending.value)
        assertTrue(!file.exists())
    }
}
