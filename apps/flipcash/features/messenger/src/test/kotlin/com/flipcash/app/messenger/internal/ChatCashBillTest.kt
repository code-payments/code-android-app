package com.flipcash.app.messenger.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCashBillShare
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatCashBillTest {

    private val key = PublicKey.fromBase58("11111111111111111111111111111111")
    private val token: Token = MintMetadata(
        address = Mint.usdf, decimals = 6, name = "T", symbol = "T", createdAt = null,
        description = "", imageUrl = "",
        vmMetadata = VmMetadata(vm = key, authority = key, lockDurationInDays = 21),
        launchpadMetadata = null, billCustomizations = null, socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    @Test
    fun `the bill shows the share the message carries, not the recomputed value`() {
        // A recompute (a curve estimate, a moved rate) lands on $4.37 for a $5.00 share.
        val recomputed = VerifiedFiat(
            LocalFiat(
                underlyingTokenAmount = Fiat(4.0),
                nativeAmount = Fiat(4.37),
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            ),
        )
        val share = ChatCashBillShare(amount = Fiat(5.0), mint = Mint.usdf)

        val bill = chatCashBillOf(token, recomputed, share)

        assertEquals(Fiat(5.0), bill.amount.nativeAmount)
        // The on-chain quarks the grab is checked against are the recompute's, untouched.
        assertEquals(Fiat(4.0), bill.amount.underlyingTokenAmount)
        assertTrue(bill.didReceive)
        assertTrue(bill.receivedInChat)
    }

    private val chatRoute = AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(ChatId(listOf(1, 2, 3, 4))))

    @Test
    fun `the chat is on top only as the root's top route and the flow's conversation step`() {
        assertTrue(chatNothingOnTop(chatRoute, ChatStep.Conversation, overlaysShowing = false))
    }

    @Test
    fun `a chat opened as a sheet is not on top of the root`() {
        // openAsSheet wraps any route in Main.Sheet; the root's top is then the sheet, not the chat.
        val sheeted = AppRoute.Main.Sheet(chatRoute)
        assertFalse(chatNothingOnTop(sheeted, ChatStep.Conversation, overlaysShowing = false))
    }

    @Test
    fun `another route above the chat or another flow step is not on top`() {
        assertFalse(chatNothingOnTop(AppRoute.Menu.Lab(), ChatStep.Conversation, overlaysShowing = false))
        assertFalse(chatNothingOnTop(null, ChatStep.Conversation, overlaysShowing = false))
        assertFalse(chatNothingOnTop(chatRoute, null, overlaysShowing = false))
    }

    @Test
    fun `an overlay over the conversation is not on top`() {
        assertFalse(chatNothingOnTop(chatRoute, ChatStep.Conversation, overlaysShowing = true))
    }
}
