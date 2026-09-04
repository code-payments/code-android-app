package com.flipcash.shared.transactionhistory

import androidx.annotation.StringRes
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import kotlin.time.Instant

/**
 * What a feed entry *was*, as the details screen says it (Figma 9708:118186).
 *
 * The activity feed's own row title is server-authored prose ("Tipped", "Purchased", "{0} sent
 * you"), which reads fine inline but can't carry a screen. The details screen states the kind in
 * the user's own voice instead, derived from the entry's [com.flipcash.app.core.feed.MessageMetadata]
 * — the only structured signal the client gets.
 *
 * The three cash kinds are distinct because the metadata distinguishes them and a user would too.
 * [GaveCash] and [ReceivedCash] are a hand-to-hand exchange — `DirectlySentCrypto` /
 * `ReceivedCrypto` with neither a user id nor a phone number, so there is nobody to name.
 * [SentCashLink] is `IndirectlySentCrypto`: the money sits in a gift-card vault until somebody
 * opens the link, which is why it is the only kind that can still be cancelled. There is no
 * received-cash-link counterpart because collecting one is indistinguishable, in the metadata,
 * from taking a bill in person — both arrive as an unattributed `ReceivedCrypto`.
 */
enum class TransactionKind(@StringRes val headingRes: Int) {
    Tipped(R.string.title_txnDetails_youTipped),
    Received(R.string.title_txnDetails_youReceived),
    Sent(R.string.title_txnDetails_youSent),
    GaveCash(R.string.title_txnDetails_youGaveCash),
    ReceivedCash(R.string.title_txnDetails_youReceivedCash),
    SentCashLink(R.string.title_txnDetails_youSentCashLink),
    Buy(R.string.title_txnDetails_buy),
    Sell(R.string.title_txnDetails_sell),
    Withdraw(R.string.title_txnDetails_withdraw),
    Deposit(R.string.title_txnDetails_deposit),
    Convert(R.string.title_txnDetails_convert),
    PoolPayment(R.string.title_txnDetails_poolPayment),
    Unknown(R.string.title_txnDetails_unknown),
}

/**
 * The on-chain account a withdrawal left for, or a deposit arrived from.
 *
 * A receipt row rather than a header line: an address is a value to check against an explorer or a
 * support ticket, not part of the sentence the header reads out — and the rows are where every
 * other checkable value on this screen already lives.
 */
data class TransactionAccount(
    val address: String,
    val direction: Direction,
) {
    enum class Direction(@StringRes val labelRes: Int) {
        To(R.string.label_txnDetails_to),
        From(R.string.label_txnDetails_from),
    }

    /** Nobody reads the middle of an address; the ends are what someone actually compares. */
    val shortAddress: String get() = truncate(address)

    companion object {
        fun truncate(address: String, edge: Int = 4): String =
            if (address.length <= edge * 2 + 1) address
            else "${address.take(edge)}…${address.takeLast(edge)}"
    }
}

/** The entry's settlement state, as the Status row phrases it. */
enum class TransactionStatus(@StringRes val labelRes: Int) {
    Pending(R.string.label_txnDetails_status_pending),
    Completed(R.string.label_txnDetails_status_completed),
    Failed(R.string.label_txnDetails_status_failed),
    Unknown(R.string.label_txnDetails_status_unknown),
}

/**
 * Everything the details screen draws, resolved.
 *
 * Every numeric field arrives pre-computed ([exchangeRate], [tokenAmount]) rather than as the
 * amount plus the token metadata needed to derive it, so the screen never runs [Fiat] or
 * [com.flipcash.libs.currency.math.Estimator] math mid-composition — and a preview or a test can
 * state a row's value directly instead of reconstructing the mint that would produce it.
 *
 * @param id The entry's id, base58-encoded — what the copy control puts on the clipboard.
 * @param avatar The row's avatar, reused verbatim so the screen opens on the thing that was tapped.
 * @param heading What the screen is titled, when the entry has something better to say than its
 * [kind] — which is the person-to-person case: a tip, a send and a receive are all headed by the
 * counterparty's display name, and the +/- on the amount is what states the direction. Null falls
 * back to [kind]'s own heading, which is what an unresolved counterparty gets: "You tipped" beats a
 * blank line.
 * @param subtitle The other side of the movement, under the heading: "In Person", the token a buy
 * was paid with, a convert's destination mint, and so on — see [TransactionSubtitles], which
 * resolves it per kind. Null wherever the header already says everything: a person entry, whose
 * name is the [heading]; a cash link, whose own heading names it; a withdrawal or deposit, whose
 * other side is an address and belongs in [account] where it can be read digit by digit.
 * @param signedAmountPrefix "-", "+", or null — the same direction marker the activity row puts on
 * its amount, so the two read the same movement the same way.
 * @param amount The entry's amount in the user's local currency, or null for a non-financial entry.
 * @param token The mint the entry moved in — the sub-line under the amount, and the badge on the
 * avatar. Null until the mint's metadata resolves.
 * @param toToken A convert's destination mint; null for everything else.
 * @param account The account a withdrawal left for or a deposit arrived from; null for every kind
 * that moved between people or mints rather than to an address.
 * @param received A convert's destination amount. Null while the swap is still pending.
 * @param fee What the movement cost. Converts only.
 * @param canCancel Whether the movement can still be pulled back — an open cash link, and nothing
 * else. Drawn as the app bar's end action.
 * @param canViewInChat Whether the counterparty's conversation can be opened from here.
 */
data class TransactionDetails(
    val id: String,
    val kind: TransactionKind,
    val avatar: TransactionAvatar,
    val heading: String? = null,
    val subtitle: String?,
    val signedAmountPrefix: String?,
    val amount: Fiat?,
    val timestamp: Instant,
    val token: Token?,
    val toToken: Token? = null,
    val account: TransactionAccount? = null,
    val status: TransactionStatus,
    val currencyCode: String?,
    val exchangeRate: Double?,
    val tokenAmount: String?,
    val fee: Fiat? = null,
    val received: Fiat? = null,
    val canCancel: Boolean = false,
    val canViewInChat: Boolean = false,
)
