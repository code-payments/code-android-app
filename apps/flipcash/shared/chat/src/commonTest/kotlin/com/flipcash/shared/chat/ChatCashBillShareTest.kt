package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The rows of the chat-cash-bill spec's test table that the trigger decision answers. */
class ChatCashBillShareTest {
    private val me = listOf<Byte>(1, 2, 3)
    private val other = listOf<Byte>(4, 5, 6)
    private val third = listOf<Byte>(7, 8, 9)
    private val chatA = ChatId("aa")
    private val chatB = ChatId("bb")
    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")
    private val five = Fiat(fiat = 5.0, currencyCode = CurrencyCode.CAD)

    private fun cash(
        receiver: List<Byte>? = me,
        multi: List<MessageContent.Cash.Payment> = emptyList(),
        amount: Fiat = five,
        action: MessageContent.Cash.Action = MessageContent.Cash.Action.SENT,
        paymentRequestId: List<Byte>? = null,
        paymentReturn: MessageContent.Cash.PaymentReturn? = null,
    ) = MessageContent.Cash(
        intentId = listOf(0),
        amount = amount,
        mint = mint,
        action = action,
        receiver = receiver,
        tokenQuarks = 500L,
        multiReceiver = multi,
        paymentRequestId = paymentRequestId,
        paymentReturn = paymentReturn,
    )

    private fun share(
        content: MessageContent.Cash = cash(),
        source: ArrivalSource = ArrivalSource.Live,
        flagOn: Boolean = true,
        openChat: ChatId? = chatA,
        chatId: ChatId = chatA,
        sender: List<Byte>? = other,
        nothingOnTop: Boolean = true,
        billShowing: Boolean = false,
    ) = chatCashBillShare(
        source = source,
        flagOn = flagOn,
        openChat = openChat,
        chatId = chatId,
        selfId = me,
        senderId = sender,
        content = content,
        nothingOnTop = nothingOnTop,
        billShowing = billShowing,
    )

    @Test
    fun `1 live cash from another to me is a bill for my amount`() {
        val result = assertNotNull(share())
        assertEquals(five, result.amount)
        assertEquals(mint, result.mint)
        assertEquals(500L, result.tokenQuarks)
    }

    @Test
    fun `2 my own send never triggers`() {
        assertNull(share(sender = me, content = cash(receiver = other)))
    }

    @Test
    fun `3 a group send bills my share, not the total`() {
        val parts = listOf(
            MessageContent.Cash.Payment(me, 3.34, 334L),
            MessageContent.Cash.Payment(third, 3.33, 333L),
            MessageContent.Cash.Payment(listOf<Byte>(10), 3.33, 333L),
        )
        val result = assertNotNull(
            share(content = cash(receiver = null, multi = parts, amount = Fiat(fiat = 10.0, currencyCode = CurrencyCode.CAD)))
        )
        assertEquals(Fiat(fiat = 3.34, currencyCode = CurrencyCode.CAD), result.amount)
        assertEquals(334L, result.tokenQuarks)
    }

    @Test
    fun `4 a group send I am not a receiver of does not trigger`() {
        val parts = listOf(
            MessageContent.Cash.Payment(other, 3.0, 300L),
            MessageContent.Cash.Payment(third, 3.0, 300L),
        )
        assertNull(share(content = cash(receiver = null, multi = parts)))
    }

    @Test
    fun `a single receiver who is someone else does not trigger`() {
        assertNull(share(content = cash(receiver = third)))
    }

    @Test
    fun `a legacy DM with no receiver field triggers as receivedBy credits it`() {
        assertNotNull(share(content = cash(receiver = null)))
    }

    @Test
    fun `5 cash in another chat does not trigger`() {
        assertNull(share(chatId = chatB))
    }

    @Test
    fun `6 no chat open does not trigger`() {
        assertNull(share(openChat = null))
    }

    @Test
    fun `7 anything that is not the live stream does not trigger`() {
        assertNull(share(source = ArrivalSource.CatchUp))
    }

    @Test
    fun `8 something on top drops the payment`() {
        assertNull(share(nothingOnTop = false))
    }

    @Test
    fun `10 and 18 a bill already showing drops the payment`() {
        assertNull(share(billShowing = true))
    }

    @Test
    fun `11 flag off does not trigger`() {
        assertNull(share(flagOn = false))
    }

    @Test
    fun `17 tips, request payments and send-back returns trigger like any cash`() {
        val tip = cash(action = MessageContent.Cash.Action.TIPPED)
        val request = cash(paymentRequestId = listOf<Byte>(9, 9))
        val sendBack = cash(
            paymentReturn = MessageContent.Cash.PaymentReturn(
                returnOf = MessageContent.Cash.PaymentReturn.ReturnOf(messageId = 7L),
            ),
        )
        for (content in listOf(tip, request, sendBack)) {
            assertEquals(five, assertNotNull(share(content = content)).amount)
        }
    }
}
