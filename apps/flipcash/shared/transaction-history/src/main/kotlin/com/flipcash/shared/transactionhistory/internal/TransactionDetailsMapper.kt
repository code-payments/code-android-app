package com.flipcash.shared.transactionhistory.internal

import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.core.feed.SwapState
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.transactionhistory.TransactionAvatar
import com.flipcash.shared.transactionhistory.TransactionDetails
import com.flipcash.shared.transactionhistory.TransactionKind
import com.flipcash.shared.transactionhistory.TransactionStatus
import com.flipcash.shared.transactionhistory.TransactionSubtitles
import com.flipcash.shared.transactionhistory.convertOf
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import com.getcode.utils.hexEncodedString
import javax.inject.Inject

/**
 * Maps a feed entry to the details screen's resolved state — the same entry the activity row draws,
 * restated as a screen.
 *
 * Shares [TransactionItemMapper]'s reading of the metadata (which counterparty, which avatar, which
 * direction) so a row and the screen it opens can never disagree about what the entry was; what it
 * adds is everything a row has no space for: the kind stated in the user's own voice, the receipt
 * values, and the actions.
 *
 * Pure and synchronous, like the row mapper: profiles and token metadata arrive through the caches
 * the coordinator observes, so an unresolved counterparty or mint simply fills in when it lands.
 */
internal class TransactionDetailsMapper @Inject constructor(
    private val resources: ResourceHelper,
) : Mapper<Pair<ActivityFeedMessageWithToken, Map<String, UserProfile>>, TransactionDetails> {

    override fun map(from: Pair<ActivityFeedMessageWithToken, Map<String, UserProfile>>): TransactionDetails {
        val (source, profiles) = from
        val msg = source.message
        val meta = msg.metadata
        val token = source.token

        val counterparty = userIdOf(meta)?.let { profiles[it.hexEncodedString()] }
        val convert = convertOf(meta)
        val kind = kindOf(meta, msg.text)

        return TransactionDetails(
            // Base58, not the row's hex: this is the value someone pastes into a support ticket or
            // an explorer, and base58 is how the rest of the app writes an id a person will read.
            id = msg.id.base58,
            kind = kind,
            avatar = avatarOf(counterparty, convert != null, meta, token, source.toToken),
            // A person entry is titled by the person; everything else falls back to the kind's own
            // heading, which is also what an unresolved counterparty gets.
            heading = counterparty?.displayName?.takeIf { it.isNotBlank() },
            subtitle = subtitleOf(kind, token, source.toToken),
            signedAmountPrefix = when {
                meta == null -> null
                meta.isOutgoing -> "-"
                else -> "+"
            },
            amount = msg.amount?.nativeAmount,
            timestamp = msg.timestamp,
            token = token,
            toToken = source.toToken,
            // The feed carries no destination address: `WithdrewCrypto` and `DepositedCrypto` say
            // only that money left or arrived, so there is nothing to put in the To/From row until
            // the notification metadata carries the account.
            account = null,
            status = statusOf(msg.state, convert?.swapState),
            currencyCode = msg.amount?.nativeAmount?.currencyCode?.name,
            exchangeRate = msg.amount?.rate?.fx,
            tokenAmount = tokenAmountOf(msg.amount?.underlyingTokenAmount, token),
            fee = convert?.fee,
            // A pending swap has no destination amount yet, which is exactly when the row is left
            // out rather than shown as zero.
            received = convert?.toAmount?.nativeAmount,
            canCancel = (meta as? MessageMetadata.IndirectlySentCrypto)?.canCancel == true,
            // Opening the conversation needs somebody to open it with, and the profile is what the
            // chat header renders from on the first frame (see `ChatIdentifier.ByUser`).
            canViewInChat = counterparty != null,
        )
    }

    /**
     * The other side of the movement, for the kinds whose heading doesn't already carry it. Null
     * wherever the header already says everything, and wherever the metadata can't answer it: a
     * legacy buy/sell records only the mint that moved, and a pool payment carries a pool id rather
     * than a name.
     */
    private fun subtitleOf(kind: TransactionKind, token: Token?, toToken: Token?): String? =
        when (kind) {
            TransactionKind.GaveCash, TransactionKind.ReceivedCash ->
                TransactionSubtitles.inPerson(resources)
            TransactionKind.Convert -> {
                val from = token?.name?.takeIf { it.isNotBlank() }
                val to = toToken?.name?.takeIf { it.isNotBlank() }
                if (from != null && to != null) {
                    TransactionSubtitles.converted(resources, from, to)
                } else {
                    null
                }
            }
            else -> null
        }
}

