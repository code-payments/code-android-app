package com.flipcash.services.models.paymentrequest

import com.codeinc.flipcash.gen.paymentrequest.v1.Model as PaymentRequestModel
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** A request survives the `PaymentRequestMetadata` bytes persistence stores. */
class PaymentRequestStorageTest {

    private val alice = List<Byte>(16) { 2 }
    private val bob = List<Byte>(16) { 3 }
    private val chatId = ChatId(ByteArray(32) { 3 })
    private val encrypted = PaymentRequestText.Encrypted(
        scheme = PaymentRequestText.SCHEME_CHAT_KEY_XCHACHA20POLY1305,
        nonce = ByteArray(24) { 7 },
        ciphertext = ByteArray(40) { 9 },
    )

    private fun share(user: List<Byte>, quarks: Long, paid: PaidShare? = null) =
        Share(user, Fiat(quarks = quarks, currencyCode = CurrencyCode.USD), paid)

    private fun request(text: PaymentRequestText, sealedText: PaymentRequestText.Encrypted? = null) = PaymentRequest(
        id = List(16) { 9 },
        creator = List(16) { 1 },
        createdAt = Instant.fromEpochSeconds(100, 500),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 7,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(
                text = text,
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(1_000),
                shares = listOf(
                    share(alice, 12_345_670, PaidShare(Instant.fromEpochSeconds(200), PaidShare.How.Manual)),
                    share(bob, 3_333_333),
                ),
                sealedText = sealedText,
            ),
        ),
    )

    @Test
    fun `a plaintext request round trips`() {
        val request = request(PaymentRequestText.Plain("Dinner", "Tacos"))

        assertEquals(request, decodePaymentRequest(request.encodePaymentRequest()))
    }

    @Test
    fun `every way a share is paid round trips`() {
        val hows = listOf(PaidShare.How.Payment, PaidShare.How.Manual, PaidShare.How.Creator, PaidShare.How.Unknown)
        val base = request(PaymentRequestText.Plain("t", ""))
        val split = base.split!!.copy(shares = hows.map { share(alice, 1, PaidShare(Instant.fromEpochSeconds(5), it)) })
        val withPaid = base.copy(kind = PaymentRequest.Kind.GroupSplit(chatId, split), closedAt = Instant.fromEpochSeconds(900), status = PaymentRequestStatus.Closed)

        assertEquals(withPaid, decodePaymentRequest(withPaid.encodePaymentRequest()))
    }

    @Test
    fun `an unopened request keeps its encrypted text`() {
        val decoded = decodePaymentRequest(request(encrypted).encodePaymentRequest())!!

        val text = assertIs<PaymentRequestText.Encrypted>(decoded.split?.text)
        assertEquals(encrypted.scheme, text.scheme)
        assertContentEquals(encrypted.nonce, text.nonce)
        assertContentEquals(encrypted.ciphertext, text.ciphertext)
    }

    @Test
    fun `an opened request is stored with the sealed text it came from, never the plain one`() {
        val opened = request(PaymentRequestText.Plain("Secret dinner", "Tacos"), sealedText = encrypted)

        val parsed = PaymentRequestModel.PaymentRequestMetadata.parseFrom(opened.encodePaymentRequest())

        assertEquals(PaymentRequestModel.Split.TextCase.ENCRYPTED, parsed.groupChat.split.textCase)
        assertContentEquals(encrypted.ciphertext, parsed.groupChat.split.encrypted.ciphertext.toByteArray())
        val decoded = decodePaymentRequest(opened.encodePaymentRequest())!!
        assertIs<PaymentRequestText.Encrypted>(decoded.split?.text)
    }

    @Test
    fun `a request of an unknown kind reads back as unsupported`() {
        val unsupported = request(PaymentRequestText.Plain("t", "")).copy(kind = PaymentRequest.Kind.Unsupported)

        val decoded = decodePaymentRequest(unsupported.encodePaymentRequest())!!

        assertEquals(PaymentRequest.Kind.Unsupported, decoded.kind)
        assertEquals(unsupported.version, decoded.version)
    }

    @Test
    fun `bytes that do not parse read as no request`() {
        assertNull(decodePaymentRequest(byteArrayOf(0x0a, 0x7f, 0x01)))
    }
}
