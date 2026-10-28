package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PaymentRequestBodyPlannerTest {

    private val chat = ChatId(ByteArray(32) { 3 })
    private val viewer = List<Byte>(16) { 5 }
    private val other = List<Byte>(16) { 6 }
    private val templates = PaymentRequestBodyTemplates(
        amountAndTitle = "Requested %1\$s for %2\$s",
        amountOnly = "Requested %1\$s",
        titleOnly = "Sent request for %1\$s",
    )

    @Before
    fun locale() = Locale.setDefault(Locale.US)

    private fun share(userId: List<Byte>, dollars: Double) =
        Share(userId, Fiat(fiat = dollars, currencyCode = CurrencyCode.USD), paid = null)

    private fun request(
        text: PaymentRequestText = PaymentRequestText.Plain("Dinner", ""),
        shares: List<Share> = listOf(share(viewer, 12.5), share(other, 20.0)),
        chatId: ChatId = chat,
    ) = PaymentRequest(
        id = List(16) { 9 },
        creator = List(16) { 1 },
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(text, CurrencyCode.USD, Instant.fromEpochSeconds(1_000), shares),
        ),
    )

    private fun plan(request: PaymentRequest?, viewerId: List<Byte>? = viewer) =
        planPaymentRequestBody(request, chat, viewerId, templates)

    @Test
    fun `a fetched request names the viewer's share and its title`() {
        assertEquals("Requested \$12.50 for Dinner", plan(request()))
    }

    @Test
    fun `a viewer with no share is told the whole split`() {
        assertEquals("Requested \$32.50 for Dinner", plan(request(), viewerId = List(16) { 7 }))
    }

    @Test
    fun `a whole-dollar amount drops its cents`() {
        assertEquals("Requested \$20 for Dinner", plan(request(shares = listOf(share(viewer, 20.0)))))
    }

    @Test
    fun `a request without a title still says the amount`() {
        assertEquals("Requested \$12.50", plan(request(text = PaymentRequestText.Plain("  ", ""))))
    }

    @Test
    fun `a title that stayed sealed is left out`() {
        assertEquals("Requested \$12.50", plan(request(text = PaymentRequestText.Undecryptable)))
    }

    @Test
    fun `a request with a title but no amount says the title`() {
        assertEquals("Sent request for Dinner", plan(request(shares = emptyList())))
    }

    @Test
    fun `a request with neither falls back`() {
        assertNull(plan(request(text = PaymentRequestText.Undecryptable, shares = emptyList())))
    }

    @Test
    fun `a failed or timed out fetch falls back`() {
        assertNull(plan(null))
    }

    @Test
    fun `a request made in another chat falls back`() {
        assertNull(plan(request(chatId = ChatId(ByteArray(32) { 4 }))))
    }
}
