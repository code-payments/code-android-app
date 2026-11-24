package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
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

    private fun collection(chatId: ChatId = chat) = request(
        kind = PaymentRequest.Kind.GroupCollection(
            chatId = chatId,
            collection = PaymentCollection(
                text = PaymentRequestText.Plain("", ""),
                currency = CurrencyCode.USD,
                endsAt = null,
                amount = CollectionAmount.Fixed(Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)),
            ),
        ),
    )

    @Test
    fun `a collection stays unsupported with collections off, as before the Group Money flag`() {
        assertEquals(RequestCardState.Unsupported, requestCardState(collection(), false, chat, creator))
    }

    @Test
    fun `a collection from the sender in this chat is a card with collections on, and has no title`() {
        val state = requestCardState(collection(), false, chat, creator, collections = true)
        assertIs<RequestCardState.Ready>(state)
        assertEquals("", state.title)
    }

    @Test
    fun `a collection from another chat or another creator is unsupported with collections on`() {
        val other = ChatId(ByteArray(32) { 4 })
        assertEquals(RequestCardState.Unsupported, requestCardState(collection(chatId = other), false, chat, creator, collections = true))
        assertEquals(RequestCardState.Unsupported, requestCardState(collection(), false, chat, List(16) { 2 }, collections = true))
    }

    @Test
    fun `a split reads the same with collections on`() {
        assertEquals(requestCardState(request(), false, chat, creator), requestCardState(request(), false, chat, creator, collections = true))
    }
}
