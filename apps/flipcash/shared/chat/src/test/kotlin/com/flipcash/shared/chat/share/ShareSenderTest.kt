package com.flipcash.shared.chat.share

import android.net.Uri
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ShareSenderTest {

    private val a = ChatId(ByteArray(32) { 1 })
    private val b = ChatId(ByteArray(32) { 2 })
    private val c = ChatId(ByteArray(32) { 3 })

    private fun uris(n: Int) = List(n) { Uri.parse("file:///share/$it.jpg") }

    /** Records every call in order; failures and photo outcomes are scripted per chat. */
    private class FakeGateway : ShareGateway {
        val calls = mutableListOf<String>()
        val failText = mutableSetOf<ChatId>()
        val failPost = mutableSetOf<ChatId>()
        /** Outcomes for each message of a chat, consumed attempt by attempt. */
        val outcomes = mutableMapOf<ChatId, ArrayDeque<List<PhotoOutcome>>>()
        private var chip = 0
        private val chipChat = mutableMapOf<String, ChatId>()
        private val messageIds = mutableMapOf<ChatId, List<String>>()

        override suspend fun sendText(chatId: ChatId, text: String): Result<Unit> {
            calls += "text:${chatId.tag()}:$text"
            return if (chatId in failText) Result.failure(RuntimeException("offline")) else Result.success(Unit)
        }

        override fun stagePhoto(chatId: ChatId, uri: Uri): String {
            val id = "chip${chip++}"
            chipChat[id] = chatId
            calls += "stage:${chatId.tag()}:$id"
            return id
        }

        override suspend fun sendPhotos(chatId: ChatId, chipIds: List<String>, caption: String): Result<List<String>> {
            calls += "post:${chatId.tag()}:${chipIds.size}:$caption"
            if (chatId in failPost) return Result.failure(RuntimeException("offline"))
            val ids = chipIds.map { "m-$it" }
            messageIds[chatId] = ids
            return Result.success(ids)
        }

        override suspend fun awaitPhotos(messageIds: List<String>): Map<String, PhotoOutcome> {
            val chat = chipChat.entries.first { "m-${it.key}" == messageIds.first() }.value
            val script = outcomes[chat]?.removeFirstOrNull()
            return messageIds.withIndex().associate { (i, id) -> id to (script?.getOrNull(i) ?: PhotoOutcome.Sent) }
        }

        override suspend fun retryPhoto(chatId: ChatId, messageId: String): Result<Unit> {
            calls += "retry:${chatId.tag()}:$messageId"
            return Result.success(Unit)
        }

        override fun discardPhotos(chipIds: List<String>) {
            calls += "discard:${chipIds.size}"
        }

        private fun ChatId.tag() = bytes[0].toInt()
    }

    private fun sender(gateway: FakeGateway = FakeGateway()) = ShareSender(gateway) to gateway

    @Test
    fun `text goes to each chat in pick order as a plain message`() = runTest {
        val (sender, gateway) = sender()

        val done = sender.send(listOf(c, a, b), emptyList(), "  hello ")

        assertTrue(done)
        assertEquals(listOf("text:3:hello", "text:1:hello", "text:2:hello"), gateway.calls)
        assertTrue(sender.progress.value!!.isComplete)
    }

    @Test
    fun `images are staged per chat and the text is the caption`() = runTest {
        val (sender, gateway) = sender()

        sender.send(listOf(a, b), uris(2), "look")

        // Each chat stages its own copies: a sealed upload is never reused for another chat.
        assertEquals(
            listOf(
                "stage:1:chip0", "stage:1:chip1", "post:1:2:look",
                "stage:2:chip2", "stage:2:chip3", "post:2:2:look",
            ),
            gateway.calls,
        )
    }

    @Test
    fun `nothing to send does nothing`() = runTest {
        val (sender, gateway) = sender()

        assertFalse(sender.send(listOf(a), emptyList(), "   "))

        assertTrue(gateway.calls.isEmpty())
        assertEquals(null, sender.progress.value)
    }

    @Test
    fun `images past the cap are not sent`() = runTest {
        val (sender, gateway) = sender()

        sender.send(listOf(a), uris(MAX_SHARE_IMAGES + 4), "")

        assertEquals(MAX_SHARE_IMAGES, gateway.calls.count { it.startsWith("stage:") })
        assertTrue(gateway.calls.contains("post:1:$MAX_SHARE_IMAGES:"))
    }

    @Test
    fun `a failed chat does not stop the others and a retry resends only it`() = runTest {
        val (sender, gateway) = sender()
        gateway.failText += b

        val first = sender.send(listOf(a, b, c), emptyList(), "hi")

        assertFalse(first)
        val progress = sender.progress.value!!
        assertEquals(listOf(b), progress.failed)
        assertEquals(listOf(b), progress.remaining)
        assertEquals(ShareChatStatus.Sent, progress.statuses[a])
        assertEquals(ShareChatStatus.Sent, progress.statuses[c])

        gateway.failText.clear()
        gateway.calls.clear()
        val second = sender.send(listOf(a, b, c), emptyList(), "hi")

        assertTrue(second)
        assertEquals(listOf("text:2:hi"), gateway.calls)
    }

    @Test
    fun `a retry resends only the photo messages that failed`() = runTest {
        val (sender, gateway) = sender()
        gateway.outcomes[a] = ArrayDeque(listOf(listOf(PhotoOutcome.Sent, PhotoOutcome.Failed(retryable = true))))

        assertFalse(sender.send(listOf(a), uris(2), ""))
        assertEquals(ShareChatStatus.Failed(ShareFailure.Send, canRetry = true), sender.progress.value!!.statuses[a])

        gateway.calls.clear()
        assertTrue(sender.send(listOf(a), uris(2), ""))

        // No new staging or posting: only the one message that failed is tried again.
        assertEquals(listOf("retry:1:m-chip1"), gateway.calls)
    }

    @Test
    fun `a rejected image is not retried and leaves the share stuck`() = runTest {
        val (sender, gateway) = sender()
        gateway.outcomes[a] = ArrayDeque(listOf(listOf(PhotoOutcome.Failed(retryable = false))))

        sender.send(listOf(a), uris(1), "")

        val progress = sender.progress.value!!
        assertEquals(ShareChatStatus.Failed(ShareFailure.ImageRejected, canRetry = false), progress.statuses[a])
        assertTrue(progress.isStuck)
    }

    @Test
    fun `a post that fails discards its chips and can be sent again from scratch`() = runTest {
        val (sender, gateway) = sender()
        gateway.failPost += a

        assertFalse(sender.send(listOf(a), uris(1), ""))
        assertTrue(gateway.calls.contains("discard:1"))

        gateway.failPost.clear()
        assertTrue(sender.send(listOf(a), uris(1), ""))
    }

    @Test
    fun `progress is reported chat by chat and cleared when the send ends`() = runTest {
        val seen = mutableListOf<ShareSending?>()
        lateinit var sender: ShareSender
        val gateway = object : ShareGateway by FakeGateway() {
            override suspend fun sendText(chatId: ChatId, text: String): Result<Unit> {
                seen += sender.sending.value
                return Result.success(Unit)
            }
        }
        sender = ShareSender(gateway)

        sender.send(listOf(a, b, c), emptyList(), "hi")

        assertEquals(listOf<ShareSending?>(ShareSending(1, 3), ShareSending(2, 3), ShareSending(3, 3)), seen)
        assertEquals(null, sender.sending.value)
    }

    @Test
    fun `reset forgets what was sent`() = runTest {
        val (sender, gateway) = sender()
        sender.send(listOf(a), emptyList(), "hi")
        sender.reset()
        gateway.calls.clear()

        sender.send(listOf(a), emptyList(), "hi")

        assertEquals(listOf("text:1:hi"), gateway.calls)
    }
}
