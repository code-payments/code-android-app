package com.flipcash.shared.transactionhistory

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
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
    /**
     * The mint this entry moved in, drawn as a small coin over the avatar's bottom-right corner
     * (Figma 9717:14138).
     *
     * Only person-shaped avatars carry one: a face says who, not what, so without the badge the row
     * never names the token — every tip reads the same whether it was dollars or a creator coin.
     * [TokenIcon] and [SwapTokens] already *are* the token, so badging them would just repeat it.
     *
     * Null until the mint's metadata resolves; the row draws the bare avatar until then.
     */
    val badgeToken: Token? get() = null

    data class Profile(
        val profile: UserProfile,
        override val badgeToken: Token? = null,
    ) : TransactionAvatar

    data class TokenIcon(val token: Token) : TransactionAvatar

    /**
     * Either side may still be unresolved (its metadata hasn't landed in the token cache yet); the
     * row draws a placeholder for a missing one rather than switching avatar shape mid-hydration.
     */
    data class SwapTokens(val from: Token?, val to: Token?) : TransactionAvatar

    data class Generic(override val badgeToken: Token? = null) : TransactionAvatar
}

data class TransactionListItem(
    val id: String,                      // stable paging key (message.id hex-encoded)
    // The raw id, for opening the entry's details. Carried alongside [id] rather than decoded back
    // out of it: [id] exists to be a paging key, and hex has no decoder in the app.
    val messageId: ID,
    val title: String,                   // server-provided message.text
    val timestamp: Instant,
    val avatar: TransactionAvatar,
    val signedAmountPrefix: String?,     // "-", "+", or null (for metadata == null)
    // The whole exchanged amount, not just its native side: the row restates it in the viewer's
    // own currency, which needs the entry's USD value and mint as well (see `forViewer`).
    val amount: LocalFiat?,              // message.amount, or null if non-financial
    val fee: Fiat?,                      // converts only: what the swap cost, shown under the amount
    val canCancel: Boolean,
)
