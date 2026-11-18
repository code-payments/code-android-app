package com.flipcash.services.models.paymentrequest

import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Instant

/**
 * A payment request: `paymentrequest.v1.PaymentRequestMetadata`, of the kinds the app knows, a
 * split or a collection in a group chat.
 *
 * [status] is as the server last sent it. Read [resolvedStatus] and [resolvedClosedAt] instead: an
 * OPEN request past its [endsAt] has expired by the clock, which the server never announces.
 *
 * @param version advances by one on every change; the store keeps the greater (see [newerOf]).
 * @param payerSummary totals over the request's payers, as the server last sent them; null when it
 *   sent none.
 */
data class PaymentRequest(
    val id: ID,
    val creator: ID,
    val createdAt: Instant,
    val status: PaymentRequestStatus,
    val closedAt: Instant?,
    val version: Long,
    val kind: Kind,
    val payerSummary: PayerSummary? = null,
) {
    /** Where the request was made and what it asks. An arm the app does not know is [Unsupported]. */
    sealed interface Kind {
        data class GroupSplit(val chatId: ChatId, val split: Split) : Kind
        data class GroupCollection(val chatId: ChatId, val collection: PaymentCollection) : Kind
        data object Unsupported : Kind
    }

    val split: Split? get() = (kind as? Kind.GroupSplit)?.split
    val collection: PaymentCollection? get() = (kind as? Kind.GroupCollection)?.collection

    /** A split's chat. Null for any other kind, so lists built for splits leave collections out. */
    val chatId: ChatId? get() = (kind as? Kind.GroupSplit)?.chatId

    /** The group chat a split or a collection was made in. Null for an [Kind.Unsupported] request. */
    val groupChatId: ChatId?
        get() = when (val kind = kind) {
            is Kind.GroupSplit -> kind.chatId
            is Kind.GroupCollection -> kind.chatId
            Kind.Unsupported -> null
        }

    /** When the request stops taking payments. Null when it has no deadline, and never expires. */
    val endsAt: Instant?
        get() = when (val kind = kind) {
            is Kind.GroupSplit -> kind.split.endsAt
            is Kind.GroupCollection -> kind.collection.endsAt
            Kind.Unsupported -> null
        }

    /** The currency of every amount in the request, [payerSummary] and its payers' included. */
    val currency: CurrencyCode?
        get() = when (val kind = kind) {
            is Kind.GroupSplit -> kind.split.currency
            is Kind.GroupCollection -> kind.collection.currency
            Kind.Unsupported -> null
        }

    fun resolvedStatus(now: Instant): PaymentRequestStatus {
        val endsAt = this.endsAt
        return if (status == PaymentRequestStatus.Open && endsAt != null && endsAt <= now) {
            PaymentRequestStatus.Expired
        } else {
            status
        }
    }

    fun resolvedClosedAt(now: Instant): Instant? =
        if (status == PaymentRequestStatus.Open && resolvedStatus(now) == PaymentRequestStatus.Expired) endsAt
        else closedAt

    fun isOpen(now: Instant): Boolean = resolvedStatus(now) == PaymentRequestStatus.Open

    /**
     * Whether the deck shows it: an open split. A request leaves the deck as soon as it ends,
     * completed, closed or expired; the timeline, details and history still show it.
     */
    fun isInDeck(now: Instant): Boolean = split != null && isOpen(now)
}

enum class PaymentRequestStatus { Open, Completed, Closed, Expired, Unknown }

/**
 * Named members each owe the creator [Share.amount], all in [currency].
 *
 * Sums are in [Fiat.quarks] so "$20 paid, $520 requested" is exact rather than a sum of doubles.
 */