/**
 * What the entry *was*, from the only structured signal the client gets.
 *
 * The two hand-to-hand kinds are the send/receive pair with neither a user id nor a phone number —
 * a bill hand-off never exchanges identities, so there is genuinely nobody to name (see
 * [isUnidentifiedBill]). [text] separates a tip from a plain send, which the metadata doesn't: the
 * server encodes it in the verb, and [isTipVerb] is where that is read.
 */
private fun kindOf(meta: MessageMetadata?, text: String): TransactionKind = when (meta) {
    is MessageMetadata.DirectlySentCrypto -> when {
        isUnidentifiedBill(meta) -> TransactionKind.GaveCash
        text.isTipVerb() -> TransactionKind.Tipped
        else -> TransactionKind.Sent
    }
    is MessageMetadata.ReceivedCrypto ->
        if (isUnidentifiedBill(meta)) TransactionKind.ReceivedCash else TransactionKind.Received
    is MessageMetadata.IndirectlySentCrypto -> TransactionKind.SentCashLink
    is MessageMetadata.WithdrewCrypto -> TransactionKind.Withdraw
    MessageMetadata.DepositedCrypto -> TransactionKind.Deposit
    MessageMetadata.BoughtToken -> TransactionKind.Buy
    MessageMetadata.SoldToken -> TransactionKind.Sell
    is MessageMetadata.SwappedCrypto -> TransactionKind.Convert
    is MessageMetadata.PaidCrypto -> TransactionKind.PoolPayment
    MessageMetadata.Unknown, null -> TransactionKind.Unknown
}

/** The row's avatar, resolved exactly as [TransactionItemMapper] resolves it. */
private fun avatarOf(
    counterparty: UserProfile?,
    isConvert: Boolean,
    meta: MessageMetadata?,
    token: Token?,
    toToken: Token?,
): TransactionAvatar = when {
    counterparty != null -> TransactionAvatar.Profile(counterparty, badgeToken = token)
    isConvert -> TransactionAvatar.SwapTokens(from = token, to = toToken)
    (hasNoCounterparty(meta) || isUnidentifiedBill(meta)) && token != null ->
        TransactionAvatar.TokenIcon(token)
    else -> TransactionAvatar.Generic(badgeToken = token)
}

/**
 * The settlement state the Status row reads out.
 *
 * A convert is settled by its swap, not by the notification: the entry itself completes as soon as
 * the source side is debited, so a failed swap would otherwise read "Completed".
 */
private fun statusOf(state: MessageState, swapState: SwapState?): TransactionStatus = when {
    swapState == SwapState.FAILED -> TransactionStatus.Failed
    swapState == SwapState.PENDING -> TransactionStatus.Pending
    state == MessageState.PENDING -> TransactionStatus.Pending
    state == MessageState.COMPLETED -> TransactionStatus.Completed
    else -> TransactionStatus.Unknown
}

/**
 * How many tokens the entry moved, formatted to the mint's own precision.
 *
 * The reserve is one-to-one with its USD value, so it needs no curve. Every other mint is priced by
 * the bonding curve, and [Fiat.estimatedTokenAmountIn] prices it against the mint's *current*
 * supply — so this is the quantity that value is worth now, not what it bought at the time. Stating
 * it is still better than leaving the row blank, since the value is the same value the header shows.
 */
private fun tokenAmountOf(underlying: Fiat?, token: Token?): String? {
    underlying ?: return null
    token ?: return null
    val quantity = if (token.address == Mint.usdf) {
        underlying
    } else {
        Fiat.tokenBalance(underlying.quarks, token)
    }
    return quantity.estimatedTokenAmountIn(token)
}
