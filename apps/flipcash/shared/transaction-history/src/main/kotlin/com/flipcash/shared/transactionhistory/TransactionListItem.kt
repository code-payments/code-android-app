package com.flipcash.shared.transactionhistory

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import kotlin.time.Instant

/**
 * Leading avatar for a transaction row.
 * - [Profile] — a resolved counterparty (tip / user-to-user send-receive), keyed by user id.
 * - [TokenIcon] — no counterparty (deposit / buy / sell / withdraw): the token's icon.
 * - [Generic] — unresolved / unknown counterparty.
 */
sealed interface TransactionAvatar {
    data class Profile(val profile: UserProfile) : TransactionAvatar
    data class TokenIcon(val token: Token) : TransactionAvatar
    data object Generic : TransactionAvatar
}

data class TransactionListItem(
    val id: String,                      // stable paging key (message.id hex-encoded)
    val title: String,                   // server-provided message.text
    val timestamp: Instant,
    val avatar: TransactionAvatar,
    val signedAmountPrefix: String?,     // "-", "+", or null (for metadata == null)
    val amount: Fiat?,                   // message.amount.nativeAmount, or null if non-financial
    val canCancel: Boolean,
)