data class Split(
    val text: PaymentRequestText,
    val currency: CurrencyCode,
    val endsAt: Instant,
    val shares: List<Share>,
    // The sealed form [text] was opened from, so persistence stores the text as the server sent it
    // (see [encodePaymentRequest]). Null when the text was never sealed or is still sealed.
    val sealedText: PaymentRequestText.Encrypted? = null,
) {
    val requested: Fiat get() = Fiat(quarks = shares.sumOf { it.amount.quarks }, currencyCode = currency)
    val paid: Fiat get() = Fiat(quarks = shares.filter { it.isPaid }.sumOf { it.amount.quarks }, currencyCode = currency)

    /** Paid over requested, the creator's share on both sides; 0 for a split with nothing asked. */
    val paidFraction: Float
        get() {
            val total = requested.quarks
            return if (total <= 0L) 0f else paid.quarks.toFloat() / total
        }

    fun shareOf(userId: ID): Share? = shares.firstOrNull { it.userId == userId }
}

data class Share(
    val userId: ID,
    val amount: Fiat,
    val paid: PaidShare?,
) {
    val isPaid: Boolean get() = paid != null
}

/**
 * How a share was paid, or a collection contribution made (`paymentrequest.v1.Paid`). Once paid it
 * stays paid.
 */
data class PaidShare(val paidAt: Instant, val how: How) {
    enum class How { Payment, Manual, Creator, Unknown }
}

/**
 * Any member but the creator may pay the creator [amount], once, in [currency], for example to chip
 * in for a gift. Who pays is not decided in advance and nothing is owed, so a collection never
 * completes: it stays open until the creator closes it or it reaches [endsAt]. Who has paid is in
 * [PaymentRequest.payerSummary] and `GetPayers`.
 *
 * Named for `paymentrequest.v1.Collection`, prefixed so it does not shadow `kotlin.collections`.
 *
 * @param endsAt when it stops taking contributions; null means it has no deadline and never expires.
 */
data class PaymentCollection(
    val text: PaymentRequestText,
    val currency: CurrencyCode,
    val endsAt: Instant?,
    val amount: CollectionAmount,
    // As on [Split.sealedText].
    val sealedText: PaymentRequestText.Encrypted? = null,
)

/**
 * What each payment into a [PaymentCollection] must be. A kind the app does not know makes the
 * request [PaymentRequest.Kind.Unsupported].
 */
sealed interface CollectionAmount {
    /** Every payer pays exactly [amount]. */
    data class Fixed(val amount: Fiat) : CollectionAmount
}

/**
 * One user's part in a request, as `GetPayers` lists it: a split has one per share, the creator's
 * own included; a collection one per member who has contributed, each paid with nothing due.
 *
 * The wire sends a payer without its currency, so amounts are quarks in the request's
 * [PaymentRequest.currency]; [amountDue] and [amountPaid] attach it.
 */
data class Payer(
    val userId: ID,
    val amountDueQuarks: Long,
    val amountPaidQuarks: Long,
    val status: Status,
) {
    sealed interface Status {
        /** Something is due and nothing is paid. */
        data object Unpaid : Status
        data class Paid(val paid: PaidShare) : Status
        /** A state this build does not know: show the payer without a status. */
        data object Unknown : Status
    }

    val isPaid: Boolean get() = status is Status.Paid

    fun amountDue(currency: CurrencyCode): Fiat = Fiat(quarks = amountDueQuarks, currencyCode = currency)
    fun amountPaid(currency: CurrencyCode): Fiat = Fiat(quarks = amountPaidQuarks, currencyCode = currency)
}

/**
 * Totals over every [Payer] in a request, for progress ("3 of 5 paid, $60 of $100") without walking
 * `GetPayers`. For a collection, [payerCount] and [paidCount] are both the members who have
 * contributed and nothing is due. Amounts are quarks in the request's currency, as on [Payer].
 */
data class PayerSummary(
    val payerCount: Int,
    val paidCount: Int,
    val amountDueQuarks: Long,
    val amountPaidQuarks: Long,
) {
    fun amountDue(currency: CurrencyCode): Fiat = Fiat(quarks = amountDueQuarks, currencyCode = currency)
    fun amountPaid(currency: CurrencyCode): Fiat = Fiat(quarks = amountPaidQuarks, currencyCode = currency)
}

