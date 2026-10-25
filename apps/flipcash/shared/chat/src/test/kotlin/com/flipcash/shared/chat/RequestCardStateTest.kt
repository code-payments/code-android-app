package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** Spec case 19, and the other rules for when a request widget draws as unsupported. */
class RequestCardStateTest {

    private val chat = ChatId(ByteArray(32) { 3 })
    private val creator = List<Byte>(16) { 1 }

    private fun request(
        chatId: ChatId = chat,
        text: PaymentRequestText = PaymentRequestText.Plain("Dinner", ""),
        kind: PaymentRequest.Kind? = null,
    ) = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = kind ?: PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(text, CurrencyCode.USD, Instant.fromEpochSeconds(1_000), emptyList()),
        ),
    )

    @Test
    fun `a request from the sender in this chat is a card`() {
        val state = requestCardState(request(), missing = false, chatId = chat, senderId = creator)
        assertEquals("Dinner", assertIs<RequestCardState.Ready>(state).title)
    }

    @Test
    fun `a request made in another chat is unsupported`() {
        val other = ChatId(ByteArray(32) { 4 })
        assertEquals(RequestCardState.Unsupported, requestCardState(request(chatId = other), false, chat, creator))
    }

    @Test
    fun `a request the sender did not create is unsupported`() {
        assertEquals(RequestCardState.Unsupported, requestCardState(request(), false, chat, List(16) { 2 }))
    }

    @Test
    fun `an unknown kind is unsupported`() {
        val state = requestCardState(request(kind = PaymentRequest.Kind.Unsupported), false, chat, creator)
        assertEquals(RequestCardState.Unsupported, state)
    }

    @Test
    fun `text that failed to open or uses an unknown scheme is unsupported`() {
        assertEquals(RequestCardState.Unsupported, requestCardState(request(text = PaymentRequestText.Undecryptable), false, chat, creator))
        val unknownScheme = PaymentRequestText.Encrypted(7, ByteArray(24), ByteArray(17))
        assertEquals(RequestCardState.Unsupported, requestCardState(request(text = unknownScheme), false, chat, creator))
    }

    @Test
    fun `text still sealed under the known scheme is loading`() {
        val sealed = PaymentRequestText.Encrypted(1, ByteArray(24), ByteArray(17))
        assertEquals(RequestCardState.Loading, requestCardState(request(text = sealed), false, chat, creator))
    }

    @Test
    fun `a request not held yet is loading, and one found missing is unsupported`() {
        assertEquals(RequestCardState.Loading, requestCardState(null, false, chat, creator))
        assertEquals(RequestCardState.Unsupported, requestCardState(null, true, chat, creator))
    }
}
