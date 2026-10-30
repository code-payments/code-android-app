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
import com.getcode.opencode.model.financial.formattedQuantity
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import com.flipcash.shared.transactionhistory.generated.resources.Res
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_boostReceived
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_boostReceivedIn
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youBoosted
import com.flipcash.shared.transactionhistory.generated.resources.title_txnDetails_youBoostedIn

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
        val boost = boostedReactionOf(meta)
        val counterpartyName = counterparty?.displayName?.takeIf { it.isNotBlank() }
        val boostLine = boost?.let { boostLineOf(meta, it.emoji.value, source.boostChatName) }
        // A split boost has nobody to name, so the group takes the name's place; the line then
        // never repeats it. A counterparty that hasn't resolved doesn't borrow the group's name
        // either: the boost line alone is the heading.
        val split = isSplitBoost(meta)
        val splitChat = source.boostChatName?.takeIf { it.isNotBlank() && split }
        val heading = when {
            boost == null -> counterpartyName
            split -> splitChat ?: boostLine
            else -> counterpartyName ?: boostLine
        }
        val boostSubtitle = when {
            boost == null -> null
            split && splitChat != null ->
                boostLineOf(meta, boost.emoji.value, chatName = null)
            counterpartyName != null -> boostLine
            else -> null
        }

        return TransactionDetails(
            // Base58, not the row's hex: this is the value someone pastes into a support ticket or
            // an explorer, and base58 is how the rest of the app writes an id a person will read.
            id = msg.id.base58,
            kind = kind,
            avatar = avatarOf(counterparty, convert != null, meta, token, source.toToken),
            // A person entry is titled by the person; everything else falls back to the kind's own
            // heading, which is also what an unresolved counterparty gets.
            // The name stays the large heading, with the boost's sentence under it; with no name
            // the sentence itself is the heading. A boost always sets one, which is what keeps the
            // kind's format-string heading from ever being drawn.
            heading = heading,
            subtitle = boostSubtitle ?: subtitleOf(kind, token, source.toToken),
            signedAmountPrefix = when {
                meta == null -> null
                meta.isOutgoing -> "-"
                else -> "+"
            },
            amount = msg.amount,
            timestamp = msg.timestamp,
            token = token,
            toToken = source.toToken,
            // The feed carries no destination address: `WithdrewCrypto` and `DepositedCrypto` say
            // only that money left or arrived, so there is nothing to put in the To/From row until
            // the notification metadata carries the account.
            account = null,
            status = statusOf(msg.state, convert?.swapState),
            exchangeRate = msg.amount?.rate?.fx,
            tokenAmount = tokenAmountOf(msg.amount?.underlyingTokenAmount, token),
            fee = convert?.fee,
            // A pending swap has no destination amount yet, which is exactly when the row is left
            // out rather than shown as zero.
            received = convert?.toAmount?.nativeAmount,
            canCancel = (meta as? MessageMetadata.IndirectlySentCrypto)?.canCancel == true,
            // The counterpart action opens a conversation or a profile, and both need somebody to
            // open it with.
            canViewInChat = counterparty != null,
            boostedReaction = boost,
            requestedPayment = requestedPaymentOf(meta),
        )
    }

    /**
     * The boost's sentence: the emoji, and "in {chat}" when [chatName] is a stored group. A DM is
     * stored without a title and a missing chat isn't stored, so both arrive as null and drop it.
     */
    private fun boostLineOf(meta: MessageMetadata?, emoji: String, chatName: String?): String {
        val chat = chatName?.takeIf { it.isNotBlank() }
        val received = meta is MessageMetadata.ReceivedCrypto
        return when {
            received && chat != null ->
                resources.getString(Res.string.title_txnDetails_boostReceivedIn, emoji, chat)
            received -> resources.getString(Res.string.title_txnDetails_boostReceived, emoji)
            chat != null -> resources.getString(Res.string.title_txnDetails_youBoostedIn, emoji, chat)
            else -> resources.getString(Res.string.title_txnDetails_youBoosted, emoji)
        }
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
        meta.boostedReaction != null -> TransactionKind.Boosted
        isUnidentifiedBill(meta) -> TransactionKind.GaveCash
        text.isTipVerb() -> TransactionKind.Tipped
        else -> TransactionKind.Sent
    }
    is MessageMetadata.ReceivedCrypto ->
        if (meta.boostedReaction != null) TransactionKind.BoostReceived
        else if (isUnidentifiedBill(meta)) TransactionKind.ReceivedCash else TransactionKind.Received
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
 * How many tokens the entry moved.
 *
 * This is the quantity the feed recorded, not one re-derived from the mint's current supply. The
 * server sends it on every entry — `CryptoPaymentAmount.quarks`, which
 * [com.getcode.opencode.model.financial.LocalFiat.underlyingTokenAmount] carries verbatim: dollars
 * for the reserve, that mint's own quarks for everything else. Pricing the value through the
 * bonding curve instead would answer a different question — what it would buy today — and would
 * drift further from what moved the longer ago the entry was. iOS reads the same field for the
 * same row (`ExchangedFiat.onChainAmount`), so the two screens state one number.
 *
 * Without the mint there is no scale to write the quarks at, and a quantity at the wrong scale is
 * worse than none — so an unresolved mint leaves the row out until it lands.
 */
private fun tokenAmountOf(underlying: Fiat?, token: Token?): String? {
    underlying ?: return null
    token ?: return null
    return token.formattedQuantity(underlying.quarks)
}
