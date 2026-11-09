package com.flipcash.app.messenger.internal

import com.flipcash.app.core.bill.Scannable
import com.flipcash.shared.chat.ChatCashBillShare
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.valuedAt
import com.getcode.opencode.model.financial.Token

/**
 * The received bill for [share]. [priced] supplies the verified state and the on-chain token
 * quarks the grab is checked against; the displayed amount is pinned to the share the message
 * carries, so the bill reads what the bubble reads even where a recompute would land elsewhere
 * (a creator token's curve estimate, a rate that moved since the message was sent).
 */
internal fun chatCashBillOf(token: Token, priced: VerifiedFiat, share: ChatCashBillShare): Scannable.Payable {
    val pinned = priced.valuedAt(share.amount)
    return Scannable.Payable.forToken(
        token = token,
        amount = pinned.localFiat,
        verifiedState = pinned.verifiedState,
        didReceive = true,
        receivedInChat = true,
    )
}
