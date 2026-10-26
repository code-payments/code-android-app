package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Private-group content sealed under the chat key: bound to its chat, sender and blob id. */
class GroupContentCryptoTest {

    private val chatId = ChatId(ByteArray(32) { 3 })
    private val otherChat = ChatId(ByteArray(32) { 4 })
    private val sender: ID = List(16) { 1 }
    private val someoneElse: ID = List(16) { 2 }
    private val blobId = ByteArray(16) { 7 }

    private var key: Result<ByteArray> = Result.success(ByteArray(32) { 5 })
    private val crypto = GroupContentCrypto(FakeChatCipher, GroupKeySource { key })

    private suspend fun sealText(text: String) =
        crypto.seal(chatId, sender, MessageContent.Text(text)).getOrThrow()

    @Test
    fun `text sealed under the chat key opens to itself`() = runTest {
        val sealed = sealText("hi")

        assertEquals(GroupContentCrypto.SCHEME_CHAT_KEY_XCHACHA20POLY1305, sealed.scheme)
        assertEquals(OpenedContent.Plaintext(MessageContent.Text("hi")), crypto.open(chatId, sender, sealed))
    }

    @Test
    fun `a payment request widget seals and opens to itself`() = runTest {
        val widget = MessageContent.Widget(WidgetContent.PaymentRequest(List(32) { 9 }))

        val sealed = crypto.seal(chatId, sender, widget).getOrThrow()

        assertEquals(OpenedContent.Plaintext(widget), crypto.open(chatId, sender, sealed))
    }

    @Test
    fun `a share profile widget can't be sealed`() = runTest {
        val widget = MessageContent.Widget(WidgetContent.ShareProfile("alice"))

        assertTrue(crypto.seal(chatId, sender, widget).isFailure)
    }

    @Test
    fun `a message claimed by another sender or chat doesn't open`() = runTest {
        val sealed = sealText("hi")

        assertEquals(
            OpenedContent.Undecryptable(UndecryptableReason.Authentication),
            crypto.open(chatId, someoneElse, sealed),
        )
        assertEquals(
            OpenedContent.Undecryptable(UndecryptableReason.Authentication),
            crypto.open(otherChat, sender, sealed),
        )
    }

    @Test
    fun `a key that can't be fetched leaves the message pending`() = runTest {
        val sealed = sealText("hi")
        key = Result.failure(IOException("offline"))

        assertEquals(OpenedContent.KeyPending, crypto.open(chatId, sender, sealed))
    }

    @Test
    fun `a key wrapped by a stranger marks the message unsupported`() = runTest {
        val sealed = sealText("hi")
        key = Result.failure(UntrustedEnvelope("wrapped by a stranger"))

        assertEquals(OpenedContent.Undecryptable(UndecryptableReason.Unsupported), crypto.open(chatId, sender, sealed))
    }

    @Test
    fun `sealing without the key fails`() = runTest {
        key = Result.failure(IOException("offline"))

        assertTrue(crypto.seal(chatId, sender, MessageContent.Text("hi")).isFailure)
    }

    @Test
    fun `a photo's bytes open for the same blob and are refused for another`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val sealed = crypto.sealBlob(chatId, sender, blobId, bytes).getOrThrow()

        val opened = crypto.openBlob(chatId, sender, blobId, bytes.size.toLong(), sealed)
        assertContentEquals(bytes, assertIs<OpenedBlob.Plaintext>(opened).bytes)

        val wrongBlob = crypto.openBlob(chatId, sender, ByteArray(16) { 8 }, bytes.size.toLong(), sealed)
        assertEquals(OpenedBlob.Failed(BlobOpenFailure.Authentication), wrongBlob)
    }

    @Test
    fun `a photo that opens to the wrong length is refused`() = runTest {
        val sealed = crypto.sealBlob(chatId, sender, blobId, byteArrayOf(1, 2, 3)).getOrThrow()

        assertEquals(OpenedBlob.Failed(BlobOpenFailure.Length), crypto.openBlob(chatId, sender, blobId, 4, sealed))
    }

    @Test
    fun `request text sealed by its creator opens to itself`() = runTest {
        val sealed = crypto.sealPaymentRequestText(chatId, sender, "Dinner", "Tacos").getOrThrow()
        assertEquals(
            PaymentRequestText.Plain("Dinner", "Tacos"),
            crypto.openPaymentRequestText(chatId, sender, sealed).getOrThrow(),
        )
    }

    @Test
    fun `request text opened under another creator or chat is undecryptable`() = runTest {
        val sealed = crypto.sealPaymentRequestText(chatId, sender, "Dinner", "").getOrThrow()
        assertEquals(PaymentRequestText.Undecryptable, crypto.openPaymentRequestText(chatId, someoneElse, sealed).getOrThrow())
        assertEquals(PaymentRequestText.Undecryptable, crypto.openPaymentRequestText(otherChat, sender, sealed).getOrThrow())
    }

    @Test
    fun `request text with an unknown scheme is undecryptable`() = runTest {
        val sealed = crypto.sealPaymentRequestText(chatId, sender, "Dinner", "").getOrThrow()
        val unknown = PaymentRequestText.Encrypted(scheme = 7, nonce = sealed.nonce, ciphertext = sealed.ciphertext)
        assertEquals(PaymentRequestText.Undecryptable, crypto.openPaymentRequestText(chatId, sender, unknown).getOrThrow())
    }

    @Test
    fun `request text waits when the key can't be fetched`() = runTest {
        val sealed = crypto.sealPaymentRequestText(chatId, sender, "Dinner", "").getOrThrow()
        key = Result.failure(IOException("offline"))
        assertTrue(crypto.openPaymentRequestText(chatId, sender, sealed).isFailure)
    }
}
