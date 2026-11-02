package com.flipcash.shared.chat.share

import com.flipcash.app.blob.FilePickedPhoto
import com.flipcash.app.blob.PickedPhoto
import com.flipcash.shared.chat.testing.FakePhoto
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.bytes
import kotlin.test.Test
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatShareTest {

    private val png = FakePhoto("shot.png")
    private val jpg = FakePhoto("pic.jpg")
    private val pdf = FakePhoto("doc.pdf")
    private val unknown = FakePhoto("mystery")
    private val types = mapOf(png to "image/png", jpg to "image/jpeg", pdf to "application/pdf")
    private val mimeOf: (PickedPhoto) -> String? = { types[it] }

    private fun parse(
        isShare: Boolean = true,
        text: CharSequence? = null,
        streams: List<PickedPhoto> = emptyList(),
    ) = parseChatShare(isShare, text, streams, mimeOf = mimeOf)

    @Test
    fun singleImage() {
        assertEquals(ChatShareRequest(null, listOf(png)), parse(streams = listOf(png)))
    }

    @Test
    fun multipleImages() {
        val request = parse(streams = listOf(png, jpg))
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
        assertNull(parse(isShare = false, text = "hi"))
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
        val shares = PendingChatShares().apply { offer(PendingChatShare(null, listOf(FilePickedPhoto(file)))) }
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
        val shares = PendingChatShares().apply { offer(PendingChatShare(null, listOf(FilePickedPhoto(file)))) }
        shares.discard()
        assertNull(shares.pending.value)
        assertTrue(!file.exists())
    }

    @Test
    fun newShareReplacesAndCleansUpTheOld() {
        val file = File.createTempFile("share", ".jpg").apply { writeText("x") }
        val shares = PendingChatShares().apply { offer(PendingChatShare(null, listOf(FilePickedPhoto(file)))) }
        shares.offer(share)
        assertEquals(share, shares.pending.value)
        assertTrue(!file.exists())
    }
}