/**
 * The request's title and description. A private group's arrives [Encrypted] and is opened once,
 * on its way into the store, into [Plain] or [Undecryptable]. Both are optional: a request with
 * neither is [Plain] with both empty.
 */
sealed interface PaymentRequestText {
    data class Plain(val title: String, val description: String) : PaymentRequestText

    class Encrypted(val scheme: Int, val nonce: ByteArray, val ciphertext: ByteArray) : PaymentRequestText

    /** The text failed to open, or its scheme is unknown. The request renders as unsupported. */
    data object Undecryptable : PaymentRequestText

    companion object {
        /** `EncryptedPaymentRequestText.Scheme.CHAT_KEY_XCHACHA20POLY1305`. */
        const val SCHEME_CHAT_KEY_XCHACHA20POLY1305 = 1
    }
}

/** Builds a split's share amount from the wire's `native_amount`, rounded to the nearest quark. */
fun shareAmount(nativeAmount: Double, currency: CurrencyCode): Fiat =
    Fiat(quarks = nativeQuarks(nativeAmount), currencyCode = currency)

/** A wire `native_amount`, in whatever currency it is in, rounded to the nearest quark. */
fun nativeQuarks(nativeAmount: Double): Long = Math.round(nativeAmount * Fiat.MULTIPLIER)

/**
 * The copy to keep when [incoming] arrives for a request [held] already: the greater version, and
 * at an equal version EXPIRED over OPEN, since expiry moves no version.
 */
fun newerOf(held: PaymentRequest?, incoming: PaymentRequest): PaymentRequest = when {
    held == null -> incoming
    incoming.version > held.version -> incoming
    incoming.version < held.version -> held
    held.status == PaymentRequestStatus.Expired && incoming.status == PaymentRequestStatus.Open -> held
    // A held copy that failed to decrypt yields to one that opened, at the same version.
    else -> incoming
}

/** What a creator sends to open a request in a private group. */
sealed interface NewPaymentRequest {
    val chatId: ChatId
}

/**
 * What a creator sends to open a split in a private group. [text] is already sealed under the
 * group's chat key; [shares] lists who is asked and for how much, in roster order, the creator
 * first when included.
 */
data class NewSplitRequest(
    override val chatId: ChatId,
    val text: PaymentRequestText.Encrypted,
    val currency: CurrencyCode,
    val endsAt: Instant,
    val shares: List<NewShare>,
) : NewPaymentRequest

/**
 * What a creator sends to open a collection in a private group: any member but the creator pays
 * [amount] once, in its currency. It carries no text and no deadline, so it stays open until the
 * creator closes it. [amount] is a whole number, at least one, of the currency's smallest unit.
 */
data class NewCollectionRequest(
    override val chatId: ChatId,
    val amount: Fiat,
) : NewPaymentRequest

/** One member's part of a [NewSplitRequest]: a whole number, at least one, of the currency's smallest unit. */
data class NewShare(val userId: ID, val amount: Fiat)

/**
 * The intent id a viewer's share payment must use. Anyone who learns it before the share is paid
 * can use it up, so it never prints: keep it in memory for the one payment and never log it.
 */
class PaymentIntentId(val bytes: ByteArray) {
    override fun toString(): String = "PaymentIntentId(redacted)"
}

/**
 * What a request's status line says: the time left to pay, rounded up, or how it ended.
 *
 * The bands are uneven on purpose: [HoursLeft] runs from 1 hour to 24 hours inclusive, so
 * [DaysLeft] is never 1 and a 1-week request reads 7 days.
 *
 * The days and hours bands give 5 minutes of grace before rounding up, so a fresh 7-day request
 * does not read 8 days. The grace covers the transcript's one-minute clock, which can lag the real
 * time, and skew between the creator's device clock and this one. The minutes band has no grace.
 *
 * An open request with no deadline (a collection without `ends_at`) has no time left to show, so
 * [statusLine] gives null for it.
 */
