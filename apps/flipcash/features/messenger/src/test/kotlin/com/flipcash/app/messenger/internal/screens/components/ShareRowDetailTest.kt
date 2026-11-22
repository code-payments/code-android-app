package com.flipcash.app.messenger.internal.screens.components

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.RequestCardState
import com.flipcash.shared.chat.requestCardState
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** What a share payment row draws beside its caption, for a split and for a collection (collections spec case 10). */
class ShareRowDetailTest {

    private val chat = ChatId(ByteArray(32) { 3 })
    private val creator: ID = List(16) { 1 }
    private val payer: ID = List(16) { 2 }
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val ten = Fiat(10.0, CurrencyCode.USD)

    private fun request(kind: PaymentRequest.Kind) = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
        createdAt = t0,
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = kind,
    )

    private val split = request(
        PaymentRequest.Kind.GroupSplit(
            chatId = chat,
            split = Split(
                PaymentRequestText.Plain("Tahoe cabin", ""),
                CurrencyCode.USD,
                Instant.fromEpochSeconds(1_800_000_000),
                listOf(Share(payer, ten, null)),
            ),
        ),
    )

    private val collection = request(
        PaymentRequest.Kind.GroupCollection(
            chatId = chat,
            collection = PaymentCollection(PaymentRequestText.Plain("", ""), CurrencyCode.USD, null, CollectionAmount.Fixed(ten)),
        ),
    )

    private fun ready(request: PaymentRequest): RequestCardState.Ready {
        val state = requestCardState(request, false, chat, creator, collections = true)
        assertIs<RequestCardState.Ready>(state)
        return state
    }

    @Test
    fun `a collection payment row has no title and no ring`() {
        assertEquals(ShareRowDetail(title = null, progress = null), shareRowDetail(ready(collection), payer, t0))
    }

    @Test
    fun `a split payment row keeps its title and ring`() {
        val detail = shareRowDetail(ready(split), payer, t0)
        assertEquals("Tahoe cabin", detail.title)
        assertNotNull(detail.progress)
    }

    @Test
    fun `a row whose request can't be shown has neither`() {
        assertNull(shareRowDetail(null, payer, t0).title)
        assertNull(shareRowDetail(null, payer, t0).progress)
    }
}
