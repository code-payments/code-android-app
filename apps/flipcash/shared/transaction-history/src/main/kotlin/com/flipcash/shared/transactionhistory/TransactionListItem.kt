package com.flipcash.shared.transactionhistory

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import kotlin.time.Instant

/**
 * Leading avatar for a transaction row.
 * - [Profile] — a resolved counterparty (tip / user-to-user send-receive), keyed by user id.
 * - [TokenIcon] — no counterparty to draw: deposit / buy / sell / withdraw, a cash link (sent to
 *   whoever opens it), or a give or grab the server left unidentified (a bill hand-off never
 *   exchanges identities). The token's icon.
 * - [SwapTokens] — a convert: both sides' icons, source behind destination.
 * - [Generic] — a counterparty that is named but not yet resolved, or unknown metadata.
 */
sealed interface TransactionAvatar {
    data class Profile(val profile: UserProfile) : TransactionAvatar
    data class TokenIcon(val token: Token) : TransactionAvatar

    /**
     * Either side may still be unresolved (its metadata hasn't landed in the token cache yet); the
     * row draws a placeholder for a missing one rather than switching avatar shape mid-hydration.
     */
    data class SwapTokens(val from: Token?, val to: Token?) : TransactionAvatar
    data object Generic : TransactionAvatar
}

data class TransactionListItem(
    val id: String,                      // stable paging key (message.id hex-encoded)
    val title: String,                   // server-provided message.text
    val timestamp: Instant,
    val avatar: TransactionAvatar,
    val signedAmountPrefix: String?,     // "-", "+", or null (for metadata == null)
    val amount: Fiat?,                   // message.amount.nativeAmount, or null if non-financial
    val fee: Fiat?,                      // converts only: what the swap cost, shown under the amount
    val canCancel: Boolean,
)