sealed interface StatusLine {
    data class DaysLeft(val days: Int) : StatusLine
    data class HoursLeft(val hours: Int) : StatusLine
    data class MinutesLeft(val minutes: Int) : StatusLine
    data object PaidInFull : StatusLine
    data object Closed : StatusLine
    data object Expired : StatusLine
}

fun PaymentRequest.statusLine(now: Instant): StatusLine? = when (resolvedStatus(now)) {
    PaymentRequestStatus.Completed -> StatusLine.PaidInFull
    PaymentRequestStatus.Closed -> StatusLine.Closed
    PaymentRequestStatus.Expired -> StatusLine.Expired
    PaymentRequestStatus.Open, PaymentRequestStatus.Unknown -> {
        val left = (this.endsAt ?: return null) - now
        val minute = 60_000L
        val hour = 60 * minute
        val ms = left.inWholeMilliseconds
        val grace = 5 * minute
        fun ceilDiv(a: Long, b: Long) = ((a + b - 1) / b).toInt()
        when {
            ms > 24 * hour + grace -> StatusLine.DaysLeft(ceilDiv(ms - grace, 24 * hour))
            ms >= hour -> StatusLine.HoursLeft(ceilDiv(ms - grace, hour).coerceIn(1, 24))
            else -> StatusLine.MinutesLeft(maxOf(1, ceilDiv(ms, minute)))
        }
    }
}

/**
 * The viewer's share to pay on this request now: unpaid, on an open request, or null. The creator
 * owes nothing, even on a share of their own that reads unpaid.
 */
fun PaymentRequest.unpaidShareOf(viewerId: ID?, now: Instant): Share? =
    if (viewerId == null || viewerId == creator || !isOpen(now)) null
    else split?.shareOf(viewerId)?.takeIf { !it.isPaid }

/** Where the viewer stands on a request, shown on every deck card. */
sealed interface ViewerStanding {
    data class Paid(val share: Fiat) : ViewerStanding
    data class Owes(val share: Fiat) : ViewerStanding
    data object Creator : ViewerStanding
    data object NoShare : ViewerStanding
}

/**
 * [viewerId]'s standing: the creator first, then a paid share (however it was paid), then a share
 * still owed on an open request. An unpaid share on an ended request owes nothing.
 */
fun PaymentRequest.viewerStanding(viewerId: ID?, now: Instant): ViewerStanding {
    if (viewerId == null) return ViewerStanding.NoShare
    if (viewerId == creator) return ViewerStanding.Creator
    val share = split?.shareOf(viewerId) ?: return ViewerStanding.NoShare
    return when {
        share.isPaid -> ViewerStanding.Paid(share.amount)
        isOpen(now) -> ViewerStanding.Owes(share.amount)
        else -> ViewerStanding.NoShare
    }
}

/**
 * The deck's default order: open requests [viewerId] owes on, then the other open requests, each
 * by deadline, nearest first. Ended requests are left out.
 */
fun deckOrder(requests: Collection<PaymentRequest>, now: Instant, viewerId: ID? = null): List<PaymentRequest> {
    val (owed, other) = requests.filter { it.isInDeck(now) }.partition { it.unpaidShareOf(viewerId, now) != null }
    return owed.sortedBy { it.split?.endsAt } + other.sortedBy { it.split?.endsAt }
}

/**
 * When the next request in [requests] expires by the clock after [now]: the nearest `ends_at` of
 * an open request, so the deck can drop it at that moment. Null when none is open.
 */
fun nextExpiry(requests: Collection<PaymentRequest>, now: Instant): Instant? =
    requests.filter { it.isInDeck(now) }.mapNotNull { it.split?.endsAt }.minOrNull()
